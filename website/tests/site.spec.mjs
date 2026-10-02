import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const release = 'https://github.com/0x94t3z/fieldatlas/releases';
const apk = `${release}/download/v1.3.0/fieldatlas.apk`;

test('renamed artwork bypasses old asset URLs and retains its original placement', async ({ page, request }) => {
  await page.goto('/');
  for (const logo of await page.locator('.wordmark img').all()) {
    await expect(logo).toHaveAttribute('src', /assets\/atlas\.svg\?v=[a-f0-9]+$/);
    const response = await request.get(await logo.getAttribute('src'));
    expect(await response.text()).toContain('viewBox="0 0 108 108"');
  }
  await expect(page.locator('.atlas-frame #compass-needle')).toBeVisible();
  await expect(page.locator('.atlas-frame > svg')).toHaveAttribute('viewBox', '0 0 640 700');
  await expect(page.locator('link[rel="icon"][type="image/svg+xml"]')).toHaveAttribute('href', /atlas\.svg\?v=[a-f0-9]+$/);
});

test('mobile menu opens, closes on navigation and Escape, and keeps the header compact', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  const toggle = page.getByRole('button', { name: 'Menu', exact: true });
  const nav = page.getByRole('navigation');
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await expect(nav).toBeHidden();
  expect((await page.locator('.site-header').boundingBox()).height).toBeLessThan(110);
  await toggle.click();
  await expect(nav).toBeVisible();
  await nav.getByRole('link', { name: 'Community' }).click();
  await expect(page).toHaveURL(/#community$/);
  await expect(nav).toBeHidden();
  await expect(page.locator('#community > .eyebrow')).toHaveText('05 / COMMUNITY');
  await toggle.click();
  await page.keyboard.press('Escape');
  await expect(nav).toBeHidden();
  await expect(toggle).toBeFocused();
  await page.setViewportSize({ width: 1440, height: 900 });
  await expect(nav).toBeVisible();
  await expect(toggle).toBeHidden();
});

test('status indicators pulse without moving and stay still with reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/');
  const dot = page.locator('.status-dot').first();
  await expect(dot).toHaveCSS('animation-name', 'status-lamp');
  const states = await dot.evaluate(el => {
    const animation = el.getAnimations()[0];
    animation.pause();
    animation.currentTime = 0;
    const bright = Number(getComputedStyle(el).opacity);
    const before = el.getBoundingClientRect();
    animation.currentTime = 1400;
    const dim = Number(getComputedStyle(el).opacity);
    const after = el.getBoundingClientRect();
    return { bright, dim, sameSize: before.width === after.width && before.height === after.height };
  });
  expect(states.bright).toBe(1);
  expect(states.dim).toBeLessThan(.3);
  expect(states.sameSize).toBe(true);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(dot).toHaveCSS('animation-name', 'none');
  await expect(dot).toHaveCSS('opacity', '1');
});

test('community navigation leads below FAQ and platform links keep the supplied address', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('navigation').getByRole('link', { name: 'Community' }).click();
  await expect(page).toHaveURL(/#community$/);
  const section = page.locator('#community');
  await expect(section.getByRole('heading')).toHaveText('Community token');
  expect(await section.evaluate(el => el.previousElementSibling.id)).toBe('faq');
  await expect(section.getByRole('link', { name: 'Bankr' })).toHaveAttribute('href', 'https://bankr.bot/terminal/trade?out=0xb462a5039a540883508ee37b5cf3999647557ba3&chain=base');
  await expect(section.getByRole('link', { name: 'Dex Screener' })).toHaveAttribute('href', 'https://dexscreener.com/base/0xb462a5039a540883508ee37b5cf3999647557ba3');
  for (const img of await section.locator('img').all()) {
    expect(await img.evaluate(el => el.complete && el.naturalWidth > 0)).toBe(true);
  }
});

test('community copies the full address and reports clipboard failure', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/');
  const copy = page.getByRole('button', { name: 'Copy full contract address' });
  await page.clock.install();
  await copy.click();
  await expect(copy).toBeEnabled();
  await expect(copy.locator('.copy-check')).toBeVisible();
  await expect(copy.locator('.copy-glyph')).toBeHidden();
  await expect(page.locator('#copy-status')).toBeEmpty();
  await expect(page.getByText('Address copied', { exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('0xb462a5039a540883508ee37b5cf3999647557ba3');
  await page.clock.fastForward(1500);
  await expect(copy.locator('.copy-check')).toBeHidden();
  await expect(copy.locator('.copy-glyph')).toBeVisible();
  await page.evaluate(() => Object.defineProperty(navigator.clipboard, 'writeText', { value: () => Promise.reject(new Error('denied')) }));
  await copy.click();
  await expect(copy.locator('.copy-check')).toBeHidden();
  await expect(page.locator('#copy-status')).toHaveText('Couldn’t copy. Select the address to copy it manually.');
  await expect(page.locator('#copy-status')).toHaveCSS('clip-path', 'none');
  await expect(page.locator('#token-address')).toHaveValue('0xb462a5039a540883508ee37b5cf3999647557ba3');
});

test('community address remains accessible without JavaScript', async ({ browser }) => {
  const page = await browser.newPage({ javaScriptEnabled: false });
  await page.goto(process.env.SITE_URL || 'http://127.0.0.1:4173');
  await expect(page.locator('#token-address')).toHaveValue('0xb462a5039a540883508ee37b5cf3999647557ba3');
  await expect(page.getByRole('button', { name: 'Copy full contract address' })).toBeHidden();
  await page.close();
});

test('social previews use the public domain and downloadable platform-sized PNGs', async ({ page, request }) => {
  await page.goto('/');
  const site = 'https://www.getfieldatlas.com/';
  const preview = `${site}assets/social-card.png`;
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', site);
  await expect(page.locator('meta[property="og:url"]')).toHaveAttribute('content', site);
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', preview);
  await expect(page.locator('meta[name="twitter:image"]')).toHaveAttribute('content', `${site}assets/social-icon.png`);
  await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute('content', 'summary');
  const response = await request.get('/assets/social-card.png');
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('image/png');
  const png = await response.body();
  expect(png.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a');
  expect(png.readUInt32BE(16)).toBe(1200);
  expect(png.readUInt32BE(20)).toBe(630);
  const iconResponse = await request.get('/assets/social-icon.png');
  expect(iconResponse.status()).toBe(200);
  expect(iconResponse.headers()['content-type']).toContain('image/png');
  const icon = await iconResponse.body();
  expect(icon.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a');
  expect(icon.readUInt32BE(16)).toBe(600);
  expect(icon.readUInt32BE(20)).toBe(600);
});

test('scroll reveals replay on re-entry and respect reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/');
  const card = page.locator('.collection-card').first();
  const title = card.locator('h3');
  const scrollToCard = () => card.evaluate(el => window.scrollTo({
    top: scrollY + el.getBoundingClientRect().top - 100, behavior: 'instant'
  }));
  await scrollToCard();
  await expect(title).toHaveCSS('animation-name', 'scroll-reveal');
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await expect(title).toHaveCSS('animation-name', 'none');
  await scrollToCard();
  await expect(title).toHaveCSS('animation-name', 'scroll-reveal');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(title).toHaveCSS('animation-name', 'none');
  await expect(title).toHaveCSS('opacity', '1');
});

test('needle facets meet at the small center circle', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
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

test('compass is centered and only its needle rotates', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/');
  const center = await page.locator('#compass-face').evaluate(el => {
    const svg = el.ownerSVGElement.getBoundingClientRect();
    const circle = el.getBoundingClientRect();
    return { x: (circle.x + circle.width / 2 - svg.x) / svg.width,
      y: (circle.y + circle.height / 2 - svg.y) / svg.height };
  });
  expect(center.x).toBeCloseTo(.5, 2);
  expect(center.y).toBeCloseTo(.5, 2);
  await expect(page.locator('#compass-needle')).toHaveCSS('animation-name', 'compass-turn');
  await expect(page.locator('#compass-face')).toHaveCSS('animation-name', 'none');
  const before = await page.locator('#compass-needle').evaluate(el => getComputedStyle(el).transform);
  await expect.poll(() => page.locator('#compass-needle').evaluate(el => getComputedStyle(el).transform)).not.toBe(before);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('#compass-needle')).toHaveCSS('animation-name', 'none');
});

test('headline loops visibly without animation controls and respects reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/');
  const lines = page.locator('.headline-line');
  await expect(lines).toHaveCount(3);
  await expect(lines.first()).toHaveCSS('animation-iteration-count', 'infinite');
  for (const line of await lines.all()) await expect(line).toHaveCSS('opacity', '1');
  await expect(page.getByRole('button', { name: /animations/i })).toHaveCount(0);
  await expect(lines.first()).toHaveCSS('animation-play-state', 'running');
  await expect(page.locator('#compass-needle')).toHaveCSS('animation-play-state', 'running');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(lines.first()).toHaveCSS('animation-name', 'none');
  await expect(page.locator('#compass-needle')).toHaveCSS('animation-name', 'none');
});

test('failed motion enhancement leaves readable content and working download links', async ({ page }) => {
  await page.route('**/assets/compass.svg*', route => route.request().resourceType() === 'fetch'
    ? route.fulfill({ status: 503, body: '' }) : route.fallback());
  await page.goto('/');
  await expect(page.locator('.atlas-frame > img')).toBeVisible();
  await expect(page.locator('.motion-toggle')).toBeHidden();
  await expect(page.locator('.headline-line').first()).toHaveCSS('animation-name', 'none');
  await expect(page.getByRole('link', { name: 'Download for Android', exact: true }).first()).toHaveAttribute('href', apk);
});

test('entrance motion is finite and content stays visible', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/');
  for (const selector of ['.hero-copy', '.hero-art']) {
    const styles = await page.locator(selector).evaluate(el => {
      const s = getComputedStyle(el);
      return { name: s.animationName, count: s.animationIterationCount, opacity: s.opacity };
    });
    expect(styles.name).not.toBe('none');
    expect(styles.count).toBe('1');
    expect(Number(styles.opacity)).toBe(1);
  }
  await expect(page.locator('html')).toHaveCSS('scroll-behavior', 'smooth');
});

test('reduced motion disables entrance and hover movement', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  for (const selector of ['.hero-copy', '.hero-art']) {
    await expect(page.locator(selector)).toHaveCSS('animation-name', 'none');
    await expect(page.locator(selector)).toHaveCSS('opacity', '1');
  }
  const button = page.locator('.button').first();
  await button.hover();
  await expect(button).toHaveCSS('transform', 'none');
  await expect(page.locator('html')).toHaveCSS('scroll-behavior', 'auto');
});

test('pointer feedback lifts buttons and moves link arrows', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/');
  const button = page.locator('.button').first();
  await button.hover();
  await expect(button).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 0, -2)');
  await page.locator('.github-link').hover();
  await expect(page.locator('.github-link .icon')).toHaveCSS('transform', 'matrix(1, 0, 0, 1, 3, 0)');
});
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

test('clear introduction and consistent release download path', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('h1')).toHaveCount(1);
  await expect(page.locator('h1')).toHaveText('Knowledge, wherever you go.');
  const downloads = page.getByRole('link', { name: 'Download for Android', exact: true });
  await expect(downloads).toHaveCount(2);
  for (const link of await downloads.all()) await expect(link).toHaveAttribute('href', apk);
  await expect(page.getByRole('link', { name: 'Verify checksum' })).toHaveAttribute('href', `${apk}.sha256`);
  await expect(page.getByRole('link', { name: 'Release notes', exact: true }).first()).toHaveAttribute('href', `${release}/tag/v1.3.0`);
  await expect(page.locator('#download')).toContainText('Release · v1.3.0');
  await expect(page.locator('#download')).toContainText('Android 13+');
});

test('navigation and FAQ work with JavaScript disabled', async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto(process.env.SITE_URL || 'http://127.0.0.1:4173');
  await expect(page.locator('.motion-toggle')).toBeHidden();
  await expect(page.locator('.headline-line').first()).toHaveCSS('animation-name', 'none');
  await page.getByRole('navigation').getByRole('link', { name: 'How it works' }).click();
  await expect(page).toHaveURL(/#how-it-works$/);
  // The hash changes before smooth scrolling completes. Wait for the destination
  // before asking the browser to scroll to a different section for the next action.
  await expect.poll(() => page.locator('#how-it-works').evaluate(el =>
    Math.abs(el.getBoundingClientRect().top - parseFloat(getComputedStyle(el).scrollMarginTop))
  )).toBeLessThan(2);
  const question = page.locator('summary').filter({ hasText: 'Does it really work offline?' });
  await question.click();
  await expect(question.locator('..')).toHaveAttribute('open', '');
  await expect(question.locator('..')).toContainText('after setup');
  await expect(page.getByRole('link', { name: 'Download for Android', exact: true }).first()).toHaveAttribute('href', apk);
  await context.close();
});

for (const width of [320, 390, 768, 1440]) {
  test(`no overflow and usable navigation at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    if (width <= 760) await page.getByRole('button', { name: 'Menu', exact: true }).click();
    await expect(page.getByRole('navigation').getByRole('link', { name: 'FAQ' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Download for Android', exact: true }).first()).toBeVisible();
  });
}

test('keyboard skip link, readable enlarged text, and reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/#main$/);
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('local assets load and page makes no third-party requests', async ({ page }) => {
  const external = [];
  page.on('request', r => { if (new URL(r.url()).origin !== new URL(process.env.SITE_URL || 'http://127.0.0.1:4173').origin) external.push(r.url()); });
  await page.goto('/');
  expect(await page.locator('img').evaluateAll(images => images.every(i => i.complete && i.naturalWidth > 0))).toBe(true);
  expect(external).toEqual([]);
  await expect(page).toHaveTitle(/Field Atlas/);
});

for (const width of [320, 768]) {
  test(`enlarged download labels remain readable at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/');
    await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
    for (const button of await page.locator('.button').all()) {
      const metrics = await button.evaluate(el => ({
        height: el.getBoundingClientRect().height,
        lineHeight: parseFloat(getComputedStyle(el).lineHeight),
      }));
      // A maximum of three label lines plus comfortable padding, not a letter column.
      expect(metrics.height).toBeLessThanOrEqual(metrics.lineHeight * 3 + 68);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}
