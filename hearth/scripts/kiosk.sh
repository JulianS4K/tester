#!/usr/bin/env bash
# Launch Hearth fullscreen (touch kiosk) in Chromium on the Pi's display.
set -euo pipefail

PORT="${PORT:-8080}"
URL="http://localhost:${PORT}"

CHROMIUM="$(command -v chromium-browser || command -v chromium || true)"
if [[ -z "${CHROMIUM}" ]]; then
  echo "Chromium not found: sudo apt install chromium-browser" >&2
  exit 1
fi

# Keep the display awake.
command -v xset >/dev/null && { xset s off; xset -dpms; xset s noblank; } || true

# Wait for the server.
for _ in $(seq 1 30); do
  curl -sf "${URL}/api/state" >/dev/null 2>&1 && break
  sleep 1
done

exec "${CHROMIUM}" \
  --kiosk \
  --app="${URL}" \
  --touch-events=enabled \
  --noerrdialogs \
  --disable-infobars \
  --disable-session-crashed-bubble \
  --check-for-update-interval=31536000 \
  --overscroll-history-navigation=0
