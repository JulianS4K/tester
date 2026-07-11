import express from 'express';
import { config } from './config.js';
import { traktFetch, toItem } from './trakt.js';
import * as auth from './auth.js';
import { providersFor, openInBrowser } from './watch.js';
import { fetchGifs } from './reddit.js';

const app = express();
app.use(express.json());
app.use(express.static(config.publicDir));

const EXTENDED = 'full,images';

/** Wrap an async route so rejections become 500s instead of crashing the process. */
const wrap = (fn) => (req, res) => fn(req, res).catch((e) => {
  console.error('[api]', req.path, e);
  res.status(500).json({ error: e.message || 'server error' });
});

// --- Session / config ---

app.get('/api/config', (req, res) => {
  res.json({
    configured: config.isConfigured(),
    signedIn: auth.isSignedIn(),
    username: auth.currentUsername(),
  });
});

// --- Auth (device flow) ---

app.post('/api/auth/device', wrap(async (req, res) => {
  if (!config.isConfigured()) return res.status(400).json({ error: 'not_configured' });
  res.json(await auth.startDevice());
}));

app.get('/api/auth/poll', wrap(async (req, res) => {
  res.json(await auth.pollDevice());
}));

app.post('/api/auth/logout', (req, res) => {
  auth.logout();
  res.json({ ok: true });
});

// --- Discover ---

async function fetchRow(pathname, type, unwrap) {
  const r = await traktFetch(pathname, { query: { extended: EXTENDED, limit: 24 } });
  if (!r.ok || !Array.isArray(r.data)) return [];
  return r.data.map((x) => toItem(unwrap ? x[unwrap] : x, type)).filter(Boolean);
}

app.get('/api/home', wrap(async (req, res) => {
  const rows = [
    { title: 'Trending Shows', path: '/shows/trending', type: 'show', unwrap: 'show' },
    { title: 'Trending Movies', path: '/movies/trending', type: 'movie', unwrap: 'movie' },
    { title: 'Popular Shows', path: '/shows/popular', type: 'show', unwrap: null },
    { title: 'Popular Movies', path: '/movies/popular', type: 'movie', unwrap: null },
    { title: 'Anticipated Movies', path: '/movies/anticipated', type: 'movie', unwrap: 'movie' },
  ];
  const results = await Promise.all(
    rows.map(async (row) => ({ title: row.title, items: await fetchRow(row.path, row.type, row.unwrap) })),
  );
  res.json({ rows: results.filter((r) => r.items.length > 0) });
}));

app.get('/api/search', wrap(async (req, res) => {
  const q = (req.query.q || '').toString().trim();
  if (q.length < 2) return res.json({ items: [] });
  const r = await traktFetch('/search/movie,show', { query: { query: q, extended: EXTENDED, limit: 40 } });
  if (!r.ok || !Array.isArray(r.data)) return res.json({ items: [] });
  const items = r.data
    .map((row) => (row.movie ? toItem(row.movie, 'movie') : row.show ? toItem(row.show, 'show') : null))
    .filter(Boolean);
  res.json({ items });
}));

app.get('/api/detail', wrap(async (req, res) => {
  const type = req.query.type === 'movie' ? 'movie' : 'show';
  const id = (req.query.id || '').toString();
  if (!id) return res.status(400).json({ error: 'missing id' });
  const base = type === 'movie' ? 'movies' : 'shows';
  const [summary, related] = await Promise.all([
    traktFetch(`/${base}/${id}`, { query: { extended: EXTENDED } }),
    traktFetch(`/${base}/${id}/related`, { query: { extended: EXTENDED, limit: 12 } }),
  ]);
  if (!summary.ok) return res.status(summary.status).json({ error: 'not found' });
  res.json({
    item: toItem(summary.data, type),
    related: (Array.isArray(related.data) ? related.data : []).map((x) => toItem(x, type)).filter(Boolean),
  });
}));

// --- Library (OAuth) ---

app.get('/api/library', wrap(async (req, res) => {
  const token = await auth.accessToken();
  if (!token) return res.status(401).json({ error: 'not_signed_in' });
  const which = req.query.which === 'history' ? 'history' : 'watchlist';
  const r = await traktFetch(`/sync/${which}`, { token, query: { extended: EXTENDED, limit: 60 } });
  if (!r.ok || !Array.isArray(r.data)) return res.json({ items: [] });
  const items = r.data
    .map((row) => (row.movie ? toItem(row.movie, 'movie') : row.show ? toItem(row.show, 'show') : null))
    .filter(Boolean);
  res.json({ items });
}));

// --- Track actions (OAuth) ---

app.post('/api/track', wrap(async (req, res) => {
  const token = await auth.accessToken();
  if (!token) return res.status(401).json({ error: 'not_signed_in' });
  const { action, type, id } = req.body || {};
  if (!id || (action !== 'watchlist' && action !== 'history')) {
    return res.status(400).json({ error: 'bad_request' });
  }
  const ref = { ids: { trakt: Number(id) } };
  const body = type === 'movie' ? { movies: [ref] } : { shows: [ref] };
  const r = await traktFetch(`/sync/${action}`, { method: 'POST', token, body });
  res.status(r.ok ? 200 : r.status).json({ ok: r.ok });
}));

// --- Ways to watch ---

app.get('/api/watch/providers', (req, res) => {
  const title = (req.query.title || '').toString();
  res.json({ providers: providersFor(title) });
});

app.post('/api/watch/open', wrap(async (req, res) => {
  const url = (req.body?.url || '').toString();
  const opened = await openInBrowser(url);
  res.json({ opened });
}));

// --- GIF Stream (Reddit) ---

app.get('/api/gifs', wrap(async (req, res) => {
  const { items, after } = await fetchGifs({
    subs: (req.query.subs || '').toString() || undefined,
    sort: (req.query.sort || 'hot').toString(),
    t: (req.query.t || 'week').toString(),
    after: (req.query.after || '').toString(),
    nsfw: req.query.nsfw === '1' || req.query.nsfw === 'true',
  });
  res.json({ items, after, subs: config.gifSubs });
}));

// SPA fallback: any non-API route serves the app shell.
app.get(/^\/(?!api\/).*/, (req, res) => {
  res.sendFile('index.html', { root: config.publicDir });
});

app.listen(config.port, () => {
  console.log(`Trakt Pi interface on http://localhost:${config.port}`);
  if (!config.isConfigured()) {
    console.warn('  ⚠ TRAKT_CLIENT_ID / TRAKT_CLIENT_SECRET not set — copy .env.example to .env');
  }
});
