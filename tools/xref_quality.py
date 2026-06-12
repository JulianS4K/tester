#!/usr/bin/env python3
"""
Cross-source mapping quality checks — is the identity layer actually right?

Every cross-source answer rides on the xref/AQ mappings (events <-> SeatGeek <->
ESPN <-> AQ hub ...). A wrong mapping produces confidently wrong answers with
perfectly valid SQL, so these checks verify the mappings themselves:

  duplicates      one source id claimed by multiple TEvo events (matcher collisions)
  date agreement  mapped pairs must occur at ~the same time (stale/wrong links)
  orphans         xref rows pointing at events that don't exist
  coverage        share of upcoming events with cross-source links (visibility, not error)
  backlog         unresolved conflicts / low-confidence matches

All SQL validated against the live DB (2026-06-12). Severities:
  CRITICAL  exit code counts it; a real mis-mapping class (e.g. WNBA game and an
            NBA Finals placeholder sharing one SeatGeek id was found by sg_duplicate)
  WARN      known-benign subsets exist (playoff alternate branches, rescheduled games)
            — review the samples, don't auto-fail
  INFO      coverage/visibility numbers for trend-watching

Run:  export DATABASE_URL=...   &&   python tools/xref_quality.py [--samples]
"""
import os
import sys

import psycopg

CHECKS = [
    dict(
        id="sg_duplicate", severity="CRITICAL",
        desc="SeatGeek event id mapped to >1 TEvo event",
        count_sql="""select count(*) from (select sg_event_id from seatgeek_event_xref
                     group by 1 having count(*) > 1) t""",
        sample_sql="""select x.sg_event_id, x.tevo_event_id, left(e.name,70) as name, e.occurs_at_local
                      from seatgeek_event_xref x join events e on e.id = x.tevo_event_id
                      where x.sg_event_id in (select sg_event_id from seatgeek_event_xref
                                              group by 1 having count(*) > 1)
                      order by x.sg_event_id limit 10""",
    ),
    dict(
        id="espn_date_mismatch_unexplained", severity="CRITICAL",
        desc="TEvo vs ESPN time differs >6h, NOT explained by a rescheduled/TBD event",
        count_sql="""select count(*) from event_xref x
                     join events e on e.id = x.tevo_event_id
                     join espn_event_date_lookup d
                       on d.espn_event_id = x.espn_event_id and d.espn_league = x.espn_league
                     where abs(extract(epoch from (e.occurs_at_local::timestamptz - d.game_at_utc))) > 6*3600
                       and e.name not ilike '%resched%' and e.name not ilike '%tbd%'
                       and e.name not ilike '%if necessary%'""",
        sample_sql="""select e.id, left(e.name,70) as name, e.occurs_at_local, d.game_at_utc
                      from event_xref x
                      join events e on e.id = x.tevo_event_id
                      join espn_event_date_lookup d
                        on d.espn_event_id = x.espn_event_id and d.espn_league = x.espn_league
                      where abs(extract(epoch from (e.occurs_at_local::timestamptz - d.game_at_utc))) > 6*3600
                        and e.name not ilike '%resched%' and e.name not ilike '%tbd%'
                        and e.name not ilike '%if necessary%'
                      order by abs(extract(epoch from (e.occurs_at_local::timestamptz - d.game_at_utc))) desc
                      limit 10""",
    ),
    dict(
        id="espn_date_mismatch_all", severity="WARN",
        desc="TEvo vs ESPN time differs >6h (incl. rescheduled games carrying stale ESPN dates)",
        count_sql="""select count(*) from event_xref x
                     join events e on e.id = x.tevo_event_id
                     join espn_event_date_lookup d
                       on d.espn_event_id = x.espn_event_id and d.espn_league = x.espn_league
                     where abs(extract(epoch from (e.occurs_at_local::timestamptz - d.game_at_utc))) > 6*3600""",
    ),
    dict(
        id="aq_date_mismatch", severity="WARN",
        desc="TEvo vs AQ hub event_date differs >26h",
        count_sql="""select count(*) from aq_event_map a
                     join events e on e.id = a.tevo_event_id
                     where a.event_date is not null
                       and abs(extract(epoch from (e.occurs_at_local::timestamp - a.event_date))) > 26*3600""",
        sample_sql="""select a.aq_short_event_id, a.tevo_event_id, left(e.name,60) as name,
                             e.occurs_at_local, a.event_date::text
                      from aq_event_map a join events e on e.id = a.tevo_event_id
                      where a.event_date is not null
                        and abs(extract(epoch from (e.occurs_at_local::timestamp - a.event_date))) > 26*3600
                      limit 10""",
    ),
    dict(
        id="aq_duplicate_tevo", severity="WARN",
        desc="TEvo event claimed by >1 AQ row (playoff alternate branches are a benign subset)",
        count_sql="""select count(*) from (select tevo_event_id from aq_event_map
                     where tevo_event_id is not null group by 1 having count(*) > 1) t""",
    ),
    dict(
        id="espn_duplicate", severity="WARN",
        desc="ESPN game claimed by >1 TEvo event (conditional playoff branches are a benign subset)",
        count_sql="""select count(*) from (select espn_event_id, espn_league from event_xref
                     group by 1,2 having count(*) > 1) t""",
    ),
    dict(
        id="aq_orphan", severity="CRITICAL",
        desc="aq_event_map.tevo_event_id pointing at a nonexistent event",
        count_sql="""select count(*) from aq_event_map a
                     left join events e on e.id = a.tevo_event_id
                     where a.tevo_event_id is not null and e.id is null""",
    ),
    dict(
        id="td_orphan", severity="INFO",
        desc="ticketsdata_event_xref.event_id not found in events — likely a DIFFERENT id space; "
             "do not join it to events.id until confirmed (map correction pending)",
        count_sql="""select count(*) from ticketsdata_event_xref t
                     left join events e on e.id = t.event_id where e.id is null""",
    ),
    dict(
        id="sg_coverage_60d", severity="INFO",
        desc="upcoming-60d events WITHOUT a SeatGeek mapping (visibility, not an error)",
        count_sql="""select count(*) from events e
                     left join seatgeek_event_xref x on x.tevo_event_id = e.id
                     where e.occurs_at_local::timestamp between now() and now() + interval '60 days'
                       and x.tevo_event_id is null""",
    ),
    dict(
        id="low_confidence_matches", severity="WARN",
        desc="accepted matches with confidence < 0.8 in event_match_attempts",
        count_sql="""select count(*) from event_match_attempts
                     where match_confidence is not null and match_confidence < 0.8
                       and matched_tevo_event_id is not null""",
    ),
    dict(
        id="open_conflicts", severity="WARN",
        desc="unresolved rows in entity_xref_conflicts",
        count_sql="select count(*) from entity_xref_conflicts where status not in ('resolved')",
    ),
]


def main():
    show_samples = "--samples" in sys.argv
    conn = psycopg.connect(os.environ["DATABASE_URL"])
    conn.execute("SET default_transaction_read_only = on")
    conn.execute("SET statement_timeout = '60s'")

    criticals = 0
    print(f"{'check':36} {'sev':8} {'count':>8}")
    print("-" * 56)
    for c in CHECKS:
        with conn.cursor() as cur:
            cur.execute(c["count_sql"])
            n = cur.fetchone()[0]
        flag = ""
        if n and c["severity"] == "CRITICAL":
            criticals += 1
            flag = "  ← FIX"
        print(f"{c['id']:36} {c['severity']:8} {n:>8}{flag}")
        if n and show_samples and c.get("sample_sql"):
            with conn.cursor() as cur:
                cur.execute(c["sample_sql"])
                cols = [d.name for d in cur.description]
                for row in cur.fetchall():
                    print("      " + " | ".join(f"{k}={v}" for k, v in zip(cols, row)))

    print("-" * 56)
    print(f"{criticals} critical check(s) firing. "
          f"{'Run with --samples to see offending rows.' if criticals and not show_samples else ''}")
    sys.exit(min(criticals, 125))


if __name__ == "__main__":
    main()
