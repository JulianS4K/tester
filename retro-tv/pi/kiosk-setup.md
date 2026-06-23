# Raspberry Pi kiosk setup

Boot the Pi straight into the Retro TV, fullscreen, with a remote. Tested
against Raspberry Pi OS (Bookworm) on a Pi 4/5. **A Pi 5 is strongly
recommended** — video playback is far smoother.

## ⚠️ Read first: the Widevine / DRM caveat
The DRM streaming services have a hard limit on a Pi:

- **Netflix, Disney+, Prime Video, Max, Hulu** use **Widevine DRM**, which on a
  Raspberry Pi only runs at **L3 → 720p max** (no HD/4K). It *works*, just not
  crisp. Chromium also needs Widevine enabled (it isn't bundled like on a PC).
- **YouTube, Plex/Jellyfin, IPTV/FAST, Twitch** have **no such cap** — full
  quality. (That's why YouTube is Channel 2.)

If 720p Netflix on the couch is fine, you're good. If you want HD/4K of the
paid services, an Android TV / Fire stick / Apple TV is the better playback box
and this UI would run as a launcher there instead.

## 1. Serve the app
Either serve the static files locally or just point Chromium at the folder via a
tiny server (modules need HTTP, not `file://`):

```bash
# install once
sudo apt update && sudo apt install -y chromium-browser unclutter

# from the retro-tv folder, run a local server on boot (see systemd unit below)
python3 -m http.server 8080
```

## 2. Auto-start Chromium in kiosk mode
Create `~/.config/autostart/retrotv.desktop`:

```ini
[Desktop Entry]
Type=Application
Name=RetroTV
Exec=chromium-browser --kiosk --noerrdialogs --disable-infobars \
  --autoplay-policy=no-user-gesture-required \
  --check-for-update-interval=31536000 \
  --app=http://localhost:8080
```

Set `LAUNCH_MODE = "navigate"` in `config/channels.js` so OK replaces the page
with the service fullscreen (new tabs don't make sense in kiosk mode).

> **Coming back from a service:** once Chromium navigates to Netflix, our JS is
> gone. Bind a remote "Home"/"Back" button to reload the TV URL. Two easy ways:
> a) map the remote key to keystroke `Alt+Home` and set Chromium's home page to
> `http://localhost:8080`; or b) run a tiny key-watcher (`triggerhappy`/`xbindkeys`)
> that runs `xdotool key ctrl+l type ...` to renavigate. Simplest of all: a
> dedicated remote button that sends `Super`/`Home` → `xdotool` reloads localhost.

## 3. Serve on boot (systemd)
`/etc/systemd/system/retrotv.service`:

```ini
[Unit]
Description=Retro TV static server
After=network.target

[Service]
WorkingDirectory=/home/pi/tester/retro-tv
ExecStart=/usr/bin/python3 -m http.server 8080
Restart=always
User=pi

[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl enable --now retrotv
```

## 4. The remote
The UI listens for plain keys, so almost any remote works once it emits them:

| Remote button | Key to emit | Action |
|---|---|---|
| Channel + | `ArrowUp` / `PageUp` | next channel |
| Channel − | `ArrowDown` / `PageDown` | prev channel |
| OK / Select | `Enter` | launch service |
| Guide / Menu | `g` | TV guide |
| 0–9 | digits | jump to channel |

Good options:
- **Flirc USB** — point-and-learn; maps any IR remote to keystrokes in hardware
  (no software config). Easiest path.
- **USB "air mouse" / mini keyboard remote** — already sends arrow keys + Enter.
- **HDMI-CEC** — use the TV's own remote via `cec-utils` + a key bridge.
- **Phone as remote** — a tiny web page that POSTs keys (can add later).

## 5. Nice-to-haves
- `unclutter -idle 0` hides the mouse cursor (the CSS already hides it too).
- Disable screen blanking: add `xset s off -dpms` to autostart.
- Hide the boot text / rainbow splash for a cleaner power-on in `config.txt`.
