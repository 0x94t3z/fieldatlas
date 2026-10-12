#!/usr/bin/env python3
"""Audit every document/chunk in a complete pinned library, without changing it.

Optional raw-shard alignment proves that every retained document also matches
its exact Wikimedia export. This establishes byte provenance, not answer quality.
Extracts only one module database at a time; never overwrites an earlier report.
"""
from __future__ import annotations

import argparse
import bz2
from contextlib import closing
import json
import os
from pathlib import Path
import shutil
import sqlite3
import stat
import tempfile
import zipfile

from build_library_batch import OWNER, _builder_identity, _plan, _verify_module
from build_modular_library import _digest, require_build_space
from stream_pack import MAX_SOURCE_RECORD_BYTES, _parse_row


def _regular(path):
    path = Path(path)
    if not stat.S_ISREG(path.lstat().st_mode):
        raise ValueError("Audit input must be a regular file, not a symlink")
    return path


def _json(raw):
    def pairs(items):
        value = {}
        for key, item in items:
            if key in value:
                raise ValueError("Duplicate JSON field")
            value[key] = item
        return value
    return json.loads(raw, object_pairs_hook=pairs)


def _metadata(path):
    path = _regular(path)
    if path.stat().st_size > 4 * 1024 * 1024:
        raise ValueError("Audit metadata exceeds its size limit")
    return _json(path.read_bytes())


def _require_digest(path, pin, message):
    _regular(path)
    if _digest(path) != (pin["bytes"], pin["sha256"]):
        raise ValueError(message)


def _source_mapping(source_path, index):
    count = 0
    with source_path.open("rb") as source:
        while True:
            offset = source.tell()
            raw = source.readline(MAX_SOURCE_RECORD_BYTES + 1)
            if not raw:
                break
            if len(raw) > MAX_SOURCE_RECORD_BYTES:
                raise ValueError("Source record exceeds its size limit")
            count += 1
            document = _parse_row(raw, count)
            row = index.execute("SELECT offset,line FROM offsets WHERE id=?", (document.document_id.encode(),)).fetchone()
            if row != (offset, count):
                raise ValueError("Source document/offset index mapping differs")
    if count != index.execute("SELECT count(*) FROM offsets").fetchone()[0]:
        raise ValueError("Source/index document counts differ")
    return count


def _raw_mapping(raw_path, source_path, provenance_path, input_pin):
    provenance = _metadata(provenance_path)
    if provenance.get("license") != "CC-BY-SA-4.0" or provenance.get("documentsSha256") != input_pin["sha256"] or provenance.get("counts", {}).get("documentBytes") != input_pin["bytes"]:
        raise ValueError("Raw provenance does not identify the complete document corpus")
    raw_pin = dict(bytes=provenance["sourceBytes"], sha256=provenance["sourceSha256"])
    _require_digest(raw_path, raw_pin, "Raw shard checksum/size mismatch")
    provenance_pin = _digest(provenance_path)
    counts = provenance["counts"]
    matched = records = expanded = text_bytes = 0
    with source_path.open("rb") as documents, bz2.open(raw_path, "rb") as raw:
        def next_document():
            value = documents.readline(MAX_SOURCE_RECORD_BYTES + 1)
            if len(value) > MAX_SOURCE_RECORD_BYTES or value and not value.endswith(b"\n"):
                raise ValueError("Incomplete or oversized retained source record")
            return _json(value) if value else None
        document = next_document()
        pending = None
        while value := raw.readline(32 * 1024 * 1024 + 1):
            if len(value) > 32 * 1024 * 1024 or not value.endswith(b"\n"):
                raise ValueError("Incomplete or oversized raw shard record")
            expanded += len(value)
            if expanded > counts["expandedBytes"]:
                raise ValueError("Raw shard expanded bytes exceed provenance")
            row = _json(value)
            if not isinstance(row, dict):
                raise ValueError("Raw shard record must be an object")
            if "index" in row:
                if pending is not None or set(row) != {"index"} or not isinstance(row["index"], dict):
                    raise ValueError("Invalid raw index/data pairing")
                pending = row["index"].get("_id")
                if type(pending) is not int or pending <= 0:
                    raise ValueError("Invalid raw page identity")
                continue
            page = row.get("page_id")
            if type(page) is not int or page <= 0 or pending != page or row.get("wiki") != "enwiki":
                raise ValueError("Raw index/data identity differs")
            pending = None
            records += 1
            if records > counts["sourceRecords"]:
                raise ValueError("Raw shard record count exceeds provenance")
            if document is None or document.get("document_id") != f"wp-{page}-0000":
                continue
            revision = row.get("version")
            if type(revision) is not int or revision <= 0 or type(row.get("namespace")) is not int or row["namespace"] != 0 or row.get("redirect_target") or row.get("page_type") == "redirect" or not isinstance(row.get("title"), str) or not isinstance(row.get("text"), str):
                raise ValueError("Retained article has invalid raw source metadata")
            expected = dict(document_id=f"wp-{page}-0000", title=row["title"] + " — Overview",
                source=f"https://en.wikipedia.org/w/index.php?oldid={revision}",
                license="CC-BY-SA-4.0", text=row["text"])
            if document != expected:
                raise ValueError("Retained document differs from its complete raw original")
            matched += 1
            text_bytes += len(row["text"].encode("utf-8"))
            document = next_document()
        if pending is not None or document is not None or matched != counts["keptArticles"] or records != counts["sourceRecords"] or expanded != counts["expandedBytes"] or text_bytes != counts["textUtf8Bytes"]:
            raise ValueError("Raw-to-document mapping or provenance counts are incomplete")
    _require_digest(raw_path, raw_pin, "Raw shard changed during audit")
    if _digest(provenance_path) != provenance_pin:
        raise ValueError("Raw provenance changed during audit")
    return dict(fullRawSourceMappingPassed=True, sourceUrl=provenance["sourceUrl"],
        rawBytes=raw_pin["bytes"], rawSha256=raw_pin["sha256"], rawRecords=records,
        rawExpandedBytes=expanded, matchedArticles=matched, originalTextUtf8Bytes=text_bytes,
        selectionFiltersIndependentlyRecomputed=False,
        scope="Every retained article matched its raw original; exclusion eligibility is not independently reselected")


def _module(library, module, plan, source, index):
    archive_path = library / module["archive"]
    require_build_space(library, module["installedBytes"])
    with tempfile.TemporaryDirectory(prefix=".module-audit-", dir=library) as temporary:
        database = Path(temporary) / "content.sqlite"
        with zipfile.ZipFile(archive_path) as archive, archive.open("content.sqlite") as payload, database.open("xb") as out:
            shutil.copyfileobj(payload, out, 1024 * 1024)
        if _digest(database) != (module["installedBytes"], module["databaseSha256"]):
            raise ValueError("Extracted database checksum/size mismatch")
        with closing(sqlite3.connect(database.resolve().as_uri() + "?mode=ro", uri=True)) as db:
            if db.execute("PRAGMA quick_check").fetchone() != ("ok",):
                raise ValueError("SQLite integrity check failed")
            rows = db.execute("SELECT chunk_id,document_id,title,source,text FROM chunks_fts ORDER BY rowid")
            next_row = rows.fetchone()
            documents = chunks = 0
            selected = index.execute("SELECT offset,line FROM offsets WHERE id>=? AND id<=? ORDER BY id",
                (plan["firstId"].encode(), plan["lastId"].encode()))
            for offset, line in selected:
                source.seek(offset)
                document = _parse_row(source.readline(MAX_SOURCE_RECORD_BYTES + 1), line)
                parts, tail, number = [], "", 0
                while next_row and next_row[1] == document.document_id:
                    chunk_id, doc_id, title, url, text = next_row
                    if (chunk_id, title, url) != (f"{doc_id}:{number:04d}", document.title, document.source):
                        raise ValueError("Chunk citation/title/source identity mismatch")
                    if not isinstance(text, str) or not 0 < len(text) <= 1200:
                        raise ValueError("Unexpected chunk bounds")
                    if number:
                        if len(text) < 150 or tail != text[:150]:
                            raise ValueError("Chunk overlap changed original text")
                        parts.append(text[150:])
                    else:
                        parts.append(text)
                    tail = text[-150:]
                    number += 1
                    chunks += 1
                    next_row = rows.fetchone()
                if not number or "".join(parts) != document.text:
                    raise ValueError("Complete packed text differs from normalized original")
                documents += 1
            if next_row is not None or documents != plan["documents"]:
                raise ValueError("Unexpected or missing indexed documents/chunks")
            first_title = db.execute("SELECT title FROM chunks_fts ORDER BY rowid LIMIT 1").fetchone()[0]
            match = '{title} : "' + first_title.replace('"', '""') + '"'
            if not db.execute("SELECT count(*) FROM chunks_fts WHERE chunks_fts MATCH ?", (match,)).fetchone()[0]:
                raise ValueError("Title query cannot reach the saved article")
    if _digest(archive_path) != (module["archiveBytes"], module["sha256"]):
        raise ValueError("Module archive changed during audit")
    return dict(id=module["id"], documents=documents, chunks=chunks,
        installedBytes=module["installedBytes"], archiveSha256=module["sha256"],
        payloadSha256=module["databaseSha256"], originalTextMappingPassed=True,
        citationIdentityPassed=True, titleQueryPassed=True)


def _report(path, value):
    descriptor, temporary_name = tempfile.mkstemp(prefix=".library-audit-report-", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as out:
            json.dump(value, out, indent=2, sort_keys=True)
            out.write("\n")
            out.flush()
            os.fsync(out.fileno())
        os.link(temporary, path)  # Exclusive publication; never overwrite a raced destination.
        descriptor = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    finally:
        temporary.unlink(missing_ok=True)


def audit_batch(library_dir, source_path, output_path, raw_shard=None, provenance=None):
    library = Path(library_dir).absolute()
    if library.is_symlink() or not library.is_dir():
        raise ValueError("Library must be a real directory")
    library = library.resolve()
    source = _regular(Path(source_path).absolute())
    output = Path(output_path).absolute()
    if output.exists() or output.is_symlink():
        raise ValueError("Audit report already exists; refusing replacement")
    output = output.parent.resolve(strict=True) / output.name
    if (raw_shard is None) != (provenance is None):
        raise ValueError("Raw shard and provenance must be supplied together")
    checkpoint_path, progress_path = library / "checkpoint.json", library / "batch-progress.json"
    checkpoint, progress = _metadata(checkpoint_path), _metadata(progress_path)
    metadata_pins = {path: _digest(path) for path in (checkpoint_path, progress_path)}
    identity = checkpoint["identity"]
    plans, modules = checkpoint["plan"], progress["modules"]
    if checkpoint.get("owner") != OWNER or progress.get("complete") is not True or progress.get("identity") != identity or not plans or len(plans) != len(modules) or progress.get("plannedModules") != len(plans):
        raise ValueError("A complete batch with matching checkpoint/progress identities is required")
    if identity["builder"] != _builder_identity():
        raise ValueError("Pinned builder sources changed; audit with the original builder implementation")
    _require_digest(source, identity["input"], "Source checksum/size mismatch")
    index_path = library / "offsets.sqlite"
    _require_digest(index_path, checkpoint["index"], "Offset index checksum/size mismatch")
    if _plan(source, index_path, identity["moduleTextBytes"], identity["license"]) != plans:
        raise ValueError("Module plans differ from pinned input")
    module_root = library / "modules"
    expected_names = {f"{identity['id']}-{number + 1:04d}" for number in range(len(plans))}
    if module_root.is_symlink() or not module_root.is_dir() or {path.name for path in module_root.iterdir()} != expected_names:
        raise ValueError("Missing or unexpected module directories")
    if raw_shard is not None:
        provenance_path = _regular(provenance)
        metadata_pins[provenance_path] = _digest(provenance_path)
        if _metadata(provenance_path).get("sourceUrls") != identity["sourceUrls"]:
            raise ValueError("Raw provenance source URLs differ from the library")
        raw_report = _raw_mapping(_regular(raw_shard), source, provenance_path, identity["input"])
    else:
        raw_report = None
    results = []
    with source.open("rb") as incoming, closing(sqlite3.connect(index_path.resolve().as_uri() + "?mode=ro", uri=True)) as index:
        index.execute("PRAGMA cache_size=-8192")
        count = _source_mapping(source, index)
        if count != sum(plan["documents"] for plan in plans):
            raise ValueError("Module plan does not cover every source document")
        for number, (module, plan) in enumerate(zip(modules, plans)):
            directory = module_root / f"{identity['id']}-{number + 1:04d}"
            if _verify_module(directory, number, plan, identity) != module:
                raise ValueError("Progress module mapping differs from verified checkpoint")
            results.append(_module(library, module, plan, incoming, index))
    if progress["installedBytes"] != sum(module["installedBytes"] for module in modules) or progress["archiveBytes"] != sum(module["archiveBytes"] for module in modules):
        raise ValueError("Progress total bytes differ from verified modules")
    _require_digest(source, identity["input"], "Source changed during audit")
    _require_digest(index_path, checkpoint["index"], "Offset index changed during audit")
    if any(_digest(path) != pin for path, pin in metadata_pins.items()) or identity["builder"] != _builder_identity():
        raise ValueError("Metadata or builder changed during audit")
    if raw_report is not None:
        _require_digest(_regular(raw_shard), dict(bytes=raw_report["rawBytes"], sha256=raw_report["rawSha256"]), "Raw shard changed during module audit")
    report = dict(mechanicalSourceIntegrityPassed=True, fullRawSourceMappingPassed=raw_report is not None,
        source=identity["input"], sourceUrls=identity["sourceUrls"], license=identity["license"],
        offsetIndex=checkpoint["index"], builderSha256=identity["builder"],
        auditorSha256=_digest(Path(__file__).resolve())[1],
        textNormalization="NFKC, canonical newlines and outer trim only",
        modules=results, documentsChecked=sum(value["documents"] for value in results),
        chunksChecked=sum(value["chunks"] for value in results),
        installedBytes=sum(value["installedBytes"] for value in results),
        rawSourceAudit=raw_report, releaseReady=False, semanticAccuracyAccepted=False,
        androidImportTested=False, independentReviewCompleted=False)
    _report(output, report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library_dir", type=Path)
    parser.add_argument("source_path", type=Path)
    parser.add_argument("output_path", type=Path)
    parser.add_argument("--raw-shard", type=Path)
    parser.add_argument("--provenance", type=Path)
    args = parser.parse_args()
    print(json.dumps(audit_batch(**vars(args)), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
