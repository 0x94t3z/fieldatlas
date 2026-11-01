import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PINNED, baseAddress, decide, recordProposal, rewardAmount, settle } from '../rewards.mjs';

const policy = { branch: 'main', chainId: PINNED.chainId, token: PINNED.token, decimals: PINNED.decimals, poolSize: 1000000000, maxPerTask: 50000000, perPersonMonthly: 50000000, monthlyTotal: 100000000, maintainers: ['0x94t3z'], maintainerIds: [52822242] };
const ADDRESS = '0x52908400098527886E0F7030069857D2E4169EE7';
const issue = (over = {}) => ({ number: 7, title: 'Time answers on a 4 GB phone', url: 'https://github.com/0x94t3z/fieldatlas/issues/7', body: 'Accepted when: the export is attached.\n\nReward: 5,000,000 ATLAS\n', author: '0x94t3z', authorId: 52822242, labels: ['reward'], assignees: ['alice'], state: 'CLOSED', closedBy: ['https://github.com/0x94t3z/fieldatlas/pull/12'], ...over });
const pr = (over = {}) => ({ number: 12, url: 'https://github.com/0x94t3z/fieldatlas/pull/12', merged: true, baseRef: 'main', author: 'alice', authorId: 1, body: `Closes #7\n\nBase address: ${ADDRESS}\n`, files: ['docs/fieldtest/device-timings/pixel-8a.md'], filesTotal: 1, renamed: [], issues: [issue()], issuesTotal: 1, ...over });
const ledger = (rewards = [], over = {}) => ({ poolWallet: PINNED.poolWallet, policy, rewards, ...over });
const today = '2026-11-20';
const reasons = (over, rows = [], ledgerOver = {}, policyOver = {}) =>
  decide({ pr: pr(over), ledger: ledger(rows, ledgerOver), policy: { ...policy, ...policyOver }, today }).reasons || [];
const row = (over = {}) => ({ date: '2026-11-02', contributor: 'carol', issue: 3, amount: 5000000, address: '0x000000000000000000000000000000000000dEaD', link: 'https://github.com/0x94t3z/fieldatlas/pull/3', ...over });

test('reads the amount from the reward line only', () => {
  assert.equal(rewardAmount('Reward: 5,000,000 ATLAS'), 5000000);
  assert.equal(rewardAmount('text\n  Reward: 250_000 ATLAS  \nmore'), 250000);
  assert.equal(rewardAmount('Reward: 5 million ATLAS'), null);
  assert.equal(rewardAmount('Reward: 0 ATLAS'), null);
  assert.equal(rewardAmount('A reward: 5,000 ATLAS for this'), null);
  assert.equal(rewardAmount(null), null);
});

test('reads a checksummed Base address and refuses a mistyped one', () => {
  assert.equal(baseAddress(`Base address: ${ADDRESS.toLowerCase()}`), ADDRESS);
  assert.equal(baseAddress(`Base address: \`${ADDRESS}\``), ADDRESS);
  assert.equal(baseAddress(`Base address: ${ADDRESS.replace('E7', 'e7')}`), null);
  assert.equal(baseAddress('Base address: 0x...'), null);
  assert.equal(baseAddress(`my wallet ${ADDRESS}`), null);
});

test('lines hidden in comments or code blocks never count, and two different addresses pay nobody', () => {
  const other = '0x0000000000000000000000000000000000000001';
  assert.equal(baseAddress(`<!--\nBase address: ${other}\n-->\nBase address: ${ADDRESS}`), ADDRESS);
  assert.equal(baseAddress(`\`\`\`\nBase address: ${other}\n\`\`\`\nBase address: ${ADDRESS}`), ADDRESS);
  assert.equal(baseAddress(`\`\`\`\nBase address: ${ADDRESS}\n\`\`\``), null);
  assert.equal(baseAddress(`Base address: ${ADDRESS}\nBase address: 0x000000000000000000000000000000000000dEaD`), null);
  assert.equal(baseAddress(`Base address: ${ADDRESS}\nBase address: ${ADDRESS.toLowerCase()}`), ADDRESS);
  assert.equal(rewardAmount('<!-- Reward: 50,000,000 ATLAS -->\nReward: 5,000,000 ATLAS'), 5000000);
  assert.equal(rewardAmount('<!-- Reward: 50,000,000 ATLAS -->'), null);
});

test('a line counts only as markdown shows it: longer fences, indented code, CRLF and differing amounts', () => {
  const other = '0x0000000000000000000000000000000000000001';
  // A four-backtick fence is not closed by a three-backtick line inside it.
  assert.equal(baseAddress(`\`\`\`\`\n\`\`\`\nBase address: ${other}\n\`\`\`\`\nBase address: ${ADDRESS}`), ADDRESS);
  assert.equal(baseAddress(`~~~\nBase address: ${other}\n~~~\nBase address: ${ADDRESS}`), ADDRESS);
  assert.equal(baseAddress(`Intro.\n\n    Base address: ${other}\n\nBase address: ${ADDRESS}`), ADDRESS);
  assert.equal(baseAddress(`Intro.\n\n\tBase address: ${ADDRESS}`), null);
  assert.equal(baseAddress(`Intro.\n    Base address: ${ADDRESS}`), null);
  assert.equal(baseAddress(`Closes #7\r\n\r\nBase address: ${ADDRESS}\r\n`), ADDRESS);
  assert.equal(rewardAmount('Reward: 5,000,000 ATLAS\nReward: 50,000,000 ATLAS'), null);
  assert.equal(rewardAmount('```\nReward: 50,000,000 ATLAS\n```\nReward: 5,000,000 ATLAS'), 5000000);
});

test('limits in the ledger that are not whole numbers stop every proposal instead of switching off', () => {
  for (const bad of [{ maxPerTask: '50,000,000' }, { perPersonMonthly: undefined }, { monthlyTotal: 0 }, { poolSize: 1.5 }]) {
    assert.match(reasons({ issues: [issue({ body: 'Reward: 900,000,000 ATLAS' })] }, [], {}, bad).join(), /limits \(\w+\) are not whole numbers/, JSON.stringify(bad));
  }
});

test('fences inside a list or a quote, and short comment forms, make the body unreadable rather than misread', () => {
  assert.equal(baseAddress(`- \`\`\`\n  Base address: 0x0000000000000000000000000000000000000001\n  \`\`\`\nBase address: ${ADDRESS}`), null);
  assert.equal(baseAddress(`> ~~~\nBase address: ${ADDRESS}`), null);
  // GitHub shows both lines here (the comment ends at once), so which address was meant is unclear.
  assert.equal(baseAddress(`<!-->\nBase address: 0x0000000000000000000000000000000000000001\n-->\nBase address: ${ADDRESS}`), null);
  assert.equal(rewardAmount('<!--->\nReward: 5,000,000 ATLAS'), null);
  assert.equal(baseAddress(`<!--- note -->\nBase address: ${ADDRESS}`), ADDRESS);
  assert.equal(baseAddress(`<!---->\nBase address: ${ADDRESS}`), ADDRESS);
});

test('a pull request too big to read in full is not paid', () => {
  assert.match(reasons({ filesTotal: 150, files: Array.from({ length: 100 }, (_, i) => `docs/f${i}.md`) }).join(), /changes 150 files, more than the check can read/);
  assert.match(reasons({ filesTotal: undefined }).join(), /unknown number of files/);
  assert.match(reasons({ issuesTotal: 11 }).join(), /closes 11 issues, more than the check can read/);
});

test('a rewarded pull request may not rename files, which could hide a change to a protected path', () => {
  assert.match(reasons({ renamed: ['docs/x.md'] }).join(), /renames `docs\/x\.md`/);
});

test("the maintainer's payment test counts against the pool, not against contributors' monthly room", () => {
  const big = { issues: [issue({ body: 'Reward: 50,000,000 ATLAS' })] };
  const test = row({ date: '2026-11-01', contributor: '0x94t3z', issue: 0, amount: 1000, link: 'https://github.com/0x94t3z/fieldatlas/blob/main/CONTRIBUTING.md' });
  assert.equal(decide({ pr: pr(big), ledger: ledger([test, row({ date: '2026-11-05', amount: 50000000 })]), policy, today }).ok, true);
});

test('a pull request that closes no reward issue in this repository is skipped silently', () => {
  assert.deepEqual(decide({ pr: pr({ issues: [issue({ labels: ['bug'] })] }), ledger: ledger(), policy, today }), { skip: true });
  assert.deepEqual(decide({ pr: pr({ issues: [] }), ledger: ledger(), policy, today }), { skip: true });
  // Audit: an issue the maintainer once opened in another repository, edited there to offer 50M, is not an offer here.
  assert.deepEqual(decide({ pr: pr({ issues: [issue({ url: 'https://github.com/evil/x/issues/7', body: 'Reward: 50,000,000 ATLAS' })] }), ledger: ledger(), policy, today }), { skip: true });
});

test('an assigned contributor with an address gets a proposed entry', () => {
  const verdict = decide({ pr: pr(), ledger: ledger(), policy, today });
  assert.equal(verdict.ok, true);
  assert.deepEqual(verdict.entry, { date: today, contributor: 'alice', contributorId: 1, work: 'Time answers on a 4 GB phone', link: 'https://github.com/0x94t3z/fieldatlas/pull/12', issue: 7, amount: 5000000, address: ADDRESS, status: 'proposed', safeTxHash: null, nonce: null, tx: null });
});

test('each rule names what is wrong', () => {
  assert.match(reasons({ merged: false }).join(), /not merged/);
  assert.match(reasons({ baseRef: 'dev' }).join(), /merged into dev/);
  assert.match(reasons({ issues: [issue({ author: 'mallory', authorId: 66 })] }).join(), /not opened by a maintainer/);
  assert.match(reasons({ issues: [issue({ body: 'no amount' })] }).join(), /no "Reward/);
  assert.match(reasons({ issues: [issue({ body: 'Reward: 60,000,000 ATLAS' })] }).join(), /above the 50,000,000 limit/);
  assert.match(reasons({ author: 'bob' }).join(), /@bob is not assigned/);
  assert.match(reasons({ author: '0x94t3z', authorId: 52822242, issues: [issue({ assignees: ['0x94t3z'] })] }).join(), /Maintainers are not paid/);
  assert.match(reasons({ body: 'Closes #7' }).join(), /no valid "Base address/);
  assert.match(reasons({ issues: [issue(), issue({ number: 8, url: 'https://github.com/0x94t3z/fieldatlas/issues/8' })] }).join(), /closes 2 reward issues \(#7, #8\)/);
});

test('the reward issue must have been closed by this pull request when it was merged', () => {
  // Audit: "Closes #7" added to an old merged pull request's description afterwards, or an issue closed by hand.
  assert.match(reasons({ issues: [issue({ closedBy: ['https://github.com/0x94t3z/fieldatlas/pull/40'] })] }).join(), /was not closed by this pull request's merge/);
  assert.match(reasons({ issues: [issue({ state: 'OPEN', closedBy: [] })] }).join(), /was not closed by this pull request's merge/);
  assert.equal(decide({ pr: pr({ issues: [issue({ closedBy: ['https://github.com/0x94t3z/fieldatlas/pull/12'] })] }), ledger: ledger(), policy, today }).ok, true);
});

test('a task is paid to one assignee only', () => {
  assert.match(reasons({ issues: [issue({ assignees: ['alice', 'bob'] })] }).join(), /2 assignees; a task is paid to one person/);
});

test('the payout settings in the ledger must equal the pinned ones', () => {
  // Audit: a rewarded pull request that quietly sets decimals to 20 would turn 5M into 500M real ATLAS.
  assert.match(reasons({}, [], {}, { decimals: 20 }).join(), /payout settings \(decimals\) differ/);
  assert.match(reasons({}, [], {}, { token: '0x0000000000000000000000000000000000000001' }).join(), /\(token\)/);
  assert.match(reasons({}, [], { poolWallet: '0x1111111111111111111111111111111111111111' }).join(), /\(poolWallet\)/);
  assert.match(reasons({}, [], {}, { chainId: 1 }).join(), /\(chainId\)/);
  assert.match(reasons({}, [], {}, { maintainerIds: [1] }).join(), /\(maintainerIds\)/);
  assert.match(reasons({}, [], {}, { branch: 'dev' }).join(), /\(branch\)/);
});

test('a rewarded pull request may not change the ledger, the payout code or the workflows', () => {
  for (const path of ['website/rewards.json', 'tools/rewards/rewards.mjs', '.github/workflows/rewards.yml']) {
    assert.match(reasons({ files: ['docs/x.md', path] }).join(), new RegExp(`It changes \`${path.replace(/\./g, '\\.')}\``));
  }
  assert.deepEqual(reasons({ files: ['website/index.html', 'tools/README.md'] }), []);
});

test('a malformed ledger row stops every proposal instead of switching off the limits', () => {
  // Audit: "50,000,000" as a string made every sum NaN, so every limit check passed.
  assert.match(reasons({}, [row({ amount: '50,000,000' })]).join(), /1 malformed row/);
  assert.match(reasons({}, [row({ issue: '7' })]).join(), /malformed/);
  assert.match(reasons({}, [row({ date: '1 Nov 2026' })]).join(), /malformed/);
});

test('an issue or a pull request is paid once, and cancelled or failed proposals free them again', () => {
  assert.match(reasons({}, [row({ issue: 7, status: 'proposed' })]).join(), /Issue #7 already has a reward queued/);
  assert.match(reasons({}, [row({ issue: 7, status: 'paid' })]).join(), /already has a reward paid/);
  assert.match(reasons({}, [row({ issue: 7 })]).join(), /already has a reward/);
  assert.match(reasons({}, [row({ issue: 99, link: 'https://github.com/0x94t3z/fieldatlas/pull/12' })]).join(), /This pull request already has a reward/);
  assert.equal(decide({ pr: pr(), ledger: ledger([row({ issue: 7, status: 'cancelled' })]), policy, today }).ok, true);
  assert.equal(decide({ pr: pr(), ledger: ledger([row({ issue: 7, status: 'failed' })]), policy, today }).ok, true);
});

test('the zero address, the token and the pool cannot receive a reward', () => {
  for (const bad of ['0x0000000000000000000000000000000000000000', '0xB462A5039a540883508Ee37b5cF3999647557ba3', PINNED.poolWallet]) {
    assert.match(reasons({ body: `Closes #7\n\nBase address: ${bad}` }).join(), /cannot receive a reward/);
  }
});

test('monthly limits per person (by login or address) and in total, and the pool size, hold', () => {
  const big = { issues: [issue({ body: 'Reward: 50,000,000 ATLAS' })] };
  assert.match(reasons(big, [row({ date: '2026-11-03', contributor: 'alice', amount: 1 })]).join(), /take @alice .* over 50,000,000/);
  // Audit: a second account paying to the same address is the same person.
  assert.match(reasons(big, [row({ date: '2026-11-03', contributor: 'alice2', address: ADDRESS, amount: 1 })]).join(), /over 50,000,000 ATLAS this month/);
  assert.equal(decide({ pr: pr(big), ledger: ledger([row({ date: '2026-10-30', contributor: 'alice', address: ADDRESS, amount: 50000000 })]), policy, today }).ok, true);
  assert.match(reasons(big, [row({ date: '2026-11-01', amount: 50000001 })]).join(), /this month's rewards over/);
  // Audit: a renamed account with a new address is still the same account.
  assert.match(reasons(big, [row({ date: '2026-11-03', contributor: 'alice-old', contributorId: 1, amount: 1 })]).join(), /over 50,000,000 ATLAS this month/);
  const spent = Array.from({ length: 20 }, (_, i) => row({ date: '2025-01-01', contributor: `p${i}`, issue: 100 + i, link: `x${i}`, amount: 50000000 }));
  assert.match(reasons({}, spent).join(), /too little left/);
});

test('maintainers are matched by account id', () => {
  const ids = {};
  // A renamed account keeps its id; someone who registers the old login does not get it.
  assert.equal(decide({ pr: pr({ issues: [issue({ author: 'renamed', authorId: 52822242 })] }), ledger: ledger(), policy: { ...policy, ...ids }, today }).ok, true);
  assert.match(reasons({ issues: [issue({ author: '0x94t3z', authorId: 999 })] }, [], {}, ids).join(), /not opened by a maintainer/);
  assert.match(reasons({ author: 'someone', authorId: 52822242, issues: [issue({ authorId: 52822242, assignees: ['someone'] })] }, [], {}, ids).join(), /Maintainers are not paid/);
});

test('settling marks paid and failed only on clear answers, and cancelled only when another transaction took the nonce', () => {
  const hash = n => `0x${String(n).repeat(64)}`;
  const start = ledger([
    { issue: 1, status: 'proposed', safeTxHash: hash(1), nonce: 3, tx: null },
    { issue: 2, status: 'proposed', safeTxHash: hash(2), nonce: 4, tx: null },
    { issue: 3, status: 'proposed', safeTxHash: hash(3), nonce: 5, tx: null },
    { issue: 4, status: 'proposed', safeTxHash: hash(4), nonce: 6, tx: null },
    { issue: 5, status: 'paid', safeTxHash: hash(5), tx: hash(9) },
    { issue: 6, status: 'proposed', safeTxHash: hash(6), nonce: 8, tx: null },
    { issue: 7, status: 'proposed', safeTxHash: hash(7), nonce: 9, tx: null },
  ]);
  const { ledger: next, changed } = settle(start, {
    [hash(1)]: { executed: true, successful: true, txHash: hash('a'), nonce: 3 },
    [hash(2)]: { executed: true, successful: false, txHash: hash('b'), nonce: 4 },
    [hash(3)]: { executed: false, successful: null, txHash: null, nonce: 5 },
    // Audit: the service had not indexed the execution yet; the nonce passed but nothing else ran at it.
    [hash(4)]: { executed: false, successful: null, txHash: null, nonce: 6 },
    // Audit: executed, but the service's success flag is missing: not a failure.
    [hash(6)]: { executed: true, successful: null, txHash: hash('c'), nonce: 8 },
    [hash(7)]: null,
  }, { 5: hash('d'), 6: hash(4) });
  assert.equal(changed, 3);
  assert.deepEqual(next.rewards.map(r => [r.issue, r.status]), [[1, 'paid'], [2, 'failed'], [3, 'cancelled'], [4, 'proposed'], [5, 'paid'], [6, 'proposed'], [7, 'proposed']]);
});

test('recording a proposal appends the row with its Safe hash and nonce', () => {
  const next = recordProposal(ledger(), { issue: 7 }, '0xabc', 4);
  assert.deepEqual(next.rewards, [{ issue: 7, safeTxHash: '0xabc', nonce: 4 }]);
});

test('the published ledger is well formed and pinned', async () => {
  const { readFile } = await import('node:fs/promises');
  const real = JSON.parse(await readFile(new URL('../../../website/rewards.json', import.meta.url), 'utf8'));
  assert.equal(real.poolWallet, PINNED.poolWallet);
  assert.equal(real.policy.chainId, PINNED.chainId);
  assert.equal(real.policy.token.toLowerCase(), PINNED.token.toLowerCase());
  assert.equal(real.policy.decimals, PINNED.decimals);
  for (const r of real.rewards) {
    assert.ok(Number.isSafeInteger(r.amount) && r.amount > 0 && Number.isSafeInteger(r.issue) && /^\d{4}-\d{2}-\d{2}$/.test(r.date), JSON.stringify(r));
  }
});
