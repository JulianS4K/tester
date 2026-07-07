import fs from 'node:fs';
import path from 'node:path';
import { config } from './config.js';
import { traktFetch } from './trakt.js';

/**
 * Server-side OAuth for the Pi: runs the Trakt device flow, persists tokens to
 * disk, and refreshes them transparently. The browser only ever sees whether
 * we're signed in — never the tokens themselves.
 */

let tokens = null; // { access_token, refresh_token, expires_at, username }

function load() {
  try {
    tokens = JSON.parse(fs.readFileSync(config.tokensPath, 'utf8'));
  } catch {
    tokens = null;
  }
}

function save() {
  try {
    fs.mkdirSync(path.dirname(config.tokensPath), { recursive: true });
    if (tokens) fs.writeFileSync(config.tokensPath, JSON.stringify(tokens, null, 2));
    else if (fs.existsSync(config.tokensPath)) fs.rmSync(config.tokensPath);
  } catch (e) {
    console.error('[auth] token persist failed:', e.message);
  }
}

load();

export function isSignedIn() {
  return Boolean(tokens && tokens.access_token);
}

export function currentUsername() {
  return tokens?.username || null;
}

/** Returns a valid access token, refreshing first if it's near expiry. */
export async function accessToken() {
  if (!tokens?.access_token) return null;
  const nearExpiry = tokens.expires_at && Date.now() > tokens.expires_at - 60_000;
  if (nearExpiry && tokens.refresh_token) await refresh();
  return tokens?.access_token || null;
}

async function storeToken(data) {
  tokens = {
    access_token: data.access_token,
    refresh_token: data.refresh_token || tokens?.refresh_token || null,
    expires_at: Date.now() + (data.expires_in || 0) * 1000,
    username: tokens?.username || null,
  };
  save();
  await fetchUsername();
}

async function fetchUsername() {
  if (!tokens?.access_token) return;
  const r = await traktFetch('/users/settings', { token: tokens.access_token });
  if (r.ok) {
    tokens.username = r.data?.user?.username || r.data?.user?.name || null;
    save();
  }
}

async function refresh() {
  const r = await traktFetch('/oauth/token', {
    method: 'POST',
    body: {
      refresh_token: tokens.refresh_token,
      client_id: config.clientId,
      client_secret: config.clientSecret,
      redirect_uri: 'urn:ietf:wg:oauth:2.0:oob',
      grant_type: 'refresh_token',
    },
  });
  if (r.ok) await storeToken(r.data);
  else {
    tokens = null;
    save();
  }
}

// --- Device flow ---

let deviceSession = null; // { device_code, interval, expires_at }
let lastPollAt = 0;

export async function startDevice() {
  const r = await traktFetch('/oauth/device/code', {
    method: 'POST',
    body: { client_id: config.clientId },
  });
  if (!r.ok) throw new Error(`device code request failed (${r.status})`);
  const d = r.data;
  deviceSession = {
    device_code: d.device_code,
    interval: d.interval || 5,
    expires_at: Date.now() + (d.expires_in || 600) * 1000,
  };
  lastPollAt = 0;
  return {
    user_code: d.user_code,
    verification_url: d.verification_url,
    expires_in: d.expires_in,
    interval: d.interval,
  };
}

/**
 * Polls Trakt for the token. The frontend calls this on a timer; we throttle to
 * the API's interval and translate status codes into a simple status string.
 */
export async function pollDevice() {
  if (!deviceSession) return { status: 'idle' };
  if (Date.now() > deviceSession.expires_at) {
    deviceSession = null;
    return { status: 'expired' };
  }
  const now = Date.now();
  if (now - lastPollAt < deviceSession.interval * 1000) {
    return { status: 'pending' };
  }
  lastPollAt = now;

  const r = await traktFetch('/oauth/device/token', {
    method: 'POST',
    body: {
      code: deviceSession.device_code,
      client_id: config.clientId,
      client_secret: config.clientSecret,
    },
  });

  if (r.status === 200) {
    await storeToken(r.data);
    deviceSession = null;
    return { status: 'authorized', username: currentUsername() };
  }
  if (r.status === 400) return { status: 'pending' };
  if (r.status === 429) {
    deviceSession.interval += 1;
    return { status: 'pending' };
  }
  deviceSession = null;
  const map = { 404: 'not_found', 409: 'used', 410: 'expired', 418: 'denied' };
  return { status: map[r.status] || 'error' };
}

export function logout() {
  tokens = null;
  deviceSession = null;
  save();
}
