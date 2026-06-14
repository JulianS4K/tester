"""Core data structures shared across the tracker."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional


@dataclass
class Competitor:
    """A single competitor to track.

    Attributes:
        name: Human-readable competitor name (also used as a storage key).
        website: Optional homepage URL to watch for changes.
        news_query: Search string used for news lookups. Defaults to ``name``.
        aliases: Alternate names/brands, folded into the news query with OR.
        tags: Free-form labels (e.g. "enterprise", "open-source").
    """

    name: str
    website: Optional[str] = None
    news_query: Optional[str] = None
    aliases: list[str] = field(default_factory=list)
    tags: list[str] = field(default_factory=list)

    def search_query(self) -> str:
        """Build the query string used when searching for news."""
        if self.news_query:
            return self.news_query
        terms = [self.name, *self.aliases]
        quoted = [f'"{t}"' if " " in t else t for t in terms]
        return " OR ".join(quoted)

    @classmethod
    def from_dict(cls, data: dict) -> "Competitor":
        if isinstance(data, str):
            return cls(name=data)
        return cls(
            name=data["name"],
            website=data.get("website"),
            news_query=data.get("news_query"),
            aliases=list(data.get("aliases", [])),
            tags=list(data.get("tags", [])),
        )


@dataclass
class Product:
    """One of our own products and the competitors we track against it."""

    key: str
    name: str
    competitors: list[Competitor] = field(default_factory=list)

    @classmethod
    def from_dict(cls, key: str, data: dict) -> "Product":
        return cls(
            key=key,
            name=data.get("name", key),
            competitors=[Competitor.from_dict(c) for c in data.get("competitors", [])],
        )


@dataclass
class Signal:
    """A single noteworthy observation about a competitor."""

    product_key: str
    competitor: str
    source: str          # e.g. "news", "website"
    kind: str            # e.g. "article", "website_change"
    title: str
    url: Optional[str] = None
    summary: Optional[str] = None
    published: Optional[datetime] = None
    discovered: datetime = field(default_factory=lambda: datetime.now(timezone.utc))

    def sort_key(self) -> datetime:
        """Most-recent-first ordering, falling back to discovery time."""
        return self.published or self.discovered

    def to_dict(self) -> dict:
        return {
            "product_key": self.product_key,
            "competitor": self.competitor,
            "source": self.source,
            "kind": self.kind,
            "title": self.title,
            "url": self.url,
            "summary": self.summary,
            "published": self.published.isoformat() if self.published else None,
            "discovered": self.discovered.isoformat(),
        }
