import { api } from './api.js';
import { Nav } from './nav.js';

const content = document.getElementById('content');
const ROOTS = ['home', 'search', 'library', 'settings'];

let session = { configured: false, signedIn: false, username: null };
let stack = [{ route: 'home' }];
let currentRoot = 'home';
let pollTimer = null;
let searchTimer = null;

// ---------- helpers ----------

const esc = (s) =>
  String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function toast(text) {
  const t = document.getElementById('toast');
  t.textContent = text;
  t.hidden = false;
  clearTimeout(t._timer);
  t._timer = setTimeout(() => { t.hidden = true; }, 2200);
}

function spinner() { content.innerHTML = '<div class="spinner"></div>'; }
function message(text) { content.innerHTML = `<div class="message">${esc(text)}</div>`; }

function clearTimers() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null; }
}

function setActiveRail() {
  document.querySelectorAll('.rail-item').forEach((b) => b.classList.toggle('active', b.dataset.route === currentRoot));
}

function updateAccount() {
  document.getElementById('account-label').textContent = session.signedIn ? (session.username || 'Account') : 'Sign in';
}

// ---------- navigation ----------

function go(route) {
  currentRoot = ROOTS.includes(route) ? route : currentRoot;
  stack = [{ route }];
  render();
}

function push(entry) { stack.push(entry); render(); }
function back() { if (stack.length > 1) { stack.pop(); render(); } }

async function render() {
  clearTimers();
  setActiveRail();
  const top = stack[stack.length - 1];
  try {
    if (top.route === 'home') await renderHome();
    else if (top.route === 'search') renderSearch();
    else if (top.route === 'library') await renderLibrary();
    else if (top.route === 'settings') renderSettings();
    else if (top.route === 'signin') await renderSignIn();
    else if (top.route === 'detail') await renderDetail(top.item);
  } catch (e) {
    message('Something went wrong: ' + (e.message || e));
  }
  Nav.refresh('#content .focusable');
}

// ---------- cards / rows ----------

function cardEl(item) {
  const b = document.createElement('button');
  b.className = 'card focusable';
  const sub = [item.year, item.network].filter(Boolean).join(' · ');
  b.innerHTML =
    `<div class="poster">${item.poster
      ? `<img src="${esc(item.poster)}" alt="">`
      : `<span class="fallback">${esc(item.title)}</span>`}</div>` +
    `<div class="title">${esc(item.title)}</div><div class="sub">${esc(sub)}</div>`;
  const img = b.querySelector('img');
  if (img) img.onerror = () => { img.parentNode.innerHTML = `<span class="fallback">${esc(item.title)}</span>`; };
  b.addEventListener('click', () => push({ route: 'detail', item }));
  return b;
}

function rowEl(title, items) {
  const row = document.createElement('div');
  row.className = 'row';
  row.innerHTML = `<h2>${esc(title)}</h2>`;
  const scroll = document.createElement('div');
  scroll.className = 'row-scroll';
  items.forEach((it) => scroll.appendChild(cardEl(it)));
  row.appendChild(scroll);
  return row;
}

function gridEl(items) {
  const g = document.createElement('div');
  g.className = 'grid';
  items.forEach((it) => g.appendChild(cardEl(it)));
  return g;
}

// ---------- screens ----------

async function renderHome() {
  spinner();
  const { rows } = await api.home();
  if (!rows || !rows.length) {
    message(session.configured
      ? "Couldn't load anything. Check the Pi's connection."
      : 'Set TRAKT_CLIENT_ID / TRAKT_CLIENT_SECRET in .env and restart the server.');
    return;
  }
  content.innerHTML = '';
  rows.forEach((r) => content.appendChild(rowEl(r.title, r.items)));
}

function renderSearch() {
  content.innerHTML =
    '<h1 class="screen-title">Search</h1>' +
    '<input class="search-input focusable" type="text" placeholder="Search movies & shows…" />' +
    '<div id="results"></div>';
  const input = content.querySelector('.search-input');
  const results = content.querySelector('#results');
  const run = async () => {
    const q = input.value.trim();
    if (q.length < 2) { results.innerHTML = ''; return; }
    const { items } = await api.search(q);
    results.innerHTML = '';
    if (!items.length) { results.innerHTML = `<div class="message">No results for “${esc(q)}”.</div>`; }
    else results.appendChild(gridEl(items));
    Nav.refresh('#content .focusable');
  };
  input.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(run, 350); });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { clearTimeout(searchTimer); run(); } });
  setTimeout(() => Nav.refresh('.search-input'), 0);
}

async function renderLibrary() {
  if (!session.signedIn) {
    content.innerHTML =
      '<h1 class="screen-title">Library</h1>' +
      '<div class="message">Sign in to see your Trakt watchlist and history.</div>' +
      '<div class="btn-row" style="padding:0 48px"><button class="btn primary focusable" id="lib-signin">Sign in with Trakt</button></div>';
    content.querySelector('#lib-signin').addEventListener('click', () => push({ route: 'signin' }));
    return;
  }
  content.innerHTML =
    '<h1 class="screen-title">Library</h1>' +
    '<div class="btn-row" style="padding:0 48px 16px">' +
    '<button class="btn focusable" data-which="watchlist">Watchlist</button>' +
    '<button class="btn focusable" data-which="history">History</button></div>' +
    '<div id="lib"></div>';
  const libEl = content.querySelector('#lib');
  const load = async (which, btn) => {
    content.querySelectorAll('[data-which]').forEach((b) => b.classList.toggle('primary', b === btn));
    libEl.innerHTML = '<div class="spinner"></div>';
    const { items } = await api.library(which);
    libEl.innerHTML = '';
    if (!items.length) libEl.innerHTML = `<div class="message">${which === 'watchlist' ? 'Your watchlist is empty.' : 'Nothing watched yet.'}</div>`;
    else libEl.appendChild(gridEl(items));
    Nav.refresh('#content .focusable');
  };
  content.querySelectorAll('[data-which]').forEach((b) => b.addEventListener('click', () => load(b.dataset.which, b)));
  load('watchlist', content.querySelector('[data-which="watchlist"]'));
}

function renderSettings() {
  const acct = session.signedIn ? `Signed in as ${esc(session.username || 'your Trakt account')}.` : 'Not signed in.';
  const keyState = session.configured ? 'API key: configured ✓' : 'API key: NOT set — add it to .env and restart.';
  content.innerHTML =
    '<h1 class="screen-title">Settings</h1>' +
    `<div class="message" style="padding-bottom:12px">${acct}</div>` +
    '<div class="btn-row" style="padding:0 48px">' +
    (session.signedIn
      ? '<button class="btn focusable" id="signout">Sign out</button>'
      : '<button class="btn primary focusable" id="signin">Sign in with Trakt</button>') +
    '</div>' +
    `<div class="message" style="font-size:15px">${keyState}</div>` +
    '<div class="message" style="font-size:15px">Trakt TV for Raspberry Pi — data & scrobbling via the Trakt API.</div>';
  const signin = content.querySelector('#signin');
  if (signin) signin.addEventListener('click', () => push({ route: 'signin' }));
  const signout = content.querySelector('#signout');
  if (signout) signout.addEventListener('click', async () => {
    await api.logout();
    session = await api.config();
    updateAccount();
    toast('Signed out');
    render();
  });
}

async function renderSignIn() {
  if (!session.configured) {
    message('No Trakt API key configured. Set TRAKT_CLIENT_ID / TRAKT_CLIENT_SECRET in .env and restart the server.');
    return;
  }
  spinner();
  let d;
  try { d = await api.startDevice(); } catch { message('Could not reach Trakt. Check the connection and try again.'); return; }

  content.innerHTML =
    '<div class="signin"><div>' +
    '<h1>Connect your Trakt account</h1>' +
    `<div class="step">1. On your phone or computer, go to</div>` +
    `<div class="url">${esc(d.verification_url)}</div>` +
    `<div class="step">2. Enter this code</div>` +
    `<div class="code">${esc(d.user_code)}</div>` +
    '<div class="step" id="signin-status">Waiting for you to authorize…</div>' +
    '<div class="btn-row" style="margin-top:24px"><button class="btn focusable" id="signin-cancel">Back</button></div>' +
    '</div></div>';
  content.querySelector('#signin-cancel').addEventListener('click', () => back());

  const statusEl = content.querySelector('#signin-status');
  const interval = Math.max(2, d.interval || 5) * 1000;
  pollTimer = setInterval(async () => {
    const s = await api.poll();
    if (s.status === 'authorized') {
      clearTimers();
      session = await api.config();
      updateAccount();
      toast('Signed in' + (session.username ? ` as ${session.username}` : ''));
      back();
    } else if (['expired', 'denied', 'error', 'used', 'not_found'].includes(s.status)) {
      clearTimers();
      statusEl.textContent = 'Sign-in ' + s.status + '. Go back and try again.';
    }
  }, interval);
}

async function renderDetail(partial) {
  spinner();
  const data = await api.detail(partial.type, partial.id);
  const it = (data && data.item) || partial;
  const related = (data && data.related) || [];

  const meta = [
    it.rating ? '★ ' + it.rating.toFixed(1) : null,
    it.year,
    it.runtime ? it.runtime + 'm' : null,
    it.certification,
    it.network,
    it.genres && it.genres.length ? it.genres.slice(0, 3).join(', ') : null,
  ].filter(Boolean).join('   ·   ');

  const root = document.createElement('div');
  root.className = 'detail';
  root.innerHTML =
    (it.backdrop ? `<div class="backdrop" style="background-image:url('${esc(it.backdrop)}')"></div>` : '') +
    '<div class="body">' +
    `<h1>${esc(it.title)}</h1>` +
    `<div class="meta">${esc(meta)}</div>` +
    (it.overview ? `<div class="overview">${esc(it.overview)}</div>` : '') +
    '<div class="section-label">Ways to watch</div><div class="btn-row" id="watch"></div>' +
    '<div class="section-label">Track</div><div class="btn-row" id="track"></div>' +
    '</div>';
  content.innerHTML = '';
  content.appendChild(root);

  // Ways to watch — open streaming web players + web links.
  const watch = root.querySelector('#watch');
  const openUrl = async (url) => {
    const { opened } = await api.open(url);
    if (!opened) window.location.href = url; // fall back to navigating the kiosk browser
  };
  const { providers } = await api.providers(it.title);
  providers.forEach((p) => {
    const b = document.createElement('button');
    b.className = 'btn focusable';
    b.textContent = p.name;
    b.addEventListener('click', () => openUrl(p.url));
    watch.appendChild(b);
  });
  webLinks(it).forEach(({ label, url }) => {
    const b = document.createElement('button');
    b.className = 'btn focusable';
    b.textContent = label;
    b.addEventListener('click', () => openUrl(url));
    watch.appendChild(b);
  });

  // Track — watchlist / mark watched (OAuth).
  const track = root.querySelector('#track');
  const addTrack = (label, action) => {
    const b = document.createElement('button');
    b.className = 'btn focusable';
    b.textContent = label;
    b.addEventListener('click', async () => {
      if (!session.signedIn) { push({ route: 'signin' }); return; }
      const { ok } = await api.track(action, it.type, it.id);
      toast(ok ? (action === 'watchlist' ? 'Added to watchlist' : 'Marked as watched') : 'Action failed');
    });
    track.appendChild(b);
  };
  addTrack('＋ Watchlist', 'watchlist');
  addTrack('✓ Mark Watched', 'history');

  if (related.length) {
    const label = document.createElement('div');
    label.className = 'section-label';
    label.style.padding = '0 48px';
    label.textContent = 'More like this';
    root.appendChild(label);
    root.appendChild(rowEl('', related));
  }
}

function webLinks(it) {
  const seg = it.type === 'movie' ? 'movies' : 'shows';
  const links = [{ label: 'Trakt', url: `https://trakt.tv/${seg}/${it.slug || it.id}` }];
  if (it.imdb) links.push({ label: 'IMDb', url: `https://www.imdb.com/title/${it.imdb}/` });
  if (it.tmdb) links.push({ label: 'TMDB', url: `https://www.themoviedb.org/${it.type === 'movie' ? 'movie' : 'tv'}/${it.tmdb}` });
  return links;
}

// ---------- boot ----------

function bindRail() {
  document.querySelectorAll('.rail-item').forEach((b) => {
    b.addEventListener('click', () => go(b.dataset.route));
  });
}

window.addEventListener('nav-back', () => back());

async function boot() {
  session = await api.config();
  updateAccount();
  bindRail();
  render();
}

boot();
