#!/usr/bin/env python3
"""Lossless answer-block review; deliberately not an atomic-claim extractor."""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
LABELS = {"supported", "contradicted", "unsupported", "not-a-claim"}
DISPLAY_HEADINGS = {"## Model explanation—not verified against saved sources", "## From saved sources"}


def blocks(answer):
    result = []
    cursor = 0
    for match in re.finditer(r"\n[ \t]*\n|\Z", answer):
        section = answer[cursor:match.start()]
        text = section.strip()
        if text:
            start = cursor + len(section) - len(section.lstrip())
            result.append({"start": start, "end": start + len(text), "text": text,
                           "label": "not-a-claim" if text in DISPLAY_HEADINGS else "unreviewed",
                           "reason": "App display heading" if text in DISPLAY_HEADINGS else ""})
        cursor = match.end()
    # Coverage is mechanical, not a claim that each block has only one assertion.
    covered = {i for block in result for i in range(block["start"], block["end"])}
    assert all(char.isspace() or i in covered for i, char in enumerate(answer))
    return result


def packed_passages(run):
    if not run.get("calls"):
        return []
    prompt = run["calls"][-1]["request"]["messages"][-1]["content"]
    if "\n\nEVIDENCE:\n" not in prompt:
        return []
    body = prompt.split("\n\nEVIDENCE:\n", 1)[1].split("\n\nQUESTION:\n", 1)[0]
    if not body.strip():
        return []
    sections = re.split(r"\n\n(?=\[S\d+\]\n)", body)
    for i, section in enumerate(sections, 1):
        if not section.startswith(f"[S{i}]\n") or "\nExcerpt: " not in section:
            raise ValueError("Unexpected evidence packing; cannot establish source mapping")
    return [section.split("\nExcerpt: ", 1)[1] for section in sections]


def prepare(report):
    answers = []
    for index, run in enumerate(report["runs"]):
        if run.get("status") != "COMPLETED_UNSCORED":
            raise ValueError("Do not score incomplete or failed answers")
        answers.append({"id": f"answer-{index}", "question": run["question"],
                        "answer": run["visibleAnswer"], "passages": packed_passages(run),
                        "coverageReview": "unreviewed", "blocks": blocks(run["visibleAnswer"])})
    return {"scope": "Agent-reviewed answer blocks, not independent labels or atomic claims",
            "answers": answers}


def export_checks(review):
    cases = []
    for answer in review["answers"]:
        if answer["coverageReview"] == "unreviewed":
            raise ValueError("Question coverage must be reviewed separately")
        expected_blocks = blocks(answer["answer"])
        if [(x["start"], x["end"], x["text"]) for x in answer["blocks"]] != [
            (x["start"], x["end"], x["text"]) for x in expected_blocks]:
            raise ValueError("Answer blocks were removed or changed")
        probes = []
        for block in answer["blocks"]:
            if block["label"] not in LABELS or not block["reason"].strip():
                raise ValueError("Every block needs an explicit label and reason")
            if block["label"] == "not-a-claim":
                continue
            if not answer["passages"]:
                # Never treat source absence as a model verification success.
                continue
            probes.append({"claim": block["text"], "citations": list(range(1, len(answer["passages"]) + 1)),
                           "label": block["label"]})
        if probes:
            cases.append({"id": answer["id"], "question": answer["question"],
                          "passages": answer["passages"], "checks": ["Every assertion in the block must be supported."],
                          "probes": probes})
    return {"scope": "Full answer-block support against all packed passages, not citation-placement accuracy", "cases": cases}


def validate_origin(review, report_bytes):
    if review.get("inputSha256") != hashlib.sha256(report_bytes).hexdigest():
        raise ValueError("Original report hash does not match the review")
    original = prepare(json.loads(report_bytes))
    fields = ("id", "question", "answer", "passages")
    if [[a[k] for k in fields] for a in review["answers"]] != [
        [a[k] for k in fields] for a in original["answers"]]:
        raise ValueError("Review changed original answers, questions or evidence")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--review", type=Path)
    parser.add_argument("--source-report", type=Path, help="Required when exporting a review; binds labels to original answers and excerpts")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if bool(args.report) == bool(args.review):
        parser.error("Provide --report to prepare or --review to export labels")
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / "build"):
        parser.error("Output must remain in ignored build/")
    source = args.report or args.review
    data = json.loads(source.read_text())
    if args.review:
        if args.source_report is None:
            parser.error("--review requires --source-report")
        validate_origin(data, args.source_report.read_bytes())
    result = prepare(data) if args.report else export_checks(data)
    result["inputSha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x") as stream:
        json.dump(result, stream, indent=2)
    print(output)


if __name__ == "__main__":
    main()
