# Release audit

Updated 29 September 2026. **Owner approved a public prerelease, not a stable-readiness claim.** Remaining quality and verification gaps below still apply. This is the single maintained readiness document. Historical release evidence remains in Git history and published release tags; older local audit drafts are archived under ignored `build/private/archive/repo-cleanup/`.

## October 1 readiness follow-up — not released

### Answer diagnosis and retrieval correction

Found and fixed two code defects: prompt excerpt selection used exact words while
retrieval accepted word variants, and a later explanation filter overrode strong
semantic matches. The existing 0.78 semantic threshold is retained; travel exclusions
and explicit overview/comparison rules remain. Small vector indexes (at most 10,000
entries) no longer require a keyword in their discovery summary before being searched.
Regression tests cover word variants, adjacent qualifications, weak/invalid scores,
travel exclusions and the small-index routing boundary.

Built a local Everyday reference 0.3.0 semantic preview from unchanged 0.2.0 passages.
The archive is 42,108,940 bytes, SHA-256
`c0142831da0017d6755b6f52c1648212c3c66b2925150abc1204e53d4a598a4a`.
Imported through Library; 0.2.0 is retained but disabled to avoid duplicate results.
Travel and Biology remain enabled. No model was replaced or downloaded on the phone.

The actual Kotlin pipeline now selects “Boat — Buoyancy”, but the generated desktop
answer still reverses a separate boat/raft distinction. Shorter instructions,
unbounded/bounded thinking and an alternate model did not establish full correctness.
See [evaluation](../evaluation.md#answer-root-cause-checks-october-1-local-development)
for recorded controls and rejection reasons. None of those inference settings or
model candidates was promoted. This is not a model-accuracy or stable-release pass.

394 regular JVM tests passed (two opt-in tests skipped), as did 57 script tests,
release build/lint and instrumentation compilation. Lint: zero errors, 41 warnings.
All 22 ARM64 ELF alignment checks, ZIP alignment and the offline packaging check passed.
The normal release was signed with the freshly verified installed certificate and
installed with `adb install -r`, preserving app data. Direct installed APK SHA-256:
`c47169ed5752b35994fab1b0feaa7659db70ac84a3f96642327e9c8c7d9e662b`.
Private artifact: `build/reasoning-diagnosis/semantic-release-signed.apk`.
Phone answer repetitions are recorded separately in
`build/reasoning-diagnosis/semantic-device-matrix.json`.
No public catalog update, release, commit or push was performed.

Completed all four installed-build phone repetitions with no active default network
reported (not a traffic audit):

| Question / run | Retrieval | First word | Total | Observed answer quality |
| --- | ---: | ---: | ---: | --- |
| LAN sharing / process-cold | 19.94 s | 73.50 s | 134.27 s | Correct “yes” core claim; unnecessary piracy/background material. |
| LAN sharing / warm | 23.38 s | 78.09 s | 117.44 s | Correct “yes” core claim; overgeneralizes internet sharing as WWW-linked. |
| Boats / process-cold | 4.92 s | 60.51 s | 94.47 s | Correct buoyancy passage retrieved, but generated text reverses the boat/raft distinction and falsely says all fully submerged objects float. |
| Boats / warm | 5.76 s | 61.66 s | 101.51 s | Same correct passage; generated text falsely says boats remain afloat even when overloaded. |

The earlier LAN No/Yes contradiction did not recur in these two runs; this is not
a general correctness or speed pass. None produced an accepted source quotation.
Mixed-answer attribution deliberately strips unverified model citation markers;
zero accepted citations must not be treated as proof that retrieval failed.
Opened “Boat — Buoyancy” in the phone source viewer and confirmed the local text,
including the overload/sinking qualification, and pinned Wikipedia revision URL.
Thus the remaining observed boat errors occur after adequate evidence reaches the
model. The model is unchanged; alternate inference settings/model experiments
remain rejected private diagnostics. Test processes finished, with app data intact.

### Everyday reference phone installation and tests

After the phone reconnected, verified its installed certificate matched the
prepared normal-release update and installed with `adb install -r`. Direct device
APK SHA-256 matches `35dbf24d1d45dfa78844b4be0584e7eaef57ccc5b002bfca67ac39f2dec1fbb3`.
Imported Everyday reference 0.2.0 through Library's normal file picker. The
transferred pack hash matches `1a874462b97877909d1ddca21ce59481cdd4b786f5a4f213fe10a02b67f0bbc9`.
Preview, Travel and Biology are enabled; the existing model remains installed.
No app data clearing, uninstall, radio changes or public publication occurred.

Two process-cold queries completed with no active default network reported:

| Query | Retrieval | First word | Total | Observation |
| --- | ---: | ---: | ---: | --- |
| Sampling bias / large survey | 21.76 s | 59.89 s | 89.72 s | Correct topic definition retained; explanation follows the selected passage, but no accepted citation and completeness is not certified. |
| LAN sharing without internet | 18.36 s | 71.87 s | 105.16 s | Three reference passages retained; **answer fails**: opens with incorrect “No” and ends by correctly saying LAN sharing works without internet. |

Opened the Internet passage in the source viewer and confirmed local text and
pinned revision URL were present. Raw UI evidence/timings are in
`build/phone-release-check/everyday-reference-device-matrix.json`. Pipeline times
exclude model preparation and UI input. These are single runs, not a latency
distribution, traffic audit or answer-quality pass. Phone left on the completed
result. Installation and retrieval work; inconsistent model generation remains
demonstrated on-device, not only on desktop.

### Everyday reference initial desktop candidate — before phone installation

Reviewed AndroidLM/BOAR architecture without importing their code, corpora or
benchmarks. Built a separate 60-article reference preview directly from pinned
Wikimedia sources, preserving paragraph context and attribution. The 4,337,718-byte
0.2.0 pack rebuilt byte-identically; hashes and paired case review are in
[the candidate/reference review](../evidence/candidate-architecture-review-2026-10-01.md).
This is optional development material, not a catalog publication or accuracy pass.

Implemented a tested recall fix for explicitly named multiword reference
definitions (e.g. sampling bias), requiring the definition and topic terms in
the body rather than trusting a title alone. Final paired desktop runs used the
same app/model/tool identities; sources are now available for seven non-live
questions, but generated answers retain factual errors. No winner/readiness
claim follows. Phone disconnected during this work: the previously installed
signed release and its collections remain unchanged.

Verification: 390 regular JVM tests passed (two opt-in tests skipped), 86 Python
tests passed, release build/lint and instrumentation compilation passed. Lint
reported zero errors and 41 warnings. Offline packaging, ZIP alignment and all
22 ARM64 ELF checks passed. Prepared, **not installed**, normal release signed
with the existing local-device key:
`build/phone-release-check/everyday-reference-signed.apk`, SHA-256
`35dbf24d1d45dfa78844b4be0584e7eaef57ccc5b002bfca67ac39f2dec1fbb3`.
Reverify the phone's installed certificate when reconnecting before installation;
no device-certificate check was possible this turn. Public catalog unchanged.

### Implemented routing and explanation-relevance fixes

- Missing attached-file requests now ask for the file before retrieval or model
  generation. Explicit live local-status requests explain the offline limitation
  without searching unrelated papers. These responses can be submitted without
  waiting for the model to load. Supplied attachments retain their reading path;
  quoted examples and ordinary saved-source questions are not treated as missing files.
- General explanations reject structured travel listings and require stronger
  topic coverage in a title or an explanatory passage. Two incidental title words
  no longer satisfy a longer question. Positive regressions preserve genuine
  explanatory passages, reference comparisons and cross-article coverage.
- Initial 16-case rerun rejected the aging/boats metaphor and plant-query noise,
  but exposed another weak title match. After tightening that rule, the exact
  shipping-code check (`build/routing-fix-shipping/report.json`) retained zero
  unrelated sources for metal/wood and LAN questions. LAN recovered the correct
  no-internet core answer; Berlin retained four saved listings. Missing report
  and live road closures used zero sources and zero model calls.
- A shorter model-only prompt was tested and rejected: concision improved but a
  seasons answer introduced an incorrect hemisphere/month statement. That prompt
  change is **not included**. Experimental reports are retained separately under
  ignored `build/routing-fix-final-matrix/` and must not be presented as shipping
  behavior. The earlier matrix is in `build/routing-fix-matrix/`.
- These fixes remove demonstrated routing/relevance failures, not every model
  error. The shipping LAN answer still invents a software-license requirement;
  general model answers remain explicitly unverified. The lexical gate is not
  semantic validation, and foundational reference coverage is unchanged.
- Signed normal-app device tests confirmed missing-file responses in 7/0 ms
  (process-cold/warm) and live local-status responses in 109/1 ms, with zero
  retrieval and generation. These are pipeline timings, not launch-to-answer
  measurements. The test exposed a misleading generic “Model-generated” label
  and library-download suggestion; input notices now omit that suggestion and
  identify themselves as notices with no model generation.
- On the same pre-UI-correction build, the LAN core answer was yes in both phone
  runs, with zero supporting sources, but generated details still failed review
  (e.g. requiring identical operating systems in the cold run). Retrieval/planning
  took 64.94/63.62 s; first word 79.76/78.61 s; total 130.72/125.03 s. No general
  latency improvement is claimed. Raw device evidence is retained in
  `build/phone-release-check/routing-fix-device-matrix.json`.
- Verification after the UI correction: 389 JVM tests passed, two opt-in tests
  skipped, no failures/errors; release build, lint and instrumentation compilation
  passed. Lint: zero errors, 41 warnings. Offline packaging, ZIP alignment and all
  22 ARM64 ELF alignment checks passed. Signed normal release installed with
  `adb install -r`, preserving data and matching the existing device certificate.
  Installed APK SHA-256:
  `86495c9f68cfbfc1e33e2742a897ce2e8ed032d7f6624ca558c1ca3ee758d474`.
  No public release, commit, or push was performed.
- Final installed-build cold/warm retest passed all four notice checks: correct
  “Research notice · no model generation” label, no irrelevant library suggestion,
  zero retrieval/generation. Missing-file totals: 4/0 ms; live-location totals:
  11/1 ms. Evidence: `build/phone-release-check/routing-fix-notice-device-matrix.json`.
  No active default network was reported; radio settings were not changed. This
  is not a traffic audit. Test processes finished and the phone remains on the
  completed result.

### Fixed matrix and repeated phone evaluation

Completed a frozen 16-question development matrix, three model-only controls and
four intended phone query runs. See the [case-by-case review](../evidence/retrieval-matrix-2026-10-01.md)
for failures, primary-source checks and harness limitations. No app changes or new
installation in this evaluation; existing signed candidate remained installed.

- Current selection still admits metaphors, narrow studies and incidental overlap.
  The boats query matched an aging-advocacy idiom; LAN sharing was incorrectly
  declared internet-dependent. Model-only corrected the LAN core claim but retained
  other errors; boats and height controls also failed correctness review.
- Phone ice first word: 32.06/31.96 s; total 82.10/67.07 s with differing output
  lengths. Berlin: 11.08/11.05 s, four sources, no model generation. These exclude
  startup/preparation and are not a representative latency distribution.
- Missing-report/live-fact requests did not fabricate the requested personal/live
  information, but some still retrieved and cited irrelevant clinical material.
- Next implementation priorities: explicit missing-input/live-fact routing,
  intent-aware evidence selection with positive recall checks, and foundational
  reference coverage. Neither keyword matching nor a model-only fallback is a
  complete quality solution. Release gate remains open.

### Causal-query fix and phone retest

Following the smoke-test failure below, short causal explanations now require all
original content terms in the passage body and exclude structured place listings.
Explicit comparisons retain their separate-subject behavior. When a short causal
query has no accepted evidence, it skips generative keyword planning and gives a
labelled model-only answer. This conservative lexical rule can miss useful synonym
passages; it is not a semantic verifier or new source coverage.

- 384 JVM tests: zero failures/errors, two skipped. 57 script tests passed.
  Release build/lint and native ELF/ZIP/static packaging checks passed.
- Desktop replay rejected irrelevant ice-question sources; existing Berlin and
  mitosis/meiosis answers remained byte-identical. No independent quality claim.
- Installed signed optimized release in place on Infinix using the matching
  local-test key; SHA-256
  `620224c51c6c59d5ec79680a2ddbd4e2a5c2d2d50bbd7c62c4b616984f0ea104`.
- Exact question `Explain why ice floats on water.`: retrieval 13.16 s, first word
  29.41 s, total 67.55 s, 147 generated tokens. No unrelated saved passages or shop
  quotation; no invalid citation warning. Earlier baseline: 92.77 s first word,
  119.87 s total, 102 tokens. These are **single observations with different output
  lengths and a warm final model**, not a controlled speed benchmark.
- An intermediate filtering-only build still took 118.45 s: planning inflated
  retrieval to 59.26 s. The final route removes that extra model turn. An automated
  input attempt missing its first character was stopped and excluded before the
  exact-question retest. Cancellation allowed a subsequent query.
- Network remained inactive; no ANR since boot or app crash found in the inspected
  crash buffer. Evidence/screenshots remain under ignored `build/phone-release-check/`.
  Broader retrieval quality, synonym recall, cold/warm latency distribution and
  remaining release gates are still open. No public release was published.

### Phone smoke test — owner resumed device testing

The owner subsequently authorized phone testing. Installed the optimized normal
`xyz.fieldatlas` release in place on Infinix X6840 using its verified existing
local-test certificate, without uninstalling or clearing data. Signed APK SHA-256:
`758450133641ea55e7e3a17c67fa4c05219ac2da10d686e21e2f8cc5d7481c03`.
This is not public-key upgrade validation or publication.

- Cold activity launches: 1,013 and 1,335 ms, not model-ready measurements.
- Model, both collections and prior history remained available. Berlin lookup
  completed with four dated/unranked sources; citation 1 opened its matching saved
  passage. The new answer survived force-stop/relaunch.
- `Explain why ice floats on water.` completed after a brief home/return transition,
  but **failed relevance and answer quality**: retrieved an ice-cream shop and a
  drug-delivery study, then repeated irrelevant shop text in the explanation.
  App metrics: retrieval 13.88 s, first word 92.77 s, total 119.87 s,
  102 generated tokens, 0.9 tok/s. Zero of four sources cited; unmatched citation
  warning shown. Evidence filtering and generation latency remain release blockers.
- Android reported no active default network. Airplane setting was on and Wi-Fi
  off; mobile-data setting remained on. No radio settings were changed. This is
  not an all-radios-disabled acceptance run or network-enabled traffic capture.
- No ANR since boot and no Field Atlas crash in the inspected crash buffer.
  Phone uses 4-KB memory pages; it does not validate 16-KB runtime behavior.
- Local record/screenshots: ignored `build/phone-release-check/`. Download recovery,
  voice recognition, full attachment/UI matrix, peak resources, GrapheneOS and
  independent answer-quality evaluation remain unverified on this APK.

### Subsequent context and native-compatibility pass

- Reference overview selection now retains an immediately adjacent retrieved chunk
  from the same document, title and source when an explanatory anchor is present.
  It does not fetch arbitrary neighbors or recursively extend the window. Bounded
  requested overview passages (at most 2,400 characters) remain whole in the prompt;
  the existing total evidence budget still applies. Three new JVM tests cover
  provenance, adjacency and retaining complete paragraphs within the budget.
- Model installation rechecks the current app footprint and free-space reserve
  after downloading, before committing the model. Rejection leaves the verified
  staging file available for retry. This is still not an atomic OS quota.
- Direct ELF inspection found the previously packaged `libvosk.so` had 4-KB LOAD
  alignment despite the APK passing ZIP alignment. Updated Vosk 0.3.47 to 0.3.75
  (transitive JNA 5.18.1). All 22 arm64 libraries in the new APK pass the new
  `scripts/check_native_alignment.py` check, now wired into CI with four unit tests.
  This checks ELF segment alignment, not speech accuracy or runtime compatibility.
  See [Android's separate ELF and ZIP checks](https://developer.android.com/guide/practices/page-sizes)
  and the [published Vosk artifact](https://repo.maven.apache.org/maven2/com/alphacephei/vosk-android/0.3.75/).
- Local verification: 382 JVM tests, zero failures/errors, two opt-in tests skipped;
  91 Python tests passed (57 scripts, 16 builders, 12 pack tooling, six benchmarks).
  Release build and instrumentation compilation passed. Lint: zero errors, 43
  warnings; native alignment warnings are gone, dependency-version notices changed
  after online resolution. No wholesale dependency upgrades were performed.
- Final unsigned APK: 100,473,525 bytes; SHA-256
  `6149e6cb6606e0241b18152bd2b78f6256a1e746648a0b79058f7a55ea7f8991`.
  Offline packaging, ELF alignment and `zipalign -c -P 16 4` passed. No phone
  installation, speech runtime test, signing, push or publication was performed.
  Device work remains deferred by the owner. Remote CI has not run these changes.
- The 11 known automatic questions were replayed with the same model, sampler and
  expanded pilot database in `build/release-context/automatic/report.json`. Ten
  visible answers are byte-identical to the preceding run. Database/spreadsheet
  receives four instead of three sources, but still wrongly generalizes databases
  as interconnected tables; this is not a quality pass. A separate two-question
  manual-selection replay in `build/release-context/manual/report.json` now sends
  all four selected database/spreadsheet passages unchanged (previously two were
  dropped). Its answer avoids that blanket claim but remains overly broad about
  enterprise scale. The algorithm answer still reverses the termination distinction
  despite both selected passages. All 13 generations completed; none constitute
  independent holdout validation or proof of correctness.
- The three existing Biology/Travel/source-only regression questions were replayed
  in `build/release-context/regression/report.json`; all three visible answers
  remain byte-identical. Existing answer defects remain, not newly scored passes.
  All three owned evaluation servers were stopped after completion.

### Earlier October 1 measurements

The entries below are newer than the historical measurements later in this file.
No APK was installed, signed for publication, committed, pushed, or published in this follow-up.

- Provisioning now measures logical bytes across APK/split APKs, native libraries,
  app data and cache, device-protected data, app-specific external files/cache,
  unregistered partial downloads and extraction staging. Overlapping roots are counted
  once, symlinks are not traversed, and scan errors fail closed. Imports check staged
  archive plus extraction growth; resumed model downloads reserve only remaining bytes.
  AppContainer serializes all model downloads, collection downloads and imports.
- This improves admission checks; it is **not an OS-enforced total-storage guarantee**.
  Concurrent history/cache growth and OS-managed storage still require device measurement.
  Metadata reserve is conservative headroom, not a measurement of every future allocation.
  No user files are deleted to make room. The global storage release gate remains open.
- CI now runs the 16 builder tests, including offline reproducibility fixtures, using
  an isolated environment with the existing pinned reference-builder requirements.
  Installation instructions identify the published 1.2.0-rc.1 versus legacy 1.1.10
  and unpublished development changes. The release asset listing was checked read-only.
- The APK audit originally failed on ACCESS_NETWORK_STATE introduced by setup's
  connectivity observer. The observer was inspected; it does not initiate requests.
  The audit now permits this read-only permission and rejects CHANGE_NETWORK_STATE,
  CHANGE_WIFI_STATE and WRITE_SETTINGS. Two regression tests cover this boundary.
  Runtime traffic remains unverified. See Android's
  [permission documentation](https://developer.android.com/reference/android/Manifest.permission#ACCESS_NETWORK_STATE).
- Verification: 379 JVM tests, zero failures/errors, two opt-in desktop tests skipped;
  release assembly and full release lint succeeded (zero errors, 41 warnings).
  Android instrumentation sources compiled, not executed. Python suites: 53 scripts,
  16 builders, 12 pack tooling, six benchmark tests. Remote Actions were not triggered.
- Unsigned APK packaging audit passed: 100,072,753 bytes, SHA-256
  `75f5ef8e873e2770e6b74bfdb8421d0afb38676de4473da4fb6a71d673acc849`.
  This is not an installable signed release or evidence of public-key upgrade compatibility.
- The frozen inference controls described in `docs/evaluation.md` recorded 48 generations
  on two known failed questions. Neither sampler establishes release quality. The app's
  inference settings and model remain unchanged; automatic verification stays disabled.

Next gates: correct full-answer failures on fixed tests and an independent holdout;
validate actual provisioning/recovery and total footprint; run optimized on-device
offline, network-enabled traffic, startup, resource and UI checks; then freeze, sign,
test public-key upgrade compatibility and publish the exact reviewed artifact.

## Prerelease handoff

- GitHub channel: `v1.2.0-rc.1`; app 1.2.0 (14). See `docs/releases/current.md`.
- Public-key APK SHA-256: `6d688a3632aa77650996aec3ae7ff4e887b7d772d9f2995ea0d8e67b71837b7f`.
- Infinix update installed in place with the existing local-test key; APK SHA-256
  `d52c63d24ebcf87fbf394da552b54738408f44b713eb9becb87885ed97b347c6`.
- Public signing certificate matches the historical release certificate. Public-key
  update compatibility has not been exercised on the local-test-key phone.
- Current fixes include parenthesized/grouped citation normalization, compact answer
  headings, source badges, possessive overview relevance, bibliographic-header filtering,
  and deterministic evidence-gap responses for unsupported source-only requests.
- Fresh four-question desktop run: Berlin produced four cited listings; unsupported
  source-only request returned an evidence gap; history and biology used model-only
  responses. History still failed quality review. This is not an accuracy pass.
- The following sections retain earlier measurements with their original limits;
  their APK hashes and counts do not identify this latest artifact.

## Current local candidate

- Version 1.2.0, versionCode 14; source contains uncommitted changes.
- Installed APK SHA-256: `8cfadcd86f603a9d0bbeba9f900fab8f2dcf612394bd53c86307d46392fe0446`.
- Signing certificate: Local Test Build, SHA-256 `ca2642c756215990c115687d41cb495621a02eeeb83f8e4d2cf6bcf9c9bb8989`.
- Historical public-release certificate: `131127512c99a625acd0dd4baf20e1c7cd240b0fd573449197070000e3d6b666`. The original owner keystore was located outside the repository and its certificate matches. No key rotation is needed. Public-signed build/update verification is still required; the phone currently uses the incompatible local-test certificate.
- This APK includes the canonical benchmark filename cleanup, native GC-safety fix, passage-based scholarly ranking changes, and mixed-answer attribution guard. Installed over the existing app without clearing saved data.

## Verified observations

| Check | Evidence and limits |
| --- | --- |
| JVM tests | 293 passed after mixed-answer changes, with zero failures, errors, or skips |
| Python tests | 44 passed: 26 script, 12 pack-tool, and 6 benchmark tests |
| Canonical benchmark | Debug-generated and merged asset matches `benchmarks/questions.json`; only this benchmark file is maintained |
| Android UI tests | Compiled; not executed in this audit |
| Release build and lint | Passed on current candidate; lint had 0 errors, 37 warnings |
| APK packaging | Offline-core packaging audit passed on the installed APK; reflection/alignment checks were on an earlier artifact and remain to be repeated |
| Saved Travel pack | Imported successfully on Infinix X6840 without a download |
| Travel download | User reports successful earlier in-app download; not independently replayed |
| Model/Biology downloads | Unverified; user requested saved packs only |
| Offline operation | Android reported no active default network during query checks; no network-enabled traffic capture performed |
| History/source/reopen | Three answers retained across restart; Berlin citation opened its matching saved passage |
| Resources | Sampled PSS 1,592,148 KiB during an earlier biology run; not peak RAM. Total installed usage not measured |
| Cold activity launch | Earlier run reported 1,165 ms; not model-ready time |

The phone already contained Qwen3.5 2B and Biology & longevity. Travel places was imported from a saved archive. These are not clean-install observations.

## Answer quality and performance

- Berlin vegan lookup returned four cited listings in 5 seconds before the routing change, and 8 seconds afterward. The answer correctly disclosed unranked/stale listings; some checks date to 2018–2020.
- Tokyo vegan lookup returned one cited bakery. This is limited coverage, not a satisfactory comprehensive recommendation.
- The original mitosis/meiosis comparison completed in 50 seconds without sources because the old discovery-summary gate skipped Biology.
- The saved Biology database contains 727 passages matching mitosis, 298 matching meiosis, and 32 matching both. Counts establish searchable material, not that all passages answer the question.
- Keyword retrieval now considers enabled packs regardless of summary wording. Expensive vector scans remain selectively gated; relevance filtering remains active.
- Native logs then measured about 110 seconds processing the evidence prompt before generation. The run was stopped; a complete grounded answer was not scored.
- Prompt packing now selects relevant sentences with adjacent context, keeps wrapped sentences intact, shortens metadata, and retains full-source citation mappings. Oversized units are omitted with an incomplete-evidence warning rather than clipped mid-claim. Distant qualifications may still be outside an excerpt; this is not a complete-document synthesis guarantee.
- The GC-safe candidate completed the exact mitosis/meiosis comparison offline: start 06:48:46, native prompt processing 06:49:06.442–06:50:53.965 (107.5 seconds), completion 06:52:22.695 (about 216 seconds total). The answer displayed five cited local sources. This single run does not demonstrate a meaningful improvement over the earlier 110-second prompt processing measurement.
- **Citation quality failed a spot check:** citation 5 for the claim about genetically identical mitotic daughter cells supporting growth/repair opened a passage from “Human female meiosis revised…” about chromosome segregation errors, not support for that claim. The answer also digressed into fertility/IVF and omitted a clear two-versus-four daughter-cell comparison. Finding sources is not sufficient evidence of answer quality.

## Remaining release gates

1. Expand startup regression coverage; two cold starts passed locally, not a guarantee against all ANRs. Improve Biology ranking/citation support and latency, then repeat quality checks.
2. Build with the recovered, certificate-matched public-release key and verify public-app update compatibility without erasing user data. Do not replace the phone's test-key installation by uninstalling it.
3. Run model/Biology download, cancel/retry, and integrity recovery tests when authorized. Travel download is user-verified, not independently recorded.
4. Run a frozen question set against a named comparable baseline. Historical free-router scores do not measure this candidate; no greater-than-50-percent quality claim is established.
5. Complete connected UI, attachment/OCR, voice, background/cancellation, and persistence regressions.
6. Measure first-token latency, peak RAM, thermal behavior, total storage, and cold model readiness. Verify no research traffic with networking available.
7. Verify supported GrapheneOS compatibility or disclose it as untested.
8. Refresh installation/release notes, freeze the tested commit/version, re-run artifact checks, sign correctly, and record the exact build's public demo.

Private screenshots, UI dumps, memory samples, and bug reports stay under ignored `build/`. No new public release, tag, post, or claim has been made. See [evaluation](../evaluation.md) for the benchmark explanation and proposed replacement questions.

## Startup freeze investigation

The 06:30 ANR trace shows the main thread waiting for garbage collection during text layout, the GC daemon waiting for thread checkpoints, and a dispatcher worker holding ART's shared mutator lock in native `InferenceEngineImpl.load` (checkpoint dump latency 6,011 ms). Model loading already runs off the UI thread.

The JNI wrapper used `FastNative` on model loading and decoding. [Android's API guidance](https://developer.android.com/reference/dalvik/annotation/optimization/FastNative) prohibits this optimization for long-running or I/O-bound native calls because it can block garbage collection. The wrapper now uses ordinary JNI transitions; signatures and persisted data are unchanged. The reproducible native patch was updated and reverse-application checked. A focused code review found no blocker.

Two cold activity starts passed on the Infinix (1,390 ms and 1,053 ms; not model-ready timings). Native model loading took about 21 and 18 seconds respectively. The second run accepted the full comparison question while preparing the model. GC completed during preparation and generation; no new ANR appeared in the sampled logs/exit history. The completed answer persisted across restart. These are manual regression observations, not an automated stress test or a release sign-off. Private captures remain in `build/biology-retrieval-audit/`.

## Passage ranking follow-up

- MeSH indexing lines no longer count as passage evidence or enter the generated prompt; original source text is retained in the viewer. Body coverage ranks scholarly candidates before FTS truncation and final selection.
- Unambiguous single-word comparisons such as “Compare mitosis and meiosis.” prioritize those subjects over trailing instructions. Multiword or ambiguous comparisons retain the previous relevance checks. Regression tests cover ambiguous trailing clauses and generic-word matches.
- A candidate set containing MeSH-tagged scholarly material receives at most four sources and 3,000 evidence characters, preserving whole excerpt units and an incomplete-synthesis warning. This also covers untagged chunks mixed with tagged chunks. These limits are a latency tradeoff, not proof that all relevant evidence was retained.
- An intermediate candidate completed in about 160 seconds but still used eight sources because the original limit incorrectly required every chunk to contain a tag. That mixed-chunk bug was reproduced and fixed with a regression test.
- Current candidate, exact comparison: start 07:19:29; native prompt processing 07:19:49.821–07:20:50.285 (60.5 seconds); completion 07:21:32.276 (about 123 seconds total). Four sources were packed, three cited. Compared with the earlier 216-second run / 107.5-second prompt processing, this is a promising single-run improvement, not a controlled benchmark score.
- **Citation acceptance still fails:** citation 4 for two genetically identical mitotic daughter cells and tissue repair opened “CHK1 controls zygote pronuclear envelope breakdown…” whose passage does not establish that claim. The answer gives a basic comparison, then inconsistently says the excerpts cannot answer it. Prompt instructions and lexical overlap do not guarantee entailment; do not claim citation reliability fixed.
- Berlin travel lookup on both the intermediate and current candidates retained the same four cited listings and stale/unranked disclosure. No downloads or pack modifications were used for these checks; Android reported no active default network.

The following mixed-answer change addresses false attribution, not general claim correctness. Repeated prompt-only changes are not an adequate release gate for this model's unsupported citations.

## Mixed-answer follow-up

- General explanatory questions now permit model knowledge alongside retrieved passages, with an explicit “Model explanation—not verified against saved sources” label. Travel venue lookups and source-only/attachment paths retain their separate evidence policies.
- In mixed answers, only a verbatim quotation found in the corresponding saved passage retains a clickable source marker. Unmatched claims become unverified model text without source links. This establishes quotation attribution, not truth, relevance, or completeness; qualifiers elsewhere in a source may still matter.
- Streaming, history, answer previews, and benchmark exports use the same attributed answer. Discarded model citation markers remain visible as a diagnostic flag, including when another valid quote uses the same source number.
- 293 JVM tests passed with zero failures/errors/skips, Android instrumentation tests compiled, and 44 Python tests passed. Focused review found no remaining blocker in this delta. Release build/lint passed (0 errors, 37 warnings), offline-core packaging audit passed, and the candidate was installed over the existing app.
- Exact biology comparison ran offline at 07:46:37–07:48:42 (about 125 seconds wall clock; native evidence prefill 07:46:59.852–07:48:02.614, 62.8 seconds). The unverified explanation had no clickable citations. One retained quote, beginning “Advances in genomic and imaging technologies…”, opened the corresponding passage and matched it. A non-verbatim aneuploidy quote lost its citation and remained labelled unverified.
- **Quality still does not pass:** the response overgeneralizes gamete outcomes as four sperm or eggs, and includes tangential aneuploidy material. The linked quotation is genuinely from the saved passage but is not useful support for the main comparison. Attribution enforcement is not an accuracy or usefulness guarantee, and this timing is not an improvement over the previous candidate. Ready-card preview retained the unverified label.
- Berlin regression retained four cited listings (Vedis, Kopps, Chay Viet, St. Bess), explicitly unranked and potentially stale. After force-stop/relaunch, both new answers remained in History, including the biology attribution warning. Activity cold launch reported 1,202 ms (not model-ready time). No new ANR was recorded in the sampled exit history. These are manual observations, not full regression coverage.
- The original release signing keystore was recovered outside Git, and its certificate matches the historical public release. No secrets were printed and no new key was generated. The phone's local-test installation is preserved.

## Desktop evaluation path

- Added an opt-in JVM runner using the app's current Kotlin research orchestrator, keyword retrieval, prompt packing, venue routing, and answer attribution. It uses a locally built pinned llama-server with the saved Qwen GGUF and extracted Biology/Travel SQLite databases. No Android installation or model download was needed.
- The first run generated answers but failed on Gradle configuration-cache serialization. The launcher now disables that cache for this opt-in task; subsequent combined-pack and model-only commands exited successfully. Server processes were stopped after each run.
- Combined-pack run: biology 16,686 ms, Berlin 2,771 ms inside the pipeline, excluding server startup/build/hash work. Biology packed four sources, including the same first and fourth titles observed on the phone. Berlin retained four saved listings. This is not full device parity: vectors are disabled, the backend is desktop, answer seed is fixed at 17, and output is non-streaming. App token-count/first-token metrics are not comparable; desktop server usage/timings are retained separately.
- The biology answer with retrieved passages incorrectly said mitosis has two divisions and copied prompt instructions. The model-only run (4,447 ms) avoided those specific failures but still oversimplified gamete formation. These are development observations, not accuracy scores or proof that model-only is generally better. The next targeted investigation is evidence/prompt interference, not another unchanged phone run.
- Private reports retain raw model responses, inference requests, candidate passages, packed sources, visible answers, model/database hashes, and source provenance: `build/desktop-evaluation/report.json` and the explicit comparison `build/desktop-evaluation/model-only/report.json`.
- Full ordinary JVM suite: 295 passed, 1 opt-in desktop test skipped, zero failures/errors. The opt-in test also passed with real inference in separate runs. Python suites: 50 passed (30 scripts, 12 pack-tool, 6 benchmark, 2 tools). Focused static review found no blocker; startup-error provenance and lifecycle regression tests were added following its minor findings. Android product code was unchanged by this harness work.

## Independent catalog development follow-up

- Added explicit **Refresh collections** in Setup and Library, backed by the bundled catalog and an atomic app-private cache. Construction and screen rendering do not fetch. Refresh accepts at most 1 MiB / 500 entries, validates before replacement, and preserves the previous list after failures. Downloads still require a separate action; installed packs are not deleted by catalog refresh. Active transfers retain their original displayed size and Cancel control across changed/removed listings.
- The configured HTTPS endpoint uses the canonical `app/src/main/assets/knowledge/catalog.json` on `0x94t3z/fieldatlas` main. Read-only remote metadata confirmed that repository/default branch. **The endpoint returned HTTP 404 during this check.** No publication occurred; live discovery remains blocked until the file is available there. The bundled/cache fallback is tested, but successful public refresh is not yet demonstrated.
- New-topic integration passed on desktop using a tiny local mineralogy fapack: real archive import, registry, SQLite keyword retrieval, prompt citation mapping, enable/disable/removal, compatible fixture-vector retrieval, and incompatible-encoder fallback. No model, network, or large pack download was used. Synthetic encoder vectors prove pipeline behavior, not neural retrieval quality or phone performance.
- Encoder identity now travels with vector requests and is checked under the encoder lock at actual encoding time. Only the implemented normalized int8-symmetric-per-vector / chunk_vectors layout is used for vectors; incompatible layouts remain keyword-only. This is not arbitrary encoder/model support.
- Verification: 324 ordinary JVM tests passed, two opt-in desktop tests skipped, zero failures/errors. The new-topic desktop fixture passed separately. Android instrumentation tests compiled; debug APK assembly passed. Python scripts: 30 passed. UI instrumentation was **not executed** and no APK was installed. Device source tapping, refresh/navigation behavior, and zero attempted research-network requests still need validation on the final candidate.
- Fresh scoped review identified two P2 issues (active-transfer UI disappearing on refresh; encoder identity changing between retrieval and encoding). Both received test-first fixes; the final unit suite and desktop fixture passed. The snapshot test validates catalog selection/immutability rather than a full network transfer; full transfer-boundary coverage remains pending. No unrelated dirty changes were reviewed as part of this scope.
- **Storage release blocker:** existing quota checks use registered payload bytes, not the complete APK + app data + cache + temporary download/extraction footprint, and do not reserve a shared budget across provisioning operations. The catalog change does not establish the bounty's total 50 GB guarantee. Per the approved scope, a cross-path storage-budget redesign requires a separate follow-up rather than silently expanding this change. Historical compliance `pass` entries are not approval of this candidate.
- New debug APK packaging audit passed (ARM64, no prohibited service/client dependencies detected), 174,830,870 bytes, SHA-256 `b1bfdb3bd99dcadd7f2737e19456a912a31b6ce50d4bd670e8b23c881f7d6fef`. This artifact was not installed. Packaging checks do not prove network behavior.
- No commit, push, public release, phone install, model/pack download, or broader UI redesign occurred. Before release: resolve the quota gap, publish/verify the catalog only with authority, finish UI polish, and run current device/resource/quality/signing checks. Kenny's supplied clarification supports explicit provisioning while requiring full offline function; it does not establish final bounty acceptance or the quality threshold.

## Desktop evidence/prompt follow-up

- Simplified general-answer instructions and moved the question after the evidence. Added a topic-independent overview relevance heuristic, retaining source-specific questions on the ordinary path. Tightened planner-synonym rescue for detailed questions. Removed `have`, `has`, and `had` from keyword terms after tracing a seasons query to the false prefix match `have` → `Haven` in Earth Haven Farm.
- Reused the saved model and Biology/Travel databases, seed 17, and six questions selected before these changes. Private before/after reports are `build/desktop-evaluation/before/report.json` and `build/desktop-evaluation/after/report.json`. These are development cases, not unseen validation or a six-out-of-six accuracy score. The canonical 18-question benchmark was not changed.
- Final observations: mitosis/meiosis no longer copied instructions or assigned two divisions to mitosis; unrelated evidence was rejected and the answer was explicitly labelled unverified model knowledge. Batteries/capacitors likewise fell back to model knowledge and recovered the chemical-versus-electric-field distinction. Seasons rejected all four tangential sources and gave an axial-tilt explanation without the earlier Australia/northern-hemisphere error. Senescence retained five passages, but its unverified explanation still treats cell death too narrowly and overgeneralizes harmful effects; this remains a quality concern.
- Berlin retained the same four cited, unranked, potentially stale saved listings. The fictional-city response invented no venues and no longer suggested online directories. Neither observation establishes current venue accuracy.
- Final pipeline times in question order were 23,067 / 12,946 / 7,169 / 5,573 / 2,464 / 13,422 ms. Latency varied; no overall speed improvement is claimed. Desktop keyword-only, non-streaming limitations above still apply. No phone performance or vector-retrieval claim follows from this comparison.
- Verification: 305 ordinary JVM tests passed, one opt-in desktop test skipped, zero failures/errors; Android instrumentation code compiled. All six real-inference desktop cases completed separately, without an automatic quality score. Python suites: 50 passed. Focused review found no blocker in the scoped changes. Regressions cover incidental study matches, source-specific requests, singular/plural forms, appositives/acronyms, non-English fallback, planner rescue, and the Haven prefix collision.
- The overview gate is an English-oriented precision heuristic, not semantic verification. Its closed predicate list and weak language detection can reject useful passages or admit irrelevant ones. No topic-specific answers were hardcoded. A more prescriptive prompt variant was rejected after actual inference worsened the capacitor explanation.
- Manual factual checks used [NASA's seasons explanation](https://spaceplace.nasa.gov/seasons/en/), [NHGRI's meiosis glossary](https://www.genome.gov/genetics-glossary/Meiosis), and [NIA's senescence explanation](https://www.nia.nih.gov/news/does-cellular-senescence-hold-secrets-healthier-aging). These external references were for review, not supplied to the offline model.
- No model or knowledge downloads, UI changes, APK rebuild/install, commit, push, or release occurred in this follow-up. The installed build remains the earlier candidate. Release readiness remains unproven: these known cases improved, but model factual quality, broader retrieval coverage, and device validation remain separate gates.
