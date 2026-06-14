"""Persistent state so the tracker can report only *new* signals over time."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


class Store:
    """A tiny JSON-backed key/value store.

    Holds two kinds of state:
      * ``seen``  - ids of news items already reported (for de-duplication).
      * ``snapshots`` - per-competitor website fingerprints (for change detection).
    """

    def __init__(self, path: str | Path):
        self.path = Path(path)
        self._data: dict[str, Any] = {"seen": {}, "snapshots": {}}
        if self.path.exists():
            try:
                self._data = json.loads(self.path.read_text())
            except (json.JSONDecodeError, OSError):
                pass  # corrupt/unreadable state -> start fresh
        self._data.setdefault("seen", {})
        self._data.setdefault("snapshots", {})

    # --- news de-duplication ------------------------------------------------
    def is_seen(self, competitor: str, item_id: str) -> bool:
        return item_id in self._data["seen"].get(competitor, [])

    def mark_seen(self, competitor: str, item_id: str) -> None:
        bucket = self._data["seen"].setdefault(competitor, [])
        if item_id not in bucket:
            bucket.append(item_id)
            # Keep the de-dupe list from growing without bound.
            if len(bucket) > 500:
                del bucket[:-500]

    # --- website snapshots --------------------------------------------------
    def get_snapshot(self, key: str) -> dict | None:
        return self._data["snapshots"].get(key)

    def set_snapshot(self, key: str, snapshot: dict) -> None:
        self._data["snapshots"][key] = snapshot

    # --- persistence --------------------------------------------------------
    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self._data, indent=2, default=str))
