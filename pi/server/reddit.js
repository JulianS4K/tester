// Turns Reddit's GIF-heavy subreddits into a continuous, playable stream.
//
// Reddit has no auth requirement for read-only listing JSON, so the Pi just
// fetches `https://www.reddit.com/r/<subs>/<sort>.json` (subs combined into a
// multireddit with "+") and extracts the best *animated* media for each post.
// Almost every "gif" on Reddit is actually served as an MP4 — smaller, smoother,
// hardware-decoded — so we prefer MP4 and only fall back to a real .gif.

import { config } from './config.js';

const UA = config.redditUserAgent;

// Reddit escapes `&` (and friends) inside preview URLs as HTML entities.
const deent = (s) =>
  String(s || '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");

const isHttp = (u) => typeof u === 'string' && /^https?:\/\//.test(u);

/**
 * Pick the best playable animated media from a Reddit post, in priority order.
 * Returns { mp4, gif, poster, width, height } — at least one of mp4/gif — or null
 * if the post isn't an animation we can play.
 */
function pickMedia(d) {
  const preview = d.preview || {};
  const image = (preview.images && preview.images[0]) || {};
  const source = image.source || {};
  const poster = isHttp(deent(source.url)) ? deent(source.url) : (isHttp(d.thumbnail) ? d.thumbnail : '');
  const dims = { width: source.width || 0, height: source.height || 0 };

  // 1) v.redd.it native video that is flagged as a GIF (no audio, loops).
  const rv = (d.media && d.media.reddit_video) || (d.secure_media && d.secure_media.reddit_video);
  if (rv && rv.is_gif && isHttp(rv.fallback_url)) {
    return { mp4: deent(rv.fallback_url), gif: '', poster, ...dims };
  }

  // 2) MP4 variant of an animated preview (the common case for i.redd.it gifs
  //    and cross-posted redgifs/gfycat/tenor content).
  const mp4Variant = image.variants && image.variants.mp4 && image.variants.mp4.source;
  if (mp4Variant && isHttp(deent(mp4Variant.url))) {
    return { mp4: deent(mp4Variant.url), gif: '', poster, width: mp4Variant.width || dims.width, height: mp4Variant.height || dims.height };
  }

  // 3) Reddit's MP4 preview of an external animation (redgifs, gfycat, imgur…).
  const rvp = preview.reddit_video_preview;
  if (rvp && isHttp(rvp.fallback_url)) {
    return { mp4: deent(rvp.fallback_url), gif: '', poster, width: rvp.width || dims.width, height: rvp.height || dims.height };
  }

  // 4) A direct .gif / .gifv link (imgur .gifv is really an .mp4).
  const url = deent(d.url_overridden_by_dest || d.url || '');
  if (/\.gifv$/i.test(url)) return { mp4: url.replace(/\.gifv$/i, '.mp4'), gif: '', poster, ...dims };
  if (/\.gif$/i.test(url)) return { mp4: '', gif: url, poster, ...dims };

  return null;
}

/** Map a Reddit listing child to our stream item, or null if not animated. */
function toGif(child) {
  const d = child && child.data;
  if (!d || d.stickied) return null;
  const media = pickMedia(d);
  if (!media || (!media.mp4 && !media.gif)) return null;
  return {
    id: d.id,
    title: d.title || '',
    subreddit: d.subreddit || '',
    author: d.author || '',
    ups: d.ups || 0,
    permalink: d.permalink ? `https://www.reddit.com${d.permalink}` : '',
    nsfw: Boolean(d.over_18),
    ...media,
  };
}

/**
 * Fetch a page of animated posts from one or more subreddits.
 * @param {object} opts
 * @param {string} opts.subs   "+"-joined subreddits (e.g. "gifs+perfectloops").
 * @param {string} opts.sort   hot | top | new | rising.
 * @param {string} opts.t      time window for sort=top (day|week|month|year|all).
 * @param {string} opts.after  Reddit pagination cursor.
 * @param {boolean} opts.nsfw  include NSFW posts (default false).
 * @returns {Promise<{items: object[], after: string|null}>}
 */
export async function fetchGifs({ subs, sort = 'hot', t = 'week', after = '', nsfw = false } = {}) {
  const cleanSubs = String(subs || config.gifSubs)
    .split(/[+,\s]+/)
    .map((s) => s.replace(/[^\w]/g, ''))
    .filter(Boolean)
    .slice(0, 30)
    .join('+');
  const allowed = new Set(['hot', 'top', 'new', 'rising']);
  const s = allowed.has(sort) ? sort : 'hot';

  const qs = new URLSearchParams({ limit: '75', raw_json: '1' });
  if (after) qs.set('after', after);
  if (s === 'top') qs.set('t', t);

  const url = `https://www.reddit.com/r/${cleanSubs}/${s}.json?${qs}`;
  const r = await fetch(url, { headers: { 'User-Agent': UA, Accept: 'application/json' } });
  if (!r.ok) {
    const err = new Error(`reddit ${r.status}`);
    err.status = r.status;
    throw err;
  }
  const body = await r.json();
  const children = (body && body.data && body.data.children) || [];
  const items = children
    .map(toGif)
    .filter(Boolean)
    .filter((it) => nsfw || !it.nsfw);
  return { items, after: (body.data && body.data.after) || null };
}
