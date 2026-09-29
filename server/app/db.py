"""SQLite storage: request counters and the subscription-check cache.

Nothing about the user's budget, questions or answers is stored. Purchase tokens are stored
only as sha256 hashes.
"""

import json
import sqlite3
import threading
from dataclasses import dataclass
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS rate_counters (
    key TEXT NOT NULL,
    period TEXT NOT NULL,
    count INTEGER NOT NULL,
    PRIMARY KEY (key, period)
);
CREATE TABLE IF NOT EXISTS subscription_cache (
    token_hash TEXT PRIMARY KEY,
    found INTEGER NOT NULL,
    products TEXT NOT NULL,
    checked_at_ms INTEGER NOT NULL
);
"""


@dataclass
class CachedSubscription:
    found: bool
    # productId -> expiry time (ms) of line items that grant access.
    products: dict[str, int]
    checked_at_ms: int


class Database:
    def __init__(self, path: str):
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        # Calls are tiny and local; a lock keeps them safe across threads.
        self._conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._lock = threading.Lock()
        with self._lock:
            if path != ":memory:":
                self._conn.execute("PRAGMA journal_mode=WAL")
            self._conn.executescript(SCHEMA)

    def hit(self, key: str, period: str, limit: int) -> bool:
        """Counts one request; returns False (and does not count) when the limit is reached."""
        with self._lock:
            row = self._conn.execute(
                "SELECT count FROM rate_counters WHERE key = ? AND period = ?", (key, period)
            ).fetchone()
            count = row[0] if row else 0
            if count >= limit:
                return False
            self._conn.execute(
                "INSERT INTO rate_counters (key, period, count) VALUES (?, ?, 1) "
                "ON CONFLICT (key, period) DO UPDATE SET count = count + 1",
                (key, period),
            )
            return True

    def delete_periods_before(self, oldest_period_to_keep: str) -> None:
        """Periods are "YYYY-MM-DD" or "YYYY-MM-DDTHH", so string order is time order."""
        with self._lock:
            self._conn.execute("DELETE FROM rate_counters WHERE period < ?", (oldest_period_to_keep,))

    def get_subscription(self, token_hash: str) -> CachedSubscription | None:
        with self._lock:
            row = self._conn.execute(
                "SELECT found, products, checked_at_ms FROM subscription_cache WHERE token_hash = ?",
                (token_hash,),
            ).fetchone()
        if row is None:
            return None
        return CachedSubscription(found=bool(row[0]), products=json.loads(row[1]), checked_at_ms=row[2])

    def put_subscription(self, token_hash: str, value: CachedSubscription) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT INTO subscription_cache (token_hash, found, products, checked_at_ms) "
                "VALUES (?, ?, ?, ?) ON CONFLICT (token_hash) DO UPDATE SET "
                "found = excluded.found, products = excluded.products, checked_at_ms = excluded.checked_at_ms",
                (token_hash, int(value.found), json.dumps(value.products), value.checked_at_ms),
            )

    def close(self) -> None:
        with self._lock:
            self._conn.close()
