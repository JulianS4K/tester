"""Detect meaningful changes to a competitor's public website."""

from __future__ import annotations

import hashlib
import re
from html.parser import HTMLParser

from ..models import Competitor, Signal
from .base import Source

# Tags whose contents are not user-visible text and only add noise.
_IGNORED_TAGS = {"script", "style", "noscript", "template", "svg"}


class _TextExtractor(HTMLParser):
    """Collect visible text from an HTML document using only the stdlib."""

    def __init__(self) -> None:
        super().__init__()
        self._chunks: list[str] = []
        self._skip_depth = 0

    def handle_starttag(self, tag, attrs):
        if tag in _IGNORED_TAGS:
            self._skip_depth += 1

    def handle_endtag(self, tag):
        if tag in _IGNORED_TAGS and self._skip_depth > 0:
            self._skip_depth -= 1

    def handle_data(self, data):
        if self._skip_depth == 0:
            text = data.strip()
            if text:
                self._chunks.append(text)

    @property
    def text(self) -> str:
        return re.sub(r"\s+", " ", " ".join(self._chunks)).strip()


class WebsiteSource(Source):
    """Fingerprint a competitor's homepage and report when it changes.

    The first run records a baseline; subsequent runs emit a ``website_change``
    signal whenever the visible text fingerprint differs from the last snapshot.
    """

    name = "website"

    def collect(self, product_key: str, competitor: Competitor) -> list[Signal]:
        if not competitor.website:
            return []

        resp = self.http.get(competitor.website)
        if resp is None:
            return []

        parser = _TextExtractor()
        try:
            parser.feed(resp.text)
        except Exception as exc:  # malformed HTML shouldn't kill the run
            print(f"  ! could not parse website for {competitor.name}: {exc}")
            return []

        text = parser.text
        fingerprint = hashlib.sha256(text.encode("utf-8")).hexdigest()
        key = f"{product_key}:{competitor.name}:{competitor.website}"
        previous = self.store.get_snapshot(key)

        self.store.set_snapshot(
            key,
            {"hash": fingerprint, "length": len(text), "url": competitor.website},
        )

        if previous is None:
            # First sighting: establish a baseline, don't cry "change".
            return []
        if previous.get("hash") == fingerprint:
            return []

        delta = len(text) - previous.get("length", len(text))
        summary = f"Homepage content changed ({delta:+d} chars vs. last check)."
        return [
            Signal(
                product_key=product_key,
                competitor=competitor.name,
                source=self.name,
                kind="website_change",
                title=f"{competitor.name} updated their website",
                url=competitor.website,
                summary=summary,
            )
        ]
