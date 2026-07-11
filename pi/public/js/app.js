import { api } from './api.js';
import { Nav } from './nav.js';

const content = document.getElementById('content');
const ROOTS = ['home', 'search', 'stream', 'library', 'settings'];

let session = { configured: false, signedIn: false, username: null };
let stack = [{ route: 'home' }];
let currentRoot = 'home';
let pollTimer = null;
let searchTimer = null;
let teardown = null; // per-screen cleanup (listeners, timers, media) run on navigation

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
  if (teardown) { const fn = teardown; teardown = null; try { fn(); } catch { /* ignore */ } }
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
    else if (top.route === 'stream') await renderStream();
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

// ---------- GIF Stream ----------
// A lean-back channel: fetch animated posts from Reddit and play them one after
// another, full-bleed, auto-advancing. Arrow keys drive the channel; there are
// no focusable cards here, so we intercept keys in the capture phase and stop
// them before nav.js's spatial mover sees them.

const SORTS = ['hot', 'top', 'new', 'rising'];
const STREAM_MIN_MS = 4000;   // show each clip at least this long…
const STREAM_MAX_MS = 20000;  // …and at most this long (caps very long loops).
const STREAM_GIF_MS = 8000;   // hold plain (non-mp4) gifs this long — no duration to read.
const STREAM_REFILL = 6;      // refetch when this few items remain ahead.

async function renderStream() {
  content.innerHTML =
    '<div class="stream" id="stream">' +
    '  <div class="stream-stage">' +
    '    <video class="stream-video" id="stream-video" playsinline muted preload="auto"></video>' +
    '    <img class="stream-img" id="stream-img" alt="" hidden />' +
    '    <video class="stream-preload" id="stream-preload" muted preload="auto" hidden></video>' +
    '  </div>' +
    '  <div class="stream-scrim"></div>' +
    '  <div class="stream-caption" id="stream-caption"></div>' +
    '  <div class="stream-badges"><span class="stream-sort" id="stream-sort"></span><span class="stream-count" id="stream-count"></span></div>' +
    '  <div class="stream-center" id="stream-center"></div>' +
    '  <div class="stream-help" id="stream-help">' +
    '    ◀ ▶ prev / next · <b>OK</b> pause · ▲ info · ▼ sort · <b>M</b> sound · <b>Back</b> home' +
    '  </div>' +
    '</div>';

  const video = document.getElementById('stream-video');
  const img = document.getElementById('stream-img');
  const preload = document.getElementById('stream-preload');
  const captionEl = document.getElementById('stream-caption');
  const sortEl = document.getElementById('stream-sort');
  const countEl = document.getElementById('stream-count');
  const centerEl = document.getElementById('stream-center');
  const helpEl = document.getElementById('stream-help');

  const S = {
    queue: [], seen: new Set(), idx: 0, after: '', sort: 'hot',
    paused: false, muted: true, fetching: false, done: false,
    advanceTimer: null, helpTimer: null, alive: true,
  };

  const clearAdvance = () => { if (S.advanceTimer) { clearTimeout(S.advanceTimer); S.advanceTimer = null; } };
  const flashCenter = (text) => { centerEl.textContent = text; centerEl.classList.add('show'); };
  const hideCenter = () => centerEl.classList.remove('show');
  const nudgeHelp = () => {
    helpEl.classList.add('show');
    clearTimeout(S.helpTimer);
    S.helpTimer = setTimeout(() => helpEl.classList.remove('show'), 3500);
  };

  async function fetchMore() {
    if (S.fetching || S.done) return;
    S.fetching = true;
    try {
      const r = await api.gifs({ sort: S.sort, after: S.after });
      const fresh = (r.items || []).filter((it) => !S.seen.has(it.id));
      fresh.forEach((it) => S.seen.add(it.id));
      S.queue.push(...fresh);
      S.after = r.after || '';
      if (!S.after) S.done = true; // reached the end of the listing — we'll wrap around
    } catch (e) {
      if (!S.queue.length) throw e; // surface only when we have nothing to show
    } finally {
      S.fetching = false;
    }
  }

  function updateBadges() {
    sortEl.textContent = S.sort.toUpperCase();
    const cur = S.queue[S.idx];
    countEl.textContent = cur ? `r/${cur.subreddit}` : '';
  }

  function showCaption(item) {
    const ups = item.ups ? `▲ ${item.ups.toLocaleString()}` : '';
    captionEl.innerHTML =
      `<div class="stream-title">${esc(item.title)}</div>` +
      `<div class="stream-sub">r/${esc(item.subreddit)}${item.author ? ' · u/' + esc(item.author) : ''}${ups ? ' · ' + ups : ''}</div>`;
    captionEl.classList.add('show');
  }

  function preloadNext() {
    const next = S.queue[S.idx + 1];
    if (next && next.mp4) { preload.src = next.mp4; try { preload.load(); } catch { /* ignore */ } }
  }

  // Advance after enough whole loops to cover STREAM_MIN_MS, capped at STREAM_MAX_MS.
  function scheduleAdvanceForVideo() {
    clearAdvance();
    if (S.paused) return;
    const durMs = (video.duration && isFinite(video.duration) ? video.duration : 6) * 1000;
    const loops = Math.max(1, Math.ceil(STREAM_MIN_MS / durMs));
    const hold = Math.min(STREAM_MAX_MS, loops * durMs);
    S.advanceTimer = setTimeout(() => advance(1), hold);
  }

  async function play() {
    const item = S.queue[S.idx];
    if (!item) { flashCenter('Loading…'); await fetchMore().catch(() => {}); if (S.queue[S.idx]) return play(); return; }
    hideCenter();
    updateBadges();
    showCaption(item);
    clearAdvance();

    if (item.mp4) {
      img.hidden = true; video.hidden = false;
      video.loop = true; video.muted = S.muted;
      video.src = item.mp4;
      video.play().catch(() => {});
    } else {
      // Plain gif — no readable duration, so hold for a fixed spell.
      video.hidden = true; video.pause(); video.removeAttribute('src'); video.load();
      img.hidden = false; img.src = item.gif;
      if (!S.paused) S.advanceTimer = setTimeout(() => advance(1), STREAM_GIF_MS);
    }
    preloadNext();

    // Keep the buffer full.
    if (S.queue.length - S.idx <= STREAM_REFILL) fetchMore().catch(() => {});
  }

  function advance(dir) {
    clearAdvance();
    let next = S.idx + dir;
    if (next >= S.queue.length) {
      if (S.done && S.queue.length) next = 0; // wrap the channel rather than dead-ending
      else { S.idx = S.queue.length; flashCenter('Loading…'); fetchMore().then(play).catch(() => flashCenter('Nothing to stream')); return; }
    }
    if (next < 0) next = S.queue.length - 1;
    S.idx = next;
    play();
  }

  function togglePause() {
    S.paused = !S.paused;
    if (S.paused) {
      clearAdvance();
      video.pause();
      flashCenter('❚❚');
    } else {
      hideCenter();
      const item = S.queue[S.idx];
      if (item && item.mp4) { video.play().catch(() => {}); scheduleAdvanceForVideo(); }
      else if (item) { S.advanceTimer = setTimeout(() => advance(1), STREAM_GIF_MS); }
    }
  }

  async function changeSort() {
    S.sort = SORTS[(SORTS.indexOf(S.sort) + 1) % SORTS.length];
    S.queue = []; S.seen = new Set(); S.idx = 0; S.after = ''; S.done = false;
    updateBadges();
    flashCenter(S.sort.toUpperCase());
    clearAdvance();
    try { await fetchMore(); hideCenter(); play(); } catch { flashCenter('Nothing to stream'); }
  }

  function toggleInfo() {
    captionEl.classList.toggle('show');
    nudgeHelp();
  }

  function toggleMute() {
    S.muted = !S.muted;
    video.muted = S.muted;
    if (!S.muted) video.play().catch(() => {}); // some browsers pause on unmute-during-autoplay
    flashCenter(S.muted ? '🔇' : '🔊');
    setTimeout(hideCenter, 900);
  }

  // A clip whose media 404s/403s: skip it so the channel never stalls.
  video.addEventListener('error', () => { if (S.alive && S.queue[S.idx] && S.queue[S.idx].mp4) advance(1); });
  img.addEventListener('error', () => { if (S.alive && S.queue[S.idx] && !S.queue[S.idx].mp4) advance(1); });
  // Once the video's duration is known, schedule the loop-aware advance.
  video.addEventListener('loadedmetadata', () => { if (!S.paused && !video.hidden) scheduleAdvanceForVideo(); });

  function onKey(e) {
    let handled = true;
    switch (e.key) {
      case 'ArrowRight': advance(1); break;
      case 'ArrowLeft': advance(-1); break;
      case 'ArrowUp': toggleInfo(); break;
      case 'ArrowDown': changeSort(); break;
      case 'Enter': case ' ': case 'p': case 'P': togglePause(); break;
      case 'm': case 'M': toggleMute(); break;
      case 'Backspace': case 'Escape': go('home'); break;
      default: handled = false;
    }
    if (handled) { e.preventDefault(); e.stopPropagation(); nudgeHelp(); }
  }
  // Capture phase so we win over nav.js's window-level (bubble) handler.
  window.addEventListener('keydown', onKey, true);

  teardown = () => {
    S.alive = false;
    clearAdvance();
    clearTimeout(S.helpTimer);
    window.removeEventListener('keydown', onKey, true);
    try { video.pause(); video.removeAttribute('src'); video.load(); } catch { /* ignore */ }
    try { preload.removeAttribute('src'); } catch { /* ignore */ }
  };

  nudgeHelp();
  flashCenter('Loading GIF stream…');
  try {
    await fetchMore();
  } catch (e) {
    flashCenter('Could not reach Reddit');
    return;
  }
  if (!S.queue.length) { flashCenter('No animated posts found'); return; }
  hideCenter();
  play();
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
