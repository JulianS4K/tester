# 🔥 Hearth — a DIY family dashboard board

A self-hosted, wall-mounted **family dashboard** for a Raspberry Pi + touchscreen —
think **Windows Live Tiles, but cooler and DIY-coded** (and a spiritual cousin of a
Skylight Calendar). A small Node server merges your **ICS calendar feeds** + **RSS
news**, and a **touch-first web UI** (fullscreen Chromium) opens on a grid of
**animated, self-updating tiles** — weather, next event, headlines, chores, health,
tonight's dinner, photos, notes — each tapping through to its full view.
**No subscription, no cloud, your data stays on the Pi.**

```
┌───────────────────────┐        ┌──────────────────────────┐
│  Wall touchscreen     │  HTTP  │  Raspberry Pi             │  HTTPS   ICS feeds,
│  (Chromium kiosk)     │◀──────▶│  Node server + web UI     │────────▶ Open-Meteo
│  tap to interact      │        │  local JSON store         │          (read-only)
└───────────────────────┘        └──────────────────────────┘
```

## Features

- **🟦 Live Tiles home** — an animated grid of self-updating tiles (clock, weather,
  next event, rotating headlines, chores/health progress, tonight's dinner, a
  rotating photo, notes). Tiles flip as they refresh; tap one to open its full view.
- **📅 Merged calendar** — subscribe to any number of **.ics** feeds (Google,
  iCloud, Outlook, Cozi, school/sports…), each **color-coded per person**. Month
  grid + an **Up Next** agenda. Recurring events are expanded correctly.
- **📰 News** — aggregate any **RSS/Atom** feeds (free, no API key); merged, newest first.
- **⏳ Countdowns & reminders** — add events ("Mom's birthday", "beach trip"); the
  **Countdown** tile shows the nearest one ("🏖️ in 5 days").
- **🗓️ On This Day** — a rotating history fact (Wikipedia, no key).
- **🎨 Themes** — pick an accent color; the whole board recolors.
- **🙂 Daily mood check-in** — a prominent **My Mood** tile on the home screen
  (it nudges you if you haven't checked in today); tap to log how you feel, and see
  a **14-day mood trend** to track your own behavior over time.
- **❤️ Health & Habits** — per-person **habits with streaks** (🔥), plus **water**,
  **mood**, and **weight** logging. Tap to check off.
- **✅ Chores** — per-person checkable chores, one-tap **Reset all**.
- **🛒 Lists** — shared to-do / grocery list, **clear done**.
- **🍽️ Meals** — a simple weekly dinner planner.
- **📝 Notes** — a shared family sticky note.
- **🌤️ Weather** — current + 3-day forecast (Open-Meteo, **no API key**).
- **🖼️ Photo screensaver** — drops to a photo slideshow after ~90s idle (put images
  in `photos/`).
- **Touch-first**, works with a mouse too; add anything from your phone by opening
  the same URL.

## Why DIY vs buying

A Skylight is **$180–$380 + $79/year** for the good features. Hearth is
**~$50–150 in parts, $0/year**, open, and yours — no account, no lock-in.

## Runs on anything with Node

Hearth is a Node web app + a browser, so the "board" can be **any always-on
computer hooked to a screen**:

- a **Raspberry Pi** (cheapest, silent, low-power), or
- a **cheap Windows/Linux mini PC** or an **old laptop** hooked to a **TV over HDMI**.

The display can be a touchscreen **or a regular TV** — with a TV, use a **$15
wireless mouse / air-remote** (or just edit from your phone at the same URL). No
code changes either way.

## Requirements

- **Node.js 18+** (nodejs.org, or `sudo apt install nodejs npm` on a Pi).
- A screen: touchscreen, or a TV + a wireless mouse/air-remote.
- A browser for kiosk mode: **Chromium** (Pi/Linux) or **Edge/Chrome** (Windows).

## Setup

```bash
cd hearth
npm install                 # express + node-ical
cp .env.example .env         # optional: set your weather location
npm start                    # http://localhost:8080
```

Open `http://<pi-ip>:8080`, tap **⚙ Settings**, and paste your calendars' **secret
.ics URLs** (see below). That's it — the board fills in.

### Where to get an .ics link

- **Google Calendar:** Settings → *Settings for my calendars* → pick a calendar →
  *Integrate calendar* → **Secret address in iCal format**.
- **Apple iCloud:** calendar.icloud.com → share a calendar → **Public Calendar** →
  copy the `webcal://…` link (Hearth accepts http/https; change `webcal` to `https`).
- **Outlook / Microsoft 365:** Calendar → Share → **Publish** → copy the **ICS** link.
- **Cozi / school / sports sites:** look for a "Subscribe / iCal / ICS" export.

### Run on Windows (mini PC / laptop → TV)

```bat
cd hearth
npm install
scripts\start.bat          REM just the server (http://localhost:8080)
REM …or fullscreen on the TV in one go (starts server + Edge kiosk):
scripts\kiosk.bat
```

Interact with a **wireless mouse / air-remote** on the TV, or from your **phone**
at `http://<pc-ip>:8080`. To launch on login, drop a shortcut to `kiosk.bat` in
the Startup folder (`Win+R` → `shell:startup`), or use Task Scheduler "At log on".
Exit kiosk with `Alt+F4`.

### Run fullscreen on boot (Raspberry Pi / Linux)

```bash
# server on boot
sudo cp scripts/hearth.service /etc/systemd/system/
#   edit User= / WorkingDirectory= inside the unit
sudo systemctl daemon-reload && sudo systemctl enable --now hearth

# kiosk on login (LXDE autostart)
mkdir -p ~/.config/autostart
cat > ~/.config/autostart/hearth-kiosk.desktop <<EOF
[Desktop Entry]
Type=Application
Name=Hearth Kiosk
Exec=/home/pi/tester/hearth/scripts/kiosk.sh
X-GNOME-Autostart-enabled=true
EOF
```

## Configuration (`.env`)

| Var | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | Port the board is served on |
| `WEATHER_LAT` / `WEATHER_LON` | NYC | Your location for weather |
| `WEATHER_UNIT` | `fahrenheit` | `fahrenheit` or `celsius` |
| `REFRESH_MINUTES` | `15` | How often feeds re-fetch |
| `HEARTH_FEEDS` | — | Optional JSON to seed calendars on first run |

## Notes

- Calendars are **read-only** (ICS subscriptions) — the fast, universal path. Live
  two-way Google/Apple/Outlook sync (add events from the board) would need OAuth per
  provider; a good next step if you want it.
- Everything local (chores/lists/meals) is stored in `data/store.json`; `.env`,
  `data/`, `photos/*`, and `node_modules/` are gitignored.
- "Skylight" is a trademark of its owner; Hearth is an independent DIY project.
