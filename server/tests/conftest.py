import json
from datetime import datetime, timezone
from urllib.parse import unquote

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app

NOW = datetime(2026, 9, 15, 12, 0, tzinfo=timezone.utc)
FUTURE = "2026-10-15T00:00:00.123456789Z"
PAST = "2026-09-01T00:00:00Z"

PRO = "bud_jet_premium"
BASIC = "bud_jet_base"


def play_subscription(product: str, state: str = "SUBSCRIPTION_STATE_ACTIVE", expiry: str = FUTURE) -> dict:
    return {"subscriptionState": state, "lineItems": [{"productId": product, "expiryTime": expiry}]}


class FakeBackends:
    """Stands in for Google Play and Ollama behind one httpx MockTransport."""

    def __init__(self):
        self.play: dict[str, tuple[int, dict]] = {}
        self.play_calls = 0
        self.model_replies: list = []
        self.model_requests: list[dict] = []
        self.model_headers: list[httpx.Headers] = []

    def handler(self, request: httpx.Request) -> httpx.Response:
        if request.url.host == "androidpublisher.googleapis.com":
            self.play_calls += 1
            token = unquote(request.url.path.rsplit("/", 1)[-1])
            status, body = self.play.get(token, (404, {"error": {"code": 404}}))
            return httpx.Response(status, json=body)
        if request.url.path == "/api/chat":
            self.model_requests.append(json.loads(request.content))
            self.model_headers.append(request.headers)
            reply = self.model_replies.pop(0) if self.model_replies else "OK"
            if isinstance(reply, Exception):
                raise reply
            return httpx.Response(
                200,
                json={"message": {"role": "assistant", "content": reply}, "eval_count": 5, "prompt_eval_count": 50},
            )
        return httpx.Response(599)


class Clock:
    def __init__(self, now: datetime):
        self.now = now

    def __call__(self) -> datetime:
        return self.now


@pytest.fixture
def backends() -> FakeBackends:
    return FakeBackends()


@pytest.fixture
def clock() -> Clock:
    return Clock(NOW)


@pytest.fixture
def make_client(backends, clock, tmp_path):
    clients = []

    def make(**overrides) -> TestClient:
        values = {"ollama_api_key": "test-key", "db_path": str(tmp_path / "test.sqlite3")}
        values.update(overrides)
        settings = Settings(_env_file=None, **values)
        http = httpx.AsyncClient(transport=httpx.MockTransport(backends.handler))

        async def google_token() -> str:
            return "google-access-token"

        app = create_app(settings, http_client=http, google_token_provider=google_token, clock=clock)
        client = TestClient(app)
        client.__enter__()
        clients.append(client)
        return client

    yield make
    for client in clients:
        client.__exit__(None, None, None)


@pytest.fixture
def client(make_client) -> TestClient:
    return make_client()


SUMMARY = {
    "currency": "USD",
    "months": [
        {"month": "2026-07", "income": 3000.0, "expense": 1850.4,
         "expenseByCategory": {"Food": 720.5, "Transport": 310.0}},
        {"month": "2026-08", "income": 3000.0, "expense": 2100.0,
         "expenseByCategory": {"Food": 980.0, "Transport": 290.0}},
        {"month": "2026-09", "income": 3000.0, "expense": 1240.9,
         "expenseByCategory": {"Food": 610.0, "Transport": 150.0}},
    ],
    "limits": [{"category": "Food", "limit": 800.0, "spentThisMonth": 610.0}],
    "savingGoal": {"target": 5000.0, "saved": 1200.0, "deadline": "2026-12-31"},
}


def headers(token: str | None = "pro-token", language: str = "en", install: str = "install-1") -> dict:
    result = {"X-Install-Id": install, "X-App-Version": "11.0", "Accept-Language": language}
    if token is not None:
        result["X-Purchase-Token"] = token
    return result
