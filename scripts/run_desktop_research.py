#!/usr/bin/env python3
"""Local model + real app pipeline evaluation. No Android device required."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


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
    except ValueError as error:
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
        "provenance": {"gitCommit": git("rev-parse", "HEAD"), "gitStatus": git("status", "--short"),
                       "appKotlinSha256": digest.hexdigest(), "serverCommand": command,
                       "llamaCommit": git("-C", "third_party/llama.cpp", "rev-parse", "HEAD")},
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
