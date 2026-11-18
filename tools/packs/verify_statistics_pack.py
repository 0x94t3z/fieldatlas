#!/usr/bin/env python3
"""Verify the built statistics packs the way the Field Atlas app does.

Ports the checks in app/src/main/java/xyz/fieldatlas/assets/PackManifestParser.kt
(required fields, schemaVersion 1..2, identifier pattern, artifact hashes,
discovery limits) and the on-disk checks from docs/tools-README.md (the app
re-hashes every artifact against the manifest before installing; the DB must
pass quick_check and answer FTS MATCH). Also checks the dual-search DB of the
leviathan release: every chunks_fts row hydrates identically to the schema-1
pack, records covers every passage, and fieldatlas_search names leviathan-0.2.0.

The Android app itself cannot run here, so this is a structural verification
against the app's own rules, not an on-device import. See VERIFICATION.md.

Usage:
  python3 verify_statistics_pack.py
"""
from __future__ import annotations

import hashlib
import json
import re
import sqlite3
import sys
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REF = HERE.parent / "fieldatlas-ref"
sys.path.insert(0, str(REF))

from tools.packs.fapack_convert import verify_pack  # noqa: E402

V1 = HERE / "statistics-pack" / "openstax-maths-statistics-0.1.0.fapack"
LEV = HERE / "leviathan" / "openstax-maths-statistics-0.1.0-leviathan.fapack"

IDENTIFIER = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
HASH = re.compile(r"[0-9a-f]{64}")
REQUIRED_ROOT = {"schemaVersion", "id", "version", "type", "title", "license", "sourceUrls", "artifacts"}
ALLOWED_ROOT = REQUIRED_ROOT | {"discovery", "embedding"}
MAX_COVERAGE_CHARS = 160
MAX_QUESTION_CHARS = 140
MAX_EXAMPLE_QUESTIONS = 6


def fail(msg):
    raise AssertionError(msg)


def check_manifest(manifest, expect_version, expect_schema):
    if set(manifest) - ALLOWED_ROOT or REQUIRED_ROOT - set(manifest):
        fail(f"manifest fields: {sorted(manifest)}")
    if manifest["schemaVersion"] not in (1, 2):
        fail(f"schemaVersion {manifest['schemaVersion']}")
    if manifest["schemaVersion"] != expect_schema:
        fail(f"schemaVersion {manifest['schemaVersion']} != {expect_schema}")
    if manifest["schemaVersion"] == 2 and manifest["type"] != "KNOWLEDGE":
        fail("schema 2 only for KNOWLEDGE packs")
    for field in ("id", "version"):
        if not IDENTIFIER.fullmatch(manifest[field]) or manifest[field] in (".", ".."):
            fail(f"unsafe {field}: {manifest[field]!r}")
    if manifest["id"] != "openstax-maths-statistics" or manifest["version"] != expect_version:
        fail(f"id/version: {manifest['id']}/{manifest['version']}")
    if manifest["type"] != "KNOWLEDGE":
        fail(f"type {manifest['type']}")
    for field in ("title", "license"):
        if not isinstance(manifest[field], str) or not manifest[field].strip():
            fail(f"blank {field}")
    if not manifest["sourceUrls"] or any(not isinstance(u, str) or not u.strip() for u in manifest["sourceUrls"]):
        fail("sourceUrls empty")
    if not manifest["artifacts"]:
        fail("artifacts empty")
    paths = [a["path"] for a in manifest["artifacts"]]
    if len(set(paths)) != len(paths):
        fail("duplicate artifact path")
    for a in manifest["artifacts"]:
        if set(a) != {"path", "bytes", "sha256"}:
            fail(f"artifact fields: {sorted(a)}")
        if a["path"].startswith("/") or ".." in Path(a["path"]).parts or not a["path"]:
            fail(f"unsafe artifact path: {a['path']}")
        if not isinstance(a["bytes"], int) or a["bytes"] <= 0:
            fail(f"artifact bytes: {a}")
        if not HASH.fullmatch(a["sha256"]):
            fail(f"artifact sha256: {a}")
    disc = manifest.get("discovery")
    if disc is not None:
        if set(disc) != {"coverageSummary", "exampleQuestions", "coverageLevel"}:
            fail(f"discovery fields: {sorted(disc)}")
        if not 1 <= len(disc["coverageSummary"]) <= MAX_COVERAGE_CHARS:
            fail(f"coverageSummary {len(disc['coverageSummary'])} chars")
        if len(disc["exampleQuestions"]) > MAX_EXAMPLE_QUESTIONS:
            fail("too many exampleQuestions")
        if len(set(disc["exampleQuestions"])) != len(disc["exampleQuestions"]):
            fail("exampleQuestions not unique")
        if any(not q or len(q) > MAX_QUESTION_CHARS for q in disc["exampleQuestions"]):
            fail("exampleQuestion length")
        if disc["coverageLevel"] not in {"demo", "focused", "broad"}:
            fail(f"coverageLevel {disc['coverageLevel']}")
    return True


def check_zip(pack):
    with zipfile.ZipFile(pack) as archive:
        names = archive.namelist()
        if "manifest.json" not in names or "content.sqlite" not in names:
            fail(f"{pack.name}: missing entries {names}")
        manifest = json.loads(archive.read("manifest.json"))
        for a in manifest["artifacts"]:
            blob = archive.read(a["path"])
            if len(blob) != a["bytes"] or hashlib.sha256(blob).hexdigest() != a["sha256"]:
                fail(f"{pack.name}: {a['path']} bytes/sha mismatch")
    return manifest


def open_db(pack):
    with zipfile.ZipFile(pack) as archive:
        data = archive.read("content.sqlite")
    tmp = Path(tempfile.mkdtemp()) / "content.sqlite"
    tmp.write_bytes(data)
    return sqlite3.connect(tmp), tmp


def check_db_v1(pack):
    db, _ = open_db(pack)
    try:
        if db.execute("PRAGMA quick_check").fetchone()[0] != "ok":
            fail(f"{pack.name}: quick_check")
        if db.execute("PRAGMA user_version").fetchone()[0] != 1:
            fail(f"{pack.name}: user_version")
        chunks = db.execute("SELECT count(*) FROM chunks_fts").fetchone()[0]
        docs = db.execute("SELECT count(DISTINCT document_id) FROM chunks_fts").fetchone()[0]
        if chunks != 1513 or docs != 80:
            fail(f"{pack.name}: {chunks} chunks / {docs} docs")
        # FTS5 MATCH smoke: the porter tokenizer must find a statistics term.
        hit = db.execute("SELECT title FROM chunks_fts WHERE chunks_fts MATCH 'confidence interval' LIMIT 1").fetchone()
        if not hit:
            fail(f"{pack.name}: FTS MATCH found nothing")
        return chunks, docs, hit[0]
    finally:
        db.close()


def check_db_lev(pack, v1_pack):
    db, _ = open_db(pack)
    v1, _ = open_db(v1_pack)
    try:
        if db.execute("PRAGMA quick_check").fetchone()[0] != "ok":
            fail(f"{pack.name}: quick_check")
        if db.execute("PRAGMA user_version").fetchone()[0] != 1:
            fail(f"{pack.name}: user_version")
        records = db.execute("SELECT count(*) FROM records").fetchone()[0]
        if records != 1513:
            fail(f"{pack.name}: {records} leviathan records")
        if not db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='record_fts'").fetchone():
            fail(f"{pack.name}: no record_fts")
        search = db.execute("SELECT * FROM fieldatlas_search").fetchall()
        if search != [(1, "leviathan-0.2.0")]:
            fail(f"{pack.name}: fieldatlas_search {search}")
        # Every chunks_fts row must hydrate identically to the schema-1 pack.
        v1_rows = v1.execute("SELECT rowid,chunk_id,document_id,title,source,text FROM chunks_fts").fetchall()
        lev_rows = db.execute("SELECT rowid,chunk_id,document_id,title,source,text FROM chunks_fts").fetchall()
        if v1_rows != lev_rows:
            fail(f"{pack.name}: chunks_fts differs from schema-1 pack")
        hit = db.execute("SELECT title FROM chunks_fts WHERE chunks_fts MATCH '\"chi-square\"' LIMIT 1").fetchone()
        if not hit:
            fail(f"{pack.name}: FTS MATCH found nothing")
        return records, search, hit[0]
    finally:
        db.close()
        v1.close()


def main():
    report = {}
    for pack, version, schema in ((V1, "0.1.0", 1), (LEV, "0.1.0-leviathan", 2)):
        manifest = check_zip(pack)
        check_manifest(manifest, version, schema)
        report[pack.name] = {"manifest": "ok (ported PackManifestParser.kt checks)",
                             "artifacts": "ok (bytes + sha256 re-hashed)"}
    report[V1.name]["phone_checks"] = verify_pack(V1)
    chunks, docs, hit = check_db_v1(V1)
    report[V1.name]["database"] = {"chunks": chunks, "documents": docs, "fts_sample_hit": hit}
    records, search, hit = check_db_lev(LEV, V1)
    report[LEV.name]["database"] = {"leviathan_records": records, "fieldatlas_search": search,
                                    "chunks_identical_to_v1": True, "fts_sample_hit": hit}
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
