# Release audit

Updated 29 September 2026. **Owner approved a public prerelease, not a stable-readiness claim.** Remaining quality and verification gaps below still apply. This is the single maintained readiness document. Historical release evidence remains in Git history and published release tags; older local audit drafts are archived under ignored `build/private/archive/repo-cleanup/`.

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
