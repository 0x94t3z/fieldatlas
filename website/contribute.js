// Fills the contributor card from rewards.json, the public ledger kept in the repository. The card already says
// everything it needs without JS; this only adds the pool wallet and the payments made so far.
const ledger = document.querySelector('#rewards-ledger');
const pool = document.querySelector('#rewards-pool');
if (ledger && pool) {
  fetch('rewards.json', { cache: 'no-store' })
    .then(response => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
    .then(({ poolWallet, rewards }) => {
      if (poolWallet) {
        const link = document.createElement('a');
        link.href = `https://basescan.org/address/${poolWallet}`;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        // In full: a shortened address is what look-alike (address-poisoning) addresses imitate.
        link.textContent = poolWallet;
        pool.replaceChildren('Pool wallet (Safe on Base): ', link);
      }
      const body = ledger.querySelector('tbody');
      // Only well-formed entries that point at this repository and a real transaction hash are shown.
      const inRepo = link => {
        try {
          const url = new URL(link);
          return url.origin === 'https://github.com' && url.pathname.startsWith('/0x94t3z/fieldatlas/') && !url.pathname.includes('/../');
        } catch { return false; }
      };
      const valid = (Array.isArray(rewards) ? rewards : []).filter(r =>
        typeof r.link === 'string' && inRepo(r.link) && /^0x[0-9a-fA-F]{64}$/.test(r.tx || ''));
      if (valid.length === 0) return;
      body.replaceChildren(...valid.map(r => {
        const row = document.createElement('tr');
        const work = document.createElement('a');
        work.href = r.link;
        work.textContent = r.work;
        const tx = document.createElement('a');
        tx.href = `https://basescan.org/tx/${r.tx}`;
        tx.textContent = 'View';
        for (const cell of [r.date, r.contributor, work, Number(r.amount).toLocaleString('en'), tx]) {
          const td = document.createElement('td');
          td.append(cell);
          row.append(td);
        }
        // Amounts line up on their last digit.
        row.children[3].className = 'num';
        return row;
      }));
    })
    .catch(() => { /* the static text stays: the ledger is also readable on GitHub */ });
}
