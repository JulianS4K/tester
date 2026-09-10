#!/usr/bin/env python3
"""
Classify event-bearing email and report what is missing from the calendar.

Like sync.py, this never touches the network. It takes a JSON dump of email
metadata (sender/subject/date/body), applies ingest_rules.yml, and reports:

  confirmed + already on a calendar  -> nothing to do
  confirmed + NOT on a calendar      -> a proposal for review
  cancelled                          -> a booking to check for a ghost event
  promo / work / ignore              -> counted, never proposed
  unmatched sender                   -> surfaced so new vendors get a rule

The dedupe pass is the point. Google already auto-creates calendar events from
some of these same emails (flights, Pioneer Works, Green-Wood), so ingesting
blind would duplicate exactly the events that are already correct.

Usage:
  ingest.py classify <emails.json> [--days-ahead N]
"""

import argparse
import difflib
import fnmatch
import json
import pathlib
import re
import sys
from datetime import datetime, timezone

import yaml

ROOT = pathlib.Path(__file__).resolve().parent
RULES_PATH = ROOT / "ingest_rules.yml"
PROPOSALS_PATH = ROOT / "proposals.json"

# How much title agreement a same-day candidate needs. Deliberately low: two
# confirmed bookings on one day that share this much of a name are the same
# booking far more often than they are a coincidence.
SAME_DAY_THRESHOLD = 0.34

sys.path.insert(0, str(ROOT))
import sync  # noqa: E402  -- reuse the store loaders

# Titles are compared after stripping the noise that differs between how a
# vendor names an event and how it ends up written on the calendar.
_NOISE = re.compile(
    r"\b(tickets?|order|confirmation|for|the|a|an|at|@|presents|w/|with)\b",
    re.I,
)


def normalize(title):
    text = _NOISE.sub(" ", (title or "").lower())
    text = re.sub(r"[^a-z0-9 ]+", " ", text)
    return " ".join(text.split())


def similarity(a, b):
    na, nb = normalize(a), normalize(b)
    if not na or not nb:
        return 0.0
    if na in nb or nb in na:
        return 1.0
    return difflib.SequenceMatcher(None, na, nb).ratio()


def sender_matches(pattern, sender):
    return fnmatch.fnmatch((sender or "").lower(), pattern.lower())


def classify(email, rules):
    """First matching rule wins, so put narrow rules above broad ones."""
    sender = email.get("sender", "")
    subject = email.get("subject", "") or ""

    for rule in rules:
        if not sender_matches(rule["sender"], sender):
            continue
        if "subject_prefix" in rule and not subject.startswith(rule["subject_prefix"]):
            continue
        if "subject_contains" in rule and rule["subject_contains"] not in subject:
            continue
        if "subject_in" in rule and subject not in rule["subject_in"]:
            continue
        return rule
    return None


def extract_title(email, rule):
    mode = rule.get("title_from", "subject")
    subject = email.get("subject", "") or ""
    if mode == "subject_suffix":
        for key in ("subject_prefix",):
            if key in rule:
                return subject[len(rule[key]):].strip()
    if mode == "flight":
        # "Your flight is booked: DCHUHP to Seattle on 09/16/2026" has to become
        # something shaped like the calendar entry ("Flight to Seattle (AS 21)")
        # or it will never match and will be reported missing every single run.
        m = re.search(r"to ([A-Za-z ]+?) on \d", subject)
        if m:
            return f"Flight to {m.group(1).strip()}"
    if mode == "body":
        # Timeleft-style: "Your spot at the coffee event on 09/13, 01:00 PM in
        # New York is confirmed." The subject alone carries no date.
        body = email.get("body", "") or email.get("snippet", "") or ""
        m = re.search(r"(?:dinner|coffee event|seat[^.]*?) on (\d{2}/\d{2}), (\d{2}:\d{2} [AP]M)", body)
        if m:
            kind = "Coffee" if "coffee" in body.lower()[:400] else "Dinner"
            return f"Timeleft {kind} ({m.group(1)} {m.group(2)})"
    return subject


def extract_date(email, rule, title):
    """Best-effort event date. None when the mail does not carry one.

    Without this the classifier reports every past purchase as "missing"
    forever, which trains the reader to ignore it.
    """
    body = (email.get("body") or "") + " " + (email.get("subject") or "")
    year = (email.get("date") or "")[:4] or str(datetime.now().year)

    m = re.search(r"\bon (\d{2})/(\d{2})/(\d{4})\b", body)      # flight: 09/16/2026
    if m:
        return f"{m.group(3)}-{m.group(1)}-{m.group(2)}"
    m = re.search(r"\bon (\d{2})/(\d{2})\b", body)               # timeleft: 09/13
    if m:
        return f"{year}-{m.group(1)}-{m.group(2)}"
    m = re.search(r"\b(\d{1,2}) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b", body)
    if m:                                                          # dice: "Sun 04 Oct"
        month = ["Jan","Feb","Mar","Apr","May","Jun",
                 "Jul","Aug","Sep","Oct","Nov","Dec"].index(m.group(2)) + 1
        return f"{year}-{month:02d}-{int(m.group(1)):02d}"
    return None


def load_store_titles():
    """Every event title already in the store, with its calendar and date."""
    out = []
    for slug in sync.load_calendars():
        for ev in sync.load_events(slug):
            out.append({
                "calendar": slug,
                "summary": ev.get("summary", ""),
                "start": str(ev.get("start", "")),
            })
    return out


def cmd_classify(args):
    rules = yaml.safe_load(RULES_PATH.open())["rules"]
    emails = json.load(open(args.emails))
    store = load_store_titles()

    buckets = {"confirmed": [], "cancelled": [], "promo": [],
               "work": [], "ignore": [], "unmatched": []}

    for email in emails:
        rule = classify(email, rules)
        if rule is None:
            buckets["unmatched"].append(email)
            continue
        cls = rule["class"]
        if cls != "confirmed":
            buckets[cls].append({**email, "_rule": rule})
            continue

        title = extract_title(email, rule)
        when = extract_date(email, rule, title)

        # Title text alone is a weak key: a vendor's name for an event and the
        # name it ends up carrying on the calendar routinely differ. When the
        # mail gives a date, a same-day event needs only loose title agreement
        # to count as the same thing.
        best, score = None, 0.0
        for existing in store:
            s = similarity(title, existing["summary"])
            same_day = bool(when) and existing["start"][:10] == when
            if same_day and s >= SAME_DAY_THRESHOLD:
                s = max(s, args.threshold)
            if s > score:
                best, score = existing, s

        buckets["confirmed"].append({
            **email,
            "title": title,
            "event_date": when,
            "calendar_hint": rule.get("calendar"),
            "match": best if score >= args.threshold else None,
            "match_score": round(score, 2),
        })

    today = datetime.now(timezone.utc).date().isoformat()
    on_calendar = [c for c in buckets["confirmed"] if c["match"]]
    unmatched_conf = [c for c in buckets["confirmed"] if not c["match"]]

    # A purchase for a date already gone is not a gap in the calendar. Only
    # dated-future, or undated-and-recent, mail is worth a human's attention.
    missing, past = [], []
    for c in unmatched_conf:
        d = c.get("event_date")
        if d and d < today:
            past.append(c)
        elif d is None and (c.get("date") or "9999") < args.stale_before:
            past.append(c)
        else:
            missing.append(c)

    print(f"scanned {len(emails)} email(s)\n")
    print(f"  confirmed purchases : {len(buckets['confirmed'])}")
    print(f"    already on calendar: {len(on_calendar)}")
    print(f"    NOT on calendar    : {len(missing)}  <-- actionable")
    print(f"    past / already done: {len(past)}")
    for label in ("cancelled", "promo", "work", "ignore", "unmatched"):
        print(f"  {label:<20}: {len(buckets[label])}")

    if on_calendar:
        print("\nalready on a calendar (no action):")
        for c in on_calendar:
            print(f"  [{c['match']['calendar']:<14}] {c['title'][:52]:<52} "
                  f"~{c['match_score']}")

    if missing:
        print("\nACTIONABLE -- confirmed, upcoming, not on any calendar:")
        for c in missing:
            print(f"  {c['title'][:52]:<52} {c.get('event_date') or 'undated'}")
    if past:
        print(f"\npast purchases, correctly ignored: "
              + ", ".join(c["title"][:32] for c in past))

    for label, header in (("cancelled", "cancellations -- check for a ghost event"),
                          ("unmatched", "unmatched senders -- may need a rule")):
        if buckets[label]:
            print(f"\n{header}:")
            for c in buckets[label]:
                print(f"  {c.get('sender','?')} | {c.get('subject','')[:60]}")

    PROPOSALS_PATH.write_text(json.dumps({
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "missing": missing,
        "cancelled": buckets["cancelled"],
        "unmatched": buckets["unmatched"],
    }, indent=2, ensure_ascii=False) + "\n")
    print(f"\nwrote {PROPOSALS_PATH.name}")
    return 0


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("classify")
    c.add_argument("emails")
    c.add_argument("--stale-before", default="2026-08-25",
                   help="undated mail older than this is treated as a past "
                        "event rather than a calendar gap")
    c.add_argument("--threshold", type=float, default=0.62,
                   help="similarity at or above which a mail is considered "
                        "already represented on a calendar")
    c.set_defaults(func=cmd_classify)
    a = p.parse_args()
    sys.exit(a.func(a) or 0)


if __name__ == "__main__":
    main()
