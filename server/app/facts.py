"""Numbers computed from the summary before the model sees it.

Language models are unreliable at arithmetic, so projections, averages, limit usage, suggested
limits, weekly pace and the savings-goal pace are calculated here and passed to the prompt as
<facts>. The model is told to use these numbers instead of computing its own.
"""

import calendar
import math
from datetime import date
from statistics import mean
from typing import Any

from .schemas import BudgetSummary, MonthSummary, WeekSummary

# Below this share of the month, a projection is mostly noise.
MIN_PROGRESS_FOR_PROJECTION = 0.1
MAX_CATEGORIES = 8
MAX_INCOME_CATEGORIES = 5
AVG_DAYS_PER_MONTH = 30.44
# Suggested limit = this share of the category's average in previous months.
SUGGESTED_LIMIT_SHARE = 0.9


def _r2(value: float | None) -> float | None:
    return None if value is None else round(value, 2)


def _pct(value: float | None) -> int | None:
    return None if value is None else int(round(value))


def friendly_round_down(value: float) -> float | None:
    """765 -> 760, 12345 -> 12000, 48 -> 48: a number a person would set as a limit."""
    if value <= 0:
        return None
    step = 10 ** max(int(math.floor(math.log10(value))) - 1, 0)
    return float(math.floor(value / step) * step)


def _active_months(months: list[MonthSummary]) -> list[MonthSummary]:
    """Drops leading months without any data (before the user started tracking)."""
    ordered = sorted(months, key=lambda m: m.month)
    for index, month in enumerate(ordered):
        if month.income or month.expense or index == len(ordered) - 1:
            return ordered[index:]
    return ordered


def _active_weeks(weeks: list[WeekSummary]) -> list[WeekSummary]:
    ordered = sorted(weeks, key=lambda w: w.weekStart)
    for index, week in enumerate(ordered):
        if week.expense or index == len(ordered) - 1:
            return ordered[index:]
    return ordered


def _avg(values: list[float]) -> float | None:
    return mean(values) if values else None


def build_facts(summary: BudgetSummary, today: date) -> dict[str, Any]:
    months = _active_months(summary.months)
    today_month = today.strftime("%Y-%m")

    current = months[-1] if months else None
    previous = months[:-1] if current else []

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
    projected_expense = project(current_expense)

    prev_income_total = sum(m.income for m in previous)
    prev_expense_total = sum(m.expense for m in previous)
    savings_rate_prev = (
        (prev_income_total - prev_expense_total) / prev_income_total * 100
        if previous and prev_income_total > 0
        else None
    )
    avg_net_prev = _avg([m.income - m.expense for m in previous])
    limited = {item.category for item in summary.limits}

    # ---- Expense categories: this month and in previous months ----
    names = list(current.expenseByCategory) if current else []
    for m in previous:
        names += [n for n in m.expenseByCategory if n not in names]
    categories = []
    for name in names:
        spent = current.expenseByCategory.get(name, 0.0) if current else 0.0
        operations = current.expenseCountByCategory.get(name) if current else None
        projected = project(spent)
        avg_prev = _avg([m.expenseByCategory.get(name, 0.0) for m in previous])
        prev_counts = [m.expenseCountByCategory.get(name, 0) for m in previous if m.expenseCountByCategory]
        avg_ops_prev = _avg(prev_counts)
        change = (
            (projected - avg_prev) / avg_prev * 100
            if projected is not None and avg_prev is not None and avg_prev > 0
            else None
        )
        basis = projected if projected is not None else spent
        categories.append(
            {
                "category": name,
                "spent_so_far": _r2(spent),
                "operations_so_far": operations,
                "avg_operation": _r2(spent / operations) if operations else None,
                "projected": _r2(projected),
                "share_of_month_expense_percent": (
                    _pct(basis / projected_expense * 100)
                    if projected is not None and projected_expense
                    else (_pct(spent / current_expense * 100) if current_expense else None)
                ),
                "avg_previous_months": _r2(avg_prev),
                "avg_operations_previous_months": _r2(avg_ops_prev),
                "change_percent": _pct(change),
                "has_limit": name in limited,
                "suggested_limit": (
                    friendly_round_down(avg_prev * SUGGESTED_LIMIT_SHARE) if avg_prev else None
                ),
                "_rank": max(basis, avg_prev or 0.0),
            }
        )
    categories.sort(key=lambda c: c["_rank"], reverse=True)
    categories = categories[:MAX_CATEGORIES]
    for category in categories:
        del category["_rank"]

    # ---- Income categories ----
    income_names = list(current.incomeByCategory) if current else []
    for m in previous:
        income_names += [n for n in m.incomeByCategory if n not in income_names]
    income_categories = [
        {
            "category": name,
            "received_so_far": _r2(current.incomeByCategory.get(name, 0.0) if current else 0.0),
            "avg_previous_months": _r2(_avg([m.incomeByCategory.get(name, 0.0) for m in previous])),
        }
        for name in income_names
    ]
    income_categories.sort(
        key=lambda c: max(c["received_so_far"], c["avg_previous_months"] or 0.0), reverse=True
    )
    income_categories = income_categories[:MAX_INCOME_CATEGORIES]

    # ---- Limits ----
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

    # ---- Weeks ----
    weeks_facts = None
    weeks = _active_weeks(summary.weeks)
    if weeks:
        current_week = weeks[-1]
        full_weeks = weeks[:-1]
        week_day = min(max((today - date.fromisoformat(current_week.weekStart)).days + 1, 1), 7)
        avg_week = _avg([w.expense for w in full_weeks])
        projected_week = current_week.expense / week_day * 7 if week_day >= 2 else None
        weeks_facts = {
            "current_week_start": current_week.weekStart,
            "current_week_day": week_day,
            "current_week_expense_so_far": _r2(current_week.expense),
            "current_week_projected": _r2(projected_week),
            "last_full_week_expense": _r2(full_weeks[-1].expense) if full_weeks else None,
            "avg_full_week_expense": _r2(avg_week),
            "full_weeks": len(full_weeks),
            "current_week_vs_avg_percent": (
                _pct((projected_week - avg_week) / avg_week * 100)
                if projected_week is not None and avg_week
                else None
            ),
        }

    # ---- Savings goal ----
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
            "months_needed_at_current_pace": (
                math.ceil(remaining / avg_net_prev) if avg_net_prev and avg_net_prev > 0 and remaining else None
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
        "current_month_projected_expense": _r2(projected_expense),
        "full_previous_months": len(previous),
        "avg_expense_previous_months": _r2(_avg([m.expense for m in previous])),
        "avg_income_previous_months": _r2(_avg([m.income for m in previous])),
        "savings_rate_previous_months_percent": _pct(savings_rate_prev),
        "categories": categories,
        "income_categories": income_categories,
        "limits": limits,
        "weeks": weeks_facts,
        "saving_goal": saving_goal,
        "data_quality": data_quality,
    }
