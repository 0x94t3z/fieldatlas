import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const poidh = JSON.parse(readFileSync(new URL('./fixtures/poidh-base.json', import.meta.url), 'utf8'));
const POIDH = '0x5555fa783936c260f77385b4e153b9725fef1719';
const SUFFIX = '62635f64696579366f69670b0080218021802180218021802180218021';

// Answers the page's Base RPC calls from the fixture, by function selector, and records every call.
// An ABI-encoded string, as a contract returns one.
const abiString = text => {
  const bytes = Buffer.from(text, 'utf8');
  return `0x${'20'.padStart(64, '0')}${bytes.length.toString(16).padStart(64, '0')}${bytes.toString('hex').padEnd(Math.ceil(bytes.length / 32) * 64, '0')}`;
};
const word = address => `0x${address.slice(2).toLowerCase().padStart(64, '0')}`;
const BASENAME_RESOLVER = '0xc6d566a56a1aff6508b41f6c90ff131615583bcd';
const uint = n => BigInt(n).toString(16).padStart(64, '0');
// One Claim as the contract encodes it (id, issuer, bountyId, bountyIssuer, name, description, createdAt, accepted),
// without the outer offset; claim n is "Claim n" by a wallet ending in n.
const claimTuple = n => {
  const str = text => { const hex = Buffer.from(text).toString('hex'); return uint(hex.length / 2) + hex.padEnd(Math.ceil(hex.length / 64) * 64 || 64, '0'); };
  const name = str(`Claim ${n}`), about = str(`About claim ${n}`);
  return uint(n) + uint(0x3000 + n) + uint(600) + uint(0x1234) + uint(256) + uint(256 + name.length / 2) + uint(1795000000 + n) + uint(0) + name + about;
};
const claimList = ids => {
  const tuples = ids.map(claimTuple);
  let at = 32 * ids.length;
  const heads = tuples.map(t => { const h = uint(at); at += t.length / 2; return h; });
  return `0x${uint(32)}${uint(ids.length)}${heads.join('')}${tuples.join('')}`;
};

// basename: { owner, name, pointsTo } gives that wallet a name in Base's reverse registrar, and the name's forward
// record points to pointsTo. Every other wallet has no Basename.
async function fakeBase(page, { joinRevert = null, joinError = null, createRevert = null, cancelRevert = null, byUser = poidh.byUser, code = {}, basename = null, reverts = {}, manyClaims = null } = {}) {
  const calls = [];
  await page.route('https://mainnet.base.org/**', async route => {
    const body = route.request().postDataJSON();
    const replies = body.map(({ id, method, params }) => {
      calls.push({ method, params });
      if (method === 'eth_getTransactionReceipt') return { jsonrpc: '2.0', id, result: { status: '0x1' } };
      // Wallets have no code unless a test gives one.
      if (method === 'eth_getCode') return { jsonrpc: '2.0', id, result: code[params[0].toLowerCase()] ?? '0x' };
      // Every wallet holds 0.0503 ETH on Base.
      if (method === 'eth_getBalance') return { jsonrpc: '2.0', id, result: '0xb2b3956033c000' };
      const data = params[0].data;
      const selector = data.slice(2, 10);
      // Basename lookups: nameForAddr, then the registry's resolver(node), then that resolver's addr(node).
      if (selector === '4ec3bd23') return { jsonrpc: '2.0', id, result: abiString(basename && data.slice(34, 74) === basename.owner.slice(2).toLowerCase() ? basename.name : '') };
      if (selector === '0178b8bf') return { jsonrpc: '2.0', id, result: word(BASENAME_RESOLVER) };
      if (selector === '3b3b57de') return { jsonrpc: '2.0', id, result: word(basename?.pointsTo ?? '0x0000000000000000000000000000000000000000') };
      const arg = data.length >= 74 ? String(BigInt(`0x${data.slice(10, 74)}`)) : '';
      const pick = table => (table[arg] ?? table.default);
      if (selector === '2b9234b9') {
        // userBounties(issuer, index): an index past the end reverts, as on chain.
        const index = Number(BigInt(`0x${data.slice(74, 138)}`));
        return index < poidh.userBounties.length ? { jsonrpc: '2.0', id, result: `0x${poidh.userBounties[index].toString(16).padStart(64, '0')}` }
          : { jsonrpc: '2.0', id, error: { code: 3, message: 'execution reverted' } };
      }
      // Claims: the newest claims per bounty, and whether anyone besides the creator ever funded it (600 and 604 here).
      // manyClaims: { bounty, count } gives that bounty claims 1..count; getClaimsByBountyId returns only the newest ten,
      // as on chain, so the rest must be read by id.
      if (manyClaims && arg === String(manyClaims.bounty)) {
        const all = Array.from({ length: manyClaims.count }, (_, i) => i + 1);
        if (selector === 'b03a50c7') return { jsonrpc: '2.0', id, result: claimList(all.slice(-10).reverse()) };
        if (selector === '34414835') {
          const index = Number(BigInt(`0x${data.slice(74, 138)}`));
          return index < all.length ? { jsonrpc: '2.0', id, result: `0x${uint(all[index])}` } : { jsonrpc: '2.0', id, error: { code: 3, message: 'execution reverted' } };
        }
      }
      if (manyClaims && selector === 'a888c2cd') return { jsonrpc: '2.0', id, result: `0x${claimTuple(Number(arg))}` };
      if (selector === 'b03a50c7') return { jsonrpc: '2.0', id, result: pick(poidh.claims) };
      if (selector === 'b04f5ebd') return { jsonrpc: '2.0', id, result: word(`0x${['600', '604'].includes(arg) ? '1' : '0'}`) };
      if (reverts[selector]) return { jsonrpc: '2.0', id, error: { code: 3, message: 'execution reverted', data: `0x${reverts[selector]}` } };
      // Claim, accept, start a vote, vote and settle succeed in the simulation.
      if (['83ffc9f2', '1d260f8e', '9e9251c8', 'b7c8994d', '30f4ccac'].includes(selector)) return { jsonrpc: '2.0', id, result: '0x' };
      // Cancel, refund and withdraw succeed in the simulation; the tests check what was sent.
      if (selector === 'd496ec7a' && cancelRevert) return { jsonrpc: '2.0', id, error: { code: 3, message: 'execution reverted', data: `0x${cancelRevert}` } };
      if (['d496ec7a', 'b1b4be97', '240b727e', '3ccfd60b'].includes(selector)) return { jsonrpc: '2.0', id, result: '0x' };
      if (selector === 'f3f43703') return { jsonrpc: '2.0', id, result: poidh.balance[data.slice(34, 74)] ?? poidh.balance.default };
      if (selector === '8c1d93c5') {
        return createRevert ? { jsonrpc: '2.0', id, error: { code: 3, message: 'execution reverted', data: `0x${createRevert}` } }
          : { jsonrpc: '2.0', id, result: '0x' };
      }
      // getBountiesByUser for the visiting wallet answers with the bounty it just started.
      if (selector === '0e122a78' && data.slice(10, 74).endsWith('2222222222222222222222222222222222222222')) return { jsonrpc: '2.0', id, result: poidh.byVisitor };
      if (selector === 'a9b2c6eb') {
        if (joinError) return { jsonrpc: '2.0', id, error: joinError };
        return joinRevert ? { jsonrpc: '2.0', id, error: { code: 3, message: 'execution reverted', data: `0x${joinRevert}` } }
          : { jsonrpc: '2.0', id, result: '0x' };
      }
      const result = { '0e122a78': byUser, c1e3bd3e: pick(poidh.participants), '383d6d43': pick(poidh.votingClaim), '10b385c2': pick(poidh.votes), dc2f8744: pick(poidh.bounty) }[selector];
      return { jsonrpc: '2.0', id, result };
    });
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(replies) });
  });
  return calls;
}

// Browser wallets announced through EIP-6963 that record what the page asks of them. With hold set,
// the account request waits until the test calls window.releaseAccounts(); window.walletEmit(event, value)
// plays an event the wallet would send, such as accountsChanged.
const fakeWallet = ({ count = 1, hold = false, account = '0x2222222222222222222222222222222222222222', authorized = false } = {}) => {
  window.walletCalls = [];
  const handlers = {};
  window.walletEmit = (event, value) => (handlers[event] ?? []).forEach(handler => handler(value));
  let release;
  const accounts = hold ? new Promise(resolve => { release = resolve; }) : Promise.resolve();
  window.releaseAccounts = () => release?.();
  for (let n = 1; n <= count; n++) {
    let chain = '0x1';
    const provider = {
      request: async ({ method, params }) => {
        window.walletCalls.push({ wallet: n, method, params });
        if (method === 'eth_requestAccounts') { await accounts; return [account]; }
        if (method === 'eth_accounts') return authorized ? [account] : [];
        if (method === 'wallet_revokePermissions') return null;
        if (method === 'eth_chainId') return chain;
        if (method === 'wallet_switchEthereumChain') { chain = params[0].chainId; return null; }
        if (method === 'eth_sendTransaction') return `0x${'ab'.repeat(32)}`;
        throw Object.assign(new Error('unsupported'), { code: 4200 });
      },
      on: (event, handler) => { (handlers[event] ??= []).push(handler); },
    };
    const announce = () => window.dispatchEvent(new CustomEvent('eip6963:announceProvider', {
      detail: Object.freeze({ info: { uuid: `test-wallet-${n}`, name: `Test wallet ${n}`, icon: '', rdns: `test.wallet${n}` }, provider }),
    }));
    window.addEventListener('eip6963:requestProvider', announce);
    announce();
  }
};

const csp = JSON.parse(readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'))
  .headers[0].headers.find(header => header.key === 'Content-Security-Policy').value;
const shown = page => page.locator('.library .book:not([hidden])');
const titles = page => shown(page).locator('h3').allTextContents();

test.beforeEach(async ({ page }) => {
  if (process.env.SITE_URL) return;
  await page.route('**/*', async route => {
    if (route.request().resourceType() !== 'document') return route.continue();
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'content-security-policy': csp } });
  });
  // No test reaches the real chain. Later routes win in Playwright, so this sits after the catch-all above,
  // and a test that needs the chain answers for it with a route of its own.
  await page.route('https://mainnet.base.org/**', route => route.abort());
  // The ENS lookup answers "no name" unless a test sets one.
  await page.route('https://ethereum-rpc.publicnode.com/**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ jsonrpc: '2.0', id: 1, result: poidh.ensNone }) }));
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
  await expect(shown(page).locator('h3')).toHaveText(['Essentials']);
  await expect(page.locator('#status')).toHaveText('1 of 10 collections · 986 MB to download');
  await page.fill('#q', 'water');
  await expect.poll(() => titles(page)).toEqual(expect.arrayContaining(['Essentials', 'Practical skills']));
  await expect(page.locator('#group-places .count')).toHaveText('1');
  await page.fill('#q', 'cafe');
  await expect.poll(() => titles(page)).toContain('Vegan places');

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
  await expect(shown(page).locator('h3')).toHaveText(['Mathematics: Prealgebra']);
  await expect(page.locator('.tab[aria-current="page"]')).toHaveText('Library');
  await expect(page.locator('.tab', { hasText: 'Wanted' })).toHaveAttribute('href', '/depack/wanted/');
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

// Total layout shift on the page so far, from the browser's own measure.
const watchShifts = page => page.addInitScript(() => {
  window.layoutShift = 0;
  new PerformanceObserver(list => { for (const entry of list.getEntries()) if (!entry.hadRecentInput) window.layoutShift += entry.value; }).observe({ type: 'layout-shift', buffered: true });
});

test('the library does not jump when its script arrives late', async ({ page }) => {
  await watchShifts(page);
  await page.route('**/depack/depack.js', async route => { await new Promise(r => setTimeout(r, 400)); await route.continue(); });
  await page.goto('/depack/');
  await expect(page.locator('.controls')).toBeVisible();
  await page.waitForTimeout(600);
  expect(await page.evaluate(() => window.layoutShift)).toBeLessThan(0.02);
});

const joinData = id => `0xa9b2c6eb${id.toString(16).padStart(64, '0')}${SUFFIX}`;

test('the Wanted page lists bounties and adds ETH through the visitor\'s wallet, with the builder code', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  const problems = [];
  page.on('console', m => m.type() === 'error' && problems.push(m.text()));
  page.on('pageerror', e => problems.push(e.message));
  await page.unroute('https://mainnet.base.org/**');
  const calls = await fakeBase(page);
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await expect(page.locator('.tab[aria-current="page"]')).toHaveText('Wanted');

  // Open first, then voting; paid ones sit on their own shelf. A cancelled bounty is left out, and so is the
  // wallet's pull-request bounty, which was not made for this page.
  await expect(page.locator('#open-shelf h3')).toHaveText(['Bird calls of Borneo', 'Tide tables for the Moluccas', 'First aid in Swahili', 'Road signs of Indonesia']);
  await expect(page.locator('#open-shelf .kicker')).toHaveText(['Wanted', 'Wanted', 'Wanted', 'Voting']);
  await expect(page.locator('#paid-shelf h3')).toHaveText(['Plants of the Andes']);
  await expect(page.locator('#bounty-538')).toHaveCount(0);
  await expect(page.locator('#open-note')).toBeHidden();
  await expect(page.locator('[data-total="open"]')).toHaveText('4');
  await expect(page.locator('[data-total="pooled"]')).toHaveText('0.0423 ETH');
  await expect(page.locator('[data-total="paid"]')).toHaveText('0.012 ETH');
  await expect(page.locator('#open-count')).toHaveText('4');
  await expect(page.locator('#bounty-600 .line')).toHaveText('0.0313 ETH · 2 backers');
  await expect(page.locator('#bounty-590 .line')).toHaveText('0.012 ETH · 2 backers');
  await expect(page.locator('#bounty-601 .line')).toContainText('voting until');
  // A solo bounty takes no ETH from others; voting and paid ones take none either.
  await expect(page.locator('#bounty-603 .get, #bounty-601 .get, #bounty-590 .get')).toHaveCount(0);
  await expect(page.locator('#bounty-603 .onpoidh')).toHaveText('Make it');
  // poidh's site numbers Base bounties 986 after the contract's own numbers.
  await expect(page.locator('#bounty-590 .onpoidh')).toHaveAttribute('href', 'https://poidh.xyz/base/bounty/1576');

  await page.locator('#bounty-600 .get').click();
  await expect(page.locator('#give')).toBeVisible();
  await expect(page.locator('#give-title')).toHaveText('First aid in Swahili');
  await expect(page.locator('#give-send')).toHaveText('Add 0.001 ETH');
  await page.fill('#give-amount', '0.000001');
  await expect(page.locator('#give-send')).toBeDisabled();
  await page.fill('#give-amount', 'abc');
  await expect(page.locator('#give-send')).toBeDisabled();
  // The label repeats the typed amount exactly, and a comma works as the decimal mark.
  await page.fill('#give-amount', '0.00015');
  await expect(page.locator('#give-send')).toHaveText('Add 0.00015 ETH');
  await page.fill('#give-amount', '0,002');
  await expect(page.locator('#give-send')).toHaveText('Add 0.002 ETH');
  await page.locator('#give .preset[data-eth="0.005"]').click();
  await expect(page.locator('#give-send')).toHaveText('Add 0.005 ETH');
  await page.click('#give-send');
  await expect(page.locator('#give-status')).toContainText('Added. Thank you.');

  const wallet = await page.evaluate(() => window.walletCalls);
  // The wallet is moved to Base, and the network is checked again right before signing.
  const methods = wallet.map(c => c.method);
  expect(methods.slice(0, 3)).toEqual(['eth_requestAccounts', 'eth_chainId', 'wallet_switchEthereumChain']);
  expect(methods.at(-2)).toBe('eth_chainId');
  expect(methods.at(-1)).toBe('eth_sendTransaction');
  expect(wallet[2].params).toEqual([{ chainId: '0x2105' }]);
  // Paying connects the wallet too.
  await expect(page.locator('#wallet-label')).toHaveText('0x2222…2222');
  await expect(page.locator('#wallet-button')).toHaveAttribute('data-state', 'base');
  const tx = wallet.at(-1).params[0];
  expect(tx.to).toBe(POIDH);
  expect(tx.chainId).toBe('0x2105');
  expect(BigInt(tx.value)).toBe(5000000000000000n);
  // joinOpenBounty(600), then the ERC-8021 suffix carrying bc_diey6oig.
  expect(tx.data).toBe(joinData(600));
  // The same payment was tried against the chain before the wallet saw it.
  expect(calls.some(c => c.method === 'eth_call' && c.params[0].data === tx.data && c.params[0].value === tx.value)).toBe(true);
  await page.keyboard.press('Escape');
  await expect(page.locator('#bounty-600 .get')).toBeFocused();
  expect(problems).toEqual([]);
});

test('a second Add ETH while a payment waits on the wallet stays on the first bounty', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet, { hold: true });
  await page.goto('/depack/wanted/');
  await page.locator('#bounty-600 .get').click();
  await page.click('#give-send');
  await expect(page.locator('#give-status')).toHaveText('Waiting for your wallet…');
  await page.keyboard.press('Escape');
  await page.locator('#bounty-604 .get').click();
  await expect(page.locator('#give-title')).toHaveText('First aid in Swahili');
  await page.evaluate(() => window.releaseAccounts());
  await expect(page.locator('#give-status')).toContainText('Added. Thank you.');
  const sent = await page.evaluate(() => window.walletCalls.filter(c => c.method === 'eth_sendTransaction').map(c => c.params[0].data));
  expect(sent).toEqual([joinData(600)]);
});

test('with two wallets, closing the chooser cancels cleanly and the next try works', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet, { count: 2 });
  await page.goto('/depack/wanted/');
  await page.locator('#bounty-600 .get').click();
  await page.click('#give-send');
  await expect(page.locator('#give-wallets .wallet')).toHaveText(['Test wallet 1', 'Test wallet 2']);
  await page.keyboard.press('Escape');
  await page.locator('#bounty-600 .get').click();
  await expect(page.locator('#give-wallets')).toBeHidden();
  await expect(page.locator('#give-send')).toBeEnabled();
  await page.click('#give-send');
  await page.locator('#give-wallets .wallet', { hasText: 'Test wallet 2' }).click();
  await expect(page.locator('#give-status')).toContainText('Added. Thank you.');
  const senders = await page.evaluate(() => window.walletCalls.filter(c => c.method === 'eth_sendTransaction').map(c => c.wallet));
  expect(senders).toEqual([2]);
});

test('a bounty that closed meanwhile is caught before the wallet signs', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { joinRevert: 'b6c99482' });
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.locator('#bounty-600 .get').click();
  await page.click('#give-send');
  await expect(page.locator('#give-status')).toHaveText('Voting has started on a claim, so the bounty is closed to new ETH for now.');
  const methods = await page.evaluate(() => window.walletCalls.map(c => c.method));
  expect(methods).not.toContain('eth_sendTransaction');
});

test('with nothing open, the Wanted page says so and still shows what was paid', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { byUser: poidh.byUserPaidOnly });
  await page.goto('/depack/wanted/');
  await expect(page.locator('#open-note')).toHaveText('Nothing is open right now. Ask for the collection you are missing below.');
  await expect(page.locator('#open-shelf .book')).toHaveCount(0);
  await expect(page.locator('#paid-shelf h3')).toHaveText(['Plants of the Andes']);
  await expect(page.locator('[data-total="open"]')).toHaveText('0');
});

test('when Base cannot be reached the Wanted page says so', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'blocks the chain');
  await page.goto('/depack/wanted/');
  await expect(page.locator('#open-note')).toContainText('Base could not be reached just now.');
  await expect(page.locator('#totals')).toBeHidden();
  // Asking and starting a bounty both stay available even when the list cannot be read.
  await expect(page.locator('.ask .quiet-link')).toHaveAttribute('href', 'https://github.com/0x94t3z/fieldatlas/issues/new?template=collection.md');
  await expect(page.locator('#start-open')).toBeVisible();
});

test('the Wanted page keeps its layout while Base is read', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain');
  await watchShifts(page);
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  // Base answers slowly, as it does on a phone network.
  await page.route('https://mainnet.base.org/**', async route => { await new Promise(r => setTimeout(r, 300)); await route.fallback(); });
  await page.goto('/depack/wanted/');
  await expect(page.locator('#totals')).toBeVisible();
  await expect(page.locator('#start-open')).toBeVisible();
  await expect(page.locator('#open-shelf .book')).toHaveCount(4);
  await page.waitForTimeout(300);
  expect(await page.evaluate(() => window.layoutShift)).toBeLessThan(0.05);
});

test('coming back to Wanted shows the last list at once, even before Base answers', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.goto('/depack/wanted/');
  await expect(page.locator('#open-shelf .book')).toHaveCount(4);
  await page.click('.tab >> text=Library');
  await expect(page).toHaveURL(/\/depack\/$/);
  // Base is now unreachable: the remembered list still shows, with a plain note that it could not be refreshed.
  await page.unroute('https://mainnet.base.org/**');
  await page.route('https://mainnet.base.org/**', route => route.abort());
  await page.click('.tab >> text=Wanted');
  await expect(page.locator('#open-shelf h3')).toHaveText(['Bird calls of Borneo', 'Tide tables for the Moluccas', 'First aid in Swahili', 'Road signs of Indonesia']);
  await expect(page.locator('[data-total="pooled"]')).toHaveText('0.0423 ETH');
  await expect(page.locator('#open-note')).toContainText('Base could not be reached just now.');
});

test('a wallet with more than ten bounties still shows the older ones made for the page', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { byUser: poidh.byUserFull });
  await page.goto('/depack/wanted/');
  // The newest ten are someone else's kind of bounty; the page's own one is eleventh from the top.
  await expect(page.locator('#open-shelf h3')).toHaveText(['Sign language basics']);
  await expect(page.locator('#bounty-650 .line')).toHaveText('0.007 ETH · 1 backer');
  await expect(page.locator('#bounty-650 .get')).toHaveCount(1);
});

test('anyone can start a bounty from the page, carrying the builder code, without going to poidh', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  const problems = [];
  page.on('pageerror', e => problems.push(e.message));
  await page.unroute('https://mainnet.base.org/**');
  const calls = await fakeBase(page);
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.click('#start-open');
  await expect(page.locator('#start')).toBeVisible();
  // Without a wallet the dialog asks to connect first; the start button and the balance follow.
  await expect(page.locator('#start-connect')).toBeVisible();
  await expect(page.locator('#start-send')).toBeHidden();
  await expect(page.locator('#start-balance-row')).toBeHidden();
  await page.click('#start-connect');
  await expect(page.locator('#start-connect')).toBeHidden();
  await expect(page.locator('#start-balance')).toHaveText('0.0503 ETH');
  await expect(page.locator('#start-send')).toHaveText('Start it with 0.005 ETH');
  // Focus moves on from the connect button to the first part to fill.
  await expect(page.locator('#start-name')).toBeFocused();
  expect(await page.locator('#start-name').evaluate(t => t.value.slice(t.selectionStart, t.selectionEnd))).toBe('[Subject]');
  // The dialog opens on the textbook template, its first [part] selected; the button waits until none are left.
  await expect(page.locator('#start-name')).toHaveValue('[Subject]: [book title]');
  await expect(page.locator('#start-about')).toHaveValue(/^\[Book title and edition\] as an offline collection/);
  await expect(page.locator('#start-left')).toHaveText('6 parts left to fill. Tab jumps to the next one.');
  await expect(page.locator('#start-send')).toBeDisabled();
  await page.keyboard.type('Chemistry');
  await page.keyboard.press('Tab');
  await page.keyboard.type('Chemistry 2e');
  await page.keyboard.press('Tab');
  await expect(page.locator('#start-about')).toBeFocused();
  expect(await page.locator('#start-about').evaluate(t => t.value.slice(t.selectionStart, t.selectionEnd))).toBe('[Book title and edition]');
  await expect(page.locator('#start-name')).toHaveValue('Chemistry: Chemistry 2e');
  await expect(page.locator('#start-left')).toHaveText('4 parts left to fill. Tab jumps to the next one.');
  // Templates swap the wording; the preview shows the page's line that is added when it is sent.
  // Typed text is replaced only after asking.
  const asked = [];
  page.once('dialog', dialog => { asked.push(dialog.message()); dialog.accept(); });
  await page.locator('#start .kind[data-kind="places"]').click();
  expect(asked).toEqual(['Replace your text with the Places template?']);
  await expect(page.locator('#start-name')).toHaveValue('Places: [what and where]');
  await page.locator('#start .view[data-view="preview"]').click();
  await expect(page.locator('#start-preview p').last()).toHaveText('Wanted on https://www.getfieldatlas.com/depack/wanted/');
  await expect(page.locator('#start-about')).toBeHidden();
  await page.locator('#start .view[data-view="write"]').click();
  // The payout row is exact, and the wallet's balance shows once it is known.
  await expect(page.locator('#start-gets')).toHaveText('0.004875 ETH');
  // Below the contract's 0.001 ETH minimum the button stays off.
  await page.fill('#start-name', poidh.create.name);
  await page.fill('#start-about', poidh.create.about);
  await expect(page.locator('#start-left')).toHaveText('');
  await page.fill('#start-amount', '0.0005');
  await expect(page.locator('#start-send')).toBeDisabled();
  await page.locator('#start .preset[data-eth="0.005"]').click();
  await page.click('#start-send');
  await expect(page.locator('#start-status')).toContainText('Your bounty is live. It shows on this page after a short review.');
  await expect(page.locator('#start-status a')).toHaveAttribute('href', 'https://poidh.xyz/base/bounty/1596');

  const tx = await page.evaluate(() => window.walletCalls.find(c => c.method === 'eth_sendTransaction').params[0]);
  expect(tx.to).toBe(POIDH);
  expect(tx.chainId).toBe('0x2105');
  expect(BigInt(tx.value)).toBe(5000000000000000n);
  // createOpenBounty(name, description + the page's address) as cast encodes it, then the ERC-8021 suffix.
  expect(tx.data).toBe(`${poidh.create.calldata}${SUFFIX}`);
  expect(calls.some(c => c.method === 'eth_call' && c.params[0].data === tx.data)).toBe(true);
  expect(problems).toEqual([]);
});

test('the start dialog cannot be sent without a wallet, and Tab leaves a field whose parts are hidden in Preview', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.click('#start-open');
  await page.locator('#start .view[data-view="preview"]').click();
  await page.fill('#start-name', poidh.create.name);
  await page.keyboard.press('Tab');
  await expect(page.locator('#start-name')).not.toBeFocused();
  await page.locator('#start .view[data-view="write"]').click();
  await page.fill('#start-about', poidh.create.about);
  // Enter in a field submits the form; with no wallet connected nothing is asked of the wallet.
  await page.locator('#start-name').press('Enter');
  await page.waitForTimeout(300);
  expect(await page.evaluate(() => window.walletCalls.map(c => c.method))).toEqual([]);
  await expect(page.locator('#start-connect')).toBeVisible();
});

test('typed descriptions preview as text, bold and https links only', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.goto('/depack/wanted/');
  await page.click('#start-open');
  await page.fill('#start-about', 'javascript:alert(1) **<img src=x onerror=alert(1)>** https://evil.example/"onmouseover=alert(1) [t](javascript:alert(1))\n\n- one\n- two');
  await page.locator('#start .view[data-view="preview"]').click();
  await expect(page.locator('#start-preview img')).toHaveCount(0);
  await expect(page.locator('#start-preview b')).toHaveText('<img src=x onerror=alert(1)>');
  await expect(page.locator('#start-preview a')).toHaveCount(1);
  await expect(page.locator('#start-preview a').first()).toHaveAttribute('href', 'https://evil.example/');
  expect(await page.locator('#start-preview a').evaluateAll(links => links.every(a => a.href.startsWith('https://') && a.target === '_blank' && !a.hasAttribute('onmouseover')))).toBe(true);
  await expect(page.locator('#start-preview li')).toHaveText(['one', 'two']);
  // A Markdown link is not a part to fill.
  await page.fill('#start-name', 'Chemistry: Chemistry 2e');
  await expect(page.locator('#start-left')).toHaveText('');
});

test('a smart-contract wallet is told plainly that poidh needs a regular wallet to start a bounty', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  // A simulated call cannot show this refusal, so the page reads the wallet's code: contract code is refused.
  await fakeBase(page, { code: { '0x2222222222222222222222222222222222222222': '0x6080604052' } });
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.click('#start-open');
  await page.click('#start-connect');
  await page.fill('#start-name', poidh.create.name);
  await page.fill('#start-about', poidh.create.about);
  await page.click('#start-send');
  await expect(page.locator('#start-status')).toHaveText('poidh lets only a regular wallet start a bounty, not a smart-contract wallet. Try another wallet.');
  expect(await page.evaluate(() => window.walletCalls.map(c => c.method))).not.toContain('eth_sendTransaction');
});

test('a regular wallet with an EIP-7702 delegation can still start a bounty', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { code: { '0x2222222222222222222222222222222222222222': `0xef0100${'61'.repeat(20)}` } });
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.click('#start-open');
  await page.click('#start-connect');
  await page.fill('#start-name', poidh.create.name);
  await page.fill('#start-about', poidh.create.about);
  await page.click('#start-send');
  await expect(page.locator('#start-status')).toContainText('poidh');
  expect(await page.evaluate(() => window.walletCalls.map(c => c.method))).toContain('eth_sendTransaction');
});

test('a wallet short of ETH is told so in plain words, as Base reports it', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { joinError: { code: -32003, message: 'EVM error: OutOfFunds' } });
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.locator('#bounty-600 .get').click();
  await page.fill('#give-amount', '0.005');
  await page.click('#give-send');
  await expect(page.locator('#give-status')).toHaveText('This wallet does not have enough ETH on Base.');
  expect(await page.evaluate(() => window.walletCalls.map(c => c.method))).not.toContain('eth_sendTransaction');
});

test('the open wallet panel follows a switch to another account', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet, { account: OWNER });
  await page.goto('/depack/wanted/');
  await page.click('#wallet-button');
  await expect(page.locator('#wallet-send')).toHaveText('Withdraw 0.005 ETH');
  await expect(page.locator('#wallet-locked')).toHaveText('0.04 ETH');
  await page.evaluate(() => window.walletEmit('accountsChanged', ['0x2222222222222222222222222222222222222222']));
  await expect(page.locator('#wallet-title')).toHaveText('0x2222…2222');
  await expect(page.locator('#wallet-balance')).toHaveText('0 ETH');
  await expect(page.locator('#wallet-send')).toBeHidden();
  await expect(page.locator('#wallet-locked-row')).toBeHidden();
  await expect(page.locator('#wallet-refunds .refund span')).toHaveText(['0.001 ETH from Cancelled request, cancelled']);
  await page.click('#wallet-close');
  await expect(page.locator('#bounty-600 .get')).toHaveText('Add ETH');
});

for (const width of [320, 360, 414]) {
  test(`at ${width}px the Add ETH and Start dialogs fit without sideways scrolling`, async ({ page }) => {
    test.skip(!!process.env.SITE_URL, 'uses a fake chain');
    await page.setViewportSize({ width, height: 760 });
    await page.unroute('https://mainnet.base.org/**');
    await fakeBase(page);
    await page.goto('/depack/wanted/');
    const fits = id => page.evaluate(id => { const d = document.getElementById(id); return d.scrollWidth <= d.clientWidth; }, id);
    await page.locator('#bounty-600 .get').click();
    await page.focus('#give-amount');
    expect(await fits('give')).toBe(true);
    await page.click('#give-close');
    await page.click('#start-open');
    await page.focus('#start-amount');
    expect(await fits('start')).toBe(true);
  });
}

test('a bounty someone else started shows once it is on the reviewed list, with who started it', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  // 611 is on the list but was not made from this page, so it stays off.
  await page.route('**/depack/wanted.json', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ approved: [610, 611] }) }));
  await page.goto('/depack/wanted/');
  await expect(page.locator('#bounty-610 h3')).toHaveText('Swahili first aid');
  await expect(page.locator('#bounty-610 .byline')).toHaveText('Started by 0x2222…2222');
  await expect(page.locator('#bounty-610 .get')).toHaveCount(1);
  await expect(page.locator('#bounty-611')).toHaveCount(0);
  await expect(page.locator('#bounty-600 .byline')).toHaveCount(0);
});

const OWNER = '0xc698865c38eC12b475AA55764d447566dd54c758';

test('a visitor connects a wallet, sees nothing to withdraw, and takes back ETH from a cancelled bounty', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await expect(page.locator('#wallet-label')).toHaveText('Connect wallet');
  await page.click('#wallet-button');
  await expect(page.locator('#wallet-label')).toHaveText('0x2222…2222');
  await expect(page.locator('#wallet-button')).toHaveAttribute('data-state', 'other');
  await expect(page.locator('#wallet-address-row')).toBeHidden();
  await expect(page.locator('#wallet-balance')).toHaveText('0 ETH');
  await expect(page.locator('#wallet-locked-row')).toBeHidden();
  await expect(page.locator('#wallet-send')).toBeHidden();
  // A visitor never sees Cancel on someone else's bounty.
  await expect(page.locator('#open-shelf .get', { hasText: 'Cancel bounty' })).toHaveCount(0);
  await expect(page.locator('#wallet-refunds .refund span')).toHaveText(['0.001 ETH from Cancelled request, cancelled']);
  await page.locator('#wallet-refunds .get').click();
  await expect(page.locator('#wallet-status')).toContainText('Taken back into your poidh balance.');
  const tx = await page.evaluate(() => window.walletCalls.find(c => c.method === 'eth_sendTransaction').params[0]);
  expect(tx.to).toBe(POIDH);
  expect(BigInt(tx.value)).toBe(0n);
  expect(tx.data).toBe(`0x240b727e${(602).toString(16).padStart(64, '0')}${SUFFIX}`);
});

test('only the creator sees Cancel bounty, and cancelling carries the builder code', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  const calls = await fakeBase(page);
  await page.addInitScript(fakeWallet, { account: OWNER });
  await page.goto('/depack/wanted/');
  await expect(page.locator('#bounty-600 .get')).toHaveText('Add ETH');
  await page.click('#wallet-button');
  await page.click('#wallet-close');
  // Once the creator's wallet is connected, their open bounties offer Cancel instead of Add ETH.
  await expect(page.locator('#bounty-600 .get')).toHaveText('Cancel bounty');
  await expect(page.locator('#bounty-601 .get')).toHaveCount(0);
  await page.locator('#bounty-600 .get').click();
  await expect(page.locator('#cancel-title')).toHaveText('First aid in Swahili');
  await expect(page.locator('#cancel-what')).toContainText('Your 0.03 ETH goes back to your poidh balance');
  await expect(page.locator('#cancel-what')).toContainText('Others who added ETH can take theirs back');
  await page.click('#cancel-send');
  await expect(page.locator('#cancel-status')).toContainText('Cancelled.');
  const tx = await page.evaluate(() => window.walletCalls.find(c => c.method === 'eth_sendTransaction').params[0]);
  expect(BigInt(tx.value)).toBe(0n);
  expect(tx.chainId).toBe('0x2105');
  expect(tx.data).toBe(`0xd496ec7a${(600).toString(16).padStart(64, '0')}${SUFFIX}`);
  expect(calls.some(c => c.method === 'eth_call' && c.params[0].data === tx.data)).toBe(true);
});

test('the creator withdraws their poidh balance from the wallet panel', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet, { account: OWNER });
  const ensCalls = [];
  await page.route('https://ethereum-rpc.publicnode.com/**', route => {
    ensCalls.push(route.request().postDataJSON());
    route.fulfill({ contentType: 'application/json', body: JSON.stringify({ jsonrpc: '2.0', id: 1, result: poidh.ens }) });
  });
  await page.goto('/depack/wanted/');
  await page.click('#wallet-button');
  await expect(page.locator('#wallet-label')).toHaveText('example.eth');
  await expect(page.locator('#wallet-title')).toHaveText('example.eth');
  await expect(page.locator('#wallet-address')).toHaveText('0xc698…c758');
  expect(ensCalls[0].method).toBe('eth_call');
  expect(ensCalls[0].params[0].to).toBe('0xeeeeeeee14d718c2b47d9923deab1335e144eeee');
  expect(ensCalls[0].params[0].data).toBe(`0x5d78a217${'40'.padStart(64, '0')}${'3c'.padStart(64, '0')}${'14'.padStart(64, '0')}${OWNER.slice(2).toLowerCase().padEnd(64, '0')}`);
  await expect(page.locator('#wallet-balance')).toHaveText('0.005 ETH');
  await expect(page.locator('#wallet-locked')).toHaveText('0.04 ETH');
  await expect(page.locator('#wallet-send')).toHaveText('Withdraw 0.005 ETH');
  await page.click('#wallet-send');
  await expect(page.locator('#wallet-status')).toContainText('Withdrawn to your wallet.');
  const tx = await page.evaluate(() => window.walletCalls.find(c => c.method === 'eth_sendTransaction').params[0]);
  expect(tx.data).toBe(`0x3ccfd60b${SUFFIX}`);
  expect(BigInt(tx.value)).toBe(0n);
});

// namehash('example.base.eth'), from cast.
const EXAMPLE_BASE_NODE = '544fb56f7a5c9c55ad3c84d026096291b12b005763e17b3534d36380c0417d8b';
const ensReply = (page, name) => page.route('https://ethereum-rpc.publicnode.com/**', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ jsonrpc: '2.0', id: 1, result: name ? poidh.ens : poidh.ensNone }) }));

test('a wallet with a Basename shows it ahead of its ENS name, once the name points back to the wallet', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  const calls = await fakeBase(page, { basename: { owner: OWNER, name: 'example.base.eth', pointsTo: OWNER } });
  await ensReply(page, true);
  await page.addInitScript(fakeWallet, { account: OWNER });
  await page.goto('/depack/wanted/');
  await page.click('#wallet-button');
  await expect(page.locator('#wallet-label')).toHaveText('example.base.eth');
  await expect(page.locator('#wallet-title')).toHaveText('example.base.eth');
  await expect(page.locator('#wallet-address')).toHaveText('0xc698…c758');
  const reads = calls.filter(c => c.method === 'eth_call').map(c => c.params[0]);
  expect(reads.find(r => r.data.startsWith('0x4ec3bd23')).to).toBe('0x0000000000d8e504002cc26e3ec46d81971c1664');
  expect(reads.find(r => r.data.startsWith('0x0178b8bf'))).toEqual({ to: '0xb94704422c2a1e396835a571837aa5ae53285a95', data: `0x0178b8bf${EXAMPLE_BASE_NODE}` });
  expect(reads.find(r => r.data.startsWith('0x3b3b57de'))).toEqual({ to: BASENAME_RESOLVER, data: `0x3b3b57de${EXAMPLE_BASE_NODE}` });
});

test('a Basename that points to another wallet is not shown, and the ENS name is used instead', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { basename: { owner: OWNER, name: 'example.base.eth', pointsTo: '0x2222222222222222222222222222222222222222' } });
  await ensReply(page, true);
  await page.addInitScript(fakeWallet, { account: OWNER });
  await page.goto('/depack/wanted/');
  await page.click('#wallet-button');
  await expect(page.locator('#wallet-label')).toHaveText('example.eth');
});

test('a wallet with neither name keeps its short address', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  const calls = await fakeBase(page);
  await page.addInitScript(fakeWallet, { account: OWNER });
  await page.goto('/depack/wanted/');
  await page.click('#wallet-button');
  await expect(page.locator('#wallet-balance')).toHaveText('0.005 ETH');
  await expect.poll(() => calls.some(c => c.method === 'eth_call' && c.params[0].data.startsWith('0x4ec3bd23'))).toBe(true);
  await expect(page.locator('#wallet-label')).toHaveText('0xc698…c758');
  await expect(page.locator('#wallet-address-row')).toBeHidden();
});

const sent = page => page.evaluate(() => window.walletCalls.filter(c => c.method === 'eth_sendTransaction').map(c => c.params[0]));

test('anyone claims a bounty from the page, with the builder code', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.locator('#bounty-600 .onpoidh').click();
  await expect(page.locator('#claims-title')).toHaveText('First aid in Swahili');
  // Visitors see how many claims there are, not what they hold.
  await expect(page.locator('#claims-list .claim')).toHaveCount(0);
  await expect(page.locator('#claims-list')).toContainText('2 claims so far.');
  await expect(page.locator('#claims-list .claim-more')).toHaveCount(0);
  // Without a wallet the dialog asks to connect first; the form follows once the page knows who is claiming.
  await expect(page.locator('#claims-form')).toBeHidden();
  await page.click('#claims-connect');
  await expect(page.locator('#claims-connect')).toBeHidden();
  await expect(page.locator('#claims-form')).toBeVisible();
  // A maker sees their own claim, and only a count of the rest.
  await expect(page.locator('#claims-list .claim b')).toHaveText(['Swahili first aid v1']);
  await expect(page.locator('#claims-list')).toContainText('1 more claim so far.');
  await expect(page.locator('#claims-list .claim-radio')).toHaveCount(0);
  // A title and a description, previewed as it will read; the pack link opens.
  await page.fill('#claim-name', poidh.claim.name);
  await page.click('#claims-send');
  await expect(page.locator('#claims-status')).toHaveText('Say what the pack holds, with its link.');
  expect(await sent(page)).toEqual([]);
  await page.fill('#claim-about', poidh.claim.about);
  await page.locator('#claims .view[data-view="preview"]').click();
  await expect(page.locator('#claim-preview a')).toHaveAttribute('href', 'https://github.com/0x94t3z/fieldatlas/pull/77');
  await expect(page.locator('#claim-preview p').last()).toHaveText('Claimed on https://www.getfieldatlas.com/depack/wanted/');
  await page.locator('#claims .view[data-view="write"]').click();
  await page.click('#claims-send');
  await expect(page.locator('#claims-status')).toContainText('Claimed.');
  // createClaim(bounty, title, description + this page, the site's card as the claim NFT's image), then the suffix.
  const [tx] = await sent(page);
  expect(tx.to).toBe(POIDH);
  expect(BigInt(tx.value)).toBe(0n);
  expect(tx.chainId).toBe('0x2105');
  expect(tx.data).toBe(`${poidh.claim.calldata}${SUFFIX}`);
  // A draft stays with its bounty: another bounty opens an empty form in Write.
  await page.fill('#claim-about', 'draft for 600');
  await page.locator('#claims .view[data-view="preview"]').click();
  await page.click('#claims-close');
  await page.locator('#bounty-603 .onpoidh').click();
  await expect(page.locator('#claim-about')).toHaveValue('');
  await expect(page.locator('#claim-about')).toBeVisible();
});

test('the creator picks one claim and confirms: a vote when others funded the bounty, acceptance when nobody did', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet, { account: OWNER });
  await page.goto('/depack/wanted/');
  await page.click('#wallet-button');
  await page.click('#wallet-close');
  await expect(page.locator('#bounty-600 .onpoidh')).toHaveText('Claims');
  await page.locator('#bounty-600 .onpoidh').click();
  await expect(page.locator('#claims-form')).toBeHidden();
  await expect(page.locator('#claims-list')).toHaveAttribute('role', 'radiogroup');
  await expect(page.locator('#claims-list .claim b')).toHaveText(['Swahili first aid v2', 'Swahili first aid v1']);
  // Only links to the hosts packs come from open; any other link in a claim stays plain text.
  await expect(page.locator('#claim-41 a.claim-link')).toHaveAttribute('href', 'https://github.com/0x94t3z/fieldatlas/pull/40');
  await expect(page.locator('#claim-41 a.claim-link')).toHaveText('github.com/0x94t3z/fieldatlas/pull/40');
  await expect(page.locator('#claim-42 a.claim-link')).toHaveCount(0);
  await expect(page.locator('#claims-list .claim-more')).toHaveCount(1);
  // Nothing can be sent until one claim is picked, and picking only opens a confirm step.
  await expect(page.locator('#claims-decide-send')).toBeDisabled();
  await page.locator('#claim-41 .claim-radio').check();
  await expect(page.locator('#claims-decide-send')).toHaveText('Put this claim to a vote');
  await page.click('#claims-decide-send');
  await expect(page.locator('#claims-confirm-text')).toHaveText('Your 0.03 ETH counts as yes and outweighs the other backers\u2019 0.0013 ETH, so this claim will be paid after two days. No other claim can be decided until the vote ends.');
  expect(await sent(page)).toEqual([]);
  await page.click('#claims-confirm-back');
  await expect(page.locator('#claims-confirm')).toBeHidden();
  await page.click('#claims-decide-send');
  await page.click('#claims-confirm-send');
  await expect(page.locator('#claims-status')).toContainText('The vote is open for two days.');
  expect((await sent(page))[0].data).toBe(`0x9e9251c8${uint(600)}${uint(41)}${SUFFIX}`);
  await page.click('#claims-close');
  await page.locator('#bounty-603 .onpoidh').click();
  await page.locator('#claim-51 .claim-radio').check();
  await expect(page.locator('#claims-decide-send')).toHaveText('Accept this claim');
  await page.click('#claims-decide-send');
  await expect(page.locator('#claims-confirm-text')).toContainText('It cannot be undone.');
  await expect(page.locator('#claims-confirm-send')).toHaveText('Accept and pay');
  await page.click('#claims-confirm-send');
  await expect(page.locator('#claims-status')).toContainText('Accepted.');
  expect((await sent(page))[1].data).toBe(`0x1d260f8e${uint(603)}${uint(51)}${SUFFIX}`);
});

test('the creator sees every claim, also past the ten poidh returns at once', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { manyClaims: { bounty: 600, count: 12 } });
  await page.addInitScript(fakeWallet, { account: OWNER });
  await page.goto('/depack/wanted/');
  await page.click('#wallet-button');
  await page.click('#wallet-close');
  await page.locator('#bounty-600 .onpoidh').click();
  // Newest first, down to the very first claim.
  await expect(page.locator('#claims-list .claim b')).toHaveText(Array.from({ length: 12 }, (_, i) => `Claim ${12 - i}`));
  await expect(page.locator('#claims-list .claim-radio')).toHaveCount(12);
  await page.locator('#claim-1 .claim-radio').check();
  await expect(page.locator('#claims-decide-send')).toBeEnabled();
});

test('claimers and bounty starters show their Basename once it points back to them', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { basename: { owner: '0x2222222222222222222222222222222222222222', name: 'maker.base.eth', pointsTo: '0x2222222222222222222222222222222222222222' } });
  await page.route('**/depack/wanted.json', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ approved: [610] }) }));
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await expect(page.locator('#bounty-610 .byline')).toHaveText('Started by maker.base.eth');
  await page.locator('#bounty-600 .onpoidh').click();
  // Visitors get only the count and nothing to pick.
  await expect(page.locator('#claims-list')).toContainText('2 claims so far.');
  await expect(page.locator('#claims-decide')).toBeHidden();
  await expect(page.locator('#claims-connect')).toBeVisible();
  await expect(page.locator('#claims-form')).toBeHidden();
  // The claimer connects and sees their claim under their Basename.
  await page.click('#claims-connect');
  await expect(page.locator('#claim-41 .claim-who')).toHaveText('maker.base.eth');
  await expect(page.locator('#claim-42')).toHaveCount(0);
});

test('funders vote on the claim in the vote, with the builder code', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await expect(page.locator('#bounty-601 .onpoidh')).toHaveText('See the vote');
  await page.locator('#bounty-601 .onpoidh').click();
  await expect(page.locator('#claims-vote-what')).toHaveText('Indonesian road signs');
  await expect(page.locator('#claims-yes-total')).toHaveText('0.005 ETH');
  await expect(page.locator('#claims-no-total')).toHaveText('0 ETH');
  await expect(page.locator('#claim-5 .claim-state')).toHaveText('In the vote');
  await expect(page.locator('#claims-form')).toBeHidden();
  await expect(page.locator('#claims-settle')).toBeHidden();
  await page.click('#claims-yes');
  await expect(page.locator('#claims-status')).toContainText('Your vote is in.');
  expect((await sent(page))[0].data).toBe(`0xb7c8994d${uint(601)}${uint(1)}${SUFFIX}`);
});

test('a wallet that cannot vote is told why, and nothing is signed', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { reverts: { b7c8994d: '2fb19d77' } });
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.locator('#bounty-601 .onpoidh').click();
  await page.click('#claims-no');
  await expect(page.locator('#claims-status')).toHaveText('Only wallets that added ETH before the vote started can vote.');
  expect(await sent(page)).toEqual([]);
});

test('once the vote has ended anyone can settle it, with the builder code', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.clock.setFixedTime(new Date('2101-01-02T00:00:00Z'));
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.locator('#bounty-601 .onpoidh').click();
  await expect(page.locator('#claims-ends')).toHaveText('Ended');
  await expect(page.locator('#claims-yes')).toBeHidden();
  await page.click('#claims-settle');
  await expect(page.locator('#claims-status')).toContainText('Settled.');
  expect((await sent(page))[0].data).toBe(`0x30f4ccac${uint(601)}${SUFFIX}`);
});

test('a wallet that already allowed the site reconnects without a prompt, and disconnect forgets it', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page);
  await page.addInitScript(() => { try { localStorage.setItem('depack-wallet', 'test.wallet1'); } catch { /* none */ } });
  await page.addInitScript(fakeWallet, { authorized: true });
  await page.goto('/depack/wanted/');
  await expect(page.locator('#wallet-label')).toHaveText('0x2222…2222');
  expect(await page.evaluate(() => window.walletCalls.map(c => c.method))).not.toContain('eth_requestAccounts');
  await page.click('#wallet-button');
  await page.click('#wallet-disconnect');
  await expect(page.locator('#wallet-label')).toHaveText('Connect wallet');
  expect(await page.evaluate(() => localStorage.getItem('depack-wallet'))).toBeNull();
});

test('a cancel the contract refuses is explained in cancel words, and nothing is signed', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { cancelRevert: '0d0d217e' });
  await page.addInitScript(fakeWallet, { account: OWNER });
  await page.goto('/depack/wanted/');
  await page.click('#wallet-button');
  await page.click('#wallet-close');
  await page.locator('#bounty-600 .get').click();
  await page.click('#cancel-send');
  await expect(page.locator('#cancel-status')).toHaveText('Only the wallet that started this bounty can cancel it.');
  expect(await page.evaluate(() => window.walletCalls.map(c => c.method))).not.toContain('eth_sendTransaction');
});
