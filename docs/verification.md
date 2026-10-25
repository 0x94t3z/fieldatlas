# Verification

## v1.4.2 release (built and published 25 October 2026; build 23 the same day)

- **Release (build 23):** the first 1.4.2 upload (build 21, `10551873…`, below) was replaced on the same
  day: from 15:08 UTC by build 22 (`9f9fec3`, 102,556,207 bytes, SHA-256 `ae0290ce…`), and then by build 23,
  built from a clean clone at `3955e2b` (the APK records `3955e2b` as its revision), with
  `third_party/llama.cpp` at its pinned upstream commit `60081bb2b` and
  `scripts/patches/ai-chat-generation-fixes.patch` applied. Public-key APK: version 1.4.2 (23),
  102,561,155 bytes, SHA-256 `16ef7d8bcced0812c91dc408f69ed2547b0beb359b21b7479912bde19471998a`, signed
  with the public release certificate (`131127512c99a625acd0dd4baf20e1c7cd240b0fd573449197070000e3d6b666`).
  The offline packaging audit passed, the APK passes `zipalign -c -P 16 4`, all 25 native libraries pass
  `check_native_alignment.py`, and the local-test twin differs only in its signature (419 of 419 files
  outside `META-INF` byte-identical). Against build 21 it changes four things: files sent with a question
  stay with it (one label under the question names the first and counts the others; tapping it lists them
  and opens each, with nothing to remove) and its follow-ups keep using them, while the follow-up box holds
  only files not sent yet; the answer page shrinks above the keyboard, so the follow-up box and Send stay
  visible and the answer still scrolls; a natural-voice download that fails at once shows as failed; and a
  topic reopened from History opens on its latest answer when two answers were saved in the same
  millisecond. Build 22 had the same changes but showed each sent file as a large card; build 23 differs
  from it only in that label.
- **Build 23 checks (25 October):** release lint reported 0 errors; 1,755 JVM tests ran, 1,699 passed and
  56 opt-in tests were skipped, none failed; 78 script, 12 pack-tool and 10 benchmark tests passed, 262
  tool tests ran with 12 skipped and none failed, the website's 6 build tests passed, and two builds of
  the starter pack were byte-identical. On a 4 GB Android 13 emulator with a 720 × 1600 screen, like the
  Redmi 13C, the 152 instrumented tests ran in airplane mode: 149 passed and 3 were skipped, the opt-in
  tests that download from the internet. Among them, the answer page was checked with the real on-screen
  keyboard at normal and double text size, both with the window Android 13 and 14 give the app and with
  the edge-to-edge window of Android 15 and newer, and the sent-file label was opened to each file's text.
  On the 12 GB Android 15 emulator, which had carried build 21, build 22 and a local preview of this change,
  each installed over the last with its data kept, build 23 was installed the same way. In airplane mode, with two files attached, "When does the hut close and when must
  beds be booked?" was answered from both with citations, and the follow-up "Where can I get water on the
  way?", which names no file, was answered from the same files ("Water is available only at the 6 km
  spring"). With no files, "What is photosynthesis?", its follow-ups "Why is it important for animals?"
  (read with the topic) and "What about cellular respiration?" were answered with citations, and a bee
  sting with swelling lips opened the anaphylaxis guide at once.
- **Release (build 21):** built from a clean clone at `eb7ab5a` (app source `3825c7d`; the APK records `eb7ab5a` as
  its revision), with `third_party/llama.cpp` at its pinned upstream commit `60081bb2b` and
  `scripts/patches/ai-chat-generation-fixes.patch` applied.
  Public-key APK: version 1.4.2 (21), 102,548,359 bytes, SHA-256
  `1055187374b035194b96115d2ac34781ae9b2d6bd4d83ba5e868d71ae12e75a7`, signed with the public release
  certificate (`131127512c99a625acd0dd4baf20e1c7cd240b0fd573449197070000e3d6b666`). Android 13 or newer,
  arm64-v8a. The offline packaging audit passed, the APK passes `zipalign -c -P 16 4`, and all 25 native
  libraries pass `check_native_alignment.py`. The same source signed with the local test key differs from
  it only in its signature: all 419 files outside `META-INF` are byte-identical. It differs from the
  previous candidate (`5c02cc26…`, built from `e854703` on 24 October) only in two screen-reader labels:
  Library's download button is read as "Download Qwen3.5 2B, 1.4 GB" instead of "1.4 GB, Qwen3.5 2B",
  and a place's address copy button as "Copy address" instead of "Copy".
- **Automated:** release lint reported 0 errors. At `eb7ab5a`, of 1,737 JVM tests 56 opt-in tests were
  skipped, 1,680 passed and 1 failed: it asked for 9 am in London in South Korea on the day it ran and
  expected 5:00 pm, which is true only during British summer time. That ended on 25 October, the day of
  the run, and the app's 6:00 pm was the right answer (on 24 October the same tests all passed). The test
  now checks fixed days on both sides of the change (`47b6669`, tests only; the APK is unchanged), and the
  rerun on 25 October passed: 1,738 JVM tests, 1,682 passed, 56 skipped, none failed. 78 script,
  12 pack-tool, 10 benchmark and 262 tool tests passed (2 skipped), two clean builds of the starter pack
  were byte-identical, and the website's 22 browser tests and 6 build tests passed. Every emergency
  routing change was checked against the 320 labelled questions.
- **On-device tests:** the 144 instrumented tests ran on the 12 GB Android 15 emulator in airplane mode on
  25 October: 141 passed and 3 were skipped, the opt-in tests that download from the internet; none
  failed. Earlier that day one test that types into a text field (Download sources) failed once in seven
  full runs and passed in the others. Library's consent sheet is tested with the app's own download
  coordinator and no network: nothing is queued before the person agrees or after "Not now", the server
  is named without the address's private query, and confirming queues the item to wait for Wi-Fi, with no
  transfer started.
- **Downloads:** the app can download 17 files: 3 models, 7 collections and the 7 files of the natural
  voice. On 24 October every one resolved at its pinned Hugging Face revision. For 16 of them the size and
  SHA-256 that Hugging Face records for the file matched the ones the app checks (a metadata check, not
  a download). `tts.json`, a small file Hugging Face serves through a redirect without that record, and
  three other small voice files were downloaded in full and matched the app's SHA-256.
- **A clean install, set up inside the app (previous candidate, 24 October):** the public-key APK
  `5c02cc26…` was installed on a fresh 4 GB Android 15 emulator with no Google packages and set up only
  through the app. It downloaded Qwen3.5 2B from Hugging Face; the download was paused and resumed after
  a cold restart, and twice resumed after the connection dropped, each time keeping what it had. On the
  device the installed file is 1,396,198,496 bytes and matches the pinned SHA-256 (`57a10858…`). It then
  downloaded the Cryptography & Ethereum collection, whose installed files match the SHA-256 values in
  the collection's manifest. In airplane mode, "How do blob transactions from EIP-4844 make rollups
  cheaper?" was answered in 41 seconds at a computer's speed, citing the EIP-4844 specification, with
  424 tokens written by the 2B: the first draft ran past its length and was not shown, and the app's one
  repair, a single sentence, passed the model's check against the cited passage.
- **An upgrade, data kept (this build, 25 October):** this APK was installed over that one on the same
  emulator; the model and the collection stayed in place. Library's download buttons read "Download
  Supertonic 3, 146 MB" and "Download Encyclopedia, 10.64 GB", and in airplane mode "What does ML-KEM
  protect against?" was answered by the 2B in 34 seconds, citing four passages from the collection
  (110 tokens written).
- **On a phone:** local-test builds of the 24 October code (`f64b0fe` to `13171f6`, before the last wording
  and cleanup commits) were installed over the previous build with data kept on a Redmi 13C (6 GB,
  Android 13). In airplane mode, six questions asked twice took, from asking to the
  complete answer, 88 to 153 seconds (simple) and 195 to 351 seconds (explanation, comparison, a trip
  plan) with Qwen3.5 2B, and 3 to 6 minutes with Qwen3.5 4B. With Wi-Fi on and other apps open, one
  4B answer (on the build of `f64b0fe`, before the model recommendation changed) took 47 minutes while
  Android was closing apps for memory; the 2B is recommended below 8 GB since. An emergency asked while the 4B was reading another question opened its guide at once;
  Stop, an emergency right after it, and the next question all worked, and the app process stayed up
  throughout. Leaving a running answer by the back arrow or Android's back button, while it searched,
  read or wrote, showed only the "Still answering" row on the Research home. The local-test build of the
  previous candidate (`e854703`) is the one on the phone: in airplane mode a bee sting with swelling lips
  opened the anaphylaxis guide with the call line in under a second, and "What is the capital of
  Australia?" was answered with Qwen3.5 2B, cited, in 1 min 47 s.
- **Network:** the app has the INTERNET permission, used only when you start a download or tap
  Refresh. Network activity was measured on the Redmi 13C with Android's own per-app counters, on the
  24 October local-test builds: the app sent and received nothing during 75 minutes of questions in airplane mode, nor during a cold start and
  50 minutes of questions with Wi-Fi on and connected.
- **Blind grading:** every set used for 1.4.2 is on [How Field Atlas 1.4.2 was tested](testing-1.4.2.md),
  with the graded answers in [fieldtest/results-1.4.2](fieldtest/results-1.4.2/).
- **Not verified:** compatibility with GrapheneOS (no Pixel or GrapheneOS device was tested), the
  public-key APK on a physical phone (both test devices carry the local test key, and switching would
  erase their data), this build on the phone (the Redmi carries the previous candidate's local-test
  build, which lacks the two labels), an 8 GB or 12 GB phone, and an emergency asked while a stopped
  answer is still finishing its reading on the phone (covered by JVM tests).

## v1.4.2 candidate (23 October 2026, superseded)

- **Automated:** of 1,733 JVM tests, none failed and 56 opt-in tests were skipped on the candidate
  source (`c4adc03`). Every emergency routing change was checked
  against the 320 labelled questions in `app/src/test/resources/emergency-labelled.jsonl` (231 right,
  none misleading).
- **Devices:** the candidate, signed with the local test key, was installed over the previous build
  with data kept on a Redmi 13C (6 GB, Android 13, Qwen3.5 4B) and a 12 GB Android 15 emulator
  (Gemma 4 26B). In airplane mode with Wi-Fi and mobile data off: nine first-aid situations on the
  Redmi opened with the call line and the right guide in 11–12 s each, the no-guide reply for sepsis
  and a cord prolapse, the splinter without an alarm, and a CPR learning question as a real answer
  (285 s); on the emulator Gemma answered general questions in 90–110 s, the "Answer ready" note
  showed only for an answer finishing while the app was in front, the model-fallback note survived
  a restart and cleared when Gemma was chosen again, and Answer details reopened from History.
  Later the same day, on both devices and offline: the 4B answered "Who designed the Sydney Opera
  House, and when was it opened?" from its notes in 5 min 38 s (Utzon, 20 October 1973, "1 cited
  local source"); "altitude sickness before trekking to Everest Base Camp" was answered as a trip,
  with no call line, on both models; "coffee shops near Shibuya Crossing" listed two cafés within a
  walk as cards; "a hospital near the Colosseum" listed six hospitals with the emergency note, whose
  line to the nearest emergency department opened its sheet, and the address opened full screen.
- **Blind grading:** every set used for 1.4.2 is on [How Field Atlas 1.4.2 was tested](testing-1.4.2.md),
  with the graded answers in [fieldtest/results-1.4.2](fieldtest/results-1.4.2/).
- **Not done yet:** the public-key release build, its hash, the tag and the release itself.

## v1.4.1 release build (October 15, 2026)

- **Public APK:** version 1.4.1 (20), built from `2a2011a` and tagged on `2817ce8`. 73,164,251
  bytes, SHA-256 `bca52edf4830ee3c853c9175f921556a2e7388c5316bd6fd61db0fadc052aff0`. Its
  certificate matches the existing public release certificate
  (`131127512c99a625acd0dd4baf20e1c7cd240b0fd573449197070000e3d6b666`). The download from the
  GitHub release was hashed again after publishing.
- **Automated:** release build and release lint passed (0 errors). 1,387 JVM tests ran with none
  failing (56 opt-in tests skipped). 78 script, 12 pack-tool, 10 benchmark and 256 tool tests
  passed, and so did the website's 25 browser tests. The offline packaging audit passed, the APK is
  16 KB-aligned, and all 23 native libraries pass the alignment check. The answer-text tests that use
  new regular expressions also ran on the Redmi 13C's own runtime.
- **Collections:** not attached to the release. Each one downloads from Hugging Face at a pinned
  revision, and each public copy was downloaded and hashed in full before the catalog pointed to it
  ([DATASETS.md](../DATASETS.md#collections-the-app-offers)).
- **Phone:** a locally signed build of the same app code on a Redmi 13C (Android 13), installed over
  earlier builds with its data kept. The five new collection versions were imported through
  Library, the earlier versions deleted in the app, and questions answered in airplane mode. The
  public-key APK itself was not installed on a phone.
- **Answer quality:** 29% of web-researched frontier-model answers on 60 fresh questions (set 9),
  with 0.67 factual errors per answer. On the same questions 1.4.0 scored 28.6% and 1.4.1 29.7%.
  20 new questions asked in the installed app on the Redmi scored 4.65 of 10. Over 120 questions in
  a row on the phone, quality did not drift (26% of the reference in the first half, 25% in the
  second) and the app neither crashed nor slowed down. Details:
  [set 9](evaluation.md#141-release-build-on-fresh-questions-october-15),
  [long session](evaluation.md#141-over-a-long-session-120-questions-in-a-row-on-the-phone-october-15),
  [on the phone](evaluation.md#141-on-the-phone-answers-speed-tokens-and-failures-october-14-15).
  The bounty's bar is not met.
- **Not verified:** the public-key APK on a phone, and network traffic during research.

## Source checks after 1.4.0 (October 14, 2026, released in 1.4.1)

These checks cover the fixes made after the published 1.4.0 (commits after `65851ba`). They were
released in 1.4.1 the next day.

- **Automated:** 1,376 Android JVM tests ran with none failing. Python: 78 script, 12 pack-tool,
  10 benchmark and 247 tool tests passed. On the Redmi 13C, the unit suite was run under Android's
  own runtime (ART) without installing anything: 1,302 tests ran, and the 50 failures all need
  something only a desktop or a full app provides (desktop files, Compose and AndroidX classes).
  All 815 regular expressions the app holds compiled with Android's ICU engine. That run caught a
  pattern that compiled on the JVM but not on a phone; a JVM test now rejects the cause (`[:` in a
  pattern).
- **Blind question sets:** an AI model that never saw the code wrote calculator (420), emergency
  number (300) and place-routing (300) questions with expected answers, split into a development
  half and a held-out half. Scored once on the held-out half before it was used, against the
  published 1.4.0:

  | Held-out half | 1.4.0 | This source |
  | --- | ---: | ---: |
  | Calculator answers shown and wrong | 20 | 0 |
  | Emergency numbers for the wrong country | 19 | 5 |
  | Place questions sent to the wrong route | 7 | 5 |

  Most of the remaining held-out errors were then fixed as general rules, so later scores on that
  half are no longer blind. The calculator is silent more often than in 1.4.0: it answers only when
  every number in the question is accounted for.
- **Emergency guides:** a blind set of 320 labelled questions runs as a unit test: no question
  opens a guide that could mislead, and none is answered worse than by 1.4.0.
- **Old against new:** every change was compared with the previous code over 7,978 corpus and
  blind questions and 614 recorded answers; each difference was read before it was kept.
- **Real model, fresh set 6 (desktop, Qwen3.5 2B, every collection):** 54 of the 60 (90%) answers are
  identical to the 1.4.0 run. Better: the frozen-lake rescue opens the drowning guide instead of
  frostbite, and a wrong "23 × 8 = 184 slices" lead is gone. Withheld instead of answered: the
  siesta question and "old churches I can walk to" in Kraków, where the model's draft (that Kraków
  has no historic churches) failed its review. Two answers written by the model were wrong: a São
  Paulo time conversion, which the phone now works out itself, and a pizza total, which the
  arithmetic check now corrects and flags. Both fixes were checked on the recorded text, not in a
  new model run.
- **Redmi 13C, local-test key:** the optimized release build installed over 1.4.0 with its data
  kept (answer model in use, nine packs). With the app showing "Offline now", "It is 22:00 in Tokyo
  (UTC+9). What time is it in Honolulu (UTC-10)?" was answered on the phone in under a second.
- **Not verified:** the public-key APK, a paused download discarded and the keep-alive service
  during a real background download on a phone, and network traffic during research.

## Source checks (October 12, 2026)

The local optimized release passed 1,153 JVM tests; 53 opt-in tests were skipped.
Release assembly, signing verification and 16 KiB APK alignment passed.

An offline Redmi imported the consolidated 24.35 GB encyclopedia. Twelve pinned
native-search queries matched the reference ordered source IDs inside the installed
release app. Two retrieval regressions (chloroplasts and P/S waves) were rechecked
on the device after fixes; both returned relevant cited excerpts in 16 seconds.
These are targeted integration checks, not an independent answer-quality benchmark
or evidence that every question is answered correctly. Raw device logs and personal
development records are not distributed with the source.

The checks below describe earlier revisions and their limits, not a new release.

## v1.4.0 release build (October 13)

- **Automated:** 1,291 Android JVM tests ran with none failing (54 opt-in tests skipped, among them
  the desktop runs against the real collections); 78 script and 246 tool tests passed. On the
  Redmi 13C, `OnDeviceRegexTest` compiled every regular-expression literal of the app with Android's
  ICU engine and ran the calculators: both tests passed after this release's new emergency, place
  and calculator patterns.
- **Answer quality:** three fresh sets of 60 questions, each graded blind twice
  ([evaluation](evaluation.md#140-release-build-on-fresh-questions-october-13)). On set 6, written
  after the release build was frozen: 34% of the web-and-frontier reference with the default 2B,
  1.02 factual errors per answer. The desktop runs loaded the phone's native search (Leviathan,
  built from the same pinned source) and every collection the app offers, byte-identical to the
  published files.
- **Hosted collections:** every catalog link points to a pinned Hugging Face revision, and each
  public copy was streamed and hashed in full: the Encyclopedia (10,643,533,492 bytes, SHA-256
  `7d41d7fb…`) and the new Biology & longevity collection (8,183,535 bytes, `06e49130…`) on
  13 October, the other five when they were mirrored.
- **Redmi 13C (6 GB, Android 13), local-test key:** the release build installed over earlier
  1.4.0 test builds with its data kept (version code 19). The Biology & longevity collection was
  downloaded through setup on Wi-Fi; the connection dropped four times and each resume continued
  from the saved bytes until the checksum passed. Library then showed the new edition in research
  and 1.2.0 kept but switched off. In airplane mode with Wi-Fi off: "what are senolytics?" was
  answered from a PMC review in the new collection in 43 s, a cited answer about the Sistine Chapel
  took 2 m 47 s, and a labelled model explanation about the Sahara 3 m 43 s. The answer model was
  re-imported from a saved `.fapack` through setup's file picker after the owner deleted it to see
  the first-run screen.
- **Downloads through setup on the phone:** with the model and six collections deleted through
  Library's own confirmations, setup downloaded each one in turn over USB reverse tethering
  (about 2.1 MB/s) and installed it after its checksum passed: Qwen3.5 2B (1.40 GB) in 11 m 15 s,
  Travel places (351 MB) 3 m 08 s, Vegan places (40 MB) 42 s, Essentials (1.78 GB) 14 m 54 s, Travel
  guides (419 MB) 3 m 37 s, Biology & longevity (8 MB) 20 s and Cryptography & Ethereum (77 MB) 42 s.
  Back in airplane mode, the downloaded model answered "What is the capital of Laos?" with a cited
  passage in 1 m 29 s.
- **Not verified for this release:** the public-key signed APK on a phone, the Encyclopedia's full
  download and install on a phone (it was imported), network traffic during research, and a clean
  install following only the README.

## v1.4.0 (October 6–7)

- **Automated:** 799 Android JVM tests ran with zero failures; 16 opt-in desktop tests were
  skipped in the ordinary run and run separately against the real collections
  (`FIELDATLAS_DESKTOP_CONFIG`). Python: 12 pack-tool, 57 script, 6 benchmark and 72 builder tests
  passed (`python3 -m unittest discover`). An instrumentation test on the Redmi 13C
  (`OnDeviceRegexTest`) compiled every regular-expression literal of the app with Android's ICU
  engine and ran the calculators on the phone: all 416 patterns compile and the calculators give
  the JVM's answers. It was written after the first 1.4.0 build stopped on launch, because ICU reads
  `[:.]` as the start of a POSIX class; two calculator patterns were rewritten.
- **Answer quality:** the field test in [evaluation](evaluation.md#the-7-october-140-candidate-on-60-new-held-out-questions-october-7):
  38% of the web-and-frontier reference on 60 held-out questions written after the last
  change, graded blind. References and graders are frontier models of one family, so this is a
  development measure, not an independent score.
- **Redmi 13C (6 GB, Android 13), local-test key, airplane mode with Wi-Fi and mobile data off:**
  the 1.4.0 source installed over 1.3.0 with its data kept; the five 1.4.0 collections imported from
  `.fapack` files and the older versions switched off by the import; a phrasebook line in 4 s, a fuel-cost split calculated in 0 s, the heat-stroke guide leading the
  answer in 40–52 s, an onion explanation quoting the English article's "Eye irritation" section in
  1 m 45 s, "Tell me about Japan" in 2 m 24 s, the best vegan restaurants in Berlin in 3 s, and
  "Where am I?" and Vitalik's question from the phone's own position (no vegan place is mapped within
  15 km of that rural spot, and the answer said so). Details and
  screenshots in [the device record](evidence/physical/redmi-13c-android13/README.md).
- **Not verified for this release:** the public-key signed APK on a phone, the in-app model and
  collection download on a phone, network traffic capture during research, a Pixel or GrapheneOS
  phone, and any 8 GB phone (so the optional 4B has not run on a phone that is offered it).

## v1.2.0 (October 2–3)

- **Automated:** 473 Android JVM tests ran with zero failures: 463 passed and 10 opt-in desktop
  tests were skipped in the ordinary run (the ranking and routing ones were run against the real packs on
  2 October). Python: 12 pack-tool, 57 script, 6 benchmark and 36 builder tests passed. The
  website's 25 browser tests passed. Android instrumentation sources compiled but were not run.
- **Answer quality:** the 24-question suite, run through the app's pipeline on desktop,
  finished at 19 pass, 3 partial and 2 fail after search fixes; see
  [evaluation](evaluation.md#v120-24-question-suite-october-2). That is author review on
  fixed questions, not an independent or held-out score.
- **Infinix X6840, Android 16, local-test key:** Encyclopedia and Travel guides imported;
  Berlin and Tokyo vegan lookups returned six cited fully vegan places each, offline, in
  15–17 s; place cards, source and place previews, the location explanation, History
  passages and delete, the Library collection menu and live dictation were exercised by hand.
  Details in the [release audit](compliance/release-audit.md#v120-release-handoff-2-october-2026).
- **Not verified for this release:** the public-key signed APK on a phone, a public-key update
  over 1.2.0-rc.1, the direct model download on a phone, network traffic capture during
  research, and a 12 GB GrapheneOS phone.

The sections below are earlier records, kept with the counts and hashes of their day.

## Files-first evidence scoping — 2026-09-28 follow-up

Attachment questions now skip library retrieval by default; clear user requests such as “also use my library” opt into combined evidence. The prompt packer independently enforces that boundary, marks each record's origin, and reserves a library passage alongside files for combined requests. No-attachment research is unchanged. Opt-in recognition currently supports documented English directives, not arbitrary natural-language intent in every language.

250 Android JVM tests and 46 Python tests passed (26 scripts, 12 packtool, 6 benchmarks, 2 tools). Regression checks cover files-only retrieval and source lists, quoted/negated library mentions, unrelated exclusions, explicit comparisons in both directions, instructions inside documents, unchanged no-attachment research, and library space alongside long files. The scope failures were observed before their fixes. Release assembly, signing verification, 16 KB zip alignment, and the APK packaging audit passed; release lint reports 0 errors and 36 warnings.

Optimized local APK installed with `install -r`: SHA-256 `08c5c663e14dbb7465f6d45d712c8677c5eb3aa4aa8838810d6b4bb16bf70ae0`, 99,928,340 bytes. Model and collections preserved. An initial device attempt encountered an input-dispatch ANR during Qwen3.5 2B startup and ended before an answer was obtained. Startup responsiveness is not claimed fixed by the evidence-scoping change.

After reopening and waiting for the model to become ready, the optimized APK answered the synthetic Green Lantern question using only its one attached text file, with three collections still enabled. It gave Green Lantern's 18-euro price, distinguished Blue Market's unconfirmed vegan options, and did not include the unrelated library venue. Its sole citation opened the exact attached passage. Answer details reported retrieval 7 ms, first token 29.18 s, total 46.32 s, and 69 generated tokens. This is a single smoke check with one attachment, not a controlled comparison with the earlier three-attachment run or a general performance claim.

A second question asked when Green Lantern closes tonight, which the file does not specify. The model declined to give a closing time and cited only the same attachment. Its prose was wordy and opened with an overbroad “no information regarding a venue” before correctly acknowledging the fictional restaurant; this is not a claim of flawless model output. Both checks ran with airplane mode on and Wi-Fi off. The temporary synthetic input was removed afterward; the two test answers remain in local History. No public release, commit, network trace, real-camera capture, or multi-model quality verification was performed in this follow-up.

The existing Research ready-card incorrectly reports “no sources cited” for canonical `[S1]` citations and “Completed in Just now” for durations below a minute; the Answer screen correctly reports the source and measured duration. These presentation defects were observed during the check and are not fixed by this evidence-scoping patch.

## New attachment path — initial offline device checks

The 2026-09-28 host check passed 244 Android JVM tests, 26 script tests, instrumentation APK assembly, signed local release assembly, the APK packaging audit, signing verification, and alignment checks. Release lint reported 0 errors and 36 warnings. The four added ARM64 OCR libraries use 16 KB-aligned load segments. After the device-discovered PDF fix, the 244 JVM tests, release build/lint, packaging audit, signature and alignment checks passed again. Installed optimized local APK SHA-256: `d733d65c64523ef42231f28a57a75d4e22b9d01b2e184dfcf9609f1718920b15` (99,925,620 bytes). This is not a published release.

The current source adds local Camera/Photos/Files extraction and cited attachment evidence. JVM checks cover streamed input limits, text validation, cancellation, cross-document evidence, two simulated context windows, and saved excerpts. On the Infinix Android 16 handset, airplane mode remained on and Wi-Fi off: all six synthetic reader tests passed in 3.655 seconds, covering printed-image OCR, EXIF rotation, PDF page numbers, mixed text/image PDFs, malformed PDFs, and blank/over-limit inputs. The mixed PDF test initially failed because the platform reported zero image contents despite a visible raster image; checking every rendered page fixed it. This test duration is not a research latency benchmark.

Instrumentation used a signed, non-minified release variant because optimized Kotlin methods caused the AndroidX test runner to crash before test execution. Use `-PfieldatlasTestBuildType=release` with both `:app:assembleRelease` and `:app:assembleReleaseAndroidTest` to reproduce that variant. Normal builds without this property remain optimized. The normal optimized APK was then restored with existing models and collections intact. Its real Files picker imported TXT and PDF, and its Photos picker imported a synthetic PNG; all reached Ready and their previews showed the expected text. Camera launch/cancel passed; actual camera capture is still pending. No personal documents were opened. There is no GrapheneOS, captured-network-traffic, or multi-model claim. See [supported inputs and limitations](attachments.md).

One optimized-release offline answer with the installed Qwen model used three synthetic attachments and three enabled collections. It correctly identified fictional Green Lantern as fully vegan with a price of 18 euros, and citation 1 opened the exact attached passage. However, it also included unrelated Pop Vegan Food from the library and incorrectly extended the synthetic/fictional label to it. **The answer-quality check did not fully pass.** Generation took several minutes (the UI was still generating at 3m17s); this was not a controlled latency benchmark. File-specific question scoping and separation of document metadata from library evidence need improvement before claiming reliable attachment-grounded answers. The temporary input files and instrumentation helper were removed; the synthetic answer remains in local history. Existing models and collections were preserved.

Remaining checks include real camera capture, cancellation during OCR and rotation, encrypted PDFs, reopening saved citations after restart, and two installed models. Measure offline output quality for explanation, comparison, synthesis, and dated travel questions. Compare baseline query time without attachments. A package audit alone does not prove no runtime network activity or satisfy the bounty quality bar.

## Earlier verification

Field Atlas treats release claims as evidence gates. The repository checks unit behavior, deterministic pack creation, manifest safety, native assembly, Android lint, release retention, and APK offline policy.

Device measurements in these records demonstrate the exact APK installed at the
time, not every later change on `main`. New source must pass the checks below and
be matched to a newly installed, tested APK before it is described as a reviewed
release.

The broader 2026-09-26 check passed 200 Android JVM tests, 12 packtool tests,
26 script tests, 6 benchmark-scorer tests, 1 travel-builder test, release lint,
release assembly, and the offline APK audit. After the voice fix, 203 Android
JVM tests, a release build, and the offline APK audit passed; the model-guide
update also passed 26 script tests and 1 tool test. The resulting unsigned APK
passed the offline audit with
SHA-256 `0247f35b5ad9319c165a775d58b16f289f68c73aae97d3d8178a2e609143adb8`.
It is not an installable signed release and these checks do not substitute for a
fresh real-device run or a current quality benchmark.

A separate debug package passed one Vosk microphone start/stop smoke test and one
Research-screen UI test on the Infinix. Those checks do not validate spoken-word
accuracy or the new minified release on-device; the installed signed app remains
the earlier 1.2.0 build.

On 2026-09-27, a later debug build passed Android JVM tests and a focused on-device
model-only Research UI test. On the Infinix, the exact former bundled Reference
pack was retired from that debug package while Qwen3.5 remained installed; the
Research screen showed `0 collections` and disclosed that uncited model answers
remain available. The checksum-matched fork biology vector pack was then imported
separately and appeared enabled in Library beside the selected Qwen3.5 model.
This was not a timed answer-quality run or a signed release test.

A focused 2026-09-27 debug smoke check imported the separately built, SHA-256-verified
Wikivoyage Eat pack onto the same Infinix alongside Qwen3.5 and the biology pack.
For “Best vegan restaurants in Berlin?”, the app returned four clickable local
listing citations in 3.31 seconds total with no model generation. The answer now
distinguishes listings that mention vegan options from fully vegan restaurants and
shows listing check dates (or that none was recorded). Opening source 3 showed the
exact Chay Viet passage describing a vegetarian restaurant with vegan dishes.
This is one historical,
source-backed city lookup—not proof of current restaurant quality, broad travel
coverage, or improved answers on difficult general questions. The Wikivoyage pack
remains a local optional artifact, not a published release download.

A separate short raw development clip, since moved out of the repository, recorded the already-ready Berlin answer and a tap into its matching local source. It is an interaction sample, not a capture of the measured 3.31-second search from start to finish.

The broader keyword-only Wikivoyage pack was built from the same pinned dump and passed pack verification. Its 351,581,170-byte artifact imported into Library on the Infinix with Qwen3.5 and the biology pack installed; airplane mode was on and Wi-Fi off. A first café lookup on the preceding debug build entered slow model generation, and Android showed an app-not-responding dialog. After a source-only fast path was added and the updated debug APK installed, “Which cafes are listed in Chiang Mai?” returned three cited listings and “Which museums are listed in Berlin?” returned four. Café citation 1 opened the matching saved Wikivoyage passage with a listing check date. This is a two-question lookup smoke check, not a broad quality benchmark, a fresh-data guarantee, or proof that complex model-generated research avoids the earlier stall. No release or public travel-data asset has been published from this experiment.

## Automated checks

```sh
python3 -m unittest discover packtool/tests
python3 -m unittest discover scripts/tests
python3 -m unittest discover benchmarks/tests
python3 -m unittest discover tools/tests
./gradlew --no-configuration-cache :app:testDebugUnitTest :app:lintRelease
./gradlew --no-configuration-cache :app:assembleRelease
./scripts/verify_offline.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

The 1.2.0 APK has `INTERNET` permission solely for user-started model and knowledge-pack downloads and catalog refresh. The APK audit still rejects cleartext traffic, network-control permissions, common remote-service clients, unexpected ABIs, and missing native inference libraries. This static check does not prove runtime network behavior; no traffic capture has been made.

## Resource boundaries

- Android API 33 or newer, ARM64 only.
- `StorageBudget` caps all installed packs at 50,000,000,000 bytes and keeps extraction headroom.
- Model weights remain in app-private storage and are mapped by the local llama.cpp runtime.
- The historical v1.1.x releases requested no network permission. Version 1.2.0 requests `INTERNET` for explicit downloads and catalog refresh; research remains local by design and does not depend on Google Play Services. Near-me questions read the phone's location through Android's `LocationManager`, after a permission prompt, and never send it.

## Physical-device record

The sanitized [Infinix X6840 record](evidence/physical/infinix-x6840-android16/README.md) documents a signed, radios-off model load and cited answers on a 4 GB Android 16 handset. It includes measured memory and timing observations, several explanation/comparison/synthesis queries, exact local source passages, screenshots, and device recordings without retaining the device serial or user data.

GrapheneOS compatibility follows the standard Android document picker, app-private storage, and bundled ARM64 runtime design. A dedicated physical Pixel/GrapheneOS record should still accompany any device-specific compatibility claim.
# Attachment presentation follow-up

Long Research attachment labels use middle ellipsis. Images have bounded, locally decoded thumbnails and a zoomable preview with separate close/remove actions; the staged preview remains available during OCR and after an OCR error. Attachment sources now show compact metadata and selectable text with a copy action, without duplicate source details. New evidence retains the detected file kind so misleading extensions do not create false PDF page labels. Older saved evidence retains the previous extension-based fallback.

257 JVM tests passed, release assembly and release lint passed (0 errors, 36 warnings), and Android UI test sources compiled. A new UI test covers an unreadable image's preview, zoom buttons, close, and removal. That instrumentation test has **not been executed on a device** in this follow-up; phone layout, pinch/pan, and installation remain pending. Debug lint in offline mode was blocked by an uncached AndroidX test dependency. No OCR language expansion or image understanding is included.
