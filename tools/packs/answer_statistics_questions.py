#!/usr/bin/env python3
"""Answer the 30 statistics questions using ONLY the built pack's search.

Two search paths, the same two the Field Atlas app uses on a pack:
  1. FTS5 MATCH on the leviathan release's chunks_fts (porter tokenizer).
  2. The leviathan 0.2.0 CLI over leviathan.sqlite (the app's native search).

For each question this prints the top FTS hits (chunk, section, source URL)
and the top leviathan hit. The answers in statistics-answers.jsonl are then
written by hand from those hits only, each citing the section that carries
the answer. Nothing is answered from memory: if the pack does not return the
answer, the question is marked unanswered.

Usage:
  python3 answer_statistics_questions.py > statistics/qa-hits.txt
"""
from __future__ import annotations

import json
import re
import sqlite3
import subprocess
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
LEV_PACK = HERE / "leviathan" / "openstax-maths-statistics-0.1.0-leviathan.fapack"
LEV_INDEX = HERE / "leviathan" / "leviathan.sqlite"
LEVIATHAN = HERE / "leviathan" / "leviathan-v0.2.0-x86_64-unknown-linux-gnu" / "leviathan"

# question number -> FTS phrases (double-quoted phrases; a bare hyphen breaks FTS5 syntax)
QUERIES = {
    1: ['"population"', '"sample"'],
    2: ['"nominal"', '"ordinal"'],
    3: ['"explanatory variable"', '"response variable"'],
    4: ['"median"'],
    5: ['"box plot"', '"five-number summary"'],
    6: ['"skewed"', '"mean" "median"'],
    7: ['"independent events"', '"multiplication rule"'],
    8: ['"addition rule"'],
    9: ['"contingency table"'],
    10: ['"probability distribution function"', '"sum"'],
    11: ['"binomial distribution"', '"expected value"'],
    12: ['"Poisson distribution"', '"mean"'],
    13: ['"uniform distribution"', '"probability density function"'],
    14: ['"exponential distribution"', '"decay parameter"'],
    15: ['"z-score"'],
    16: ['"standard normal"', '"68"'],
    17: ['"90th percentile"'],
    18: ['"Central Limit Theorem"'],
    19: ['"standard error"', '"mean"'],
    20: ['"error bound"', '"population mean"'],
    21: ['"95%"', '"1.96"'],
    22: ['"confidence interval"', '"sample size"'],
    23: ['"Type I error"'],
    24: ['"null hypothesis"'],
    25: ['"significance level"', '"p-value"'],
    26: ['"two independent"', '"population means"'],
    27: ['"matched pairs"'],
    28: ['"goodness-of-fit"'],
    29: ['"degrees of freedom"', '"test of independence"'],
    30: ['"least-squares"', '"correlation coefficient"'],
}


def load_db(pack):
    with zipfile.ZipFile(pack) as archive:
        data = archive.read("content.sqlite")
    tmp = Path("/tmp") / f"qa-{pack.name}.sqlite"
    tmp.write_bytes(data)
    return sqlite3.connect(tmp)


def fts_search(db, phrases, limit=3):
    match = " OR ".join(phrases)
    rows = db.execute(
        "SELECT chunk_id, document_id, title, source, snippet(chunks_fts, 4, '[', ']', '...', 40) "
        "FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts) LIMIT ?",
        (match, limit)).fetchall()
    return rows


def leviathan_search(words):
    try:
        out = subprocess.run([str(LEVIATHAN), "search", "--index", str(LEV_INDEX), *words],
                             capture_output=True, text=True, timeout=60)
        return (out.stdout or out.stderr).strip().splitlines()[:6]
    except Exception as exc:  # noqa: BLE001
        return [f"leviathan search failed: {exc}"]


def main():
    questions = [line.strip() for line in (HERE / "statistics" / "questions.txt").read_text().splitlines()
                 if re.match(r"^\d+\. ", line)]
    db = load_db(LEV_PACK)
    for line in questions:
        number = int(line.split(".")[0])
        print(f"\n=== Q{number}: {line[len(str(number)) + 2:]}")
        for chunk_id, doc, title, source, snippet in fts_search(db, QUERIES[number]):
            print(f"  FTS  [{doc}] {title}\n       {source}\n       {snippet}")
        words = re.findall(r"[A-Za-z][A-Za-z-]{2,}", line)
        print("  LEV  " + " | ".join(leviathan_search(words[:4])))


if __name__ == "__main__":
    main()
