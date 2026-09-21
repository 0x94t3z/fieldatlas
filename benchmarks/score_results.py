from __future__ import annotations

import argparse
from collections import defaultdict
import json
from pathlib import Path
import re
import unicodedata


QUESTION_FIELDS = {
    "id", "category", "prompt", "answerable", "requiredEvidence", "prohibitedClaims", "scoringNotes"
}
RESULT_FIELDS = {"questionId", "answer"}
ABSTENTION_MARKERS = ("insufficient evidence", "cannot determine", "not enough evidence")
CITATION = re.compile(r"\[S[1-9][0-9]*\]")


class BenchmarkError(ValueError):
    pass


def _normalized(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def _require_root(value: object, collection: str) -> list[dict[str, object]]:
    if not isinstance(value, dict) or set(value) != {"schemaVersion", collection}:
        raise BenchmarkError(f"invalid {collection} root")
    if value["schemaVersion"] != 1 or not isinstance(value[collection], list):
        raise BenchmarkError(f"unsupported {collection} schema")
    return value[collection]


def _validate_benchmark(benchmark: object) -> list[dict[str, object]]:
    questions = _require_root(benchmark, "questions")
    if not questions:
        raise BenchmarkError("benchmark has no questions")
    seen: set[str] = set()
    for index, question in enumerate(questions):
        if not isinstance(question, dict) or set(question) != QUESTION_FIELDS:
            raise BenchmarkError(f"invalid question fields at index {index}")
        for field in ("id", "category", "prompt", "scoringNotes"):
            if not isinstance(question[field], str) or not question[field].strip():
                raise BenchmarkError(f"invalid {field} at index {index}")
        identifier = question["id"]
        if identifier in seen:
            raise BenchmarkError(f"duplicate question id: {identifier}")
        seen.add(identifier)
        if not isinstance(question["answerable"], bool):
            raise BenchmarkError(f"invalid answerable at index {index}")
        for field in ("requiredEvidence", "prohibitedClaims"):
            phrases = question[field]
            if not isinstance(phrases, list) or any(not isinstance(item, str) or not item.strip() for item in phrases):
                raise BenchmarkError(f"invalid {field} at index {index}")
        if question["answerable"] and not question["requiredEvidence"]:
            raise BenchmarkError(f"answerable question has no required evidence: {identifier}")
    return questions


def _validate_results(results: object, expected_ids: set[str]) -> dict[str, str]:
    rows = _require_root(results, "results")
    parsed: dict[str, str] = {}
    for index, row in enumerate(rows):
        if not isinstance(row, dict) or set(row) != RESULT_FIELDS:
            raise BenchmarkError(f"invalid result fields at index {index}")
        identifier = row["questionId"]
        answer = row["answer"]
        if not isinstance(identifier, str) or not isinstance(answer, str):
            raise BenchmarkError(f"invalid result at index {index}")
        if identifier in parsed:
            raise BenchmarkError(f"duplicate result: {identifier}")
        parsed[identifier] = answer
    if set(parsed) != expected_ids:
        raise BenchmarkError(
            f"result ids mismatch; missing={sorted(expected_ids - set(parsed))}, "
            f"extra={sorted(set(parsed) - expected_ids)}"
        )
    return parsed


def _score(question: dict[str, object], answer: str) -> dict[str, object]:
    normalized = _normalized(answer)
    if not question["answerable"]:
        abstained = any(marker in normalized for marker in ABSTENTION_MARKERS)
        return {"score": 1.0 if abstained else 0.0, "abstained": abstained}
    required = [_normalized(phrase) for phrase in question["requiredEvidence"]]
    prohibited = [_normalized(phrase) for phrase in question["prohibitedClaims"]]
    coverage = sum(phrase in normalized for phrase in required) / len(required)
    prohibited_hits = sum(phrase in normalized for phrase in prohibited)
    grounded = max(0.0, coverage - min(1.0, prohibited_hits * 0.5))
    citation_factor = 1.0 if CITATION.search(answer) else 0.5
    return {
        "score": round(grounded * citation_factor, 6),
        "required_coverage": round(coverage, 6),
        "prohibited_hits": prohibited_hits,
        "has_citation": citation_factor == 1.0,
    }


def _summary(questions: list[dict[str, object]], answers: dict[str, str]) -> dict[str, object]:
    rows = []
    categories: dict[str, list[float]] = defaultdict(list)
    for question in questions:
        detail = _score(question, answers[question["id"]])
        score = detail["score"]
        categories[question["category"]].append(score)
        rows.append({"questionId": question["id"], "category": question["category"], **detail})
    category_means = {
        category: round(sum(values) / len(values), 6) for category, values in sorted(categories.items())
    }
    overall = round(sum(row["score"] for row in rows) / len(rows), 6)
    return {"overall": overall, "categories": category_means, "questions": rows}


def score_pair(benchmark: object, offline: object, baseline: object) -> dict[str, object]:
    questions = _validate_benchmark(benchmark)
    identifiers = {question["id"] for question in questions}
    offline_summary = _summary(questions, _validate_results(offline, identifiers))
    baseline_summary = _summary(questions, _validate_results(baseline, identifiers))
    denominator = baseline_summary["overall"]
    ratio = None if denominator == 0 else round(offline_summary["overall"] / denominator, 6)
    comparisons = [
        {
            "questionId": offline_row["questionId"],
            "offline": offline_row["score"],
            "baseline": baseline_row["score"],
            "delta": round(offline_row["score"] - baseline_row["score"], 6),
        }
        for offline_row, baseline_row in zip(offline_summary["questions"], baseline_summary["questions"])
    ]
    return {
        "schemaVersion": 1,
        "offline": offline_summary,
        "baseline": baseline_summary,
        "offline_to_baseline_ratio": ratio,
        "raw_comparisons": comparisons,
        "not_an_issuer_determination": True,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Score paired FieldAtlas benchmark outputs")
    parser.add_argument("--benchmark", required=True, type=Path)
    parser.add_argument("--offline", required=True, type=Path)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    values = [json.loads(path.read_text(encoding="utf-8")) for path in (args.benchmark, args.offline, args.baseline)]
    scored = score_pair(*values)
    args.output.write_text(
        json.dumps(scored, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n"
    )


if __name__ == "__main__":
    main()
