"""Numbers computed from the summary before the model sees it.

Language models are unreliable at arithmetic, so projections, averages, limit usage and the
savings-goal pace are calculated here and passed to the prompt as <facts>.
"""

import calendar
import math
from datetime import date
from statistics import mean
from typing import Any

from .schemas import BudgetSummary

# Below this share of the month, a projection is mostly noise.
MIN_PROGRESS_FOR_PROJECTION = 0.1
MAX_CATEGORIES = 8
AVG_DAYS_PER_MONTH = 30.44


def _r2(value: float | None) -> float | None:
    return None if value is None else round(value, 2)


def _pct(value: float | None) -> int | None:
    return None if value is None else int(round(value))


def build_facts(summary: BudgetSummary, today: date) -> dict[str, Any]:
    months = sorted(summary.months, key=lambda m: m.month)
    today_month = today.strftime("%Y-%m")

    current = months[-1] if months else None
    previous = [m for m in months[:-1]] if current else []

    if current is not None:
        year, month = (int(part) for part in current.month.split("-"))
        days_in_month = calendar.monthrange(year, month)[1]
        # The app's clock may be a day off from UTC; an older month counts as finished.
        if current.month == today_month:
            day_of_month = today.day
        elif current.month < today_month:
            day_of_month = days_in_month
        else:
            day_of_month = 1
    else:
        days_in_month = calendar.monthrange(today.year, today.month)[1]
        day_of_month = today.day

    progress = day_of_month / days_in_month
    days_left = days_in_month - day_of_month
    can_project = progress >= MIN_PROGRESS_FOR_PROJECTION

    def project(amount: float) -> float | None:
        return amount / progress if can_project else None

    current_expense = current.expense if current else 0.0
    current_income = current.income if current else 0.0

    avg_expense_prev = mean(m.expense for m in previous) if previous else None
    avg_income_prev = mean(m.income for m in previous) if previous else None
    prev_income_total = sum(m.income for m in previous)
    prev_expense_total = sum(m.expense for m in previous)
    savings_rate_prev = (
        (prev_income_total - prev_expense_total) / prev_income_total * 100
        if previous and prev_income_total > 0
        else None
    )
    avg_net_prev = mean(m.income - m.expense for m in previous) if previous else None

    categories = []
    for name, spent in (current.expenseByCategory.items() if current else []):
        projected = project(spent)
        avg_prev = mean(m.expenseByCategory.get(name, 0.0) for m in previous) if previous else None
        change = (
            (projected - avg_prev) / avg_prev * 100
            if projected is not None and avg_prev is not None and avg_prev > 0
            else None
        )
        categories.append(
            {
                "category": name,
                "spent_so_far": _r2(spent),
                "projected": _r2(projected),
                "avg_previous_months": _r2(avg_prev),
                "change_percent": _pct(change),
            }
        )
    categories.sort(key=lambda c: c["projected"] if c["projected"] is not None else c["spent_so_far"], reverse=True)
    categories = categories[:MAX_CATEGORIES]

    limits = []
    for item in summary.limits:
        spent = item.spentThisMonth
        remaining = item.limit - spent
        projected = project(spent)
        limits.append(
            {
                "category": item.category,
                "limit": _r2(item.limit),
                "spent": _r2(spent),
                "used_percent": _pct(spent / item.limit * 100) if item.limit > 0 else None,
                "remaining": _r2(remaining),
                "daily_allowance_for_rest_of_month": (
                    _r2(remaining / days_left) if remaining > 0 and days_left > 0 else None
                ),
                "projected_at_month_end": _r2(projected),
                "will_exceed": spent > item.limit or (projected is not None and projected > item.limit),
            }
        )

    saving_goal = None
    goal = summary.savingGoal
    if goal is not None:
        remaining = max(goal.target - goal.saved, 0.0)
        months_left = None
        if goal.deadline:
            deadline = date.fromisoformat(goal.deadline)
            days = (deadline - today).days
            months_left = max(math.ceil(days / AVG_DAYS_PER_MONTH), 0) if days > 0 else 0
        required = remaining / months_left if months_left else None
        saving_goal = {
            "target": _r2(goal.target),
            "saved": _r2(goal.saved),
            "remaining": _r2(remaining),
            "deadline": goal.deadline,
            "months_left": months_left,
            "deadline_passed": months_left == 0 if goal.deadline else None,
            "required_per_month": _r2(required),
            "avg_monthly_net_previous": _r2(avg_net_prev),
            "on_track": (
                None if required is None or avg_net_prev is None else avg_net_prev >= required
            ),
            "reached": remaining == 0,
        }

    has_expenses = any(m.expense > 0 for m in months)
    if not has_expenses:
        data_quality = "empty"
    elif not previous:
        data_quality = "thin"
    else:
        data_quality = "ok"

    return {
        "today": today.isoformat(),
        "current_month": current.month if current else today_month,
        "day_of_month": day_of_month,
        "days_in_month": days_in_month,
        "days_left_in_month": days_left,
        "month_progress_percent": _pct(progress * 100),
        "current_month_expense_so_far": _r2(current_expense),
        "current_month_income_so_far": _r2(current_income),
        "current_month_projected_expense": _r2(project(current_expense)),
        "full_previous_months": len(previous),
        "avg_expense_previous_months": _r2(avg_expense_prev),
        "avg_income_previous_months": _r2(avg_income_prev),
        "savings_rate_previous_months_percent": _pct(savings_rate_prev),
        "categories": categories,
        "limits": limits,
        "saving_goal": saving_goal,
        "data_quality": data_quality,
    }
