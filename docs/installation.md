# Installation

## APK

Download the signed [Field Atlas 1.1.1 APK](https://github.com/0x94t3z/fieldatlas/raw/refs/heads/main/releases/field-atlas-v1.1.1.apk), verify SHA-256 `6ffdc71cba6543e57adbdb4d2c51b6866a7e941d754108b1344216830b1936fa`, and sideload it. For local testing, the Android toolchain signs `app/build/outputs/apk/debug/app-debug.apk` with the local debug key. The unsigned release artifact exists only for reproducible inspection; never present it as installable. On the phone, open the APK through the system file manager and approve that file manager as an unknown-app source only when Android asks. Field Atlas needs no account, Play Services, or network permission.

## Packs

Download the ready-to-import [Field Atlas Starter Evidence pack](https://github.com/0x94t3z/fieldatlas/raw/refs/heads/main/releases/fieldatlas-starter-1.0.0.fapack) and verify SHA-256 `51769d845dc163aa4a56a15d9ad68eb3d65f12f1649d56b2105a906c9e0c453b`. To reproduce it instead, run `./scripts/build_starter_pack.sh`. Create the model pack with the commands in `MODELS.md`. Copy both files to the phone over USB; the offline app deliberately has no downloader or network permission.

On first launch, tap **Choose model pack**, select the model `.fapack`, then tap **Choose knowledge pack** and select the knowledge `.fapack`. Import can take longer than a normal copy because Field Atlas streams and verifies every declared byte. The app does not load the model automatically. Once both packs appear, open Research and tap **Prepare for research**. A first load may take tens of seconds on a low-end phone.

The bundled starter knowledge is visibly labelled **Demo coverage**. Its suggested questions describe only its installed scope; they are not a claim of broad or comprehensive coverage.

To update a pack, use a new manifest version. Existing versions are never silently overwritten. Android's uninstall flow removes the app and its app-private packs; keep the original `.fapack` files elsewhere if they are needed again.

## Troubleshooting

- **Hash mismatch:** delete the transferred file, download or copy it again, and verify the desktop hash.
- **Insufficient space:** keep enough free space for the archive plus its extracted payload and safety reserve.
- **Model load failure:** confirm the GGUF is the documented Q4_K_M artifact and export redacted diagnostics.
- **Slow or hot device:** stop generation, unload the model, allow the phone to cool, and record the failure rather than hiding it.
- **No Research button:** both verified pack types must be installed and the model must reach Ready.
- **Need diagnostics:** open **More** for technical details, redacted export, and the device benchmark.
