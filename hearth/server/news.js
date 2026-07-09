import Parser from 'rss-parser';
import { config } from './config.js';
import { store } from './store.js';

/**
 * RSS/Atom aggregator — the free, no-key equivalent of the ICS calendar feeds.
 * Fetches the configured news feeds, merges + sorts headlines, caches per feed.
 */
const parser = new Parser({ timeout: 10000 });
const cache = new Map(); // url -> { at, items }

async function fetchFeed(feed) {
  const ttl = Math.max(5, config.refreshMinutes) * 60 * 1000;
  const cached = cache.get(feed.url);
  if (cached && Date.now() - cached.at < ttl) return cached.items;
  const parsed = await parser.parseURL(feed.url);
  const items = (parsed.items || []).map((it) => ({
    title: it.title || '(untitled)',
    link: it.link || null,
    source: feed.name,
    date: it.isoDate || it.pubDate || null,
  }));
  cache.set(feed.url, { at: Date.now(), items });
  return items;
}

export function clearNewsCache() { cache.clear(); }

export async function getNews(limit = 40) {
  const feeds = store.newsFeeds();
  const all = [];
  await Promise.all(feeds.map(async (feed) => {
    try {
      all.push(...(await fetchFeed(feed)));
    } catch (e) {
      console.warn(`[news] feed failed (${feed.name}):`, e.message);
    }
  }));
  all.sort((a, b) => new Date(b.date || 0) - new Date(a.date || 0));
  return all.slice(0, limit);
}
