#!/usr/bin/env python3
"""Build a keyword-searchable encyclopedia pack from a Wikipedia CirrusSearch dump.

Two steps keep the large download and the build separate:

  spool  Stream a CirrusSearch "content" dump (gzip JSON lines) from a URL or file without
         saving it. Records the dump's SHA-256 and size, and keeps a compressed spool of
         what the pack can use: each article's lead section, its redirects as aliases, and
         the opening of the body for widely linked articles. Disambiguation pages and empty
         leads are skipped.
  build  Offline and deterministic. Chooses articles by incoming links, most linked first,
         until a text budget is reached, then writes an "Article — Overview" passage for each
         lead and an "Article — Details" passage for each body excerpt. Sources link to the
         exact revision (oldid) that was dumped.

Keyword search only: no embeddings, so a rebuild takes the time of one pass over the dump.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import re
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from stream_pack import build_pack_streaming  # noqa: E402

PACK_ID = "wikipedia-en"
LICENSE = "CC-BY-SA-4.0"
USER_AGENT = "FieldAtlas-pack-builder/0.1 (+https://github.com/0x94t3z/fieldatlas)"
MIN_LEAD_CHARS = 80
BODY_MIN_LINKS = 300      # widely linked articles also keep part of their body
BODY_CHARS = 6000
MAX_ALIASES = 8

# Some dumps carry template failures rendered as text. A failing Nihongo template stands in
# for the subject's name at the start of a lead ("<error> is the capital city of Hokkaido"),
# so there the article title takes its place; elsewhere the affected sentence is dropped.
PACKAGE_ERROR = re.compile(r"Lua error in package\.lua at line \d+: [^.]*\.\s*")
SCRIPT_ERROR = re.compile(r"Lua error in \S+ at line \d+: [^.()]*(?:\([^)]*\))?\.")
SENTENCE_END = re.compile(r"(?<=[.!?])\s+")


class HashingReader(io.RawIOBase):
    """Passes bytes through while hashing them, so the dump's SHA-256 needs no second read."""

    def __init__(self, raw):
        self.raw, self.sha, self.bytes = raw, hashlib.sha256(), 0

    def readable(self):
        return True

    def readinto(self, buffer):
        data = self.raw.read(len(buffer))
        if not data:
            return 0
        self.sha.update(data)
        self.bytes += len(data)
        buffer[:len(data)] = data
        return len(data)


def is_disambiguation(doc: dict) -> bool:
    templates = doc.get("template") or []
    return doc.get("title", "").endswith("(disambiguation)") or any(
        "disambiguation" in t.lower() or t in {"Template:Dmbox", "Template:Set index article"} for t in templates)


def clean_text(text: str, title: str) -> str:
    if "Lua error" not in text:
        return text
    text = PACKAGE_ERROR.sub("", text)
    leading = SCRIPT_ERROR.match(text)
    if leading:
        text = title + text[leading.end():]
    text = SCRIPT_ERROR.sub("\0", text)
    return " ".join(part for part in SENTENCE_END.split(text) if "\0" not in part).strip()


def spool_record(doc: dict, body_min_links: int = BODY_MIN_LINKS, body_chars: int = BODY_CHARS) -> dict | None:
    if doc.get("namespace") != 0 or is_disambiguation(doc):
        return None
    lead = clean_text(" ".join((doc.get("opening_text") or "").split()), doc.get("title", ""))
    if len(lead) < MIN_LEAD_CHARS:
        return None
    links = int(doc.get("incoming_links") or 0)
    aliases = []
    for redirect in doc.get("redirect") or []:
        title = (redirect.get("title") or "").strip()
        if redirect.get("namespace") == 0 and title and title.lower() != doc["title"].lower() and title not in aliases:
            aliases.append(title)
        if len(aliases) >= MAX_ALIASES:
            break
    record = {"id": int(doc["page_id"]), "rev": int(doc.get("version") or 0), "title": doc["title"],
              "links": links, "aliases": aliases, "lead": lead}
    if links >= body_min_links:
        text = clean_text(" ".join((doc.get("text") or "").split()), doc["title"])
        # The plain text repeats the lead first; keep what follows it.
        body = text[len(lead):] if text.startswith(lead[:200]) else text
        body = body.strip()[:body_chars]
        cut = body.rfind(". ")
        if cut > body_chars // 2:
            body = body[:cut + 1]
        if len(body) >= 200:
            record["body"] = body
    return record


def spool(args: argparse.Namespace) -> int:
    source = args.source
    if source.startswith(("http://", "https://")):
        raw = urllib.request.urlopen(urllib.request.Request(source, headers={"User-Agent": USER_AGENT}), timeout=120)
    else:
        raw = open(source, "rb")
    hashing = HashingReader(raw)
    stream = gzip.GzipFile(fileobj=io.BufferedReader(hashing, buffer_size=1 << 20))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    kept = seen = 0
    started = time.time()
    with gzip.open(args.out, "wt", encoding="utf-8", compresslevel=3) as out:
        for line in io.TextIOWrapper(stream, encoding="utf-8"):
            if line.startswith('{"index"'):
                continue
            seen += 1
            try:
                record = spool_record(json.loads(line), args.body_min_links, args.body_chars)
            except (ValueError, KeyError, TypeError):
                continue
            if record is not None:
                out.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
                kept += 1
            if seen % 200_000 == 0:
                print(f"{seen} pages, {kept} kept, {hashing.bytes / 1e9:.1f} GB read, "
                      f"{time.time() - started:.0f}s", flush=True)
    raw.close()
    meta = {"source": source, "sha256": hashing.sha.hexdigest(), "bytes": hashing.bytes,
            "pages": seen, "kept": kept, "spool_sha256": file_sha256(args.out)}
    args.out.with_suffix(".meta.json").write_text(json.dumps(meta, indent=2) + "\n")
    print(json.dumps(meta))
    return 0


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def select(spool_path: Path, text_budget: int) -> dict[int, bool]:
    """Page ids to keep, mapped to whether the body excerpt fits too. Most linked articles
    come first until the budget; ties break by page id. Only a small index is held in memory."""
    index = []
    with gzip.open(spool_path, "rt", encoding="utf-8") as handle:
        for line in handle:
            record = json.loads(line)
            index.append((-record["links"], record["id"], len(record["lead"]), len(record.get("body", ""))))
    index.sort()
    chosen, used = {}, 0
    for _, page, lead, body in index:
        if used + lead + body <= text_budget:
            chosen[page] = body > 0
            used += lead + body
        elif used + lead <= text_budget:
            chosen[page] = False
            used += lead
    return chosen


def documents(spool_path: Path, chosen: dict[int, bool], host: str = "en.wikipedia.org", prefix: str = "wp"):
    with gzip.open(spool_path, "rt", encoding="utf-8") as handle:
        for line in handle:
            record = json.loads(line)
            keep_body = chosen.get(record["id"])
            if keep_body is None:
                continue
            source = f"https://{host}/w/index.php?oldid={record['rev']}"
            # Spools written before clean_text existed are cleaned here; it is idempotent.
            lead = clean_text(record["lead"], record["title"])
            if not lead:
                continue
            if record["aliases"]:
                # A paragraph of its own: the app quotes the lead paragraph verbatim and only
                # accepts it when it ends on a complete sentence.
                lead += "\n\nAlso known as: " + "; ".join(record["aliases"])
            yield {"document_id": f"{prefix}-{record['id']}-0000", "title": f"{record['title']} — Overview",
                   "source": source, "license": LICENSE, "text": lead}
            if keep_body and record.get("body"):
                yield {"document_id": f"{prefix}-{record['id']}-0001", "title": f"{record['title']} — Details",
                       "source": source, "license": LICENSE, "text": clean_text(record["body"], record["title"])}


def build(args: argparse.Namespace) -> int:
    meta = json.loads(args.spool.with_suffix(".meta.json").read_text())
    chosen = select(args.spool, args.text_budget)
    if not chosen:
        raise SystemExit("spool is empty")
    args.out.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".jsonl", prefix="wikipedia-",
                                     dir=args.out, delete=False) as handle:
        spool_path = Path(handle.name)
        count = 0
        for document in documents(args.spool, chosen, args.host, args.id_prefix):
            handle.write(json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n")
            count += 1
    try:
        artifacts = build_pack_streaming(
            input_path=spool_path,
            output_dir=args.out / args.pack_id,
            pack_id=args.pack_id,
            version=args.version,
            title=args.title,
            license_id=LICENSE,
            source_urls=[meta["source"], f"https://{args.host}/wiki/{args.copyright_page}"],
            coverage_summary=args.coverage.format(count=f"{len(chosen):,}"),
            example_questions=args.example,
            coverage_level="broad",
            fts_tokenizer="porter unicode61",
        )
    finally:
        spool_path.unlink(missing_ok=True)
    (artifacts.pack.parent / "SOURCE.json").write_text(json.dumps(
        {**meta, "articles": len(chosen), "documents": count, "text_budget": args.text_budget}, indent=2) + "\n")
    print(f"{len(chosen)} articles, {count} documents -> {artifacts.pack}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    s = commands.add_parser("spool", help="stream a CirrusSearch content dump into a compact spool (network)")
    s.add_argument("--source", required=True, help="dump URL or local .json.gz path")
    s.add_argument("--out", required=True, type=Path, help="spool path, e.g. build/wikipedia/enwiki.spool.jsonl.gz")
    s.add_argument("--body-min-links", type=int, default=BODY_MIN_LINKS, help="incoming links needed to keep body text")
    s.add_argument("--body-chars", type=int, default=BODY_CHARS)
    b = commands.add_parser("build", help="build the pack from a spool (offline)")
    b.add_argument("--spool", required=True, type=Path)
    b.add_argument("--out", required=True, type=Path)
    b.add_argument("--version", required=True)
    b.add_argument("--text-budget", type=int, default=2_000_000_000, help="characters of article text")
    b.add_argument("--pack-id", default=PACK_ID)
    b.add_argument("--title", default="Encyclopedia (Wikipedia)")
    b.add_argument("--host", default="en.wikipedia.org", help="site for revision links")
    b.add_argument("--id-prefix", default="wp", help="document id prefix")
    b.add_argument("--copyright-page", default="Wikipedia:Copyrights",
                   help="the site's licence page, e.g. Wikivoyage:Copyleft")
    b.add_argument("--coverage", default="Lead sections of {count} English Wikipedia articles, most linked first, "
                   "with longer excerpts for widely linked topics. A snapshot; not live.")
    b.add_argument("--example", action="append", default=None, help="example question (repeatable)")
    args = parser.parse_args()
    if args.command == "build" and not args.example:
        args.example = ["Tell me about Japan's history", "What is photosynthesis?", "Compare mitosis and meiosis"]
    return spool(args) if args.command == "spool" else build(args)


if __name__ == "__main__":
    raise SystemExit(main())
