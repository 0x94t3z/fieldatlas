# Field Atlas physical-device record

Initial capture was made on 2026-09-22 from a USB-paired Infinix SMART 20 / X6840 running Android 16 (API 36), ARM64, with follow-up multi-query evidence captured on 2026-09-23. On 2026-09-26, the signed 1.2.0 integration build was installed over the existing app and tested with the preserved local assets. The device reported `Infinix X6840`. This build is not tagged or published as a GitHub release.

## Current reviewed artifact

- Signed integration APK: 93,528,063 bytes (`1.2.0`, `versionCode` 14).
- SHA-256: `5eed91fca8af33cceee44c48c8dae68b8aeb7686c9dcbcba6658a261bfaa8fcc`.
- Offline APK audit: passed.
- Selected model: Qwen3.5 2B Q4_K_M Compact, 1.4 GB.
- Enabled knowledge: Biology & longevity vector pack, 1.57 GB.
- Retained but inactive: Qwen3 1.7B model and Field Atlas Reference pack.

The recorded package version, APK checksum, Android release, and airplane-mode state are retained as adjacent text files.

The integration APK was installed and launched on the connected device with airplane mode enabled, Wi-Fi disabled, and no data connection. The earlier Qwen3/Reference water-treatment comparison exercised the on-device keyword planner, local retrieval, answer generation, clickable citation, and exact local source passage. The planner produced the specific terms `boiling`, `filtration`, `pathogens`, and `metals`; the answer used one relevant local source rather than unrelated partial matches. No crash or Field Atlas ANR occurred in that acceptance run.

The later fork-stack check verified exact download hashes, imported and selected `Qwen3.5 2B — Q4_K_M Compact`, enabled `Biology & longevity (world_knowledge)`, disabled Field Atlas Reference, and completed model preparation. It intentionally stopped before research generation, so this record does not present the earlier answer as a Qwen3.5/vector result.

The integration candidate passed 176 JVM tests, release lint, the offline APK policy, signature verification, and all 30 connected Android tests on this device. The source is ready for `main`, but no release asset or tag is claimed.

The Reference-pack review page is [here](../../current-reference-v1.1.6/README.md); its directory name is retained for stable links.

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

`current-app-meminfo.txt` records a 1,580,107 KiB total PSS observation after Qwen3.5 preparation. This is about 1.5 GiB, below the 12 GB environment ceiling. It is a field observation, not a performance guarantee.

## Video

<video src="field-atlas-current-demo.mp4" controls width="720">
  <a href="field-atlas-current-demo.mp4">Play the complete physical-device demo</a>
</video>

[![Animated preview of the Field Atlas physical-device demo](field-atlas-claim-preview.gif)](field-atlas-current-demo.mp4)

The complete recording is a 46.97-second 1920 × 1080 H.264 demo from the earlier Infinix run. It shows the Field Atlas branding, offline research flow, installed local model and knowledge pack, radios-off operation, the seasons question, local answer generation, cited answer, and local grounding. The recording predates the current retrieval, voice, model-selection, vector-search, and history changes and remains historical evidence.

### Demo overview

![Overview frames from the Field Atlas physical-device demo](field-atlas-claim-overview.jpg)

## Additional multi-query evidence

These follow-up runs used the earlier installed signed APK, local Qwen model, Starter Evidence pack, airplane mode, and disabled Wi-Fi. The raw portrait recordings retain the real inference wait rather than replacing it with simulated output; they are historical evidence and do not claim to demonstrate the current Reference pack. The current Reference-pack install and behavior are documented above and in the current-build review page.

### Water-treatment limitation

Question: “What does boiling water remove, and what can remain?”

<video src="water-safety-proof.mp4" controls width="360">
  <a href="water-safety-proof.mp4">Play the 87.65-second raw water-safety run</a>
</video>

![Offline water-safety answer with a numbered local citation](water-safety-answer.png)

![Exact local water-safety passage opened from the answer citation](water-safety-source.png)

### Solar-storage reasoning

Question: “When can battery storage help a solar-heavy grid?”

<video src="solar-storage-proof.mp4" controls width="360">
  <a href="solar-storage-proof.mp4">Play the 96.69-second raw solar-storage run</a>
</video>

![Offline solar-storage answer grounded in installed sources](solar-storage-answer.png)

![Exact local solar-storage passage opened from the answer citation](solar-storage-source.png)

### Cross-source comparison and synthesis

Question: “Compare boiling and filtration for water that may contain pathogens and dissolved metals, then explain what evidence must be checked before recommending treatment.”

<video src="cross-source-water-proof.mp4" controls width="360">
  <a href="cross-source-water-proof.mp4">Play the 170.32-second raw cross-source run</a>
</video>

![Offline cross-source answer generated from three installed passages](cross-source-water-answer.png)

![Cross-source answer showing its numbered local citations](cross-source-water-citations.png)

![The separate evidence-comparison passage opened from citation 2](cross-source-water-source.png)

This record demonstrates installation, local execution, offline mode, local source attribution, and the current visual build on one physical Android device. It does not represent GrapheneOS testing or an independent assessment of the bounty's quality bar.

Public proof: [Farcaster](https://farcaster.xyz/0x94t3z.eth/0x9538cba8).
