import { chromium } from '@playwright/test';
import { fileURLToPath } from 'node:url';

// Development-only renderer. The checked-in PNG needs no browser at deploy time.
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' });
try {
  const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1, reducedMotion: 'reduce' });
  await page.goto(new URL('./social-card.html', import.meta.url).href);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map(img => img.decode()));
  });
  await page.screenshot({ path: fileURLToPath(new URL('../assets/social-card.png', import.meta.url)) });
  console.log('Rendered assets/social-card.png (1200 × 630).');
} finally {
  await browser.close();
}
