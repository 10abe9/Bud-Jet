"""Request limits kept in SQLite (fixed windows: UTC hour or UTC day)."""

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from .db import Database


def hour_period(now: datetime) -> str:
    return now.astimezone(timezone.utc).strftime("%Y-%m-%dT%H")


def day_period(now: datetime) -> str:
    return now.astimezone(timezone.utc).strftime("%Y-%m-%d")


@dataclass
class Charge:
    """Counted requests, so they can be given back if the model fails."""

    entries: list[tuple[str, str]] = field(default_factory=list)


class RateLimiter:
    def __init__(self, db: Database):
        self._db = db
        self._last_cleanup_day: str | None = None

    def allow_hourly(self, key: str, limit: int, now: datetime, charge: Charge | None = None) -> int | None:
        """Requests left in this hour after counting this one, or None when the limit is reached."""
        return self._hit(key, hour_period(now), limit, now, charge)

    def allow_daily(self, key: str, limit: int, now: datetime, charge: Charge | None = None) -> int | None:
        """Requests left today after counting this one, or None when the limit is reached."""
        return self._hit(key, day_period(now), limit, now, charge)

    def refund(self, charge: Charge) -> None:
        for key, period in charge.entries:
            self._db.refund(key, period)
        charge.entries.clear()

    def _hit(self, key: str, period: str, limit: int, now: datetime, charge: Charge | None) -> int | None:
        self._cleanup(now)
        count = self._db.hit(key, period, limit)
        if count is None:
            return None
        if charge is not None:
            charge.entries.append((key, period))
        return limit - count

    def _cleanup(self, now: datetime) -> None:
        """Once a day, drop counters older than yesterday."""
        today = day_period(now)
        if self._last_cleanup_day == today:
            return
        self._last_cleanup_day = today
        self._db.delete_periods_before(day_period(now - timedelta(days=1)))
