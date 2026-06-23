# 📺 Retro TV — channel-surf your streaming services

An old-school CRT television for the couch. Spin the "dial" to surf channels;
press **OK** to launch the real streaming service (your paid subscriptions +
YouTube) fullscreen. Built to run on a **Raspberry Pi** in Chromium kiosk mode,
but it's just a static web app — it runs in any browser.

> **Deep-link launcher, by design.** This does *not* (and legally cannot) embed
> Netflix/Disney video inside itself — those are DRM-locked to their own apps.
> Instead each channel hands off to the service's official web player. The retro
> TV is the *remote + guide*; the services do the playing.

## Try it now (any computer)
```bash
cd retro-tv
python3 -m http.server 8080
# open http://localhost:8080
```
A module-aware static server is required (so `python -m http.server`, not just
opening the file). Controls:

| Key (remote) | Action |
|---|---|
| **▲ / ▼** (or PageUp/Down) | Channel up / down |
| **0–9** | Type a channel number |
| **Enter / Space** = OK | Launch the current service |
| **G** | TV guide (all channels + what's on) |

In a desktop browser, channels open in a **new tab** so the TV stays put
(`LAUNCH_MODE = "newtab"` in `config/channels.js`). On a Pi kiosk, set it to
`"navigate"`.

## Make it yours
- **Channels & deep-links:** edit `config/channels.js` — add/remove services,
  reorder, change accent colors. YouTube uses the remote-friendly 10-foot UI
  (`youtube.com/tv`).
- **"Now showing" data:** `providers/` is a swappable backend. Today it uses a
  built-in **mock** (no API keys). Drop in a real provider later — see below.

## "What's on" backends (optional, later)
The UI only depends on `provider.nowShowing(channel)`, so the data source is
pluggable:

| Provider | Gives you | Cost |
|---|---|---|
| **mock** (default) | believable fake data | free, no key |
| **TMDB** | posters / trailers / metadata (JustWatch-powered) | free key |
| **Watchmode** | per-service deep-links + catalog | free tier |
| **Streaming Availability** (movieofthenight) | catalog + direct deep-links | free tier |
| **Reelgood** | catalog + links | B2B/partner deal |

Add one by creating e.g. `providers/tmdb.js` that exports a class with
`async nowShowing(channel)`, then swap the import in `providers/index.js`.

## Run on a Raspberry Pi (kiosk)
See [`pi/kiosk-setup.md`](pi/kiosk-setup.md) for boot-to-fullscreen Chromium,
mapping a USB/IR remote to the channel keys, and the **Widevine 720p caveat**
(DRM services cap at 720p on a Pi; YouTube/FAST/your-own media are full quality).

## Files
```
retro-tv/
  index.html            CRT markup
  assets/crt.css        scanlines, static, vignette, power-on, guide
  assets/app.js         channel surfing, launch, guide, remote input
  config/channels.js    YOUR lineup + deep-links  ← edit this
  providers/            swappable "what's on" backend (mock by default)
  pi/kiosk-setup.md     Raspberry Pi kiosk + remote notes
```
