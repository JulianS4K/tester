# Competitor Tracker (D1 & D4)

A small, dependency-light tool that tracks the **major competitors** of two
products — referred to as `d1` and `d4` — and surfaces fresh signals about them:

- 📰 **Recent news** via Google News RSS (no API key required)
- 🌐 **Website changes** — fingerprints each competitor's homepage and reports
  when the visible content changes between runs

State is persisted between runs so each report only shows what's **new**.

## Setup

```bash
pip install -r requirements.txt
```

Only `requests` and `PyYAML` are needed — RSS and HTML parsing use the Python
standard library.

## Configure

Edit [`config.yaml`](config.yaml). It ships with placeholder competitors so the
tool runs immediately; replace `d1`/`d4` and their competitor lists with the
real names and websites for your market:

```yaml
products:
  d1:
    name: "My D1 Product"
    competitors:
      - name: "Acme"
        website: "https://acme.example"
        aliases: ["Acme Corp"]
        tags: ["enterprise"]
```

## Usage

```bash
# List what's configured
python -m competitor_tracker list

# Track everything
python -m competitor_tracker track

# Just one product, longer news window
python -m competitor_tracker track --product d4 --lookback-days 14

# Only news (skip website change detection)
python -m competitor_tracker track --source news
```

A Markdown report is written to `reports/latest.md` and a summary is printed to
the console. Run it on a schedule (cron, CI, etc.) to build an ongoing feed —
because state lives in `data/state.json`, repeat runs only report new items.

## How it works

```
config.yaml ─▶ Settings ─▶ Tracker ─▶ [ NewsSource, WebsiteSource ] ─▶ Signals ─▶ report
                                              │
                                         data/state.json  (de-dupe + snapshots)
```

Sources are pluggable: add a class under `competitor_tracker/sources/`,
implement `collect()`, and register it in `sources/__init__.py`. Natural next
additions include pricing-page tracking, GitHub release/activity feeds, job
postings, and app-store reviews.

## Tests

```bash
python -m pytest -q        # or: python -m unittest
```

All tests run offline (no network).
```
