#!/usr/bin/env python3
"""Answers run for the Introductory Statistics collection (POIDH Base #1554).

Method (same order as the Prealgebra answers run):
  1. Solve and answer from memory first. The expected answers in
     introstats-answers.jsonl were written before any retrieval query ran.
  2. Retrieve. The built pack openstax-maths-introstats-0.1.0.fapack is
     unpacked and its content.sqlite queried directly. The pack's only
     index is the FTS5 virtual table chunks_fts (porter tokenizer), so a
     query is an FTS5 MATCH over the chunk text; the top hits are read and
     the answer is checked against the retrieved passage.
  3. Record. Per question: the expected answer, the answer given with the
     collection, the passages' document ids, and a correct flag.

Usage:
  python3 tools/packs/introstats/answers_run.py [--pack PATH]
Prints the per-question retrieval hits and writes introstats-answers.jsonl
beside this script (the expected answers are embedded below, written
before retrieval; edit them only by re-solving, never to match retrieval).
"""
from __future__ import annotations

import argparse
import json
import sqlite3
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = Path(__file__).resolve().parents[3]

QUESTIONS = [
    {
        "id": "q1",
        "question": "What is the formula for relative frequency?",
        "expected": "Relative frequency = frequency / total number of data values, "
                    "RF = f/n, where f is the frequency of a data value and n is "
                    "the total number of data values.",
        "query": '"relative frequency" f n',
    },
    {
        "id": "q2",
        "question": "A binomial distribution has n = 20 trials and probability of "
                    "success p = 0.25. What are its mean and standard deviation?",
        "expected": "Mean mu = n*p = 20*0.25 = 5. Standard deviation "
                    "sigma = sqrt(n*p*q) = sqrt(20*0.25*0.75) = sqrt(3.75) = 1.94 "
                    "(to two decimal places).",
        "query": "binomial mean standard deviation",
    },
    {
        "id": "q3",
        "question": "State the Empirical Rule (the 68-95-99.7 rule) for a normal distribution.",
        "expected": "About 68% of the values lie within one standard deviation of "
                    "the mean, about 95% within two standard deviations, and about "
                    "99.7% within three standard deviations.",
        "query": "empirical rule",
    },
    {
        "id": "q4",
        "question": "What is the formula for a confidence interval for a single "
                    "population mean when the population standard deviation is known?",
        "expected": "x-bar +/- z_(alpha/2) * (sigma / sqrt(n)): the sample mean "
                    "plus or minus the z critical value times the standard error "
                    "sigma/sqrt(n).",
        "query": '"confidence interval" population mean normal distribution',
    },
    {
        "id": "q5",
        "question": "What is the test statistic for a chi-square test of independence, "
                    "and what is the null hypothesis?",
        "expected": "Test statistic chi-square = sum over all cells of "
                    "(Observed - Expected)^2 / Expected. Null hypothesis H0: the "
                    "two variables are independent (there is no association "
                    "between them).",
        "query": "chi square test independence",
    },
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", type=Path,
                    default=ROOT / "build" / "introstats-pack" / "openstax-maths-introstats-0.1.0.fapack")
    args = ap.parse_args()
    with tempfile.TemporaryDirectory() as tmp:
        with zipfile.ZipFile(args.pack) as z:
            z.extract("content.sqlite", tmp)
        con = sqlite3.connect(f"file:{tmp}/content.sqlite?mode=ro", uri=True)
        out = []
        for q in QUESTIONS:
            rows = con.execute(
                "SELECT document_id, title, snippet(chunks_fts, 4, '>>', '<<', ' ... ', 24) "
                "FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY rank LIMIT 5",
                (q["query"],)).fetchall()
            print(f"== {q['id']}: {q['question']}")
            for doc_id, title, snippet in rows:
                print(f"   [{doc_id}] {title}\n     {snippet}")
            out.append({**q, "hits": [{"document_id": d, "title": t, "snippet": s}
                                      for d, t, s in rows]})
        (HERE / "introstats-retrieval.json").write_text(
            json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("wrote", HERE / "introstats-retrieval.json")


if __name__ == "__main__":
    main()
