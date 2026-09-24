# Installation

## Simple Android Setup

Field Atlas uses local files only. Before opening the app for the first time, put these three files on the phone:

- `field-atlas-v1.1.3.apk` - the Android app
- `qwen3-1.7b-q4-k-m-1.0.0.fapack` - the local model pack
- `fieldatlas-starter-1.0.0.fapack` - the starter knowledge pack

Install the APK from the phone's file manager. Then open Field Atlas, tap **Choose model pack**, and select the model `.fapack`. After it verifies, tap **Choose knowledge pack** and select the starter knowledge `.fapack`. The model and knowledge files are separate; selecting the knowledge pack during the model step will not work.

The app does not download packs by itself because the installed Android app has no network permission. Download or build the files on a desktop first, then copy them to the phone over USB, Nearby Share, or another file-transfer method.

## APK

Download the signed [Field Atlas 1.1.3 APK](https://github.com/0x94t3z/fieldatlas/raw/refs/heads/main/releases/field-atlas-v1.1.3.apk), verify the SHA-256 published beside it, and sideload it. For local testing, the Android toolchain signs `app/build/outputs/apk/debug/app-debug.apk` with the local debug key. The unsigned release artifact exists only for reproducible inspection; never present it as installable. On the phone, open the APK through the system file manager and approve that file manager as an unknown-app source only when Android asks. Field Atlas needs no account, Play Services, or network permission.

## Packs

Download the ready-to-import [Field Atlas Starter Evidence pack](https://github.com/0x94t3z/fieldatlas/raw/refs/heads/main/releases/fieldatlas-starter-1.0.0.fapack) and verify SHA-256 `51769d845dc163aa4a56a15d9ad68eb3d65f12f1649d56b2105a906c9e0c453b`. To reproduce it instead, run `./scripts/build_starter_pack.sh`.

Create the model pack with the commands in [`MODELS.md`](../MODELS.md). That process downloads the pinned `Qwen3-1.7B-Q4_K_M.gguf`, verifies its hash, and wraps it as `qwen3-1.7b-q4-k-m-1.0.0.fapack`. Field Atlas imports that `.fapack` file, not the raw `.gguf`.

On first launch, tap **Choose model pack**, select the model `.fapack`, then tap **Choose knowledge pack** and select the knowledge `.fapack`. Import can take longer than a normal copy because Field Atlas streams and verifies every declared byte. The app does not load the model automatically. Once both packs appear, open Research and tap **Prepare for research**. A first load may take tens of seconds on a low-end phone.

The bundled starter knowledge is visibly labelled **Demo coverage**. Its suggested questions describe only its installed scope; they are not a claim of broad or comprehensive coverage. If a question has no matching local source, Field Atlas can still answer from the offline model, but the answer is shown without local citations.

To update a pack, use a new manifest version. Existing versions are never silently overwritten. Android's uninstall flow removes the app and its app-private packs; keep the original `.fapack` files elsewhere if they are needed again.

## Troubleshooting

- **Hash mismatch:** delete the transferred file, download or copy it again, and verify the desktop hash.
- **Insufficient space:** keep enough free space for the archive plus its extracted payload and safety reserve.
- **Model import failure:** confirm the selected file is the model `.fapack`, not the raw `.gguf`, the starter knowledge pack, or a `.sha256` checksum file.
- **Model load failure after import:** confirm the model pack was built from the documented Q4_K_M GGUF artifact and export redacted diagnostics.
- **Slow or hot device:** stop generation, unload the model, allow the phone to cool, and record the failure rather than hiding it.
- **No Research button:** both verified pack types must be installed and the model must reach Ready.
- **Need diagnostics:** open **More** for technical details, redacted export, and the device benchmark.
