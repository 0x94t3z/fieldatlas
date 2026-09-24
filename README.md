# Field Atlas

**Offline Research for Android**

*Answers grounded in what you carry.*

Field Atlas answers with an on-device model and uses its bundled or imported local knowledge packs when it can ground a response in citations. If no matching local source is found, it can still give an uncited offline model answer and labels that path clearly. It has no network permission, makes no remote inference or search requests, and does not require Google Play Services. User-supplied packs are selected from local storage and verified before installation.

The signed Android release has been exercised offline on a physical Infinix X6840. Reproducible checks cover the app, pack format, native runtime, offline policy, and release artifacts.

## Demo

<video src="https://github.com/0x94t3z/fieldatlas/raw/refs/heads/main/docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4" controls width="720">
  <a href="https://github.com/0x94t3z/fieldatlas/raw/refs/heads/main/docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4">Play the Field Atlas demo</a>
</video>

[![Field Atlas offline Android demo](docs/evidence/physical/infinix-x6840-android16/field-atlas-claim-preview.gif)](https://github.com/0x94t3z/fieldatlas/raw/refs/heads/main/docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4)

The recording shows Field Atlas running on a physical Infinix Android phone with radios off, using its installed local model and knowledge pack to produce a cited answer. Select the animated preview if your Markdown viewer does not display the video player. Additional unedited [multi-query device evidence](docs/evidence/physical/infinix-x6840-android16/README.md#additional-multi-query-evidence) covers water safety, solar storage, and a cross-source treatment recommendation.

Public proof: [Farcaster post](https://farcaster.xyz/0x94t3z.eth/0x9538cba8).

## Project layout

- `app/` — native Kotlin/Jetpack Compose Android application and tests
- `third_party/llama.cpp/` — exact pinned on-device inference runtime
- `packtool/` and `fixtures/` — deterministic offline knowledge-pack builder and curated reference corpus
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

Field Atlas needs one user-supplied local file for the full research workflow:

- a model pack, built from the documented Qwen3 1.7B GGUF model
- the APK's bundled Reference knowledge pack, installed automatically on first launch

The model pack lets Field Atlas answer offline. The bundled Reference pack lets it retrieve local passages, add citations, and open exact supporting sources. The app imports `.fapack` files. The raw `.gguf` model is used to create the model pack first; selecting the raw `.gguf` in the Android app is not the expected install path. The exact model-pack command is in [MODELS.md](MODELS.md). The bundled Reference pack can be reproduced locally:

```sh
./scripts/build_reference_pack.sh
```

Build the model pack using the verified steps in [MODELS.md](MODELS.md). The Reference pack is focused coverage, not a comprehensive encyclopedia; questions outside it fall back to an uncited offline model answer. Its exact scope and licensing are in [DATASETS.md](DATASETS.md).

## Install and use

1. Download the signed [Field Atlas 1.1.6 APK](https://github.com/0x94t3z/fieldatlas/releases/download/v1.1.6/field-atlas-v1.1.6.apk).
2. Download or build the model `.fapack`.
3. Copy the APK and model `.fapack` to the Android phone.
4. Open the APK from the phone's file manager and install it. Android may ask to allow installs from that file manager.
5. Open Field Atlas, tap **Choose model pack**, and select the model `.fapack`.
6. Open Research, tap **Prepare for research**, enter a question, then tap **Start research**.
7. Read the answer and select a numbered citation to inspect its exact supporting passage.
8. Turn on airplane mode when testing offline behavior. Field Atlas has no network permission, so research works from the files on the phone.
9. Open **More** for privacy details, the guided device benchmark, diagnostics export, and **Release model memory**.

The release APK SHA-256 is `3b9edba447cfad6e777e2243ab3ae9f9b75973493c6ebc8a7993aafbad654783`. For a local build, transfer the installable debug APK at `app/build/outputs/apk/debug/app-debug.apk`. The unsigned release artifact is for reproducibility checks and is not installable.

Detailed procedures are in [installation](docs/installation.md), [device testing](docs/device-testing.md), and [evaluation](docs/evaluation.md).

## Architecture and privacy

The Compose UI calls a research orchestrator that sanitizes an FTS5 query, retrieves bounded passages, packs a citation-constrained prompt when local sources exist, and streams llama.cpp output. When retrieval finds no matching local source, the orchestrator switches to an uncited offline-model prompt instead of making a network request. The model is loaded only after an explicit tap. Import rejects unknown manifest fields, unsafe ZIP paths, compression, encryption, undeclared files, bad sizes, bad hashes, duplicate versions, and storage-budget violations. Research questions remain on-device; diagnostic export excludes the latest question unless the user opts in.

## Verification

Unit, pack reproducibility, manifest, lint, native assembly, APK offline-policy, and physical-device results are tracked in [verification](docs/verification.md). Release assets include checksums so the installed build can be matched to the reviewed artifact.
