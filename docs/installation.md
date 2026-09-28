# Install Field Atlas

[Back to the overview](../README.md)

The published app still needs a matching model `.fapack` imported from the phone. The current development build adds a user-started download of its pinned model as an alternative. **Finish model setup before going offline.** The model is about 1.4 GB, so allow time and free storage.

## Choose your path

| If you want to… | Use this | Important difference |
| --- | --- | --- |
| Try the published Android app | [Signed v1.1.10 release](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10) | Use its **Qwen3 1.7B** model pack instructions. Its focused Reference collection is bundled. |
| Test the newest source features | [Build the current debug APK](building.md) | Use the **Qwen3.5 2B** model pack. Knowledge collections are separate. This is not the signed public release. |

Do not install the old APK and follow the new Qwen3.5 instructions, or expect the public release to have the newer UI, voice, vector search, or restaurant lookup. Check the package version in **More** if you are unsure.

## Published release: phone setup

1. Download `fieldatlas-1.1.10.apk` from the [v1.1.10 release](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10). Its `.sha256` file lets you check that the download is intact.
2. On a computer, follow the [v1.1.10 model instructions](https://github.com/0x94t3z/fieldatlas/blob/v1.1.10/MODELS.md) to create `qwen3-1.7b-q4-k-m-1.0.0.fapack`. The release does **not** attach a ready-to-import model pack. This is the least beginner-friendly step today; a computer is currently required.
3. Copy the APK and `.fapack` to the phone with a USB cable or your preferred file-transfer method.
4. Open the APK in the phone's Files app. If Android asks, allow that Files app to install it.
5. Open Field Atlas. Tap **Choose model pack**, select `qwen3-1.7b-q4-k-m-1.0.0.fapack`, and wait while it verifies the file. In the public release, the small Reference knowledge collection is already bundled.
6. Open **Research**. If the model is not ready, tap **Prepare for research** and wait. Ask a question, then tap **Start research**.
7. When an answer has a numbered citation, tap it to inspect the local supporting passage. An answer without a matched local source is labelled as uncited.

Turn on airplane mode if you want to test the published release's offline behavior. That release has no Android network permission and cannot fetch a missing model or new facts.

## Current development build

The source in this checkout is ahead of the published release. Developers can [build a debug APK](building.md) at `app/build/outputs/apk/debug/app-debug.apk`; it installs as `xyz.fieldatlas.debug` alongside the signed app. In setup, choose **Download recommended model** (internet needed, about 1.4 GB) or import the Qwen3.5 2B `.fapack` built with the command in [MODELS.md](../MODELS.md). Both paths install the same pinned model weights. The app verifies the direct download's exact size and SHA-256 before using it. The direct-download path has not yet been validated on a physical phone; keep the pack-import option for now.

The current build needs **only an installed model** to answer questions. A knowledge pack is optional and adds local source passages and citations for its own topic. After model setup, choose **Open library** to see the recommended [biology vector collection](../DATASETS.md#biology-vector-pack-optional), or skip it and start researching. Library also accepts imported `.fapack` files. Biology covers biology and longevity; the [Wikivoyage places-to-eat pack](../DATASETS.md#wikivoyage-places-to-eat-optional-build) contains dated venue listings. Neither is bundled into the APK; the biology pack is hosted by the fork, while the travel pack is not a public in-app download. The manual import path accepts `.fapack`, not raw `.gguf` files.

You can [download Travel places](https://github.com/0x94t3z/fieldatlas/releases/tag/knowledge-travel-2026.09.1) and import the `.fapack` from Library. It includes restaurants, hotels, sights, shopping, and other dated listings. Its in-app download button is in the updated local catalog; the corresponding Android update has not been published. Earlier Infinix checks returned cited café and museum listings. Developers can also build a [Wikipedia mini pack](../DATASETS.md#wikipedia-mini-keyword-only-builder), which remains unpublished and has not been phone-tested.

Voice input in the current build uses a bundled English Vosk speech model and, if you choose to use it, microphone permission. It works on-device. You can keep more than one verified model and switch the active one in Library. The newer answer flow prepares a selected model automatically; **Prepare for research** is a retry if loading needs help.

## Common problems

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
