// The island: a live world the trail crosses. Loaded only for the island view (see game.js). Every word on the page
// is in the HTML, so the world is wayfinding and atmosphere, never content.
import * as THREE from './vendor/three.module.min.js';
import { journey } from './game.js';
import { play, setHeli, setMood } from './audio.js';

const root = document.documentElement;
const canvas = document.getElementById('world');
const reducedQuery = matchMedia('(prefers-reduced-motion: reduce)');
const mobile = matchMedia('(max-width: 760px)').matches;
let reduced = reducedQuery.matches;

let renderer;
try {
  renderer = new THREE.WebGLRenderer({ canvas, antialias: !mobile, powerPreference: 'high-performance' });
} catch (error) {
  // game.js catches this and hands the reader the page view.
  root.classList.add('no-webgl');
  throw error;
}
// A GPU context lost for good (common on phones after backgrounding) leaves a blank world: read the plain page instead.
canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); setTimeout(() => { if (renderer.getContext().isContextLost()) journey.exitGame(); }, 1500); });
renderer.setPixelRatio(Math.min(devicePixelRatio, mobile ? 1.25 : 1.75));
renderer.outputColorSpace = THREE.SRGBColorSpace;

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(46, innerWidth / innerHeight, 0.5, 2400);

// ---------- deterministic noise, so the island is the same for every visitor ----------
function rand(seed) { return () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296); }
const R = rand(20261015);
const perm = new Uint8Array(512);
{ const p = [...Array(256).keys()]; for (let i = 255; i > 0; i--) { const j = Math.floor(R() * (i + 1)); [p[i], p[j]] = [p[j], p[i]]; } for (let i = 0; i < 512; i++) perm[i] = p[i & 255]; }
const fade = t => t * t * (3 - 2 * t);
function vnoise(x, y) {
  const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
  const h = (a, b) => perm[(perm[a & 255] + b) & 255] / 255;
  const a = h(xi, yi), b = h(xi + 1, yi), c = h(xi, yi + 1), d = h(xi + 1, yi + 1);
  const u = fade(xf), v = fade(yf);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}
function fbm(x, y) { let s = 0, a = 0.5, f = 1; for (let i = 0; i < 5; i++) { s += a * vnoise(x * f, y * f); f *= 2.03; a *= 0.5; } return s; }
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

// The island: a main body, a peak for the summit, two islets (kept out from behind the title), all warped by noise.
const PEAK = new THREE.Vector2(14, -30);
function height(x, z) {
  const warp = (fbm(x * 0.012 + 3, z * 0.012 - 7) - 0.5) * 34;
  const d = Math.hypot(x + warp * 0.6, z - warp * 0.4) / 98;
  const body = 1 - smooth(0.5, 1.02, d);
  const hills = fbm(x * 0.03, z * 0.03) * 13;
  const pd = Math.hypot(x - PEAK.x, z - PEAK.y);
  const peak = Math.exp(-(pd * pd) / (2 * 21 * 21)) * 34;
  const islet1 = (1 - smooth(0.2, 1, Math.hypot(x - 130, z + 70) / 26)) * (7 + fbm(x * 0.05, z * 0.05) * 6);
  const islet2 = (1 - smooth(0.2, 1, Math.hypot(x - 10, z + 165) / 19)) * (5 + fbm(x * 0.06 + 9, z * 0.06) * 5);
  return body * (5 + hills + peak) + islet1 + islet2 - 4.2;
}

const PALETTE = {
  deep: new THREE.Color('#1f5a51'), shallow: new THREE.Color('#5aa897'), sand: new THREE.Color('#e8dcb2'),
  grass: new THREE.Color('#a9c48a'), meadow: new THREE.Color('#86ae6f'), forest: new THREE.Color('#3f7a55'),
  rock: new THREE.Color('#8f9483'), snow: new THREE.Color('#f7f4ea'),
};
function groundColor(h, slope) {
  if (h < 0.4) return PALETTE.sand;
  if (h < 1.6) return PALETTE.sand.clone().lerp(PALETTE.grass, (h - 0.4) / 1.2);
  if (slope > 0.75) return PALETTE.rock;
  if (h < 7) return PALETTE.grass.clone().lerp(PALETTE.meadow, (h - 1.6) / 5.4);
  if (h < 16) return PALETTE.meadow.clone().lerp(PALETTE.forest, (h - 7) / 9);
  if (h < 26) return PALETTE.forest.clone().lerp(PALETTE.rock, (h - 16) / 10);
  return PALETTE.rock.clone().lerp(PALETTE.snow, Math.min(1, (h - 26) / 6));
}

// ---------- terrain: flat-shaded, coloured per face ----------
{
  const SIZE = 320, SEG = mobile ? 110 : 160;
  // Heights on the shared (indexed) vertices first, then split into flat faces: a sixth of the noise evaluations.
  const grid = new THREE.PlaneGeometry(SIZE, SIZE, SEG, SEG).rotateX(-Math.PI / 2);
  const gp = grid.attributes.position;
  for (let i = 0; i < gp.count; i++) gp.setY(i, height(gp.getX(i), gp.getZ(i)));
  const geo = grid.toNonIndexed();
  const pos = geo.attributes.position;
  geo.computeVertexNormals();
  const colors = new Float32Array(pos.count * 3);
  const n = geo.attributes.normal;
  for (let i = 0; i < pos.count; i += 3) {
    const h = (pos.getY(i) + pos.getY(i + 1) + pos.getY(i + 2)) / 3;
    const slope = 1 - (n.getY(i) + n.getY(i + 1) + n.getY(i + 2)) / 3;
    const c = groundColor(h, slope * 3.2).clone().offsetHSL(0, 0, (R() - 0.5) * 0.035);
    for (let k = 0; k < 3; k++) c.toArray(colors, (i + k) * 3);
  }
  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  scene.add(new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ vertexColors: true, flatShading: true })));
}

// ---------- sea: waves displaced in the vertex shader, flat-shaded facets ----------
const seaUniforms = { uTime: { value: 0 } };
{
  const geo = new THREE.PlaneGeometry(2200, 2200, mobile ? 90 : 140, mobile ? 90 : 140);
  const mat = new THREE.MeshPhongMaterial({ color: '#3f8f80', specular: '#5f8478', shininess: 24, flatShading: true, transparent: true, opacity: 0.86 });
  mat.onBeforeCompile = shader => {
    shader.uniforms.uTime = seaUniforms.uTime;
    shader.vertexShader = 'uniform float uTime;\n' + shader.vertexShader.replace('#include <begin_vertex>', `
      vec3 transformed = vec3(position);
      float w = sin(position.x * 0.045 + uTime * 0.9) * 0.9 + cos(position.y * 0.05 + uTime * 0.7) * 0.8
              + sin((position.x + position.y) * 0.11 + uTime * 1.6) * 0.25;
      transformed.z += w;`);
  };
  const sea = new THREE.Mesh(geo, mat);
  sea.rotation.x = -Math.PI / 2;
  sea.position.y = -0.6;
  scene.add(sea);
}

// ---------- the route: seven waypoints, shore to summit ----------
// Base camp sits on the west beach, a full stop's walk from the next one (the others are ~33 apart).
const ROUTE_XZ = [[-72.6, 7.6], [-52, 38], [-20, 48], [16, 30], [38, 2], [-4, -10], [PEAK.x, PEAK.y + 4]];
// Every stop stands on dry land: a point that falls in the water walks inland until it reaches the beach.
const waypoints = ROUTE_XZ.map(([x, z]) => {
  for (let k = 0; k < 60 && height(x, z) < 1.2; k++) { const d = Math.hypot(x, z); x -= (x / d) * 1.5; z -= (z / d) * 1.5; }
  return new THREE.Vector3(x, height(x, z), z);
});
const trailPoints = [];
for (let i = 0; i < waypoints.length - 1; i++) {
  const a = waypoints[i], b = waypoints[i + 1];
  for (let k = 0; k < 14; k++) {
    const t = k / 14, x = a.x + (b.x - a.x) * t + Math.sin(t * Math.PI) * (i % 2 ? 6 : -6), z = a.z + (b.z - a.z) * t;
    trailPoints.push(new THREE.Vector3(x, Math.max(0.5, height(x, z)) + 0.55, z));
  }
}
trailPoints.push(waypoints.at(-1).clone().setY(waypoints.at(-1).y + 0.55));
const trail = new THREE.CatmullRomCurve3(trailPoints, false, 'centripetal');
// Where each waypoint sits along the trail, as a fraction of its length.
const wpU = waypoints.map(w => {
  let best = 0, bestD = Infinity;
  for (let i = 0; i <= 600; i++) { const d = trail.getPointAt(i / 600).distanceToSquared(w); if (d < bestD) { bestD = d; best = i / 600; } }
  return best;
});

const trailUniforms = { uTime: { value: 0 }, uProgress: { value: 0 } };
{
  const geo = new THREE.TubeGeometry(trail, 900, 0.32, 5, false);
  const mat = new THREE.ShaderMaterial({
    uniforms: trailUniforms, transparent: true, depthWrite: false,
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: `uniform float uTime; uniform float uProgress; varying vec2 vUv;
      void main(){
        float dash = step(0.45, fract(vUv.x * 160.0 - uTime * 0.6));
        if (dash < 0.5) discard;
        vec3 walked = vec3(0.85, 0.64, 0.25), ahead = vec3(0.99, 0.97, 0.9);
        float done = step(vUv.x, uProgress);
        gl_FragColor = vec4(mix(ahead, walked, done), mix(0.75, 1.0, done));
      }`,
  });
  scene.add(new THREE.Mesh(geo, mat));
}

// Waypoint markers: a pole with a pennant planted in the ground just beside the trail, and a ring on the ground that
// lights under the active stop. Pennants are vermilion ahead and turn gold (the walked trail's colour) once reached.
const FLAG_AHEAD = new THREE.Color('#e0603c'), FLAG_REACHED = new THREE.Color('#eeb54c');
const PENNANT = new THREE.ShapeGeometry(new THREE.Shape([new THREE.Vector2(0, 0), new THREE.Vector2(2.3, -0.65), new THREE.Vector2(0, -1.3)]));
const beacons = [];
for (const [i, w] of waypoints.entries()) {
  const g = new THREE.Group(); g.position.copy(w);
  // Fixed in the world, a few steps to the side of the trail, standing on the ground where it is (not at the stop's
  // height, which differs on a slope). Only the pennant turns to the camera.
  const tan = trail.getTangentAt(Math.min(0.999, Math.max(0.001, wpU[i])));
  const side = Math.hypot(tan.x, tan.z) || 1, mx = w.x - (tan.z / side) * 3.4, mz = w.z + (tan.x / side) * 3.4;
  const marker = new THREE.Group(); marker.position.set(mx, Math.max(0.6, height(mx, mz)) - 0.2, mz);
  const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.11, 7.6, 6), new THREE.MeshLambertMaterial({ color: '#3d2f22' }));
  pole.position.y = 3.8;
  const flag = new THREE.Mesh(PENNANT, new THREE.MeshLambertMaterial({ color: FLAG_AHEAD, emissive: '#6a3420', side: THREE.DoubleSide }));
  flag.position.set(0.08, 7.5, 0);
  marker.add(pole, flag);
  // Base camp is where the walk starts, not a stop to reach: it has its ring but no flag.
  if (i > 0) scene.add(marker);
  const ring = new THREE.Mesh(new THREE.RingGeometry(1.9, 2.4, 48), new THREE.MeshBasicMaterial({ color: '#eeb54c', transparent: true, opacity: 0.0, side: THREE.DoubleSide, depthWrite: false }));
  ring.rotation.x = -Math.PI / 2; ring.position.y = 0.25;
  g.add(ring);
  scene.add(g);
  beacons.push({ g, marker, flag, ring, glow: 0 });
}

// The explorer: the app's pixel dinosaur (FieldAtlasIcons.Offline), built in voxels from the same SVG paths the HUD
// uses, so the two never drift apart. Seen side-on it is exactly the app's sprite; in depth it is a chunky block
// four voxels thick with its two legs set apart, so it turns in real 3D. Three poses: standing, and the two walking
// frames.
const DINO_PX = 0.24, DINO_H = 26 * DINO_PX;
const DINO_EYE = [[20, 5], [21, 5], [20, 6], [21, 6]];
function spriteCells(id) {
  const c = document.createElement('canvas'); c.width = c.height = 32;
  const ctx = c.getContext('2d'), path = document.querySelector(`#${id} path`);
  ctx.fill(new Path2D(path.getAttribute('d')), path.getAttribute('fill-rule') === 'evenodd' ? 'evenodd' : 'nonzero');
  const data = ctx.getImageData(0, 0, 32, 32).data, on = new Uint8Array(32 * 32);
  for (let i = 0; i < 32 * 32; i++) on[i] = data[i * 4 + 3] > 127 ? 1 : 0;
  for (const [x, y] of DINO_EYE) on[y * 32 + x] = 2;   // the eye: solid inside, dark on both faces
  return on;
}
function dinoLayers(on) {
  const body = [], eye = [];
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const v = on[y * 32 + x];
    if (!v) continue;
    const leg = y >= 22;
    // Legs: two voxels thick each, one either side of the body. Body and head: four voxels thick.
    const zs = leg ? (x < 11 ? [-2, -1] : [1, 2]) : [-1.5, -0.5, 0.5, 1.5];
    zs.forEach((z, k) => ((v === 2 && (k === 0 || k === zs.length - 1)) ? eye : body).push([x, y, z]));
  }
  return { body, eye };
}
const explorer = new THREE.Group();
const dinoBody = new THREE.Group();
explorer.add(dinoBody);
const dinoPoses = {};
{
  const box = new THREE.BoxGeometry(DINO_PX, DINO_PX, DINO_PX);
  const bodyMat = new THREE.MeshLambertMaterial({ color: '#f6f0dc', emissive: '#58523f' });
  const eyeMat = new THREE.MeshBasicMaterial({ color: '#17261f' });
  const m = new THREE.Matrix4();
  const place = ([x, y, z]) => m.makeTranslation((x - 16) * DINO_PX, (28 - y) * DINO_PX - DINO_PX / 2, z * DINO_PX);
  for (const id of ['dino', 'dino-walk-a', 'dino-walk-b']) {
    const { body, eye } = dinoLayers(spriteCells(id));
    const g = new THREE.Group();
    for (const [cells, mat] of [[body, bodyMat], [eye, eyeMat]]) {
      const mesh = new THREE.InstancedMesh(box, mat, cells.length);
      cells.forEach((c, k) => mesh.setMatrixAt(k, place(c)));
      g.add(mesh);
    }
    g.visible = id === 'dino';
    dinoBody.add(g);
    dinoPoses[id] = g;
  }
  const lantern = new THREE.PointLight('#ffd27a', 18, 24, 1.6);
  lantern.position.y = DINO_H + 1;
  // No ring of its own: the stop's gold ring marks where it stands.
  // The point light costs every lit pixel on screen; phones do without it.
  if (!mobile) explorer.add(lantern);
  scene.add(explorer);
}
let dinoPose = 'dino';
function setDinoPose(id) {
  if (id === dinoPose) return;
  dinoPoses[dinoPose].visible = false; dinoPoses[id].visible = true; dinoPose = id;
}

// A pixel "?" over the dinosaur's head while it waits at base camp for the reader; "!" when they set off.
const thought = new THREE.Group();
const thoughtMarks = {};
{
  const marks = {
    '?': [' ### ', '#   #', '    #', '   # ', '  #  ', '     ', '  #  '],
    '!': ['  #  ', '  #  ', '  #  ', '  #  ', '  #  ', '     ', '  #  '],
  };
  const px = 0.55, box = new THREE.BoxGeometry(px, px, px * 1.4), mat = new THREE.MeshBasicMaterial({ color: '#fffaf0' });
  const m = new THREE.Matrix4();
  for (const [key, rows] of Object.entries(marks)) {
    const cells = [];
    rows.forEach((r, y) => [...r].forEach((ch, x) => { if (ch === '#') cells.push([x, y]); }));
    const mesh = new THREE.InstancedMesh(box, mat, cells.length);
    cells.forEach(([x, y], k) => mesh.setMatrixAt(k, m.makeTranslation((x - 2) * px, (6 - y) * px, 0)));
    mesh.visible = key === '?';
    thought.add(mesh); thoughtMarks[key] = mesh;
  }
  thought.visible = false;
  scene.add(thought);
}

// ---------- the helicopter: a voxel model that comes for the explorer at the summit ----------
const heli = new THREE.Group();
const heliRotor = new THREE.Group(), heliTail = new THREE.Group();
{
  const mat = c => new THREE.MeshLambertMaterial({ color: c, flatShading: true });
  const red = mat('#e0603c'), cream = mat('#f6f0dc'), dark = mat('#2c3a33');
  const box = (w, h, d, m, x, y, z) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), m); b.position.set(x, y, z); return b; };
  // Built nose along +z, so lookAt() points it where it flies.
  heli.add(
    box(2.8, 2.4, 4, red, 0, 0, 0),            // cabin
    box(2.5, 1.2, 0.5, cream, 0, 0.35, 2.05),  // windscreen
    box(2.9, 0.9, 2.4, cream, 0, 0.35, 0.4),   // side windows
    box(0.6, 0.6, 5, red, 0, 0.4, -4.4),       // tail boom
    box(0.2, 1.7, 1.1, red, 0, 1.1, -6.6),     // tail fin
    box(0.3, 0.9, 0.3, dark, 0, 1.6, 0),       // mast
    box(0.22, 0.22, 4.6, dark, 1.3, -1.75, 0.2), box(0.22, 0.22, 4.6, dark, -1.3, -1.75, 0.2),   // skids
    box(0.18, 0.6, 0.18, dark, 1.3, -1.4, 1.2), box(0.18, 0.6, 0.18, dark, -1.3, -1.4, 1.2),
    box(0.18, 0.6, 0.18, dark, 1.3, -1.4, -0.9), box(0.18, 0.6, 0.18, dark, -1.3, -1.4, -0.9),
  );
  heliRotor.add(box(11, 0.1, 0.45, dark, 0, 0, 0), box(0.45, 0.1, 11, dark, 0, 0, 0));
  heliRotor.position.y = 2.1;
  heliTail.add(box(0.1, 2, 0.3, dark, 0, 0, 0), box(0.1, 0.3, 2, dark, 0, 0, 0));
  heliTail.position.set(0.25, 1.1, -6.6);
  heli.add(heliRotor, heliTail);
  heli.scale.setScalar(0.8);
  heli.visible = false;
  scene.add(heli);
}
const PEAK_Y = height(PEAK.x, PEAK.y);
heli.rotation.order = 'YXZ';
const rope = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 1, 5), new THREE.MeshLambertMaterial({ color: '#3d2f22' }));
rope.visible = false;
scene.add(rope);
// After the summit celebration the helicopter lifts the explorer on a rope and flies it round the peak. Going back
// down the tour, it first brings the explorer back to the summit and sets it down; only then does the walk resume.
// States: off → in → lower → lift → carry → (reader goes back) return → drop → release → away → off.
const HOVER = DINO_H + 10, LIFT = 5, HOOK = 1.4, GRAB = DINO_H * 0.9;
const ROPE_DOWN = HOVER - HOOK - GRAB, ROPE_CARRY = ROPE_DOWN - LIFT;
const heliCtl = { state: 'off', ropeFrom: 0, t: 0, spot: new THREE.Vector3(), lift: 0, ropeLen: 0, orbit: 0, yaw: 0, speed: 0 };
const HELD = new Set(['lift', 'carry', 'return', 'drop']);
const hang = new THREE.Vector3(), heliTarget = new THREE.Vector3(), heliPrev = new THREE.Vector3(), heliVel = new THREE.Vector3();
const hoverAt = new THREE.Vector3(), hook = new THREE.Vector3(), ropeEnd = new THREE.Vector3(), Y_UP = new THREE.Vector3(0, 1, 0);
const ease = x => x * x * (3 - 2 * x);
let pickupIn = 0;
// Off to one side of the view (never behind the camera), close enough to arrive in a few seconds.
function farPoint(out, sign) {
  const a = Math.atan2(camera.position.z - PEAK.y, camera.position.x - PEAK.x) + sign * 1.45;
  return out.set(PEAK.x + Math.cos(a) * 85, PEAK_Y + 26, PEAK.y + Math.sin(a) * 85);
}
function setState(name) { heliCtl.state = name; heliCtl.t = 0; }
function callHeli(spot) {
  heliCtl.spot.copy(spot);
  if (heliCtl.state === 'off') { farPoint(heli.position, 1); heliPrev.copy(heli.position); }
  heli.visible = true;
  setState('in');
}
// The reader went back down the tour: return a carried explorer, or call the helicopter off before it grabs.
function heliGoBack() {
  const st = heliCtl.state;
  if (st === 'lift' || st === 'carry') setState('return');
  else if (st === 'in' || st === 'lower') setState('release');
}
// Advances the helicopter; returns where the explorer hangs while it is held, or null when it stands on the trail.
function updateHeli(dt) {
  const c = heliCtl;
  if (c.state === 'off') return null;
  c.t += dt;
  hoverAt.copy(c.spot).y += HOVER;
  heliTarget.copy(hoverAt);
  const near = heli.position.distanceTo(hoverAt) < 0.8;
  switch (c.state) {
    case 'in': c.ropeLen = 0; if (near) setState('lower'); break;
    case 'lower':
      c.ropeLen = ROPE_DOWN * ease(Math.min(1, c.t / 0.7));
      if (c.t > 0.8) { setState('lift'); c.lift = 0; hang.copy(c.spot); play('lift'); }
      break;
    case 'lift':
      c.lift = LIFT * ease(Math.min(1, c.t / 1.2)); c.ropeLen = ROPE_DOWN - c.lift;
      if (c.t > 1.3) { setState('carry'); c.orbit = Math.atan2(c.spot.z - PEAK.y, c.spot.x - PEAK.x); }
      break;
    case 'carry': {
      c.orbit += dt * 0.3 * Math.min(1, c.t / 2);
      const r = 17 * Math.min(1, c.t / 2.5);
      heliTarget.set(PEAK.x + Math.cos(c.orbit) * r, hoverAt.y + 3 + Math.sin(c.t * 0.9) * 0.8, PEAK.y + Math.sin(c.orbit) * r);
      c.ropeLen = ROPE_CARRY;
      break;
    }
    case 'return': c.ropeLen = ROPE_CARRY; if (near && hang.distanceTo(tmpV.copy(c.spot).setY(c.spot.y + LIFT)) < 1) setState('drop'); break;
    case 'drop':
      c.lift = LIFT * (1 - ease(Math.min(1, c.t / 1.3))); c.ropeLen = ROPE_DOWN - c.lift;
      if (c.t > 1.35) { setState('release'); play('land'); }
      break;
    case 'release':
      if (c.t <= dt) c.ropeFrom = c.ropeLen;   // wind in from wherever the rope was
      c.ropeLen = c.ropeFrom * (1 - ease(Math.min(1, c.t / 0.6))); if (c.t > 0.65) setState('away'); break;
    case 'away': c.ropeLen = 0; farPoint(heliTarget, -1);
      if (c.t > 5) { setState('off'); heli.visible = false; rope.visible = false; setHeli(0); return null; }
      break;
  }
  // Fly toward the target at a capped speed, slowing as it arrives, so it never snaps.
  heliPrev.copy(heli.position);
  tmpV.subVectors(heliTarget, heli.position);
  const dist = tmpV.length(), speed = Math.min(32, dist * 1.5);
  if (dist > 0.001) heli.position.addScaledVector(tmpV, Math.min(1, (speed * dt) / dist));
  heliVel.subVectors(heli.position, heliPrev).divideScalar(Math.max(dt, 1e-3));
  c.speed = heliVel.length();
  // Nose along the flight, pitched forward with speed; hovering, side-on to the camera.
  const wantYaw = c.speed > 1.5 ? Math.atan2(heliVel.x, heliVel.z) : Math.atan2(camera.position.x - heli.position.x, camera.position.z - heli.position.z) + Math.PI / 2;
  c.yaw += Math.atan2(Math.sin(wantYaw - c.yaw), Math.cos(wantYaw - c.yaw)) * (1 - Math.exp(-dt * 3));
  heli.rotation.set(Math.min(0.28, c.speed * 0.012), c.yaw, 0);
  heliRotor.rotation.y += dt * 28;
  heliTail.rotation.x += dt * 34;
  setHeli(Math.max(0, 1 - heli.position.distanceTo(camera.position) / 260));

  // Where the explorer is while held: straight up and down at the summit spot, or swinging under the helicopter.
  const held = HELD.has(c.state);
  if (held) {
    const k = 1 - Math.exp(-dt * 2.6);
    if (c.state === 'lift' || c.state === 'drop') { hang.x += (c.spot.x - hang.x) * k; hang.z += (c.spot.z - hang.z) * k; hang.y = c.spot.y + c.lift; }
    else { hang.x += (heli.position.x - hang.x) * k; hang.z += (heli.position.z - hang.z) * k; hang.y = heli.position.y - HOOK - ROPE_CARRY - GRAB; }
  }
  // The rope: from the hook to the explorer's back while held, otherwise hanging straight down.
  hook.copy(heli.position).y -= HOOK;
  if (held) ropeEnd.copy(hang).y += GRAB; else ropeEnd.copy(hook).y -= c.ropeLen;
  tmpV.subVectors(ropeEnd, hook);
  const len = tmpV.length();
  rope.visible = len > 0.05;
  if (rope.visible) {
    rope.position.copy(hook).addScaledVector(tmpV, 0.5);
    rope.scale.set(1, len, 1);
    rope.quaternion.setFromUnitVectors(Y_UP, tmpV.multiplyScalar(-1 / len));
  }
  return held ? hang : null;
}
let wasMoving = false, walkDir = 1, celebrated = false, celebrate = 0;

// ---------- confetti for the summit: voxel bits thrown up from the peak, tumbling down ----------
const CONFETTI = 240;
const confetti = new THREE.InstancedMesh(new THREE.BoxGeometry(0.55, 0.55, 0.1), new THREE.MeshBasicMaterial({ side: THREE.DoubleSide }), CONFETTI);
confetti.visible = false;
confetti.frustumCulled = false;
const bits = Array.from({ length: CONFETTI }, () => ({ p: new THREE.Vector3(), v: new THREE.Vector3(), r: new THREE.Euler(), w: new THREE.Vector3() }));
{
  const colors = ['#eeb54c', '#e0603c', '#f6f0dc', '#9fdcb5', '#f2c46d'].map(c => new THREE.Color(c));
  for (let i = 0; i < CONFETTI; i++) confetti.setColorAt(i, colors[i % colors.length]);
}
scene.add(confetti);
let confettiT = -1;
const cm = new THREE.Matrix4(), cq = new THREE.Quaternion(), cs = new THREE.Vector3();
function burst(at) {
  for (const b of bits) {
    const a = Math.random() * Math.PI * 2, up = 14 + Math.random() * 12, out = 6 + Math.random() * 12;
    b.p.copy(at).add(cs.set(0, DINO_H * 0.8, 0));
    b.v.set(Math.cos(a) * out, up, Math.sin(a) * out);
    b.r.set(Math.random() * 6, Math.random() * 6, Math.random() * 6);
    b.w.set((Math.random() - 0.5) * 12, (Math.random() - 0.5) * 12, (Math.random() - 0.5) * 12);
  }
  confettiT = 0; confetti.visible = true;
}
function stepConfetti(dt) {
  if (confettiT < 0) return;
  confettiT += dt;
  const fade = Math.max(0, 1 - Math.max(0, confettiT - 3.2) / 1.2);
  for (let i = 0; i < CONFETTI; i++) {
    const b = bits[i];
    b.v.y -= 14 * dt; b.v.multiplyScalar(1 - 1.1 * dt);          // gravity and air: it flutters rather than drops
    b.p.addScaledVector(b.v, dt);
    b.r.x += b.w.x * dt; b.r.y += b.w.y * dt; b.r.z += b.w.z * dt;
    confetti.setMatrixAt(i, cm.compose(b.p, cq.setFromEuler(b.r), cs.setScalar(fade)));
  }
  confetti.instanceMatrix.needsUpdate = true;
  if (fade <= 0) { confettiT = -1; confetti.visible = false; }
}
const finish = document.querySelector('.finish-banner');
let finishTimer = 0, finishHide = 0;
function showFinish() {
  // Never over an open section: it would cover the pop-up's own bar.
  if (!finish || journey.open > 0) return;
  finish.hidden = false;
  requestAnimationFrame(() => finish.classList.add('is-in'));
  clearTimeout(finishTimer); clearTimeout(finishHide);
  finishTimer = setTimeout(() => { finish.classList.remove('is-in'); finishHide = setTimeout(() => { finish.hidden = true; }, 500); }, 4200);
}

// ---------- trees: kept off the trail, with a clearing at each stop so the explorer is never hidden ----------
// Samples about 2 units apart along the trail: close enough for a 3.2-unit clearance.
const trailXZ = Array.from({ length: 160 }, (_, i) => trail.getPointAt(i / 159));
function nearTrail(x, z) {
  for (const w of waypoints) if (Math.hypot(w.x - x, w.z - z) < 9) return true;
  for (const t of trailXZ) if (Math.hypot(t.x - x, t.z - z) < 3.2) return true;
  return false;
}

{
  const count = mobile ? 260 : 620;
  const geo = new THREE.ConeGeometry(1, 3.4, 6); geo.translate(0, 1.7, 0);
  const mesh = new THREE.InstancedMesh(geo, new THREE.MeshLambertMaterial({ flatShading: true }), count);
  const m = new THREE.Matrix4(), q = new THREE.Quaternion(), s = new THREE.Vector3(), p = new THREE.Vector3(), c = new THREE.Color();
  let placed = 0, tries = 0;
  while (placed < count && tries++ < count * 40) {
    const x = (R() - 0.5) * 260, z = (R() - 0.5) * 260, h = height(x, z);
    if (h < 2.2 || h > 22) continue;
    const slope = Math.abs(height(x + 1, z) - h) + Math.abs(height(x, z + 1) - h);
    if (slope > 1.6 || nearTrail(x, z)) continue;
    const k = 0.7 + R() * 1.1;
    m.compose(p.set(x, h - 0.2, z), q.setFromAxisAngle(new THREE.Vector3(0, 1, 0), R() * 6.28), s.set(k, k * (0.9 + R() * 0.5), k));
    mesh.setMatrixAt(placed, m);
    mesh.setColorAt(placed, c.set(R() < 0.5 ? '#2f6b4a' : '#3d7d52').offsetHSL(0, 0, (R() - 0.5) * 0.06));
    placed++;
  }
  mesh.count = placed;
  scene.add(mesh);
}

// ---------- clouds: soft white puffs out on the horizon, never between the camera and the page ----------
const clouds = [];
{
  const mat = new THREE.MeshLambertMaterial({ color: '#ffffff', emissive: '#c9d6cf', transparent: true, opacity: 0.9 });
  for (let i = 0; i < (mobile ? 8 : 13); i++) {
    const g = new THREE.Group();
    for (let k = 0; k < 5 + Math.floor(R() * 4); k++) {
      const r = 3 + R() * 4.5;
      const puff = new THREE.Mesh(new THREE.IcosahedronGeometry(r, 1), mat);
      puff.position.set((k - 3) * 4.2 + (R() - 0.5) * 3, (R() - 0.3) * 2.2, (R() - 0.5) * 6);
      g.add(puff);
    }
    // Flattened, like fair-weather cumulus: wide and low.
    g.scale.set(1.3, 0.55, 1);
    // Only on the side the title screen leaves open (screen right from base camp), so none drift behind the words.
    let a = 0;
    do { a = R() * Math.PI * 2; } while (-0.67 * Math.cos(a) + 0.75 * Math.sin(a) < 0.15);
    const d = 170 + R() * 120;
    g.position.set(Math.cos(a) * d, 26 + R() * 18, Math.sin(a) * d);
    g.userData = { home: g.position.clone(), phase: R() * 6.28, speed: 0.08 + R() * 0.08 };
    scene.add(g); clouds.push(g);
  }
}

// ---------- birds circling the peak ----------
const birds = [];
{
  const wing = new THREE.BufferGeometry();
  wing.setAttribute('position', new THREE.Float32BufferAttribute([0, 0, 0, -1.6, 0.25, -0.4, -1.6, 0.25, 0.4, 0, 0, 0, 1.6, 0.25, -0.4, 1.6, 0.25, 0.4], 3));
  const mat = new THREE.MeshBasicMaterial({ color: '#203b30', side: THREE.DoubleSide });
  for (let i = 0; i < 7; i++) {
    const b = new THREE.Mesh(wing.clone(), mat);
    b.userData = { r: 30 + R() * 30, a: R() * 6.28, h: 34 + R() * 14, s: 0.18 + R() * 0.14, f: 4 + R() * 3 };
    scene.add(b); birds.push(b);
  }
}

// ---------- a giant compass floating off the beach: the brand, in the world ----------
const compass = new THREE.Group();
{
  const disk = new THREE.Mesh(new THREE.RingGeometry(13, 14.2, 96), new THREE.MeshBasicMaterial({ color: '#f5f2e9', side: THREE.DoubleSide, transparent: true, opacity: 0.9 }));
  const inner = new THREE.Mesh(new THREE.RingGeometry(9.6, 10, 96), new THREE.MeshBasicMaterial({ color: '#f5f2e9', side: THREE.DoubleSide, transparent: true, opacity: 0.6 }));
  compass.add(disk, inner);
  const tickMat = new THREE.MeshBasicMaterial({ color: '#f5f2e9', side: THREE.DoubleSide });
  for (let i = 0; i < 16; i++) {
    const t = new THREE.Mesh(new THREE.PlaneGeometry(i % 4 ? 0.25 : 0.55, i % 4 ? 1.2 : 2.4), tickMat);
    const a = (i / 16) * Math.PI * 2; t.position.set(Math.sin(a) * 12, Math.cos(a) * 12, 0); t.rotation.z = -a;
    compass.add(t);
  }
  const shape = new THREE.Shape(); shape.moveTo(0, 9); shape.lineTo(1.4, 0); shape.lineTo(0, -9); shape.lineTo(-1.4, 0); shape.closePath();
  const needle = new THREE.Group();
  const north = new THREE.Mesh(new THREE.ShapeGeometry(shape), new THREE.MeshBasicMaterial({ color: '#c7672f', side: THREE.DoubleSide }));
  north.scale.y = 1; const southShape = new THREE.Shape(); southShape.moveTo(-1.4, 0); southShape.lineTo(1.4, 0); southShape.lineTo(0, -9); southShape.closePath();
  const south = new THREE.Mesh(new THREE.ShapeGeometry(southShape), new THREE.MeshBasicMaterial({ color: '#f5f2e9', side: THREE.DoubleSide }));
  south.position.z = 0.05;
  needle.add(north, south);
  compass.add(needle);
  compass.userData.needle = needle;
  compass.position.set(-128, 9, 108);
  compass.rotation.set(-1.25, 0, 0.35);
  scene.add(compass);
}

// ---------- sky, fog and light: morning at base camp, gold at the summit ----------
const skyUniforms = { top: { value: new THREE.Color() }, horizon: { value: new THREE.Color() }, sun: { value: new THREE.Vector3() } };
scene.add(new THREE.Mesh(new THREE.SphereGeometry(1800, 32, 16), new THREE.ShaderMaterial({
  uniforms: skyUniforms, side: THREE.BackSide, depthWrite: false,
  vertexShader: 'varying vec3 vDir; void main(){ vDir = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
  fragmentShader: `uniform vec3 top; uniform vec3 horizon; uniform vec3 sun; varying vec3 vDir;
    void main(){
      float h = clamp(vDir.y * 1.6, 0.0, 1.0);
      vec3 c = mix(horizon, top, pow(h, 0.7));
      float s = max(dot(normalize(vDir), normalize(sun)), 0.0);
      c += vec3(1.0, 0.86, 0.6) * (pow(s, 600.0) * 1.4 + pow(s, 12.0) * 0.18);
      gl_FragColor = vec4(c, 1.0);
    }`,
})));
scene.fog = new THREE.Fog('#f2efe2', 160, 900);
const hemi = new THREE.HemisphereLight('#f6f3e6', '#3f6b52', 1.15);
const sunLight = new THREE.DirectionalLight('#fff4dc', 1.6);
scene.add(hemi, sunLight);
const SKY = [
  { top: '#b9d6cd', horizon: '#f4f0e2', sun: '#fff4dc', fog: '#f2efe2', elev: 0.55 },
  { top: '#a7cbd0', horizon: '#eef1e6', sun: '#ffffff', fog: '#eef0e6', elev: 0.9 },
  { top: '#3d5d6b', horizon: '#f0c79a', sun: '#ffc98a', fog: '#e9cfae', elev: 0.12 },
];
for (const k of SKY) for (const f of ['top', 'horizon', 'sun', 'fog']) k[f] = new THREE.Color(k[f]);   // parsed once
const tmpA = new THREE.Color(), tmpB = new THREE.Color();
function applySky(t) {
  const k = t * (SKY.length - 1), i = Math.min(SKY.length - 2, Math.floor(k)), f = k - i, a = SKY[i], b = SKY[i + 1];
  skyUniforms.top.value.copy(a.top).lerp(b.top, f);
  skyUniforms.horizon.value.copy(a.horizon).lerp(b.horizon, f);
  scene.fog.color.copy(a.fog).lerp(b.fog, f);
  sunLight.color.copy(a.sun).lerp(b.sun, f);
  const elev = a.elev + (b.elev - a.elev) * f, az = -0.6 + t * 1.4;
  skyUniforms.sun.value.set(Math.cos(az) * Math.cos(elev), Math.sin(elev), Math.sin(az) * Math.cos(elev));
  sunLight.position.copy(skyUniforms.sun.value).multiplyScalar(300);
}


// ---------- the HUD ----------
const hud = {
  coords: document.querySelector('.hud-coords'), alt: document.querySelector('.hud-alt'),
 name: document.querySelector('.hud-wp-name'),
  fill: document.querySelector('.hud-bar-fill'),
  map: document.querySelector('.hud-map-canvas'),
  tag: document.querySelector('.beacon-tag'), tagText: document.querySelector('.beacon-tag-text'),
  dino: document.querySelector('.hud-status .dino use'),
};
const LAST = waypoints.length - 1;

// Minimap: the island drawn once from the same heights, the trail and waypoints on top.
const mapCtx = hud.map.getContext('2d');
const MAP = 168, WORLD = 250;
const toMap = (x, z) => [(x / WORLD + 0.5) * MAP, (z / WORLD + 0.5) * MAP];
const mapBase = document.createElement('canvas'); mapBase.width = mapBase.height = MAP;
{
  const ctx = mapBase.getContext('2d'), img = ctx.createImageData(MAP, MAP);
  for (let py = 0; py < MAP; py++) for (let px = 0; px < MAP; px++) {
    const x = (px / MAP - 0.5) * WORLD, z = (py / MAP - 0.5) * WORLD, h = height(x, z);
    const c = h < -1 ? PALETTE.deep.clone().lerp(PALETTE.shallow, Math.max(0, (h + 6) / 5)) : groundColor(Math.max(h, 0), 0);
    const o = (py * MAP + px) * 4; img.data[o] = c.r * 255; img.data[o + 1] = c.g * 255; img.data[o + 2] = c.b * 255; img.data[o + 3] = 255;
  }
  ctx.putImageData(img, 0, 0);
  ctx.strokeStyle = '#fffbea'; ctx.lineWidth = 1.2; ctx.setLineDash([3, 3]); ctx.beginPath();
  for (let i = 0; i <= 120; i++) { const p = trail.getPointAt(i / 120); const [mx, my] = toMap(p.x, p.z); i ? ctx.lineTo(mx, my) : ctx.moveTo(mx, my); }
  ctx.stroke(); ctx.setLineDash([]);
  for (const [i, w] of waypoints.entries()) {
    const [mx, my] = toMap(w.x, w.z);
    ctx.fillStyle = i === LAST ? '#d9a441' : '#10261e'; ctx.beginPath(); ctx.arc(mx, my, 3, 0, 7); ctx.fill();
    ctx.strokeStyle = '#fffbea'; ctx.lineWidth = 1; ctx.stroke();
  }
}
// The minimap is round and turns with the camera, like a game's: what the view faces is up. A compass ring round
// the edge keeps north findable; a soft cone from the explorer shows what the camera sees.
let mapRot = 0;
const MAP_R = MAP / 2 - 13;   // the map disc; the ring of letters sits outside it
function drawMap(p, heading) {
  const c = MAP / 2;
  const dx = look.x - camera.position.x, dz = look.z - camera.position.z;
  if (Math.hypot(dx, dz) > 0.01) mapRot = -Math.PI / 2 - Math.atan2(dz, dx);
  mapCtx.clearRect(0, 0, MAP, MAP);
  mapCtx.save();
  mapCtx.beginPath(); mapCtx.arc(c, c, MAP_R, 0, Math.PI * 2); mapCtx.clip();
  mapCtx.translate(c, c); mapCtx.rotate(mapRot); mapCtx.translate(-c, -c);
  mapCtx.drawImage(mapBase, 0, 0);
  const [mx, my] = toMap(p.x, p.z);
  // View cone: from the explorer toward the top of the (rotated) map, i.e. the way the camera looks.
  mapCtx.save(); mapCtx.translate(mx, my); mapCtx.rotate(-mapRot);
  const cone = mapCtx.createRadialGradient(0, 0, 0, 0, 0, 34);
  cone.addColorStop(0, '#fff3c466'); cone.addColorStop(1, '#fff3c400');
  mapCtx.fillStyle = cone; mapCtx.beginPath(); mapCtx.moveTo(0, 0); mapCtx.arc(0, 0, 34, -Math.PI / 2 - 0.42, -Math.PI / 2 + 0.42); mapCtx.closePath(); mapCtx.fill();
  mapCtx.restore();
  mapCtx.save(); mapCtx.translate(mx, my); mapCtx.rotate(heading);
  mapCtx.fillStyle = '#f2b84b'; mapCtx.strokeStyle = '#10261e'; mapCtx.lineWidth = 1.6;
  mapCtx.beginPath(); mapCtx.moveTo(0, -10); mapCtx.lineTo(6.5, 7); mapCtx.lineTo(0, 3.5); mapCtx.lineTo(-6.5, 7); mapCtx.closePath(); mapCtx.fill(); mapCtx.stroke();
  mapCtx.restore();
  mapCtx.restore();
  // Compass ring: the rim, a tick every 30°, and N E S W turning with the map.
  mapCtx.strokeStyle = '#eef3ea55'; mapCtx.lineWidth = 1;
  mapCtx.beginPath(); mapCtx.arc(c, c, MAP_R + 0.5, 0, Math.PI * 2); mapCtx.stroke();
  for (let i = 0; i < 12; i++) {
    if (i % 3 === 0) continue;
    const a = mapRot - Math.PI / 2 + (i * Math.PI) / 6;
    mapCtx.beginPath(); mapCtx.moveTo(c + Math.cos(a) * (MAP_R + 3), c + Math.sin(a) * (MAP_R + 3));
    mapCtx.lineTo(c + Math.cos(a) * (MAP_R + 7), c + Math.sin(a) * (MAP_R + 7)); mapCtx.stroke();
  }
  mapCtx.font = '700 10px ui-monospace, SFMono-Regular, Menlo, monospace'; mapCtx.textAlign = 'center'; mapCtx.textBaseline = 'middle';
  for (const [i, l] of ['N', 'E', 'S', 'W'].entries()) {
    const a = mapRot - Math.PI / 2 + (i * Math.PI) / 2, x = c + Math.cos(a) * (MAP_R + 7), y = c + Math.sin(a) * (MAP_R + 7);
    if (l === 'N') { mapCtx.fillStyle = '#10261e'; mapCtx.beginPath(); mapCtx.arc(x, y, 6.5, 0, Math.PI * 2); mapCtx.fill(); }
    mapCtx.fillStyle = l === 'N' ? '#e3b45a' : '#b6cabb'; mapCtx.fillText(l, x, y + 0.5);
  }
}
// Tap a waypoint on the map to walk there (undoing the map's turn to find what was tapped).
hud.map.addEventListener('click', e => {
  const r = hud.map.getBoundingClientRect(), c = MAP / 2;
  const qx = (e.clientX - r.left) * MAP / r.width - c, qy = (e.clientY - r.top) * MAP / r.height - c;
  const mx = c + qx * Math.cos(-mapRot) - qy * Math.sin(-mapRot), my = c + qx * Math.sin(-mapRot) + qy * Math.cos(-mapRot);
  let best = -1, bestD = 18;
  for (const [i, w] of waypoints.entries()) { const [wx, wy] = toMap(w.x, w.z), d = Math.hypot(wx - mx, wy - my); if (d < bestD) { bestD = d; best = i; } }
  if (best >= 0) journey.travel(best);
});

// ---------- flags on screen: what a click or tap on the world means ----------
const flagScreen = waypoints.map(() => ({ x: 0, y: 0, on: false }));
const tmpV = new THREE.Vector3(), tmpH = new THREE.Vector3();
function projectFlags() {
  for (const [i, w] of waypoints.entries()) {
    tmpV.copy(w).setY(w.y + DINO_H + (curious || exclaim > 0 ? 6.4 : 2.6)).project(camera);
    const f = flagScreen[i];
    f.x = ((tmpV.x + 1) / 2) * viewW; f.y = ((1 - tmpV.y) / 2) * viewH;
    f.on = tmpV.z < 1 && Math.abs(tmpV.x) < 1 && Math.abs(tmpV.y) < 1;
  }
}
function flagAt(x, y) {
  let best = -1, bestD = mobile ? 40 : 34;
  for (const [i, f] of flagScreen.entries()) { if (!f.on) continue; const d = Math.hypot(f.x - x, f.y - y); if (d < bestD) { bestD = d; best = i; } }
  return best;
}
let hover = -1;
canvas.addEventListener('pointermove', e => {
  hover = flagAt(e.clientX, e.clientY);
  canvas.style.cursor = hover >= 0 || dinoAt(e.clientX, e.clientY) ? 'pointer' : '';
}, { passive: true });
canvas.addEventListener('pointerleave', () => { hover = -1; canvas.style.cursor = ''; });
// A flag walks there and opens its stop; empty ground closes whatever is open.
// The dinosaur itself: a tap or click makes it jump, like its namesake in Chrome's offline page.
let jumpT = -1;
function dinoAt(x, y) {
  if (heliCtl.state !== 'off') return false;
  tmpV.copy(explorer.position).setY(explorer.position.y + DINO_H * 0.5).project(camera);
  const sx = ((tmpV.x + 1) / 2) * viewW, sy = ((1 - tmpV.y) / 2) * viewH;
  // Hit radius from the dinosaur's size on screen, never under a comfortable finger.
  tmpH.copy(explorer.position).setY(explorer.position.y + DINO_H).project(camera);
  const r = Math.max(mobile ? 34 : 26, Math.abs(((1 - tmpH.y) / 2) * viewH - sy) * 1.1);
  return tmpV.z < 1 && Math.hypot(sx - x, sy - y) < r;
}
canvas.addEventListener('click', e => {
  if (dinoAt(e.clientX, e.clientY)) { if (jumpT < 0 && !reduced) { jumpT = 0; play('jump'); if (curious) wakeUp(); } return; }
  const i = flagAt(e.clientX, e.clientY);
  if (i < 0) { if (journey.open > 0) journey.close(); return; }
  journey.travel(i, { open: i > 0 });
});
hud.tag.addEventListener('click', () => {
  const at = Math.round(journey.stage);
  if (at === 0) journey.travel(1); else journey.travel(at, { open: true });
});

// The tag over the flag never sits on text or controls.
// Text and controls the tag must not cover. Found once; their boxes are read only at the 10 Hz HUD tick.
const tagBlockers = [...document.querySelectorAll('.site-header > *, .prompt, .hero .panel, .hero-facts, .hud-map')];
let tagW = 0, tagH = 0;
addEventListener('resize', () => { tagW = 0; });
function setText(el, text) { if (el.textContent !== text) el.textContent = text; }
function tagBlocked(x, y, w, h) {
  for (const el of tagBlockers) {
    // Hidden things have no box (display:none) or are flagged by the classes that hide them.
    if (el.closest('.stage.hero') && root.classList.contains('hero-away')) continue;
    if (el.classList.contains('prompt') && !root.classList.contains('prompt-on')) continue;
    const r = el.getBoundingClientRect();
    if (!r.width) continue;
    if (x < r.right + 8 && x + w > r.left - 8 && y < r.bottom + 8 && y + h > r.top - 8) return true;
  }
  return false;
}

const stageU = s => { const i = Math.min(LAST - 1, Math.floor(s)), f = s - i; return wpU[i] + (wpU[i + 1] - wpU[i]) * f; };

let shown = journey.stage, last = performance.now(), time = 0, hudTick = 0, tagLabel = '', dinoTick = 0, dinoFrame = '#dino';
const view = { x: journey.view.x, y: journey.view.y, zoom: journey.view.zoom };
const target = new THREE.Vector3(), camPos = new THREE.Vector3(), look = new THREE.Vector3(), mouse = new THREE.Vector2(), mouseS = new THREE.Vector2();
// The establishing shot: on a wide screen the island shares the view with the title, so it stands further back.
const BASE_DIST = mobile ? 150 : 192, BASE_LIFT = mobile ? 78 : 98;
const ORBIT0 = Math.atan2(waypoints[0].x, waypoints[0].z);
const CENTER = new THREE.Vector3(0, 6, 0), trailP = new THREE.Vector3(), trailAhead = new THREE.Vector3();
let dinoYaw = 0;
// Waiting at base camp until the reader scrolls, taps or presses a key (not with reduced motion, nor when the page
// opens part-way along the tour). Brought back to base camp and left there, it grows unsure again.
let curious = !reduced && journey.stage < 0.02, exclaim = 0, homeIdle = 0;
function puzzle() {
  curious = true; exclaim = 0;
  play('puzzle');
  thoughtMarks['?'].visible = true; thoughtMarks['!'].visible = false;
}
function wakeUp() {
  if (!curious) return;
  curious = false; exclaim = 0.9;
  play('wake');
  thoughtMarks['?'].visible = false; thoughtMarks['!'].visible = true;
}
for (const ev of ['wheel', 'pointerdown', 'keydown', 'touchstart']) addEventListener(ev, () => { homeIdle = 0; wakeUp(); }, { passive: true });
// Mouse only: a scrolling finger must not tilt the camera and leave it tilted.
addEventListener('pointermove', e => { if (e.pointerType === 'mouse') mouse.set(e.clientX / viewW - 0.5, e.clientY / viewH - 0.5); }, { passive: true });

// Sized from the canvas's own box, not viewW/viewH: on phones the toolbar makes those differ from what is
// shown, which stretched the picture and put flags and tap targets in the wrong place. Toolbar-sized height changes
// while scrolling are absorbed rather than reallocating the drawing buffer each time.
let viewW = 0, viewH = 0;
function resize() {
  const w = canvas.clientWidth || innerWidth, h = canvas.clientHeight || innerHeight;
  if (w === viewW && Math.abs(h - viewH) < 1) return;
  viewW = w; viewH = h;
  renderer.setSize(w, h, false);
  camera.aspect = w / h; camera.updateProjectionMatrix();
}
let resizeTimer = 0;
addEventListener('resize', () => { clearTimeout(resizeTimer); resizeTimer = setTimeout(resize, 120); });
resize();

function frame(now) {
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
  if (!reduced) time += dt;
  // Ease toward the trail position and framing: the camera travels rather than jumps.
  const k = reduced ? 1 : 1 - Math.exp(-dt * 3.2);
  shown += (journey.stage - shown) * k;
  // While the helicopter holds the explorer, the walk waits at the summit; going back sends the helicopter home first.
  if (heliCtl.state !== 'off' && journey.stage < LAST - 0.05) heliGoBack();
  if (journey.stage < LAST - 0.05) pickupIn = 0;
  // Still at the summit with the celebration done and no helicopter about: it comes (again) after a short pause.
  else if (celebrated && pickupIn <= 0 && heliCtl.state === 'off' && !reduced) pickupIn = 1.2;
  const held = HELD.has(heliCtl.state);
  if (held) shown = LAST;
  view.x += (journey.view.x - view.x) * k; view.y += (journey.view.y - view.y) * k; view.zoom += (journey.view.zoom - view.zoom) * k;

  const u = stageU(Math.min(LAST, shown));
  const p = trail.getPointAt(Math.min(1, u), trailP), ahead = trail.getPointAt(Math.min(1, u + 0.01), trailAhead);
  const heading = Math.atan2(ahead.x - p.x, -(ahead.z - p.z));
  const moving = !reduced && !held && Math.abs(journey.stage - shown) > 0.004;
  if (moving) walkDir = journey.stage > shown ? 1 : -1;
  // A little hop in each stride while walking; standing still, the dinosaur only breathes.
  if (curious && (journey.stage > 0.02 || moving)) wakeUp();
  // Back home and left alone for a couple of seconds: puzzled again.
  homeIdle = !curious && !reduced && !moving && journey.open < 0 && journey.stage < 0.02 && shown < 0.02 ? homeIdle + dt : 0;
  if (homeIdle > 2.2) puzzle();
  // At the summit: every arrival is celebrated (jingle, confetti, banner, hops), then the helicopter comes for the
  // explorer. Leaving the summit re-arms it.
  const atSummit = !moving && journey.stage > LAST - 0.05 && shown > LAST - 0.05;
  if (atSummit && !reduced) {
    if (!celebrated) { celebrated = true; celebrate = 1.2; pickupIn = 1.6; play('victory'); setMood('summit'); burst(explorer.position); showFinish(); }
  } else if (!atSummit && shown < LAST - 0.5) { celebrated = false; setMood('trail'); }
  // The countdown only runs while no helicopter is about, so an earlier one still flying off cannot swallow it.
  if (pickupIn > 0 && heliCtl.state === 'off' && (pickupIn -= dt) <= 0 && atSummit) callHeli(p);
  const carried = updateHeli(dt);
  if (celebrate > 0) celebrate -= dt;
  if (jumpT >= 0) { jumpT += dt; if (jumpT > 0.62) jumpT = -1; }
  const hop = (jumpT >= 0 ? Math.sin((jumpT / 0.62) * Math.PI) * 3.2 : 0)
    + (curious && !reduced ? Math.max(0, Math.sin(time * 1.9)) ** 12 * 0.5 : 0)
    + (celebrate > 0 ? Math.abs(Math.sin((1.2 - celebrate) * Math.PI / 0.6)) * 1.3 : 0);
  if (carried) explorer.position.copy(carried);
  else explorer.position.copy(p).setY(p.y + hop + (moving ? Math.abs(Math.sin(time * 10.5)) * 0.18 : reduced ? 0 : Math.sin(time * 2) * 0.03));
  // Walking, the dinosaur faces along the trail. Stopped, it turns three-quarters toward the camera; while it waits
  // at base camp it looks one way and then the other, unsure where to go.
  const yawOf = (dx, dz) => Math.atan2(-dz, dx);
  // Facing the way it walks: up the trail, or back down it.
  const trailYaw = yawOf((ahead.x - p.x) * walkDir, (ahead.z - p.z) * walkDir), camYaw = yawOf(camera.position.x - p.x, camera.position.z - p.z);
  const side = Math.sign(Math.atan2(Math.sin(trailYaw - camYaw), Math.cos(trailYaw - camYaw))) || 1;
  let wantYaw = moving ? trailYaw : camYaw + side * 1.2;
  // Carried, it faces the way the helicopter flies.
  if (carried && heliCtl.speed > 1.5) wantYaw = yawOf(heliVel.x, heliVel.z);
  if (curious && !reduced) wantYaw = camYaw + (Math.sin(time * 0.85) > 0 ? 1.25 : -1.25) + Math.sin(time * 3.1) * 0.06;
  const turn = Math.atan2(Math.sin(wantYaw - dinoYaw), Math.cos(wantYaw - dinoYaw));
  dinoYaw += turn * (reduced ? 1 : 1 - Math.exp(-dt * (curious ? 5 : 6)));
  explorer.rotation.y = dinoYaw;

  // Wide establishing shot at base camp, fully closed in by the first stop (t = 1/6), so every stop is a close-up;
  // rising for the summit; closer still while a
  // stop is open, as if the explorer stopped to read.
  const t = Math.min(1, shown / LAST);
  // The opening shot looks at base camp from the sea, so the start stands front and centre; once the tour is under
  // way the camera swings round to follow the trail.
  const orbit = THREE.MathUtils.lerp(ORBIT0, -2.3 + t * 2.2, smooth(0, 0.2, t)) + (reduced ? 0 : Math.sin(time * 0.08) * 0.08) + mouseS.x * 0.25;
  const dist = (THREE.MathUtils.lerp(BASE_DIST, 44, smooth(0, 0.16, t)) + (t > 0.85 ? (t - 0.85) * 160 : 0)) * view.zoom;
  const lift = (THREE.MathUtils.lerp(BASE_LIFT, 22, smooth(0, 0.16, t)) + (t > 0.85 ? (t - 0.85) * 120 : 0)) * (0.55 + 0.45 * view.zoom) - mouseS.y * 6;
  target.copy(p).lerp(CENTER, 1 - smooth(0, 0.15, t));
  camPos.set(target.x + Math.sin(orbit) * dist, target.y + lift, target.z + Math.cos(orbit) * dist);
  camera.position.lerp(camPos, reduced ? 1 : 1 - Math.exp(-dt * 2.4));
  look.lerp(target, reduced ? 1 : 1 - Math.exp(-dt * 3));
  camera.lookAt(look);
  // Move the subject into the open part of the screen, clear of the hero text, the prompt or a pop-up.
  if (Math.abs(view.x) > 0.5 || Math.abs(view.y) > 0.5) camera.setViewOffset(viewW, viewH, -view.x, -view.y, viewW, viewH);
  else camera.clearViewOffset();
  mouseS.lerp(mouse, reduced ? 0 : 1 - Math.exp(-dt * 2));

  applySky(t);
  seaUniforms.uTime.value = time;
  trailUniforms.uTime.value = time;
  trailUniforms.uProgress.value = u;

  const active = Math.round(Math.min(LAST, Math.max(0, shown)));
  for (const [i, b] of beacons.entries()) {
    const want = i === active ? 1 : i === hover ? 0.6 : 0;
    b.glow += (want - b.glow) * (reduced ? 1 : 1 - Math.exp(-dt * 4));
    b.ring.material.opacity = 0.15 + b.glow * 0.75;
    b.ring.scale.setScalar(1 + b.glow * (0.4 + (reduced ? 0 : Math.sin(time * 4) * 0.15)));
    if (i === 0) continue;   // base camp has no flag
    // The pole stays planted; the pennant turns its face to the camera, waving a little, so it is never seen edge-on.
    b.marker.rotation.y = Math.atan2(camera.position.x - b.marker.position.x, camera.position.z - b.marker.position.z);
    b.flag.rotation.y = reduced ? 0 : Math.sin(time * 2.4 + i) * 0.2;
    b.reached = (b.reached ?? 0) + ((i <= shown + 0.05 ? 1 : 0) - (b.reached ?? 0)) * (reduced ? 1 : 1 - Math.exp(-dt * 5));
    b.flag.material.color.copy(FLAG_AHEAD).lerp(FLAG_REACHED, b.reached);
  }
  // Clouds sway around where they were placed rather than circling, so they stay out of the title's side.
  for (const c of clouds) {
    if (reduced) break;
    const u = c.userData;
    c.position.set(u.home.x + Math.sin(time * u.speed + u.phase) * 9, u.home.y + Math.sin(time * u.speed * 1.7 + u.phase) * 1.2, u.home.z + Math.cos(time * u.speed + u.phase) * 6);
  }
  for (const b of birds) {
    const d = b.userData; d.a += d.s * dt * (reduced ? 0 : 1);
    b.position.set(PEAK.x + Math.cos(d.a) * d.r, d.h + Math.sin(d.a * 3) * 1.5, PEAK.y + Math.sin(d.a) * d.r);
    b.rotation.y = -d.a; b.scale.y = reduced ? 1 : 0.6 + Math.abs(Math.sin(time * d.f)) * 0.8;
  }
  compass.userData.needle.rotation.z = reduced ? 0.1 : Math.sin(time * 0.6) * 0.18 + Math.sin(time * 1.7) * 0.05;

  thought.visible = curious || exclaim > 0;
  if (thought.visible) {
    if (exclaim > 0) exclaim -= dt;
    thought.position.copy(explorer.position).add(tmpV.set(0, DINO_H + 1.4 + (reduced ? 0 : Math.sin(time * 2.6) * 0.2), 0));
    thought.rotation.y = Math.atan2(camera.position.x - thought.position.x, camera.position.z - thought.position.z);
    thought.scale.setScalar(exclaim > 0 ? 1 + Math.max(0, exclaim - 0.6) * 1.5 : 1);
  }

  stepConfetti(dt);
  renderer.render(scene, camera);

  // The dinosaur stands while the explorer rests and walks, two frames, while the camera travels: in the world and
  // in the HUD badge alike.
  dinoTick += dt;
  // Carried, its legs kick twice as fast.
  const frameWanted = carried ? (dinoTick % 0.16 < 0.08 ? '#dino-walk-a' : '#dino-walk-b')
    : !moving ? '#dino' : dinoTick % 0.3 < 0.15 ? '#dino-walk-a' : '#dino-walk-b';
  setDinoPose(frameWanted.slice(1));
  if (hud.dino && frameWanted !== dinoFrame) {
    dinoFrame = frameWanted; hud.dino.setAttribute('href', frameWanted);
    if (frameWanted !== '#dino' && !carried) play(walkDir < 0 ? 'stepBack' : 'step');
  }
  // Arriving at a stop (not passing through it): rising going up the trail, falling coming back to an earlier flag,
  // a low note home at base camp. The summit has its own fanfare.
  const stopNow = Math.round(shown);
  if (wasMoving && !moving && Math.abs(shown - stopNow) < 0.06) {
    if (walkDir < 0) play(stopNow === 0 ? 'home' : 'back');
    else if (stopNow > 0 && stopNow < LAST) play('reach');
  }
  wasMoving = moving;

  // The tag follows its flag every frame (on whole pixels, so the text stays crisp); whether it may show is checked
  // with the rest of the HUD below.
  projectFlags();
  const tagFlag = flagScreen[Math.round(Math.min(LAST, Math.max(0, shown)))];
  // Never let it ride up under the top bar while the camera moves; the next HUD check decides when it may return.
  if (hud.tag.classList.contains('is-shown') && (heliCtl.state !== 'off' || tagFlag.y - tagH - 22 < 70)) hud.tag.classList.remove('is-shown');
  if (hud.tag.classList.contains('is-shown')) hud.tag.style.transform = `translate3d(${Math.round(tagFlag.x)}px, ${Math.round(tagFlag.y)}px, 0) translate(-50%, calc(-100% - 22px))`;

  // The HUD is text: update it ten times a second, not every frame.
  if ((hudTick += dt) > 0.1 || reduced) {
    hudTick = 0;
    // Where the explorer stands: made-up coordinates and altitude that change as it walks.
    const lat = 38.65 + (-p.z) * 0.0011, lon = 9.25 - p.x * 0.0013;
    setText(hud.coords, `N ${lat.toFixed(4)}° · W ${lon.toFixed(4)}°`);
    setText(hud.alt, `ALT ${String(Math.max(0, Math.round(p.y * 18))).padStart(3, '0')} m`);
    setText(hud.name, journey.names[active] || '');
    const fill = `scaleX(${t.toFixed(3)})`;
    if (hud.fill.style.transform !== fill) hud.fill.style.transform = fill;
    // The arrow points where the dinosaur faces (its model faces +x, turned by dinoYaw), so it follows every turn of
    // its head, not just the trail's direction.
    if (hud.map.offsetParent && journey.open < 0) drawMap(explorer.position, Math.PI / 2 - dinoYaw);
    const label = journey.label(active);
    if (label !== tagLabel) { tagLabel = label; hud.tagText.textContent = label; tagW = 0; }
    // Hidden while the helicopter is busy: the tag would point at an empty summit or sit on the carried explorer.
    const f = flagScreen[active], arrived = journey.open < 0 && Math.abs(journey.stage - active) < 0.2 && heliCtl.state === 'off';
    if (!tagW) { tagW = hud.tag.offsetWidth; tagH = hud.tag.offsetHeight; }
    const w = tagW, h = tagH;
    const show = arrived && f.on && !tagBlocked(f.x - w / 2, f.y - h - 22, w, h);
    if (show && !hud.tag.classList.contains('is-shown')) hud.tag.style.transform = `translate3d(${Math.round(f.x)}px, ${Math.round(f.y)}px, 0) translate(-50%, calc(-100% - 22px))`;
    hud.tag.classList.toggle('is-shown', show);
  }
}

// One animation-frame chain at most: hidden tabs pause queued callbacks rather than drop them, so the id is kept and
// cancelled on stop, never just flagged.
let rafId = 0, stopped = false, lastDrawn = 0;
function loop(now) {
  rafId = requestAnimationFrame(loop);
  // A pop-up covers most of a phone's screen: half the frame rate there saves battery and nobody sees the difference.
  if (mobile && journey.open > 0 && now - lastDrawn < 30) return;
  lastDrawn = now;
  frame(now);
}
function setRunning() {
  const want = !document.hidden && !reduced && !stopped;
  if (want && !rafId) { last = performance.now(); rafId = requestAnimationFrame(loop); }
  if (!want && rafId) { cancelAnimationFrame(rafId); rafId = 0; }
  if (reduced && !stopped) frame(performance.now());
}
// With reduced motion the world is a still picture, redrawn when the reader moves along the trail.
journey.events.addEventListener('change', () => { if (reduced) requestAnimationFrame(() => frame(performance.now())); });
journey.events.addEventListener('exit', () => { stopped = true; setRunning(); });
addEventListener('resize', () => { if (reduced) frame(performance.now()); });
document.addEventListener('visibilitychange', setRunning);
reducedQuery.addEventListener('change', () => {
  reduced = reducedQuery.matches;
  // Switching to reduced motion mid-show: set the world down at once rather than freeze it mid-air.
  if (reduced) {
    setState('off'); heli.visible = false; rope.visible = false; setHeli(0); pickupIn = 0;
    confettiT = -1; confetti.visible = false; jumpT = -1;
    exclaim = 0; curious = false; thought.visible = false;
    shown = journey.stage;
  }
  setRunning();
});
root.classList.add('world-ready');
setRunning();
