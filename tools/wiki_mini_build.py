#!/usr/bin/env python3
"""wikipedia-mini — regenerate the small Wikipedia KNOWLEDGE fapack end to end.

Self-contained pipeline, three stages, each skippable, so you can resume after an
interrupted download instead of re-fetching 20+ GB:

  1. CLONE   download the English Wikipedia articles dump from the Wikimedia archive:
       https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-pages-articles-multistream.xml.bz2
     (kept as <archive-dir>/enwiki-latest-pages-articles-multistream.xml.bz2)
  2. EXTRACT run WikiExtractor over the dump to get the plain-text article tree
     (pip install wikiextractor; point --extractor at the binary, e.g. a venv one:
       python3 -m venv /tmp/wikivenv && /tmp/wikivenv/bin/pip install wikiextractor)
  3. SELECT + BUILD  pick the article subset (every Wikipedia Vital article plus the
     most substantive filler articles under a total text budget), spool it as JSONL in
     the packtool document format, and build the .fapack via stream_pack — the exact
     byte-format code that produced every shipping pack — then re-verify the result
     the way the phone does.

The article subset rules (budgets, junk filters) are pinned below so rebuilds stay
comparable to the shipping pack. The vital-article list is snapshotted in
tools/wiki_vital_titles.json; --fetch-vital refreshes it from the live wiki.

Examples:
    # full regeneration from the Wikimedia archive (long; stages 1+2 dominate):
    python3 tools/wiki_mini_build.py --fetch-vital

    # resume from an already-extracted text tree (skips download + extraction):
    python3 tools/wiki_mini_build.py --text-root content2/wikipedia/text2

    # smoke test: first 5000 documents only, tiny budget, output to /tmp:
    python3 tools/wiki_mini_build.py --text-root content2/wikipedia/text2 \
        --limit-docs 5000 --text-budget 5000000 --out-dir /tmp/wiki-test
"""
from __future__ import annotations

import argparse
import html as H
import json
import re
import subprocess
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
REPO = TOOLS.parent                                            # fieldatlas repo (packtool/)
WORK = REPO.parent                                             # working dir: fapacks/, content2/
sys.path.insert(0, str(TOOLS))
sys.path.insert(0, str(REPO))

import content2fapack as c2f                                  # noqa: E402 (emit/iter helpers)
from stream_pack import build_pack_streaming                  # noqa: E402
from fapack_convert import verify_pack                         # noqa: E402

DUMP_URL = "https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-pages-articles-multistream.xml.bz2"
VITAL_SNAPSHOT = TOOLS / "wiki_vital_titles.json"
VITAL_PAGE = "Wikipedia:Vital articles/Level 5"   # the ~50k list (Level 4 is the 10k core)
API = "https://en.wikipedia.org/w/api.php"
# Wikimedia requires an identifying User-Agent (bare urllib gets 403).
USER_AGENT = "FieldAtlasWikiMiniBuilder/1.0 (https://fieldatlas.xyz; research tool; contact@app.fieldatlas.xyz)"


def open_url(url: str, timeout: int = 60):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    return urllib.request.urlopen(request, timeout=timeout)

DEFAULT_TEXT_ROOT = WORK / "content2" / "wikipedia" / "text2"
DEFAULT_ARCHIVE_DIR = WORK / "content2" / "wikipedia" / "archive"

# --- subset rules (pinned: identical to the shipping 2.0.0 pack) ---------------
TEXT_BUDGET = 1_950_000_000
MIN_BODY, FILLER_MAX = 800, 60_000
JUNK = [re.compile(p, re.I) for p in (
    r"\bdisambiguation\b",
    r"\bdiscography\b",
    r"^\d{3,4}s?$",
    r"^(january|february|march|april|may|june|july|august|september|october|november|december)\s+\d",
    r"\((\d{4}) (album|film|song|single|novel|election)\)$",
    r"^(men's |women's )?\d{4} .* (football|basketball|baseball|cricket|tennis|hockey) league",
)]
LISTLIKE = re.compile(r"^(list|index|outline|glossary) of ", re.I)

PACK_TITLE = "Wikipedia Mini (offline essence)"
SUMMARY = ("Every Wikipedia Vital article plus the most substantive remaining articles "
           "from the English dump, curated for offline research.")
QUESTIONS = [
    "Who was the first woman to win a Nobel Prize?",
    "What is the capital of Australia?",
    "How do vaccines work?",
]


def norm(title: str) -> str:
    return unicodedata.normalize("NFKC", H.unescape(title or "").replace("_", " ")).strip().casefold()


def junk(title: str) -> bool:
    return any(rx.search(title) for rx in JUNK)


# ---------------------------------------------------------------------------
# stage 1: clone the dump from the Wikimedia archive
# ---------------------------------------------------------------------------

def ensure_dump(archive_dir: Path, url: str, dry_run: bool) -> Path:
    target = archive_dir / url.rsplit("/", 1)[-1].split("?")[0]
    if target.is_file() and target.stat().st_size > 1_000_000_000:
        print(f"[clone] reusing {target} ({target.stat().st_size / 1e9:.1f} GB)")
        return target
    if dry_run:
        print(f"[clone] would download {url} -> {target}")
        raise SystemExit(0)
    archive_dir.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(target.suffix + ".part")
    print(f"[clone] downloading {url}\n          (~22 GB, resumable by rerunning)")
    with open_url(url) as response, partial.open("ab") as out:
        out.seek(0, 2)
        while True:
            chunk = response.read(1 << 22)
            if not chunk:
                break
            out.write(chunk)
    partial.replace(target)
    return target


# ---------------------------------------------------------------------------
# stage 2: WikiExtractor -> plain-text tree
# ---------------------------------------------------------------------------

def ensure_text_root(dump: Path, text_root: Path, extractor: str, dry_run: bool) -> Path:
    markers = [p for p in text_root.rglob("*") if p.is_file() and p.stat().st_size > 0][:3]
    if markers:
        print(f"[extract] reusing {text_root}")
        return text_root
    if dry_run:
        print(f"[extract] would run {extractor} --processes 12 --discard_empty "
              f"-o {text_root} {dump}")
        raise SystemExit(0)
    text_root.parent.mkdir(parents=True, exist_ok=True)
    cmd = [extractor, "--processes", "12", "--discard_empty", "-q",
           "-o", str(text_root), str(dump)]
    print(f"[extract] {' '.join(cmd)}  (this takes hours)")
    subprocess.run(cmd, check=True)
    return text_root


# ---------------------------------------------------------------------------
# vital-article list: snapshot on disk, or refreshed from the live wiki
# ---------------------------------------------------------------------------

def _parse_links(page: str) -> list[dict]:
    """All links from a page, paginated (action=parse keeps its continue token inside parse)."""
    params = {"action": "parse", "page": page, "prop": "links", "plnamespace": "0",
              "pllimit": "500", "format": "json", "redirects": "1"}
    out: list[dict] = []
    while True:
        query = "&".join(f"{k}={urllib.parse.quote_plus(str(v))}" for k, v in params.items())
        with open_url(f"{API}?{query}", timeout=60) as response:
            payload = json.load(response)
        parsed = payload["parse"]
        out.extend(parsed["links"])
        token = payload.get("continue") or parsed.get("continue") or {}
        if not token:
            return out
        params.update(token)


def _all_level4_pages() -> list[str]:
    """Enumerate the vital-list index + topic pages + nested subpages via allpages."""
    prefix = VITAL_PAGE.split(":", 1)[1]          # "Vital articles/Level 5"
    pages: list[str] = []
    cont: dict[str, str] = {}
    while True:
        query = {"action": "query", "list": "allpages", "apnamespace": "4",
                 "apprefix": prefix, "aplimit": "100", "format": "json"}
        query.update(cont)
        url = f"{API}?" + "&".join(f"{k}={urllib.parse.quote_plus(str(v))}" for k, v in query.items())
        with open_url(url, timeout=60) as response:
            payload = json.load(response)
        pages.extend(p["title"] for p in payload["query"]["allpages"])
        if "continue" not in payload:
            break
        cont = payload["continue"]
    keep = [
        p for p in pages
        if not p.endswith("/") and "/Article alerts" not in p and "draft" not in p.lower()
    ]
    if len(keep) < 10:
        raise SystemExit(f"only {len(keep)} {VITAL_PAGE} pages found — Wikimedia layout changed?")
    return keep


def fetch_vital(out_path: Path) -> Path:
    """Every namespace-0 article linked from the Level-4 topic pages (all depths).

    Wikipedia organises the ~50k vital articles across topic pages (Arts, People, ...)
    and nested subpages (People/Scientists, ..., Technology/Transportation). allpages
    enumerates them all; each one links its articles directly.
    """
    pages = _all_level4_pages()
    print(f"[vital] {len(pages)} topic pages to scan")
    titles: set[str] = set()
    for page in pages:
        before = len(titles)
        for link in _parse_links(page):
            if link.get("ns", 0) == 0:
                titles.add(link["*"])
        print(f"[vital]   {page.replace(VITAL_PAGE + '/', '') or page}: {len(titles) - before}")
    data = sorted(titles)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    print(f"[vital] fetched {len(data)} titles -> {out_path}")
    return out_path


# ---------------------------------------------------------------------------
# stage 3a: scan the text tree and choose the subset
# ---------------------------------------------------------------------------

def scan(text_root: Path, vital: set[str], limit_docs: int, text_budget: int) -> set[str]:
    vital_ids: set[str] = set()
    fillers: list[tuple[int, str]] = []
    vital_bytes = 0
    for i, (did, _url, title, body) in enumerate(c2f.iter_wiki_docs(text_root)):
        if limit_docs and i >= limit_docs:
            break
        if i and i % 500_000 == 0:
            print(f"[scan {i} docs, vital {len(vital_ids)}, fillers {len(fillers)}]", flush=True)
        title = H.unescape(title or "")
        size = len(body)
        if norm(title) in vital:
            vital_ids.add(f"wiki-{did}")
            vital_bytes += size
        elif MIN_BODY <= size <= FILLER_MAX and not junk(title) and not (LISTLIKE.match(title) and size < 2500):
            fillers.append((size, f"wiki-{did}"))
    fillers.sort(reverse=True)
    keep, fill_bytes = set(vital_ids), 0
    for size, doc_id in fillers:
        if vital_bytes + fill_bytes >= text_budget:
            break
        keep.add(doc_id)
        fill_bytes += size
    print(f"[scan done] vital={len(vital_ids)} ({vital_bytes / 1e9:.2f} GB) "
          f"filler={len(keep) - len(vital_ids)} ({fill_bytes / 1e9:.2f} GB) total={len(keep)} docs",
          flush=True)
    return keep


# ---------------------------------------------------------------------------
# stage 3b: spool JSONL (packtool document format) and build the pack
# ---------------------------------------------------------------------------

def spool(text_root: Path, keep: set[str], path: Path, limit_docs: int) -> int:
    written = 0
    with path.open("w", encoding="utf-8") as out:
        for i, (did, url, title, body) in enumerate(c2f.iter_wiki_docs(text_root)):
            if limit_docs and i >= limit_docs:
                break
            doc_id = f"wiki-{did}"
            if doc_id not in keep:
                continue
            text = body
            lines = text.split("\n")
            title = H.unescape(title or "")
            if lines and lines[0].strip() == title.strip():
                text = "\n".join(lines[1:])          # drop duplicated title heading
            doc = c2f.emit(doc_id, title, url or "en.wikipedia.org", "CC-BY-SA-4.0", text)
            if doc is None:
                continue
            out.write(json.dumps(doc, ensure_ascii=False) + "\n")
            written += 1
            if written % 200_000 == 0:
                print(f"[spool {written} docs]", flush=True)
    return written


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--text-root", type=Path, default=DEFAULT_TEXT_ROOT,
                        help="already-extracted WikiExtractor tree (skips stages 1-2 when present)")
    parser.add_argument("--archive-dir", type=Path, default=DEFAULT_ARCHIVE_DIR)
    parser.add_argument("--dump-url", default=DUMP_URL)
    parser.add_argument("--extractor", default="wikiextractor",
                        help="WikiExtractor binary (e.g. /tmp/wikivenv/bin/wikiextractor)")
    parser.add_argument("--vital", type=Path, default=VITAL_SNAPSHOT)
    parser.add_argument("--fetch-vital", action="store_true", help="refresh the vital list first")
    parser.add_argument("--out-dir", type=Path, default=WORK / "fapacks")
    parser.add_argument("--pack-id", default="world-knowledge-wikipedia-mini")
    parser.add_argument("--version", default="2.0.0")
    parser.add_argument("--text-budget", type=int, default=TEXT_BUDGET)
    parser.add_argument("--limit-docs", type=int, default=0, help="scan only the first N docs (smoke test)")
    parser.add_argument("--keep-jsonl", action="store_true")
    parser.add_argument("--dry-run", action="store_true", help="stop where a download would begin")
    args = parser.parse_args(argv)

    started = time.time()
    text_root = args.text_root
    # valid extracted tree = at least one non-empty file anywhere below it
    has_docs = text_root.is_dir() and any(
        p.is_file() and p.stat().st_size > 0 for p in text_root.rglob("*")
    )
    if has_docs:
        print(f"[source] using existing text tree {text_root}")
    else:
        dump = ensure_dump(args.archive_dir, args.dump_url, args.dry_run)
        text_root = ensure_text_root(dump, text_root, args.extractor, args.dry_run)

    vital_path = args.vital
    if args.fetch_vital:
        vital_path = fetch_vital(args.vital)
    if not vital_path.is_file():
        sys.exit(f"vital list missing: {vital_path} (use --fetch-vital)")
    vital = {norm(t) for t in json.loads(vital_path.read_text(encoding="utf-8"))}
    print(f"[vital] {len(vital)} titles from {vital_path}")

    keep = scan(text_root, vital, args.limit_docs, args.text_budget)

    spool_path = args.out_dir / ".spool" / "wikipedia-mini.jsonl"
    spool_path.parent.mkdir(parents=True, exist_ok=True)
    count = spool(text_root, keep, spool_path, args.limit_docs)
    print(f"[spool] {count} documents -> {spool_path}", flush=True)
    if count == 0:
        sys.exit("nothing spooled — subset empty?")

    output_dir = args.out_dir / args.pack_id
    if output_dir.exists():
        import shutil
        shutil.rmtree(output_dir)
    artifacts = build_pack_streaming(
        input_path=spool_path,
        output_dir=output_dir,
        pack_id=args.pack_id,
        version=args.version,
        title=PACK_TITLE,
        license_id="CC-BY-SA-4.0",
        source_urls=["https://dumps.wikimedia.org/enwiki/",
                     "https://en.wikipedia.org/wiki/Wikipedia:Vital_articles"],
        coverage_summary=SUMMARY[:160],
        example_questions=QUESTIONS[:6],
        coverage_level="broad",
        fts_tokenizer="porter unicode61",
    )
    summary = verify_pack(artifacts.pack)
    if not args.keep_jsonl:
        spool_path.unlink(missing_ok=True)
    print(f"built {artifacts.pack} ({summary['bytes'] / 1e6:.0f} MB, {summary['chunks']} chunks) "
          f"in {time.time() - started:.0f}s; verified hashes + FTS sample hit: {summary['sample_hit']!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
