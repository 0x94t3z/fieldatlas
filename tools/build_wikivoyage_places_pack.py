#!/usr/bin/env python3
"""Build a keyword-searchable offline pack of Wikivoyage travel listings.

The input is an English Wikivoyage pages-articles dump. This intentionally uses
FTS5 only: embedding a whole travel corpus is not required to make it usable on
a phone. See, Do, Eat, Drink, Sleep, and Buy listings become separate documents.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_wikivoyage_eat_pack import (  # noqa: E402
    DUMP_URL,
    LICENSE,
    child_text,
    local_tag,
    plain,
    split_fields,
    template_spans,
)

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from stream_pack import build_pack_streaming  # noqa: E402


KINDS = frozenset({"see", "do", "eat", "drink", "sleep", "buy"})


def place_listings(wikitext: str):
    for template in template_spans(wikitext):
        parts = split_fields(template)
        kind = parts[0].strip().lower()
        fields: dict[str, str] = {}
        for part in parts[1:]:
            key, separator, value = part.partition("=")
            if separator:
                fields[key.strip().lower()] = plain(value)
        if kind == "listing":
            kind = fields.get("type", "").lower()
        if kind in KINDS and fields.get("name"):
            yield kind, fields


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
    for index, (kind, fields) in enumerate(place_listings(wikitext), 1):
        name = fields["name"]
        lines = [f"Destination: {destination}", f"Category: {kind.title()}", f"Place: {name}"]
        for field, label in (
            ("address", "Address"),
            ("directions", "Directions"),
            ("content", "Description"),
            ("hours", "Hours in source"),
            ("price", "Price in source"),
            ("lat", "Latitude"),
            ("long", "Longitude"),
            ("lastedit", "Listing last checked"),
        ):
            if fields.get(field):
                lines.append(f"{label}: {fields[field]}")
        lines.append(f"Source page revision: {revised}")
        yield {
            "document_id": f"wv-place-{page_id}-{index:04d}",
            "title": f"{destination} — {name}",
            "source": source,
            "license": LICENSE,
            "text": "\n".join(lines),
        }


def dump_documents(path: Path):
    from bz2 import open as bz2_open

    opener = bz2_open if path.suffix == ".bz2" else open
    with opener(path, "rb") as source:
        for _, page in ET.iterparse(source, events=("end",)):
            if local_tag(page.tag) == "page":
                yield from page_documents(page)
                page.clear()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dump", required=True, type=Path, help="pages-articles XML or XML.bz2")
    parser.add_argument("--out", required=True, type=Path, help="output directory")
    parser.add_argument("--version", required=True, help="snapshot version, e.g. 2026.09.1")
    parser.add_argument("--source-url", default=DUMP_URL, help="immutable URL of the input dump")
    parser.add_argument("--expected-sha256", help="verify the dump before processing")
    parser.add_argument("--keep-spool", action="store_true", help="retain generated JSONL")
    args = parser.parse_args()
    if not args.dump.is_file():
        parser.error("dump not found")
    with args.dump.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if args.expected_sha256 and digest.lower() != args.expected_sha256.lower():
        parser.error(f"dump SHA-256 mismatch: got {digest}")
    args.out.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", suffix=".jsonl", prefix="wikivoyage-places-",
        dir=args.out, delete=False,
    ) as spool:
        spool_path = Path(spool.name)
        count = 0
        for document in dump_documents(args.dump):
            spool.write(json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n")
            count += 1
    if count == 0:
        spool_path.unlink()
        raise SystemExit("No travel listings found; check the input dump")
    try:
        artifacts = build_pack_streaming(
            input_path=spool_path,
            output_dir=args.out / "wikivoyage-places",
            pack_id="wikivoyage-places",
            version=args.version,
            title="Wikivoyage places",
            license_id=LICENSE,
            source_urls=[args.source_url, "https://en.wikivoyage.org/wiki/Wikivoyage:Copyleft"],
            coverage_summary="Dated travel places: restaurants, cafes, hotels, museums, sights, shopping, nightlife, and things to do; details may be stale.",
            example_questions=[
                "Which museums are listed in Berlin?",
                "Which cafes are listed in Chiang Mai?",
                "Which hotels are listed in Jakarta?",
            ],
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
