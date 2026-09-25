#!/usr/bin/env python3
"""content2fapack — convert world_knowledge source corpora into FieldAtlas .fapack packs.

The ZIMs under world_knowledge/zim wrap the same content in reader HTML; FieldAtlas
indexes raw text, so this converter reads the original sources under content/ (the
same trees build_zims.py consumed) and emits packtool `documents.jsonl` directly:
one document per knowledge unit (wiki article, Q&A thread, paper, book, page, file).

Usage:
    python3 content2fapack.py <corpus>|all [--out DIR] [--limit N] [--keep-jsonl]

Only the Python standard library is required. Deterministic: identical inputs and
versions always produce byte-identical .fapack files (packtool handles the rest).
"""

from __future__ import annotations

import argparse
import hashlib
import html as html_mod
import json
import re
import sys
import time
import unicodedata
from pathlib import Path

CONTENT = Path("/home/v/world_knowledge/content")
REPO = Path(__file__).resolve().parent.parent                  # fieldatlas repo (packtool/)
WORK = REPO.parent                                             # working dir: fapacks/
DEFAULT_OUT = WORK / "fapacks"
PACKTOOL_REPO = REPO

PACK_VERSION = "1.0.0"
RAW_THRESHOLD = 16 * 1024 * 1024  # same cap as build_zims: never index oversized files
EXCLUDE_NAMES = {".venv", "__pycache__", ".git", "node_modules", ".DS_Store"}
TEXT_EXTS = {".txt", ".md", ".csv", ".tsv", ".json", ".jsonl", ".geojson", ".obo",
             ".fasta", ".dat", ".rdf", ".xml", ".yaml", ".yml", ".toml", ".ini",
             ".cfg", ".sh", ".py", ".rs", ".go", ".js", ".ts", ".c", ".h", ".hpp",
             ".cpp", ".cc", ".nr", ".sol", ".java", ".rb", ".sql", ".css", ".log",
             ".3", ".3p", ".2", ".1", ".2const", ".3const", ".3type", ".3macro",
             ".3lib", ".3head", ".3erl", ".4", ".5", ".6", ".7", ".8", ".9", ".aus", ".all"}

CTRL_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
WS_RE = re.compile(r"\n{3,}")
TAG_RE = re.compile(r"<[^>]+>")

# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

def clean(value: str, limit: int | None = None) -> str:
    """Normalise a text field the way packtool will, but never raise on it."""
    value = CTRL_RE.sub("", unicodedata.normalize("NFKC", value))
    value = value.replace("\r\n", "\n").replace("\r", "\n")
    if limit:
        value = value[:limit]
    return WS_RE.sub("\n\n", value).strip()


def slugify(value: str, budget: int) -> str:
    value = re.sub(r"[^a-z0-9._-]+", "-", value.lower()).strip("-._")
    if len(value) > budget:
        digest = hashlib.sha256(value.encode("utf-8")).hexdigest()[:12]
        value = f"{value[:max(1, budget - 13)].strip('-._')}-{digest}"
    return value


def make_id(prefix: str, *parts: str, budget: int = 128) -> str:
    """Document id: prefix + slugified parts, hash-shortened when too long."""
    body = slugify("-".join(p for p in parts if p), budget - len(prefix) - 1)
    if not body:
        raw = "-".join(parts)
        body = "x" + hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
    full = f"{prefix}-{body}"
    assert re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,127}", full), full
    return full


def html_to_text(text: str) -> str:
    """Same lossy HTML->text conversion build_zims uses for vendor text pages."""
    text = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", text)
    text = re.sub(r"(?i)<(br|/p|/div|/li|/h[1-6]|/tr|/td)[^>]*>", "\n", text)
    text = TAG_RE.sub("", text)
    return html_mod.unescape(text)


PUNCT_LINE_RE = re.compile(r"^[\[\]{<>(){}|~`+=_.,;:\"'*#!-]+$")


def first_line_title(text: str, fallback: str, limit: int = 200) -> str:
    for line in text.split("\n"):
        line = line.strip().strip("#").strip()
        if not line:
            continue
        if PUNCT_LINE_RE.match(line) or len(line) < 4:
            return fallback
        return line[:limit]
    return fallback


def emit(doc_id: str, title: str, source: str, license_: str, text: str):
    doc = {
        "document_id": doc_id,
        "title": clean(title, 300) or doc_id,
        "source": clean(source, 300) or "world_knowledge",
        "license": clean(license_, 200) or "unknown",
        "text": clean(text),
    }
    if not doc["text"]:
        return None
    return doc


# ---------------------------------------------------------------------------
# wikiextractor corpora (wikipedia, wiktionary, wikibooks, wikivoyage,
# wikisource, wikispecies)
# ---------------------------------------------------------------------------

DOC_RE = re.compile(r'^<doc id="([^"]+)" url="([^"]*)" title="((?:[^"\\]|\\.)*)">')


def iter_wiki_docs(root: Path):
    """Yield (doc_id, url, title, body) from wikiextractor fragments, streamed."""
    for fp in sorted(p for p in root.rglob("*") if p.is_file() and not p.name.endswith(".tsv")):
        doc_id = url = title = None
        body: list[str] = []
        with open(fp, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                if doc_id is None:
                    m = DOC_RE.match(line.rstrip("\n"))
                    if m:
                        doc_id, url, title = m.group(1), m.group(2), m.group(3)
                        body = []
                    continue
                if line.startswith("</doc>"):
                    yield doc_id, url, html_mod.unescape(title), "".join(body)
                    doc_id = url = title = None
                    body = []
                    continue
                body.append(line)


def corpus_wiki(name: str):
    def build():
        for did, url, title, body in iter_wiki_docs(CONTENT / name / "text"):
            text = body
            lines = text.split("\n")
            if lines and lines[0].strip() == title.strip():  # duplicated title heading
                text = "\n".join(lines[1:])
            source = url or f"{name}.org"
            if name == "wikispecies":
                source = "species.wikimedia.org"
            yield emit(f"wiki-{did}", title, source, "CC-BY-SA-4.0", text)
    return build


# ---------------------------------------------------------------------------
# stackexchange
# ---------------------------------------------------------------------------

Q_RE = re.compile(r'^=== Q id=(\d+) score=(-?\d+) views=(\d+) tags="([^"]*)"')
A_RE = re.compile(r"^--- A id=(\d+) score=(-?\d+)( accepted)?")


def _se_flush(qid, score, views, tags, blocks, site):
    if qid is None or not blocks:
        return None
    title = blocks[0][0].strip() if blocks[0] else ""
    q_body = "\n".join(blocks[0][1:]).strip()
    if not title or not q_body:
        return None
    parts = [q_body]
    for b in blocks[1:]:
        head = b[0].strip()
        parts.append(f"\n--- Answer{(' ' + head) if head else ''}\n" + "\n".join(b[1:]).strip())
    text = "\n".join(parts).strip()
    header = f"score {score} · views {views}" + (f" · tags: {tags}" if tags else "")
    return emit(make_id("se", site, qid), title, site, "CC-BY-SA-4.0", f"{header}\n\n{text}")


def corpus_stackexchange():
    se = CONTENT / "stackexchange"
    for fp in sorted((se / "text").glob("*.txt")):
        stem = fp.stem
        if stem.endswith("-Comments"):  # bare comment lines, no question headers
            continue
        if stem.endswith("-Posts"):  # stackoverflow.com dump split into Posts/Comments
            stem = stem[: -len("-Posts")]
        site = stem.lower()  # e.g. unix.stackexchange.com
        with open(fp, encoding="utf-8", errors="replace") as fh:
            qid = score = views = tags = None
            blocks: list[list[str]] = []
            cur: list[str] | None = None
            for line in fh:
                m = Q_RE.match(line)
                if m:
                    doc = _se_flush(qid, score, views, tags, blocks, site)
                    if doc:
                        yield doc
                    blocks = []
                    qid, score, views, tags = m.groups()
                    cur = None
                    continue
                if qid is None:
                    continue
                if A_RE.match(line):
                    cur = [line[4:].strip()]  # keep "A id=.. score=.. accepted" as block header
                    blocks.append(cur)
                    continue
                if cur is None and line.strip():
                    cur = []
                    blocks.append(cur)
                if cur is not None:
                    cur.append(line.rstrip("\n"))
            doc = _se_flush(qid, score, views, tags, blocks, site)
            if doc:
                yield doc


# ---------------------------------------------------------------------------
# literature (gutenberg, standard ebooks, slate star codex)
# ---------------------------------------------------------------------------

def corpus_literature():
    lit = CONTENT / "literature"
    catalog = {}
    cat = lit / "gutenberg" / "catalog.tsv"
    if cat.exists():
        with open(cat, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                parts = line.rstrip("\n").split("\t")
                if len(parts) >= 3 and parts[0].isdigit():
                    catalog[parts[0]] = parts[1]
    for fp in sorted((lit / "gutenberg").glob("*.txt")):
        text = fp.read_text(encoding="utf-8", errors="replace")
        title = catalog.get(fp.stem, f"Gutenberg ebook {fp.stem}")
        yield emit(make_id("lit-gut", fp.stem), title, "gutenberg.org",
                   "public-domain-US", text)
    for fp in sorted((lit / "standard-ebooks" / "txt").glob("*.txt")):
        text = fp.read_text(encoding="utf-8", errors="replace")
        yield emit(make_id("lit-se", fp.stem), fp.stem.replace("-", " ").title(),
                   "standardebooks.org", "public-domain-US", text)
    for fp in sorted((lit / "slate-star-codex" / "txt").glob("*.txt")):
        text = fp.read_text(encoding="utf-8", errors="replace")
        yield emit(make_id("lit-ssc", fp.stem), first_line_title(text, fp.stem),
                   "slatestarcodex.com", "CC-BY-SA-4.0", text)


# ---------------------------------------------------------------------------
# cryptography (text only; the PDF originals are skipped by design)
# ---------------------------------------------------------------------------

def corpus_crypto():
    cr = CONTENT / "crypto"
    idx = {}
    with open(cr / "iacr-index.tsv", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            p = line.rstrip("\n").split("\t")
            if len(p) >= 5:
                idx[p[0]] = (p[1], p[2], p[3], p[4])
    txt_root = cr / "iacr-eprint-text"
    for fp in sorted(txt_root.rglob("*.txt")):
        eid = str(fp.relative_to(txt_root).with_suffix(""))  # e.g. 2001/001
        meta = idx.get(eid)
        text = fp.read_text(encoding="utf-8", errors="replace")
        if meta:
            date, title, authors, abstract = meta
            head = f"IACR ePrint {eid} · {date} · {authors}\n\nAbstract: {abstract}\n\n"
        else:
            title, head = f"IACR ePrint {eid}", f"IACR ePrint {eid}\n\n"
        yield emit(make_id("iacr", eid.replace("/", "-")), title, "eprint.iacr.org",
                   "CC-BY-4.0", head + text)
    for sub in ("books", "nist-standards"):
        d = cr / sub
        if not d.exists():
            continue
        lic = "public-domain-US-Gov" if sub == "nist-standards" else "see-book-license"
        for fp in sorted(d.glob("*.txt")):
            if fp.stat().st_size >= RAW_THRESHOLD:
                continue
            text = fp.read_text(encoding="utf-8", errors="replace")
            yield emit(make_id(f"crypto-{sub}", fp.stem), first_line_title(text, fp.stem),
                       "nist.gov" if sub == "nist-standards" else "eprint.iacr.org",
                       lic, text)


# ---------------------------------------------------------------------------
# biology (pubmed jsonl, fight aging posts, reddit threads, text databases)
# ---------------------------------------------------------------------------

def corpus_biology():
    bio = CONTENT / "biology"
    for fp in sorted((bio / "pubmed").glob("*.jsonl")):
        corpus = fp.stem
        with open(fp, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    rec = json.loads(line)
                except json.JSONDecodeError:
                    continue
                pmid = str(rec.get("pmid", ""))
                abstract = str(rec.get("abstract") or "").strip()
                if not pmid or not abstract or abstract == "Not Available":
                    continue
                parts = [f"PMID {pmid}"]
                if rec.get("year"):
                    parts.append(f"Year: {rec['year']}")
                if rec.get("journal"):
                    parts.append(f"Journal: {rec['journal']}")
                if rec.get("authors"):
                    parts.append("Authors: " + ", ".join(map(str, rec["authors"])))
                parts.append("Abstract: " + abstract)
                mesh = rec.get("mesh") or rec.get("mesh_terms") or rec.get("MeSH")
                if mesh:
                    parts.append("MeSH: " + ("; ".join(map(str, mesh))
                                             if isinstance(mesh, list) else str(mesh)))
                yield emit(make_id("pm", f"{corpus}-{pmid}"),
                           str(rec.get("title", f"PMID {pmid}")),
                           "pubmed (NCBI)", "public-data-NCBI", "\n".join(parts))
    fa = bio / "longevity" / "fight-aging" / "posts"
    if fa.exists():
        for fp in sorted(fa.glob("*.txt")):
            txt = fp.read_text(encoding="utf-8", errors="replace")
            title, date, url_, rest_start = fp.stem, "", "", 0
            lines = txt.split("\n")
            for i, ln in enumerate(lines[:6]):
                if ln.startswith("Title: "):
                    title = ln[7:].strip()
                elif ln.startswith("Date: "):
                    date = ln[6:].strip()
                elif ln.startswith("URL: "):
                    url_ = ln[5:].strip()
                    rest_start = i + 1
            body = "\n".join(lines[rest_start:])
            head = " · ".join(p for p in (date, url_) if p)
            yield emit(make_id("fightaging", fp.stem), title, "fightaging.org",
                       "see-fightaging.org", (head + "\n\n" if head else "") + body)
    rl = bio / "longevity" / "reddit-longevity" / "top-threads.jsonl"
    if rl.exists():
        with open(rl, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    rec = json.loads(line)
                except json.JSONDecodeError:
                    continue
                rid = rec.get("id") or rec.get("url", "").rstrip("/").rsplit("/", 1)[-1]
                title = str(rec.get("title", f"r/longevity thread {rid}"))
                text = (rec.get("selftext") or "").strip()
                if not rid or not text:
                    continue
                head = f"score {rec.get('score', '?')} · {rec.get('url', '')}"
                yield emit(make_id("reddit-long", str(rid)), title, "reddit.com/r/longevity",
                           "reddit-terms", head + "\n\n" + text)
    for sub in ("longevity/databases", "databases", "biodefense", "clean-indoor-air"):
        yield from walk_tree(bio / sub, f"bio-{slugify(sub.replace('/', '-'), 20)}",
                             "biology (world_knowledge)", "public-data-see-content-README")


# ---------------------------------------------------------------------------
# generic trees (programming, data, geodata, travel, aztec, misc)
# ---------------------------------------------------------------------------

def walk_tree(root: Path, prefix: str, source: str, license_: str, title_from_line: bool = False):
    if not root.exists():
        return
    for fp in sorted(p for p in root.rglob("*") if p.is_file()):
        if any(part in EXCLUDE_NAMES for part in fp.parts):
            continue
        try:
            size = fp.stat().st_size
        except OSError:
            continue
        if size == 0 or size >= RAW_THRESHOLD:
            continue
        ext = fp.suffix.lower()
        if ext in TEXT_EXTS:
            raw = fp.read_text(encoding="utf-8", errors="replace")
        elif ext in (".html", ".htm"):
            raw = html_to_text(fp.read_text(encoding="utf-8", errors="replace"))
        else:
            continue
        rel = str(fp.relative_to(root))
        yield emit(make_id(prefix, rel),
                   first_line_title(raw, fp.name) if title_from_line else fp.name,
                   source, license_, raw)


def corpus_programming():
    yield from walk_tree(CONTENT / "programming", "prog", "programming docs (world_knowledge)",
                         "various-open-licenses", title_from_line=True)


def corpus_data():
    yield from walk_tree(CONTENT / "data", "data", "world data (world_knowledge)",
                         "open-data", title_from_line=True)


def corpus_geodata():
    yield from walk_tree(CONTENT / "geodata", "geodata", "geodata (world_knowledge)",
                         "public-domain-CC-BY", title_from_line=True)


def corpus_travel():
    yield from walk_tree(CONTENT / "travel", "travel", "travel (world_knowledge)",
                         "ODbL-open-data", title_from_line=True)


def corpus_aztec():
    yield from walk_tree(CONTENT / "aztec", "aztec", "github.com/AztecProtocol",
                         "Apache-2.0-MIT", title_from_line=True)


def corpus_misc():
    yield from walk_tree(CONTENT / "misc", "misc", "world_knowledge",
                         "unspecified", title_from_line=True)


def corpus_textbooks():
    root = CONTENT / "textbooks" / "openstax"
    for fp in sorted(root.glob("*.txt")):
        text = fp.read_text(encoding="utf-8", errors="replace")
        yield emit(make_id("os", fp.stem), fp.stem.replace("-", " ").title(),
                   "openstax.org", "CC-BY-4.0", text)


# ---------------------------------------------------------------------------
# corpus registry: name -> iterator, pack title, coverage, license, sources
# ---------------------------------------------------------------------------

def wiki_entry(name, title, summary, src, questions):
    return dict(iter=corpus_wiki(name), title=title, summary=summary, level="broad",
                license="CC-BY-SA-4.0", sources=[src], questions=questions)


CORPORA: dict[str, dict] = {
    "misc": dict(
        iter=corpus_misc, title="Misc analyses (world_knowledge)",
        summary="Standalone analyses and notes produced alongside the world_knowledge corpus.",
        level="demo", license="unspecified", sources=["world_knowledge"],
        questions=["What analyses are included in these notes?"]),
    "data": dict(
        iter=corpus_data, title="World data (world_knowledge)",
        summary="CIA Factbook, World Bank and OWID indicators, CODATA constants, periodic table, city coordinates.",
        level="focused", license="open-data",
        sources=["cia.gov/the-world-factbook", "data.worldbank.org", "ourworldindata.org", "nist.gov"],
        questions=["What is the population of Portugal?",
                   "What is the CODATA value of the speed of light?",
                   "Which country has the highest GDP per capita in the World Bank table?"]),
    "geodata": dict(
        iter=corpus_geodata, title="Geodata (world_knowledge)",
        summary="GeoNames city dumps, Natural Earth-derived country polygons, top-10k cities table.",
        level="focused", license="public-domain-CC-BY",
        sources=["naturalearthdata.com", "geonames.org"],
        questions=["What are the coordinates of Kyoto?",
                   "Which are the ten largest cities by population?"]),
    "travel": dict(
        iter=corpus_travel, title="Travel & transit (world_knowledge)",
        summary="GTFS schedules for 7 cities, airports/airlines/routes data, OSM city POIs.",
        level="focused", license="ODbL-open-data",
        sources=["transitfeeds.com", "ourairports.com", "openflights.org", "openstreetmap.org"],
        questions=["Which subway lines stop at Union Square in New York?",
                   "What is the IATA code of Berlin Brandenburg airport?"]),
    "textbooks": dict(
        iter=corpus_textbooks, title="OpenStax textbooks",
        summary="OpenStax CC-BY textbooks: biology, chemistry, physics, math, economics, psychology.",
        level="focused", license="CC-BY-4.0", sources=["openstax.org"],
        questions=["Explain the stages of mitosis.",
                   "What is the second law of thermodynamics?"]),
    "wikibooks": wiki_entry(
        "wikibooks", "Wikibooks (offline)", "78k Wikibooks pages: open textbooks, manuals and recipes.",
        "https://dumps.wikimedia.org/enwikibooks/", ["How do you cook a basic custard?"]),
    "wikivoyage": wiki_entry(
        "wikivoyage", "Wikivoyage travel guides", "34k Wikivoyage travel guides: cities, regions, itineraries, transit.",
        "https://dumps.wikimedia.org/enwikivoyage/", ["What should a first-time visitor see in Lisbon?"]),
    "wiktionary": wiki_entry(
        "wiktionary", "Wiktionary (offline)", "1.5M English Wiktionary entries: definitions, etymology, translations.",
        "https://dumps.wikimedia.org/enwiktionary/", ["What does the word petrichor mean?"]),
    "wikispecies": wiki_entry(
        "wikispecies", "Wikispecies (offline)", "941k Wikispecies taxonomy pages: species and classification.",
        "https://dumps.wikimedia.org/specieswiki/", ["What family does the European badger belong to?"]),
    "programming": dict(
        iter=corpus_programming, title="Programming documentation",
        summary="MDN, Python 3.14, Node.js, Rust, Go, cppreference, IETF RFCs, Linux and POSIX man pages.",
        level="broad", license="various-open-licenses",
        sources=["developer.mozilla.org", "docs.python.org", "nodejs.org", "rust-lang.org",
                 "go.dev", "cppreference.com", "rfc-editor.org", "kernel.org"],
        questions=["How do you declare an async function in Rust?",
                   "What does the POSIX man page say about fsync()?",
                   "How does HTTP/2 flow control work per RFC 9113?"]),
    "aztec": dict(
        iter=corpus_aztec, title="Aztec protocol documentation",
        summary="Aztec protocol docs and aztec-packages source snapshot.",
        level="focused", license="Apache-2.0-MIT", sources=["github.com/AztecProtocol"],
        questions=["How does the Aztec rollup handle private state?"]),
    "crypto": dict(
        iter=corpus_crypto, title="Cryptography (IACR ePrint + NIST, text)",
        summary="IACR ePrint 1996-2026 (25.7k papers) as full text, crypto textbooks, 17 NIST standards.",
        level="broad", license="CC-BY-4.0-public-domain",
        sources=["eprint.iacr.org", "nist.gov"],
        questions=["What is the Fiat-Shamir transform?",
                   "Summarize the CRYSTALS-Kyber key encapsulation mechanism.",
                   "How does Chaumian blind signing work?"]),
    "literature": dict(
        iter=corpus_literature, title="Literature (Gutenberg, Standard Ebooks, SSC)",
        summary="1,825 Project Gutenberg books, 100 Standard Ebooks, 874 Slate Star Codex essays.",
        level="broad", license="public-domain-CC-BY-SA",
        sources=["gutenberg.org", "standardebooks.org", "slatestarcodex.com"],
        questions=["Who wrote 'Pride and Prejudice'?",
                   "What is the plot of Frankenstein?"]),
    "biology": dict(
        iter=corpus_biology, title="Biology & longevity (world_knowledge)",
        summary="PubMed abstracts (longevity, biodefense, indoor air), Fight Aging archive, longevity databases.",
        level="broad", license="public-data",
        sources=["ncbi.nlm.nih.gov", "fightaging.org", "genomics.senescence.info"],
        questions=["What does rapamycin do in aging studies?",
                   "Which genes are associated with human longevity in GenAge?"]),
    "wikisource": wiki_entry(
        "wikisource", "Wikisource (offline)", "544k source documents: historical texts, speeches, public-domain books.",
        "https://dumps.wikimedia.org/enwikisource/", ["What does the Magna Carta say in its first chapter?"]),
    "stackexchange": dict(
        iter=corpus_stackexchange, title="Stack Exchange Q&A",
        summary="5.1M quality-filtered Q&A threads from 77 Stack Exchange sites (2024-04 dump).",
        level="broad", license="CC-BY-SA-4.0",
        sources=["archive.org/details/stackexchange"],
        questions=["How do I recursively change file permissions on Linux?",
                   "What is the difference between a mutex and a semaphore?"]),
    "wikipedia": wiki_entry(
        "wikipedia", "Wikipedia (offline text)", "All 7.1M English Wikipedia articles as offline full text (July 2026 dump).",
        "https://dumps.wikimedia.org/enwiki/", ["Who was the first woman to win a Nobel Prize?",
                                                "What is the capital of Australia?"]),
}

# build order: cheap corpora first, the giants last
ORDER = ["misc", "data", "geodata", "travel", "textbooks", "wikibooks", "wikivoyage",
         "wiktionary", "wikispecies", "aztec", "programming", "crypto", "biology",
         "literature", "wikisource", "stackexchange", "wikipedia"]


# ---------------------------------------------------------------------------
# runner
# ---------------------------------------------------------------------------

def build_corpus(name: str, spec: dict, out_root: Path, limit: int, keep_jsonl: bool,
               version: str = PACK_VERSION, fts_tokenizer: str = "unicode61") -> str:
    pack_id = f"world-knowledge-{name}"
    pack_dir = out_root / pack_id
    pack_file = pack_dir / f"{pack_id}-{version}.fapack"
    if pack_file.exists():
        return f"{name}: already built, skipped ({pack_file})"

    spool_dir = out_root / ".spool"
    spool_dir.mkdir(parents=True, exist_ok=True)
    spool = spool_dir / f"{name}.jsonl"
    started = time.time()
    written = skipped = 0
    seen_ids: set[str] = set()
    with open(spool, "w", encoding="utf-8") as fh:
        for doc in spec["iter"]():
            if doc is None:
                skipped += 1
                continue
            if doc["document_id"] in seen_ids:
                skipped += 1
                continue
            seen_ids.add(doc["document_id"])
            fh.write(json.dumps(doc, ensure_ascii=False) + "\n")
            written += 1
            if limit and written >= limit:
                break
            if written % 100_000 == 0:
                print(f"  [{name}] {written} docs, {fh.tell() / 1e6:.0f} MB, "
                      f"{time.time() - started:.0f}s", flush=True)
    print(f"[{name}] spooled {written} documents ({skipped} skipped) in "
          f"{time.time() - started:.0f}s -> {spool}", flush=True)
    if written == 0:
        spool.unlink(missing_ok=True)
        return f"{name}: FAILED - no documents produced"

    if str(PACKTOOL_REPO) not in sys.path:
        sys.path.insert(0, str(PACKTOOL_REPO))
    if str(PACKTOOL_REPO / "tools") not in sys.path:
        sys.path.insert(0, str(PACKTOOL_REPO / "tools"))
    from stream_pack import build_pack_streaming as build_pack  # noqa: E402

    artifacts = build_pack(
        input_path=spool,
        output_dir=pack_dir,
        pack_id=pack_id,
        version=version,
        fts_tokenizer=fts_tokenizer,
        title=spec["title"],
        license_id=spec["license"],
        source_urls=spec["sources"],
        coverage_summary=spec["summary"][:160],
        example_questions=spec["questions"][:6],
        coverage_level=spec["level"],
    )
    if not keep_jsonl:
        spool.unlink(missing_ok=True)
    return (f"{name}: built {artifacts.pack} "
           f"({artifacts.pack.stat().st_size / 1e6:.0f} MB, {written} docs, "
           f"{time.time() - started:.0f}s total)")


def main() -> None:
    ap = argparse.ArgumentParser(description="Convert world_knowledge corpora to FieldAtlas packs")
    ap.add_argument("corpus", help="corpus name or 'all'")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--limit", type=int, default=0, help="cap documents (testing)")
    ap.add_argument("--keep-jsonl", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--version", default=PACK_VERSION, help="pack version to stamp")
    ap.add_argument("--fts-tokenizer", default="unicode61",
                    choices=("unicode61", "porter unicode61"),
                    help="FTS5 tokenizer; 'porter unicode61' stems morphology (caused~cause)")
    args = ap.parse_args()

    if args.list:
        for n in ORDER:
            print(n)
        return
    names = ORDER if args.corpus == "all" else [args.corpus]
    for name in names:
        if name not in CORPORA:
            sys.exit(f"unknown corpus: {name} (use --list)")
    args.out.mkdir(parents=True, exist_ok=True)
    for name in names:
        try:
            print(f"[{name}] start", flush=True)
            print(build_corpus(name, CORPORA[name], args.out, args.limit, args.keep_jsonl,
                           args.version, args.fts_tokenizer), flush=True)
        except Exception as error:  # keep the batch going
            import traceback
            traceback.print_exc()
            print(f"[{name}] FAILED: {error}", flush=True)


if __name__ == "__main__":
    main()
