# 🔥 Hearth — a DIY family calendar board

A self-hosted, wall-mounted **family calendar & organizer** for a Raspberry Pi +
touchscreen — a DIY take on the Skylight Calendar. A small Node server merges your
**ICS calendar feeds**, and a **touch-first web UI** (shown fullscreen in Chromium)
gives you a shared month calendar, chores, lists, meal plan, weather, and a photo
screensaver. **No subscription, no cloud, your data stays on the Pi.**

```
┌───────────────────────┐        ┌──────────────────────────┐
│  Wall touchscreen     │  HTTP  │  Raspberry Pi             │  HTTPS   ICS feeds,
│  (Chromium kiosk)     │◀──────▶│  Node server + web UI     │────────▶ Open-Meteo
│  tap to interact      │        │  local JSON store         │          (read-only)
└───────────────────────┘        └──────────────────────────┘
```

## Features

- **📅 Merged calendar** — subscribe to any number of **.ics** feeds (Google,
  iCloud, Outlook, Cozi, school/sports…), each **color-coded per person**. Month
  grid + an **Up Next** agenda. Recurring events are expanded correctly.
- **✅ Chores** — per-person checkable chores, one-tap **Reset all**.
- **🛒 Lists** — shared to-do / grocery list, **clear done**.
- **🍽️ Meals** — a simple weekly dinner planner.
- **🌤️ Weather** — current + 3-day forecast (Open-Meteo, **no API key**).
- **🕑 Clock & date** header.
- **🖼️ Photo screensaver** — drops to a photo slideshow after ~90s idle (put images
  in `photos/`).
- **Touch-first**, works with a mouse too; add anything from your phone by opening
  the same URL.

## Why DIY vs buying

A Skylight is **$180–$380 + $79/year** for the good features. Hearth is
**~$50–150 in parts, $0/year**, open, and yours — no account, no lock-in.

## Requirements

- Raspberry Pi (3/4/5 or Zero 2 W) with Raspberry Pi OS (desktop).
- **Node.js 18+** (`sudo apt install nodejs npm`).
- A touchscreen (official Pi display, or any HDMI touch monitor).
- Chromium (`sudo apt install chromium-browser`) for the kiosk.

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

### Run fullscreen on boot

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
