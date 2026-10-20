import { test } from 'node:test';
import assert from 'node:assert/strict';
import { links, problems } from '../tools/check-release-links.mjs';

const BASE = 'https://github.com/0x94t3z/fieldatlas';
// Published v1.4.1 with fieldatlas.apk; v1.1.9 a tag without a release; v1.5.0 a draft.
const head = async (url) => (url === `${BASE}/releases/download/v1.4.1/fieldatlas.apk` ? 302
  : url === `${BASE}/blob/v1.4.1/tools/README.md` ? 200 : 404);
const release = async (tag) => ({ 'v1.4.1': { draft: false }, 'v1.5.0': { draft: true } })[tag] ?? null;

test('finds downloads, release pages and files at a tag', () => {
  const found = links(`<a href="${BASE}/releases/download/v1.4.2/fieldatlas.apk">x</a> <a href="${BASE}/releases/tag/v1.4.2">y</a>\n` +
    `<a href="${BASE}/blob/v1.4.2/tools/README.md#packs">z</a> <a href="${BASE}/releases/latest">latest</a>`);
  assert.deepEqual(found.map((l) => [l.line, l.kind, l.tag]), [[1, 'download', 'v1.4.2'], [1, 'release', 'v1.4.2'], [2, 'file', 'v1.4.2']]);
});

test('a published release, its asset and a file at its tag pass', async () => {
  const page = `${BASE}/releases/download/v1.4.1/fieldatlas.apk ${BASE}/releases/tag/v1.4.1 ${BASE}/blob/v1.4.1/tools/README.md`;
  assert.deepEqual(await problems({ 'index.html': page }, head, release), []);
});

test('an unpublished release, a tag without a release, a draft and a missing asset fail', async () => {
  const page = [`${BASE}/releases/download/v1.4.2/fieldatlas.apk`, `${BASE}/releases/tag/v1.1.9`, `${BASE}/releases/tag/v1.5.0`,
    `${BASE}/releases/download/v1.4.1/missing.apk`].join('\n');
  assert.deepEqual(await problems({ 'index.html': page }, head, release), [
    `index.html:1 ${BASE}/releases/download/v1.4.2/fieldatlas.apk: no published asset (HTTP 404)`,
    `index.html:2 ${BASE}/releases/tag/v1.1.9: no published release`,
    `index.html:3 ${BASE}/releases/tag/v1.5.0: the release is a draft`,
    `index.html:4 ${BASE}/releases/download/v1.4.1/missing.apk: no published asset (HTTP 404)`,
  ]);
});

test('without GitHub the check fails rather than passes', async () => {
  const offline = async () => { throw new Error('no network'); };
  const found = await problems({ 'index.html': `${BASE}/releases/tag/v1.4.1` }, offline, offline);
  assert.equal(found.length, 1);
  assert.match(found[0], /could not be checked/);
});
