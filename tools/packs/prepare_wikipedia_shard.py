#!/usr/bin/env python3
"""Convert one pinned new-format English CirrusSearch shard into full-text documents.

Offline only. Atomic output, bounded records/workspace, exact oldid attribution,
disk-backed page deduplication and exclusions. No invented sections or publication.
"""
from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))


import argparse
import bz2
import gzip
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
from urllib.parse import urlsplit, unquote

from tools.packs.build_modular_library import _digest, require_build_space

LICENSE = "CC-BY-SA-4.0"
COPYRIGHT_URL = "https://en.wikipedia.org/wiki/Wikipedia:Copyrights"
MAX_RECORD = 32 * 1024 * 1024
INDEX_CATEGORIES = {"All disambiguation pages", "All article disambiguation pages", "All set index articles"}


def builder_identity():
    directory = Path(__file__).resolve().parents[1]
    return {name: _digest(directory / name)[1] for name in (
        "packs/prepare_wikipedia_shard.py", "packs/build_modular_library.py", "../packtool/build_pack.py")}


def is_index_article(row):
    # New exports include transitive template/module dependencies: a helper named
    # "disambiguation" is present in ordinary articles too. Actual tracking
    # categories describe the rendered page, not those shared dependencies.
    categories = row.get("category", [])
    if not isinstance(categories, list) or any(not isinstance(value, str) for value in categories):
        raise ValueError("Invalid article category metadata")
    return row["title"].endswith("(disambiguation)") or bool(INDEX_CATEGORIES.intersection(categories))


def strict_json(raw):
    def object_pairs(pairs):
        row = {}
        for key, value in pairs:
            if key in row:
                raise ValueError(f"Duplicate JSON field: {key}")
            row[key] = value
        return row
    return json.loads(raw, object_pairs_hook=object_pairs)


def positive_id(value, field):
    if type(value) is not int or value <= 0:
        raise ValueError(f"Invalid {field}")
    return value


def snapshot_url(url):
    parsed = urlsplit(url)
    match = re.fullmatch(
        r"/other/cirrus_search_index/([0-9]{8})/index_name=enwiki_content/"
        r"enwiki_content-([0-9]{8})-([0-9]{5})\.json\.bz2", unquote(parsed.path))
    if parsed.scheme != "https" or parsed.netloc != "dumps.wikimedia.org" or \
            parsed.query or parsed.fragment or not match or match[1] != match[2]:
        raise ValueError("Expected a dated official English Wikipedia content shard URL")
    return match[1], match[3]


def records(path, max_expanded_bytes=10_000_000_000):
    """Strict bounded JSONL, optionally gzip, without an expanded temporary copy."""
    path = Path(path)
    if max_expanded_bytes <= 0:
        raise ValueError("Exclusion expanded byte limit must be positive")
    opener = gzip.open if path.suffix == ".gz" else Path.open
    expanded = 0
    with opener(path, "rb") as source:
        while raw := source.readline(MAX_RECORD + 1):
            if len(raw) > MAX_RECORD or not raw.endswith(b"\n"):
                raise ValueError("Oversized or incomplete exclusion record")
            expanded += len(raw)
            if expanded > max_expanded_bytes:
                raise ValueError("Expanded exclusion input exceeds its byte limit")
            yield strict_json(raw)


def convert(input_path, output_dir, expected_sha256, expected_bytes, source_url,
            exclude_spools=(), exclude_documents=(), max_output_bytes=1_000_000_000,
            max_expanded_bytes=10_000_000_000):
    input_path, output_dir = Path(input_path), Path(output_dir)
    builder = builder_identity()
    snapshot, shard = snapshot_url(source_url)
    if expected_bytes <= 0 or max_output_bytes <= 0 or max_expanded_bytes <= 0:
        raise ValueError("Byte limits must be positive")
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError("Invalid SHA-256 pin")
    if input_path.is_symlink() or not input_path.is_file():
        raise ValueError("Input must be an existing regular file, not a symlink")
    if output_dir.exists() or output_dir.is_symlink():
        raise ValueError("Output already exists; never replace a completed corpus")
    if _digest(input_path) != (expected_bytes, expected_sha256):
        raise ValueError("Source size/checksum mismatch")
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    require_build_space(output_dir.parent, 0)
    excluded_pins = []
    for kind, paths in (("spool", exclude_spools), ("documents", exclude_documents)):
        for path in map(Path, paths):
            if path.is_symlink() or not path.is_file():
                raise ValueError("Exclusion input must be a regular file")
            size, digest = _digest(path)
            excluded_pins.append((kind, path, size, digest))
    stage = Path(tempfile.mkdtemp(prefix=".wiki-shard-", dir=output_dir.parent))
    counts = dict(sourceRecords=0, keptArticles=0, excludedArticles=0,
                  nonContentRecords=0, emptyArticles=0, disambiguationArticles=0,
                  textUtf8Bytes=0, documentBytes=0, expandedBytes=0)
    try:
        with closing(sqlite3.connect(stage / "pages.sqlite")) as db:
            db.execute("PRAGMA cache_size=-8192")
            db.execute("CREATE TABLE excluded (page INTEGER PRIMARY KEY)")
            db.execute("CREATE TABLE seen (page INTEGER PRIMARY KEY)")
            exclusion_records = 0
            for kind, path, _, _ in excluded_pins:
                for row in records(path, max_expanded_bytes=max_expanded_bytes):
                    if not isinstance(row, dict):
                        raise ValueError("Exclusion record must be an object")
                    if kind == "spool":
                        if row.get("missing"):
                            continue
                        page = positive_id(row.get("pageid"), "excluded pageid")
                    else:
                        match = re.fullmatch(r"(?:wp|enw)-([0-9]+)-[0-9]{4,}", str(row.get("document_id")))
                        if not match or not re.fullmatch(
                                r"https://en\.wikipedia\.org/w/index\.php\?oldid=[0-9]+", str(row.get("source"))):
                            raise ValueError("Exclusion documents must identify English Wikipedia pages")
                        page = positive_id(int(match[1]), "excluded document page")
                    db.execute("INSERT OR IGNORE INTO excluded VALUES (?)", (page,))
                    exclusion_records += 1
                    if exclusion_records % 1000 == 0:
                        db.commit()
                        require_build_space(stage, 0)
            db.commit()
            document_digest = hashlib.sha256()
            with bz2.open(input_path, "rb") as source, (stage / "documents.jsonl").open("xb") as out:
                pending = None
                while raw := source.readline(MAX_RECORD + 1):
                    if len(raw) > MAX_RECORD or not raw.endswith(b"\n"):
                        raise ValueError("Oversized or incomplete source record")
                    counts["expandedBytes"] += len(raw)
                    if counts["expandedBytes"] > max_expanded_bytes:
                        raise ValueError("Expanded source exceeds its byte limit")
                    row = strict_json(raw)
                    if not isinstance(row, dict):
                        raise ValueError("Source record must be an object")
                    if "index" in row:
                        if pending is not None or set(row) != {"index"} or not isinstance(row["index"], dict):
                            raise ValueError("Invalid index/data pairing")
                        pending = positive_id(row["index"].get("_id"), "index page_id")
                        continue
                    page = positive_id(row.get("page_id"), "page_id")
                    if pending != page:
                        raise ValueError("Index/data page identity mismatch")
                    pending = None
                    if row.get("wiki") != "enwiki":
                        raise ValueError("Source contains a different wiki")
                    try:
                        db.execute("INSERT INTO seen VALUES (?)", (page,))
                    except sqlite3.IntegrityError as error:
                        raise ValueError("Duplicate source page") from error
                    counts["sourceRecords"] += 1
                    if counts["sourceRecords"] % 1000 == 0:
                        db.commit()
                        require_build_space(stage, 0)
                    if type(row.get("namespace")) is not int:
                        raise ValueError("Invalid namespace")
                    if row["namespace"] != 0 or row.get("redirect_target") or row.get("page_type") == "redirect":
                        counts["nonContentRecords"] += 1
                        continue
                    title, body = row.get("title"), row.get("text")
                    if not isinstance(title, str) or not title.strip() or not isinstance(body, str):
                        raise ValueError("Invalid article title or exported text")
                    if is_index_article(row):
                        counts["disambiguationArticles"] += 1
                        continue
                    if db.execute("SELECT 1 FROM excluded WHERE page=?", (page,)).fetchone():
                        counts["excludedArticles"] += 1
                        continue
                    if not body.strip():
                        counts["emptyArticles"] += 1
                        continue
                    revision = positive_id(row.get("version"), "revision")
                    # New exports omit opening_text and section offsets. Store the whole body
                    # once, unchanged. Existing ordinary keyword/title/article paths can read it;
                    # do not infer fake sections or concatenate source wikitext/auxiliary fields.
                    document = dict(document_id=f"wp-{page}-0000", title=title + " — Overview",
                                    source=f"https://en.wikipedia.org/w/index.php?oldid={revision}",
                                    license=LICENSE, text=body)
                    encoded = (json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
                    if counts["documentBytes"] + len(encoded) > max_output_bytes:
                        raise ValueError("Document corpus exceeds its byte limit")
                    require_build_space(stage, len(encoded))
                    out.write(encoded)
                    document_digest.update(encoded)
                    counts["keptArticles"] += 1
                    counts["documentBytes"] += len(encoded)
                    counts["textUtf8Bytes"] += len(body.encode("utf-8"))
                if pending is not None:
                    raise ValueError("Incomplete index/data pair")
                if not counts["keptArticles"]:
                    raise ValueError("No new articles retained")
                out.flush()
                os.fsync(out.fileno())
            if _digest(input_path) != (expected_bytes, expected_sha256):
                raise ValueError("Source changed during conversion")
            for _, path, size, digest in excluded_pins:
                if _digest(path) != (size, digest):
                    raise ValueError("Exclusion input changed during conversion")
            if builder_identity() != builder:
                raise ValueError("Converter or shared builder changed during conversion")
            report = dict(sourceUrl=source_url, sourceBytes=expected_bytes, sourceSha256=expected_sha256,
                          snapshot=snapshot, shard=shard, license=LICENSE, sourceUrls=[source_url, COPYRIGHT_URL],
                          exclusions=[dict(kind=kind, bytes=size, sha256=digest,
                                           format="jsonl-gzip" if path.suffix == ".gz" else "jsonl")
                                      for kind, path, size, digest in excluded_pins],
                          excludedPageCount=db.execute("SELECT count(*) FROM excluded").fetchone()[0],
                          documentsSha256=document_digest.hexdigest(), counts=counts,
                          builderSha256=builder["packs/prepare_wikipedia_shard.py"], builder=builder,
                          pythonVersion=sys.version.split()[0], sqliteVersion=sqlite3.sqlite_version,
                          limits=dict(maxRecordBytes=MAX_RECORD, maxOutputBytes=max_output_bytes,
                                      maxExpandedBytes=max_expanded_bytes),
                          retrievalMode="general-keyword", textPolicy="Complete exported plain text, once per article",
                          sectionOffsetsAvailable=False, publisherAuthenticatedChecksum=False,
                          indexArticlePolicy="Exact rendered-page tracking categories or title suffix, not template dependencies",
                          releaseReady=False, accuracyAccepted=False)
        (stage / "pages.sqlite").unlink()  # Only this converter's disposable dedup index.
        with (stage / "SOURCE.json").open("x", encoding="utf-8") as out:
            json.dump(report, out, indent=2, ensure_ascii=False, sort_keys=True)
            out.write("\n")
            out.flush()
            os.fsync(out.fileno())
        if output_dir.exists() or output_dir.is_symlink():
            raise ValueError("Output appeared during conversion; refusing to replace it")
        os.rename(stage, output_dir)
        descriptor = os.open(output_dir.parent, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        return report
    finally:
        if stage.exists():
            shutil.rmtree(stage)  # Exact mkdtemp-owned stage, never source/output parents.


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("input", type=Path)
    parser.add_argument("--input-sha256", required=True)
    parser.add_argument("--input-bytes", required=True, type=int)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--exclude-spool", type=Path, action="append", default=[])
    parser.add_argument("--exclude-documents", type=Path, action="append", default=[])
    parser.add_argument("--max-output-bytes", type=int, default=1_000_000_000)
    parser.add_argument("--max-expanded-bytes", type=int, default=10_000_000_000)
    args = parser.parse_args()
    report = convert(args.input, args.out, args.input_sha256, args.input_bytes, args.source_url,
                     args.exclude_spool, args.exclude_documents, args.max_output_bytes, args.max_expanded_bytes)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
