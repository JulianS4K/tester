// Thin wrapper over the server's /api endpoints. The browser never talks to
// Trakt directly — the Pi server injects the key + token.
const json = (r) => r.json();

export const api = {
  config: () => fetch('/api/config').then(json),
  startDevice: () => fetch('/api/auth/device', { method: 'POST' }).then(json),
  poll: () => fetch('/api/auth/poll').then(json),
  logout: () => fetch('/api/auth/logout', { method: 'POST' }).then(json),

  home: () => fetch('/api/home').then(json),
  gifs: ({ subs = '', sort = 'hot', t = 'week', after = '', nsfw = false } = {}) => {
    const q = new URLSearchParams({ sort, t });
    if (subs) q.set('subs', subs);
    if (after) q.set('after', after);
    if (nsfw) q.set('nsfw', '1');
    return fetch('/api/gifs?' + q).then(json);
  },
  search: (q) => fetch('/api/search?q=' + encodeURIComponent(q)).then(json),
  detail: (type, id) => fetch(`/api/detail?type=${type}&id=${encodeURIComponent(id)}`).then(json),
  library: (which) => fetch('/api/library?which=' + which).then((r) => (r.ok ? r.json() : { items: [] })),

  track: (action, type, id) =>
    fetch('/api/track', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, type, id }),
    }).then((r) => (r.ok ? r.json() : { ok: false })),

  providers: (title) => fetch('/api/watch/providers?title=' + encodeURIComponent(title)).then(json),
  open: (url) =>
    fetch('/api/watch/open', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url }),
    }).then((r) => (r.ok ? r.json() : { opened: false })),
};
