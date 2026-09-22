# Field Atlas 1.1.1 physical-device record

Captured on 2026-09-22 from a USB-paired Infinix SMART 20 / X6840 running Android 16 (API 36), ARM64. The device reported `Infinix X6840`; the signed installed package was `xyz.fieldatlas` version 1.1.1 (`versionCode` 4).

## Exact tested artifact

- Signed APK: 49,144,290 bytes.
- SHA-256: `104461ea2c5a55c4832238757b012eb8bf4623a2bbe6f4d7dbf24af770cd35ba`.
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

`field-atlas-current-demo.mp4` is a 234.39-second native 720 × 1576 H.264 recording pulled directly from the Infinix during a radios-off session. It records the current Field Notebook Research home, installed local model and knowledge pack, Android radios-off state, real preparation, the full local research run, and the completed cited answer. `field-atlas-current-demo-source-hold.mp4` is a separate raw source-passage hold from the same device and answer state. The two recordings are intentionally preserved as separate, unedited device files because the OEM recorder enforces a real-frame cap on long captures.

This record demonstrates installation, local execution, offline mode, local source attribution, and the current visual build on one physical Android device. It does not represent GrapheneOS testing or an independent assessment of the bounty's quality bar.
