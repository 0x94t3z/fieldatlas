# POIDH bounty 31 release audit

Source: `https://poidh.xyz/mainnet/bounty/31`. The reviewed text is preserved in `docs/compliance/bounty-31-source.md`; SHA-256: `81507393d46e58800373024441b2222a271fa8bc4e2a0fc77f0dd4556de7a4bb`.

Statuses are evidence gates, not aspirations. `PASS` means the named artifact exists and was checked; `BLOCKED` identifies missing evidence.

## Candidate verification

The current public candidate is Field Atlas `1.1.1` (`versionCode` 4). Its signed APK is 49,128,438 bytes with SHA-256 `c66e3d7943adb290a65108e20c270a17fc4b0f3f2ab6f03853d808e555af136e`. It contains only the Field Notebook appearance: warm paper surfaces, forest-green accents, editorial typography, and tactile research cards; system dark-theme selection is not part of this release. The release candidate was upgraded in place, imported both offline packs, loaded the model, completed a cited answer, and opened its exact source passage on the physical Infinix X6840. The complete 28-test connected suite passed immediately before the final citation-wrapping and benchmark-back refinements; their JVM and Android-test compilation gates pass, while the paired handset currently requires ADB authorization before those focused device checks can be repeated.

The table below records the original `1.0.0` hardware evidence session retained with the repository.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug APK | 84,612,936 | `0e362502a0c82550f922861897984306d7915bd7bfa408919c679e43ff38d6c8` |
| Unsigned release APK | 49,014,387 | `39d59da9295fd07d886f920104a0c0362a379c33884c6d5a88000df75f2c7f1d` |
| Signed release APK | 49,022,579 | `13448e4b91ffc00971168a024dd74a808ac3394d14866c4858c85c2875a1b022` |
| Starter knowledge pack | 25,500 | `51769d845dc163aa4a56a15d9ad68eb3d65f12f1649d56b2105a906c9e0c453b` |
| Qwen3 1.7B model pack | 1,282,441,304 | `e4ac4b6b68d55b1846c70fc877ef142954669cb8e04b85d4a74e4838430ae60b` |

The release matrix covers 12 packtool tests, 26 release-script tests, 4 scorer tests, 96 JVM tests, release lint, debug/unsigned/signed assembly, signature identity, offline APK policy, release-reflection retention, and physical instrumentation. Candidate APKs are ARM64/API 33+ and contain no network permission. The signed APK uses v2 signing and certificate SHA-256 `131127512c99a625acd0dd4baf20e1c7cd240b0fd573449197070000e3d6b666`.

The signed radios-off acceptance run used a physical Infinix SMART 20 / X6840 on Android 16. Exact screenshots and observations are in `docs/evidence/physical/infinix-x6840-android16/`.

## B31-01 — BLOCKED

The native ARM64 Android app runs on the Infinix. No supported Pixel running GrapheneOS has produced device evidence, so GrapheneOS compatibility is not claimed from API compatibility alone.

## B31-02 — PASS

The successful loaded-model run occurred on a device with 3,831,080 KiB physical memory. Measured app memory was 2,135,363 KiB PSS, 1,290,491 KiB RSS, and 927,123 KiB swap PSS, below the 12 GB environment ceiling.

## B31-03 — PASS

`StorageBudget` enforces a 50,000,000,000-byte installed cap plus archive/extraction headroom. Overflow and low-space behavior are tested; imported model and starter packs total about 1.283 GB.

## B31-04 — PASS

Airplane mode was enabled and Wi-Fi/mobile data disabled before a real model load and completed cited query. The exact installed signed APK also passed static offline policy checks.

## B31-05 — PASS

The manifest requests neither `INTERNET` nor `ACCESS_NETWORK_STATE`, disables cleartext, includes no scanned network/API client package, and routes inference through local JNI. Offline audits cover debug, unsigned release, and signed release artifacts.

## B31-06 — PASS

The APK audit found no Google Play Services or Firebase packages. Instrumentation asserts that Google API availability classes are absent, and core use succeeded without Play Services integration.

## B31-07 — BLOCKED

The frozen 18-question hardware run completed with 16 `COMPLETE` and 2 explicit `INSUFFICIENT` rows across explanation, comparison, synthesis, reasoning, factual, and abstention categories. The starter corpus is intentionally narrow and outputs have not been scored against a named baseline, so broad research-quality coverage is not claimed.

## B31-08 — PASS

The demonstrated loaded-model lookup completed in 41,276 ms for 108 tokens (2.62 tokens/s) with 34 ms retrieval. That is usable for a real offline lookup on this low-memory device. Sustained benchmark polling bounds ranged from 5 to 155 seconds, so this pass is not a claim of uniformly fast responses.

## B31-09 — PASS

The reviewed tree is public at `https://github.com/0x94t3z/fieldatlas`.

## B31-10 — PASS

The tree contains source, exact submodule gitlink, deterministic pack builders, CI, toolchain bootstrap, model/data provenance, release gates, and reproduction instructions. A healthy-network clean clone must still initialize the pinned llama.cpp submodule before publication.

## B31-11 — PASS

`MODELS.md`, `DATASETS.md`, immutable revisions, licenses, hashes, SQLite FTS5 schema, chunking, and deterministic indexes document all model/data resources.

## B31-12 — PASS

The signed release installed, loaded the real Qwen model, retrieved local evidence, and completed cited inference with radios disabled on physical Infinix X6840 hardware.

## B31-13 — PASS

Observed acceptance bounds were about 8 seconds for APK installation, at most 64 seconds for model-pack import, at most 5 seconds for knowledge import, about 30 seconds for model load, and 41.3 seconds for the demonstrated query. These are field observations, not a controlled clean-install benchmark.

## B31-14 — PASS

The repository provides deterministic knowledge/model pack builders plus immutable model download, checksum, USB transfer, import, and troubleshooting instructions.

## B31-15 — BLOCKED

The authentic device demo is published with the GitHub release, but no X or Farcaster post URL is recorded.

## B31-16 — BLOCKED

The public recording shows offline operation, explanation, comparison, synthesis, source cards, model reload, proof metrics, and abstention. It does not yet establish the requested difficult-query bar through a public X or Farcaster post.

## B31-17 — BLOCKED

No POIDH claim transaction is recorded.

## B31-18 — BLOCKED

The current Field Notebook build has a new signed local-device record, but it must be committed, pushed, and released before a POIDH claim can truthfully identify it as the public reviewed version.

## B31-19 — PASS

Project-authored corpus licensing, upstream notices, dependency versions, release signing, malicious-permission checks, private-path scans, secret scans, and exact-artifact audits are documented. Apache Commons Compress is updated to 1.28.0. This is a local compliance review, not a warranty or issuer determination.

## B31-20 — BLOCKED

The deterministic paired scorer emits per-question, category, and overall results and refuses issuer-success wording. The local run exists, but a frozen named online-baseline output has not been collected, so no greater-than-50-percent claim is made.
