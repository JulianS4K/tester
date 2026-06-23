// ── Content provider interface ───────────────────────────────────────
// A "provider" answers one question: "what's showing on this channel?"
// The retro-TV UI only depends on this interface, so the backend is
// swappable — start on the mock, then drop in a real one later:
//
//   • mock                 — built-in fake data, no API key (default)
//   • TMDB                 — free art/trailers/metadata (JustWatch-powered)
//   • Watchmode            — per-service deep-links
//   • Streaming Availability (movieofthenight / RapidAPI) — catalog + links
//   • Reelgood             — B2B/partner only
//
// A provider implements:
//   async nowShowing(channel) -> { title, subtitle, art } | null
//
// To add a real one: create ./tmdb.js exporting the same shape and swap
// the import below.

import { MockProvider } from "./mock.js";

export const provider = new MockProvider();
