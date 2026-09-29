# Evaluation

`benchmarks/questions.json` freezes 18 historical questions aligned with the former Field Atlas Reference pack: three each for factual retrieval, explanation, comparison, synthesis, multi-step reasoning, and unanswerable requests. That pack is no longer bundled, so a fresh run with only the current model or with the optional biology pack is a different test configuration. Do not compare its score to Reference-pack results as if the evidence were unchanged. Freeze offline outputs before obtaining the named online baseline so baseline knowledge cannot influence the local run.

Each answer is scored for required evidence phrases, prohibited claims, citations, and appropriate abstention. This mechanical rubric is deliberately reproducible but cannot replace human review of correctness or prose quality.

The published 2026-09-24 free-router result is a historical v1 exploratory run,
not a score for the current source or current prompt set. A comparable current result
requires a fresh complete device export from the exact build under review and a
named online baseline over the same prompts. No greater-than-50-percent claim is
established by the existing artifacts.

```sh
python3 benchmarks/score_results.py \
  --benchmark benchmarks/questions.json \
  --offline build/evidence/fieldatlas-benchmark-DEVICE-TIMESTAMP.json \
  --baseline build/evidence/baseline.json \
  --output build/evidence/paired-score.json
```

Report every response, category mean, overall mean, and the offline-to-baseline ratio. A zero baseline denominator produces `null`, never infinity. The scorer also marks the result as a mechanical measurement rather than an independent quality judgment.

## Capture a local-device run

### Current research-quality check (2026-09-29)

The desktop check uses the saved Qwen3.5 2B model with Biology and Travel,
covering a history overview, biology comparison, Berlin vegan listings, and
an unsupported source-specific question. Its current local output is
`build/desktop-evaluation/report.json`; completion is not an accuracy score.

Regression fixes cover possessive overview questions, source-request boilerplate,
and author names in structured scientific headers being mistaken for topical
evidence. Original source text and citation attribution are preserved. The
relevance checks remain heuristics, not semantic verification.
When a source-only request has no matching evidence and no attachments, the app
now returns a short evidence-gap response without generating a model answer.

The history output still fails manual quality review after irrelevant passages
are removed: unverified model text contains internal inconsistencies and exceeds
the requested length. Do not claim that every answer is research-quality or
that these checks establish release/bounty accuracy. Desktop keyword retrieval
does not validate device inference, vector retrieval, or all languages.

### Desktop development loop (no phone)

Use `scripts/run_desktop_research.py` to run the current app's Kotlin orchestrator,
multi-pack keyword retrieval, prompt builder, venue routing, and answer attribution
on the host JVM. This is not the older Python pipeline replica in `tools/e2e_test.py`
or `tools/e2e_battery.py`; those legacy scripts do not represent the current app.

Build the pinned local inference server once (CMake and a C++ toolchain required):

```sh
cmake -S third_party/llama.cpp -B build/desktop-llama \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=ON -DLLAMA_CURL=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build build/desktop-llama --target llama-server -j 4
python3 scripts/run_desktop_research.py \
  --question 'Compare mitosis and meiosis. Explain how their different outcomes support growth and sexual reproduction.' \
  --question 'Tell me the best vegan restaurants in Berlin'
```

Defaults use the existing saved GGUF, extracted Biology database in
`build/biology-retrieval-audit/`, and Travel database in
`build/packs/travel/wikivoyage-places/`. On another machine pass `--model`,
`--server`, and one `--database /path/to/content.sqlite` per enabled collection.
Use `--model-only` for the no-evidence comparison. Missing files fail explicitly;
this runner does not download models/packs or alter phone data.

The first Gradle run may fetch desktop SQLite JNI jars; subsequent runs can use
`--offline-gradle`. An Android SDK is still required to compile the existing project,
but no emulator, connected device, APK build, installation, or Android Studio is needed.
Inference binds to `127.0.0.1` only. The launcher verifies the loaded model path
and shuts down its own server afterward.

One current report is maintained at `build/desktop-evaluation/report.json`, with
server logs and configuration alongside it. It includes artifact hashes, source
identity, candidate passages, packed sources, complete inference requests, raw
model responses, and the visible answer. Reports are private/ignored by Git.
Use a separate `--output build/.../report.json` only when deliberately retaining a
comparison. A completed run is marked **COMPLETED_UNSCORED**, never an accuracy pass.

Limits: this initial runner is **keyword-only**, without the pack's vector encoder.
It uses the same app pipeline code but a desktop inference adapter, fixed answer
seed 17, and non-streaming output. It does not reproduce Android's native
repetition-loop stopping. First-token and token-count fields from the orchestrator
are therefore not meaningful desktop performance measurements; use server usage/
timings for desktop diagnostics only. It does not validate attachments/OCR, UI,
phone memory, battery, network isolation, or phone latency. Final phone checks
remain required. Model self-grading is not used as proof of correctness.

### Overview-relevance development checks

The current overview filter is an English precision heuristic, not a semantic
verifier. For short definition requests and unambiguous single-word comparisons,
it favors sentences directly explaining the subject rather than mentions inside
experimental descriptions. It handles basic inflections, articles, appositives,
and acronyms; source-specific requests keep the ordinary relevance path. Unknown
language forms fall back to lexical matching using a weak English-marker heuristic.
This can still reject useful multilingual/indirect explanations or admit terse
incidental claims. It must not be described as understanding or verifying sources.

Planner expansion no longer rescues a detailed question from only one shared
content word. Auxiliary verbs `have`, `has`, and `had` are excluded from FTS terms,
including the reproduced `have` prefix matching the unrelated name “Haven”.

The frozen six-question development comparison is retained privately at
`build/desktop-evaluation/before/report.json` and
`build/desktop-evaluation/after/report.json`. It covers mitosis/meiosis, cellular
senescence, batteries/capacitors, hemispheric seasons, Berlin vegan listings, and
a fictional-city lookup. These are known development cases, not an unseen benchmark.
The canonical 18-question benchmark is unchanged. Inspect raw responses as well
as displayed answers; absence of citation or prompt-format errors is not a factual
accuracy score. See the maintained release audit for outcomes and remaining faults.

### Android evidence capture

1. Install the verified model and any knowledge packs being evaluated, prepare the model from Research, then open **More → Open device benchmark**.
2. Confirm the exact frozen prompt shown for the next row and tap **Run next**. The app advances only one question per tap. A stopped row retains its partial answer and evidence identifiers as `CANCELLED`.
3. Complete all 18 rows and use **Export benchmark evidence**. The exported `BenchmarkRun` includes the frozen prompt, user-visible answer, evidence chunk identifiers, measurements, installed artifact hashes, and a SHA-256 identity for the diagnostics snapshot. Mixed-answer output includes attribution labels and removes unsupported citation markers; it is not the raw model token stream. The unmapped-citation measurement also flags discarded citation markers.
4. Preserve that rich export as device evidence. The scorer accepts the Android export directly,
   rejects incomplete rows, and reads only each immutable question ID and answer.

The export records whether each pack was enabled and which model was active. That distinguishes
the model and knowledge configuration actually tested from assets that merely happened to be
installed on the phone.

## Capture the online comparison

Use the same frozen prompt file and a named model ID. Do not use the dynamic `openrouter/free` router
for a release comparison because its provider can change between rows. The runner records the
model, provider endpoint, temperature, timestamps, and prompt-set path beside the answers.

```sh
python3 scripts/run_online_baseline.py \
  --benchmark benchmarks/questions.json \
  --model 'NAMED_PROVIDER/MODEL:free' \
  --output build/evidence/baseline.json \
  --resume --timeout 300
```

The app never runs ordinary research and a benchmark row concurrently. Exports preserve evidence identifiers and are not, by themselves, a quality judgment. In mixed answers, source links require an exact quotation found in the corresponding saved passage. This checks attribution, not truth, relevance, completeness, or preservation of every qualification. Attachment and source-only answer paths retain their separate policies and do not use this quotation guard.

## Proposed replacement questions — for review

This is a draft, not the active benchmark and not a scored result. The existing results remain historical; the current questions are unchanged. The current device counter measures recorded questions, not correct answers; a cancelled row can also be recorded.

### Purpose

Compare releases on useful offline answers, supporting evidence, honesty about missing/current information, and phone performance. A completed run is not a quality score or proof of the bounty's greater-than-50-percent bar.

Freeze prompts, rubric, model/pack hashes, and settings before evaluating. Run the same prompts against a named online baseline, keeping its internet access and tool settings explicit. Preserve all answers, including failures. Do not compare scores from different suites as if they were the same test.

### Proposed 24-question release suite

Four questions per group. Berlin, Tokyo, and mitosis/meiosis are known development regressions, not unseen validation examples. The other questions must not be selected or removed based on whether this build happens to answer them well.

| ID | Question |
| --- | --- |
| travel-berlin | Tell me the best vegan restaurants in Berlin. |
| travel-tokyo | Tell me the best vegan restaurants in Tokyo. |
| travel-jakarta | Which hotels are listed in Jakarta? Include locations where the saved sources provide them. |
| travel-london | Which museums are listed in London, and what does each focus on? |
| biology-division | Compare mitosis and meiosis. Explain how their different outcomes support growth and sexual reproduction. |
| biology-senescence | Explain cellular senescence and how it differs from a cell dying. |
| biology-autophagy | Explain autophagy and distinguish a proposed role in aging from evidence that an intervention extends human life. |
| biology-telomeres | What can telomere length tell us about aging, and what can it not establish? |
| comparison-study-types | Compare observational studies and randomized trials when evaluating a longevity claim. |
| comparison-species | Why might a result in yeast or mice not predict the same result in humans? |
| comparison-diet | Is a vegetarian restaurant necessarily suitable for a vegan traveller? Explain using the saved listings if relevant. |
| comparison-evidence | How should conflicting studies be compared before deciding which conclusion is better supported? |
| synthesis-rapamycin | Summarize what saved sources say about rapamycin and aging. Separate reported findings from remaining uncertainty. |
| synthesis-cell-aging | How might cellular senescence and inflammation be connected in aging? Distinguish observations from causal claims. |
| synthesis-trip | Using saved Berlin listings, suggest a vegan food stop and a museum visit. Explain what cannot be verified offline; do not invent travel times. |
| synthesis-research | Two studies report different lifespan outcomes. What study details would you need before trying to combine their conclusions? |
| limits-hours | Which of the saved Berlin vegan restaurants is open right now? |
| limits-news | What important longevity study was published today? |
| limits-location | What is the nearest vegan restaurant to my current location? |
| limits-missing-place | Recommend a vegan restaurant in Fictional Test City ZXQ-417. |
| general-seasons | Why do Earth's hemispheres have opposite seasons? |
| general-energy | Compare battery capacity and power with a practical example. |
| general-offline | What can an offline app know from saved files, and what needs fresh information? |
| general-performance | Why does a faster processor not always make an app faster? |

### Evaluation rules to finalize before activation

- Record model, enabled collections, artifact hashes, device RAM/OS, and app commit/APK hash.
- Judge correctness, task usefulness, citation support, and uncertainty separately. A citation counts only if its actual passage supports the associated claim.
- A general question can be answered correctly without a citation if clearly labelled model-generated. A request specifically asking for saved-source findings must not fabricate evidence.
- Missing coverage is a product limitation even when the app honestly abstains; give credit for honesty without awarding full usefulness credit.
- An offline listing date is not proof a venue remains open. A page revision date is not necessarily the date its listing was checked.
- Static-list lookup and generated answers must have separate latency summaries. Record first-token time, total time, cancellation/failure, and peak memory where measurable.
- Human review is needed for paraphrases, contradictions, and source support. The existing exact-phrase scorer must not be presented as a sufficient judge of this new suite.
- Keep a small, separately frozen holdout set that is not used to tune retrieval. Do not claim unseen generalization from the known regression questions.

### Separate functional checks

Camera/OCR, image preview, PDF/text attachments, download/import recovery, and persistence need repeatable fixtures and UI checks. They are not covered merely by completing these 24 text questions. Run them as a separate acceptance checklist rather than silently inflating the text benchmark count.

### Suggested public wording

“24 questions recorded” describes progress. Show quality scores only after scoring, with the suite version and method visible. A shorter smoke-test subset can be used during development; the full frozen suite is for release comparisons.

Next: review these prompts and define the per-question rubric before replacing the contents of `benchmarks/questions.json`.

The maintained filename stays `benchmarks/questions.json`. Identify future changes by Git commit and the file's SHA-256, not by creating numbered files. Android generates its bundled copy from this single source during the build.
