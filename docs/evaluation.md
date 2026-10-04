# Evaluation

## 1.3.0 review against the bounty bar (October 4)

The [24 frozen questions](#24-question-release-suite) were rerun unchanged through the 1.3.0
desktop pipeline with the same model (Qwen3.5 2B Q4_K_M, seed 17), the same five collections and
the same settings as the [1.2.0 run](#v120-24-question-suite-october-2). 15 answers are identical
to 1.2.0; the other 9 differ because 1.2.1 changed the strict-answer policy and some retrieval,
not because of 1.3.0 (1.3.0 does not change any library-research prompt). The reviewer is the
author's assistant, not an independent human; no online baseline was run.

Each answer was judged twice: **correct** (pass, partial or fail on facts, citations and honesty)
and **useful compared with internet search plus a frontier model** answering the same question
(1 = about as useful, ½ = useful but clearly weaker, 0 = not useful).

| Group | Correct | Useful vs. online frontier |
| --- | --- | --- |
| Travel (4) | 3 pass, 1 fail (London lists an Oxford museum) | 2 of 4 (½ each: no ranking; Tokyo has no addresses) |
| Explanation (4) | 2 pass, 2 partial | 3 of 4 |
| Comparison (4) | 2 pass, 2 partial | 3 of 4 |
| Synthesis (4) | 2 pass, 2 partial | 3 of 4 |
| Limits (4) | 4 pass (honest) | 1½ of 3 (location needs a phone; “today's study” is 0) |
| General (4) | 2 pass, 1 partial, 1 fail | 2½ of 4 |
| **Total** | **15 pass, 7 partial, 2 fail** | **15 of 23 = 65%** |

Answers about attached files and emergency questions are outside this suite; they were checked on
desktop and on a Redmi 13C and are reported in the [1.3.0 release notes](releases/current.md#verification).

What keeps answers below the bar, most impact first:

1. **Model explanations without sources carry most of the errors.** “Yeast lacks many metabolic
   pathways of eukaryotic cells” (yeast are eukaryotes), senescent cells “generally eliminated
   before becoming permanent” (they accumulate with age), a muddled battery example. These are 2B
   model limits; the 4B model did better on the attachment fixtures (34/35 vs 32/35) but is about
   ten times slower on a low-end phone.
2. **Relevant saved passages are often not found.** Autophagy, study types, yeast-to-human and
   the speed question retrieved no sources although the Encyclopedia covers these topics, so the
   model answered alone and unverified.
3. **Travel answers cannot rank and sometimes misfile places.** “Best” lists are unranked, Tokyo
   listings have no addresses, a zoo was offered as a museum and an Oxford museum as a London one.
   (Misfiled museums and missing locations are addressed [below](#after-the-retrieval-and-travel-fixes-october-4).)
4. **Some answerable questions are only partly answered.** “Open right now” lists recorded hours
   without using the phone's clock to say which places those hours mark as open (addressed below).
5. **One question was answered about the wrong topic** (“what can an offline app know” answered
   from a navigation-app article).

Report: `build/desktop-evaluation/v130-suite24/report.json` (ignored build directory).

### After the retrieval and travel fixes (October 4)

The same 24 questions were rerun with the same model, seed, collections and settings after
three changes: museum questions keep only places mapped as museums or galleries in the named
city, “open now” checks each place's recorded opening hours against the phone's clock, and
places without an address show coordinates. An “Explain X and distinguish …” question now
finds the topic's overview passage. 19 answers are identical to the first 1.3.0 run; the
5 that changed were graded again by the same reviewer:

| ID | Correct | Usefulness | What changed |
| --- | --- | --- | --- |
| travel-london | fail → partial | 0 → ½ | Four London museums with what each source says they focus on; no Oxford museum. The RAF Museum's directions are garbled (so is the source's), and “art and design” for V&A East is not in its listing. |
| synthesis-trip | partial → partial | +½ | A saved fully vegan stop (Kopps) and a real museum (Musical Instrument Museum, Tiergartenstraße 1). The Kopps menu is paraphrased confusingly. |
| limits-hours | pass → pass | ½ → 1 | Two of six places marked open at the clock's time, with closing times; places without readable hours are not guessed. |
| travel-tokyo | pass → pass | unchanged (½) | Coordinates for every listing (none has an address in the map data); still no ranking. |
| biology-autophagy | partial → partial | unchanged | Four relevant sources are now found, but the strict mixed-answer rule keeps a citation only on verbatim quotes, so the answer still shows as an unverified model explanation. The model's draft cited them correctly. |

| | Correct | Useful vs. online frontier |
| --- | --- | --- |
| First 1.3.0 run | 15 pass, 7 partial, 2 fail | 15 of 23 = 65% |
| After these fixes | 15 pass, 8 partial, 1 fail | 16½ of 23 = 72% |

The first review recorded usefulness per group, not per question; the changes above are the
reviewer's judgement of how much each answer improved. These questions were used to find the
problems the fixes address, so 72% is a development result on known questions, not a held-out
score, and the reviewer is still the author's assistant rather than an independent judge. The phone's clock is used as local time: for a
named city the answer says it assumed the phone is set to that city's time.

Reports: `build/desktop-evaluation/v130-final/report.json`, and `hours.json` for the open-now
answer after holiday days and open-ended closing times (“10:00-18:00+”) were made readable
(ignored build directory).

### Speed: thread count kept, smaller library prompts rejected (October 4)

On a Redmi 13C the model reads a prompt at about 17–18 tokens a second and writes about 4.5, so
the prompt is most of the wait (a 1,127-token prompt took 65.6 s to read). Raising inference from
four to six threads brought the first word of the same question from 94.7 s to 71.7 s and was
kept ([measurements](../scripts/patches/README.md#thread-count)).

Library prompts are not sized to the phone's reading speed, as file evidence is. Capping them was
tried on the 24 questions, simulating this phone's learned speed (desktop runs with
`FIELDATLAS_DESKTOP_PREFILL_TPS`):

| Target wait | Prompt tokens (24 questions) | Answers changed | Worse | Better |
| --- | --- | --- | --- | --- |
| none (current) | 21,697 | – | – | – |
| 60 s | 15,718 | 10 | 2 (trip loses its vegan stop; two-studies answer garbled) | 1 (vegetarian vs vegan) |
| 40 s | 11,611 | 13 | 5 (Jakarta: "no hotels listed"; trip gives a museum's address for an ice-cream shop; telomeres; autophagy; vegetarian) | 2 (senescence, battery) |

Passages are cut in rank order, and a question with several parts (a food stop and a museum)
ranks the second part's passages lower, so they go first. Neither cap was kept. Reports:
`build/desktop-evaluation/v130-budget/` and `v130-budget60/` (ignored build directory).

## Attachment reasoning feedback (October 3)

An offline 8 GB emulator test of the 1.2.0 release reported seven attachment questions: three
passes, two mixed and two failures. File attachments, source previews and history worked
without crashes. The failures: a citation pointed at Revision A while the claim came from
Revision B; a maintenance hold was described as watered "despite the hold"; a question for a
Wi-Fi password got unrelated travel details; and 21:30 was converted to 10:30 PM.

Synthetic files reproducing these cases are in
[`fixtures/attachment-reasoning/`](../fixtures/attachment-reasoning/README.md), and the desktop
runner now accepts text attachments (`--attachments`) through the app's own attachment path.
The swapped citation reproduced on the release code; the app numbers attachments in display
order, so the model, not the numbering, attached the wrong source.

Changes, each with tests in `AnswerChecksTest`:

- **Citation repair.** For evidence-only answers, a citation moves to another source only when
  every distinctive detail in its sentence (labelled identifiers such as "Revision B", part codes
  such as K-9, numbers) is unique to that one source and none is unique to the cited source. A
  colon does not split the sentence, and a list item carries the line that introduces its list,
  so "Revision B service interval: 400 hours [S2]", and the same value as a bullet under
  "Revision B:", keep their citation rather than moving to Revision A, which would make the
  wrong 400 look sourced.
- **Missing items.** A question for a password, phone number or email address that no file
  contains gets a one-sentence reply that the files don't mention it, without running the model.
  Presence is judged by the item's own noun: "free Wi-Fi in the lobby" does not count as a Wi-Fi
  password. Addresses, prices, times and other items can be written in too many ways to call
  them absent, so they are left to the model.
- **Time pairs.** A 12-hour time written beside a 24-hour time the source states is corrected
  when they disagree ("10:30 PM (21:30)" becomes "9:30 PM (21:30)").
- **Requested 12-hour times.** When the question asks for 12-hour time, the app writes the
  conversion beside every 24-hour time in the answer, outside quotations.
- **Exception note.** A decision question about files that contain exception wording
  ("exception", "hold", "unless", "override") ends with a note to check each decision against
  those exceptions. It does not judge the answer.
- **Instructions.** Answer only what was asked, under 80 words, with no notes or reminders;
  decide several items one by one (rule, then any exception or hold, then the decision); keep
  numbers and negations as written; do not substitute other details for a missing item. The
  attachment instructions are 1,392 bytes against 1,356 in 1.2.0, within the 4,096-token limit.

Desktop runs on the seven fixture questions (Qwen3.5 2B, seed 17, author review):

| Question | Release code | With these changes |
| --- | --- | --- |
| Helios serial 2345, compare revisions | fail: Revision B cited as [S1] | pass: cited [S2], K-9, M5, 600 h |
| Helios serial 1500 | pass | pass |
| Garden irrigation, East and West | pass | partial: right decisions, one false remark that both plots have holds |
| Wi-Fi password at Cedar Lodge | partial: long non-answer asking for more files | pass: one-line gap reply |
| Phone number for Cedar Lodge | pass | pass: one-line gap reply |
| Last shuttle, 12-hour format | pass | pass: 21:30 (9:30 PM) |
| Train 90 minutes late | fail: Station Hotel "can assist with transportation" | partial: right conclusion, wrong arrival time (22:00 for 22:40) |

Remaining weaknesses are the model's: time arithmetic and occasional false side remarks. The
checks above do not detect those. An independent review of these checks found four cases that
earlier versions got wrong: a wrong value under the right revision label was moved to the other
revision, both on one line and as a bullet under a "Revision B:" line; a stated address was
reported missing; and a mention of Wi-Fi without a password skipped the gap reply. Each is now a
regression test, and the checks were narrowed as described.
The reported failures are improved, not resolved: the model can still state wrong values. These are fixed development questions, not a held-out score.

On a Redmi 13C (Android 13, 6 GB, airplane mode, six collections) the same build answered the
Wi-Fi password question with the one-line gap reply in 0 s; the shuttle question with 21:30
(9:30 PM) [1] but a wrong aside ("one hour after" 21:10); the garden question with the right
per-plot reasons and an opening sentence saying both plots should be watered; and the Helios
question with the right final values and citations (K-9, M5, 600 h [2]) after a muddled middle
that attributed Revision A's values to "this revision" and called 2345 "greater than 2999". The
phone samples at the same temperature as the desktop with a random seed, so answers vary
between runs. The same session found that opening the file picker let MIUI kill the app; see
the [release audit](compliance/release-audit.md#v121-release-handoff-3-october-2026).

### Repeated runs

One run per question hides how much a 2B model varies, so the desktop runner now takes
`--seeds` and `--temperature`, and `fixtures/attachment-reasoning/score.py` scores each answer
with rules taken from the files (keyword checks for the reported mistakes, not proof of a
correct answer). Seven questions, five seeds each, temperature 0.3 as on the phone:

| Build | Passing answers | Garden (exception) | Shuttle (12-hour) |
| --- | --- | --- | --- |
| 1.2.0 instructions with the checks above, before the changes below | 27/35 | 2/5 | 1/5 |
| Shorter answers, per-item decisions, app-side 12-hour times | 32/35 | 3/5 | 5/5 |
| Same at temperature 0.1 | 33/35 | 3/5 | 5/5 |
| "Quote the exception first" instruction (not kept) | 30/35 | 1/5 | 4/5 |
| Qwen3.5 4B instead of 2B, same instructions | 34/35 | 4/5 | 5/5 |

The shipped build is the second row. The garden question is a model limit: the 2B model applied
the maintenance hold in three of five runs whatever the instructions. Qwen3.5 4B did better but
took a median 42 s per answer against 4 s for 2B on the same Mac, so it would take minutes on
the test phones; the bounty sets no parameter limit, but it does require usable speed. 1.2.1
keeps 2B and adds the exception note above.

Rerun on the final 1.2.1 build (versionCode 16), same phone and settings, one run each: the
Helios answer cited Revision B for K-9, M5 and 600 h, with one wrong aside that Revision A "also
specifies these components"; the garden answer said not to water East because of the hold and to
water West, with no contradictory sentence; the shuttle answer gave 21:30 (9:30 PM) [1] with no
wrong aside; the Wi-Fi question gave the gap reply in 0 s. One run per question does not show the
earlier slips are gone, only that they did not recur here.

## v1.2.0 24-question suite (October 2)

The [24 questions below](#24-question-release-suite) were run once, unchanged, through the
app's own research pipeline on desktop (Kotlin orchestrator, keyword retrieval, prompt
packing, venue routing and answer attribution) with Qwen3.5 2B Q4_K_M and five collections:
Encyclopedia, Travel guides, Travel places, Vegan places and Biology & Longevity. The
author reviewed each answer against its sources; this is not an independent human review.
Desktop has no location fix and no biology vector search, and a phone is several times
slower, so the times below are not phone measurements.

| Group | First run | After search fixes |
| --- | --- | --- |
| Travel (4) | 3 pass, 1 fail | 3 pass, 1 fail |
| Explanation (4) | 3 pass, 1 partial | 3 pass, 1 partial |
| Comparison (4) | 4 pass | 4 pass |
| Synthesis (4) | 1 pass, 3 fail | 3 pass, 1 partial |
| Limits (4) | 2 pass, 2 partial | 4 pass |
| General (4) | 2 pass, 2 fail | 2 pass, 1 partial, 1 fail |
| **Total** | **15 pass, 3 partial, 6 fail** | **19 pass, 3 partial, 2 fail** |

Clear passes went from 15/24 (62.5%) to 19/24 (79%); counting partials as half, from
16.5/24 (69%) to 20.5/24 (85%). The search fixes were made after reading the first run's
failures, so the second figure is a fixed-question development result, not unseen
validation. Each fix has a regression test in `SuiteFailureRegressionTest`:

- Instruction words ("summarize", "separate", "findings") no longer outrank the subject
  in keyword search; the rapamycin question now retrieves three relevant papers.
- Questions about today's news or the latest research get a fixed, honest reply without
  running the model.
- A food-stop question that also asks for a museum keeps the Eat listings, and a trip
  question naming a city also searches the place collections for fully vegan food.
- Papers that share only research vocabulary ("study", "outcomes") with the question are
  no longer treated as relevant.
- "Open right now" answers say plainly that live hours cannot be confirmed, then list the
  saved places with their recorded hours.

Remaining failures and partials:

| ID | Result | Problem |
| --- | --- | --- |
| travel-london | fail | Lists Oxford's Bate Collection as a London museum and conflates two others |
| general-offline | fail | Answers about GPS navigation apps instead of what an offline app can know |
| biology-telomeres | partial | Right distinction between marker and cause, with muddled supporting claims |
| synthesis-trip | partial | Correct saved vegan stops (Daizu, Chay Viet); the museum suggestion is wrong |
| general-energy | partial | Capacity and power defined correctly; the practical example has errors |

Desktop time per answer: 1.5–1.8 s for place lookups, 11–35 s for model answers.
Reports with every answer, packed source and model request are kept under the ignored
`build/desktop-evaluation/v120-suite24/` (first run), `v120-suite24-fix1/` and
`v120-fix2-subset/`. No online baseline was run against this suite, so it does not
establish the bounty's greater-than-50-percent comparison.

## Answer root-cause checks (October 1, local development)

Artifacts are under `build/reasoning-diagnosis/`; none are a held-out accuracy score.
Raw requests and responses were retained, including rejected experiments.

- Excerpt selection used exact tokens even though retrieval accepted word variants.
  A regression test reproduced selecting introductory history instead of the
  explanatory paragraph. Excerpt paragraph/sentence ranking now uses the existing
  retrieval term matching, keeping adjacent qualifications and original source text.
- The original reference pack had the buoyancy explanation but no semantic index.
  The new index retrieves it at approximately 0.79 cosine similarity. A later
  lexical gate wrongly discarded strong semantic matches; that gate now honors
  the pre-existing 0.78 threshold. Travel exclusions and explicit overview/comparison
  constraints remain. Indexes of at most 10,000 entries may be searched without
  matching a discovery-summary keyword; larger indexes retain scope routing.
- `semantic-app/report.json` exercises actual Kotlin retrieval and generation on
  eight existing reference questions plus the Berlin lookup. It retains the live
  information refusal and travel behavior. The buoyancy core explanation improves,
  but the model reverses the supplied boat/raft distinction. Other generated
  inaccuracies remain. Better retrieval is not sufficient to certify generation.

Three inference experiments were stopped early after failures, not completed
benchmarks: enabling thinking produced no final answer within 1,536 tokens on
all six completed thinking trials (`paired.json`); a shorter instruction prefix
reproduced the LAN contradiction (`prompt-paired.json`, three completed pairs);
a native 256-token reasoning budget still produced dimensional errors and
irrelevant claims (`bounded.json`, two completed trials). No candidate setting
was promoted. [Qwen's model card](https://huggingface.co/Qwen/Qwen3.5-2B) documents
the thinking switch and warns about thinking loops in this model size.

An additional model-only substitution recorded 18 responses: the same seven
automatic-reference requests and two previous complete-context controls, each
with seeds 17 and 29. The official LiquidAI LFM2.5-1.2B-Instruct Q6_K artifact
matches SHA-256 `c5e895c191a066f6b26a8f09f10e94cdb799e579216f87df61a7e27beacd9a2b`
at revision `8ed288026e23958ad9dfa92d53ed773a8eee7125`.
`alternative-model.json` preserves inputs, sampling settings and complete outputs.
The LAN answers improve, but database generalization, cell-division wording and
heat-transfer claims still fail review. It is **not** a validated default replacement,
and was not installed on the phone. Its [model card](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct)
recommends retrieval tasks but cautions against knowledge-intensive use.

For semantic diagnostics, `scripts/run_desktop_research.py --vector-fixture FILE`
accepts frozen query vectors, embedding metadata and a database SHA-256. The actual
Kotlin vector search/fusion/filtering still runs; source selection is not supplied
by the fixture. Encoder queries are replayed, not recomputed by this desktop adapter.
Unknown queries fall back to keywords. This is not full Android inference parity.

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

#### Claim-support development suite

`tools/claim_support_cases.json` contains eight project-authored, fictional CC0
cases: direct support, partial support, negation, conflicting sources, wrong-source
citations, stale evidence, absent evidence, and partially unsupported compound
claims. Each includes review criteria and labelled claim/citation probes. Labels
describe support by the supplied passages, not whether a claim is true in the world.
An in-range citation can still be irrelevant, contradicted, or insufficient.

```sh
python3 -m unittest discover -s scripts/tests -p 'test_claim_support.py'
python3 scripts/run_claim_support.py --output-dir build/claim-support/new-run
```

Use a new output directory for each run; existing directories are never overwritten.
`--prepare-only` builds the local fixtures without inference. `--answer-policy`
accepts `app`, `source-only`, or `source-partial` for the existing diagnostic modes.
No downloads or phone installation are performed. The tiny database is a test
fixture, not a distributable knowledge pack. Its example.invalid source URLs are
identifiers, not websites to fetch.

The runner reuses the desktop app pipeline with manually selected passages, saves
a fixture snapshot, and produces `report.json` plus `review.json`. Review is bound
to fixture/report SHA-256 hashes. The review sheet starts **unreviewed**, even for
completed runs. For each criterion, record pass/fail/uncertain with answer spans
and source IDs; inspect the actual prompt to detect any relevance/packing omissions.
Assess raw claim support, completeness, contradictions, and displayed attribution
separately. Probe citation indices use fixture passage order; model citation indices
use the actual packed prompt and may differ. Never substitute marker counts,
substring overlap, or model self-grading for semantic review. These known development
cases do not establish bounty accuracy or generalization to fresh questions.

Initial app-policy baseline: `build/claim-support/app-baseline/report.json`
(SHA-256 `82823eeb9e73f2b471a5daf24d4eb14e6b12ac82d8ab64b2b62036b2133d47e6`),
fixture snapshot SHA-256
`ffc64ab8642b39d12d11fb8227c743a063262e998cd5a48485b7845fb82c1349`.
All eight executions completed; agent inspection found the requested core facts
and uncertainty handling present. This is provisional inspection, not independent
human validation; the generated review sheet remains unreviewed. The supplied
short passages reached the answer prompts unchanged. The empty-evidence case
used the deterministic evidence-gap response after a search-planning call, so its
planner output must not be mistaken for a generated answer.

Observed limitations to retain in future comparisons:

- The parcel answer correctly compares 7 kg with 4 kg, then adds an unnecessary
  caveat that the sources do not explicitly compare their weights.
- Mixed-answer rendering removes paraphrase citations and leaves awkward
  wording such as "According to Source,". The conflicting-notes question follows
  the app's existing source-only route and retains correctly mapped citations.
- The lamp quotation lacks a citation on its own line; the sensor quotation uses
  an inline heading. Neither becomes a verified clickable quotation under the
  current mixed-answer format rules.
- The bridge answer refuses to infer today's opening status, but introduces a
  current-date assertion not supplied by the fixture. Correct abstention on the
  main question is not proof that every sentence is evidence-supported.

These minimal fixtures isolate basic behaviors. Passing their requested content
checks cannot erase the failures on longer real-source excerpts recorded in
`DATASETS.md`. Before promoting a change, compare both suites and have claim-level
review completed; do not tune only to these fictional examples.

Attribution follow-up: `build/claim-support/attribution-fix/report.json` reproduces
all eight raw baseline outputs exactly. Rendering now recognizes inline source
headings and links an unnumbered quotation only when its complete text has one
unique exact match in the available passages. Explicit incorrect citations are
never reassigned; ambiguous, fabricated, incomplete, and overflowing-number
references cannot create inferred links. The lamp and sensor cases now have
correct quotation links, and the parcel answer no longer leaves "According to
Source," after removing an unsupported paraphrase citation. These are attribution
and display fixes, not a model-accuracy improvement. Unit tests cover these cases.

The Biology/Travel regression at
`build/desktop-evaluation/final-local-review/report.json` retains Berlin's four
dated cited listings and the evidence-gap answer for a fictional current fact.
The mitosis/meiosis explanation remains unverified; its categorical statement
about recombination is not a general quality pass. The ordinary JVM suite, release
build, 34 script tests, 14 tools tests and 12 pack-format tests passed in this local
review. Device behavior remains untested because installation was deferred.

The initial larger-model comparison attempt could not complete its download. It selected
[bartowski's Qwen3.5 4B Q4_K_M](https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF),
pinned to revision `4168f45a16a1290d65a4ec0fa312ae917a4c15d6`, with expected
3,013,027,808 bytes and SHA-256
`13c16f426047e2de38cd075bdade4a7bcbc8c774384876f677740cda65f8a983`.
The direct download failed with HTTP/2 stream cancellation; the resumable-provider
attempt stalled at approximately 10 MB and was stopped. Neither incomplete file
was loaded or treated as checksum-verified. Provenance and a checksum-verifying
retry script remain in ignored `build/model-cache/`. No claim about 4B answer
quality, device performance or compatibility follows from this attempt. The app's
recommended 2B model, model URL and checksum remain unchanged.

### Completed 2B/4B desktop diagnostic

The follow-up download completed using bounded parallel byte ranges, followed by
the full-file size and SHA-256 checks above. The verified 4B GGUF remains private
under `build/model-cache/`; it is not a new app default or published download.
The same local server loaded it successfully. Its log reports unused additional
MTP tensors, so this run does not establish MTP support or Android compatibility.

Both models completed eight authored support fixtures and seven known
general-reference development questions. Fresh 2B baselines and 4B outputs live
under `build/claim-support/model-comparison-{2b,4b}/` and
`build/general-reference/evaluation/model-comparison-{2b,4b}/`. Each suite's
`model-comparison-controls.json` confirms matching app/tool hashes, database
hashes, answer policy, llama commit, and **identical actual requests for all 15
questions**, including supplied passages and sampling settings. The server binary
was unchanged (`3c8deb8c4971ed4fa10577950c271dd8b7148b20df214baa80d5c3a6c03cbc0d`).
The plan was frozen before 4B generation in
`build/model-cache/comparison-plan.json`.

Qualitative agent review, not independent human scoring or an accuracy benchmark:

- Heat transfer: 4B supplies all three examples and the correct vacuum mechanism;
  2B omitted a radiation example and contrasted photons with particles.
- Local networks: 4B still introduces confusing overlay/address-resolution
  language and an unnecessary gateway condition. This is not a clean answer.
- Agriculture: 4B removes the baseline's fabricated quotation and explicitly
  rejects universal benefit, but still makes broad historical generalizations.
- Current earthquake/road closures: both correctly return the same evidence gap.
- Water cycle: 4B explicitly explains the release of latent heat in condensation,
  improving the baseline's explanation.
- Database/spreadsheet: 4B retains the misleading emphasis on semi-structured or
  unstructured data as the defining database category. The failure is unresolved.
- Genes/environment: 4B drops the baseline's unrelated CO2/heart-attack material.
  Its percentages appear in the supplied passage, but its framing of heritability
  still needs care; source agreement is not proof that an explanation is complete
  or scientifically precise.
- Small support fixtures: both retain the main facts, conflicts and unknowns.
  4B is often more repetitive; its sensor answer adds "only" beyond what the note
  establishes. The lamp/sensor quotation links seen with 2B are not reproduced
  in 4B's visible format, while 4B provides a linked historical bridge quotation.

In these single desktop runs, model-call time for the six generated research
answers was approximately 6–8 seconds with 2B versus 27–46 seconds with 4B. These
are observations, not controlled device-speed benchmarks: there were no repeated
timing trials, thermal controls, or Android memory/battery measurements. The
no-evidence case's model-call time is planning, not a generated final answer.

Decision: do not promote 4B or the candidate reference pack. Larger capacity helps
some cases but does not resolve evidence relevance, overgeneralization or output
format compliance. Keep the default model unchanged and prioritize evidence
selection and claim-level review before any model-default decision. Phone testing
and installation remain explicitly deferred. All owned download/inference
processes exited after this comparison.

The claim-support runner now accepts `--model /absolute/path/to/model.gguf` for
reproducible alternatives; omitting it preserves the existing default. Its new
argument-forwarding test passes with the full 35-test script suite. The 14
knowledge-builder tests also pass. No app production code changed in this
comparison, and no new release was published.

### Rejected grounding-policy candidates (October 1)

Two additional opt-in desktop policies were tested after the difference-question
retrieval fix, with 2B and the same paragraph-pack database. They are **diagnostic
only**, not production instructions:

- `--answer-policy evidence-first`: request exact quotations before a short
  source-bounded explanation. Reports:
  `build/general-reference/evaluation/evidence-first/report.json`.
- `--answer-policy bounded-summary`: remove the quotation task and request a
  short explanation of supported parts plus explicit gaps. Reports:
  `build/general-reference/evaluation/bounded-summary/report.json`.

Both ran all seven known development questions. Checks confirmed identical
production-code hashes, model/database identities, generation settings, and
packed evidence and question suffixes against `qualified-difference-subjects`.
Only the mixed-answer instructions changed; source-only requests, planning and
model-only calls were untouched. Unit tests protect those boundaries.

Neither candidate is acceptable for promotion. Evidence-first correctly notices
the missing spreadsheet definition but alters quotations with ellipses or changed
wording, retains irrelevant genetics material and adds questionable agriculture
claims. The existing exact-quote attribution gate keeps those altered quotations
uncited; it does not validate the remaining explanation. Bounded-summary produces
a direct contradiction in the heat answer: "All three mechanisms can occur in a
vacuum". It also supplies spreadsheet claims despite the missing evidence rather
than identifying that gap. Shorter answers and more source-related wording are
not correctness improvements by themselves.

The app's answer policies, model and attribution behavior remain unchanged. These
negative results do not complete the grounding work. A future claim-checking
stage needs its own support/contradiction/gap tests; a matching citation number or
quoted substring must not be treated as verification of generated paraphrases.
No new app release or phone installation follows from this experiment.

### Experimental claim-support checker

`scripts/check_claim_support.py` tests a separate local inference call for one
claim and its cited passages. It does not generate an answer, split an answer
into claims, rewrite claims, or run inside the Android app. Expected labels and
review criteria never enter inference requests. Default fixtures combine the
13 probes in `tools/claim_support_cases.json` with 13 additional probes in
`tools/claim_checker_cases.json` (scope, conditions, uncertainty, contradictions,
conflicting reports and source-embedded instructions).

```sh
python3 scripts/check_claim_support.py \
  --output-dir build/claim-checker/new-run
```

Use a new output directory for every run. `--fixtures` accepts repeatable authored
fixture files and `--model` selects a local GGUF without changing the app default.
Reports retain exact requests, raw responses, fixture copies/hashes, model and
server hashes, and partial results on failure. The runner owns a loopback-only
server and stops only that process. A busy port is rejected; `--port` can choose
another port without reusing a running server.

The checker rejects invalid source numbers before inference. Its response parser
accepts only a strict three-way JSON verdict: supported, contradicted or
insufficient. Supported/contradicted verdicts require exact quotations from the
specified passages. Malformed JSON, duplicate keys, fabricated quotations and
unmapped source IDs become invalid responses, never support. **This validates
format and attribution, not entailment.** A model can still quote a real passage
while drawing an unsupported conclusion. Such failures are counted explicitly.

Two runs use the original 26 authored probes at
`build/claim-checker/{2b,4b}-first-pass/report.json`. Another 12 probes were frozen
in `build/claim-checker/packed-regressions.json` using the exact excerpts from six
known research requests, with the originating report hash retained. Their runs
are at `build/claim-checker/{2b,4b}-packed-regressions/report.json`. The source
passages remain private build artifacts, not a new published dataset. These are
development checks, not independent validation or a score for whole answers.

Completed comparison (same requests, labels, fixture hashes and checker code for
both models; no prompt tuning between models):

| Model | Checks | Full-label matches | False support | Missed supported claims | Invalid responses |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2B | 38 | 24 | 5 | 3 | 5 |
| 4B | 38 | 33 | 0 | 1 | 2 |

Each total includes one deterministic invalid-citation check, rather than an
inference call. "False support" means an unsupported or contradicted claim was
accepted after response validation. Invalid responses fail closed and count as
misses when the claim was supported; the columns therefore overlap. Full-label
matches also distinguish contradiction from insufficient evidence, which neither
model handles perfectly.

2B falsely accepts an undated capacity as the latest value, an unmeasured pump
as silent, a subtype property as universal, thermal conduction/convection through
a perfect vacuum, and a primary energy source as the only possible source.
The first three occur despite exact, correctly attributed quotations. It catches
the simplified fictional mechanism contradiction but fails the comparable
real-excerpt case, so short-fixture success does not generalize to longer context.

4B produces no accepted false support in these cases, but a supported database
claim fails because its quotation introduces an ellipsis that is not in the
passage. Its subtype-gap response also violates the response contract. This is
promising development evidence, not verification reliability: the cases were
authored/reviewed by the agent, the actual research requests are already-known
regressions, and neither claim extraction, answer coverage/relevance, independent
holdouts nor phone performance was tested. A cited source can itself be wrong.

Decision: retain the checker as an experimental development tool. Do not enable
automatic answer acceptance, rewriting, deletion or a "verified" badge. The app
continues using its existing 2B model and attribution behavior. All owned local
inference processes stopped after the comparison. The 41 script tests pass,
including strict verdict parsing and false-support accounting; no new app build,
publication or installation was needed for this test-only work.

### Frozen inference controls (October 1)

After consulting [Qwen's model card](https://huggingface.co/Qwen/Qwen3.5-2B#best-practices)
and the [Sufficient Context study](https://arxiv.org/abs/2411.06037), two known failures
(database/spreadsheet and algorithm/program) were frozen before generation. This is
diagnosis, not an unseen accuracy benchmark. `scripts/run_inference_controls.py`
replays exact final requests, retaining complete raw responses, input/model/server
hashes, token budgets and seeds. It never promotes a model or assigns an accuracy score.

The first matrix has three conditions (model-only, automatic retrieval, manually
selected passages still passed through production filtering/packing), two samplers,
and seeds 17/29/43: 36 generations. The existing settings are temperature 0.3,
top-k 40, top-p .95, min-p .05, repetition penalty 1.10. The alternative uses Qwen's
non-thinking text sampling values: temperature 1, top-k 20, top-p 1, min-p 0,
presence penalty 2 and repetition penalty 1. The existing 128-token penalty window
remains; this is an adaptation to llama.cpp, not framework-independent parity.
Messages, output limits, thinking configuration and evidence are identical between
samplers for each condition. Model-only uses the app's model-only policy, so it is
not a pure evidence-removal ablation with otherwise identical instructions.

Preflight exposed a selection limitation: the database's non-relational classification
and spreadsheet use cases disappeared even from manually selected candidates, because
the overview relevance gate required a defining sentence. An additional frozen
control bypassed filtering and excerpt selection, inserting the four complete original
database/spreadsheet chunks within the existing evidence budget. The two algorithm
chunks were already complete. This added 12 generations; its six algorithm requests
and answers duplicate the previous manual-context condition and are not independent
new cases. Total: 48 recorded generations on **two** known questions.

Agent review observations (not independent human scoring):

- Both sampling configurations continued producing materially wrong claims. Automatic
  database answers still generalized relational structures to databases overall.
- Complete database context helped some answers acknowledge advanced spreadsheet uses
  or avoid the blanket multiple-table definition, but did not reliably eliminate
  overgeneralization. Existing sampler seed 29 still described manual recalculation
  despite supplied text explaining that manual recalculation is unnecessary.
- Manual algorithm context did not cure source interpretation: existing sampler seed
  17 reversed the termination distinction; recommended sampler seed 29 invented named
  references and attributed absent statements to S1. Other seeds also contained errors.
- All 48 responses ended with `stop`, not output-budget truncation. No latency conclusion:
  this was desktop inference, with local build activity during part of the run.
- **Decision:** no sampling or model change in the app. Improve contextual selection
  and source quality, but do not assume these alone fix generation. Automatic judging
  remains disabled. The prior release gate is not passed.

Private reproducibility artifacts: `build/release-controls/plan.json`,
`selection.json`, `comparison/report.json`, `complete-context-plan.json`,
`complete-context-requests.json`, and `complete-comparison/report.json`.
The complete-context input is explicitly marked FROZEN_REQUEST, not a successful
app run. Its preparation retains original chunk text and records database/selection
hashes. No competitor data or remotely generated answer was used.

### Coverage follow-up and release gate (October 1)

Coverage work uses the seven existing general-reference queries plus all four
`tools/answer_review_questions.json` queries. These are used development cases,
not an unseen benchmark. The 0.3.0 candidate adds two articles without refreshing
the original 24 revisions. An initial coverage-only run and a separate title-selection
run isolate data changes from retrieval changes; reports are recorded in DATASETS.md.

Mixed-answer UI now puts original retrieved passage previews before the generated
explanation. Previews render plain source text, retain full context through the source
viewer, and explicitly do not certify the explanation. The first two appear initially;
the remaining passages are expandable. For the supported narrow comparison grammar,
a missing subject word produces a gap notice based on passage text, not the title.
Absence is a lexical warning; presence never earns a complete-coverage badge.

The candidate cannot be promoted until all of these release checks pass:

- Review every complete answer, not selected claims: no material contradictions,
  invented evidence, or unjustified universal claims on the fixed development set.
- Judge question coverage separately: missing comparisons or requested examples
  fail usefulness even if the answer honestly states a gap.
- Review support against actual packed excerpts, and factual correctness separately.
  Exact quotation and a model checker cannot certify either alone.
- Preserve the missing-current-information refusal and existing travel behavior.
- Pass unit tests, release build, and on-device source navigation, accessibility,
  offline performance and cancellation checks. Device work remains deferred.
- Before public quality claims, freeze a separate holdout and obtain independent
  review. Do not relabel repeatedly tuned development questions as a holdout.

Current gate is **NOT PASSED**: database generalization and the algorithm contradiction
remain; device UI tests are not executed. The candidate pack stays development-only.

Verification: the three existing biology/travel/missing-source regression answers
are byte-identical to their previous run. Two offline candidate builds produced
byte-identical `.fapack` files. Script tests (46) and reference-builder tests (14)
passed. The final full JVM suite and release build passed; the Android UI test
compiled but was not run on a device. Local evaluation servers were stopped.

### Fresh complete-answer check (October 1)

`tools/answer_review_questions.json` froze four new development questions before
generation: ecosystem roles, algorithms versus programs, atomic structure/mass,
and p-value limits. They were not selected or changed after seeing outputs.
They are now used development cases, not an untouched holdout. The unchanged 2B
app pipeline generated their answers against the same paragraph reference pilot:
`build/answer-review/fresh-2b/report.json`.

`scripts/prepare_answer_review.py` prepares a review sheet from completed reports.
It preserves every non-whitespace character in answer blocks with exact offsets,
keeps adjacent bullets together, and automatically excludes only the two known
app display headings from factual review. **It is not an atomic-claim extractor.**
A block can contain several assertions, all of which require support. This avoids
selecting only convenient claims but does not solve semantic decomposition.

For export, every block requires a label and reason, and every answer requires
a separate question-coverage review. `--source-report` checks the original hash,
question, answer and packed passages; changed or dropped text is rejected. Empty
evidence is not silently counted as a verified case. Labels are agent judgements,
not independent human review. The atom answer's provisional supported label is
based on agreement with the supplied excerpts, including their mass wording;
its scientific precision and loose orbit/cloud language need separate review.

The reviewed answers are at `build/answer-review/fresh-2b/review.json`. Their
labels were assigned before checker inference; exported probes are at
`build/answer-review/fresh-2b/checks.json`. Re-export with origin validation at
`bound-checks.json` is byte-identical. Checker inputs contain the complete answer
blocks and all actually packed passages, but no gold labels or review reasons.
This tests whole-block support, not whether each generated citation is correctly
placed. The 4B checker output is at
`build/answer-review/fresh-4b-checker/report.json`.

Results: one unsupported block accepted, three invalid responses, and the one
provisionally supported block missed. In particular:

- The p-value answer adds general claims not established by the single packed
  coin-toss example. 4B nevertheless marks the whole block supported and supplies
  valid quotations. Exact attribution therefore does not establish full support.
- The algorithm answer says an implementation description gives exact machine
  states. The passage explicitly says it does not. The raw checker verdict still
  says supported; only its malformed quotation causes rejection by the parser.
- The ecosystem and atom blocks also fail quote validation, through changed
  wording, ellipses or incorrect source mapping. These are not successful semantic
  detections and must not be scored as such.

Decision: the earlier isolated-claim results do **not** justify automatic checking
of complete answers. Keep the checker experimental, with no verified badge,
rewriting, claim removal, Android integration or model-default change. Supported
sentences inside rejected blocks have not been separately certified. The 46
script tests pass, including lossless block coverage and origin binding; this
does not constitute an answer-quality pass. All owned inference processes exited.
Phone installation/performance testing remains deferred.

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

#### Manual-evidence diagnostic

To isolate retrieval from downstream generation, pass
`--manual-selection build/path/selection.json` with exactly one `--database`.
The JSON object maps each exact requested question to an ordered array of chunk
IDs from that database. Empty arrays explicitly supply no evidence. The launcher
rejects missing or duplicate IDs and copies the original passage and metadata
without rewriting them. The report records the selection hash and evidence mode.

This bypasses automatic retrieval only: the app's relevance gate, result limit,
excerpt selection, prompt budget, generation, and answer attribution still apply.
Inspect the recorded inference request, not just the supplied passage list, to
verify what reached the model. Manual selection is not guaranteed sufficient or
unbiased and is not a retrieval-quality score. Keep questions, model, database,
app source and inference settings identical in the automatic control; save each
report in its own directory. Run Gradle evaluations and unit tests sequentially
because they share Gradle's test-result files.

For an instruction-only comparison, add `--answer-policy source-only`. This
test-only switch substitutes the existing `PromptBuilder` strict policy for the
instructions before the evidence disclaimer. The complete packed evidence,
question, system message and generation settings remain unchanged. Planning and
model-only calls are not modified. Default `--answer-policy app` leaves all
prompts unchanged. The report records the selected policy and actual requests.
`--answer-policy source-partial` is a separate test-only candidate requiring
supported parts to be answered and unsupported parts to be identified individually.
It does not change evidence selection or supply additional facts. Treat results
on the development questions used to design it as exploratory, not held-out validation.

This does **not** switch the production answer-attribution mode: a question that
normally uses mixed answers still goes through mixed-answer display processing.
Review `calls[].rawAnswer` for generation and citation support, and `visibleAnswer`
separately for the UI result. A source number does not establish that the cited
passage supports the claim. This diagnostic is not a shippable policy/UI change.

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

## 24-question release suite

These questions were frozen before the v1.2.0 run [reported above](#v120-24-question-suite-october-2). They are not yet the contents of `benchmarks/questions.json`, which still holds the earlier questions; the device counter measures recorded questions, not correct answers, and a cancelled row can also be recorded.

### Purpose

Compare releases on useful offline answers, supporting evidence, honesty about missing/current information, and phone performance. A completed run is not a quality score or proof of the bounty's greater-than-50-percent bar.

Freeze prompts, rubric, model/pack hashes, and settings before evaluating. Run the same prompts against a named online baseline, keeping its internet access and tool settings explicit. Preserve all answers, including failures. Do not compare scores from different suites as if they were the same test.

### Questions

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

### Evaluation rules

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

Next: write the per-question rubric down, run a named online baseline on the same questions, and keep a separate holdout set before replacing the contents of `benchmarks/questions.json`.

The maintained filename stays `benchmarks/questions.json`. Identify future changes by Git commit and the file's SHA-256, not by creating numbered files. Android generates its bundled copy from this single source during the build.
