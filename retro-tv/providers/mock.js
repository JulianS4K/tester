// ── Mock provider ────────────────────────────────────────────────────
// Returns believable "now showing" data with zero API keys so the TV is
// fully runnable today. Swap for a real provider (TMDB/Watchmode/etc.)
// without touching the UI — it only relies on `nowShowing(channel)`.

const SAMPLES = {
  YouTube: [
    { title: "Your Subscriptions", subtitle: "New from channels you follow" },
    { title: "Trending Now", subtitle: "What's popular today" },
    { title: "Live: 24/7 Lo-fi", subtitle: "Beats to relax / study to" },
  ],
  Netflix: [
    { title: "Top 10 in your country", subtitle: "Trending this week" },
    { title: "Continue Watching", subtitle: "Pick up where you left off" },
    { title: "New Releases", subtitle: "Fresh originals" },
  ],
  "Disney+": [
    { title: "Marvel Cinematic Universe", subtitle: "Watch in order" },
    { title: "Star Wars Saga", subtitle: "A galaxy far, far away" },
    { title: "Pixar Classics", subtitle: "Family movie night" },
  ],
  "Prime Video": [
    { title: "Amazon Originals", subtitle: "Exclusive series & films" },
    { title: "Included with Prime", subtitle: "No extra charge" },
  ],
  Max: [
    { title: "HBO Originals", subtitle: "Prestige drama" },
    { title: "Max Movies", subtitle: "Blockbusters & classics" },
  ],
  Hulu: [
    { title: "Next-Day TV", subtitle: "Last night's episodes" },
    { title: "Hulu Originals", subtitle: "Exclusive series" },
  ],
  "Apple TV+": [
    { title: "Apple Originals", subtitle: "Award-winning series" },
  ],
};

export class MockProvider {
  async nowShowing(channel) {
    const list = SAMPLES[channel.name];
    if (!list || list.length === 0) return null;
    // Rotate by minute so the "channel" feels alive across surfs.
    const idx = new Date().getMinutes() % list.length;
    return { ...list[idx], art: null };
  }
}
