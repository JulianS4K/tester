#!/usr/bin/env python3
"""
Parse a venue page fetched by somebody else into normalised events.

Same seam as the rest of the store (`sources.py`): this repo has no egress,
so fetching happens elsewhere -- in practice a Zapier "Webhooks GET" call
through MCP, which reaches sites this sandbox cannot -- and the HTML lands in
`calendar/.snapshots/`. This module only parses.

DICE embeds a JSON-LD Event list in the venue page, so one fetch yields the
whole calendar. Eventbrite organizer pages do not: they render client-side and
expose only event slugs, so `parse_eventbrite_slugs` returns links for a
second hop rather than pretending to return events.
"""

import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent
SNAPSHOT_DIR = ROOT / ".snapshots"

# The payload arrives double-encoded: Zapier wraps the page in JSON, and the
# page's own JSON-LD is escaped inside that. Unescaping with json.loads on a
# quoted span handles / and friends, which a chain of str.replace calls
# silently does not -- venue names came through as "Silvera // ..."
# until this went in.
_EVENT_MARK = re.compile(r'"@type":\s*"Event"')
_START = re.compile(r'"startDate":\s*"([0-9T:+\-]{16,25})"')
_NAME = re.compile(r'"name":\s*("(?:[^"\\]|\\.)*")')


def _unwrap(raw):
    """Peel Zapier's JSON envelope off, if present."""
    try:
        return json.loads(raw)["results"][0]["text"]
    except Exception:
        return raw


def parse_dice(html):
    """Return [{start, name}] from a DICE venue page, sorted and deduped."""
    html = _unwrap(html)
    found = {}
    for mark in _EVENT_MARK.finditer(html):
        window = html[mark.start():mark.start() + 1200]
        start = _START.search(window)
        if not start:
            continue
        name = _NAME.search(window)
        title = json.loads(name.group(1)) if name else "?"
        found[(start.group(1)[:16], title)] = None
    return [{"start": s, "name": n} for s, n in sorted(found)]


def parse_eventbrite_slugs(html):
    """Return event slugs only. Eventbrite organizer pages carry no dates."""
    html = _unwrap(html)
    return sorted(set(re.findall(r"eventbrite\.com/e/([a-z0-9\-]{10,120})", html)))


def load(name):
    path = SNAPSHOT_DIR / name
    if not path.exists():
        raise FileNotFoundError(
            f"no snapshot at {path}. Nothing here can fetch it -- there is no "
            f"egress to the venue. Fetch it via Zapier and drop the file in."
        )
    return path.read_text(encoding="utf-8", errors="replace")


if __name__ == "__main__":
    import sys

    for arg in sys.argv[1:]:
        events = parse_dice(pathlib.Path(arg).read_text(errors="replace"))
        print(f"{arg}: {len(events)} events")
        for e in events:
            print(f"  {e['start'].replace('T', '  ')}   {e['name']}")
