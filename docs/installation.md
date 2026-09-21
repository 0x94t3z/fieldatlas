# Installation

## APK

Download `field-atlas-v1.0.0.apk` from the [v1.0.0 release](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.0.0) and verify SHA-256 `13448e4b91ffc00971168a024dd74a808ac3394d14866c4858c85c2875a1b022` before sideloading it. For local testing, the Android toolchain signs `app/build/outputs/apk/debug/app-debug.apk` with the local debug key. The unsigned release artifact exists only for reproducible inspection; never present it as installable. On the phone, open the APK through the system file manager and approve that file manager as an unknown-app source only when Android asks. Field Atlas needs no account, Play Services, or network permission.

## Packs

Create `fieldatlas-starter-1.0.0.fapack` with `./scripts/build_starter_pack.sh`. Create the model pack with the commands in `MODELS.md`. Copy both files to the phone over USB; do not ask the offline app to download them.

On first launch, tap **Choose model pack**, select the model `.fapack`, then tap **Choose knowledge pack** and select the knowledge `.fapack`. Import can take longer than a normal copy because Field Atlas streams and verifies every declared byte. The app does not load the model automatically. Once both packs appear, open Research and tap **Load model**. A first load may take tens of seconds on a low-end phone.

The bundled starter knowledge is visibly labelled **Demo coverage**. Its suggested questions describe only its installed scope; they are not a claim of broad or comprehensive coverage.

To update a pack, use a new manifest version. Existing versions are never silently overwritten. Android's uninstall flow removes the app and its app-private packs; keep the original `.fapack` files elsewhere if they are needed again.

## Troubleshooting

- **Hash mismatch:** delete the transferred file, download or copy it again, and verify the desktop hash.
- **Insufficient space:** keep enough free space for the archive plus its extracted payload and safety reserve.
- **Model load failure:** confirm the GGUF is the documented Q4_K_M artifact and export redacted diagnostics.
- **Slow or hot device:** stop generation, unload the model, allow the phone to cool, and record the failure rather than hiding it.
- **No Research button:** both verified pack types must be installed and the model must reach Ready.
- **Need audit details:** open **Proof** for origin-labelled facts and redacted export; open the device benchmark there after the model reaches Ready.
