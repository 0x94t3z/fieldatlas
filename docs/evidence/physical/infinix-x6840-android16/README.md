# Infinix SMART 20 / X6840 Android 16 release record

Captured on 2026-09-21 from a USB-paired physical handset. Android reports manufacturer `INFINIX`, model `Infinix X6840`, API 36, ARM64, 720 × 1576 px, and 3,831,080 KiB physical memory. The system document picker identifies the retail device as Infinix SMART 20. No device serial, account, network address, or private user data is retained.

## Exact tested assets

- Signed release: `app-release.apk` from the final local release matrix; its byte count and SHA-256 are recorded in `docs/compliance/release-audit.md`.
- Release signer SHA-256: `131127512c99a625acd0dd4baf20e1c7cd240b0fd573449197070000e3d6b666`.
- Final starter knowledge pack: 25,500 bytes; SHA-256 `51769d845dc163aa4a56a15d9ad68eb3d65f12f1649d56b2105a906c9e0c453b`.
- Qwen3 1.7B Q4_K_M model pack: 1,282,441,304 bytes; SHA-256 `e4ac4b6b68d55b1846c70fc877ef142954669cb8e04b85d4a74e4838430ae60b`.
- Android package/version: `xyz.fieldatlas`, version `1.0.0` (`versionCode` 2).

The signed APK was installed with Android Debug Bridge, pulled back from the package path, and compared byte-for-byte with the local signed artifact. Both packs were imported through Android's document picker and their source-file hashes were checked on the handset.

## Radios-off research proof

Airplane mode was enabled, Wi-Fi disabled, and mobile data disabled before model loading and research. The local model loaded from the extracted ARM64 runtime/backend. The query **“Why does Earth have seasons”** completed with local source `S1`, all citation markers mapped, and no network permission in the installed APK.

- Retrieval: 34 ms
- Total response: 41,276 ms
- Generated tokens: 108
- Derived rate: 2.62 tokens/s
- Loaded-run app PSS: 2,135,363 KiB
- Loaded-run app RSS: 1,290,491 KiB
- Loaded-run swap PSS: 927,123 KiB

This demonstrates a usable single lookup on a device with far less than the 12 GB ceiling. It does not imply every frozen benchmark prompt is equally fast: the sustained 18-question run included polling bounds from 5 to 155 seconds.

## Guided benchmark and export recovery

The frozen 18-question guided suite completed in one continuous radios-off run: 16 rows were `COMPLETE` and 2 were explicit `INSUFFICIENT` results. The source export was 25,821 bytes with SHA-256 `ad3c5d12e4169455f894d1477ac6012cec752727327f984bd7b3c96977c69452`. It contained no serial, account, email, private storage path, or Android identifier found by the release scan. That run used the byte-identical research text with the earlier unspaced source label; the final deterministic pack changes only that user-visible attribution to “Field Atlas” and was reimported for the final installed state.

That run exposed an empty-export bug when the OEM killed the memory-heavy app behind Android's document picker. The repair stages evidence in an fsynced private file before opening the picker. A repeated hardware export entered the same process-death state and returned a non-empty, valid 25,821-byte JSON document after activity recreation. Regression tests cover store recreation, missing payloads, stale replacement, and removal of model reasoning markers. The raw pre-sanitizer run remains in the ignored local `build/evidence/device/` tree; it is not presented as the final public benchmark output and no greater-than-50-percent quality claim is made.

## Screenshots

- `first-launch.png` — first physical launch.
- `radios-off-research.png` — completed offline research with radio isolation.
- `proof-metrics.png` — measured timing, memory, and citation mapping.
- `signed-release-app-info.png` — installed Field Atlas version information.

These screenshots support the device record; they do not replace the reproducible commands, APK hashes, benchmark export, or public demo required by the bounty.

## Demo asset

`field-atlas-public-demo.mp4` is a 120.88-second, 720 × 1576 H.264 device demo (1,766,167 bytes; SHA-256 `2a1078c4fbb991ff22801f74e7352908ad0dd57f58dbd01f2c17cc10279bb938`). It shows the installed **Field Atlas** identity, two explanation/comparison answers, a model reload and solar-storage synthesis, source attribution, measured proof, and an explicit insufficient-evidence response. Long generation waits are trimmed; answers are unmodified, and every moving segment comes from the same radios-off physical-device session. No X or Farcaster post URL is currently recorded.

## Installation/readiness observation

The signed APK installed in about 8 seconds. Observed model-pack import completed within 64 seconds, knowledge-pack import within 5 seconds, model load in about 30 seconds, and the demonstrated lookup in 41.3 seconds. These are direct observed upper bounds from the acceptance session, not a laboratory clean-install benchmark.

## Remaining boundary

This record satisfies the physical Infinix, offline, memory, installation, and measured-lookup gates. A GrapheneOS-compatible Pixel run, a paired named online baseline, and an authentic public X or Farcaster video remain outstanding and are tracked as blocked in the requirement ledger.
