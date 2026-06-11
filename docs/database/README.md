# The Map of the Database — "Terminal .5"

This is the narrative guide to the Supabase Postgres database behind **Terminal .5**, a
ticket-market intelligence platform. Read this to understand *what the data is* and *how the
pieces join*; use [`schema-reference.md`](./schema-reference.md) for the exhaustive,
column-by-column reference of all 207 tables.

- **Project ref:** `hzrizjeaxlqcxfrtczpq`  ·  **API URL:** `https://hzrizjeaxlqcxfrtczpq.supabase.co`
- **Postgres:** 17  ·  **Schema:** everything below is in `public`
- **Connecting (read-only):** see [`access.md`](./access.md)

---

## What this database is

It continuously collects ticket **listings, sales, and orders** for live events (sports,
concerts, theater) from many ticketing platforms, normalizes them against a single event
spine, and derives **market metrics and demand signals** (pricing, inventory, movers,
sentiment, weather, news, macro). On top of the raw warehouse sit a chatbot, an alerting
system, and a small first-party ticketing product (EXOS).

The data is **time-series and snapshot-heavy**: most large tables capture the state of a market
at a `captured_at` timestamp, so the same event/section appears many times over its life.

---

## The spine: `events` and the TEvo id

```
events.id  ==  TicketEvolution (TEvo) event id   ← the primary key everything hangs off
```

`public.events` (~10K rows) is the canonical event list. Its `id` is the TEvo event id. The
columns you'll use most: `name`, `occurs_at_local` (local time, **text**), `venue_id`,
`venue_name`, `primary_performer_id/name`, `event_type`, `popularity_score`.

Almost every operational table links back with an `event_id` or `tevo_event_id` column that
foreign-keys to `events.id`. Examples: `listings_snapshots`, `section_metrics`,
`zone_metrics`, `event_metrics`, `event_sentiment`, `event_alerts`, `snapshots`,
`watch_sources`, `seatgeek_*`, `seatdata_*`, `tickpick_orders`, `vivid_orders`.

---

## The cross-source identity model (how platforms tie together)

Each external platform has its **own** id space. They are reconciled two ways:

1. **Per-source xref tables** map a source id ↔ the TEvo `events.id`:
   - `seatgeek_event_xref` (`tevo_event_id ↔ sg_event_id`), `event_xref` (TEvo ↔ ESPN),
     `seatdata_event_xref`, `ticketsdata_event_xref`, plus performer/venue variants
     (`seatgeek_performer_xref`, `seatgeek_venue_xref`, `performer_external_ids`,
     `cross_source_venue_map`, …).
2. **The AQ hub** — `aq_event_map`, keyed by **`aq_short_event_id`** (a 7-char operator id).
   This is the **canonical cross-source event key**: it carries columns for every platform's id
   (`tevo_event_id`, `sg_event_id`, `vivid_event_id`, `sh_event_id`, `tm_event_id`,
   `axs_event_id`, …) plus `venue_short_id`→`aq_venue_map` and
   `performer_short_id`→`aq_performer_map`. Order/listing tables across resale sources FK to
   `aq_event_map.aq_short_event_id`.

```
                       ┌────────────────────────┐
   SeatGeek (sg_*) ───►│  aq_event_map          │◄─── TickPick / Vivid orders
   SeatData (seatdata_)│  aq_short_event_id (PK) │
   AXS (axs_*) ───────►│  + tevo_event_id ───────┼───►  events.id  ◄── EVO orders, metrics,
   TicketsData (td_*)  │  + sg/sh/tm/vivid ids    │              listings, sentiment, alerts
                       └─────────┬──────────────┘
                                 │
                 aq_venue_map ◄──┴──► aq_performer_map
```

Also useful: `canonical_external_ids` (entity_kind, tevo_id, source_key → external id) and the
**convenience views** `entity_event_map`, `entity_performer_map`, `entity_venue_map`,
`cross_source_event_audit` — these pre-join the identity layer so you often don't have to.

---

## Domains at a glance

| Domain | Key tables | Notes |
|---|---|---|
| **Core events & inventory (TEvo/EVO)** | `events`, `listings_snapshots` (~86M), `section_metrics` (~29M), `event_section_row_snapshots` (~9M), `event_metrics`, `zone_metrics`, `event_sentiment` | The source-of-truth spine. Raw listings + section/zone/event rollups over time. |
| **Cross-source identity** | `aq_event_map`, `aq_venue_map`, `aq_performer_map`, `*_xref`, `canonical_external_ids` | Ties every platform's ids to the TEvo spine. |
| **SeatGeek** | `seatgeek_listings_snapshots` (~14M), `seatgeek_sales_snapshots` (~11M), `seatgeek_event_metrics`, `seatgeek_orders`, `sg_events_canonical` | Largest external source; `sg_events_canonical` = one row per SG event. |
| **SeatData** | `seatdata_sales_snapshots`, `seatdata_*_xref` | Historic sales feed. |
| **TicketsData** | `ticketsdata_listings_snapshots` (~5.6M), `td_*` | Multi-platform proxy (StubHub/Gametime/Vivid/TickPick/Ticketmaster). |
| **AXS** | `axs_seat_snapshots` (~269K), `axs_section_snapshots`, `axs_event_snapshots`, `axs_venues` | Primary ticketer + AXS resale; per-seat detail. |
| **Other resale orders** | `tickpick_orders`, `vivid_orders`, `evo_orders`/`evo_order_items` | Realized sales/orders by source. |
| **Movers & signals** | `event_movers_index`, `event_movers_agg`, `discovery_gap_alerts`, `event_listing_snapshot_daily` | Top price/inventory movers; long-term daily rollup. |
| **Venues & seat maps** | `venue_section_map`, `seatmap_manifest`, `cross_source_venue_map`, `venue_metrics_daily` | Seat-map crosswalk across platforms (the literal "map"). |
| **Performers** | `performer_metadata`, `performer_external_ids`, `performer_wikipedia`, `performer_metrics_daily` | Genre/category/popularity + enrichment. |
| **ESPN** | `espn_event_snapshots`, `espn_team_snapshots`, `espn_injuries_snapshots` (~43K), `espn_athletes`, `espn_athlete_team_history` (~1.2M), `espn_teams_canonical` | Sports context: scores, odds, standings, injuries, rosters. |
| **Weather & NWS** | `weather_observations` (~173K), `nws_alerts`, `nws_alert_zones` | Demand-affecting weather. |
| **Macro / social / calendar** | `macro_indicators` (FRED), `event_competitors_snapshot`, `holidays`, `important_x_accounts` | Contextual demand signals. |
| **Chatbot** | `bot_chat`, `bot_messages`, `chat_*` | Conversation logs + analytics. |
| **EXOS** | `exos_events`, `exos_tickets`, `exos_orgs`, … | First-party primary-ticketing product (own id space, UUID PKs). |
| **Ops** | `cron_policy`, `cron_gate_decisions` (~198K), `collector_cadence`, `integration_policy` | Scheduler/policy bookkeeping — rarely needed for data questions. |

Full per-table detail (every column, PK, FK, comment) is in
[`schema-reference.md`](./schema-reference.md).

---

## Gotchas — read before you query

- **Time columns:** `captured_at`, `*_at` are `timestamptz` (**UTC**). `occurs_at_local` is a
  **text** local-time string, not a timestamp — don't do timestamp math on it. Sports games
  have UTC kickoff in `espn_event_date_lookup.game_at_utc`.
- **Snapshots repeat:** raw `*_snapshots` tables have many rows per event over time. To get
  "current" state, take the latest `captured_at` per key (or use the `latest_*` views:
  `latest_snapshots`, `latest_event_metrics`, `latest_zone_metrics`).
- **Use rollups, not raw, for trends:** `event_metrics` (7-day TTL raw) and
  `event_listing_snapshot_daily` (3×/day, kept 1 year) are far cheaper than
  `listings_snapshots`/`section_metrics`.
- **"owned" / s4k:** `is_owned = true` (and `owned_*` metric columns) means inventory owned by
  *this operator*, vs the whole market. Many chat/audit tables distinguish "s4k_only" vs "all sources."
- **Money:** `retail_price` = ask price; `wholesale_price` = cost/net. `getin_price` = cheapest
  ("get-in") price for an event/section.
- **Empty tables are normal:** many staging/`*_pending`/future-feature tables have 0 rows.
  Counts in the reference tell you what's actually populated.
- **Views vs tables:** several `public` relations are **views** (e.g. `entity_*_map`,
  `event_lifecycle`, `event_velocity`, `latest_*`, `cross_source_*`). They're great shortcuts.

---

## Worked example queries

**1 — Find an event by name, then its latest market snapshot**
```sql
select id, name, occurs_at_local, venue_name, primary_performer_name
from events
where name ilike '%lakers%celtics%'
order by occurs_at_local
limit 20;

-- latest EVO event metrics for one event
select *
from event_metrics
where event_id = :event_id
order by captured_at desc
limit 1;
```

**2 — Cheapest current listings for an event (filter + limit on the big table)**
```sql
select section, row, quantity, retail_price, is_owned, captured_at
from listings_snapshots
where event_id = :event_id
  and captured_at = (select max(captured_at) from listings_snapshots where event_id = :event_id)
order by retail_price asc
limit 25;
```

**3 — Compare EVO vs SeatGeek pricing via the daily rollup**
```sql
select snapshot_date, evo_retail_getin, sg_all_getin, amalgam_getin
from event_listing_snapshot_daily
where event_id = :event_id
order by snapshot_date desc
limit 30;
```

**4 — Walk the cross-source map for one event**
```sql
select aq_short_event_id, event_name, tevo_event_id, sg_event_id, vivid_event_id, axs_event_id
from aq_event_map
where tevo_event_id = :event_id;
```

**5 — Top price movers right now**
```sql
select category, rank, event_id, cur_price, price_delta_pct, signal_score
from event_movers_index
where source = 'evo' and window_days = 7
order by category, rank
limit 50;
```

**6 — Sports context: upcoming game + team injuries**
```sql
select e.id, e.name, x.espn_event_id, x.espn_league, d.game_at_utc
from events e
join event_xref x on x.tevo_event_id = e.id
join espn_event_date_lookup d on d.espn_event_id = x.espn_event_id and d.espn_league = x.espn_league
where e.id = :event_id;
```

If a question can't be answered from the schema you see, **run a small exploratory query**
(`select * from <table> limit 5`) to learn the shape before committing to an answer.
