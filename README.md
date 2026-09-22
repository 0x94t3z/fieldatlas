# Field Atlas

**Offline Research for Android**

*Answers grounded in what you carry.*

Field Atlas retrieves local evidence and produces cited answers with an on-device model. It has no network permission, makes no remote inference or search requests, and does not require Google Play Services. Model and knowledge packs are selected from local storage and verified before installation.

The signed Android release has been exercised offline on a physical Infinix X6840. Reproducible checks cover the app, pack format, native runtime, offline policy, and release artifacts.

## Project layout

- `app/` — native Kotlin/Jetpack Compose Android application and tests
- `third_party/llama.cpp/` — exact pinned on-device inference runtime
- `packtool/` and `fixtures/` — deterministic offline knowledge-pack builder and starter corpus
- `models/` — immutable model provenance and checksum configuration
- `benchmarks/` — frozen research-quality questions and paired scorer
- `scripts/` — toolchain, asset, APK, and physical-device verification commands
- `docs/` — installation, evaluation, device testing, verification, and physical-device evidence
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

1. Download the signed [Field Atlas 1.1.1 APK](https://github.com/0x94t3z/fieldatlas/raw/refs/heads/main/releases/field-atlas-v1.1.1.apk), verify SHA-256 `c66e3d7943adb290a65108e20c270a17fc4b0f3f2ab6f03853d808e555af136e`, and install it. For a local build, transfer the installable debug APK at `app/build/outputs/apk/debug/app-debug.apk`. The unsigned release artifact is for reproducibility checks and is not installable.
2. Install the APK. Android may require permission for the file manager to install unknown apps.
3. Open Field Atlas and import the model and knowledge packs with the system document picker.
4. Turn on airplane mode and explicitly disable Wi-Fi and mobile data.
5. Tap **Prepare for research**, enter a question, then tap **Start research**.
6. Read the answer and select a numbered citation to inspect its exact supporting passage.
7. Open **More** for privacy details, the guided device benchmark, and diagnostics export.
8. Use **Release model memory** in More when the local model is no longer needed.

Detailed procedures are in [installation](docs/installation.md), [device testing](docs/device-testing.md), and [evaluation](docs/evaluation.md).

## Architecture and privacy

The Compose UI calls a research orchestrator that sanitizes an FTS5 query, retrieves bounded passages, packs a citation-constrained prompt, and streams llama.cpp output. The model is loaded only after an explicit tap. Import rejects unknown manifest fields, unsafe ZIP paths, compression, encryption, undeclared files, bad sizes, bad hashes, duplicate versions, and storage-budget violations. Research questions remain on-device; diagnostic export excludes the latest question unless the user opts in.

## Verification

Unit, pack reproducibility, manifest, lint, native assembly, APK offline-policy, and physical-device results are tracked in [verification](docs/verification.md). Release assets include checksums so the installed build can be matched to the reviewed artifact.
