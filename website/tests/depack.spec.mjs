import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const csp = JSON.parse(readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'))
  .headers[0].headers.find(header => header.key === 'Content-Security-Policy').value;
const shown = page => page.locator('.book:not([hidden])');
const titles = page => shown(page).locator('h3').allTextContents();

test.beforeEach(async ({ page }) => {
  if (process.env.SITE_URL) return;
  await page.route('**/*', async route => {
    if (route.request().resourceType() !== 'document') return route.continue();
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'content-security-policy': csp } });
  });
});

test('the library page searches and filters under the site policy', async ({ page }) => {
  const problems = [];
  page.on('console', m => m.type() === 'error' && problems.push(m.text()));
  page.on('pageerror', e => problems.push(e.message));
  await page.goto('/depack/');
  await expect(page.locator('.controls')).toBeVisible();
  await expect(page.locator('#status')).toHaveText('');
  await expect(page.locator('.controls select')).toHaveCount(0);

  // Words from the full descriptions count, and every word must match.
  await page.fill('#q', 'drinking water');
  await expect(shown(page)).toHaveCount(1);
  expect(await titles(page)).toEqual(['Essentials']);
  await expect(page.locator('#status')).toHaveText('1 of 10 collections · 986 MB to download');
  await page.fill('#q', 'water');
  expect(await titles(page)).toEqual(expect.arrayContaining(['Essentials', 'Practical skills']));
  await expect(page.locator('#group-places .count')).toHaveText('1');
  await page.fill('#q', 'cafe');
  expect(await titles(page)).toContain('Vegan places');

  // The clear button empties the search; chips filter by category.
  await page.click('#clear');
  await expect(page.locator('#q')).toHaveValue('');
  await expect(page.locator('#clear')).toBeHidden();
  await page.click('.chip[data-category="Science"]');
  await expect(shown(page)).toHaveCount(3);
  await expect(page).toHaveURL(/category=Science/);
  await page.click('.chip[data-category="all"]');

  // Nothing found, then one tap back to everything.
  await page.fill('#q', 'zzzz');
  await expect(page.locator('#empty')).toBeVisible();
  await page.click('#reset');
  await expect(shown(page)).toHaveCount(10);
  await expect(page.locator('.group').first()).toBeVisible();

  // Details open as a catalogue card and close back to the cover.
  await page.locator('#practical-skills summary').click();
  await expect(page.locator('#card')).toBeVisible();
  await expect(page.locator('#card-title')).toHaveText('Practical skills');
  await expect(page.locator('#card-body')).toContainText('SHA-256');
  await page.keyboard.press('Escape');
  await expect(page.locator('#card')).toBeHidden();
  await expect(page.locator('#practical-skills summary')).toBeFocused();
  await page.locator('#osm-essentials .cover').click();
  await expect(page.locator('#card-title')).toHaveText('Essentials');
  await page.click('#card-close');
  await expect(page.locator('#card')).toBeHidden();

  // "/" jumps to the search box; a shared link keeps its filters.
  await page.locator('h1').click();
  await page.keyboard.press('/');
  await expect(page.locator('#q')).toBeFocused();
  await page.goto('/depack/?q=algebra');
  expect(await titles(page)).toEqual(['Mathematics: Prealgebra']);
  expect(problems).toEqual([]);
});

test('without the script every collection is listed', async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto('/depack/');
  await expect(page.locator('.book')).toHaveCount(10);
  await expect(page.locator('.controls')).toBeHidden();
  await context.close();
});
