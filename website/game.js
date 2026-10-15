// The island view. The page becomes a trail of stops: scrolling walks the trail, and each stop's section opens as
// a pop-up over the world only when the reader asks for it. world.js draws the island from the state kept here.
// Without the `game` class (set by boot.js: no WebGL) nothing here runs and the page reads as usual.
const root = document.documentElement;
const stages = [...document.querySelectorAll('.stage')];
const reducedQuery = matchMedia('(prefers-reduced-motion: reduce)');
const phone = matchMedia('(max-width: 760px)');
const events = new EventTarget();

export const journey = {
  game: root.classList.contains('game'),
  count: stages.length,
  stage: 0,                       // position along the trail: 0 at base camp, count - 1 at the summit
  open: -1,                       // the stop whose pop-up is showing
  view: { x: 0, y: 0, zoom: 1 },  // how far, in px, the world should move its subject clear of what covers the screen
  events,
  names: stages.map(s => s.dataset.waypoint || ''),
  label: i => (i <= 0 ? 'Start the tour' : stages[i]?.dataset.open || 'Open'),
  travel, openStop, close, exitGame,
};

const track = document.querySelector('.track');
const prompt = document.querySelector('.prompt');
const footer = document.querySelector('.site-footer');
const heroStage = stages[0];
const footerHome = footer && { parent: footer.parentNode, next: footer.nextSibling };
let lastHeroOut = '', lastStep = 0, target = null, targetTimer = 0, shownPrompt = -1, returnFocus = null, frameQueued = false;

const reduced = () => reducedQuery.matches;
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
// One stop per track slot. Measured, not assumed: phone toolbars make 100vh and innerHeight disagree.
const step = () => { const s = track?.children; return s && s.length > 1 ? s[1].offsetTop - s[0].offsetTop : innerHeight; };
const panelOf = i => stages[i]?.querySelector('.panel');

function travel(i, { open = false, instant = false } = {}) {
  i = clamp(i, 0, stages.length - 1);
  target = i;
  clearTimeout(targetTimer);
  // A smooth scroll can be cut short by the reader; never let a stale target hold a pop-up open.
  targetTimer = setTimeout(() => { target = null; update(); }, 2600);
  if (open && i > 0) openStop(i); else if (journey.open !== i) close({ restore: false });
  // 'instant', not 'auto': the stylesheet's smooth scrolling would otherwise animate even a deep-link jump.
  scrollTo({ top: i * step(), behavior: instant || reduced() ? 'instant' : 'smooth' });
}

function svg(path) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  el.setAttribute('class', 'icon'); el.setAttribute('aria-hidden', 'true'); el.setAttribute('viewBox', '0 0 24 24');
  if (path === '#arrow') {
    const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
    use.setAttribute('href', '#arrow'); el.append(use);
  } else {
    const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    p.setAttribute('d', path); el.append(p);
  }
  return el;
}
function button(cls, label, icon) {
  const b = document.createElement('button');
  b.type = 'button'; b.className = cls; b.setAttribute('aria-label', label); b.append(svg(icon));
  return b;
}

// Each pop-up gets a title bar: where it sits on the trail, and the way on, back, or out.
function buildBars() {
  stages.forEach((s, i) => {
    if (i === 0) return;
    const panel = panelOf(i);
    const bar = document.createElement('div');
    bar.className = 'popup-bar';
    // The section's own label moves into the bar, so the pop-up does not say where it is twice.
    const eyebrow = panel.querySelector('.eyebrow');
    const where = document.createElement('p');
    where.className = 'popup-where';
    where.textContent = eyebrow ? eyebrow.textContent.trim() : journey.names[i];
    eyebrow?.classList.add('eyebrow-in-bar');
    const prev = button('round-btn popup-prev', `Previous stop: ${journey.names[i - 1]}`, '#arrow');
    const next = button('round-btn popup-next', i < stages.length - 1 ? `Next stop: ${journey.names[i + 1]}` : 'Last stop', '#arrow');
    next.disabled = i === stages.length - 1;
    const shut = button('round-btn popup-close', 'Close', 'm6 6 12 12M6 18 18 6');
    prev.addEventListener('click', () => travel(i - 1, { open: true }));
    next.addEventListener('click', () => travel(i + 1, { open: true }));
    shut.addEventListener('click', () => close());
    bar.append(where, prev, next, shut);
    panel.prepend(bar);
    panel.setAttribute('role', 'dialog');
    // Non-modal on purpose: the tour around it stays usable (Escape closes, focus returns to what opened it).
    panel.setAttribute('aria-modal', 'false');
    panel.setAttribute('aria-labelledby', s.getAttribute('aria-labelledby'));
    panel.tabIndex = -1;
  });
}

function openStop(i, { focus = true } = {}) {
  if (i <= 0 || i >= stages.length) return;
  if (journey.open === i) { if (focus) panelOf(i).focus({ preventScroll: true }); return; }
  const active = document.activeElement;
  close({ restore: false, silent: true });
  if (active && active !== document.body && !active.closest('.panel[role="dialog"]')) returnFocus = active;
  stages[i].classList.add('is-open');
  const panel = panelOf(i);
  panel.scrollTop = 0;
  journey.open = i;
  events.dispatchEvent(new Event('open'));
  root.classList.add('popup-on');
  // Focus the dialog itself: screen readers announce its title, and no ring lands on a button nobody pressed.
  if (focus) panel.focus({ preventScroll: true });
  setHash(stages[i].id);
  queue();
  // The pop-up slides in; frame the world again once it has settled.
  setTimeout(queue, 480);
}

function close({ restore = true, silent = false } = {}) {
  if (journey.open < 0) return;
  const panel = panelOf(journey.open);
  const hadFocus = panel?.contains(document.activeElement);
  stages[journey.open].classList.remove('is-open');
  journey.open = -1;
  if (!silent) events.dispatchEvent(new Event('close'));
  root.classList.remove('popup-on');
  setHash('');
  if (restore && hadFocus) {
    const back = returnFocus && returnFocus.isConnected && returnFocus.offsetParent ? returnFocus : prompt?.querySelector('.prompt-open');
    back?.focus({ preventScroll: true });
  }
  queue();
}

// Leave the island when the world cannot start: everything goes back where the plain page keeps it.
function exitGame() {
  if (!journey.game) return;
  close({ restore: false });
  root.classList.remove('game', 'popup-on', 'prompt-on', 'hero-away');
  heroStage?.style.removeProperty('--hero-out');
  journey.game = false;
  if (footer && footerHome) footerHome.parent.insertBefore(footer, footerHome.next);
  document.querySelectorAll('.popup-bar').forEach(b => b.remove());
  document.querySelectorAll('.eyebrow-in-bar').forEach(e => e.classList.remove('eyebrow-in-bar'));
  stages.forEach(s => { const p = s.querySelector('.panel'); for (const a of ['role', 'aria-modal', 'aria-labelledby', 'tabindex']) p.removeAttribute(a); });
  scrollTo({ top: 0, behavior: 'instant' });
  events.dispatchEvent(new Event('exit'));
}

// The address follows the open section (#faq …) so it can be shared; the query string (utm…) is kept. Unchanged
// addresses are skipped: Safari limits how often a page may rewrite it.
function setHash(id) {
  const url = location.pathname + location.search + (id ? `#${id}` : '');
  if (url !== location.pathname + location.search + location.hash) history.replaceState(null, '', url);
}

function setPrompt(i) {
  if (!prompt || i === shownPrompt || i <= 0) return;
  shownPrompt = i;
  const s = stages[i];
  // The same label the section's pop-up shows, so a stop has one name everywhere.
  prompt.querySelector('.prompt-kicker').textContent = s.querySelector('.eyebrow')?.textContent.trim() || journey.names[i];
  prompt.querySelector('.prompt-title').textContent = s.dataset.title || '';
  prompt.querySelector('.prompt-lead').textContent = s.dataset.lead || '';
  prompt.querySelector('.prompt-open-text').textContent = journey.label(i);
  prompt.querySelector('.prompt-next').disabled = i === stages.length - 1;
  // No animation on the text when it changes: in Chrome, an animated layer here was sometimes never painted and
  // the card showed blank. The card's own show/hide transition is enough.
}

// Where the world should put its subject: in the open part of the screen, not under the hero, prompt or pop-up.
function computeView() {
  // On phones aim a little lower than the top bar: the explorer stands above the point it marks.
  const W = innerWidth, H = innerHeight, top = phone.matches ? 110 : 64, v = journey.view;
  const heroOut = clamp(journey.stage / 0.6, 0, 1);
  let x = 0, y = 0, zoom = 1;
  if (journey.open > 0) {
    const r = panelOf(journey.open).getBoundingClientRect();
    if (phone.matches) y = (top + r.top) / 2 - H / 2; else x = r.left / 2 - W / 2;
    // On a phone the sheet leaves a short strip of world; moving in would crop the explorer.
    zoom = phone.matches ? 1 : 0.72;
  } else {
    let hx = 0, hy = 0, px = 0, py = 0;
    const hero = panelOf(0)?.getBoundingClientRect();
    if (hero) { if (phone.matches) hy = (top + hero.top) / 2 - H / 2; else hx = (hero.right + W) / 2 - W / 2; }
    const pr = prompt?.getBoundingClientRect();
    if (pr) py = phone.matches ? (top + pr.top) / 2 - H / 2 : -36;
    x = hx + (px - hx) * heroOut; y = hy + (py - hy) * heroOut;
  }
  v.x = x; v.y = y; v.zoom = zoom;
}

function update() {
  frameQueued = false;
  if (!journey.game) return;
  const st = clamp(scrollY / step(), 0, stages.length - 1);
  journey.stage = st;
  const active = Math.round(st);
  if (target !== null && Math.abs(st - target) < 0.02) { target = null; clearTimeout(targetTimer); }
  // Walking away from an open stop closes it; a pop-up never floats over the wrong place.
  if (journey.open > 0 && target === null && Math.abs(st - journey.open) > 0.45) close();
  // Measure first, then write: reading boxes after a write would force an extra layout on every scroll frame.
  computeView();
  const heroOut = clamp(st / 0.6, 0, 1).toFixed(3);
  // Scoped to the hero and written only when it changes, so a scroll does not restyle the whole page.
  if (heroOut !== lastHeroOut) { lastHeroOut = heroOut; heroStage?.style.setProperty('--hero-out', heroOut); }
  if (root.classList.contains('hero-away') !== heroOut > 0.98) root.classList.toggle('hero-away', heroOut > 0.98);
  if (root.classList.contains('prompt-on') !== st > 0.5) root.classList.toggle('prompt-on', st > 0.5);
  setPrompt(Math.max(1, active));
  events.dispatchEvent(new Event('change'));
}
function queue() { if (!frameQueued) { frameQueued = true; requestAnimationFrame(update); } }

if (journey.game) {
  root.classList.add('game-live');   // tells boot.js the island view is running
  buildBars();
  // The page footer closes the trail: it sits at the end of the summit pop-up.
  if (footer) panelOf(stages.length - 1).append(footer);

  prompt.querySelector('.prompt-open').addEventListener('click', () => travel(Math.max(1, Math.round(journey.stage)), { open: true }));
  prompt.querySelector('.prompt-prev').addEventListener('click', () => travel(Math.round(journey.stage) - 1));
  prompt.querySelector('.prompt-next').addEventListener('click', () => travel(Math.round(journey.stage) + 1));
  // Keyboard users land on the first stop's button once the walk there settles, not on the empty page.
  document.querySelector('.start-hint')?.addEventListener('click', () => {
    travel(1);
    setTimeout(() => prompt.querySelector('.prompt-open')?.focus({ preventScroll: true }), reduced() ? 50 : 900);
  });
  // "Skip to content" turns the tour into the plain page: every section in reading order, which is what a keyboard or
  // screen-reader user skipping ahead wants.
  document.querySelector('.skip-link')?.addEventListener('click', e => {
    e.preventDefault();
    exitGame();
    document.getElementById('main')?.focus();
  });
  addEventListener('hashchange', () => {
    const i = stages.findIndex(s => s.id && `#${s.id}` === location.hash);
    if (i > 0 && journey.open !== i) travel(i, { open: true });
  });

  // In-page links travel instead of jumping: the section opens at its stop.
  document.addEventListener('click', e => {
    if (!journey.game) return;
    const link = e.target.closest('a[href^="#"]');
    if (!link) return;
    const id = link.getAttribute('href').slice(1);
    if (id === 'main') return;
    const i = id ? stages.findIndex(s => s.id === id) : 0;
    if (i < 0) return;
    e.preventDefault();
    travel(i, { open: i > 0 });
  });

  addEventListener('keydown', e => {
    if (!journey.game || e.metaKey || e.ctrlKey || e.altKey) return;
    if (e.key === 'Escape' && journey.open > 0) { e.preventDefault(); close(); return; }
    if (e.target.closest('input, textarea, select, [contenteditable]')) return;
    const at = Math.round(journey.stage);
    if (e.key === ']') { e.preventDefault(); travel(at + 1, { open: journey.open > 0 }); }
    if (e.key === '[') { e.preventDefault(); travel(at - 1, { open: journey.open > 0 }); }
  });

  addEventListener('scroll', queue, { passive: true });
  // A rotation or window resize changes the length of a stop; keep the reader at the same stop, not the same pixel.
  addEventListener('resize', () => {
    const s = step();
    if (lastStep && Math.abs(s - lastStep) > 1) scrollTo({ top: journey.stage * s, behavior: 'instant' });
    lastStep = s;
    queue();
  });
  phone.addEventListener('change', queue);

  const fromHash = stages.findIndex(s => s.id && `#${s.id}` === location.hash);
  if (fromHash > 0) {
    travel(fromHash, { open: true, instant: true });
    requestAnimationFrame(() => panelOf(fromHash)?.focus({ preventScroll: true }));
  }
  // Reloads start from the top (or the linked section) rather than a restored scroll the tour did not walk.
  if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
  lastStep = step();
  update();
  // The world is loaded only for the island view. If it cannot start, the reader gets the plain page instead.
  import('./world.js').catch(() => exitGame());
}
