import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const release = 'https://github.com/0x94t3z/fieldatlas/releases';
const apk = `${release}/download/v1.4.1/fieldatlas.apk`;
const token = '0xb462a5039a540883508ee37b5cf3999647557ba3';
const home = process.env.SITE_URL || 'http://127.0.0.1:4173';
const csp = JSON.parse(readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'))
  .headers[0].headers.find(header => header.key === 'Content-Security-Policy').value;

test.beforeEach(async ({ page }) => {
  if (process.env.SITE_URL) return;
  // Exercise the same content policy locally as on the public deployment.
  await page.route('**/*', async route => {
    if (route.request().resourceType() !== 'document') return route.continue();
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'content-security-policy': csp } });
  });
});

// The plain page, as visitors without WebGL get it: boot.js finds no context and keeps every section in the page.
const withoutWebGL = page => page.addInitScript(() => {
  const get = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (type, ...rest) { return /webgl/.test(type) ? null : get.call(this, type, ...rest); };
});
const stage = page => page.evaluate(() => import('./game.js').then(m => m.journey.stage));
const openSection = page => page.locator('.stage.is-open');

// ---------- content that must hold in every view ----------

test('clear introduction and consistent release download path', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('h1')).toHaveCount(1);
  await expect(page.locator('h1')).toHaveText('Knowledge, wherever you go.');
  const downloads = page.locator('a.button', { hasText: 'Download for Android' });
  await expect(downloads).toHaveCount(2);
  for (const link of await downloads.all()) await expect(link).toHaveAttribute('href', apk);
  await expect(page.locator('a', { hasText: 'Verify checksum' })).toHaveAttribute('href', `${apk}.sha256`);
  await expect(page.locator('.download-links a', { hasText: 'Release notes' })).toHaveAttribute('href', `${release}/tag/v1.4.1`);
  await expect(page.locator('#download')).toContainText('Release · v1.4.1');
  await expect(page.locator('#download')).toContainText('Android 13+');
});

test('local assets load and the page makes no third-party requests', async ({ page }) => {
  const external = [];
  page.on('request', r => { if (new URL(r.url()).origin !== new URL(home).origin) external.push(r.url()); });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  await page.goto('/');
  await expect(page.locator('html')).toHaveClass(/world-ready|no-webgl/);
  expect(await page.locator('img:visible').evaluateAll(images => images.every(i => i.complete && i.naturalWidth > 0))).toBe(true);
  expect(external).toEqual([]);
  expect(errors).toEqual([]);
  await expect(page).toHaveTitle(/Field Atlas/);
});

test('social previews use the public domain and platform-sized PNGs', async ({ page, request }) => {
  await page.goto('/');
  const site = 'https://www.getfieldatlas.com/';
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', site);
  await expect(page.locator('meta[property="og:url"]')).toHaveAttribute('content', site);
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', `${site}assets/social-card.png`);
  await expect(page.locator('meta[name="twitter:image"]')).toHaveAttribute('content', `${site}assets/social-icon.png`);
  await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute('content', 'summary');
  for (const [path, w, h] of [['/assets/social-card.png', 1200, 630], ['/assets/social-icon.png', 600, 600]]) {
    const response = await request.get(path);
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('image/png');
    const png = await response.body();
    expect(png.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a');
    expect([png.readUInt32BE(16), png.readUInt32BE(20)]).toEqual([w, h]);
  }
});

// ---------- the island view ----------

test('the island view starts at the title screen with the world drawn', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('html')).toHaveClass(/game/);
  await expect(page.locator('html')).toHaveClass(/world-ready/);
  await expect(page.locator('#world')).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  // Sections wait for their stop; none is open yet.
  await expect(openSection(page)).toHaveCount(0);
  await expect(page.locator('#faq')).toBeHidden();
});

test('the prompt opens a stop as a dialog that Escape closes, returning focus', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await page.getByRole('button', { name: 'Start the tour' }).click();
  await expect.poll(() => stage(page)).toBeGreaterThan(0.95);
  const open = page.locator('.prompt-open');
  await expect(open).toHaveText(/View features/);
  await open.click();
  const dialog = page.getByRole('dialog', { name: /Research with/ });
  await expect(dialog).toBeVisible();
  await expect(dialog).toBeFocused();
  await expect(page).toHaveURL(/#features$/);
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(open).toBeFocused();
});

test('navigation links open their section at its stop', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/');
  await page.getByRole('navigation').getByRole('link', { name: 'FAQ' }).click();
  await expect(page.getByRole('dialog', { name: 'Common questions.' })).toBeVisible();
  await expect(page).toHaveURL(/#faq$/);
  const question = page.locator('summary', { hasText: 'Does it really work offline?' });
  await question.click();
  await expect(question.locator('..')).toHaveAttribute('open', '');
  await expect(question.locator('..')).toContainText('after setup');
});

test('a shared section link opens that section and focuses it', async ({ page }) => {
  await page.goto('/#collections');
  const dialog = page.getByRole('dialog', { name: /Choose what/ });
  await expect(dialog).toBeVisible();
  await expect(dialog).toBeFocused();
  await expect(dialog.locator('.collection-card')).toHaveCount(5);
});

test('community links keep the supplied address, and copying works and reports failure', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/#community');
  const section = page.locator('#community');
  await expect(section.getByRole('heading', { name: 'Community token' })).toBeVisible();
  await expect(section.locator('.community-copy > p')).toHaveText('The Field Atlas dev didn’t launch this token. A third party launched it on Bankr, with creator fees going to the dev. No token is needed to use the app.');
  await expect(section.getByRole('note', { name: 'Token risk warning' })).toContainText('Tokens can lose all their value.');
  await expect(section.getByRole('link', { name: 'Bankr' })).toHaveAttribute('href', `https://bankr.bot/terminal/trade?out=${token}&chain=base`);
  await expect(section.getByRole('link', { name: 'Dex Screener' })).toHaveAttribute('href', `https://dexscreener.com/base/${token}`);
  const copy = page.getByRole('button', { name: 'Copy full contract address' });
  await copy.click();
  await expect(copy.locator('.copy-check')).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(token);
  await page.evaluate(() => Object.defineProperty(navigator.clipboard, 'writeText', { value: () => Promise.reject(new Error('denied')) }));
  await copy.click();
  await expect(page.locator('#copy-status')).toHaveText('Couldn’t copy. Select the address to copy it manually.');
  await expect(page.locator('#token-address')).toHaveValue(token);
});

test('sound waits for a tap; "Not now" declines it for the visit', async ({ page }) => {
  await page.goto('/');
  const ask = page.getByRole('region', { name: 'Sound' });
  const speaker = page.getByRole('button', { name: 'Sound', exact: true });
  await expect(speaker).toBeVisible();
  // A key press never starts sound.
  await page.keyboard.press('Tab');
  if (await ask.isVisible()) {
    await ask.getByRole('button', { name: 'Not now' }).click();
    await expect(ask).toBeHidden();
    await page.mouse.click(200, 700);
    await expect(speaker).toHaveAttribute('aria-pressed', 'false');
  }
});

test('reduced motion keeps the world still and the tour usable', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await expect(page.locator('html')).toHaveCSS('scroll-behavior', 'auto');
  await page.keyboard.press(']');
  await expect.poll(() => stage(page)).toBeGreaterThan(0.95);
  await expect(page.locator('.prompt')).toBeVisible();
});

for (const width of [320, 390, 820, 1440]) {
  test(`no overflow and reachable navigation at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    if (width <= 960) await page.getByRole('button', { name: 'Menu', exact: true }).click();
    await expect(page.getByRole('navigation').getByRole('link', { name: 'FAQ' })).toBeVisible();
    await expect(page.locator('a.button', { hasText: 'Download for Android' }).first()).toBeVisible();
  });
}

test('the phone menu closes on navigation and on Escape', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  const toggle = page.getByRole('button', { name: 'Menu', exact: true });
  const nav = page.getByRole('navigation');
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await expect(nav).toBeHidden();
  await toggle.click();
  await expect(nav).toBeVisible();
  await nav.getByRole('link', { name: 'Community' }).click();
  await expect(page).toHaveURL(/#community$/);
  await expect(nav).toBeHidden();
  await toggle.click();
  await page.keyboard.press('Escape');
  await expect(nav).toBeHidden();
  await expect(toggle).toBeFocused();
});

// ---------- the plain page, for everyone the island does not suit ----------

test('"Skip to content" turns the tour into the plain page', async ({ page }) => {
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('html')).not.toHaveClass(/(^|\s)game(\s|$)/);
  await expect(page.locator('main')).toBeFocused();
  for (const id of ['features', 'how-it-works', 'collections', 'download', 'faq', 'community']) await expect(page.locator(`#${id}`)).toBeVisible();
});

test('without WebGL the plain page shows every section and the compass art', async ({ page }) => {
  await withoutWebGL(page);
  await page.goto('/');
  await expect(page.locator('html')).toHaveClass(/no-webgl/);
  await expect(page.locator('.world-fallback img')).toBeVisible();
  for (const id of ['features', 'how-it-works', 'collections', 'download', 'faq', 'community']) await expect(page.locator(`#${id}`)).toBeVisible();
  await expect(page.locator('.prompt')).toBeHidden();
});

test('a short screen (landscape phone, 200% zoom) gets the plain page', async ({ page }) => {
  await page.setViewportSize({ width: 844, height: 390 });
  await page.goto('/');
  await expect(page.locator('html')).toHaveClass(/no-webgl/);
  await expect(page.locator('#faq')).toBeVisible();
});

test('if the tour script never arrives, the plain page takes over', async ({ page }) => {
  await page.route('**/game.js', route => route.fulfill({ status: 503, body: '' }));
  await page.goto('/');
  await expect(page.locator('html')).toHaveClass(/no-webgl/, { timeout: 8000 });
  await expect(page.locator('#faq')).toBeVisible();
});

test('navigation and FAQ work with JavaScript disabled', async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto(home);
  await page.getByRole('navigation').getByRole('link', { name: 'How it works' }).click();
  await expect(page).toHaveURL(/#how-it-works$/);
  const question = page.locator('summary').filter({ hasText: 'Does it really work offline?' });
  await question.click();
  await expect(question.locator('..')).toHaveAttribute('open', '');
  await expect(page.locator('#token-address')).toHaveValue(token);
  await expect(page.getByRole('button', { name: 'Copy full contract address' })).toBeHidden();
  await expect(page.locator('a.button', { hasText: 'Download for Android' }).first()).toHaveAttribute('href', apk);
  await context.close();
});

test('enlarged text keeps the plain page within the screen', async ({ page }) => {
  await withoutWebGL(page);
  await page.setViewportSize({ width: 320, height: 900 });
  await page.goto('/');
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('compass artwork: needle facets meet at the small center circle', async ({ page }) => {
  await page.goto('/assets/compass.svg');
  const joins = await page.locator('#compass-needle path').evaluateAll(paths => {
    const hub = paths[0].parentElement.querySelector('circle');
    const x = hub.cx.baseVal.value, y = hub.cy.baseVal.value;
    return paths.slice(1).map(path => {
      const tip = path.getPointAtLength(0);
      // The closing edge should run directly from the hub to the tip.
      const join = path.getPointAtLength(path.getTotalLength() - Math.hypot(tip.x - x, tip.y - y));
      return Math.hypot(join.x - x, join.y - y);
    });
  });
  for (const distance of joins) expect(distance).toBeLessThan(.1);
});
