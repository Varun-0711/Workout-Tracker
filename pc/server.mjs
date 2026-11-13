import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, extname, sep } from 'node:path';

const root = dirname(fileURLToPath(import.meta.url));
const types = { '.html': 'text/html', '.css': 'text/css', '.mjs': 'text/javascript', '.js': 'text/javascript', '.png': 'image/png' };
const allowed = new Set(['index.html', 'styles.css', 'app.mjs', 'data.mjs', 'config.js', 'logo.png']);
createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    const name = pathname === '/' ? 'index.html' : pathname.slice(1);
    const path = resolve(root, name);
    if (!allowed.has(name) || !path.startsWith(root + sep)) { res.writeHead(404).end('Not found'); return; }
    let body;
    try { body = await readFile(path); }
    catch (error) {
      if (name === 'config.js' && error.code === 'ENOENT') body = 'window.WORKOUT_CONFIG = {};';
      else throw error;
    }
    res.writeHead(200, { 'Content-Type': types[extname(path)] || 'application/octet-stream', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'strict-origin-when-cross-origin' });
    res.end(body);
  } catch { res.writeHead(404).end('Not found'); }
}).listen(4173, '127.0.0.1', () => console.log('Workout Tracker PC: http://localhost:4173'));
