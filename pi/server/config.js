import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');

// Minimal .env loader so the app has no runtime dependency beyond express.
function loadEnv() {
  const envPath = path.join(root, '.env');
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
    const m = line.match(/^\s*([\w.]+)\s*=\s*(.*)\s*$/);
    if (!m || line.trimStart().startsWith('#')) continue;
    const key = m[1];
    let val = m[2].trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = val;
  }
}
loadEnv();

export const config = {
  clientId: process.env.TRAKT_CLIENT_ID || '',
  clientSecret: process.env.TRAKT_CLIENT_SECRET || '',
  port: parseInt(process.env.PORT || '8730', 10),
  tokensPath: process.env.TRAKT_TOKENS_PATH || path.join(root, 'tokens.json'),
  watchOpenCmd: process.env.WATCH_OPEN_CMD || 'xdg-open',
  // GIF Stream — a lean-back channel of animated posts from Reddit.
  gifSubs: process.env.GIF_SUBS || 'gifs+perfectloops+oddlysatisfying+aww+reactiongifs+AnimalsBeingDerps+nextfuckinglevel+BetterEveryLoop',
  redditUserAgent: process.env.REDDIT_USER_AGENT || 'trakt-pi/0.1 (gif-stream; +https://github.com/JulianS4K/tester)',
  publicDir: path.join(root, 'public'),
  isConfigured() {
    return Boolean(this.clientId && this.clientSecret);
  },
};
