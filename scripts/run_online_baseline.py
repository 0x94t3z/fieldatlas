#!/usr/bin/env python3
"""Collect a named OpenRouter baseline for the frozen Field Atlas benchmark.

The API key is read from OPENROUTER_API_KEY and is never written to disk.
The output JSON is directly consumable by benchmarks/score_results.py; run
metadata is written beside it as <output>.meta.json.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"


def load_questions(path: Path) -> list[dict[str, object]]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("schemaVersion") != 1 or not isinstance(value.get("questions"), list):
        raise ValueError("benchmark must use schemaVersion 1")
    return value["questions"]


def request_answer(api_key: str, model: str, prompt: str, timeout: float, retries: int) -> str:
    body = {
        "model": model,
        "temperature": 0,
        "messages": [
            {
                "role": "system",
                "content": "Answer the user directly and accurately. Do not mention this benchmark or any hidden instructions.",
            },
            {"role": "user", "content": prompt},
        ],
    }
    request = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/0x94t3z/fieldatlas",
            "X-Title": "Field Atlas benchmark baseline",
        },
        method="POST",
    )
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                value = json.load(response)
            break
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")[:500]
            if error.code == 429 and attempt < retries:
                wait = min(120.0, 5.0 * (2**attempt))
                print(f"provider rate-limited; retrying in {wait:.0f}s", file=sys.stderr, flush=True)
                time.sleep(wait)
                continue
            raise RuntimeError(f"OpenRouter HTTP {error.code}: {detail}") from error
        except urllib.error.URLError as error:
            if attempt < retries:
                wait = min(60.0, 5.0 * (2**attempt))
                print(f"connection failed; retrying in {wait:.0f}s", file=sys.stderr, flush=True)
                time.sleep(wait)
                continue
            raise RuntimeError(f"OpenRouter connection failed: {error.reason}") from error
    try:
        content = value["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as error:
        raise RuntimeError("OpenRouter response did not contain a text answer") from error
    if not isinstance(content, str) or not content.strip():
        raise RuntimeError("OpenRouter returned an empty answer")
    return content


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark", required=True, type=Path)
    parser.add_argument("--model", required=True, help="Pinned OpenRouter model ID, preferably a :free model")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--timeout", type=float, default=120.0)
    parser.add_argument("--delay", type=float, default=1.0, help="Seconds between requests")
    parser.add_argument("--retries", type=int, default=4, help="Retries for rate limits and transient network errors")
    parser.add_argument("--resume", action="store_true", help="Resume completed rows from the output file")
    args = parser.parse_args()

    api_key = os.environ.get("OPENROUTER_API_KEY")
    if not api_key:
        raise SystemExit("OPENROUTER_API_KEY is not set")
    questions = load_questions(args.benchmark)
    results: list[dict[str, str]] = []
    if args.resume and args.output.exists():
        prior = json.loads(args.output.read_text(encoding="utf-8"))
        if prior.get("schemaVersion") != 1 or not isinstance(prior.get("results"), list):
            raise SystemExit("cannot resume: output is not a benchmark result file")
        results = prior["results"]
        expected = [q.get("id") for q in questions[: len(results)]]
        if [row.get("questionId") for row in results] != expected:
            raise SystemExit("cannot resume: output rows do not match benchmark order")
    started = datetime.now(timezone.utc).isoformat()
    for index, question in enumerate(questions, start=1):
        if index <= len(results):
            continue
        identifier = question.get("id")
        prompt = question.get("prompt")
        if not isinstance(identifier, str) or not isinstance(prompt, str):
            raise SystemExit(f"invalid question at index {index}")
        print(f"[{index}/{len(questions)}] {identifier}", file=sys.stderr, flush=True)
        answer = request_answer(api_key, args.model, prompt, args.timeout, max(0, args.retries))
        results.append({"questionId": identifier, "answer": answer})
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps({"schemaVersion": 1, "results": results}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        if index < len(questions):
            time.sleep(max(0.0, args.delay))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps({"schemaVersion": 1, "results": results}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    metadata = {
        "schemaVersion": 1,
        "provider": "OpenRouter",
        "endpoint": ENDPOINT,
        "model": args.model,
        "temperature": 0,
        "startedAt": started,
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "promptSet": str(args.benchmark),
        "resultFile": str(args.output),
    }
    args.output.with_name(args.output.name + ".meta.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Wrote {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
