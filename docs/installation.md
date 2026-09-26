# Installation

## Simple Android Setup

Field Atlas uses local files only. Before opening the app for the first time, put these two files on the phone:

- the latest signed `fieldatlas-*.apk` from GitHub Releases
- `qwen3-1.7b-q4-k-m-1.0.0.fapack` - the local model pack

Install the APK from the phone's file manager. Field Atlas installs its Reference knowledge pack automatically on first launch. Then tap **Choose model pack** and select the model `.fapack`.

The app does not download packs by itself because the installed Android app has no network permission. Download or build the model pack on a desktop first, then copy it to the phone over USB, Nearby Share, or another file-transfer method.

Field Atlas can also keep more than one verified model pack. Choose the active model in
Library; switching remains local and works with radios off. If you enable voice input,
Android asks for microphone permission and Vosk transcribes on-device using the bundled
speech model. No audio or text is sent to a server.

## APK

Download the latest signed APK from [Field Atlas releases](https://github.com/0x94t3z/fieldatlas/releases/latest), verify the SHA-256 published beside it, and sideload it. For local testing, the Android toolchain signs `app/build/outputs/apk/debug/app-debug.apk` with the local debug key. The unsigned release artifact exists only for reproducible inspection; never present it as installable. On the phone, open the APK through the system file manager and approve that file manager as an unknown-app source only when Android asks. Field Atlas needs no account, Play Services, or network permission.

## Packs

The Field Atlas Reference pack is bundled in the APK and installed into app-private storage on first launch. Its provenance and reproducible build are documented in [DATASETS.md](../DATASETS.md). Advanced users can still import a different knowledge `.fapack` from the file picker.

Create the model pack with the commands in [`MODELS.md`](../MODELS.md). That process downloads the pinned `Qwen3-1.7B-Q4_K_M.gguf`, verifies its hash, and wraps it as `qwen3-1.7b-q4-k-m-1.0.0.fapack`. Field Atlas imports that `.fapack` file, not the raw `.gguf`.

On first launch, Field Atlas installs the bundled Reference pack. Tap **Choose model pack** and select the model `.fapack`. Model import can take longer than a normal copy because Field Atlas streams and verifies every declared byte. Once the model and bundled knowledge appear in Library, Field Atlas prepares the selected model automatically. A first load may take tens of seconds on a low-end phone; **Prepare for research** remains available if preparation needs to be retried.

The bundled Reference pack is visibly labelled **Focused coverage**. Its suggested questions describe only its installed scope; it is not a claim of comprehensive coverage. If a question has no matching local source, Field Atlas can still answer from the offline model, but the answer is shown without local citations.

To update a pack, use a new manifest version. Existing versions are never silently overwritten. Android's uninstall flow removes the app and its app-private packs; keep the original `.fapack` files elsewhere if they are needed again.

## Troubleshooting

- **Hash mismatch:** delete the transferred file, download or copy it again, and verify the desktop hash.
- **Insufficient space:** keep enough free space for the archive plus its extracted payload and safety reserve.
- **Model import failure:** confirm the selected file is the model `.fapack`, not the raw `.gguf`, a knowledge pack, or a `.sha256` checksum file.
- **Model load failure after import:** confirm the model pack was built from the documented Q4_K_M GGUF artifact and export redacted diagnostics.
- **Slow or hot device:** stop generation, unload the model, allow the phone to cool, and record the failure rather than hiding it.
- **No Research button:** both verified pack types must be installed and the model must reach Ready.
- **Need diagnostics:** open **More** for technical details, redacted export, and the device benchmark.
