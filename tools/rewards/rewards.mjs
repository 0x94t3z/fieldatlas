// The payout rules. Pure functions over the pull request, its linked issues and the public ledger
// (website/rewards.json), so every rule is testable without GitHub or a wallet.
import { getAddress, isAddress, zeroAddress } from 'viem';

// At most three spaces of indent: four or more (or a tab) makes a Markdown code block, which is not part of what is read.
const REWARD_LINE = /^ {0,3}Reward:[ \t]*([\d,_ ]+?)[ \t]*ATLAS[ \t]*$/;
const ADDRESS_LINE = /^ {0,3}Base address:[ \t]*`?(0x[0-9a-fA-F]{40})`?[ \t]*$/;

/** Reward issues count only in this repository: another repository's issue is not an offer, whoever opened it. */
export const REPO_URL = 'https://github.com/0x94t3z/fieldatlas';

/**
 * The payout settings, pinned in code as well as in the ledger. A merged pull request is paid by the code on main after
 * its own merge, so a quiet edit to the ledger's decimals or token would otherwise change its own payout.
 */
export const PINNED = {
  branch: 'main',
  maintainerIds: [52822242],
  chainId: 8453,
  token: '0xb462a5039a540883508ee37b5cf3999647557ba3',
  decimals: 18,
  poolWallet: '0x527C6fb80013882280CE4DB88E720F0fd8b1AEa2',
};

/** The ledger's limits, in ATLAS. */
const LIMITS = ['maxPerTask', 'perPersonMonthly', 'monthlyTotal', 'poolSize'];

/** Files a rewarded pull request may not change: the ledger, the payout code and the workflows that run it. */
const PROTECTED = /^(website\/rewards\.json$|tools\/rewards\/|\.github\/)/;

/**
 * The lines a reader sees, as GitHub renders the body: HTML comments and fenced code (closed only by a fence of the same
 * character at least as long) are left out, so an example line hidden there never counts. Indented code needs no
 * handling here: the reward and address lines allow at most three spaces of indent.
 */
function visibleLines(body) {
  const text = String(body || '');
  // "<!-->" and "<!--->" close at once in Markdown but not in the pattern below, so the lines after them would be misread.
  if (/<!---?>/.test(text)) return null;
  const lines = text.replace(/<!--[\s\S]*?(?:-->|$)/g, '').split(/\r?\n/);
  // A fence inside a list item or a quote shows its lines as code, which this reader cannot follow: read nothing.
  if (lines.some(line => /^[ \t]*(?:(?:[-*+>]|\d{1,9}[.)])[ \t]*)+(`{3,}|~{3,})/.test(line))) return null;
  const out = [];
  let fence = null;
  for (const line of lines) {
    if (fence) {
      if (new RegExp(`^ {0,3}${fence.char === '`' ? '`' : '~'}{${fence.length},}[ \\t]*$`).test(line)) fence = null;
      continue;
    }
    const opening = /^ {0,3}(`{3,}|~{3,})/.exec(line);
    if (opening) fence = { char: opening[1][0], length: opening[1].length };
    else out.push(line);
  }
  return out;
}

/** The amount on a reward issue's "Reward: 5,000,000 ATLAS" line, or null; two different amounts mean none. */
export function rewardAmount(body) {
  const amounts = (visibleLines(body) || []).map(line => REWARD_LINE.exec(line)).filter(Boolean).map(match => Number(match[1].replace(/[,_ ]/g, '')));
  if (amounts.length === 0 || new Set(amounts).size > 1) return null;
  return Number.isSafeInteger(amounts[0]) && amounts[0] > 0 ? amounts[0] : null;
}

/** The checksummed address on a pull request's "Base address: 0x…" line, or null. A mixed-case address with a
 * wrong checksum is refused: it is usually a typo. */
export function baseAddress(body) {
  const lines = (visibleLines(body) || []).map(line => ADDRESS_LINE.exec(line)).filter(Boolean).map(match => match[1]);
  // Two different addresses: which one was meant is a guess, so neither is paid.
  if (lines.length === 0 || new Set(lines.map(line => line.toLowerCase())).size > 1) return null;
  return isAddress(lines[0], { strict: true }) ? getAddress(lines[0]) : null;
}

/** Rows that still claim budget: paid, proposed, or written by hand before the automation (no status). */
const active = row => !['cancelled', 'failed'].includes(row.status);
const sum = rows => rows.reduce((total, row) => total + row.amount, 0);
const same = (a, b) => typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();

/** A ledger row the limits can count: a hand-typed "50,000,000" or a string issue number would slip past them. */
const malformed = row => !(Number.isSafeInteger(row.amount) && row.amount > 0 && Number.isSafeInteger(row.issue) &&
  typeof row.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(row.date));

/**
 * A maintainer, by GitHub account id when the policy lists ids: a login can be renamed and taken by someone else.
 * Without ids (older ledgers, tests) the login decides.
 */
const isMaintainer = (policy, user) => (policy.maintainerIds?.length
  ? policy.maintainerIds.includes(user.id)
  : (policy.maintainers || []).includes(user.login));

/** Whether this pull request is the maintainer's own, which is never paid and needs no comment. */
export const byMaintainer = (pr, policy) => isMaintainer(policy, { login: pr.author, id: pr.authorId });

/** The reward issues a pull request closes in this repository. */
export const rewardIssuesOf = pr => (pr.issues || []).filter(issue => issue.labels.includes('reward') &&
  typeof issue.url === 'string' && issue.url.startsWith(`${REPO_URL}/issues/`));

/**
 * Whether a merged pull request earns its reward. Returns { skip } when it closes no reward issue (most pull
 * requests), { ok: false, issue, reasons } when it does but fails a rule, and { ok: true, entry } otherwise.
 */
export function decide({ pr, ledger, policy, today }) {
  const rewardIssues = rewardIssuesOf(pr);
  if (rewardIssues.length === 0) return { skip: true };
  if (rewardIssues.length > 1) {
    return { ok: false, issue: rewardIssues[0], reasons: [`It closes ${rewardIssues.length} reward issues (${rewardIssues.map(i => `#${i.number}`).join(', ')}); one pull request is paid for one issue.`] };
  }
  const [issue] = rewardIssues;
  const reasons = [];
  const pinnedMismatch = Object.entries(PINNED).filter(([key, value]) =>
    key === 'poolWallet' ? !same(ledger.poolWallet, value) : key === 'token' ? !same(policy.token, value)
      : Array.isArray(value) ? JSON.stringify(policy[key]) !== JSON.stringify(value) : policy[key] !== value);
  if (pinnedMismatch.length) reasons.push(`The ledger's payout settings (${pinnedMismatch.map(([key]) => key).join(', ')}) differ from the ones pinned in tools/rewards/rewards.mjs.`);
  // A limit typed as "50,000,000" or left out would make every comparison below false, switching the limit off.
  const badLimits = LIMITS.filter(key => !(Number.isSafeInteger(policy[key]) && policy[key] > 0));
  if (badLimits.length) return { ok: false, issue, reasons: [...reasons, `The ledger's limits (${badLimits.join(', ')}) are not whole numbers above zero; they must be fixed before any reward is queued.`] };
  const touched = (pr.files || []).filter(path => PROTECTED.test(path));
  // Only the first 100 files and 10 linked issues can be read; anything beyond them could hide a protected file or a
  // second reward issue. A count the API did not give is treated the same way.
  if (pr.filesTotal == null || pr.filesTotal > (pr.files || []).length) reasons.push(`It changes ${pr.filesTotal ?? 'an unknown number of'} files, more than the check can read; a rewarded pull request must be smaller.`);
  if (pr.issuesTotal != null && pr.issuesTotal > (pr.issues || []).length) reasons.push(`It closes ${pr.issuesTotal} issues, more than the check can read.`);
  // A rename shows only the new path, so a file moved out of a protected folder would not be seen.
  if ((pr.renamed || []).length) reasons.push(`It renames ${pr.renamed.map(path => `\`${path.replace(/`/g, '')}\``).join(', ')}; a rewarded pull request may not rename files.`);
  // Paths come from the pull request: shown as code so they cannot become mentions or links in the comment.
  if (touched.length) reasons.push(`It changes ${touched.map(path => `\`${path.replace(/`/g, '')}\``).join(', ')}; a rewarded pull request may not change the reward settings, the payout code or the workflows.`);
  const badRows = (ledger.rewards || []).filter(malformed);
  if (badRows.length) reasons.push(`The ledger has ${badRows.length} malformed row(s) (amount, issue or date); they must be fixed before any reward is queued.`);
  if (!pr.merged) reasons.push('It is not merged.');
  // GitHub closes the issue when the pull request that says "Closes #N" is merged. A link added to the description after
  // the merge, or an issue closed by hand, does not count: the work being paid must be the work that was merged.
  if (issue.state !== 'CLOSED' || !(issue.closedBy || []).includes(pr.url)) reasons.push(`Issue #${issue.number} was not closed by this pull request's merge.`);
  if (pr.baseRef !== policy.branch) reasons.push(`It was merged into ${pr.baseRef}, not ${policy.branch}.`);
  if (!isMaintainer(policy, { login: issue.author, id: issue.authorId })) reasons.push(`Issue #${issue.number} was not opened by a maintainer, so its amount is not an offer.`);
  const amount = rewardAmount(issue.body);
  if (amount === null) reasons.push(`Issue #${issue.number} has no "Reward: <amount> ATLAS" line.`);
  else if (amount > policy.maxPerTask) reasons.push(`Issue #${issue.number} offers ${amount.toLocaleString('en')} ATLAS, above the ${policy.maxPerTask.toLocaleString('en')} limit per task.`);
  if (isMaintainer(policy, { login: pr.author, id: pr.authorId })) reasons.push('Maintainers are not paid from the pool.');
  else if (issue.assignees.length !== 1 || issue.assignees[0] !== pr.author) {
    reasons.push(issue.assignees.length > 1 ? `Issue #${issue.number} has ${issue.assignees.length} assignees; a task is paid to one person.` : `@${pr.author} is not assigned to issue #${issue.number}.`);
  }
  const address = baseAddress(pr.body);
  if (address === null) reasons.push('The description has no valid "Base address: 0x…" line.');
  else if ([zeroAddress, PINNED.token, PINNED.poolWallet].some(bad => same(bad, address))) reasons.push(`${address} cannot receive a reward (the zero address, the token or the pool itself).`);
  const rows = (ledger.rewards || []).filter(active).filter(row => !malformed(row));
  const earlier = rows.find(row => row.issue === issue.number || row.link === pr.url);
  if (earlier) reasons.push(`${earlier.link === pr.url ? 'This pull request' : `Issue #${issue.number}`} already has a reward ${earlier.status === 'paid' ? 'paid' : 'queued'}.`);
  if (amount !== null) {
    const month = today.slice(0, 7);
    // The maintainer's own rows (the payment test) count against the pool but not against contributors' monthly room.
    const thisMonth = rows.filter(row => row.date.startsWith(month) && !(policy.maintainers || []).includes(row.contributor));
    // One person, however many accounts: a second login paying to the same address counts as the same person, and a
    // renamed account is matched by its id.
    const theirs = thisMonth.filter(row => row.contributor === pr.author || (address && same(row.address, address)) ||
      (Number.isSafeInteger(pr.authorId) && row.contributorId === pr.authorId));
    if (sum(theirs) + amount > policy.perPersonMonthly) {
      reasons.push(`It would take @${pr.author} (or ${address}) over ${policy.perPersonMonthly.toLocaleString('en')} ATLAS this month (UTC); it can be checked again next month.`);
    }
    if (sum(thisMonth) + amount > policy.monthlyTotal) reasons.push(`It would take this month's rewards over ${policy.monthlyTotal.toLocaleString('en')} ATLAS (UTC); it can be checked again next month.`);
    if (sum(rows) + amount > policy.poolSize) reasons.push('The pool has too little left for it.');
  }
  if (reasons.length) return { ok: false, issue, reasons };
  return {
    ok: true,
    entry: { date: today, contributor: pr.author, contributorId: pr.authorId ?? null, work: issue.title, link: pr.url, issue: issue.number, amount, address, status: 'proposed', safeTxHash: null, nonce: null, tx: null },
  };
}

export function recordProposal(ledger, entry, safeTxHash, nonce) {
  return { ...ledger, rewards: [...(ledger.rewards || []), { ...entry, safeTxHash, nonce }] };
}

/**
 * Updates proposed rows from what the Safe reports. lookups maps safeTxHash to { executed, successful, txHash, nonce }
 * or null when the service no longer knows it; executedAtNonce maps a nonce to the safeTxHash executed there.
 * Paid once executed and successful; failed only on an explicit failure; cancelled only when another transaction was
 * executed at its nonce. Anything else (a lagging service, an odd answer) leaves the row for the next run.
 */
export function settle(ledger, lookups, executedAtNonce = {}) {
  let changed = 0;
  const rewards = (ledger.rewards || []).map(row => {
    if (row.status !== 'proposed' || !row.safeTxHash) return row;
    const found = lookups[row.safeTxHash];
    if (found?.executed && found.successful === true && /^0x[0-9a-fA-F]{64}$/.test(found.txHash || '')) { changed++; return { ...row, status: 'paid', tx: found.txHash }; }
    if (found?.executed && found.successful === false) { changed++; return { ...row, status: 'failed' }; }
    const nonce = found?.nonce ?? row.nonce;
    const replacement = Number.isInteger(nonce) ? executedAtNonce[nonce] : undefined;
    if (!found?.executed && replacement && !same(replacement, row.safeTxHash)) { changed++; return { ...row, status: 'cancelled' }; }
    return row;
  });
  return { ledger: { ...ledger, rewards }, changed };
}
