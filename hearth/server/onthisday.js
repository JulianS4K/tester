/**
 * "On This Day" facts from Wikipedia's free REST feed (no API key). Cached for
 * the day. Falls back to an empty list on failure.
 */
let cache = { key: null, items: [] };

const pad = (n) => String(n).padStart(2, '0');

export async function getOnThisDay() {
  const now = new Date();
  const key = `${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  if (cache.key === key && cache.items.length) return cache.items;

  const url = `https://en.wikipedia.org/api/rest_v1/feed/onthisday/events/${pad(now.getMonth() + 1)}/${pad(now.getDate())}`;
  try {
    const res = await fetch(url, {
      headers: { 'User-Agent': 'HearthBoard/0.1 (personal DIY family board)', Accept: 'application/json' },
    });
    if (!res.ok) return cache.items;
    const data = await res.json();
    const items = (data.events || [])
      .filter((e) => e.year && e.text)
      .map((e) => ({ year: e.year, text: e.text }))
      .sort(() => 0)
      .slice(0, 20);
    cache = { key, items };
    return items;
  } catch {
    return cache.items;
  }
}
