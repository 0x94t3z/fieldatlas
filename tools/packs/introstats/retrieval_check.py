#!/usr/bin/env python3
"""Reproduce the retrieval check for the Introductory Statistics pack.

Unpacks the built .fapack, queries its FTS5 index with the key terms of the
ten questions in introstats-questions.txt (the same MATCH strings recorded
in introstats-retrieval.json), and prints the top documents for each. The
expected teaching document is listed first; the run was made before the
answers were written.

Usage:
  python3 tools/packs/introstats/retrieval_check.py [PACK]
"""
from __future__ import annotations

import argparse
import json
import os
import sqlite3
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
DEFAULT_PACK = ROOT / "build" / "introstats-pack" / "openstax-maths-introstats-0.1.0.fapack"

# question, MATCH string, expected document id
QUERIES = [
    ("What is the difference between a population and a sample?",
     "population OR sample OR statistic OR parameter", "openstax-introstats-m46909"),
    ("How do you find the median of a data set?",
     "median OR \"middle value\" OR \"measures of the center\"", "openstax-introstats-m46928"),
    ("What is the mean of a binomial distribution?",
     "binomial AND mean AND np", "openstax-introstats-m46950"),
    ("How do you find a 95% confidence interval for a population mean when the population standard deviation is known?",
     "confidence AND interval AND \"population mean\" AND sigma", "openstax-introstats-m47002"),
    ("When do you reject the null hypothesis?",
     "\"null hypothesis\" AND reject AND (\"p-value\" OR rare OR decision)", "openstax-introstats-m47044"),
    ("What is the central limit theorem for sample means?",
     "\"central limit theorem\" AND \"sample means\"", "openstax-introstats-m46994"),
    ("How do you compute the test statistic for a chi-square goodness-of-fit test?",
     "\"chi-square\" AND \"goodness-of-fit\" AND \"test statistic\"", "openstax-introstats-m47064"),
    ("What is the regression equation?",
     "regression AND \"least squares\"", "openstax-introstats-m47117"),
    ("What is an F distribution used for?",
     "\"F distribution\" AND (variance OR ANOVA OR \"F-ratio\")", "openstax-introstats-m47128"),
    ("What is the difference between a Type I and a Type II error?",
     "\"type i\" AND \"type ii\" AND error", "openstax-introstats-m47029"),
]


def run(pack: Path):
    with zipfile.ZipFile(pack) as archive:
        database = tempfile.NamedTemporaryFile(suffix=".sqlite", delete=False)
        database.write(archive.read("content.sqlite"))
        database.close()
    try:
        connection = sqlite3.connect(database.name)
        try:
            hits = 0
            results = []
            for question, match, expected in QUERIES:
                rows = connection.execute(
                    "SELECT document_id, title, source, bm25(chunks_fts) FROM chunks_fts "
                    "WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts) LIMIT 5", (match,)).fetchall()
                documents = []
                for document_id, title, source, _ in rows:
                    if document_id not in documents:
                        documents.append(document_id)
                rank = documents.index(expected) + 1 if expected in documents else None
                hits += rank == 1
                in_top3 = rank is not None and rank <= 3
                results.append({"question": question, "match": match, "expected": expected,
                                "rank": rank, "in_top_three": in_top3,
                                "top_documents": documents})
                print(f"{'#1' if rank == 1 else f'#{rank}' if rank else 'miss':>4}  {question}")
            print(f"\nTop-1: {hits}/{len(QUERIES)}   top-3: "
                  f"{sum(r['in_top_three'] for r in results)}/{len(QUERIES)}")
            return results
        finally:
            connection.close()
    finally:
        os.unlink(database.name)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("pack", nargs="?", type=Path, default=DEFAULT_PACK)
    args = parser.parse_args()
    run(args.pack)


if __name__ == "__main__":
    main()
