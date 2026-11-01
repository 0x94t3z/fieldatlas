import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { decodeFunctionData, erc20Abi, getAddress, hashTypedData, recoverTypedDataAddress } from 'viem';
import { privateKeyToAccount } from 'viem/accounts';
import { propose, settleLedger, sweep } from '../cli.mjs';
import { PINNED } from '../rewards.mjs';
import { safeTypedData, transferTx } from '../safe.mjs';

// A throwaway key made for this test; it holds nothing on any chain.
const KEY = '0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d';
const SAFE = PINNED.poolWallet;
const ADDRESS = '0x52908400098527886E0F7030069857D2E4169EE7';
const policy = { branch: 'main', chainId: PINNED.chainId, token: PINNED.token, decimals: PINNED.decimals, poolSize: 1000000000, maxPerTask: 50000000, perPersonMonthly: 50000000, monthlyTotal: 100000000, maintainers: ['0x94t3z'], maintainerIds: [52822242] };
const hash = c => `0x${c.repeat(64)}`;
const reward = (to = ADDRESS, amount = 5000000) => transferTx({ token: PINNED.token, to, amount, decimals: 18, nonce: 0 });
const queued = (over = {}) => ({ safeTxHash: hash('e'), nonce: 4, origin: 'Field Atlas reward for #7', isExecuted: false, to: reward().to, data: reward().data, value: '0', operation: 0, safeTxGas: 0, baseGas: 0, gasPrice: '0',
  gasToken: '0x0000000000000000000000000000000000000000', refundReceiver: '0x0000000000000000000000000000000000000000', submissionDate: '2026-11-19T08:00:00Z', ...over });

async function ledgerFile(rewards = []) {
  const path = join(await mkdtemp(join(tmpdir(), 'rewards-')), 'rewards.json');
  await writeFile(path, JSON.stringify({ about: 'x', poolWallet: SAFE, policy, rewards }, null, 2));
  return path;
}

/** Answers GitHub and the Safe service like the real ones, and records every call. */
function fakeApis({ prBody = `Closes #7\n\nBase address: ${ADDRESS}`, labels = ['reward'], author = 'alice', authorId = 1, closedBy = null,
  safeNonce = 4, queuedTop = [], recent = [], tx = {}, executedAt = {}, merged = [12], commentFails = false, safeDown = false } = {}) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    const body = init.body ? JSON.parse(init.body) : null;
    calls.push({ url, method: init.method || 'GET', body, headers: init.headers });
    const json = (status, data) => ({ ok: status < 300, status, json: async () => data });
    if (url === 'https://api.github.com/graphql') {
      const number = body.variables.number;
      return json(200, { data: { repository: { pullRequest: {
        number, url: `https://github.com/0x94t3z/fieldatlas/pull/${number}`, merged: true, baseRefName: 'main', body: prBody,
        author: { login: author, databaseId: authorId },
        files: { totalCount: 1, nodes: [{ path: 'docs/fieldtest/device-timings/pixel-8a.md', changeType: 'MODIFIED' }] },
        closingIssuesReferences: { totalCount: 1, nodes: [{ number: 7, title: 'Time answers on a 4 GB phone', url: 'https://github.com/0x94t3z/fieldatlas/issues/7', body: 'Reward: 5,000,000 ATLAS',
          state: 'CLOSED', author: { login: '0x94t3z', databaseId: 52822242 }, labels: { nodes: labels.map(name => ({ name })) }, assignees: { nodes: [{ login: 'alice' }] },
          timelineItems: { nodes: [{ closer: { url: `https://github.com/0x94t3z/fieldatlas/pull/${closedBy ?? number}` } }] } }] },
      } } } });
    }
    if (url.startsWith('https://api.github.com/search/issues')) return json(200, { items: merged.map(number => ({ number })) });
    if (url.includes('/comments')) return commentFails ? json(502, {}) : json(201, {});
    if (url.includes('/tx-service/') && safeDown) return json(503, {});
    if (url.endsWith(`/api/v1/safes/${SAFE}/`)) return json(200, { nonce: safeNonce });
    if (url.includes(`/api/v1/safes/${SAFE}/multisig-transactions/?executed=false&ordering=-nonce&limit=1`)) return json(200, { results: queuedTop });
    if (url.includes(`/api/v1/safes/${SAFE}/multisig-transactions/?ordering=-nonce&limit=100`)) return json(200, { results: recent });
    const at = url.match(/multisig-transactions\/\?nonce=(\d+)&executed=true/);
    if (at) return json(200, { results: executedAt[at[1]] ? [{ isExecuted: true, safeTxHash: executedAt[at[1]], nonce: Number(at[1]), safe: SAFE }] : [] });
    if (url.endsWith(`/api/v1/safes/${SAFE}/multisig-transactions/`) && init.method === 'POST') return json(201, null);
    const lookup = url.match(/multisig-transactions\/(0x[0-9a-f]+)\/$/);
    if (lookup) return tx[lookup[1]] === 404 ? json(404, {}) : json(200, tx[lookup[1]]);
    throw new Error(`unexpected call ${url}`);
  };
  return { fetch, calls };
}

const env = (fetch, over = {}) => ({ fetch, GITHUB_TOKEN: 't', GITHUB_REPOSITORY: '0x94t3z/fieldatlas', PR_NUMBER: '12', REWARD_PROPOSER_KEY: KEY, SAFE_API_KEY: 'k', CLOSE_WAIT_MS: '0', ...over });
const posts = calls => calls.filter(call => call.method === 'POST' && call.url.includes('/tx-service/'));
const comments = calls => calls.filter(call => call.url.includes('/comments'));
const rowsOf = async path => JSON.parse(await readFile(path, 'utf8')).rewards;

test('a passing pull request is proposed to the Safe, signed by the proposer, recorded and announced', async () => {
  const path = await ledgerFile();
  const { fetch, calls } = fakeApis({ queuedTop: [{ nonce: 6 }] });
  const result = await propose({ env: env(fetch), ledgerPath: path, today: '2026-11-20' });
  assert.equal(result.outcome, 'proposed');
  const [post] = posts(calls);
  assert.equal(post.url, `https://api.safe.global/tx-service/base/api/v1/safes/${SAFE}/multisig-transactions/`);
  assert.equal(post.headers.Authorization, 'Bearer k');
  // After the queued nonce 6, not the executed nonce 4.
  assert.equal(post.body.nonce, '7');
  assert.equal(post.body.origin, 'Field Atlas reward for #7');
  assert.equal(post.body.to, getAddress(policy.token));
  assert.equal(post.body.value, '0');
  assert.equal(post.body.operation, 0);
  const transfer = decodeFunctionData({ abi: erc20Abi, data: post.body.data });
  assert.deepEqual(transfer.args, [ADDRESS, 5000000n * 10n ** 18n]);
  const typed = safeTypedData({ safe: SAFE, chainId: 8453, tx: { ...post.body, value: 0n, safeTxGas: 0n, baseGas: 0n, gasPrice: 0n, nonce: 7n, operation: 0 } });
  assert.equal(post.body.contractTransactionHash, hashTypedData(typed));
  assert.equal(post.body.sender, privateKeyToAccount(KEY).address);
  assert.equal(await recoverTypedDataAddress({ ...typed, signature: post.body.signature }), post.body.sender);
  assert.deepEqual(await rowsOf(path), [{ date: '2026-11-20', contributor: 'alice', contributorId: 1, work: 'Time answers on a 4 GB phone', link: 'https://github.com/0x94t3z/fieldatlas/pull/12', issue: 7, amount: 5000000, address: ADDRESS, status: 'proposed', safeTxHash: post.body.contractTransactionHash, nonce: 7, tx: null }]);
  assert.match(comments(calls)[0].body.body, /^Reward queued: 5,000,000 ATLAS to `0x5290/);
  assert.ok(!JSON.stringify(calls).includes(KEY.slice(2)), 'the key never leaves the process');
});

test('a reward already queued or paid for the issue, paying exactly this entry, is recorded and never queued twice', async () => {
  // Audit: the comment or the push failed after the Safe accepted the proposal, and the check is run again.
  for (const isExecuted of [false, true]) {
    const path = await ledgerFile();
    const { fetch, calls } = fakeApis({ recent: [queued({ isExecuted })] });
    assert.equal((await propose({ env: env(fetch), ledgerPath: path, today: '2026-11-20' })).outcome, 'already-queued');
    assert.equal(posts(calls).length, 0);
    const [r] = await rowsOf(path);
    // Dated when it was queued, not when the run found it.
    assert.deepEqual([r.safeTxHash, r.nonce, r.date], [hash('e'), 4, '2026-11-19']);
  }
});

test('a reward recovered from an earlier month is checked against that month, so it cannot overfill it', async () => {
  const full = { date: '2026-10-05', contributor: 'carol', issue: 3, amount: 99000000, address: '0x000000000000000000000000000000000000dEaD', link: 'https://github.com/0x94t3z/fieldatlas/pull/3', status: 'paid', safeTxHash: hash('a'), nonce: 1, tx: hash('b') };
  const path = await ledgerFile([full]);
  const { fetch, calls } = fakeApis({ recent: [queued({ submissionDate: '2026-10-31T23:00:00Z' })] });
  await assert.rejects(propose({ env: env(fetch), ledgerPath: path, today: '2026-11-01' }), /queued on 2026-10-31, which breaks that month's rules/);
  assert.equal(posts(calls).length, 0);
  assert.equal((await rowsOf(path)).length, 1);
});

test('a transaction labelled for the issue with a gas refund, a delegatecall or any other extra stops the run', async () => {
  const other = '0x000000000000000000000000000000000000dEaD';
  for (const over of [{ refundReceiver: other }, { gasToken: PINNED.token }, { gasPrice: '1' }, { baseGas: 21000 }, { safeTxGas: 1 }, { operation: 1 }, { value: '1' }, { refundReceiver: null }]) {
    const path = await ledgerFile();
    const { fetch, calls } = fakeApis({ recent: [queued(over)] });
    await assert.rejects(propose({ env: env(fetch), ledgerPath: path, today: '2026-11-20' }), /pays someone or something else/, JSON.stringify(over));
    assert.equal(posts(calls).length, 0);
    assert.deepEqual(await rowsOf(path), []);
  }
});

test('a transaction labelled for the issue that pays someone else stops the run instead of being recorded', async () => {
  const path = await ledgerFile();
  const { fetch, calls } = fakeApis({ recent: [queued({ data: reward('0x000000000000000000000000000000000000dEaD').data })] });
  await assert.rejects(propose({ env: env(fetch), ledgerPath: path, today: '2026-11-20' }), /pays someone or something else/);
  assert.equal(posts(calls).length, 0);
  assert.deepEqual(await rowsOf(path), []);
  assert.match(comments(calls)[0].body.body, /was not queued/);
});

test('reward transactions the ledger does not list stop new proposals, because the limits come from the ledger', async () => {
  const { fetch, calls } = fakeApis({ recent: [queued({ origin: 'Field Atlas reward for #8', safeTxHash: hash('f') })] });
  await assert.rejects(propose({ env: env(fetch), ledgerPath: await ledgerFile(), today: '2026-11-20' }), /does not list \(#8\)/);
  assert.equal(posts(calls).length, 0);
});

test('a rejected earlier proposal, or one whose nonce has passed, does not block a retry', async () => {
  const cancelled = { date: '2026-11-10', contributor: 'alice', issue: 7, amount: 5000000, address: ADDRESS, link: 'https://github.com/0x94t3z/fieldatlas/pull/12', status: 'cancelled', safeTxHash: hash('e'), nonce: 2, tx: null };
  const a = fakeApis({ recent: [queued({ nonce: 2 })] });
  assert.equal((await propose({ env: env(a.fetch), ledgerPath: await ledgerFile([cancelled]), today: '2026-11-20' })).outcome, 'proposed');
  const b = fakeApis({ safeNonce: 5, recent: [queued({ nonce: 3, origin: 'Field Atlas reward for #8', safeTxHash: hash('f') })] });
  assert.equal((await propose({ env: env(b.fetch), ledgerPath: await ledgerFile(), today: '2026-11-20' })).outcome, 'proposed');
});

test('a failing comment does not stop the reward from being recorded', async () => {
  const path = await ledgerFile();
  const { fetch } = fakeApis({ commentFails: true });
  assert.equal((await propose({ env: env(fetch), ledgerPath: path, today: '2026-11-20' })).outcome, 'proposed');
  assert.equal((await rowsOf(path)).length, 1);
});

test('when the Safe service fails, the contributor is told and nothing is recorded', async () => {
  const path = await ledgerFile();
  const { fetch, calls } = fakeApis({ safeDown: true });
  await assert.rejects(propose({ env: env(fetch), ledgerPath: path, today: '2026-11-20' }), /503/);
  assert.match(comments(calls)[0].body.body, /was not queued/);
  assert.deepEqual(await rowsOf(path), []);
});

test('without a key, or on a dry run, nothing is proposed or recorded', async () => {
  for (const [over, why] of [[{ REWARD_PROPOSER_KEY: '' }, /REWARD_PROPOSER_KEY/], [{ DRY_RUN: 'true' }, /dry run/]]) {
    const path = await ledgerFile();
    const { fetch, calls } = fakeApis();
    assert.equal((await propose({ env: env(fetch, over), ledgerPath: path, today: '2026-11-20' })).outcome, 'dry-run');
    assert.match(comments(calls)[0].body.body, why);
    assert.ok(!calls.some(call => call.url.includes('/tx-service/')));
    assert.deepEqual(await rowsOf(path), []);
  }
});

test('a failing pull request gets a comment with the reasons, how to rerun, and no proposal', async () => {
  const { fetch, calls } = fakeApis({ prBody: 'Closes #7' });
  assert.equal((await propose({ env: env(fetch), ledgerPath: await ledgerFile(), today: '2026-11-20' })).outcome, 'rejected');
  const body = comments(calls)[0].body.body;
  assert.match(body, /reward issue #7, but its reward can't be queued:\n\n- The description has no valid "Base address/);
  assert.match(body, /"Dry run" unticked/);
  assert.ok(!calls.some(call => call.url.includes('/tx-service/')));
});

test('a reward issue closed by another pull request is not paid to this one', async () => {
  const { fetch, calls } = fakeApis({ closedBy: 40 });
  assert.equal((await propose({ env: env(fetch), ledgerPath: await ledgerFile(), today: '2026-11-20' })).outcome, 'rejected');
  assert.match(comments(calls)[0].body.body, /was not closed by this pull request's merge/);
});

test('payment lines without a linked reward issue get a note; an ordinary pull request gets nothing', async () => {
  const lines = fakeApis({ labels: ['bug'] });
  assert.equal((await propose({ env: env(lines.fetch), ledgerPath: await ledgerFile(), today: '2026-11-20' })).outcome, 'skipped');
  assert.match(comments(lines.calls)[0].body.body, /closes no issue labelled `reward` in this repository/);
  const plain = fakeApis({ labels: ['bug'], prBody: 'Fix a typo' });
  await propose({ env: env(plain.fetch), ledgerPath: await ledgerFile(), today: '2026-11-20' });
  assert.equal(plain.calls.length, 1);
});

test("the maintainer's own pull request is skipped without a comment", async () => {
  const { fetch, calls } = fakeApis({ author: '0x94t3z', authorId: 52822242 });
  assert.equal((await propose({ env: env(fetch), ledgerPath: await ledgerFile(), today: '2026-11-20' })).outcome, 'skipped');
  assert.equal(calls.length, 1);
});

test('the sweep only records rewards already queued; it never queues, comments, or retries a refusal', async () => {
  const paid = { date: '2026-11-10', contributor: 'bob', issue: 5, amount: 5000000, address: '0x000000000000000000000000000000000000dEaD', link: 'https://github.com/0x94t3z/fieldatlas/pull/13', status: 'paid', safeTxHash: hash('f'), nonce: 1, tx: hash('1') };
  const recovered = fakeApis({ merged: [12, 13], recent: [queued()] });
  const path = await ledgerFile([paid]);
  const result = await sweep({ env: env(recovered.fetch), ledgerPath: path, today: '2026-11-20' });
  assert.deepEqual([result.changed, result.needsRun], [1, []]);
  assert.equal((await rowsOf(path)).length, 2);
  assert.equal(posts(recovered.calls).length + comments(recovered.calls).length, 0);
  // Merged, passes the check, but nothing queued: listed for a maintainer, not queued by the sweep.
  const missed = fakeApis({ merged: [12] });
  const summary = join(await mkdtemp(join(tmpdir(), 'summary-')), 'summary.md');
  const listed = await sweep({ env: env(missed.fetch, { GITHUB_STEP_SUMMARY: summary }), ledgerPath: await ledgerFile(), today: '2026-11-20' });
  assert.deepEqual(listed.needsRun, [12]);
  assert.match(await readFile(summary, 'utf8'), /never queued: #12/);
  assert.equal(posts(missed.calls).length + comments(missed.calls).length, 0);
});

test('the sweep lists a payout rejected in the Safe apart, and fails the run when a pull request cannot be checked', async () => {
  const rejected = { date: '2026-11-10', contributor: 'alice', contributorId: 1, issue: 7, amount: 5000000, address: ADDRESS, link: 'https://github.com/0x94t3z/fieldatlas/pull/12', status: 'cancelled', safeTxHash: hash('a'), nonce: 2, tx: null };
  const a = fakeApis({ merged: [12] });
  const summary = join(await mkdtemp(join(tmpdir(), 'summary-')), 'summary.md');
  const result = await sweep({ env: env(a.fetch, { GITHUB_STEP_SUMMARY: summary }), ledgerPath: await ledgerFile([rejected]), today: '2026-11-20' });
  assert.deepEqual([result.needsRun, result.refusedBefore, result.problems], [[], [12], 0]);
  assert.match(await readFile(summary, 'utf8'), /earlier payout was rejected in the Safe or failed: #12/);
  assert.doesNotMatch(await readFile(summary, 'utf8'), /never queued/);
  const b = fakeApis({ merged: [12] });
  const githubDown = async (url, init) => url.endsWith('/graphql') ? { ok: false, status: 502, json: async () => ({}) } : b.fetch(url, init);
  assert.equal((await sweep({ env: env(githubDown), ledgerPath: await ledgerFile(), today: '2026-11-20' })).problems, 1);
});

test('settling reports lookups that failed, so the run fails instead of passing quietly', async () => {
  const row = { date: '2026-11-20', contributor: 'alice', issue: 7, amount: 5000000, address: ADDRESS, link: 'l7', status: 'proposed', safeTxHash: hash('a'), nonce: 7, tx: null };
  const { fetch } = fakeApis({ safeNonce: 7, tx: { [hash('a')]: undefined } });
  const broken = async (url, init) => url.includes(`/multisig-transactions/${hash('a')}/`) ? { ok: false, status: 429, json: async () => ({}) } : fetch(url, init);
  assert.deepEqual(await settleLedger({ env: env(broken), ledgerPath: await ledgerFile([row]) }), { changed: 0, problems: 1 });
});

test('the Safe and the merged pull requests are read past their first page', async () => {
  const { fetch } = fakeApis({ merged: [] });
  const seen = [];
  const stray = queued({ origin: 'Field Atlas reward for #9', safeTxHash: hash('f'), nonce: 5 });
  const paged = async (url, init) => {
    seen.push(url);
    if (url.includes('multisig-transactions/?ordering=-nonce&limit=100&offset=0')) return { ok: true, status: 200, json: async () => ({ next: 'more', results: [] }) };
    if (url.includes('multisig-transactions/?ordering=-nonce&limit=100&offset=100')) return { ok: true, status: 200, json: async () => ({ next: null, results: [stray] }) };
    if (url.includes('/search/issues') && url.endsWith('&page=1')) return { ok: true, status: 200, json: async () => ({ items: Array.from({ length: 100 }, (_, i) => ({ number: 1000 + i })) }) };
    if (url.includes('/search/issues') && url.endsWith('&page=2')) return { ok: true, status: 200, json: async () => ({ items: [] }) };
    return fetch(url, init);
  };
  const path = await ledgerFile(Array.from({ length: 100 }, (_, i) => ({ date: '2026-01-01', contributor: 'x', issue: 1000 + i, amount: 1, address: ADDRESS,
    link: `https://github.com/0x94t3z/fieldatlas/pull/${1000 + i}`, status: 'paid', safeTxHash: hash('c'), nonce: 0, tx: hash('d') })));
  const result = await sweep({ env: env(paged), ledgerPath: path, today: '2026-11-20' });
  assert.deepEqual(result.unlisted, [hash('f')]);
  assert.ok(seen.some(url => url.includes('/search/issues') && url.endsWith('&page=2')));
});

test('the sweep lists reward transactions in the Safe that the ledger does not list, even when no pull request passes', async () => {
  const stray = queued({ origin: 'Field Atlas reward for #9', safeTxHash: hash('f'), nonce: 5 });
  const { fetch, calls } = fakeApis({ merged: [], recent: [stray, queued({ origin: 'Field Atlas reward for #3', safeTxHash: hash('d'), nonce: 2 })] });
  const summary = join(await mkdtemp(join(tmpdir(), 'summary-')), 'summary.md');
  const result = await sweep({ env: env(fetch, { GITHUB_STEP_SUMMARY: summary }), ledgerPath: await ledgerFile(), today: '2026-11-20' });
  // The nonce 2 one can never run, so it is not listed.
  assert.deepEqual(result.unlisted, [hash('f')]);
  assert.match(await readFile(summary, 'utf8'), /Field Atlas reward for #9 \(nonce 5, queued/);
  assert.equal(posts(calls).length + comments(calls).length, 0);
});

test('settling writes paid rows, keeps going past a missing one, and cancels only a replaced one', async () => {
  const rows = [
    { date: '2026-11-20', contributor: 'alice', issue: 7, amount: 5000000, address: ADDRESS, link: 'l7', status: 'proposed', safeTxHash: hash('a'), nonce: 7, tx: null },
    { date: '2026-11-20', contributor: 'bob', issue: 8, amount: 5000000, address: ADDRESS, link: 'l8', status: 'proposed', safeTxHash: hash('c'), nonce: 6, tx: null },
    { date: '2026-11-20', contributor: 'eve', issue: 9, amount: 5000000, address: ADDRESS, link: 'l9', status: 'proposed', safeTxHash: hash('d'), nonce: 5, tx: null },
  ];
  const path = await ledgerFile(rows);
  const { fetch } = fakeApis({ safeNonce: 8, executedAt: { 5: hash('9') },
    tx: { [hash('a')]: { isExecuted: true, isSuccessful: true, transactionHash: hash('b'), nonce: 7 }, [hash('c')]: 404, [hash('d')]: { isExecuted: false, isSuccessful: null, nonce: 5 } } });
  assert.deepEqual(await settleLedger({ env: env(fetch), ledgerPath: path }), { changed: 2 });
  const after = await rowsOf(path);
  assert.deepEqual(after.map(r => [r.issue, r.status]), [[7, 'paid'], [8, 'proposed'], [9, 'cancelled']]);
  assert.equal(after[0].tx, hash('b'));
});
