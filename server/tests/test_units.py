import json
from datetime import date

from app.facts import build_facts
from app.google_play import entitled_products, parse_rfc3339_ms
from app.schemas import BudgetSummary
from app.text import clean_reply, normalize_message, parse_insights

from .conftest import SUMMARY

TODAY = date(2026, 9, 15)


def summary(**overrides) -> BudgetSummary:
    data = {**SUMMARY, **overrides}
    return BudgetSummary.model_validate(data)


# ---- facts ----

def test_facts_full_data():
    facts = build_facts(summary(), TODAY)
    assert facts["data_quality"] == "ok"
    assert facts["days_in_month"] == 30
    assert facts["day_of_month"] == 15
    assert facts["days_left_in_month"] == 15
    assert facts["full_previous_months"] == 2
    assert facts["current_month_projected_expense"] == 2481.8
    assert facts["avg_expense_previous_months"] == 1975.2
    food = facts["categories"][0]
    assert food["category"] == "Food"
    assert food["projected"] == 1220.0
    assert food["avg_previous_months"] == 850.25
    assert food["change_percent"] == 43
    limit = facts["limits"][0]
    assert limit["used_percent"] == 76
    assert limit["remaining"] == 190.0
    assert limit["daily_allowance_for_rest_of_month"] == 12.67
    assert limit["will_exceed"] is True  # 610 by mid-month -> 1220 projected
    goal = facts["saving_goal"]
    assert goal["remaining"] == 3800.0
    assert goal["months_left"] == 4
    assert goal["required_per_month"] == 950.0
    assert goal["on_track"] is True  # saved ~1025/month before


def test_facts_empty_summary():
    facts = build_facts(BudgetSummary(currency="EUR"), TODAY)
    assert facts["data_quality"] == "empty"
    assert facts["categories"] == []
    assert facts["saving_goal"] is None


def test_facts_single_month_is_thin():
    facts = build_facts(summary(months=[SUMMARY["months"][-1]]), TODAY)
    assert facts["data_quality"] == "thin"
    assert facts["avg_expense_previous_months"] is None
    assert facts["categories"][0]["change_percent"] is None


def test_facts_early_month_has_no_projection():
    facts = build_facts(summary(), date(2026, 9, 2))
    assert facts["current_month_projected_expense"] is None
    assert facts["limits"][0]["will_exceed"] is False


def test_facts_finished_month_when_clock_is_ahead():
    # The app's current month is August but the server is already in September.
    facts = build_facts(summary(months=SUMMARY["months"][:2]), TODAY)
    assert facts["current_month"] == "2026-08"
    assert facts["day_of_month"] == 31
    assert facts["days_left_in_month"] == 0


def test_facts_goal_without_deadline_and_passed_deadline():
    no_deadline = build_facts(summary(savingGoal={"target": 100, "saved": 150, "deadline": None}), TODAY)
    assert no_deadline["saving_goal"]["months_left"] is None
    assert no_deadline["saving_goal"]["reached"] is True
    passed = build_facts(summary(savingGoal={"target": 1000, "saved": 10, "deadline": "2026-01-01"}), TODAY)
    assert passed["saving_goal"]["deadline_passed"] is True
    assert passed["saving_goal"]["required_per_month"] is None


def test_zero_limit_does_not_divide_by_zero():
    facts = build_facts(summary(limits=[{"category": "Fun", "limit": 0, "spentThisMonth": 5}]), TODAY)
    assert facts["limits"][0]["used_percent"] is None
    assert facts["limits"][0]["will_exceed"] is True


# ---- Google Play parsing ----

def test_parse_rfc3339_with_nanoseconds():
    assert parse_rfc3339_ms("2026-10-15T00:00:00.123456789Z") == 1792022400123
    assert parse_rfc3339_ms("2026-10-15T00:00:00Z") == 1792022400000


def test_entitled_products_filters_state_and_expiry():
    now = parse_rfc3339_ms("2026-09-15T00:00:00Z")
    response = {
        "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
        "lineItems": [
            {"productId": "a", "expiryTime": "2026-10-01T00:00:00Z"},
            {"productId": "b", "expiryTime": "2026-09-01T00:00:00Z"},
            {"productId": "c"},
        ],
    }
    assert list(entitled_products(response, now)) == ["a"]
    assert entitled_products({**response, "subscriptionState": "SUBSCRIPTION_STATE_EXPIRED"}, now) == {}
    assert entitled_products({**response, "subscriptionState": "SUBSCRIPTION_STATE_PENDING"}, now) == {}


# ---- text ----

def test_normalize_message():
    assert normalize_message("  a \n\t b  ") == "a b"


def test_clean_reply_strips_markdown_and_limits_length():
    assert clean_reply("### Title\n* one\n+ two\n3) three\n\n\n\nend `x`") == "Title\n• one\n• two\n• three\n\nend x"
    long = clean_reply("word " * 1000)
    assert len(long) <= 1500 and long.endswith("…")


def test_parse_insights_variants():
    assert parse_insights('{"insights": []}') == []
    assert parse_insights("garbage") is None
    assert parse_insights('{"tips": []}') is None
    assert parse_insights('```json\n{"insights": [{"title": "**A**", "text": "b"}]}\n```') == [{"title": "A", "text": "b"}]
    assert parse_insights('{"insights": ["x", {"title": "t"}]}') == []


# ---- Ollama errors ----

def test_ollama_error_detail_is_reported():
    import asyncio

    import httpx

    from app.ollama_client import ModelError, OllamaClient

    def handler(request):
        if request.url.path == "/api/tags":
            return httpx.Response(200, json={"models": [{"name": "gpt-oss:120b"}, {"model": "qwen3:8b"}]})
        return httpx.Response(404, json={"error": "model 'nope' not found"})

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
            client = OllamaClient(http, "https://ollama.com/", "key", "nope")
            assert await client.list_models() == ["gpt-oss:120b", "qwen3:8b"]
            try:
                await client.chat([{"role": "user", "content": "SECRET"}], temperature=0, num_predict=1)
            except ModelError as error:
                return str(error)

    message = asyncio.run(run())
    assert message == "http 404: model 'nope' not found"



# ---- facts v2: active months, counts, suggested limits, weeks ----

def test_leading_empty_months_do_not_lower_averages():
    empty = [{"month": m, "income": 0, "expense": 0, "expenseByCategory": {}} for m in ("2026-04", "2026-05", "2026-06")]
    facts = build_facts(summary(months=empty + SUMMARY["months"]), TODAY)
    assert facts["full_previous_months"] == 2
    assert facts["avg_expense_previous_months"] == 1975.2


def test_categories_with_counts_and_suggested_limit():
    months = json.loads(json.dumps(SUMMARY["months"]))
    months[-1]["expenseCountByCategory"] = {"Food": 20, "Transport": 5}
    months[0]["expenseCountByCategory"] = {"Food": 18}
    months[1]["expenseCountByCategory"] = {"Food": 22}
    facts = build_facts(summary(months=months), TODAY)
    food = facts["categories"][0]
    assert food["operations_so_far"] == 20
    assert food["avg_operation"] == 30.5
    assert food["avg_operations_previous_months"] == 20.0
    assert food["has_limit"] is True
    assert food["suggested_limit"] == 760.0  # 850.25 * 0.9 = 765.2 -> 760
    transport = next(c for c in facts["categories"] if c["category"] == "Transport")
    assert transport["has_limit"] is False
    assert transport["suggested_limit"] == 270.0  # 300 * 0.9


def test_category_only_in_previous_months_is_listed():
    months = json.loads(json.dumps(SUMMARY["months"]))
    months[0]["expenseByCategory"]["Travel"] = 900.0
    facts = build_facts(summary(months=months), TODAY)
    travel = next(c for c in facts["categories"] if c["category"] == "Travel")
    assert travel["spent_so_far"] == 0.0
    assert travel["avg_previous_months"] == 450.0


def test_income_categories():
    months = json.loads(json.dumps(SUMMARY["months"]))
    for m in months:
        m["incomeByCategory"] = {"Salary": 3000.0}
    months[-1]["incomeByCategory"]["Freelance"] = 200.0
    facts = build_facts(summary(months=months), TODAY)
    assert facts["income_categories"][0] == {"category": "Salary", "received_so_far": 3000.0, "avg_previous_months": 3000.0}
    assert facts["income_categories"][1]["category"] == "Freelance"


def test_weeks_facts():
    weeks = [
        {"weekStart": "2026-08-10", "expense": 0},
        {"weekStart": "2026-08-17", "expense": 0},
        {"weekStart": "2026-08-24", "expense": 400.0},
        {"weekStart": "2026-08-31", "expense": 500.0},
        {"weekStart": "2026-09-07", "expense": 600.0},
        {"weekStart": "2026-09-14", "expense": 300.0},  # Monday; TODAY is Tuesday the 15th
    ]
    facts = build_facts(summary(weeks=weeks), TODAY)["weeks"]
    assert facts["current_week_day"] == 2
    assert facts["full_weeks"] == 3  # leading empty weeks dropped
    assert facts["avg_full_week_expense"] == 500.0
    assert facts["last_full_week_expense"] == 600.0
    assert facts["current_week_projected"] == 1050.0
    assert facts["current_week_vs_avg_percent"] == 110


def test_no_weeks_from_old_app():
    assert build_facts(summary(), TODAY)["weeks"] is None


def test_goal_pace():
    goal = build_facts(summary(), TODAY)["saving_goal"]
    # 3800 left, ~1025 saved per month before -> 4 months.
    assert goal["months_needed_at_current_pace"] == 4


def test_friendly_round_down():
    from app.facts import friendly_round_down

    assert friendly_round_down(765.2) == 760.0
    assert friendly_round_down(12345) == 12000.0
    assert friendly_round_down(48.7) == 48.0
    assert friendly_round_down(0) is None


def test_prompts_match_the_documentation():
    """docs/backend-prompt.md section 4 shows the prompts; keep it identical to the code."""
    import re
    from pathlib import Path

    import pytest

    from app import prompts

    doc = Path(__file__).resolve().parents[2] / "docs" / "backend-prompt.md"
    if not doc.exists():
        pytest.skip("docs/ is not deployed with the server")
    blocks = re.findall(r"```text\n(.*?)```", doc.read_text(encoding="utf-8"), re.S)
    assert blocks[1].rstrip("\n") == prompts.CHAT_SYSTEM_PROMPT.template.rstrip("\n")
    assert blocks[2].rstrip("\n") == prompts.INSIGHTS_SYSTEM_PROMPT.template.rstrip("\n")
