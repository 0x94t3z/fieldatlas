#!/usr/bin/env python3
"""fapack_convert — turn a JSONL document spool into a Field Atlas KNOWLEDGE fapack.

This is the data-to-pack converter: everything the app needs to install a searchable
knowledge pack, built from one deterministic input file.

INPUT FORMAT
------------
A UTF-8 JSON-lines file ("spool"): one JSON object per line, one line per document,
exactly five string fields (the packtool schema enforces this):

    {"document_id": "wiki-123", "title": "Article title", "source": "en.wikipedia.org",
     "license": "CC-BY-SA-4.0", "text": "Full body text ..."}

  * document_id  unique; [a-z0-9][a-z0-9._-]{0,127}
  * title/source/license  plain strings (cleaned, length-capped by the schema)
  * text  the searchable body; long texts are split into chunks automatically by the
    shared chunker (paragraph-first, windowed), never whole-document blobs.

OUTPUT
------
<fapacks>/<pack-id>/
    <pack-id>-<version>.fapack   the installable zip: manifest.json + content.sqlite
    content.sqlite               a build leftover, identical to the one inside the zip
    manifest.json                ditto
    SHA256SUMS                   database, manifest and pack hashes

The .fapack zip is what the phone downloads. Its manifest (schemaVersion 1, type
KNOWLEDGE, artifacts with byte counts + sha256, optional discovery block) is what
AssetInstaller validates before accepting the pack: every artifact is re-hashed on
device, so the hashes baked in here must match the final bytes exactly.

content.sqlite carries a single FTS5 table:

    CREATE VIRTUAL TABLE chunks_fts USING fts5(
        chunk_id UNINDEXED, document_id UNINDEXED, title, source UNINDEXED, text,
        tokenize='porter unicode61')

Use tokenizer "porter unicode61" for prose corpora: it stems morphology ("caresses"
finds "caress"), which the app's keyword retriever depends on. Rowid order = insertion
order = document_id byte order, which the app relies on for stable chunk addressing,
and bm25() is stored with NEGATIVE = stronger — matching KnowledgeDatabase.getDouble().

DETERMINISM
-----------
Same spool bytes + same version => byte-identical .fapack (fixed zip timestamps,
canonical JSON manifest). This is what makes packs auditable.

IMPLEMENTATION
--------------
The byte-format logic is NOT reimplemented here; it is imported from the same two
modules that produced every shipping pack, so packs from this CLI are provably
compatible with the installed ones:
    packtool.{schema,chunking}     document validation + chunking rules
    stream_pack.build_pack_streaming   constant-memory sqlite/zip writer
      (/home/v/fieldatlas/tools/stream_pack.py, byte-identical to packtool.build_pack)

USAGE
-----
    python3 tools/fapack_convert.py docs.jsonl \
        --id world-knowledge-mycorpus --version 1.0.0 \
        --title "My Corpus (offline)" --license CC-BY-4.0 \
        --source-url https://example.org/data \
        --coverage-summary "What the pack holds, <=160 chars." \
        --coverage-level broad \
        --example-question "A question this pack can answer?" \
        [--tokenizer "porter unicode61"] [--out-dir /home/v/fieldatlas/fapacks] [--force]

Then serve the .fapack from fapacks/ (lan_server.py) and install it on the phone from
the Library screen. To regenerate a pack from raw sources, see wiki_mini_build.py for
a worked end-to-end example (it spools JSONL and calls build_pack_streaming directly).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sqlite3
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent                 # fieldatlas repo (packtool/)
WORK = REPO.parent                                             # working dir: fapacks/
PACKTOOL_REPO = REPO                                          # contains the packtool package
TOOLS_DIR = REPO / "tools"                                    # contains stream_pack.py
DEFAULT_OUT = WORK / "fapacks"

sys.path.insert(0, str(PACKTOOL_REPO))
sys.path.insert(0, str(TOOLS_DIR))

from stream_pack import build_pack_streaming                # noqa: E402
from packtool.schema import DocumentError                   # noqa: E402

ALLOWED_LEVELS = ("demo", "focused", "broad")


def verify_pack(pack: Path) -> dict:
    """Re-run the phone's acceptance checks on a finished pack. Raises on any mismatch.

    Checks: manifest parses + declares its artifacts, every artifact's bytes and sha256
    match the zip content, the database opens, passes quick_check, contains chunks, and
    an FTS MATCH works. Returns a small summary dict for logging.
    """
    with zipfile.ZipFile(pack) as archive:
        names = archive.namelist()
        assert "manifest.json" in names and "content.sqlite" in names, f"{pack}: missing entries {names}"
        manifest = json.loads(archive.read("manifest.json"))
        assert manifest["type"] == "KNOWLEDGE" and manifest["schemaVersion"] == 1, pack
        for artifact in manifest["artifacts"]:
            blob = archive.read(artifact["path"])
            assert len(blob) == artifact["bytes"], f"{pack}:{artifact['path']} byte count"
            assert hashlib.sha256(blob).hexdigest() == artifact["sha256"], f"{pack}:{artifact['path']} sha256"
        sqlite_bytes = archive.read("content.sqlite")
    db_path = Path("/tmp") / f"verify-{pack.name}.sqlite"
    db_path.write_bytes(sqlite_bytes)
    try:
        db = sqlite3.connect(db_path)
        try:
            assert db.execute("PRAGMA quick_check").fetchone()[0] == "ok", f"{pack}: quick_check"
            chunks = db.execute("SELECT count(*) FROM chunks_fts").fetchone()[0]
            assert chunks > 0, f"{pack}: no chunks"
            sample = db.execute(
                "SELECT title, bm25(chunks_fts) FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts) LIMIT 1",
                (f'"{manifest["title"].split()[0]}"',),
            ).fetchall()
        finally:
            db.close()
    finally:
        db_path.unlink(missing_ok=True)
    return {
        "pack": str(pack), "bytes": pack.stat().st_size, "chunks": chunks,
        "docs": None, "title": manifest["title"], "sample_hit": sample[0][0] if sample else None,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__.splitlines()[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("input", type=Path, help="JSONL spool (document_id/title/source/license/text)")
    parser.add_argument("--id", required=True, help="pack id, e.g. world-knowledge-mycorpus")
    parser.add_argument("--version", default="1.0.0")
    parser.add_argument("--title", required=True)
    parser.add_argument("--license", required=True, dest="license_id")
    parser.add_argument("--source-url", action="append", required=True, dest="source_urls")
    parser.add_argument("--coverage-summary", default=None, help="<=160 chars, shown in the Library")
    parser.add_argument("--example-question", action="append", default=[], dest="questions")
    parser.add_argument("--coverage-level", default=None, choices=ALLOWED_LEVELS)
    parser.add_argument("--tokenizer", default="porter unicode61",
                        choices=("unicode61", "porter unicode61"),
                        help="FTS5 tokenizer; porter stems morphology — keep it for prose")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--force", action="store_true", help="replace an existing output directory")
    args = parser.parse_args(argv)

    if not args.input.is_file():
        parser.error(f"input not found: {args.input}")
    output_dir = args.out_dir / args.id
    if output_dir.exists():
        if not args.force:
            parser.error(f"{output_dir} exists (use --force to replace)")
        shutil.rmtree(output_dir)

    artifacts = build_pack_streaming(
        input_path=args.input,
        output_dir=output_dir,
        pack_id=args.id,
        version=args.version,
        title=args.title,
        license_id=args.license_id,
        source_urls=args.source_urls,
        coverage_summary=args.coverage_summary,
        example_questions=args.questions or None,
        coverage_level=args.coverage_level,
        fts_tokenizer=args.tokenizer,
    )
    summary = verify_pack(artifacts.pack)
    print(f"built {artifacts.pack} ({summary['bytes'] / 1e6:.1f} MB, {summary['chunks']} chunks)")
    print(f"verified: hashes, quick_check, FTS sample hit: {summary['sample_hit']!r}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except DocumentError as error:
        sys.exit(f"invalid spool: {error}")
