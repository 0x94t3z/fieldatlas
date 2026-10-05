#!/usr/bin/env python3
"""Build a keyword-searchable pack of whole Wikivoyage guides, one passage per section.

The input is the pinned English Wikivoyage pages-articles dump that the places packs use. Each
destination becomes "<Destination> — Overview" for its lead and "<Destination> — <Section>" or
"<Destination> — <Section> > <Subsection>" for the rest ("Japan — Buy > Money", "Hanoi — Stay
safe"), so a question about cash in Japan or crossing the road in Hanoi can reach the section that
answers it. The earlier CirrusSearch build kept only each guide's opening, as one flat passage.

Wikitext is reduced to readable prose: links keep their label, warning and caution boxes keep
their text, and transport, safety and practical sections keep their listings as one line each
("Narita Express: ..."). Listings in See, Do, Eat, Drink, Sleep and Buy are left to the places
pack, which already holds them with addresses and coordinates. Keyword search only.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_wikivoyage_eat_pack import LICENSE, child_text, local_tag, split_fields, template_spans  # noqa: E402
from stream_pack import build_pack_streaming  # noqa: E402

DUMP_URL = "https://dumps.wikimedia.org/enwikivoyage/20260901/enwikivoyage-20260901-pages-articles.xml.bz2"
DUMP_SHA256 = "c6cebf6b109c31698e736858fd1d8dec1c41d87437aa4c4cafd7b0df88777773"
PACK_ID = "wikivoyage-guides"
MIN_SECTION_CHARS = 80

LISTING_KINDS = {"see", "do", "eat", "drink", "sleep", "buy", "go", "listing", "vcard"}
# ISO 4217 codes: Wikivoyage writes prices as {{EUR|25}}, and a bare {{EUR}} as the currency name.
CURRENCY_CODES = set("""aed afn all amd ang aoa ars aud awg azn bam bbd bdt bgn bhd bif bmd bnd bob brl bsd btn bwp byn bzd cad
cdf chf clp cny cop crc cuc cup cve czk djf dkk dop dzd egp ern etb eur fjd fkp gbp gel ghs gip gmd gnf gtq gyd hkd hnl hrk htg
huf idr ils inr iqd irr isk jmd jod jpy kes kgs khr kmf kpw krw kwd kyd kzt lak lbp lkr lrd lsl lyd mad mdl mga mkd mmk mnt mop
mru mur mvr mwk mxn myr mzn nad ngn nio nok npr nzd omr pab pen pgk php pkr pln pyg qar ron rsd rub rwf sar sbd scr sdg sek sgd
shp sle sll sos srd ssp stn svc syp szl thb tjs tmt tnd top try ttd twd tzs uah ugx usd uyu uzs ves vnd vuv wst xaf xcd xof xpf
yer zar zmw zwl""".split())
# Sections whose listings the places pack already carries; their prose still counts.
PLACE_SECTIONS = {"see", "do", "eat", "drink", "sleep", "buy"}
# Display templates whose argument is the readable text.
UNIT_TEMPLATES = {"km": "km", "mi": "mi", "mile": "miles", "m": "m", "ft": "ft", "kg": "kg", "km/h": "km/h", "mph": "mph",
                  "km2": "km²", "acre": "acres", "c": "°C", "f": "°F"}
TEXT_TEMPLATES = {"warningbox", "cautionbox", "infobox", "pull quote", "quote", "iata", "icao", "nowrap", "lang",
                  "smallcaps", "sic", "nobr", "flag", "unbulleted list", "station", "phone", "pron"}
NOT_GUIDES = re.compile(r"{{\s*(disamb|disambiguation|dab|surname|title index page|vfd|delete|redirect)\b", re.I)

LINK = re.compile(r"\[\[([^\[\]|]*\|)?([^\[\]]*)]]")
FILE_LINK = re.compile(r"\[\[\s*(?:file|image|category|[a-z]{2,3}(?:-[a-z]+)?)\s*:[^\[\]]*(?:\[\[[^\[\]]*]][^\[\]]*)*]]", re.I)
EXTERNAL_LINK = re.compile(r"\[(?:https?:)?//\S+(?:\s+([^\]]*))?]")
BARE_URL = re.compile(r"https?://\S+")
REF = re.compile(r"<ref[^>/]*/>|<ref[^>]*>.*?</ref>", re.S | re.I)
COMMENT = re.compile(r"<!--.*?(?:-->|\Z)", re.S)
MAP_DATA = re.compile(r"<(maplink|mapframe|gallery|graph)\b[^>]*>.*?</\1>", re.S | re.I)
TAG = re.compile(r"</?[a-z][^>]*>", re.I)
TABLE = re.compile(r"^\{\|.*?^\|}", re.S | re.M)
HEADING = re.compile(r"^(={2,6})\s*(.*?)\s*\1\s*$", re.M)


def template_name(body: str) -> str:
    return split_fields(body)[0].strip().lower().replace("_", " ")


def fields_of(body: str) -> tuple[list[str], dict[str, str]]:
    positional, named = [], {}
    for part in split_fields(body)[1:]:
        key, separator, value = part.partition("=")
        if separator and re.fullmatch(r"\s*[\w -]+\s*", key):
            named[key.strip().lower()] = value.strip()
        else:
            positional.append(part.strip())
    return positional, named


def render_template(body: str, keep_listings: bool) -> str:
    name = template_name(body)
    positional, named = fields_of(body)
    if name in LISTING_KINDS:
        if not keep_listings or not named.get("name"):
            return ""
        parts = [named["name"]]
        for key in ("alt", "directions", "content"):
            if named.get(key):
                parts.append(named[key])
        return "\n- " + ": ".join([parts[0], " ".join(parts[1:])]).rstrip(": ") + "\n"
    if name == "marker":
        # In "Cities" and "Other destinations" lists the marker is the name the line describes.
        return named.get("name", "")
    if name in CURRENCY_CODES:
        amount = positional[0] if positional else ""
        return f"{name.upper()} {amount}".strip()
    if name in UNIT_TEMPLATES and positional:
        unit = UNIT_TEMPLATES[name]
        return f"{positional[0]}{unit}" if unit.startswith("°") else f"{positional[0]} {unit}"
    if name == "convert" and len(positional) >= 2:
        return f"{positional[0]} {positional[1]}"
    if name == "frac" and positional:
        return {1: lambda p: f"1/{p[0]}", 2: lambda p: f"{p[0]}/{p[1]}"}.get(len(positional), lambda p: f"{p[0]} {p[1]}/{p[2]}")(positional)
    if name == "rint" and positional:
        return positional[-1].upper()
    if name in TEXT_TEMPLATES:
        # Box text can be positional or the named "1=" / "text=" parameter.
        text = " ".join(p for p in positional if p) or named.get("1", "") or named.get("text", "")
        if name in {"warningbox", "cautionbox"}:
            headline = named.get("boldtext", "")
            text = f"{headline}. {text}".strip(". ") if headline else text
            return f"\n{'Warning' if name == 'warningbox' else 'Caution'}: {text}\n" if text else ""
        return text
    return ""


def expand_templates(text: str, keep_listings: bool) -> str:
    """Replace each outermost template with its readable text, innermost first."""
    for _ in range(6):
        spans = []
        for body in template_spans(text):
            if "{{" not in body:
                spans.append(body)
        if not spans:
            break
        for body in set(spans):
            text = text.replace("{{" + body + "}}", render_template(body, keep_listings))
    return text.replace("{{", "").replace("}}", "")


def drop_file_links(text: str) -> str:
    """Remove [[File:...]] and [[Image:...]] with any captions, however deeply their links nest."""
    out, i = [], 0
    opener = re.compile(r"\[\[\s*(?:file|image)\s*:", re.I)
    while True:
        m = opener.search(text, i)
        if not m:
            out.append(text[i:])
            return "".join(out)
        out.append(text[i:m.start()])
        depth, j = 0, m.start()
        while j < len(text):
            if text.startswith("[[", j):
                depth += 1; j += 2
            elif text.startswith("]]", j):
                depth -= 1; j += 2
                if depth == 0:
                    break
            else:
                j += 1
        i = j


def clean(wikitext: str, keep_listings: bool) -> str:
    text = COMMENT.sub("", wikitext)
    text = MAP_DATA.sub("", text)
    text = REF.sub("", text)
    text = TABLE.sub("", text)
    text = FILE_LINK.sub("", text)
    text = expand_templates(text, keep_listings)
    text = drop_file_links(text)
    text = LINK.sub(lambda m: m.group(2), text)
    text = EXTERNAL_LINK.sub(lambda m: m.group(1) or "", text)
    text = BARE_URL.sub("", text)
    text = TAG.sub("", text)
    text = html.unescape(text).replace("'''", "").replace("''", "")
    lines = []
    for line in text.splitlines():
        line = re.sub(r"[ \t]+", " ", line).strip()
        if not line or line.startswith(("__", "|", "!")):
            lines.append("")
            continue
        bullet = re.match(r"^[*#:;]+\s*", line)
        if bullet:
            line = "- " + line[bullet.end():]
            if line == "- ":
                continue
        lines.append(line)
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()


def sections(wikitext: str):
    """(path, wikitext) for the lead and every section, path being () or ("Buy", "Money")."""
    matches = list(HEADING.finditer(wikitext))
    yield (), wikitext[: matches[0].start() if matches else len(wikitext)]
    trail: list[tuple[int, str]] = []
    for index, match in enumerate(matches):
        level = min(len(match.group(1)), 4)
        heading = clean(match.group(2), keep_listings=False).strip(" :=")
        trail = [(lvl, name) for lvl, name in trail if lvl < level] + [(level, heading)]
        end = matches[index + 1].start() if index + 1 < len(matches) else len(wikitext)
        yield tuple(name for _, name in trail), wikitext[match.end():end]


def page_documents(page: ET.Element):
    if child_text(page, "ns") != "0" or any(local_tag(child.tag) == "redirect" for child in page):
        return
    page_id = child_text(page, "id")
    destination = child_text(page, "title")
    revision = next((child for child in page if local_tag(child.tag) == "revision"), None)
    if not page_id.isdigit() or not destination or revision is None:
        return
    wikitext = child_text(revision, "text")
    if not wikitext or wikitext.lstrip().lower().startswith("#redirect") or NOT_GUIDES.search(wikitext):
        return
    source = f"https://en.wikivoyage.org/w/index.php?oldid={child_text(revision, 'id')}"
    for number, (path, body) in enumerate(sections(wikitext)):
        keep_listings = not path or path[0].lower() not in PLACE_SECTIONS
        text = clean(body, keep_listings)
        if len(text) < MIN_SECTION_CHARS:
            continue
        name = " > ".join(path) if path else "Overview"
        yield {
            "document_id": f"wvs-{page_id}-{number:04d}",
            "title": f"{destination} — {name}",
            "source": source,
            "license": LICENSE,
            "text": text,
        }


def dump_documents(path: Path):
    from bz2 import open as bz2_open

    opener = bz2_open if path.suffix == ".bz2" else open
    with opener(path, "rb") as stream:
        for _, element in ET.iterparse(stream, events=("end",)):
            if local_tag(element.tag) == "page":
                yield from page_documents(element)
                element.clear()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dump", required=True, type=Path, help="pages-articles XML or XML.bz2")
    parser.add_argument("--out", required=True, type=Path, help="output directory")
    parser.add_argument("--version", required=True, help="snapshot version, e.g. 2026.09.01")
    parser.add_argument("--source-url", default=DUMP_URL)
    parser.add_argument("--expected-sha256", default=DUMP_SHA256)
    parser.add_argument("--sample", help="print the passages of one destination and stop")
    args = parser.parse_args()
    if args.sample:
        seen = False
        for document in dump_documents(args.dump):
            if document["title"].split(" — ")[0] == args.sample:
                seen = True
                print(f"## {document['title']}\n{document['text']}\n")
            elif seen:
                break
        return 0
    with args.dump.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if digest != args.expected_sha256:
        parser.error(f"dump SHA-256 mismatch: got {digest}")
    args.out.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".jsonl", prefix="wikivoyage-guides-",
                                     dir=args.out, delete=False) as spool:
        spool_path = Path(spool.name)
        count, guides = 0, set()
        for document in dump_documents(args.dump):
            spool.write(json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n")
            count += 1
            guides.add(document["document_id"].split("-")[1])
    try:
        artifacts = build_pack_streaming(
            input_path=spool_path,
            output_dir=args.out / PACK_ID,
            pack_id=PACK_ID,
            version=args.version,
            title="Travel guides (Wikivoyage)",
            license_id=LICENSE,
            source_urls=[args.source_url, "https://en.wikivoyage.org/wiki/Wikivoyage:Copyleft"],
            coverage_summary=f"{len(guides):,} whole Wikivoyage destination guides by section: understand, get in, "
                             "get around, buy, stay safe, stay healthy, respect, cope. A snapshot; not live.",
            example_questions=[
                "How do I get around Tokyo?",
                "Is it safe to walk at night in Bangkok?",
                "What should I know before visiting Chiang Mai?",
            ],
            coverage_level="broad",
            fts_tokenizer="porter unicode61",
        )
    finally:
        spool_path.unlink(missing_ok=True)
    (artifacts.pack.parent / "SOURCE.json").write_text(json.dumps(
        {"source": args.source_url, "sha256": digest, "guides": len(guides), "documents": count}, indent=2) + "\n")
    print(f"{len(guides)} guides, {count} section passages -> {artifacts.pack}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
