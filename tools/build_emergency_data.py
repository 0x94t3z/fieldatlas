#!/usr/bin/env python3
"""Snapshot pinned emergency-guidance sources, then build verbatim offline JSON assets.

Outputs (default build/emergency/):
  guides.json   Curated emergency guides. Text is selected from the source, never rewritten.
  numbers.json  Emergency telephone numbers per ISO 3166-1 alpha-2 country code.

`fetch` downloads each source once, sequentially and politely, into a content-addressed
cache with a lock file (URL, retrieval time, HTTP status, SHA-256). `build` never touches
the network: it verifies every cached byte against the lock before extracting text.
`outline` prints the heading/paragraph structure of a cached source so maintainers can
choose sections in tools/emergency_sources.json without guessing.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import sys
import time
import urllib.parse
import urllib.request

from bs4 import BeautifulSoup, NavigableString, Tag

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCES = ROOT / "tools/emergency_sources.json"
DEFAULT_OUT = ROOT / "build/emergency"
API = "https://en.wikipedia.org/w/api.php"
USER_AGENT = "FieldAtlasEmergencyBuilder/1.0 (https://github.com/0x94t3z/fieldatlas)"
MAX_RESPONSE = 8 * 1024 * 1024
FETCH_DELAY = 1.0
PUBLIC_DOMAIN = "Public domain (US federal government work)"
CC_BY_SA = "CC BY-SA 4.0"
LICENSES = {PUBLIC_DOMAIN, CC_BY_SA}
CATEGORIES = {"medical", "environment", "survival", "disaster"}
# Only hosts whose pages are US federal government works may carry the public-domain label.
FEDERAL_HOSTS = {"www.cdc.gov", "www.weather.gov", "www.ready.gov", "www.nps.gov", "www.dhs.gov",
                 "www.niams.nih.gov", "www.ninds.nih.gov", "www.nhlbi.nih.gov", "www.niaid.nih.gov",
                 "www.nigms.nih.gov", "medlineplus.gov", "www.noaa.gov", "www.tsunami.gov",
                 "www.fema.gov", "www.usfa.fema.gov", "www.osha.gov"}
GUIDE_WORDS = (80, 600)
MAX_GUIDES_BYTES = 600 * 1024

# Inline/structural nodes that never carry guidance text.
DROP_SELECTORS = ("script, style, noscript, template, svg, img, picture, figure, figcaption, iframe, "
                  "video, audio, button, form, input, select, nav, footer, aside, "
                  ".mw-editsection, sup.reference, .reference, .reflist, .navbox, .hatnote, "
                  ".metadata, .sidebar, .thumb, .mw-empty-elt, .noprint.Inline-Template, "
                  ".ambox, .shortdescription, .infobox, .mw-references-wrap, .cdc-references-cite, "
                  ".cdc-references, a[href^='#cdcreference'], a.toggle > span.control, .gallery, "
                  ".mediaContainer, .listaudio, .haudio, .mw-tmh-player, .page-content-sources, "
                  ".cdc-page-title-bar, .page-mobile-bar, .page-right-rail__dynamic")
CITATION = re.compile(r"\[(?:\d+|[a-z]|note \d+|citation needed|clarification needed|"
                      r"better source needed|failed verification|when\?|who\?|according to whom\?)\]",
                      re.I)
# Strings that indicate navigation, share widgets or page chrome leaked into guide text.
BOILERPLATE = re.compile(
    r"(?:\bOn This Page\b|\bRelated Pages?\b|\bSkip to (?:main )?content\b|\bShare (?:this|on)\b|"
    r"\bDownload (?:Infographic|PDF|the app)\b|\bPrint this page\b|\bPage last (?:reviewed|updated)\b|"
    r"\bLast Reviewed:|\bThis page was last updated\b|\bClick here\b|\bSign up for\b|"
    r"\bFollow us\b|\bAn official website of the United States government\b|\bView larger\b|"
    r"\bExit Notification\b|\bedit source\b|\bRetrieved \d{1,2} \w+ \d{4}\b)", re.I)
STANDALONE_DATE = re.compile(r"(?:Jan|Feb|Mar|Apr|May|June?|July?|Aug|Sept?|Oct|Nov|Dec)[a-z]*\.? \d{1,2}, \d{4}")
MARKUP = re.compile(r"<[a-zA-Z/!][^>]*>|&(?:[a-zA-Z]+|#\d+|#x[0-9a-fA-F]+);|\{\{|\}\}|\[\[|\]\]|'''|"
                    r"^\s*[=*#|]|\[\s*\d+\s*\]")


# ---------------------------------------------------------------------------- shared helpers

def canonical(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def compact(value):
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".partial")
    temporary.write_bytes(data)
    temporary.replace(path)


def utc_now():
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def clean_text(text):
    """Normalise whitespace only. Wording, punctuation and case are left exactly as published."""
    text = text.replace(" ", " ").replace("​", "").replace("­", "")
    text = CITATION.sub("", text)
    text = re.sub(r"\s+", " ", text).strip()
    # Removing a citation marker can leave a space before punctuation ("burn .").
    return re.sub(r"\s+([.,;:!?)])", r"\1", text)


def text_problems(text):
    """Return reasons a string is unsafe to ship as guide text (empty list when clean)."""
    problems = []
    if MARKUP.search(text):
        problems.append("markup or citation marker")
    if BOILERPLATE.search(text):
        problems.append("navigation boilerplate")
    if STANDALONE_DATE.fullmatch(text):
        problems.append("standalone page date")
    if text != text.strip() or "  " in text:
        problems.append("unnormalised whitespace")
    if not text:
        problems.append("empty")
    return problems


# ---------------------------------------------------------------------------- network

def http_get(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT,
                                               "Accept": "text/html,application/json;q=0.9"})
    with urllib.request.urlopen(req, timeout=60) as response:
        raw = response.read(MAX_RESPONSE + 1)
        status, final = response.status, response.geturl()
        content_type = response.headers.get("Content-Type", "")
    if len(raw) > MAX_RESPONSE:
        raise ValueError(f"Response exceeded the 8 MiB safety limit: {url}")
    return raw, status, final, content_type


def wikipedia_request(params):
    query = urllib.parse.urlencode({"format": "json", "formatversion": 2, "maxlag": 5, **params})
    raw, status, _, _ = http_get(API + "?" + query)
    result = json.loads(raw)
    if "error" in result or "warnings" in result:
        raise ValueError(f"Wikipedia rejected the request: {result.get('error', result.get('warnings'))}")
    return result, status


# ---------------------------------------------------------------------------- source list

def load_sources(path):
    data = json.loads(path.read_bytes())
    if data.get("schema") != 1:
        raise ValueError("Unsupported emergency source list schema")
    sources = data["sources"]
    ids = [s["id"] for s in sources]
    if len(set(ids)) != len(ids):
        raise ValueError("Duplicate source id")
    for source in sources:
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", source["id"]):
            raise ValueError(f"Unsafe source id: {source['id']}")
        if source["kind"] == "html":
            host = urllib.parse.urlsplit(source["url"]).netloc
            if urllib.parse.urlsplit(source["url"]).scheme != "https" or host not in FEDERAL_HOSTS:
                raise ValueError(f"Not an allow-listed US federal host: {source['url']}")
        elif source["kind"] == "wikipedia":
            if not isinstance(source.get("revision"), int) or not source.get("title"):
                raise ValueError(f"Wikipedia source needs a title and pinned integer revision: {source['id']}")
        else:
            raise ValueError(f"Unknown source kind: {source['kind']}")
    guides = data["guides"]
    if len({g["id"] for g in guides}) != len(guides):
        raise ValueError("Duplicate guide id")
    known = set(ids)
    for guide in guides:
        if guide["source"] not in known:
            raise ValueError(f"Guide {guide['id']} references unknown source {guide['source']}")
        if guide["category"] not in CATEGORIES:
            raise ValueError(f"Guide {guide['id']} has unknown category {guide['category']}")
    if data["numbers"]["source"] not in known:
        raise ValueError("Numbers table references an unknown source")
    return data


def source_url(source):
    if source["kind"] == "wikipedia":
        return f"https://en.wikipedia.org/w/index.php?oldid={source['revision']}"
    return source["url"]


# ---------------------------------------------------------------------------- fetch / cache

def cache_name(source):
    return f"{source['id']}.{'json' if source['kind'] == 'wikipedia' else 'html'}"


def fetch(sources_path, cache, refresh=()):
    data = load_sources(sources_path)
    cache.mkdir(parents=True, exist_ok=True)
    lock_path = cache / "sources.lock.json"
    lock = json.loads(lock_path.read_bytes()) if lock_path.exists() else {"schema": 1, "sources": {}}
    wanted = {s["id"] for s in data["sources"]}
    for stale in sorted(set(lock["sources"]) - wanted):
        print(f"Lock entry no longer referenced (kept, unused): {stale}", file=sys.stderr)
    for source in data["sources"]:
        row = lock["sources"].get(source["id"])
        if row and source["id"] not in refresh and row["url"] == source_url(source):
            read_cached(cache, source, row)
            continue
        time.sleep(FETCH_DELAY)
        if source["kind"] == "wikipedia":
            parsed, status = wikipedia_request({"action": "parse", "oldid": source["revision"],
                                                "prop": "text|revid|displaytitle"})
            parsed = parsed["parse"]
            if parsed["revid"] != source["revision"]:
                raise ValueError(f"Revision mismatch for {source['id']}")
            raw, final, content_type = canonical(parsed), source_url(source), "application/json"
        else:
            raw, status, final, content_type = http_get(source["url"])
            if status != 200:
                raise ValueError(f"HTTP {status} for {source['url']}")
            if urllib.parse.urlsplit(final).netloc not in FEDERAL_HOSTS:
                raise ValueError(f"Redirected off an allow-listed federal host: {final}")
            text = raw.decode("utf-8")
            if re.search(r"<meta[^>]+http-equiv=[\"']?refresh", text, re.I) and len(text) < 4096:
                raise ValueError(f"{source['url']} is a client-side redirect stub; pin the target URL instead")
        name = cache_name(source)
        write_atomic(cache / name, raw)
        lock["sources"][source["id"]] = {"url": source_url(source), "final_url": final,
            "retrieved": utc_now(), "http_status": status, "content_type": content_type,
            "file": name, "bytes": len(raw), "sha256": digest(raw)}
        write_atomic(lock_path, canonical(lock))
        print(f"Cached {source['id']}: {final} ({len(raw)} bytes)", flush=True)
    return lock


def read_cached(cache, source, row):
    if row["file"] != cache_name(source):
        raise ValueError(f"Unexpected cache filename for {source['id']}")
    if row["url"] != source_url(source):
        raise ValueError(f"Cached URL for {source['id']} does not match the pinned source; refetch it")
    raw = (cache / row["file"]).read_bytes()
    if digest(raw) != row["sha256"]:
        raise ValueError(f"Cache hash mismatch: {row['file']}")
    return raw


def source_html(source, raw):
    if source["kind"] == "wikipedia":
        parsed = json.loads(raw)
        if parsed["revid"] != source["revision"]:
            raise ValueError(f"Cached revision mismatch for {source['id']}")
        return f'<div class="mw-parser-output-wrap">{parsed["text"]}</div>'
    return raw.decode("utf-8", errors="strict")


# ---------------------------------------------------------------------------- HTML extraction

HEADINGS = {"h1", "h2", "h3", "h4", "h5", "h6"}
INLINE = {"a", "abbr", "b", "bdi", "bdo", "cite", "code", "data", "dfn", "em", "font", "i", "kbd",
          "mark", "q", "s", "samp", "small", "span", "strong", "sub", "sup", "time", "u", "var", "wbr"}


def _is_text(node):
    # Comments, CDATA and doctypes are NavigableString subclasses that must never become prose.
    return type(node) is NavigableString


def _inline_only(node):
    return not any(isinstance(d, Tag) and d.name not in INLINE for d in node.descendants)


def _text(node):
    """Rendered text: inline tags join without spaces (as a browser shows them); block tags are separated."""
    if _is_text(node):
        return str(node)
    if not isinstance(node, Tag):
        return ""
    parts = [_text(child) for child in node.children]
    joined = "".join(parts)
    return joined if node.name in INLINE else f" {joined} "


def html_blocks(html, container="main"):
    """Flatten a page into ordered blocks: ("h", level, text), ("p", text) or ("li", text, ordered, depth).

    Walks flow content in document order. Text sitting directly in a div (callouts, div-based
    tables) becomes a paragraph rather than vanishing. Tables, images, forms, navigation and
    footers are removed before reading so their text cannot leak in.
    """
    soup = BeautifulSoup(html, "html.parser")
    root = soup.select_one(container)
    if root is None:
        raise ValueError(f"Container {container!r} not found")
    # Older NPS pages lay out their whole article in a table; unwrap those, drop data tables.
    for node in root.select("table.CS_Layout_Table, table[role=presentation]"):
        for part in node.find_all(["tbody", "thead", "tr", "td"]):
            part.unwrap()
        node.name = "div"
    for node in root.select(DROP_SELECTORS + ", table, .sr-only, .visually-hidden, .usa-sr-only"):
        node.decompose()
    for br in root.find_all("br"):
        br.replace_with(NavigableString(" "))
    for frac in root.select("span.frac"):
        # Wikipedia {{frac}} renders "7½"; plain text must keep the whole number apart from the fraction.
        num = frac.select_one(".num") or frac.find("sup")
        den = frac.select_one(".den") or frac.find("sub")
        if num and den:
            whole = "".join(str(t) for t in reversed(num.find_previous_siblings(string=True))
                            if t.parent is frac).strip()
            frac.replace_with(NavigableString(f"{whole} {_text(num)}⁄{_text(den)}".strip()))
    blocks = []

    def emit_paragraph(parts):
        text = clean_text("".join(parts))
        if text:
            blocks.append(("p", text))
        parts.clear()

    def walk(node, depth):
        pending = []
        for child in node.children:
            if _is_text(child):
                pending.append(str(child))
                continue
            if not isinstance(child, Tag):
                continue
            if child.name in INLINE and _inline_only(child):
                pending.append(_text(child))
                continue
            emit_paragraph(pending)
            if child.name in HEADINGS:
                text = clean_text(_text(child))
                if text:
                    blocks.append(("h", int(child.name[1]), text))
            elif child.name in ("ul", "ol"):
                for item in child.find_all("li", recursive=False):
                    list_item(item, child.name == "ol", depth + 1)
            elif child.name == "li":
                # CDC div-tables place bare <li> items in a cell without a list wrapper.
                list_item(child, False, depth + 1)
            elif child.name == "p" and _inline_only(child):
                emit_paragraph([_text(child)])
            else:
                walk(child, depth)
        emit_paragraph(pending)

    def list_item(item, ordered, depth):
        own, nested = [], []
        for child in item.children:
            if isinstance(child, Tag) and child.name in ("ul", "ol"):
                nested.append(child)
            else:
                own.append(_text(child))
        text = clean_text("".join(own))
        if text:
            blocks.append(("li", text, ordered, depth))
        for sub in nested:
            for sub_item in sub.find_all("li", recursive=False):
                list_item(sub_item, sub.name == "ol", depth + 1)

    walk(root, 0)
    return blocks


def outline(blocks):
    lines = []
    for index, block in enumerate(blocks):
        if block[0] == "h":
            lines.append(f"{index:4d} {'#' * block[1]} {block[2]}")
        elif block[0] == "p":
            lines.append(f"{index:4d}     p: {block[1]}")
        else:
            lines.append(f"{index:4d}     {'  ' * (block[3] - 1)}{'1.' if block[2] else '-'} {block[1]}")
    return "\n".join(lines)


# ---------------------------------------------------------------------------- guide selection

def _block_text(block):
    return block[2] if block[0] == "h" else block[1]


def _find_heading(blocks, path):
    """Index of the unique heading matching "Parent > Child" (each part an exact heading text)."""
    parts = [part.strip() for part in path.split(" > ")]
    matches = []
    for index, block in enumerate(blocks):
        if block[0] != "h" or block[2] != parts[-1]:
            continue
        # Walk back through enclosing headings to confirm the ancestor chain.
        wanted, level = parts[:-1], block[1]
        for earlier in reversed(blocks[:index]):
            if not wanted:
                break
            if earlier[0] == "h" and earlier[1] < level:
                level = earlier[1]
                if earlier[2] == wanted[-1]:
                    wanted = wanted[:-1]
        if not wanted:
            matches.append(index)
    if len(matches) != 1:
        raise ValueError(f"Heading {path!r} matched {len(matches)} times; make the path unique")
    return matches[0]


def _find_prefix(blocks, prefix):
    matches = [i for i, block in enumerate(blocks) if _block_text(block).startswith(prefix)]
    if len(matches) != 1:
        raise ValueError(f"Block prefix {prefix!r} matched {len(matches)} times")
    return matches[0]


def _ancestors(blocks, index):
    chain, level = [], blocks[index][1]
    for earlier in range(index - 1, -1, -1):
        block = blocks[earlier]
        if block[0] == "h" and block[1] < level and block[1] > 1:
            chain.append(earlier)
            level = block[1]
    return list(reversed(chain))


def select_spans(blocks, selectors):
    """Resolve selectors to ordered spans of block indices. Selection never rewrites text.

    A heading selector with "context": true also emits its enclosing headings (heading text only)
    so that repeated subheadings such as "First aid" stay attributable to their parent topic.
    """
    spans, chosen = [], set()
    for selector in selectors:
        if "heading" in selector:
            start = _find_heading(blocks, selector["heading"])
            level, end = blocks[start][1], start + 1
            while end < len(blocks):
                block = blocks[end]
                if block[0] == "h" and (block[1] <= level or not selector.get("nested", True)):
                    break
                end += 1
            span = list(range(start, end))
            if selector.get("context"):
                span = [i for i in _ancestors(blocks, start) if i not in chosen] + span
        elif selector.get("intro"):
            first_h1 = next((i for i, b in enumerate(blocks) if b[0] == "h" and b[1] == 1), -1)
            end = next((i for i, b in enumerate(blocks) if i > first_h1 and b[0] == "h"), len(blocks))
            span = list(range(first_h1 + 1, end))
        elif "from" in selector:
            start, stop = _find_prefix(blocks, selector["from"]), _find_prefix(blocks, selector["through"])
            if stop < start:
                raise ValueError(f"Range {selector} ends before it starts")
            span = list(range(start, stop + 1))
        else:
            raise ValueError(f"Unknown selector {selector}")
        if not span:
            raise ValueError(f"Selector {selector} selected nothing")
        if set(span) & chosen:
            raise ValueError(f"Selector {selector} overlaps an earlier selector")
        chosen.update(span)
        spans.append(span)
    return spans


def apply_omit(blocks, spans, omit):
    """Remove blocks by text prefix. Every prefix must match, so source drift fails the build."""
    indices = [i for span in spans for i in span]
    dropped = set()
    for prefix in omit:
        hits = [i for i in indices if _block_text(blocks[i]).startswith(prefix)]
        if not hits:
            raise ValueError(f"Omit prefix {prefix!r} matched nothing in the selection")
        if len(hits) > 1 and prefix != "Download":
            raise ValueError(f"Omit prefix {prefix!r} matched {len(hits)} blocks; make it specific")
        dropped.update(hits)
    kept = [[i for i in span if i not in dropped] for span in spans]
    return [span for span in kept if span]


def blocks_to_sections(blocks, spans):
    """Group selected blocks into sections. Paragraphs that follow a list start a continuation
    section (empty heading) so lead-in lines stay attached to the list they introduce."""
    if spans and isinstance(spans[0], int):
        spans = [spans]
    sections, current, current_level = [], None, 1

    def start(heading, level):
        section = {"heading": heading, "level": level, "paragraphs": [], "steps": []}
        sections.append(section)
        return section

    for span in spans:
        # Levels are relative to the shallowest heading in each span so every span starts at level 1.
        heading_levels = [blocks[i][1] for i in span if blocks[i][0] == "h"]
        base = min(heading_levels) if heading_levels else 1
        for position, index in enumerate(span):
            block = blocks[index]
            if block[0] == "h":
                current_level = max(1, block[1] - base + 1)
                current = start(block[2], current_level)
                continue
            if position == 0:
                # A span that does not begin at a heading is shown untitled at the top level.
                current_level = 1
                current = start("", current_level)
            if block[0] == "p":
                if current["steps"]:
                    current = start("", current_level)
                current["paragraphs"].append(block[1])
            else:
                current["steps"].append(block[1])
                current.setdefault("_meta", []).append((block[3], block[2]))
    finished = []
    for section in sections:
        meta = section.pop("_meta", [])
        if not section["paragraphs"] and not section["steps"]:
            # A heading whose content was omitted, or a parent heading with only subsections.
            if section["heading"]:
                finished.append(section)
            continue
        if meta:
            top = min(depth for depth, _ in meta)
            levels = [depth - top + 1 for depth, _ in meta]
            section["numbered"] = all(ordered for depth, ordered in meta if depth == top)
            if max(levels) > 1:
                section["step_levels"] = levels
        finished.append(section)
    # Drop headings that end up with no content and no following deeper content.
    result = []
    for i, section in enumerate(finished):
        has_body = section["paragraphs"] or section["steps"]
        next_deeper = i + 1 < len(finished) and finished[i + 1]["level"] > section["level"]
        if has_body or next_deeper:
            result.append(section)
    return result


def section_texts(sections):
    for section in sections:
        if section["heading"]:
            yield section["heading"]
        yield from section["paragraphs"]
        yield from section["steps"]


def word_count(sections):
    return sum(len(text.split()) for text in section_texts(sections))


# US phone numbers, agencies or geography in the text mean the advice assumes US services.
US_SPECIFIC = re.compile(r"\b9-1-1\b|\b911\b|\bUnited States\b|\bU\.S\.|1-800-222-1222|\bFEMA\b|"
                         r"\bNOAA Weather Radio\b|\bEAS\b")


def source_title(source, html):
    if source["kind"] == "wikipedia":
        return source["title"]
    soup = BeautifulSoup(html, "html.parser")
    h1 = soup.find("h1")
    text = clean_text(_text(h1)) if h1 else ""
    if not text and soup.title:
        text = clean_text(soup.title.get_text())
    return text


def source_record(source, row, html):
    record = {"publisher": source["publisher"], "title": source_title(source, html),
              "url": source_url(source), "retrieved": row["retrieved"],
              "license": CC_BY_SA if source["kind"] == "wikipedia" else PUBLIC_DOMAIN,
              "revision": source.get("revision"), "sha256": row["sha256"]}
    if source["kind"] == "wikipedia":
        record["license_url"] = "https://creativecommons.org/licenses/by-sa/4.0/"
        record["attribution"] = (f"Text from the English Wikipedia article \"{source['title']}\" "
                                 f"(revision {source['revision']}) by Wikipedia contributors, licensed CC BY-SA 4.0. "
                                 "Changes: selected sections only; citation markers, tables and images removed.")
    return record


def build_guide(spec, source, row, html):
    container = ".mw-parser-output" if source["kind"] == "wikipedia" else source["container"]
    blocks = html_blocks(html, container)
    spans = apply_omit(blocks, select_spans(blocks, spec["select"]), spec.get("omit", []))
    sections = blocks_to_sections(blocks, spans)
    if not sections:
        raise ValueError(f"Guide {spec['id']} selected no text")
    texts = list(section_texts(sections))
    for text in texts:
        problems = text_problems(text)
        if problems:
            raise ValueError(f"Guide {spec['id']}: {', '.join(problems)} in {text[:80]!r}")
    detected_us = any(US_SPECIFIC.search(text) for text in texts)
    region = spec.get("region") or ("US" if detected_us else None)
    return {"id": spec["id"], "title": spec["title"], "category": spec["category"],
            "keywords": spec["keywords"], "urgent": spec["urgent"], "region": region,
            "sections": sections, "source": source_record(source, row, html)}


# ---------------------------------------------------------------------------- emergency numbers

# ISO 3166-1 alpha-2 codes with English short names. Explicit so the build needs no external data.
ISO_3166 = {
    "AD": "Andorra", "AE": "United Arab Emirates", "AF": "Afghanistan", "AG": "Antigua and Barbuda",
    "AI": "Anguilla", "AL": "Albania", "AM": "Armenia", "AO": "Angola", "AQ": "Antarctica",
    "AR": "Argentina", "AS": "American Samoa", "AT": "Austria", "AU": "Australia", "AW": "Aruba",
    "AX": "Åland Islands", "AZ": "Azerbaijan", "BA": "Bosnia and Herzegovina", "BB": "Barbados",
    "BD": "Bangladesh", "BE": "Belgium", "BF": "Burkina Faso", "BG": "Bulgaria", "BH": "Bahrain",
    "BI": "Burundi", "BJ": "Benin", "BL": "Saint Barthélemy", "BM": "Bermuda", "BN": "Brunei",
    "BO": "Bolivia", "BQ": "Bonaire, Sint Eustatius and Saba", "BR": "Brazil", "BS": "Bahamas",
    "BT": "Bhutan", "BV": "Bouvet Island", "BW": "Botswana", "BY": "Belarus", "BZ": "Belize",
    "CA": "Canada", "CC": "Cocos (Keeling) Islands", "CD": "Democratic Republic of the Congo",
    "CF": "Central African Republic", "CG": "Republic of the Congo", "CH": "Switzerland",
    "CI": "Côte d'Ivoire", "CK": "Cook Islands", "CL": "Chile", "CM": "Cameroon", "CN": "China",
    "CO": "Colombia", "CR": "Costa Rica", "CU": "Cuba", "CV": "Cabo Verde", "CW": "Curaçao",
    "CX": "Christmas Island", "CY": "Cyprus", "CZ": "Czechia", "DE": "Germany", "DJ": "Djibouti",
    "DK": "Denmark", "DM": "Dominica", "DO": "Dominican Republic", "DZ": "Algeria", "EC": "Ecuador",
    "EE": "Estonia", "EG": "Egypt", "EH": "Western Sahara", "ER": "Eritrea", "ES": "Spain",
    "ET": "Ethiopia", "FI": "Finland", "FJ": "Fiji", "FK": "Falkland Islands", "FM": "Micronesia",
    "FO": "Faroe Islands", "FR": "France", "GA": "Gabon", "GB": "United Kingdom", "GD": "Grenada",
    "GE": "Georgia", "GF": "French Guiana", "GG": "Guernsey", "GH": "Ghana", "GI": "Gibraltar",
    "GL": "Greenland", "GM": "Gambia", "GN": "Guinea", "GP": "Guadeloupe", "GQ": "Equatorial Guinea",
    "GR": "Greece", "GS": "South Georgia and the South Sandwich Islands", "GT": "Guatemala",
    "GU": "Guam", "GW": "Guinea-Bissau", "GY": "Guyana", "HK": "Hong Kong",
    "HM": "Heard Island and McDonald Islands", "HN": "Honduras", "HR": "Croatia", "HT": "Haiti",
    "HU": "Hungary", "ID": "Indonesia", "IE": "Ireland", "IL": "Israel", "IM": "Isle of Man",
    "IN": "India", "IO": "British Indian Ocean Territory", "IQ": "Iraq", "IR": "Iran", "IS": "Iceland",
    "IT": "Italy", "JE": "Jersey", "JM": "Jamaica", "JO": "Jordan", "JP": "Japan", "KE": "Kenya",
    "KG": "Kyrgyzstan", "KH": "Cambodia", "KI": "Kiribati", "KM": "Comoros",
    "KN": "Saint Kitts and Nevis", "KP": "North Korea", "KR": "South Korea", "KW": "Kuwait",
    "KY": "Cayman Islands", "KZ": "Kazakhstan", "LA": "Laos", "LB": "Lebanon", "LC": "Saint Lucia",
    "LI": "Liechtenstein", "LK": "Sri Lanka", "LR": "Liberia", "LS": "Lesotho", "LT": "Lithuania",
    "LU": "Luxembourg", "LV": "Latvia", "LY": "Libya", "MA": "Morocco", "MC": "Monaco",
    "MD": "Moldova", "ME": "Montenegro", "MF": "Saint Martin", "MG": "Madagascar",
    "MH": "Marshall Islands", "MK": "North Macedonia", "ML": "Mali", "MM": "Myanmar",
    "MN": "Mongolia", "MO": "Macao", "MP": "Northern Mariana Islands", "MQ": "Martinique",
    "MR": "Mauritania", "MS": "Montserrat", "MT": "Malta", "MU": "Mauritius", "MV": "Maldives",
    "MW": "Malawi", "MX": "Mexico", "MY": "Malaysia", "MZ": "Mozambique", "NA": "Namibia",
    "NC": "New Caledonia", "NE": "Niger", "NF": "Norfolk Island", "NG": "Nigeria", "NI": "Nicaragua",
    "NL": "Netherlands", "NO": "Norway", "NP": "Nepal", "NR": "Nauru", "NU": "Niue",
    "NZ": "New Zealand", "OM": "Oman", "PA": "Panama", "PE": "Peru", "PF": "French Polynesia",
    "PG": "Papua New Guinea", "PH": "Philippines", "PK": "Pakistan", "PL": "Poland",
    "PM": "Saint Pierre and Miquelon", "PN": "Pitcairn", "PR": "Puerto Rico", "PS": "Palestine",
    "PT": "Portugal", "PW": "Palau", "PY": "Paraguay", "QA": "Qatar", "RE": "Réunion",
    "RO": "Romania", "RS": "Serbia", "RU": "Russia", "RW": "Rwanda", "SA": "Saudi Arabia",
    "SB": "Solomon Islands", "SC": "Seychelles", "SD": "Sudan", "SE": "Sweden", "SG": "Singapore",
    "SH": "Saint Helena, Ascension and Tristan da Cunha", "SI": "Slovenia",
    "SJ": "Svalbard and Jan Mayen", "SK": "Slovakia", "SL": "Sierra Leone", "SM": "San Marino",
    "SN": "Senegal", "SO": "Somalia", "SR": "Suriname", "SS": "South Sudan",
    "ST": "Sao Tome and Principe", "SV": "El Salvador", "SX": "Sint Maarten", "SY": "Syria",
    "SZ": "Eswatini", "TC": "Turks and Caicos Islands", "TD": "Chad",
    "TF": "French Southern Territories", "TG": "Togo", "TH": "Thailand", "TJ": "Tajikistan",
    "TK": "Tokelau", "TL": "Timor-Leste", "TM": "Turkmenistan", "TN": "Tunisia", "TO": "Tonga",
    "TR": "Türkiye", "TT": "Trinidad and Tobago", "TV": "Tuvalu", "TW": "Taiwan", "TZ": "Tanzania",
    "UA": "Ukraine", "UG": "Uganda", "UM": "United States Minor Outlying Islands",
    "US": "United States", "UY": "Uruguay", "UZ": "Uzbekistan", "VA": "Vatican City",
    "VC": "Saint Vincent and the Grenadines", "VE": "Venezuela", "VG": "British Virgin Islands",
    "VI": "U.S. Virgin Islands", "VN": "Vietnam", "VU": "Vanuatu", "WF": "Wallis and Futuna",
    "WS": "Samoa", "YE": "Yemen", "YT": "Mayotte", "ZA": "South Africa", "ZM": "Zambia",
    "ZW": "Zimbabwe",
}
# Names used by the Wikipedia list that differ from the short names above.
NAME_ALIASES = {
    "the bahamas": "BS", "cape verde": "CV", "republic of congo": "CG",
    "democratic republic of congo": "CD", "ivory coast": "CI", "saint helena": "SH",
    "caribbean netherlands": "BQ", "curacao": "CW", "turks and caicos": "TC",
    "united states of america": "US", "people's republic of china": "CN", "east timor": "TL",
    "democratic people's republic of korea": "KP", "republic of korea": "KR", "macau": "MO",
    "republic of china (taiwan)": "TW", "turkey": "TR", "czech republic": "CZ",
}
# Rows that are not ISO 3166-1 entries (disputed or sub-national territories); reported, not guessed.
KNOWN_UNMAPPED = {"Abkhazia", "South Ossetia", "Transnistria", "Northern Cyprus", "Akrotiri and Dhekelia",
                  "Kosovo", "Ascension Island", "Tristan da Cunha", "Clipperton Island"}
NUMBER_TOKEN = r"(?:\(\+\d+\)\s*)?\d(?:[\d\- ]*\d)?"
STRICT_CELL = re.compile(rf"{NUMBER_TOKEN}(?:\s*(?:\bor\b|\band\b|/|,)\s*{NUMBER_TOKEN})*")
SERVICE_COLUMNS = {"police": "police", "ambulance": "ambulance", "fire": "fire"}
NOTE_LABELS = {"Police": "police", "Ambulance": "ambulance", "Fire": "fire", "Fire brigade": "fire",
               "Fire department": "fire", "Emergency": "general", "Mobile phones": "mobile"}
NOTE_PATTERN = re.compile(r"(Police|Ambulance|Fire brigade|Fire department|Fire|Emergency|Mobile phones)"
                          rf"\s*[–-]\s*({NUMBER_TOKEN}(?:\s*(?:\bor\b|/|,)\s*{NUMBER_TOKEN})*)")
DEFAULT_NOTE = ("When the local number is unknown, try 112 or 911. 112 is the standard emergency number "
                "across the European Union and works on GSM mobile networks in many other countries; "
                "911 is used across North America and is redirected in some other countries. Prefer the "
                "country-specific numbers below when known.")


def country_code(name):
    key = name.casefold()
    for code, iso_name in ISO_3166.items():
        if iso_name.casefold() == key:
            return code
    return NAME_ALIASES.get(key)


def split_numbers(text):
    """Numbers from a cell only when the cell is purely numbers joined by or/and/slash/comma."""
    if not STRICT_CELL.fullmatch(text):
        return None
    return [token.strip() for token in re.findall(NUMBER_TOKEN, text)]


def labelled_note_numbers(notes):
    """Service numbers stated in notes as "Police – 110". Labels must start a clause so that
    "Non-emergency police – 101" or "State Emergency Service – 132 500" are never captured."""
    found = []
    for match in NOTE_PATTERN.finditer(notes):
        before = notes[:match.start()].rstrip()
        if before and not re.search(r"[;.,:)\]\d]$", before):
            continue
        for number in re.findall(NUMBER_TOKEN, match.group(2)):
            found.append((NOTE_LABELS[match.group(1)], number.strip()))
    return found


def _add(entry, field, numbers):
    for number in numbers:
        if number not in entry[field]:
            entry[field].append(number)


def parse_numbers(html):
    soup = BeautifulSoup(html, "html.parser")
    root = soup.select_one(".mw-parser-output")
    for node in root.select("sup.reference, .reference, .noprint, style, .mw-editsection"):
        node.decompose()
    countries, unmapped, complex_cells, region = {}, [], [], None
    for node in root.find_all(["h2", "table"]):
        if node.name == "h2":
            region = clean_text(_text(node))
            continue
        if "wikitable" not in (node.get("class") or []):
            continue
        rows = node.find_all("tr")
        header = [clean_text(_text(c)).casefold() for c in rows[0].find_all(["th", "td"])]
        if header[:4] != ["country", "police", "ambulance", "fire"]:
            raise ValueError(f"Unexpected numbers table header in {region}: {header}")
        for row in rows[1:]:
            cells = row.find_all(["th", "td"])
            if any(c.get("rowspan") for c in cells):
                raise ValueError("Row spans are not supported; the table layout changed")
            columns = []
            for cell in cells:
                columns.extend([cell] * int(cell.get("colspan", 1)))
            if len(columns) != len(header):
                raise ValueError(f"Row width mismatch in {region}: {clean_text(_text(cells[0]))}")
            name = clean_text(_text(columns[0]))
            notes = clean_text(_text(columns[4])) if len(columns) > 4 else ""
            code = country_code(name)
            if code is None:
                unmapped.append(name)
                continue
            entry = {"name": name, "region": region, "police": [], "ambulance": [], "fire": [],
                     "general": [], "mobile": [], "listed": {}, "notes": notes}
            span = int(columns[1].get("colspan", 1)) if columns[1] is columns[3] else 0
            for position, service in ((1, "police"), (2, "ambulance"), (3, "fire")):
                raw = clean_text(_text(columns[position]))
                entry["listed"][service] = raw
                numbers = split_numbers(raw) if raw else []
                if numbers is None:
                    complex_cells.append({"country": name, "service": service, "text": raw})
                    numbers = []
                _add(entry, service, numbers)
            if span == 3:
                _add(entry, "general", entry["police"])
            for field, number in labelled_note_numbers(notes):
                _add(entry, field, [number])
            if code in countries:
                previous = dict(countries[code], region=None)
                if previous != dict(entry, region=None):
                    raise ValueError(f"Conflicting duplicate rows for {code}")
                continue
            countries[code] = entry
    return countries, unmapped, complex_cells


def build_numbers(source, row, raw):
    parsed = json.loads(raw)
    countries, unmapped, complex_cells = parse_numbers(parsed["text"])
    numbers = {"version": None, "generated": None, "default": ["112", "911"], "default_note": DEFAULT_NOTE,
               "source": source_record(source, row, parsed["text"]),
               "countries": {code: countries[code] for code in sorted(countries)}}
    report = {"countries": len(countries), "unmapped_rows": unmapped,
              "unexpected_unmapped": sorted(set(unmapped) - KNOWN_UNMAPPED),
              "iso_codes_without_row": sorted(set(ISO_3166) - set(countries)),
              "unparsed_cells": complex_cells}
    return numbers, report


# ---------------------------------------------------------------------------- build

def build(sources_path, cache, output, version=None):
    data = load_sources(sources_path)
    lock = json.loads((cache / "sources.lock.json").read_bytes())
    sources = {s["id"]: s for s in data["sources"]}
    used = {g["source"] for g in data["guides"]} | {data["numbers"]["source"]}
    missing = sorted(used - set(lock["sources"]))
    if missing:
        raise ValueError(f"Sources not in cache, run fetch first: {', '.join(missing)}")
    raws = {sid: read_cached(cache, sources[sid], lock["sources"][sid]) for sid in sorted(used)}
    # Stamp outputs with the newest snapshot time so rebuilding from the same cache is byte-identical.
    newest = max(lock["sources"][sid]["retrieved"] for sid in used)
    generated = newest[:10]
    version = version or generated.replace("-", ".")
    guides, report_guides = [], []
    for spec in data["guides"]:
        source = sources[spec["source"]]
        guide = build_guide(spec, source, lock["sources"][source["id"]], source_html(source, raws[source["id"]]))
        words = word_count(guide["sections"])
        report_guides.append({"id": guide["id"], "words": words, "region": guide["region"],
                              "within_target": GUIDE_WORDS[0] <= words <= GUIDE_WORDS[1]})
        guides.append(guide)
    guides_doc = {"version": version, "generated": generated, "guides": guides}
    guides_bytes = canonical(guides_doc)
    if len(guides_bytes) > MAX_GUIDES_BYTES:
        raise ValueError(f"guides.json is {len(guides_bytes)} bytes, above the {MAX_GUIDES_BYTES} limit")
    numbers_source = sources[data["numbers"]["source"]]
    numbers_doc, numbers_report = build_numbers(numbers_source, lock["sources"][numbers_source["id"]],
                                                raws[numbers_source["id"]])
    numbers_doc["version"], numbers_doc["generated"] = version, generated
    if numbers_report["unexpected_unmapped"]:
        raise ValueError(f"Unmapped country rows: {numbers_report['unexpected_unmapped']}")
    output.mkdir(parents=True, exist_ok=True)
    write_atomic(output / "guides.json", guides_bytes)
    write_atomic(output / "numbers.json", canonical(numbers_doc))
    write_atomic(output / "build-report.json", canonical({
        "version": version, "generated": generated, "guides": report_guides, "numbers": numbers_report,
        "builder_sha256": digest(Path(__file__).read_bytes()), "sources_sha256": digest(sources_path.read_bytes()),
        "guides_sha256": digest(guides_bytes), "beautifulsoup": __import__("bs4").__version__}))
    outside = [g["id"] for g in report_guides if not g["within_target"]]
    print(f"Built {len(guides)} guides ({len(guides_bytes)} bytes) and {numbers_report['countries']} countries.")
    if outside:
        print(f"Guides outside the {GUIDE_WORDS[0]}-{GUIDE_WORDS[1]} word target: {', '.join(outside)}")
    if numbers_report["unmapped_rows"]:
        print(f"Unmapped number rows (not ISO 3166-1): {', '.join(numbers_report['unmapped_rows'])}")
    return guides_doc, numbers_doc


def outline_source(sources_path, cache, source_id):
    data = load_sources(sources_path)
    source = next(s for s in data["sources"] if s["id"] == source_id)
    lock = json.loads((cache / "sources.lock.json").read_bytes())
    html = source_html(source, read_cached(cache, source, lock["sources"][source_id]))
    container = ".mw-parser-output" if source["kind"] == "wikipedia" else source["container"]
    print(outline(html_blocks(html, container)))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["fetch", "build", "outline"])
    parser.add_argument("--sources", type=Path, default=DEFAULT_SOURCES)
    parser.add_argument("--cache", type=Path, default=DEFAULT_OUT / "cache")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--version", help="Override the YYYY.MM.DD version stamp")
    parser.add_argument("--refresh", nargs="*", default=[], help="fetch: source ids to download again")
    parser.add_argument("--source", help="outline: source id to print")
    args = parser.parse_args()
    if args.action == "fetch":
        fetch(args.sources, args.cache, set(args.refresh))
    elif args.action == "build":
        build(args.sources, args.cache, args.output, args.version)
    else:
        if not args.source:
            parser.error("outline requires --source")
        outline_source(args.sources, args.cache, args.source)


if __name__ == "__main__":
    main()
