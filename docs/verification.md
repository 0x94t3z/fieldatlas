# Verification

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

The signed 1.2.0 APK in the [release audit](compliance/release-audit.md) predates
the latest source edits. Its device measurements demonstrate that exact older APK,
not every change on `main`. New source must pass the checks below and be matched
to a newly installed, tested APK before it is described as a reviewed release.

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

A separate [short raw development clip](evidence/development/README.md) records the already-ready Berlin answer and a tap into its matching local source. It is an interaction sample, not a capture of the measured 3.31-second search from start to finish.

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

The current development APK has `INTERNET` permission solely for user-started model and knowledge-pack downloads. The APK audit still rejects cleartext traffic, network-control permissions, common remote-service clients, unexpected ABIs, and missing native inference libraries. This static check does not prove the new provisioning paths or offline research behavior; both still need a fresh physical-device test before release.

## Resource boundaries

- Android API 33 or newer, ARM64 only.
- `StorageBudget` caps all installed packs at 50,000,000,000 bytes and keeps extraction headroom.
- Model weights remain in app-private storage and are mapped by the local llama.cpp runtime.
- The historical signed release requested no network permission. The current development build requests `INTERNET` for explicit model provisioning; core research remains local by design and does not depend on Google Play Services. This change has not yet been phone-verified.

## Physical-device record

The sanitized [Infinix X6840 record](evidence/physical/infinix-x6840-android16/README.md) documents a signed, radios-off model load and cited answers on a 4 GB Android 16 handset. It includes measured memory and timing observations, several explanation/comparison/synthesis queries, exact local source passages, screenshots, and device recordings without retaining the device serial or user data.

GrapheneOS compatibility follows the standard Android document picker, app-private storage, and bundled ARM64 runtime design. A dedicated physical Pixel/GrapheneOS record should still accompany any device-specific compatibility claim.
# Attachment presentation follow-up

Long Research attachment labels use middle ellipsis. Images have bounded, locally decoded thumbnails and a zoomable preview with separate close/remove actions; the staged preview remains available during OCR and after an OCR error. Attachment sources now show compact metadata and selectable text with a copy action, without duplicate source details. New evidence retains the detected file kind so misleading extensions do not create false PDF page labels. Older saved evidence retains the previous extension-based fallback.

257 JVM tests passed, release assembly and release lint passed (0 errors, 36 warnings), and Android UI test sources compiled. A new UI test covers an unreadable image's preview, zoom buttons, close, and removal. That instrumentation test has **not been executed on a device** in this follow-up; phone layout, pinch/pan, and installation remain pending. Debug lint in offline mode was blocked by an uncached AndroidX test dependency. No OCR language expansion or image understanding is included.
