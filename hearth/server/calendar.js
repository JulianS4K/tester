import nodeIcal from 'node-ical';
import { config } from './config.js';
import { store } from './store.js';

/**
 * Fetches the configured ICS feeds, expands recurring events into a date window,
 * and returns a flat, merged, color-tagged event list for the UI. Feeds are
 * cached in memory and refreshed on an interval.
 */

const cache = new Map(); // url -> { at: ms, events: parsed }

async function fetchFeed(url) {
  const cached = cache.get(url);
  const ttl = config.refreshMinutes * 60 * 1000;
  if (cached && Date.now() - cached.at < ttl) return cached.events;
  const events = await nodeIcal.async.fromURL(url);
  cache.set(url, { at: Date.now(), events });
  return events;
}

export function clearCache() { cache.clear(); }

function isAllDay(ev) {
  if (ev.datetype === 'date') return true;
  const s = ev.start;
  const e = ev.end;
  if (!(s instanceof Date) || !(e instanceof Date)) return false;
  const midnight = s.getHours() === 0 && s.getMinutes() === 0 && s.getSeconds() === 0;
  return midnight && (e - s) % 86400000 === 0 && e - s >= 86400000;
}

function ymd(date) {
  return date.toISOString().substring(0, 10);
}

function toOut(cal, title, start, end, allDay, location) {
  return {
    id: `${cal.id}-${start.getTime()}-${title}`,
    calendarId: cal.id,
    color: cal.color,
    calendarName: cal.name,
    title: title || '(busy)',
    allDay,
    start: start.toISOString(),
    end: end.toISOString(),
    startDate: ymd(start),
    endDate: ymd(new Date(end.getTime() - (allDay ? 1 : 0))), // all-day DTEND is exclusive
    location: location || null,
  };
}

// Expand one parsed feed into occurrences within [rangeStart, rangeEnd].
function expandFeed(cal, events, rangeStart, rangeEnd) {
  const out = [];
  for (const key of Object.keys(events)) {
    const ev = events[key];
    if (!ev || ev.type !== 'VEVENT') continue;
    const allDay = isAllDay(ev);
    const duration = (ev.end && ev.start) ? ev.end - ev.start : 3600000;

    if (ev.rrule) {
      const dates = ev.rrule.between(rangeStart, rangeEnd, true);
      // Include modified occurrences (recurrence overrides) that land in range.
      for (const r of Object.keys(ev.recurrences || {})) {
        const rd = new Date(r);
        if (rd >= rangeStart && rd <= rangeEnd && !dates.some((d) => ymd(d) === ymd(rd))) dates.push(rd);
      }
      for (const date of dates) {
        const lookup = ymd(date);
        if (ev.exdate && ev.exdate[lookup]) continue; // cancelled occurrence
        const override = ev.recurrences && ev.recurrences[lookup];
        const cur = override || ev;
        const start = override ? cur.start : date;
        const end = new Date(start.getTime() + (override ? (cur.end - cur.start) : duration));
        out.push(toOut(cal, cur.summary, start, end, override ? isAllDay(cur) : allDay, cur.location));
      }
    } else {
      if (ev.end >= rangeStart && ev.start <= rangeEnd) {
        out.push(toOut(cal, ev.summary, ev.start, ev.end, allDay, ev.location));
      }
    }
  }
  return out;
}

/** Merged, sorted events across all feeds within [start, end] (ISO strings). */
export async function getEvents(startIso, endIso) {
  const rangeStart = new Date(startIso);
  const rangeEnd = new Date(endIso);
  const calendars = store.calendars();
  const all = [];
  await Promise.all(
    calendars.map(async (cal) => {
      try {
        const events = await fetchFeed(cal.url);
        all.push(...expandFeed(cal, events, rangeStart, rangeEnd));
      } catch (e) {
        console.warn(`[calendar] feed failed (${cal.name}):`, e.message);
      }
    }),
  );
  all.sort((a, b) => new Date(a.start) - new Date(b.start));
  return all;
}
