#!/usr/bin/env python3
"""Replay frozen app requests across evidence conditions and samplers. No accuracy score."""
import argparse
import copy
import json
from pathlib import Path
import socket
import subprocess
import time
import urllib.error
import urllib.request

from check_claim_support import sha
from run_desktop_research import ROOT, server_command, validate_inputs

SAMPLERS = {
    "existing": {"temperature": 0.3, "top_k": 40, "top_p": 0.95, "min_p": 0.05,
                 "repeat_penalty": 1.10, "repeat_last_n": 128, "presence_penalty": 0.0},
    "qwen-recommended": {"temperature": 1.0, "top_k": 20, "top_p": 1.0, "min_p": 0.0,
                         "repeat_penalty": 1.0, "repeat_last_n": 128, "presence_penalty": 2.0},
}


def payload(request, sampler, seed):
    result = copy.deepcopy(request)
    result.update(SAMPLERS[sampler], seed=seed, cache_prompt=False, stream=False)
    return result


def inputs(plan, reports):
    cases = []
    expected = plan["questions"]
    if set(reports) != set(plan["conditions"]):
        raise ValueError("Reports must cover all frozen conditions")
    for condition, report in reports.items():
        rows = [r for r in report["runs"] if r["question"] in expected]
        if sorted(r["question"] for r in rows) != sorted(expected):
            raise ValueError("Missing or duplicate frozen questions")
        for row in rows:
            if row["status"] == "FROZEN_REQUEST" and condition == "manual-complete-context":
                request = row["request"]
            else:
                if row["status"] != "COMPLETED_UNSCORED" or not row.get("calls"):
                    raise ValueError("Incomplete source run")
                request = row["calls"][-1]["request"]
            if row["question"] not in request["messages"][-1]["content"]:
                raise ValueError("Final request does not contain its question")
            cases.append({"condition": condition, "question": row["question"], "request": request})
    return cases


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--report", action="append", required=True, help="condition=report.json")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--port", type=int, default=8138)
    parser.add_argument("--model", type=Path, default=ROOT / "build/model-cache/Qwen_Qwen3.5-2B-Q4_K_M.verified.gguf")
    parser.add_argument("--server", type=Path, default=ROOT / "build/desktop-llama/bin/llama-server")
    args = parser.parse_args()
    paths = dict(item.split("=", 1) for item in args.report)
    if len(paths) != len(args.report):
        parser.error("Duplicate condition")
    plan = json.loads(args.plan.read_text())
    reports = {name: json.loads(Path(path).read_text()) for name, path in paths.items()}
    cases = inputs(plan, reports)
    if any(s not in SAMPLERS for s in plan["sampling"]) or not plan["seeds"]:
        parser.error("Unknown sampler or missing seeds")
    directory = args.output_dir.resolve()
    if not directory.is_relative_to(ROOT / "build") or directory == ROOT / "build":
        parser.error("Use a new directory under ignored build/")
    model, server = args.model.resolve(), args.server.resolve()
    validate_inputs(model, [], server)
    model_hash = sha(model)
    for report in reports.values():
        if report["identities"][0]["sha256"] != model_hash:
            parser.error("Input report used a different model")
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", args.port))
    directory.mkdir(parents=True, exist_ok=False)
    (directory / "runner.py.snapshot").write_bytes(Path(__file__).read_bytes())
    (directory / "plan.json").write_bytes(args.plan.read_bytes())
    for index, path in enumerate(paths.values()):
        (directory / f"input-{index}.json").write_bytes(Path(path).read_bytes())
    report = {"status": "STARTING", "quality": "UNSCORED", "plan": plan,
              "planSha256": sha(args.plan), "modelSha256": model_hash, "serverSha256": sha(server),
              "toolSha256": sha(Path(__file__)), "samplers": SAMPLERS,
              "inputs": {name: {"path": path, "sha256": sha(Path(path))} for name, path in paths.items()},
              "cases": cases, "rows": []}
    output = directory / "report.json"
    def save():
        output.write_text(json.dumps(report, indent=2))
    save()
    endpoint = f"http://127.0.0.1:{args.port}"
    command = server_command(server, model, args.port)
    report["serverCommand"] = command
    with (directory / "server.log").open("w") as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 120
            while True:
                if process.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError("Local server failed to start")
                try:
                    with urllib.request.urlopen(endpoint + "/health", timeout=2) as response:
                        if response.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError):
                    pass
                time.sleep(0.5)
            with urllib.request.urlopen(endpoint + "/props", timeout=5) as response:
                props = json.load(response)
            if Path(props["model_path"]).resolve() != model:
                raise RuntimeError("Wrong model loaded")
            report["status"] = "RUNNING"
            # Alternate sampler order by seed; timings remain descriptive, not a phone benchmark.
            for index, seed in enumerate(plan["seeds"]):
                for case in cases:
                    samplers = plan["sampling"][::1 if index % 2 == 0 else -1]
                    for sampler in samplers:
                        request_data = payload(case["request"], sampler, seed)
                        request = urllib.request.Request(endpoint + "/v1/chat/completions",
                            data=json.dumps(request_data).encode(), headers={"Content-Type": "application/json"})
                        started = time.monotonic()
                        with urllib.request.urlopen(request, timeout=180) as response:
                            result = json.load(response)
                        report["rows"].append({"condition": case["condition"], "question": case["question"],
                            "sampler": sampler, "seed": seed, "request": request_data, "response": result,
                            "durationSeconds": time.monotonic() - started})
                        save()
                        print(f"Recorded {len(report['rows'])}: {case['condition']} / {sampler} / {seed}", flush=True)
            report["status"] = "COMPLETED_UNSCORED"
        except BaseException as error:
            report.update(status="ERROR", error=str(error))
            raise
        finally:
            save()
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == "__main__":
    main()
