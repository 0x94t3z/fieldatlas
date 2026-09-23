# Field Atlas 1.1.1 physical-device record

Captured on 2026-09-22 from a USB-paired Infinix SMART 20 / X6840 running Android 16 (API 36), ARM64. The device reported `Infinix X6840`; the signed installed package was `xyz.fieldatlas` version 1.1.1 (`versionCode` 4).

## Exact tested artifact

- Signed APK: 49,143,558 bytes.
- SHA-256: `6ffdc71cba6543e57adbdb4d2c51b6866a7e941d754108b1344216830b1936fa`.
- Offline APK audit: passed.
- Model: Qwen3 1.7B Q4_K_M Compact, 1.28 GB.
- Knowledge pack: Field Atlas Starter Evidence, 24.58 KB.

The recorded package version, APK checksum, Android release, and airplane-mode state are retained as adjacent text files.

## Radios-off run

Airplane mode was enabled and Wi-Fi and mobile data were disabled before launching the app. The installed model and knowledge pack were used to answer “Why do Earth's hemispheres have opposite seasons?” locally. The resulting answer displays the offline state and mapped local citations.

- `research-home-light.png` — current light-theme research home while radios were off.
- `radios-off-answer.png` — completed on-device answer with local source and citation markers.
- `library-light.png` — installed model and knowledge pack in the current Field Notebook library.
- `research-home-dark.png` — current Android-system dark theme.

## Memory observation

`current-app-meminfo.txt` records a 2,099,224 KiB total PSS observation for the loaded current app process. This is about 2.0 GiB, below the 12 GB environment ceiling. It is a field observation, not a performance guarantee.

## Video

`field-atlas-current-demo.mp4` is a 46.97-second 1920 x 1080 H.264 demo built from the current Infinix run. It shows the current Field Atlas branding, offline research flow, the installed local model and knowledge pack, radios-off operation, the seasons question, local answer generation, a cited answer, and local grounding. `field-atlas-claim-answer.png`, `field-atlas-claim-overview.jpg`, and `field-atlas-claim-preview.gif` are derived from that demo for public proof posts and POIDH claim upload.

This record demonstrates installation, local execution, offline mode, local source attribution, and the current visual build on one physical Android device. It does not represent GrapheneOS testing or an independent assessment of the bounty's quality bar.
