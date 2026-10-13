#!/usr/bin/env python3
"""Build a pinned library in bounded, resumable batches; never publish artifacts.

Completed module directories are atomic checkpoints. The original source and
verified offset index remain necessary; this is not an unlimited streaming disk.
"""
from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))


import argparse
from contextlib import closing
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import uuid

from tools.packs.build_modular_library import (
    BUILD_FREE_RESERVE_BYTES, MAX_INSTALLED_LIBRARY_BYTES, MAX_MODULE_ARCHIVE_BYTES,
    IDENTIFIER, _digest, require_build_space, verified_archive,
)
from tools.packs.stream_pack import MAX_SOURCE_RECORD_BYTES, _index_documents, _parse_row, build_pack_streaming
from packtool.schema import DocumentError

ROOT = Path(__file__).resolve().parents[2]
OWNER = "fieldatlas-library-batch-v1"


def _builder_identity():
    return {name: _digest(ROOT / name)[1] for name in (
        "tools/packs/build_library_batch.py", "tools/packs/build_modular_library.py", "tools/packs/stream_pack.py",
        "packtool/schema.py", "packtool/chunking.py", "packtool/build_pack.py")}


def _atomic_json(path, data):
    fd, temporary = tempfile.mkstemp(prefix=".checkpoint-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(data, stream, indent=2, sort_keys=True, ensure_ascii=False)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    finally:
        Path(temporary).unlink(missing_ok=True)


def _read_document(source, offset, line):
    source.seek(offset)
    raw = source.readline(MAX_SOURCE_RECORD_BYTES + 1)
    if len(raw) > MAX_SOURCE_RECORD_BYTES:
        raise DocumentError(f"line {line}: document record exceeds 32 MB")
    return raw, _parse_row(raw, line)


def _plan(source_path, index_path, text_limit, license_id):
    """Validate the whole input before committing a valid-looking partial library."""
    plans, current, section_input = [], None, None
    previous_article = None
    with closing(sqlite3.connect(index_path.resolve().as_uri() + "?mode=ro", uri=True)) as db, source_path.open("rb") as source:
        db.execute("PRAGMA cache_size=-8192")
        for offset, line in db.execute("SELECT offset, line FROM offsets ORDER BY id"):
            raw, document = _read_document(source, offset, line)
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
                if article != previous_article and not document.document_id.endswith("-0000"):
                    raise ValueError("Every article must start with its original 0000 lead section")
            size = len(document.text.encode("utf-8"))
            if current and current["textBytes"] + size > text_limit and (article is None or article != previous_article):
                plans.append(current)
                current = None
            if current is None:
                if len(plans) >= 500:
                    raise ValueError("Library would exceed the app's 500-entry catalog limit")
                current = dict(firstId=document.document_id, lastId=document.document_id,
                    documents=0, articleGroups=0, textBytes=0, inputBytes=0,
                    retrievalMode="article-sections" if is_section else "general-keyword")
            current["lastId"] = document.document_id
            current["documents"] += 1
            current["textBytes"] += size
            current["inputBytes"] += len(raw) + (not raw.endswith(b"\n"))
            current["articleGroups"] += int(article is not None and article != previous_article)
            previous_article = article
        if current:
            plans.append(current)
    return plans


def _stage_cleanup(stage, token):
    if stage.exists():
        marker = stage / "owner.txt"
        if stage.is_symlink() or stage.resolve() != stage or not marker.is_file() or marker.is_symlink() or marker.read_text() != token:
            raise ValueError("Unrecognized pending directory; refusing cleanup")
        shutil.rmtree(stage)


def _verify_module(directory, number, plan, identity):
    module_id = f"{identity['id']}-{number + 1:04d}"
    pack_name = f"{module_id}-{identity['version']}.fapack"
    receipt = json.loads((directory / "module-build.json").read_text())
    if receipt.get("identity") != identity or receipt.get("plan") != plan or receipt.get("number") != number:
        raise ValueError("Module checkpoint identity/plan mismatch")
    if directory.is_symlink() or directory.resolve() != directory:
        raise ValueError("Module checkpoint must be a real owned directory")
    if sorted(p.name for p in directory.iterdir()) != sorted(["manifest.json", "SHA256SUMS", pack_name, "module-build.json"]):
        raise ValueError("Unexpected files in committed module")
    for path in directory.iterdir():
        if not path.is_file() or path.is_symlink():
            raise ValueError("Module file must not be a symlink")
    module = receipt["module"]
    archive = directory / pack_name
    archive_size, archive_hash = _digest(archive)
    manifest = verified_archive(archive)
    expected_fields = dict(id=module_id, version=identity["version"], type="KNOWLEDGE",
        title=f"{identity['title']} · {number + 1}", license=identity["license"], sourceUrls=identity["sourceUrls"])
    if any(manifest.get(key) != value for key, value in expected_fields.items()):
        raise ValueError("Archive metadata differs from pinned build identity")
    if json.loads((directory / "manifest.json").read_text()) != manifest:
        raise ValueError("Sidecar manifest differs from archive")
    artifact = manifest["artifacts"][0]
    expected_module = dict(id=module_id, version=identity["version"], documents=plan["documents"],
        archive=f"modules/{module_id}/{pack_name}", archiveBytes=archive_size, sha256=archive_hash,
        installedBytes=artifact["bytes"], databaseSha256=artifact["sha256"],
        retrievalMode=plan["retrievalMode"], articleGroups=plan["articleGroups"],
        articleBoundariesPreserved=plan["retrievalMode"] == "article-sections")
    if module != expected_module:
        raise ValueError("Module checksum/size or metadata changed")
    sums = f"{_digest(directory / 'manifest.json')[1]}  manifest.json\n{archive_hash}  {pack_name}\n"
    if (directory / "SHA256SUMS").read_text() != sums:
        raise ValueError("Module checksum sidecar changed")
    if archive_size > identity["maxArchiveBytes"]:
        raise ValueError("Module archive exceeds its limit; reduce --module-text-bytes")
    return module


def _build_module(source_path, output, checkpoint, number, installed_so_far):
    identity, plan = checkpoint["identity"], checkpoint["plan"][number]
    # Includes the pending spool plus SQLite/FTS/archive coexistence, with 1 GB
    # reserve. This is conservative admission, not a guaranteed peak bound.
    require_build_space(output, plan["inputBytes"] * 7)
    stage = output / ".pending"
    stage.mkdir()
    (stage / "owner.txt").write_text(checkpoint["token"])
    try:
        spool = stage / "module.jsonl"
        count = 0
        with closing(sqlite3.connect((output / "offsets.sqlite").resolve().as_uri() + "?mode=ro", uri=True)) as db, source_path.open("rb") as source, spool.open("wb") as target:
            db.execute("PRAGMA cache_size=-8192")
            query = "SELECT offset, line FROM offsets WHERE id >= ? AND id <= ? ORDER BY id"
            for offset, line in db.execute(query, (plan["firstId"].encode(), plan["lastId"].encode())):
                raw, _ = _read_document(source, offset, line)
                target.write(raw if raw.endswith(b"\n") else raw + b"\n")
                count += 1
        if count != plan["documents"] or spool.stat().st_size != plan["inputBytes"]:
            raise ValueError("Source/index no longer matches the module plan")
        require_build_space(output, plan["inputBytes"] * 6)
        module_id = f"{identity['id']}-{number + 1:04d}"
        built = build_pack_streaming(spool, stage / module_id, module_id, identity["version"],
            f"{identity['title']} · {number + 1}", identity["license"], identity["sourceUrls"], fts_tokenizer="porter unicode61")
        manifest = verified_archive(built.pack)
        archive_size, archive_hash = _digest(built.pack)
        artifact = manifest["artifacts"][0]
        if archive_size > identity["maxArchiveBytes"]:
            raise ValueError("Module archive exceeds its limit; reduce --module-text-bytes")
        if installed_so_far + artifact["bytes"] > identity["maxInstalledBytes"]:
            raise ValueError("Measured installed library exceeds its configured limit")
        if _digest(source_path) != (identity["input"]["bytes"], identity["input"]["sha256"]) or _builder_identity() != identity["builder"]:
            raise ValueError("Source or builder changed during batch")
        module = dict(id=module_id, version=identity["version"], documents=plan["documents"],
            archive=f"modules/{module_id}/{built.pack.name}", archiveBytes=archive_size, sha256=archive_hash,
            installedBytes=artifact["bytes"], databaseSha256=artifact["sha256"],
            retrievalMode=plan["retrievalMode"], articleGroups=plan["articleGroups"],
            articleBoundariesPreserved=plan["retrievalMode"] == "article-sections")
        built.checksums.write_text(f"{_digest(built.manifest)[1]}  manifest.json\n{archive_hash}  {built.pack.name}\n")
        # Original source remains; the verified archive contains the exact payload.
        built.database.unlink()
        _atomic_json(built.pack.parent / "module-build.json", dict(identity=identity, plan=plan, number=number, module=module))
        _verify_module(built.pack.parent, number, plan, identity)
        destination = output / "modules" / module_id
        if destination.exists() or destination.is_symlink():
            raise ValueError("Refusing to replace an existing module")
        os.rename(built.pack.parent, destination)
        fd = os.open(destination.parent, os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
        return module
    finally:
        _stage_cleanup(stage, checkpoint["token"])


def build_batch(input_path: Path, output_dir: Path, expected_sha256: str, pack_id: str,
                version: str, title: str, license_id: str, source_urls: list[str],
                module_text_bytes: int = 250_000_000, max_modules: int = 1,
                max_archive_bytes: int = MAX_MODULE_ARCHIVE_BYTES,
                max_installed_bytes: int = MAX_INSTALLED_LIBRARY_BYTES, resume: bool = False):
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError("A lowercase SHA-256 input pin is required")
    if not IDENTIFIER.fullmatch(pack_id) or len(pack_id) > 118 or not IDENTIFIER.fullmatch(version):
        raise ValueError("Invalid module id/version")
    if not title.strip() or not license_id.strip() or not source_urls or any(not value.strip() for value in source_urls):
        raise ValueError("Title, licence and source attribution are required")
    if not 1 <= module_text_bytes <= max_archive_bytes <= MAX_MODULE_ARCHIVE_BYTES or not 1 <= max_installed_bytes <= MAX_INSTALLED_LIBRARY_BYTES or not 1 <= max_modules <= 500:
        raise ValueError("Invalid batch size limits")
    input_path, output_dir = input_path.resolve(strict=True), output_dir.absolute()
    if output_dir.is_symlink():
        raise ValueError("Workspace must not be a symlink")
    # macOS /var and /tmp are parent-directory aliases. Resolve those once;
    # never follow a symlink at the workspace itself for writes or cleanup.
    output_dir = output_dir.parent.resolve() / output_dir.name
    size, checksum = _digest(input_path)
    if checksum != expected_sha256:
        raise ValueError("Input snapshot checksum mismatch")
    identity = dict(input=dict(bytes=size, sha256=checksum), id=pack_id, version=version,
        title=title, license=license_id, sourceUrls=source_urls, moduleTextBytes=module_text_bytes,
        maxArchiveBytes=max_archive_bytes, maxInstalledBytes=max_installed_bytes, builder=_builder_identity(),
        sqliteVersion=sqlite3.sqlite_version)
    if output_dir.exists():
        if not resume or output_dir.is_symlink() or not (output_dir / "checkpoint.json").is_file() or (output_dir / "checkpoint.json").is_symlink():
            raise ValueError("Output already exists or is not a resumable checkpoint; refusing replacement")
        checkpoint = json.loads((output_dir / "checkpoint.json").read_text())
        if checkpoint.get("owner") != OWNER or checkpoint.get("identity") != identity or not re.fullmatch(r"[0-9a-f]{32}", checkpoint.get("token", "")):
            raise ValueError("Resume identity changed; source, options and builder must match")
    else:
        if resume:
            raise ValueError("Resume workspace does not exist")
        output_dir.parent.mkdir(parents=True, exist_ok=True)
        # The original source already exists; only bounded offset rows are added
        # here. Recheck headroom at each indexing batch instead of demanding a
        # second full-corpus-sized allocation.
        require_build_space(output_dir.parent, 16_000_000)
        output_dir.mkdir()
        checkpoint = None
    if (output_dir / ".build.lock").is_symlink():
        raise ValueError("Workspace lock must not be a symlink")
    with (output_dir / ".build.lock").open("a+b") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError("Another batch builder owns this workspace") from error
        if checkpoint is None:
            try:
                index = output_dir / "offsets.sqlite"
                _index_documents(input_path, index,
                    on_batch=lambda: require_build_space(output_dir, 16_000_000))
                plans = _plan(input_path, index, module_text_bytes, license_id)
                if _digest(input_path) != (size, checksum):
                    raise ValueError("Input snapshot changed during planning")
                checkpoint = dict(owner=OWNER, token=uuid.uuid4().hex, identity=identity,
                    index=dict(bytes=index.stat().st_size, sha256=_digest(index)[1]), plan=plans)
                (output_dir / "modules").mkdir()
                _atomic_json(output_dir / "checkpoint.json", checkpoint)
            except BaseException:
                shutil.rmtree(output_dir)
                raise
        index = output_dir / "offsets.sqlite"
        if index.is_symlink() or index.resolve() != index:
            raise ValueError("Pinned offset index must not be a symlink")
        if _digest(index) != (checkpoint["index"]["bytes"], checkpoint["index"]["sha256"]):
            raise ValueError("Pinned offset index changed")
        if _plan(input_path, index, module_text_bytes, license_id) != checkpoint["plan"]:
            raise ValueError("Checkpoint plan differs from the pinned original source")
        _stage_cleanup(output_dir / ".pending", checkpoint["token"])
        modules = []
        missing = False
        expected_names = {f"{pack_id}-{number + 1:04d}" for number in range(len(checkpoint["plan"]))}
        module_root = output_dir / "modules"
        if module_root.is_symlink() or any(p.is_symlink() or p.name not in expected_names for p in module_root.iterdir()):
            raise ValueError("Unexpected module directory; refusing to overwrite or delete it")
        for number, plan in enumerate(checkpoint["plan"]):
            directory = module_root / f"{pack_id}-{number + 1:04d}"
            if directory.exists():
                if missing:
                    raise ValueError("Noncontiguous module checkpoints")
                modules.append(_verify_module(directory, number, plan, identity))
            else:
                missing = True
        total = sum(module["installedBytes"] for module in modules)
        if total > max_installed_bytes:
            raise ValueError("Committed library exceeds its configured limit")
        for number in range(len(modules), min(len(modules) + max_modules, len(checkpoint["plan"]))):
            module = _build_module(input_path, output_dir, checkpoint, number, total)
            modules.append(module)
            total += module["installedBytes"]
        complete = len(modules) == len(checkpoint["plan"])
        report = dict(schemaVersion=1, releaseReady=False, complete=complete,
            identity=identity, modules=modules, plannedModules=len(checkpoint["plan"]),
            installedBytes=total, archiveBytes=sum(m["archiveBytes"] for m in modules),
            meetsTwentyDecimalGBTarget=complete and total >= 20_000_000_000,
            runtimeStorageLimitBytes=50_000_000_000, buildFreeReserveBytes=BUILD_FREE_RESERVE_BYTES,
            sourceAndFinishedArchivesStillRequired=True,
            remainingGates=["Useful new knowledge, not duplicated existing coverage", "Verify hosting and Android import/storage/RAM", "Independent retrieval and answer-support acceptance"])
        _atomic_json(output_dir / "batch-progress.json", report)
        return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--input-sha256", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--id", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--license", dest="license_id", required=True)
    parser.add_argument("--source-url", action="append", dest="source_urls", required=True)
    parser.add_argument("--module-text-bytes", type=int, default=250_000_000)
    parser.add_argument("--max-modules", type=int, default=1)
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    report = build_batch(args.input, args.out, args.input_sha256, args.id, args.version,
        args.title, args.license_id, args.source_urls, args.module_text_bytes, args.max_modules, resume=args.resume)
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
