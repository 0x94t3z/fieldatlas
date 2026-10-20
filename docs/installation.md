# Install Field Atlas

[Back to the overview](../README.md)

The current release is **v1.4.2**, for Android 13+ ARM64 devices. It uses Qwen3.5 2B, Qwen3.5 4B on a phone with 6 GB of memory or more, or Gemma 4 26B on a phone with 12 GB, installed separately by download or matching model `.fapack`. **Finish model setup before going offline.** The 2B model is about 1.4 GB; the APK and optional collections need additional storage. Answers can be inaccurate; read the known limitations.

## Choose your path

| If you want to… | Use this | Important difference |
| --- | --- | --- |
| Use the current release | [Signed v1.4.2](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.4.2) | Qwen3.5 2B, the 4B from 6 GB, or Gemma 4 26B on a 12 GB phone; optional collections and voice are separate. Read the [known limitations](releases/current.md). |
| Use the previous stable release | [v1.4.1](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.4.1) | Follow its [version-specific instructions](https://github.com/0x94t3z/fieldatlas/blob/v1.4.1/docs/installation.md). |
| Test source changes | [Build from source](building.md) | Qwen3.5 2B. Changes after the release are not in the published APK. A debug build is a separate app, not an update to the signed release. |

Check the version in **More**. Do not follow Qwen3.5 instructions for a 1.1.10 APK. The public signing key cannot update a local-test-key build; do not uninstall to work around this, because uninstalling removes app-private data.

## Current release: phone setup

1. Download `fieldatlas.apk` and `fieldatlas.apk.sha256` from [v1.4.2](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.4.2). Verify the checksum if possible.
2. Open the APK on an Android 13+ ARM64 phone. Allow installation from the app opening the APK if Android asks.
3. In setup, download the model it offers first (Gemma 4 26B on a phone with 12 GB of memory, Qwen3.5 4B from 6 GB, Qwen3.5 2B otherwise), or import a matching `.fapack` using the [model guide at this release](https://github.com/0x94t3z/fieldatlas/blob/v1.4.2/MODELS.md). A raw `.gguf` is not a model pack.
4. Optionally download or import a knowledge collection, and the Supertonic 3 reading voice (about 146 MB). A model is required; collections provide saved passages for their covered topics.
5. Start research once the model is installed. Allow time for first model preparation.
6. Open source citations to inspect actual passages. An unverified model explanation is not proof that the saved sources support it.

After setup, turn on airplane mode and turn Wi-Fi off to try offline research. This release has network permission for explicit downloads and catalog refresh, not remote inference. Download resume after a dropped connection was checked on a phone for 1.4.1; network traffic during research has not been captured.

## What setup offers in 1.4.2

The app needs **only an installed model** to answer questions. The 44 emergency guides and the
emergency-number table for 242 countries ship inside the app and work before any download. Setup
shows the model and the optional collections together, grouped by category, straight from the
download catalog ([sizes, sources and checksums](../DATASETS.md#collections-the-app-offers)):

| Collection | Download | Covers |
| --- | ---: | --- |
| Encyclopedia | about 10.64 GB (24.35 GB installed) | English and Simple English Wikipedia in one collection, 8.39 million passages; dated snapshots |
| Travel guides | about 290 MB (660 MB installed) | Wikivoyage guides by section, September 2026: getting in and around, money, safety, health, etiquette, travel topics and phrasebooks |
| Travel places | about 215 MB (650 MB installed) | Dated Wikivoyage listings: sights, food, hotels, shops |
| Vegan places | about 24 MB (77 MB installed) | OpenStreetMap places tagged fully vegan or with vegan options, worldwide |
| Essentials | about 986 MB (3.62 GB installed) | OpenStreetMap pharmacies, hospitals, ATMs, toilets, drinking water, stations, supermarkets and hostels, worldwide |
| Cryptography & Ethereum | about 59 MB (92 MB installed) | Ethereum proposals and specifications, NIST post-quantum standards and related Wikipedia articles |
| Biology & longevity | about 1.08 GB (2.86 GB installed) | 48,286 open-access ageing and longevity papers from PubMed Central, OpenStax Biology 2e, MedlinePlus health and genetics summaries and ageing databases; not medical advice |

A collection installed in a newer version takes over from the older one, which stays installed
and switched off until you delete it in Library.

Collections download from Hugging Face, from [Field Atlas Knowledge Packs](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge)
and [Field Atlas Encyclopedia](https://huggingface.co/datasets/0x94t3z/fieldatlas-encyclopedia). To set up a phone without a
connection, save a `.fapack` from there on another device and import it in setup. Field Atlas
1.3.0 and older downloaded collections from GitHub releases, which are retired; update the app.


Choose **Download recommended model** (internet needed, about 1.4 GB) or import the Qwen3.5 2B `.fapack` built with the command in [MODELS.md](../MODELS.md); both install the same pinned weights, and the app verifies the exact size and SHA-256 before using either. The direct model download was checked on a Redmi 13C for 1.4.1 (1.40 GB in about 11 minutes); the pack import remains the way to set up a phone that cannot download.

You can switch apps while a collection downloads; an active transfer has a foreground notification. Only one download or import runs at a time. Pause keeps the downloaded bytes, and reopening the app after a process interruption resumes the transfer. If a transfer fails, tap Resume in setup or Library to try again; a server that does not support range requests may require a full restart. Once a model is installed, choose **Start researching** with or without knowledge; more collections can be added later in Library. Every collection downloads from Hugging Face at a pinned revision. The manual import path accepts `.fapack`, not raw `.gguf` files.

Place questions about where you are ("vegan restaurants near me", "where am I?") use the phone's own location through Android's location service, only after a short explanation and Android's permission prompt; naming a city works without location. A place card can open an offline maps app: install one such as [Organic Maps](https://organicmaps.app) or [OsmAnd](https://osmand.net) (both on F-Droid) and its maps before you travel, or copy the coordinates from the card. Voice input uses a bundled English Vosk speech model and, if you choose to use it, microphone permission; words appear in the question box as you speak, and nothing is submitted automatically. You can keep more than one verified model and switch the active one in Library. The answer flow prepares a selected model automatically; **Prepare for research** is a retry if loading needs help.

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
