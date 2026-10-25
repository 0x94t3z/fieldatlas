#!/usr/bin/env python3
"""Draw the animated README hero: docs/branding/field-atlas-hero-light.svg and field-atlas-hero-dark.svg.

The README shows one or the other with <picture> and prefers-color-scheme, which GitHub matches to each reader's
theme; neither has a background, so the page shows through. Text is set in Inter Tight and Instrument Serif Italic
(the faces of the demo poster and the figures) with HarfBuzz and drawn as outlines; motion is CSS keyframes, no
script, so the files play inside GitHub's <img>.

An atlas being drawn. The words never move, so the name reads from the first frame; only the map and the walker do.

  0.15 - 2.9 s  topographic contours print outward from a summit, every fifth one heavier with its height, as
                survey sheets mark index contours
  0.6 - 3.0     the app's pixel dinosaur (the Offline chip, read from FieldAtlasIcons.kt) walks in from the west
                edge, its two walking frames in turn, with a short tail of gold trail behind it, as on the website's
                island, that fades with distance and is gone once it stops
  3.05 - 4.25   beside it the mark lands on the summit: the tile, the mint disc, the needle spinning and settling
                north, the horizon bars; the dinosaur hops as it lands
  5.0 - 10.0    the map breathes; from 9.2 everything draws away, and the loop starts again

Readers who ask for reduced motion see the final lockup. The mark is the app icon on its rounded square tile (corner
radius 18 of 108), as the README icon draws it; its shapes and colours are read from
docs/branding/field-atlas-readme-icon.svg unchanged.

    pip install fonttools==4.55.3 uharfbuzz==0.42.0
    python3 tools/charts/make_hero.py [--out docs/branding] [--fonts DIR]   # DIR holds the two faces' static TTFs
"""
import argparse, io, math, pathlib, random, re

ROOT = pathlib.Path(__file__).resolve().parents[2]
W, H, LOOP = 1280, 440, 10.0
EASE_OUT, OVERSHOOT, EASE_IO = "cubic-bezier(.2,.8,.2,1)", "cubic-bezier(.3,1.5,.5,1)", "cubic-bezier(.65,0,.35,1)"
THEMES = {  # chosen for contrast on GitHub's light (#ffffff) and dark (#0d1117) pages; walked is the website's trail gold
    "light": dict(text="#0E1F18", accent="#12805F", eyebrow="#4F6B5E", line="#12805F", minor=.42, index=.8, walked="#B8862B"),
    "dark": dict(text="#EEF3EA", accent="#7FD9B3", eyebrow="#8FB9A2", line="#3DCB97", minor=.30, index=.62, walked="#D9A340"),
}


# ---------- type ----------
def _round(path):
    return re.sub(r"-?\d+\.\d+", lambda m: f"{float(m.group()):.1f}".rstrip("0").rstrip("."), path)


def set_line(font, text, size, x, y, tracking=0.0):
    """One outline path per glyph ("" for spaces), set with HarfBuzz (kerning on) on baseline y from x; and the width."""
    import uharfbuzz as hb
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen
    shaper = hb.Font(hb.Face(hb.Blob(font.data)))
    buf = hb.Buffer(); buf.add_str(text); buf.guess_segment_properties(); hb.shape(shaper, buf, {"kern": True})
    tt = font.tt; gs = tt.getGlyphSet(); order = tt.getGlyphOrder(); upm = tt["head"].unitsPerEm; s = size / upm
    paths, pen_x = [], 0.0
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        pen = SVGPathPen(gs)
        gs[order[info.codepoint]].draw(TransformPen(pen, (s, 0, 0, -s, x + (pen_x + pos.x_offset) * s, y)))
        paths.append(_round(pen.getCommands()))
        pen_x += pos.x_advance + tracking * upm
    return paths, pen_x * s


def width(font, text, size, tracking=0.0):
    return set_line(font, text, size, 0, 0, tracking)[1]


class Font:
    """A static font, held in memory for HarfBuzz and fontTools alike."""

    def __init__(self, path):
        from fontTools.ttLib import TTFont
        self.data = pathlib.Path(path).read_bytes()
        self.tt = TTFont(io.BytesIO(self.data))


# ---------- the mark ----------
def mark_parts():
    svg = (ROOT / "docs/branding/field-atlas-readme-icon.svg").read_text()
    tile = re.search(r'<rect[^>]*fill="(#[0-9A-Fa-f]+)"', svg).group(1)
    (disc_c, disc), (dia_c, diamond), (arrow_c, arrow), (bar_c, bars) = re.findall(r'<path fill="(#[0-9A-Fa-f]+)" d="([^"]+)"', svg)
    return tile, (disc_c, disc), (dia_c, diamond), (arrow_c, arrow), (bar_c, bars)


# ---------- motion ----------
def pct(t): return f"{100 * t / LOOP:.2f}".rstrip("0").rstrip(".") + "%"


class Timeline:
    def __init__(self): self.rules = []
    def add(self, cls, stops, origin=None):
        # A property missing from a keyframe is interpolated from the element's resting value, so a fade written only at
        # the end would start at 0 s. Every stop gets every property: held from the last stop that set it, resting before that.
        parsed = [dict(kv.split(":", 1) for kv in d.split(";")) for _, d, _ in stops]
        for prop in {k for f in parsed for k in f}:
            last = {"opacity": "1", "transform": "none", "stroke-dashoffset": "0"}[prop]
            for f in parsed: last = f.setdefault(prop, last)
        stops = [(t, ";".join(f"{k}:{v}" for k, v in f.items()), e) for (t, _, e), f in zip(stops, parsed)]
        body = "".join(f"{pct(t)}{{{d}{';animation-timing-function:' + e if e else ''}}}" for t, d, e in stops)
        o = f"transform-box:fill-box;transform-origin:{origin};" if origin else ""
        self.rules.append(f".{cls}{{{o}animation:{cls} {LOOP}s linear infinite both}}@keyframes {cls}{{{body}}}")
    def enter(self, cls, start, dur, hidden, shown, ease=EASE_OUT, origin=None, out=9.2):
        self.add(cls, [(0, hidden, None), (start, hidden, ease), (start + dur, shown, None), (out, shown, "ease-in"),
                       (out + .5, hidden if "opacity" in hidden else shown + ";opacity:0", None), (LOOP, hidden, None)], origin)


def smooth_closed(pts):
    """Catmull-Rom through closed points, as cubic Béziers."""
    n = len(pts); d = f"M{pts[0][0]:.1f} {pts[0][1]:.1f}"
    for i in range(n):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6); c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f"C{c1[0]:.1f} {c1[1]:.1f} {c2[0]:.1f} {c2[1]:.1f} {p2[0]:.1f} {p2[1]:.1f}"
    return d + "Z"


def contours(cx, cy, n=13):
    """Nested closed contours around a summit: wobbling ellipses whose wobble drifts with height, like real terrain."""
    rnd = random.Random(31); ph = [rnd.uniform(0, 6.3) for _ in range(4)]; out = []
    for i in range(1, n + 1):
        rx, ry = 26 + i * 44, 16 + i * 25; pts = []
        for k in range(72):
            a = 2 * math.pi * k / 72
            w = 1 + .10 * math.sin(2 * a + ph[0] + i * .21) + .06 * math.sin(3 * a + ph[1] - i * .17) + .035 * math.sin(5 * a + ph[2] + i * .3)
            ridge = 1 + .18 * max(0, math.cos(a - 3.3)) ** 3  # a ridge running off to the west, where the trail comes in
            pts.append((cx + rx * w * ridge * math.cos(a), cy + ry * w * math.sin(a)))
        out.append((smooth_closed(pts), pts[0]))
    return out


def sample_open(pts, per=40):
    """Catmull-Rom through open points (ends doubled), sampled densely."""
    P = [pts[0]] + pts + [pts[-1]]; out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for j in range(per):
            t = j / per; t2, t3 = t * t, t * t * t
            out.append(tuple(.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                                   + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in (0, 1)))
    return out + [pts[-1]]


def along(pts, n):
    """n + 1 points at even distances along a polyline."""
    acc = [0.0]
    for a, b in zip(pts, pts[1:]): acc.append(acc[-1] + math.dist(a, b))
    out, i = [], 0
    for j in range(n + 1):
        d = acc[-1] * j / n
        while i < len(acc) - 2 and acc[i + 1] < d: i += 1
        f = (d - acc[i]) / ((acc[i + 1] - acc[i]) or 1)
        out.append((pts[i][0] + f * (pts[i + 1][0] - pts[i][0]), pts[i][1] + f * (pts[i + 1][1] - pts[i][1])))
    return out


def dino_paths():
    """The standing and two walking dinosaurs, read from the app's icon set so the hero always matches the app."""
    kt = (ROOT / "app/src/main/java/xyz/fieldatlas/ui/theme/FieldAtlasIcons.kt").read_text()
    return [re.search(rf'val {name} by prototypeIcon\("{name}", "([^"]+)"', kt).group(1) for name in ("Offline", "DinoWalkA", "DinoWalkB")]


def build(fonts, theme):
    c = THEMES[theme]; tl = Timeline()
    sans, serif, small = fonts["sans"], fonts["serif"], fonts["small"]
    tile, (disc_c, disc), (dia_c, diamond), (arrow_c, arrow), (bar_c, bars) = mark_parts()
    sx, sy = W / 2, 112  # the summit, where the mark lands
    D = 104; k = D / 108; R = D / 2; ox, oy = sx - R, sy - R; mark = f'transform="translate({ox:.1f} {oy:.1f}) scale({k:.4f})"'

    # Contours: printed outward, index lines heavier and a beat later; the whole map breathes after.
    rings = contours(sx, sy)
    cont, labels, knock = "", "", ""
    for i, (d, east) in enumerate(rings):
        idx = (i + 1) % 5 == 0; cls = f"ct{i}"
        cont += f'<path class="{cls}" d="{d}" pathLength="1" stroke-width="{1.6 if idx else 1}" stroke-opacity="{c["index"] if idx else c["minor"]}"/>'
        start = .15 + i * .11 + (.25 if idx else 0)
        tl.add(cls, [(0, "stroke-dashoffset:1", None), (start, "stroke-dashoffset:1", EASE_OUT), (start + 1.3, "stroke-dashoffset:0", None),
                     (9.2, "stroke-dashoffset:0", "ease-in"), (9.7, "stroke-dashoffset:-1", None), (LOOP, "stroke-dashoffset:-1", None)])
        if idx:
            txt = f"{1250 - 50 * (i + 1)}"; lw = width(small, txt, 11, .06); lx, ly = east[0] - lw / 2, east[1] + 4
            path, _ = set_line(small, txt, 11, lx, ly, .06)
            labels += f'<g class="el{i}">' + "".join(f'<path d="{q}"/>' for q in path if q) + "</g>"
            knock += f'<rect x="{lx - 5:.1f}" y="{ly - 13:.1f}" width="{lw + 10:.1f}" height="18" fill="#000"/>'
            tl.enter(f"el{i}", start + .9, .5, "opacity:0", "opacity:1")
    tl.add("map", [(0, "transform:none", None), (5.0, "transform:none", EASE_IO), (7.5, "transform:rotate(-1.4deg) scale(1.012)", EASE_IO), (LOOP, "transform:none", None)], "50% 25%")

    # The walker: the app's own pixel dinosaur (the Offline chip) walks in from the west edge along the ridge, leaving a
    # fading trail, and stops beside the summit just as the mark lands there. It moves at an even pace along the trail's
    # length, so the trail, drawn at the same even pace by a mask, always ends at its feet.
    feet = (ox - 40, sy + R)
    trail_pts = sample_open([(-70, 300), (120, 274), (250, 204), (380, 174), (476, 198), feet])
    trail = "M" + "L".join(f"{x:.1f} {y:.1f}" for x, y in trail_pts)
    w0, w1, n = .6, 3.0, 32
    spots = along(trail_pts, n)
    tl.add("walker", [(0, f"transform:translate({spots[0][0]:.1f}px,{spots[0][1]:.1f}px)", None)]
           + [(w0 + (w1 - w0) * j / n, f"transform:translate({x:.1f}px,{y:.1f}px)", None) for j, (x, y) in enumerate(spots)]
           + [(9.2, f"transform:translate({feet[0]:.1f}px,{feet[1]:.1f}px);opacity:1", "ease-in"),
              (9.7, f"transform:translate({feet[0]:.1f}px,{feet[1]:.1f}px);opacity:0", None),
              (LOOP, f"transform:translate({spots[0][0]:.1f}px,{spots[0][1]:.1f}px);opacity:0", None)])
    # A step every 0.12 s while walking (one foot lifted, then the other), standing once it arrives; a small hop as the
    # mark lands beside it.
    steps = [w0 + .12 * j for j in range(int((w1 - w0) / .12))]
    for cls, parity in (("stepa", 0), ("stepb", 1)):
        tl.add(cls, [(0, "opacity:0", "step-end")] + [(t, f"opacity:{int(j % 2 == parity)}", "step-end") for j, t in enumerate(steps)]
               + [(w1, "opacity:0", "step-end"), (LOOP, "opacity:0", None)])
    tl.add("stand", [(0, "opacity:0", "step-end"), (w1, "opacity:1", "step-end"), (LOOP - .01, "opacity:1", "step-end"), (LOOP, "opacity:0", None)])
    tl.add("hop", [(0, "transform:none", None), (3.15, "transform:none", "cubic-bezier(.2,.7,.4,1)"), (3.35, "transform:translateY(-12px)", "cubic-bezier(.6,0,.8,.3)"),
                   (3.55, "transform:none", None), (3.62, "transform:translateY(-3px)", None), (3.7, "transform:none", None), (LOOP, "transform:none", None)])
    # The trail as on the website's island: short gold dashes marching towards the summit. Only a short tail shows, right
    # behind the walker, fading with distance in three steps, and it is gone a moment after the walker stops: each step is
    # a window on the trail (in fractions of its length) that slides along at the walker's pace and on past the end.
    seg, tail_end = .055, w1 + (w1 - w0) * 3 * .055
    for k in range(3):
        tl.add(f"tail{k}", [(0, f"stroke-dashoffset:{(k + 1) * seg:.4f}", None), (w0, f"stroke-dashoffset:{(k + 1) * seg:.4f}", None),
                            (tail_end, f"stroke-dashoffset:{(k + 1) * seg - 1 - 3 * seg:.4f}", None), (LOOP, f"stroke-dashoffset:{(k + 1) * seg - 1 - 3 * seg:.4f}", None)])
    tail = "".join(f'<path class="tail{k}" d="{{trail}}" pathLength="1" fill="none" stroke="#fff" stroke-opacity="{op}" stroke-width="8" stroke-dasharray="{seg} 3"/>'
                   for k, op in enumerate((1, .55, .22)))
    # 9 dash periods (11 px) per loop, so the march has no seam.
    tl.add("march", [(0, "stroke-dashoffset:0", None), (LOOP, "stroke-dashoffset:-99", None)])
    stand_d, walk_a, walk_b = dino_paths()
    dk = 1.625  # 52 px tall on its 32 grid: half the mark, big enough to read as the app's dinosaur
    dino = f'transform="translate({-12 * dk:.2f} {-28 * dk:.2f}) scale({dk})"'
    # The mark: the tile lands, the disc opens, the needle spins and settles north, the horizon bars draw out.
    tl.enter("tile", 3.05, .45, "opacity:0;transform:scale(.4) rotate(-12deg)", "opacity:1;transform:none", OVERSHOOT, "50% 50%")
    tl.enter("disc", 3.25, .4, "transform:scale(0)", "transform:none", OVERSHOOT, "50% 50%")
    tl.add("needle", [(0, "opacity:0;transform:rotate(-400deg) scale(.5)", None), (3.3, "opacity:0;transform:rotate(-400deg) scale(.5)", None),
                      (3.31, "opacity:1;transform:rotate(-400deg) scale(.5)", EASE_OUT), (3.9, "opacity:1;transform:rotate(12deg)", "ease-in-out"),
                      (4.07, "opacity:1;transform:rotate(-4deg)", "ease-in-out"), (4.23, "opacity:1;transform:none", None),
                      (9.2, "opacity:1;transform:none", None), (9.7, "opacity:0;transform:none", None), (LOOP, "opacity:0;transform:rotate(-400deg) scale(.5)", None)], "50% 50%")
    tl.enter("bars", 3.7, .35, "transform:scaleX(0)", "transform:none", EASE_OUT, "50% 50%")

    # Words: still from the first frame.
    s1, s2, s3 = 88, 46, 15
    wm, ww = set_line(sans, "Field Atlas", s1, 0, 0, -0.035); x1 = (W - ww) / 2; wm, _ = set_line(sans, "Field Atlas", s1, x1, 286, -0.035)
    t_plain, t_it = "Answers from ", "what you carry."
    wa = width(sans, t_plain, s2 * .74, -0.02); wb = width(serif, t_it, s2)
    x2 = (W - wa - wb) / 2; la, _ = set_line(small, t_plain, s2 * .74, x2, 352, -0.01); lb, _ = set_line(serif, t_it, s2, x2 + wa, 352)
    eb_txt = "OFFLINE RESEARCH FOR ANDROID"; we = width(small, eb_txt, s3, .18); eb, _ = set_line(small, eb_txt, s3, (W - we) / 2, 398, .18)

    g = lambda paths: "".join(f'<path d="{d}"/>' for d in paths if d)
    style = "".join(tl.rules) + ("[class^=ct]{stroke-dasharray:1 1}"
                                 "@media (prefers-reduced-motion:reduce){svg *{animation:none!important}.stepa,.stepb,[class^=tail]{opacity:0}.stand{opacity:1}"
                                 f".walker{{transform:translate({feet[0]:.1f}px,{feet[1]:.1f}px)}}}}")
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" role="img" aria-labelledby="t d">
<title id="t">Field Atlas</title><desc id="d">Field Atlas, offline research for Android: answers from what you carry.</desc>
<style>{style}</style>
<defs>
<radialGradient id="fade" cx="{sx / W:.3f}" cy="{sy / H:.3f}" r=".62" gradientTransform="translate({sx / W:.3f} {sy / H:.3f}) scale(1 2.9) translate({-sx / W:.3f} {-sy / H:.3f})">
<stop offset="0" stop-color="#fff"/><stop offset=".55" stop-color="#fff" stop-opacity=".85"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
<mask id="cut" maskUnits="userSpaceOnUse" x="0" y="0" width="{W}" height="{H}"><rect width="{W}" height="{H}" fill="#fff"/>{knock}</mask>
<linearGradient id="calm" x1="0" y1="0" x2="0" y2="1"><stop offset=".42" stop-color="#fff"/><stop offset=".62" stop-color="#fff" stop-opacity=".18"/><stop offset="1" stop-color="#fff" stop-opacity=".05"/></linearGradient>
<mask id="m1" maskUnits="userSpaceOnUse" x="0" y="0" width="{W}" height="{H}"><rect width="{W}" height="{H}" fill="url(#fade)"/></mask>
<mask id="m2" maskUnits="userSpaceOnUse" x="0" y="0" width="{W}" height="{H}"><rect width="{W}" height="{H}" fill="url(#calm)"/></mask>
<mask id="rtm" maskUnits="userSpaceOnUse" x="-80" y="0" width="{W + 160}" height="{H}">{tail.format(trail=trail)}</mask>
</defs>
<g mask="url(#m2)"><g mask="url(#m1)"><g class="map"><g mask="url(#cut)" fill="none" stroke="{c['line']}">{cont}</g><g fill="{c['eyebrow']}" fill-opacity=".8">{labels}</g></g></g></g>
<g fill="none" stroke-width="3.4" stroke-linecap="round" stroke-dasharray="3 8">
<path class="march" d="{trail}" stroke="{c['walked']}" mask="url(#rtm)"/></g>
<rect class="tile" x="{ox:.1f}" y="{oy:.1f}" width="{D}" height="{D}" rx="{18 * k:.1f}" fill="{tile}"/>
<g class="disc"><g {mark}><path fill="{disc_c}" d="{disc}"/></g></g>
<g class="needle"><g {mark}><path fill="{dia_c}" d="{diamond}"/><path fill="{arrow_c}" d="{arrow}"/></g></g>
<g class="bars"><g {mark}><path fill="{bar_c}" d="{bars}"/></g></g>
<g class="walker"><g class="hop" fill="{c['text']}" shape-rendering="crispEdges"><g {dino}><path class="stand" fill-rule="evenodd" d="{stand_d}"/><path class="stepa" d="{walk_a}"/><path class="stepb" d="{walk_b}"/></g></g></g>
<g class="wm" fill="{c['text']}">{g(wm)}</g>
<g class="tg"><g fill="{c['text']}" fill-opacity=".86">{g(la)}</g><g fill="{c['accent']}">{g(lb)}</g></g>
<g class="eb" fill="{c['eyebrow']}">{g(eb)}</g>
</svg>
"""


def main():
    parser = argparse.ArgumentParser(description="Draw the animated README hero, light and dark.")
    parser.add_argument("--out", type=pathlib.Path, default=ROOT / "docs" / "branding", help="output directory")
    parser.add_argument("--fonts", type=pathlib.Path, default=pathlib.Path.home() / ".fieldatlas" / "fonts-cache",
                        help="directory with InterTight-600.ttf, InterTight-800.ttf and InstrumentSerif-400i.ttf")
    a = parser.parse_args()
    fonts = {"sans": Font(a.fonts / "InterTight-800.ttf"), "small": Font(a.fonts / "InterTight-600.ttf"),
             "serif": Font(a.fonts / "InstrumentSerif-400i.ttf")}
    a.out.mkdir(parents=True, exist_ok=True)
    for theme in THEMES:
        f = a.out / f"field-atlas-hero-{theme}.svg"
        f.write_text(build(fonts, theme))
        print("wrote", f, f.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
