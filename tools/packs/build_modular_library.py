#!/usr/bin/env python3
"""Build independently importable, pre-indexed modules from a checksum-pinned JSONL corpus.

Offline only. Keeps original document IDs, titles, passages, licences and source URLs.
Uses a disk-backed sorted index and only one temporary module spool at a time.
The measured report is NOT an app catalog and never makes unhosted files downloadable.
No corpus duplication/padding is used to reach a size target.
"""
from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))


import argparse
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import sys
import tempfile
import zipfile

from tools.packs.stream_pack import MAX_SOURCE_RECORD_BYTES, _index_documents, _parse_row, build_pack_streaming
from packtool.build_pack import IDENTIFIER, _digest
from packtool.schema import DocumentError

MAX_INSTALLED_LIBRARY_BYTES = 35_000_000_000
MAX_MODULE_ARCHIVE_BYTES = 1_500_000_000
BUILD_FREE_RESERVE_BYTES = 1_000_000_000


def require_build_space(directory: Path, incoming_bytes: int) -> None:
    """Conservative build admission, not a measurement of Android install peaks."""
    if shutil.disk_usage(directory).free < incoming_bytes + BUILD_FREE_RESERVE_BYTES:
        raise ValueError("Insufficient build disk space; keep 1 GB free and use a larger build volume")


def verified_archive(path: Path) -> dict:
    """Verify every stored payload without allocating another extracted database."""
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        if [item.filename for item in entries] != ["manifest.json", "content.sqlite"] or any(
                item.compress_type != zipfile.ZIP_STORED for item in entries):
            raise ValueError("Unexpected archive layout")
        manifest = json.loads(archive.read("manifest.json"))
        artifact = manifest["artifacts"][0]
        digest, size = hashlib.sha256(), 0
        with archive.open("content.sqlite") as payload:
            for block in iter(lambda: payload.read(1024 * 1024), b""):
                size += len(block)
                digest.update(block)
        if size != artifact["bytes"] or digest.hexdigest() != artifact["sha256"]:
            raise ValueError("Payload size/checksum mismatch")
    return manifest


def build_library(input_path: Path, output_dir: Path, expected_sha256: str,
                  pack_id: str, version: str, title: str, license_id: str,
                  source_urls: list[str], module_text_bytes: int = 250_000_000,
                  max_archive_bytes: int = MAX_MODULE_ARCHIVE_BYTES,
                  max_installed_bytes: int = MAX_INSTALLED_LIBRARY_BYTES) -> dict:
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError("A lowercase SHA-256 input pin is required")
    if not IDENTIFIER.fullmatch(pack_id) or pack_id in (".", "..") or len(pack_id) > 118:
        raise ValueError("Invalid module id prefix")
    if not IDENTIFIER.fullmatch(version) or version in (".", ".."):
        raise ValueError("Invalid version")
    if not title.strip() or not license_id.strip() or not source_urls or any(not u.strip() for u in source_urls):
        raise ValueError("Title, licence and source attribution are required")
    if not 1 <= module_text_bytes <= max_archive_bytes <= MAX_MODULE_ARCHIVE_BYTES:
        raise ValueError("Invalid module size limit")
    if not 1 <= max_installed_bytes <= MAX_INSTALLED_LIBRARY_BYTES:
        raise ValueError("Installed library limit must be between 1 byte and 35 decimal GB")
    if output_dir.exists():
        raise ValueError("Output already exists; refusing to replace artifacts")
    input_size, input_hash = _digest(input_path)
    if input_hash != expected_sha256:
        raise ValueError("Input snapshot checksum mismatch")
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    # The corpus is retained. Offsets, IDs and temporary SQLite pages need their
    # own space; don't begin a large build on a nearly full source volume.
    require_build_space(output_dir.parent, input_size * 2)
    # Atomic final directory: a failed module cannot leave a library advertised as complete.
    working = Path(tempfile.mkdtemp(prefix=f".{output_dir.name}.", dir=output_dir.parent))
    try:
        index_path = working / ".offsets.sqlite"
        _index_documents(input_path, index_path)
        modules: list[dict] = []
        total_installed = 0
        section_input = None
        with closing(sqlite3.connect(f"{index_path.resolve().as_uri()}?mode=ro", uri=True)) as index, input_path.open("rb") as source:
            index.execute("PRAGMA cache_size=-8192")
            offsets = iter(index.execute("SELECT offset, line FROM offsets ORDER BY id"))
            pending = next(offsets, None)
            while pending is not None:
                if len(modules) >= 500:
                    raise ValueError("Library would exceed the app's 500-entry catalog limit")
                module_id = f"{pack_id}-{len(modules) + 1:04d}"
                spool = working / ".module.jsonl"
                text_bytes, documents, articles = 0, 0, 0
                first_id = last_id = None
                last_article = None
                with spool.open("wb") as destination:
                    while pending is not None:
                        offset, line = pending
                        source.seek(offset)
                        # Keep the reread bounded too: a changed source must not bypass
                        # the indexing pass's per-record allocation limit.
                        raw = source.readline(MAX_SOURCE_RECORD_BYTES + 1)
                        if len(raw) > MAX_SOURCE_RECORD_BYTES:
                            raise DocumentError(f"line {line}: document record exceeds 32 MB")
                        document = _parse_row(raw, line)
                        if document.license != license_id:
                            raise ValueError("Input document licence differs from the declared library licence")
                        is_section = document.document_id.startswith("enw-")
                        if section_input is None:
                            section_input = is_section
                        elif section_input != is_section:
                            raise ValueError("Article-section and ordinary documents require separate libraries")
                        article = None
                        if is_section:
                            if not re.fullmatch(r"enw-[0-9]+-[0-9]{4,}", document.document_id):
                                raise ValueError("Invalid article-section document identifier")
                            article = document.document_id.rsplit("-", 1)[0]
                            if article != last_article and not document.document_id.endswith("-0000"):
                                raise ValueError("Every article must start with its original 0000 lead section")
                        # The Android article lookup reads one contiguous page within one
                        # pack. Do not strand its later sections in another module.
                        size = len(document.text.encode("utf-8"))
                        if documents and text_bytes + size > module_text_bytes and (article is None or article != last_article):
                            break
                        destination.write(raw if raw.endswith(b"\n") else raw + b"\n")
                        text_bytes += size
                        documents += 1
                        first_id = first_id or document.document_id
                        last_id = document.document_id
                        if article is not None and article != last_article:
                            articles += 1
                        last_article = article
                        pending = next(offsets, None)
                # Space for FTS/database construction and the uncompressed archive
                # simultaneously: conservative admission, not measured peak storage.
                require_build_space(working, spool.stat().st_size * 6)
                built = build_pack_streaming(spool, working / module_id, module_id, version,
                    f"{title} · {len(modules) + 1}", license_id, source_urls,
                    fts_tokenizer="porter unicode61")
                manifest = verified_archive(built.pack)
                archive_bytes, archive_hash = _digest(built.pack)
                installed_bytes = sum(a["bytes"] for a in manifest["artifacts"])
                total_installed += installed_bytes
                if archive_bytes > max_archive_bytes:
                    raise ValueError("Module archive exceeds its limit; reduce --module-text-bytes")
                if total_installed > max_installed_bytes:
                    raise ValueError("Measured installed library exceeds its configured limit")
                # Like the app, article-section inputs need an article lookup, not ordinary FTS.
                mode = "article-sections" if first_id.startswith("enw-") and last_id.startswith("enw-") else "general-keyword"
                modules.append(dict(id=module_id, version=version, documents=documents,
                    archive=f"{module_id}/{built.pack.name}", archiveBytes=archive_bytes, sha256=archive_hash,
                    installedBytes=installed_bytes, retrievalMode=mode,
                    articleGroups=articles, articleBoundariesPreserved=bool(section_input),
                    databaseSha256=manifest["artifacts"][0]["sha256"]))
                # The payload hash stays in manifest/report. The sidecar checker
                # must list only files actually retained beside the archive.
                manifest_hash = _digest(built.manifest)[1]
                built.checksums.write_text(f"{manifest_hash}  {built.manifest.name}\n"
                                          f"{archive_hash}  {built.pack.name}\n",
                                          encoding="ascii", newline="\n")
                # Only our scratch spool and redundant generated database are removed.
                # The verified archive retains all payload bytes and can restore the database.
                built.database.unlink()
                spool.unlink()
        index_path.unlink()
        # Detect changes to the source between validation and the offset-based build.
        if _digest(input_path) != (input_size, input_hash):
            raise ValueError("Input snapshot changed during build")
        report = dict(schemaVersion=1, releaseReady=False,
            input=dict(bytes=input_size, sha256=input_hash),
            builder=dict(sqliteVersion=sqlite3.sqlite_version, tokenizer="porter unicode61",
                         sourceSha256={name: _digest(Path(__file__).resolve().parents[2] / name)[1]
                                       for name in ("tools/packs/build_modular_library.py", "tools/packs/stream_pack.py",
                                                    "packtool/schema.py", "packtool/chunking.py", "packtool/build_pack.py")}),
            title=title, license=license_id, sourceUrls=source_urls,
            installedBytes=total_installed, archiveBytes=sum(m["archiveBytes"] for m in modules),
            meetsTwentyDecimalGBTarget=total_installed >= 20_000_000_000,
            runtimeStorageLimitBytes=50_000_000_000, buildFreeReserveBytes=BUILD_FREE_RESERVE_BYTES,
            remainingGates=["Host and verify every download", "Measure app/model/staging/update footprint",
                            "Evaluate retrieval and citations on Android"], modules=modules)
        (working / "library-build.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        os.replace(working, output_dir)
        return report
    except BaseException:
        shutil.rmtree(working)
        raise


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("input", type=Path)
    parser.add_argument("--input-sha256", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--id", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--license", dest="license_id", required=True)
    parser.add_argument("--source-url", dest="source_urls", action="append", required=True)
    parser.add_argument("--module-text-bytes", type=int, default=250_000_000)
    args = parser.parse_args()
    report = build_library(args.input, args.out, args.input_sha256, args.id, args.version,
                           args.title, args.license_id, args.source_urls, args.module_text_bytes)
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
