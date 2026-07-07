#!/usr/bin/env bash
# Launch the Trakt interface fullscreen in Chromium on the Pi's display.
# Run from the Pi's graphical session (e.g. from autostart). Adjust PORT to match .env.
set -euo pipefail

PORT="${PORT:-8730}"
URL="http://localhost:${PORT}"

# Pick whichever Chromium binary this Raspberry Pi OS ships.
CHROMIUM="$(command -v chromium-browser || command -v chromium || true)"
if [[ -z "${CHROMIUM}" ]]; then
  echo "Chromium not found. Install it: sudo apt install chromium-browser" >&2
  exit 1
fi

# Keep the screen awake.
command -v xset >/dev/null && { xset s off; xset -dpms; xset s noblank; } || true

# Wait for the server to come up.
for _ in $(seq 1 30); do
  if curl -sf "${URL}/api/config" >/dev/null 2>&1; then break; fi
  sleep 1
done

exec "${CHROMIUM}" \
  --kiosk \
  --app="${URL}" \
  --noerrdialogs \
  --disable-infobars \
  --disable-session-crashed-bubble \
  --check-for-update-interval=31536000 \
  --overscroll-history-navigation=0 \
  --autoplay-policy=no-user-gesture-required
