#!/usr/bin/env python3
"""Practical skills collection from US federal public-domain guides, quoted as published.

Scope: home canning and food safety; getting a home ready for floods, storms and earthquakes;
safe water; shelter; fire; signalling for rescue; reading the weather; basic navigation.
Out of scope, and refused by `check_passage`: first aid, medicine and dosing, and identifying
wild plants, fungi or animals as food. ATP 3-50.21 is read without chapter 2 (survival medicine)
and without chapter 4 (food: wild plants, trapping, butchering) or its field-expedient weapons.

Sources are listed in tools/data/practical_skills_sources.json, each a work of the US federal
government (LicenseRef-US-Government-Work).

Usage:
  python3 tools/packs/build_practical_skills_pack.py fetch   --cache build/practical-skills/cache
  python3 tools/packs/build_practical_skills_pack.py extract --cache build/practical-skills/cache \
      --output build/practical-skills/passages
  python3 tools/packs/build_practical_skills_pack.py build   --cache build/practical-skills/cache \
      --output build/practical-skills/out --version 2026.11.09

`fetch` downloads each source once, politely, and records publisher, title, edition, URL, final
URL, fetch time (UTC), HTTP status, bytes and SHA-256 in sources.lock.json. `extract` and `build`
never touch the network: they check every cached byte against the lock first.

`extract` and `build` read the PDFs with poppler's `pdftotext` (poppler-utils), a system tool
rather than a Python package; the tests do not need it.

The USDA process-time tables are read separately from `pdftotext -layout`, one passage per table,
cells joined with " | " under their altitude, jar-size and process-time headers. A table is kept
only whole, when every cell sits under a header column and every row is proven against the
publisher's line; the others are left out and counted in extract-report.json. HTML tables are kept
whole the same way, from their rows. Plain reading order flattens a table into one cell per
paragraph and can drop a cell, shifting later numbers into the wrong column, so runs of such cells
are left out of the prose (drop_table_fragments): table content enters the pack only whole.

Every passage is the source's own text, selected and never rewritten. The only changes are
whitespace (line breaks and runs of spaces become one space), joining a word hyphenated across a
line break, and dropping running page headers and footers. `extract` proves this: each passage,
whitespace-normalised, must occur in the whitespace-normalised full text of its source.
"""
from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
import datetime as dt
import hashlib
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCES = ROOT / "tools/data/practical_skills_sources.json"
LICENSE = "LicenseRef-US-Government-Work"
PACK_ID = "practical-skills"
TITLE = "Practical skills"
TOOL_AGENT = "FieldAtlasPracticalBuilder/1.0 (https://github.com/0x94t3z/fieldatlas)"
# armypubs.army.mil refuses non-browser user agents.
BROWSER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0"
# nchfp.uga.edu is the USDA's own distributor for the Complete Guide to Home Canning (the guide
# names it); every other host is a US federal government site.
HOSTS = {"nchfp.uga.edu", "www.ready.gov", "www.cdc.gov", "www.weather.gov", "pubs.usgs.gov",
         "armypubs.army.mil"}
MAX_BYTES = 40 * 1024 * 1024
FETCH_DELAY = 1.5
PASSAGE_TARGET = 1200
MIN_PASSAGE = 80


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def load_sources(path: Path) -> list[dict]:
    data = json.loads(path.read_bytes())
    if data.get("schema") != 1 or data.get("license") != LICENSE:
        raise ValueError("Unsupported practical-skills source list")
    sources = data["sources"]
    if len({s["id"] for s in sources}) != len(sources):
        raise ValueError("Duplicate source id")
    for source in sources:
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", source["id"]):
            raise ValueError(f"Unsafe source id: {source['id']}")
        parts = urllib.parse.urlsplit(source["url"])
        if parts.scheme != "https" or parts.netloc not in HOSTS:
            raise ValueError(f"Not an allow-listed host: {source['url']}")
        if source["kind"] not in ("pdf", "html"):
            raise ValueError(f"Unknown kind: {source['kind']}")
        for key in ("publisher", "title", "edition", "topic"):
            if not str(source.get(key, "")).strip():
                raise ValueError(f"{source['id']} lacks {key}")
    return sources


# ---------------------------------------------------------------------------- fetch

def http_get(url: str, browser: bool) -> tuple[bytes, int, str, str]:
    headers = {"User-Agent": BROWSER_AGENT if browser else TOOL_AGENT,
               "Accept": "text/html,application/pdf;q=0.9,*/*;q=0.5"}
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=180) as response:
        raw = response.read(MAX_BYTES + 1)
        status, final = response.status, response.geturl()
        content_type = response.headers.get("Content-Type", "")
    if len(raw) > MAX_BYTES:
        raise ValueError(f"Response exceeded {MAX_BYTES} bytes: {url}")
    final_parts = urllib.parse.urlsplit(final)
    if final_parts.scheme != "https" or final_parts.netloc not in HOSTS:
        raise ValueError(f"Redirected off the allow-list: {url}")
    return raw, status, final, content_type


def looks_like(kind: str, raw: bytes) -> bool:
    if kind == "pdf":
        return raw.startswith(b"%PDF-")
    head = raw[:4096].lstrip().lower()
    return head.startswith(b"<!doctype html") or head.startswith(b"<html")


def fetch(sources_path: Path, cache: Path) -> None:
    sources = load_sources(sources_path)
    lock_path = cache / "sources.lock.json"
    lock = json.loads(lock_path.read_bytes()) if lock_path.exists() else {"schema": 1, "license": LICENSE, "sources": {}}
    (cache / "raw").mkdir(parents=True, exist_ok=True)
    failures = []
    for source in sources:
        if source["id"] in lock["sources"]:
            continue
        time.sleep(FETCH_DELAY)
        try:
            raw, status, final, content_type = http_get(source["url"], bool(source.get("browser_user_agent")))
        except Exception as error:  # recorded and reported; the lock never holds a failed fetch
            failures.append((source["id"], f"{type(error).__name__}: {error}"))
            print(f"FAILED {source['id']}: {type(error).__name__}", flush=True)
            continue
        if status != 200 or not looks_like(source["kind"], raw):
            failures.append((source["id"], f"status {status}, type {content_type!r}"))
            print(f"FAILED {source['id']}: status {status}", flush=True)
            continue
        name = f"{source['id']}.{source['kind']}"
        (cache / "raw" / name).write_bytes(raw)
        lock["sources"][source["id"]] = {
            "publisher": source["publisher"], "title": source["title"], "edition": source["edition"],
            "url": source["url"], "final_url": final, "fetched": utc_now(), "http_status": status,
            "content_type": content_type, "bytes": len(raw), "sha256": sha(raw), "file": f"raw/{name}",
            "license": LICENSE}
        lock_path.write_bytes(canonical(lock))
        print(f"fetched {source['id']}: {len(raw):,} bytes", flush=True)
    if failures:
        raise SystemExit("Fetch failures: " + "; ".join(f"{i} ({why})" for i, why in failures))


def read_cached(cache: Path, lock: dict, source_id: str) -> bytes:
    entry = lock["sources"].get(source_id)
    if entry is None:
        raise ValueError(f"{source_id} was never fetched")
    path = (cache / entry["file"]).resolve()
    if not path.is_relative_to(cache.resolve()) or path.is_symlink():
        raise ValueError(f"Unsafe cache path for {source_id}")
    raw = path.read_bytes()
    if sha(raw) != entry["sha256"]:
        raise ValueError(f"Cached {source_id} changed since fetch")
    return raw



# ---------------------------------------------------------------------------- scope checks

# Out of scope for this collection: first aid, medicine and dosing. The bundled emergency guides
# answer those, quoted from clinical sources; this collection must never compete with them.
MEDICAL = re.compile(
    r"(?i)\b(?:doses?|dosages?|dosing|milligrams?|\d+\s?mg\b|medications?|medicines?|medicinal|antibiotics?|"
    r"aspirin|ibuprofen|acetaminophen|first[- ]aid|CPR|tourniquets?|splints?|wounds?|fractures?|"
    r"hypothermia|frostbite|heat ?stroke|heat exhaustion|seek medical|medical (?:care|attention|help|treatment)|"
    r"symptoms?|treat(?:ment|ing)? (?:the )?(?:victim|casualty|injur\w*)|casualty|antivenom|poison control)\b")
# Out of scope: identifying wild plants, fungi or animals as food (and hunting them).
WILD_FOOD = re.compile(
    r"(?i)\b(?:edible|edibility|inedible|mushrooms?|fung(?:i|us|al)|forag\w*|wild plants?|berr(?:y|ies)|"
    r"insects? (?:as|for) food|grubs?|snares?|traps?|trapping|butcher\w*|game animals?|"
    r"fish(?:ing| hooks?)|nuts? and seeds?|universal edibility|survival food|procure food)\b")
# ATP 3-50.21 only: water from plants needs plant identification; chapter 8 also arms the reader.
ATP_ONLY = re.compile(
    r"(?i)\b(?:vines?|coconuts?|bananas?|plantains?|cact(?:us|i)|transpiration|green vegetation|"
    r"plant (?:parts?|life|identification)|safe plants?|weapons?|spears?|clubs?|bolas?|slingshots?)\b")
GARBLED = re.compile(r"[\x00-\x08\x0b-\x1f\x7f\ufffd]|6HSWHPEHU|\(37\(0%\(5")
NAV = re.compile(
    r"(?i)(?:\bOn This Page\b|\bRelated Pages?\b|\bSkip to (?:main )?content\b|\bShare (?:this|on)\b|"
    r"\bPrint this page\b|\bDownload (?:Infographic|PDF)\b|\bPage last (?:reviewed|updated)\b|\bLast Reviewed:|\bSign up for\b|\bFollow us\b|"
    r"\bAn official website of the United States government\b|\bExit Notification\b|\bClick here\b)")


def check_passage(source_id: str, text: str) -> list[str]:
    """Reasons a passage may not ship; an empty list means it is in scope."""
    problems = []
    if MEDICAL.search(text):
        problems.append("medical: " + MEDICAL.search(text).group(0))
    if WILD_FOOD.search(text):
        problems.append("wild food: " + WILD_FOOD.search(text).group(0))
    if source_id.startswith("army-atp") and ATP_ONLY.search(text):
        problems.append("atp exclusion: " + ATP_ONLY.search(text).group(0))
    if GARBLED.search(text):
        problems.append("garbled text layer")
    if NAV.search(text):
        problems.append("navigation boilerplate")
    return problems


# ---------------------------------------------------------------------------- text extraction

def squash(text: str) -> str:
    """Whitespace only: no-break space, zero-width space and soft hyphen, then runs of spaces.
    U+FFFD is the space glyph of the USDA guides' tables that poppler cannot map; it only ever
    sits between two words or numbers ("12\ufffdlb"), so it is read as a space."""
    text = text.replace("\u00a0", " ").replace("\u200b", "").replace("\u00ad", "").replace("\ufffd", " ")
    return re.sub(r"\s+", " ", text).strip()


def proof_key(text: str) -> str:
    """What the verbatim proof compares: the text without whitespace, hyphens or "|" (the
    separator between table cells). Every word, letter, digit and other punctuation mark must
    survive in the same order."""
    return re.sub(r"[\s\-\u2010\u2011\u00ad\u200b\ufffd|]+", "", text)


def require_pdftotext() -> None:
    import shutil
    if shutil.which("pdftotext") is None:
        raise SystemExit("pdftotext (poppler-utils) is required to read the PDF sources: "
                         "apt install poppler-utils, or brew install poppler")


def pdf_pages(raw: bytes) -> list[str]:
    """Poppler's pdftotext in reading order (pypdf splits words in ATP 3-50.21: "car e")."""
    import subprocess
    import tempfile
    require_pdftotext()
    with tempfile.TemporaryDirectory(prefix="fieldatlas-practical-") as temp:
        source = Path(temp) / "in.pdf"
        source.write_bytes(raw)
        result = subprocess.run(["pdftotext", "-enc", "UTF-8", str(source), "-"], check=True,
                                capture_output=True, timeout=300)
    text = result.stdout.decode("utf-8")
    if len(text) > 20 * 1024 * 1024:
        raise ValueError("Extracted text exceeds 20 MiB")
    return text.split("\f")


def pdftotext_version() -> str:
    import subprocess
    result = subprocess.run(["pdftotext", "-v"], capture_output=True, text=True, timeout=30)
    return (result.stderr or result.stdout).splitlines()[0].strip()


def in_ranges(page: int, ranges: list[list[int]]) -> bool:
    return any(start <= page <= end for start, end in ranges)


DOT_LEADER = re.compile(r"\.{5,}|(?:\. ){4,}")
BULLET = re.compile(r"^(?:[•▪◦●−–-]|z |\d+-\d+\. |[A-Z]-\d+\. |\d+\. )")


def join_lines(lines: list[str], vocabulary: set[str]) -> str:
    """Join the lines of one paragraph. A word broken across lines with a hyphen is joined
    without the hyphen only when the joined word occurs elsewhere in the same source;
    otherwise the hyphen stays ("up-to-date")."""
    out = ""
    for line in lines:
        if not out:
            out = line
            continue
        match = re.search(r"([A-Za-z]+)-$", out)
        nxt = re.match(r"([a-z]+)", line)
        if match and nxt:
            joined = match.group(1) + nxt.group(1)
            out = out[:-1] + line if joined.lower() in vocabulary else out + line
        elif BULLET.match(line):
            out += "\n" + line
        else:
            out += " " + line
    return out


def is_heading(paragraph: str) -> bool:
    return ("\n" not in paragraph and len(paragraph) <= 70 and not BULLET.match(paragraph)
            and paragraph[:1].isupper() and not re.search(r"[.,;:]$", paragraph)
            and not re.fullmatch(r"(?:Figure|Table) .*", paragraph) and len(paragraph.split()) <= 9)


def pdf_sections(source: dict, raw: bytes) -> tuple[list[tuple[str, str]], dict]:
    rules = source.get("pdf", {})
    drop = [re.compile(r) for r in rules.get("drop_lines", [])]
    pages = pdf_pages(raw)
    include = rules.get("pages") or [[1, len(pages)]]
    exclude = rules.get("exclude_pages", [])
    vocabulary = set(w.lower() for w in re.findall(r"[A-Za-z]{3,}", "\n".join(pages)))
    paragraphs, stats = [], {"pages": len(pages), "pages_used": 0, "lines_dropped": 0}
    named = {h.lower() for h in source.get("keep_sections", []) + source.get("exclude_sections", [])}
    for number, page in enumerate(pages, start=1):
        if not in_ranges(number, include) or in_ranges(number, exclude):
            continue
        stats["pages_used"] += 1
        block = []
        for line in page.splitlines() + [""]:
            line = squash(line)
            if line and (DOT_LEADER.search(line) or any(r.search(line) for r in drop)):
                stats["lines_dropped"] += 1
                continue
            if line:
                block.append(line)
            elif block and len(block) >= 4 and sorted(len(b) for b in block)[len(block) // 2] < 22:
                # A table cell per line: poppler's reading order loses the rows, so a processing
                # time could be read against the wrong altitude. Tables are left out, not rebuilt.
                stats["table_blocks_dropped"] = stats.get("table_blocks_dropped", 0) + 1
                block = []
            elif block:
                if len(block) > 1 and block[0].lower() in named:
                    paragraphs.append(("h", block[0]))  # a listed heading set flush on its text
                    block = block[1:]
                paragraph = join_lines(block, vocabulary)
                paragraphs.append(("h" if is_heading(paragraph) else "p", paragraph))
                block = []
    paragraphs, stats["table_fragments_dropped"] = drop_table_fragments(paragraphs)
    return group_sections(source, paragraphs), stats



# ---------------------------------------------------------------------------- table fragments in reading order

# Words that are table furniture in the USDA guides: units, jar sizes, column headers.
TABLE_WORDS = {"min", "lb", "ft", "cup", "cups", "pint", "pints", "quart", "quarts", "half-pints", "or", "and",
               "hot", "raw", "style", "of", "pack", "jar", "size", "process", "time", "above", "altitudes",
               "at", "canner", "pressure", "(psi)", "psi", "oz", "°f", "tsp", "tbsp", "lbs"}
NUMBERISH = re.compile(r"^[\d.,/%°–\-]+(?:ft|lb|min|°F)?[.,;:]?$")


def tabular_score(paragraph: str) -> float:
    """Share of tokens that are numbers, fractions, units or table headers; 0 without a number."""
    tokens = paragraph.replace("–", "– ").split()
    if not tokens:
        return 0.0
    numbers = sum(1 for token in tokens if NUMBERISH.match(token))
    if not numbers:
        return 0.0  # "Pressure canners" is a heading, not a cell
    hits = numbers + sum(1 for token in tokens if token.lower().strip(".,;:") in TABLE_WORDS)
    return hits / len(tokens)


def is_cell(paragraph: str) -> bool:
    """A table cell that reading order set on its own: mostly numbers and units, or a label of at
    most three words with no sentence punctuation ("Light", "Very Light", "Jar Size Pints")."""
    if TABLE_TITLE.match(paragraph) or tabular_score(paragraph) >= 0.5:
        return True
    return len(paragraph.split()) <= 3 and not re.search(r"[.!?:;]$", paragraph)


def drop_table_fragments(paragraphs: list[tuple[str, str]]) -> tuple[list[tuple[str, str]], int]:
    """Plain reading order flattens a table into one cell per paragraph and can lose a cell, which
    shifts every number after it into the wrong column ("Light 20 5-3/4 1-1/2 2-1/4" for
    "Light 20 5-3/4 1-1/2 9 2-1/4"). Tables enter the pack only whole, from the layout reading
    (process_tables); here every run of cells is left out: three or more cells, two of them mostly
    numbers, with at most one other paragraph between two of them, that paragraph included when it
    is short enough to be a cell of words. A lone paragraph that is mostly numbers, or a table
    title, is left out too; a lone short label stays, since it may be a heading."""
    flags = [is_cell(text) for _, text in paragraphs]
    drop = [False] * len(paragraphs)
    run = []
    for i in [i for i, flag in enumerate(flags) if flag] + [None]:
        if run and (i is None or i - run[-1] > 2):
            numeric = sum(1 for j in run if tabular_score(paragraphs[j][1]) >= 0.5)
            if len(run) >= 3 and numeric >= 2:
                for j in range(run[0], run[-1] + 1):
                    # A cell of words ("Very sweet fruit. Try a small amount ...") stays with its table.
                    if flags[j] or len(paragraphs[j][1]) < 120:
                        drop[j] = True
            run = []
        if i is not None:
            run.append(i)
    for i, (_, text) in enumerate(paragraphs):
        if tabular_score(text) >= 0.5 or TABLE_TITLE.match(text):
            drop[i] = True
    return [item for item, gone in zip(paragraphs, drop) if not gone], sum(drop)



# ---------------------------------------------------------------------------- USDA process tables

TABLE_TITLE = re.compile(r"(?i)^(?:Table \d+\.\s*)?Recommended process times? for\b")


def layout_pages(raw: bytes) -> list[str]:
    import subprocess
    import tempfile
    require_pdftotext()
    with tempfile.TemporaryDirectory(prefix="fieldatlas-practical-") as temp:
        source = Path(temp) / "in.pdf"
        source.write_bytes(raw)
        result = subprocess.run(["pdftotext", "-layout", "-enc", "UTF-8", str(source), "-"], check=True,
                                capture_output=True, timeout=300)
    return result.stdout.decode("utf-8").split("\f")


def table_cells(line: str) -> list[tuple[int, str]]:
    """Cells with their start columns. Columns are separated by runs of two or more spaces in
    poppler's layout output; the replacement character is a one-column space glyph."""
    line = line.replace(" ", " ").replace("�", " ").rstrip()
    return [(m.start(), re.sub(r" +", " ", m.group(0))) for m in re.finditer(r"\S+(?: \S+)*", line)]


def align_row(cells: list[tuple[int, str]], columns: list[int], slack: int = 6) -> list[str] | None:
    """Put each cell under the header column it starts beneath; None when a cell sits under no
    column or two cells under one (the columns slipped)."""
    out = [""] * len(columns)
    for start, text in cells:
        nearest = min(range(len(columns)), key=lambda c: abs(columns[c] - start))
        if abs(columns[nearest] - start) > slack or out[nearest]:
            return None
        out[nearest] = text
    while out and not out[-1]:
        out.pop()
    return out


def process_tables(source: dict, raw: bytes) -> tuple[list[dict], dict]:
    """The USDA process-time tables as text rows, one passage per table.

    A table is kept only whole: its title, the altitude header, the "Jar Size" header and every
    row, each row with no more cells than the header has columns (a longer row means the columns
    slipped). Cells are joined with " | "; the verbatim proof ignores the separator, so every row
    must still read, cell by cell, as one line of the publisher's table."""
    tables, stats = [], {"tables_found": 0, "tables_kept": 0, "tables_rejected": []}
    for number, page in enumerate(layout_pages(raw), start=1):
        lines = page.split("\n")
        i = 0
        while i < len(lines):
            first = squash(lines[i])
            if not TABLE_TITLE.match(first):
                i += 1
                continue
            stats["tables_found"] += 1
            title, i = first, i + 1
            # The title may wrap onto one more line ("... in a" / "dial-gauge pressure canner").
            if i < len(lines) and squash(lines[i]) and len(table_cells(lines[i])) == 1 and title.endswith((" a", " an")):
                title, i = f"{title} {squash(lines[i])}", i + 1
            rows, blank = [], 0
            while i < len(lines) and blank < 2:
                if not squash(lines[i]):
                    blank += 1 if rows else 0
                    i += 1
                    continue
                if TABLE_TITLE.match(squash(lines[i])) or (squash(lines[i]).isupper() and len(squash(lines[i])) > 5):
                    break  # the next table, or the next food's heading ("CARROTS—SLICED OR DICED")
                blank = 0
                rows.append(table_cells(lines[i]))
                i += 1
            problems = []
            header = max(rows[:4], key=len, default=[])
            columns = [start for start, _ in header]
            # The guide's page number and chapter tab ("4-9", "4") print in the right margin, on
            # the table's lines; they are page furniture, not cells.
            if columns:
                margin = max(start + len(text) for start, text in header) + 8
                rows = [[c for c in cells if not (c[0] > margin and re.fullmatch(r"\d(?:-\d+)?", c[1]))] for cells in rows]
                rows = [cells for cells in rows if cells]
            spanning = re.compile(r"(?i)(?:at )?altitudes? of$")  # "Canner Pressure (PSI) at Altitudes of" spans the altitude columns
            aligned = ([[cells[0][1]] if len(cells) == 1 and spanning.search(cells[0][1]) else align_row(cells, columns)
                        for cells in rows] if len(columns) >= 3 else [None])
            if any(row is None for row in aligned):
                problems.append("a cell sits under no header column")
            text_rows = [" | ".join(row) for row in aligned if row is not None]
            body = "\n".join(text_rows)
            if not re.search(r"(?i)altitudes? of", body) or not re.search(r"\bft\b", body):
                problems.append("no altitude header")
            if not re.search(r"(?i)\bJar\b", body) or not re.search(r"(?i)\bSize\b", body):
                problems.append("no jar size header")
            if not any(re.search(r"\d+ min|\d+ lb", r) for r in text_rows):
                problems.append("no data rows")
            if problems:
                stats["tables_rejected"].append({"page": number, "title": title, "problems": problems})
                continue
            stats["tables_kept"] += 1
            tables.append({"page": number, "heading": title, "text": title + "\n" + body})
    return tables, stats


def group_sections(source: dict, paragraphs: list[tuple[str, str]]) -> list[tuple[str, str]]:
    """Paragraphs under their heading; each heading starts a new passage."""
    deny = {h.lower() for h in source.get("exclude_sections", [])}
    keep = {h.lower() for h in source.get("keep_sections", [])}
    drop = [re.compile(r) for r in source.get("drop_paragraphs", [])]
    sections, heading, current, skipping = [], source.get("default_heading", "Overview"), [], bool(keep)
    seen = set()
    def flush():
        if current and not skipping:
            sections.append((heading, current[:]))
    for kind, paragraph in paragraphs:
        if kind == "h":
            flush()
            current.clear()
            heading = paragraph
            seen.add(paragraph.lower())
            skipping = paragraph.lower() in deny or bool(keep and paragraph.lower() not in keep)
            continue
        if not any(r.search(paragraph) for r in drop):
            current.append(paragraph)
    flush()
    missing = (deny | keep) - seen
    if missing:
        raise ValueError(f"{source['id']}: excluded headings not found (rule would not hold): {sorted(missing)}")
    return [(h, "\n\n".join(ps)) for h, ps in sections]


HTML_DROP = ("script, style, noscript, template, svg, img, picture, figure, figcaption, iframe, video, audio, "
             "button, form, input, select, nav, footer, header, aside, #topnav, .topnav, #site-search, "
             ".cdc-page-title-bar, .page-mobile-bar, .page-right-rail__dynamic, .cdc-references, "
             ".page-content-sources, .breadcrumb, .cdc-dfe-body__bottom, .dfe-section--related-pages, "
             "a.toggle > span.control, #footer_legal_gov, #footer_legal_info, #footer_required_links")


BLOCKS = ["h1", "h2", "h3", "h4", "p", "li", "td", "th", "div"]


def html_sections(source: dict, raw: bytes) -> tuple[list[tuple[str, str]], dict]:
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(raw, "html.parser")
    root = soup.select_one("main") or soup.body
    for node in root.select(HTML_DROP):
        node.decompose()
    for br in root.find_all("br"):
        br.replace_with("\n")
    paragraphs, stats = [], {"blocks": 0, "link_only_dropped": 0, "tables": 0}
    for node in root.find_all(BLOCKS + ["table"]):
        if node.name == "table":
            # A table stays whole, a row per line with its cells joined by " | ", header row first,
            # so no number is read away from its row and column headings.
            rows = []
            for tr in node.find_all("tr"):
                cells = [squash(cell.get_text(" ")) for cell in tr.find_all(["th", "td"])]
                if any(cells):
                    rows.append(" | ".join(cells))
            caption = node.find("caption")
            if caption and squash(caption.get_text(" ")):
                rows.insert(0, squash(caption.get_text(" ")))
            if rows:
                stats["tables"] += 1
                paragraphs.append(("p", "\n".join(rows)))
            continue
        if node.find_parent("table"):
            continue  # read with its table
        if node.name in ("li", "td", "th", "div") and node.find(BLOCKS):
            continue  # the inner blocks are read on their own
        lines = [squash(line) for line in node.get_text().split("\n")]
        lines = [re.sub(r" ([,.;:!?)])", r"\1", line) for line in lines if line]
        text = " ".join(lines)
        if not text:
            continue
        links = squash(" ".join(a.get_text(" ") for a in node.find_all("a")))
        if node.name != "h1" and node.name[0] != "h" and links == text and len(text) < 100:
            stats["link_only_dropped"] += 1
            continue
        stats["blocks"] += 1
        if node.name in ("h1", "h2", "h3", "h4"):
            paragraphs.append(("h", text))
        else:
            paragraphs.extend(("p", line) for line in lines)
    return group_sections(source, paragraphs), stats


def passages_for(source: dict, sections: list[tuple[str, str]]) -> list[dict]:
    """One or more passages per section, split only between paragraphs. A section too short to
    stand alone is carried into the next one, its heading kept as a line of source text."""
    out, carry, carry_heading = [], [], ""
    for heading, body in sections:
        paragraphs = body.split("\n\n")
        if carry:
            paragraphs, heading = carry + [heading] + paragraphs, carry_heading
        if len("\n\n".join(paragraphs)) < 200:
            carry, carry_heading = paragraphs, heading
            continue
        carry = []
        groups, current = [], []
        for paragraph in paragraphs:
            if current and len("\n\n".join(current + [paragraph])) > PASSAGE_TARGET:
                groups.append("\n\n".join(current))
                current = []
            current.append(paragraph)
        if current:
            groups.append("\n\n".join(current))
        out.extend({"heading": heading, "text": text} for text in groups)
    if carry:
        out.append({"heading": carry_heading, "text": "\n\n".join(carry)})
    return out


def reference_text(source: dict, raw: bytes) -> str:
    """The whole source as extracted, before any selection, for the verbatim proof."""
    if source["kind"] == "pdf":
        return "\n".join(pdf_pages(raw))
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(raw, "html.parser")
    for glyph in soup.select("a.toggle > span.control"):  # the "+" of a collapsible heading
        glyph.decompose()
    return soup.get_text(" ")


def extract(sources_path: Path, cache: Path, output: Path) -> dict:
    sources = load_sources(sources_path)
    lock = json.loads((cache / "sources.lock.json").read_bytes())
    if output.exists():
        raise ValueError("Output already exists; choose a new directory")
    documents, rejected, report = [], [], {"sources": {}, "pdftotext": pdftotext_version()}
    for source in sources:
        raw = read_cached(cache, lock, source["id"])
        sections, stats = (pdf_sections if source["kind"] == "pdf" else html_sections)(source, raw)
        reference = proof_key(reference_text(source, raw))
        kept = 0
        for index, passage in enumerate(passages_for(source, sections)):
            heading, chosen = passage["heading"], []
            # Checked paragraph by paragraph: an out-of-scope or unproven paragraph is left out
            # whole; a paragraph is never edited.
            for paragraph in passage["text"].split("\n\n"):
                items = []  # a list item is checked, and left out, on its own
                for item in paragraph.split("\n"):
                    problems = check_passage(source["id"], item)
                    if proof_key(item) not in reference:
                        problems.append("NOT VERBATIM")
                    if problems:
                        rejected.append({"source": source["id"], "heading": heading, "problems": problems, "text": item[:400]})
                    else:
                        items.append(item)
                if items:
                    chosen.append("\n".join(items))
            text = "\n\n".join(chosen)
            if len(text) < MIN_PASSAGE:
                if text:
                    rejected.append({"source": source["id"], "heading": heading, "problems": ["too short"], "text": text})
                continue
            kept += 1
            documents.append({"document_id": f"{source['id']}-{index:04d}", "title": f"{source['title']} — {heading}",
                              "source": lock["sources"][source["id"]]["url"], "license": LICENSE, "text": text})
        if source.get("pdf", {}).get("process_tables"):
            layout_reference = proof_key("\n".join(layout_pages(raw)))
            tables, table_stats = process_tables(source, raw)
            stats.update(table_stats)
            for number, table in enumerate(tables):
                problems = check_passage(source["id"], table["text"])
                unproven = [row for row in table["text"].split("\n") if proof_key(row) not in layout_reference]
                if unproven:
                    problems.append("NOT VERBATIM")
                if problems:
                    rejected.append({"source": source["id"], "heading": table["heading"], "problems": problems, "text": table["text"][:400]})
                    stats["tables_kept"] -= 1
                    continue
                kept += 1
                documents.append({"document_id": f"{source['id']}-table-{number:03d}",
                                  "title": f"{source['title']} — {table['heading']}",
                                  "source": lock["sources"][source["id"]]["url"], "license": LICENSE, "text": table["text"]})
        report["sources"][source["id"]] = {**stats, "sections": len(sections), "passages": kept}
        if kept == 0:
            raise ValueError(f"{source['id']}: no passage survived; the extraction is broken")
    not_verbatim = [r for r in rejected if "NOT VERBATIM" in r["problems"]]
    report.update({"documents": len(documents), "rejected": len(rejected), "not_verbatim": len(not_verbatim),
                   "text_bytes": sum(len(d["text"].encode()) for d in documents)})
    output.mkdir(parents=True)
    (output / "documents.jsonl").write_text("".join(json.dumps(d, ensure_ascii=False, sort_keys=True) + "\n" for d in documents), encoding="utf-8")
    (output / "rejected.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n" for r in rejected), encoding="utf-8")
    (output / "extract-report.json").write_bytes(canonical(report))
    print(f"{len(documents)} passages kept, {len(rejected)} rejected ({len(not_verbatim)} not verbatim)", flush=True)
    return report



# ---------------------------------------------------------------------------- build (offline)

def attribution(sources: list[dict], lock: dict) -> str:
    lines = [
        "Practical skills collection for Field Atlas.",
        "",
        "Every passage is quoted from a work of the United States federal government, which is not",
        "subject to copyright in the United States (17 U.S.C. 105). Licence: LicenseRef-US-Government-Work.",
        "Passages are selected, never reworded: only whitespace, line-break hyphenation and running page",
        "headers and footers were changed. Tables, figures, first aid, medicine and dosing, and the",
        "identification of wild plants, fungi or animals as food are left out. ATP 3-50.21 is read without",
        "chapter 2 (survival medicine) and chapter 4 (food). No publisher endorses Field Atlas.",
        "",
        "Sources (publisher | title | edition | URL | fetched UTC | SHA-256 of the file read):",
    ]
    for source in sources:
        entry = lock["sources"][source["id"]]
        lines.append(f"{source['publisher']} | {source['title']} | {source['edition']} | {entry['url']} | "
                     f"{entry['fetched']} | {entry['sha256']}")
    lines += ["", "The USDA Complete Guide to Home Canning (2015) is read from the National Center for Home Food",
              "Preservation (nchfp.uga.edu), which distributes the USDA's own files.", ""]
    return "\n".join(lines)


def add_attribution(artifacts, text: str) -> None:
    """Put ATTRIBUTION.txt inside the .fapack, list it with its hash in the manifest, and keep the
    manifest and SHA256SUMS beside the pack in step. Entries are written the way stream_pack
    writes them (stored, fixed timestamps), so the build stays byte-reproducible."""
    import shutil
    import zipfile
    from tools.packs.stream_pack import _canonical_json, _zip_info
    data = text.encode("utf-8")
    pack = artifacts.pack
    temporary = pack.with_name(pack.name + ".partial")
    with zipfile.ZipFile(pack) as source:
        manifest = json.loads(source.read("manifest.json"))
        if any(a["path"] == "ATTRIBUTION.txt" for a in manifest["artifacts"]):
            raise ValueError("Pack already carries ATTRIBUTION.txt")
        manifest["artifacts"].append({"bytes": len(data), "path": "ATTRIBUTION.txt", "sha256": sha(data)})
        manifest_bytes = _canonical_json(manifest)
        with zipfile.ZipFile(temporary, "w", allowZip64=True) as target:
            target.writestr(_zip_info("manifest.json", len(manifest_bytes)), manifest_bytes)
            info = source.getinfo("content.sqlite")
            with source.open(info) as src, target.open(_zip_info("content.sqlite", info.file_size), "w", force_zip64=True) as dst:
                shutil.copyfileobj(src, dst, 1 << 20)
            target.writestr(_zip_info("ATTRIBUTION.txt", len(data)), data)
    temporary.replace(pack)
    artifacts.manifest.write_bytes(manifest_bytes)
    database_hash = next(a["sha256"] for a in manifest["artifacts"] if a["path"] == "content.sqlite")
    artifacts.checksums.write_text(
        f"{database_hash}  content.sqlite\n{sha(manifest_bytes)}  manifest.json\n"
        f"{sha(data)}  ATTRIBUTION.txt\n{sha(pack.read_bytes())}  {pack.name}\n", encoding="ascii", newline="\n")


def build(sources_path: Path, cache: Path, output: Path, version: str) -> None:
    from tools.packs.stream_pack import build_pack_streaming
    from tools.packs.fapack_convert import verify_pack
    from tools.packs.build_general_reference import paragraph_chunks
    if output.exists():
        raise ValueError("Output already exists; choose a new build directory")
    sources = load_sources(sources_path)
    lock = json.loads((cache / "sources.lock.json").read_bytes())
    report = extract(sources_path, cache, output / "passages")
    artifacts = build_pack_streaming(
        output / "passages" / "documents.jsonl", output / "pack", PACK_ID, version, TITLE, LICENSE,
        [lock["sources"][s["id"]]["url"] for s in sources],
        coverage_summary="Home canning, food and water safety, storm, flood and quake readiness, shelter, fire, rescue signals, weather signs and navigation.",
        example_questions=["How much lemon juice goes in a quart of canned tomatoes?",
                           "How much water should I store per person?", "How do I find north with a stick's shadow?"],
        coverage_level="focused", fts_tokenizer="porter unicode61", chunker=paragraph_chunks)
    credits = attribution(sources, lock)
    add_attribution(artifacts, credits)
    verify_pack(artifacts.pack)
    (output / "ATTRIBUTION.txt").write_text(credits, encoding="utf-8", newline="\n")
    # Beside the unpacked manifest too, so package_search_upgrade.py carries it into the Leviathan version.
    (artifacts.pack.parent / "ATTRIBUTION.txt").write_text(credits, encoding="utf-8", newline="\n")
    (output / "sources.lock.json").write_bytes(canonical(lock))
    data = artifacts.pack.read_bytes()
    if len(data) >= 100 * 1000 * 1000:
        raise ValueError(f"Pack is {len(data):,} bytes; the collection must stay under 100 MB")
    (output / "quality-report.json").write_bytes(canonical({
        "status": "BUILT_NOT_ANSWER_VALIDATED", "pack": artifacts.pack.name, "pack_bytes": len(data),
        "pack_sha256": sha(data), "builder_sha256": sha(Path(__file__).read_bytes()), **report}))
    print(f"Built {artifacts.pack}: {len(data):,} bytes, SHA-256 {sha(data)}")


# ---------------------------------------------------------------------------- command line

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["fetch", "extract", "build"])
    parser.add_argument("--sources", type=Path, default=DEFAULT_SOURCES)
    parser.add_argument("--cache", type=Path, default=ROOT / "build/practical-skills/cache")
    parser.add_argument("--output", type=Path, default=ROOT / "build/practical-skills/out")
    parser.add_argument("--version", default="2026.11.09")
    args = parser.parse_args()
    if args.action == "fetch":
        fetch(args.sources, args.cache)
    elif args.action == "extract":
        extract(args.sources, args.cache, args.output)
    else:
        build(args.sources, args.cache, args.output, args.version)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
