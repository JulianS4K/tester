#!/usr/bin/env python3
"""
Source registry for the calendar store.

Every source follows the same shape as the email pipeline: something outside
this repo fetches, and drops a JSON snapshot into `calendar/.snapshots/`; the
Python side reads the file. That is not a stylistic choice -- this environment
has NO egress to any of these APIs (api.trakt.tv, ws.audioscrobbler.com and
api.themoviedb.org all fail to connect), so a source that calls out directly
cannot work here at all. Fetching is somebody else's job; parsing is ours.

A source contributes one or both of:

  events    -> proposed calendar entries, deduped and pushed via sync.py
  affinity  -> weighted taste signal, consumed by the weekly brief when it
               ranks nearby events. NOT calendar entries.

That split matters. A contract where every source emits events would be wrong
for Last.fm: scrobbles are not things to put on a calendar, they are evidence
about which of the things already on offer are worth going to.
"""

import json
import pathlib
from dataclasses import dataclass, field

ROOT = pathlib.Path(__file__).resolve().parent
SNAPSHOT_DIR = ROOT / ".snapshots"


@dataclass
class Source:
    """Declares what a source is and what it can contribute.

    `emits_events` / `emits_affinity` are declared up front so the brief can
    reason about coverage ("no affinity source is wired, so ranking is by the
    static taste list") without importing every adapter.
    """

    key: str
    name: str
    snapshot: str
    emits_events: bool
    emits_affinity: bool
    status: str                      # "live" | "stub"
    notes: str = ""
    auth_env: list = field(default_factory=list)

    def snapshot_path(self):
        return SNAPSHOT_DIR / self.snapshot

    def load_snapshot(self):
        p = self.snapshot_path()
        if not p.exists():
            raise FileNotFoundError(
                f"{self.key}: no snapshot at {p}. Nothing in this repo can fetch "
                f"it -- there is no egress to the source API. Drop the file in "
                f"and re-run."
            )
        return json.loads(p.read_text())

    def to_events(self, snapshot):
        raise NotImplementedError(f"{self.key} emits no events")

    def to_affinity(self, snapshot):
        raise NotImplementedError(f"{self.key} emits no affinity")


# --------------------------------------------------------------------------
# email -- LIVE. Implemented in ingest.py; declared here so the registry is
# a complete picture rather than a list of the things that don't work yet.
# --------------------------------------------------------------------------

EMAIL = Source(
    key="email",
    name="Gmail",
    snapshot="emails.json",
    emits_events=True,
    emits_affinity=False,
    status="live",
    notes=(
        "See ingest.py + ingest_rules.yml. Classifies on sender AND subject "
        "shape; marketing outnumbers real purchases ~10:1 so sender alone is "
        "useless. Emits no affinity: a ticket confirmation says what he "
        "committed to, not what he likes."
    ),
)


# --------------------------------------------------------------------------
# trakt.tv -- STUB
# --------------------------------------------------------------------------

class TraktSource(Source):
    """Film and TV. Emits BOTH events and affinity.

    Events: Julian already keeps film releases on the Pending calendar by hand
    (Dune: Part Three, Clayface, Digger). `/calendars/my/movies/{start}/{days}`
    returns exactly those -- releases for things on his watchlist -- so this
    replaces manual entry rather than adding a new category.

    Affinity: watched genres sharpen the "cult / underground / macabre" line in
    the brief's taste filter, which is currently a static string.

    API (verified 2026-09-10):
      base    https://api.trakt.tv
      headers trakt-api-version: 2, trakt-api-key: <client id>,
              Authorization: Bearer <token> for anything under /sync or
              /calendars/my
      GET /calendars/my/movies/{start_date}/{days}   OAuth required
      GET /calendars/my/shows/{start_date}/{days}    OAuth required
      GET /sync/watchlist/movies                     OAuth required

    Auth already exists in this repo: pi/server/auth.js runs the Trakt device
    flow and persists tokens to disk; pi/server/trakt.js wraps the fetch. The
    cheapest path to a snapshot is a small export command in pi/ that writes
    calendar/.snapshots/trakt.json -- it already holds valid tokens, and this
    container cannot reach api.trakt.tv at all.
    """

    def to_events(self, snapshot):
        raise NotImplementedError(
            "trakt: not wired. Expected snapshot shape is a list of "
            "{title, year, released, ids:{trakt,slug,imdb,tmdb}, genres[]} — "
            "matching pi/server/trakt.js toItem(). Map released -> an all-day "
            "event on the Pending calendar, uid f'trakt-{ids.slug}'."
        )

    def to_affinity(self, snapshot):
        raise NotImplementedError(
            "trakt: not wired. Aggregate genres[] across watched history into "
            "{genre: weight}."
        )


TRAKT = TraktSource(
    key="trakt",
    name="Trakt.tv",
    snapshot="trakt.json",
    emits_events=True,
    emits_affinity=True,
    status="stub",
    auth_env=["TRAKT_CLIENT_ID", "TRAKT_CLIENT_SECRET"],
    notes="Auth + fetch already implemented in pi/server/. Needs an export command.",
)


# --------------------------------------------------------------------------
# last.fm -- STUB
# --------------------------------------------------------------------------

class LastfmSource(Source):
    """Music. Emits affinity ONLY -- deliberately no events.

    This is the highest-value source for the weekly brief and the one most
    likely to be mis-wired. Scrobbles are not calendar entries; nothing here
    belongs on a calendar. What they give is evidence: the brief currently
    ranks nearby shows against a hand-written taste list ("metal, punk,
    darkwave, doom"). Top artists turn that into a measurement, and let the
    brief say the thing that actually changes a decision -- "you played this
    band 40 times last month and they are at Saint Vitus in three weeks".

    That also patches the terminal feed's blind spot: the broker terminal has
    zero coverage of small rooms, so artist affinity has to drive the
    web-search discovery instead.

    API (verified 2026-09-10):
      base   https://ws.audioscrobbler.com/2.0/
      auth   api_key query param; format=json. No OAuth for read methods.
      user.getTopArtists  — user, limit (default 50), page,
                            period: overall|7day|1month|3month|6month|12month
      user.getRecentTracks— user, limit (default 50, MAX 200), page,
                            from (UNIX ts, UTC), extended (0|1)
                            includes the now-playing track when listening

    Paging is the trap: limit caps at 200, so any window longer than a few days
    needs page-walking, and `from` must be a UTC UNIX timestamp.
    """

    def to_affinity(self, snapshot):
        raise NotImplementedError(
            "lastfm: not wired. Expected snapshot shape is "
            "{topartists:{artist:[{name, playcount}]}}. Normalise playcount to "
            "a 0-1 weight per artist; the brief multiplies a candidate event's "
            "score by the weight of its performer."
        )


LASTFM = LastfmSource(
    key="lastfm",
    name="Last.fm",
    snapshot="lastfm.json",
    emits_events=False,
    emits_affinity=True,
    status="stub",
    auth_env=["LASTFM_API_KEY", "LASTFM_USER"],
    notes="Read-only API key, no OAuth. Affinity only — never calendar events.",
)


REGISTRY = {s.key: s for s in (EMAIL, TRAKT, LASTFM)}


def summary():
    rows = []
    for s in REGISTRY.values():
        emits = ", ".join(
            [x for x, on in (("events", s.emits_events), ("affinity", s.emits_affinity)) if on]
        ) or "nothing"
        present = "present" if s.snapshot_path().exists() else "absent"
        rows.append(f"  {s.key:<8} {s.status:<5} emits {emits:<18} snapshot {present}")
    return "\n".join(rows)


if __name__ == "__main__":
    print("calendar sources\n" + summary())
