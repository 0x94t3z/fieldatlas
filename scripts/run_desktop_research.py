#!/usr/bin/env python3
"""Local model + real app pipeline evaluation. No Android device required."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import sqlite3
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def selected_evidence(path, databases, questions):
    """Diagnostic only: copy unchanged passages from the hashed input database."""
    if len(databases) != 1:
        raise ValueError("Manual selection requires exactly one database")
    selection = json.loads(path.read_text())
    if not isinstance(selection, dict) or set(selection) != set(questions):
        raise ValueError("Manual selection must cover exactly the requested questions")
    result = {}
    with sqlite3.connect(databases[0].as_uri() + "?mode=ro", uri=True) as db:
        for question, ids in selection.items():
            if not isinstance(ids, list) or any(not isinstance(i, str) for i in ids) or len(ids) != len(set(ids)):
                raise ValueError("Selected chunk IDs must be a unique list of strings")
            evidence = []
            for chunk_id in ids:
                rows = db.execute("SELECT document_id, chunk_id, title, source, text FROM chunks_fts WHERE chunk_id = ?", (chunk_id,)).fetchall()
                if len(rows) != 1:
                    raise ValueError(f"Expected exactly one passage for {chunk_id}")
                evidence.append(dict(zip(["documentId", "chunkId", "title", "source", "text"], rows[0]), score=0.0,
                                     matchedBy="manually selected diagnostic passage; not retrieval"))
            result[question] = evidence
    return result


def validate_inputs(model, databases, server):
    for label, path in [("Model", model), ("Server", server)] + [("Database", db) for db in databases]:
        if not path.is_file():
            raise ValueError(f"{label} file does not exist: {path}")


def server_command(server, model, port):
    return [str(server), "-m", str(model), "--host", "127.0.0.1", "--port", str(port),
            "-c", "8192", "--parallel", "1", "-ngl", "99", "--jinja"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--question", action="append", required=True, help="Repeat for several questions")
    parser.add_argument("--model", type=Path, default=ROOT / "build/model-cache/Qwen_Qwen3.5-2B-Q4_K_M.verified.gguf")
    parser.add_argument("--database", action="append", type=Path, help="Extracted content.sqlite; repeat for multiple packs")
    parser.add_argument("--model-only", action="store_true", help="Evaluate without collections")
    parser.add_argument("--manual-selection", type=Path, help="Diagnostic JSON mapping exact questions to ordered chunk IDs; bypasses retrieval only")
    parser.add_argument("--vector-fixture", type=Path, help="Diagnostic frozen query embeddings and matching vector-pack manifest; does not bypass retrieval")
    parser.add_argument("--answer-policy", choices=["app", "source-only", "source-partial", "evidence-first", "bounded-summary"], default="app", help="Diagnostic policy override; source-only uses the existing app strict policy")
    parser.add_argument("--server", type=Path, default=ROOT / "build/desktop-llama/bin/llama-server")
    parser.add_argument("--output", type=Path, default=ROOT / "build/desktop-evaluation/report.json")
    parser.add_argument("--port", type=int, default=8127)
    parser.add_argument("--offline-gradle", action="store_true", help="After first dependency resolution, forbid Gradle downloads")
    args = parser.parse_args()
    if args.model_only and args.database:
        parser.error("Use --model-only or --database, not both")
    if any(not question.strip() for question in args.question):
        parser.error("Questions must not be blank")
    databases = [] if args.model_only else args.database or [
        ROOT / "build/biology-retrieval-audit/content.sqlite",
        ROOT / "build/packs/travel/wikivoyage-places/content.sqlite",
    ]
    model, server = args.model.resolve(), args.server.resolve()
    databases = [db.resolve() for db in databases]
    try:
        validate_inputs(model, databases, server)
        manual = selected_evidence(args.manual_selection, databases, args.question) if args.manual_selection else None
    except (ValueError, OSError, sqlite3.Error) as error:
        parser.error(str(error))
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / "build"):
        parser.error("Reports must stay under the repository's ignored build/ directory")
    output.parent.mkdir(parents=True, exist_ok=True)
    # Never reuse or terminate somebody else's server.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", args.port))
    endpoint = f"http://127.0.0.1:{args.port}"
    command = server_command(server, model, args.port)
    git = lambda *argv: subprocess.check_output(["git", *argv], cwd=ROOT, text=True).strip()
    digest = hashlib.sha256()
    for path in sorted((ROOT / "app/src/main/java").rglob("*.kt")):
        digest.update(str(path.relative_to(ROOT)).encode())
        digest.update(path.read_bytes())
    config = {
        "model": str(model), "databases": list(map(str, databases)),
        "questions": args.question, "output": str(output), "endpoint": endpoint,
        "answerPolicy": args.answer_policy,
        "provenance": {"gitCommit": git("rev-parse", "HEAD"), "gitStatus": git("status", "--short"),
                       "appKotlinSha256": digest.hexdigest(), "serverCommand": command,
                       "llamaCommit": git("-C", "third_party/llama.cpp", "rev-parse", "HEAD")},
    }
    if args.vector_fixture:
        fixture = json.loads(args.vector_fixture.read_text())
        vector_database = Path(fixture["database"]).resolve()
        if vector_database not in databases or hashlib.sha256(vector_database.read_bytes()).hexdigest() != fixture["databaseSha256"]:
            parser.error("Vector fixture database does not match an enabled database")
        config["vectorFixture"] = fixture
        config["provenance"]["vectorFixtureSha256"] = hashlib.sha256(args.vector_fixture.read_bytes()).hexdigest()
    if manual is not None:
        config["manualEvidence"] = manual
        config["provenance"]["manualSelectionSha256"] = hashlib.sha256(args.manual_selection.read_bytes()).hexdigest()
        config["provenance"]["diagnosticMode"] = "manual passages; relevance gate and prompt packing unchanged; not retrieval accuracy"
    config["provenance"]["diagnosticToolSha256"] = {
        str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in [ROOT / "scripts/run_desktop_research.py",
                     ROOT / "app/src/test/java/xyz/fieldatlas/desktop/DesktopResearchTest.kt",
                     ROOT / "app/src/test/java/xyz/fieldatlas/desktop/DesktopGateway.kt"]
        if path.is_file()
    }
    config_path = output.parent / "config.json"
    config_path.write_text(json.dumps(config, indent=2))
    # Mark this attempt immediately; a failure must not leave an older success looking current.
    output.write_text(json.dumps({"status": "STARTING", "qualityVerdict": "UNSCORED", "provenance": config["provenance"]}, indent=2))
    with (output.parent / "server.log").open("w") as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 120
            while True:
                if process.poll() is not None:
                    raise RuntimeError("Model server exited; inspect server.log")
                try:
                    with urllib.request.urlopen(endpoint + "/health", timeout=2) as response:
                        if response.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError):
                    pass
                if time.monotonic() >= deadline:
                    raise TimeoutError("Model server did not become ready within 120 seconds")
                time.sleep(0.5)
            with urllib.request.urlopen(endpoint + "/props", timeout=5) as response:
                props = json.load(response)
            if Path(props["model_path"]).resolve() != model:
                raise RuntimeError("Server model does not match the requested model")
            config["provenance"]["serverProperties"] = props
            config_path.write_text(json.dumps(config, indent=2))
            gradle = [str(ROOT / "gradlew"), ":app:testDebugUnitTest", "--tests", "xyz.fieldatlas.desktop.DesktopResearchTest", "--quiet", "--no-configuration-cache"]
            if args.offline_gradle:
                gradle.append("--offline")
            result = subprocess.run(gradle, cwd=ROOT, env={**os.environ, "FIELDATLAS_DESKTOP_CONFIG": str(config_path)})
            if result.returncode:
                report = json.loads(output.read_text())
                report["status"] = "ERROR"
                output.write_text(json.dumps(report, indent=2))
            print(f"Report: {output}\nCompletion is not an accuracy score.")
            return result.returncode
        except Exception as error:
            report = json.loads(output.read_text())
            report.update(status="ERROR", error=str(error), qualityVerdict="UNSCORED")
            output.write_text(json.dumps(report, indent=2))
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == "__main__":
    raise SystemExit(main())
