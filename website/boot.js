// Runs before first paint so the layout never jumps: the island view needs WebGL. Without WebGL or JavaScript the
// sections read as an ordinary page instead.
(function () {
  var root = document.documentElement;
  var gl = false;
  try {
    var probe = document.createElement('canvas');
    var ctx = probe.getContext('webgl2') || probe.getContext('webgl');
    gl = !!ctx;
    // Give the probe context back at once; the world creates its own.
    var lose = ctx && ctx.getExtension('WEBGL_lose_context');
    if (lose) lose.loseContext();
  } catch (e) { gl = false; }
  // Short screens (a landscape phone, 200% zoom) cannot fit the title screen, and crawlers should read every
  // section: both get the plain page.
  var shortScreen = Math.min(innerHeight, screen.height || innerHeight) < 500;
  var crawler = /bot|crawl|spider|slurp|lighthouse|preview/i.test(navigator.userAgent);
  if (!gl || shortScreen || crawler) { root.classList.add('no-webgl'); return; }
  root.classList.add('game');
  // The island view hides every section until game.js takes over. If game.js never arrives (a dropped connection,
  // a blocker, a browser too old for it), give the reader the plain page instead of an empty island.
  var fallBack = function () { if (!root.classList.contains('game-live')) { root.classList.remove('game'); root.classList.add('no-webgl'); } };
  addEventListener('error', function (e) { if (e.target && e.target.tagName === 'SCRIPT') fallBack(); }, true);
  addEventListener('load', function () { setTimeout(fallBack, 4000); });
})();
