#!/usr/bin/env python3
"""Extract the OpenStax *Introductory Statistics 2e* corpus for Field Atlas.

Reads the book from a checkout of openstax/osbooks-introductory-statistics-bundle
(the corpus here was extracted at commit 50ec91749080bb040b57bb40c34337cacc994cba,
17 March 2026, the last revision before OpenStax moved the bundle to CC BY-NC-SA on
19 March 2026) and writes one JSONL row per module, in book order:

    {"document_id": "openstax-introductory-statistics-<module id>",
     "title": "<chapter title> — <section title>",
     "source": "https://openstax.org/books/introductory-statistics-2e/pages/<slug>",
     "license": "CC-BY-4.0",
     "text": "<the whole module, in OpenStax's own words>"}

Kept: every numbered section of the 13 chapters (the labs are numbered sections in
this book), each with its worked examples, try-it exercises, chapter-review summary,
formula review, practice, homework, bring-it-together problems and the book's
solutions, followed by its glossary; each chapter's introduction (several carry
glossary terms or a formula review); and the two appendices that hold the formula
tables: "Mathematical Phrases, Symbols, and Formulas" and "Tables".
Left out: the preface and the appendices of review sets, practice tests, data sets,
projects, solution sheets and calculator notes. The script prints each module it
leaves out and why.

The CNXML/MathML to text conversion is the Prealgebra extractor's
(tools/packs/extract_openstax_prealgebra.py), reused unchanged: nothing is rewritten,
formulas are LaTeX converted from MathML, tables are one row to a line, images are
left out with their captions and descriptions kept.

Output is deterministic: book order, fixed key order, no timestamps.

Usage:
  python3 tools/packs/extract_openstax_introductory_statistics.py --osbooks DIR [--out FILE]
"""

from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from tools.packs.extract_openstax_prealgebra import CNXML, COLLXML, MDML, _flat, _local, kebab, module_to_text

BOOK_URL = "https://openstax.org/books/introductory-statistics-2e"
COLLECTION = "introductory-statistics-2e.collection.xml"
LICENSE_ID = "CC-BY-4.0"
ID_PREFIX = "openstax-introductory-statistics-"
# Appendices are lettered in book order; only the two holding formula tables are kept.
KEPT_APPENDICES = {"Mathematical Phrases, Symbols, and Formulas", "Tables"}


# Three fixes to the source text, all to characters that would otherwise show as noise or as boxes:
# a summation sign whose empty superscript is a zero-width space ("{\u2211}^{\\text{\u200b}}") becomes
# the plain sign, and two characters from the private-use area of the Symbol font are written as the
# characters they stand for there (\uf061 = alpha, \uf02d = minus).
SOURCE_FIXES = (("{\u2211}^{\\text{\u200b}}", "\u2211"), ("\uf061", "\u03b1"), ("\uf02d", "-"))


def clean(text: str) -> str:
    for old, new in SOURCE_FIXES:
        text = text.replace(old, new)
    return text


def parse_book(osbooks: Path):
    root = ET.parse(osbooks / "collections" / COLLECTION).getroot()

    def module(mid):
        doc = ET.parse(osbooks / "modules" / mid / "index.cnxml").getroot()
        title_el = doc.find(f"{{{CNXML}}}title")
        return doc.get("class") or "", (_flat(title_el) if title_el is not None else mid), doc

    front, chapters, back = [], [], []
    for node in root.find(f"{{{COLLXML}}}content"):
        if _local(node) == "module":
            mid = node.get("document")
            cls, title, _ = module(mid)
            (back if chapters else front).append((mid, cls, title))
        elif _local(node) == "subcollection":
            mods = []
            for m in node.find(f"{{{COLLXML}}}content").iter(f"{{{COLLXML}}}module"):
                cls, title, _ = module(m.get("document"))
                mods.append((m.get("document"), cls, title))
            chapters.append((_flat(node.find(f"{{{MDML}}}title")), mods))
    return front, chapters, back, module


def main():
    ap = argparse.ArgumentParser()
    here = Path(__file__).resolve().parent
    ap.add_argument("--osbooks", type=Path, required=True,
                    help="checkout of openstax/osbooks-introductory-statistics-bundle")
    ap.add_argument("--out", type=Path, default=here / "introductory_statistics" / "corpus.jsonl")
    args = ap.parse_args()

    try:
        commit = subprocess.run(["git", "-C", str(args.osbooks), "rev-parse", "HEAD"],
                                capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        commit = "unknown"
    print(f"osbooks commit: {commit}", file=sys.stderr)

    front, chapters, back, module = parse_book(args.osbooks)
    all_titles = {mid: title for mid, _, title in front + back}
    for _, mods in chapters:
        all_titles.update({mid: title for mid, _, title in mods})

    def make_resolver(doc_root):
        by_id = {el.get("id"): el for el in doc_root.iter() if el.get("id")}
        labels = {"figure": "Figure", "table": "Table", "example": "Example", "exercise": "Exercise"}

        def resolve(link_el):
            target_doc = link_el.get("document")
            if target_doc and target_doc in all_titles:
                return all_titles[target_doc]
            target = by_id.get(link_el.get("target-id") or "")
            if target is None:
                return ""
            if _local(target) == "section":
                t = target.find(f"{{{CNXML}}}title")
                return _flat(t) if t is not None else ""
            return labels.get(_local(target), "")

        return resolve

    def text_of(mid):
        _, _, doc = module(mid)
        text = module_to_text(doc.find(f"{{{CNXML}}}content"), resolve_link=make_resolver(doc))
        glossary = doc.find(f"{{{CNXML}}}glossary")
        if glossary is not None:
            # The glossary stands beside the content in the source; the book prints it with the section.
            terms = module_to_text(glossary, resolve_link=make_resolver(doc))
            if terms:
                text += "\n\nGlossary\n" + terms
        return clean(text)

    rows, excluded = [], []
    for mid, cls, title in front:
        excluded.append((mid, cls, title, "front matter"))
    for ch_no, (ch_title, mods) in enumerate(chapters, start=1):
        sec_no = 0
        for mid, cls, title in mods:
            if cls == "introduction":
                slug = f"{ch_no}-introduction"
            else:
                sec_no += 1
                slug = f"{ch_no}-{sec_no}-{kebab(title)}"
            rows.append({"document_id": ID_PREFIX + mid, "title": f"{ch_title} — {title}",
                         "source": f"{BOOK_URL}/pages/{slug}", "license": LICENSE_ID, "text": text_of(mid)})
    for letter_no, (mid, cls, title) in enumerate(back):
        if title not in KEPT_APPENDICES:
            excluded.append((mid, cls, title, "appendix without formula tables"))
            continue
        letter = chr(ord("a") + letter_no)
        rows.append({"document_id": ID_PREFIX + mid, "title": f"Appendix — {title}",
                     "source": f"{BOOK_URL}/pages/{letter}-{kebab(title)}", "license": LICENSE_ID,
                     "text": text_of(mid)})

    payload = "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(payload, encoding="utf-8")
    print(f"rows: {len(rows)}", file=sys.stderr)
    print("excluded modules:", file=sys.stderr)
    for mid, cls, title, why in excluded:
        print(f"  {mid} [{cls}] {title} ({why})", file=sys.stderr)
    sizes = [len(r["text"]) for r in rows]
    print(f"total text chars: {sum(sizes)}  min row chars: {min(sizes)}  max row chars: {max(sizes)}", file=sys.stderr)
    print(f"corpus bytes: {len(payload.encode('utf-8'))}", file=sys.stderr)
    print(f"corpus sha256: {hashlib.sha256(payload.encode('utf-8')).hexdigest()}", file=sys.stderr)


if __name__ == "__main__":
    main()
