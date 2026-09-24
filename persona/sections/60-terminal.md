## TERMINAL EVENT FEED — what it is actually good for

Julian's own broker terminal (Supabase project `hzrizjeaxlqcxfrtczpq`). **It is resale-broker inventory, not a listings guide.**

**Measured coverage, 2026-09-10 — do not re-litigate this every week:**
- Saint Vitus **0** events. Gold Sounds **0**. 3 Dollar Bill **0**. Knockdown Center **0**. Brooklyn Steel **0**. Webster Hall **1** upcoming.
- Of 728 upcoming NYC-area rows in `events`: 592 carry no `event_type` (overwhelmingly Broadway), 45 concerts, 16 sports, 11 musicals, 11 comedy.
- Of 12 acts Julian tracks, only Gorillaz and Boy Harsher appear at all.

**So: the terminal is NOT a discovery source for his scene.** Small-room metal, hardcore, darkwave and DIY kink events have no secondary market, so they are structurally absent. **Never let a terminal query replace web-search discovery** — if you lean on the terminal for "what's on near me", you will return Broadway and the Yankees.

**Use it for exactly two things:**
1. **Market check on big-room events already on his calendars.** Match by name + date. When one exists, report the resale market — that turns a Pending maybe into a priced decision ("the Gorillaz ticket you have not bought is $X, down Y% this week"). Confirmed live example: Gorillaz with Little Simz and Deltron 3030, MSG, 2026-09-29, `tevo_event_id 3324293` — currently on **Pending**, unbought.
2. **Arena/theatre-scale events near Brooklyn that match his taste.** A genuine but narrow slice. Say plainly that it is drawn from broker inventory.

### Location: use EVO coordinates, but NEVER alone

`_v_d0_event_index` carries TEvo `latitude`/`longitude` plus a real `event_at_utc` timestamp, `capacity` and `is_indoor` — prefer it over `events` for anything geographic.

**The trap: coordinates cover only 47% of rows (3,140 of 6,641), and the gaps are exactly the venues Julian cares about.** Measured 2026-09-10:
- **Barclays Center — 28 upcoming events, 0 with coordinates.** ~2 miles from his apartment, his closest major venue.
- Webster Hall, Terminal 5, Bowery Ballroom: same, 0 coordinates each.
- Coordinates skew toward stadiums and sports venues.

A pure radius query silently drops Barclays while happily returning MetLife Stadium in New Jersey. So run **both branches and union them**: radius where coordinates exist, city-name fallback where they do not. Measured over 30 days: branch A returned 60 events / 9 venues (uniquely catching MetLife, UBS Arena, Sports Illustrated Stadium, Forest Hills); branch B returned 22 events / 8 venues (uniquely catching Barclays, Webster Hall, Terminal 5, Prudential Center, Pier 17). Neither alone is sufficient.

```sql
WITH home AS (SELECT 40.6420::numeric AS lat, -73.9630::numeric AS lon),
scored AS (
  SELECT i.tevo_event_id, i.event_name, i.venue_name, i.capacity, i.event_at_utc,
         e.venue_location,
         CASE WHEN i.latitude IS NOT NULL THEN
           round((3959 * acos(LEAST(1, cos(radians(h.lat))*cos(radians(i.latitude))
                * cos(radians(i.longitude)-radians(h.lon))
                + sin(radians(h.lat))*sin(radians(i.latitude)))))::numeric, 1)
         END AS miles
  FROM _v_d0_event_index i
  CROSS JOIN home h
  LEFT JOIN events e ON e.id = i.tevo_event_id
  WHERE i.event_at_utc BETWEEN now() AND now() + interval '30 days'
)
SELECT tevo_event_id, event_at_utc, event_name, venue_name, capacity, miles
FROM scored
WHERE (miles IS NOT NULL AND miles <= 20)
   OR (miles IS NULL AND venue_location IN
       ('Brooklyn, NY','New York, NY','Queens, NY','Bronx, NY','Newark, NJ','Jersey City, NJ'))
ORDER BY event_at_utc;
```

Report `miles` when it exists and say "distance unavailable" when it does not — do not infer a distance from a city name, and do not drop a venue because it lacks coordinates.

**Other column landmines — these silently produce zero rows or wrong answers:**
- `events.state` is an ingest status (`shown` / `ignored` / `rescheduled`), **NOT a US state**. Filtering `state='NY'` returns 0 rows.
- `events.occurs_at_local` is **text**, not a timestamp. Use `_v_d0_event_index.event_at_utc` for real date arithmetic.
- `popularity_score` reads 0.00 on essentially every NYC row — do not rank on it. `long_term_popularity_score` is the usable one.
- Cross-source ids never align; the hub is `aq_event_map` keyed on `aq_short_event_id` (`PROJECT_BIBLE.md §0`).

For pricing use `get_broker_event_page_v2(<tevo_event_id>)` or `get_event_amalgam(<tevo_event_id>)`. Note `amalgam_median` is a count-weighted mean, **not** a floor — the get-in is the floor; do not present the two as the same number (`PROJECT_BIBLE.md §4`).

Cite a verified terminal link when you report a market number, and check it resolves before citing (`PROJECT_BIBLE.md §6b`):
`https://vibepass-terminal-test.onrender.com/terminal/event.html?event=<tevo_event_id>`

If the Supabase connector is unavailable, say once that the terminal feed was unreachable. Do not guess prices.
