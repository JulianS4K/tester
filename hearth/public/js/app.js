import { api } from './api.js';

const view = document.getElementById('view');
const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MEAL_DAYS = [['mon', 'Monday'], ['tue', 'Tuesday'], ['wed', 'Wednesday'], ['thu', 'Thursday'], ['fri', 'Friday'], ['sat', 'Saturday'], ['sun', 'Sunday']];

let tab = 'home';
let monthCursor = firstOfMonth(new Date());
let gridEvents = [];
let upcoming = [];
let weather = null;
let newsItems = [];
let state = { calendars: [], chores: [], list: [], meals: {}, health: { people: [], habits: [], today: {} }, notes: '' };
let calIdx = 0;
let newsIdx = 0;
let photoIdx = 0;
let tileTimers = [];

// ---------- helpers ----------
const pad = (n) => String(n).padStart(2, '0');
const localYmd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
function firstOfMonth(d) { return new Date(d.getFullYear(), d.getMonth(), 1); }
function addDays(d, n) { const x = new Date(d); x.setDate(x.getDate() + n); return x; }
function timeStr(iso) { return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }); }

// ---------- clock ----------
function tickClock() {
  const now = new Date();
  const t = now.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  const d = now.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });
  document.getElementById('time').textContent = t;
  document.getElementById('date').textContent = d;
  const tt = document.getElementById('tile-time'); if (tt) tt.textContent = t;
  const td = document.getElementById('tile-date'); if (td) td.textContent = d;
}

// ---------- weather ----------
async function loadWeather() {
  weather = await api.weather().catch(() => null);
  const el = document.getElementById('weather');
  if (!weather || !weather.current) { if (el) el.innerHTML = ''; return; }
  const days = (weather.daily || []).slice(1, 4).map((d) => {
    const wd = DAY_NAMES[new Date(d.date + 'T12:00:00').getDay()];
    return `<span>${wd} ${d.icon} ${d.max}°</span>`;
  }).join('');
  if (el) el.innerHTML = `<span class="now">${weather.current.icon} ${weather.current.temp}°${weather.unit}</span><span class="days">${days}</span>`;
  if (tab === 'home') fillWeatherTile();
}

async function loadNews() {
  newsItems = (await api.news().catch(() => ({ items: [] }))).items || [];
  if (tab === 'home') refreshNewsTile();
  if (tab === 'news') renderNews();
}

// ---------- data ----------
async function loadState() { state = await api.state(); if (tab !== 'calendar') render(); else renderAgenda(); }
async function loadGrid() {
  const start = addDays(monthCursor, -7);
  const end = addDays(firstOfMonth(addDays(monthCursor, 40)), 14);
  gridEvents = (await api.events(start, end).catch(() => ({ events: [] }))).events || [];
  if (tab === 'calendar') renderCalendar();
}
async function loadUpcoming() {
  const today = new Date(); today.setHours(0, 0, 0, 0);
  upcoming = (await api.events(today, addDays(today, 8)).catch(() => ({ events: [] }))).events || [];
  if (tab === 'calendar') renderAgenda();
}

function eventsOnDay(list, dayKey) {
  return list.filter((ev) => ev.allDay
    ? (ev.startDate <= dayKey && ev.endDate >= dayKey)
    : localYmd(new Date(ev.start)) === dayKey);
}

// ---------- render: tabs ----------
function render() {
  clearTiles();
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === tab));
  if (tab === 'home') renderHome();
  else if (tab === 'calendar') renderCalendarView();
  else if (tab === 'chores') renderChores();
  else if (tab === 'list') renderList();
  else if (tab === 'meals') renderMeals();
  else if (tab === 'health') renderHealth();
  else if (tab === 'news') { renderNews(); loadNews(); }
}

// ---------- render: Live Tiles home ----------
const TILE = (cls, bg, label, inner, attrs = '') =>
  `<div class="tile ${cls}" style="background:${bg}" ${attrs}><div class="t-label">${label}</div>${inner}</div>`;

function renderHome() {
  const dayKey = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'][new Date().getDay()];
  const meal = state.meals[dayKey] || '—';
  const choresDone = state.chores.filter((c) => c.done).length;
  const habits = state.health?.habits || [];
  const habitsDone = habits.filter((h) => h.doneToday).length;

  view.innerHTML = `<div class="tiles">
    ${TILE('wide', '#1f6feb', 'Clock', '<div class="face"><div class="t-main" id="tile-time">--:--</div><div class="t-sub" id="tile-date"></div></div>', 'data-tab="calendar"')}
    ${TILE('', '#0b7285', 'Weather', '<div class="face" id="tile-weather"></div>', 'data-tab="calendar"')}
    ${TILE('wide', '#2f9e44', 'Up Next', '<div class="face" id="tile-cal"></div>', 'data-tab="calendar"')}
    ${TILE('wide', '#c2410c', 'News', '<div class="face" id="tile-news"></div>', 'data-tab="news"')}
    ${TILE('', '#6741d9', 'Chores', `<div class="face"><div class="t-main">${choresDone}/${state.chores.length}</div><div class="t-sub">done</div></div>`, 'data-tab="chores"')}
    ${TILE('', '#d6336c', 'Health', `<div class="face"><div class="t-main">${habitsDone}/${habits.length}</div><div class="t-sub">habits</div></div>`, 'data-tab="health"')}
    ${TILE('', '#e8590c', 'Tonight', `<div class="face"><div class="t-main small">${esc(meal)}</div><div class="t-sub">dinner</div></div>`, 'data-tab="meals"')}
    ${TILE('', '#1c7ed6', 'Lists', `<div class="face"><div class="t-main">${state.list.length}</div><div class="t-sub">items</div></div>`, 'data-tab="list"')}
    ${TILE('tall photo', '#111', '', '<div class="scrim"></div>', 'id="tile-photo" data-tab="home"')}
    ${TILE('wide', '#495057', 'Notes', '<div class="face"><div class="t-main small" id="tile-notes"></div></div>', 'data-action="notes"')}
  </div>`;

  view.querySelectorAll('[data-tab]').forEach((t) => t.addEventListener('click', () => { tab = t.dataset.tab; render(); }));
  const notesTile = view.querySelector('[data-action="notes"]');
  if (notesTile) notesTile.addEventListener('click', openNotes);

  tickClock();
  fillWeatherTile();
  refreshCalTile();
  refreshNewsTile();
  refreshPhotoTile();
  refreshNotesTile();
  startTiles();
}

function applyFlip(el) { if (!el) return; el.classList.remove('flip'); void el.offsetWidth; el.classList.add('flip'); }

function fillWeatherTile() {
  const el = document.getElementById('tile-weather');
  if (!el) return;
  if (!weather?.current) { el.innerHTML = '<div class="t-main small">—</div>'; return; }
  const hi = weather.daily?.[0];
  el.innerHTML = `<div class="t-emoji">${weather.current.icon}</div><div class="t-main">${weather.current.temp}°${weather.unit}</div>` +
    `<div class="t-sub">${esc(weather.current.label)}${hi ? ` · ${hi.max}°/${hi.min}°` : ''}</div>`;
}

function refreshCalTile() {
  const el = document.getElementById('tile-cal');
  if (!el) return;
  if (!upcoming.length) { el.innerHTML = '<div class="t-main small">No events</div>'; return; }
  calIdx %= upcoming.length;
  const ev = upcoming[calIdx];
  const when = ev.allDay ? 'All day' : timeStr(ev.start);
  const day = new Date(ev.allDay ? ev.startDate + 'T12:00:00' : ev.start).toLocaleDateString([], { weekday: 'short' });
  el.innerHTML = `<div class="corner">📅</div><div class="t-main small">${esc(ev.title)}</div><div class="t-sub">${day} · ${esc(when)}</div>`;
  applyFlip(el);
}

function refreshNewsTile() {
  const el = document.getElementById('tile-news');
  if (!el) return;
  if (!newsItems.length) { el.innerHTML = '<div class="t-main small">Add news feeds in the News tab</div>'; return; }
  newsIdx %= newsItems.length;
  const it = newsItems[newsIdx];
  el.innerHTML = `<div class="corner">📰</div><div class="t-main small">${esc(it.title)}</div><div class="t-sub">${esc(it.source || '')}</div>`;
  applyFlip(el);
}

function refreshPhotoTile() {
  const el = document.getElementById('tile-photo');
  if (!el) return;
  if (!photos.length) { el.style.background = '#111'; return; }
  photoIdx %= photos.length;
  el.style.backgroundImage = `url('${photos[photoIdx]}')`;
}

function refreshNotesTile() {
  const el = document.getElementById('tile-notes');
  if (el) el.textContent = state.notes ? state.notes.slice(0, 140) : 'Tap to add a note';
}

function startTiles() {
  clearTiles();
  tileTimers.push(setInterval(() => { calIdx++; refreshCalTile(); }, 6000));
  tileTimers.push(setInterval(() => { newsIdx++; refreshNewsTile(); }, 7000));
  tileTimers.push(setInterval(() => { photoIdx++; refreshPhotoTile(); }, 9000));
}
function clearTiles() { tileTimers.forEach(clearInterval); tileTimers = []; }

// ---------- render: calendar ----------
function renderCalendarView() {
  view.innerHTML =
    '<div class="cal-layout">' +
    '<div class="cal-main">' +
    '<div class="cal-head"><h2></h2><div class="cal-nav">' +
    '<button data-nav="-1">‹</button><button data-nav="0">Today</button><button data-nav="1">›</button></div></div>' +
    '<div class="grid" id="cal-grid"></div></div>' +
    '<div class="agenda" id="agenda"></div></div>';
  view.querySelectorAll('[data-nav]').forEach((b) => b.addEventListener('click', () => {
    const n = parseInt(b.dataset.nav, 10);
    monthCursor = n === 0 ? firstOfMonth(new Date()) : new Date(monthCursor.getFullYear(), monthCursor.getMonth() + n, 1);
    loadGrid();
    renderCalendar();
  }));
  renderCalendar();
  renderAgenda();
}

function renderCalendar() {
  const grid = document.getElementById('cal-grid');
  const titleEl = document.querySelector('.cal-head h2');
  if (!grid) return;
  if (titleEl) titleEl.textContent = monthCursor.toLocaleDateString([], { month: 'long', year: 'numeric' });

  const first = firstOfMonth(monthCursor);
  const start = addDays(first, -first.getDay()); // back to Sunday
  const todayKey = localYmd(new Date());
  let html = DAY_NAMES.map((d) => `<div class="dow">${d}</div>`).join('');

  for (let i = 0; i < 42; i++) {
    const day = addDays(start, i);
    const key = localYmd(day);
    const inMonth = day.getMonth() === monthCursor.getMonth();
    const evs = eventsOnDay(gridEvents, key).slice(0, 4);
    const total = eventsOnDay(gridEvents, key).length;
    const chips = evs.map((ev) => `<div class="ev ${ev.allDay ? 'allday' : ''}" style="background:${esc(ev.color)}">${ev.allDay ? '' : esc(timeStr(ev.start)) + ' '}${esc(ev.title)}</div>`).join('');
    const more = total > 4 ? `<div class="more">+${total - 4} more</div>` : '';
    html += `<div class="cell ${inMonth ? '' : 'dim'} ${key === todayKey ? 'today' : ''}"><span class="num">${day.getDate()}</span>${chips}${more}</div>`;
  }
  grid.innerHTML = html;
}

function renderAgenda() {
  const el = document.getElementById('agenda');
  if (!el) return;
  const byDay = {};
  upcoming.forEach((ev) => {
    const key = ev.allDay ? ev.startDate : localYmd(new Date(ev.start));
    (byDay[key] ||= []).push(ev);
  });
  const keys = Object.keys(byDay).sort();
  let html = '<h3>Up Next</h3>';
  if (!keys.length) html += '<div class="muted">Nothing scheduled. Add a calendar in Settings ⚙</div>';
  for (const key of keys.slice(0, 6)) {
    const label = new Date(key + 'T12:00:00').toLocaleDateString([], { weekday: 'long', month: 'short', day: 'numeric' });
    const items = byDay[key].map((ev) => `<div class="a-ev"><span class="dot" style="background:${esc(ev.color)}"></span><span class="a-time">${ev.allDay ? 'All day' : esc(timeStr(ev.start))}</span><span class="a-title">${esc(ev.title)}</span></div>`).join('');
    html += `<div class="day-group"><div class="day-label">${esc(label)}</div>${items}</div>`;
  }
  el.innerHTML = html;
}

// ---------- render: chores ----------
function renderChores() {
  const rows = state.chores.map((c) => `
    <div class="row-item ${c.done ? 'done' : ''}">
      <button class="check" data-chk="${c.id}">${c.done ? '✓' : ''}</button>
      <span class="label">${esc(c.title)}</span>
      ${c.person ? `<span class="who">${esc(c.person)}</span>` : ''}
      <button class="del" data-del="${c.id}">✕</button>
    </div>`).join('') || '<div class="empty">No chores yet. Add one below.</div>';
  view.innerHTML = `<div class="panel"><h2>Chores</h2>
    <div class="add-row"><input id="chore-person" style="max-width:160px" placeholder="Who" />
    <input id="chore-title" placeholder="Add a chore…" />
    <button class="btn accent" id="chore-add">Add</button>
    <button class="btn" id="chore-reset">Reset all</button></div>${rows}</div>`;
  view.querySelector('#chore-add').onclick = async () => {
    const title = view.querySelector('#chore-title').value.trim();
    if (!title) return;
    await api.addChore(view.querySelector('#chore-person').value.trim(), title);
    await loadState();
  };
  view.querySelector('#chore-reset').onclick = async () => { await api.resetChores(); await loadState(); };
  view.querySelectorAll('[data-chk]').forEach((b) => b.onclick = async () => { await api.toggleChore(b.dataset.chk); await loadState(); });
  view.querySelectorAll('[data-del]').forEach((b) => b.onclick = async () => { await api.removeChore(b.dataset.del); await loadState(); });
}

// ---------- render: list ----------
function renderList() {
  const rows = state.list.map((i) => `
    <div class="row-item ${i.done ? 'done' : ''}">
      <button class="check" data-chk="${i.id}">${i.done ? '✓' : ''}</button>
      <span class="label">${esc(i.text)}</span>
      <button class="del" data-del="${i.id}">✕</button>
    </div>`).join('') || '<div class="empty">List is empty.</div>';
  view.innerHTML = `<div class="panel"><h2>Lists &amp; Groceries</h2>
    <div class="add-row"><input id="list-text" placeholder="Add an item…" />
    <button class="btn accent" id="list-add">Add</button>
    <button class="btn" id="list-clear">Clear done</button></div>${rows}</div>`;
  const add = async () => {
    const t = view.querySelector('#list-text').value.trim();
    if (!t) return;
    await api.addListItem(t); await loadState();
  };
  view.querySelector('#list-add').onclick = add;
  view.querySelector('#list-text').addEventListener('keydown', (e) => { if (e.key === 'Enter') add(); });
  view.querySelector('#list-clear').onclick = async () => { await api.clearDone(); await loadState(); };
  view.querySelectorAll('[data-chk]').forEach((b) => b.onclick = async () => { await api.toggleListItem(b.dataset.chk); await loadState(); });
  view.querySelectorAll('[data-del]').forEach((b) => b.onclick = async () => { await api.removeListItem(b.dataset.del); await loadState(); });
}

// ---------- render: meals ----------
function renderMeals() {
  const rows = MEAL_DAYS.map(([k, label]) =>
    `<div class="mday">${label}</div><input data-meal="${k}" value="${esc(state.meals[k] || '')}" placeholder="What's for dinner?" />`).join('');
  view.innerHTML = `<div class="panel"><h2>This Week's Meals</h2><div class="meals-grid">${rows}</div></div>`;
  view.querySelectorAll('[data-meal]').forEach((inp) => {
    inp.addEventListener('change', () => api.setMeal(inp.dataset.meal, inp.value));
  });
}

// ---------- render: health ----------
const MOODS = ['😀', '🙂', '😐', '😕', '😢'];
function renderHealth() {
  const h = state.health || { people: [], habits: [], today: {} };
  const cards = h.people.map((p) => {
    const today = h.today[p.id] || {};
    const habits = h.habits.filter((x) => x.personId === p.id);
    const chips = habits.map((hb) => `<button class="habit-chip ${hb.doneToday ? 'done' : ''}" data-hab="${hb.id}">${hb.emoji || '✅'} ${esc(hb.name)}${hb.streak ? ` <span class="streak">🔥${hb.streak}</span>` : ''} <span class="del" data-rmhab="${hb.id}">✕</span></button>`).join('') || '<span class="muted">No habits yet</span>';
    const moods = MOODS.map((m) => `<button class="mood ${today.mood === m ? 'sel' : ''}" data-mood="${p.id}|${m}">${m}</button>`).join('');
    return `<div class="person-card">
      <div class="p-head"><span>${p.emoji || '🙂'}</span><span>${esc(p.name)}</span><button class="del" data-rmperson="${p.id}">✕</button></div>
      <div class="metric-row"><span class="lab">Mood</span>${moods}</div>
      <div class="metric-row"><span class="lab">Water</span><button class="pill" data-water="${p.id}|-1">−</button><span>💧 ${today.water || 0}</span><button class="pill" data-water="${p.id}|1">＋</button></div>
      <div class="metric-row"><span class="lab">Weight</span><input class="pill" style="width:120px" data-weight="${p.id}" value="${esc(today.weight || '')}" placeholder="—" /></div>
      <div style="margin-top:10px">${chips}</div>
      <div class="add-row" style="margin-top:12px"><input data-nh-name="${p.id}" placeholder="Add habit…" /><input data-nh-emoji="${p.id}" style="max-width:70px" placeholder="✅" /><button class="btn accent" data-addhab="${p.id}">+</button></div>
    </div>`;
  }).join('') || '<div class="empty">Add a family member to start tracking.</div>';
  view.innerHTML = `<div class="panel"><h2>Health &amp; Habits</h2>
    <div class="add-row"><input id="person-name" placeholder="Name" style="max-width:200px" /><input id="person-emoji" placeholder="🙂" style="max-width:70px" /><button class="btn accent" id="person-add">Add person</button></div>
    <div class="people">${cards}</div></div>`;
  view.querySelector('#person-add').onclick = async () => {
    const n = view.querySelector('#person-name').value.trim();
    if (!n) return;
    await api.addPerson(n, view.querySelector('#person-emoji').value.trim() || '🙂');
    await loadState();
  };
  view.querySelectorAll('[data-rmperson]').forEach((b) => b.onclick = async () => { await api.removePerson(b.dataset.rmperson); await loadState(); });
  view.querySelectorAll('[data-hab]').forEach((b) => b.onclick = async (e) => { if (e.target.dataset.rmhab) return; await api.toggleHabit(b.dataset.hab); await loadState(); });
  view.querySelectorAll('[data-rmhab]').forEach((b) => b.onclick = async (e) => { e.stopPropagation(); await api.removeHabit(b.dataset.rmhab); await loadState(); });
  view.querySelectorAll('[data-mood]').forEach((b) => b.onclick = async () => { const [pid, m] = b.dataset.mood.split('|'); await api.metric(pid, 'mood', m); await loadState(); });
  view.querySelectorAll('[data-water]').forEach((b) => b.onclick = async () => { const [pid, d] = b.dataset.water.split('|'); await api.water(pid, Number(d)); await loadState(); });
  view.querySelectorAll('[data-weight]').forEach((inp) => inp.addEventListener('change', () => api.metric(inp.dataset.weight, 'weight', inp.value)));
  view.querySelectorAll('[data-addhab]').forEach((b) => b.onclick = async () => {
    const pid = b.dataset.addhab;
    const name = view.querySelector(`[data-nh-name="${pid}"]`).value.trim();
    if (!name) return;
    await api.addHabit(pid, name, view.querySelector(`[data-nh-emoji="${pid}"]`).value.trim() || '✅');
    await loadState();
  });
}

// ---------- render: news ----------
function renderNews() {
  const feeds = state.news || [];
  const feedChips = feeds.map((f) => `<span class="who">${esc(f.name)} <button class="del" data-rmfeed="${f.id}" style="padding:0 6px">✕</button></span>`).join('');
  const items = newsItems.map((it) => `<div class="n-item"><span class="n-src">${esc(it.source || '')}</span><span class="n-title">${esc(it.title)}</span><span class="n-time">${it.date ? new Date(it.date).toLocaleDateString([], { month: 'short', day: 'numeric' }) : ''}</span></div>`).join('') || '<div class="empty">Add an RSS feed to see headlines.</div>';
  view.innerHTML = `<div class="panel"><h2>News</h2>
    <div class="add-row"><input id="feed-name" placeholder="Name" style="max-width:160px" /><input id="feed-url" placeholder="https://…/rss (any RSS/Atom URL)" /><button class="btn accent" id="feed-add">Add feed</button></div>
    <div style="margin-bottom:12px">${feedChips}</div>
    <div class="news-list">${items}</div></div>`;
  view.querySelector('#feed-add').onclick = async () => {
    const url = view.querySelector('#feed-url').value.trim();
    if (!url) return;
    await api.addNews(view.querySelector('#feed-name').value.trim() || 'News', url);
    await loadState(); await loadNews();
  };
  view.querySelectorAll('[data-rmfeed]').forEach((b) => b.onclick = async () => { await api.removeNews(b.dataset.rmfeed); await loadState(); await loadNews(); });
}

// ---------- notes modal ----------
function openNotes() {
  const modal = document.getElementById('modal');
  modal.innerHTML = `<div class="sheet"><h2>Family Notes</h2>
    <textarea class="notes" id="notes-area" placeholder="Shared notes…">${esc(state.notes || '')}</textarea>
    <div style="text-align:right;margin-top:12px"><button class="btn" id="notes-close">Done</button></div></div>`;
  modal.hidden = false;
  const area = modal.querySelector('#notes-area');
  let t;
  area.addEventListener('input', () => { clearTimeout(t); t = setTimeout(async () => { state.notes = area.value; await api.setNotes(area.value); refreshNotesTile(); }, 500); });
  modal.querySelector('#notes-close').onclick = () => { modal.hidden = true; };
}

// ---------- settings modal ----------
const PALETTE = ['#e5484d', '#2f80ed', '#2f9e44', '#f2994a', '#9b51e0', '#e6a817', '#12b5b0'];
function openSettings() {
  const modal = document.getElementById('modal');
  const cals = state.calendars.map((c) => `
    <div class="cal-list-item"><span class="swatch" style="background:${esc(c.color)}"></span>
    <span>${esc(c.name)}</span><button class="del" data-rmcal="${c.id}" style="margin-left:auto">✕</button></div>`).join('') ||
    '<div class="muted">No calendars yet.</div>';
  modal.innerHTML = `<div class="sheet">
    <h2>Calendars</h2>${cals}
    <h2 style="margin-top:20px;font-size:20px">Add a calendar (.ics link)</h2>
    <p class="muted">Export a secret iCal/ICS URL from Google, iCloud, Outlook, Cozi, a school/sports site — paste it here.</p>
    <div class="add-row"><input id="cal-name" style="max-width:150px" placeholder="Name" /><input id="cal-url" placeholder="https://…/basic.ics" /></div>
    <div class="add-row"><div id="palette" style="display:flex;gap:8px;flex:1"></div>
    <button class="btn accent" id="cal-add">Add</button></div>
    <div style="text-align:right;margin-top:16px"><button class="btn" id="close">Close</button></div>
  </div>`;
  modal.hidden = false;
  let chosen = PALETTE[state.calendars.length % PALETTE.length];
  const pal = modal.querySelector('#palette');
  PALETTE.forEach((col) => {
    const b = document.createElement('button');
    b.className = 'swatch'; b.style.background = col; b.style.width = '32px'; b.style.height = '32px'; b.style.border = col === chosen ? '3px solid #fff' : '0';
    b.onclick = () => { chosen = col; pal.querySelectorAll('.swatch').forEach((s) => s.style.border = '0'); b.style.border = '3px solid #fff'; };
    pal.appendChild(b);
  });
  modal.querySelector('#close').onclick = () => { modal.hidden = true; };
  modal.querySelector('#cal-add').onclick = async () => {
    const url = modal.querySelector('#cal-url').value.trim();
    if (!url) return;
    await api.addCalendar(modal.querySelector('#cal-name').value.trim() || 'Calendar', chosen, url);
    await loadState(); await loadGrid(); await loadUpcoming();
    openSettings();
  };
  modal.querySelectorAll('[data-rmcal]').forEach((b) => b.onclick = async () => {
    await api.removeCalendar(b.dataset.rmcal); await loadState(); await loadGrid(); await loadUpcoming(); openSettings();
  });
}

// ---------- screensaver ----------
let idleTimer = null;
let ssTimer = null;
let photos = [];
async function loadPhotos() { photos = (await api.photos().catch(() => ({ photos: [] }))).photos || []; }
function resetIdle() {
  clearTimeout(idleTimer);
  hideScreensaver();
  idleTimer = setTimeout(showScreensaver, 90_000);
}
function showScreensaver() {
  if (!photos.length) return;
  const ss = document.getElementById('screensaver');
  const img = document.getElementById('ss-img');
  let i = 0;
  const next = () => { img.src = photos[i % photos.length]; i++; };
  next();
  ss.hidden = false;
  ssTimer = setInterval(next, 8000);
}
function hideScreensaver() {
  const ss = document.getElementById('screensaver');
  if (!ss.hidden) ss.hidden = true;
  if (ssTimer) { clearInterval(ssTimer); ssTimer = null; }
}

// ---------- boot ----------
function bindChrome() {
  document.querySelectorAll('.tab').forEach((t) => t.addEventListener('click', () => { tab = t.dataset.tab; render(); }));
  document.getElementById('settings-btn').addEventListener('click', openSettings);
  ['pointerdown', 'keydown', 'mousemove'].forEach((ev) => window.addEventListener(ev, resetIdle, { passive: true }));
}

async function boot() {
  bindChrome();
  tickClock();
  setInterval(tickClock, 10_000);
  render();
  await Promise.all([loadState(), loadGrid(), loadUpcoming(), loadWeather(), loadPhotos(), loadNews()]);
  render();
  resetIdle();
  setInterval(loadWeather, 15 * 60_000);
  setInterval(() => { loadGrid(); loadUpcoming(); }, 15 * 60_000);
  setInterval(loadNews, 15 * 60_000);
  setInterval(loadPhotos, 10 * 60_000);
}

boot();
