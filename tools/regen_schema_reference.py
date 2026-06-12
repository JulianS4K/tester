#!/usr/bin/env python3
"""
Regenerate docs/database/schema-reference.md from the live Postgres catalog.

Keeps the map honest (limit #5: docs drift). Run after any schema change, or let
the weekly GitHub Action (.github/workflows/schema-drift.yml) do it.

    export DATABASE_URL="postgresql://data_reader:PW@db.<ref>.supabase.co:5432/postgres?sslmode=require"
    python tools/regen_schema_reference.py            # rewrite the reference
    python tools/regen_schema_reference.py --check    # exit 2 if structure drifted
                                                      # (row-count-only changes ignored)
"""
import os
import re
import sys
import pathlib

import psycopg

REPO = pathlib.Path(__file__).resolve().parents[1]
TARGET = REPO / "docs" / "database" / "schema-reference.md"

CATALOG_SQL = """
select
  c.relname as table_name,
  obj_description(c.oid) as comment,
  (select string_agg(a.attname||' '||format_type(a.atttypid,a.atttypmod)||
          case when a.attnotnull then '!' else '' end, ', ' order by a.attnum)
     from pg_attribute a
     where a.attrelid=c.oid and a.attnum>0 and not a.attisdropped) as cols,
  (select string_agg(att.attname, ', ' order by array_position(con.conkey, att.attnum::smallint))
     from pg_constraint con
     join pg_attribute att on att.attrelid=con.conrelid and att.attnum = any(con.conkey)
     where con.conrelid=c.oid and con.contype='p') as pk
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where n.nspname='public' and c.relkind='r'
order by c.relname
"""

FK_SQL = """
select c.relname, a.attname, rc.relname, ra.attname
from pg_constraint con
join pg_class c on c.oid=con.conrelid
join pg_namespace n on n.oid=c.relnamespace
join pg_attribute a on a.attrelid=con.conrelid and a.attnum=con.conkey[1]
join pg_class rc on rc.oid=con.confrelid
join pg_attribute ra on ra.attrelid=con.confrelid and ra.attnum=con.confkey[1]
where con.contype='f' and n.nspname='public'
order by c.relname, a.attname, ra.attname
"""

ROWS_SQL = "select relname, n_live_tup from pg_stat_user_tables where schemaname='public'"


def dom(n):
    """Domain assignment — first match wins. Keep in sync with the README's domain table."""
    if n in ('events', 'listings_snapshots', 'section_metrics', 'zone_metrics', 'event_metrics',
             'event_section_row_snapshots', 'snapshots', 'event_sentiment', 'event_alerts',
             'event_listing_snapshot_daily', 'event_competitors_snapshot', 'event_watchlist',
             'event_pulls', 'event_match_attempts', 'watch_sources', 'watchlist', 'runs', 'settings',
             'leads', 'share_links', 'tevo_ticket_groups_cache', 'listing_xref', 'event_xref',
             'event_xref_cleanup_archive_20260606', 'zone_rules', 'performer_zones', 'performer_zone_rules'):
        return 'Core: events, listings & metrics (TEvo / EVO source of truth)'
    if n.startswith('event_movers'):
        return 'Movers & market-signal indices'
    if n.startswith('evo_'):
        return 'EVO (TEvo/TicketEvolution) orders & polling'
    if n.startswith('seatgeek_') or n.startswith('sg_'):
        return 'SeatGeek (listings, sales, orders, matching)'
    if n.startswith('seatdata_'):
        return 'SeatData source'
    if n.startswith('ticketsdata_') or n.startswith('td_'):
        return 'TicketsData (SH/GT/VD/TP/TM multi-platform)'
    if n.startswith('axs_'):
        return 'AXS (primary + marketplace)'
    if n.startswith('espn_') or n == 'performer_espn_team_xref':
        return 'ESPN (teams, athletes, injuries, scores, news)'
    if n.startswith('performer_'):
        return 'Performers (metadata, enrichment, baselines)'
    if n.startswith('venue_') or n in ('cross_source_venue_map', 'seatmap_manifest', 'venue_section_map'):
        return 'Venues & seat maps'
    if n.startswith('aq_') or n in ('canonical_external_ids', 'entity_xref_conflicts', 'entity_xref_overrides',
                                    'broker_xref', 'matchup_xref', 'taxonomy_xref', 'order_status_xref',
                                    'order_fee_schedule', 'data_sources', 'data_source_field_map',
                                    'bridge_event_xref', 'listing_xref'):
        return 'Cross-source identity map (AQ hub, xrefs, canonical IDs)'
    if n.startswith('bot_') or n.startswith('chat_'):
        return 'Chatbot & conversation analytics'
    if n.startswith('exos_'):
        return 'EXOS (own primary-ticketing platform)'
    if n.startswith('weather_') or n.startswith('nws_'):
        return 'Weather & NWS alerts (demand signals)'
    if n.startswith('macro_') or n == 'fred_pending':
        return 'Macro indicators (FRED)'
    if n.startswith('reddit_') or n in ('important_x_accounts', 'news_keywords', 'general_subreddits',
                                        'performer_subreddits', 'why_signals'):
        return 'Social / news signals'
    if n in ('holidays', 'school_break_windows', 'sporting_rivalries', 'mlb_branded_series'):
        return 'Calendar & context reference'
    if n.startswith('tickpick_') or n.startswith('vivid_'):
        return 'Other resale orders (TickPick, Vivid)'
    if n.startswith('tournament_'):
        return 'Tournaments / non-team events'
    if n.startswith('cron_') or n in ('collector_cadence', 'integration_policy', 'pull_rate_limits',
                                      'discovery_gap_alerts', 'sg_priority_policy', 'sg_league_priority_config',
                                      'seatdata_pull_budget'):
        return 'Ops: scheduling, policy & coverage'
    if n.startswith('alert_') or n == 'user_alerts':
        return 'Alerts'
    return 'Other / reference'


ORDER = [
    'Core: events, listings & metrics (TEvo / EVO source of truth)',
    'Cross-source identity map (AQ hub, xrefs, canonical IDs)',
    'EVO (TEvo/TicketEvolution) orders & polling',
    'SeatGeek (listings, sales, orders, matching)',
    'SeatData source',
    'TicketsData (SH/GT/VD/TP/TM multi-platform)',
    'AXS (primary + marketplace)',
    'Other resale orders (TickPick, Vivid)',
    'Movers & market-signal indices',
    'Venues & seat maps',
    'Performers (metadata, enrichment, baselines)',
    'ESPN (teams, athletes, injuries, scores, news)',
    'Weather & NWS alerts (demand signals)',
    'Macro indicators (FRED)',
    'Social / news signals',
    'Calendar & context reference',
    'Chatbot & conversation analytics',
    'EXOS (own primary-ticketing platform)',
    'Tournaments / non-team events',
    'Ops: scheduling, policy & coverage',
    'Alerts',
    'Other / reference',
]


def fmt_rows(n):
    if n is None:
        return '?'
    if n >= 1_000_000:
        return f'{n / 1_000_000:.1f}M'
    if n >= 1_000:
        return f'{n / 1_000:.0f}K'
    return str(n)


def generate(conn) -> str:
    with conn.cursor() as cur:
        cur.execute(CATALOG_SQL)
        tables = [{"table_name": r[0], "comment": r[1], "cols": r[2], "pk": r[3]}
                  for r in cur.fetchall()]
        cur.execute(FK_SQL)
        fks = {}
        for t, col, ref, refcol in cur.fetchall():
            fks.setdefault(t, []).append((col, ref, refcol))
        cur.execute(ROWS_SQL)
        rows = dict(cur.fetchall())

    by = {t['table_name']: t for t in tables}
    groups = {}
    for t in tables:
        groups.setdefault(dom(t['table_name']), []).append(t['table_name'])
    order = ORDER + [g for g in groups if g not in ORDER]

    out = []
    out.append("# Schema Reference — `public` (auto-generated)\n")
    out.append("> Generated from the live Postgres catalog. `!` after a type = NOT NULL. "
               "Row counts are live estimates (`pg_stat_user_tables.n_live_tup`) and drift between refreshes. "
               "This file is the exhaustive per-table reference; see `README.md` for the narrative map and join graph.\n")
    out.append(f"**{len(tables)} base tables.** Jump to a domain:\n")
    for g in order:
        if g in groups:
            anchor = re.sub(r'[^a-z0-9 ]', '', g.lower()).replace(' ', '-')
            out.append(f"- [{g}](#{anchor}) ({len(groups[g])})")
    out.append("")

    for g in order:
        if g not in groups:
            continue
        out.append(f"\n## {g}\n")
        for name in sorted(groups[g], key=lambda x: (-(rows.get(x) or 0), x)):
            t = by[name]
            out.append(f"### `{name}` &nbsp; ≈{fmt_rows(rows.get(name))} rows")
            if t.get('comment'):
                out.append(f"\n{t['comment'].strip()}")
            meta = []
            if t.get('pk'):
                meta.append(f"**PK** `{t['pk']}`")
            if name in fks:
                seen, parts = set(), []
                for col, ref, refcol in fks[name]:
                    if (col, ref) in seen:
                        continue
                    seen.add((col, ref))
                    parts.append(f"`{col}`→`{ref}.{refcol}`")
                if parts:
                    meta.append("**FK** " + "; ".join(parts))
            if meta:
                out.append("\n" + "  •  ".join(meta))
            out.append("\n```")
            out.append((t.get('cols') or '').replace(', ', ',\n'))
            out.append("```")
        out.append("")
    return "\n".join(out)


def normalize(text: str) -> str:
    """Strip volatile row counts so --check flags only structural drift."""
    return re.sub(r'≈\S+ rows', '≈N rows', text)


def main():
    check = "--check" in sys.argv
    with psycopg.connect(os.environ["DATABASE_URL"]) as conn:
        conn.execute("SET default_transaction_read_only = on")
        fresh = generate(conn)

    if check:
        committed = TARGET.read_text() if TARGET.exists() else ""
        if normalize(committed) == normalize(fresh):
            print("OK: schema reference matches the live catalog (row counts ignored).")
            sys.exit(0)
        print("DRIFT: live schema differs from docs/database/schema-reference.md — regenerate.")
        sys.exit(2)

    TARGET.write_text(fresh)
    print(f"Wrote {TARGET} ({len(fresh)} chars).")


if __name__ == "__main__":
    main()
