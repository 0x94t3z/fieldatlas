# Field Atlas

**Offline Research for Android**

*Answers grounded in what you carry.*

Field Atlas retrieves local evidence and produces cited answers with an on-device model. It has no network permission, makes no remote inference or search requests, and does not require Google Play Services. Model and knowledge packs are selected from local storage and verified before installation.

This repository is a POIDH bounty 31 candidate. Software verification is automated and a signed radios-off release has been exercised on a real Infinix SMART 20 / X6840. A GrapheneOS-compatible Pixel run, paired baseline score, and public X or Farcaster demo remain outstanding.

## Project layout

- `app/` — native Kotlin/Jetpack Compose Android application and tests
- `third_party/llama.cpp/` — exact pinned on-device inference runtime
- `packtool/` and `fixtures/` — deterministic offline knowledge-pack builder and starter corpus
- `models/` — immutable model provenance and checksum configuration
- `benchmarks/` — frozen research-quality questions and paired scorer
- `scripts/` — toolchain, asset, APK, and physical-device verification commands
- `docs/` — installation, evaluation, device testing, and requirement-by-requirement audit
- `.github/workflows/` — reproducible CI build and release audit

## Build

Requirements are JDK 17, Gradle 8.13, Android Gradle Plugin 8.11.1, Kotlin 2.1.0, Compose BOM 2025.06.01, Android SDK 36, Build Tools 35.0.0, NDK 29.0.13113456, CMake 3.31.6, and Git submodules. Exact Maven versions live in `gradle/libs.versions.toml`.

```sh
git clone --recurse-submodules https://github.com/0x94t3z/fieldatlas.git
cd fieldatlas
env -i PATH="$PATH" ./scripts/install_toolchain.sh
./gradlew --no-configuration-cache \
  :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease
./scripts/verify_offline.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

`install_toolchain.sh` discovers an existing SDK from `ANDROID_HOME`,
`ANDROID_SDK_ROOT`, `sdkmanager`, or the supported Homebrew location. It validates
the pinned packages and writes the repository-local, git-ignored
`local.properties`; it does not edit shell profiles.

The pinned submodule is `ggml-org/llama.cpp@60081bb2b5b3294165a4d67c5cbeebe74c868014`. The release APK contains ARM64 libraries only and supports Android API 33 or newer.

## Prepare offline assets

Build the project-authored starter knowledge pack. It is labelled **Demo coverage** in the app; it validates the workflow and is not presented as broad research coverage.

```sh
./scripts/build_starter_pack.sh
```

Build the model pack using the verified steps in [MODELS.md](MODELS.md). The starter corpus proves the workflow but is not sufficient for competitive research quality; its exact scope and licensing are in [DATASETS.md](DATASETS.md).

## Install and use

1. Download the signed APK from the [v1.0.0 release](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.0.0), or transfer the installable debug APK at `app/build/outputs/apk/debug/app-debug.apk` for a local build. The unsigned release audit artifact is not installable.
2. Install the APK. Android may require permission for the file manager to install unknown apps.
3. Open Field Atlas and import the model and knowledge packs with the system document picker.
4. Turn on airplane mode and explicitly disable Wi-Fi and mobile data.
5. Tap **Load model**, enter a question, then tap **Research offline**.
6. Open each `[S#]` evidence card to inspect the exact supporting passage.
7. Open **Proof** to distinguish manifest audits, Android-reported facts, and measured run data. From there, the guided device benchmark runs one frozen prompt per tap and exports raw evidence.
8. Use **Unload model** to release native model memory.

Detailed procedures are in [installation](docs/installation.md), [device testing](docs/device-testing.md), and [evaluation](docs/evaluation.md).

## Architecture and privacy

The Compose UI calls a research orchestrator that sanitizes an FTS5 query, retrieves bounded passages, packs a citation-constrained prompt, and streams llama.cpp output. The model is loaded only after an explicit tap. Import rejects unknown manifest fields, unsafe ZIP paths, compression, encryption, undeclared files, bad sizes, bad hashes, duplicate versions, and storage-budget violations. Research questions remain on-device; diagnostic export excludes the latest question unless the user opts in.

## Honest status

Unit, pack reproducibility, manifest, lint, native assembly, APK offline audit, and physical-device results are tracked separately in `docs/compliance/release-audit.md`. The public `v1.0.0` release contains the signed APK, starter pack, physical-device demo, and radios-off proof image. Remaining evidence gaps are recorded directly in the audit.
