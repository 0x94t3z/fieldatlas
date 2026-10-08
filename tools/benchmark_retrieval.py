#!/usr/bin/env python3
"""Offline desktop component comparison: app SQL, compact candidate cards, upstream Leviathan.

Never runs inference or changes production indexes. A desktop subset is not an
Android/large-library/answer-quality result. Cards remain selection aids; full
passages are kept in the baseline database under the original chunk identifiers.
"""
from __future__ import annotations

import argparse
from contextlib import closing
import hashlib
import json
from pathlib import Path
import platform
import re
import sqlite3
import statistics
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from packtool.build_pack import _digest


def score(ids: list[str], gold: list[str]) -> dict:
    if not gold:
        return dict(expectedMissing=True, noResults=not ids)
    return dict(hitAt1=bool(ids and ids[0] in gold), hitAt5=bool(set(ids[:5]) & set(gold)))


def compact_cards(rows: list[tuple]) -> list[dict]:
    # This is intentionally not supporting evidence: previews may omit qualifiers.
    return [dict(chunkId=row[1], documentId=row[0], title=row[2], source=row[3],
                 preview=row[4][:300], requiresFullPassage=True) for row in rows]


def run(database: Path, cases_path: Path, work: Path, leviathan: Path,
        records: int = 5000, repetitions: int = 5) -> dict:
    if work.exists():
        raise ValueError("Refusing to overwrite an existing experiment")
    if not 1 <= records <= 1_000_000 or not 1 <= repetitions <= 30:
        raise ValueError("Invalid sample size or repetition count")
    cases_bytes = cases_path.read_bytes()
    cases = json.loads(cases_bytes)
    if not isinstance(cases, list) or not cases or len(cases) > 200:
        raise ValueError("Expected 1–200 fixed query cases")
    for case in cases:
        if not isinstance(case.get("id"), str) or not case["id"] or not isinstance(case.get("query"), str) or not case["query"].strip():
            raise ValueError("Every case needs an id and a nonempty query")
        if not isinstance(case.get("goldChunkIds"), list) or not all(isinstance(i, str) for i in case["goldChunkIds"]):
            raise ValueError("Gold supporting chunk identifiers (or an explicit missing label) are required")
    if len({c["id"] for c in cases}) != len(cases):
        raise ValueError("Duplicate query identifiers")
    # Use the actual app SQL, so a changed retriever cannot inherit stale baseline results.
    code = Path(__file__).resolve().parents[1] / "app/src/main/java/xyz/fieldatlas/research/KnowledgeDatabase.kt"
    source = code.read_text(encoding="utf-8")
    match = re.search(r'private const val SEARCH_SQL = """(.*?)"""', source, re.S)
    if match is None:
        raise ValueError("App search SQL was not found")
    sql = match.group(1)
    source_bytes, source_hash = _digest(database)
    version = subprocess.run([str(leviathan.resolve()), "--version"], check=True, capture_output=True, text=True, timeout=10).stdout.strip()
    work.mkdir(parents=True)
    baseline = work / "baseline.sqlite"
    with closing(sqlite3.connect(database.resolve().as_uri() + "?mode=ro", uri=True)) as original, closing(sqlite3.connect(baseline)) as subset:
        subset.execute("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED, document_id UNINDEXED, title, source UNINDEXED, text, tokenize='porter unicode61')")
        cursor = original.execute("SELECT chunk_id, document_id, title, source, text FROM chunks_fts ORDER BY rowid LIMIT ?", (records,))
        subset.executemany("INSERT INTO chunks_fts VALUES (?, ?, ?, ?, ?)", cursor)
        subset.commit()
        actual_records = subset.execute("SELECT count(*) FROM chunks_fts").fetchone()[0]
    if not actual_records:
        raise ValueError("Source database is empty")
    with closing(sqlite3.connect(baseline.resolve().as_uri() + "?mode=ro", uri=True)) as subset:
        for case in cases:
            for identifier in case["goldChunkIds"]:
                if subset.execute("SELECT 1 FROM chunks_fts WHERE chunk_id=? LIMIT 1", (identifier,)).fetchone() is None:
                    raise ValueError("Gold passage is outside the frozen subset; enlarge the subset before scoring")
    upstream = work / "leviathan.sqlite"
    command = [str(leviathan.resolve()), "index", str(baseline.resolve()), "--index", str(upstream.resolve()),
               "--format", "sqlite", "--sql", "SELECT chunk_id AS id, document_id, title, source, text FROM chunks_fts",
               "--id", "id", "--title", "title", "--text", "text", "--display", "source", "--group", "document_id", "--strict", "--quiet"]
    index_start = time.perf_counter()
    subprocess.run(command, cwd=work, check=True, capture_output=True, text=True, timeout=1800)
    index_seconds = time.perf_counter() - index_start
    results = []
    with closing(sqlite3.connect(baseline.resolve().as_uri() + "?mode=ro", uri=True)) as subset:
        for case in cases:
            terms = re.findall(r"[^\W_]+", case["query"], flags=re.UNICODE)
            if not terms:
                raise ValueError("Query contains no searchable words")
            expression = " OR ".join('"' + term + '"' for term in terms)
            baseline_times, upstream_times = [], []
            for _ in range(repetitions):
                start = time.perf_counter()
                rows = subset.execute(sql, (expression, 5)).fetchall()
                baseline_times.append((time.perf_counter() - start) * 1000)
                start = time.perf_counter()
                response = subprocess.run([str(leviathan.resolve()), "search", case["query"], "--index", str(upstream.resolve()),
                    "--json", "--limit", "5", "--no-fallback"], cwd=work, check=True, capture_output=True, text=True, timeout=60)
                upstream_times.append((time.perf_counter() - start) * 1000)
                outcome = json.loads(response.stdout)
            returned = outcome.get("results", [])
            upstream_ids = [item["id"] for item in returned]
            # Reject a claimed source reference that cannot resolve to its original full passage.
            for identifier in upstream_ids:
                if subset.execute("SELECT 1 FROM chunks_fts WHERE chunk_id=? LIMIT 1", (identifier,)).fetchone() is None:
                    raise ValueError("Upstream returned an unknown citation identifier")
            baseline_ids = [row[1] for row in rows]
            cards = compact_cards(rows)
            results.append(dict(id=case["id"], query=case["query"],
                baseline=dict(chunkIds=baseline_ids, score=score(baseline_ids, case["goldChunkIds"]),
                    timingsMs=baseline_times, medianMs=statistics.median(baseline_times),
                    evidenceChars=sum(len(row[4]) for row in rows)),
                compact=dict(chunkIds=[c["chunkId"] for c in cards], selectionOnly=True,
                    serializedChars=len(json.dumps(cards, ensure_ascii=False)), fullPassageExpansionRequired=True),
                leviathan=dict(chunkIds=upstream_ids, score=score(upstream_ids, case["goldChunkIds"]),
                    timingsMs=upstream_times, medianMs=statistics.median(upstream_times),
                    serializedChars=len(response.stdout), result=outcome)))
    if _digest(database) != (source_bytes, source_hash):
        raise ValueError("Source database changed during the experiment")
    report = dict(scope="desktop_component_subset_not_android_or_answer_quality", productionAdopted=False,
        limitations=["First-row subset, not representative/held-out", "Developer gold passages are non-exhaustive; not an answer-quality score",
                     "SQLite Python versus upstream CLI includes process-start overhead",
                     "Query parsing and title weights differ", "No Qwen token counts, cold cache, peak RAM or generation timings measured"],
        source=dict(bytes=source_bytes, sha256=source_hash), records=actual_records,
        casesSha256=hashlib.sha256(cases_bytes).hexdigest(), appSqlSha256=hashlib.sha256(sql.encode()).hexdigest(),
        versions=dict(leviathan=version, sqlite=sqlite3.sqlite_version, platform=platform.platform()),
        indexes=dict(baselineBytes=baseline.stat().st_size, leviathanBytes=upstream.stat().st_size,
                     leviathanBuildSeconds=index_seconds), results=results)
    (work / "comparison.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--leviathan", type=Path, required=True)
    parser.add_argument("--records", type=int, default=5000)
    parser.add_argument("--repetitions", type=int, default=5)
    args = parser.parse_args()
    report = run(args.database, args.cases, args.work_dir, args.leviathan, args.records, args.repetitions)
    print(json.dumps({k: v for k, v in report.items() if k != "results"}, indent=2))


if __name__ == "__main__":
    main()
