#!/usr/bin/env python3
"""Introductory Statistics 2e pack, the sibling of the Prealgebra pack.

The corpus is committed at tools/packs/introstats/corpus.jsonl: one passage
per numbered section of OpenStax *Introductory Statistics 2e*, in the book's own words,
formulas kept as LaTeX converted from the book's MathML, each section
followed by its glossary, plus the two appendices that hold the formula
tables (Appendix F: Mathematical Phrases, Symbols, and Formulas; Appendix H:
Tables). It was extracted from
openstax/osbooks-introductory-statistics-bundle at commit
50ec91749080bb040b57bb40c34337cacc994cba by
tools/packs/extract_openstax_introstats.py and is pinned by the SHA-256
below; the same pin is asserted by tools/tests/test_introstats_corpus.py.

The pack is written by tools/packs/stream_pack.py with the settings
tools/packs/build_modular_library.py uses (porter tokenizer, passages of
up to 1,200 characters; here each starts at the start of a line or a word),
under its own id so the download catalog can name it beside the Prealgebra
pack, and so this build never disturbs the hosted Prealgebra pack: issue
#24 proposes one `openstax-maths-stats` collection holding all three books,
and merging this book into that collection's next version is a maintainer
decision, not something a first build should do by replacing the hosted
pack's identity. tools/packs/introstats/ATTRIBUTION.txt
goes inside the archive, listed in the manifest with its hash, and beside
it: the app shows a collection's credits from the file inside.

Usage:
  python3 tools/packs/build_introstats_pack.py [--out build/introstats-pack]

Offline only. Refuses to overwrite an existing output directory.
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
import shutil
from pathlib import Path

from packtool.chunking import Chunk, _boundary
from tools.packs.build_practical_skills_pack import add_attribution
from tools.packs.fapack_convert import verify_pack
from tools.packs.stream_pack import build_pack_streaming

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
CORPUS = HERE / "introstats" / "corpus.jsonl"
ATTRIBUTION = HERE / "introstats" / "ATTRIBUTION.txt"
CORPUS_SHA256 = "bd073cdc920228a63f5d5c9b5ab31108136516b161ac1de5293275ed43f7f755"
PACK_ID = "openstax-maths-introstats"
TITLE = "Mathematics: Introductory Statistics"
LICENSE_ID = "CC-BY-4.0"
SOURCE_URLS = ["https://openstax.org/details/books/introductory-statistics-2e"]


def line_chunks(document, max_chars: int = 1200, overlap_chars: int = 150) -> list[Chunk]:
    """packtool's chunk_document, with one change: a passage starts at the start of a line, or of
    a word inside a long paragraph. The plain overlap starts a fixed number of characters back,
    which here is often the middle of a number or a formula ("57.$" for "$157.$")."""
    text, chunks, start = document.text, [], 0
    while start < len(text):
        target = min(start + max_chars, len(text))
        end = target if target == len(text) else _boundary(text, start, target, overlap_chars)
        chunks.append(Chunk(chunk_id=f"{document.document_id}:{len(chunks):04d}", document_id=document.document_id,
                            title=document.title, source=document.source, text=text[start:end].strip("\n")))
        if end == len(text):
            break
        back = end - overlap_chars
        line = text.find("\n", back, end)
        word = text.find(" ", back, end)
        start = line + 1 if line >= 0 else word + 1 if word >= 0 else back
    return [chunk for chunk in chunks if chunk.text]


def build(corpus: Path, out: Path, version: str) -> dict:
    found = hashlib.sha256(corpus.read_bytes()).hexdigest()
    if found != CORPUS_SHA256:
        raise ValueError(f"Corpus does not match its pin: {found}")
    artifacts = build_pack_streaming(corpus, out, PACK_ID, version, TITLE, LICENSE_ID, SOURCE_URLS,
                                     fts_tokenizer="porter unicode61", chunker=line_chunks)
    add_attribution(artifacts, ATTRIBUTION.read_text(encoding="utf-8"))
    shutil.copyfile(ATTRIBUTION, artifacts.pack.parent / "ATTRIBUTION.txt")
    summary = verify_pack(artifacts.pack)
    data = artifacts.pack.read_bytes()
    manifest = json.loads(artifacts.manifest.read_bytes())
    return {"pack": artifacts.pack.name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
            "installedBytes": sum(a["bytes"] for a in manifest["artifacts"]),
            "corpusSha256": found, "verified": summary}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--corpus", type=Path, default=CORPUS)
    parser.add_argument("--out", type=Path, default=ROOT / "build" / "introstats-pack")
    parser.add_argument("--version", default="0.1.0")
    args = parser.parse_args()
    print(json.dumps(build(args.corpus, args.out, args.version), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
