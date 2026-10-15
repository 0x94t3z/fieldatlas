// Sound for the island view: a calm generated soundtrack and small effects, all synthesised with Web Audio. Nothing
// is fetched (the site's CSP allows no media), so there are no files and no licences. Sound is on by default and
// tries to start as the page opens; browsers that block that (most, until the visitor interacts) get it on the first
// tap, click or key press. The speaker button mutes and unmutes, and a mute is remembered.
import { journey } from './game.js';

const KEY = 'fa-sound';
const button = document.getElementById('sound-toggle');
let ctx = null, master, musicBus, sfxBus, delay, noise;
let enabled = false, musicTimer = 0, nextNote = 0, step = 0, heliNodes = null;

function remembered() { try { return localStorage.getItem(KEY) !== 'off'; } catch { return true; } }
function remember(on) { try { localStorage.setItem(KEY, on ? 'on' : 'off'); } catch { /* lasts this visit only */ } }

function build() {
  ctx = new (window.AudioContext || window.webkitAudioContext)();
  master = ctx.createGain(); master.gain.value = 0;
  const comp = ctx.createDynamicsCompressor();
  master.connect(comp).connect(ctx.destination);
  musicBus = ctx.createGain(); musicBus.gain.value = 0.55; musicBus.connect(master);
  sfxBus = ctx.createGain(); sfxBus.gain.value = 0.9; sfxBus.connect(master);
  // A soft echo for the melody: dotted-eighth repeats, darkened, so the tune feels like it carries across the island.
  delay = ctx.createDelay(1); delay.delayTime.value = 0.535;   // dotted eighth at the trail's tempo
  const fb = ctx.createGain(); fb.gain.value = 0.32;
  const tone = ctx.createBiquadFilter(); tone.type = 'lowpass'; tone.frequency.value = 1800;
  delay.connect(tone).connect(fb).connect(delay);
  const wet = ctx.createGain(); wet.gain.value = 0.35; tone.connect(wet).connect(musicBus);
  // One second of white noise, reused by the rotor and the shaker.
  noise = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
  const d = noise.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
}

const hz = m => 440 * 2 ** ((m - 69) / 12);

// ---------- the soundtrack: two pieces, four chords each, two bars a chord ----------
// The trail: A minor at a walking pace, calm. The summit: C major, quicker and brighter, with a kick drum.
const SONGS = {
  trail: {
    bpm: 84, lead: 'triangle', leadOct: 12, leadGain: 0.05, pad: 900, kick: false,
    chords: [  // Am, F, C, G: root (MIDI) and the tones the melody picks from
      { root: 45, tones: [57, 60, 64, 69, 72] },
      { root: 41, tones: [57, 60, 65, 69, 72] },
      { root: 48, tones: [55, 60, 64, 67, 72] },
      { root: 43, tones: [55, 59, 62, 67, 71] },
    ],
    // Which chord tone the melody plays on each eighth of a bar (-1 rests), varied between a chord's two bars.
    arp: [[0, 2, 4, 2, 3, 2, 1, -1], [0, 2, 3, 4, 3, -1, 2, 1]],
  },
  summit: {
    bpm: 104, lead: 'square', leadOct: 12, leadGain: 0.032, pad: 1600, kick: true,
    chords: [  // C, G, Am, F
      { root: 48, tones: [60, 64, 67, 72, 76] },
      { root: 43, tones: [59, 62, 67, 71, 74] },
      { root: 45, tones: [60, 64, 69, 72, 76] },
      { root: 41, tones: [60, 65, 69, 72, 77] },
    ],
    arp: [[0, 2, 4, 2, 0, 2, 4, 3], [4, 3, 2, 3, 4, -1, 2, -1]],
  },
};
let song = SONGS.trail, wantSong = 'trail';
const eighthOf = sg => 60 / sg.bpm / 2;

// Switch pieces; the change lands at the start of the next chord so it never cuts a phrase mid-note.
export function setMood(name) { if (SONGS[name]) wantSong = name; }

function voice(type, freq, t, len, peak, dest, attack = 0.01) {
  const o = ctx.createOscillator(), g = ctx.createGain();
  o.type = type; o.frequency.value = freq;
  g.gain.setValueAtTime(0, t);
  g.gain.linearRampToValueAtTime(peak, t + attack);
  g.gain.exponentialRampToValueAtTime(0.0001, t + len);
  o.connect(g).connect(dest);
  o.start(t); o.stop(t + len + 0.05);
  return o;
}

function scheduleMusic() {
  // After a stall (a long frame, a busy start-up) pick up from now rather than play every missed note at once.
  if (nextNote < ctx.currentTime) nextNote = ctx.currentTime + 0.05;
  while (nextNote < ctx.currentTime + 0.25) {
    if (step % 16 === 0 && song !== SONGS[wantSong]) { song = SONGS[wantSong]; step = 0; }
    const E = eighthOf(song), bar = Math.floor(step / 8), eighth = step % 8, chord = song.chords[Math.floor(bar / 2) % 4], t = nextNote;
    if (eighth === 0) {
      // Pad: the chord held across the bar, quiet.
      for (const m of [chord.root + 12, chord.tones[0], chord.tones[2]]) {
        const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = song.pad; lp.connect(musicBus);
        voice('triangle', hz(m), t, E * 8.2, 0.035, lp, 0.6);
      }
    }
    if (eighth === 0 || eighth === 4 || (song.kick && eighth === 6)) voice('sine', hz(chord.root), t, E * 3.5, 0.16, musicBus, 0.02);  // bass
    if (song.kick && eighth % 2 === 0) {  // kick on the beats: a fast falling sine
      const o = voice('sine', 140, t, 0.16, 0.22, musicBus, 0.002); o.frequency.exponentialRampToValueAtTime(45, t + 0.12);
    }
    const pick = song.arp[bar % 2][eighth];
    if (pick >= 0) {  // melody into the echo
      const g = ctx.createGain(); g.gain.value = 1; g.connect(musicBus); g.connect(delay);
      voice(song.lead, hz(chord.tones[pick] + song.leadOct), t, E * 1.6, song.leadGain, g, 0.005);
    }
    if (eighth % 2 === 1) {  // shaker on the off-beats
      const n = ctx.createBufferSource(), hp = ctx.createBiquadFilter(), g = ctx.createGain();
      n.buffer = noise; hp.type = 'highpass'; hp.frequency.value = 6000;
      g.gain.setValueAtTime(song.kick ? 0.03 : 0.018, t); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.06);
      n.connect(hp).connect(g).connect(musicBus); n.start(t, Math.random() * 0.5, 0.08);
    }
    nextNote += E; step++;
  }
}

// ---------- effects ----------
const EFFECTS = {
  // Each footstep: a dull tap.
  step: t => voice('sine', 150, t, 0.07, 0.05, sfxBus),
  // Walking back down the trail: a lower, softer shuffle.
  stepBack: t => { const o = voice('triangle', 120, t, 0.09, 0.04, sfxBus); o.frequency.exponentialRampToValueAtTime(90, t + 0.08); },
  // Back at an earlier flag: the same two notes falling, the flag turning from gold back to red.
  back: t => { voice('triangle', hz(93), t, 0.09, 0.06, sfxBus); voice('triangle', hz(88), t + 0.09, 0.3, 0.06, sfxBus); },
  // Home at base camp: one low, warm note.
  home: t => { voice('triangle', hz(69), t, 0.5, 0.07, sfxBus); voice('sine', hz(57), t, 0.6, 0.05, sfxBus); },
  // The dinosaur's jump: the short square blip of the Chrome game.
  jump: t => { const o = voice('square', 520, t, 0.11, 0.05, sfxBus, 0.002); o.frequency.setValueAtTime(780, t + 0.035); },
  // Hoisted by the helicopter: a rising whistle. Set down: a soft thump.
  lift: t => { const o = voice('sine', 300, t, 1.1, 0.07, sfxBus, 0.05); o.frequency.exponentialRampToValueAtTime(1200, t + 1.0); },
  land: t => { const o = voice('sine', 160, t, 0.25, 0.2, sfxBus, 0.003); o.frequency.exponentialRampToValueAtTime(50, t + 0.2); },
  // Reaching a stop: two rising notes, the flag turning gold.
  reach: t => { voice('square', hz(88), t, 0.09, 0.05, sfxBus); voice('square', hz(93), t + 0.08, 0.28, 0.05, sfxBus); },
  // A pop-up opening and closing.
  open: t => { const o = voice('triangle', 520, t, 0.14, 0.09, sfxBus); o.frequency.exponentialRampToValueAtTime(980, t + 0.09); },
  close: t => { const o = voice('triangle', 900, t, 0.14, 0.07, sfxBus); o.frequency.exponentialRampToValueAtTime(480, t + 0.1); },
  // The "?" appearing: a small rising "hm?".
  puzzle: t => { voice('sine', hz(72), t, 0.14, 0.08, sfxBus); const o = voice('sine', hz(74), t + 0.13, 0.25, 0.08, sfxBus); o.frequency.exponentialRampToValueAtTime(hz(78), t + 0.32); },
  // "!" and off it goes: a quick upward boing.
  wake: t => { const o = voice('square', 260, t, 0.22, 0.05, sfxBus); o.frequency.exponentialRampToValueAtTime(880, t + 0.16); },
  // The summit: a level-complete jingle. A quick run up, a turn, then a held C major chord with sparkles and a cymbal
  // swell; the soundtrack dips underneath it.
  victory: t => {
    duckMusic(t, 3.4);
    const S = 0.085;
    [60, 64, 67, 72, 76, 79].forEach((m, i) => voice('square', hz(m + 12), t + i * S, 0.12, 0.045, sfxBus));
    const t2 = t + 6 * S + 0.05;
    [[79, 0], [81, 1], [83, 2]].forEach(([m, i]) => voice('square', hz(m), t2 + i * S * 1.5, 0.14, 0.05, sfxBus));
    const t3 = t2 + 3 * S * 1.5 + 0.04;
    for (const m of [72, 76, 79, 84]) {
      const o = voice('triangle', hz(m), t3, 2.1, 0.06, sfxBus, 0.02);
      const vib = ctx.createOscillator(), depth = ctx.createGain();   // a little shimmer on the held chord
      vib.frequency.value = 6; depth.gain.value = hz(m) * 0.006; vib.connect(depth).connect(o.frequency); vib.start(t3); vib.stop(t3 + 2.2);
    }
    voice('square', hz(84), t3, 1.4, 0.035, sfxBus, 0.01);
    voice('sine', hz(48), t3, 1.8, 0.16, sfxBus, 0.02);
    voice('sine', hz(36), t3, 1.8, 0.12, sfxBus, 0.02);
    for (let i = 0; i < 9; i++) voice('sine', hz(96 + [0, 4, 7, 12][i % 4]), t3 + 0.1 + i * 0.13, 0.25, 0.025, sfxBus);   // sparkles
    const s = ctx.createBufferSource(), hp = ctx.createBiquadFilter(), g = ctx.createGain();   // cymbal swell
    s.buffer = noise; s.loop = true; hp.type = 'highpass'; hp.frequency.value = 5000;
    g.gain.setValueAtTime(0.0001, t3 - 0.3); g.gain.exponentialRampToValueAtTime(0.05, t3); g.gain.exponentialRampToValueAtTime(0.0001, t3 + 1.6);
    s.connect(hp).connect(g).connect(sfxBus); s.start(t3 - 0.3); s.stop(t3 + 1.7);
  },
};

function duckMusic(t, len) {
  musicBus.gain.cancelScheduledValues(t);
  musicBus.gain.setTargetAtTime(0.12, t, 0.08);
  musicBus.gain.setTargetAtTime(0.55, t + len, 0.6);
}

export function play(name) {
  if (!enabled || !ctx || ctx.state !== 'running') return;
  EFFECTS[name]?.(ctx.currentTime + 0.01);
}

// The helicopter: noise chopped by the rotor's beat, plus a low thump; louder the nearer it is to the camera.
export function setHeli(level) {
  if (!ctx || !enabled) { if (heliNodes && !level) stopHeli(); return; }
  if (level > 0 && !heliNodes) {
    const src = ctx.createBufferSource(); src.buffer = noise; src.loop = true;
    const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 700; bp.Q.value = 0.8;
    const chop = ctx.createGain(); chop.gain.value = 0.5;
    const lfo = ctx.createOscillator(); lfo.type = 'square'; lfo.frequency.value = 13;
    const depth = ctx.createGain(); depth.gain.value = 0.5; lfo.connect(depth).connect(chop.gain);
    const thump = ctx.createOscillator(); thump.frequency.value = 52;
    const tg = ctx.createGain(); tg.gain.value = 0.4; depth.connect(tg.gain);
    const out = ctx.createGain(); out.gain.value = 0;
    src.connect(bp).connect(chop).connect(out); thump.connect(tg).connect(out); out.connect(sfxBus);
    src.start(); lfo.start(); thump.start();
    heliNodes = { out, stop: () => { src.stop(); lfo.stop(); thump.stop(); out.disconnect(); } };
  }
  if (heliNodes) {
    heliNodes.out.gain.setTargetAtTime(level * 0.22, ctx.currentTime, 0.1);
    if (!level) stopHeli();
  }
}
function stopHeli() { const h = heliNodes; heliNodes = null; setTimeout(() => h.stop(), 400); }

// ---------- on / off ----------
const running = () => !!ctx && ctx.state === 'running';
// The ask: a small card offering sound, once a visit, when the browser is holding it back and the reader has not
// muted. Its "Turn on sound" press is the interaction the browser waits for.
const ask = document.querySelector('.sound-ask');
let askShown = false;
function seenAsk() { try { return sessionStorage.getItem('fa-sound-asked') === '1'; } catch { return false; } }
// Under the speaker on wide screens (its place moves with the nav); full width under the top bar on phones (CSS).
function placeAsk() {
  if (!ask || ask.hidden || !button || innerWidth <= 760) { if (ask) ask.style.right = ''; return; }
  const r = button.getBoundingClientRect();
  ask.style.right = `${Math.max(12, Math.round(innerWidth - r.right - 40))}px`;
}
addEventListener('resize', placeAsk);
function hideAsk() {
  // If focus was inside the card, hand it to the speaker rather than dropping it on the page.
  if (ask && ask.contains(document.activeElement)) button?.focus({ preventScroll: true });
  document.documentElement.classList.remove('sound-asking');
  if (!ask || ask.hidden) return;
  ask.classList.remove('is-in');
  setTimeout(() => { ask.hidden = true; }, 300);
}
function offerSound() {
  if (!ask || askShown || seenAsk() || !enabled || running()) return;
  askShown = true;
  try { sessionStorage.setItem('fa-sound-asked', '1'); } catch { /* shown once this page load instead */ }
  ask.hidden = false;
  placeAsk();
  document.documentElement.classList.add('sound-asking');
  requestAnimationFrame(() => ask.classList.add('is-in'));
}

function render() {
  if (!button) return;
  // On, but the browser is still holding audio back until the visitor interacts: say so, rather than show "on".
  const waiting = enabled && !running();
  button.setAttribute('aria-pressed', String(enabled && !waiting));
  // A fixed name; aria-pressed carries on/off, and the visible "Tap for sound" hint speaks for itself.
  button.setAttribute('aria-label', 'Sound');
  button.classList.toggle('is-on', enabled && !waiting);
  button.classList.toggle('is-waiting', waiting);
  if (!waiting) hideAsk();
}

async function start() {
  if (!ctx) { build(); ctx.addEventListener('statechange', render); }
  if (ctx.state !== 'running') await ctx.resume().catch(() => {});
  render();
  if (ctx.state !== 'running') return;
  master.gain.cancelScheduledValues(ctx.currentTime);
  master.gain.setTargetAtTime(0.8, ctx.currentTime, 0.4);
  if (!musicTimer) { nextNote = ctx.currentTime + 0.1; musicTimer = setInterval(scheduleMusic, 40); }
}
function stop() {
  if (!ctx) return;
  master.gain.setTargetAtTime(0, ctx.currentTime, 0.15);
  setTimeout(() => { if (!enabled) { clearInterval(musicTimer); musicTimer = 0; ctx.suspend(); } }, 600);
}

export function setEnabled(on) {
  enabled = on; remember(on); render();
  if (on) start(); else stop();
}

if (journey.game && button && (window.AudioContext || window.webkitAudioContext)) {
  button.hidden = false;
  enabled = remembered();
  render();
  // While sound is waiting on the browser, the button starts it; otherwise it mutes and unmutes.
  button.addEventListener('click', () => { if (enabled && !running()) start(); else setEnabled(!enabled); });
  // Try at once; where the browser holds audio back, every tap or key press tries again until it is running. The
  // speaker button's own click is left to its handler, so a click that mutes does not first start the music.
  if (enabled) start();
  const unlock = e => {
    if (e.target.closest?.('#sound-toggle, .sound-ask')) return;
    if (enabled && (!ctx || ctx.state !== 'running')) start();
    if (running()) for (const ev of ['pointerdown', 'touchend']) removeEventListener(ev, unlock, true);
  };
  // Taps and clicks only: a key press (Tab to the skip link, a screen reader's keys) must never start the music.
  for (const ev of ['pointerdown', 'touchend']) addEventListener(ev, unlock, { capture: true, passive: true });
  if (ask) {
    ask.querySelector('.sound-ask-yes').addEventListener('click', () => { start(); hideAsk(); });
    // "Not now" means no sound this visit: taps elsewhere no longer start it (the speaker still can). Not remembered,
    // so the next visit asks again.
    ask.querySelector('.sound-ask-no').addEventListener('click', () => { enabled = false; render(); hideAsk(); });
    ask.addEventListener('keydown', e => { if (e.key === 'Escape') hideAsk(); });
    // Give the page a moment to appear first; ask only if sound has not started by then.
    if (enabled) setTimeout(offerSound, 1200);
    // It steps aside once the reader gets going (walks on, opens a section) or after a while: it must never sit over a
    // pop-up or the summit banner. The speaker button still offers sound afterwards.
    journey.events.addEventListener('change', () => { if (journey.stage > 0.4) hideAsk(); });
    journey.events.addEventListener('open', hideAsk);
    setTimeout(hideAsk, 13000);
  }
  document.addEventListener('visibilitychange', () => {
    if (!ctx || !enabled) return;
    if (document.hidden) ctx.suspend(); else start();
  });
  journey.events.addEventListener('open', () => play('open'));
  // The world could not start and the plain page took over: no sound there at all.
  journey.events.addEventListener('exit', () => {
    enabled = false; hideAsk(); button.hidden = true;
    for (const ev of ['pointerdown', 'touchend']) removeEventListener(ev, unlock, true);
    if (ctx) { clearInterval(musicTimer); musicTimer = 0; ctx.close(); ctx = null; }
  });
  journey.events.addEventListener('close', () => play('close'));
}
