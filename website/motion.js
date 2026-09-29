// Optional enhancement. Without it the text and compass remain static and usable.
async function enableMotion() {
  const image = document.querySelector('.atlas-frame > img');
  if (!image) return;

  // Reuse the local artwork for motion and background-tab handling.
  // No duplicate SVG to maintain.
  const response = await fetch(image.src);
  if (!response.ok) return;
  const doc = new DOMParser().parseFromString(await response.text(), 'image/svg+xml');
  const svg = doc.documentElement;
  if (svg.localName !== 'svg' || doc.querySelector('parsererror')) return;
  svg.setAttribute('width', '640');
  svg.setAttribute('height', '700');
  svg.setAttribute('aria-hidden', 'true');
  image.replaceWith(document.importNode(svg, true));

  const preference = matchMedia('(prefers-reduced-motion: reduce)');
  function update() {
    document.documentElement.dataset.motion = preference.matches ? 'reduced'
      : document.hidden ? 'paused' : 'running';
  }
  preference.addEventListener('change', update);
  document.addEventListener('visibilitychange', update);
  update();
}

// Failure leaves the static image, readable text, and normal links intact.
enableMotion().catch(() => {});

function enableScrollReveals() {
  if (!('IntersectionObserver' in window)) return;
  const preference = matchMedia('(prefers-reduced-motion: reduce)');
  const targets = document.querySelectorAll(
    '.value-grid > article, .steps > li, .collection-card, .faq-list > details'
  );
  // Observe the stationary container; animate its children so the animation
  // cannot repeatedly move the observed box across the viewport boundary.
  const observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      entry.target.classList.toggle('scroll-reveal', entry.isIntersecting && !preference.matches);
    }
  }, { threshold: 0 });

  function update() {
    observer.disconnect();
    for (const target of targets) {
      target.classList.remove('scroll-reveal');
      if (!preference.matches) observer.observe(target);
    }
  }
  preference.addEventListener('change', update);
  update();
}

enableScrollReveals();
