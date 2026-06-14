"""Pluggable data sources for competitor signals."""

from .base import Source
from .news import NewsSource
from .website import WebsiteSource

# Registry used by the tracker to instantiate sources by name from config.
REGISTRY: dict[str, type[Source]] = {
    "news": NewsSource,
    "website": WebsiteSource,
}

__all__ = ["Source", "NewsSource", "WebsiteSource", "REGISTRY"]
