# Evaluation

`benchmarks/questions-v2.json` freezes 18 questions aligned with the bundled Field Atlas Reference pack: three each for factual retrieval, explanation, comparison, synthesis, multi-step reasoning, and unanswerable requests. Freeze offline outputs before obtaining the named online baseline so baseline knowledge cannot influence the local run.

Each answer is scored for required evidence phrases, prohibited claims, citations, and appropriate abstention. This mechanical rubric is deliberately reproducible but cannot replace human review of correctness or prose quality.

```sh
python3 benchmarks/score_results.py \
  --benchmark benchmarks/questions-v2.json \
  --offline build/evidence/offline-results.json \
  --baseline build/evidence/baseline-results.json \
  --output build/evidence/paired-score.json
```

Report every response, category mean, overall mean, and the offline-to-baseline ratio. A zero baseline denominator produces `null`, never infinity. The scorer also marks the result as a mechanical measurement rather than an independent quality judgment.

## Capture a local-device run

1. Install the verified model and knowledge packs, prepare the model from Research, then open **More → Open device benchmark**.
2. Confirm the exact frozen prompt shown for the next row and tap **Run next**. The app advances only one question per tap. A stopped row retains its partial answer and evidence identifiers as `CANCELLED`.
3. Complete all 18 rows and use **Export benchmark evidence**. The exported `BenchmarkRun` includes the frozen prompt, raw answer, evidence chunk identifiers, measurements, installed artifact hashes, and a SHA-256 identity for the diagnostics snapshot.
4. Preserve that rich export as device evidence. Derive the scorer-only file without editing answers:

```sh
python3 -c 'import json,sys; r=json.load(open(sys.argv[1])); json.dump({"schemaVersion":1,"results":[{"questionId":x["questionId"],"answer":x["answer"]} for x in r["results"]]},open(sys.argv[2],"w"),indent=2)' \
  build/evidence/device-run.json build/evidence/offline-results.json
```

The app never runs ordinary research and a benchmark row concurrently. Exports preserve raw evidence and are not, by themselves, a quality judgment.
