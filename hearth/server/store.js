import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { config } from './config.js';

const DEFAULT_COLORS = ['#e5484d', '#2f80ed', '#2f9e44', '#f2994a', '#9b51e0', '#e6a817', '#12b5b0'];
const DAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];

const pad2 = (n) => String(n).padStart(2, '0');
export function todayKey(d = new Date()) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}
function addDaysKey(dateKey, delta) {
  const [y, m, d] = dateKey.split('-').map(Number);
  const dt = new Date(y, m - 1, d + delta);
  return todayKey(dt);
}

/**
 * Tiny JSON-file store for everything local: calendar feeds, chores, the shared
 * list, and the weekly meal plan. No database — a family board doesn't need one.
 */
class Store {
  constructor() {
    this.data = this.empty();
    this.load();
  }

  empty() {
    return {
      calendars: [], chores: [], list: [], meals: {},
      health: { people: [], habits: [], habitLog: {}, metrics: {} },
      news: [],
      notes: '',
    };
  }

  load() {
    try {
      this.data = JSON.parse(fs.readFileSync(config.storePath, 'utf8'));
    } catch {
      this.data = this.empty();
    }
    // Normalize shape.
    this.data.calendars ||= [];
    this.data.chores ||= [];
    this.data.list ||= [];
    this.data.meals ||= {};
    for (const d of DAYS) this.data.meals[d] ??= '';
    this.data.health ||= { people: [], habits: [], habitLog: {}, metrics: {} };
    this.data.health.people ||= [];
    this.data.health.habits ||= [];
    this.data.health.habitLog ||= {};
    this.data.health.metrics ||= {};
    this.data.news ||= [];
    this.data.notes ??= '';

    // Seed feeds from config on first run.
    if (this.data.calendars.length === 0 && config.seedFeeds.length) {
      config.seedFeeds.forEach((f, i) => this.addCalendar(f.name, f.color || DEFAULT_COLORS[i % DEFAULT_COLORS.length], f.url, false));
      this.save();
    }
  }

  save() {
    try {
      fs.mkdirSync(path.dirname(config.storePath), { recursive: true });
      fs.writeFileSync(config.storePath, JSON.stringify(this.data, null, 2));
    } catch (e) {
      console.error('[store] save failed:', e.message);
    }
  }

  // ---- Calendars ----
  calendars() { return this.data.calendars; }

  addCalendar(name, color, url, persist = true) {
    if (!url) return null;
    const cal = {
      id: randomUUID(),
      name: name || 'Calendar',
      color: color || DEFAULT_COLORS[this.data.calendars.length % DEFAULT_COLORS.length],
      url,
    };
    this.data.calendars.push(cal);
    if (persist) this.save();
    return cal;
  }

  removeCalendar(id) {
    this.data.calendars = this.data.calendars.filter((c) => c.id !== id);
    this.save();
  }

  // ---- Chores ----
  addChore(person, title) {
    const chore = { id: randomUUID(), person: person || '', title: title || '', done: false };
    this.data.chores.push(chore);
    this.save();
    return chore;
  }

  toggleChore(id) {
    const c = this.data.chores.find((x) => x.id === id);
    if (c) { c.done = !c.done; this.save(); }
    return c;
  }

  removeChore(id) {
    this.data.chores = this.data.chores.filter((c) => c.id !== id);
    this.save();
  }

  resetChores() {
    this.data.chores.forEach((c) => { c.done = false; });
    this.save();
  }

  // ---- Shared list ----
  addListItem(text) {
    const item = { id: randomUUID(), text: text || '', done: false };
    this.data.list.push(item);
    this.save();
    return item;
  }

  toggleListItem(id) {
    const i = this.data.list.find((x) => x.id === id);
    if (i) { i.done = !i.done; this.save(); }
    return i;
  }

  removeListItem(id) {
    this.data.list = this.data.list.filter((i) => i.id !== id);
    this.save();
  }

  clearDone() {
    this.data.list = this.data.list.filter((i) => !i.done);
    this.save();
  }

  // ---- Meals ----
  setMeal(day, text) {
    if (DAYS.includes(day)) { this.data.meals[day] = text || ''; this.save(); }
    return this.data.meals;
  }

  // ---- Health: people ----
  addPerson(name, emoji) {
    const p = { id: randomUUID(), name: name || 'Me', emoji: emoji || '🙂' };
    this.data.health.people.push(p);
    this.save();
    return p;
  }
  removePerson(id) {
    const h = this.data.health;
    h.people = h.people.filter((p) => p.id !== id);
    h.habits = h.habits.filter((x) => x.personId !== id);
    this.save();
  }

  // ---- Health: habits ----
  addHabit(personId, name, emoji) {
    const habit = { id: randomUUID(), personId, name: name || 'Habit', emoji: emoji || '✅' };
    this.data.health.habits.push(habit);
    this.save();
    return habit;
  }
  removeHabit(id) {
    this.data.health.habits = this.data.health.habits.filter((x) => x.id !== id);
    this.save();
  }
  toggleHabit(id, dateKey = todayKey()) {
    const log = this.data.health.habitLog;
    const set = new Set(log[dateKey] || []);
    if (set.has(id)) set.delete(id); else set.add(id);
    log[dateKey] = [...set];
    this.save();
  }
  habitStreak(id, dateKey = todayKey()) {
    const log = this.data.health.habitLog;
    // If not done today yet, streak still counts through yesterday.
    let day = (log[dateKey] || []).includes(id) ? dateKey : addDaysKey(dateKey, -1);
    let count = 0;
    while ((log[day] || []).includes(id)) { count++; day = addDaysKey(day, -1); }
    return count;
  }

  // ---- Health: metrics (water/mood/weight per person/day) ----
  setMetric(personId, key, value, dateKey = todayKey()) {
    const m = this.data.health.metrics;
    m[dateKey] ||= {};
    m[dateKey][personId] ||= {};
    m[dateKey][personId][key] = value;
    this.save();
    return m[dateKey][personId];
  }
  incWater(personId, delta = 1, dateKey = todayKey()) {
    const m = this.data.health.metrics;
    m[dateKey] ||= {};
    m[dateKey][personId] ||= {};
    m[dateKey][personId].water = Math.max(0, (m[dateKey][personId].water || 0) + delta);
    this.save();
    return m[dateKey][personId];
  }

  // Last `days` of a person's mood (oldest → newest), for behavior tracking.
  moodHistory(personId, days = 14) {
    const out = [];
    let day = todayKey();
    for (let i = 0; i < days; i++) {
      out.push({ date: day, mood: this.data.health.metrics[day]?.[personId]?.mood || null });
      day = addDaysKey(day, -1);
    }
    return out.reverse();
  }

  // ---- News feeds ----
  newsFeeds() { return this.data.news; }
  addNewsFeed(name, url) {
    if (!url) return null;
    const f = { id: randomUUID(), name: name || 'News', url };
    this.data.news.push(f);
    this.save();
    return f;
  }
  removeNewsFeed(id) {
    this.data.news = this.data.news.filter((f) => f.id !== id);
    this.save();
  }

  // ---- Notes ----
  setNotes(text) { this.data.notes = text || ''; this.save(); return this.data.notes; }

  // Public snapshot (calendar/news urls stripped — the browser never needs them).
  snapshot() {
    const today = todayKey();
    const h = this.data.health;
    return {
      calendars: this.data.calendars.map(({ id, name, color }) => ({ id, name, color })),
      chores: this.data.chores,
      list: this.data.list,
      meals: this.data.meals,
      health: {
        people: h.people,
        habits: h.habits.map((hb) => ({ ...hb, doneToday: (h.habitLog[today] || []).includes(hb.id), streak: this.habitStreak(hb.id) })),
        today: h.metrics[today] || {},
        moodHistory: Object.fromEntries(h.people.map((p) => [p.id, this.moodHistory(p.id, 14)])),
      },
      news: this.data.news.map(({ id, name }) => ({ id, name })),
      notes: this.data.notes,
    };
  }
}

export const store = new Store();
export { DAYS };
