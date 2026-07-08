import { config } from './config.js';

const BASE = 'https://api.trakt.tv';

/**
 * Low-level Trakt API call. Injects the required headers (and Bearer token when
 * provided) so the browser never sees the client secret or access token.
 * Returns { status, ok, data } — never throws on non-2xx.
 */
export async function traktFetch(pathname, { method = 'GET', token = null, body = null, query = null } = {}) {
  const url = new URL(BASE + pathname);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== null && v !== undefined) url.searchParams.set(k, v);
    }
  }

  const headers = {
    'Content-Type': 'application/json',
    'User-Agent': 'TraktPi/0.1',
    'trakt-api-version': '2',
    'trakt-api-key': config.clientId,
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  return { status: res.status, ok: res.ok, data };
}

// --- Mapping raw Trakt objects to the flat shape the frontend consumes ---

function pickImage(arr) {
  if (!Array.isArray(arr) || arr.length === 0) return null;
  const u = arr[0];
  return u.startsWith('http') ? u : `https://${u}`;
}

export function toItem(x, type) {
  if (!x) return null;
  const ids = x.ids || {};
  const images = x.images || {};
  return {
    type,
    id: ids.trakt,
    slug: ids.slug || null,
    imdb: ids.imdb || null,
    tmdb: ids.tmdb || null,
    title: x.title || '',
    year: x.year || null,
    overview: x.overview || null,
    rating: typeof x.rating === 'number' ? x.rating : null,
    runtime: x.runtime || null,
    network: x.network || null,
    certification: x.certification || null,
    status: x.status || null,
    genres: Array.isArray(x.genres) ? x.genres : [],
    poster: pickImage(images.poster),
    backdrop: pickImage(images.fanart),
    logo: pickImage(images.logo),
  };
}
