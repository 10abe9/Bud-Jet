"""Subscription checks with the Google Play Developer API (purchases.subscriptionsv2.get)."""

import logging
import re
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from datetime import datetime
from urllib.parse import quote

import anyio
import httpx

log = logging.getLogger("budjet.google_play")

API_URL = (
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
    "{package}/purchases/subscriptionsv2/tokens/{token}"
)
SCOPE = "https://www.googleapis.com/auth/androidpublisher"

# CANCELED = auto-renew turned off; the paid period still runs until expiryTime.
ENTITLED_STATES = {
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    "SUBSCRIPTION_STATE_CANCELED",
}


class GooglePlayUnavailable(Exception):
    """Google could not be asked (network, 5xx, credentials). Not the same as "not active"."""


@dataclass
class PlayCheck:
    # False when Google does not know the token (invalid, other app, long expired).
    found: bool
    # productId -> expiry (ms) for line items that currently grant access.
    products: dict[str, int]


def parse_rfc3339_ms(value: str) -> int:
    """Google sends e.g. 2026-10-01T12:00:00.123456789Z; Python takes at most 6 fraction digits."""
    value = re.sub(r"(\.\d{6})\d+", r"\1", value.strip())
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return int(datetime.fromisoformat(value).timestamp() * 1000)


def entitled_products(response: dict, now_ms: int) -> dict[str, int]:
    if response.get("subscriptionState") not in ENTITLED_STATES:
        return {}
    products: dict[str, int] = {}
    for item in response.get("lineItems") or []:
        product_id = item.get("productId")
        expiry = item.get("expiryTime")
        if not product_id or not expiry:
            continue
        try:
            expiry_ms = parse_rfc3339_ms(expiry)
        except ValueError:
            continue
        if expiry_ms > now_ms:
            products[product_id] = max(expiry_ms, products.get(product_id, 0))
    return products


def service_account_token_provider(credentials_path: str) -> Callable[[], Awaitable[str]]:
    """OAuth access token for the service account; refreshed only when expired."""
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account

    credentials = None
    lock = anyio.Lock()

    async def provide() -> str:
        nonlocal credentials
        async with lock:
            try:
                if credentials is None:
                    credentials = service_account.Credentials.from_service_account_file(
                        credentials_path, scopes=[SCOPE]
                    )
                if not credentials.valid:
                    await anyio.to_thread.run_sync(credentials.refresh, Request())
            except Exception as error:  # file missing, bad key, network
                raise GooglePlayUnavailable(f"credentials: {type(error).__name__}") from error
            return credentials.token

    return provide


class GooglePlayClient:
    def __init__(
        self,
        http: httpx.AsyncClient,
        package_name: str,
        token_provider: Callable[[], Awaitable[str]],
    ):
        self._http = http
        self._package = package_name
        self._token_provider = token_provider

    async def check(self, purchase_token: str, now_ms: int) -> PlayCheck:
        access_token = await self._token_provider()
        url = API_URL.format(package=quote(self._package, safe=""), token=quote(purchase_token, safe=""))
        try:
            response = await self._http.get(
                url, headers={"Authorization": f"Bearer {access_token}"}, timeout=10.0
            )
        except httpx.HTTPError as error:
            raise GooglePlayUnavailable(type(error).__name__) from error

        if response.status_code in (400, 404, 410):
            return PlayCheck(found=False, products={})
        if response.status_code in (401, 403):
            # Our service account lacks access: a setup problem, not the user's fault.
            log.error("Google Play API refused access (%s): check service account permissions", response.status_code)
            raise GooglePlayUnavailable(f"http {response.status_code}")
        if response.status_code != 200:
            raise GooglePlayUnavailable(f"http {response.status_code}")
        return PlayCheck(found=True, products=entitled_products(response.json(), now_ms))
