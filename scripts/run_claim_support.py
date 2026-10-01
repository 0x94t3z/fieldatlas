#!/usr/bin/env python3
"""Run authored support fixtures; emit a human-review sheet, never an automatic accuracy score."""
import argparse
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tools/claim_support_cases.json"


def load_cases(path):
    suite = json.loads(path.read_text())
    cases = suite["cases"]
    if not cases or len({c["id"] for c in cases}) != len(cases) or len({c["question"] for c in cases}) != len(cases):
        raise ValueError("Cases must have unique IDs and questions")
    for case in cases:
        if not case["checks"] or not case["question"].strip():
            raise ValueError("Each case needs a question and review checks")
        for probe in case["probes"]:
            if probe["label"] not in {"supported", "unsupported", "contradicted", "invalid-citation"}:
                raise ValueError("Unknown support label")
            valid = all(type(i) is int and 1 <= i <= len(case["passages"]) for i in probe["citations"])
            if (probe["label"] == "invalid-citation") == valid:
                raise ValueError("Probe citation bounds disagree with label")
    return cases


def prepare(cases, directory):
    directory.mkdir(parents=True, exist_ok=False)
    database = directory / "content.sqlite"
    selection = {}
    with sqlite3.connect(database) as db:
        db.execute("PRAGMA user_version=1")
        db.execute("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED, document_id UNINDEXED, title, source UNINDEXED, text, tokenize='porter unicode61')")
        for case in cases:
            ids = []
            for index, passage in enumerate(case["passages"]):
                identifier = f'{case["id"]}:{index + 1}'
                ids.append(identifier)
                db.execute("INSERT INTO chunks_fts VALUES(?,?,?,?,?)", (identifier, identifier,
                           case["question"], f'https://example.invalid/claim-support/{identifier}', passage))
            selection[case["question"]] = ids
    (directory / "selection.json").write_text(json.dumps(selection, indent=2))
    return database


def review_sheet(cases, report):
    rows = {row["question"]: row for row in report.get("runs", [])}
    return {
        "status": "UNREVIEWED: no semantic score computed",
        "instructions": "Review raw answer claims against the actual request excerpts. Then review visibleAnswer separately. Mark each check pass/fail/uncertain and explain with answer spans and source IDs. Citation presence or verbatim matching alone is not claim support. Fixture probes use fixture passage order; actual prompt S-numbers may be reordered. Missing or failed runs are not passes.",
        "cases": [{"id": c["id"], "question": c["question"],
                   "executionStatus": rows.get(c["question"], {}).get("status", "MISSING"),
                   "checks": [{"criterion": check, "verdict": "unreviewed", "reason": ""} for check in c["checks"]],
                   "referenceProbes": c["probes"]} for c in cases],
    }


def evaluation_command(directory, database, answer_policy, cases, model=None):
    command = [sys.executable, str(ROOT / "scripts/run_desktop_research.py"), "--offline-gradle",
               "--database", str(database), "--manual-selection", str(directory / "selection.json"),
               "--answer-policy", answer_policy, "--output", str(directory / "report.json")]
    if model is not None:
        command.extend(["--model", str(model.resolve())])
    for case in cases:
        command.extend(["--question", case["question"]])
    return command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, required=True, help="New directory under build/")
    parser.add_argument("--answer-policy", choices=["app", "source-only", "source-partial", "evidence-first", "bounded-summary"], default="app")
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--model", type=Path, help="Local GGUF override for a diagnostic comparison; does not change the app default")
    args = parser.parse_args()
    directory = args.output_dir.resolve()
    if not directory.is_relative_to(ROOT / "build"):
        parser.error("Output must be under ignored build/")
    cases = load_cases(FIXTURES)
    database = prepare(cases, directory)
    (directory / "fixtures.json").write_bytes(FIXTURES.read_bytes())
    report_path = directory / "report.json"
    command = evaluation_command(directory, database, args.answer_policy, cases, args.model)
    result = 0 if args.prepare_only else subprocess.run(command, cwd=ROOT).returncode
    report = json.loads(report_path.read_text()) if report_path.exists() else {}
    sheet = review_sheet(cases, report)
    sheet["fixtureSha256"] = hashlib.sha256((directory / "fixtures.json").read_bytes()).hexdigest()
    sheet["reportSha256"] = hashlib.sha256(report_path.read_bytes()).hexdigest() if report_path.exists() else None
    (directory / "review.json").write_text(json.dumps(sheet, indent=2))
    print(f"Review sheet: {directory / 'review.json'}; completion is not correctness.")
    return result


if __name__ == "__main__":
    raise SystemExit(main())
