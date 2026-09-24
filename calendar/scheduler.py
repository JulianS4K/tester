#!/usr/bin/env python3
"""
The scheduler: one cycle from sources to a reviewed calendar proposal.

It sits on top of the pieces that already exist rather than replacing them --
`ingest.py` turns mail into candidates, `poll_venues.py` parses a fetched
venue page, `sync.py` plans the push to Google. This module is the part that
decides WHAT SHOULD BE ON THE CALENDAR AT ALL, which none of those do.

THE CONSTRAINT THAT SHAPES EVERYTHING HERE: the calendar has two authors.
Julian edits it by hand, continuously, while this runs. On 2026-09-23 five
created events vanished within minutes; that was read as a flaky API and all
five were recreated, silently putting back things he had deliberately
deleted. A scheduler that re-proposes what its user removed does not degrade
gracefully -- it fights him, and it wins, because it never gets bored. So
deletion is durable state (`tombstones.yml`) and is checked before anything
is proposed, not after.

Commands:

    gaps      which cadence slots are unmet, week by week
    rank      score candidates against slot need, affinity and the rest day
    cycle     gaps -> rank -> write proposals.json for review
    tombstone record a deletion so it is never proposed again

`cycle` never writes to Google. It produces proposals; an agent holding
OAuth applies the ones Julian approves, exactly as `sync.py` already works.
"""

import argparse
import datetime as dt
import json
import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent
CADENCE_PATH = ROOT / "cadence.yml"
TOMBSTONES_PATH = ROOT / "tombstones.yml"
SOURCES_PATH = ROOT / "sources.yml"
VENUES_PATH = ROOT / "venues.yml"
PROPOSALS_PATH = ROOT / "scheduler_proposals.json"

_PUNCT = re.compile(r"[^a-z0-9 ]+")
_SPACE = re.compile(r"\s+")


def _load(path):
    return yaml.safe_load(path.read_text()) or {}


def fingerprint(summary, start):
    """Stable identity for an event across re-proposals.

    Deliberately NOT the Google event id: a re-proposed event gets a fresh
    id, so an id-keyed tombstone would miss the exact case it exists for.
    Title plus date survives that, and survives the small title edits
    (punctuation, a trailing venue) that sources make between announcements.
    """
    text = _PUNCT.sub(" ", (summary or "").lower())
    text = _SPACE.sub(" ", text).strip()
    return f"{text}|{(start or '')[:10]}"


def load_tombstones():
    rows = _load(TOMBSTONES_PATH).get("tombstones") or []
    return {r["fingerprint"] for r in rows if r.get("fingerprint")}


def week_of(date_str):
    """ISO date -> the Monday of its week, as a date string."""
    d = dt.date.fromisoformat(date_str[:10])
    return (d - dt.timedelta(days=d.weekday())).isoformat()


# --------------------------------------------------------------------------
# slot classification
# --------------------------------------------------------------------------

def _slot_index():
    """Two separate maps, because they are not equally trustworthy.

    A venue is a strong signal -- Saint Vitus is a music room whatever is on.
    An ORGANIZER is weak: Pagans Paradise runs kink parties and a yoga class,
    Brooklyn Comedy Collective runs improv classes and the shows those classes
    put on. Folding both into one dict made the organizer win by accident of
    ordering, which put Naked Yoga in kink-community and a comedy show in
    learning. They are returned apart so precedence can be explicit.
    """
    venues, orgs = {}, {}
    for v in _load(VENUES_PATH).get("venues") or []:
        if v.get("slot"):
            venues[v["name"].lower()] = v["slot"]
    for slot, spec in (_load(SOURCES_PATH).get("slots") or {}).items():
        for src in spec.get("sources") or []:
            org = (src.get("organizer") or "").lower()
            if not org:
                continue
            # An organizer listed under several slots resolves by whichever
            # was iterated first, which is arbitrary. `classifies_as` on the
            # source entry settles it explicitly and always wins.
            hint = src.get("classifies_as")
            if hint:
                orgs[org] = hint
            else:
                orgs.setdefault(org, slot)
    return {"venues": venues, "orgs": orgs}


# Title keywords, checked BEFORE the registries. The title says what the
# event is; a venue says where, and an organizer only says who booked it.
# Order matters within the list -- "class show" is a performance you watch,
# so it has to be tested before the bare "class" that means a course.
_KEYWORDS = [
    ("screen-page", ("class show", "film", "movie", "screening", "reading",
                     "book launch", "comedy", "puppet", "museum", "trivia")),
    ("physical", ("yoga", "boxing", "pilates", "pilatease", "soccer", "wrestling",
                  "run club", "gym", "fitness", "dance release")),
    ("learning", ("improv level", "academy", "workshop", "lesson", "course",
                  "101", "intro to", " class")),
    ("kink-community", ("kinky", "kink", "play party", "rope jam", "munch",
                        "mixer", "speed dating", "sparkle", "fetish", "tantric",
                        "poly ", "open house")),
    ("live-music", (" @ ", "presents", "tour", "fest", " dj ")),
    ("spectator-sport", ("tv taping", "match", "vs ")),
]


def classify_slot(summary, location="", index=None):
    """Slot for an event, most trustworthy signal first.

    Title keywords, then venue, then organizer. An event is what its title
    says it is; the organizer is the last resort because the ones here run
    programmes that span slots.
    """
    index = index if index is not None else _slot_index()
    title = (summary or "").lower()
    hay = f"{title} {(location or '').lower()}"
    for slot, words in _KEYWORDS:
        if any(w in title for w in words):
            return slot
    for name, slot in index["venues"].items():
        if name and name in hay:
            return slot
    for name, slot in index["orgs"].items():
        if name and name in hay:
            return slot
    return "unclassified"


# --------------------------------------------------------------------------
# gaps
# --------------------------------------------------------------------------

def expand_recurring(scheduled, weeks):
    """Expand collapsed recurring series into one event per week in range.

    The store keeps a recurring series as a SINGLE row -- `sync.py import`
    collapses instances deliberately, so a push cannot create N standalone
    copies. Counting that row once makes a weekly anchor look like it happens
    once, which would have the scheduler proposing a gym session into every
    week boxing already covers. A `recurs` block on the row is what lets this
    read the series back out; rows without one pass through untouched.

    Feeding this a `list_events` dump instead is also fine and needs no
    annotation, because Google returns instances already.
    """
    if not weeks:
        return list(scheduled)
    window_end = dt.date.fromisoformat(weeks[-1]) + dt.timedelta(days=6)
    out = []
    for ev in scheduled:
        rec = ev.get("recurs")
        if not rec or rec.get("freq") != "weekly":
            out.append(ev)
            continue
        cur = dt.date.fromisoformat(ev["start"][:10])
        until = dt.date.fromisoformat(rec["until"]) if rec.get("until") else window_end
        last = min(until, window_end)
        while cur <= last:
            out.append({**ev, "start": cur.isoformat() + ev["start"][10:]})
            cur += dt.timedelta(days=7)
    return out


def compute_gaps(scheduled, weeks):
    """Per-week slot coverage against cadence targets.

    `scheduled` is [{summary, start, location}] already on a calendar.
    Returns one row per week with what is met, what is short, and whether a
    rest day survives -- the rest day is a ceiling, so a week without one is
    reported as FULL and takes no proposals at all.
    """
    cadence = _load(CADENCE_PATH)
    slots = cadence.get("slots") or {}
    index = _slot_index()

    by_week = {}
    for ev in expand_recurring(scheduled, weeks):
        wk = week_of(ev["start"])
        slot = classify_slot(ev.get("summary"), ev.get("location"), index)
        day = dt.date.fromisoformat(ev["start"][:10]).isoformat()
        b = by_week.setdefault(wk, {"counts": {}, "days": set(), "events": 0})
        b["counts"][slot] = b["counts"].get(slot, 0) + 1
        b["days"].add(day)
        b["events"] += 1

    rows = []
    for wk in weeks:
        b = by_week.get(wk, {"counts": {}, "days": set(), "events": 0})
        free_days = 7 - len(b["days"])
        short = {}
        for slot, spec in slots.items():
            want = spec.get("target_per_week", 0)
            every_n = spec.get("every_n_weeks")
            if every_n:
                # A fortnightly slot is satisfied by the pair of weeks, so it
                # is only short when the previous week missed it too.
                prev = by_week.get(
                    (dt.date.fromisoformat(wk) - dt.timedelta(days=7)).isoformat(),
                    {"counts": {}},
                )
                if prev["counts"].get(slot, 0) > 0:
                    want = 0
            have = b["counts"].get(slot, 0)
            if have < want:
                short[slot] = want - have
        rows.append({
            "week": wk,
            "events": b["events"],
            "counts": b["counts"],
            "free_days": free_days,
            "rest_day_intact": free_days >= 1,
            "short": short,
            "full": free_days <= 1,
        })
    return rows


# --------------------------------------------------------------------------
# ranking
# --------------------------------------------------------------------------

def rank_candidates(candidates, gaps, tombstones, scheduled_fps):
    """Score candidates. Returns (ranked, rejected-with-reason).

    Rejection is separated from low scores on purpose: a tombstoned event is
    not a weak suggestion, it is one Julian already answered, and burying it
    at rank 40 would still surface it eventually.
    """
    gap_by_week = {g["week"]: g for g in gaps}
    affinity = {}
    for v in _load(VENUES_PATH).get("venues") or []:
        affinity[v["name"].lower()] = v.get("tickets", 0)
    index = _slot_index()

    ranked, rejected = [], []
    for c in candidates:
        fp = fingerprint(c.get("summary"), c.get("start"))
        if fp in tombstones:
            rejected.append({**c, "reason": "tombstoned -- deleted by hand"})
            continue
        if fp in scheduled_fps:
            rejected.append({**c, "reason": "already on a calendar"})
            continue
        wk = week_of(c["start"])
        g = gap_by_week.get(wk)
        if g is None:
            rejected.append({**c, "reason": "outside the planning window"})
            continue
        if g["full"]:
            rejected.append({**c, "reason": "week has no rest day left"})
            continue

        slot = c.get("slot") or classify_slot(c.get("summary"), c.get("location"), index)
        score = 0
        need = g["short"].get(slot, 0)
        score += need * 100                       # an unmet slot dominates
        if need == 0:
            score -= 10                           # already covered this week
        for venue, tickets in affinity.items():
            if venue and venue in f"{c.get('summary','')} {c.get('location','')}".lower():
                score += min(tickets, 10) * 2     # revealed preference, capped
                break
        score += max(0, 5 - g["events"])          # prefer emptier weeks
        ranked.append({**c, "slot": slot, "week": wk, "score": score,
                       "fills": slot if need else None})

    ranked.sort(key=lambda r: (-r["score"], r["start"]))
    return ranked, rejected


# --------------------------------------------------------------------------
# commands
# --------------------------------------------------------------------------

def _weeks_from(start, n):
    first = dt.date.fromisoformat(week_of(start))
    return [(first + dt.timedelta(days=7 * i)).isoformat() for i in range(n)]


def cmd_gaps(args):
    scheduled = json.loads(pathlib.Path(args.scheduled).read_text())
    weeks = _weeks_from(args.start, args.weeks)
    for g in compute_gaps(scheduled, weeks):
        rest = "rest ok" if g["rest_day_intact"] else "NO REST DAY"
        short = ", ".join(f"{k} -{v}" for k, v in g["short"].items()) or "-"
        print(f"{g['week']}  {g['events']:2d} ev  {g['free_days']} free  "
              f"{rest:11}  short: {short}")


def cmd_cycle(args):
    scheduled = json.loads(pathlib.Path(args.scheduled).read_text())
    candidates = json.loads(pathlib.Path(args.candidates).read_text())
    weeks = _weeks_from(args.start, args.weeks)
    gaps = compute_gaps(scheduled, weeks)
    tombstones = load_tombstones()
    scheduled_fps = {fingerprint(e.get("summary"), e.get("start")) for e in scheduled}

    ranked, rejected = rank_candidates(candidates, gaps, tombstones, scheduled_fps)
    out = {
        "generated": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "window": {"start": weeks[0], "weeks": args.weeks},
        "gaps": gaps,
        "propose": ranked[: args.limit],
        "held_back": ranked[args.limit:],
        "rejected": rejected,
    }
    PROPOSALS_PATH.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n")
    print(f"proposals: {len(out['propose'])} to review, "
          f"{len(out['held_back'])} held back, {len(rejected)} rejected "
          f"({sum(1 for r in rejected if 'tombstoned' in r['reason'])} tombstoned)")
    print(f"written to {PROPOSALS_PATH.name}")


def cmd_tombstone(args):
    doc = _load(TOMBSTONES_PATH)
    rows = doc.setdefault("tombstones", [])
    have = {r["fingerprint"] for r in rows}
    added = 0
    for spec in args.event:
        summary, _, start = spec.rpartition("@")
        fp = fingerprint(summary or spec, start)
        if fp not in have:
            rows.append({"fingerprint": fp, "source": args.source})
            have.add(fp)
            added += 1
    TOMBSTONES_PATH.write_text(yaml.safe_dump(doc, sort_keys=False, allow_unicode=True))
    print(f"tombstoned {added} event(s); {len(rows)} total")


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    g = sub.add_parser("gaps", help="slot coverage vs cadence targets")
    g.add_argument("scheduled")
    g.add_argument("--start", default=dt.date.today().isoformat())
    g.add_argument("--weeks", type=int, default=6)
    g.set_defaults(func=cmd_gaps)

    c = sub.add_parser("cycle", help="gaps + rank -> proposals.json")
    c.add_argument("scheduled")
    c.add_argument("candidates")
    c.add_argument("--start", default=dt.date.today().isoformat())
    c.add_argument("--weeks", type=int, default=6)
    c.add_argument("--limit", type=int, default=8)
    c.set_defaults(func=cmd_cycle)

    t = sub.add_parser("tombstone", help="record a deletion; never propose again")
    t.add_argument("event", nargs="+", help='"Title@YYYY-MM-DD"')
    t.add_argument("--source", default="manual")
    t.set_defaults(func=cmd_tombstone)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    sys.exit(main())
