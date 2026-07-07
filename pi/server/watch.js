import { exec } from 'node:child_process';
import { config } from './config.js';

const enc = encodeURIComponent;

/**
 * Streaming services reachable from a Pi's browser. Each builds a web-player
 * search URL for the title — the closest a Pi kiosk gets to "launch to watch"
 * (Widevine-limited, but Netflix/Prime/Disney+/YouTube web players work in
 * Chromium). Trakt has no entitlement data, so these are best-effort searches.
 */
export const PROVIDERS = [
  { id: 'netflix', name: 'Netflix', url: (t) => `https://www.netflix.com/search?q=${enc(t)}` },
  { id: 'youtube', name: 'YouTube', url: (t) => `https://www.youtube.com/results?search_query=${enc(t)}` },
  { id: 'prime', name: 'Prime Video', url: (t) => `https://www.amazon.com/s?k=${enc(t)}&i=instant-video` },
  { id: 'disney', name: 'Disney+', url: (t) => `https://www.disneyplus.com/search?q=${enc(t)}` },
  { id: 'max', name: 'Max', url: (t) => `https://play.max.com/search?q=${enc(t)}` },
  { id: 'hulu', name: 'Hulu', url: (t) => `https://www.hulu.com/search?q=${enc(t)}` },
  { id: 'appletv', name: 'Apple TV', url: (t) => `https://tv.apple.com/search?term=${enc(t)}` },
];

export function providersFor(title) {
  return PROVIDERS.map((p) => ({ id: p.id, name: p.name, url: p.url(title) }));
}

/** Open a URL in the Pi's browser via the configured command (e.g. xdg-open). */
export function openInBrowser(url) {
  return new Promise((resolve) => {
    if (typeof url !== 'string' || !/^https?:\/\//.test(url)) return resolve(false);
    // url is passed as a single quoted argv item; no shell interpolation of it.
    exec(`${config.watchOpenCmd} "${url.replace(/"/g, '%22')}"`, (err) => resolve(!err));
  });
}
