import { chromium } from '@playwright/test';
import { readFile, writeFile } from 'node:fs/promises';

// Render the existing vector logo; production only copies the checked-in icons.
const root = new URL('../', import.meta.url);
const svg = await readFile(new URL('assets/compass.svg', root), 'utf8');
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' });
try {
  const images = [];
  for (const size of [16, 32, 48, 180]) {
    const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 });
    await page.setContent(`<style>html,body{margin:0;background:transparent}svg{display:block;width:100vw;height:100vh}</style>${svg}`);
    const png = await page.screenshot({ omitBackground: true });
    await page.close();
    if (size === 180) await writeFile(new URL('apple-touch-icon.png', root), png);
    else images.push({ size, png });
    if (size === 48) await writeFile(new URL('favicon.png', root), png);
  }
  // ICO directory containing three PNG images at native browser-tab sizes.
  const directory = Buffer.alloc(6 + images.length * 16);
  directory.writeUInt16LE(1, 2);
  directory.writeUInt16LE(images.length, 4);
  let offset = directory.length;
  images.forEach(({ size, png }, i) => {
    const entry = 6 + i * 16;
    directory[entry] = size;
    directory[entry + 1] = size;
    directory.writeUInt16LE(1, entry + 4);
    directory.writeUInt16LE(32, entry + 6);
    directory.writeUInt32LE(png.length, entry + 8);
    directory.writeUInt32LE(offset, entry + 12);
    offset += png.length;
  });
  await writeFile(new URL('favicon.ico', root), Buffer.concat([directory, ...images.map(({ png }) => png)]));
  console.log('Rendered favicon.ico, favicon.png, and apple-touch-icon.png.');
} finally {
  await browser.close();
}
