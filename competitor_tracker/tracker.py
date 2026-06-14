"""Orchestrates sources across products and competitors."""

from __future__ import annotations

from .config import Settings
from .http import HttpClient
from .models import Product, Signal
from .sources import REGISTRY
from .storage import Store


class Tracker:
    """Run the configured sources over a set of products and gather signals."""

    def __init__(self, settings: Settings, store: Store):
        self.settings = settings
        self.store = store
        self.http = HttpClient(
            user_agent=settings.user_agent,
            timeout=settings.request_timeout,
        )
        self.sources = self._build_sources(settings.sources)

    def _build_sources(self, names: list[str]):
        sources = []
        for name in names:
            cls = REGISTRY.get(name)
            if cls is None:
                print(f"  ! unknown source '{name}' (known: {', '.join(REGISTRY)})")
                continue
            sources.append(cls(self.http, self.store, self.settings))
        return sources

    def run(self, products: list[Product]) -> list[Signal]:
        """Collect signals for every competitor of the given products."""
        all_signals: list[Signal] = []
        for product in products:
            print(f"\n== {product.name} ({product.key}) ==")
            for competitor in product.competitors:
                print(f"  - {competitor.name}")
                for source in self.sources:
                    try:
                        found = source.collect(product.key, competitor)
                    except Exception as exc:  # one bad source shouldn't abort all
                        print(f"    ! {source.name} failed: {exc}")
                        continue
                    if found:
                        print(f"    + {len(found)} {source.name} signal(s)")
                    all_signals.extend(found)
        all_signals.sort(key=lambda s: s.sort_key(), reverse=True)
        return all_signals

    def close(self) -> None:
        self.http.close()
        self.store.save()
