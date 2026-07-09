import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');

// Minimal .env loader (no dependency).
function loadEnv() {
  const envPath = path.join(root, '.env');
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
    if (line.trimStart().startsWith('#')) continue;
    const m = line.match(/^\s*([\w.]+)\s*=\s*(.*)\s*$/);
    if (!m) continue;
    let val = m[2].trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (!(m[1] in process.env)) process.env[m[1]] = val;
  }
}
loadEnv();

function seedFeeds() {
  try {
    return JSON.parse(process.env.HEARTH_FEEDS || '[]');
  } catch {
    console.warn('[config] HEARTH_FEEDS is not valid JSON; ignoring.');
    return [];
  }
}

export const config = {
  port: parseInt(process.env.PORT || '8080', 10),
  publicDir: path.join(root, 'public'),
  storePath: process.env.HEARTH_STORE_PATH || path.join(root, 'data', 'store.json'),
  photosDir: process.env.HEARTH_PHOTOS_DIR || path.join(root, 'photos'),
  refreshMinutes: parseInt(process.env.REFRESH_MINUTES || '15', 10),
  weather: {
    lat: parseFloat(process.env.WEATHER_LAT || '40.7128'),
    lon: parseFloat(process.env.WEATHER_LON || '-74.0060'),
    unit: (process.env.WEATHER_UNIT || 'fahrenheit').toLowerCase(),
  },
  seedFeeds: seedFeeds(),
};
