#!/usr/bin/env python3
"""Streaming drop-in for packtool.build_pack with identical, deterministic output.

packtool.read_documents keeps every Document (and then every Chunk) in memory, which
needs ~2x the corpus size in RAM. For the 14 GB stackexchange and 19 GB wikipedia
spools that is more than this machine has. This module produces byte-identical
.fapack files (verified against packtool on the smaller corpora) by:

  1. validating each jsonl row into a bounded-cache, disk-backed id/offset index;
  2. reading the index in utf-8 byte order (the same key as packtool);
  3. re-reading rows in sorted order, chunking per document, and inserting in
     batches into the same FTS5 schema.

Uses only the packtool helpers that define the on-disk format, so manifest and zip
bytes match the official builder exactly.

An explicit chunker callback supports independently evaluated development packs.
The default remains packtool.chunk_document; callers changing the callback must
record their policy and validate its content boundaries separately.
"""

from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))


import hashlib
import json
import os
import shutil
import sqlite3
import tempfile
import zipfile
from contextlib import closing
from pathlib import Path

from packtool.build_pack import (  # noqa: F401  (re-export BuildArtifacts)
    IDENTIFIER,
    BuildArtifacts,
    _canonical_json,
    _digest,
    _zip_info,
)
from packtool.chunking import chunk_document
from packtool.schema import DOCUMENT_ID, Document, DocumentError

_BATCH = 2_000
MAX_SOURCE_RECORD_BYTES = 32_000_000


def _parse_row(raw: bytes, line_number: int) -> Document:
    from packtool.schema import _text  # noqa: PLC2701  (private by necessity, format-defining)

    try:
        def strict_object(pairs):
            value = {}
            for key, item in pairs:
                if key in value:
                    raise DocumentError(f"line {line_number}: duplicate JSON field: {key}")
                value[key] = item
            return value

        row = json.loads(raw, object_pairs_hook=strict_object)
    except json.JSONDecodeError as error:
        raise DocumentError(f"line {line_number}: invalid JSON: {error.msg}") from error
    if not isinstance(row, dict):
        raise DocumentError(f"line {line_number}: row must be an object")
    fields = {"document_id", "title", "source", "license", "text"}
    if set(row) != fields:
        missing = sorted(fields - set(row))
        unknown = sorted(set(row) - fields)
        raise DocumentError(f"line {line_number}: fields mismatch; missing={missing}, unknown={unknown}")
    document_id = _text(row["document_id"], "document_id", line_number)
    if not DOCUMENT_ID.fullmatch(document_id):
        raise DocumentError(f"line {line_number}: invalid document_id")
    return Document(
        document_id=document_id,
        title=_text(row["title"], "title", line_number),
        source=_text(row["source"], "source", line_number),
        license=_text(row["license"], "license", line_number),
        text=_text(row["text"], "text", line_number),
    )


def _index_documents(path: Path, index_path: Path, on_batch=None) -> None:
    """Validate globally unique ids without an O(document-count) Python set/list.

    BLOB ordering matches packtool's UTF-8 sort exactly. This scratch database is
    never distributed and cannot change the final content database/ZIP bytes.
    """
    if on_batch is not None:
        on_batch()
    with closing(sqlite3.connect(index_path)) as index:
        index.execute("PRAGMA journal_mode=OFF")
        index.execute("PRAGMA synchronous=OFF")
        index.execute("PRAGMA cache_size=-8192")
        index.execute("PRAGMA temp_store=FILE")
        index.execute("CREATE TABLE offsets (id BLOB PRIMARY KEY, offset INTEGER NOT NULL, line INTEGER NOT NULL) WITHOUT ROWID")
        with path.open("rb") as fh:
            line_number = 0
            while True:
                offset = fh.tell()
                raw = fh.readline(MAX_SOURCE_RECORD_BYTES + 1)
                if not raw:
                    break
                line_number += 1
                if on_batch is not None and line_number > 1 and (line_number - 1) % _BATCH == 0:
                    on_batch()
                if len(raw) > MAX_SOURCE_RECORD_BYTES:
                    raise DocumentError(f"line {line_number}: document record exceeds 32 MB")
                if not raw.strip():
                    raise DocumentError(f"line {line_number}: blank lines are not allowed")
                document = _parse_row(raw, line_number)
                try:
                    index.execute("INSERT INTO offsets VALUES (?, ?, ?)",
                                  (document.document_id.encode("utf-8"), offset, line_number))
                except sqlite3.IntegrityError as error:
                    raise DocumentError(f"line {line_number}: duplicate document_id: {document.document_id}") from error
                if line_number % _BATCH == 0:
                    index.commit()
        if not line_number:
            raise DocumentError("input contains no documents")
        index.commit()


def _rows_in_order(path: Path, index_path: Path):
    with closing(sqlite3.connect(f"{index_path.resolve().as_uri()}?mode=ro", uri=True)) as index, path.open("rb") as fh:
        index.execute("PRAGMA cache_size=-8192")
        for offset, line_number in index.execute("SELECT offset, line FROM offsets ORDER BY id"):
            fh.seek(offset)
            raw = fh.readline(MAX_SOURCE_RECORD_BYTES + 1)
            if len(raw) > MAX_SOURCE_RECORD_BYTES:
                raise DocumentError(f"line {line_number}: document record exceeds 32 MB")
            yield _parse_row(raw, line_number)


def _iter_chunks(index, path: Path, chunker=chunk_document):
    with closing(_rows_in_order(path, index)) as documents:
        for document in documents:
            for chunk in chunker(document):
                yield (chunk.chunk_id, chunk.document_id, chunk.title, chunk.source, chunk.text)


ALLOWED_TOKENIZERS = {"unicode61", "porter unicode61"}


def _build_database_streaming(path: Path, rows, fts_tokenizer: str = "unicode61", extend=None) -> None:
    if fts_tokenizer not in ALLOWED_TOKENIZERS:
        raise ValueError(f"unsupported FTS5 tokenizer: {fts_tokenizer!r}")
    database = sqlite3.connect(path)
    try:
        database.execute("PRAGMA page_size=4096")
        database.execute("PRAGMA journal_mode=OFF")
        database.execute("PRAGMA synchronous=OFF")
        database.execute("PRAGMA temp_store=FILE")
        database.execute("PRAGMA cache_size=-8192")
        database.execute("PRAGMA user_version=1")
        database.execute(
            "CREATE VIRTUAL TABLE chunks_fts USING fts5("
            "chunk_id UNINDEXED, document_id UNINDEXED, title, source UNINDEXED, text, "
            f"tokenize='{fts_tokenizer}')"
        )
        batch: list = []
        for row in rows:
            batch.append(row)
            if len(batch) >= _BATCH:
                database.executemany(
                    "INSERT INTO chunks_fts(chunk_id, document_id, title, source, text) VALUES (?, ?, ?, ?, ?)",
                    batch,
                )
                batch.clear()
        if batch:
            database.executemany(
                "INSERT INTO chunks_fts(chunk_id, document_id, title, source, text) VALUES (?, ?, ?, ?, ?)",
                batch,
            )
        database.commit()
        if extend is not None:
            # Auxiliary tables (rowid-aligned with chunks_fts) are added before VACUUM so
            # the file layout, and therefore the pack hash, stays deterministic.
            extend(database)
            database.commit()
        database.execute("VACUUM")
        if database.execute("PRAGMA quick_check").fetchone() != ("ok",):
            raise RuntimeError("SQLite quick_check failed")
    finally:
        try:
            close = getattr(rows, "close", None)
            if close is not None:
                close()
        finally:
            database.close()


def build_pack_streaming(
    input_path: Path,
    output_dir: Path,
    pack_id: str,
    version: str,
    title: str,
    license_id: str,
    source_urls: list[str],
    coverage_summary: str | None = None,
    example_questions: list[str] | None = None,
    coverage_level: str | None = None,
    fts_tokenizer: str = "unicode61",
    chunker=chunk_document,
    extend_database=None,
) -> BuildArtifacts:
    for name, value in (("pack_id", pack_id), ("version", version), ("title", title), ("license", license_id)):
        if not value.strip():
            raise ValueError(f"{name} must be non-blank")
    for name, value in (("pack_id", pack_id), ("version", version)):
        if not IDENTIFIER.fullmatch(value) or value in (".", ".."):
            raise ValueError(f"{name} must be a safe pack identifier")
    if not source_urls or any(not url.strip() for url in source_urls):
        raise ValueError("source_urls must contain non-blank values")
    if output_dir.exists():
        raise ValueError(f"output directory already exists: {output_dir}")

    discovery_values = (coverage_summary, example_questions, coverage_level)
    if any(value is not None for value in discovery_values):
        if any(value is None for value in discovery_values):
            raise ValueError("discovery metadata must be supplied together")
        import unicodedata

        coverage_summary = unicodedata.normalize("NFKC", coverage_summary).strip()
        example_questions = [unicodedata.normalize("NFKC", item).strip() for item in example_questions]
        if not 1 <= len(coverage_summary) <= 160:
            raise ValueError("coverage_summary must contain 1 to 160 characters")
        if len(example_questions) > 6:
            raise ValueError("example_questions must contain at most 6 items")
        if any(not item or len(item) > 140 for item in example_questions):
            raise ValueError("example questions must contain 1 to 140 characters")
        if len(set(example_questions)) != len(example_questions):
            raise ValueError("example_questions must be unique")
        if coverage_level not in {"demo", "focused", "broad"}:
            raise ValueError("coverage_level must be demo, focused, or broad")

    output_dir.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=f".{output_dir.name}.", dir=output_dir.parent))
    try:
        index = temporary / ".document-index.sqlite"
        _index_documents(input_path, index)
        database_path = temporary / "content.sqlite"
        _build_database_streaming(database_path, _iter_chunks(index, input_path, chunker), fts_tokenizer,
                                  extend_database)
        index.unlink()
        database_size, database_hash = _digest(database_path)
        manifest = {
            "artifacts": [{"bytes": database_size, "path": "content.sqlite", "sha256": database_hash}],
            "id": pack_id,
            "license": license_id,
            "schemaVersion": 1,
            "sourceUrls": source_urls,
            "title": title,
            "type": "KNOWLEDGE",
            "version": version,
        }
        if coverage_summary is not None:
            manifest["discovery"] = {
                "coverageLevel": coverage_level,
                "coverageSummary": coverage_summary,
                "exampleQuestions": example_questions,
            }
        manifest_bytes = _canonical_json(manifest)
        manifest_path = temporary / "manifest.json"
        manifest_path.write_bytes(manifest_bytes)
        manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()
        pack_path = temporary / f"{pack_id}-{version}.fapack"
        with zipfile.ZipFile(pack_path, "w", allowZip64=True) as archive:
            archive.writestr(_zip_info("manifest.json", len(manifest_bytes)), manifest_bytes)
            with archive.open(_zip_info("content.sqlite", database_size), "w", force_zip64=True) as destination:
                with database_path.open("rb") as source:
                    shutil.copyfileobj(source, destination, 1024 * 1024)
        pack_size, pack_hash = _digest(pack_path)
        checksums_path = temporary / "SHA256SUMS"
        checksums_path.write_text(
            f"{database_hash}  content.sqlite\n"
            f"{manifest_hash}  manifest.json\n"
            f"{pack_hash}  {pack_path.name}\n",
            encoding="ascii",
            newline="\n",
        )
        os.replace(temporary, output_dir)
    except Exception:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return BuildArtifacts(
        database=output_dir / "content.sqlite",
        manifest=output_dir / "manifest.json",
        checksums=output_dir / "SHA256SUMS",
        pack=output_dir / f"{pack_id}-{version}.fapack",
    )
