# Verification

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
