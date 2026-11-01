// Fills the contributor card from rewards.json, the public ledger kept in the repository. The card already says
// everything it needs without JS; this only adds the pool wallet and the payments made so far, newest first, a page
// at a time.
const ledger = document.querySelector('#rewards-ledger');
const pool = document.querySelector('#rewards-pool');
const total = document.querySelector('#rewards-total');
const pager = document.querySelector('.ledger-pager');
const PAGE = 5;
if (ledger && pool) {
  fetch('rewards.json', { cache: 'no-store' })
    .then(response => (response.ok ? response.json() : Promise.reject(new Error(String(response.status)))))
    .then(({ poolWallet, rewards, policy }) => {
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
      // Newest first: by date, and among payments of one day the one written into the ledger last.
      const valid = (Array.isArray(rewards) ? rewards : [])
        .map((r, order) => ({ r, order }))
        .filter(({ r }) => typeof r.link === 'string' && inRepo(r.link) && /^0x[0-9a-fA-F]{64}$/.test(r.tx || ''))
        .sort((a, b) => String(b.r.date).localeCompare(String(a.r.date)) || b.order - a.order)
        .map(({ r }) => r);
      if (valid.length === 0) return;

      // The maintainer's own payment test is listed, but it paid no contributor.
      const maintainers = new Set((policy?.maintainers || []).map(m => String(m).toLowerCase()));
      const paid = valid.filter(r => !maintainers.has(String(r.contributor).toLowerCase()))
        .reduce((sum, r) => sum + (Number(r.amount) || 0), 0);
      if (total && paid > 0) {
        const amount = document.createElement('strong');
        amount.textContent = `${paid.toLocaleString('en')} ATLAS`;
        total.replaceChildren(amount, ' paid to contributors so far.');
        total.hidden = false;
      }

      const rowFor = r => {
        const row = document.createElement('tr');
        const work = document.createElement('a');
        // "proof" stands in when the pull request page is gone (its author's account was suspended); the reward tools
        // still match the row by its pull request link.
        work.href = typeof r.proof === 'string' && inRepo(r.proof) ? r.proof : r.link;
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
      };
      const pages = Math.ceil(valid.length / PAGE);
      const range = pager?.querySelector('#ledger-range');
      const [newer, older] = pager ? pager.querySelectorAll('[data-step]') : [];
      let page = 0;
      const show = () => {
        const from = page * PAGE;
        body.replaceChildren(...valid.slice(from, from + PAGE).map(rowFor));
        if (!pager) return;
        range.textContent = `${from + 1}–${Math.min(from + PAGE, valid.length)} of ${valid.length}`;
        newer.disabled = page === 0;
        older.disabled = page === pages - 1;
      };
      show();
      if (pager && pages > 1) {
        pager.hidden = false;
        pager.addEventListener('click', event => {
          const button = event.target.closest('[data-step]');
          if (!button || button.disabled) return;
          page = Math.min(pages - 1, Math.max(0, page + Number(button.dataset.step)));
          show();
          // A button that has just been disabled would drop focus to the page; the other one keeps it in the pager.
          if (button.disabled) (button === newer ? older : newer).focus();
        });
      }
    })
    .catch(() => { /* the static text stays: the ledger is also readable on GitHub */ });
}
