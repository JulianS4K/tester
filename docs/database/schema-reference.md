# Schema Reference — `public` (auto-generated)

> Generated from the live Postgres catalog. `!` after a type = NOT NULL. Row counts are live estimates (`pg_stat_user_tables.n_live_tup`) and drift between refreshes. This file is the exhaustive per-table reference; see `README.md` for the narrative map and join graph.

**207 base tables.** Jump to a domain:

- [Core: events, listings & metrics (TEvo / EVO source of truth)](#core-events-listings--metrics-tevo--evo-source-of-truth) (27)
- [Cross-source identity map (AQ hub, xrefs, canonical IDs)](#crosssource-identity-map-aq-hub-xrefs-canonical-ids) (15)
- [EVO (TEvo/TicketEvolution) orders & polling](#evo-tevoticketevolution-orders--polling) (6)
- [SeatGeek (listings, sales, orders, matching)](#seatgeek-listings-sales-orders-matching) (24)
- [SeatData source](#seatdata-source) (11)
- [TicketsData (SH/GT/VD/TP/TM multi-platform)](#ticketsdata-shgtvdtptm-multiplatform) (14)
- [AXS (primary + marketplace)](#axs-primary--marketplace) (8)
- [Other resale orders (TickPick, Vivid)](#other-resale-orders-tickpick-vivid) (4)
- [Movers & market-signal indices](#movers--marketsignal-indices) (3)
- [Venues & seat maps](#venues--seat-maps) (10)
- [Performers (metadata, enrichment, baselines)](#performers-metadata-enrichment-baselines) (9)
- [ESPN (teams, athletes, injuries, scores, news)](#espn-teams-athletes-injuries-scores-news) (14)
- [Weather & NWS alerts (demand signals)](#weather--nws-alerts-demand-signals) (6)
- [Macro indicators (FRED)](#macro-indicators-fred) (3)
- [Social / news signals](#social--news-signals) (6)
- [Calendar & context reference](#calendar--context-reference) (4)
- [Chatbot & conversation analytics](#chatbot--conversation-analytics) (12)
- [EXOS (own primary-ticketing platform)](#exos-own-primaryticketing-platform) (14)
- [Tournaments / non-team events](#tournaments--nonteam-events) (4)
- [Ops: scheduling, policy & coverage](#ops-scheduling-policy--coverage) (7)
- [Alerts](#alerts) (3)
- [Other / reference](#other--reference) (3)


## Core: events, listings & metrics (TEvo / EVO source of truth)

### `listings_snapshots` &nbsp; ≈86.2M rows

**PK** `event_id, captured_at, tevo_ticket_group_id`  •  **FK** `aq_short_event_id`→`aq_event_map.aq_short_event_id`; `performer_short_id`→`aq_performer_map.performer_short_id`; `venue_short_id`→`aq_venue_map.venue_short_id`

```
event_id bigint!,
captured_at timestamp with time zone!,
tevo_ticket_group_id bigint!,
section text,
row text,
quantity integer,
retail_price numeric(12,2),
wholesale_price numeric(12,2),
format text,
splits integer[],
wheelchair boolean,
instant_delivery boolean,
eticket boolean,
is_ancillary boolean!,
type text,
office_id bigint,
office_name text,
brokerage_id bigint,
brokerage_name text,
is_owned boolean!,
aq_short_event_id text,
venue_short_id text,
performer_short_id text,
prev_retail_price numeric,
prev_quantity integer
```
### `section_metrics` &nbsp; ≈29.0M rows

**PK** `event_id, captured_at, section`

```
event_id bigint!,
captured_at timestamp with time zone!,
section text!,
is_ancillary boolean!,
tickets_count integer,
groups_count integer,
retail_min numeric(12,2),
retail_median numeric(12,2),
retail_mean numeric(12,2),
retail_max numeric(12,2),
sections_count integer,
retail_p25 numeric,
retail_p75 numeric,
retail_p90 numeric,
retail_sum numeric,
wholesale_min numeric,
wholesale_median numeric,
wholesale_mean numeric,
wholesale_max numeric,
getin_price numeric,
owned_groups_count integer,
owned_tickets_count integer,
owned_share numeric,
owned_median_retail numeric,
zone text,
zone_source text
```
### `event_section_row_snapshots` &nbsp; ≈9.2M rows

**PK** `event_id, source, captured_at, section_norm, row`  •  **FK** `event_id`→`events.id`

```
event_id bigint!,
source text!,
captured_at timestamp with time zone!,
section_norm text!,
row text!,
quantity integer!,
listings_count integer!,
min_price numeric(10,2),
median_price numeric(10,2),
max_price numeric(10,2),
owned_quantity integer!,
content_hash text!,
meta jsonb!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `listing_xref` &nbsp; ≈1.0M rows

**PK** `id`

```
id bigint!,
tevo_ticket_group_id bigint!,
sg_listing_id text!,
captured_at_tevo timestamp with time zone!,
captured_at_sg timestamp with time zone!,
confidence numeric(3,2)!,
match_method text!,
price_spread_pct numeric(5,2),
notes text,
meta jsonb!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `event_alerts` &nbsp; ≈492K rows

**PK** `id`  •  **FK** `tevo_event_id`→`events.id`

```
id bigint!,
rule_key text!,
tevo_event_id bigint!,
fired_at timestamp with time zone,
severity text,
message text!,
payload jsonb,
acknowledged_at timestamp with time zone,
acknowledged_by text
```
### `event_metrics` &nbsp; ≈400K rows

**PK** `event_id, captured_at`

```
event_id bigint!,
captured_at timestamp with time zone!,
tickets_count integer,
groups_count integer,
sections_count integer,
median_group_size numeric(8,2),
ancillary_groups integer,
ancillary_tickets integer,
retail_min numeric(12,2),
retail_p25 numeric(12,2),
retail_median numeric(12,2),
retail_mean numeric(12,2),
retail_p75 numeric(12,2),
retail_p90 numeric(12,2),
retail_max numeric(12,2),
retail_sum numeric(14,2),
wholesale_min numeric(12,2),
wholesale_median numeric(12,2),
wholesale_mean numeric(12,2),
wholesale_max numeric(12,2),
getin_price numeric(12,2),
top5_concentration numeric(5,4),
price_dispersion numeric,
tail_premium numeric,
owned_groups_count integer,
owned_tickets_count integer,
owned_share numeric,
owned_median_retail numeric,
splits_min_q integer,
splits_listings_with_singles integer,
splits_listings_with_pairs integer,
splits_listings_with_3 integer,
splits_listings_with_4plus integer,
splits_listings_no_split integer,
splits_pct_pairs numeric(5,4),
splits_pct_singles numeric(5,4),
nonowned_median_retail numeric,
owned_p25 numeric,
owned_p75 numeric,
owned_p90 numeric
```
### `event_listing_snapshot_daily` &nbsp; ≈234K rows

3 daily point-in-time metric snapshots per event (morning/midday/evening ET). EVO source: event_metrics. SG source: seatgeek_event_metrics. Kept 1 year. Complements event_metrics (7-day TTL raw data) for long-term trend analysis.

**PK** `id`

```
id bigint!,
event_id bigint!,
snapshot_slot text!,
snapshot_date date!,
evo_tickets_count integer,
evo_owned_tickets integer,
evo_owned_median numeric(10,2),
evo_retail_getin numeric(10,2),
evo_retail_median numeric(10,2),
evo_retail_mean numeric(10,2),
evo_retail_min numeric(10,2),
evo_retail_max numeric(10,2),
sg_all_listings integer,
sg_all_tickets integer,
sg_owned_tickets integer,
sg_owned_median numeric(10,2),
sg_all_median numeric(10,2),
sg_all_getin numeric(10,2),
sg_all_mean numeric(10,2),
captured_at timestamp with time zone!,
td_sh_listings integer,
td_sh_median numeric(10,2),
td_gt_listings integer,
td_gt_median numeric(10,2),
td_vd_listings integer,
td_vd_median numeric(10,2),
td_combined_median numeric(10,2),
td_tp_listings integer,
td_tp_median numeric,
td_tm_listings integer,
td_tm_median numeric,
td_tm_resale_listings integer,
td_tm_resale_median numeric,
td_sh_getin numeric,
td_gt_getin numeric,
td_vd_getin numeric,
td_tp_getin numeric,
td_tm_getin numeric,
amalgam_getin numeric(12,2),
amalgam_source_count smallint,
amalgam_median numeric(12,2)
```
### `zone_metrics` &nbsp; ≈146K rows

Per-zone aggregates captured at same snapshot tick as event_metrics. zone_source: curated (performer_zones), fallback (derive_zone_fallback), unmapped.

**PK** `event_id, captured_at, zone`  •  **FK** `event_id`→`events.id`

```
event_id bigint!,
captured_at timestamp with time zone!,
zone text!,
zone_source text!,
tickets_count integer,
groups_count integer,
sections_count integer,
retail_min numeric,
retail_p25 numeric,
retail_median numeric,
retail_mean numeric,
retail_p75 numeric,
retail_p90 numeric,
retail_max numeric,
retail_sum numeric,
wholesale_min numeric,
wholesale_median numeric,
wholesale_mean numeric,
wholesale_max numeric,
getin_price numeric,
owned_groups_count integer,
owned_tickets_count integer,
owned_share numeric,
owned_median_retail numeric
```
### `event_sentiment` &nbsp; ≈83K rows

**PK** `event_id, captured_at`

```
event_id bigint!,
captured_at timestamp with time zone!,
sentiment_index numeric!,
tix_per_hour numeric,
getin_per_hour numeric,
owned_share_change numeric,
pairs_change_per_hour numeric,
computed_at timestamp with time zone!
```
### `watch_sources` &nbsp; ≈13K rows

**PK** `event_id, source_type, source_id`  •  **FK** `event_id`→`events.id`

```
event_id bigint!,
source_type text!,
source_id bigint!,
source_label text,
first_seen timestamp with time zone!
```
### `events` &nbsp; ≈10K rows

**PK** `id`

```
id bigint!,
name text,
occurs_at_local text,
state text,
venue_id bigint,
venue_name text,
venue_location text,
primary_performer_id bigint,
primary_performer_name text,
performer_ids bigint[],
last_seen timestamp with time zone,
is_chat_tracked boolean,
chat_first_pinged_at timestamp with time zone,
chat_last_pinged_at timestamp with time zone,
chat_ping_count integer,
configuration_id integer,
configuration_name text,
seating_chart_medium text,
seating_chart_large text,
fanvenues_key text,
popularity_score numeric,
long_term_popularity_score numeric,
event_type text
```
### `runs` &nbsp; ≈4K rows

**PK** `id`

```
id bigint!,
started_at timestamp with time zone!,
finished_at timestamp with time zone,
events_collected integer,
stats_errors integer
```
### `event_xref` &nbsp; ≈2K rows

TEvo event -> ESPN event mapping. Populated lazily by the espn Edge Function when an event_id is first looked up.

**PK** `tevo_event_id`  •  **FK** `tevo_event_id`→`events.id`

```
tevo_event_id bigint!,
espn_event_id text!,
espn_league text!,
espn_slug text!,
matched_at timestamp with time zone!,
match_method text!,
meta jsonb
```
### `event_competitors_snapshot` &nbsp; ≈2K rows

**PK** `tevo_event_id`  •  **FK** `tevo_event_id`→`events.id`

```
tevo_event_id bigint!,
competitors_count integer,
competitors jsonb,
refreshed_at timestamp with time zone
```
### `event_xref_cleanup_archive_20260606` &nbsp; ≈155 rows

**PK** `tevo_event_id`

```
tevo_event_id bigint!,
espn_event_id text!,
espn_league text!,
espn_slug text!,
matched_at timestamp with time zone!,
match_method text!,
meta jsonb,
archived_at timestamp with time zone!,
archive_reason text
```
### `event_match_attempts` &nbsp; ≈40 rows

Audit log of every event-matching attempt. Lets the day-trader trace why an event landed in v_unmatched_events and what was tried.

**PK** `id`

```
id bigint!,
source_key text!,
source_event_id text!,
attempted_at timestamp with time zone,
matched_tevo_event_id bigint,
match_method text,
match_confidence numeric(3,2),
reason_unmatched text,
candidate_pool_size integer,
notes jsonb
```
### `watchlist` &nbsp; ≈17 rows

**PK** `id`

```
id bigint!,
kind text!,
ext_id bigint!,
label text,
added_at timestamp with time zone!
```
### `event_watchlist` &nbsp; ≈4 rows

**PK** `id`

```
id bigint!,
user_email text!,
tevo_event_id bigint!,
event_name text,
occurs_at_local text,
venue_name text,
alert_enabled boolean!,
added_at timestamp with time zone!
```
### `event_pulls` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
event_id bigint!,
source text!,
requester text,
requested_at timestamp with time zone!,
served_from text!,
snapshot_age_seconds integer,
tevo_calls integer!,
meta jsonb
```
### `leads` &nbsp; ≈0 rows

Reserve-via-rep submissions. HARD RESTRICT (rule 15): no auto-purchase.

**PK** `id`  •  **FK** `event_id`→`events.id`

```
id bigint!,
event_id bigint,
ticket_group_id bigint,
qty integer,
zone text,
max_price numeric,
budget_basis text,
name text,
email text,
phone text,
notes text,
channel text!,
source_url text,
anon_id text,
status text!,
assigned_to text,
created_at timestamp with time zone!,
updated_at timestamp with time zone!,
contacted_at timestamp with time zone,
closed_at timestamp with time zone
```
### `performer_zone_rules` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `zone_id`→`performer_zones.id`

```
id bigint!,
zone_id bigint!,
section_from text!,
section_to text!,
row_from text,
row_to text,
created_at timestamp with time zone!
```
### `performer_zones` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
performer_id bigint!,
venue_id bigint!,
name text!,
display_order integer!,
created_at timestamp with time zone!,
source text!
```
### `settings` &nbsp; ≈0 rows

**PK** `key`

```
key text!,
value text!,
updated_at timestamp with time zone!
```
### `share_links` &nbsp; ≈0 rows

Revocable storefront share links — /s/{id} resolves to one event with saved filters.

**PK** `id`  •  **FK** `event_id`→`events.id`

```
id text!,
event_id bigint!,
filters jsonb!,
note text,
created_at timestamp with time zone!,
updated_at timestamp with time zone!,
created_by uuid,
expires_at timestamp with time zone,
revoked_at timestamp with time zone,
view_count integer!,
last_viewed_at timestamp with time zone
```
### `snapshots` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `event_id`→`events.id`

```
id bigint!,
event_id bigint!,
captured_at timestamp with time zone!,
ticket_groups_count integer,
tickets_count integer,
retail_price_min numeric,
retail_price_avg numeric,
retail_price_max numeric,
retail_price_sum numeric,
wholesale_price_avg numeric,
wholesale_price_sum numeric
```
### `tevo_ticket_groups_cache` &nbsp; ≈0 rows

Short-TTL cache of /v9/ticket_groups responses. Used by chat fn to deduplicate redundant TEvo fetches across get_event_zones + find_listings + find_better_seats.

**PK** `event_id`

```
event_id bigint!,
payload jsonb!,
captured_at timestamp with time zone!,
expires_at timestamp with time zone!
```
### `zone_rules` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
match_type text!,
section_pattern text!,
zone text!,
priority integer!,
notes text,
active boolean!,
created_at timestamp with time zone!
```


## Cross-source identity map (AQ hub, xrefs, canonical IDs)

### `aq_event_map` &nbsp; ≈8K rows

Operator-curated cross-source event map from AQ/Automatiq. aq_short_event_id (7-char) is the canonical primary key; every order/listing table FKs back to this. Imported 2026-05-14 from kjUntitled spreadsheet.xlsx. 5,921 rows with non-null short IDs (238 unclassified rows dropped).

**PK** `id`  •  **FK** `performer_short_id`→`aq_performer_map.performer_short_id`; `venue_short_id`→`aq_venue_map.venue_short_id`

```
aq_short_event_id text!,
event_id_uuid uuid,
event_name text,
venue_name text,
event_date timestamp without time zone,
city text,
state text,
performer text,
category text,
vivid_event_id bigint,
sh_event_id bigint,
sg_event_id bigint,
tm_event_id bigint,
tnow_production_id text,
imported_at timestamp with time zone!,
id bigint!,
aq_source text,
venue_short_id text,
performer_short_id text,
tevo_event_id bigint,
sd_event_id bigint,
axs_event_id text
```
### `canonical_external_ids` &nbsp; ≈7K rows

**PK** `entity_kind, tevo_id, source_key`  •  **FK** `source_key`→`data_sources.source_key`

```
entity_kind text!,
tevo_id bigint!,
source_key text!,
external_id text!,
external_name text,
match_method text,
match_confidence numeric(3,2),
matched_at timestamp with time zone,
meta jsonb
```
### `aq_performer_map` &nbsp; ≈863 rows

**PK** `performer_short_id`

```
performer_short_id text!,
performer_name text!,
performer_uuid uuid,
tevo_performer_id bigint,
category text,
aq_source text,
imported_at timestamp with time zone!,
aliases text[]
```
### `aq_venue_map` &nbsp; ≈712 rows

**PK** `venue_short_id`

```
venue_short_id text!,
venue_name text!,
city text,
state text,
country text,
tevo_venue_id bigint,
sg_venue_id bigint,
tickpick_venue_id bigint,
latitude numeric,
longitude numeric,
aq_source text,
imported_at timestamp with time zone!
```
### `aq_tevo_search_attempts` &nbsp; ≈510 rows

Per-hub-row attempt ledger for the AQ->TEvo cold-search bridge (aq-to-tevo-search-bridge edge fn). Keyed on aq_event_map.id. Mirrors sg_tevo_search_attempts. no_results/low_score rows are retried after the backoff window; matched rows are terminal.

**PK** `aq_id`  •  **FK** `aq_id`→`aq_event_map.id`

```
aq_id bigint!,
attempted_at timestamp with time zone!,
result text!,
meta jsonb
```
### `order_status_xref` &nbsp; ≈14 rows

**PK** `id`

```
id bigint!,
source text!,
source_status text!,
source_status_kind text,
canonical_status text!,
is_terminal boolean!,
is_sale_succeeded boolean!,
notes text,
created_at timestamp with time zone
```
### `bridge_event_xref` &nbsp; ≈0 rows

**PK** `exos_event_id`  •  **FK** `exos_event_id`→`exos_events.id`

```
exos_event_id uuid!,
aq_short_event_id text,
tevo_event_id bigint,
sg_event_id bigint,
match_method text,
matched_at timestamp with time zone,
meta jsonb,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `broker_xref` &nbsp; ≈0 rows

**PK** `tevo_brokerage_id, sg_seller_id`

```
tevo_brokerage_id bigint!,
tevo_brokerage_name text,
sg_seller_id text!,
sg_seller_name text,
confidence numeric(3,2)!,
match_method text!,
matched_by text!,
notes text,
meta jsonb!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `data_source_field_map` &nbsp; ≈0 rows

**PK** `source_key, entity_kind, canonical_field`  •  **FK** `source_key`→`data_sources.source_key`

```
source_key text!,
entity_kind text!,
canonical_field text!,
source_field text!,
source_table text,
example_value text,
notes text
```
### `data_sources` &nbsp; ≈0 rows

**PK** `source_key`

```
source_key text!,
display_name text!,
kind text!,
host text,
auth_method text,
read_only boolean,
added_at timestamp with time zone,
notes text
```
### `entity_xref_conflicts` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
layer text!,
tevo_id text!,
candidate_sg_ids text[]!,
confidence_scores numeric(3,2)[]!,
status text!,
resolved_at timestamp with time zone,
resolution_note text,
meta jsonb!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `entity_xref_overrides` &nbsp; ≈0 rows

**PK** `layer, tevo_id`

```
layer text!,
tevo_id text!,
sg_id text!,
reason text,
set_by text!,
meta jsonb!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `matchup_xref` &nbsp; ≈0 rows

**PK** `matchup_id`

```
matchup_id bigint!,
team_a_id bigint!,
team_b_id bigint!,
team_a_name text,
team_b_name text,
first_seen_event bigint,
last_seen_at timestamp with time zone!
```
### `order_fee_schedule` &nbsp; ≈0 rows

**PK** `source`

```
source text!,
buyer_fee_pct numeric(5,4),
seller_fee_pct numeric(5,4),
fixed_fee_per_order numeric(8,2),
notes text,
effective_from date
```
### `taxonomy_xref` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
canonical_label text!,
canonical_kind text!,
tevo_event_type text,
tevo_top_category text,
espn_league text,
sg_event_type text,
sd_event_type text,
notes text,
created_at timestamp with time zone
```


## EVO (TEvo/TicketEvolution) orders & polling

### `evo_listings_poll_state` &nbsp; ≈10K rows

**PK** `event_id`  •  **FK** `event_id`→`events.id`

```
event_id bigint!,
last_polled_listings_at timestamp with time zone,
listings_polls_today integer!,
budget_day date!
```
### `evo_orders_pending` &nbsp; ≈2K rows

**PK** `id`

```
id bigint!,
request_id bigint!,
page integer!,
query_str text,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
rows_persisted integer
```
### `evo_order_items` &nbsp; ≈1K rows

**PK** `id`  •  **FK** `aq_short_event_id`→`aq_event_map.aq_short_event_id`; `evo_order_id`→`evo_orders.evo_order_id`

```
id bigint!,
evo_order_id bigint!,
evo_item_id bigint,
quantity integer,
price numeric,
ticket_group_id bigint,
ticket_group_remote_id text,
ticket_group_office_id bigint,
ticket_group_section text,
ticket_group_row text,
ticket_group_seats jsonb,
ticket_group_quantity integer,
ticket_group_retail_price numeric,
ticket_group_wholesale_price numeric,
ticket_group_external_notes text,
event_id bigint,
event_name text,
occurs_at timestamp with time zone,
venue_id bigint,
venue_name text,
eticket_available boolean,
eticket_delivery text,
eticket_downloaded_at timestamp with time zone,
eticket_downloaded_by bigint,
pulled_at timestamp with time zone!,
raw jsonb!,
aq_short_event_id text,
venue_short_id text,
performer_short_id text
```
### `evo_orders` &nbsp; ≈1K rows

**PK** `evo_order_id`

```
evo_order_id bigint!,
state text,
type text,
fraud_check_status text,
total numeric,
subtotal numeric,
tax numeric,
shipping numeric,
service_fee numeric,
refunded numeric,
balance numeric,
additional_expense numeric,
reference text,
invoice_number text,
po_number text,
instructions text,
partner boolean,
buyer_id bigint,
buyer_name text,
buyer_brokerage_id bigint,
buyer_brokerage_name text,
seller_id bigint,
seller_name text,
seller_brokerage_id bigint,
seller_brokerage_name text,
client_id bigint,
client_name text,
client_phone text,
client_email text,
evo_created_at timestamp with time zone,
evo_updated_at timestamp with time zone,
hold_placed_at timestamp with time zone,
hold_expires_at timestamp with time zone,
pulled_at timestamp with time zone!,
last_seen_at timestamp with time zone!,
raw jsonb!,
tevo_event_id bigint,
aq_short_event_id text
```
### `evo_event_backfill_pending` &nbsp; ≈14 rows

**PK** `tevo_event_id`

```
tevo_event_id bigint!,
request_id bigint!,
reason text,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
status text
```
### `evo_only_patterns` &nbsp; ≈2 rows

Allowlist driving tag_evo_only_events(): performer/venue ILIKE patterns whose matching TEvo events are intentionally EVO-only (no cross-source twin exists).

**PK** `id`

```
id bigint!,
performer_pattern text,
venue_pattern text,
note text,
enabled boolean!,
created_at timestamp with time zone!
```


## SeatGeek (listings, sales, orders, matching)

### `seatgeek_listings_snapshots` &nbsp; ≈14.1M rows

**PK** `id`  •  **FK** `aq_short_event_id`→`aq_event_map.aq_short_event_id`; `performer_short_id`→`aq_performer_map.performer_short_id`; `sg_event_id`→`sg_events_canonical.sg_event_id`; `tevo_event_id`→`events.id`

```
id bigint!,
tevo_event_id bigint,
sg_event_id bigint!,
captured_at timestamp with time zone!,
sglid bigint,
display_id text,
retail_price_all_in numeric,
broadcast_price numeric,
deal_quality_score numeric,
quantity integer,
splits jsonb,
section text,
row text,
is_broker_owned boolean,
is_b2b boolean,
is_instant_download boolean,
is_sro boolean,
has_limited_view boolean,
is_wheelchair_acc boolean,
ada_details text,
delivery_method text,
in_hand_date date,
market_source text,
stock_type text,
seller_notes text,
endpoint text!,
content_hash text!,
raw jsonb,
aq_short_event_id text,
venue_short_id text,
performer_short_id text,
prev_broadcast_price numeric,
prev_quantity integer
```
### `seatgeek_sales_snapshots` &nbsp; ≈11.5M rows

**PK** `id`  •  **FK** `aq_short_event_id`→`aq_event_map.aq_short_event_id`; `sg_event_id`→`sg_events_canonical.sg_event_id`; `tevo_event_id`→`events.id`

```
id bigint!,
tevo_event_id bigint,
sg_event_id bigint!,
pulled_at timestamp with time zone!,
sg_sale_id text,
broadcast_price numeric,
quantity integer,
section text,
row text,
stock_type text,
delivery_method text,
in_hand_date date,
is_instant boolean,
seller_notes text,
sale_at_utc timestamp with time zone,
raw jsonb,
aq_short_event_id text,
venue_short_id text
```
### `sg_broker_pending` &nbsp; ≈269K rows

**PK** `id`

```
id bigint!,
request_id bigint!,
scope text!,
sg_event_id bigint!,
fired_at timestamp with time zone!,
resolved_at timestamp with time zone,
rows_persisted integer
```
### `seatgeek_event_metrics` &nbsp; ≈127K rows

Per-event SG-side aggregates. A1 2026-05-18: dropped legacy cost_* columns (SellerDirect-only populator retired). Active columns: listings_all_* (PR #204), listings_owned_* (PR #204), sold_* (PR #161), orders_* (legacy), edelivery_share/instant_share/fill_rate/unique_sections/unique_rows (legacy from compute_seatgeek_event_metrics path — now also inert post-drop).

**PK** `id`  •  **FK** `sg_event_id`→`sg_events_canonical.sg_event_id`; `tevo_event_id`→`events.id`

```
id bigint!,
tevo_event_id bigint,
sg_event_id bigint,
captured_at timestamp with time zone!,
listings_count integer,
tickets_count integer,
orders_open integer,
orders_pending integer,
orders_confirmed integer,
orders_fulfilled integer,
orders_delivered integer,
orders_cancelled integer,
sold_quantity integer,
sold_gross numeric,
sold_price_min numeric,
sold_price_median numeric,
sold_price_mean numeric,
sold_price_max numeric,
edelivery_share numeric,
instant_share numeric,
unique_sections integer,
unique_rows integer,
fill_rate numeric,
listings_all_count integer,
listings_all_tickets integer,
listings_all_min numeric,
listings_all_p25 numeric,
listings_all_median numeric,
listings_all_mean numeric,
listings_all_p75 numeric,
listings_all_p90 numeric,
listings_all_max numeric,
listings_owned_count integer,
listings_owned_tickets integer,
listings_owned_min numeric,
listings_owned_p25 numeric,
listings_owned_median numeric,
listings_owned_mean numeric,
listings_owned_p75 numeric,
listings_owned_p90 numeric,
listings_owned_max numeric
```
### `sg_seller_pending` &nbsp; ≈12K rows

**PK** `id`

```
id bigint!,
request_id bigint!,
scope text!,
page integer!,
per_page integer,
status_csv text,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
rows_persisted integer
```
### `sg_event_priority_state` &nbsp; ≈8K rows

**PK** `sg_event_id`

```
sg_event_id bigint!,
sg_event_name text,
sg_venue_name text,
sg_datetime_utc timestamp with time zone,
aq_short_event_id text,
owned_count_last_7d integer!,
active_listings_last_7d integer!,
tier text!,
hours_to_event numeric,
last_polled_sales_at timestamp with time zone,
last_polled_listings_at timestamp with time zone,
sales_polls_today integer!,
listings_polls_today integer!,
budget_day date!,
computed_at timestamp with time zone!,
tevo_event_id bigint,
last_fired_listings_at timestamp with time zone,
force_track boolean!,
force_track_since timestamp with time zone
```
### `sg_events_canonical` &nbsp; ≈8K rows

One row per SG event we have ANY data on (seller_listings, /v2 pull, or just metadata). tevo_event_id NULL = unmatched. Auto-matcher iterates this table to fill links over time.

**PK** `sg_event_id`  •  **FK** `tevo_event_id`→`events.id`

```
sg_event_id bigint!,
sg_event_name text!,
sg_event_date date,
sg_datetime_utc timestamp with time zone,
sg_category text,
sg_url text,
sg_venue_id bigint,
sg_venue_name text,
sg_venue_city text,
sg_venue_state text,
sg_venue_country text,
sg_venue_address text,
has_seller_listings boolean,
has_v2_listings_pulled boolean,
last_v2_pull_at timestamp with time zone,
last_v2_status integer,
last_v2_listings_count integer,
tevo_event_id bigint,
match_method text,
match_confidence numeric(3,2),
matched_at timestamp with time zone,
raw_event_jsonb jsonb,
created_at timestamp with time zone,
updated_at timestamp with time zone,
has_orders boolean,
has_broker_sales boolean,
match_status text,
last_60d_pulled_at timestamp with time zone
```
### `sg_market_chart` &nbsp; ≈5K rows

**PK** `chart_date, sg_event_id`

```
chart_date date!,
sg_event_id bigint!,
rank integer!,
chart_score numeric!,
pct_volume numeric,
pct_median numeric,
ma7_volume numeric,
ma7_median numeric,
ma7_gross numeric,
days_with_sales integer,
prev_rank integer,
peak_rank integer,
days_on_chart integer,
tevo_event_id bigint,
sg_event_name text,
sg_venue_name text,
sg_datetime_utc timestamp with time zone,
sd_ma7_gross numeric,
sd_sales_7d integer,
sd_ma7_volume numeric,
sd_ma7_median numeric,
ma7_volume_blended numeric,
ma7_median_blended numeric,
ma7_gross_blended numeric,
blended_score numeric,
score_basis text
```
### `seatgeek_event_xref` &nbsp; ≈3K rows

**PK** `tevo_event_id`  •  **FK** `sg_event_id`→`sg_events_canonical.sg_event_id`; `tevo_event_id`→`events.id`

```
tevo_event_id bigint!,
sg_event_id bigint!,
sg_event_name text,
sg_event_type text,
sg_event_location text,
sg_start_data timestamp with time zone,
sg_visible_until timestamp with time zone,
matched_at timestamp with time zone!,
match_method text!,
match_confidence numeric,
meta jsonb,
last_listings_at timestamp with time zone,
last_sales_at timestamp with time zone
```
### `seatgeek_seller_listings` &nbsp; ≈2K rows

**PK** `id`

```
id bigint!,
pulled_at timestamp with time zone!,
seller_listing_id text,
ticket_id text,
sg_listing_id bigint,
sg_event_id bigint!,
sg_event_name text,
sg_venue text,
sg_event_date date,
sg_event_time text,
quantity integer,
section text,
row text,
seat_from text,
seat_thru text,
notes text,
cost numeric,
is_edelivery boolean,
is_instant boolean,
in_hand_date date,
tevo_event_id bigint,
content_hash text!,
raw jsonb,
aq_short_event_id text,
venue_short_id text,
performer_short_id text
```
### `sg_tevo_search_attempts` &nbsp; ≈1K rows

Tracks TEvo search-bridge attempts per sg_event_id to avoid burning API on repeat queries. Cleared automatically when sg_event matches.

**PK** `sg_event_id`

```
sg_event_id bigint!,
attempted_at timestamp with time zone!,
result text!,
meta jsonb,
retries integer!
```
### `sg_listings_pending` &nbsp; ≈449 rows

**PK** `id`  •  **FK** `sg_event_id`→`sg_events_canonical.sg_event_id`

```
id bigint!,
request_id bigint!,
sg_event_id bigint!,
window_label text,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
rows_persisted integer
```
### `seatgeek_orders` &nbsp; ≈400 rows

**PK** `id`  •  **FK** `aq_short_event_id`→`aq_event_map.aq_short_event_id`; `sg_event_id`→`sg_events_canonical.sg_event_id`; `tevo_event_id`→`events.id`

```
id bigint!,
sg_order_id text!,
status text!,
created_at_sg timestamp with time zone,
delivery text,
delivery_method text,
stock_type text,
fulfillment_issue_message text,
pickup_email text,
pickup_phone text,
pickup_availability text,
pickup_location text,
pickup_instructions text,
sg_event_id bigint,
sg_event_name text,
sg_venue text,
sg_event_date date,
sg_event_time text,
sg_listing_id text,
sale_price numeric,
sale_row text,
sale_section text,
sale_quantity integer,
tevo_event_id bigint,
payment_total numeric,
payment_price numeric,
payment_fees numeric,
payment_tax numeric,
payment_delivery numeric,
pulled_at timestamp with time zone!,
last_status_at timestamp with time zone!,
raw jsonb,
aq_short_event_id text,
venue_short_id text
```
### `seatgeek_performer_xref` &nbsp; ≈132 rows

**PK** `tevo_performer_id`  •  **FK** `tevo_performer_id`→`performer_metadata.performer_id`

```
tevo_performer_id bigint!,
sg_performer_name text!,
match_method text!,
match_confidence numeric,
matched_at timestamp with time zone!,
meta jsonb
```
### `seatgeek_venue_xref` &nbsp; ≈38 rows

**PK** `tevo_venue_id`  •  **FK** `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
tevo_venue_id bigint!,
sg_venue_name text!,
match_method text!,
match_confidence numeric,
matched_at timestamp with time zone!,
meta jsonb
```
### `sg_priority_policy` &nbsp; ≈8 rows

**PK** `tier`

```
tier text!,
label text!,
interval_seconds integer!,
min_hours_to_event numeric,
max_hours_to_event numeric,
requires_owned boolean!,
daily_polls_per_event integer,
enabled boolean!,
sort_order integer!,
notes text,
updated_at timestamp with time zone!
```
### `sg_league_priority_config` &nbsp; ≈6 rows

**PK** `league`

```
league text!,
enabled boolean!,
note text,
updated_at timestamp with time zone!
```
### `sg_event_backfill_pending` &nbsp; ≈2 rows

**PK** `sg_event_id`  •  **FK** `sg_event_id`→`sg_events_canonical.sg_event_id`

```
sg_event_id bigint!,
request_id bigint!,
reason text,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
status text
```
### `seatgeek_historic_sales` &nbsp; ≈0 rows

Structured historic SG sales — parsed performers + season + section. Materialized from seatgeek_orders. Re-runnable via populate_seatgeek_historic_sales().

**PK** `id`  •  **FK** `sg_event_id`→`sg_events_canonical.sg_event_id`; `sg_order_row_id`→`seatgeek_orders.id`

```
id bigint!,
sg_order_row_id bigint!,
sg_event_id bigint,
sg_event_name text,
sg_event_date date,
sg_venue text,
sg_category text,
performer_away text,
performer_home text,
performer_headliner text,
performer_support text,
performer_count integer,
tevo_performer_id_away bigint,
tevo_performer_id_home bigint,
tevo_performer_id_headliner bigint,
season_label text,
season_type text,
event_year integer,
event_month integer,
event_dow integer,
section_kind text,
section_prefix text,
section_number integer,
section_raw text,
row_raw text,
sale_quantity integer,
sale_price numeric,
sale_total numeric,
status text,
derived_at timestamp with time zone
```
### `seatgeek_order_tickets` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `sg_order_id`→`seatgeek_orders.sg_order_id`

```
id bigint!,
sg_order_id text!,
ticket_index integer!,
section text,
row text,
seat text,
is_ada boolean,
is_ga boolean,
obstructed_view boolean,
barcode_type text,
barcode_value text,
mobile_passes jsonb,
raw jsonb
```
### `seatgeek_orders_resolved` &nbsp; ≈0 rows

**PK** `sg_order_id`  •  **FK** `sg_order_id`→`seatgeek_orders.sg_order_id`; `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
sg_order_id text!,
tevo_venue_id bigint,
venue_confidence numeric(3,2),
venue_match_method text,
tevo_performer_ids bigint[]!,
performer_confidences numeric(3,2)[]!,
performer_match_methods text[]!,
occurs_at_resolved timestamp with time zone,
resolution_method text!,
notes text,
meta jsonb!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `seatgeek_pull_log` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `sg_event_id`→`sg_events_canonical.sg_event_id`; `tevo_event_id`→`events.id`

```
id bigint!,
called_at timestamp with time zone!,
endpoint text!,
tevo_event_id bigint,
sg_event_id bigint,
http_status integer,
rows_returned integer,
cache_hit boolean,
refreshed_at_utc timestamp with time zone,
error_message text,
request_meta jsonb
```
### `seatgeek_seller_pull_log` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
called_at timestamp with time zone!,
endpoint text!,
status_filter text,
page integer,
http_status integer,
rows_returned integer,
total_reported integer,
bytes integer,
error_message text,
request_meta jsonb
```
### `sg_event_match_pending` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `sg_event_id`→`sg_events_canonical.sg_event_id`

```
id bigint!,
sg_event_id bigint!,
proposed_tevo_event_id bigint!,
proposed_by text,
proposed_at timestamp with time zone!,
reviewed_by text,
reviewed_at timestamp with time zone,
status text!,
notes text,
meta jsonb
```


## SeatData source

### `seatdata_sales_snapshots` &nbsp; ≈57K rows

**PK** `id`  •  **FK** `tevo_event_id`→`events.id`

```
id bigint!,
tevo_event_id bigint!,
sd_event_id bigint!,
pulled_at timestamp with time zone!,
sale_timestamp timestamp with time zone!,
quantity integer,
price numeric,
zone text,
section text,
row text,
content_hash text!
```
### `seatdata_pull_log` &nbsp; ≈240 rows

**PK** `id`  •  **FK** `tevo_event_id`→`events.id`

```
id bigint!,
called_at timestamp with time zone!,
endpoint text!,
tevo_event_id bigint,
sd_event_id bigint,
was_charged boolean!,
http_status integer,
rows_returned integer,
error_message text,
request_meta jsonb
```
### `seatdata_event_xref` &nbsp; ≈108 rows

**PK** `tevo_event_id`  •  **FK** `tevo_event_id`→`events.id`

```
tevo_event_id bigint!,
sd_event_id bigint!,
sd_tm_event_id text,
sd_std_event_id bigint,
sd_std_venue_id bigint,
matched_at timestamp with time zone!,
match_method text!,
match_confidence numeric,
meta jsonb,
last_paid_pull_at timestamp with time zone,
last_refresh_ts timestamp with time zone
```
### `seatdata_search_attempts` &nbsp; ≈53 rows

**PK** `tevo_event_id`

```
tevo_event_id bigint!,
attempted_at timestamp with time zone!,
result text!,
sd_event_id bigint,
meta jsonb,
retries integer!
```
### `seatdata_pull_budget` &nbsp; ≈6 rows

**PK** `day`

```
day date!,
paid_pulls integer!,
free_calls integer!,
daily_cap integer!,
monthly_cap integer!,
updated_at timestamp with time zone!
```
### `seatdata_event_stats` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `tevo_event_id`→`events.id`

```
id bigint!,
tevo_event_id bigint!,
sd_event_id bigint!,
pulled_at timestamp with time zone!,
snapshot_ts timestamp with time zone!,
total_listings_all integer,
total_listings_active integer,
listing_fill_rate numeric,
avg_price numeric,
median_price numeric,
get_in numeric,
get_in_qty2plus numeric,
zones jsonb,
content_hash text!
```
### `seatdata_listings_snapshots` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `tevo_event_id`→`events.id`

```
id bigint!,
tevo_event_id bigint!,
sd_event_id bigint!,
pulled_at timestamp with time zone!,
refresh_ts timestamp with time zone,
has_refreshed boolean,
sd_listing_id bigint!,
active boolean,
zone text,
section text,
row text,
quantity_start integer,
quantity integer,
price numeric,
content_hash text!
```
### `seatdata_performer_xref` &nbsp; ≈0 rows

**PK** `tevo_performer_id`  •  **FK** `tevo_performer_id`→`performer_metadata.performer_id`

```
tevo_performer_id bigint!,
sd_std_event_id bigint,
sd_performer_name text,
match_method text!,
match_confidence numeric,
matched_at timestamp with time zone!,
meta jsonb
```
### `seatdata_section_xref` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `tevo_event_id`→`events.id`

```
id bigint!,
tevo_event_id bigint!,
sd_section text!,
tevo_section text,
tevo_zone text,
match_method text!,
match_confidence numeric,
created_at timestamp with time zone
```
### `seatdata_venue_xref` &nbsp; ≈0 rows

**PK** `tevo_venue_id`  •  **FK** `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
tevo_venue_id bigint!,
sd_std_venue_id bigint,
sd_venue_name text,
sd_venue_slug text,
sd_venue_city text,
sd_venue_state text,
match_method text!,
match_confidence numeric,
matched_at timestamp with time zone!,
meta jsonb
```
### `seatdata_zone_xref` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `tevo_event_id`→`events.id`

```
id bigint!,
tevo_event_id bigint!,
sd_zone text!,
tevo_zone text,
match_method text!,
match_confidence numeric,
notes text,
created_at timestamp with time zone
```


## TicketsData (SH/GT/VD/TP/TM multi-platform)

### `ticketsdata_listings_snapshots` &nbsp; ≈5.7M rows

**PK** `id`

```
id bigint!,
event_id bigint!,
platform text!,
captured_at timestamp with time zone!,
td_listing_id text,
section text,
row text,
quantity integer,
list_price numeric,
price_with_fees numeric,
is_parking boolean!,
content_hash text!,
raw jsonb
```
### `ticketsdata_credit_usage` &nbsp; ≈5K rows

**PK** `id`

```
id bigint!,
called_at timestamp with time zone!,
event_id bigint,
platform text,
endpoint text!,
credits integer!,
status_code integer,
quota_remaining integer,
error_msg text,
reports integer!,
reports_remaining integer
```
### `td_pull_queue` &nbsp; ≈3K rows

**PK** `id`

```
id bigint!,
event_id bigint!,
platform text!,
event_url text!,
interval_tag text,
request_id bigint,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
status_code integer,
credits_used integer!,
retry_count integer!,
error_msg text,
rows_inserted integer,
created_at timestamp with time zone!
```
### `ticketsdata_event_xref` &nbsp; ≈1K rows

**PK** `event_id, platform`

```
event_id bigint!,
platform text!,
aq_short_event_id text,
event_url text,
td_event_id text,
active boolean!,
always_on boolean!,
rank_score numeric(8,4),
last_enqueued_at timestamp with time zone,
last_fetched_at timestamp with time zone,
enqueues_today integer!,
match_method text!,
meta jsonb!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!,
owned_tickets integer,
median_retail_price numeric(10,2),
manual_opt_in boolean!
```
### `td_discover_targets` &nbsp; ≈374 rows

**PK** `id`

```
id bigint!,
platform text!,
performer_url text,
venue_url text,
label text,
tevo_performer_id bigint,
active boolean!,
pending_request_id bigint,
last_discovered_at timestamp with time zone,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `td_poll_failures` &nbsp; ≈281 rows

**PK** `id`

```
id bigint!,
failed_at timestamp with time zone!,
event_id bigint,
platform text,
tier text,
status_code integer,
error_msg text,
request_id bigint
```
### `td_match_queue` &nbsp; ≈44 rows

**PK** `id`

```
id bigint!,
tevo_event_id bigint,
aq_short_event_id text,
seed_platform text,
seed_event_url text!,
purpose text!,
request_id bigint,
fired_at timestamp with time zone!,
resolved_at timestamp with time zone,
status_code integer,
retry_count integer!,
error_msg text
```
### `td_sg_performers` &nbsp; ≈20 rows

Allowlist of high-value unmapped TEvo performers to discover on SeatGeek via the TicketsData /events?platform=seatgeek search. Drives td_sg_discover(). Budget-gated.

**PK** `id`

```
id bigint!,
tevo_performer_id bigint,
performer_name text!,
performer_url text!,
active boolean!,
pending_discover_request_id bigint,
last_discovered_at timestamp with time zone,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `td_poll_policy` &nbsp; ≈18 rows

**PK** `platform, tier`

```
platform text!,
tier text!,
peak_min integer!,
offpeak_min integer!,
max_dte integer,
active boolean!,
updated_at timestamp with time zone!
```
### `td_gt_performers` &nbsp; ≈5 rows

**PK** `id`

```
id integer!,
performer_url text!,
performer_slug text,
team_name text,
active boolean!,
pending_discover_request_id bigint,
last_discovered_at timestamp with time zone,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `td_tm_performers` &nbsp; ≈2 rows

**PK** `id`

```
id bigint!,
performer_url text!,
active boolean!,
last_discovered_at timestamp with time zone,
pending_discover_request_id bigint,
updated_at timestamp with time zone!
```
### `td_tp_performers` &nbsp; ≈2 rows

**PK** `id`

```
id bigint!,
performer_url text!,
active boolean!,
last_discovered_at timestamp with time zone,
pending_discover_request_id bigint,
updated_at timestamp with time zone!
```
### `td_discovered_events` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `target_id`→`td_discover_targets.id`

```
id bigint!,
platform text!,
td_event_id text!,
event_url text,
event_name text,
event_date date,
venue_name text,
city text,
performer_label text,
target_id bigint,
matched_tevo_event_id bigint,
raw jsonb,
discovered_at timestamp with time zone!
```
### `td_match_results` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `queue_id`→`td_match_queue.id`

```
id bigint!,
queue_id bigint,
tevo_event_id bigint,
aq_short_event_id text,
purpose text,
match_type text,
match_confidence numeric(6,2),
match_reason jsonb,
event_date date,
event_venue text,
sg_event_id bigint,
sh_event_id bigint,
vivid_event_id bigint,
tm_url text,
tickpick_url text,
gametime_url text,
matches_raw jsonb,
applied_at timestamp with time zone,
created_at timestamp with time zone!
```


## AXS (primary + marketplace)

### `axs_seat_snapshots` &nbsp; ≈269K rows

Per-seat normalization of an AXS pull (FK axs_event_snapshots / axs_section_snapshots). One row per veritix offers[].items[] seat; raw_item retains the full source object. Read-only source.

**PK** `id`  •  **FK** `section_snapshot_id`→`axs_section_snapshots.id`; `snapshot_id`→`axs_event_snapshots.id`

```
id bigint!,
snapshot_id bigint!,
section_snapshot_id bigint,
captured_at timestamp with time zone!,
tevo_event_id bigint,
tevo_venue_id bigint,
axs_event_id text,
section_id text,
section_label text,
row_id text,
row_label text,
seat_number text,
seat_type text,
status_code integer,
status_label text,
neighborhood text,
neighborhood_label text,
price_level_id text,
price_level_label text,
price_code_label text,
is_ga boolean,
display_order integer,
offer_id text,
src text,
price numeric,
original_price numeric,
seller_name text,
info1 text,
info2 text,
raw_item jsonb
```
### `axs_venues` &nbsp; ≈9K rows

AXS.com primary-ticketer venue list (operator scrape 2026-06-09). Static reference; tevo_venue_id is an exact-normalized link to v_canonical_venue/cross_source_venue_map where one exists, NULL = unmapped (review later). AXS is not a resale source.

**PK** `id`

```
id bigint!,
axs_name text!,
section text,
tevo_venue_id bigint,
matched_name text,
match_method text,
created_at timestamp with time zone!,
axs_name_norm text
```
### `axs_section_snapshots` &nbsp; ≈4K rows

Per-section normalization of an AXS pull (FK axs_event_snapshots). AXS analog of per-listing snapshot rows.

**PK** `id`  •  **FK** `snapshot_id`→`axs_event_snapshots.id`

```
id bigint!,
snapshot_id bigint!,
captured_at timestamp with time zone!,
tevo_event_id bigint,
tevo_venue_id bigint,
axs_event_id text,
section_label text,
neighborhood text,
is_ga boolean,
sold_out boolean,
avail_qty integer,
price_min numeric,
price_max numeric,
seat_types text[],
has_resale boolean,
connection_fee numeric
```
### `axs_events` &nbsp; ≈368 rows

AXS events to ingest (from AXS_RESALE workbook col D). Drained into axs_event_snapshots via /fetch?platform=axs. Read-only source.

**PK** `id`  •  **FK** `last_snapshot_id`→`axs_event_snapshots.id`

```
id bigint!,
axs_event_id text!,
event_url text!,
source_sheets text,
tevo_event_id bigint,
tevo_venue_id bigint,
active boolean!,
last_pulled_at timestamp with time zone,
last_snapshot_id bigint,
created_at timestamp with time zone!,
is_axs_primary boolean,
probe_status text,
probed_at timestamp with time zone,
aq_short_event_id text
```
### `axs_event_snapshots` &nbsp; ≈355 rows

AXS event pulls (primary + AXS-Marketplace resale), one row per fetch. raw=full document; metrics derived by axs_client.normalize(). tevo_venue_id pegged from axs_venues; tevo_event_id AQ-derived. Source is read-only.

**PK** `id`

```
id bigint!,
captured_at timestamp with time zone!,
axs_event_id text,
event_url text,
tevo_event_id bigint,
tevo_venue_id bigint,
aq_short_event_id text,
venue_short_id text,
event_name text,
venue_name text,
occurs_at_local text,
event_tz text,
api text,
onsale_now boolean,
onsale_at timestamp with time zone,
offsale_at timestamp with time zone,
currency text,
price_min numeric,
price_max numeric,
getin numeric,
listings_count integer,
sections_count integer,
offers_count integer,
seats_primary integer,
seats_resale integer,
in_axs_list boolean,
canonical_matched boolean,
response_s numeric,
quota_remaining bigint,
raw jsonb
```
### `axs_probe` &nbsp; ≈352 rows

**PK** `axs_event_id`  •  **FK** `axs_event_id`→`axs_events.axs_event_id`

```
axs_event_id text!,
request_id bigint,
fired_at timestamp with time zone,
status_code integer,
is_axs_primary boolean,
note text,
checked_at timestamp with time zone,
attempts integer!,
status text,
last_reason text,
snapshot_id bigint
```
### `axs_performers` &nbsp; ≈130 rows

**PK** `axs_artist_id`

```
axs_artist_id text!,
performer text,
updated_at timestamp with time zone
```
### `axs_event_requests` &nbsp; ≈83 rows

**PK** `id`

```
id bigint!,
performer text!,
venue_name text!,
event_date_text text,
event_date date,
primary_market text,
tevo_venue_id bigint,
tevo_event_id bigint,
axs_event_id text,
status text,
created_at timestamp with time zone!
```


## Other resale orders (TickPick, Vivid)

### `tickpick_orders` &nbsp; ≈8K rows

**PK** `tp_order_id`  •  **FK** `aq_short_event_id`→`aq_event_map.aq_short_event_id`; `tevo_event_id`→`events.id`

```
tp_order_id text!,
event_name text,
event_date timestamp with time zone,
section text,
row text,
seat_numbers text[],
status text,
quantity integer,
total numeric,
notes text,
ordered_at timestamp with time zone,
tevo_event_id bigint,
pulled_at timestamp with time zone!,
last_seen_at timestamp with time zone!,
raw jsonb!,
order_status text,
matched_via text,
match_confidence numeric(3,2),
matched_at timestamp with time zone,
aq_short_event_id text,
venue_short_id text,
performer_short_id text
```
### `vivid_orders` &nbsp; ≈3K rows

**PK** `vivid_order_id`  •  **FK** `aq_short_event_id`→`aq_event_map.aq_short_event_id`; `tevo_event_id`→`events.id`

```
vivid_order_id text!,
event_name text,
event_date timestamp with time zone,
section text,
row text,
status text,
quantity integer,
total numeric,
first_name text,
last_name text,
email_address text,
transfer_via_url boolean,
ordered_at timestamp with time zone,
tevo_event_id bigint,
pulled_at timestamp with time zone!,
last_seen_at timestamp with time zone!,
raw_xml text,
raw jsonb!,
aq_short_event_id text,
venue_short_id text,
performer_short_id text
```
### `vivid_orders_pending` &nbsp; ≈929 rows

**PK** `id`

```
id bigint!,
request_id bigint!,
query_str text,
resolved_at timestamp with time zone,
rows_persisted integer,
created_at timestamp with time zone!
```
### `tickpick_orders_pending` &nbsp; ≈55 rows

**PK** `id`

```
id bigint!,
request_id bigint!,
query_str text,
resolved_at timestamp with time zone,
rows_persisted integer,
created_at timestamp with time zone!
```


## Movers & market-signal indices

### `event_movers_index_history` &nbsp; ≈6K rows

Composition changes for event_movers_index: enter/exit/rank_change. When did an event join the index and at what price?

**PK** `id`

```
id bigint!,
source text!,
window_days integer!,
category text!,
event_id bigint!,
action text!,
rank_before integer,
rank_after integer,
price_at_action numeric(10,2),
signal_score_at numeric(10,4),
recorded_at timestamp with time zone!
```
### `event_movers_agg` &nbsp; ≈4K rows

**PK** `window_days, event_id`

```
window_days integer!,
event_id bigint!,
computed_at timestamp with time zone!,
days_to_event numeric,
ev_price_recent numeric,
ev_price_recent_n integer,
ev_price_prior numeric,
ev_price_prior_n integer,
ev_owned_recent numeric,
ev_owned_prior numeric,
ev_cur_price numeric,
ev_cur_owned numeric,
ev_cur_tix numeric,
sg_price_recent numeric,
sg_price_recent_n integer,
sg_price_prior numeric,
sg_price_prior_n integer,
sg_fill_recent numeric,
sg_fill_prior numeric,
sg_cur_price numeric,
sg_cur_tix numeric,
sh_price_recent numeric,
sh_price_recent_n integer,
sh_price_prior numeric,
sh_price_prior_n integer,
sh_cur_price numeric,
sh_cur_listings numeric,
gt_price_recent numeric,
gt_price_recent_n integer,
gt_price_prior numeric,
gt_price_prior_n integer,
gt_cur_price numeric,
gt_cur_listings numeric,
vd_price_recent numeric,
vd_price_recent_n integer,
vd_price_prior numeric,
vd_price_prior_n integer,
vd_cur_price numeric,
vd_cur_listings numeric,
ls_evo_retail_median numeric,
ls_sg_all_median numeric,
ls_td_sh_median numeric,
ls_td_gt_median numeric,
ls_td_vd_median numeric
```
### `event_movers_index` &nbsp; ≈809 rows

Top-25 movers per (source × window_days × category). window_days = event horizon (events in next N days). Signal = half-window SMA vs prior-half SMA. Updated 3x/day by compute_movers_index_post_snapshot cron (7:35/13:35/19:35 UTC).

**PK** `source, window_days, category, event_id`

```
source text!,
window_days integer!,
category text!,
event_id bigint!,
rank integer!,
entered_at timestamp with time zone!,
last_computed_at timestamp with time zone!,
entry_price numeric(10,2),
entry_owned integer,
entry_tix integer,
cur_price numeric(10,2),
cur_owned integer,
cur_tix integer,
price_delta_pct numeric(8,4),
owned_delta numeric(8,2),
tix_delta numeric(8,2),
signal_score numeric(10,4),
data_coverage_pct numeric(5,2)
```


## Venues & seat maps

### `venue_section_map` &nbsp; ≈7K rows

Cross-platform seat-map crosswalk: for each (venue, platform, raw section string) the matched seatmap_manifest polygon key + method/confidence. Written only by build_venue_section_map() (SECURITY DEFINER). EVO populated 2026-06-08; sg/tp/vd/sh extend later.

**PK** `tevo_venue_id, configuration_id, platform, section_raw`

```
tevo_venue_id bigint!,
platform text!,
section_raw text!,
section_token text,
section_token_base text,
seatmap_key text,
configuration_id integer!,
match_method text!,
confidence numeric!,
candidate_keys text[],
seen_listings integer,
first_seen_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `venue_metrics_daily` &nbsp; ≈2K rows

**PK** `venue_id, snapshot_date`

```
venue_id bigint!,
snapshot_date date!,
venue_name text,
event_count integer!,
evo_tickets_total integer,
evo_owned_tickets_total integer,
sg_all_tickets_total integer,
sg_owned_tickets_total integer,
getin_min numeric,
getin_median numeric,
price_min numeric,
price_median numeric,
price_p90 numeric,
owned_book_notional numeric,
computed_at timestamp with time zone!
```
### `seatmap_manifest` &nbsp; ≈1K rows

Cached TEvo seat-map manifests (section keys) per venue/config, fetched by the seatmap-manifest-sync edge fn. has_map = real interactive-map availability (replaces fanvenues_key proxy). D0, mig 20260603160000.

**PK** `venue_id, configuration_id`

```
venue_id bigint!,
configuration_id integer!,
has_map boolean!,
http_status integer,
section_count integer!,
section_keys text[]!,
fetched_at timestamp with time zone!
```
### `cross_source_venue_map` &nbsp; ≈392 rows

**PK** `tevo_venue_id`

```
tevo_venue_id bigint!,
tevo_venue_name text!,
tevo_venue_location text,
canonical_name text,
city text,
state text,
country text,
sg_aliases jsonb,
sg_venue_id bigint,
tickpick_aliases jsonb,
tickpick_venue_id bigint,
vivid_aliases jsonb,
sources_count integer,
created_at timestamp with time zone,
updated_at timestamp with time zone
```
### `venue_crawl_state` &nbsp; ≈135 rows

**PK** `venue_id`

```
venue_id bigint!,
venue_name text,
last_crawled_at timestamp with time zone,
events_found integer,
performers_found integer,
last_error text
```
### `venue_geocode_pending` &nbsp; ≈111 rows

**PK** `tevo_venue_id`  •  **FK** `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
tevo_venue_id bigint!,
venue_name text,
query_string text,
request_id bigint,
fired_at timestamp with time zone,
resolved_at timestamp with time zone
```
### `venue_nws_points_pending` &nbsp; ≈24 rows

**PK** `id`  •  **FK** `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
id bigint!,
tevo_venue_id bigint!,
request_id bigint!,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
http_status integer,
error text
```
### `venue_assets` &nbsp; ≈0 rows

**PK** `tevo_venue_id`

```
tevo_venue_id bigint!,
venue_name text,
espn_venue_id text,
espn_venue_name text,
hero_image_url text,
map_image_url text,
capacity integer,
city text,
state text,
country text,
is_indoor boolean,
espn_raw jsonb,
source text,
fetched_at timestamp with time zone,
latitude numeric(9,6),
longitude numeric(9,6),
geocoded_at timestamp with time zone,
geocode_source text,
ugc_county_code text,
ugc_forecast_zone text,
ugc_fire_weather_zone text,
nws_grid_id text,
nws_grid_x integer,
nws_grid_y integer,
nws_radar_station text,
nws_time_zone text,
nws_points_fetched_at timestamp with time zone
```
### `venue_baselines` &nbsp; ≈0 rows

**PK** `venue_id`

```
venue_id bigint!,
computed_at timestamp with time zone!,
events_in_window integer,
median_retail numeric,
median_tickets integer,
zone_breakdown jsonb
```
### `venue_pulls` &nbsp; ≈0 rows

**PK** `tevo_venue_id`  •  **FK** `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
tevo_venue_id bigint!,
venue_label text!,
reason text,
pages_to_pull integer,
enabled boolean,
added_at timestamp with time zone
```


## Performers (metadata, enrichment, baselines)

### `performer_metrics_daily` &nbsp; ≈9K rows

**PK** `performer_id, snapshot_date, split`

```
performer_id bigint!,
snapshot_date date!,
split text!,
performer_name text,
what_event_type text,
event_count integer!,
evo_tickets_total integer,
evo_owned_tickets_total integer,
sg_all_tickets_total integer,
sg_owned_tickets_total integer,
getin_min numeric,
getin_median numeric,
price_min numeric,
price_median numeric,
price_p90 numeric,
owned_book_notional numeric,
computed_at timestamp with time zone!
```
### `performer_metadata` &nbsp; ≈1K rows

Cache of TEvo /v9/performers/{id}. Captures genre + top_category + popularity for every performer encountered. Powers WHAT/WHY chatbot slots.

**PK** `performer_id`  •  **FK** `espn_team_id`→`espn_teams_canonical.espn_team_id`

```
performer_id bigint!,
name text,
slug text,
popularity_score numeric,
keywords text,
category_id text,
category_name text,
parent_category_name text,
top_category_name text,
what_event_type text,
genre text,
upcoming_first timestamp with time zone,
upcoming_last timestamp with time zone,
raw jsonb,
fetched_at timestamp with time zone!,
s4k_popularity_boost numeric,
espn_team_id text,
espn_league text,
color_primary text,
color_alternate text,
logo_default_url text,
logo_dark_url text,
logo_scoreboard_url text,
logo_4k_primary_url text,
logo_secondary_url text,
espn_team_url text,
espn_roster_url text,
espn_schedule_url text,
espn_raw jsonb,
espn_fetched_at timestamp with time zone
```
### `performer_external_ids` &nbsp; ≈973 rows

Maps TEvo performer_id to identifiers used by external feeds (ESPN, Odds API, SportsDataIO). Required join layer for ESPN/odds/standings/player-stats integration.

**PK** `performer_id, source`

```
performer_id bigint!,
source text!,
external_id text!,
external_name text,
league text,
meta jsonb,
set_at timestamp with time zone!
```
### `performer_wikipedia` &nbsp; ≈731 rows

Wikipedia summary enrichment per TEvo performer (title, extract, image_url, wiki_url). Intended consumer: AI/RAG connectors that want plain-English context about a performer (e.g. "who is this team / artist"). NOT intended as a SQL analytics surface — buzz/trend signals live in price/order deltas. See docs/wikipedia-usage-2026-05-09.md.

**PK** `tevo_performer_id`  •  **FK** `tevo_performer_id`→`performer_metadata.performer_id`

```
tevo_performer_id bigint!,
performer_name text!,
wiki_title text,
wiki_pageid bigint,
wiki_url text,
wiki_lang text,
description text,
extract text,
image_url text,
match_confidence numeric(3,2),
match_method text,
is_rejected boolean,
reject_reason text,
last_refreshed_at timestamp with time zone,
created_at timestamp with time zone,
updated_at timestamp with time zone
```
### `performer_wiki_pending` &nbsp; ≈14 rows

**PK** `tevo_performer_id, stage`  •  **FK** `tevo_performer_id`→`performer_metadata.performer_id`

```
tevo_performer_id bigint!,
performer_name text,
stage text!,
search_request_id bigint,
summary_request_id bigint,
fired_at timestamp with time zone,
resolved_at timestamp with time zone
```
### `performer_home_venues` &nbsp; ≈10 rows

Maps a performer (sports team) to its home venue. Lets the chatbot interpret "X at home" / "X home game" → filter events to venue_id. Seeded from TEvo /v9/performers.

**PK** `performer_id`

```
performer_id bigint!,
performer_name text,
venue_id bigint!,
venue_name text,
venue_location text,
league text!,
source text!,
set_at timestamp with time zone!
```
### `performer_baselines` &nbsp; ≈0 rows

**PK** `performer_id`

```
performer_id bigint!,
computed_at timestamp with time zone!,
events_in_window integer,
median_retail numeric,
median_getin numeric,
median_tickets integer,
median_owned_tix integer,
avg_owned_share numeric,
avg_owned_premium_pct numeric,
total_book_notional numeric
```
### `performer_crawl_state` &nbsp; ≈0 rows

**PK** `performer_id`

```
performer_id bigint!,
performer_name text,
last_crawled_at timestamp with time zone,
events_found integer,
venues_found integer,
last_error text
```
### `performer_subreddits` &nbsp; ≈0 rows

**PK** `tevo_performer_id, subreddit`  •  **FK** `tevo_performer_id`→`performer_metadata.performer_id`

```
tevo_performer_id bigint!,
subreddit text!,
weight numeric(3,2),
is_primary boolean,
notes text,
league text
```


## ESPN (teams, athletes, injuries, scores, news)

### `espn_athlete_team_history` &nbsp; ≈1.2M rows

**PK** `id`  •  **FK** `espn_athlete_id`→`espn_athletes.espn_athlete_id`; `espn_team_id`→`espn_teams_canonical.espn_team_id`

```
id bigint!,
espn_athlete_id text!,
espn_team_id text,
espn_league text,
start_date date!,
end_date date,
transaction_type text,
prior_team_id text,
notes text,
detected_at timestamp with time zone!
```
### `espn_injuries_snapshots` &nbsp; ≈43K rows

**PK** `id`  •  **FK** `espn_team_id`→`espn_teams_canonical.espn_team_id`

```
id bigint!,
espn_team_id text!,
espn_league text!,
captured_at timestamp with time zone!,
athlete_id text,
athlete_name text,
position text,
status text,
injury_type text,
short_comment text,
long_comment text,
return_date timestamp with time zone,
meta jsonb,
content_hash text,
last_seen_at timestamp with time zone,
is_baseline boolean
```
### `espn_athletes` &nbsp; ≈14K rows

**PK** `espn_athlete_id`  •  **FK** `espn_team_id`→`espn_teams_canonical.espn_team_id`

```
espn_athlete_id text!,
full_name text,
display_name text,
short_name text,
jersey text,
position text,
position_abbr text,
height_inches integer,
weight_lbs integer,
birth_date date,
age integer,
espn_team_id text,
espn_league text,
status text,
experience_years integer,
headshot_url text,
meta jsonb,
first_seen_at timestamp with time zone!,
last_seen_at timestamp with time zone!
```
### `espn_event_snapshots` &nbsp; ≈13K rows

**PK** `id`

```
id bigint!,
espn_event_id text!,
espn_league text!,
captured_at timestamp with time zone!,
state text,
status_short text,
home_team_id text,
away_team_id text,
home_score integer,
away_score integer,
odds_provider text,
spread text,
over_under numeric,
home_ml integer,
away_ml integer,
home_win_prob numeric,
attendance integer,
meta jsonb,
content_hash text,
last_seen_at timestamp with time zone,
is_baseline boolean
```
### `espn_runs` &nbsp; ≈11K rows

**PK** `id`

```
id bigint!,
started_at timestamp with time zone!,
finished_at timestamp with time zone,
teams_processed integer,
events_processed integer,
injuries_inserted integer,
news_inserted integer,
team_snaps_inserted integer,
event_snaps_inserted integer,
errors integer,
log text
```
### `espn_event_date_lookup` &nbsp; ≈5K rows

**PK** `espn_event_id, espn_league`

```
espn_event_id text!,
espn_league text!,
game_at_utc timestamp with time zone!,
home_team_id text,
away_team_id text,
status_short text,
ingested_at timestamp with time zone
```
### `espn_news` &nbsp; ≈4K rows

**PK** `id`  •  **FK** `espn_team_id`→`espn_teams_canonical.espn_team_id`

```
id bigint!,
espn_article_id text!,
espn_team_id text,
espn_league text,
headline text,
description text,
published_at timestamp with time zone,
url text,
image_url text,
type text,
first_seen_at timestamp with time zone!,
meta jsonb
```
### `espn_team_snapshots` &nbsp; ≈2K rows

**PK** `id`  •  **FK** `espn_team_id`→`espn_teams_canonical.espn_team_id`

```
id bigint!,
espn_team_id text!,
espn_league text!,
captured_at timestamp with time zone!,
wins integer,
losses integer,
ties integer,
win_pct numeric,
games_back numeric,
playoff_seed integer,
conference_rank integer,
division_rank integer,
record_summary text,
standing_summary text,
streak text,
meta jsonb,
content_hash text,
last_seen_at timestamp with time zone,
is_baseline boolean
```
### `espn_scoreboard_pending` &nbsp; ≈308 rows

**PK** `id`

```
id bigint!,
espn_league text!,
request_id bigint!,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
http_status integer,
events_count integer,
error text
```
### `espn_teams_canonical` &nbsp; ≈217 rows

Hard-coded ESPN team list (152 teams across NBA/NFL/MLB/NHL/WNBA). Source: ESPN site.api /teams endpoint, captured 2026-05-10. Refresh when team relocations / expansion happen.

**PK** `espn_team_id, espn_league`

```
espn_team_id text!,
espn_league text!,
display_name text!,
abbreviation text
```
### `espn_tournament_events` &nbsp; ≈168 rows

**PK** `espn_event_id`

```
espn_event_id text!,
espn_sport text!,
espn_league text!,
name text!,
short_name text,
start_date timestamp with time zone!,
end_date timestamp with time zone,
state text,
status_short text,
venue_name text,
venue_city text,
venue_state text,
venue_country text,
total_purse numeric,
defending_champ text,
field_size integer,
course_name text,
circuit_name text,
raw_jsonb jsonb,
ingested_at timestamp with time zone,
last_seen_at timestamp with time zone
```
### `espn_tournament_pending` &nbsp; ≈24 rows

**PK** `id`

```
id bigint!,
espn_sport text!,
espn_league text!,
date_range text!,
request_id bigint!,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
http_status integer,
events_count integer,
error text
```
### `espn_asset_crawl_state` &nbsp; ≈0 rows

**PK** `performer_id`  •  **FK** `espn_team_id`→`espn_teams_canonical.espn_team_id`

```
performer_id bigint!,
espn_team_id text!,
espn_league text!,
status text!,
last_fetched_at timestamp with time zone,
last_error text,
retries integer
```
### `performer_espn_team_xref` &nbsp; ≈0 rows

Maps TEvo performer_id (team identity) to ESPN team_id per league. Built from name match against espn_teams_canonical with variant aliases for known edge cases (LA Clippers, Athletics).

**PK** `tevo_performer_id, espn_league`  •  **FK** `espn_team_id`→`espn_teams_canonical.espn_team_id`; `tevo_performer_id`→`performer_metadata.performer_id`

```
tevo_performer_id bigint!,
espn_team_id text!,
espn_league text!,
match_method text
```


## Weather & NWS alerts (demand signals)

### `weather_observations` &nbsp; ≈173K rows

Open-Meteo weather observations. is_forecast=true for ≤16-day forecasts; =false for historical archive (ERA5 reanalysis, lags real-time by ~5 days).

**PK** `id`  •  **FK** `source_key`→`data_sources.source_key`; `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
id bigint!,
source_key text!,
tevo_venue_id bigint!,
observed_at timestamp with time zone!,
fetched_at timestamp with time zone,
is_forecast boolean!,
temp_f numeric,
precip_in numeric,
precip_pct integer,
wind_mph numeric,
gust_mph numeric,
humidity_pct integer,
wmo_code integer,
weather_summary text,
raw_jsonb jsonb
```
### `nws_alert_zones` &nbsp; ≈64K rows

**PK** `alert_id, ugc_code`  •  **FK** `alert_id`→`nws_alerts.id`

```
alert_id text!,
ugc_code text!,
ugc_kind text!,
ugc_state text
```
### `nws_alert_pending` &nbsp; ≈38K rows

**PK** `id`

```
id bigint!,
state_code text!,
request_id bigint!,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
http_status integer,
alerts_count integer,
error text
```
### `nws_alerts` &nbsp; ≈15K rows

**PK** `id`

```
id text!,
alert_url text,
event text!,
event_code_nws text,
event_code_same text,
category text,
status text,
message_type text,
scope text,
alert_code text,
language text,
severity text,
urgency text,
certainty text,
response_action text,
impact_tier text,
sent_at timestamp with time zone!,
effective_at timestamp with time zone,
onset_at timestamp with time zone,
expires_at timestamp with time zone,
ends_at timestamp with time zone,
area_desc text,
fips_codes text[],
affected_zones_urls text[],
sender text,
sender_name text,
web_url text,
headline text,
description text,
instruction text,
note text,
max_wind_gust_mph numeric,
max_hail_size_in numeric,
wind_threat text,
hail_threat text,
thunderstorm_damage_threat text,
waterspout_detection text,
event_motion_description text,
vtec text,
awips_identifier text,
wmo_identifier text,
nws_headline_text text,
block_channels text[],
eas_org text,
expired_references text[],
raw_parameters jsonb,
raw_jsonb jsonb,
ingested_at timestamp with time zone,
last_seen_at timestamp with time zone
```
### `weather_forecast_pending` &nbsp; ≈12K rows

**PK** `tevo_venue_id, scope, fired_at`  •  **FK** `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
tevo_venue_id bigint!,
scope text!,
date_window daterange,
request_id bigint,
fired_at timestamp with time zone!,
resolved_at timestamp with time zone
```
### `nws_alert_references` &nbsp; ≈8K rows

**PK** `alert_id, references_id`  •  **FK** `alert_id`→`nws_alerts.id`

```
alert_id text!,
references_id text!,
sender text,
sent_at timestamp with time zone
```


## Macro indicators (FRED)

### `macro_indicators` &nbsp; ≈327 rows

**PK** `series_id, observation_date, realtime_start`  •  **FK** `series_id`→`macro_series_config.series_id`

```
series_id text!,
observation_date date!,
value numeric,
realtime_start date!,
realtime_end date,
ingested_at timestamp with time zone
```
### `fred_pending` &nbsp; ≈35 rows

**PK** `id`

```
id bigint!,
series_id text!,
request_id bigint!,
observation_start date,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
http_status integer,
rows_upserted integer,
error text
```
### `macro_series_config` &nbsp; ≈0 rows

**PK** `series_id`

```
series_id text!,
display_name text!,
units text,
frequency text!,
source text!,
enabled boolean!,
notes text,
added_at timestamp with time zone
```


## Social / news signals

### `general_subreddits` &nbsp; ≈0 rows

Leaguewide / cross-team subreddits not tied to a specific performer. Used for general-sentiment pulls.

**PK** `subreddit`

```
subreddit text!,
league text!,
scope text,
notes text
```
### `important_x_accounts` &nbsp; ≈0 rows

Roster of X.com handles by category (insiders, beat reporters, teams, leagues, Broadway shows/producers/reporters, concert venues/promoters/artists). Phase 1 cannot pull X directly. This table is INFORMATIONAL CONTEXT for AI/RAG consumers: when the chatbot sees a Reddit post quoting a known insider, it can recognize the source. Same intent as performer_wikipedia: prompt context, not SQL analytics.

**PK** `handle`

```
handle text!,
name text!,
account_type text!,
league text,
team_name text,
priority text,
added_at timestamp with time zone,
sport text,
category text
```
### `news_keywords` &nbsp; ≈0 rows

Keywords that signal a Reddit post is "important news" worth surfacing to the chatbot. Per-category weights so a Broadway sub does not match sports keywords accidentally.

**PK** `keyword`

```
keyword text!,
category text!,
weight numeric(3,2),
notes text
```
### `reddit_pending` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
subreddit text!,
request_id bigint!,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
rows_persisted integer
```
### `reddit_posts` &nbsp; ≈0 rows

**PK** `reddit_post_id`  •  **FK** `tevo_performer_id`→`performer_metadata.performer_id`

```
reddit_post_id text!,
subreddit text!,
tevo_performer_id bigint,
title text,
body text,
author text,
url text,
score integer,
num_comments integer,
created_utc timestamp with time zone,
pulled_at timestamp with time zone,
raw jsonb
```
### `why_signals` &nbsp; ≈0 rows

Contextual demand-signal store. Sources: weather (NOAA/OWM), social (Spotify/Reddit/YouTube/Bandsintown), news (RSS), holidays. Per-event/performer/city/global scope. Used to enrich WHY bucket + boost chip rotation.

**PK** `id`

```
id bigint!,
scope text!,
scope_id bigint,
scope_label text,
signal_kind text!,
signal_value numeric!,
signal_label text,
weight numeric,
source text!,
meta jsonb,
fetched_at timestamp with time zone!,
expires_at timestamp with time zone
```


## Calendar & context reference

### `holidays` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
holiday_name text!,
observed_date date!,
country text!,
holiday_type text!,
attendance_impact text,
notes text,
added_at timestamp with time zone,
state text,
city text
```
### `mlb_branded_series` &nbsp; ≈0 rows

**PK** `branded_name`

```
branded_name text!,
team_a text!,
team_b text!,
notes text,
added_at timestamp with time zone
```
### `school_break_windows` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
break_name text!,
start_date date!,
end_date date!,
country text!,
region_scope text,
notes text,
added_at timestamp with time zone,
state text,
city text,
district_name text
```
### `sporting_rivalries` &nbsp; ≈0 rows

**PK** `rivalry_name`

```
rivalry_name text!,
league text!,
team_a text!,
team_b text!,
is_branded boolean,
rivalry_intensity text,
wikipedia_url text,
notes text,
added_at timestamp with time zone,
team_a_performer_id bigint,
team_b_performer_id bigint
```


## Chatbot & conversation analytics

### `bot_chat` &nbsp; ≈2K rows

**PK** `id`  •  **FK** `in_reply_to`→`bot_chat.id`

```
id bigint!,
bot_level text!,
bot_lane text!,
event_type text!,
message text!,
related_pr integer,
related_mig text,
in_reply_to bigint,
acked_at timestamp with time zone,
acked_by text,
resolved_at timestamp with time zone,
resolved_by text,
meta jsonb,
created_at timestamp with time zone!
```
### `chat_term_freq_out` &nbsp; ≈644 rows

**PK** `term`

```
term text!,
occurrences bigint!,
first_seen timestamp with time zone!,
last_seen timestamp with time zone!
```
### `chat_aliases` &nbsp; ≈369 rows

**PK** `id`

```
id bigint!,
alias_norm text!,
alias_kind text!,
performer_id bigint,
venue_id bigint,
display_name text!,
league text,
city text,
source text!
```
### `chat_term_freq_in` &nbsp; ≈53 rows

**PK** `term`

```
term text!,
occurrences bigint!,
first_seen timestamp with time zone!,
last_seen timestamp with time zone!
```
### `bot_messages` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
channel text!,
direction text!,
phone text!,
body text,
message_sid text,
meta jsonb,
created_at timestamp with time zone!
```
### `bot_users` &nbsp; ≈0 rows

**PK** `phone`

```
phone text!,
label text!,
active boolean!,
channels text[]!,
created_at timestamp with time zone!,
is_internal boolean!
```
### `chat_audit_findings` &nbsp; ≈0 rows

Ground-truth audit of bot no-results claims. For each bot reply that claimed nothing was available, we replay the filters against listings_snapshots at the response time and verify.

**PK** `id`  •  **FK** `bot_message_id`→`bot_messages.id`

```
id bigint!,
bot_message_id bigint!,
user_text text,
bot_text text,
claim_type text!,
event_id bigint,
zone text,
max_price numeric,
min_qty integer,
include_all boolean,
s4k_only_count integer,
all_sources_count integer,
was_truthful boolean!,
severity text!,
notes text,
audited_at timestamp with time zone!
```
### `chat_corpus` &nbsp; ≈0 rows

Training-grade rollup of paired (user, bot) turns from bot_messages. Future fine-tune dataset for the Groq/OSS-LLM swap.

**PK** `id`  •  **FK** `bot_msg_id`→`bot_messages.id`; `user_msg_id`→`bot_messages.id`

```
id bigint!,
channel text!,
user_msg_id bigint,
bot_msg_id bigint,
user_text text!,
bot_text text!,
tool_trace jsonb,
outcome text!,
tool_calls integer,
listings_shown integer,
zones_shown integer,
events_resolved bigint[],
created_at timestamp with time zone!
```
### `chat_glossary_known` &nbsp; ≈0 rows

**PK** `term`

```
term text!,
meaning text,
added_at timestamp with time zone!
```
### `chat_rate_limits` &nbsp; ≈0 rows

```
ip text!,
ts timestamp with time zone!
```
### `chat_stopwords` &nbsp; ≈0 rows

**PK** `word`

```
word text!
```
### `chat_term_frequency` &nbsp; ≈0 rows

N-gram word pool from retail chat user inputs. Drives chat_glossary_candidates discovery.

**PK** `term, n, channel`

```
term text!,
n integer!,
channel text!,
occurrences bigint!,
first_seen timestamp with time zone!,
last_seen timestamp with time zone!
```


## EXOS (own primary-ticketing platform)

### `exos_tickets` &nbsp; ≈32 rows

**PK** `id`  •  **FK** `buyer_id`→`users.id`; `event_id`→`exos_events.id`; `org_id`→`exos_orgs.id`; `owner_id`→`users.id`; `tier_id`→`exos_ticket_tiers.id`; `voided_by`→`users.id`

```
id uuid!,
event_id uuid!,
org_id uuid!,
tier_id uuid,
tier_name text,
buyer_id uuid!,
owner_id uuid!,
buyer_email text,
status text!,
barcode_secret text!,
price_paid numeric!,
order_ref text,
channel_source text!,
promoter_id text,
pending_transfer_id uuid,
transfer_id uuid,
voided_at timestamp with time zone,
voided_by uuid,
voided_reason text,
check_in_at timestamp with time zone,
last_reissue_at timestamp with time zone,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `exos_scan_rejects` &nbsp; ≈9 rows

**PK** `id`  •  **FK** `event_id`→`exos_events.id`; `rejected_by`→`users.id`

```
id uuid!,
event_id uuid!,
org_id uuid!,
rejected_by uuid!,
reason text!,
source text!,
ticket_id_attempted text,
wrong_event_id uuid,
wrong_event_title text,
reason_detail text,
rejected_at timestamp with time zone!
```
### `exos_event_checkins` &nbsp; ≈7 rows

**PK** `id`  •  **FK** `event_id`→`exos_events.id`; `scanned_by`→`users.id`

```
id uuid!,
event_id uuid!,
ticket_id uuid!,
org_id uuid!,
scanned_by uuid,
source text,
verification text,
scanned_at timestamp with time zone!
```
### `exos_events` &nbsp; ≈1 rows

**PK** `id`  •  **FK** `created_by`→`users.id`; `org_id`→`exos_orgs.id`

```
id uuid!,
org_id uuid!,
name text!,
occurs_at_local text,
venue_id bigint,
venue_name text,
venue_location text,
primary_performer_id bigint,
primary_performer_name text,
event_type text,
slug text,
description text,
status text!,
starts_at timestamp with time zone,
doors_at timestamp with time zone,
ends_at timestamp with time zone,
timezone text,
currency text!,
venue_address jsonb,
performer_names text[],
category text,
genres text[],
subgenres text[],
image_url text,
total_tickets integer!,
tickets_sold integer!,
branding jsonb,
exclusivity jsonb,
purchase_limits jsonb,
distribution_networks text[],
automatiq_listing_id text,
sync_status text,
cancelled_at timestamp with time zone,
cancel_reason text,
created_by uuid,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `exos_org_memberships` &nbsp; ≈1 rows

**PK** `id`  •  **FK** `added_by`→`users.id`; `org_id`→`exos_orgs.id`; `user_id`→`users.id`

```
id uuid!,
org_id uuid!,
user_id uuid!,
role text!,
added_by uuid,
disabled boolean!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `exos_orgs` &nbsp; ≈1 rows

**PK** `id`  •  **FK** `owner_uid`→`users.id`

```
id uuid!,
name text!,
slug text!,
owner_uid uuid!,
theme jsonb,
created_at timestamp with time zone!,
updated_at timestamp with time zone!,
description text,
followers_count integer!,
marketing jsonb
```
### `exos_profiles` &nbsp; ≈1 rows

**PK** `id`  •  **FK** `id`→`users.id`

```
id uuid!,
display_name text,
photo_url text,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `exos_ticket_tiers` &nbsp; ≈1 rows

**PK** `id`  •  **FK** `event_id`→`exos_events.id`

```
id uuid!,
event_id uuid!,
name text!,
description text,
price numeric!,
capacity integer!,
sold integer!,
ticket_type text!,
visibility text!,
sales_start timestamp with time zone,
sales_end timestamp with time zone,
sort_order integer!,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `exos_discount_codes` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `event_id`→`exos_events.id`

```
id uuid!,
event_id uuid!,
code text!,
type text!,
value numeric!,
usage_limit integer,
used_count integer!,
expires_at timestamp with time zone,
unlocks_tier_ids uuid[],
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `exos_mail` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `created_by`→`users.id`

```
id uuid!,
to_email text!,
template text!,
subject text!,
html text!,
status text!,
created_by uuid,
created_at timestamp with time zone!,
sent_at timestamp with time zone,
error text,
attempts integer!,
claimed_at timestamp with time zone,
last_attempt_at timestamp with time zone
```
### `exos_org_follows` &nbsp; ≈0 rows

**PK** `follower_uid, org_id`  •  **FK** `follower_uid`→`users.id`; `org_id`→`exos_orgs.id`

```
follower_uid uuid!,
org_id uuid!,
created_at timestamp with time zone!
```
### `exos_org_invites` &nbsp; ≈0 rows

**PK** `token`  •  **FK** `claimed_by`→`users.id`; `created_by`→`users.id`; `org_id`→`exos_orgs.id`

```
token uuid!,
org_id uuid!,
email text!,
role text!,
created_by uuid,
status text!,
expires_at timestamp with time zone,
claimed_by uuid,
claimed_at timestamp with time zone,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `exos_org_secrets` &nbsp; ≈0 rows

**PK** `org_id`  •  **FK** `org_id`→`exos_orgs.id`

```
org_id uuid!,
payments jsonb,
distribution jsonb,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```
### `exos_transfers` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `sender_id`→`users.id`; `ticket_id`→`exos_tickets.id`

```
id uuid!,
ticket_id uuid!,
org_id uuid,
sender_id uuid!,
sender_email text,
receiver_email text!,
status text!,
event_id uuid,
event_title text,
event_image text,
tier_name text,
organizer_id uuid,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```


## Tournaments / non-team events

### `tournament_category_pending` &nbsp; ≈191 rows

**PK** `id`

```
id bigint!,
category_id integer!,
page integer!,
request_id bigint!,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
events_returned integer,
events_persisted integer,
events_filtered integer,
status text
```
### `tournament_categories` &nbsp; ≈0 rows

**PK** `category_id`

```
category_id integer!,
tevo_label text!,
genre text!,
pages_to_pull integer,
enabled boolean,
notes text,
added_at timestamp with time zone
```
### `tournament_search_pending` &nbsp; ≈0 rows

**PK** `id`

```
id bigint!,
query text!,
request_id bigint!,
fired_at timestamp with time zone,
resolved_at timestamp with time zone,
events_persisted integer,
status text
```
### `tournament_search_queries` &nbsp; ≈0 rows

**PK** `query`

```
query text!,
genre text!,
region text!,
notes text,
enabled boolean,
added_at timestamp with time zone
```


## Ops: scheduling, policy & coverage

### `cron_gate_decisions` &nbsp; ≈198K rows

**PK** `jobname, decided_at`

```
jobname text!,
decided_at timestamp with time zone!,
decision text!,
hour_et integer!,
last_fire_at timestamp with time zone,
fires_today integer
```
### `discovery_gap_alerts` &nbsp; ≈2K rows

Active market coverage and opportunity gaps from evo_sg_discovery_gaps(). gap_types: sg_no_evo | td_sh/gt/vd_no_evo | td_sh_no_gt | td_gt/vd_premium | evo_no_sg | value_gap_large | fill_rate_spike

**PK** `id`

```
id bigint!,
event_id bigint!,
gap_type text!,
detail text,
signal_score numeric(8,4),
detected_at timestamp with time zone!,
resolved_at timestamp with time zone
```
### `cron_policy` &nbsp; ≈85 rows

**PK** `jobname`

```
jobname text!,
peak_hours_et integer[]!,
peak_min_interval_min integer!,
offpeak_min_interval_min integer!,
work_check_sql text,
daily_max_fires integer,
enabled boolean!,
notes text,
updated_at timestamp with time zone!
```
### `cron_pause_state_20260514` &nbsp; ≈75 rows

**PK** `jobid`

```
jobid bigint!,
jobname text!,
schedule text!,
command text!,
paused_at timestamp with time zone!
```
### `collector_cadence` &nbsp; ≈24 rows

**PK** `source, scope, band, owned_kind`

```
source text!,
scope text!,
band text!,
min_hours numeric!,
max_hours numeric,
peak_interval_min integer!,
offpeak_interval_min integer!,
enabled boolean!,
sort_order integer!,
notes text,
updated_at timestamp with time zone!,
owned_kind text!
```
### `integration_policy` &nbsp; ≈1 rows

**PK** `key`

```
key text!,
enabled boolean!,
reason text,
updated_by text,
updated_at timestamp with time zone!
```
### `pull_rate_limits` &nbsp; ≈0 rows

**PK** `scope`

```
scope text!,
min_seconds_between integer!,
meta jsonb,
updated_at timestamp with time zone!
```


## Alerts

### `alert_metric_catalog` &nbsp; ≈36 rows

**PK** `metric_key`

```
metric_key text!,
scope_type text!,
label text!,
value_kind text!,
allowed_operators text[]!,
default_operator text!,
threshold_unit text,
param_kind text,
source_note text,
sort_order integer!,
active boolean!
```
### `alert_mail` &nbsp; ≈3 rows

**PK** `id`

```
id uuid!,
to_email text!,
template text!,
subject text!,
html text!,
status text!,
created_at timestamp with time zone!,
sent_at timestamp with time zone,
error text,
attempts integer!,
claimed_at timestamp with time zone,
last_attempt_at timestamp with time zone
```
### `user_alerts` &nbsp; ≈0 rows

**PK** `id`  •  **FK** `metric_key`→`alert_metric_catalog.metric_key`

```
id bigint!,
owner_id uuid!,
scope_type text!,
scope_id bigint!,
scope_label text,
metric_key text!,
operator text!,
threshold numeric,
params jsonb!,
channels text[]!,
notify_email text,
notify_phone text,
cooldown_minutes integer!,
active boolean!,
last_fired_at timestamp with time zone,
expires_at timestamp with time zone,
created_at timestamp with time zone!,
updated_at timestamp with time zone!
```


## Other / reference

### `wc_price_daily` &nbsp; ≈284 rows

**PK** `sg_event_id, snapshot_date`

```
sg_event_id bigint!,
snapshot_date date!,
sg_event_name text,
venue_name text,
match_date date,
captured_at timestamp with time zone,
listings_count integer,
tickets_count integer,
owned_listings integer,
owned_tickets integer,
allin_min numeric,
allin_median numeric,
allin_p90 numeric,
broadcast_min numeric,
broadcast_median numeric,
computed_at timestamp with time zone!
```
### `tevo_blindspot_discovery_attempts` &nbsp; ≈21 rows

Audit log for tevo_blindspot_discovery_daily cron firings.

**PK** `attempt_id`

```
attempt_id bigint!,
attempted_at timestamp with time zone!,
result text!,
events_found integer,
events_added integer,
meta jsonb,
search_criteria jsonb
```
### `major_event_calendar` &nbsp; ≈0 rows

Curated calendar of major non-team events (F1/NASCAR/Tennis/Golf majors). Anchors venue+date filtering for events that don't have a TEvo team performer.

**PK** `id`  •  **FK** `tevo_venue_id`→`venue_assets.tevo_venue_id`

```
id bigint!,
event_class text!,
event_name text!,
venue_name text!,
venue_city text,
venue_country text,
tevo_venue_id bigint,
window_start date!,
window_end date!,
recurrence text!,
notes text,
active boolean!,
added_at timestamp with time zone!
```
