#!/usr/bin/env python3
"""Extract the OpenStax *Prealgebra 2e* corpus for Field Atlas issue #26.

Reads the book from a checkout of openstax/osbooks-prealgebra-bundle
(pinned by the checkout itself; this script records the commit it finds)
and writes one JSONL row per numbered book section, in book order:

    {"document_id": "openstax-prealgebra-<module id>",
     "title": "<chapter title> \u2014 <section title>",
     "source": "https://openstax.org/books/prealgebra-2e/pages/<ch>-<sec>-<slug>",
     "license": "CC-BY-4.0",
     "text": "<the whole section, in OpenStax's own words>"}

Conversion rules (CNXML -> plain text):
  * Nothing is rewritten, summarised or reordered. Block structure becomes
    line breaks; headings, examples, exercises, problems, solutions, notes,
    tables and the end-of-section glossary are all kept, in order.
  * MathML carries no LaTeX alttext in the bundle, so formulas are kept as
    LaTeX produced by a deterministic MathML -> LaTeX conversion
    ($...$ inline, $$...$$ for display equations).
  * Images are not embedded (the pack is text). A figure contributes its
    caption and, when present, the image's alt text as "[Image: ...]".
  * Tables become one line per row, cells joined with " | ".

Excluded modules (not numbered book sections):
  * the Preface (class "preface"),
  * each chapter's unnumbered Introduction page (class "introduction"),
  * the three appendix modules (class "appendix": Cumulative Review,
    Powers and Roots Tables, Geometric Formulas).
The script prints exactly which module ids it excluded and why.

Output is deterministic: book order, fixed key order, no timestamps.

Usage:
  python3 tools/packs/extract_openstax_prealgebra.py --osbooks DIR [--out FILE]

--osbooks is a checkout of openstax/osbooks-prealgebra-bundle (the corpus
here was extracted at commit 7fcdabf0364f3b308c0190557dfc961b579b374c).
--out defaults to tools/packs/prealgebra/corpus.jsonl beside this script.
The extraction is deterministic: the same checkout always produces the
same bytes, whose SHA-256 is the build pin recorded in
tools/packs/build_prealgebra_pack.py and tools/tests/test_prealgebra_corpus.py.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

CNXML = "http://cnx.rice.edu/cnxml"
COLLXML = "http://cnx.rice.edu/collxml"
MDML = "http://cnx.rice.edu/mdml"
MATHML = "http://www.w3.org/1998/Math/MathML"

BOOK_URL = "https://openstax.org/books/prealgebra-2e"
LICENSE_ID = "CC-BY-4.0"
EXCLUDED_CLASSES = {"preface", "introduction", "appendix"}

# ---------------------------------------------------------------------------
# MathML -> LaTeX
# ---------------------------------------------------------------------------

_MO_MAP = {
    "\u2212": "-", "\u00d7": r"\times", "\u00f7": r"\div", "\u22c5": r"\cdot",
    "\u00b7": r"\cdot", "\u2264": r"\le", "\u2265": r"\ge", "\u2260": r"\ne",
    "\u00b1": r"\pm", "\u221e": r"\infty", "\u03c0": r"\pi", "\u2026": r"\dots",
    "\u2062": "", "\u2061": "", "\u2009": r"\,", "\u00a0": " ",
    "\u221a": r"\sqrt", "\u2248": r"\approx", "\u2218": r"^\circ",
    "\u2032": "'", "\u2033": "''",
}
_MI_MAP = {"\u03c0": r"\pi", "\u0394": r"\Delta", "\u03b8": r"\theta"}


def _kids(el):
    return [c for c in el if isinstance(c.tag, str)]


def _flat(el):
    """All text under el, whitespace-collapsed."""
    return re.sub(r"\s+", " ", "".join(el.itertext())).strip()


def mathml_to_latex(el) -> str:
    tag = el.tag.rsplit("}", 1)[-1]
    children = _kids(el)
    if tag == "math":
        return "".join(mathml_to_latex(c) for c in children)
    if tag in ("mrow", "mstyle", "mpadded"):
        return "".join(mathml_to_latex(c) for c in children)
    if tag == "mn":
        return _flat(el)
    if tag == "mi":
        t = _flat(el)
        if t in _MI_MAP:
            return _MI_MAP[t]
        return t
    if tag == "mo":
        t = _flat(el)
        return _MO_MAP.get(t, t)
    if tag == "mtext":
        t = _flat(el)
        return r"\text{" + t + "}" if t else ""
    if tag == "mspace":
        return " "
    if tag == "mphantom":
        return ""
    if tag == "mfrac":
        if len(children) != 2:
            return "".join(mathml_to_latex(c) for c in children)
        return r"\frac{%s}{%s}" % (mathml_to_latex(children[0]),
                                   mathml_to_latex(children[1]))
    if tag == "msup":
        if len(children) != 2:
            return "".join(mathml_to_latex(c) for c in children)
        return "{%s}^{%s}" % (mathml_to_latex(children[0]),
                              mathml_to_latex(children[1]))
    if tag == "msub":
        if len(children) != 2:
            return "".join(mathml_to_latex(c) for c in children)
        return "{%s}_{%s}" % (mathml_to_latex(children[0]),
                              mathml_to_latex(children[1]))
    if tag == "msubsup":
        if len(children) != 3:
            return "".join(mathml_to_latex(c) for c in children)
        return "{%s}_{%s}^{%s}" % tuple(mathml_to_latex(c) for c in children)
    if tag == "msqrt":
        return r"\sqrt{%s}" % "".join(mathml_to_latex(c) for c in children)
    if tag == "mroot":
        if len(children) != 2:
            return "".join(mathml_to_latex(c) for c in children)
        return r"\sqrt[%s]{%s}" % (mathml_to_latex(children[1]),
                                   mathml_to_latex(children[0]))
    if tag == "mfenced":
        open_c = el.get("open", "(")
        close_c = el.get("close", ")")
        body = ",".join(mathml_to_latex(c) for c in children)
        return r"\left%s %s \right%s" % (open_c, body, close_c)
    if tag == "menclose":
        inner = "".join(mathml_to_latex(c) for c in children)
        if "radical" in (el.get("notation") or ""):
            return r"\sqrt{%s}" % inner
        return inner  # longdiv etc.: keep the content, drop the layout rule
    if tag == "munder":
        if len(children) != 2:
            return "".join(mathml_to_latex(c) for c in children)
        return "{%s}_{%s}" % (mathml_to_latex(children[0]),
                              mathml_to_latex(children[1]))
    if tag == "mover":
        if len(children) != 2:
            return "".join(mathml_to_latex(c) for c in children)
        return "{%s}^{%s}" % (mathml_to_latex(children[0]),
                              mathml_to_latex(children[1]))
    if tag == "munderover":
        if len(children) != 3:
            return "".join(mathml_to_latex(c) for c in children)
        return "{%s}_{%s}^{%s}" % tuple(mathml_to_latex(c) for c in children)
    if tag == "mtable":
        rows = []
        for tr in children:
            cells = [mathml_to_latex(td) for td in _kids(tr)]
            rows.append(" & ".join(cells))
        return r"\begin{matrix} %s \end{matrix}" % (r" \\ ".join(rows))
    if tag in ("mtr", "mtd"):
        return "".join(mathml_to_latex(c) for c in children)
    if tag == "semantics":
        return mathml_to_latex(children[0]) if children else ""
    if tag == "maction":
        return mathml_to_latex(children[0]) if children else ""
    if tag == "annotation":
        return ""
    # Unknown MathML element: keep its content rather than dropping math.
    return "".join(mathml_to_latex(c) for c in children) or _flat(el)


# ---------------------------------------------------------------------------
# CNXML -> plain text
# ---------------------------------------------------------------------------

BLOCK_TAGS = {
    "para", "section", "example", "exercise", "problem", "solution",
    "note", "figure", "table", "list", "glossary", "definition",
    "equation", "quote", "preformat", "caption", "media",
}
# Elements whose whole subtree is not book prose.
SKIP_TAGS = {"metadata"}


def _local(el):
    return el.tag.rsplit("}", 1)[-1] if isinstance(el.tag, str) else ""


class Serializer:
    def __init__(self, resolve_link=None):
        self.lines: list[str] = []
        self.buf: list[str] = []
        self.in_equation = 0
        self.resolve_link = resolve_link or (lambda el: "")

    def flush(self):
        text = re.sub(r"[ \t]+", " ", "".join(self.buf)).strip()
        self.buf = []
        if text:
            self.lines.append(text)

    def add(self, s: str):
        if s:
            self.buf.append(s)

    def walk(self, el):
        tag = _local(el)
        if tag in SKIP_TAGS:
            return
        if tag == "link" and not _flat(el):
            # Empty cross-reference: the book fills in the target's label.
            self.add(self.resolve_link(el))
            return
        if tag == "equation":
            self.flush()
            self.in_equation += 1
            for child in el:
                self.walk(child)
                if child.tail:
                    self.add(child.tail)
            self.in_equation -= 1
            self.flush()
            return
        if tag == "math" and el.tag.startswith("{" + MATHML):
            display = el.get("display") == "block" or self.in_equation > 0
            latex = mathml_to_latex(el).strip()
            if latex:
                if display:
                    self.flush()
                    self.lines.append("$$%s$$" % latex)
                else:
                    self.add("$%s$" % latex)
            return
        block = tag in BLOCK_TAGS
        if block:
            self.flush()
        if tag == "title":
            self.flush()
            t = _flat(el)
            if t:
                self.lines.append(t)
            return
        if tag == "figure":
            self._figure(el)
            self.flush()
            return
        if tag == "table":
            self._table(el)
            self.flush()
            return
        if tag == "item":
            self.flush()
            self.add("- ")
        if el.text:
            self.add(el.text)
        for child in el:
            self.walk(child)
            if child.tail:
                self.add(child.tail)
        if block:
            self.flush()

    def _figure(self, el):
        alt = None
        caption = None
        for node in el.iter():
            if _local(node) == "media":
                alt = node.get("alt")
            elif _local(node) == "caption":
                caption = node
        if alt:
            self.add("[Image: %s]" % re.sub(r"\s+", " ", alt).strip())
            self.flush()
        if caption is not None:
            for child in caption:
                self.walk(child)
                if child.tail:
                    self.add(child.tail)
            if caption.text:
                self.buf.insert(0, caption.text)
            self.flush()

    def _table(self, el):
        for node in el.iter():
            if _local(node) == "row":
                cells = []
                for entry in _kids(node):
                    s = Serializer()
                    if entry.text:
                        s.add(entry.text)
                    for child in entry:
                        s.walk(child)
                        if child.tail:
                            s.add(child.tail)
                    s.flush()
                    cells.append(" ".join(s.lines))
                line = " | ".join(c for c in cells if c != "")
                if line.strip(" |"):
                    self.lines.append(line)


def module_to_text(content_el, resolve_link=None) -> str:
    s = Serializer(resolve_link=resolve_link)
    s.walk(content_el)
    s.flush()
    text = "\n".join(s.lines)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


# ---------------------------------------------------------------------------
# Book structure
# ---------------------------------------------------------------------------

def kebab(title: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
    return re.sub(r"-{2,}", "-", slug)


def parse_book(osbooks: Path):
    """Return (chapters, flat) where chapters is a list of
    (chapter_title, [(module_id, class, doc_title), ...]) and flat is the
    preface list. Module class/title come from each module's CNXML."""
    coll = ET.parse(osbooks / "collections" / "prealgebra-2e.collection.xml")
    root = coll.getroot()

    def module_info(mid):
        path = osbooks / "modules" / mid / "index.cnxml"
        doc = ET.parse(path).getroot()
        cls = doc.get("class") or ""
        title_el = doc.find(f"{{{CNXML}}}title")
        title = _flat(title_el) if title_el is not None else mid
        content = doc.find(f"{{{CNXML}}}content")
        return cls, title, content, doc

    chapters = []
    preface = []
    content_el = root.find(f"{{{COLLXML}}}content")
    for node in content_el:
        if _local(node) == "module":
            mid = node.get("document")
            cls, title, _, _ = module_info(mid)
            preface.append((mid, cls, title))
        elif _local(node) == "subcollection":
            title_el = node.find(f"{{{MDML}}}title")
            ch_title = _flat(title_el)
            mods = []
            inner = node.find(f"{{{COLLXML}}}content")
            for m in inner.iter(f"{{{COLLXML}}}module"):
                mid = m.get("document")
                cls, title, _, _ = module_info(mid)
                mods.append((mid, cls, title))
            chapters.append((ch_title, mods))
    return chapters, preface, module_info


def main():
    ap = argparse.ArgumentParser()
    here = Path(__file__).resolve().parent
    ap.add_argument("--osbooks", type=Path, required=True,
                    help="checkout of openstax/osbooks-prealgebra-bundle")
    ap.add_argument("--out", type=Path, default=here / "prealgebra" / "corpus.jsonl")
    args = ap.parse_args()

    try:
        commit = subprocess.run(
            ["git", "-C", str(args.osbooks), "rev-parse", "HEAD"],
            capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        commit = "unknown"
    print(f"osbooks commit: {commit}", file=sys.stderr)

    chapters, preface, module_info = parse_book(args.osbooks)

    # Titles of every module in the book, for cross-reference resolution.
    all_titles = {}
    for mid, _, title in preface:
        all_titles[mid] = title
    for _, mods in chapters:
        for mid, _, title in mods:
            all_titles[mid] = title

    def make_resolver(doc_root):
        by_id = {el.get("id"): el for el in doc_root.iter() if el.get("id")}
        labels = {"figure": "Figure", "table": "Table", "example": "Example",
                  "exercise": "Exercise"}

        def resolve(link_el):
            target_doc = link_el.get("document")
            if target_doc and target_doc in all_titles:
                return all_titles[target_doc]
            target = by_id.get(link_el.get("target-id") or "")
            if target is None:
                return ""
            tag = _local(target)
            if tag == "section":
                t = target.find(f"{{{CNXML}}}title")
                return _flat(t) if t is not None else ""
            return labels.get(tag, "")

        return resolve

    rows = []
    excluded = []
    for mid, cls, title in preface:
        excluded.append((mid, cls, title, "outside the chapter subcollections"))
    for ch_no, (ch_title, mods) in enumerate(chapters, start=1):
        sec_no = 0
        for mid, cls, title in mods:
            if cls in EXCLUDED_CLASSES:
                excluded.append((mid, cls, title, f"chapter {ch_no} ({ch_title})"))
                continue
            sec_no += 1
            _, _, content, doc_root = module_info(mid)
            text = module_to_text(content, resolve_link=make_resolver(doc_root))
            slug = f"{ch_no}-{sec_no}-{kebab(title)}"
            rows.append({
                "document_id": f"openstax-prealgebra-{mid}",
                "title": f"{ch_title} \u2014 {title}",
                "source": f"{BOOK_URL}/pages/{slug}",
                "license": LICENSE_ID,
                "text": text,
            })

    payload = "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
    args.out.write_text(payload, encoding="utf-8")

    print(f"rows: {len(rows)}", file=sys.stderr)
    print("excluded modules:", file=sys.stderr)
    for mid, cls, title, where in excluded:
        print(f"  {mid} [{cls}] {title} ({where})", file=sys.stderr)
    sizes = [len(r["text"]) for r in rows]
    print(f"total text chars: {sum(sizes)}", file=sys.stderr)
    print(f"min row chars: {min(sizes)}  max row chars: {max(sizes)}",
          file=sys.stderr)
    print(f"corpus bytes: {len(payload.encode('utf-8'))}", file=sys.stderr)
    print(f"corpus sha256: {hashlib.sha256(payload.encode('utf-8')).hexdigest()}",
          file=sys.stderr)


if __name__ == "__main__":
    main()
