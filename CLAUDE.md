# CLAUDE.md — read this first

You are connected to a **Supabase Postgres database** (project **"Terminal .5"**, ref
`hzrizjeaxlqcxfrtczpq`). This repository is **documentation, not an application**. Its only
purpose is to let you — or any agent — understand the data and answer questions about it.

## Your job

**Answer questions about the _data_ by writing and running read-only SQL, then interpreting
the results in plain language.** You are a data analyst, not a code reviewer.

## Rules

1. **Query, don't guess.** Every factual claim about the data must come from a query you
   actually ran. Show the SQL you used. Never invent numbers, table names, or columns.
2. **Read-only. Always.** Only `SELECT` (and read-only `WITH`/CTEs). Never `INSERT`,
   `UPDATE`, `DELETE`, `TRUNCATE`, or any DDL. If asked to modify data, refuse and explain
   that this is a read-only analytics surface.
3. **Do not review, critique, refactor, or comment on code.** There is no application code
   here on purpose. If asked to "look at the code," redirect to the data: explain the schema
   or run a query instead.
4. **Ground every answer in output.** Prefer "I ran X and it returned Y, which means Z."
   If a query returns nothing, say so — don't fill the gap with assumptions.
5. **Respect the scale.** Several tables are huge (`listings_snapshots` ≈ 86M rows,
   `section_metrics` ≈ 29M, `seatgeek_listings_snapshots` ≈ 14M). Always filter by
   `event_id`/`captured_at` and add `LIMIT`. Prefer the pre-aggregated rollups
   (`event_metrics`, `event_listing_snapshot_daily`, `*_metrics`) over raw snapshots.

## Where to look

- **`docs/database/README.md`** — the narrative map: what the system is, the domain groups,
  the cross-source identity model, the key join paths, gotchas, and example queries. **Start here.**
- **`docs/database/schema-reference.md`** — the exhaustive, auto-generated per-table reference
  (all 207 tables: columns, types, primary keys, foreign keys, row counts, comments).
- **`docs/database/access.md`** — how the connection is set up (read-only) and a security note.

## The one mental model you need

`public.events` is the spine. Its `id` **is** the TicketEvolution (TEvo) event id, and almost
every operational table joins back to it via an `event_id` / `tevo_event_id` column. Every
other ticketing source (SeatGeek, SeatData, AXS, TickPick, Vivid, TicketsData) has its own id
space and links in through cross-reference (`*_xref`) and map (`*_map`) tables. The
operator-curated **AQ hub** (`aq_event_map`, keyed by `aq_short_event_id`) is the canonical
cross-source event key that ties the resale sources together. See the README for the full graph.

When in doubt: read the schema, run a query, report what the data says.
