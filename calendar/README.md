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
| `sources.py` | Source registry — what feeds the store |
| `ingest.py`, `ingest_rules.yml` | Email → calendar classification |
| `.snapshots/` | Raw dumps used for import (gitignored) |
| `plan.json`, `applied.json`, `proposals.json` | Build artifacts (gitignored) |

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

## Sources

`sources.py` is the registry. Every source works the same way: something
outside this repo fetches, drops a JSON snapshot in `.snapshots/`, and the
Python side parses it. That is forced, not stylistic — **this environment has
no egress to any of these APIs.** `api.trakt.tv`, `ws.audioscrobbler.com` and
`api.themoviedb.org` all fail to connect, so a source that calls out directly
cannot work here at all.

| Source | Status | Emits | Snapshot |
|---|---|---|---|
| `email` | live | events | `emails.json` |
| `trakt` | stub | events + affinity | `trakt.json` |
| `lastfm` | stub | affinity | `lastfm.json` |

```bash
python3 calendar/sources.py    # what is wired, what is missing
```

### events vs affinity

A source contributes one or both:

- **events** — proposed calendar entries, deduped and pushed through `sync.py`.
- **affinity** — a weighted taste signal the weekly brief uses to *rank* events
  it found elsewhere. Not calendar entries.

The split is the point. A contract where every source emits events would be
wrong for Last.fm: scrobbles do not belong on a calendar. They are evidence
about which of the things already on offer are worth going to. The brief
currently ranks candidates against a hand-written taste list; affinity turns
that into a measurement. That also patches the terminal feed's blind spot —
the broker terminal has zero coverage of small rooms, so artist affinity has to
drive web-search discovery rather than replace it.

The stubs raise `NotImplementedError` with the expected snapshot shape rather
than returning empty. A silently-empty affinity source is indistinguishable
from "nothing matched", which is exactly the failure that would go unnoticed.

### Wiring Trakt

Auth already exists in this repo. `pi/server/auth.js` runs the Trakt device
flow and persists tokens; `pi/server/trakt.js` wraps the fetch with the
`trakt-api-version` / `trakt-api-key` headers. The cheapest path to a snapshot
is an export command in `pi/` that writes `calendar/.snapshots/trakt.json` —
it already holds valid tokens, and nothing in this container can reach the API.

Relevant endpoints (OAuth required): `/calendars/my/movies/{start_date}/{days}`,
`/calendars/my/shows/{start_date}/{days}`, `/sync/watchlist/movies`. The
calendar endpoints return releases for things already on the watchlist, which
is precisely what Julian enters by hand today (Dune: Part Three, Clayface,
Digger all sit on Pending) — so this replaces manual entry rather than adding
a category.

### Wiring Last.fm

Simpler: a read-only `api_key` query param, no OAuth. Base
`https://ws.audioscrobbler.com/2.0/`, `format=json`.

- `user.getTopArtists` — `period` is one of `overall|7day|1month|3month|6month|12month`
- `user.getRecentTracks` — `from` is a **UTC UNIX timestamp**; `limit` caps at
  **200**, so any window beyond a few days has to page-walk. That cap is the
  trap worth remembering.

## Email ingestion

`ingest.py` reads a JSON dump of email metadata, classifies it against
`ingest_rules.yml`, and reports which confirmed bookings are missing from the
calendar. Like `sync.py` it never touches the network.

```bash
python3 calendar/ingest.py classify calendar/.snapshots/emails.json
```

### Sender alone is useless

The rules match a sender **and** a subject shape, because the mailbox does not
separate cleanly by sender:

- `noreply@order.eventbrite.com` + `"Order Confirmation for X"` -> a purchase
- `noreply@reminder.eventbrite.com` + `"Just added! X"` -> marketing
- `event@email.eventbrite.com` + `"Get your tickets for X"` -> **also marketing**,
  an advert to buy, which reads almost identically to a confirmation

Marketing outnumbers purchases roughly 10:1 in this mailbox. Classifying on
`from:eventbrite.com` would bury the calendar in events that were never bought.
Anything matching no rule is reported as `unmatched` rather than guessed at, so
a new vendor surfaces for review instead of being silently dropped.

Two classes exist to prevent specific mistakes:

- **`cancelled`** — ingesting bookings without honouring cancellations leaves
  ghost events on the calendar.
- **`work`** — SeatGeek purchases get forwarded to `po@s4kent.com` with a PO
  number. They are S4K Entertainment buys and never belong on a personal
  calendar.

### Matching against what already exists

Google **already auto-creates** calendar events from some of these same emails
(both flights, the Pioneer Works supper clubs, Nightfall, Colin Stetson — each
carries "This event was created from an email you received in Gmail"). Ingesting
blind would duplicate exactly the events that are already correct.

Matching therefore uses two keys:

- **Title similarity**, after stripping vendor noise (`tickets`, `order
  confirmation`, `presents`, …).
- **Event date**, parsed from the body where the vendor puts it there. A
  same-day candidate needs only loose title agreement to count as the same
  booking — vendors and calendars routinely name the same event differently
  ("Your flight is booked: DCHUHP to Seattle" vs "Flight to Seattle (AS 21)").

Date also decides what is worth reporting: a purchase for a date already gone is
not a gap in the calendar. Without that filter every past purchase reports as
missing forever, which trains the reader to ignore the output.

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
