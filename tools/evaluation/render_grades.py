#!/usr/bin/env python3
"""Render docs/fieldtest/results-1.4.2/heldoutNN-grades.jsonl as one readable page, README.md in the same folder.

Each set gets a summary table (good answers, wrong details, unsafe) and, folded away, every question with each
setup's two scores and what the graders found wrong. The setup names in the files are the test harness's; they
are shown here in words a reader without the code can follow.
"""
from __future__ import annotations

import json
import statistics
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FOLDER = ROOT / "docs" / "fieldtest" / "results-1.4.2"

# What each set tested, in order of reading.
SETS = [
    ("44", "Emergencies: Gemma writing from the guide, first try", "36 first-aid situations"),
    ("45", "Emergencies: the same change on fresh questions", "40 first-aid situations"),
    ("47", "Emergencies: what the small models should do with no guide", "40 first-aid situations"),
    ("48", "Emergencies: Gemma with the answer checks", "24 first-aid situations"),
    ("50", "Emergencies: before and after the 1.4.2 matching fixes", "40 first-aid situations"),
    ("52", "Emergencies: fresh questions after the set 50 fixes", "40 first-aid situations"),
    ("53", "Emergencies: fresh questions after the set 52 fixes", "40 first-aid situations"),
    ("54", "Emergencies: fresh questions after the set 53 fixes", "40 first-aid situations"),
    ("55", "Emergencies: fresh questions after the broad fixes", "40 first-aid situations"),
    ("46", "Everyday questions: an adaptive length rule (not kept)", "40 mixed questions"),
    ("46f", "Everyday questions: the length rule, rerun on a frozen build (not kept)", "40 mixed questions"),
    ("49", "Everyday questions: plain-language wording (not kept)", "40 mixed questions"),
    ("51", "Everyday questions: one sentence for single facts (not kept)", "40 mixed questions"),
]

# Harness arm name -> words for the reader.
LABELS = {
    "reference": "Web reference answer",
    "a": "Guide shown as published (1.4.1)", "b": "Gemma writes from the guide (1.4.2)",
    "gemma-a": "Gemma, guide shown as published (1.4.1)", "gemma-b": "Gemma writes from the guide",
    "gemma-c": "Gemma writes from the guide, with the answer checks (1.4.2)",
    "4b-a": "4B answers from memory (1.4.1)", "4b-c": "4B with a careful prompt (not kept)",
    "4b-now": "4B, release wording", "4b-careful": "4B, careful prompt (not kept)", "4b-cited": "4B, citing the guides (not kept)",
    "4b-fallback": "4B, guide or a short no-guide reply (1.4.2)", "2b-now": "2B, release wording",
    "gemma-cand": "Gemma writes from the guide, with the answer checks",
    "gemma-now": "Gemma, release wording", "gemma-adapt": "Gemma, adaptive length (not kept)", "4b-adapt": "4B, adaptive length (not kept)",
    "gemma-plain": "Gemma, plain-language wording (not kept)", "4b-plain": "4B, plain-language wording (not kept)",
    "gemma-fact": "Gemma, one-sentence facts (not kept)", "4b-fact": "4B, one-sentence facts (not kept)",
    "gemma-before": "Gemma, before the fixes", "gemma-after": "Gemma, after the fixes",
    "4b-before": "4B, before the fixes", "4b-after": "4B, after the fixes",
    "2b-before": "2B, before the fixes", "2b-after": "2B, after the fixes",
}
ORDER = ["gemma", "4b", "2b", "a", "b", "reference"]


def label(system: str) -> str:
    return LABELS.get(system, system)


def rank(system: str) -> int:
    for i, prefix in enumerate(ORDER):
        if system == prefix or system.startswith(prefix + "-"):
            return i
    return len(ORDER)


def load(set_id: str) -> list[dict]:
    path = FOLDER / f"heldout{set_id}-grades.jsonl"
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def summary_rows(rows: list[dict]) -> list[tuple[str, int, int, float, float, int]]:
    by_system: dict[str, list[dict]] = OrderedDict()
    for row in sorted(rows, key=lambda r: (rank(r["system"]), r["system"])):
        by_system.setdefault(row["system"], []).append(row)
    out = []
    for system, answers in by_system.items():
        good = sum(1 for a in answers if all(g["score"] >= 7 for g in a["grades"]))
        mean = statistics.mean(statistics.mean(g["score"] for g in a["grades"]) for a in answers)
        wrong = statistics.mean(statistics.mean(len(g["errors"]) for g in a["grades"]) for a in answers)
        unsafe = sum(1 for a in answers if all(g["unsafe"] for g in a["grades"]))
        out.append((system, len(answers), good, mean, wrong, unsafe))
    return out


def cell(answer: dict) -> str:
    scores = "/".join(str(g["score"]) for g in answer["grades"])
    unsafe = " ⚠" if all(g["unsafe"] for g in answer["grades"]) else ""
    return scores + unsafe


def found_wrong(answer: dict) -> str:
    errors = []
    for g in answer["grades"]:
        for e in g["errors"]:
            if e not in errors:
                errors.append(e)
    text = "; ".join(errors)
    return text if len(text) <= 220 else text[:217].rstrip() + "…"


def render_set(set_id: str, title: str, size: str) -> str:
    rows = load(set_id)
    emergency = "first-aid" in size
    # A set can mix setups graded once with setups graded twice (set 46), so the rounds are said per setup.
    twice = [s for s, *_ in summary_rows(rows) if min(len(r["grades"]) for r in rows if r["system"] == s) > 1]
    graded = ("two rounds" if len(twice) == len(summary_rows(rows)) else "one round" if not twice
              else "one round, except " + " and ".join(f"“{label(s)}”" for s in twice) + " in two")
    lines = [f"## Set {set_id}: {title}", "",
             f"{size}, written blind and sealed as `../heldout{set_id[:2]}.jsonl`. Each answer was graded in "
             f"{graded}; a good answer scores 7 or more in every round."
             + (" ⚠ marks an answer both graders said could plausibly harm the person." if emergency else ""), ""]
    head = "| Setup | Answers | Good answers | Mean score | Grader annotations per answer |" + (" Unsafe |" if emergency else "")
    lines += [head, "| --- | ---: | ---: | ---: | ---: |" + (" ---: |" if emergency else "")]
    for system, n, good, mean, wrong, unsafe in summary_rows(rows):
        lines.append(f"| {label(system)} | {n} | {good} ({round(100 * good / n)}%) | {mean:.1f} | {wrong:.2f} |" + (f" {unsafe} |" if emergency else ""))
    systems = [s for s, *_ in summary_rows(rows)]
    by_q: dict[str, dict[str, dict]] = OrderedDict()
    for row in sorted(rows, key=lambda r: r["id"]):
        by_q.setdefault(row["id"], {})[row["system"]] = row
    lines += ["", "<details>", f"<summary>Every question of set {set_id}, with each setup's scores</summary>", ""]
    lines.append("| # | Question | " + " | ".join(label(s) for s in systems) + " | What the graders found wrong |")
    lines.append("| --- | --- | " + " | ".join("---:" for _ in systems) + " | --- |")
    for qid, answers in by_q.items():
        any_row = next(iter(answers.values()))
        question = any_row["question"].replace("|", "\\|").replace("\n", " ")
        wrong = "; ".join(f"{label(s)}: {found_wrong(answers[s])}" for s in systems
                          if s in answers and s != "reference" and found_wrong(answers[s]))
        cells = " | ".join(cell(answers[s]) if s in answers else "—" for s in systems)
        wrong = wrong.replace("|", "\\|")
        lines.append(f"| {qid.split('-')[-1]} | {question} | {cells} | {wrong} |")
    lines += ["", "</details>", ""]
    return "\n".join(lines)


def main() -> None:
    parts = ["# Test results for 1.4.2, set by set", "",
             "Every question set used for the 1.4.2 release, with each setup's scores. The method and the conclusions are on "
             "[How Field Atlas 1.4.2 was tested](../../testing-1.4.2.md); this page is the full record behind it.", "",
             "How to read a row: each answer was scored 0 to 10 by an AI grader who checked the facts on the web without "
             "knowing which setup wrote it, in two rounds with different graders, shown as `first/second` (some sets or "
             "setups had one round; each set says which). A **good answer** scores 7 or more in every round. "
             "**Grader annotations** are the problems the graders listed: mostly wrong facts, also omissions and repeated "
             "points, not independently confirmed errors. "
             "The **web reference answer** is what an assistant with internet access wrote for the same question, graded the "
             "same way, as a yardstick. The raw files (`heldoutNN-grades.jsonl`) hold the same data, one line per answer, "
             "with the graders' notes.", "",
             "Emergency sets first, then the everyday-question sets used to test wording changes that were not kept. "
             "Where a set was graded in more than two rounds (set 55's reference answers were graded alongside both the "
             "small models and Gemma), every score is shown.", ""]
    for set_id, title, size in SETS:
        if (FOLDER / f"heldout{set_id}-grades.jsonl").exists():
            parts.append(render_set(set_id, title, size))
    (FOLDER / "README.md").write_text("\n".join(parts))
    print("wrote", FOLDER / "README.md")


if __name__ == "__main__":
    main()
