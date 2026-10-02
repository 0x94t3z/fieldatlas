# Install Field Atlas

[Back to the overview](../README.md)

The current release is **v1.3.0**, for Android 13+ ARM64 devices. It uses Qwen3.5 2B, installed separately by download or matching model `.fapack`. **Finish model setup before going offline.** The model is about 1.4 GB; the APK and optional collections need additional storage. Answers can be inaccurate; read the known limitations.

## Choose your path

| If you want to… | Use this | Important difference |
| --- | --- | --- |
| Use the current release | [Signed v1.3.0](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.3.0) | Qwen3.5 2B; optional collections are separate. Read the [known limitations](releases/current.md). |
| Use the previous stable release | [v1.1.10](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10) | Follow its [version-specific instructions](https://github.com/0x94t3z/fieldatlas/blob/v1.1.10/docs/installation.md), including Qwen3 1.7B. |
| Test unpublished source changes | [Build from source](building.md) | Qwen3.5 2B. Local changes are not necessarily in the published APK. A debug build is a separate app, not an update to the signed release. |

Check the version in **More**. Do not follow Qwen3.5 instructions for a 1.1.10 APK. The public signing key cannot update a local-test-key build; do not uninstall to work around this, because uninstalling removes app-private data.

## Current release: phone setup

1. Download `fieldatlas.apk` and `fieldatlas.apk.sha256` from [v1.3.0](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.3.0). Verify the checksum if possible.
2. Open the APK on an Android 13+ ARM64 phone. Allow installation from the app opening the APK if Android asks.
3. In setup, download the recommended Qwen3.5 2B model, or import a matching `.fapack` using the [model guide at this release](https://github.com/0x94t3z/fieldatlas/blob/v1.3.0/MODELS.md). A raw `.gguf` is not a model pack.
4. Optionally download or import a knowledge collection. A model is required; collections provide saved passages for their covered topics.
5. Start research once the model is installed. Allow time for first model preparation.
6. Open source citations to inspect actual passages. An unverified model explanation is not proof that the saved sources support it.

After setup, turn on airplane mode and turn Wi-Fi off to try offline research. This release has network permission for explicit downloads and catalog refresh, not remote inference. Download recovery and network-enabled traffic capture have not been independently verified for this release. Do not assume the unpublished background-download improvements below are present in the public APK.

## Current development build

The source in this checkout is ahead of the published release. Developers can [build a debug APK](building.md) at `app/build/outputs/apk/debug/app-debug.apk`; it installs as `xyz.fieldatlas.debug` alongside the signed app. In setup, choose **Download recommended model** (internet needed, about 1.4 GB) or import the Qwen3.5 2B `.fapack` built with the command in [MODELS.md](../MODELS.md). Both paths install the same pinned model weights. The app verifies the direct download's exact size and SHA-256 before using it. The direct-download path has not yet been validated on a physical phone; keep the pack-import option for now.

The current development build needs **only an installed model** to answer questions. The setup screen shows the model and optional knowledge downloads together: **Biology & longevity** (about 1.57 GB), **Travel places** (about 352 MB) and **Vegan places** (about 40 MB), directly from the download catalog. Scroll below the model to choose a collection and confirm the download. You can switch apps while it downloads; an active transfer has a foreground notification. Only one download or import runs at a time. Pause keeps the downloaded bytes, and reopening the app after a process interruption resumes the transfer. If a transfer fails, tap Resume in setup or Library to try again. A server that does not support range requests may require a full restart. You can also import a saved `.fapack`. Once a model is installed, choose **Start researching** with or without knowledge; more collections can be added later in Library. The app verifies each download's exact size and SHA-256 before installing it. Knowledge adds local source passages and citations for its own topic; it is not bundled into the APK. Biology is hosted by the fork, and Travel places is hosted as a project release asset. The manual import path accepts `.fapack`, not raw `.gguf` files.

You can [download Travel places](https://github.com/0x94t3z/fieldatlas/releases/tag/knowledge-travel-2026.09.1) and import the `.fapack` from Library. It includes restaurants, hotels, sights, shopping, and other dated listings. Its in-app download button is in the updated local catalog; the corresponding Android update has not been published. Earlier Infinix checks returned cited café and museum listings. Developers can also build a [Wikipedia mini pack](../DATASETS.md#wikipedia-mini-keyword-only-builder), which remains unpublished and has not been phone-tested.

Voice input in the current build uses a bundled English Vosk speech model and, if you choose to use it, microphone permission. It works on-device. You can keep more than one verified model and switch the active one in Library. The newer answer flow prepares a selected model automatically; **Prepare for research** is a retry if loading needs help.

## Common problems

### Refreshing collections in the development build

Setup and Library offer **Refresh collections**, an explicit online provisioning action. Opening the app or those screens does not refresh automatically. The app uses its bundled list until a refresh succeeds, then keeps the accepted catalog locally for offline use. Refresh failure leaves that list and installed packs available. Refresh downloads metadata only; each pack still requires a separate confirmed download.

The catalog is maintained at the repository's `main/app/src/main/assets/knowledge/catalog.json` path and fetched over HTTPS. New compatible entries can appear without an APK update once catalog changes are published there. Imported packs need not appear in this catalog. If refresh fails or the endpoint is unavailable, use bundled listings or import a saved pack. Current local changes still require final device/network verification.

The trust boundary is the maintained GitHub repository and HTTPS. Pack checksums detect mismatches against the accepted catalog, not a compromised publisher. No automatic pack updates or deletions occur. Unpublished provisioning checks now count the APK, native libraries, app data/cache and staged files, reserve extraction space, and serialize app provisioning operations. They are not an OS-enforced quota on subsequent cache/history growth; final total-storage measurement remains a release gate.

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
