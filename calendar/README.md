# Calendar store

Git-backed source of truth for Julian's Google Calendars, with an idempotent
push path. Edit YAML here, push the diff to Google.

## Why it is shaped this way

Pushing to Google needs OAuth credentials that this repo should not hold. So
the work is split:

- **`sync.py` does the thinking** — reads the store, compares it against
  recorded state, and writes a `plan.json` naming the exact create/update/
  delete calls required. It never touches the network.
- **An agent with Google Calendar tools does the pushing** — executes the plan,
  then feeds the results back through `sync.py record`.

Everything that makes this safe (matching local events to remote ones, not
re-creating what already exists, refusing to touch events it does not own)
lives in the deterministic half.

## Layout

| Path | What it is |
|---|---|
| `calendars.yml` | Calendar registry — IDs and what belongs on each |
| `events/<slug>.yml` | The events themselves, one file per calendar |
| `state.json` | `uid` → Google event ID + content hash. **The anti-duplication ledger.** |
| `sync.py` | Planner |
| `.snapshots/` | Raw `list_events` dumps used for import (gitignored) |
| `plan.json`, `applied.json` | Build artifacts (gitignored) |

## The loop

```bash
python3 calendar/sync.py plan      # what would change
# ... agent executes plan.json against Google, writes applied.json ...
python3 calendar/sync.py record calendar/applied.json
python3 calendar/sync.py verify    # consistency check
```

A clean `plan` means the store and Google agree.

## Why `state.json` matters

Without it every push would re-create every event. `state.json` records which
local `uid` became which Google event ID, plus a hash of the content that was
pushed. That gives three properties:

- An event already in Google is **updated**, never duplicated.
- An event whose YAML is unchanged produces **no API call at all** — the hash
  matches, so it is skipped.
- Only events this store actually created can be **deleted**, and only with an
  explicit `--allow-delete`. Events it merely observed are never removed.

The hash covers only the fields that get pushed (`summary`, `start`, `end`,
`timezone`, `location`, `description`, `all_day`, `reminders`, `recurrence`).
Local-only bookkeeping — `uid`, `managed`, `notes` — is excluded, so
re-annotating an event does not trigger a pointless remote write.

## Recurring events

`list_events` returns a recurring series as one row per instance. Importing
those naively would store twelve copies of one series and then try to push
twelve standalone events.

The importer collapses instances back to their master and marks the series
`managed: false`. Unmanaged events are tracked but never pushed — the RRULE
stays owned by Google. This is deliberate: editing a recurrence rule through
the API is unreliable and needs a delete-and-recreate, which is not something
a sync script should do unattended.

To change a recurring series, do it in Google, then re-import.

## Seeding

The store was seeded by importing what was already live, so the first plan
after import was empty — no duplicates. To re-seed or add a calendar:

```bash
# dump a calendar via list_events into calendar/.snapshots/<slug>.json
python3 calendar/sync.py import <slug> calendar/.snapshots/<slug>.json
```

Import is idempotent on `uid`, so re-running it refreshes rather than
duplicates.

## Conventions

Carried over from how these calendars are already run:

- Confirmed and ticketed → `the-show`. Maybes → `pending`. Recurring,
  personal, community → `julian-things`.
- Event URLs go in the description; ticketed events get a 7-day popup reminder
  (`minutes: 10080`).
- Overlaps are a deliberate menu. Conflict analysis belongs in a report, never
  in event text.
- When a date or venue is not certain, say `VERIFY DATE` / `VERIFY VENUE` in
  the title rather than guessing.
- Push with `notificationLevel: NONE` so edits do not mail attendees.

## Not registered on purpose

The two Ticketmaster feeds, the FIFA World Cup Sync2Cal feed, and the holiday
calendars are externally synced. They are absent from `calendars.yml` so this
store can never push to a calendar something else owns.
