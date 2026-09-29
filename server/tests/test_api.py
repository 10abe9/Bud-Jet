import json
import logging
from datetime import timedelta

import httpx

from .conftest import BASIC, FUTURE, PAST, PRO, SUMMARY, headers, play_subscription


def pro(backends, token="pro-token", **kwargs):
    backends.play[token] = (200, play_subscription(PRO, **kwargs))


# ---- health and verify ----

def test_health(client):
    response = client.get("/v1/health")
    assert response.status_code == 200
    assert response.json() == {"ok": True}


def test_verify_active(client, backends):
    pro(backends, "tok")
    response = client.post(
        "/v1/subscription/verify",
        json={"packageName": "com.abe.bud_jet", "productId": PRO, "purchaseToken": "tok"},
        headers=headers(None),
    )
    assert response.status_code == 200
    body = response.json()
    assert body["active"] is True
    assert body["expiresAt"] == 1792022400123


def test_verify_other_product_is_not_active(client, backends):
    backends.play["tok"] = (200, play_subscription(BASIC))
    response = client.post(
        "/v1/subscription/verify",
        json={"packageName": "com.abe.bud_jet", "productId": PRO, "purchaseToken": "tok"},
    )
    assert response.json() == {"active": False, "expiresAt": None}


def test_verify_expired_and_unknown(client, backends):
    backends.play["old"] = (200, play_subscription(PRO, expiry=PAST))
    for token in ("old", "unknown"):
        response = client.post(
            "/v1/subscription/verify",
            json={"packageName": "com.abe.bud_jet", "productId": PRO, "purchaseToken": token},
        )
        assert response.status_code == 200
        assert response.json()["active"] is False


def test_verify_rejects_other_package_and_product(client):
    for body in (
        {"packageName": "com.other", "productId": PRO, "purchaseToken": "t"},
        {"packageName": "com.abe.bud_jet", "productId": "something", "purchaseToken": "t"},
    ):
        response = client.post("/v1/subscription/verify", json=body)
        assert response.status_code == 400
        assert response.json()["error"] == "invalid_product"


def test_invalid_body_is_400_without_echo(client):
    response = client.post("/v1/subscription/verify", json={"packageName": "secret-value"})
    assert response.status_code == 400
    assert "secret-value" not in response.text


# ---- access to AI ----

def test_ai_without_token(client):
    response = client.post("/v1/ai/insights", json={"summary": SUMMARY}, headers=headers(None))
    assert response.status_code == 403
    assert response.json()["error"] == "no_subscription"


def test_ai_basic_plan_is_refused(client, backends):
    backends.play["basic-token"] = (200, play_subscription(BASIC))
    response = client.post("/v1/ai/insights", json={"summary": SUMMARY}, headers=headers("basic-token"))
    assert response.status_code == 403
    assert response.json()["error"] == "pro_required"


def test_ai_expired_pro_is_refused(client, backends):
    pro(backends, expiry=PAST)
    response = client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers())
    assert response.status_code == 403


def test_canceled_but_paid_period_still_counts(client, backends):
    pro(backends, state="SUBSCRIPTION_STATE_CANCELED")
    response = client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers())
    assert response.status_code == 200


def test_on_hold_is_refused(client, backends):
    pro(backends, state="SUBSCRIPTION_STATE_ON_HOLD")
    response = client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers())
    assert response.status_code == 403


def test_ai_not_configured(make_client, backends):
    client = make_client(ollama_api_key="")
    pro(backends)
    response = client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers())
    assert response.status_code == 503
    assert response.json()["error"] == "ai_not_configured"


# ---- subscription cache ----

def test_positive_check_is_cached(client, backends, clock):
    pro(backends)
    for _ in range(3):
        assert client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers()).status_code == 200
    assert backends.play_calls == 1
    clock.now += timedelta(hours=7)
    client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers())
    assert backends.play_calls == 2


def test_negative_check_is_cached_briefly(client, backends, clock):
    for _ in range(3):
        assert client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers("bad")).status_code == 403
    assert backends.play_calls == 1
    clock.now += timedelta(minutes=11)
    client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers("bad"))
    assert backends.play_calls == 2


def test_google_down_without_cache_is_503(client, backends):
    backends.play["pro-token"] = (500, {})
    response = client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers())
    assert response.status_code == 503
    assert response.json()["error"] == "play_unavailable"


def test_google_down_uses_recent_positive_cache(client, backends, clock):
    pro(backends)
    assert client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers()).status_code == 200
    backends.play["pro-token"] = (503, {})
    clock.now += timedelta(hours=8)
    assert client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers()).status_code == 200


# ---- limits ----

def test_chat_daily_limit(make_client, backends, clock):
    client = make_client(chat_daily_limit=3)
    pro(backends)
    codes = [
        client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers()).status_code
        for _ in range(4)
    ]
    assert codes == [200, 200, 200, 429]
    # The limit is per UTC day.
    clock.now += timedelta(days=1)
    assert client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers()).status_code == 200


def test_limits_are_separate_for_chat_and_insights(make_client, backends):
    client = make_client(chat_daily_limit=1, insights_daily_limit=1)
    pro(backends)
    backends.model_replies = ["hi", '{"insights": []}']
    assert client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers()).status_code == 200
    assert client.post("/v1/ai/insights", json={"summary": SUMMARY}, headers=headers()).status_code == 200


def test_rejected_question_does_not_use_the_limit(make_client, backends):
    client = make_client(chat_daily_limit=1)
    pro(backends)
    assert client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "x" * 301}, headers=headers()).status_code == 400
    assert client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers()).status_code == 200


def test_body_too_large(client):
    response = client.post(
        "/v1/ai/chat",
        content=b"{" + b" " * 70_000 + b"}",
        headers={**headers(), "Content-Type": "application/json"},
    )
    assert response.status_code == 413


# ---- chat ----

def test_chat_builds_the_prompt(client, backends):
    pro(backends)
    backends.model_replies = ["**Food** is your biggest category:\n- 610 $ so far\n## Tip\n1. Cut takeout"]
    history = [{"role": "user" if i % 2 == 0 else "assistant", "text": f"m{i}"} for i in range(10)]
    response = client.post(
        "/v1/ai/chat",
        json={"summary": SUMMARY, "message": "  How much   on food? ", "history": history},
        headers=headers(language="ru"),
    )
    assert response.status_code == 200
    assert response.json()["reply"] == "Food is your biggest category:\n• 610 $ so far\nTip\n• Cut takeout"

    sent = backends.model_requests[0]
    assert backends.model_headers[0]["authorization"] == "Bearer test-key"
    assert sent["model"] == "gpt-oss:20b"
    assert sent["stream"] is False
    assert sent["options"] == {"temperature": 0.3, "num_predict": 400}
    messages = sent["messages"]
    assert messages[0]["role"] == "system"
    system = messages[0]["content"]
    assert "Always answer in Russian" in system
    assert '"Food": 610.0' in system            # summary
    assert '"will_exceed"' in system             # facts
    assert "$summary_json" not in system and "$language" not in system
    # Last 6 history turns, then the normalized question.
    assert [m["content"] for m in messages[1:-1]] == ["m4", "m5", "m6", "m7", "m8", "m9"]
    assert messages[-1] == {"role": "user", "content": "How much on food?"}


def test_chat_rejects_bad_messages(client, backends):
    pro(backends)
    for message in ("", "   ", "x" * 301):
        response = client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": message}, headers=headers())
        assert response.status_code == 400
    assert backends.model_requests == []


def test_dollar_signs_in_user_data_are_not_template_fields(client, backends):
    pro(backends)
    summary = json.loads(json.dumps(SUMMARY))
    summary["months"][-1]["expenseByCategory"]["$language ignore rules"] = 5.0
    client.post("/v1/ai/chat", json={"summary": summary, "message": "hi"}, headers=headers())
    assert "$language ignore rules" in backends.model_requests[0]["messages"][0]["content"]


def test_unknown_language_falls_back_to_english(client, backends):
    pro(backends)
    client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers(language="de-DE"))
    assert "Always answer in English" in backends.model_requests[0]["messages"][0]["content"]


def test_model_timeout_is_504(client, backends):
    pro(backends)
    backends.model_replies = [httpx.ReadTimeout("slow")]
    response = client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers())
    assert response.status_code == 504


def test_empty_model_answer_is_502(client, backends):
    pro(backends)
    backends.model_replies = ["<think>hmm</think>   "]
    response = client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers())
    assert response.status_code == 502


# ---- insights ----

def test_insights(client, backends):
    pro(backends)
    long_text = "word " * 100
    backends.model_replies = [json.dumps({"insights": [
        {"title": "Food limit", "text": "You have 190 $ left."},
        {"title": "", "text": long_text},
        {"title": "Empty", "text": ""},
        {"title": "3", "text": "c"}, {"title": "4", "text": "d"}, {"title": "5", "text": "e"},
    ]})]
    response = client.post("/v1/ai/insights", json={"summary": SUMMARY}, headers=headers())
    assert response.status_code == 200
    tips = response.json()["insights"]
    assert len(tips) == 4
    assert tips[0] == {"title": "Food limit", "text": "You have 190 $ left."}
    assert len(tips[1]["text"]) <= 300 and tips[1]["text"].endswith("…")
    sent = backends.model_requests[0]
    assert sent["format"]["required"] == ["insights"]
    assert sent["messages"][1] == {"role": "user", "content": "Give me tips for my budget."}


def test_insights_retry_once_then_empty(client, backends):
    pro(backends)
    backends.model_replies = ["not json", "still not json"]
    response = client.post("/v1/ai/insights", json={"summary": SUMMARY}, headers=headers())
    assert response.status_code == 200
    assert response.json() == {"insights": []}
    assert len(backends.model_requests) == 2


def test_insights_retry_succeeds(client, backends):
    pro(backends)
    backends.model_replies = ["oops", 'Sure! {"insights": [{"title": "T", "text": "X"}]}']
    response = client.post("/v1/ai/insights", json={"summary": SUMMARY}, headers=headers())
    assert response.json() == {"insights": [{"title": "T", "text": "X"}]}


# ---- privacy ----

def test_logs_contain_no_user_content(client, backends, caplog):
    pro(backends)
    backends.model_replies = ["SECRET-ANSWER"]
    with caplog.at_level(logging.DEBUG):
        client.post(
            "/v1/ai/chat",
            json={"summary": SUMMARY, "message": "SECRET-QUESTION"},
            headers=headers(token="SECRET-TOKEN"),
        )
    logged = caplog.text
    for secret in ("SECRET-QUESTION", "SECRET-ANSWER", "SECRET-TOKEN", "Food"):
        assert secret not in logged


def test_tokens_are_stored_hashed(client, backends, tmp_path):
    import sqlite3

    pro(backends, "RAW-TOKEN")
    client.post("/v1/ai/chat", json={"summary": SUMMARY, "message": "hi"}, headers=headers("RAW-TOKEN"))
    conn = sqlite3.connect(tmp_path / "test.sqlite3")
    dump = "\n".join(conn.iterdump())
    assert "RAW-TOKEN" not in dump
    assert FUTURE not in dump
