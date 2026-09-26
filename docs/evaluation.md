# Evaluation

`benchmarks/questions-v2.json` freezes 18 questions aligned with the bundled Field Atlas Reference pack: three each for factual retrieval, explanation, comparison, synthesis, multi-step reasoning, and unanswerable requests. Freeze offline outputs before obtaining the named online baseline so baseline knowledge cannot influence the local run.

Each answer is scored for required evidence phrases, prohibited claims, citations, and appropriate abstention. This mechanical rubric is deliberately reproducible but cannot replace human review of correctness or prose quality.

The published 2026-09-24 free-router result is a historical v1 exploratory run,
not a score for the current source or the v2 prompt set. A comparable v2 result
requires a fresh complete device export from the exact build under review and a
named online baseline over the same prompts. No greater-than-50-percent claim is
established by the existing artifacts.

```sh
python3 benchmarks/score_results.py \
  --benchmark benchmarks/questions-v2.json \
  --offline build/evidence/fieldatlas-benchmark-DEVICE-TIMESTAMP.json \
  --baseline build/evidence/baseline-v2-PINNED-MODEL.json \
  --output build/evidence/paired-score.json
```

Report every response, category mean, overall mean, and the offline-to-baseline ratio. A zero baseline denominator produces `null`, never infinity. The scorer also marks the result as a mechanical measurement rather than an independent quality judgment.

## Capture a local-device run

1. Install the verified model and knowledge packs, prepare the model from Research, then open **More → Open device benchmark**.
2. Confirm the exact frozen prompt shown for the next row and tap **Run next**. The app advances only one question per tap. A stopped row retains its partial answer and evidence identifiers as `CANCELLED`.
3. Complete all 18 rows and use **Export benchmark evidence**. The exported `BenchmarkRun` includes the frozen prompt, raw answer, evidence chunk identifiers, measurements, installed artifact hashes, and a SHA-256 identity for the diagnostics snapshot.
4. Preserve that rich export as device evidence. The scorer accepts the Android export directly,
   rejects incomplete rows, and reads only each immutable question ID and answer.

The export records whether each pack was enabled and which model was active. That distinguishes
the model and knowledge configuration actually tested from assets that merely happened to be
installed on the phone.

## Capture the online comparison

Use the same v2 prompt file and a named model ID. Do not use the dynamic `openrouter/free` router
for a release comparison because its provider can change between rows. The runner records the
model, provider endpoint, temperature, timestamps, and prompt-set path beside the answers.

```sh
python3 scripts/run_online_baseline.py \
  --benchmark benchmarks/questions-v2.json \
  --model 'NAMED_PROVIDER/MODEL:free' \
  --output build/evidence/baseline-v2-PINNED-MODEL.json \
  --resume --timeout 300
```

The app never runs ordinary research and a benchmark row concurrently. Exports preserve raw evidence and are not, by themselves, a quality judgment.
