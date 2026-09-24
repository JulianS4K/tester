#!/usr/bin/env python3
"""
Git-backed calendar store with idempotent push to Google Calendar.

This script is the deterministic half of the sync. It never talks to Google:
it reads the YAML store plus the recorded state and emits a PLAN describing
exactly which create/update/delete calls to make. An agent with Google
Calendar tools executes the plan and feeds the results back via `record`.

That split exists because pushing requires OAuth credentials this repo does
not (and should not) hold. The script owns diffing and idempotency; the
transport is whoever holds the tokens.

Commands:
  import <slug> <snapshot.json>   Seed the store from a list_events dump.
  plan [--allow-delete]           Diff store vs state -> plan.json.
  record <applied.json>           Write back Google event IDs after a push.
  verify                          Consistency check of store + state.
"""

import argparse
import hashlib
import json
import pathlib
import sys
from datetime import datetime, timezone

import yaml

ROOT = pathlib.Path(__file__).resolve().parent
EVENTS_DIR = ROOT / "events"
STATE_PATH = ROOT / "state.json"
PLAN_PATH = ROOT / "plan.json"
CALENDARS_PATH = ROOT / "calendars.yml"

# Fields that actually get pushed to Google. The content hash covers exactly
# these -- adding a field here changes every hash, which is intended: it makes
# the next plan re-push everything so the remote matches the new schema.
PUSHABLE = ("summary", "start", "end", "timezone", "location",
            "description", "all_day", "reminders", "recurrence")


def load_calendars():
    with CALENDARS_PATH.open() as fh:
        data = yaml.safe_load(fh) or {}
    return data.get("calendars", {})


def load_state():
    if not STATE_PATH.exists():
        return {"version": 1, "entries": {}}
    with STATE_PATH.open() as fh:
        return json.load(fh)


def save_state(state):
    STATE_PATH.write_text(
        # ensure_ascii=False keeps accented uids readable in the file and
        # makes the round-trip stable -- escaping them produces spurious
        # diffs every time the state is rewritten.
        json.dumps(state, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    )


def store_path(slug):
    return EVENTS_DIR / f"{slug}.yml"


def load_events(slug):
    path = store_path(slug)
    if not path.exists():
        return []
    with path.open() as fh:
        return yaml.safe_load(fh) or []


def save_events(slug, events):
    events = sorted(events, key=lambda e: (str(e.get("start", "")), e["uid"]))
    store_path(slug).write_text(
        yaml.safe_dump(events, sort_keys=False, allow_unicode=True,
                       default_flow_style=False, width=100)
    )


def content_hash(event):
    """Stable hash over pushable fields only.

    Bookkeeping keys (uid, managed, notes, source) are excluded so that
    re-annotating an event locally does not trigger a pointless remote update.
    """
    payload = {k: event.get(k) for k in PUSHABLE if event.get(k) is not None}
    blob = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(blob.encode("utf-8")).hexdigest()[:16]


def state_key(slug, uid):
    return f"{slug}/{uid}"


def slugify(text, fallback="event"):
    keep = [c.lower() if c.isalnum() else "-" for c in (text or "")]
    out = "".join(keep)
    while "--" in out:
        out = out.replace("--", "-")
    out = out.strip("-")
    return out[:60] or fallback


# --------------------------------------------------------------------------
# import
# --------------------------------------------------------------------------

def cmd_import(args):
    calendars = load_calendars()
    if args.slug not in calendars:
        sys.exit(f"unknown calendar slug '{args.slug}' -- add it to calendars.yml first")
    calendar_id = calendars[args.slug]["id"]

    with open(args.snapshot) as fh:
        snapshot = json.load(fh)
    remote_events = snapshot.get("events", [])

    existing = {e["uid"]: e for e in load_events(args.slug)}
    state = load_state()

    # A recurring series comes back from list_events as one row per instance.
    # Storing each instance would both bloat the file and, worse, make a push
    # try to create N standalone events. Collapse to the master and record the
    # series once as an unmanaged reference -- the RRULE stays owned by Google.
    seen_series = set()
    imported = skipped_instances = 0

    for ev in remote_events:
        if ev.get("status") == "cancelled":
            continue

        series_id = ev.get("recurringEventId")
        if series_id:
            if series_id in seen_series:
                skipped_instances += 1
                continue
            seen_series.add(series_id)

        google_id = series_id or ev["id"]
        start = ev.get("start", {})
        end = ev.get("end", {})
        all_day = "date" in start

        uid = slugify(ev.get("summary", "")) or f"imported-{google_id[:8]}"
        # Disambiguate on date so two shows with the same name stay distinct.
        date_part = (start.get("dateTime") or start.get("date") or "")[:10]
        if date_part and not series_id:
            uid = f"{uid}-{date_part}"

        record = {
            "uid": uid,
            "summary": ev.get("summary", ""),
            "start": start.get("dateTime") or start.get("date"),
            "end": end.get("dateTime") or end.get("date"),
            "timezone": start.get("timeZone"),
            "all_day": all_day,
        }
        if ev.get("location"):
            record["location"] = ev["location"]
        if ev.get("description"):
            record["description"] = ev["description"]
        if ev.get("overrideReminders"):
            record["reminders"] = [
                {"method": r["method"], "minutes": r["minutes"]}
                for r in ev["overrideReminders"]
            ]

        if series_id:
            # Recurrence edits through the API are unreliable (the RRULE update
            # path errors and needs delete+recreate), so the store tracks the
            # series without claiming ownership of it.
            record["managed"] = False
            record["notes"] = "recurring series -- owned in Google, not pushed from repo"

        existing[uid] = record
        state["entries"][state_key(args.slug, uid)] = {
            "calendar_id": calendar_id,
            "google_event_id": google_id,
            "content_hash": content_hash(record),
            "imported_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "recurring": bool(series_id),
        }
        imported += 1

    save_events(args.slug, list(existing.values()))
    save_state(state)
    print(f"imported {imported} event(s) into {store_path(args.slug).name}")
    if skipped_instances:
        print(f"  collapsed {skipped_instances} recurring instance(s) into their series")


# --------------------------------------------------------------------------
# plan
# --------------------------------------------------------------------------

def cmd_plan(args):
    calendars = load_calendars()
    state = load_state()
    actions = []
    live_keys = set()

    for slug, meta in calendars.items():
        for ev in load_events(slug):
            uid = ev["uid"]
            key = state_key(slug, uid)
            live_keys.add(key)

            if ev.get("managed") is False:
                continue

            digest = content_hash(ev)
            entry = state["entries"].get(key)

            if entry is None:
                actions.append({
                    "action": "create", "calendar": slug,
                    "calendar_id": meta["id"], "uid": uid,
                    "content_hash": digest, "event": ev,
                })
            elif entry.get("content_hash") != digest:
                actions.append({
                    "action": "update", "calendar": slug,
                    "calendar_id": meta["id"], "uid": uid,
                    "google_event_id": entry["google_event_id"],
                    "content_hash": digest, "event": ev,
                })

    # Anything in state but no longer in the store was deleted locally. Only
    # ever propose removing events this repo created -- never remote events it
    # merely observed -- and require an explicit flag even then.
    for key, entry in state["entries"].items():
        if key in live_keys:
            continue
        slug, uid = key.split("/", 1)
        if entry.get("recurring"):
            continue
        if not args.allow_delete:
            print(f"  note: '{key}' is in state but gone from the store "
                  f"(re-run with --allow-delete to propose removal)")
            continue
        actions.append({
            "action": "delete", "calendar": slug,
            "calendar_id": entry["calendar_id"], "uid": uid,
            "google_event_id": entry["google_event_id"],
        })

    PLAN_PATH.write_text(json.dumps({
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "actions": actions,
    }, indent=2, ensure_ascii=False) + "\n")

    if not actions:
        print("plan: no changes -- store and Google are in sync")
        return

    print(f"plan: {len(actions)} action(s) -> {PLAN_PATH.name}")
    for a in actions:
        title = a.get("event", {}).get("summary", a["uid"])
        print(f"  {a['action'].upper():6} [{a['calendar']}] {title}")


# --------------------------------------------------------------------------
# record
# --------------------------------------------------------------------------

def cmd_record(args):
    with open(args.applied) as fh:
        applied = json.load(fh)
    state = load_state()
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    updated = 0

    for item in applied.get("results", []):
        key = state_key(item["calendar"], item["uid"])
        if item["action"] == "delete":
            state["entries"].pop(key, None)
            updated += 1
            continue
        state["entries"][key] = {
            "calendar_id": item["calendar_id"],
            "google_event_id": item["google_event_id"],
            "content_hash": item["content_hash"],
            "pushed_at": now,
            "recurring": False,
        }
        updated += 1

    save_state(state)
    print(f"recorded {updated} result(s) into {STATE_PATH.name}")


# --------------------------------------------------------------------------
# verify
# --------------------------------------------------------------------------

def cmd_verify(args):
    calendars = load_calendars()
    state = load_state()
    problems = []
    total = 0

    for slug in calendars:
        seen = set()
        for ev in load_events(slug):
            total += 1
            uid = ev.get("uid")
            if not uid:
                problems.append(f"[{slug}] event with no uid: {ev.get('summary')!r}")
                continue
            if uid in seen:
                problems.append(f"[{slug}] duplicate uid: {uid}")
            seen.add(uid)
            for field in ("summary", "start", "end"):
                if not ev.get(field):
                    problems.append(f"[{slug}/{uid}] missing required field '{field}'")

    for key in state["entries"]:
        slug = key.split("/", 1)[0]
        if slug not in calendars:
            problems.append(f"state references unknown calendar '{slug}'")

    print(f"verify: {total} event(s) across {len(calendars)} calendar(s), "
          f"{len(state['entries'])} state entr(ies)")
    for p in problems:
        print(f"  PROBLEM {p}")
    return 1 if problems else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_imp = sub.add_parser("import", help="seed the store from a list_events dump")
    p_imp.add_argument("slug")
    p_imp.add_argument("snapshot")
    p_imp.set_defaults(func=cmd_import)

    p_plan = sub.add_parser("plan", help="diff store against state")
    p_plan.add_argument("--allow-delete", action="store_true")
    p_plan.set_defaults(func=cmd_plan)

    p_rec = sub.add_parser("record", help="write back results after a push")
    p_rec.add_argument("applied")
    p_rec.set_defaults(func=cmd_record)

    p_ver = sub.add_parser("verify", help="consistency check")
    p_ver.set_defaults(func=cmd_verify)

    args = parser.parse_args()
    sys.exit(args.func(args) or 0)


if __name__ == "__main__":
    main()
