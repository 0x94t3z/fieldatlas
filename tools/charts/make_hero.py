#!/usr/bin/env python3
"""Draw the animated README hero: docs/branding/field-atlas-hero-light.svg and field-atlas-hero-dark.svg.

The README shows one or the other with <picture> and prefers-color-scheme, which GitHub matches to each reader's
theme; neither has a background, so the page shows through. Text is set in the app's own fonts (Manrope and Source
Serif 4 Italic from app/src/main/res/font, instanced here) with HarfBuzz and drawn as outlines; motion is CSS
keyframes, no script, so the files play inside GitHub's <img>.

One motion language. Text only rises from behind its baseline. The mark builds like a compass being set down: a dot
opens into a ring, the ring breaks into arcs that swing out to the icon's edge, the dark circle fills, the mint disc
pops in, and the needle spins and settles north while the horizon bars draw out.

  0.0 - 1.4 s  "OFFLINE RESEARCH FOR ANDROID", then "Answers from" (Manrope), then "what you carry."
               (Source Serif 4 Italic, the face the app sets answers in), each rising into place
  3.1 - 3.5 s  the lines part, up and down; a dot appears between them and opens into a ring
  3.6 - 4.35   the mark assembles at the centre
  4.45 - 5.1   it slides left and "Field Atlas" rises beside it
  7.4 - 7.8    everything fades, and the loop starts again

Readers who ask for reduced motion see the final lockup. The mark is the app icon masked as a circle, as a round
launcher shows it; its shapes and colours are read from docs/branding/field-atlas-readme-icon.svg unchanged.

    pip install fonttools==4.55.3 uharfbuzz==0.42.0
    python3 tools/charts/make_hero.py [--out docs/branding]
"""
import argparse, io, math, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parents[2]
FONT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"
W, H, LOOP = 1280, 380, 7.8
EASE_OUT, EASE_IN, EASE_SWAP = "cubic-bezier(.2,.8,.2,1)", "cubic-bezier(.6,0,.9,.4)", "cubic-bezier(.7,0,.2,1)"
OVERSHOOT = "cubic-bezier(.3,1.45,.5,1)"
THEMES = {  # text colours chosen for contrast on GitHub's light (#ffffff) and dark (#0d1117) pages
    "light": {"text": "#0e1f18", "accent": "#12805F", "eyebrow": "#4F6B5E"},
    "dark": {"text": "#EAF1E4", "accent": "#3DCB97", "eyebrow": "#8FB9A2"},
}
SPARK = "#18A67E"  # the dot, ring and arcs: the mark's own green, visible on both pages


# ---------- type ----------
def _round(path):
    return re.sub(r"-?\d+\.\d+", lambda m: f"{float(m.group()):.1f}".rstrip("0").rstrip("."), path)


class Font:
    """A static instance of one of the app's variable fonts, held in memory for HarfBuzz and fontTools alike."""

    def __init__(self, file, axes):
        from fontTools.ttLib import TTFont
        from fontTools.varLib.instancer import instantiateVariableFont
        buf = io.BytesIO()
        instantiateVariableFont(TTFont(FONT_DIR / file), axes, inplace=False).save(buf)
        self.data = buf.getvalue()
        self.tt = TTFont(io.BytesIO(self.data))


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


# ---------- motion ----------
class Timeline:
    """Collects one @keyframes block per animated class, each spanning the whole loop, so every element stays in step
    across repeats (a delay would shift an element's whole cycle and let it flash at the restart)."""

    def __init__(self):
        self.rules = []

    def add(self, cls, frames, origin=None):
        """frames: [(seconds, css declarations, easing for the segment that starts there or None)]."""
        def pct(t):
            return f"{100 * t / LOOP:.2f}".rstrip("0").rstrip(".") + "%"
        body = "".join(f"{pct(t)}{{{d}{';animation-timing-function:' + e if e else ''}}}" for t, d, e in frames)
        o = f"transform-origin:{origin};" if origin else ""
        self.rules.append(f".{cls}{{{o}animation:{cls} {LOOP}s linear infinite both}}@keyframes {cls}{{{body}}}")

    def rise(self, cls, start, dist, dur=0.42):
        """In from below a clip at the baseline, and stay."""
        self.add(cls, [(0, f"transform:translateY({dist}px)", None), (start, f"transform:translateY({dist}px)", EASE_OUT),
                       (start + dur, "transform:none", None), (LOOP, "transform:none", None)])

    def show(self, cls, start, end_in, before, after, ease=EASE_OUT, origin=None):
        self.add(cls, [(0, before, None), (start, before, ease), (end_in, after, None), (LOOP, after, None)], origin)

    def css(self):
        return "".join(self.rules)


# ---------- the mark ----------
def mark_parts():
    svg = (ROOT / "docs/branding/field-atlas-readme-icon.svg").read_text()
    tile = re.search(r'<rect[^>]*fill="(#[0-9A-Fa-f]+)"', svg).group(1)
    (disc_c, disc), (dia_c, diamond), (arrow_c, arrow), (bar_c, bars) = re.findall(r'<path fill="(#[0-9A-Fa-f]+)" d="([^"]+)"', svg)
    return tile, (disc_c, disc), (dia_c, diamond), (arrow_c, arrow), (bar_c, bars)


def arc(cx, cy, r, a0, a1):
    p = lambda a: (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
    (x0, y0), (x1, y1) = p(a0), p(a1)
    return f"M{x0:.1f} {y0:.1f}A{r:.1f} {r:.1f} 0 0 1 {x1:.1f} {y1:.1f}"


# ---------- the hero ----------
def build(fonts, theme):
    c = THEMES[theme]
    sans, sans7, serif = fonts["sans"], fonts["eyebrow"], fonts["serif"]
    tl = Timeline()
    tile, (disc_c, disc), (dia_c, diamond), (arrow_c, arrow), (bar_c, bars) = mark_parts()

    # Scene 1: eyebrow, a sans line and a serif line, left-aligned as a block in the middle of the canvas.
    eyebrow, s1, s2 = "OFFLINE RESEARCH FOR ANDROID", 96, 104
    w1, w2 = width(sans, "Answers from", s1, -0.03), width(serif, "what you carry.", s2)
    x0 = (W - max(w1, w2)) / 2
    b0, b1, b2 = 96, 196, 300
    eb, _ = set_line(sans7, eyebrow, 19, x0 + 4, b0, 0.22)
    l1, _ = set_line(sans, "Answers from", s1, x0, b1, -0.03)
    l2, _ = set_line(serif, "what you carry.", s2, x0 + 6, b2)
    tl.show("eb", 0.0, 0.45, "opacity:0;transform:translateY(10px)", "opacity:1;transform:none")
    for i, _ in enumerate(l1):
        tl.rise(f"a{i}", 0.18 + i * 0.05, 120)
    for i, _ in enumerate(l2):
        tl.rise(f"b{i}", 0.74 + i * 0.045, 150)
    for name, sign in (("up", -1), ("down", 1)):
        tl.add(name, [(0, "opacity:1;transform:none", None), (3.1, "opacity:1;transform:none", "ease-in-out"),
                      (3.32, f"opacity:1;transform:translateY({sign * 18}px)", EASE_IN),
                      (3.5, f"opacity:1;transform:translateY({sign * 320}px)", None),
                      (3.51, f"opacity:0;transform:translateY({sign * 320}px)", None), (LOOP, f"opacity:0;transform:translateY({sign * 320}px)", None)])

    # Scene 2: the mark (the icon as a circle) beside the wordmark; mark 1.6x the wordmark's cap height.
    s3, gap = 112, 40
    D = 1.6 * 0.72 * s3; R = D / 2; k = D / 108
    wmw = width(sans, "Field Atlas", s3, -0.03)
    cx, cy = (W - (D + gap + wmw)) / 2 + R, H / 2
    ox, oy = cx - 54 * k, cy - 54 * k
    wm, _ = set_line(sans, "Field Atlas", s3, cx + R + gap, cy + 0.36 * s3, -0.03)
    o = f"{cx:.1f}px {cy:.1f}px"
    tl.add("slide", [(0, f"transform:translateX({W / 2 - cx:.1f}px)", None), (4.45, f"transform:translateX({W / 2 - cx:.1f}px)", EASE_SWAP),
                     (4.65, "transform:none", None), (LOOP, "transform:none", None)])
    tl.add("seed", [(0, "opacity:0;transform:scale(.3)", None), (3.14, "opacity:0;transform:scale(.3)", None),
                    (3.16, "opacity:1;transform:scale(.3)", EASE_OUT), (3.38, "opacity:1;transform:none", None),
                    (3.39, "opacity:0;transform:none", None), (LOOP, "opacity:0;transform:none", None)], o)
    tl.add("ring", [(0, "opacity:0;transform:scale(.6)", None), (3.38, "opacity:0;transform:scale(.6)", None),
                    (3.39, "opacity:1;transform:scale(.6)", EASE_OUT), (3.62, "opacity:1;transform:scale(1.3)", None),
                    (3.63, "opacity:0;transform:scale(1.3)", None), (LOOP, "opacity:0;transform:scale(1.3)", None)], o)
    arcs = []
    for j, turn in enumerate([-150, 120, -95, 165]):
        a0 = j * 90 - 90 + 8
        arcs.append(f'<path class="r{j}" d="{arc(cx, cy, R, a0, a0 + 74)}"/>')
        t = 3.62 + j * 0.02
        tl.add(f"r{j}", [(0, f"opacity:0;transform:rotate({turn}deg) scale(.3)", None), (t, f"opacity:0;transform:rotate({turn}deg) scale(.3)", None),
                         (t + 0.01, f"opacity:1;transform:rotate({turn}deg) scale(.3)", OVERSHOOT), (3.98, "opacity:1;transform:none", None),
                         (4.02, "opacity:1;transform:none", None), (4.1, "opacity:0;transform:none", None), (LOOP, "opacity:0;transform:none", None)], o)
    tl.add("tile", [(0, "opacity:0;transform:scale(.2)", None), (3.84, "opacity:0;transform:scale(.2)", None),
                    (3.85, "opacity:1;transform:scale(.2)", EASE_OUT), (4.04, "opacity:1;transform:none", None), (LOOP, "opacity:1;transform:none", None)], o)
    tl.add("disc", [(0, "opacity:0;transform:scale(0)", None), (3.94, "opacity:0;transform:scale(0)", None),
                    (3.95, "opacity:1;transform:scale(0)", OVERSHOOT), (4.18, "opacity:1;transform:none", None), (LOOP, "opacity:1;transform:none", None)], o)
    tl.add("needle", [(0, "opacity:0;transform:rotate(-540deg) scale(.4)", None), (3.7, "opacity:0;transform:rotate(-540deg) scale(.4)", None),
                      (3.71, "opacity:1;transform:rotate(-540deg) scale(.4)", EASE_OUT), (4.12, "opacity:1;transform:rotate(14deg)", "ease-in-out"),
                      (4.26, "opacity:1;transform:rotate(-5deg)", "ease-in-out"), (4.38, "opacity:1;transform:none", None),
                      (LOOP, "opacity:1;transform:none", None)], o)
    tl.add("bars", [(0, "transform:scaleX(0)", None), (4.12, "transform:scaleX(0)", EASE_OUT),
                    (4.32, "transform:none", None), (LOOP, "transform:none", None)], o)
    for i, _ in enumerate(wm):
        tl.rise(f"c{i}", 4.6 + i * 0.04, 140)
    tl.add("all", [(0, "opacity:1", None), (7.4, "opacity:1", "ease-in"), (7.78, "opacity:0", None), (LOOP, "opacity:0", None)])

    def glyphs(paths, p):
        return "".join(f'<path class="{p}{i}" d="{d}"/>' for i, d in enumerate(paths) if d)

    def clip(cid, baseline, size):
        return f'<clipPath id="{cid}"><rect x="0" y="{baseline - size * 1.02:.1f}" width="{W}" height="{size * 1.36:.1f}"/></clipPath>'

    mark = f'transform="translate({ox:.1f} {oy:.1f}) scale({k:.4f})"'
    hidden = ".up,.down,.seed,.ring,.r0,.r1,.r2,.r3{opacity:0}"
    style = tl.css() + hidden + "@media (prefers-reduced-motion:reduce){svg *{animation:none!important}}"
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" role="img" aria-labelledby="t d">
<title id="t">Field Atlas</title><desc id="d">Field Atlas, offline research for Android: answers from what you carry.</desc>
<style>{style}</style>
<defs>{clip("k1", b1, s1)}{clip("k2", b2, s2)}{clip("k3", cy + 0.36 * s3, s3)}</defs>
<g class="all">
<g class="up"><g class="eb" fill="{c['eyebrow']}">{"".join(f'<path d="{d}"/>' for d in eb if d)}</g>
<g clip-path="url(#k1)" fill="{c['text']}">{glyphs(l1, "a")}</g></g>
<g class="down"><g clip-path="url(#k2)" fill="{c['accent']}">{glyphs(l2, "b")}</g></g>
<g class="slide">
<circle class="seed" cx="{cx:.1f}" cy="{cy:.1f}" r="5" fill="{SPARK}"/>
<circle class="ring" cx="{cx:.1f}" cy="{cy:.1f}" r="15" fill="none" stroke="{SPARK}" stroke-width="3"/>
<g fill="none" stroke="{SPARK}" stroke-width="6" stroke-linecap="round">{"".join(arcs)}</g>
<circle class="tile" cx="{cx:.1f}" cy="{cy:.1f}" r="{R:.1f}" fill="{tile}"/>
<g class="disc"><g {mark}><path fill="{disc_c}" d="{disc}"/></g></g>
<g class="bars"><g {mark}><path fill="{bar_c}" d="{bars}"/></g></g>
<g class="needle"><g {mark}><path fill="{dia_c}" d="{diamond}"/><path fill="{arrow_c}" d="{arrow}"/></g></g></g>
<g clip-path="url(#k3)" fill="{c['text']}">{glyphs(wm, "c")}</g>
</g></svg>
"""


def main():
    parser = argparse.ArgumentParser(description="Draw the animated README hero, light and dark.")
    parser.add_argument("--out", type=pathlib.Path, default=ROOT / "docs" / "branding", help="output directory")
    out = parser.parse_args().out
    fonts = {"sans": Font("manrope_variable.ttf", {"wght": 800}), "eyebrow": Font("manrope_variable.ttf", {"wght": 700}),
             "serif": Font("source_serif_4_italic_variable.ttf", {"wght": 560, "opsz": 60})}
    out.mkdir(parents=True, exist_ok=True)
    for theme in THEMES:
        f = out / f"field-atlas-hero-{theme}.svg"
        f.write_text(build(fonts, theme))
        print("wrote", f, f.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
