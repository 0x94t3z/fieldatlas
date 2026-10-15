import { chromium } from '@playwright/test';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Development-only renderer. The checked-in PNG needs no browser at deploy time.
// The card is the island's own title screen: the site is served locally, opened at 1200 × 630 with reduced motion
// (a still picture), and everything but the wordmark, the headline and the world is hidden before the shot.
const root = fileURLToPath(new URL('../', import.meta.url));
const port = 4174;
const server = spawn('python3', ['-m', 'http.server', String(port), '--bind', '127.0.0.1'], { cwd: root, stdio: 'ignore' });
const hide = `.sound-ask, #sound-toggle, #site-navigation, #menu-toggle, .hud-strip, .hud, .beacon-tag, .hero-actions,
  .hero-meta, .hero-facts, .prompt, .finish-banner { display: none !important; }`;
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome', args: ['--enable-gpu', '--ignore-gpu-blocklist'] });
try {
  const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1, reducedMotion: 'reduce' });
  for (let i = 0; ; i++) {
    try { await page.goto(`http://127.0.0.1:${port}/`); break; } catch (error) { if (i > 20) throw error; await page.waitForTimeout(250); }
  }
  await page.waitForFunction(() => document.documentElement.classList.contains('world-ready'));
  await page.addStyleTag({ content: hide });
  await page.evaluate(() => dispatchEvent(new Event('resize')));
  await page.waitForTimeout(1500);
  await page.screenshot({ path: fileURLToPath(new URL('../assets/social-card.png', import.meta.url)) });
  console.log('Rendered assets/social-card.png (1200 × 630).');
} finally {
  await browser.close();
  server.kill();
}
