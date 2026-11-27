#!/usr/bin/env python3
"""Introductory Statistics 2e pack for the `openstax-introductory-statistics` collection.

The corpus is committed at tools/packs/introductory_statistics/corpus.jsonl: one
passage per module of OpenStax *Introductory Statistics 2e* (each chapter's
introduction, every numbered section with its examples, exercises, reviews and
solutions, each followed by its glossary, and the two appendices of formula
tables), in the book's own words, formulas kept as LaTeX converted from the
book's MathML. It was extracted from openstax/osbooks-introductory-statistics-bundle
at commit 50ec91749080bb040b57bb40c34337cacc994cba by
tools/packs/extract_openstax_introductory_statistics.py and is pinned by the
SHA-256 below; the same pin is asserted by
tools/tests/test_introductory_statistics_corpus.py.

The pack is built exactly like the Prealgebra one (tools/packs/build_prealgebra_pack.py,
whose chunker it reuses): tools/packs/stream_pack.py, porter tokenizer, passages
of up to 1,200 characters, each starting at the start of a line or a word, under
the collection's own id. tools/packs/introductory_statistics/ATTRIBUTION.txt goes
inside the archive and beside it.

Usage:
  python3 tools/packs/build_introductory_statistics_pack.py [--out build/introductory-statistics-pack]

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

from tools.packs.build_practical_skills_pack import add_attribution
from tools.packs.build_prealgebra_pack import line_chunks
from tools.packs.fapack_convert import verify_pack
from tools.packs.stream_pack import build_pack_streaming

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
CORPUS = HERE / "introductory_statistics" / "corpus.jsonl"
ATTRIBUTION = HERE / "introductory_statistics" / "ATTRIBUTION.txt"
CORPUS_SHA256 = "0e2585510ea0ae1bfac2ff389a16fd9b60d8018d7f4a89bdacd11ae2c9e75fc2"
PACK_ID = "openstax-introductory-statistics"
TITLE = "Mathematics: Introductory statistics"
LICENSE_ID = "CC-BY-4.0"
SOURCE_URLS = ["https://openstax.org/details/books/introductory-statistics-2e"]

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
    parser.add_argument("--out", type=Path, default=ROOT / "build" / "introductory-statistics-pack")
    parser.add_argument("--version", default="0.1.0")
    args = parser.parse_args()
    print(json.dumps(build(args.corpus, args.out, args.version), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
