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

### Research home · light theme

![Field Atlas research home in the light theme while offline](research-home-light.png)

### Installed local assets

![Field Atlas library showing the installed local model and knowledge pack](library-light.png)

### Cited offline answer

![Completed on-device answer with local citations](radios-off-answer.png)

### Research home · dark theme

![Field Atlas research home following the Android system dark theme](research-home-dark.png)

## Memory observation

`current-app-meminfo.txt` records a 2,099,224 KiB total PSS observation for the loaded current app process. This is about 2.0 GiB, below the 12 GB environment ceiling. It is a field observation, not a performance guarantee.

## Video

<video src="field-atlas-current-demo.mp4" controls width="720">
  <a href="field-atlas-current-demo.mp4">Play the complete physical-device demo</a>
</video>

[![Animated preview of the Field Atlas physical-device demo](field-atlas-claim-preview.gif)](field-atlas-current-demo.mp4)

The complete recording is a 46.97-second 1920 × 1080 H.264 demo from the current Infinix run. It shows the current Field Atlas branding, offline research flow, installed local model and knowledge pack, radios-off operation, the seasons question, local answer generation, cited answer, and local grounding. Select the animated preview if the video player is unavailable.

### Demo overview

![Overview frames from the Field Atlas physical-device demo](field-atlas-claim-overview.jpg)

This record demonstrates installation, local execution, offline mode, local source attribution, and the current visual build on one physical Android device. It does not represent GrapheneOS testing or an independent assessment of the bounty's quality bar.
