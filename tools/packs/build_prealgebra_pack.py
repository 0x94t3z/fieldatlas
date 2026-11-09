#!/usr/bin/env python3
"""Prealgebra 2e pack for the `openstax-maths-stats` collection (issue #26).

The corpus is committed at tools/packs/prealgebra/corpus.jsonl: one passage
per numbered section of OpenStax *Prealgebra 2e*, in the book's own words,
formulas kept as LaTeX converted from the book's MathML. It was extracted
from openstax/osbooks-prealgebra-bundle at commit
7fcdabf0364f3b308c0190557dfc961b579b374c by
tools/packs/extract_openstax_prealgebra.py and is pinned by the SHA-256
below; the same pin is asserted by tools/tests/test_prealgebra_corpus.py.

This wrapper builds the single .fapack module with
tools/packs/build_modular_library.py and copies
tools/packs/prealgebra/ATTRIBUTION.txt beside the built archive. (The
archive itself holds only its manifest and content database; attribution
also travels in the manifest's source URLs and licence and in every corpus
row's source and licence fields.)

Usage:
  python3 tools/packs/build_prealgebra_pack.py [--out build/prealgebra-pack]

Offline only. Refuses to overwrite an existing output directory.
"""
from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
import json
import shutil
from pathlib import Path

from tools.packs.build_modular_library import build_library

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
CORPUS = HERE / "prealgebra" / "corpus.jsonl"
ATTRIBUTION = HERE / "prealgebra" / "ATTRIBUTION.txt"
CORPUS_SHA256 = "4ba6b16bd39c5b07eb2a631916026ae01a3e5cf74273825cdcf12f2d72bb3e86"
PACK_ID = "openstax-maths-stats"
TITLE = "Mathematics & statistics from OpenStax"
LICENSE_ID = "CC-BY-4.0"
SOURCE_URLS = ["https://openstax.org/details/books/prealgebra-2e"]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--corpus", type=Path, default=CORPUS)
    parser.add_argument("--out", type=Path, default=ROOT / "build" / "prealgebra-pack")
    parser.add_argument("--version", default="0.1.0")
    args = parser.parse_args()

    report = build_library(args.corpus, args.out, CORPUS_SHA256, PACK_ID,
                           args.version, TITLE, LICENSE_ID, SOURCE_URLS)
    for module in report["modules"]:
        module_dir = args.out / Path(module["archive"]).parent
        shutil.copyfile(ATTRIBUTION, module_dir / "ATTRIBUTION.txt")
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
