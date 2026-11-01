// Run by .github/workflows/rewards.yml.
//   node tools/rewards/cli.mjs propose   checks merged pull request $PR_NUMBER and queues its reward in the Safe
//   node tools/rewards/cli.mjs sweep     records rewards a run queued in the Safe but never wrote to the ledger
//   node tools/rewards/cli.mjs settle    marks queued rewards paid, failed or cancelled from what the Safe reports
// All three edit website/rewards.json; the workflow commits it. Nothing here can move funds: the owners confirm in the Safe.
import { appendFile, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { getAddress, hashTypedData, zeroAddress } from 'viem';
import { privateKeyToAccount } from 'viem/accounts';
import { baseAddress, byMaintainer, decide, recordProposal, rewardIssuesOf, settle } from './rewards.mjs';
import { safeTypedData, transferTx, txService } from './safe.mjs';
import { github } from './github.mjs';

export const LEDGER = fileURLToPath(new URL('../../website/rewards.json', import.meta.url));
// The old safe-transaction-base.safe.global host now answers 308 to this one.
export const DEFAULT_SAFE_SERVICE = 'https://api.safe.global/tx-service/base';
/** How far back the sweep looks for merged pull requests whose queued reward never reached the ledger. */
const SWEEP_DAYS = 30;
const ORIGIN_PREFIX = 'Field Atlas reward for #';

const atlas = amount => `${amount.toLocaleString('en')} ATLAS`;
const readLedger = async path => JSON.parse(await readFile(path, 'utf8'));
const writeLedger = (path, ledger) => writeFile(path, JSON.stringify(ledger, null, 2) + '\n');
const service = env => txService({ baseUrl: env.SAFE_TX_SERVICE || DEFAULT_SAFE_SERVICE, apiKey: env.SAFE_API_KEY, fetch: env.fetch });
const originFor = issue => `${ORIGIN_PREFIX}${issue}`;
const lower = value => String(value || '').toLowerCase();
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const RERUN = 'Once that is fixed, a maintainer can run the check again from the Actions tab (Contributor rewards, with this '
  + 'pull request\'s number and "Dry run" unticked).';

/** A comment that cannot fail the run: the payout and its ledger row matter more than the note on the pull request. */
async function note(gh, number, body, log = console.error) {
  try { await gh.comment(number, body); } catch (error) { log(`Could not comment on #${number}: ${error.message}`); }
}

/** The merged pull request, read again while GitHub is still closing the issue it closes (it does so just after the merge). */
async function mergedPullRequest(gh, number, waitMs) {
  let pr = await gh.pullRequest(number);
  for (let attempt = 0; waitMs > 0 && attempt < 4 && pr.merged && rewardIssuesOf(pr).some(issue => issue.state === 'OPEN'); attempt++) {
    await sleep(waitMs);
    pr = await gh.pullRequest(number);
  }
  return pr;
}

/**
 * Checks one merged pull request and queues its reward. recover (the sweep) never queues anything new and never
 * comments: it only records a reward that a failed run already queued in the Safe.
 */
export async function propose({ env, ledgerPath = LEDGER, today = new Date().toISOString().slice(0, 10), number = Number(env.PR_NUMBER), recover = false }) {
  const ledger = await readLedger(ledgerPath);
  const policy = ledger.policy;
  const gh = github({ token: env.GITHUB_TOKEN, repo: env.GITHUB_REPOSITORY, fetch: env.fetch });
  const pr = await mergedPullRequest(gh, number, recover ? 0 : Number(env.CLOSE_WAIT_MS ?? 15000));
  if (byMaintainer(pr, policy)) return { outcome: 'skipped' };
  const verdict = decide({ pr, ledger, policy, today });
  if (verdict.skip) {
    // Payment details but no linked reward issue: usually "Closes #N" left inside a comment or misspelt.
    if (!recover && pr.merged && baseAddress(pr.body) && rewardIssuesOf(pr).length === 0) {
      await note(gh, pr.number, 'This pull request has a "Base address:" line but closes no issue labelled `reward` in this repository, so no '
        + 'reward was checked. If it was meant to, check that the issue has the `reward` label and that `Closes #<issue number>` is in the '
        + 'description as a plain line (not inside a comment or a code block). ' + RERUN);
    }
    return { outcome: 'skipped' };
  }
  if (!verdict.ok) {
    if (!recover) {
      await note(gh, pr.number, `This pull request closes reward issue #${verdict.issue.number}, but its reward can't be queued:\n\n`
        + verdict.reasons.map(reason => `- ${reason}`).join('\n') + `\n\n${RERUN}`);
    }
    return { outcome: 'rejected', reasons: verdict.reasons };
  }
  const { entry } = verdict;
  const notLive = recover ? null : env.DRY_RUN === 'true' ? 'this was a dry run'
    : !env.REWARD_PROPOSER_KEY ? 'the REWARD_PROPOSER_KEY secret is not set' : null;
  if (notLive) {
    await note(gh, pr.number, `(Dry run) Reward check passed: ${atlas(entry.amount)} to \`${entry.address}\` for #${entry.issue}. Not queued, because ${notLive}.`);
    return { outcome: 'dry-run', entry };
  }
  try {
    return await queue({ env, ledgerPath, ledger, policy, gh, pr, entry, recover });
  } catch (error) {
    if (!recover) {
      await note(gh, pr.number, `Reward check passed (${atlas(entry.amount)} for #${entry.issue}), but the payment was not queued: ${error.message} `
        + 'The maintainer sees this failed run and will check it.');
    }
    throw error;
  }
}

/**
 * Queues one approved reward, after checking the Safe: a reward queued (or paid) for this issue by a run that failed
 * before its ledger row reached main is recorded instead of queued again, but only if it pays exactly this entry; and
 * any reward transaction the ledger does not list stops everything, because the limits are counted from the ledger.
 */
async function queue({ env, ledgerPath, ledger, policy, gh, pr, entry, recover }) {
  const safe = getAddress(ledger.poolWallet);
  const safeService = service(env);
  const expected = transferTx({ token: policy.token, to: entry.address, amount: entry.amount, decimals: policy.decimals, nonce: 0 });
  const rows = ledger.rewards || [];
  const listed = new Set(rows.map(row => lower(row.safeTxHash)));
  const dead = new Set(rows.filter(row => ['cancelled', 'failed'].includes(row.status)).map(row => lower(row.safeTxHash)));
  const current = await safeService.safeNonce(safe);
  // Transactions that can still be, or were, executed: queued ones at a nonce the Safe has passed can never run.
  const live = (await safeService.recent(safe)).filter(t => !dead.has(lower(t.safeTxHash)) && (t.executed || t.nonce >= current));
  const mine = live.filter(t => t.origin === originFor(entry.issue) && !listed.has(lower(t.safeTxHash)));
  if (mine.length > 1) throw new Error(`the Safe holds ${mine.length} transactions labelled for #${entry.issue}; reject all but one in the Safe first.`);
  if (mine.length === 1) {
    const [t] = mine;
    // Every field, not only the transfer: a gas refund in ATLAS to someone else, or a delegatecall, would pay them too.
    const matches = lower(t.to) === lower(expected.to) && lower(t.data) === lower(expected.data) && t.value === '0' && t.operation === 0 &&
      t.safeTxGas === '0' && t.baseGas === '0' && t.gasPrice === '0' && lower(t.gasToken) === lower(zeroAddress) && lower(t.refundReceiver) === lower(zeroAddress);
    if (!matches) throw new Error(`the Safe already holds a transaction labelled for #${entry.issue} that pays someone or something else; check it in the Safe.`);
    // Dated when it was queued, so it counts in that month, as CONTRIBUTING says, and checked against that month's limits.
    const queuedOn = /^\d{4}-\d{2}-\d{2}/.test(t.submissionDate || '') ? t.submissionDate.slice(0, 10) : entry.date;
    if (queuedOn.slice(0, 7) !== entry.date.slice(0, 7)) {
      const then = decide({ pr, ledger, policy, today: queuedOn });
      if (!then.ok) throw new Error(`the Safe holds a transaction for #${entry.issue} queued on ${queuedOn}, which breaks that month's rules: ${then.reasons.join(' ')}`);
    }
    await writeLedger(ledgerPath, recordProposal(ledger, { ...entry, date: queuedOn }, t.safeTxHash, t.nonce));
    return { outcome: 'already-queued', entry, safeTxHash: t.safeTxHash };
  }
  if (recover) return { outcome: 'not-queued', entry };
  const unlisted = live.filter(t => typeof t.origin === 'string' && t.origin.startsWith(ORIGIN_PREFIX) && !listed.has(lower(t.safeTxHash)));
  if (unlisted.length) {
    throw new Error(`the Safe holds reward transactions the ledger does not list (${unlisted.map(t => `#${t.origin.slice(ORIGIN_PREFIX.length)}`).join(', ')}); `
      + 'the ledger must list them before more are queued.');
  }
  let account;
  try { account = privateKeyToAccount(env.REWARD_PROPOSER_KEY); } catch { throw new Error('REWARD_PROPOSER_KEY is not a valid key.'); }
  const nonce = await safeService.nextNonce(safe);
  const tx = transferTx({ token: policy.token, to: entry.address, amount: entry.amount, decimals: policy.decimals, nonce });
  const typed = safeTypedData({ safe, chainId: policy.chainId, tx });
  const safeTxHash = hashTypedData(typed);
  const signature = await account.signTypedData(typed);
  await safeService.propose(safe, { tx, safeTxHash, sender: account.address, signature, origin: originFor(entry.issue) });
  await writeLedger(ledgerPath, recordProposal(ledger, entry, safeTxHash, nonce));
  await note(gh, pr.number, `Reward queued: ${atlas(entry.amount)} to \`${entry.address}\` for #${entry.issue}. `
    + 'It is paid once the maintainer confirms it in the pool wallet, and then listed at https://www.getfieldatlas.com/#community.');
  return { outcome: 'proposed', entry, safeTxHash };
}

/**
 * Records rewards a run queued in the Safe but could not write to the ledger, and lists merged pull requests that
 * earn a reward nobody queued (GitHub drops a run that waits behind another): those need a maintainer's run, never an
 * automatic one, so a pull request refused earlier is not paid later without a decision.
 */
export async function sweep({ env, ledgerPath = LEDGER, today = new Date().toISOString().slice(0, 10) }) {
  const gh = github({ token: env.GITHUB_TOKEN, repo: env.GITHUB_REPOSITORY, fetch: env.fetch });
  const since = new Date(Date.parse(today) - SWEEP_DAYS * 86400000).toISOString().slice(0, 10);
  const recovered = [];
  const needsRun = [];
  const refusedBefore = [];
  const problems = [];
  for (const number of await gh.mergedSince(since)) {
    const ledger = await readLedger(ledgerPath);
    const link = `https://github.com/${env.GITHUB_REPOSITORY}/pull/${number}`;
    const rows = (ledger.rewards || []).filter(row => row.link === link);
    if (rows.some(row => !['cancelled', 'failed'].includes(row.status))) continue;
    try {
      const result = await propose({ env, ledgerPath, today, number, recover: true });
      if (result.outcome === 'already-queued') recovered.push(number);
      // A payout the maintainer rejected in the Safe (or that failed) is not offered again as if it were forgotten.
      if (result.outcome === 'not-queued') (rows.length ? refusedBefore : needsRun).push(number);
    } catch (error) {
      problems.push(`#${number}: ${error.message}`);
    }
  }
  // Reward transactions in the Safe that the ledger still does not list block every new proposal: say which, so the
  // maintainer can record or reject them even when their pull request no longer passes the check.
  const ledger = await readLedger(ledgerPath);
  const safeService = service(env);
  const safe = getAddress(ledger.poolWallet);
  const current = await safeService.safeNonce(safe);
  const listed = new Set((ledger.rewards || []).map(row => lower(row.safeTxHash)));
  const unlisted = (await safeService.recent(safe)).filter(t => typeof t.origin === 'string' && t.origin.startsWith(ORIGIN_PREFIX) &&
    !listed.has(lower(t.safeTxHash)) && (t.executed || t.nonce >= current));
  const lines = [
    needsRun.length && `Merged pull requests that pass the reward check but were never queued: ${needsRun.map(n => `#${n}`).join(', ')}. `
      + 'Run "Contributor rewards" for each, with its number and "Dry run" unticked.',
    refusedBefore.length && `Merged pull requests whose earlier payout was rejected in the Safe or failed: ${refusedBefore.map(n => `#${n}`).join(', ')}. `
      + 'Run "Contributor rewards" again only if the payout is still meant.',
    unlisted.length && `Reward transactions in the Safe that the ledger does not list (new rewards are blocked until they are recorded or rejected): `
      + unlisted.map(t => `${t.origin} (nonce ${t.nonce}, ${t.executed ? 'executed' : 'queued'}, ${t.safeTxHash})`).join('; ') + '.',
    problems.length && `Could not check: ${problems.join('; ')}.`,
  ].filter(Boolean);
  for (const line of lines) console.error(line);
  if (lines.length && env.GITHUB_STEP_SUMMARY) await appendFile(env.GITHUB_STEP_SUMMARY, lines.join('\n\n') + '\n');
  return { outcome: 'swept', changed: recovered.length, needsRun, refusedBefore, unlisted: unlisted.map(t => t.safeTxHash), problems: problems.length };
}

export async function settleLedger({ env, ledgerPath = LEDGER }) {
  const ledger = await readLedger(ledgerPath);
  const waiting = (ledger.rewards || []).filter(row => row.status === 'proposed' && row.safeTxHash);
  if (waiting.length === 0 || !ledger.poolWallet) return { changed: 0 };
  const safeService = service(env);
  const safe = getAddress(ledger.poolWallet);
  const safeNonce = await safeService.safeNonce(safe);
  const lookups = {};
  const executedAtNonce = {};
  let problems = 0;
  // One row the service cannot answer for must not stop the others from settling.
  for (const row of waiting) {
    try {
      const found = await safeService.transaction(row.safeTxHash);
      lookups[row.safeTxHash] = found;
      const nonce = found?.nonce ?? row.nonce;
      if (!found?.executed && Number.isInteger(nonce) && safeNonce > nonce && !(nonce in executedAtNonce)) {
        executedAtNonce[nonce] = await safeService.executedAt(safe, nonce);
      }
    } catch (error) {
      problems++;
      console.error(`Could not look up ${row.safeTxHash}: ${error.message}`);
    }
  }
  const result = settle(ledger, lookups, executedAtNonce);
  if (result.changed) await writeLedger(ledgerPath, result.ledger);
  return { changed: result.changed, ...(problems ? { problems } : {}) };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const command = process.argv[2];
  const run = { propose, sweep, settle: settleLedger }[command];
  if (!run) {
    console.error('usage: node tools/rewards/cli.mjs propose|sweep|settle');
    process.exit(2);
  }
  const result = await run({ env: process.env });
  // The key never reaches the log: only the outcome and the public parts of the payout.
  console.log(JSON.stringify({ outcome: result.outcome, changed: result.changed, issue: result.entry?.issue, amount: result.entry?.amount, safeTxHash: result.safeTxHash }));
  // A pull request or a payment that could not be checked fails the run, so it shows in Actions; what was settled is kept.
  if (result.problems) process.exitCode = 1;
}
