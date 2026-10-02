<p align="center">
  <img src="docs/branding/field-atlas-readme-icon.svg" alt="Field Atlas app icon" width="160" height="160">
</p>

<h1 align="center">Field Atlas</h1>

<p align="center">Research, explore, and question the knowledge on your phone—even offline.</p>

<p align="center">
  <strong><a href="https://github.com/0x94t3z/fieldatlas/releases/download/v1.2.0/fieldatlas.apk">Download for Android</a></strong> ·
  <a href="#watch-it-work">Demo</a> ·
  <a href="#for-developers-and-data-builders">Docs</a> ·
  <a href="https://github.com/0x94t3z/fieldatlas">Source</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-174D3B?style=flat-square" alt="License: Apache 2.0"></a>
  <a href="#get-started"><img src="https://img.shields.io/badge/Android-13%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 13 or newer"></a>
  <a href="#tech-stack"><img src="https://img.shields.io/badge/Kotlin-native-7F52FF?style=flat-square&amp;logo=kotlin&amp;logoColor=white" alt="Kotlin: native"></a>
  <a href="#tech-stack"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square" alt="UI: Jetpack Compose"></a>
  <a href="#tech-stack"><img src="https://img.shields.io/badge/inference-llama.cpp-174D3B?style=flat-square" alt="Inference: llama.cpp"></a>
  <a href="#tech-stack"><img src="https://img.shields.io/badge/search-SQLite-417E87?style=flat-square&amp;logo=sqlite&amp;logoColor=white" alt="Search: SQLite"></a>
  <a href="#tech-stack"><img src="https://img.shields.io/badge/tools-Python-3776AB?style=flat-square&amp;logo=python&amp;logoColor=white" alt="Tools: Python"></a>
</p>

<p align="center"><a href="https://github.com/0x94t3z/fieldatlas/releases/tag/v1.2.0">v1.2.0 · Release</a> · Works offline after setup · No account required</p>

---

Field Atlas runs a language model on the phone. You can also add local knowledge collections; when one contains a relevant passage, the answer can show a numbered citation that opens that passage. There is no account, search API, remote inference, or Google Play Services requirement. User-started downloads and catalog refresh require internet access; research uses installed files.

## At a glance

| | What it means |
| --- | --- |
| **Works offline** | Questions, local search, and answer generation stay on the device. Download or transfer the files you need *before* going offline. |
| **Model first** | Download the default model or import a compatible `.fapack`. General questions can receive an unverified model explanation without a knowledge collection. |
| **Collections are optional** | Download Biology & Longevity or Travel Places during setup or from Library, or import a compatible knowledge `.fapack`. Collections add searchable passages on their own topics. |
| **Android** | ARM64 phone, Android 13 (API 33) or newer. Tested on a physical Infinix X6840; other devices may differ. |

## Watch it work

The [original 47-second Infinix demo](docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4) shows airplane mode, an installed model and knowledge collection, an offline answer, and a source opened from a citation. It is **historical footage**, not a recording of the latest development UI.

<video src="docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4" controls width="360">
  <a href="docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4">Play the Infinix demo</a>
</video>

[![Preview of the original offline Infinix demo](docs/evidence/physical/infinix-x6840-android16/field-atlas-claim-preview.gif)](docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4)

More raw [multi-question device recordings](docs/evidence/physical/infinix-x6840-android16/README.md#additional-multi-query-evidence) are available. These recordings are historical evidence, not a benchmark of 1.2.0.

## Get started

> **Latest release:** [v1.2.0](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.2.0). Answers can be inaccurate, and collection coverage is limited. Read the [release notes and known limitations](docs/releases/current.md).

On an Android 13+ ARM64 phone:

1. [Download Field Atlas](https://github.com/0x94t3z/fieldatlas/releases/download/v1.2.0/fieldatlas.apk). A [SHA-256 checksum](https://github.com/0x94t3z/fieldatlas/releases/download/v1.2.0/fieldatlas.apk.sha256) is available to verify the APK.
2. Open the APK on your phone and follow Android’s installation prompts.
3. In setup, **download the default model** while online, or **import a saved model `.fapack`**. Wait for verification and installation to finish. A raw `.gguf` or checksum file is not an importable model pack.
4. Optionally download collections such as **Encyclopedia**, **Travel guides**, **Vegan places**, **Travel places** or **Biology & Longevity**, or import a saved knowledge `.fapack`. You can add more later in **Library**.
5. Open **Research**, ask a question, and tap **Start research**. If the answer includes numbered citations, open them to inspect the saved passages.

**Install → download or import a model → optionally add knowledge → research offline.**

The APK is about 100 MB; the model and collections require additional downloads and storage. Finish setup before going offline. Model/Biology download recovery has not been independently verified for this release; compatible saved-pack imports remain an alternative.

**Updating a test build?** The public APK cannot update a build signed with the local-test key. Preserve local data before considering a reinstall—uninstalling removes app-private data. See the [installation notes](docs/releases/current.md#installation). For the older v1.1.10 release, use its [version-specific setup guide](https://github.com/0x94t3z/fieldatlas/blob/v1.1.10/docs/installation.md).

## Models, collections, and citations

| File | Required? | What it does | What it cannot prove |
| --- | --- | --- | --- |
| Model `.fapack` | Yes | Generates answers on the phone. | An uncited model answer is not evidence that a claim is true. |
| Knowledge `.fapack` | No | Supplies local passages the app can search and cite. | A citation only supports what its passage actually says. |
| Audio pack | No | Supports optional offline speech input in compatible builds. | It does not add factual knowledge. |

The [Travel Places collection](DATASETS.md#broader-travel-places-keyword-only-builder) contains dated Wikivoyage listings for restaurants, hotels, sights, shops, and other places. It cannot check today's opening hours, menus, quality, or whether a place still exists. Biology & Longevity covers specialist material, not every subject. The Encyclopedia collection is Simple English Wikipedia (a December 2025 snapshot): short leads, not full articles.

General questions may receive an **unverified model explanation** when local evidence is missing. Unsupported source-only requests return an evidence-gap message. A citation is a way to inspect a passage, not proof that an answer is correct; check the passage's wording, context, and date.

## Version status

Releases since 1.2.0 include [research with local documents and photos](docs/attachments.md): text extraction stays on the phone and selected excerpts can become cited sources. This is text recognition, not general image understanding; multilingual quality is not guaranteed.

On the [24-question release suite](docs/evaluation.md#v120-24-question-suite-october-2), run through the 1.2.0 research pipeline on a desktop, 19 answers passed review, 3 partly passed and 2 failed. That is the author's review on fixed questions, without an online baseline: a development measure, not an independent accuracy score.

| Version | Public download? | What the evidence covers |
| --- | --- | --- |
| [v1.2.0 release](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.2.0) | Yes — signed APK and checksum | App 1.2.0 (15). Adds Encyclopedia, Travel guides and Vegan places collections, near-me lookups, place cards, source previews and live dictation. Known answer-quality and verification gaps remain; see the [release notes](docs/releases/current.md). |
| [v1.2.0-rc.1 previous prerelease](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.2.0-rc.1) | Yes — signed APK and checksum | App 1.2.0 (14). Includes updated setup, catalog downloads, attachments, and citation handling. Known answer-quality and verification gaps remain; see the [release audit](docs/compliance/release-audit.md). |
| [v1.1.10 previous stable release](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10) | Yes | Older features and setup flow. Use the instructions at that tag. |
| Current source / local builds | Build from source | May differ from published artifacts. Local-test signing keys are not compatible with public-release updates. |

The [Farcaster post](https://farcaster.xyz/0x94t3z.eth/0x9538cba8) shows the original public demo. The repository evidence is linked above. A useful offline answer on one phone is not an independent quality benchmark or a guarantee for every Android device.

## Tech stack

| Layer | Technology | Role |
| --- | --- | --- |
| Android app | Kotlin · Jetpack Compose · Material 3 | Native screens and on-device workflows |
| Model runtime | llama.cpp · C++ · JNI | Local language-model inference |
| Local search | SQLite · FTS5 | Search through installed knowledge passages |
| App state | Kotlin Coroutines · Flow | Asynchronous work and reactive UI state |
| Pack tooling | Python · `.fapack` | Build and validate model and knowledge archives |
| Build tooling | Gradle · Android NDK · CMake | Build the app and native runtime |

## For developers and data builders

- [Build the Android app](docs/building.md)
- [Recommended model and other model definitions](MODELS.md)
- [Knowledge collections, their sources, and freshness](DATASETS.md)
- [Create your own `.fapack` collection](tools/README.md)
- [Testing, benchmark limits, and physical-device evidence](docs/verification.md)

The app is native Kotlin/Jetpack Compose with a pinned local llama.cpp runtime. The pack tools, tests, and offline-policy checks are in this repository. Large model and collection files are not committed; their sources and checksums are documented so they can be rebuilt or verified.
