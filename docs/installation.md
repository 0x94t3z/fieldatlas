# Install Field Atlas

[Back to the overview](../README.md)

The current release is **v1.2.0**, for Android 13+ ARM64 devices. It uses Qwen3.5 2B, installed separately by download or matching model `.fapack`. **Finish model setup before going offline.** The model is about 1.4 GB; the APK and optional collections need additional storage. Answers can be inaccurate; read the known limitations.

## Choose your path

| If you want to… | Use this | Important difference |
| --- | --- | --- |
| Use the current release | [Signed v1.2.0](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.2.0) | Qwen3.5 2B; optional collections are separate. Read the [known limitations](releases/current.md). |
| Use the previous stable release | [v1.1.10](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10) | Follow its [version-specific instructions](https://github.com/0x94t3z/fieldatlas/blob/v1.1.10/docs/installation.md), including Qwen3 1.7B. |
| Test source changes | [Build from source](building.md) | Qwen3.5 2B. Changes after the release are not in the published APK. A debug build is a separate app, not an update to the signed release. |

Check the version in **More**. Do not follow Qwen3.5 instructions for a 1.1.10 APK. The public signing key cannot update a local-test-key build; do not uninstall to work around this, because uninstalling removes app-private data.

## Current release: phone setup

1. Download `fieldatlas.apk` and `fieldatlas.apk.sha256` from [v1.2.0](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.2.0). Verify the checksum if possible.
2. Open the APK on an Android 13+ ARM64 phone. Allow installation from the app opening the APK if Android asks.
3. In setup, download the recommended Qwen3.5 2B model, or import a matching `.fapack` using the [model guide at this release](https://github.com/0x94t3z/fieldatlas/blob/v1.2.0/MODELS.md). A raw `.gguf` is not a model pack.
4. Optionally download or import a knowledge collection. A model is required; collections provide saved passages for their covered topics.
5. Start research once the model is installed. Allow time for first model preparation.
6. Open source citations to inspect actual passages. An unverified model explanation is not proof that the saved sources support it.

After setup, turn on airplane mode and turn Wi-Fi off to try offline research. This release has network permission for explicit downloads and catalog refresh, not remote inference. Download recovery and network-enabled traffic capture have not been independently verified for this release.

## What setup offers in 1.2.0

The app needs **only an installed model** to answer questions. Setup shows the model and the optional collections together, grouped by category, straight from the download catalog:

| Collection | Download | Covers |
| --- | ---: | --- |
| Encyclopedia | about 316 MB | Simple English Wikipedia, December 2025 |
| Travel guides | about 375 MB | Wikivoyage destination guides, December 2025 |
| Travel places | about 352 MB | Dated Wikivoyage listings: sights, food, hotels, shops |
| Vegan places | about 40 MB | OpenStreetMap places tagged fully vegan or with vegan options, worldwide |
| Biology & longevity | about 1.57 GB | Biology and aging research passages |

After **Refresh collections**, Library also offers **Essentials** (about 1.78 GB): pharmacies, hospitals, ATMs, toilets, drinking water, stations, supermarkets and hostels OpenStreetMap maps worldwide, for questions such as "nearest pharmacy in Jakarta" or "ATM near me".

Choose **Download recommended model** (internet needed, about 1.4 GB) or import the Qwen3.5 2B `.fapack` built with the command in [MODELS.md](../MODELS.md); both install the same pinned weights, and the app verifies the exact size and SHA-256 before using either. The direct model download has not been validated on a physical phone, so keep the pack-import option in mind.

You can switch apps while a collection downloads; an active transfer has a foreground notification. Only one download or import runs at a time. Pause keeps the downloaded bytes, and reopening the app after a process interruption resumes the transfer. If a transfer fails, tap Resume in setup or Library to try again; a server that does not support range requests may require a full restart. Once a model is installed, choose **Start researching** with or without knowledge; more collections can be added later in Library. Biology is hosted by the fork; the other collections are project release assets. The manual import path accepts `.fapack`, not raw `.gguf` files.

Place questions about where you are ("vegan restaurants near me") use the phone's own location through Android's location service, only after a short explanation and Android's permission prompt; naming a city works without location. Voice input uses a bundled English Vosk speech model and, if you choose to use it, microphone permission; words appear in the question box as you speak, and nothing is submitted automatically. You can keep more than one verified model and switch the active one in Library. The answer flow prepares a selected model automatically; **Prepare for research** is a retry if loading needs help.

Developers can also [build a debug APK](building.md) (`xyz.fieldatlas.debug`, installed alongside the signed app) or a [Wikipedia mini pack](../DATASETS.md#wikipedia-mini-keyword-only-builder), which is unpublished and has not been phone-tested.

## Common problems

### Refreshing collections

Setup and Library offer **Refresh collections**, an explicit online provisioning action. Opening the app or those screens does not refresh automatically. The app uses its bundled list until a refresh succeeds, then keeps the accepted catalog locally for offline use. Refresh failure leaves that list and installed packs available. Refresh downloads metadata only; each pack still requires a separate confirmed download.

The catalog is maintained at the repository's `main/app/src/main/assets/knowledge/catalog.json` path and fetched over HTTPS. New compatible entries can appear without an APK update once catalog changes are published there. Imported packs need not appear in this catalog. If refresh fails or the endpoint is unavailable, use bundled listings or import a saved pack. Refresh has not been checked with a network traffic capture.

The trust boundary is the maintained GitHub repository and HTTPS. Pack checksums detect mismatches against the accepted catalog, not a compromised publisher. No automatic pack updates or deletions occur. Provisioning checks count the APK, native libraries, app data/cache and staged files, reserve extraction space, and serialize app provisioning operations. They are not an OS-enforced quota on subsequent cache/history growth; final total-storage measurement remains a release gate.

### Troubleshooting

| What you see | What to check |
| --- | --- |
| “Unsupported file” or model import fails | Select the **model `.fapack`**, not a raw `.gguf`, `.sha256` file, or knowledge pack. Use the model instructions matching your APK version. |
| Hash or verification failure | Recopy or redownload the file, then verify its checksum on the computer. Do not bypass verification. |
| Not enough space | Leave room for the downloaded archive, installed model, and Android's working space. |
| Model takes time to load | The first preparation can take tens of seconds on a modest phone. Let it finish; if it fails, retry from Research and check **More → diagnostics**. |
| Answer has no citation | The model can answer without local evidence. Add a relevant knowledge collection for source-backed answers; a collection on another topic will not help. |
| A venue answer might be stale | The offline pack cannot check live opening hours, menus, or whether a place still exists. Read the listing date and confirm details separately when online. |
| Phone gets hot or slow | Stop generation, release model memory from **More**, and allow the phone to cool. |

Installed models and imported packs live in app-private storage. Uninstalling the app removes them, so keep your original `.fapack` files if you may need to reinstall. The unsigned release APK made by a local build is for inspection only; do not sideload it. See [verification](verification.md) for what was actually tested on a device.
