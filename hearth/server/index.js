import express from 'express';
import fs from 'node:fs';
import { config } from './config.js';
import { store } from './store.js';
import { getEvents, clearCache } from './calendar.js';
import { getWeather } from './weather.js';
import { getNews, clearNewsCache } from './news.js';
import { getOnThisDay } from './onthisday.js';

const app = express();
app.use(express.json());
app.use(express.static(config.publicDir));
app.use('/photos', express.static(config.photosDir));

const wrap = (fn) => (req, res) => fn(req, res).catch((e) => {
  console.error('[api]', req.path, e);
  res.status(500).json({ error: e.message || 'server error' });
});

// ---- State (calendars/chores/list/meals) ----
app.get('/api/state', (req, res) => res.json(store.snapshot()));

// ---- Calendar events ----
app.get('/api/events', wrap(async (req, res) => {
  const now = new Date();
  const start = req.query.start ? new Date(req.query.start) : new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const end = req.query.end ? new Date(req.query.end) : new Date(now.getFullYear(), now.getMonth() + 2, 0);
  res.json({ events: await getEvents(start.toISOString(), end.toISOString()) });
}));

// ---- Weather ----
app.get('/api/weather', wrap(async (req, res) => res.json((await getWeather()) || {})));

// ---- Calendars (feeds) ----
app.post('/api/calendars', (req, res) => {
  const { name, color, url } = req.body || {};
  if (!url) return res.status(400).json({ error: 'url required' });
  const cal = store.addCalendar(name, color, url);
  clearCache();
  res.json(cal ? { id: cal.id, name: cal.name, color: cal.color } : {});
});
app.delete('/api/calendars/:id', (req, res) => {
  store.removeCalendar(req.params.id);
  clearCache();
  res.json({ ok: true });
});

// ---- Chores ----
app.post('/api/chores', (req, res) => res.json(store.addChore(req.body?.person, req.body?.title)));
app.post('/api/chores/:id/toggle', (req, res) => res.json(store.toggleChore(req.params.id) || {}));
app.delete('/api/chores/:id', (req, res) => { store.removeChore(req.params.id); res.json({ ok: true }); });
app.post('/api/chores/reset', (req, res) => { store.resetChores(); res.json({ ok: true }); });

// ---- Shared list ----
app.post('/api/list', (req, res) => res.json(store.addListItem(req.body?.text)));
app.post('/api/list/:id/toggle', (req, res) => res.json(store.toggleListItem(req.params.id) || {}));
app.delete('/api/list/:id', (req, res) => { store.removeListItem(req.params.id); res.json({ ok: true }); });
app.post('/api/list/clear-done', (req, res) => { store.clearDone(); res.json({ ok: true }); });

// ---- Meals ----
app.put('/api/meals/:day', (req, res) => res.json(store.setMeal(req.params.day, req.body?.text)));

// ---- Health ----
app.post('/api/health/people', (req, res) => res.json(store.addPerson(req.body?.name, req.body?.emoji)));
app.delete('/api/health/people/:id', (req, res) => { store.removePerson(req.params.id); res.json({ ok: true }); });
app.post('/api/health/habits', (req, res) => res.json(store.addHabit(req.body?.personId, req.body?.name, req.body?.emoji)));
app.delete('/api/health/habits/:id', (req, res) => { store.removeHabit(req.params.id); res.json({ ok: true }); });
app.post('/api/health/habits/:id/toggle', (req, res) => { store.toggleHabit(req.params.id); res.json({ ok: true }); });
app.post('/api/health/water', (req, res) => res.json(store.incWater(req.body?.personId, Number(req.body?.delta) || 1)));
app.post('/api/health/metric', (req, res) => res.json(store.setMetric(req.body?.personId, req.body?.key, req.body?.value)));

// ---- News ----
app.get('/api/news', wrap(async (req, res) => res.json({ items: await getNews() })));
app.post('/api/news', (req, res) => {
  const f = store.addNewsFeed(req.body?.name, req.body?.url);
  clearNewsCache();
  res.json(f ? { id: f.id, name: f.name } : {});
});
app.delete('/api/news/:id', (req, res) => { store.removeNewsFeed(req.params.id); clearNewsCache(); res.json({ ok: true }); });

// ---- Notes ----
app.put('/api/notes', (req, res) => res.json({ notes: store.setNotes(req.body?.notes) }));

// ---- Reminders / countdowns ----
app.post('/api/reminders', (req, res) => res.json(store.addReminder(req.body?.title, req.body?.date, req.body?.emoji)));
app.delete('/api/reminders/:id', (req, res) => { store.removeReminder(req.params.id); res.json({ ok: true }); });

// ---- Theme ----
app.put('/api/theme', (req, res) => res.json(store.setTheme(req.body?.accent)));

// ---- On This Day (Wikipedia, no key) ----
app.get('/api/onthisday', wrap(async (req, res) => res.json({ items: await getOnThisDay() })));

// ---- Photos (slideshow) ----
app.get('/api/photos', (req, res) => {
  let files = [];
  try {
    files = fs.readdirSync(config.photosDir).filter((f) => /\.(jpe?g|png|webp|gif)$/i.test(f));
  } catch { /* no photos dir yet */ }
  res.json({ photos: files.map((f) => `/photos/${encodeURIComponent(f)}`) });
});

// SPA fallback.
app.get(/^\/(?!api\/|photos\/).*/, (req, res) => res.sendFile('index.html', { root: config.publicDir }));

app.listen(config.port, () => {
  console.log(`Hearth family calendar on http://localhost:${config.port}`);
  if (store.calendars().length === 0) {
    console.log('  ℹ No calendars yet — add .ics feeds in Settings (top-right on the board).');
  }
});
