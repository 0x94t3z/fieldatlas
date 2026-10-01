#!/usr/bin/env python3
"""Snapshot selected original Wikipedia revisions, then build offline from a locked cache.

No competitor assets or code are used. Fetch is explicit, sequential and bounded.
Build never accesses the network. This pilot is not a quality-certified release.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import sys
import time
import urllib.parse
import urllib.request

import bs4
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from stream_pack import build_pack_streaming
from fapack_convert import verify_pack
from packtool.chunking import Chunk, chunk_document

API = "https://en.wikipedia.org/w/api.php"
LICENSE = "CC-BY-SA-4.0"
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
USER_AGENT = "FieldAtlasReferenceBuilder/1.0 (https://github.com/0x94t3z/fieldatlas)"
MAX_RESPONSE = 8 * 1024 * 1024
SKIP_SECTIONS = {"references", "notes", "citations", "bibliography", "further reading",
                 "external links", "see also", "sources", "footnotes"}


def canonical(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_atomic(path, data):
    temporary = path.with_suffix(path.suffix + ".partial")
    temporary.write_bytes(data)
    temporary.replace(path)


def request(params):
    query = urllib.parse.urlencode({"format": "json", "formatversion": 2, "maxlag": 5, **params})
    req = urllib.request.Request(API + "?" + query, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as response:
        raw = response.read(MAX_RESPONSE + 1)
    if len(raw) > MAX_RESPONSE:
        raise ValueError("Wikipedia response exceeded the 8 MiB safety limit")
    result = json.loads(raw)
    if "error" in result or "warnings" in result:
        raise ValueError(f"Wikipedia rejected the request: {result.get('error', result.get('warnings'))}")
    return result


def topic_entries(path):
    data = json.loads(path.read_bytes())
    entries = [(category, title) for category, titles in data["topics"].items() for title in titles]
    if not 1 <= len(entries) <= 200 or any(not isinstance(t, str) or not t.strip() for _, t in entries):
        raise ValueError("Pilot requires 1–200 nonempty titles")
    if len({t.casefold() for _, t in entries}) != len(entries):
        raise ValueError("Duplicate topic title")
    return entries


def seed_snapshot(topics, cache, seed):
    """Reuse verified revisions in a new selection without refreshing old articles."""
    if cache.exists():
        raise ValueError("Seed destination already exists")
    entries = set(topic_entries(topics))
    lock = json.loads((seed / "sources.lock.json").read_bytes())
    if lock.get("schema") != 1:
        raise ValueError("Unsupported seed schema")
    rows = [r for r in lock["sources"] if (r["category"], r["requested"]) in entries]
    if len({r["requested"] for r in rows}) != len(rows) or len({r["pageid"] for r in rows}) != len(rows):
        raise ValueError("Duplicate seed sources")
    # Validate every selected snapshot before creating the destination.
    for row in rows:
        read_snapshot(seed, row)
    cache.mkdir(parents=True)
    for row in rows:
        write_atomic(cache / row["file"], (seed / row["file"]).read_bytes())
    write_atomic(cache / "sources.lock.json", canonical({"schema": 1,
        "topics_sha256": digest(topics.read_bytes()), "sources": rows}))


def fetch(topics, cache):
    entries = topic_entries(topics)
    cache.mkdir(parents=True, exist_ok=True)
    lock_path = cache / "sources.lock.json"
    topic_hash = digest(topics.read_bytes())
    lock = json.loads(lock_path.read_bytes()) if lock_path.exists() else {
        "schema": 1, "topics_sha256": topic_hash, "sources": []}
    if lock["topics_sha256"] != topic_hash:
        raise ValueError("Topic selection changed: use a new cache directory")
    for category, requested in entries:
        existing = next((row for row in lock["sources"] if row["requested"] == requested), None)
        if existing:
            read_snapshot(cache, existing)
            continue
        time.sleep(0.5)
        result = request({"action": "query", "prop": "revisions|pageprops", "titles": requested,
                          "redirects": 1, "rvprop": "ids|timestamp", "ppprop": "disambiguation"})
        pages = result["query"]["pages"]
        if len(pages) != 1 or pages[0].get("missing") or pages[0].get("ns") != 0:
            raise ValueError(f"Not a main-namespace article: {requested}")
        page = pages[0]
        if "disambiguation" in page.get("pageprops", {}):
            raise ValueError(f"Disambiguation article: {requested}")
        revision = page["revisions"][0]
        time.sleep(0.5)
        parsed = request({"action": "parse", "oldid": revision["revid"], "prop": "text|revid"})["parse"]
        if parsed["revid"] != revision["revid"]:
            raise ValueError("Revision mismatch")
        raw = canonical(parsed)
        filename = f"{page['pageid']}-{revision['revid']}.json"
        write_atomic(cache / filename, raw)
        lock["sources"].append({"requested": requested, "category": category, "title": page["title"],
            "pageid": page["pageid"], "revision": revision["revid"], "timestamp": revision["timestamp"],
            "file": filename, "sha256": digest(raw), "license": LICENSE,
            "url": f"https://en.wikipedia.org/w/index.php?oldid={revision['revid']}"})
        write_atomic(lock_path, canonical(lock))
        print(f"Cached {len(lock['sources'])}/{len(entries)}: {page['title']}", flush=True)
    return lock


def read_snapshot(cache, row):
    filename = row["file"]
    if not re.fullmatch(r"[0-9]+-[0-9]+\.json", filename):
        raise ValueError("Unsafe cache filename")
    raw = (cache / filename).read_bytes()
    if digest(raw) != row["sha256"]:
        raise ValueError(f"Cache hash mismatch: {filename}")
    value = json.loads(raw)
    if value["revid"] != row["revision"] or row["license"] != LICENSE:
        raise ValueError("Invalid revision or license")
    if row["url"] != f"https://en.wikipedia.org/w/index.php?oldid={row['revision']}":
        raise ValueError("Source URL does not match the pinned revision")
    return value


def sections(html):
    soup = BeautifulSoup(html, "html.parser")
    body = soup.select_one(".mw-parser-output")
    if body is None:
        raise ValueError("Missing Wikipedia article body")
    # Do not pretend images, tables or equations have been preserved as prose.
    removed = {"tables": len(body.find_all("table")), "math": len(body.find_all("math"))}
    for node in body.select("script, style, table, figure, .mw-editsection, .reference, .reflist, "
                            ".navbox, .hatnote, .metadata, .sidebar, .thumb, .mw-empty-elt"):
        node.decompose()
    path = []
    paragraphs = []
    skipped = False
    output = []
    def flush():
        if paragraphs:
            output.append((" > ".join(text for _, text in path) or "Overview", "\n\n".join(paragraphs)))
            paragraphs.clear()
    for node in body.find_all(["h2", "h3", "h4", "h5", "h6", "p", "li"]):
        if node.name.startswith("h"):
            flush()
            level = int(node.name[1])
            title = node.get_text(" ", strip=True)
            path = [(n, t) for n, t in path if n < level] + [(level, title)]
            skipped = any(t.casefold() in SKIP_SECTIONS for _, t in path)
        elif not skipped and not node.find_parent(["p", "li"]):
            if node.find("math") or node.select_one(".mwe-math-element"):
                # A removed equation could invert the meaning of the remaining sentence.
                continue
            text = re.sub(r"\s+", " ", node.get_text()).strip()
            if text:
                paragraphs.append(text)
    flush()
    if not output:
        raise ValueError("Article yielded no usable prose")
    return output, removed


def paragraph_chunks(document):
    """Keep source paragraphs whole. Soft target; never split text to hit a quota.

    No character overlap: it created midword starts in the legacy pilot. Adjacent
    dependent/qualification paragraphs stay in the same unit, even above target.
    This English heuristic cannot establish semantic completeness across sections.
    """
    paragraphs = document.text.split("\n\n")
    dependent = re.compile(r"^(?:however|but|nevertheless|nonetheless|although|in contrast|this|these|those|it|they|such)\b", re.I)
    groups, current = [], []
    for paragraph in paragraphs:
        if current and len("\n\n".join(current + [paragraph])) > 1200 and not dependent.match(paragraph):
            groups.append("\n\n".join(current))
            current = []
        current.append(paragraph)
    if current:
        groups.append("\n\n".join(current))
    return [Chunk(f"{document.document_id}:{i:04d}", document.document_id,
                  document.title, document.source, text) for i, text in enumerate(groups)]


def build(topics, cache, output, version, preserve_paragraphs=False,
          pack_id="general-reference-pilot", title="General Reference · Pilot"):
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,79}", pack_id) or not title.strip():
        raise ValueError("Invalid pack identity")
    if output.exists():
        raise ValueError("Output already exists; choose a new build directory")
    lock = json.loads((cache / "sources.lock.json").read_bytes())
    entries = topic_entries(topics)
    if lock.get("schema") != 1 or lock["topics_sha256"] != digest(topics.read_bytes()):
        raise ValueError("Lock does not match topic selection")
    actual = [(r["category"], r["requested"]) for r in lock["sources"]]
    if sorted(actual) != sorted(entries) or len({r['pageid'] for r in lock['sources']}) != len(entries):
        raise ValueError("Incomplete or duplicate source snapshot")
    documents, report = [], []
    for row in sorted(lock["sources"], key=lambda r: r["pageid"]):
        article = read_snapshot(cache, row)
        extracted, removed = sections(article["text"])
        for index, (heading, text) in enumerate(extracted):
            documents.append({"document_id": f"gr-{row['pageid']}-{index:04d}",
                "title": f"{row['title']} — {heading}", "source": row["url"], "license": LICENSE,
                "text": text})
        report.append({**row, "sections": len(extracted), "removed": removed})
    output.mkdir(parents=True)
    spool = output / "documents.jsonl"
    spool.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in documents), encoding="utf-8")
    artifacts = build_pack_streaming(spool, output / "pack", pack_id, version,
        title, LICENSE, [r["url"] for r in lock["sources"]] + [LICENSE_URL],
        coverage_summary="Selected foundational articles in science, computing, history and society. Development pilot; coverage is limited.",
        example_questions=[], coverage_level="focused", fts_tokenizer="porter unicode61",
        chunker=paragraph_chunks if preserve_paragraphs else chunk_document)
    verify_pack(artifacts.pack)
    write_atomic(output / "topics.json", topics.read_bytes())
    write_atomic(output / "sources.lock.json", canonical(lock))
    write_atomic(output / "quality-report.json", canonical({"status": "BUILT_NOT_ANSWER_VALIDATED",
        "articles": len(report), "sections": len(documents), "sources": report,
        "chunk_policy": "whole-paragraph-v1" if preserve_paragraphs else "legacy-1200-overlap150",
        "build_identity": {"builder_sha256": digest(Path(__file__).read_bytes()),
            "topics_sha256": lock["topics_sha256"], "python": sys.version,
            "sqlite": sqlite3.sqlite_version, "beautifulsoup": bs4.__version__},
        "documents_sha256": digest(spool.read_bytes()), "pack_sha256": digest(artifacts.pack.read_bytes()),
        "limitations": ["Selected prose only; tables, images and math-containing paragraphs omitted.",
            "Whole paragraphs retained; cross-section context can still be missing." if preserve_paragraphs else
            "Existing pack chunking can split long paragraphs; no full-procedure preservation guarantee.",
            "No phone retrieval, answer-quality or safety certification follows from successful packaging."]}))
    (output / "ATTRIBUTION.txt").write_text(
        f"{title} — adapted Wikipedia text by Wikipedia contributors.\n"
        f"License: {LICENSE} {LICENSE_URL}\n"
        "Changes: selected sections, removed references/navigation/media/tables/math paragraphs; whitespace normalized and text chunked.\n"
        "Revision links below provide article history and contributor attribution. No endorsement implied.\n\n" +
        "\n".join(f"{r['title']} | {r['timestamp']} | {r['url']}" for r in lock["sources"]) + "\n", encoding="utf-8")
    print(f"Built {artifacts.pack}: {len(report)} articles, {len(documents)} sections. Answer quality remains unscored.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["fetch", "build"])
    parser.add_argument("--topics", type=Path, default=ROOT / "tools/general_reference_topics.json")
    parser.add_argument("--cache", type=Path, default=ROOT / "build/general-reference/snapshot")
    parser.add_argument("--output", type=Path, default=ROOT / "build/general-reference/pilot")
    parser.add_argument("--version", default="0.1.0")
    parser.add_argument("--pack-id", default="general-reference-pilot")
    parser.add_argument("--title", default="General Reference · Pilot")
    parser.add_argument("--preserve-paragraphs", action="store_true", help="Candidate chunker: whole source paragraphs, no character overlap")
    parser.add_argument("--seed-cache", type=Path, help="For fetch only: copy matching verified revisions into a new cache before fetching additions")
    args = parser.parse_args()
    if args.seed_cache:
        if args.action != "fetch":
            parser.error("--seed-cache requires fetch")
        seed_snapshot(args.topics, args.cache, args.seed_cache)
    if args.action == "fetch":
        fetch(args.topics, args.cache)
    else:
        build(args.topics, args.cache, args.output, args.version, args.preserve_paragraphs,
              args.pack_id, args.title)


if __name__ == "__main__":
    main()
