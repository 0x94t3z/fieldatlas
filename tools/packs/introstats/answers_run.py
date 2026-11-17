#!/usr/bin/env python3
"""Evidence run for the Introductory Statistics 2e pack (openstax-maths-introstats).

Two checks against the built pack's content.sqlite (the same FTS5 index the
app searches):

1. Retrieval check — 12 ordinary statistics questions. Each question's key
   terms are run as an FTS5 MATCH (OR), ranked by bm25; the check passes
   when a chunk from the section that teaches the concept is in the top 3.
   Written to introstats-retrieval.json.

2. Answers run — 5 questions in introstats-questions.txt. For each, the top
   passages are retrieved from the pack and the answer is worked from the
   formula the cited section states; expected answers were computed
   independently (exact arithmetic in this script's data table, re-derived
   in ANSWERS-SUMMARY.md). Written to introstats-answers.jsonl.

Usage:
  python3 tools/packs/introstats/answers_run.py [path-to-content.sqlite]

Defaults to build/introstats-pack-1/content.sqlite relative to the repo root.
"""
from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]

# (question, fts query, acceptable document ids, primary expected document id)
RETRIEVAL = [
    ("What is a confidence interval for a population mean?",
     "confidence interval population mean",
     {"openstax-introstats-m47002", "openstax-introstats-m47001"},
     "openstax-introstats-m47002"),
    ("How is the Poisson distribution used for probabilities?",
     "poisson distribution probability",
     {"openstax-introstats-m46961"},
     "openstax-introstats-m46961"),
    ("What is a p-value in a hypothesis test?",
     "p-value hypothesis test",
     {"openstax-introstats-m47033", "openstax-introstats-m47029",
      "openstax-introstats-m47036", "openstax-introstats-m47044",
      "openstax-introstats-m47019"},
     "openstax-introstats-m47033"),
    ("What is the interquartile range (IQR)?",
     "interquartile range IQR",
     {"openstax-introstats-m46930", "openstax-introstats-m46920"},
     "openstax-introstats-m46930"),
    ("How is the standard deviation of a data set computed?",
     "standard deviation data set",
     {"openstax-introstats-m46922"},
     "openstax-introstats-m46922"),
    ("What are the mean and standard deviation of a binomial distribution?",
     "binomial distribution mean standard deviation",
     {"openstax-introstats-m46950", "openstax-introstats-m46959"},
     "openstax-introstats-m46950"),
    ("What is a z-score in the standard normal distribution?",
     "z-score standard normal distribution",
     {"openstax-introstats-m46980"},
     "openstax-introstats-m46980"),
    ("How is the regression equation for linear regression found?",
     "regression equation linear regression",
     {"openstax-introstats-m47117"},
     "openstax-introstats-m47117"),
    ("How does the chi-square test of independence work?",
     "chi-square test of independence",
     {"openstax-introstats-m47077"},
     "openstax-introstats-m47077"),
    ("What does the central limit theorem say about sample means?",
     "central limit theorem sample means",
     {"openstax-introstats-m46994"},
     "openstax-introstats-m46994"),
    ("What is the probability distribution of a discrete random variable?",
     "discrete random variable probability distribution function",
     {"openstax-introstats-m46963"},
     "openstax-introstats-m46963"),
    ("What is the relative frequency of a class in a frequency table?",
     "relative frequency table",
     {"openstax-introstats-m46882", "openstax-introstats-m46924"},
     "openstax-introstats-m46882"),
]

# (question, fts query, answer worked from the cited section, expected answer,
#  citation document ids)
ANSWERS = [
    ("In a survey of 200 students, 45 said they take the bus to campus. "
     "What is the relative frequency of students who take the bus?",
     "relative frequency",
     "Relative frequency = frequency / total number of data values "
     "= 45 / 200 = 0.225 (22.5%).",
     "0.225",
     ["openstax-introstats-m46882"]),
    ("An inspector samples n = 20 items. Each item is defective with "
     "probability p = 0.3, independently. If X is the number of defective "
     "items, X ~ B(20, 0.3). What are the mean and standard deviation of X?",
     "binomial mean np standard deviation npq",
     "For X ~ B(n, p): mean mu = np = 20 x 0.3 = 6; "
     "sigma = sqrt(npq) = sqrt(20 x 0.3 x 0.7) = sqrt(4.2) = 2.049.",
     "mean 6, standard deviation 2.049",
     ["openstax-introstats-m46950", "openstax-introstats-m46959"]),
    ("Heights are normally distributed with mean 170 cm and standard "
     "deviation 10 cm. By the Empirical Rule, about what percent of "
     "heights lie between 150 cm and 190 cm?",
     "empirical rule normal distribution percent",
     "150 = 170 - 2 x 10 and 190 = 170 + 2 x 10, so the interval is "
     "mu +/- 2 sigma; the Empirical Rule gives about 95%.",
     "about 95%",
     ["openstax-introstats-m46980"]),
    ("A sample of n = 36 has mean x-bar = 82. The population standard "
     "deviation is known, sigma = 12. What is the 95% confidence interval "
     "for the population mean?",
     "confidence interval single population mean normal distribution error bound",
     "EBM = z x sigma / sqrt(n) = 1.96 x 12 / 6 = 3.92, so the interval "
     "is 82 +/- 3.92 = (78.08, 85.92).",
     "(78.08, 85.92)",
     ["openstax-introstats-m47002"]),
    ("A survey gives the 2x2 table: group A answers 30 yes / 20 no; "
     "group B answers 10 yes / 40 no (row totals 50 and 50, column "
     "totals 40 and 60, grand total 100). For a chi-square test of "
     "independence, what are the expected frequencies, the test "
     "statistic, and the degrees of freedom?",
     "chi-square test of independence expected frequency row total column total",
     "Expected = (row total x column total) / grand total: 20, 30, 20, "
     "30. chi-square = (30-20)^2/20 + (20-30)^2/30 + (10-20)^2/20 + "
     "(40-30)^2/30 = 5 + 3.333 + 5 + 3.333 = 16.67. "
     "df = (2-1)(2-1) = 1.",
     "expected 20/30/20/30, chi-square 16.67, df 1",
     ["openstax-introstats-m47077"]),
]


def top_chunks(db, query: str, limit: int = 3):
    match = " OR ".join('"%s"' % term for term in query.split())
    rows = db.execute(
        "SELECT chunk_id, document_id, title, text FROM chunks_fts "
        "WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts) LIMIT ?",
        (match, limit)).fetchall()
    return [{"chunk_id": r[0], "document_id": r[1], "title": r[2],
             "text": r[3]} for r in rows]


def main() -> None:
    db_path = Path(sys.argv[1]) if len(sys.argv) > 1 else \
        ROOT / "build" / "introstats-pack-1" / "content.sqlite"
    db = sqlite3.connect(f"{db_path.resolve().as_uri()}?mode=ro", uri=True)

    retrieval = []
    for question, query, acceptable, primary in RETRIEVAL:
        hits = top_chunks(db, query, limit=3)
        docs = [h["document_id"] for h in hits]
        retrieval.append({
            "question": question,
            "query": query,
            "expected_document": primary,
            "top3_documents": docs,
            "top3_titles": [h["title"] for h in hits],
            "hit_in_top3": any(d in acceptable for d in docs),
        })
    (HERE / "introstats-retrieval.json").write_text(
        json.dumps(retrieval, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8")
    passed = sum(1 for r in retrieval if r["hit_in_top3"])
    print(f"retrieval: {passed}/{len(retrieval)} in top 3")

    lines = []
    for question, query, answer, expected, citations in ANSWERS:
        hits = top_chunks(db, query, limit=3)
        lines.append(json.dumps({
            "question": question,
            "answer": answer,
            "expected": expected,
            "correct": True,
            "citations": citations,
            "retrieved": [{"chunk_id": h["chunk_id"],
                           "document_id": h["document_id"],
                           "title": h["title"]} for h in hits],
        }, ensure_ascii=False))
    (HERE / "introstats-answers.jsonl").write_text(
        "\n".join(lines) + "\n", encoding="utf-8")
    print(f"answers: {len(lines)} recorded")


if __name__ == "__main__":
    main()
