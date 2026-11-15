// Search and category filter for the library list. Without this script every collection is still listed.
(() => {
  const form = document.querySelector('.controls');
  const rows = [...document.querySelectorAll('.book')];
  if (!form || !rows.length) return;

  const query = document.getElementById('q');
  const clear = document.getElementById('clear');
  const status = document.getElementById('status');
  const empty = document.getElementById('empty');
  const groups = [...document.querySelectorAll('.group')];
  const chips = [...document.querySelectorAll('.chip')];
  let category = 'all';

  const fold = (text) => text.toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '');
  const size = (bytes) => bytes >= 1e9 ? `${(bytes / 1e9).toFixed(2)} GB`
    : bytes >= 1e7 ? `${Math.round(bytes / 1e6)} MB`
    : bytes >= 1e6 ? `${(bytes / 1e6).toFixed(1)} MB` : `${Math.round(bytes / 1e3)} KB`;

  function apply({ remember = true } = {}) {
    // Every word must appear somewhere in the collection's text; "drinking water" narrows, it does not widen.
    const words = fold(query.value).split(/\s+/).filter(Boolean);
    const shown = rows.filter((row) =>
      (category === 'all' || row.dataset.category === category)
      && words.every((word) => row.dataset.text.includes(word)));
    const visible = new Set(shown);
    rows.forEach((row) => { row.hidden = !visible.has(row); });
    groups.forEach((group) => {
      const count = group.querySelectorAll('.book:not([hidden])').length;
      group.hidden = count === 0;
      group.querySelector('.count').textContent = count;
    });

    // The header already gives the totals; the line speaks only once something is filtered out.
    const bytes = shown.reduce((sum, row) => sum + Number(row.dataset.bytes), 0);
    status.textContent = shown.length === rows.length || !shown.length ? ''
      : `${shown.length} of ${rows.length} collections · ${size(bytes)} to download`;
    empty.hidden = shown.length > 0;
    clear.hidden = !query.value;
    chips.forEach((chip) => chip.setAttribute('aria-pressed', String(chip.dataset.category === category)));

    if (remember) {
      const params = new URLSearchParams();
      if (query.value.trim()) params.set('q', query.value.trim());
      if (category !== 'all') params.set('category', category);
      const search = params.toString();
      history.replaceState(null, '', search ? `?${search}` : location.pathname);
    }
  }

  // A shared link opens with the same search.
  const params = new URLSearchParams(location.search);
  query.value = params.get('q') ?? '';
  if (chips.some((chip) => chip.dataset.category === params.get('category'))) category = params.get('category');

  const reset = () => { query.value = ''; category = 'all'; apply(); query.focus(); };
  form.hidden = false;
  form.addEventListener('submit', (event) => { event.preventDefault(); query.blur(); });
  query.addEventListener('input', () => apply());
  clear.addEventListener('click', () => { query.value = ''; apply(); query.focus(); });
  chips.forEach((chip) => chip.addEventListener('click', () => { category = chip.dataset.category; apply(); }));
  document.getElementById('reset').addEventListener('click', reset);
  document.addEventListener('keydown', (event) => {
    const typing = /^(INPUT|SELECT|TEXTAREA)$/.test(document.activeElement?.tagName ?? '');
    if (event.key === '/' && !typing && !event.metaKey && !event.ctrlKey) { event.preventDefault(); query.focus(); }
    if (event.key === 'Escape' && document.activeElement === query && query.value) { query.value = ''; apply(); }
  });
  // Details open as a catalogue card. Without the script (or without <dialog>) they unfold on the cover.
  const card = document.getElementById('card');
  if (card && typeof card.showModal === 'function') {
    const body = document.getElementById('card-body');
    let opener = null;
    document.documentElement.classList.add('has-card');
    rows.forEach((book) => {
      const summary = book.querySelector('summary');
      // The cover is the big target for pointers; the Details button stays the keyboard way in.
      book.querySelector('.cover').addEventListener('click', () => summary.click());
      summary.addEventListener('click', (event) => {
        event.preventDefault();
        opener = summary;
        card.style.setProperty('--cloth', getComputedStyle(book).getPropertyValue('--cloth'));
        document.getElementById('card-kicker').textContent = book.dataset.category;
        document.getElementById('card-title').textContent = book.querySelector('h3').textContent;
        body.replaceChildren(book.querySelector('.more').cloneNode(true));
        card.showModal();
      });
    });
    document.getElementById('card-close').addEventListener('click', () => card.close());
    // A click on the dimmed page around the card closes it.
    card.addEventListener('click', (event) => { if (event.target === card) card.close(); });
    card.addEventListener('close', () => opener?.focus());
  }
  apply({ remember: false });
})();
