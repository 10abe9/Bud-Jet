"""Bud-Jet backend: subscription checks and the AI assistant (Pro plan).

Contract with the Android app: docs/backend-api.md. Nothing the user sends (budget summary,
questions) or the model returns is stored or logged.
"""

import logging
import os
import time
from collections.abc import Awaitable, Callable
from contextlib import asynccontextmanager
from dataclasses import dataclass
from datetime import datetime, timezone

import httpx
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from . import prompts
from .config import Settings, get_settings
from .db import Database
from .entitlement import Entitlements, token_hash
from .facts import build_facts
from .google_play import GooglePlayClient, GooglePlayUnavailable, service_account_token_provider
from .ollama_client import ModelError, ModelTimeout, OllamaClient
from .ratelimit import RateLimiter
from .schemas import (
    MAX_MESSAGE_CHARS,
    ChatRequest,
    ChatResponse,
    InsightsRequest,
    InsightsResponse,
    VerifyRequest,
    VerifyResponse,
)
from .text import INSIGHTS_JSON_SCHEMA, clean_reply, normalize_message, parse_insights

log = logging.getLogger("budjet")

MAX_TOKEN_CHARS = 4096


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str = ""):
        self.status = status
        self.code = code
        self.message = message or code


@dataclass
class Services:
    settings: Settings
    db: Database
    limiter: RateLimiter
    entitlements: Entitlements
    ollama: OllamaClient
    clock: Callable[[], datetime]


def create_app(
    settings: Settings | None = None,
    *,
    http_client: httpx.AsyncClient | None = None,
    google_token_provider: Callable[[], Awaitable[str]] | None = None,
    clock: Callable[[], datetime] | None = None,
) -> FastAPI:
    """App factory; tests pass fake HTTP transport, Google credentials and clock."""
    settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        http = http_client or httpx.AsyncClient()
        db = Database(settings.db_path)
        provider = google_token_provider or service_account_token_provider(
            settings.google_application_credentials
        )
        google = GooglePlayClient(http, settings.package_name, provider)
        app.state.services = Services(
            settings=settings,
            db=db,
            limiter=RateLimiter(db),
            entitlements=Entitlements(db, google),
            ollama=OllamaClient(
                http,
                settings.ollama_base_url,
                settings.ollama_api_key,
                settings.ollama_model,
                settings.ollama_think,
                settings.ollama_timeout_seconds,
            ),
            clock=clock or (lambda: datetime.now(timezone.utc)),
        )
        if not settings.ollama_api_key:
            log.warning("OLLAMA_API_KEY is not set: AI endpoints will answer 503")
        if google_token_provider is None and not os.path.isfile(settings.google_application_credentials):
            log.warning(
                "Google service account file %s not found: subscription checks will answer 503",
                settings.google_application_credentials,
            )
        try:
            yield
        finally:
            if http_client is None:
                await http.aclose()
            db.close()

    app = FastAPI(title="Bud-Jet API", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)

    # ---- Errors: always {"error": code, "message": text}; never echo the request body ----

    @app.exception_handler(ApiError)
    async def _api_error(_: Request, error: ApiError):
        return JSONResponse({"error": error.code, "message": error.message}, status_code=error.status)

    @app.exception_handler(RequestValidationError)
    async def _invalid(_: Request, __: RequestValidationError):
        return JSONResponse({"error": "invalid_request", "message": "invalid request body"}, status_code=400)

    @app.exception_handler(StarletteHTTPException)
    async def _http(_: Request, error: StarletteHTTPException):
        code = "not_found" if error.status_code == 404 else "http_error"
        return JSONResponse({"error": code, "message": code}, status_code=error.status_code)

    # ---- Body size limit and access log (method, path, status, time only) ----

    @app.middleware("http")
    async def _limits_and_log(request: Request, call_next):
        started = time.monotonic()
        length = request.headers.get("content-length")
        if length is not None:
            if not length.isdigit():
                return JSONResponse({"error": "invalid_request", "message": "bad content-length"}, status_code=400)
            if int(length) > settings.max_body_bytes:
                return JSONResponse({"error": "too_large", "message": "request body too large"}, status_code=413)
        response = await call_next(request)
        log.info(
            "%s %s %s %.0fms", request.method, request.url.path, response.status_code,
            (time.monotonic() - started) * 1000,
        )
        return response

    # ---- Helpers ----

    def services(request: Request) -> Services:
        return request.app.state.services

    def client_ip(request: Request) -> str:
        return request.client.host if request.client else "unknown"

    def install_id(request: Request) -> str:
        return (request.headers.get("x-install-id") or "unknown").strip()[:64]

    def language(request: Request) -> str:
        code = (request.headers.get("accept-language") or "en").strip()[:2].lower()
        return code if code in prompts.LANGUAGE_NAMES else "en"

    def check_ip(svc: Services, request: Request, now: datetime) -> None:
        if not svc.limiter.allow_hourly(f"ip:{client_ip(request)}", svc.settings.ip_hourly_limit, now):
            raise ApiError(429, "rate_limited", "too many requests")

    async def active_products(svc: Services, token: str, now: datetime) -> dict[str, int]:
        try:
            return await svc.entitlements.products(token, int(now.timestamp() * 1000))
        except GooglePlayUnavailable as error:
            log.error("subscription check failed: %s", error)
            raise ApiError(503, "play_unavailable", "could not check the subscription, try later")

    async def authorize_ai(svc: Services, request: Request, kind: str, daily_limit: int, now: datetime) -> None:
        """Pro subscription plus per-install and per-subscription daily limits."""
        if not svc.settings.ollama_api_key:
            raise ApiError(503, "ai_not_configured", "the AI service is not configured")
        check_ip(svc, request, now)
        token = (request.headers.get("x-purchase-token") or "").strip()
        if not token:
            raise ApiError(403, "no_subscription", "a Pro subscription is required")
        if len(token) > MAX_TOKEN_CHARS:
            raise ApiError(400, "invalid_request", "bad purchase token")
        products = await active_products(svc, token, now)
        if svc.settings.pro_product_id not in products:
            raise ApiError(403, "pro_required", "a Pro subscription is required")
        if not svc.limiter.allow_daily(f"ai-install:{install_id(request)}", svc.settings.install_daily_ai_limit, now):
            raise ApiError(429, "rate_limited", "daily limit reached")
        if not svc.limiter.allow_daily(f"{kind}:{token_hash(token)}", daily_limit, now):
            raise ApiError(429, "rate_limited", "daily limit reached")

    async def ask_model(svc: Services, messages, **kwargs) -> str:
        try:
            return await svc.ollama.chat(messages, **kwargs)
        except ModelTimeout:
            raise ApiError(504, "model_timeout", "the AI took too long, try again")
        except ModelError as error:
            log.warning("model error: %s", error)
            raise ApiError(502, "model_error", "the AI service failed, try again")

    # ---- Routes ----

    @app.get("/v1/health")
    async def health():
        return {"ok": True}

    @app.post("/v1/subscription/verify", response_model=VerifyResponse)
    async def verify(body: VerifyRequest, request: Request):
        svc = services(request)
        now = svc.clock()
        if body.packageName != svc.settings.package_name or body.productId not in svc.settings.product_ids:
            raise ApiError(400, "invalid_product", "unknown package or product")
        check_ip(svc, request, now)
        if not svc.limiter.allow_hourly(
            f"verify:{install_id(request)}", svc.settings.verify_hourly_limit_per_install, now
        ):
            raise ApiError(429, "rate_limited", "too many requests")
        products = await active_products(svc, body.purchaseToken, now)
        expiry = products.get(body.productId)
        return VerifyResponse(active=expiry is not None, expiresAt=expiry)

    @app.post("/v1/ai/insights", response_model=InsightsResponse)
    async def insights(body: InsightsRequest, request: Request):
        svc = services(request)
        now = svc.clock()
        await authorize_ai(svc, request, "insights", svc.settings.insights_daily_limit, now)
        today = now.date()
        system = prompts.render(
            prompts.INSIGHTS_SYSTEM_PROMPT,
            language=language(request),
            currency=body.summary.currency,
            today=today,
            summary=body.summary.model_dump(),
            facts=build_facts(body.summary, today),
        )
        messages = [
            {"role": "system", "content": system},
            {"role": "user", "content": prompts.INSIGHTS_USER_MESSAGE},
        ]
        # Structured output is reliable but not guaranteed: one retry, then no tips.
        for _ in range(2):
            raw = await ask_model(svc, messages, temperature=0.2, num_predict=700, json_schema=INSIGHTS_JSON_SCHEMA)
            tips = parse_insights(raw)
            if tips is not None:
                return {"insights": tips}
        log.warning("insights: model returned unusable JSON twice")
        return {"insights": []}

    @app.post("/v1/ai/chat", response_model=ChatResponse)
    async def chat(body: ChatRequest, request: Request):
        svc = services(request)
        now = svc.clock()
        message = normalize_message(body.message)
        if not message or len(message) > MAX_MESSAGE_CHARS:
            raise ApiError(400, "invalid_message", f"the question must be 1-{MAX_MESSAGE_CHARS} characters")
        await authorize_ai(svc, request, "chat", svc.settings.chat_daily_limit, now)
        today = now.date()
        system = prompts.render(
            prompts.CHAT_SYSTEM_PROMPT,
            language=language(request),
            currency=body.summary.currency,
            today=today,
            summary=body.summary.model_dump(),
            facts=build_facts(body.summary, today),
        )
        messages = [{"role": "system", "content": system}]
        messages += [{"role": turn.role, "content": turn.text} for turn in body.history]
        messages.append({"role": "user", "content": message})
        reply = clean_reply(await ask_model(svc, messages, temperature=0.3, num_predict=400))
        if not reply:
            raise ApiError(502, "model_error", "the AI returned an empty answer, try again")
        return {"reply": reply}

    return app


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
# httpx logs full request URLs, which include purchase tokens (Google API path).
logging.getLogger("httpx").setLevel(logging.WARNING)
