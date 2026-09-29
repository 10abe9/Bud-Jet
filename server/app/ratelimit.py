"""Request limits kept in SQLite (fixed windows: UTC hour or UTC day)."""

from datetime import datetime, timedelta, timezone

from .db import Database


def hour_period(now: datetime) -> str:
    return now.astimezone(timezone.utc).strftime("%Y-%m-%dT%H")


def day_period(now: datetime) -> str:
    return now.astimezone(timezone.utc).strftime("%Y-%m-%d")


class RateLimiter:
    def __init__(self, db: Database):
        self._db = db
        self._last_cleanup_day: str | None = None

    def allow_hourly(self, key: str, limit: int, now: datetime) -> bool:
        self._cleanup(now)
        return self._db.hit(key, hour_period(now), limit)

    def allow_daily(self, key: str, limit: int, now: datetime) -> bool:
        self._cleanup(now)
        return self._db.hit(key, day_period(now), limit)

    def _cleanup(self, now: datetime) -> None:
        """Once a day, drop counters older than yesterday."""
        today = day_period(now)
        if self._last_cleanup_day == today:
            return
        self._last_cleanup_day = today
        self._db.delete_periods_before(day_period(now - timedelta(days=1)))
