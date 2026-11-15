import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';

test('build publishes only the public allowlist', () => {
  execFileSync(process.execPath, ['build.mjs'], { cwd: new URL('../', import.meta.url) });
  const dist = new URL('../dist/', import.meta.url);
  assert.deepEqual(readdirSync(dist).sort(), ['apple-touch-icon.png', 'assets', 'audio.js', 'boot.js', 'community.js', 'contribute.js', 'depack', 'favicon.ico', 'favicon.png', 'game.js', 'index.html', 'navigation.js', 'rewards.json', 'styles.css', 'vendor', 'world.js']);
  assert.deepEqual(readdirSync(new URL('vendor/', dist)).sort(), ['three.LICENSE', 'three.module.min.js']);
  assert.deepEqual(readdirSync(new URL('assets/', dist)).sort(), ['atlas.svg', 'bankr.svg', 'base.ico', 'compass.svg', 'depack.gif', 'depack.png', 'dexscreener.png', 'pons.png', 'robinhood.png', 'social-card.png', 'social-icon.png']);
  assert.match(readFileSync(new URL('index.html', dist), 'utf8'), /Knowledge,/);
});

test('the dePACK page lists every collection in the app catalog, with no buy path', () => {
  const dist = new URL('../dist/', import.meta.url);
  assert.deepEqual(readdirSync(new URL('depack/', dist)).sort(), ['depack.css', 'depack.js', 'index.html']);
  const html = readFileSync(new URL('depack/index.html', dist), 'utf8');
  const catalog = JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/catalog-2.json', import.meta.url), 'utf8'));
  for (const pack of catalog.packs) assert.ok(html.includes(pack.url.replace(/&/g, '&amp;')), `missing download for ${pack.id}`);
  // The page is written from the catalog; a catalog change must regenerate it.
  execFileSync(process.execPath, ['tools/depack-list.mjs', '--check'], { cwd: new URL('../', import.meta.url) });
  assert.match(html, /name="robots" content="noindex"/);
  // Arrows typed as text read as decoration; icons and plain words do the job.
  for (const file of ['depack/index.html', 'depack/depack.css']) assert.doesNotMatch(readFileSync(new URL(file, dist), 'utf8'), /[←→↑↓]/u, file);
  assert.doesNotMatch(html, /bankr\.bot\/terminal|dexscreener|\bbuy\b/i);
  // The site's security policy allows no inline styles or scripts.
  assert.doesNotMatch(html, /<script(?! src="\/depack\/depack\.js" defer><\/script>)|style=/);
  for (const ref of html.matchAll(/(?:src|href)="(\/[^"]+)"/g)) {
    const path = ref[1].replace(/^\//, '').replace(/\?.*$/, '') || 'index.html';
    assert.ok(readdirSync(new URL(path.replace(/[^/]+$/, '') || './', dist)).includes(path.split('/').pop()), `missing ${path}`);
  }
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
