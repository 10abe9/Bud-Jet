"""Which subscriptions a purchase token grants, with a cache in front of Google."""

import hashlib
import logging

from .db import CachedSubscription, Database
from .google_play import GooglePlayClient, GooglePlayUnavailable

log = logging.getLogger("budjet.entitlement")

HOUR_MS = 60 * 60 * 1000
# A positive answer is reused this long (and never past the expiry it reported).
POSITIVE_TTL_MS = 6 * HOUR_MS
# A negative answer is reused briefly, so a bad token cannot make us hammer Google.
NEGATIVE_TTL_MS = 10 * 60 * 1000
# When Google is unreachable, an older positive answer still counts for this long.
STALE_GRACE_MS = 72 * HOUR_MS


def token_hash(purchase_token: str) -> str:
    return hashlib.sha256(purchase_token.encode("utf-8")).hexdigest()


class Entitlements:
    def __init__(self, db: Database, google: GooglePlayClient):
        self._db = db
        self._google = google

    async def products(self, purchase_token: str, now_ms: int) -> dict[str, int]:
        """productId -> expiry (ms) of subscriptions that grant access now.

        Raises GooglePlayUnavailable when Google cannot answer and nothing usable is cached.
        """
        key = token_hash(purchase_token)
        cached = self._db.get_subscription(key)
        if cached is not None:
            age = now_ms - cached.checked_at_ms
            live = {p: exp for p, exp in cached.products.items() if exp > now_ms}
            if cached.products and age < POSITIVE_TTL_MS and live:
                return live
            if not cached.products and age < NEGATIVE_TTL_MS:
                return {}

        try:
            check = await self._google.check(purchase_token, now_ms)
        except GooglePlayUnavailable as error:
            if cached is not None and cached.products and now_ms - cached.checked_at_ms < STALE_GRACE_MS:
                log.warning("Google Play unavailable (%s); using cached subscription", error)
                return {p: exp for p, exp in cached.products.items() if exp > now_ms}
            raise

        self._db.put_subscription(
            key, CachedSubscription(found=check.found, products=check.products, checked_at_ms=now_ms)
        )
        return check.products
