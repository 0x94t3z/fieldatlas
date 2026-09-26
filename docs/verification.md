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

The offline-policy audit rejects network permissions, cleartext traffic, common network clients, Firebase and Google Play Services integrations, unexpected ABIs, and missing native inference libraries.

## Resource boundaries

- Android API 33 or newer, ARM64 only.
- `StorageBudget` caps all installed packs at 50,000,000,000 bytes and keeps extraction headroom.
- Model weights remain in app-private storage and are mapped by the local llama.cpp runtime.
- The app requests no network permission and core use does not depend on Google Play Services.

## Physical-device record

The sanitized [Infinix X6840 record](evidence/physical/infinix-x6840-android16/README.md) documents a signed, radios-off model load and cited answers on a 4 GB Android 16 handset. It includes measured memory and timing observations, several explanation/comparison/synthesis queries, exact local source passages, screenshots, and device recordings without retaining the device serial or user data.

GrapheneOS compatibility follows the standard Android document picker, app-private storage, and bundled ARM64 runtime design. A dedicated physical Pixel/GrapheneOS record should still accompany any device-specific compatibility claim.
