#!/usr/bin/env python3
"""Read-only provenance and citation review of real desktop research reports.

Mechanical integrity is not semantic support or answer correctness. Reports and
review output stay private in build/. No app, source database or answer is edited.
"""
import argparse
from contextlib import ExitStack, closing
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_answer_review import blocks, packed_passages

ROOT = Path(__file__).resolve().parents[1]
CITATION = re.compile(r"\[(?:S)?(\d+)\]", re.IGNORECASE)


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def normalized(text):
    return re.sub(r"\s+", " ", text).strip()


def excerpt_from_original(excerpt, original):
    # Same metadata exclusion as EvidenceRelevance.passageText. Boundary ellipses
    # mark omitted context, not a licence to reorder or paraphrase the source.
    body = "\n".join(line for line in original.splitlines()
                     if not line.lstrip().lower().startswith("mesh:"))
    excerpt = normalized(excerpt).removeprefix("… ").removesuffix(" …")
    return bool(excerpt) and excerpt in normalized(body)


def compact_metadata(value):
    encoded = value.encode("utf-16-le")
    return value if len(encoded) <= 192 else encoded[:190].decode("utf-16-le", errors="ignore") + "…"


def verify_prompt_metadata(run):
    if not run["sources"]:
        return
    prompt = run.get("packedPrompt") or run["calls"][-1]["request"]["messages"][-1]["content"]
    if run.get("packedPrompt") and run.get("calls"):
        actual = run["calls"][-1]["request"]["messages"][-1]["content"]
        if "\n\nEVIDENCE:\n" in actual and prompt != actual:
            raise ValueError("Recorded packing differs from the actual inference request")
    body = prompt.split("\n\nEVIDENCE:\n", 1)[1].split("\n\nQUESTION:\n", 1)[0]
    sections = re.split(r"\n\n(?=\[S\d+\]\n)", body)
    for i, (section, source) in enumerate(zip(sections, run["sources"]), 1):
        prefix = (f"[S{i}]\nTitle: {compact_metadata(source['title'])}\n"
                  f"Source: {compact_metadata(source['source'])}\nExcerpt: ")
        if not section.startswith(prefix):
            raise ValueError("Prompt title/source metadata differs from source-viewer mapping")


def original_rows(db, source, chunk_id):
    query = "SELECT document_id, chunk_id, title, source, text FROM chunks_fts WHERE chunk_id = ?"
    schema = db.execute("SELECT sql FROM sqlite_master WHERE name='chunks_fts'").fetchone()
    # chunk_id is UNINDEXED in real packs. Narrow via the indexed exact title
    # phrase, then still compare every original field; never accept title overlap
    # as source identity. Plain diagnostic tables / non-token titles keep the
    # existing read-only lookup. A stale FTS index can fail closed, not fabricate a match.
    if schema and re.search(r"USING\s+fts5\s*\(", schema[0] or "", re.IGNORECASE) and any(c.isalnum() for c in source["title"]):
        match = '{title} : "' + source["title"].replace('"', '""') + '"'
        return db.execute(query + " AND chunks_fts MATCH ?", (chunk_id, match)).fetchall()
    return db.execute(query, (chunk_id,)).fetchall()


def original_mapping(db, source):
    fields = ("documentId", "chunkId", "title", "source", "text")
    expected = tuple(source[k] for k in fields)
    found = original_rows(db, source, source["chunkId"])
    if expected in found:
        return {"chunkIds": [source["chunkId"]], "transformation": "none"}
    # ArticleDepth joins consecutive chunks and removes only the article alias
    # line. Reconstruct that precise operation; never accept arbitrary substrings.
    match = re.fullmatch(r"(.+):(\d{4})", source["chunkId"])
    if not match or match.group(1) != source["documentId"]:
        return None
    rows = [row for row in found if row[:4] == expected[:4]]
    if len(rows) != 1:
        return None
    def without_alias(text):
        return "\n".join(line for line in text.splitlines() if not line.startswith("Also known as:")).strip()
    joined = rows[0][4]
    ids = [source["chunkId"]]
    clean = without_alias(joined)
    if clean == source["text"]:
        return {"chunkIds": ids, "transformation": "article-alias-line-removed"}
    # Only lead chunks can be cut by ArticleDepth.truncated, at a sentence end.
    cut = source["text"]
    if source["documentId"].endswith("-0000") and int(match.group(2)) == 0 and len(cut) >= 240 and clean.startswith(cut) and re.search(r"[.!?]$", cut) and len(clean) > len(cut) and clean[len(cut)].isspace():
        return {"chunkIds": ids, "transformation": "article-lead-sentence-cut"}
    number = int(match.group(2))
    while len(without_alias(joined)) < len(source["text"]):
        number += 1
        next_id = f"{match.group(1)}:{number:04d}"
        next_rows = original_rows(db, source, next_id)
        next_rows = [row for row in next_rows if (row[0], row[2], row[3]) == (expected[0], expected[2], expected[3])]
        if len(next_rows) != 1:
            return None
        text = next_rows[0][4]
        if not text:
            return None
        overlap = text[:150]
        joined += text[150:] if joined.endswith(overlap) else " " + text
        ids.append(next_id)
        if without_alias(joined) == source["text"]:
            return {"chunkIds": ids, "transformation": "article-depth-consecutive-chunks"}
    return None


def audit(report, databases):
    if not report.get("runs"):
        raise ValueError("Report has no completed runs")
    if any(run.get("status") != "COMPLETED_UNSCORED" for run in report["runs"]):
        raise ValueError("Incomplete or failed run cannot pass a provenance audit")
    rows = []
    with ExitStack() as stack:
        connections = [stack.enter_context(closing(sqlite3.connect(
            path.resolve().as_uri() + "?mode=ro", uri=True))) for path in databases]
        for run in report["runs"]:
            originals = []
            for source in run["sources"]:
                matches = []
                for path, db in zip(databases, connections):
                    mapping = original_mapping(db, source)
                    if mapping:
                        matches.append({"database": str(path), **mapping})
                if not matches:
                    raise ValueError("Source differs from every pinned original: " + source["chunkId"])
                originals.append({"chunkId": source["chunkId"], "originalMappings": matches})
            passages = packed_passages(run)
            if len(passages) != len(run["sources"]):
                raise ValueError("Packed evidence count does not match source-viewer identities")
            verify_prompt_metadata(run)
            for passage, source in zip(passages, run["sources"]):
                if not excerpt_from_original(passage, source["text"]):
                    raise ValueError("Packed excerpt is not an unchanged original passage")
            answer = run["visibleAnswer"]
            references = [int(m.group(1)) for m in CITATION.finditer(answer)]
            if any(i < 1 or i > len(passages) for i in references):
                raise ValueError("Answer contains an unmapped citation")
            review = []
            for block in blocks(answer):
                ids = sorted({int(m.group(1)) for m in CITATION.finditer(block["text"])})
                review.append({**block, "citationIds": ids,
                    "citedPassages": [{"citationId": i, "chunkId": run["sources"][i - 1]["chunkId"],
                                       "text": passages[i - 1]} for i in ids],
                    "supportReview": "unreviewed", "questionCoverageReview": "unreviewed"})
            rows.append({"question": run["question"], "visibleAnswer": answer,
                         "originalSources": originals, "packedPassages": passages,
                         "citationCount": len(references), "blocks": review})
    return {"scope": "Mechanical source identity and citation mapping only; agent review is not independent validation",
            "sourceIntegrityPassed": True, "automaticAnswerQualityPass": False,
            "releaseReady": False, "runs": rows}


def pinned_databases(report):
    pins = [p for p in report["identities"] if Path(p["path"]).suffix == ".sqlite"]
    if not pins:
        raise ValueError("No pinned database identities in report")
    paths = []
    for pin in pins:
        path = Path(pin["path"]).resolve(strict=True)
        if not path.is_relative_to(ROOT / "build"):
            raise ValueError("Expected private build/ database")
        if path.stat().st_size != pin["bytes"] or digest(path) != pin["sha256"]:
            raise ValueError("Source database changed since generation")
        paths.append(path)
    return paths


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / "build") or output.exists():
        parser.error("Output must be a new private file under build/")
    raw = args.report.read_bytes()
    report = json.loads(raw)
    result = audit(report, pinned_databases(report))
    result["reportSha256"] = hashlib.sha256(raw).hexdigest()
    if args.report.read_bytes() != raw:
        raise ValueError("Report changed during audit")
    # Recheck immutable sources after querying them, not only before.
    pinned_databases(report)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x") as stream:
        json.dump(result, stream, indent=2, ensure_ascii=False)
        stream.write("\n")
    print(output)


if __name__ == "__main__":
    main()
