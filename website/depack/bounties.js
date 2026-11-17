// Wanted page: poidh bounties on Base for new collections, read live from the chain.
// Funding goes straight to the poidh contract from the visitor's own wallet; this page never holds funds.
(() => {
  const section = document.getElementById('wanted');
  if (!section) return;

  const RPC = 'https://mainnet.base.org';
  const CHAIN = '0x2105'; // Base mainnet, 8453
  const POIDH = '0x5555fa783936c260f77385b4e153b9725fef1719'; // PoidhV3, source verified on Sourcify
  // ERC-8021 attribution: "bc_diey6oig", its length, schema 0, then the 16-byte marker.
  const BUILDER_SUFFIX = '62635f64696579366f69670b0080218021802180218021802180218021';
  // A connected wallet shows its Basename, read on Base, and otherwise its ENS name, read on Ethereum.
  // ENS's reverse registrar on Base (L2ReverseRegistrar, source verified) and the Basenames registry.
  const BASE_REVERSE = '0x0000000000d8e504002cc26e3ec46d81971c1664';
  const BASE_REGISTRY = '0xb94704422c2a1e396835a571837aa5ae53285a95';
  // The ENS Universal Resolver returns a primary name only after checking that the name resolves back to the wallet.
  const ENS_RPC = 'https://ethereum-rpc.publicnode.com';
  const ENS_RESOLVER = '0xeeeeeeee14d718c2b47d9923deab1335e144eeee';
  const MIN_WEI = 10n ** 13n; // the contract's MIN_CONTRIBUTION, 0.00001 ETH
  // poidh's site numbers Base v3 bounties after its 986 older ones, so contract bounty 538 is page 1524.
  const POIDH_PAGE_OFFSET = 986n;
  // Only bounties made for this page carry its address in their description; other bounties by the same wallet stay off it.
  const MARKER = 'getfieldatlas.com/depack/wanted';
  const SEL = {
    byUser: '0e122a78', userBounties: '2b9234b9', bounty: 'dc2f8744', participants: 'c1e3bd3e',
    votingClaim: '383d6d43', votes: '10b385c2', join: 'a9b2c6eb', create: '8c1d93c5',
    cancelOpen: 'd496ec7a', cancelSolo: 'b1b4be97', refund: '240b727e', withdraw: '3ccfd60b', balance: 'f3f43703',
    claims: 'b03a50c7', everExternal: 'b04f5ebd', claim: '83ffc9f2', accept: '1d260f8e', startVote: '9e9251c8',
    vote: 'b7c8994d', settle: '30f4ccac',
  };
  const MIN_BOUNTY_WEI = 10n ** 15n; // the contract's MIN_BOUNTY_AMOUNT, 0.001 ETH
  const ERRORS = {
    '7ec19a65': 'The smallest amount is 0.00001 ETH.',
    b6c99482: 'Voting has started on a claim, so the bounty is closed to new ETH for now.',
    '861fbe13': 'This bounty has already been paid.',
    '88bb2354': 'This bounty was cancelled.',
    '0d0d217e': 'The wallet that made this bounty cannot add to it.',
    '6c8c8d21': 'This bounty has the most contributors it can take.',
    a6901796: 'Only its maker funds this bounty.',
    a4675adf: 'Enter an amount above zero.',
    '0fa2e38d': 'The smallest bounty is 0.001 ETH.',
    '4b087ad8': 'poidh lets only a regular wallet start a bounty, not a smart-contract wallet. Try another wallet.',
    d0d04f60: 'There is nothing to withdraw yet.',
    dcd2fc90: 'That bounty is not cancelled.',
    '2fb19d77': 'This wallet has nothing to take back from that bounty.',
    '8f60bb0a': 'The wallet that started this bounty cannot claim it.',
    '10c2a11b': 'Others added ETH to this bounty, so the claim goes to a vote instead.',
    '7c9a1cf9': 'Your vote is already in.',
    '7a19ed05': 'The vote has ended. Settle it to finish.',
    '6e242191': 'There is no vote on this bounty right now.',
    d11e9bde: 'That claim was already accepted.',
    '0455eeee': 'That claim is not on this bounty.',
  };
  const issuer = (section.dataset.issuer || '').toLowerCase();
  if (!/^0x[0-9a-f]{40}$/.test(issuer)) return;

  // ---------- Reading the chain ----------
  const pad = (hex) => hex.padStart(64, '0');
  const uintArg = (n) => pad(BigInt(n).toString(16));
  let rpcId = 0;
  const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  // The public endpoint allows about 25 calls a second per visitor, counting each call inside a batch. Every request
  // books its share of one shared clock (about 20 calls a second), so back-to-back reads never burst past the limit.
  let nextSend = 0;
  async function post(body) {
    for (let attempt = 0; ; attempt++) {
      const start = Math.max(Date.now(), nextSend);
      nextSend = start + body.length * 50;
      if (start > Date.now()) await pause(start - Date.now());
      const response = await fetch(RPC, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
      if (response.status === 429 && attempt === 0) { nextSend = Date.now() + 1100; continue; }
      if (!response.ok) throw new Error(`RPC ${response.status}`);
      return response;
    }
  }
  // Calls go in batches of 20, and a call the endpoint turned away as over the limit is tried once more.
  async function rpc(calls, retry = true) {
    const replies = [];
    for (let i = 0; i < calls.length; i += 20) {
      const body = calls.slice(i, i + 20).map(([method, params]) => ({ jsonrpc: '2.0', id: ++rpcId, method, params }));
      const response = await post(body);
      const byId = new Map((await response.json()).map((reply) => [reply.id, reply]));
      replies.push(...body.map(({ id }) => byId.get(id)));
    }
    const limited = replies.flatMap((reply, i) => (!reply || reply.error?.code === -32007 ? [i] : []));
    if (retry && limited.length) {
      await pause(1100);
      const again = await rpc(limited.map((i) => calls[i]), false);
      limited.forEach((i, k) => { replies[i] = again[k]; });
    }
    return replies;
  }
  const call = (selector, args = '') => ['eth_call', [{ to: POIDH, data: `0x${selector}${args}` }, 'latest']];

  // Minimal ABI reader for the few return types this page needs; positions are byte offsets.
  function reader(result) {
    const hex = result.slice(2);
    const word = (pos) => hex.slice(pos * 2, pos * 2 + 64);
    const uint = (pos) => BigInt(`0x${word(pos) || '0'}`);
    const num = (pos) => Number(uint(pos));
    const address = (pos) => `0x${word(pos).slice(24)}`;
    const string = (pos) => {
      const length = num(pos);
      const bytes = hex.slice((pos + 32) * 2, (pos + 32 + length) * 2);
      return new TextDecoder().decode(Uint8Array.from(bytes.match(/../g) ?? [], (b) => parseInt(b, 16)));
    };
    return { uint, num, address, string };
  }

  // A Bounty (id, issuer, name, description, amount, claimer, createdAt, claimId) starting at byte t;
  // its two strings are placed relative to t.
  const decodeBounty = (r, t) => ({
    id: r.uint(t), issuer: r.address(t + 32), name: r.string(t + r.num(t + 64)),
    description: r.string(t + r.num(t + 96)), amount: r.uint(t + 128), claimer: r.address(t + 160),
  });

  function decodeBounties(result) {
    const r = reader(result);
    const base = r.num(0);
    const count = r.num(base);
    const start = base + 32;
    const list = [];
    // getBountiesByUser always returns ten slots; unused ones are all zeros.
    for (let i = 0; i < count; i++) list.push(decodeBounty(r, start + r.num(start + 32 * i)));
    return list.filter((b) => !ZERO.test(b.issuer));
  }
  // getClaimsByBountyId: the newest ten claims, unused slots all zeros.
  function decodeClaims(result) {
    const r = reader(result);
    const base = r.num(0);
    const count = r.num(base);
    const start = base + 32;
    const list = [];
    for (let i = 0; i < count; i++) {
      const t = start + r.num(start + 32 * i);
      list.push({
        id: r.uint(t), issuer: r.address(t + 32).toLowerCase(), name: r.string(t + r.num(t + 128)),
        description: r.string(t + r.num(t + 160)), createdAt: r.num(t + 192), accepted: r.uint(t + 224) === 1n,
      });
    }
    return list.filter((c) => !ZERO.test(c.issuer));
  }

  function decodeParticipants(result) {
    const r = reader(result);
    const addresses = r.num(0);
    const amounts = r.num(32);
    const slots = r.num(addresses);
    let people = 0;
    let total = 0n;
    const entries = [];
    for (let i = 0; i < slots; i++) {
      const amount = r.uint(amounts + 32 + 32 * i);
      const address = r.address(addresses + 32 + 32 * i).toLowerCase();
      // A contributor who took their ETH back leaves an empty slot behind.
      if (!/^0x0{40}$/.test(address) && amount > 0n) { people++; entries.push({ address, amount }); }
      total += amount;
    }
    // Solo bounties have no participant list; only open bounties take ETH from others.
    return { people, total, open: slots > 0, entries };
  }

  // getBountiesByUser gives only the newest ten and cannot page back, so past ten the rest are read one by one.
  async function allBounties() {
    const [list] = await rpc([call(SEL.byUser, pad(issuer.slice(2)) + uintArg(0))]);
    if (!list?.result) throw new Error('bounties');
    const newest = decodeBounties(list.result);
    if (newest.length < 10) return newest;
    const ids = [];
    for (let i = 0; ; i += 20) {
      const replies = await rpc(Array.from({ length: 20 }, (_, k) => call(SEL.userBounties, pad(issuer.slice(2)) + uintArg(i + k))));
      // Reading past the end of the wallet's list reverts; that is where it stops.
      // A call still turned away as over the limit is not the end of the list; stop with an error rather than drop bounties.
      if (replies.some((reply) => !reply || reply.error?.code === -32007)) throw new Error('rate limited');
      const found = replies.map((reply) => reply?.result).filter(Boolean).map((result) => BigInt(result));
      ids.push(...found);
      if (found.length < 20 || replies.some((reply) => reply?.error)) break;
    }
    const known = new Set(newest.map((b) => b.id));
    const older = ids.filter((id) => !known.has(id));
    const replies = older.length ? await rpc(older.map((id) => call(SEL.bounty, uintArg(id)))) : [];
    return [...newest, ...replies.flatMap((reply) => (reply?.result ? [decodeBounty(reader(reply.result), 0)] : []))];
  }

  const ZERO = /^0x0{40}$/;
  // Bounties anyone started from this page show once their number is on the reviewed list, which keeps spam
  // and scam text off the site; the page owner's own bounties show straight away.
  const approvedList = () => fetch('/depack/wanted.json')
    .then((response) => (response.ok ? response.json() : {}))
    .then((data) => (data.approved ?? []).map((id) => BigInt(id)))
    .catch(() => []);
  async function reviewed(approved, skip) {
    const ids = (await approved).filter((id) => !skip.has(id));
    if (!ids.length) return [];
    const replies = await rpc(ids.map((id) => call(SEL.bounty, uintArg(id))));
    return replies.flatMap((reply) => (reply?.result ? [decodeBounty(reader(reply.result), 0)] : []));
  }

  async function load() {
    // The reviewed list downloads while the chain is read.
    const approved = approvedList();
    const own = (await allBounties()).filter((b) => b.issuer.toLowerCase() === issuer);
    const others = await reviewed(approved, new Set(own.map((b) => b.id)));
    // Cancelled bounties stay in the data, off the shelves, so their contributors can still take their ETH back here.
    const mine = [...own, ...others].filter((b) => !ZERO.test(b.issuer) && b.description.includes(MARKER));
    if (!mine.length) return [];
    const unpaid = mine.filter((b) => ZERO.test(b.claimer));
    const details = await rpc([
      ...mine.map((b) => call(SEL.participants, uintArg(b.id))),
      ...unpaid.map((b) => call(SEL.votingClaim, uintArg(b.id))),
    ]);
    const crowd = new Map(mine.map((b, i) => [b.id, details[i]?.result ? decodeParticipants(details[i].result) : null]));
    const voting = new Set(unpaid.filter((b, i) => {
      const result = details[mine.length + i]?.result;
      return result && BigInt(result) !== 0n;
    }).map((b) => b.id));
    const votingList = unpaid.filter((b) => voting.has(b.id));
    const tallies = votingList.length ? await rpc(votingList.map((b) => call(SEL.votes, uintArg(b.id)))) : [];
    const tally = new Map(votingList.map((b, i) => {
      const r = tallies[i]?.result ? reader(tallies[i].result) : null;
      return [b.id, r ? { yes: r.uint(0), no: r.uint(32), deadline: Number(r.uint(64)) } : null];
    }));
    const votingClaim = new Map(unpaid.map((b, i) => [b.id, details[mine.length + i]?.result ? BigInt(details[mine.length + i].result) : 0n]));
    return mine.map((b) => {
      const c = crowd.get(b.id);
      // A cancelled bounty has its issuer written in as the claimer.
      const state = b.claimer.toLowerCase() === b.issuer.toLowerCase() ? 'cancelled'
        : !ZERO.test(b.claimer) ? 'paid' : voting.has(b.id) ? 'voting' : 'open';
      // Payout zeroes the bounty's amount; for open bounties the contributions still add up to what was paid.
      const wei = state === 'paid' ? (c?.total || null) : b.amount;
      const by = b.issuer.toLowerCase() === issuer ? null : `${b.issuer.slice(0, 6)}…${b.issuer.slice(-4)}`;
      return {
        ...b, state, by, people: c?.people ?? null, open: !!c?.open, joinable: state === 'open' && !!c?.open,
        entries: c?.entries ?? [], wei, deadline: tally.get(b.id)?.deadline ?? 0,
        yes: tally.get(b.id)?.yes ?? 0n, no: tally.get(b.id)?.no ?? 0n, votingClaim: votingClaim.get(b.id) ?? 0n,
      };
    });
  }

  // ---------- Showing it ----------
  const eth = (wei, exact = false) => {
    const whole = wei / 10n ** 18n;
    const digits = (wei % 10n ** 18n).toString().padStart(18, '0');
    // Four decimals read well, but the contract takes amounts down to 0.00001, so a smaller amount keeps its digits;
    // exact keeps them all, for a payout that must not look rounded.
    const frac = (exact ? digits : whole || /[1-9]/.test(digits.slice(0, 4)) || !wei ? digits.slice(0, 4) : digits.slice(0, 8)).replace(/0+$/, '');
    return `${whole}${frac ? `.${frac}` : ''} ETH`;
  };
  const poidhUrl = (id) => `https://poidh.xyz/base/bounty/${BigInt(id) + POIDH_PAGE_OFFSET}`;
  const el = (tag, { dataset, ...props } = {}, ...children) => {
    const node = Object.assign(document.createElement(tag), props);
    if (dataset) Object.assign(node.dataset, dataset);
    node.append(...children.filter((child) => child !== null && child !== undefined));
    return node;
  };
  const icon = (id) => {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('class', 'icon');
    svg.setAttribute('aria-hidden', 'true');
    const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
    use.setAttribute('href', `#${id}`);
    svg.append(use);
    return svg;
  };
  const dateFmt = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });
  const LABEL = { open: 'Wanted', voting: 'Voting', paid: 'Paid' };

  const peopleText = (n) => `${n} ${n === 1 ? 'backer' : 'backers'}`;
  function lineFor(b) {
    if (b.state === 'voting' && b.deadline) {
      // A vote that has ended stays open on chain until someone settles it.
      const when = b.deadline * 1000 > Date.now() ? `voting until ${dateFmt.format(new Date(b.deadline * 1000))}` : 'vote ended, being settled';
      return `${eth(b.wei)} · ${when}`;
    }
    const parts = [b.wei ? eth(b.wei) : null, b.people ? peopleText(b.people) : null].filter(Boolean);
    return parts.join(' · ');
  }

  function book(b) {
    const take = el('div', { className: 'take' });
    const mine = account && account === b.issuer.toLowerCase();
    if (b.state === 'open' || b.state === 'voting') {
      // Claims and votes happen here, so they carry the builder code too.
      const claims = el('button', { type: 'button', className: 'onpoidh', textContent: b.state === 'voting' ? 'See the vote' : mine ? 'Claims' : 'Make it' });
      claims.addEventListener('click', () => openClaims(b, claims));
      take.append(claims);
    }
    if (b.state === 'open') {
      // Only the wallet that started a bounty can cancel it (poidh checks this too); it cannot add to its own.
      if (mine) {
        const stop = el('button', { type: 'button', className: 'get' }, 'Cancel bounty');
        stop.addEventListener('click', () => openCancel(b, stop));
        take.append(stop);
      } else if (b.joinable) {
        const give = el('button', { type: 'button', className: 'get' }, icon('i-plus'), 'Add ETH');
        give.addEventListener('click', () => openGive(b, give));
        take.append(give);
      }
    } else if (b.state !== 'voting') {
      take.append(el('a', { className: 'onpoidh', href: poidhUrl(b.id), textContent: 'See it on poidh' }));
    }
    const line = lineFor(b);
    return el('article', { className: `book wanted-book is-${b.state}`, id: `bounty-${b.id}` },
      el('div', { className: 'cover' },
        el('p', { className: 'kicker', textContent: LABEL[b.state] }),
        el('h3', { textContent: b.name }),
        line ? el('p', { className: 'line', textContent: line }) : null,
        b.by ? el('p', { className: 'byline', textContent: `Started by ${b.by}`, dataset: { address: b.issuer.toLowerCase() } }) : null),
      take);
  }

  const note = document.getElementById('open-note');
  const quiet = (text, link) => {
    note.replaceChildren(text, ...(link ? [' ', el('a', { href: link.href, textContent: link.text })] : []));
    note.hidden = false;
  };

  const STASH = `depack-wanted-v1-${issuer}`;
  const stash = (list) => {
    try { sessionStorage.setItem(STASH, JSON.stringify(list, (key, value) => (typeof value === 'bigint' ? { $big: value.toString() } : value))); } catch { /* not kept */ }
  };
  const stashed = () => {
    try {
      const text = sessionStorage.getItem(STASH);
      return text ? JSON.parse(text, (key, value) => (typeof value?.$big === 'string' ? BigInt(value.$big) : value)) : null;
    } catch {
      return null;
    }
  };
  const refresh = () => load().then((list) => { stash(list); render(list); });

  let lastList = null;
  function render(list) {
    lastList = list;
    // Open first, then voting; the latest paid ones show that bounties here do get paid.
    const live = list.filter((b) => b.state === 'open' || b.state === 'voting').sort((a, b) => (a.state === b.state ? Number(b.id - a.id) : a.state === 'open' ? -1 : 1));
    const paid = list.filter((b) => b.state === 'paid').sort((a, b) => Number(b.id - a.id)).slice(0, 3);
    document.getElementById('open-shelf').replaceChildren(...live.map(book));
    document.getElementById('paid-shelf').replaceChildren(...paid.map(book));
    document.getElementById('paid').hidden = paid.length === 0;
    document.getElementById('open-count').textContent = live.length || '';
    if (live.length) note.hidden = true;
    else quiet('Nothing is open right now. Ask for the collection you are missing below.');
    const sum = (rows) => rows.reduce((total, b) => total + (b.wei ?? 0n), 0n);
    const totals = document.getElementById('totals');
    totals.querySelector('[data-total="open"]').textContent = live.length;
    totals.querySelector('[data-total="pooled"]').textContent = eth(sum(live));
    totals.querySelector('[data-total="paid"]').textContent = eth(sum(list.filter((b) => b.state === 'paid')));
    totals.hidden = false;
    document.getElementById('open-shelf').removeAttribute('data-loading');
    refreshClaims();
    // Someone else's bounty shows its starter's Basename or ENS name once it is read.
    document.querySelectorAll('.byline[data-address]').forEach((line) => {
      walletName(line.dataset.address).then((name) => { if (name && line.isConnected) line.textContent = `Started by ${name}`; });
    });
    refreshWallet();
  }

  // ---------- Paying from the visitor's wallet ----------
  // EIP-6963: every installed wallet announces itself; window.ethereum is the fallback for older ones.
  const announced = new Map();
  window.addEventListener('eip6963:announceProvider', (event) => {
    const { info, provider } = event.detail ?? {};
    if (info?.uuid && provider) announced.set(info.uuid, { name: info.name, rdns: info.rdns ?? '', provider });
  });
  window.dispatchEvent(new Event('eip6963:requestProvider'));
  let chosen = null;
  // The connected account, lower case; it decides which bounties show Cancel instead of Add ETH.
  let account = null;
  let onBase = false;
  const walletButton = document.getElementById('wallet-button');
  const walletLabel = document.getElementById('wallet-label');
  const short = (address) => `${address.slice(0, 6)}…${address.slice(-4)}`;
  // Which wallet was used last is a per-browser convenience; storage can be missing or blocked.
  const remember = (rdns) => { try { if (rdns) localStorage.setItem('depack-wallet', rdns); else localStorage.removeItem('depack-wallet'); } catch { /* not kept */ } };
  const remembered = () => { try { return localStorage.getItem('depack-wallet'); } catch { return null; } };
  const watched = new WeakSet();

  // Keccak-256, for the ENS namehash that checks a Basename points back to its wallet. BigInt lanes keep it short;
  // it runs a handful of times per visit.
  const keccak256 = (() => {
    const mask = (1n << 64n) - 1n;
    const rotl = (x, n) => ((x << BigInt(n)) | (x >> BigInt(64 - n))) & mask;
    const ROT = [0, 1, 62, 28, 27, 36, 44, 6, 55, 20, 3, 10, 43, 25, 39, 41, 45, 15, 21, 8, 18, 2, 61, 56, 14];
    const RC = [];
    for (let i = 0, R = 1; i < 24; i++) {
      let rc = 0n;
      for (let j = 0; j < 7; j++) {
        R = ((R << 1) ^ ((R >> 7) * 0x71)) % 256;
        if (R & 2) rc ^= 1n << BigInt((1 << j) - 1);
      }
      RC.push(rc);
    }
    const permute = (A) => {
      for (let round = 0; round < 24; round++) {
        const C = [0, 1, 2, 3, 4].map((x) => A[x] ^ A[x + 5] ^ A[x + 10] ^ A[x + 15] ^ A[x + 20]);
        for (let x = 0; x < 5; x++) {
          const D = C[(x + 4) % 5] ^ rotl(C[(x + 1) % 5], 1);
          for (let y = 0; y < 25; y += 5) A[x + y] ^= D;
        }
        const B = new Array(25);
        for (let x = 0; x < 5; x++) for (let y = 0; y < 5; y++) B[y + 5 * ((2 * x + 3 * y) % 5)] = rotl(A[x + 5 * y], ROT[x + 5 * y]);
        for (let y = 0; y < 25; y += 5) for (let x = 0; x < 5; x++) A[x + y] = B[x + y] ^ (~B[((x + 1) % 5) + y] & mask & B[((x + 2) % 5) + y]);
        A[0] ^= RC[round];
      }
    };
    return (bytes) => {
      const A = new Array(25).fill(0n);
      const padded = new Uint8Array(Math.floor(bytes.length / 136) * 136 + 136);
      padded.set(bytes);
      padded[bytes.length] ^= 0x01;
      padded[padded.length - 1] ^= 0x80;
      for (let off = 0; off < padded.length; off += 136) {
        for (let i = 0; i < 17; i++) {
          let lane = 0n;
          for (let b = 7; b >= 0; b--) lane = (lane << 8n) | BigInt(padded[off + i * 8 + b]);
          A[i] ^= lane;
        }
        permute(A);
      }
      return Uint8Array.from({ length: 32 }, (_, i) => Number((A[i >> 3] >> BigInt(8 * (i & 7))) & 0xffn));
    };
  })();
  const namehash = (name) => Array.from(name.split('.').reverse().reduce((node, label) => {
    const both = new Uint8Array(64);
    both.set(node);
    both.set(keccak256(new TextEncoder().encode(label)), 32);
    return keccak256(both);
  }, new Uint8Array(32)), (b) => b.toString(16).padStart(2, '0')).join('');

  const readAt = (to, data) => ['eth_call', [{ to, data }, 'latest']];
  // Spaces, control and direction-override characters could make one name look like another.
  const safeName = (name) => (/^[^\s\p{Cc}\u200b-\u200f\u202a-\u202e\u2066-\u2069]{1,80}$/u.test(name) ? name : null);
  async function baseName(address) {
    const [reply] = await rpc([readAt(BASE_REVERSE, `0x4ec3bd23${pad(address.slice(2))}`)]); // nameForAddr(address)
    if (!reply?.result || reply.result === '0x') return null;
    const r = reader(reply.result);
    const name = safeName(r.string(r.num(0)));
    if (!name?.endsWith('.base.eth')) return null;
    // Any wallet can claim any name in reverse, so the name shows only if it also points back to this wallet.
    const node = namehash(name);
    const [found] = await rpc([readAt(BASE_REGISTRY, `0x0178b8bf${node}`)]); // resolver(bytes32)
    const resolver = found?.result ? reader(found.result).address(0) : null;
    if (!resolver || ZERO.test(resolver)) return null;
    const [back] = await rpc([readAt(resolver, `0x3b3b57de${node}`)]); // addr(bytes32)
    return back?.result && reader(back.result).address(0) === address ? name : null;
  }
  async function ensName(address) {
    // reverse(bytes address, uint256 coinType 60 for Ethereum)
    const data = `0x5d78a217${pad('40')}${pad('3c')}${pad('14')}${address.slice(2).padEnd(64, '0')}`;
    const response = await fetch(ENS_RPC, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'eth_call', params: [{ to: ENS_RESOLVER, data }, 'latest'] }) });
    const r = reader((await response.json()).result);
    return safeName(r.string(r.num(0)));
  }
  const names = new Map();
  function walletName(address) {
    if (!names.has(address)) {
      names.set(address, (async () => (await baseName(address).catch(() => null)) ?? ensName(address))()
        .catch(() => { names.delete(address); return null; }));
    }
    return names.get(address);
  }
  let accountName = null;
  function showAccount() {
    walletLabel.textContent = account ? (accountName ?? short(account)) : 'Connect wallet';
    walletButton.dataset.state = account ? (onBase ? 'base' : 'other') : 'none';
    walletButton.setAttribute('aria-label', account ? `Wallet ${accountName ? `${accountName}, ` : ''}${account}${onBase ? ', on Base' : ', not on Base'}` : 'Connect wallet');
    if (account) document.getElementById('wallet-title').textContent = accountName ?? short(account);
    document.getElementById('wallet-address-row').hidden = !accountName;
    document.getElementById('wallet-address').textContent = accountName && account ? short(account) : '';
    if (lastList) render(lastList); else refreshWallet();
    fillStartBalance();
  }

  async function setAccount(provider, address) {
    chosen = provider;
    account = address ? address.toLowerCase() : null;
    accountName = null;
    if (account) {
      const asked = account;
      walletName(asked).then((name) => { if (name && account === asked) { accountName = name; showAccount(); } });
    }
    try { onBase = account ? (await provider.request({ method: 'eth_chainId' })) === CHAIN : false; } catch { onBase = false; }
    remember(account ? [...announced.values()].find((w) => w.provider === provider)?.rdns : null);
    // Follow the wallet when its account or network changes outside the page.
    if (provider?.on && !watched.has(provider)) {
      watched.add(provider);
      provider.on('accountsChanged', (accounts) => { if (chosen === provider) setAccount(provider, accounts?.[0] ?? null); });
      provider.on('chainChanged', (id) => { if (chosen === provider) { onBase = id === CHAIN; showAccount(); } });
    }
    showAccount();
  }
  // One payment at a time across both dialogs, so a second click can never redirect a payment in flight.
  let busy = false;

  // Phones with a comma decimal key type "0,005".
  const typedIn = (input) => input.value.trim().replace(',', '.');
  const toWei = (value) => {
    if (!/^\d*(\.\d{0,18})?$/.test(value) || value === '' || value === '.') return null;
    const [whole, frac = ''] = value.split('.');
    return BigInt(whole || '0') * 10n ** 18n + BigInt(frac.padEnd(18, '0'));
  };
  const toHex = (text) => [...new TextEncoder().encode(text)].map((b) => b.toString(16).padStart(2, '0')).join('');
  // ABI encoding of (string, string): two offsets, then each string's length and its bytes padded to 32.
  // ABI-encodes numbers (bigint) and strings in order, strings after the head as the ABI lays them out.
  const encodeArgs = (...args) => {
    const tails = [];
    let size = args.length * 32;
    const heads = args.map((arg) => {
      if (typeof arg !== 'string') return uintArg(arg);
      const hex = toHex(arg);
      const tail = uintArg(hex.length / 2) + hex.padEnd(Math.ceil(hex.length / 64) * 64, '0');
      const at = size;
      size += tail.length / 2;
      tails.push(tail);
      return uintArg(at);
    });
    return heads.join('') + tails.join('');
  };

  const CANCELLED = Symbol('cancelled');
  // Connects a wallet from inside a dialog, using that dialog's wallet chooser and status line.
  async function connect(flow) {
    try {
      const provider = await pickWallet(flow);
      if (provider === CANCELLED) return;
      if (!provider) { flow.say('No wallet in this browser. Open this page in your wallet app.'); return; }
      flow.say('Waiting for your wallet…');
      const [address] = await provider.request({ method: 'eth_requestAccounts' });
      await setAccount(provider, address);
      flow.say('');
    } catch (error) {
      flow.say(error?.code === 4001 ? 'Cancelled in the wallet.' : 'The wallet did not connect.');
    }
  }
  const flows = [];
  function makeFlow(prefix) {
    const flow = {
      dialog: document.getElementById(prefix),
      status: document.getElementById(`${prefix}-status`),
      wallets: document.getElementById(`${prefix}-wallets`),
      send: document.getElementById(`${prefix}-send`),
      stopPicking: null,
      say(text, link) {
        flow.status.replaceChildren(text, ...(link ? [' ', el('a', { href: link.href, textContent: link.text })] : []));
      },
    };
    flows.push(flow);
    return flow;
  }

  function pickWallet(flow) {
    const list = [...announced.values()];
    if (chosen) return Promise.resolve(chosen);
    if (list.length === 1) return Promise.resolve(chosen = list[0].provider);
    if (!list.length) return Promise.resolve(window.ethereum ?? null);
    // Several wallets: let the visitor choose instead of guessing. Closing the dialog cancels the choice.
    return new Promise((resolve) => {
      flow.stopPicking = () => { flow.wallets.hidden = true; flow.stopPicking = null; resolve(CANCELLED); };
      flow.wallets.replaceChildren(...list.map((w) => {
        const b = el('button', { type: 'button', className: 'wallet', textContent: w.name });
        b.addEventListener('click', () => { flow.wallets.hidden = true; flow.stopPicking = null; chosen = w.provider; resolve(w.provider); });
        return b;
      }));
      flow.wallets.hidden = false;
      flow.say('Choose a wallet.');
    });
  }

  async function ensureBase(provider) {
    if ((await provider.request({ method: 'eth_chainId' })) === CHAIN) return;
    try {
      await provider.request({ method: 'wallet_switchEthereumChain', params: [{ chainId: CHAIN }] });
    } catch (error) {
      if (error?.code !== 4902) throw error;
      await provider.request({ method: 'wallet_addEthereumChain', params: [{
        chainId: CHAIN, chainName: 'Base', nativeCurrency: { name: 'Ether', symbol: 'ETH', decimals: 18 },
        rpcUrls: [RPC], blockExplorerUrls: ['https://basescan.org'],
      }] });
    }
  }

  // The same contract error can mean different things per action, so an action may word some of its own.
  const revertReason = (error, own = {}) => {
    const data = String(error?.data ?? error?.error?.data ?? '').replace(/^0x/, '').slice(0, 8);
    return own[data] ?? ERRORS[data] ?? null;
  };

  // Sends calldata plus the builder code to the poidh contract and waits for it on chain.
  // Resolves to { from } once it succeeded, or null after telling the visitor why it did not.
  async function pay(flow, { data, wei, page, errors = {} }) {
    const provider = await pickWallet(flow);
    if (provider === CANCELLED) { flow.say(''); return null; }
    if (!provider) {
      flow.say('No wallet in this browser. Open this page in your wallet app, or use', page);
      return null;
    }
    flow.say('Waiting for your wallet…');
    const [from] = await provider.request({ method: 'eth_requestAccounts' });
    await ensureBase(provider);
    await setAccount(provider, from);
    const payment = { from, to: POIDH, value: `0x${wei.toString(16)}`, data: `0x${data}${BUILDER_SUFFIX}` };
    // poidh lets only a regular wallet start a bounty. The check below cannot catch a smart-contract wallet, because a
    // simulated call counts its sender as a regular one, so look at the wallet's code: none, or an EIP-7702 delegation, is fine.
    if (data.startsWith(SEL.create)) {
      const [code] = await rpc([['eth_getCode', [from, 'latest']]]).catch(() => [null]);
      const bytes = code?.result;
      if (typeof bytes !== 'string') { flow.say('Base could not be reached to check this. Nothing was sent; try again.'); return null; }
      if (bytes !== '0x' && !bytes.startsWith('0xef0100')) { flow.say(ERRORS['4b087ad8']); return null; }
    }
    // Run it against the chain first, so anything the contract would refuse fails here and not in the wallet.
    let check;
    try {
      [check] = await rpc([['eth_call', [payment, 'latest']]]);
    } catch {
      check = null;
    }
    if (!check || (!check.error && check.result === undefined)) { flow.say('Base could not be reached to check this. Nothing was sent; try again.'); return null; }
    if (check.error) {
      // Base words a short balance as "OutOfFunds"; other nodes say "insufficient funds".
      if (/insufficient funds|OutOfFunds/i.test(check.error.message ?? '')) flow.say('This wallet does not have enough ETH on Base.');
      else if (revertReason(check.error, errors)) flow.say(revertReason(check.error, errors));
      else flow.say('poidh did not accept this. Check it on', page);
      return null;
    }
    // The wallet could have been moved to another network meanwhile; poidh has the same address on Arbitrum.
    if ((await provider.request({ method: 'eth_chainId' })) !== CHAIN) { flow.say('Your wallet left Base. Switch it back to Base and try again.'); return null; }
    flow.say('Confirm in your wallet.');
    const hash = await provider.request({ method: 'eth_sendTransaction', params: [{ ...payment, chainId: CHAIN }] });
    const receiptLink = { href: `https://basescan.org/tx/${hash}`, text: 'View on Basescan' };
    flow.say('Sent. Waiting for Base…', receiptLink);
    for (let i = 0; i < 40; i++) {
      await pause(3000);
      const [receipt] = await rpc([['eth_getTransactionReceipt', [hash]]]).catch(() => [null]);
      const result = receipt?.result;
      if (!result) continue;
      if (result.status === '0x1') return { from, receiptLink };
      flow.say('The transaction failed on chain. Your ETH stayed in your wallet, apart from the fee.', receiptLink);
      return null;
    }
    flow.say('Sent, still waiting for Base to confirm.', receiptLink);
    return null;
  }

  async function run(flow, task) {
    if (busy) return;
    busy = true;
    flows.forEach((f) => f.refresh?.());
    try {
      await task();
    } catch (error) {
      flow.say(error?.code === 4001 ? 'Cancelled in the wallet.' : revertReason(error) ?? 'The wallet stopped with an error. Nothing was sent.');
    } finally {
      busy = false;
      flows.forEach((f) => f.refresh?.());
    }
  }

  function wireDialog(flow, returnFocus) {
    flow.dialog.querySelector('.close').addEventListener('click', () => flow.dialog.close());
    flow.dialog.addEventListener('click', (event) => { if (event.target === flow.dialog) flow.dialog.close(); });
    flow.dialog.addEventListener('close', () => {
      flow.stopPicking?.();
      returnFocus?.();
    });
  }

  function wire(flow, { amount, minWei, label, onOpen, ready = () => true, after = () => {} }) {
    flow.refresh = () => {
      const wei = toWei(typedIn(amount));
      // The label repeats exactly what was typed, so it always matches what the wallet will show.
      flow.send.textContent = wei && wei >= minWei ? label(typedIn(amount).replace(/^\./, '0.')) : label(null);
      flow.send.disabled = busy || !wei || wei < minWei || !ready();
      after(wei);
    };
    if (!flow.dialog || typeof flow.dialog.showModal !== 'function') return;
    amount.addEventListener('input', () => flow.refresh());
    flow.dialog.querySelectorAll('.preset').forEach((preset) => preset.addEventListener('click', () => {
      amount.value = preset.dataset.eth;
      flow.refresh();
    }));
    wireDialog(flow, () => onOpen.returnFocus?.());
  }

  // ---------- What people type: a small, safe Markdown reading, built as nodes ----------
  // Paragraphs, "- " lists, **bold** and https links; anything else stays plain text.
  function inline(text) {
    const out = [];
    const pattern = /\*\*([^*\n]+)\*\*|https:\/\/[^\s<>"]*[^\s<>".,;:!?)]/g;
    let at = 0;
    for (const match of text.matchAll(pattern)) {
      if (match.index > at) out.push(text.slice(at, match.index));
      out.push(match[1] !== undefined ? el('b', { textContent: match[1] })
        : el('a', { href: match[0], target: '_blank', rel: 'noopener noreferrer', textContent: match[0].slice(8) }));
      at = match.index + match[0].length;
    }
    if (at < text.length) out.push(text.slice(at));
    return out;
  }
  const markdown = (text) => text.trim().split(/\n\s*\n/).filter(Boolean).map((block) => {
    const lines = block.split('\n');
    if (lines.every((line) => /^\s*[-*] /.test(line))) return el('ul', {}, ...lines.map((line) => el('li', {}, ...inline(line.replace(/^\s*[-*] /, '')))));
    return el('p', {}, ...lines.flatMap((line, i) => (i ? [el('br'), ...inline(line)] : inline(line))));
  });

  // Write and Preview over one textarea; foot is a line the page adds after the text when it is sent.
  function wireViews(textarea, preview, foot = null) {
    const buttons = textarea.closest('.field').querySelectorAll('.view');
    const show = (view) => {
      buttons.forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.view === view)));
      if (view === 'preview') preview.replaceChildren(...markdown(textarea.value), ...(foot ? [el('p', { className: 'md-foot', textContent: foot })] : []));
      textarea.hidden = view === 'preview';
      preview.hidden = view !== 'preview';
    };
    buttons.forEach((button) => button.addEventListener('click', () => show(button.dataset.view)));
    return show;
  }

  // ---------- Adding ETH to a bounty ----------
  const giveFlow = makeFlow('give');
  const giveAmount = document.getElementById('give-amount');
  let current = null;
  let opener = null;
  const giveFocus = {
    // The shelf may have been redrawn after a payment, so focus the button for the same bounty as it is now.
    returnFocus: () => ((current && document.querySelector(`#bounty-${current.id} .get`)) ?? opener)?.focus(),
  };
  wire(giveFlow, { amount: giveAmount, minWei: MIN_WEI, label: (text) => (text ? `Add ${text} ETH` : 'Add ETH'), onOpen: giveFocus });

  function openGive(b, button) {
    // Browsers without <dialog> still get a way to add ETH: poidh's own page.
    if (typeof giveFlow.dialog?.showModal !== 'function') { location.href = poidhUrl(b.id); return; }
    // While a payment is under way the dialog stays on that bounty, so a second click cannot redirect it.
    if (busy) { if (current) giveFlow.dialog.showModal(); return; }
    current = b;
    opener = button;
    document.getElementById('give-title').textContent = b.name;
    document.getElementById('give-pot').textContent = `${eth(b.wei)} so far${b.people ? ` · ${peopleText(b.people)}` : ''}`;
    document.getElementById('give-poidh').href = poidhUrl(b.id);
    giveFlow.wallets.hidden = true;
    giveFlow.say('');
    giveFlow.refresh();
    giveFlow.dialog.showModal();
    giveAmount.focus();
    giveAmount.select();
  }

  giveFlow.dialog?.querySelector('form').addEventListener('submit', (event) => {
    event.preventDefault();
    const wei = toWei(typedIn(giveAmount));
    if (!current || !wei || wei < MIN_WEI) return;
    // Everything below uses this bounty, whatever the dialog shows by the time the wallet answers.
    const target = current;
    run(giveFlow, async () => {
      const done = await pay(giveFlow, { data: `${SEL.join}${uintArg(target.id)}`, wei, page: { href: poidhUrl(target.id), text: 'poidh' } });
      if (!done) return;
      giveFlow.say('Added. Thank you.', done.receiptLink);
      refresh().catch(() => {});
    });
  });

  // ---------- Starting a bounty ----------
  // Each template is the wording of a bounty that worked, with the parts to change in brackets.
  const ACCEPTED = 'Accepted when: the .fapack is built with the Field Atlas pack tools, imports in the app with Library then Import, and answers a few questions with citations to the right sections. Claim here with the link to the pack.';
  const TEMPLATES = {
    textbook: { name: '[Subject]: [book title]', about: `[Book title and edition] as an offline collection, like the existing Mathematics: Prealgebra pack: every chapter split into sections, with [worked examples, exercises, the glossary].\n\nSource: [link to the book], licence [CC BY 4.0], credited inside the pack.\n\n${ACCEPTED}` },
    guide: { name: '[Topic] guide', about: `[Guide name] as an offline collection, like the existing Practical skills pack: one passage per step or section, with [the checklists and warnings].\n\nSource: [link to the guide], licence [CC BY 4.0], credited inside the pack.\n\n${ACCEPTED}` },
    places: { name: 'Places: [what and where]', about: '[Kind of places] in [area] as an offline collection, like the existing Essentials pack: one entry per place with its name, location and [opening hours and phone].\n\nSource: [link to the data], licence [ODbL], credited inside the pack.\n\nAccepted when: the .fapack is built with the Field Atlas pack tools, imports in the app with Library then Import, and answers a few questions about the places with citations to the right entries. Claim here with the link to the pack.' },
    blank: { name: '', about: '' },
  };
  // A [part] to fill; a Markdown link's [text](url) is not one.
  const BLANK = /\[[^[\]\n]{1,80}\](?!\()/g;
  const blanks = (text) => text.match(BLANK)?.length ?? 0;
  // Selects the first [part] at or after from, so Tab walks through what is left to fill.
  function selectBlank(input, from) {
    if (input.hidden) return false;
    const next = [...input.value.matchAll(BLANK)].find((match) => match.index >= from);
    if (!next) return false;
    input.focus();
    input.setSelectionRange(next.index, next.index + next[0].length);
    return true;
  }
  const WANTED_LINE = 'Wanted on https://www.getfieldatlas.com/depack/wanted/';
  // Phones have no Tab key; a tap inside a part selects it instead.
  const TOUCH = matchMedia('(pointer: coarse)').matches;
  const startFlow = makeFlow('start');
  const startOpen = document.getElementById('start-open');
  const startName = document.getElementById('start-name');
  const startAbout = document.getElementById('start-about');
  const startAmount = document.getElementById('start-amount');
  const startLeft = () => blanks(startName.value) + blanks(startAbout.value);
  wire(startFlow, {
    amount: startAmount, minWei: MIN_BOUNTY_WEI,
    label: (text) => (text ? `Start it with ${text} ETH` : 'Start the bounty'),
    onOpen: { returnFocus: () => startOpen?.focus() },
    ready: () => !!account && !!startName.value.trim() && !!startAbout.value.trim() && !startLeft(),
    after: (wei) => {
      const left = startLeft();
      document.getElementById('start-left').textContent = left ? `${left} ${left === 1 ? 'part' : 'parts'} left to fill. ${TOUCH ? 'Tap one to type over it.' : 'Tab jumps to the next one.'}` : '';
      document.getElementById('start-gets').textContent = wei ? eth(wei - (wei * 250n) / 10000n, true) : '';
    },
  });

  async function fillStartBalance() {
    const row = document.getElementById('start-balance-row');
    if (!row) return;
    // No wallet yet: the dialog offers to connect one first, and the start button follows once it is known.
    document.getElementById('start-connect').hidden = !!account;
    document.getElementById('start-send').hidden = !account;
    // Called on every wallet change, which can come before the dialog has ever been opened.
    if (!document.getElementById('start')?.open) return;
    startFlow.refresh();
    // The last wallet's balance never shows against a new one; the row waits for this wallet's answer.
    row.hidden = true;
    document.getElementById('start-balance').textContent = '';
    if (!account) return;
    const asked = account;
    const [reply] = await rpc([['eth_getBalance', [asked, 'latest']]]).catch(() => [null]);
    if (asked !== account) return;
    row.hidden = !reply?.result;
    if (reply?.result) document.getElementById('start-balance').textContent = eth(BigInt(reply.result));
  }

  if (startOpen) startOpen.hidden = typeof startFlow.dialog?.showModal !== 'function';
  if (startOpen && typeof startFlow.dialog?.showModal === 'function') {
    const showStart = wireViews(startAbout, document.getElementById('start-preview'), WANTED_LINE);
    const kinds = startFlow.dialog.querySelectorAll('.kind');
    const firstBlank = () => selectBlank(startName, 0) || selectBlank(startAbout, 0);
    const useTemplate = (kind) => {
      kinds.forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.kind === kind)));
      startName.value = TEMPLATES[kind].name;
      startAbout.value = TEMPLATES[kind].about;
      showStart('write');
      startFlow.refresh();
      if (!firstBlank()) startName.focus();
    };
    // Typed text is only replaced after asking; an untouched template or an empty form switches at once.
    const untouched = () => Object.values(TEMPLATES).some((t) => t.name === startName.value && t.about === startAbout.value);
    kinds.forEach((button) => button.addEventListener('click', () => {
      if (!untouched() && !window.confirm(`Replace your text with the ${button.textContent} template?`)) return;
      useTemplate(button.dataset.kind);
    }));
    document.getElementById('start-connect').addEventListener('click', async () => {
      if (busy) return;
      await connect(startFlow);
      // The connect button has just hidden itself; focus moves on to the next part to fill, or the start button.
      if (account && startFlow.dialog.open && !firstBlank()) startFlow.send.focus();
    });
    for (const input of [startName, startAbout]) {
      input.addEventListener('input', () => startFlow.refresh());
      input.addEventListener('keydown', (event) => {
        if (event.key !== 'Tab' || event.shiftKey || event.altKey || event.ctrlKey || event.metaKey) return;
        const found = selectBlank(input, input.selectionEnd) || (input === startName && selectBlank(startAbout, 0));
        if (found) event.preventDefault();
      });
      // A click inside a [part] selects all of it, ready to type over.
      input.addEventListener('click', () => {
        if (input.selectionStart !== input.selectionEnd) return;
        const at = input.selectionStart;
        const hit = [...input.value.matchAll(BLANK)].find((match) => match.index < at && at < match.index + match[0].length);
        if (hit) input.setSelectionRange(hit.index, hit.index + hit[0].length);
      });
    }
    startOpen.addEventListener('click', () => {
      if (!busy) { startFlow.wallets.hidden = true; startFlow.say(''); }
      startFlow.dialog.showModal();
      fillStartBalance();
      if (!startName.value && !startAbout.value) useTemplate([...kinds].find((button) => button.getAttribute('aria-pressed') === 'true').dataset.kind);
      else { startFlow.refresh(); if (!firstBlank()) startName.focus(); }
    });
    startFlow.dialog.querySelector('form').addEventListener('submit', (event) => {
      event.preventDefault();
      const name = startName.value.trim();
      const about = startAbout.value.trim();
      const wei = toWei(typedIn(startAmount));
      if (!name || !about) { startFlow.say('Give the collection a title and say what it should cover.'); (name ? startAbout : startName).focus(); return; }
      if (startLeft()) { firstBlank(); return; }
      if (!account || !wei || wei < MIN_BOUNTY_WEI) return;
      // The page's address in the description is what lets this page find and list the bounty later.
      const description = `${about}\n\n${WANTED_LINE}`;
      run(startFlow, async () => {
        const done = await pay(startFlow, { data: `${SEL.create}${encodeArgs(name, description)}`, wei, page: { href: 'https://poidh.xyz', text: 'poidh' } });
        if (!done) return;
        // The newest bounty from this wallet with this name is the one just made.
        const [list] = await rpc([call(SEL.byUser, pad(done.from.toLowerCase().slice(2)) + uintArg(0))]).catch(() => [null]);
        const made = list?.result ? decodeBounties(list.result).find((b) => b.name === name) : null;
        startFlow.say('Your bounty is live. It shows on this page after a short review.', made ? { href: poidhUrl(made.id), text: 'See it on poidh' } : done.receiptLink);
        startName.value = '';
        startAbout.value = '';
        fillStartBalance();
      });
    });
  }

  // ---------- The connected wallet: balance on poidh, refunds, withdraw ----------
  const walletFlow = makeFlow('wallet');
  const walletAccount = document.getElementById('wallet-account');
  const refunds = document.getElementById('wallet-refunds');
  walletFlow.refresh = () => {
    walletFlow.send.disabled = busy;
    document.getElementById('wallet-disconnect').disabled = busy;
    refunds.querySelectorAll('button').forEach((b) => { b.disabled = busy; });
  };

  // The open panel follows the wallet's account and network and the latest read of the bounties.
  function refreshWallet() {
    if (document.getElementById('wallet')?.open) fillWallet();
  }

  let filledFor = null;
  async function fillWallet() {
    document.getElementById('wallet-title').textContent = account ? (accountName ?? short(account)) : 'Connect a wallet';
    walletAccount.hidden = !account;
    if (!account) return;
    document.getElementById('wallet-network').textContent = onBase ? 'Base' : 'Not Base; it switches when you send';
    const asked = account;
    const balance = document.getElementById('wallet-balance');
    // A refresh for the same wallet keeps the last balance on screen until the new one arrives.
    if (filledFor !== asked) {
      balance.textContent = '…';
      walletFlow.send.hidden = true;
    }
    // ETH still held by live bounties is not withdrawable, so it gets its own row instead of hiding in the balance.
    const locked = (lastList ?? []).filter((b) => b.state === 'open' || b.state === 'voting')
      .reduce((sum, b) => sum + (b.entries.find((e) => e.address === account)?.amount
        ?? (b.issuer.toLowerCase() === account ? b.amount : 0n)), 0n);
    document.getElementById('wallet-locked-row').hidden = !locked;
    document.getElementById('wallet-locked').textContent = locked ? eth(locked) : '';
    const [reply] = await rpc([call(SEL.balance, pad(account.slice(2)))]).catch(() => [null]);
    // The wallet may have switched accounts while this was read; the newer fill shows that account instead.
    if (account !== asked) return;
    filledFor = asked;
    const wei = reply?.result ? BigInt(reply.result) : null;
    balance.textContent = wei === null ? 'could not be read' : eth(wei);
    walletFlow.send.hidden = !wei;
    if (wei) walletFlow.send.textContent = `Withdraw ${eth(wei)}`;
    // ETH this wallet put into a bounty that was then cancelled waits here until it is taken back.
    const back = (lastList ?? []).filter((b) => b.state === 'cancelled')
      .flatMap((b) => b.entries.filter((e) => e.address === account).map((e) => ({ b, amount: e.amount })));
    refunds.replaceChildren(...back.map(({ b, amount: wei }) => {
      const take = el('button', { type: 'button', className: 'get', textContent: 'Take back' });
      take.addEventListener('click', () => run(walletFlow, async () => {
        const done = await pay(walletFlow, { data: `${SEL.refund}${uintArg(b.id)}`, wei: 0n, page: { href: poidhUrl(b.id), text: 'poidh' } });
        if (!done) return;
        walletFlow.say('Taken back into your poidh balance. Withdraw it below.', done.receiptLink);
        refresh().catch(() => {});
      }));
      return el('div', { className: 'refund' }, el('span', { textContent: `${eth(wei)} from ${b.name}, cancelled` }), take);
    }));
    walletFlow.refresh();
  }

  if (walletButton) walletButton.hidden = typeof walletFlow.dialog?.showModal !== 'function';
  if (walletButton && typeof walletFlow.dialog?.showModal === 'function') {
    wireDialog(walletFlow, () => walletButton.focus());
    walletButton.addEventListener('click', async () => {
      walletFlow.say('');
      walletFlow.wallets.hidden = true;
      walletFlow.dialog.showModal();
      fillWallet();
      if (account || busy) return;
      connect(walletFlow);
    });
    walletFlow.send.addEventListener('click', () => run(walletFlow, async () => {
      const done = await pay(walletFlow, { data: SEL.withdraw, wei: 0n, page: { href: 'https://poidh.xyz', text: 'poidh' } });
      if (!done) return;
      walletFlow.say('Withdrawn to your wallet.', done.receiptLink);
      fillWallet();
    }));
    document.getElementById('wallet-disconnect').addEventListener('click', async () => {
      // Wallets that support it forget this site's permission too; the page forgets the account either way.
      try { await chosen?.request({ method: 'wallet_revokePermissions', params: [{ eth_accounts: {} }] }); } catch { /* not supported */ }
      chosen = null;
      await setAccount(null, null);
      walletFlow.dialog.close();
    });
    // A wallet that already allowed this site reconnects quietly, without a prompt.
    const known = remembered();
    const again = known && [...announced.values()].find((w) => w.rdns === known);
    if (again) {
      again.provider.request({ method: 'eth_accounts' })
        .then((accounts) => { if (accounts?.[0] && !account) setAccount(again.provider, accounts[0]); })
        .catch(() => {});
    }
  }

  // ---------- Cancelling a bounty (its creator only) ----------
  const cancelFlow = makeFlow('cancel');
  let cancelling = null;
  let cancelOpener = null;
  cancelFlow.refresh = () => { cancelFlow.send.disabled = busy; };
  if (typeof cancelFlow.dialog?.showModal === 'function') {
    wireDialog(cancelFlow, () => cancelOpener?.focus());
    document.getElementById('cancel-keep').addEventListener('click', () => cancelFlow.dialog.close());
    cancelFlow.send.addEventListener('click', () => {
      const target = cancelling;
      if (!target) return;
      run(cancelFlow, async () => {
        const data = `${target.open ? SEL.cancelOpen : SEL.cancelSolo}${uintArg(target.id)}`;
        const done = await pay(cancelFlow, {
          data, wei: 0n, page: { href: poidhUrl(target.id), text: 'poidh' },
          errors: { '0d0d217e': 'Only the wallet that started this bounty can cancel it.', b6c99482: 'A claim is being voted on, so it cannot be cancelled now.' },
        });
        if (!done) return;
        cancelFlow.say('Cancelled. Your ETH is in your poidh balance; withdraw it from the wallet button at the top.', done.receiptLink);
        refresh().catch(() => {});
      });
    });
  }

  function openCancel(b, button) {
    if (typeof cancelFlow.dialog?.showModal !== 'function') { location.href = poidhUrl(b.id); return; }
    if (busy) return;
    cancelling = b;
    cancelOpener = button;
    document.getElementById('cancel-title').textContent = b.name;
    const yours = b.entries.find((e) => e.address === account)?.amount ?? b.wei;
    const others = b.people > 1 ? ' Others who added ETH can take theirs back from their wallet button here.' : '';
    document.getElementById('cancel-what').textContent = `Your ${eth(yours)} goes back to your poidh balance, ready to withdraw.${others} This cannot be undone.`;
    cancelFlow.wallets.hidden = true;
    cancelFlow.say('');
    cancelFlow.refresh();
    cancelFlow.dialog.showModal();
  }

  // ---------- Claims and votes ----------
  const claimsFlow = makeFlow('claims');
  const claimsList = document.getElementById('claims-list');
  const claimForm = document.getElementById('claims-form');
  const voteBox = document.getElementById('claims-vote');
  let claimsFor = null;
  let claimsOpener = null;
  let draftFor = null;
  const decideBox = document.getElementById('claims-decide');
  const decideSend = document.getElementById('claims-decide-send');
  const confirmBox = document.getElementById('claims-confirm');
  const picked = () => claimsList.querySelector('input[name="claim-pick"]:checked');
  let shown = { claims: [], external: true };
  claimsFlow.refresh = () => {
    claimsFlow.send.disabled = busy;
    claimsFlow.dialog.querySelectorAll('#claims-confirm-send, #claims-yes, #claims-no, #claims-settle, input[name="claim-pick"]').forEach((control) => { control.disabled = busy; });
    decideSend.disabled = busy || !picked();
  };
  // Pack links in a claim open only for the two hosts packs come from; anything else stays plain text.
  const packLink = (text) => text.match(/https:\/\/(?:github\.com|huggingface\.co)\/[^\s<>"]*[^\s<>".,;:!?)]/)?.[0] ?? null;
  const decider = (b) => b.state === 'open' && account && account === b.issuer.toLowerCase();

  const WANTED_URL = 'https://www.getfieldatlas.com/depack/wanted/';
  const CLAIMED_LINE = `Claimed on ${WANTED_URL}`;
  // poidh mints every claim as an NFT whose image is the claim's uri. Claims here carry no proof image, so the NFT
  // shows the site's card; the proof is the pack itself, linked in the description.
  const CLAIM_IMAGE = 'https://www.getfieldatlas.com/assets/social-card.png';
  const claimAbout = document.getElementById('claim-about');
  const showClaimText = wireViews(claimAbout, document.getElementById('claim-preview'), CLAIMED_LINE);

  function claimRow(b, c, pickable) {
    const link = packLink(c.description);
    const about = c.description.split('\n')[0].slice(0, 200);
    const who = el('span', { className: 'claim-who', textContent: short(c.issuer), dataset: { address: c.issuer } });
    const text = el('div', { className: 'claim-text' },
      el('b', { textContent: c.name }),
      el('span', { className: 'claim-facts' }, 'by ', who, c.createdAt ? ` · ${dateFmt.format(new Date(c.createdAt * 1000))}` : ''),
      el('span', { className: 'claim-about', textContent: about }),
      // The link's own address is its label, so a claim's wording cannot dress up where it leads.
      link ? el('a', { className: 'claim-link', href: link, target: '_blank', rel: 'noopener noreferrer', textContent: link.slice(8) }) : null);
    const state = c.accepted ? 'Accepted' : b.votingClaim === c.id ? 'In the vote' : null;
    // The creator picks one claim, then confirms; a button on every row made a wrong tap too easy.
    if (pickable && !state) {
      const radio = el('input', { type: 'radio', name: 'claim-pick', value: String(c.id), className: 'claim-radio' });
      radio.addEventListener('change', () => { confirmBox.hidden = true; decideBox.hidden = false; claimsFlow.refresh(); });
      return el('li', { className: 'claim is-pickable', id: `claim-${c.id}` }, el('label', { className: 'claim-pick' }, radio, text));
    }
    return el('li', { className: 'claim', id: `claim-${c.id}` }, text, state ? el('span', { className: 'claim-state', textContent: state }) : null);
  }

  async function fillClaims(b) {
    claimsList.replaceChildren(el('li', { className: 'quiet', textContent: 'Reading the claims from Base…' }));
    let replies;
    try {
      replies = await rpc([call(SEL.claims, uintArg(b.id) + uintArg(0)), call(SEL.everExternal, uintArg(b.id))]);
    } catch {
      replies = [];
    }
    if (claimsFor !== b) return;
    if (!replies[0]?.result) {
      claimsList.replaceChildren(el('li', { className: 'quiet' }, 'Base could not be reached just now. The claims are also on ', el('a', { href: poidhUrl(b.id), textContent: 'poidh' }), '.'));
      return;
    }
    const claims = decodeClaims(replies[0].result);
    const external = replies[1]?.result ? BigInt(replies[1].result) !== 0n : true;
    shown = { claims, external };
    const voted = claims.find((c) => c.id === b.votingClaim);
    document.getElementById('claims-vote-what').textContent = voted ? voted.name : `Claim ${b.votingClaim}`;
    const pickable = decider(b);
    if (pickable) claimsList.setAttribute('role', 'radiogroup'); else claimsList.removeAttribute('role');
    // Only the creator reads every claim here, so this page does not hand early claims to later makers (they stay
    // public on chain and on poidh); others see their own claims and, during a vote, the claim they are voting on.
    const creator = !!account && account === b.issuer.toLowerCase();
    const visible = creator ? claims : claims.filter((c) => c.id === b.votingClaim || (account && c.issuer === account));
    const others = claims.length - visible.length;
    claimsList.replaceChildren(...visible.map((c) => claimRow(b, c, pickable)));
    if (!claims.length) claimsList.append(el('li', { className: 'quiet', textContent: 'No claims yet.' }));
    else if (others) claimsList.append(el('li', { className: 'quiet', textContent: `${visible.length ? `${others} more` : others} ${others === 1 ? 'claim' : 'claims'} so far.` }));
    if (creator) claimsList.append(el('li', { className: 'claim-more' }, el('a', { href: poidhUrl(b.id), textContent: 'Proof images and every claim on poidh' })));
    decideSend.textContent = external ? 'Put this claim to a vote' : 'Accept this claim';
    decideBox.hidden = !(pickable && claims.some((c) => !c.accepted && c.id !== b.votingClaim));
    confirmBox.hidden = true;
    // Claimers show their Basename or ENS name once it is read.
    claimsList.querySelectorAll('.claim-who').forEach((span) => {
      walletName(span.dataset.address).then((name) => { if (name && span.isConnected) span.textContent = name; });
    });
    claimsFlow.refresh();
  }

  // What confirming does, in the contract's terms: the creator's share votes yes, and a vote passes when yes outweighs no.
  function confirmText(b, c, external) {
    if (!external) return `This pays ${eth(b.wei - (b.wei * 250n) / 10000n)} (the ${eth(b.wei)} reward less poidh\u2019s 2.5% fee) to ${short(c.issuer)} now. It cannot be undone.`;
    const yours = b.entries.find((e) => e.address === account)?.amount ?? 0n;
    const others = b.wei - yours;
    const how = yours > others
      ? `Your ${eth(yours)} counts as yes and outweighs the other backers\u2019 ${eth(others)}, so this claim will be paid after two days.`
      : `Your ${eth(yours)} counts as yes. Backers have two days to vote, and it is paid unless more ETH votes no than yes.`;
    return `${how} No other claim can be decided until the vote ends.`;
  }

  function showClaims(b) {
    claimsFor = b;
    document.getElementById('claims-title').textContent = b.name;
    const voting = b.state === 'voting';
    voteBox.hidden = !voting;
    if (voting) {
      const ended = b.deadline * 1000 <= Date.now();
      document.getElementById('claims-yes-total').textContent = eth(b.yes);
      document.getElementById('claims-no-total').textContent = eth(b.no);
      document.getElementById('claims-ends').textContent = b.deadline ? (ended ? 'Ended' : dateFmt.format(new Date(b.deadline * 1000))) : '';
      document.getElementById('claims-yes').hidden = ended;
      document.getElementById('claims-no').hidden = ended;
      document.getElementById('claims-settle').hidden = !ended;
    }
    // The wallet decides the view: the creator picks a claim, anyone else claims while no vote is running.
    document.getElementById('claims-connect').hidden = !!account || b.state !== 'open';
    claimForm.hidden = !account || b.state !== 'open' || account === b.issuer.toLowerCase();
    fillClaims(b);
  }

  // The open dialog redraws after every fresh read and every wallet change, from the bounty as it is now.
  function refreshClaims() {
    if (!claimsFlow.dialog?.open || !claimsFor || busy) return;
    showClaims(lastList?.find((item) => item.id === claimsFor.id) ?? claimsFor);
  }

  function decide(b, c, external) {
    run(claimsFlow, async () => {
      const data = `${external ? SEL.startVote : SEL.accept}${uintArg(b.id)}${uintArg(c.id)}`;
      const done = await pay(claimsFlow, { data, wei: 0n, page: { href: poidhUrl(b.id), text: 'poidh' },
        errors: { '0d0d217e': 'Only the wallet that started this bounty can decide its claims.', b6c99482: 'A claim is already being voted on.' } });
      if (!done) return;
      confirmBox.hidden = true;
      claimsFlow.say(external ? 'The vote is open for two days.' : 'Accepted. The reward, less poidh\u2019s 2.5% fee, is in the claimer\u2019s poidh balance.', done.receiptLink);
      refresh().catch(() => {});
    });
  }

  function castVote(yes) {
    const b = claimsFor;
    if (!b) return;
    run(claimsFlow, async () => {
      const done = await pay(claimsFlow, { data: `${SEL.vote}${uintArg(b.id)}${uintArg(yes ? 1 : 0)}`, wei: 0n, page: { href: poidhUrl(b.id), text: 'poidh' },
        errors: { '2fb19d77': 'Only wallets that added ETH before the vote started can vote.' } });
      if (!done) return;
      claimsFlow.say('Your vote is in.', done.receiptLink);
      refresh().catch(() => {});
    });
  }

  if (typeof claimsFlow.dialog?.showModal === 'function') {
    wireDialog(claimsFlow, () => ((claimsFor && document.querySelector(`#bounty-${claimsFor.id} .onpoidh`)) ?? claimsOpener)?.focus());
    document.getElementById('claims-connect').addEventListener('click', () => { if (!busy) connect(claimsFlow); });
    decideSend.addEventListener('click', () => {
      const c = shown.claims.find((item) => String(item.id) === picked()?.value);
      if (!claimsFor || !c) return;
      document.getElementById('claims-confirm-text').textContent = confirmText(claimsFor, c, shown.external);
      document.getElementById('claims-confirm-send').textContent = shown.external ? 'Start the vote' : 'Accept and pay';
      decideBox.hidden = true;
      confirmBox.hidden = false;
      document.getElementById('claims-confirm-send').focus();
    });
    document.getElementById('claims-confirm-back').addEventListener('click', () => {
      confirmBox.hidden = true;
      decideBox.hidden = false;
      decideSend.focus();
    });
    document.getElementById('claims-confirm-send').addEventListener('click', () => {
      const c = shown.claims.find((item) => String(item.id) === picked()?.value);
      if (claimsFor && c) decide(claimsFor, c, shown.external);
    });
    document.getElementById('claims-yes').addEventListener('click', () => castVote(true));
    document.getElementById('claims-no').addEventListener('click', () => castVote(false));
    document.getElementById('claims-settle').addEventListener('click', () => {
      const b = claimsFor;
      if (!b) return;
      run(claimsFlow, async () => {
        const done = await pay(claimsFlow, { data: `${SEL.settle}${uintArg(b.id)}`, wei: 0n, page: { href: poidhUrl(b.id), text: 'poidh' },
          errors: { b6c99482: 'The vote is still running.' } });
        if (!done) return;
        claimsFlow.say('Settled.', done.receiptLink);
        refresh().catch(() => {});
      });
    });
    claimForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const b = claimsFor;
      if (!b) return;
      const name = document.getElementById('claim-name').value.trim();
      const about = claimAbout.value.trim();
      if (!name) { claimsFlow.say('Give the claim a title.'); return; }
      if (!about) { claimsFlow.say('Say what the pack holds, with its link.'); return; }
      run(claimsFlow, async () => {
        const description = `${about}\n\n${CLAIMED_LINE}`;
        const done = await pay(claimsFlow, { data: `${SEL.claim}${encodeArgs(b.id, name, description, CLAIM_IMAGE)}`, wei: 0n, page: { href: poidhUrl(b.id), text: 'poidh' } });
        if (!done) return;
        claimForm.reset();
        showClaimText('write');
        claimsFlow.say('Claimed. The bounty’s creator decides, or its funders vote.', done.receiptLink);
        refresh().catch(() => {});
      });
    });
  }

  function openClaims(b, button) {
    if (typeof claimsFlow.dialog?.showModal !== 'function') { location.href = poidhUrl(b.id); return; }
    // While something is being sent the dialog stays on that bounty.
    if (busy) { if (claimsFor) claimsFlow.dialog.showModal(); return; }
    claimsOpener = button;
    claimsFlow.wallets.hidden = true;
    claimsFlow.say('');
    // A draft belongs to the bounty it was written for; another bounty starts empty.
    if (draftFor !== b.id) { claimForm.reset(); showClaimText('write'); draftFor = b.id; }
    showClaims(b);
    claimsFlow.refresh();
    claimsFlow.dialog.showModal();
  }

  quiet('Reading the open bounties from Base.');
  // A return to this page during the visit shows the last list at once; a fresh read from Base then replaces it.
  const cached = stashed();
  if (cached) render(cached);
  refresh().catch(() => {
    document.getElementById('open-shelf').removeAttribute('data-loading');
    if (!cached) document.getElementById('totals').hidden = true;
    quiet('Base could not be reached just now. The bounties are also on', { href: 'https://poidh.xyz', text: 'poidh' });
  });
})();
