import { api } from './api.js';

const view = document.getElementById('view');
const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MEAL_DAYS = [['mon', 'Monday'], ['tue', 'Tuesday'], ['wed', 'Wednesday'], ['thu', 'Thursday'], ['fri', 'Friday'], ['sat', 'Saturday'], ['sun', 'Sunday']];

let tab = 'calendar';
let monthCursor = firstOfMonth(new Date());
let gridEvents = [];
let upcoming = [];
let state = { calendars: [], chores: [], list: [], meals: {} };

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
  document.getElementById('time').textContent = now.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  document.getElementById('date').textContent = now.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });
}

// ---------- weather ----------
async function loadWeather() {
  const w = await api.weather().catch(() => null);
  const el = document.getElementById('weather');
  if (!w || !w.current) { el.innerHTML = ''; return; }
  const days = (w.daily || []).slice(1, 4).map((d) => {
    const wd = DAY_NAMES[new Date(d.date + 'T12:00:00').getDay()];
    return `<span>${wd} ${d.icon} ${d.max}°</span>`;
  }).join('');
  el.innerHTML = `<span class="now">${w.current.icon} ${w.current.temp}°${w.unit}</span><span class="days">${days}</span>`;
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
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === tab));
  if (tab === 'calendar') renderCalendarView();
  else if (tab === 'chores') renderChores();
  else if (tab === 'list') renderList();
  else if (tab === 'meals') renderMeals();
}

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
  await Promise.all([loadState(), loadGrid(), loadUpcoming(), loadWeather(), loadPhotos()]);
  render();
  resetIdle();
  setInterval(loadWeather, 15 * 60_000);
  setInterval(() => { loadGrid(); loadUpcoming(); }, 15 * 60_000);
  setInterval(loadPhotos, 10 * 60_000);
}

boot();
