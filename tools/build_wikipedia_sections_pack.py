#!/usr/bin/env python3
"""Build a keyword-searchable pack of English Wikipedia's vital articles, one passage per section.

Simple English Wikipedia defines a subject but rarely explains its causes or mechanisms; the full
English article usually does. Its dump is 43 GB, so this tool fetches only the listed articles
through the Wikipedia API instead, each pinned to the revision it read.

  fetch  Reads a JSON list of titles (tools/wiki_vital_titles.json, the Level 5 vital articles)
         and appends one record per title to a JSON-lines spool: the article's plain text with
         its section headings, page and revision ids, and up to eight redirects as aliases.
         Resumable: titles already in the spool are skipped. Network; stays under Wikimedia's
         limit of 200 requests a minute for unauthenticated bots
         (https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits).
  build  Offline and deterministic. Each article becomes "<Article> — Overview" for its lead,
         with an "Also known as:" line as in the Simple English pack, and "<Article> — <Section>"
         or "<Article> — <Section> > <Subsection>" for the rest, as in the travel guides pack.
         Reference sections are left out, and each article keeps its sections in order up to a
         text budget. Sources link to the exact revision (oldid) that was read.

Keyword search only.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from stream_pack import build_pack_streaming  # noqa: E402

PACK_ID = "wikipedia-en-vital"
LICENSE = "CC-BY-SA-4.0"
API = "https://en.wikipedia.org/w/api.php"
USER_AGENT = "FieldAtlas-pack-builder/0.1 (+https://github.com/0x94t3z/fieldatlas)"
DEFAULT_TITLES = Path(__file__).resolve().parent / "wiki_vital_titles.json"
# Wikimedia allows unauthenticated bots 200 requests a minute; 180 leaves a margin.
REQUESTS_PER_SECOND = 3.0
MAX_ALIASES = 8
MIN_SECTION_CHARS = 80
# The lead and the sections that explain a subject come first; history, naming and reception fill
# what is left. Microwave oven opens with five history sections before "Principles", and Inflation
# with its history before "Causes", so taking sections in article order would keep the wrong ones.
ARTICLE_CHARS = 16_000
HEADING = re.compile(r"^(={2,6})\s*(.*?)\s*\1\s*$", re.M)
# Lists of other works, not explanation.
SKIP_SECTIONS = {
    "see also", "references", "notes", "external links", "further reading", "bibliography", "sources",
    "citations", "footnotes", "gallery", "works cited", "explanatory notes", "notes and references",
    "references and notes", "primary sources", "secondary sources", "general references", "cited sources",
    "external links and further reading", "general and cited references", "notes and citations",
}
LATER_SECTIONS = {
    "history", "timeline", "etymology", "name", "names", "naming", "terminology", "etymology and terminology",
    "name and etymology", "names and etymology", "in popular culture", "popular culture", "in culture",
    "cultural references", "in fiction", "legacy", "reception", "awards", "honours", "honors", "filmography",
    "discography", "works", "selected works",
}


def api_url(title: str) -> str:
    query = {
        "action": "query", "format": "json", "formatversion": "2", "redirects": "1", "maxlag": "5",
        "prop": "extracts|revisions|redirects|pageprops", "explaintext": "1", "exsectionformat": "wiki",
        "rvprop": "ids", "rdnamespace": "0", "rdlimit": "max", "ppprop": "disambiguation", "titles": title,
    }
    return API + "?" + urllib.parse.urlencode(query)


class Pace:
    """Starts at most [rate] requests a second across all workers; a 429 pauses every worker,
    including one already waiting for a slot it reserved before the pause."""

    def __init__(self, rate: float):
        self.interval = 1.0 / rate
        self.lock = threading.Lock()
        self.next_start = time.monotonic()
        self.paused_until = 0.0
        self.limited = 0

    def wait(self) -> None:
        with self.lock:
            now = time.monotonic()
            start = max(now, self.next_start)
            self.next_start = start + self.interval
        time.sleep(max(0.0, start - now))
        while True:
            with self.lock:
                remaining = self.paused_until - time.monotonic()
            if remaining <= 0:
                return
            time.sleep(remaining)

    def pause(self, seconds: float) -> None:
        with self.lock:
            self.limited += 1
            until = time.monotonic() + seconds
            self.paused_until = max(self.paused_until, until)
            self.next_start = max(self.next_start, until)


def fetch_one(title: str, pace: Pace, attempts: int = 6) -> dict:
    """One article's record; raises after repeated failures so the title stays unfetched."""
    delay = 5.0
    for attempt in range(attempts):
        pace.wait()
        try:
            # Article text compresses about fourfold, and a slow link is what limits the fetch.
            request = urllib.request.Request(api_url(title), headers={"User-Agent": USER_AGENT, "Accept-Encoding": "gzip"})
            with urllib.request.urlopen(request, timeout=60) as response:
                raw = response.read()
                if response.headers.get("Content-Encoding") == "gzip":
                    raw = gzip.decompress(raw)
                body = json.loads(raw)
            if body.get("error", {}).get("code") == "maxlag":
                pace.pause(max(5.0, float(body["error"].get("lag", 5))))
                continue
            if "error" in body:
                raise RuntimeError(body["error"].get("info", "API error"))
            page = (body.get("query", {}).get("pages") or [{}])[0]
            if page.get("missing") or page.get("invalid") or "extract" not in page:
                return {"requested": title, "missing": True}
            name = page["title"]
            # Redirects come oldest first, and the oldest are the established names ("Hurricane",
            # "Flu"); misspellings come later. Case, punctuation and plural variants add nothing a
            # search does not already match.
            aliases, seen = [], {normalize(name)}
            for redirect in page.get("redirects", []):
                alias = redirect.get("title", "")
                key = normalize(alias)
                if not alias or "/" in alias or alias.startswith("List of") or key in seen or \
                        key.rstrip("s") in seen or key + "s" in seen:
                    continue
                seen.add(key)
                aliases.append(alias)
            return {
                "requested": title,
                "title": name,
                "pageid": page["pageid"],
                "revid": (page.get("revisions") or [{}])[0].get("revid"),
                "disambiguation": "disambiguation" in page.get("pageprops", {}),
                "aliases": aliases[:MAX_ALIASES],
                "extract": page["extract"],
            }
        except urllib.error.HTTPError as error:
            if error.code not in (429, 500, 502, 503, 504):
                raise
            # Wikimedia asks clients to honour Retry-After, or wait at least five seconds; every
            # worker waits, including after this title's last attempt.
            pace.pause(max(5.0, float(error.headers.get("Retry-After") or delay)))
            if attempt == attempts - 1:
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            if attempt == attempts - 1:
                raise
            time.sleep(delay)
        delay = min(delay * 2, 120.0)
    raise RuntimeError(f"gave up on {title}")


def normalize(title: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", title.lower())


def truncate_partial(spool_path: Path, step: int = 1_000_000) -> None:
    """An interrupted run can leave half a line; appending after it would spoil the next record. The
    file is cut back to just after its last newline, searching back in steps however long that
    half line is (a record of a long article runs to most of a megabyte)."""
    with spool_path.open("rb+") as spool:
        end = spool.seek(0, 2)
        if not end:
            return
        spool.seek(end - 1)
        if spool.read(1) == b"\n":
            return
        position, cut = end, 0
        while position > 0:
            start = max(0, position - step)
            spool.seek(start)
            newline = spool.read(position - start).rfind(b"\n")
            if newline >= 0:
                cut = start + newline + 1
                break
            position = start
        spool.truncate(cut)


def fetch(titles_path: Path, spool_path: Path, workers: int, limit: int, rate: float = REQUESTS_PER_SECOND) -> int:
    titles = json.loads(titles_path.read_text(encoding="utf-8"))
    done = set()
    if spool_path.exists():
        truncate_partial(spool_path)
        with spool_path.open(encoding="utf-8", errors="replace") as spool:
            for line in spool:
                try:
                    done.add(json.loads(line)["requested"])
                except (json.JSONDecodeError, KeyError):
                    continue  # a line cut short by an interrupted run; its title is fetched again
    todo = [t for t in titles if t not in done]
    if limit:
        todo = todo[:limit]
    print(f"{len(done)} already fetched, {len(todo)} to fetch with {workers} workers at {rate}/s", flush=True)
    pace = Pace(rate)
    lock = threading.Lock()
    started, counts = time.monotonic(), {"ok": 0, "failed": 0}
    spool_path.parent.mkdir(parents=True, exist_ok=True)
    with spool_path.open("a", encoding="utf-8") as spool:
        def work(title: str) -> None:
            try:
                record = fetch_one(title, pace)
            except Exception as error:  # noqa: BLE001 - one bad title must not stop the run
                with lock:
                    counts["failed"] += 1
                    print(f"failed: {title}: {error}", file=sys.stderr, flush=True)
                return
            line = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
            with lock:
                spool.write(line)
                counts["ok"] += 1
                if counts["ok"] % 500 == 0:
                    spool.flush()
                    done_rate = counts["ok"] / (time.monotonic() - started)
                    print(f"{counts['ok']} fetched, {counts['failed']} failed, {done_rate:.1f}/s, "
                          f"{pace.limited} rate-limit pauses", flush=True)

        with ThreadPoolExecutor(max_workers=workers) as pool:
            list(pool.map(work, todo))
    print(f"done: {counts['ok']} fetched, {counts['failed']} failed, {pace.limited} rate-limit pauses", flush=True)
    return 1 if counts["failed"] else 0


def sections(extract: str):
    """(path, text) for the lead and every section, path being () or ("History", "Origins")."""
    matches = list(HEADING.finditer(extract))
    yield (), extract[: matches[0].start() if matches else len(extract)]
    trail: list[tuple[int, str]] = []
    for index, match in enumerate(matches):
        level = len(match.group(1))
        trail = [(lvl, name) for lvl, name in trail if lvl < level] + [(level, match.group(2).strip())]
        end = matches[index + 1].start() if index + 1 < len(matches) else len(extract)
        yield tuple(name for _, name in trail), extract[match.end():end]


MATHS = ("{\\displaystyle", "{\\textstyle")


def latex(lines: list[str]) -> str:
    """The LaTeX inside the first {\\displaystyle ...} or {\\textstyle ...} of [lines], braces balanced."""
    text = " ".join(line.strip() for line in lines)
    starts = [(position, prefix) for prefix in MATHS if (position := text.find(prefix)) >= 0]
    if not starts:
        return ""
    start, prefix = min(starts)
    depth, body = 0, []
    for char in text[start:]:
        depth += char == "{"
        depth -= char == "}"
        body.append(char)
        if depth == 0:
            break
    return "".join(body)[len(prefix):-1].strip()


def prose(line: str) -> bool:
    """A line of sentence among a formula's one-token MathML lines: several words, not a symbol."""
    words = line.split()
    return len(words) >= 3 and len(line.strip()) >= 12


def latex_end(run: list[str], first: int) -> int:
    """Index of the line in [run] where the formula starting at run[first] closes its last brace."""
    depth = 0
    for index in range(first, len(run)):
        for char in run[index]:
            depth += char == "{"
            depth -= char == "}"
        if depth <= 0:
            return index
    return len(run) - 1


def formulas(text: str) -> str:
    """TextExtracts writes a formula as one indented token per line ("A", "=", "π"...) followed by
    its LaTeX source; keep the compact LaTeX in its place, inside the sentence it belongs to."""
    lines, out, i = text.split("\n"), [], 0
    indented = lambda line: line.startswith("  ") or (line != "" and not line.strip())
    while i < len(lines):
        if not indented(lines[i]):
            out.append(lines[i])
            i += 1
            continue
        j = i
        while j < len(lines) and indented(lines[j]):
            j += 1
        run = lines[i:j]
        found = [k for k, line in enumerate(run) if line.strip().startswith(MATHS)]
        if not found:
            out.extend(run)
            i = j
            continue
        # Each formula's LaTeX in its place, and the prose between and after the formulas, which the
        # extract indents like them. The one-token lines before each LaTeX are its MathML rendering.
        parts, previous_end = [], -1
        for k in found:
            parts += [line.strip() for line in run[previous_end + 1:k] if prose(line)]
            parts.append(latex(run[k:]))
            previous_end = latex_end(run, k)
        parts += [line.strip() for line in run[previous_end + 1:] if line.strip()]
        before = out.pop() if out else ""
        # A sentence that goes on after an inline formula continues on a line with one leading space.
        after = lines[j] if j < len(lines) and lines[j].startswith(" ") else None
        tail = (after or "").strip()
        out.append(" ".join(part for part in (before.rstrip(), *parts, tail) if part))
        i = j + (1 if after is not None else 0)
    return "\n".join(out)


def clean(text: str) -> str:
    text = formulas(text)
    text = re.sub(r"[ \t]+\n", "\n", text)
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def distance(a: str, b: str, cap: int = 3) -> int:
    """Edit distance, stopping early once it exceeds [cap]."""
    if abs(len(a) - len(b)) > cap:
        return cap + 1
    row = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        previous, row[0] = row[0], i
        for j, cb in enumerate(b, 1):
            previous, row[j] = row[j], min(row[j] + 1, row[j - 1] + 1, previous + (ca != cb))
        if min(row) > cap:
            return cap + 1
    return row[-1]


def useful_aliases(title: str, aliases: list[str], keep: int = 6) -> list[str]:
    """Redirects worth listing: not a misspelling of the title or of a kept name ("Huricane"),
    not an old CamelCase page name ("AppliedStatistics"), and containing letters."""
    kept, seen = [], [normalize(title)]
    for alias in aliases:
        key = normalize(alias)
        if not re.search(r"[A-Za-z]", alias) or re.fullmatch(r"[A-Z][a-z]+(?:[A-Z][a-z]+)+", alias):
            continue
        if any(len(key) >= 5 and len(other) >= 5 and distance(key, other, 2) <= 2 for other in seen):
            continue
        seen.append(key)
        kept.append(alias)
        if len(kept) == keep:
            break
    return kept


def article_documents(record: dict, article_chars: int = ARTICLE_CHARS):
    if record.get("missing") or record.get("disambiguation") or not record.get("revid"):
        return
    source = f"https://en.wikipedia.org/w/index.php?oldid={record['revid']}"
    candidates = []
    for number, (path, body) in enumerate(sections(record["extract"])):
        if path and path[0].lower() in SKIP_SECTIONS:
            continue
        text = clean(body)
        if not path and not text:
            return  # the app reaches an article through its lead ("<Article> — Overview")
        if len(text) >= MIN_SECTION_CHARS or not path:
            rank = 0 if not path else 2 if path[0].lower() in LATER_SECTIONS else 1
            candidates.append((rank, number, path, text))
    kept, budget = [], article_chars
    for rank, number, path, text in sorted(candidates, key=lambda c: (c[0], c[1])):
        # The lead always stays; a later section that does not fit is left out whole, and a
        # shorter one after it may still fit.
        if rank and len(text) > budget:
            continue
        budget -= len(text)
        kept.append((number, path, text))
    aliases = useful_aliases(record["title"], record.get("aliases", []))
    for number, path, text in sorted(kept, key=lambda k: k[0]):
        if not path and aliases:
            text += "\n\nAlso known as: " + "; ".join(aliases)
        yield {
            "document_id": f"enw-{record['pageid']}-{number:04d}",
            "title": f"{record['title']} — {' > '.join(path) if path else 'Overview'}",
            "source": source,
            "license": LICENSE,
            "text": text,
        }


def spool_index(path: Path) -> tuple[list[tuple[str, int]], str]:
    """(title, byte offset) of each article's first record, in title order, and the SHA-256 of the
    bytes read. A line cut short (even inside a character) is skipped; its title is fetched again."""
    first, digest, offset = {}, hashlib.sha256(), 0
    with path.open("rb") as spool:
        for raw in spool:
            digest.update(raw)
            try:
                record = json.loads(raw.decode("utf-8", errors="replace"))
            except json.JSONDecodeError:
                record = None
            if record and not record.get("missing") and "pageid" in record:
                first.setdefault(record["pageid"], (record["title"], offset))
            offset += len(raw)
    return sorted(first.values()), digest.hexdigest()


def spool_records(path: Path, index: list[tuple[str, int]]):
    """Records in title order with duplicates removed, so a resumed fetch builds the same pack."""
    with path.open("rb") as spool:
        for _, offset in index:
            spool.seek(offset)
            yield json.loads(spool.readline().decode("utf-8", errors="replace"))


def build(spool_path: Path, out: Path, version: str, article_chars: int) -> int:
    index, digest = spool_index(spool_path)
    out.mkdir(parents=True, exist_ok=True)
    articles = documents = 0
    revisions = []
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".jsonl", prefix="wikipedia-en-",
                                     dir=out, delete=False) as staged:
        staged_path = Path(staged.name)
        for record in spool_records(spool_path, index):
            count = 0
            for document in article_documents(record, article_chars):
                staged.write(json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n")
                count += 1
            if count:
                articles += 1
                documents += count
                revisions.append(f"{record['title']} | https://en.wikipedia.org/w/index.php?oldid={record['revid']}")
    try:
        artifacts = build_pack_streaming(
            input_path=staged_path,
            output_dir=out / PACK_ID,
            pack_id=PACK_ID,
            version=version,
            title="Encyclopedia in depth (English Wikipedia)",
            license_id=LICENSE,
            source_urls=["https://en.wikipedia.org/wiki/Wikipedia:Vital_articles/Level/5",
                         "https://en.wikipedia.org/wiki/Wikipedia:Text_of_the_Creative_Commons_Attribution-ShareAlike_4.0_International_License"],
            coverage_summary=f"{articles:,} of English Wikipedia's vital articles by section: what a subject is, "
                             "how it works, its causes and effects. A snapshot; not live.",
            example_questions=[
                "How does a heat pump heat a house?",
                "What causes inflation?",
                "Why did the Silk Road matter for trade?",
            ],
            coverage_level="broad",
            fts_tokenizer="porter unicode61",
        )
    finally:
        staged_path.unlink(missing_ok=True)
    (artifacts.pack.parent / "SOURCE.json").write_text(json.dumps(
        {"spool": spool_path.name, "spool_sha256": digest, "articles": articles, "documents": documents,
         "article_chars": article_chars}, indent=2) + "\n")
    # CC BY-SA 4.0 asks for credit, the licence and a note of the changes made; each passage also
    # links to the revision it came from.
    (artifacts.pack.parent / "ATTRIBUTION.txt").write_text(
        "Encyclopedia in depth (English Wikipedia): adapted Wikipedia text by Wikipedia contributors.\n"
        "License: CC BY-SA 4.0 https://creativecommons.org/licenses/by-sa/4.0/\n"
        "Changes: plain text from the MediaWiki TextExtracts API (tables, images and references are not part of it); "
        "reference, link and bibliography sections left out; later sections left out past a text budget per article; "
        "formulas written as their LaTeX source; an 'Also known as' line of redirect titles added to the lead; "
        "text split into passages.\n"
        "Each article's revision link below gives its history and contributors. No endorsement implied.\n\n" +
        "\n".join(revisions) + "\n", encoding="utf-8")
    print(f"{articles} articles, {documents} section passages -> {artifacts.pack}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    f = commands.add_parser("fetch", help="fetch the listed articles into a spool (network)")
    f.add_argument("--titles", type=Path, default=DEFAULT_TITLES)
    f.add_argument("--spool", type=Path, required=True)
    f.add_argument("--workers", type=int, default=4)
    f.add_argument("--rate", type=float, default=REQUESTS_PER_SECOND, help="requests a second across all workers")
    f.add_argument("--limit", type=int, default=0, help="fetch at most this many new titles (a smoke test)")
    b = commands.add_parser("build", help="build the pack from a spool (offline, deterministic)")
    b.add_argument("--spool", type=Path, required=True)
    b.add_argument("--out", type=Path, required=True)
    b.add_argument("--version", required=True, help="snapshot version, e.g. 2026.10.06")
    b.add_argument("--article-chars", type=int, default=ARTICLE_CHARS)
    args = parser.parse_args()
    if args.command == "fetch":
        if not 0 < args.rate <= REQUESTS_PER_SECOND:
            parser.error(f"--rate must be above 0 and at most {REQUESTS_PER_SECOND} (Wikimedia's limit)")
        return fetch(args.titles, args.spool, args.workers, args.limit, args.rate)
    return build(args.spool, args.out, args.version, args.article_chars)


if __name__ == "__main__":
    raise SystemExit(main())
