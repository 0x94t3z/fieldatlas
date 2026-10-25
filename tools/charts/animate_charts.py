#!/usr/bin/env python3
"""Animated versions of the README and article figures, from the static SVGs make_charts.py writes.

Only lines and shapes move, in the hero's motion language: grid lines draw up from the axis and bars grow from it; in a
diagram the boxes stay and the connectors draw in left to right. No text is ever animated, so every word, value and
label can be read from the first frame. The figure holds, the moving parts fade and build again. Motion
is CSS keyframes inside the SVG, each spanning the whole loop as in make_hero.py, so the files play inside GitHub's
<img>. An SVG shown as an image cannot load web fonts, so the faces it uses (Inter Tight, Instrument Serif) are
embedded as WOFF2 subsets of just the characters drawn. Readers who ask for reduced motion see the finished figure.

X shows no SVG, so --video also renders each animation once through (build and hold, no loop) to MP4 and GIF.

    pip install fonttools==4.55.3 brotli==1.1.0       # and playwright, for --video
    python3 tools/charts/animate_charts.py build/charts/fieldatlas-models.svg ... [--out DIR] [--fonts DIR] [--video]
"""
import argparse, base64, io, pathlib, re, subprocess, xml.etree.ElementTree as ET

LOOP, HOLD_END, FADE = 9.0, 8.2, 0.5           # seconds: built by ~4 s, held, faded out by 8.7, restart at 9
EASE_OUT, OVERSHOOT = "cubic-bezier(.2,.8,.2,1)", "cubic-bezier(.3,1.25,.5,1)"
SVG_NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG_NS); ET.register_namespace("xlink", "http://www.w3.org/1999/xlink")
FACES = {("Inter Tight", w, False): f"InterTight-{w}.ttf" for w in (400, 500, 600, 700, 800)}
FACES[("Instrument Serif", 400, True)] = "InstrumentSerif-400i.ttf"; FACES[("Instrument Serif", 400, False)] = "InstrumentSerif-400.ttf"

def pct(t): return f"{100 * t / LOOP:.3f}".rstrip("0").rstrip(".") + "%"

def keyframes(name, start, dur, hidden, shown, ease=EASE_OUT, loop=True):
    end = min(start + dur, HOLD_END - .05)
    stops = [(0, hidden, None), (start, hidden, ease), (end, shown, None)]
    stops += [(HOLD_END, shown, "ease-in"), (HOLD_END + FADE, hidden, None), (LOOP, hidden, None)] if loop else [(LOOP, shown, None)]
    body = "".join(f"{pct(t)}{{{d}{';animation-timing-function:' + e if e else ''}}}" for t, d, e in stops)
    return f"@keyframes {name}{{{body}}}"

def style_of(el): return dict(kv.split(":", 1) for kv in (el.get("style") or "").split(";") if ":" in kv)

def box(el):
    """x, y, right, bottom of an element, from its own attributes (enough for the kit's rects, lines and text)."""
    f = lambda k: float(el.get(k) or 0)
    tag = el.tag.split("}")[1]
    if tag == "rect": return f("x"), f("y"), f("x") + f("width"), f("y") + f("height")
    if tag == "line": return min(f("x1"), f("x2")), min(f("y1"), f("y2")), max(f("x1"), f("x2")), max(f("y1"), f("y2"))
    if tag == "text": return f("x"), f("y") - 20, f("x"), f("y")
    return None

def animate(svg_text, loop=True, diagram=False):
    """Only the chart moves: grid lines draw up from the axis, bars grow, and each value appears once its bar is in.
    The title, the method line, row names, notes and the footer are there from the first frame."""
    root = ET.fromstring(svg_text); W = float(root.get("width"))
    kids = list(root); rules, chars = [], {}
    drawn = [k for k in kids if k.tag.split("}")[1] not in ("title", "defs") and not (k.tag.endswith("rect") and k.get("width") == root.get("width"))]
    body = drawn[:-3]; texts = [k for k in body if k.tag.endswith("text")]; body = [k for k in body if k not in texts[:2]]
    tag = lambda el: el.tag.split("}")[1]
    def is_bar(el):
        w = float(el.get("width") or 0); h = float(el.get("height") or 0)
        return tag(el) == "rect" and w > 2.2 * h and w < W * .97
    bars = [] if diagram else [k for k in body if is_bar(k)]  # a diagram's wide boxes are not bars
    if bars:  # a chart: its area is where the bars are, with room for the axis labels under it
        x0 = min(box(b)[0] for b in bars); y0 = min(box(b)[1] for b in bars) - 90; y1 = max(box(b)[3] for b in bars) + 110
        def in_chart(el):
            bx = box(el)
            return bx is not None and bx[0] >= x0 - 4 and y0 <= bx[3] and bx[1] <= y1
        moving = [k for k in body if in_chart(k)]
    else:  # a diagram: all of it, swept left to right the way it reads
        moving = body
    # Words are never animated: anything a reader reads is on screen from the first frame. Only lines and shapes move.
    moving = [k for k in moving if tag(k) != "text"]
    grid = [k for k in moving if tag(k) == "line" and box(k)[0] == box(k)[2] and box(k)[3] - box(k)[1] > 100]  # tall vertical rules: the grid
    rest = [k for k in moving if k not in grid]
    plan = [(k, .2 + .5 * i / max(1, len(grid)), .55, "draw") for i, k in enumerate(grid)]
    if not bars:  # a diagram: the boxes stay, the connectors draw in left to right and each arrowhead lands as its line arrives
        def arrive(el):
            b = box(el)
            if b: return b[2]
            nums = [float(v) for v in re.findall(r"-?\d+\.?\d*", el.get("d") or el.get("points") or "")]
            return max(nums[0::2]) if nums else 0
        links = [k for k in rest if tag(k) in ("line", "polyline", "polygon", "path")]
        xs = [arrive(k) for k in links]; lo, hi = (min(xs), max(xs)) if xs else (0, 1)
        for k, x in zip(links, xs):
            start = .4 + 3.0 * (x - lo) / max(1, hi - lo)
            if tag(k) == "line":
                bx = box(k); across = bx[2] - bx[0] >= bx[3] - bx[1]
                plan.append((k, start, .5, "draw-x" if across else "draw-y"))
            else:
                plan.append((k, start + .4, .3, "fade"))
    else:
        for i, k in enumerate(rest):
            plan.append((k, .9 + 2.6 * i / max(1, len(rest)), .75 if is_bar(k) else .45, "grow" if is_bar(k) else "fade"))
    for n, (el, start, dur, kind) in enumerate(plan):
        name = f"a{n}"
        hidden, shown, ease, origin = {
            "rise": ("opacity:0;transform:translateY(10px)", "opacity:1;transform:none", EASE_OUT, None),
            "fade": ("opacity:0", "opacity:1", EASE_OUT, None),
            "grow": ("transform:scaleX(0)", "transform:none", OVERSHOOT, "0 50%"),
            "draw": ("transform:scaleY(0)", "transform:none", EASE_OUT, "50% 100%"),
            "draw-x": ("transform:scaleX(0)", "transform:none", EASE_OUT, "0 50%"),
            "draw-y": ("transform:scaleY(0)", "transform:none", EASE_OUT, "50% 0"),
        }[kind]
        el.set("class", ((el.get("class") or "") + " " + name).strip())
        o = f"transform-box:fill-box;transform-origin:{origin};" if origin else ""
        rules.append(f".{name}{{{o}animation:{name} {LOOP}s linear {'infinite' if loop else '1'} both}}" + keyframes(name, start, dur, hidden, shown, ease, loop))
    for el in root.iter():
        if el.tag.endswith("text") or el.tag.endswith("tspan"):
            st = style_of(el); fam = st.get("font-family", "").split(",")[0].strip("'\" ")
            key = (fam, int(st.get("font-weight", 400)), st.get("font-style") == "italic")
            chars.setdefault(key, set()).update(el.text or "")
            if el.tag.endswith("text") and list(el):
                chars[key].update("".join((c.tail or "") for c in el))
    return root, rules, chars

def font_css(chars, fonts_dir):
    from fontTools import subset
    from fontTools.ttLib import TTFont
    css = []
    for (fam, w, it), cs in chars.items():
        f = FACES.get((fam, w, it))
        if not f or not cs: continue
        font = TTFont(str(fonts_dir / f)); opts = subset.Options(); opts.flavor = "woff2"; opts.layout_features = ["*"]
        sub = subset.Subsetter(opts); sub.populate(text="".join(cs) + " "); sub.subset(font)
        buf = io.BytesIO(); font.flavor = "woff2"; font.save(buf)
        css.append(f"@font-face{{font-family:'{fam}';font-weight:{w};font-style:{'italic' if it else 'normal'};"
                   f"src:url(data:font/woff2;base64,{base64.b64encode(buf.getvalue()).decode()}) format('woff2')}}")
    return "".join(css)

def build(svg_path, fonts_dir, loop=True):
    diagram = any(w in svg_path.stem for w in ("pipeline", "flow"))  # boxes and arrows, read left to right
    root, rules, chars = animate(svg_path.read_text(), loop, diagram)
    style = ET.Element(f"{{{SVG_NS}}}style")
    style.text = font_css(chars, fonts_dir) + "".join(rules) + "@media (prefers-reduced-motion:reduce){svg *{animation:none!important}}"
    root.insert(1, style)
    return ET.tostring(root, encoding="unicode")

def video(svg_text, out_base, seconds=6.5, fps=30):
    """Renders the once-through animation frame by frame (animations paused and stepped), then MP4 and GIF."""
    import asyncio, tempfile
    from playwright.async_api import async_playwright
    W, H = (int(float(v)) for v in re.search(r'width="([\d.]+)" height="([\d.]+)"', svg_text).groups())
    tmp = pathlib.Path(tempfile.mkdtemp())
    async def run():
        async with async_playwright() as p:
            b = await p.chromium.launch(channel="chrome"); pg = await b.new_page(viewport={"width": W, "height": H})
            html = tmp / "f.html"; html.write_text(f"<!doctype html><style>html,body{{margin:0}}svg{{display:block}}</style>{svg_text}")
            await pg.goto(html.as_uri()); await pg.evaluate("document.fonts.ready")
            for k in range(int(seconds * fps)):
                await pg.evaluate(f"document.getAnimations().forEach(a => {{ a.pause(); a.currentTime = {k * 1000 / fps}; }})")
                await pg.screenshot(path=str(tmp / f"{k:04d}.png"))
            await b.close()
    asyncio.run(run())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", str(fps), "-i", str(tmp / "%04d.png"), "-c:v", "libx264", "-crf", "16",
                    "-pix_fmt", "yuv420p", "-movflags", "+faststart", f"{out_base}.mp4"], check=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", str(fps), "-i", str(tmp / "%04d.png"), "-vf",
                    "fps=20,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=4",
                    "-loop", "0", f"{out_base}.gif"], check=True)
    for f in tmp.iterdir(): f.unlink()
    tmp.rmdir()

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("svgs", nargs="+"); ap.add_argument("--out", default="build/charts-animated")
    ap.add_argument("--fonts", default=str(pathlib.Path.home() / ".fieldatlas/fonts-cache")); ap.add_argument("--video", action="store_true")
    a = ap.parse_args(); out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True); fonts = pathlib.Path(a.fonts)
    for s in a.svgs:
        src = pathlib.Path(s); dst = out / f"{src.stem}-animated.svg"; dst.write_text(build(src, fonts))
        print(dst, f"{dst.stat().st_size / 1024:.0f} KB")
        if a.video: video(build(src, fonts, loop=False), out / src.stem); print(out / f"{src.stem}.mp4", out / f"{src.stem}.gif")

if __name__ == "__main__":
    main()
