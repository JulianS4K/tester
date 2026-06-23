// ── Retro-TV controller ──────────────────────────────────────────────
import { CHANNELS, LAUNCH_MODE } from "../config/channels.js";
import { provider } from "../providers/index.js";

const $ = (sel) => document.querySelector(sel);
const tv = $("#tv");
const screen = $("#screen");
const badge = $("#channel-badge");
const nowShowing = $("#now-showing");
const guide = $("#guide");

let index = 0;          // current channel index
let guideOpen = false;
let numberBuffer = "";  // for typing a channel number
let numberTimer = null;

// ── Static-noise renderer (channel-change snow) ──────────────────────
const staticCanvas = $("#static");
const sctx = staticCanvas.getContext("2d");
function sizeStatic() {
  staticCanvas.width = Math.floor(window.innerWidth / 3);
  staticCanvas.height = Math.floor(window.innerHeight / 3);
}
sizeStatic();
window.addEventListener("resize", sizeStatic);

let staticRAF = null;
function drawStatic() {
  const img = sctx.createImageData(staticCanvas.width, staticCanvas.height);
  const buf = img.data;
  for (let i = 0; i < buf.length; i += 4) {
    const v = (Math.random() * 255) | 0;
    buf[i] = buf[i + 1] = buf[i + 2] = v;
    buf[i + 3] = 255;
  }
  sctx.putImageData(img, 0, 0);
  staticRAF = requestAnimationFrame(drawStatic);
}
function showStatic(on) {
  staticCanvas.classList.toggle("on", on);
  if (on && !staticRAF) drawStatic();
  if (!on && staticRAF) { cancelAnimationFrame(staticRAF); staticRAF = null; }
}

// ── Render the tuned-in channel ──────────────────────────────────────
async function render() {
  const ch = CHANNELS[index];
  document.documentElement.style.setProperty("--accent", ch.color || "#38e0ff");

  screen.innerHTML = `
    <div class="channel-card">
      <div class="channel-logo">${ch.name}</div>
      <div class="channel-tagline">${ch.tagline || ""}</div>
    </div>`;

  badge.innerHTML = `${String(ch.number).padStart(2, "0")}<small>CH</small>`;

  // Ask the provider what's "on" right now (mock today, real later).
  let info = null;
  try { info = await provider.nowShowing(ch); } catch (_) {}
  if (info) {
    nowShowing.innerHTML = `
      <div class="ns-label">NOW SHOWING</div>
      <div class="ns-title">${info.title}</div>
      <div class="ns-sub">${info.subtitle || ""}</div>`;
    nowShowing.style.display = "block";
  } else {
    nowShowing.style.display = "none";
  }
  if (guideOpen) renderGuide();
}

// ── Channel changing (with snow transition) ──────────────────────────
let changing = false;
async function changeChannel(delta, absolute = null) {
  if (changing) return;
  changing = true;
  showStatic(true);
  screen.style.opacity = "0.15";

  await wait(140); // snow burst

  if (absolute !== null) {
    const found = CHANNELS.findIndex((c) => c.number === absolute);
    if (found >= 0) index = found;
  } else {
    index = (index + delta + CHANNELS.length) % CHANNELS.length;
  }
  await render();

  screen.style.opacity = "1";
  showStatic(false);
  changing = false;
}

// ── Launch the current channel's service ─────────────────────────────
function launchCurrent() {
  const ch = CHANNELS[index];
  if (!ch.launch) return;
  flashLaunch(ch);
  if (LAUNCH_MODE === "newtab") {
    window.open(ch.launch, "_blank", "noopener");
  } else {
    // Pi kiosk: replace the page with the service, fullscreen.
    window.location.href = ch.launch;
  }
}

function flashLaunch(ch) {
  showStatic(true);
  screen.style.opacity = "0.1";
  setTimeout(() => { showStatic(false); screen.style.opacity = "1"; }, 260);
}

// ── Guide overlay ────────────────────────────────────────────────────
async function renderGuide() {
  const rows = await Promise.all(
    CHANNELS.map(async (ch, i) => {
      let now = "";
      try { const info = await provider.nowShowing(ch); now = info ? info.title : ""; }
      catch (_) {}
      return `
        <div class="guide-row ${i === index ? "active" : ""}">
          <span class="g-num">${String(ch.number).padStart(2, "0")}</span>
          <span class="g-name">${ch.name}</span>
          <span class="g-now">${now}</span>
        </div>`;
    })
  );
  guide.innerHTML = `<h2>▚ TV GUIDE ▚</h2>${rows.join("")}`;
}
function toggleGuide(force) {
  guideOpen = force ?? !guideOpen;
  guide.classList.toggle("open", guideOpen);
  if (guideOpen) renderGuide();
}

// ── Number entry (type a channel, e.g. "4" then Enter or pause) ───────
function pushNumber(d) {
  numberBuffer += d;
  badge.innerHTML = `${numberBuffer}_<small>CH</small>`;
  clearTimeout(numberTimer);
  numberTimer = setTimeout(commitNumber, 900);
}
function commitNumber() {
  clearTimeout(numberTimer);
  if (!numberBuffer) return;
  const n = parseInt(numberBuffer, 10);
  numberBuffer = "";
  changeChannel(0, n);
}

// ── Input: keyboard = remote (Pi: map IR remote to these keys) ───────
window.addEventListener("keydown", (e) => {
  switch (e.key) {
    case "ArrowUp":
    case "ChannelUp":
    case "PageUp":
      changeChannel(+1); break;
    case "ArrowDown":
    case "ChannelDown":
    case "PageDown":
      changeChannel(-1); break;
    case "Enter":
    case " ":
      if (numberBuffer) commitNumber();
      else if (guideOpen) { toggleGuide(false); }
      else launchCurrent();
      break;
    case "g":
    case "Escape":
      toggleGuide(); break;
    default:
      if (/^[0-9]$/.test(e.key)) pushNumber(e.key);
  }
});

// Optional: click/tap a channel to launch (touchscreen kiosks)
screen.addEventListener("click", launchCurrent);

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

// ── Boot ─────────────────────────────────────────────────────────────
async function boot() {
  tv.classList.add("booting");
  showStatic(true);
  await wait(450);
  showStatic(false);
  await render();
  setTimeout(() => tv.classList.remove("booting"), 800);
}
boot();
