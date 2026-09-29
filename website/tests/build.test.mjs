import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';

test('build publishes only the public allowlist', () => {
  execFileSync(process.execPath, ['build.mjs'], { cwd: new URL('../', import.meta.url) });
  const dist = new URL('../dist/', import.meta.url);
  assert.deepEqual(readdirSync(dist).sort(), ['apple-touch-icon.png', 'assets', 'community.js', 'favicon.ico', 'favicon.png', 'index.html', 'motion.js', 'navigation.js', 'styles.css']);
  assert.deepEqual(readdirSync(new URL('assets/', dist)).sort(), ['atlas.svg', 'bankr.svg', 'base.ico', 'compass.svg', 'dexscreener.png', 'social-card.png', 'social-icon.png']);
  assert.match(readFileSync(new URL('index.html', dist), 'utf8'), /Knowledge,/);
});

test('published favicon fallbacks have valid image sizes and HTML declarations', () => {
  const dist = new URL('../dist/', import.meta.url);
  const html = readFileSync(new URL('index.html', dist), 'utf8');
  for (const [name, size] of [['favicon.png', 48], ['apple-touch-icon.png', 180]]) {
    const png = readFileSync(new URL(name, dist));
    assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    assert.equal(png.readUInt32BE(16), size);
    assert.equal(png.readUInt32BE(20), size);
    assert.ok(html.includes(`href="/${name}"`));
  }
  const ico = readFileSync(new URL('favicon.ico', dist));
  assert.equal(ico.readUInt16LE(0), 0);
  assert.equal(ico.readUInt16LE(2), 1);
  assert.equal(ico.readUInt16LE(4), 3);
  for (const [i, size] of [16, 32, 48].entries()) {
    const offset = 6 + i * 16;
    assert.equal(ico[offset], size);
    assert.equal(ico[offset + 1], size);
    const payload = ico.readUInt32LE(offset + 12);
    assert.equal(ico.subarray(payload, payload + 8).toString('hex'), '89504e470d0a1a0a');
  }
  assert.match(html, /rel="icon"[^>]+href="\/favicon.ico"/);
  assert.match(html, /rel="apple-touch-icon"[^>]+href="\/apple-touch-icon.png"/);
});
