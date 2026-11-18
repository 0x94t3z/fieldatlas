#!/usr/bin/env python3
"""Introductory Statistics 2e pack for Field Atlas (poidh bounty, Base 1554).

The corpus is committed at statistics/corpus.jsonl: one passage per numbered
section of OpenStax *Introductory Statistics 2e*, in the book's own words,
formulas kept as LaTeX converted from the book's MathML, each section
followed by its glossary. It was extracted from
openstax/osbooks-introductory-statistics-bundle at commit
24e9884724b13baba2b8879d03103cccafc7e25d by
extract_openstax_statistics.py and is pinned by the SHA-256 below; the same
pin is asserted by test_statistics_corpus.py.

The pack is written by the Field Atlas pack tools (tools/packs/stream_pack.py
with the settings tools/packs/build_modular_library.py uses: porter
tokenizer, passages of up to 1,200 characters; here each starts at the start
of a line or a word), under the collection's own id so the download catalog
can name it. statistics/ATTRIBUTION.txt goes inside the archive, listed in
the manifest with its hash, and beside it: the app shows a collection's
credits from the file inside.

Licence note: the bounty text says "CC BY 4.0", but the book itself is
licensed CC BY-NC-SA 4.0 (details page and preface; the bundle LICENSE).
The pack carries the book's real licence. OpenStax moved its 2e books to
CC BY-NC-SA in March 2026; the Prealgebra pack froze at the last CC BY
revision, but no such revision exists for this book, so BY-NC-SA it is.

Usage:
  python3 build_statistics_pack.py [--out build/statistics-pack]

Offline only. Refuses to overwrite an existing output directory.
"""
from __future__ import annotations

import sys
from pathlib import Path as _Path

_REF = _Path(__file__).resolve().parents[1] / "fieldatlas-ref"
if str(_REF) not in sys.path:
    sys.path.insert(0, str(_REF))

import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

from packtool.chunking import Chunk, _boundary
from tools.packs.stream_pack import _canonical_json, _zip_info, build_pack_streaming
from tools.packs.fapack_convert import verify_pack

HERE = Path(__file__).resolve().parent
CORPUS = HERE / "statistics" / "corpus.jsonl"
ATTRIBUTION = HERE / "statistics" / "ATTRIBUTION.txt"
CORPUS_SHA256 = "07b41fbf3248385b8e6330def595b61c5dc2dc85a7cb01a020dabda9065d3f96"
PACK_ID = "openstax-maths-statistics"
TITLE = "Mathematics: Introductory Statistics"
LICENSE_ID = "CC-BY-NC-SA-4.0"
SOURCE_URLS = ["https://openstax.org/details/books/introductory-statistics-2e"]
COVERAGE_SUMMARY = (
    "The whole OpenStax Introductory Statistics 2e textbook: 13 chapters "
    "in 80 sections, with worked examples, exercises, glossaries and formulas."
)
EXAMPLE_QUESTIONS = [
    "What is the difference between a population and a sample?",
    "In a right-skewed distribution, is the mean greater than, less than, or equal to the median?",
    "What is the z-score formula?",
    "What is the formula for the error bound for a population mean (EBM) when the population standard deviation is known?",
    "What is a Type I error in hypothesis testing?",
    "For a chi-square test of independence with r rows and c columns, what are the degrees of freedom?",
]


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def add_attribution(artifacts, text: str) -> None:
    """Put ATTRIBUTION.txt inside the .fapack, list it with its hash in the
    manifest, and keep the manifest and SHA256SUMS beside the pack in step.
    (Same behaviour as tools/packs/build_practical_skills_pack.add_attribution;
    vendored here so this build does not import that module.)"""
    data = text.encode("utf-8")
    pack = artifacts.pack
    temporary = pack.with_name(pack.name + ".partial")
    with zipfile.ZipFile(pack) as source:
        manifest = json.loads(source.read("manifest.json"))
        if any(a["path"] == "ATTRIBUTION.txt" for a in manifest["artifacts"]):
            raise ValueError("Pack already carries ATTRIBUTION.txt")
        manifest["artifacts"].append({"bytes": len(data), "path": "ATTRIBUTION.txt", "sha256": _sha(data)})
        manifest_bytes = _canonical_json(manifest)
        with zipfile.ZipFile(temporary, "w", allowZip64=True) as target:
            target.writestr(_zip_info("manifest.json", len(manifest_bytes)), manifest_bytes)
            info = source.getinfo("content.sqlite")
            with source.open(info) as src, target.open(_zip_info("content.sqlite", info.file_size), "w", force_zip64=True) as dst:
                shutil.copyfileobj(src, dst, 1 << 20)
            target.writestr(_zip_info("ATTRIBUTION.txt", len(data)), data)
    temporary.replace(pack)
    artifacts.manifest.write_bytes(manifest_bytes)
    database_hash = next(a["sha256"] for a in manifest["artifacts"] if a["path"] == "content.sqlite")
    artifacts.checksums.write_text(
        f"{database_hash}  content.sqlite\n{_sha(manifest_bytes)}  manifest.json\n"
        f"{_sha(data)}  ATTRIBUTION.txt\n{_sha(pack.read_bytes())}  {pack.name}\n",
        encoding="ascii", newline="\n")


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
                                     coverage_summary=COVERAGE_SUMMARY,
                                     example_questions=EXAMPLE_QUESTIONS,
                                     coverage_level="broad",
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
    parser.add_argument("--out", type=Path, default=HERE / "statistics-pack")
    parser.add_argument("--version", default="0.1.0")
    args = parser.parse_args()
    print(json.dumps(build(args.corpus, args.out, args.version), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
