"""News signals via the Google News RSS endpoint (no API key required)."""

from __future__ import annotations

import urllib.parse
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime

from ..models import Competitor, Signal
from .base import Source

GOOGLE_NEWS_RSS = "https://news.google.com/rss/search"


class NewsSource(Source):
    """Pull recent press coverage for a competitor.

    Uses Google News' public RSS search feed, which needs no credentials. Each
    article becomes a :class:`Signal`; previously-seen articles are filtered out
    via the store so repeated runs only surface what's new.
    """

    name = "news"

    def _feed_url(self, query: str) -> str:
        params = {
            "q": query,
            "hl": self.settings.news_language,
            "gl": self.settings.news_country,
            "ceid": f"{self.settings.news_country}:{self.settings.news_language}",
        }
        return f"{GOOGLE_NEWS_RSS}?{urllib.parse.urlencode(params)}"

    def collect(self, product_key: str, competitor: Competitor) -> list[Signal]:
        url = self._feed_url(competitor.search_query())
        resp = self.http.get(url)
        if resp is None:
            return []

        try:
            root = ET.fromstring(resp.content)
        except ET.ParseError as exc:
            print(f"  ! could not parse news feed for {competitor.name}: {exc}")
            return []

        cutoff = datetime.now(timezone.utc) - timedelta(days=self.settings.lookback_days)
        signals: list[Signal] = []

        for item in root.iterfind(".//item"):
            title = (item.findtext("title") or "").strip()
            link = (item.findtext("link") or "").strip()
            guid = (item.findtext("guid") or link or title).strip()
            published = _parse_date(item.findtext("pubDate"))
            source_name = item.findtext("{*}source") or item.findtext("source") or ""

            if published and published < cutoff:
                continue
            if not guid or self.store.is_seen(competitor.name, guid):
                continue

            self.store.mark_seen(competitor.name, guid)
            signals.append(
                Signal(
                    product_key=product_key,
                    competitor=competitor.name,
                    source=self.name,
                    kind="article",
                    title=title or "(untitled)",
                    url=link or None,
                    summary=source_name.strip() or None,
                    published=published,
                )
            )

        return signals


def _parse_date(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        dt = parsedate_to_datetime(value)
    except (TypeError, ValueError):
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt
