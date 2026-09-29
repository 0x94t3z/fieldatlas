import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';

test('build publishes only the public allowlist', () => {
  execFileSync(process.execPath, ['build.mjs'], { cwd: new URL('../', import.meta.url) });
  const dist = new URL('../dist/', import.meta.url);
  assert.deepEqual(readdirSync(dist).sort(), ['assets', 'index.html', 'styles.css']);
  assert.deepEqual(readdirSync(new URL('assets/', dist)).sort(), ['atlas.svg', 'compass.svg']);
  assert.match(readFileSync(new URL('index.html', dist), 'utf8'), /Knowledge,/);
});
