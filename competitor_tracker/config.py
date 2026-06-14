"""Loading and validation of the tracker configuration."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import yaml

from .models import Product


@dataclass
class Settings:
    """Top-level configuration loaded from ``config.yaml``."""

    products: dict[str, Product]
    lookback_days: int = 7
    news_language: str = "en"
    news_country: str = "US"
    request_timeout: int = 15
    user_agent: str = (
        "competitor-tracker/0.1 (+https://example.com; research bot)"
    )
    sources: list[str] = field(default_factory=lambda: ["news", "website"])

    @classmethod
    def load(cls, path: str | Path) -> "Settings":
        path = Path(path)
        if not path.exists():
            raise FileNotFoundError(
                f"Config file not found: {path}. Copy config.yaml and edit it."
            )
        raw = yaml.safe_load(path.read_text()) or {}

        products_raw = raw.get("products") or {}
        if not products_raw:
            raise ValueError("Config must define at least one product under 'products'.")

        products = {
            key: Product.from_dict(key, pdata)
            for key, pdata in products_raw.items()
        }

        defaults = raw.get("settings", {})
        return cls(
            products=products,
            lookback_days=int(defaults.get("lookback_days", 7)),
            news_language=defaults.get("news_language", "en"),
            news_country=defaults.get("news_country", "US"),
            request_timeout=int(defaults.get("request_timeout", 15)),
            user_agent=defaults.get("user_agent", cls.user_agent),
            sources=list(defaults.get("sources", ["news", "website"])),
        )

    def select_products(self, keys: list[str] | None) -> list[Product]:
        """Return the requested products, or all of them if ``keys`` is empty."""
        if not keys:
            return list(self.products.values())
        missing = [k for k in keys if k not in self.products]
        if missing:
            known = ", ".join(self.products) or "(none)"
            raise KeyError(f"Unknown product(s): {', '.join(missing)}. Known: {known}")
        return [self.products[k] for k in keys]
