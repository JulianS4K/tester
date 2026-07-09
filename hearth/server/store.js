import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { config } from './config.js';

const DEFAULT_COLORS = ['#e5484d', '#2f80ed', '#2f9e44', '#f2994a', '#9b51e0', '#e6a817', '#12b5b0'];
const DAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];

/**
 * Tiny JSON-file store for everything local: calendar feeds, chores, the shared
 * list, and the weekly meal plan. No database — a family board doesn't need one.
 */
class Store {
  constructor() {
    this.data = { calendars: [], chores: [], list: [], meals: {} };
    this.load();
  }

  load() {
    try {
      this.data = JSON.parse(fs.readFileSync(config.storePath, 'utf8'));
    } catch {
      this.data = { calendars: [], chores: [], list: [], meals: {} };
    }
    // Normalize shape.
    this.data.calendars ||= [];
    this.data.chores ||= [];
    this.data.list ||= [];
    this.data.meals ||= {};
    for (const d of DAYS) this.data.meals[d] ??= '';

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

  // Public snapshot (calendar urls stripped — the browser never needs them).
  snapshot() {
    return {
      calendars: this.data.calendars.map(({ id, name, color }) => ({ id, name, color })),
      chores: this.data.chores,
      list: this.data.list,
      meals: this.data.meals,
    };
  }
}

export const store = new Store();
export { DAYS };
