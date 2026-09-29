// Without JavaScript all navigation links remain visible.
const menuButton = document.querySelector('#menu-toggle');
const menu = document.querySelector('#site-navigation');
const header = document.querySelector('.site-header');
if (menuButton && menu && header) {
  const mobile = matchMedia('(max-width: 760px)');
  const setOpen = open => {
    menuButton.setAttribute('aria-expanded', String(open));
    header.classList.toggle('menu-open', open);
  };
  menuButton.hidden = false;
  header.classList.add('nav-ready');
  menuButton.addEventListener('click', () => setOpen(menuButton.getAttribute('aria-expanded') !== 'true'));
  menu.addEventListener('click', event => {
    const link = event.target.closest('a[href^="#"]');
    if (!link || !mobile.matches) return;
    setOpen(false);
    const target = document.querySelector(link.getAttribute('href'));
    if (target) {
      target.setAttribute('tabindex', '-1');
      target.focus({ preventScroll: true });
    }
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && mobile.matches && menuButton.getAttribute('aria-expanded') === 'true') {
      setOpen(false);
      menuButton.focus();
    }
  });
  mobile.addEventListener('change', () => {
    const focusInMenu = menu.contains(document.activeElement);
    setOpen(false);
    if (mobile.matches && focusInMenu) menuButton.focus();
  });
}
