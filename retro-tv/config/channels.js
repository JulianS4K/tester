// ── Your channel lineup ──────────────────────────────────────────────
// Edit this file to add/remove "channels". Each channel deep-links into a
// streaming service's web player. On a Raspberry Pi running Chromium kiosk,
// `launch` navigates the browser straight to that service, fullscreen.
//
// Tips:
//  • YouTube uses the 10-foot "TV" UI (youtube.com/tv) — remote-friendly.
//  • Some services support deep-links to a specific title; drop them in `launch`.
//  • `color` is the channel's accent (used on the guide + now-showing bar).

export const CHANNELS = [
  {
    number: 2,
    name: "YouTube",
    tagline: "Subscriptions & live",
    color: "#ff0033",
    // 10-foot leanback UI — best for a remote on the couch:
    launch: "https://www.youtube.com/tv",
  },
  {
    number: 3,
    name: "Netflix",
    tagline: "Originals & films",
    color: "#e50914",
    launch: "https://www.netflix.com",
  },
  {
    number: 4,
    name: "Disney+",
    tagline: "Disney · Pixar · Marvel · Star Wars",
    color: "#1f80e0",
    launch: "https://www.disneyplus.com",
  },
  {
    number: 5,
    name: "Prime Video",
    tagline: "Amazon Originals",
    color: "#00a8e1",
    launch: "https://www.primevideo.com",
  },
  {
    number: 6,
    name: "Max",
    tagline: "HBO & more",
    color: "#7b2ff7",
    launch: "https://play.max.com",
  },
  {
    number: 7,
    name: "Hulu",
    tagline: "TV & next-day episodes",
    color: "#1ce783",
    launch: "https://www.hulu.com",
  },
  {
    number: 8,
    name: "Apple TV+",
    tagline: "Apple Originals",
    color: "#dddddd",
    launch: "https://tv.apple.com",
  },
];

// How a channel opens when you press OK:
//   "navigate" → replace this page with the service (correct for a Pi kiosk;
//                bind a remote "Home" key to re-open the TV URL to come back).
//   "newtab"   → open the service in a new tab and keep the TV running
//                (handy for testing in a normal desktop browser).
export const LAUNCH_MODE = "newtab";
