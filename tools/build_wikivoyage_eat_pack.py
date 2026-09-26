#!/usr/bin/env python3
"""Build a searchable offline pack of Wikivoyage restaurant listings from a pages dump.

The input is enwikivoyage's pages-articles XML (plain or .bz2). Each Eat listing
becomes one small document with its destination, name, description, page revision,
and any listing update date. The phone only reads the resulting local .fapack.
"""

from __future__ import annotations

import argparse
import bz2
import hashlib
import html
import json
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from stream_pack import build_pack_streaming


DUMP_URL = "https://dumps.wikimedia.org/enwikivoyage/latest/enwikivoyage-latest-pages-articles.xml.bz2"
LICENSE = "CC-BY-SA-4.0"
LINK = re.compile(r"\[\[(?:[^]|]+\|)?([^]]+)]]")
EXTERNAL_LINK = re.compile(r"\[https?://\S+\s+([^]]+)]]")
TAG = re.compile(r"<[^>]*>")
SPACE = re.compile(r"\s+")


def plain(value: str) -> str:
    value = html.unescape(value)
    value = LINK.sub(r"\1", value)
    value = EXTERNAL_LINK.sub(r"\1", value)
    value = TAG.sub(" ", value)
    value = value.replace("'''", "").replace("''", "")
    # Preserve the readable part of simple nested display templates. Deeply nested
    # wiki markup is omitted rather than mistaken for a restaurant claim.
    for _ in range(3):
        updated = re.sub(r"{{[^{}]*}}", " ", value)
        if updated == value:
            break
        value = updated
    return SPACE.sub(" ", value).strip(" |\n\t")


def template_spans(text: str):
    """Yield complete templates, including ones with nested templates in their fields."""
    stack: list[int] = []
    i = 0
    while i < len(text) - 1:
        pair = text[i:i + 2]
        if pair == "{{":
            stack.append(i)
            i += 2
        elif pair == "}}" and stack:
            start = stack.pop()
            yield text[start + 2:i]
            i += 2
        else:
            i += 1


def split_fields(template: str) -> list[str]:
    """Split only on top-level pipes, respecting nested templates and wikilinks."""
    parts: list[str] = []
    start = i = depth = links = 0
    while i < len(template):
        pair = template[i:i + 2]
        if pair == "{{":
            depth += 1
            i += 2
        elif pair == "}}" and depth:
            depth -= 1
            i += 2
        elif pair == "[[":
            links += 1
            i += 2
        elif pair == "]]" and links:
            links -= 1
            i += 2
        elif template[i] == "|" and not depth and not links:
            parts.append(template[start:i].strip())
            start = i = i + 1
        else:
            i += 1
    parts.append(template[start:].strip())
    return parts


def eat_listings(wikitext: str):
    for template in template_spans(wikitext):
        parts = split_fields(template)
        kind = parts[0].strip().lower()
        if kind not in ("eat", "listing"):
            continue
        fields: dict[str, str] = {}
        for part in parts[1:]:
            key, separator, value = part.partition("=")
            if separator:
                fields[key.strip().lower()] = plain(value)
        if kind == "listing" and fields.get("type", "").lower() != "eat":
            continue
        name = fields.get("name", "")
        if name:
            yield fields


def local_tag(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child_text(element: ET.Element, name: str) -> str:
    return next((child.text or "" for child in element if local_tag(child.tag) == name), "")


def page_documents(page: ET.Element):
    if child_text(page, "ns") != "0":
        return
    page_id = child_text(page, "id")
    destination = child_text(page, "title")
    revision = next((child for child in page if local_tag(child.tag) == "revision"), None)
    if not page_id.isdigit() or not destination or revision is None:
        return
    revision_id = child_text(revision, "id")
    revised = child_text(revision, "timestamp")[:10]
    wikitext = child_text(revision, "text")
    source = f"https://en.wikivoyage.org/w/index.php?oldid={revision_id}"
    for index, fields in enumerate(eat_listings(wikitext), 1):
        name = fields["name"]
        lines = [f"Destination: {destination}", f"Place to eat: {name}"]
        for field, label in (
            ("address", "Address"),
            ("directions", "Directions"),
            ("content", "Description"),
            ("price", "Price in source"),
            ("lastedit", "Listing last checked"),
        ):
            if fields.get(field):
                lines.append(f"{label}: {fields[field]}")
        lines.append(f"Source page revision: {revised}")
        yield {
            "document_id": f"wv-eat-{page_id}-{index:04d}",
            "title": f"{destination} — {name}",
            "source": source,
            "license": LICENSE,
            "text": "\n".join(lines),
        }


def dump_documents(path: Path):
    opener = bz2.open if path.suffix == ".bz2" else open
    with opener(path, "rb") as source:
        for _, page in ET.iterparse(source, events=("end",)):
            if local_tag(page.tag) == "page":
                yield from page_documents(page)
                page.clear()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dump", required=True, type=Path, help="enwikivoyage pages-articles XML or XML.bz2")
    parser.add_argument("--out", required=True, type=Path, help="output directory for the pack")
    parser.add_argument("--version", required=True, help="snapshot version, e.g. 2026.09.1")
    parser.add_argument("--source-url", default=DUMP_URL, help="immutable URL of the input dump")
    parser.add_argument("--expected-sha256", help="verify the dump before processing")
    parser.add_argument("--keep-spool", action="store_true", help="retain generated JSONL for inspection")
    args = parser.parse_args()
    if not args.dump.is_file():
        parser.error(f"dump not found: {args.dump}")
    with args.dump.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if args.expected_sha256 and digest.lower() != args.expected_sha256.lower():
        parser.error(f"dump SHA-256 mismatch: got {digest}")
    args.out.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", suffix=".jsonl", prefix="wikivoyage-eat-",
        dir=args.out, delete=False,
    ) as spool:
        spool_path = Path(spool.name)
        count = 0
        for document in dump_documents(args.dump):
            spool.write(json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n")
            count += 1
    if count == 0:
        spool_path.unlink()
        raise SystemExit("No Eat listings found; check that the input is an enwikivoyage pages dump")
    try:
        artifacts = build_pack_streaming(
            input_path=spool_path,
            output_dir=args.out / "wikivoyage-eat",
            pack_id="wikivoyage-eat",
            version=args.version,
            title="Wikivoyage places to eat",
            license_id=LICENSE,
            source_urls=[args.source_url, "https://en.wikivoyage.org/wiki/Wikivoyage:Copyleft"],
            coverage_summary="Named places to eat from English Wikivoyage; listings may be out of date.",
            example_questions=["Which vegan restaurants are listed in Chiang Mai?"],
            coverage_level="focused",
            fts_tokenizer="porter unicode61",
        )
    finally:
        if not args.keep_spool:
            spool_path.unlink(missing_ok=True)
    (artifacts.pack.parent / "SOURCE-DUMP-SHA256").write_text(
        f"{digest}  {args.dump.name}\n", encoding="utf-8",
    )
    print(f"{count} listings -> {artifacts.pack}")
    print(f"Input dump SHA-256: {digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
