#!/usr/bin/env python3
"""Extract the OpenStax *Introductory Statistics 2e* corpus for Field Atlas.

Reads the book from a checkout of
openstax/osbooks-introductory-statistics-bundle pinned at the last revision
published under CC BY 4.0 (50ec91749080bb040b57bb40c34337cacc994cba,
17 March 2026; the publisher changed the licence to CC BY-NC-SA on
19 March 2026, and no later revision is used) and writes one JSONL row per
numbered book section, in book order:

    {"document_id": "openstax-introstats-<module id>",
     "title": "<chapter title> \u2014 <section title>",
     "source": "https://openstax.org/books/introductory-statistics-2e/pages/<ch>-<sec>-<slug>",
     "license": "CC-BY-4.0",
     "text": "<the whole section, in OpenStax's own words>"}

Two appendices are kept because they hold the book's formula and
statistical tables: Appendix F (Mathematical Phrases, Symbols, and
Formulas) and Appendix H (Tables). The preface, the chapter introductions
and the other six appendices are excluded, as in the Prealgebra pack.

Conversion rules and the CNXML/MathML helpers are shared with
tools/packs/extract_openstax_prealgebra.py: nothing is rewritten,
summarised or reordered; formulas become LaTeX; tables become one line per
row; images contribute their caption and alt text; each section is followed
by its glossary.

Output is deterministic: book order, fixed key order, no timestamps.

Usage:
  python3 tools/packs/extract_openstax_introstats.py --osbooks DIR [--out FILE]

--osbooks is a checkout of openstax/osbooks-introductory-statistics-bundle at
commit 50ec91749080bb040b57bb40c34337cacc994cba. The extraction is
deterministic: the same checkout always produces the same bytes, whose
SHA-256 is the build pin recorded in tools/packs/build_introstats_pack.py
and tools/tests/test_introstats_corpus.py.
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

from tools.packs.extract_openstax_prealgebra import (
    CNXML,
    COLLXML,
    MDML,
    _flat,
    _local,
    kebab,
    module_to_text,
)

BOOK_URL = "https://openstax.org/books/introductory-statistics-2e"
LICENSE_ID = "CC-BY-4.0"
COLLECTION_FILE = "introductory-statistics-2e.collection.xml"
PREFACE_CLASSES = {"preface", "introduction"}
# Appendices that carry the book's formula tables (F and H).
INCLUDED_APPENDICES = {
    "m47891": ("Appendix F: Mathematical Phrases, Symbols, and Formulas",
               "f-mathematical-phrases-symbols-and-formulas"),
    "m47875": ("Appendix H: Tables", "h-tables"),
}


def slug(title: str) -> str:
    """The book's own page slug for a section title.

    OpenStax writes the possessive without an apostrophe ("womens-heights"),
    which the generic kebab() would turn into "women-s-heights".
    """
    cleaned = title.replace("\u2019", "").replace("'", "").replace("\u2018", "")
    return kebab(cleaned)


def make_resolver(all_titles, doc_root):
    """Resolve an empty CNXML cross-reference to the label the book prints."""
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


def parse_book(osbooks: Path):
    """Return (chapters, top_modules, module_info).

    chapters: [(chapter_title, [(module_id, class, title, section_no)])]
    top_modules: [(module_id, class, title)]
    module_info(mid): (class, title, content_element, document_root)
    """
    coll = ET.parse(osbooks / "collections" / COLLECTION_FILE)
    root = coll.getroot()

    def module_info(mid):
        path = osbooks / "modules" / mid / "index.cnxml"
        doc = ET.parse(path).getroot()
        cls = doc.get("class") or ""
        title_el = doc.find(f"{{{CNXML}}}title")
        title = _flat(title_el) if title_el is not None else mid
        content = doc.find(f"{{{CNXML}}}content")
        return cls, title, content, doc

    chapters, top_modules = [], []
    content_el = root.find(f"{{{COLLXML}}}content")
    for node in content_el:
        if _local(node) == "module":
            mid = node.get("document")
            cls, title, _, _ = module_info(mid)
            top_modules.append((mid, cls, title))
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
    return chapters, top_modules, module_info


def rows_for_book(osbooks: Path):
    chapters, top_modules, module_info = parse_book(osbooks)

    all_titles = {mid: title for mid, _, title in top_modules}
    for _, mods in chapters:
        for mid, _, title in mods:
            all_titles[mid] = title

    rows = []
    excluded = []
    used_slugs = set()

    def add_row(mid, cls, doc_title, source_slug, display_title):
        _, _, content, doc_root = module_info(mid)
        text = module_to_text(content, resolve_link=make_resolver(all_titles, doc_root))
        glossary = doc_root.find(f"{{{CNXML}}}glossary")
        if glossary is not None:
            terms = module_to_text(glossary, resolve_link=make_resolver(all_titles, doc_root))
            if terms:
                text += "\n\nGlossary\n" + terms
        if source_slug in used_slugs:
            raise ValueError(f"duplicate source slug: {source_slug}")
        used_slugs.add(source_slug)
        rows.append({
            "document_id": f"openstax-introstats-{mid}",
            "title": display_title,
            "source": f"{BOOK_URL}/pages/{source_slug}",
            "license": LICENSE_ID,
            "text": text,
        })

    for mid, cls, title in top_modules:
        if mid in INCLUDED_APPENDICES:
            display_title, appendix_slug = INCLUDED_APPENDICES[mid]
            add_row(mid, cls, title, appendix_slug, display_title)
        else:
            excluded.append((mid, cls, title, "outside the chapter subcollections"))

    for ch_no, (ch_title, mods) in enumerate(chapters, start=1):
        sec_no = 0
        for mid, cls, title in mods:
            if cls in PREFACE_CLASSES:
                excluded.append((mid, cls, title, f"chapter {ch_no} ({ch_title}) introduction"))
                continue
            sec_no += 1
            add_row(mid, cls, title, f"{ch_no}-{sec_no}-{slug(title)}",
                    f"{ch_title} \u2014 {title}")

    return rows, excluded


def main():
    ap = argparse.ArgumentParser()
    here = Path(__file__).resolve().parent
    ap.add_argument("--osbooks", type=Path, required=True,
                    help="checkout of openstax/osbooks-introductory-statistics-bundle")
    ap.add_argument("--out", type=Path, default=here / "introstats" / "corpus.jsonl")
    args = ap.parse_args()

    try:
        commit = subprocess.run(
            ["git", "-C", str(args.osbooks), "rev-parse", "HEAD"],
            capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        commit = "unknown"
    print(f"osbooks commit: {commit}", file=sys.stderr)

    rows, excluded = rows_for_book(args.osbooks)

    payload = "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(payload, encoding="utf-8")

    print(f"rows: {len(rows)}", file=sys.stderr)
    print("excluded modules:", file=sys.stderr)
    for mid, cls, title, where in excluded:
        print(f"  {mid} [{cls}] {title} ({where})", file=sys.stderr)
    sizes = [len(r["text"]) for r in rows]
    print(f"total text chars: {sum(sizes)}", file=sys.stderr)
    print(f"min row chars: {min(sizes)}  max row chars: {max(sizes)}", file=sys.stderr)
    print(f"corpus bytes: {len(payload.encode('utf-8'))}", file=sys.stderr)
    print(f"corpus sha256: {hashlib.sha256(payload.encode('utf-8')).hexdigest()}", file=sys.stderr)


if __name__ == "__main__":
    main()
