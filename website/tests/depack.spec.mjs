import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const poidh = JSON.parse(readFileSync(new URL('./fixtures/poidh-base.json', import.meta.url), 'utf8'));
const POIDH = '0x5555fa783936c260f77385b4e153b9725fef1719';
const SUFFIX = '62635f64696579366f69670b0080218021802180218021802180218021';

// Answers the page's Base RPC calls from the fixture, by function selector, and records every call.
async function fakeBase(page, { joinRevert = null, joinError = null, createRevert = null, cancelRevert = null, byUser = poidh.byUser, code = {} } = {}) {
  const calls = [];
  await page.route('https://mainnet.base.org/**', async route => {
    const body = route.request().postDataJSON();
    const replies = body.map(({ id, method, params }) => {
      calls.push({ method, params });
      if (method === 'eth_getTransactionReceipt') return { jsonrpc: '2.0', id, result: { status: '0x1' } };
      // Wallets have no code unless a test gives one.
      if (method === 'eth_getCode') return { jsonrpc: '2.0', id, result: code[params[0].toLowerCase()] ?? '0x' };
      const data = params[0].data;
      const selector = data.slice(2, 10);
      const arg = data.length >= 74 ? String(BigInt(`0x${data.slice(10, 74)}`)) : '';
      const pick = table => (table[arg] ?? table.default);
      if (selector === '2b9234b9') {
        // userBounties(issuer, index): an index past the end reverts, as on chain.
        const index = Number(BigInt(`0x${data.slice(74, 138)}`));
        return index < poidh.userBounties.length ? { jsonrpc: '2.0', id, result: `0x${poidh.userBounties[index].toString(16).padStart(64, '0')}` }
          : { jsonrpc: '2.0', id, error: { code: 3, message: 'execution reverted' } };
      }
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
  await expect(page.locator('#bounty-600 .line')).toHaveText('0.0313 ETH · 2 people');
  await expect(page.locator('#bounty-590 .line')).toHaveText('0.012 ETH · 2 people');
  await expect(page.locator('#bounty-601 .line')).toContainText('voting until');
  // A solo bounty takes no ETH from others; voting and paid ones take none either.
  await expect(page.locator('#bounty-603 .get, #bounty-601 .get, #bounty-590 .get')).toHaveCount(0);
  await expect(page.locator('#bounty-603 .onpoidh')).toHaveText('Make it');
  // poidh's site numbers Base bounties 986 after the contract's own numbers.
  await expect(page.locator('#bounty-600 .onpoidh')).toHaveAttribute('href', 'https://poidh.xyz/base/bounty/1586');

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

test('a wallet with more than ten bounties still shows the older ones made for the page', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain');
  await page.unroute('https://mainnet.base.org/**');
  await fakeBase(page, { byUser: poidh.byUserFull });
  await page.goto('/depack/wanted/');
  // The newest ten are someone else's kind of bounty; the page's own one is eleventh from the top.
  await expect(page.locator('#open-shelf h3')).toHaveText(['Sign language basics']);
  await expect(page.locator('#bounty-650 .line')).toHaveText('0.007 ETH · 1 person');
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
  await expect(page.locator('#start-send')).toHaveText('Start it with 0.005 ETH');
  // Below the contract's 0.001 ETH minimum the button stays off; a name and a description are required.
  await page.fill('#start-amount', '0.0005');
  await expect(page.locator('#start-send')).toBeDisabled();
  await page.locator('#start .preset[data-eth="0.005"]').click();
  await page.click('#start-send');
  await expect(page.locator('#start-status')).toHaveText('Give the collection a name and say what it should cover.');
  await page.fill('#start-name', poidh.create.name);
  await page.fill('#start-about', poidh.create.about);
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

test('a smart-contract wallet is told plainly that poidh needs a regular wallet to start a bounty', async ({ page }) => {
  test.skip(!!process.env.SITE_URL, 'uses a fake chain and wallet');
  await page.unroute('https://mainnet.base.org/**');
  // A simulated call cannot show this refusal, so the page reads the wallet's code: contract code is refused.
  await fakeBase(page, { code: { '0x2222222222222222222222222222222222222222': '0x6080604052' } });
  await page.addInitScript(fakeWallet);
  await page.goto('/depack/wanted/');
  await page.click('#start-open');
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
