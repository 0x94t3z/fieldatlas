#!/usr/bin/env python3
"""Reader benchmark: does a small model answer from the passages it is given, cite them, and admit gaps?

Each item in docs/fieldtest/reader/*.jsonl holds a question, the passages Field Atlas's own search returned for it
on the desktop, and a reference written from those passages only. The score is automatic and strict, so it is a
guide for development, not a grade: final judging adds a blind read of the answers.

    python3 tools/evaluation/reader_bench.py run   --data docs/fieldtest/reader/dev.jsonl --server http://127.0.0.1:8080 --out preds.jsonl
    python3 tools/evaluation/reader_bench.py score --data docs/fieldtest/reader/dev.jsonl --pred preds.jsonl
    python3 tools/evaluation/reader_bench.py prompt --data docs/fieldtest/reader/dev.jsonl --id dev-01

`run` talks to any OpenAI-compatible chat endpoint, such as llama.cpp's llama-server. Standard library only.
"""
from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
import time
import unicodedata
import urllib.request
from pathlib import Path

SYSTEM = "You answer questions offline from the numbered passages you are given."
INSTRUCTIONS = (
    "Answer the question using only the passages below. End each sentence that uses a passage with its number, "
    "like [S2]. If the passages do not answer the question, say \"The saved sources don't say.\" and stop. If they "
    "answer only part of it, answer that part and say what they don't cover. Never add medical or first-aid steps "
    "that are not in the passages. Keep it under 150 words."
)

# Wordings that count as admitting the passages do not answer. The prompt asks for the first; models paraphrase.
ABSTAIN = re.compile(
    r"(sources?|passages?|notes?|texts?)\s+(provided\s+)?(do(es)?\s*n[o']t|do(es)? not|don't)\s+"
    r"(say|cover|mention|contain|include|give|state|answer|provide|specify)"
    r"|not\s+(in|covered by|mentioned in|found in)\s+the\s+(saved\s+)?(sources?|passages?|notes?)"
    r"|no\s+(information|mention|details?)\s+(about|on|of)\b.*\b(in\s+the\s+)?(sources?|passages?)",
    re.I)
CITE = re.compile(r"\[S(\d+)\]")
NUMBER = re.compile(r"\d+(?:[.,]\d+)*")


def load(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def prompt(item: dict) -> str:
    passages = "\n\n".join(f"[S{p['n']}] {p['title']}\n{p['text']}" for p in item["passages"])
    return f"{INSTRUCTIONS}\n\nPASSAGES:\n{passages}\n\nQUESTION: {item['question']}"


def fold(text: str) -> str:
    """Lowercase, no accents, one kind of dash and apostrophe, no thousands separators, single spaces."""
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    text = text.lower().replace("’", "'")
    text = re.sub(r"(?<=\d),(?=\d{3}\b)", "", text)
    return re.sub(r"\s+", " ", re.sub(r"[‐-―]", "-", text))


def numbers(text: str) -> set[str]:
    # Citation markers and list numbering are not claims.
    text = re.sub(r"(?m)^\s*\d+[.)]\s", " ", CITE.sub(" ", text))
    return {n.rstrip(".,") for n in NUMBER.findall(fold(text))}


def score_item(item: dict, answer: str) -> dict:
    folded = fold(answer)
    cites = [int(n) for n in CITE.findall(answer)]
    valid = {p["n"] for p in item["passages"]}
    abstained = bool(ABSTAIN.search(answer))
    # A figure the passages and the question never mention came from memory, or was made up.
    known = numbers(item["question"]).union(*(numbers(p["text"] + " " + p["title"]) for p in item["passages"]))
    unsupported = sorted(numbers(answer) - known)
    facts = item.get("keyFacts", [])
    found = sum(any(fold(alt) in folded for alt in alts) for alts in facts)
    bad_cite = any(n not in valid for n in cites)
    if item["answerable"] == "no":
        correct = abstained and not unsupported
    else:
        correct = found == len(facts) and bool(cites) and not bad_cite and not unsupported
        if item["answerable"] == "yes":
            correct = correct and not abstained
    return dict(id=item["id"], category=item["category"], answerable=item["answerable"], correct=correct,
                abstained=abstained, factsFound=found, factsTotal=len(facts), cited=bool(cites), badCite=bad_cite,
                unsupportedNumbers=unsupported, words=len(answer.split()))


def score(data: list[dict], preds: dict[str, dict]) -> dict:
    rows = [score_item(item, preds.get(item["id"], {}).get("answer", "")) for item in data]
    missing = [item["id"] for item in data if item["id"] not in preds]
    share = lambda rs, key: round(100 * sum(bool(r[key]) for r in rs) / len(rs), 1) if rs else None
    answerable = [r for r in rows if r["answerable"] != "no"]
    gaps = [r for r in rows if r["answerable"] == "no"]
    categories = sorted({r["category"] for r in rows})
    seconds = [p["seconds"] for p in preds.values() if isinstance(p.get("seconds"), (int, float))]
    return dict(
        items=len(rows), missing=missing,
        correctPct=share(rows, "correct"),
        byCategory={c: share([r for r in rows if r["category"] == c], "correct") for c in categories},
        keyFactRecallPct=round(100 * sum(r["factsFound"] for r in answerable)
                               / max(1, sum(r["factsTotal"] for r in answerable)), 1),
        gapsAdmittedPct=share(gaps, "abstained"),
        falseRefusalPct=share([r for r in rows if r["answerable"] == "yes"], "abstained"),
        answersWithUnsupportedNumbersPct=share(rows, "unsupportedNumbers"),
        answersWithBadCitePct=share(rows, "badCite"),
        medianWords=statistics.median(r["words"] for r in rows) if rows else None,
        medianSeconds=round(statistics.median(seconds), 1) if seconds else None,
        rows=rows)


def run(data: list[dict], server: str, model: str, out: Path, max_tokens: int, think: bool) -> None:
    done = {json.loads(line)["id"] for line in out.read_text().splitlines() if line.strip()} if out.exists() else set()
    with out.open("a", encoding="utf-8") as sink:
        for item in data:
            if item["id"] in done:
                continue
            body = dict(model=model, temperature=0, max_tokens=max_tokens,
                        messages=[dict(role="system", content=SYSTEM), dict(role="user", content=prompt(item))],
                        # Qwen3-family templates read this; other servers ignore it.
                        chat_template_kwargs=dict(enable_thinking=think))
            request = urllib.request.Request(server.rstrip("/") + "/v1/chat/completions", json.dumps(body).encode(),
                                             {"Content-Type": "application/json"})
            start = time.monotonic()
            with urllib.request.urlopen(request, timeout=900) as response:
                reply = json.load(response)
            answer = reply["choices"][0]["message"]["content"] or ""
            answer = re.sub(r"(?s)<think>.*?</think>", "", answer).strip()
            sink.write(json.dumps(dict(id=item["id"], answer=answer,
                                       seconds=round(time.monotonic() - start, 2))) + "\n")
            sink.flush()
            print(item["id"], f"{time.monotonic() - start:.1f}s", file=sys.stderr)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="command", required=True)
    for name in ("run", "score", "prompt"):
        p = sub.add_parser(name)
        p.add_argument("--data", type=Path, required=True)
    sub.choices["run"].add_argument("--server", default="http://127.0.0.1:8080")
    sub.choices["run"].add_argument("--model", default="reader")
    sub.choices["run"].add_argument("--out", type=Path, required=True)
    sub.choices["run"].add_argument("--max-tokens", type=int, default=400)
    sub.choices["run"].add_argument("--think", action="store_true", help="let a thinking model think first")
    sub.choices["score"].add_argument("--pred", type=Path, required=True)
    sub.choices["score"].add_argument("--rows", action="store_true", help="include every item's result")
    sub.choices["prompt"].add_argument("--id", required=True)
    a = ap.parse_args()
    data = load(a.data)
    if a.command == "run":
        run(data, a.server, a.model, a.out, a.max_tokens, a.think)
    elif a.command == "prompt":
        print(prompt(next(item for item in data if item["id"] == a.id)))
    else:
        result = score(data, {p["id"]: p for p in load(a.pred)})
        if not a.rows:
            result.pop("rows")
        print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
