import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';

test('build publishes only the public allowlist', () => {
  execFileSync(process.execPath, ['build.mjs'], { cwd: new URL('../', import.meta.url) });
  const dist = new URL('../dist/', import.meta.url);
  assert.deepEqual(readdirSync(dist).sort(), ['apple-touch-icon.png', 'assets', 'audio.js', 'boot.js', 'community.js', 'contribute.js', 'depack', 'favicon.ico', 'favicon.png', 'game.js', 'index.html', 'navigation.js', 'rewards.json', 'styles.css', 'vendor', 'world.js']);
  assert.deepEqual(readdirSync(new URL('vendor/', dist)).sort(), ['three.LICENSE', 'three.module.min.js']);
  assert.deepEqual(readdirSync(new URL('assets/', dist)).sort(), ['atlas.svg', 'bankr.svg', 'base.ico', 'compass.svg', 'depack-mark.gif', 'depack-mark.png', 'depack.gif', 'depack.png', 'dexscreener.png', 'pons.png', 'robinhood.png', 'social-card.png', 'social-icon.png']);
  assert.match(readFileSync(new URL('index.html', dist), 'utf8'), /Knowledge,/);
});

test('the dePACK page lists every collection in the app catalog, with no buy path', () => {
  const dist = new URL('../dist/', import.meta.url);
  assert.deepEqual(readdirSync(new URL('depack/', dist)).sort(), ['bounties.js', 'depack.css', 'depack.js', 'index.html', 'wanted', 'wanted.json']);
  // Bounties others start appear only once reviewed; the list ships empty.
  assert.deepEqual(JSON.parse(readFileSync(new URL('depack/wanted.json', dist), 'utf8')), { approved: [] });
  assert.deepEqual(readdirSync(new URL('depack/wanted/', dist)), ['index.html']);
  const html = readFileSync(new URL('depack/index.html', dist), 'utf8');
  const catalog = JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/catalog-2.json', import.meta.url), 'utf8'));
  for (const pack of catalog.packs) assert.ok(html.includes(pack.url.replace(/&/g, '&amp;')), `missing download for ${pack.id}`);
  // The page is written from the catalog; a catalog change must regenerate it.
  execFileSync(process.execPath, ['tools/depack-list.mjs', '--check'], { cwd: new URL('../', import.meta.url) });
  // The library page stays the library; bounties live on the Wanted page.
  assert.doesNotMatch(html, /bounties\.js/);
  const pages = { 'depack/index.html': 'depack', 'depack/wanted/index.html': 'bounties' };
  for (const [file, script] of Object.entries(pages)) {
    const page = readFileSync(new URL(file, dist), 'utf8');
    assert.match(page, /name="robots" content="noindex"/, file);
    assert.match(page, /href="\/depack\/wanted\/"/, file);
    assert.doesNotMatch(page, /bankr\.bot\/terminal|dexscreener|\bbuy\b|\bfree\b/i, file);
    // No ticker on the pages.
    assert.doesNotMatch(page, /dePACK|\$PACK/, file);
    // The site's security policy allows no inline styles or scripts.
    assert.doesNotMatch(page, new RegExp(`<script(?! src="/depack/${script}\\.js" defer></script>)|style=`), file);
    for (const ref of page.matchAll(/(?:src|srcset|href)="(\/[^"]+)"/g)) {
      const path = ref[1].replace(/^\//, '').replace(/\?.*$/, '').replace(/\/$/, '/index.html') || 'index.html';
      assert.ok(readdirSync(new URL(path.replace(/[^/]+$/, '') || './', dist)).includes(path.split('/').pop()), `missing ${path} in ${file}`);
    }
  }
  // Arrows typed as text read as decoration; icons and plain words do the job.
  for (const file of [...Object.keys(pages), 'depack/depack.css']) assert.doesNotMatch(readFileSync(new URL(file, dist), 'utf8'), /[←→↑↓]/u, file);
  // The website shows the logo without its square background.
  for (const file of Object.keys(pages)) assert.doesNotMatch(readFileSync(new URL(file, dist), 'utf8'), /assets\/depack\.(gif|png)/, file);
});

test('the Wanted page reads only Base and ENS and pays only the poidh contract, with the builder code', () => {
  const root = new URL('../', import.meta.url);
  const js = readFileSync(new URL('depack/bounties.js', root), 'utf8');
  const policy = JSON.parse(readFileSync(new URL('vercel.json', root), 'utf8')).headers[0].headers
    .find(header => header.key === 'Content-Security-Policy').value;
  assert.match(policy, /connect-src 'self' https:\/\/mainnet\.base\.org https:\/\/ethereum-rpc\.publicnode\.com;/);
  assert.deepEqual([...new Set(js.match(/https:\/\/[a-z.-]+/g))].sort(), ['https://basescan.org', 'https://ethereum-rpc.publicnode.com', 'https://mainnet.base.org', 'https://poidh.xyz', 'https://www.getfieldatlas.com']);
  // PoidhV3 on Base, the only address funds can go to; the rest are name lookups that are only read: ENS's reverse
  // registrar on Base, the Basenames registry, and the ENS Universal Resolver.
  assert.deepEqual([...new Set(js.match(/0x[0-9a-f]{40}/gi))].sort(), ['0x0000000000d8e504002cc26e3ec46d81971c1664', '0x5555fa783936c260f77385b4e153b9725fef1719', '0xb94704422c2a1e396835a571837aa5ae53285a95', '0xeeeeeeee14d718c2b47d9923deab1335e144eeee']);
  assert.match(js, /fetch\(ENS_RPC[^\n]*method: 'eth_call'/);
  assert.doesNotMatch(js, /to: ENS_RESOLVER[^\n]*eth_sendTransaction|eth_sendTransaction[^\n]*ENS_RESOLVER/);
  const suffix = Buffer.from(js.match(/BUILDER_SUFFIX = '([0-9a-f]+)'/)[1], 'hex');
  assert.equal(suffix.subarray(-16).toString('hex'), '8021'.repeat(8));
  assert.equal(suffix[suffix.length - 17], 0);
  const length = suffix[suffix.length - 18];
  assert.equal(suffix.subarray(0, length).toString('ascii'), 'bc_diey6oig');
  assert.equal(suffix.length, length + 18);
  assert.doesNotMatch(js, /innerHTML|insertAdjacentHTML|eval\(|new Function/);
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
