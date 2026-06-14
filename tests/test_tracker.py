"""Tests for the competitor tracker that run fully offline."""

import sys
from datetime import datetime, timezone
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from competitor_tracker.config import Settings
from competitor_tracker.models import Competitor, Signal
from competitor_tracker.report import to_markdown
from competitor_tracker.sources.news import NewsSource
from competitor_tracker.sources.website import WebsiteSource, _TextExtractor
from competitor_tracker.storage import Store


class _FakeResponse:
    def __init__(self, body: str):
        self.content = body.encode("utf-8")
        self.text = body


class _FakeHttp:
    """Stub HTTP client that returns canned bodies instead of hitting network."""

    def __init__(self, body: str):
        self.body = body
        self.calls: list[str] = []

    def get(self, url):
        self.calls.append(url)
        return _FakeResponse(self.body)


class _Settings:
    news_language = "en"
    news_country = "US"
    lookback_days = 3650  # wide window so the canned item isn't filtered out


def test_competitor_search_query_combines_aliases():
    c = Competitor(name="Acme", aliases=["Acme Corp", "ACME Inc"])
    q = c.search_query()
    assert "Acme" in q
    assert '"Acme Corp"' in q
    assert " OR " in q


def test_competitor_custom_news_query_wins():
    c = Competitor(name="Acme", news_query="acme widgets")
    assert c.search_query() == "acme widgets"


def test_competitor_from_dict_accepts_plain_string():
    c = Competitor.from_dict("Acme")
    assert c.name == "Acme"
    assert c.website is None


def test_store_dedupes_and_persists(tmp_path):
    state = tmp_path / "state.json"
    store = Store(state)
    assert not store.is_seen("Acme", "id1")
    store.mark_seen("Acme", "id1")
    assert store.is_seen("Acme", "id1")
    store.save()

    reloaded = Store(state)
    assert reloaded.is_seen("Acme", "id1")


def test_store_snapshot_roundtrip(tmp_path):
    store = Store(tmp_path / "state.json")
    assert store.get_snapshot("k") is None
    store.set_snapshot("k", {"hash": "abc", "length": 10})
    assert store.get_snapshot("k")["hash"] == "abc"


def test_text_extractor_skips_scripts_and_styles():
    html = """
        <html><head><style>.a{color:red}</style></head>
        <body><h1>Hello</h1><script>var x = 1;</script><p>World</p></body></html>
    """
    parser = _TextExtractor()
    parser.feed(html)
    text = parser.text
    assert "Hello" in text and "World" in text
    assert "color:red" not in text
    assert "var x" not in text


def test_settings_load_and_select(tmp_path):
    cfg = tmp_path / "config.yaml"
    cfg.write_text(
        """
settings:
  lookback_days: 3
products:
  d1:
    name: "D1"
    competitors:
      - name: "Foo"
        website: "https://foo.example"
  d4:
    name: "D4"
    competitors:
      - "Bar"
"""
    )
    settings = Settings.load(cfg)
    assert settings.lookback_days == 3
    assert set(settings.products) == {"d1", "d4"}
    assert len(settings.select_products(None)) == 2
    assert settings.select_products(["d4"])[0].key == "d4"
    with pytest.raises(KeyError):
        settings.select_products(["nope"])


def test_settings_requires_products(tmp_path):
    cfg = tmp_path / "config.yaml"
    cfg.write_text("settings:\n  lookback_days: 1\n")
    with pytest.raises(ValueError):
        Settings.load(cfg)


def test_report_markdown_groups_by_product():
    signals = [
        Signal(
            product_key="d1", competitor="Foo", source="news", kind="article",
            title="Foo raises money", url="https://x.example",
            published=datetime(2026, 6, 1, tzinfo=timezone.utc),
        ),
        Signal(
            product_key="d4", competitor="Bar", source="website",
            kind="website_change", title="Bar updated their website",
        ),
    ]
    md = to_markdown(signals, {"d1": "D1", "d4": "D4"})
    assert "# Competitor Tracking Report" in md
    assert "## D1" in md and "## D4" in md
    assert "Foo raises money" in md


def test_report_markdown_handles_empty():
    md = to_markdown([], {"d1": "D1"})
    assert "No new signals" in md


SAMPLE_RSS = """<?xml version="1.0"?>
<rss version="2.0"><channel>
  <item>
    <title>Acme launches new widget</title>
    <link>https://news.example/acme-widget</link>
    <guid>https://news.example/acme-widget</guid>
    <pubDate>Mon, 01 Jun 2026 12:00:00 GMT</pubDate>
    <source url="https://techsite.example">TechSite</source>
  </item>
</channel></rss>"""


def test_news_source_parses_and_dedupes(tmp_path):
    store = Store(tmp_path / "state.json")
    http = _FakeHttp(SAMPLE_RSS)
    src = NewsSource(http, store, _Settings())
    competitor = Competitor(name="Acme")

    first = src.collect("d1", competitor)
    assert len(first) == 1
    sig = first[0]
    assert sig.title == "Acme launches new widget"
    assert sig.url == "https://news.example/acme-widget"
    assert sig.summary == "TechSite"
    assert sig.published is not None

    # Second pass: same item is already seen, so nothing new is reported.
    assert src.collect("d1", competitor) == []


def test_website_source_baseline_then_change(tmp_path):
    store = Store(tmp_path / "state.json")
    competitor = Competitor(name="Acme", website="https://acme.example")

    src1 = WebsiteSource(_FakeHttp("<html><body><p>Version one</p></body></html>"), store, _Settings())
    assert src1.collect("d1", competitor) == []  # baseline, no signal

    src2 = WebsiteSource(_FakeHttp("<html><body><p>Version TWO is different</p></body></html>"), store, _Settings())
    changed = src2.collect("d1", competitor)
    assert len(changed) == 1
    assert changed[0].kind == "website_change"

    # Unchanged content on the next run -> no signal.
    src3 = WebsiteSource(_FakeHttp("<html><body><p>Version TWO is different</p></body></html>"), store, _Settings())
    assert src3.collect("d1", competitor) == []
