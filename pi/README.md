# 📺 Trakt TV for Raspberry Pi

A [Trakt](https://trakt.tv) **10-foot interface you build and host on a Raspberry Pi**.
A small Node server runs on the Pi, serves a TV-friendly web UI, and proxies the
Trakt API v2 so your `client_secret` and OAuth tokens stay on the device. Show it
fullscreen on the TV with Chromium in kiosk mode and drive it with a remote or keyboard.

```
┌─────────────┐     HTTP      ┌────────────────────┐   HTTPS   ┌──────────────┐
│  TV screen  │◀────kiosk─────│  Raspberry Pi       │──────────▶│  api.trakt.tv │
│ (Chromium)  │──arrow keys──▶│  Node server + UI   │  (key +   └──────────────┘
└─────────────┘               │  tokens on disk     │   token injected here)
                              └────────────────────┘
```

## Features

- **Server-side device OAuth** — the Pi runs Trakt's device flow, shows a code + URL
  on the TV, polls for the token, and persists/refreshes it in `tokens.json`. The
  browser only learns *whether* you're signed in.
- **Browse** — trending / popular / anticipated shows & movies, in D-pad-navigable rows.
- **Search** — movies & shows (debounced).
- **Detail** — backdrop, overview, rating, genres, related titles.
- **Track** — add to **Watchlist** / **Mark Watched** (`/sync/*`).
- **Library** — your watchlist & history.
- **Ways to watch** — buttons that open each service's **web player search** for the
  title (Netflix, YouTube, Prime, Disney+, Max, Hulu, Apple TV) in the Pi's browser,
  plus Trakt / IMDb / TMDB links. Trakt has no entitlement data, so these are
  best-effort searches — the reliable, Pi-native way to jump toward playback.
- **GIF Stream** — a lean-back "channel" that turns Reddit's GIF-heavy subreddits
  into a continuous, auto-advancing, full-bleed stream. Uses Reddit's public
  read-only listing JSON (no account or API key), prefers each post's MP4 form
  (smaller, smoother, hardware-decoded) and falls back to a real `.gif`. Buffers
  ahead, paginates for an endless feed, and skips any clip whose media won't load.
- **Arrow-key / remote navigation** — custom spatial focus manager; also works with a mouse.

## Requirements

- Raspberry Pi (Pi 3/4/5 or Zero 2 W) running Raspberry Pi OS with a desktop.
- **Node.js 18+** (`sudo apt install nodejs npm`, or nodesource for a newer build).
- Chromium (`sudo apt install chromium-browser`) for kiosk display.
- A Trakt API app — create one at <https://trakt.tv/oauth/applications/new> with
  redirect URI `urn:ietf:wg:oauth:2.0:oob`.

## Setup

```bash
cd pi
npm install                 # installs express (the only dependency)
cp .env.example .env        # then edit .env and paste your client id/secret
npm start                   # serves http://localhost:8730
```

Open `http://<pi-ip>:8730` in a browser to try it, or run the kiosk on the Pi itself.

### Run fullscreen on the TV (kiosk)

```bash
PORT=8730 ./scripts/kiosk.sh
```

To start on boot, run the **server** as a systemd service and launch the **kiosk**
from the desktop session's autostart:

```bash
# 1) server on boot
sudo cp scripts/trakt-tv.service /etc/systemd/system/
#    edit User= / WorkingDirectory= inside the unit to match your path
sudo systemctl daemon-reload && sudo systemctl enable --now trakt-tv

# 2) kiosk on login (LXDE autostart example)
mkdir -p ~/.config/autostart
cat > ~/.config/autostart/trakt-kiosk.desktop <<EOF
[Desktop Entry]
Type=Application
Name=Trakt Kiosk
Exec=/home/pi/tester/pi/scripts/kiosk.sh
X-GNOME-Autostart-enabled=true
EOF
```

## Controls

| Key / remote | Action |
|---|---|
| Arrow keys / D-pad | Move focus |
| Enter / OK | Select |
| Backspace / Back | Go back |
| Esc | Go back |

### GIF Stream controls

| Key / remote | Action |
|---|---|
| ◀ / ▶ | Previous / next clip |
| Enter / OK / Space / P | Pause & resume |
| ▲ | Toggle the title/subreddit caption |
| ▼ | Cycle sort (Hot → Top → New → Rising) |
| M | Mute / unmute (most clips are silent) |
| Backspace / Esc | Back to Home |

## Configuration (env / `.env`)

| Var | Default | Purpose |
|---|---|---|
| `TRAKT_CLIENT_ID` | — | Trakt app client id (required) |
| `TRAKT_CLIENT_SECRET` | — | Trakt app client secret (required) |
| `PORT` | `8730` | Port the interface is served on |
| `TRAKT_TOKENS_PATH` | `pi/tokens.json` | Where OAuth tokens are cached |
| `WATCH_OPEN_CMD` | `xdg-open` | Command used to open a streaming web player |
| `GIF_SUBS` | `gifs+perfectloops+…` | `+`-joined subreddits for the GIF Stream |
| `REDDIT_USER_AGENT` | `trakt-pi/0.1 (gif-stream)` | User-Agent sent to Reddit |

## Notes

- **"Ways to watch"** opens a service's web player via `WATCH_OPEN_CMD` (`xdg-open`).
  In a single kiosk window that may replace the current page; the UI falls back to
  navigating the browser if the command isn't available. Press **Back** to return.
  Some services' web players are Widevine-limited in Chromium on ARM — YouTube and
  Prime/Netflix web generally work; results vary by service and Pi model.
- `tokens.json`, `.env`, and `node_modules/` are gitignored — nothing secret is committed.
- This is an unofficial client; "Trakt" is a trademark of its owners.
