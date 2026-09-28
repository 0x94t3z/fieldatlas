<p align="center">
  <img src="docs/branding/field-atlas-readme-icon.svg" alt="Field Atlas app icon" width="112" height="112">
</p>

<h1 align="center">Field Atlas</h1>

<p align="center">Ask questions from the knowledge saved on your Android phone—even with no connection.</p>

<p align="center">
  <a href="#get-started">Get started</a> ·
  <a href="#watch-it-work">Watch the demo</a> ·
  <a href="docs/installation.md">Setup help</a> ·
  <a href="docs/verification.md">What we tested</a>
</p>

Field Atlas runs a language model on the phone. You can also add local knowledge collections; when one contains a relevant passage, the answer can show a numbered citation that opens that passage. There is no account, search API, remote inference, or Google Play Services requirement. The current development build requests internet access only for user-started model or collection downloads; research uses installed files.

## At a glance

| | What it means |
| --- | --- |
| **Works offline** | Questions, local search, and answer generation stay on the device. Download or transfer the files you need *before* going offline. |
| **Model first** | The current development build can download its pinned model or import a compatible `.fapack`. It can answer without a knowledge collection, but that answer has no local citation. |
| **Collections are optional** | The current development build offers a recommended biology download in Library, or you can import another compatible knowledge `.fapack`. A collection adds searchable passages on its own topic; tap a numbered citation to inspect one. |
| **Android** | ARM64 phone, Android 13 (API 33) or newer. Tested on a physical Infinix X6840; other devices may differ. |

## Watch it work

The [original 47-second Infinix demo](docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4) shows airplane mode, an installed model and knowledge collection, an offline answer, and a source opened from a citation. It is **historical footage**, not a recording of the latest development UI.

<video src="docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4" controls width="360">
  <a href="docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4">Play the Infinix demo</a>
</video>

[![Preview of the original offline Infinix demo](docs/evidence/physical/infinix-x6840-android16/field-atlas-claim-preview.gif)](docs/evidence/physical/infinix-x6840-android16/field-atlas-current-demo.mp4)

More raw [multi-question device recordings](docs/evidence/physical/infinix-x6840-android16/README.md#additional-multi-query-evidence) are available. These recordings are historical evidence, not a benchmark of the current development build.

## Get started

> **Know which version you are using.** As checked on 2026-09-27, the latest public [signed release is v1.1.10](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10). The newer Qwen3.5, voice, vector-search, and restaurant-lookup changes in this repository are **not** in that download. A v1.2.0 integration APK was tested on the Infinix but was not published as a GitHub release. See [version status](#version-status).

For the public signed release:

1. [Download the APK from v1.1.10](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10) on a computer or your phone. The release also provides its checksum.
2. Get the **model `.fapack` for that same version** using the [v1.1.10 model instructions](https://github.com/0x94t3z/fieldatlas/blob/v1.1.10/MODELS.md). A raw `.gguf`, `.sha256` checksum, or knowledge pack is not a model pack. **There is no ready-made model pack attached to the release**, so this step currently needs a computer and the documented pack builder.
3. Copy the APK and model pack to the phone. Open the APK in the phone's file manager and approve the Android install prompt.
4. Open Field Atlas, choose the **model pack**, and wait for it to verify and prepare. Ask a question in **Research**.
5. Want answers linked to saved sources? Add a compatible **knowledge pack** in **Library**. If an answer has a numbered citation, tap it to read the supporting passage.

**Phone → install APK → import model pack → ask offline → optionally add a knowledge pack → inspect citations.**

This is not yet a one-tap install: the model file is large and must be prepared separately. If setup fails, use the plain-language [installation and troubleshooting guide](docs/installation.md). The current unpublished source build and its Qwen3.5 model have [separate instructions](docs/installation.md#current-development-build); do not mix its files with the v1.1.10 APK.

## Models, collections, and citations

| File | Required? | What it does | What it cannot prove |
| --- | --- | --- | --- |
| Model `.fapack` | Yes | Generates answers on the phone. | An uncited model answer is not evidence that a claim is true. |
| Knowledge `.fapack` | No | Supplies local passages the app can search and cite. | A citation only supports what its passage actually says. |
| Audio pack | No | Supports optional offline speech input in compatible builds. | It does not add factual knowledge. |

For example, the optional [Wikivoyage places-to-eat collection](DATASETS.md#wikivoyage-places-to-eat-optional-build) contains dated listings. A new [broader travel pack builder](DATASETS.md#broader-travel-places-keyword-only-builder) covers other place types without the expensive vector-generation step. Its full pack built and imported on a phone; simple offline café and museum lookup smoke checks returned cited listings. Broad answer quality remains unmeasured. Neither pack can check today's opening hours, menu, quality, or whether a place still exists. A biology collection is useful for biology; it is not a travel guide. Field Atlas falls back to an **uncited model answer** when no relevant local source is found.

## Version status

| Version | Public download? | What the evidence covers |
| --- | --- | --- |
| [v1.1.10 signed release](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10) | Yes | Downloadable APK and checksum. Use the setup instructions at that tag. |
| v1.2.0 integration APK | No | Tested on an Infinix; [historical audit](docs/compliance/release-audit.md). Not a downloadable release. |
| Current source / debug build | No signed public release yet | Qwen3.5, user-started model download, an optional catalog-listed biology download, voice and vector retrieval, UI updates, and the dated restaurant lookup are under development. The in-app download paths are not yet phone-verified. See [verification](docs/verification.md); do not assume older demo footage proves every new feature. |

The [Farcaster post](https://farcaster.xyz/0x94t3z.eth/0x9538cba8) shows the original public demo. The repository evidence is linked above. A useful offline answer on one phone is not an independent quality benchmark or a guarantee for every Android device.

## For developers and data builders

- [Build the Android app](docs/building.md)
- [Recommended model and other model definitions](MODELS.md)
- [Knowledge collections, their sources, and freshness](DATASETS.md)
- [Create your own `.fapack` collection](tools/README.md)
- [Testing, benchmark limits, and physical-device evidence](docs/verification.md)

The app is native Kotlin/Jetpack Compose with a pinned local llama.cpp runtime. The pack tools, tests, and offline-policy checks are in this repository. Large model and collection files are not committed; their sources and checksums are documented so they can be rebuilt or verified.
