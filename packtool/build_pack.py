from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import unicodedata
import zipfile

from .chunking import chunk_documents
from .schema import read_documents


FIXED_TIME = (1980, 1, 1, 0, 0, 0)
IDENTIFIER = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z")


@dataclass(frozen=True)
class BuildArtifacts:
    database: Path
    manifest: Path
    checksums: Path
    pack: Path


def _digest(path: Path) -> tuple[int, str]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as source:
        while block := source.read(1024 * 1024):
            size += len(block)
            digest.update(block)
    return size, digest.hexdigest()


def _canonical_json(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def _zip_info(name: str, size: int) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    info.file_size = size
    return info


def _build_database(path: Path, chunks) -> None:
    database = sqlite3.connect(path)
    try:
        database.execute("PRAGMA page_size=4096")
        database.execute("PRAGMA journal_mode=OFF")
        database.execute("PRAGMA synchronous=OFF")
        database.execute("PRAGMA temp_store=MEMORY")
        database.execute("PRAGMA user_version=1")
        database.execute(
            "CREATE VIRTUAL TABLE chunks_fts USING fts5("
            "chunk_id UNINDEXED, document_id UNINDEXED, title, source UNINDEXED, text, "
            "tokenize='unicode61')"
        )
        database.executemany(
            "INSERT INTO chunks_fts(chunk_id, document_id, title, source, text) VALUES (?, ?, ?, ?, ?)",
            [(chunk.chunk_id, chunk.document_id, chunk.title, chunk.source, chunk.text) for chunk in chunks],
        )
        database.commit()
        database.execute("VACUUM")
        if database.execute("PRAGMA quick_check").fetchone() != ("ok",):
            raise RuntimeError("SQLite quick_check failed")
    finally:
        database.close()


def build_pack(
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

    documents = read_documents(input_path)
    chunks = chunk_documents(documents)
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=f".{output_dir.name}.", dir=output_dir.parent))
    try:
        database_path = temporary / "content.sqlite"
        _build_database(database_path, chunks)
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


def main() -> None:
    parser = argparse.ArgumentParser(description="Build a deterministic FieldAtlas knowledge pack")
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--id", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--license", required=True)
    parser.add_argument("--source-url", action="append", required=True)
    parser.add_argument("--coverage-summary")
    parser.add_argument("--example-question", action="append")
    parser.add_argument("--coverage-level", choices=("demo", "focused", "broad"))
    args = parser.parse_args()
    artifacts = build_pack(
        args.input, args.output, args.id, args.version, args.title, args.license, args.source_url,
        coverage_summary=args.coverage_summary,
        example_questions=args.example_question,
        coverage_level=args.coverage_level,
    )
    print(artifacts.pack)


if __name__ == "__main__":
    main()
