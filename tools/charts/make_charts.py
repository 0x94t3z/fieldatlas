#!/usr/bin/env python3
"""Draw the README and social images from charts.json.

Each image is written as SVG and rendered to PNG by headless Chrome through Playwright, the way
the demo-film mockup boards are, with Inter Tight and Instrument Serif loaded from Google Fonts.
Without Playwright it falls back to rsvg-convert, where those two faces are replaced by the
nearest installed ones.

    python3 tools/charts/make_charts.py [--out build/charts] [--scale 2]

The images are laid out as figures, not posters: the title states the finding with its numbers,
one line says how it was measured, values are labelled where they are drawn, and the source sits
under the figure. Amber marks the one value each figure is about; everything else stays in the
green-grey range so the eye lands there first.
"""

import argparse
import base64
import json
import os
import shutil
import struct
import subprocess
import sys
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
FONT_DIR = REPO / "app" / "src" / "main" / "res" / "font"

# Night palette taken from the website's HUD tokens (website/styles.css: --hud, --hud-ink,
# --hud-dim, --cta), so the images match the site's dark surfaces.
GROUND = "#0e1f18"
RAISED = "#142a21"
INK = "#eaf1e4"
DIM = "#a9c2b2"
FAINT = "#6f887a"
RULE = "#24392f"
GRID = "#1d3128"
SAGE_1 = "#36564a"
SAGE_2 = "#5d8a74"
SAGE_3 = "#8fb9a2"
AMBER = "#e3b45a"

# The type pairing of the demo-film boards: a heavy, tightly set sans for statements and an
# italic display serif for the phrase that qualifies them.
SANS = "Inter Tight"
SERIF = "Instrument Serif"
FONTS_CSS = ("https://fonts.googleapis.com/css2?family=Inter+Tight:wght@400;500;600;700;800"
             "&family=Instrument+Serif:ital@0;1&display=swap")

W, H = 1600, 900
X0 = 96
X1 = W - 96


def text(x, y, s, size, fill=INK, weight=400, family=SANS, anchor="start", italic=False, track=0):
    generic = "serif" if family == SERIF else "sans-serif"
    style = f"font-family:'{family}',{generic};font-size:{size}px;font-weight:{weight};fill:{fill}"
    if italic:
        style += ";font-style:italic"
    if track:
        style += f";letter-spacing:{track}px"
    return f'<text x="{x:.1f}" y="{y:.1f}" text-anchor="{anchor}" style="{style}">{escape(s)}</text>'


def lines(x, y, rows, size, step, **kw):
    return "".join(text(x, y + i * step, r, size, **kw) for i, r in enumerate(rows))


def frame(body, title, source):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
        f'width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
        f"<title>{escape(title)}</title>"
        f'<rect width="{W}" height="{H}" fill="{GROUND}"/>'
        f"{body}"
        f'<line x1="{X0}" y1="{H - 86}" x2="{X1}" y2="{H - 86}" stroke="{RULE}" stroke-width="1.5"/>'
        + text(X0, H - 48, source, 18, FAINT)
        + text(X1, H - 48, "Field Atlas", 18, DIM, 700, anchor="end")
        + "</svg>"
    )


def statement(x, y, parts, size, anchor="start"):
    """A title in two voices: the plain part in heavy, tight sans, the qualifying part in italic serif."""
    plain, qualified = parts
    sans = f"font-family:'{SANS}',sans-serif;font-size:{size}px;font-weight:800;letter-spacing:{-size * 0.03:.2f}px;fill:{INK}"
    serif = (f"font-family:'{SERIF}',serif;font-style:italic;font-weight:400;font-size:{size * 1.1:.1f}px;"
             f"letter-spacing:{-size * 0.01:.2f}px;fill:{SAGE_3}")
    return (f'<text x="{x:.1f}" y="{y:.1f}" text-anchor="{anchor}" style="{sans}">{escape(plain)}'
            f'<tspan style="{serif}">{escape(qualified)}</tspan></text>')


def heading(title, method):
    out = statement(X0, 142, title, 50)
    out += text(X0, 192, method, 22, DIM)
    return out


def axis(x_of, ticks, top, base_y, caption):
    out = ""
    for v in ticks:
        x = x_of(v)
        out += f'<line x1="{x:.1f}" y1="{top}" x2="{x:.1f}" y2="{base_y}" stroke="{RULE if v == ticks[0] else GRID}" stroke-width="{2 if v == ticks[0] else 1}"/>'
        out += text(x, base_y + 28, f"{v}%", 17, FAINT, 500, anchor="middle")
    out += text((x_of(ticks[0]) + x_of(ticks[-1])) / 2, base_y + 58, caption, 17, FAINT, anchor="middle")
    return out


def axis_counts(x_of, ticks, top, base_y, caption):
    out = ""
    for v in ticks:
        x = x_of(v)
        out += f'<line x1="{x:.1f}" y1="{top}" x2="{x:.1f}" y2="{base_y}" stroke="{RULE if v == ticks[0] else GRID}" stroke-width="{2 if v == ticks[0] else 1}"/>'
        out += text(x, base_y + 28, f"{v}", 17, FAINT, 500, anchor="middle")
    out += text((x_of(ticks[0]) + x_of(ticks[-1])) / 2, base_y + 58, caption, 17, FAINT, anchor="middle")
    return out


def ladder(data):
    # Counted the way Field Atlas measures itself: how many questions got a good answer (7 or more out of 10 from
    # blind graders), beside how many wrong details an answer carries.
    d = data["ladder"]
    rows = d["rows"]
    n = d["questions"]
    counts = ", ".join(str(r["good"]) for r in rows[:-1]) + " and " + str(rows[-1]["good"])
    body = heading((f"{counts} good answers out of {n},", " by phone size."), d["method"])
    top, row_h, bar_h = 300, 132, 44
    # The label column holds the phone tier and the route on separate lines, clear of the axis.
    scale_x, scale_w = X0 + 520, 690

    def x_of(v):
        return scale_x + scale_w * v / n

    base_y = top + row_h * len(rows) - 20
    body += axis_counts(x_of, list(range(0, n + 1, 10)), top - 4, base_y, f"questions out of {n} with a good answer")
    body += text(X1, top - 2, "wrong details", 17, FAINT, 600, anchor="end")
    body += text(X1, top + 20, "per answer", 17, FAINT, 600, anchor="end")
    fills = [SAGE_1, SAGE_2, AMBER]
    for i, r in enumerate(rows):
        y = top + 42 + i * row_h
        best = i == len(rows) - 1
        body += text(X0, y + 22, r["model"], 27, INK, 700)
        body += text(X0, y + 50, r["phone"], 18, DIM)
        body += text(X0, y + 74, r["route"], 18, DIM)
        w = max(x_of(r["good"]) - scale_x, 3)
        body += f'<rect x="{scale_x}" y="{y}" width="{w:.1f}" height="{bar_h}" rx="2" fill="{fills[i % 3]}"/>'
        # The count and its share, so the bar reads at a glance without the axis.
        body += text(scale_x + w + 14, y + 32, f"{r['good']} of {n} · {round(100 * r['good'] / n)}%", 27, AMBER if best else INK, 700)
        body += text(X1, y + 32, f"{r['errors']:.2f}", 27, DIM, 600, anchor="end")
    return frame(body, "Good answers by phone size", d["sourceLine"])


def collections(data):
    d = data["collections"]
    rows = d["rows"]
    n = d["questions"]
    body = heading(("More good answers", " when the model reads your saved sources."), d["method"])
    top, row_h = 300, 128
    scale_x, scale_w, lo, hi = X0 + 600, 680, 0, 10

    def sx(v):
        return scale_x + scale_w * (v - lo) / (hi - lo)

    base_y = top + row_h * len(rows) - 6
    body += axis_counts(sx, list(range(lo, hi + 1, 2)), top - 4, base_y, f"good answers out of {n}")
    for i, r in enumerate(rows):
        y = top + 26 + i * row_h
        cy = y + 34
        body += text(X0, y + 24, r["label"], 26, INK, 700)
        body += text(X0, y + 54, r["detail"], 18, DIM)
        body += text(X0, y + 80, f"{r['set']} · wrong details per answer {r['errorsBefore']:.2f} → {r['errorsAfter']:.2f}", 18, FAINT, italic=True, family=SERIF)
        a, b = sx(r["before"]), sx(r["after"])
        body += f'<line x1="{a:.1f}" y1="{cy}" x2="{b:.1f}" y2="{cy}" stroke="{SAGE_2}" stroke-width="3"/>'
        body += f'<circle cx="{a:.1f}" cy="{cy}" r="9" fill="{GROUND}" stroke="{SAGE_3}" stroke-width="3"/>'
        body += f'<circle cx="{b:.1f}" cy="{cy}" r="10" fill="{AMBER}"/>'
        body += text(a, cy - 22, r["beforeLabel"], 16, FAINT, anchor="middle")
        body += text(a, cy + 38, f"{r['before']}", 18, DIM, 500, anchor="middle")
        body += text(b, cy - 22, r["afterLabel"], 16, DIM, 600, anchor="middle")
        body += text(b, cy + 38, f"{r['after']}", 18, INK, 700, anchor="middle")
        # The gain, with the shares, so the dots read without the axis.
        body += text(b + 26, cy + 8, f"+{r['after'] - r['before']} · {round(100 * r['before'] / n)}% → {round(100 * r['after'] / n)}%", 22, AMBER, 700)
    return frame(body, "What the saved sources add", d["sourceLine"])


def storage(data):
    d = data["storage"]
    budget = d["budgetGB"]
    body = heading((f"{d['measuredGB']:.2f} GB of {budget},", " with every model, collection and voice installed."), d["method"])
    y0, bh = 400, 60
    bw = X1 - X0

    def gx(gb):
        return X0 + bw * gb / budget

    body += f'<defs><clipPath id="bar"><rect x="{X0}" y="{y0}" width="{bw}" height="{bh}" rx="2"/></clipPath></defs>'
    body += f'<rect x="{X0}" y="{y0}" width="{bw}" height="{bh}" rx="2" fill="{RAISED}" stroke="{RULE}" stroke-width="1.5"/>'
    fills = [SAGE_2, SAGE_3, AMBER]
    cursor = 0.0
    segments = []
    for i, p in enumerate(d["parts"]):
        a, b = gx(cursor), gx(cursor + p["gb"])
        body += f'<rect x="{a:.1f}" y="{y0}" width="{max(b - a, 3):.1f}" height="{bh}" fill="{fills[min(i, 2)]}" clip-path="url(#bar)"/>'
        segments.append((a, b, p))
        cursor += p["gb"]
    # Label the segments where they are instead of in a legend.
    (a0, b0, p0), (a1, b1, p1) = segments[0], segments[1]
    body += text(a0 + 18, y0 + 39, f"{p0['label']}, {p0['gb']:.2f} GB", 21, GROUND, 700)
    body += text((a1 + b1) / 2, y0 + 39, f"{p1['label']}, {p1['gb']:.2f} GB", 19, GROUND, 700, anchor="middle")
    rest = segments[2:]
    small = sum(p["gb"] for _, _, p in rest)
    mid2 = (rest[0][0] + rest[-1][1]) / 2
    body += f'<line x1="{mid2:.1f}" y1="{y0 + bh + 6}" x2="{mid2:.1f}" y2="{y0 + bh + 28}" stroke="{AMBER}" stroke-width="1.5"/>'
    rest_label = " and ".join(p["label"].lower() for _, _, p in rest)
    body += text(mid2 + 12, y0 + bh + 52, f"{rest_label}, {small * 1000:.0f} MB", 18, INK, 600, anchor="end")
    # What is left under the limit, named above its own slice of the bar.
    free = budget - d["measuredGB"]
    body += f'<line x1="{(gx(d["measuredGB"]) + X1) / 2:.1f}" y1="{y0 - 6}" x2="{(gx(d["measuredGB"]) + X1) / 2:.1f}" y2="{y0 - 28}" stroke="{DIM}" stroke-width="1.5"/>'
    body += text(X1, y0 - 38, f"{free:.2f} GB to spare", 19, DIM, 600, anchor="end")
    for gb in range(0, budget + 1, 10):
        anchor = "start" if gb == 0 else ("end" if gb == budget else "middle")
        body += text(gx(gb), y0 + bh + 100, f"{gb} GB", 16, FAINT, anchor=anchor)
    body += f'<line x1="{X0}" y1="{y0 + bh + 150}" x2="{X0 + 64}" y2="{y0 + bh + 150}" stroke="{AMBER}" stroke-width="2"/>'
    body += lines(X0, y0 + bh + 200, d["notes"], 24, 40, fill=INK, weight=500)
    return frame(body, "Storage on the phone", d["sourceLine"])


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


def hero(data, shots):
    d = data["hero"]
    shot = shots / Path(d["screenshot"]).name
    sw, sh = png_size(shot)
    b64 = base64.b64encode(shot.read_bytes()).decode()
    # The screenshot as it is, cropped to the top of the answer: no device mockup. The app is a
    # light page, so it reads as a sheet of paper laid on the dark ground.
    shot_w = 430
    s = shot_w / sw
    view_h = 704
    px, py = X1 - shot_w, 60
    body = (
        f'<defs><clipPath id="screen"><rect x="{px}" y="{py}" width="{shot_w}" height="{view_h}" rx="8"/></clipPath>'
        f'<linearGradient id="fade" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{GROUND}" stop-opacity="0"/><stop offset="1" stop-color="{GROUND}" stop-opacity="1"/>'
        f"</linearGradient></defs>"
    )
    body += (
        f'<image x="{px}" y="{py}" width="{shot_w}" height="{sh * s:.1f}" clip-path="url(#screen)" '
        f'preserveAspectRatio="xMidYMin meet" xlink:href="data:image/png;base64,{b64}"/>'
    )
    # The crop fades into the ground instead of ending on a hard cut through a sentence.
    body += f'<rect x="{px - 2}" y="{py + view_h - 170}" width="{shot_w + 4}" height="172" fill="url(#fade)"/>'
    body += statement(X0, 236, tuple(d["title"]), 104)
    body += f'<line x1="{X0 + 4}" y1="{290}" x2="{X0 + 68}" y2="{290}" stroke="{AMBER}" stroke-width="3"/>'
    body += lines(X0, 356, d["caption"], 29, 44, fill=INK, weight=500)
    body += lines(X0, 356 + 46 * len(d["caption"]) + 56, d["facts"], 21, 34, fill=DIM)
    return frame(body, "".join(d["title"]), d["sourceLine"])


def wrap(s, width, size):
    # Manrope averages about half an em per character; wrapping on that estimate keeps the
    # cards free of a text-measuring dependency, and the cards leave slack for the widest lines.
    limit = max(8, int(width / (size * 0.5)))
    rows, row = [], ""
    for word in s.split():
        if row and len(row) + 1 + len(word) > limit:
            rows.append(row)
            row = word
        else:
            row = f"{row} {word}".strip()
    return rows + ([row] if row else [])


def arrow(x1, y1, x2, y2, color, width=2):
    head = 9
    return (
        f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2 - head:.1f}" y2="{y2:.1f}" stroke="{color}" stroke-width="{width}"/>'
        f'<polygon points="{x2 - head:.1f},{y2 - 6:.1f} {x2:.1f},{y2:.1f} {x2 - head:.1f},{y2 + 6:.1f}" fill="{color}"/>'
    )


def pipeline(data):
    d = data["pipeline"]
    stages = d["stages"]
    body = heading(tuple(d["title"]), d["method"])
    gap = 44
    col_w = (X1 - X0 - gap * (len(stages) - 1)) / len(stages)
    top = 262
    card_gap = 10
    pad = 16
    bottoms = []
    for i, st in enumerate(stages):
        x = X0 + i * (col_w + gap)
        accent = AMBER if st.get("skipsModel") else SAGE_3
        body += text(x, top, f"{i + 1:02d}", 16, accent, 700, track=1)
        body += text(x + 34, top, st["title"], 19, INK, 700)
        body += text(x, top + 26, st["note"], 15, FAINT)
        y = top + 50
        for card in st["cards"]:
            detail = wrap(card["detail"], col_w - 2 * pad, 15)
            h = pad + 20 + 6 + 20 * len(detail) + pad - 6
            stroke = AMBER if st.get("skipsModel") else RULE
            body += f'<rect x="{x:.1f}" y="{y:.1f}" width="{col_w:.1f}" height="{h}" rx="4" fill="{RAISED}" stroke="{stroke}" stroke-opacity="{0.55 if st.get("skipsModel") else 1}" stroke-width="1.5"/>'
            body += text(x + pad, y + pad + 15, card["title"], 17, INK, 700)
            body += lines(x + pad, y + pad + 15 + 26, detail, 15, 20, fill=DIM)
            y += h + card_gap
        bottoms.append(y - card_gap)
    # The main path: each stage hands to the next at the height of its heading.
    for i in range(len(stages) - 1):
        if stages[i].get("skipsModel"):
            continue
        xa = X0 + (i + 1) * col_w + i * gap + 6
        xb = X0 + (i + 1) * (col_w + gap) - 6
        body += arrow(xa, top - 6, xb, top - 6, SAGE_2)
    # A question one of the direct routes answers never reaches search or the model: it goes straight to
    # the checked, sourced screen. That shortcut is the figure's one accent.
    skip = next((i for i, st in enumerate(stages) if st.get("skipsModel")), None)
    if skip is not None:
        x_from = X0 + skip * (col_w + gap) + col_w / 2
        x_to = X0 + (len(stages) - 1) * (col_w + gap) + col_w / 2
        y_low = max(bottoms) + 30
        body += f'<line x1="{x_from:.1f}" y1="{bottoms[skip] + 4:.1f}" x2="{x_from:.1f}" y2="{y_low:.1f}" stroke="{AMBER}" stroke-width="2"/>'
        body += f'<line x1="{x_from:.1f}" y1="{y_low:.1f}" x2="{x_to:.1f}" y2="{y_low:.1f}" stroke="{AMBER}" stroke-width="2"/>'
        body += f'<line x1="{x_to:.1f}" y1="{y_low:.1f}" x2="{x_to:.1f}" y2="{bottoms[-1] + 14:.1f}" stroke="{AMBER}" stroke-width="2"/>'
        body += f'<polygon points="{x_to - 6:.1f},{bottoms[-1] + 14:.1f} {x_to:.1f},{bottoms[-1] + 5:.1f} {x_to + 6:.1f},{bottoms[-1] + 14:.1f}" fill="{AMBER}"/>'
        gap_w = len(d["shortcut"]) * 16 * 0.55 + 36
        body += f'<rect x="{(x_from + x_to - gap_w) / 2:.1f}" y="{y_low - 14:.1f}" width="{gap_w:.1f}" height="28" fill="{GROUND}"/>'
        body += text((x_from + x_to) / 2, y_low + 6, d["shortcut"], 16, AMBER, 600, anchor="middle")
        # From the direct routes, everything they do not answer goes on to search.
        xa = X0 + (skip + 1) * col_w + skip * gap + 6
        xb = X0 + (skip + 1) * (col_w + gap) - 6
        body += arrow(xa, top - 6, xb, top - 6, SAGE_2)
    return frame(body, "How Field Atlas answers", d["sourceLine"])


def route(points, color, width=2, head=True):
    """An orthogonal connector through the given points, with an arrowhead on the last segment."""
    path = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in points)
    out = f'<path d="{path}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linejoin="round"/>'
    if head:
        (xa, ya), (xb, yb) = points[-2], points[-1]
        if abs(xb - xa) >= abs(yb - ya):
            sgn = 1 if xb > xa else -1
            tri = f"{xb - 9 * sgn:.1f},{yb - 6:.1f} {xb:.1f},{yb:.1f} {xb - 9 * sgn:.1f},{yb + 6:.1f}"
        else:
            sgn = 1 if yb > ya else -1
            tri = f"{xb - 6:.1f},{yb - 9 * sgn:.1f} {xb:.1f},{yb:.1f} {xb + 6:.1f},{yb - 9 * sgn:.1f}"
        out += f'<polygon points="{tri}" fill="{color}"/>'
    return out


def box(x, y, w, h, title, detail=None, stroke=RULE, title_size=17):
    out = f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="4" fill="{RAISED}" stroke="{stroke}" stroke-width="1.5"/>'
    rows = wrap(detail, w - 28, 15) if detail else []
    block = 20 + 20 * len(rows)
    top = y + (h - block) / 2 + 15
    out += text(x + 14, top, title, title_size, INK, 700)
    out += lines(x + 14, top + 24, rows, 15, 20, fill=DIM)
    return out


def fitted(x, cy, w, title, detail, title_size=17):
    """A box centred on cy and as tall as its text needs, so nothing spills out of it."""
    h = 28 + 20 + 20 * len(wrap(detail, w - 28, 15))
    return box(x, cy - h / 2, w, h, title, detail, title_size=title_size), h


def question(x, cy, w, label):
    # Decisions are pills with an open outline, so they read as a question asked, not a step done.
    h = 46
    return (f'<rect x="{x:.1f}" y="{cy - h / 2:.1f}" width="{w:.1f}" height="{h}" rx="{h / 2}" fill="{GROUND}" stroke="{SAGE_3}" stroke-width="1.5"/>'
            + text(x + w / 2, cy + 6, label, 16, INK, 600, anchor="middle"))


def edge_label(x, y, s, color=FAINT, anchor="start"):
    return text(x, y, s, 15, color, 600, anchor=anchor)


def flow(data):
    d = data["flow"]
    body = heading(tuple(d["title"]), d["method"])
    # A grid: the app's own answers run along the top lane, search and the model below it, and every
    # path ends in the answer column on the right.
    ans_x, ans_top, ans_bot = 1340, 250, 786
    box_r = ans_x - 40
    q_x, q_w, q_cy = X0, 140, 470
    d1x, d1w = 264, 156
    d1c = d1x + d1w / 2
    lane_x = 470
    top_cy = 309
    # Question, then the first decision.
    body += fitted(q_x, q_cy, q_w, d["ask"][0], d["ask"][1])[0]
    body += route([(q_x + q_w, q_cy), (d1x, q_cy)], SAGE_2)
    body += question(d1x, q_cy, d1w, d["exact"])
    # Yes: the app writes the answer itself.
    gy, gh = 250, 118
    gw = box_r - lane_x
    body += f'<rect x="{lane_x}" y="{gy}" width="{gw}" height="{gh}" rx="6" fill="none" stroke="{AMBER}" stroke-opacity="0.6" stroke-width="1.5"/>'
    body += text(lane_x + 16, gy + 26, d["direct"]["title"], 16, AMBER, 700)
    chip_w = (gw - 32 - 3 * 12) / 4
    for i, (t, dt) in enumerate(d["direct"]["routes"]):
        body += box(lane_x + 16 + i * (chip_w + 12), gy + 40, chip_w, 62, t, dt, title_size=16)
    body += route([(d1c, q_cy - 23), (d1c, top_cy), (lane_x, top_cy)], AMBER)
    body += edge_label(d1c + 10, (top_cy + q_cy) / 2 + 10, "yes", AMBER)
    body += route([(box_r, top_cy), (ans_x, top_cy)], AMBER)
    # No: search the collections, then ask which model the phone holds.
    s_cy, s_w = 600, 150
    body += route([(d1c, q_cy + 23), (d1c, s_cy), (lane_x, s_cy)], SAGE_2)
    body += edge_label(d1c + 10, (q_cy + s_cy) / 2 + 10, "no")
    body += fitted(lane_x, s_cy, s_w, d["search"][0], d["search"][1])[0]
    d2x, d2w = lane_x + s_w + 26, 150
    d2c = d2x + d2w / 2
    body += route([(lane_x + s_w, s_cy), (d2x, s_cy)], SAGE_2)
    body += question(d2x, s_cy, d2w, d["memory"])
    bx = 880
    small_cy, rare_cy = 480, 712
    body += route([(d2c, s_cy - 23), (d2c, small_cy), (bx, small_cy)], SAGE_2)
    body += edge_label(d2c + 12, small_cy - 10, d["small"][0])
    body += route([(d2c, s_cy + 23), (d2c, rare_cy), (bx, rare_cy)], SAGE_2)
    body += edge_label(d2c + 12, rare_cy - 10, d["big"][0])
    body += fitted(bx, small_cy, box_r - bx, d["small"][1], d["small"][2])[0]
    # 6 to 12 GB: a rare subject is read from the sources, anything else answered from memory.
    rw = 124
    body += question(bx, rare_cy, rw, d["rare"])
    nx = bx + rw + 64
    split = bx + rw + 22
    yes_cy, no_cy = 652, 748
    body += route([(bx + rw, rare_cy), (split, rare_cy), (split, yes_cy), (nx, yes_cy)], SAGE_2)
    body += route([(split, rare_cy), (split, no_cy), (nx, no_cy)], SAGE_2)
    # The branch words sit on the vertical stems, clear of the boxes they lead to.
    body += edge_label(split + 8, (rare_cy + yes_cy) / 2 + 2, "yes")
    body += edge_label(split + 8, (rare_cy + no_cy) / 2 + 12, "no")
    body += fitted(nx, yes_cy, box_r - nx, d["yes"][0], d["yes"][1], title_size=16)[0]
    body += fitted(nx, no_cy, box_r - nx, d["no"][0], d["no"][1], title_size=16)[0]
    for cy in (small_cy, yes_cy, no_cy):
        body += route([(box_r, cy), (ans_x, cy)], SAGE_2)
    # Every path ends on one answer page.
    ans_w = X1 - ans_x
    body += f'<rect x="{ans_x}" y="{ans_top}" width="{ans_w}" height="{ans_bot - ans_top}" rx="6" fill="{RAISED}" stroke="{SAGE_3}" stroke-width="1.5"/>'
    mid = (ans_top + ans_bot) / 2
    body += statement(ans_x + 18, mid - 44, ("Answer", ""), 30)
    rows = []
    for line in d["answer"]:
        rows += wrap(line, ans_w - 36, 15) + [""]
    body += lines(ans_x + 18, mid - 8, rows[:-1], 15, 20, fill=DIM)
    return frame(body, "".join(d["title"]), d["sourceLine"])


def font_config(out):
    conf = out / "fonts.conf"
    cache = out / ".fontcache"
    conf.write_text(
        '<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "fonts.dtd"><fontconfig>'
        f"<dir>{escape(str(FONT_DIR))}</dir>"
        "<dir>/System/Library/Fonts</dir>"
        f"<cachedir>{escape(str(cache))}</cachedir>"
        "</fontconfig>"
    )
    return conf


def render_chrome(svgs, scale):
    """Render each SVG in headless Chrome with the web fonts loaded; None when Playwright is missing."""
    try:
        import asyncio
        from playwright.async_api import async_playwright
    except ImportError:
        return None

    async def run():
        async with async_playwright() as p:
            browser = await p.chromium.launch(channel="chrome")
            page = await browser.new_page(viewport={"width": W, "height": H}, device_scale_factor=scale)
            for svg_path, png_path in svgs:
                html = svg_path.with_suffix(".html")
                html.write_text(
                    f'<!doctype html><meta charset="utf-8"><link rel="stylesheet" href="{FONTS_CSS}">'
                    f"<style>html,body{{margin:0;background:{GROUND}}}svg{{display:block}}</style>"
                    + svg_path.read_text()
                )
                await page.goto(html.as_uri())
                await page.evaluate("document.fonts.ready")
                # Fonts referenced only from SVG text load lazily; wait until both faces are in.
                await page.wait_for_function(
                    f"document.fonts.check('800 50px \"{SANS}\"') && document.fonts.check('italic 50px \"{SERIF}\"')",
                    timeout=15000)
                await page.screenshot(path=str(png_path), clip={"x": 0, "y": 0, "width": W, "height": H})
                html.unlink()
            await browser.close()

    asyncio.run(run())
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(REPO / "build" / "charts"))
    ap.add_argument("--scale", type=float, default=2.0, help="PNG pixels per SVG unit")
    ap.add_argument("--engine", choices=["chrome", "rsvg"], default="chrome")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    data = json.loads((HERE / "charts.json").read_text())
    images = {
        "fieldatlas-hero": hero(data, HERE / "screens"),
        "fieldatlas-models": ladder(data),
        "fieldatlas-collections": collections(data),
        "fieldatlas-storage": storage(data),
        "fieldatlas-pipeline": pipeline(data),
        "fieldatlas-flow": flow(data),
    }
    pairs = []
    for name, svg in images.items():
        svg_path = out / f"{name}.svg"
        svg_path.write_text(svg)
        pairs.append((svg_path, out / f"{name}.png"))
    if args.engine == "chrome" and render_chrome(pairs, args.scale):
        for _, png in pairs:
            print(png)
        return
    renderer = shutil.which("rsvg-convert")
    if not renderer:
        sys.exit("Neither Playwright nor rsvg-convert is available to render PNG")
    print("Playwright not found: rendering with rsvg-convert and fallback fonts", file=sys.stderr)
    # Pango on macOS uses CoreText unless told otherwise, which would ignore a private font directory.
    env = dict(os.environ, FONTCONFIG_FILE=str(font_config(out)), PANGOCAIRO_BACKEND="fc")
    for svg_path, png_path in pairs:
        subprocess.run([renderer, "-z", str(args.scale), "-o", str(png_path), str(svg_path)], check=True, env=env)
        print(png_path)


if __name__ == "__main__":
    main()
