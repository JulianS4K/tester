# Terminal .5 — database guide for AI agents

This repository is a **map of a Supabase Postgres database**. It contains no application code.
Its purpose: give a person *and their AI agent* (Claude, Grok, GPT, or any bot) everything
needed to understand the data and **answer questions by running read-only SQL — not by reading
or reviewing code.**

## What's here

| File | What it's for |
|---|---|
| [`CLAUDE.md`](./CLAUDE.md) | Behavioral contract Claude auto-loads: be a read-only data analyst, query don't guess, don't comment on code. |
| [`AGENTS.md`](./AGENTS.md) | Same contract, vendor-neutral — paste into Grok / GPT / any bot's system prompt. |
| [`docs/database/README.md`](./docs/database/README.md) | **The map**: what the data is, the domain groups, the cross-source join model, gotchas, example queries. Start here. |
| [`docs/database/schema-reference.md`](./docs/database/schema-reference.md) | Exhaustive auto-generated reference for all 207 tables (columns, types, keys, FKs, row counts). |
| [`docs/database/access.md`](./docs/database/access.md) | How to set up **read-only** access (a database-enforced `data_reader` role) and connect. |
| [`.mcp.json`](./.mcp.json) | Ready-to-use read-only Supabase MCP server for Claude Code / Cursor. |
| [`examples/grok_sql_agent.py`](./examples/grok_sql_agent.py) | Runnable reference client plugging **Grok (xAI)** into the data via function calling. |

## How to give someone access

1. Run the read-only role SQL in [`docs/database/access.md`](./docs/database/access.md) once.
2. Hand them this repo + the `data_reader` connection string.
3. Their agent reads `CLAUDE.md`/`AGENTS.md`, orients with `docs/database/`, and answers data
   questions with SQL it runs itself — physically unable to write, because the role is `SELECT`-only.

## Database at a glance

Ticket-market intelligence warehouse (project ref `hzrizjeaxlqcxfrtczpq`): listings, sales, and
orders for live events across SeatGeek, TicketEvolution, SeatData, AXS, TickPick, Vivid and
TicketsData, normalized to one event spine (`events.id` = the TicketEvolution event id), plus
derived metrics, movers, and demand signals (ESPN, weather, macro, social). ~207 tables; the
largest, `listings_snapshots`, holds ~86M rows.

> The schema reference is generated from the live catalog. To regenerate it after schema
> changes, re-run the catalog query and the generator (see git history of `schema-reference.md`).
