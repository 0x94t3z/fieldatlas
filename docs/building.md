# Build Field Atlas from source

This page is for developers. If you just want to try the app, start with the [public setup steps](../README.md#get-started). A debug APK built here is a separate Android package from the signed public release and is not a new published version.

## Requirements

JDK 17, Gradle 8.13, Android Gradle Plugin 8.11.1, Kotlin 2.1.0, Compose BOM 2025.06.01, Android SDK 36, Build Tools 35.0.0, NDK 29.0.13113456, CMake 3.31.6, and Git submodules. Exact Maven versions are in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). The APK is ARM64-only and targets Android API 33 or newer.

## Build and check

```sh
git clone --recurse-submodules https://github.com/0x94t3z/fieldatlas.git
cd fieldatlas
env -i PATH="$PATH" ./scripts/install_toolchain.sh
git -C third_party/llama.cpp apply ../../scripts/patches/ai-chat-generation-fixes.patch
./gradlew --no-configuration-cache \
  :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease
./scripts/verify_offline.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

`install_toolchain.sh` finds an existing SDK from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdkmanager`, or the supported Homebrew location. It validates pinned packages and writes a repository-local, git-ignored `local.properties`; it does not change shell profiles.

The pinned submodule is `ggml-org/llama.cpp@60081bb2b5b3294165a4d67c5cbeebe74c868014`. Apply the small Field Atlas runtime patch only once. CI applies it automatically; see [patch notes](../scripts/patches/README.md). Do not apply it to a different llama.cpp commit without review.

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk` (`xyz.fieldatlas.debug`). The unsigned release APK is for reproducibility checks, **not** installation. A signed release requires the owner's signing configuration and a separate release audit.

## Prepare offline files

The current source uses the Qwen3.5 2B model pack from [MODELS.md](../MODELS.md). The app imports `.fapack` files, not raw `.gguf` models. Knowledge packs are optional; see [DATASETS.md](../DATASETS.md). `tools/build_model_packs.py`, `tools/build_wikivoyage_eat_pack.py`, `tools/build_vector_pack.py`, and the other [pack tools](../tools/README.md) recreate large artifacts from pinned inputs. Model weights, audio packs, and most knowledge packs are intentionally not committed.

## Repository map

| Path | Purpose |
| --- | --- |
| `app/` | Android application and tests |
| `third_party/llama.cpp/` | Pinned on-device inference runtime |
| `packtool/`, `fixtures/`, `tools/` | Pack creation and reproducible data inputs |
| `models/` | Model provenance and expected hashes |
| `benchmarks/` | Frozen questions and paired scorer |
| `scripts/` | Toolchain, APK, and device-verification commands |
| `docs/` | Installation, evaluations, and device evidence |
| `.github/workflows/` | CI build and offline-policy checks |

The app searches relevant local packs first. If the initial result is weak, the selected on-device model can produce one short query expansion. Only passages that pass relevance checks can enter a cited answer. Otherwise the app uses an explicitly uncited model-only answer. Simple local place-listing questions can use a faster source-backed path with dates and freshness caveats. Other answers stream from llama.cpp. Import verifies declared pack files, sizes, hashes, manifest fields, storage limits, and safe archive paths before installation.

For test commands and the limits of current device evidence, see [verification](verification.md).
