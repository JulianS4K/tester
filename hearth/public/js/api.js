// Thin wrapper over Hearth's /api endpoints.
const j = (r) => r.json();
const body = (b) => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(b) });

export const api = {
  state: () => fetch('/api/state').then(j),
  events: (start, end) => fetch(`/api/events?start=${start.toISOString()}&end=${end.toISOString()}`).then(j),
  weather: () => fetch('/api/weather').then(j),
  photos: () => fetch('/api/photos').then(j),

  addCalendar: (name, color, url) => fetch('/api/calendars', body({ name, color, url })).then(j),
  removeCalendar: (id) => fetch(`/api/calendars/${id}`, { method: 'DELETE' }).then(j),

  addChore: (person, title) => fetch('/api/chores', body({ person, title })).then(j),
  toggleChore: (id) => fetch(`/api/chores/${id}/toggle`, { method: 'POST' }).then(j),
  removeChore: (id) => fetch(`/api/chores/${id}`, { method: 'DELETE' }).then(j),
  resetChores: () => fetch('/api/chores/reset', { method: 'POST' }).then(j),

  addListItem: (text) => fetch('/api/list', body({ text })).then(j),
  toggleListItem: (id) => fetch(`/api/list/${id}/toggle`, { method: 'POST' }).then(j),
  removeListItem: (id) => fetch(`/api/list/${id}`, { method: 'DELETE' }).then(j),
  clearDone: () => fetch('/api/list/clear-done', { method: 'POST' }).then(j),

  setMeal: (day, text) => fetch(`/api/meals/${day}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) }).then(j),

  // Health
  addPerson: (name, emoji) => fetch('/api/health/people', body({ name, emoji })).then(j),
  removePerson: (id) => fetch(`/api/health/people/${id}`, { method: 'DELETE' }).then(j),
  addHabit: (personId, name, emoji) => fetch('/api/health/habits', body({ personId, name, emoji })).then(j),
  removeHabit: (id) => fetch(`/api/health/habits/${id}`, { method: 'DELETE' }).then(j),
  toggleHabit: (id) => fetch(`/api/health/habits/${id}/toggle`, { method: 'POST' }).then(j),
  water: (personId, delta) => fetch('/api/health/water', body({ personId, delta })).then(j),
  metric: (personId, key, value) => fetch('/api/health/metric', body({ personId, key, value })).then(j),

  // News
  news: () => fetch('/api/news').then(j),
  addNews: (name, url) => fetch('/api/news', body({ name, url })).then(j),
  removeNews: (id) => fetch(`/api/news/${id}`, { method: 'DELETE' }).then(j),

  // Notes
  setNotes: (notes) => fetch('/api/notes', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ notes }) }).then(j),
};
