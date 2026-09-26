# POIDH bounty 31 release audit

Source: `https://poidh.xyz/mainnet/bounty/31`. The reviewed text is preserved in `docs/compliance/bounty-31-source.md`; SHA-256: `81507393d46e58800373024441b2222a271fa8bc4e2a0fc77f0dd4556de7a4bb`.

Statuses are evidence gates, not aspirations. `PASS` means the named artifact exists and was checked; `BLOCKED` identifies missing evidence.

## Candidate verification

This audit covers the unreleased Field Atlas `1.2.0` integration candidate (`versionCode` 14). It applies the fork's offline-research improvements to the existing Field Notebook UI: on-device LLM query planning, selectable answer-model packs, optional offline Vosk voice input, vector-aware retrieval with lexical fallback, answer history, and detailed progress. The bundled focused Field Atlas Reference pack still installs automatically. No tag or GitHub release is claimed for this candidate.

Physical testing found and fixed three release-path defects before review: R8 removed a private JNI callback, native conversation resets could run on the Android main thread, and generic/modal retrieval terms could introduce unrelated citations. The final airplane-mode query used the specific planned terms `boiling`, `filtration`, `pathogens`, and `metals`, selected one relevant local passage, produced a clickable citation, and opened the exact source without a Field Atlas crash or ANR. Cited answers require local-evidence matches; a question without a match falls back to an explicitly uncited offline-model answer.

The table below identifies the current candidate plus the large local model used in the physical run.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Signed 1.2.0 integration APK | 93,528,063 | `5eed91fca8af33cceee44c48c8dae68b8aeb7686c9dcbcba6658a261bfaa8fcc` |
| Bundled Field Atlas Reference pack | 45,976 | `75e5fea70d94ea17803f4e77699796d0837926d2372b11883ba6806a37e76816` |
| Qwen3 1.7B model pack | 1,282,441,304 | `e4ac4b6b68d55b1846c70fc877ef142954669cb8e04b85d4a74e4838430ae60b` |
| Qwen3.5 2B model pack | 1,396,200,903 | `50326d8d18578faef80cdfd4b72ade8d090085dc57847b58bf385753eaec94c8` |
| Biology vector knowledge pack | 1,567,877,980 | `0cc4cfddc2eb6660707e3388e5e2a6f687c334cd1bb74c4f0621d4becb091d01` |

The release matrix covers 12 packtool tests, 26 repository-script tests, 4 scorer tests, 176 JVM tests, and 30 connected Android tests on the Infinix X6840, plus release lint, debug/signed assembly, signature identity, and the offline APK policy. The candidate is ARM64/API 33+ and contains no network permission. The signed APK uses v2 signing and certificate SHA-256 `131127512c99a625acd0dd4baf20e1c7cd240b0fd573449197070000e3d6b666`.

The signed radios-off acceptance run used a physical Infinix SMART 20 / X6840 on Android 16. Exact screenshots and observations are in `docs/evidence/physical/infinix-x6840-android16/`.

## B31-01 — PASS

The native ARM64 Android app runs on the physical Infinix SMART 20 / X6840. This satisfies the bounty’s real-device requirement for a compatible Android device; GrapheneOS is an alternative device path, not a requirement to test both platforms.

## B31-02 — PASS

The device reports 3,831,080 KiB physical memory. With Qwen3.5 selected and prepared, the retained observation reports 1,580,107 KiB total PSS, 142,867 KiB total RSS, and 1,509,572 KiB swap PSS, below the 12 GB environment ceiling. This is one field observation rather than a peak-memory guarantee.

## B31-03 — PASS

`StorageBudget` enforces a 50,000,000,000-byte installed cap plus archive/extraction headroom. Overflow and low-space behavior are tested. Both answer models, the bundled Reference pack, and the biology vector pack remain well below 50 GB. The Starter artifact is historical provenance only.

## B31-04 — PASS

Airplane mode was enabled and Wi-Fi/mobile data disabled before a real model load and completed cited query. The exact installed signed APK also passed static offline policy checks.

## B31-05 — PASS

The manifest requests neither `INTERNET` nor `ACCESS_NETWORK_STATE`, disables cleartext, includes no scanned network/API client package, and routes inference through local JNI. Offline audits cover debug, unsigned release, and signed release artifacts.

## B31-06 — PASS

The APK audit found no Google Play Services or Firebase packages. Instrumentation asserts that Google API availability classes are absent, and core use succeeded without Play Services integration.

## B31-07 — PASS

The physical-device record includes a seasons explanation, a qualified water-treatment comparison, solar-storage reasoning, and earlier cross-source synthesis evidence. The integration device run demonstrates the new LLM query planner while rejecting unrelated partial matches and opening the exact supporting passage. Field Atlas also supports uncited offline-model answers when no matching source is retrieved. The Reference pack is focused rather than comprehensive, and these results do not establish broad or frontier-level research quality.

## B31-08 — PASS

The demonstrated loaded-model lookup completed in 41,276 ms for 108 tokens (2.62 tokens/s) with 34 ms retrieval. That is usable for a real offline lookup on this low-memory device. Sustained benchmark polling bounds ranged from 5 to 155 seconds, so this pass is not a claim of uniformly fast responses.

## B31-09 — PASS

The reviewed tree is public at `https://github.com/0x94t3z/fieldatlas`.

## B31-10 — PASS

The tree contains source, exact submodule gitlink, deterministic pack builders, the bundled Reference knowledge pack, CI, toolchain bootstrap, model/data provenance, release gates, and reproduction instructions. A healthy-network clean clone initializes the pinned llama.cpp submodule recursively.

## B31-11 — PASS

`MODELS.md`, `DATASETS.md`, immutable revisions, licenses, hashes, SQLite FTS5 schema, chunking, and deterministic indexes document all model/data resources.

## B31-12 — PASS

The signed integration build installed in place, retained the earlier Qwen3/Reference acceptance assets, and preserved their cited radios-off result. The pinned Qwen3.5 pack and fork biology vector pack were subsequently checksum-verified, imported, selected, and prepared on the same device; the Reference pack was disabled. A full Qwen3.5/vector answer run was intentionally not claimed or repeated during this bounded audit.

## B31-13 — PASS

Observed acceptance bounds were about 8 seconds for APK installation, at most 64 seconds for model-pack import, at most 5 seconds for knowledge import, about 30 seconds for model load, and 41.3 seconds for the demonstrated query. These are field observations, not a controlled clean-install benchmark.

## B31-14 — PASS

The repository provides deterministic knowledge/model pack builders plus immutable model download, checksum, USB transfer, import, and troubleshooting instructions.

## B31-15 — PASS

The authentic device demo is published in the repository and linked from the public Farcaster post at `https://farcaster.xyz/0x94t3z.eth/0x9538cba8`. The post links the public repository and briefly describes the local-model and local-knowledge approach.

## B31-16 — PASS

The public Farcaster thread demonstrates offline local inference with cited evidence across multiple research tasks: cross-source water comparison, water-safety reasoning, and solar-storage reasoning. The thread links the public repository and explains the local-model/local-knowledge approach; the posted recordings are named `cross-source-water-proof.mp4`, `water-safety-proof.mp4`, and `solar-storage-proof.mp4`.

## B31-17 — PASS

The POIDH claim transaction and relevant screenshot are recorded in `docs/compliance/bounty-31.json`.

## B31-18 — PASS

The version reviewed at claim time remains in the immutable `v1.1.10` release. This `1.2.0` integration source is published on `main` by the audited commit but has no release tag or downloadable release asset yet.


## B31-19 — PASS

Project-authored corpus licensing, upstream notices, dependency versions, release signing, malicious-permission checks, private-path scans, secret scans, and exact-artifact audits are documented. Apache Commons Compress is updated to 1.28.0. This is a local compliance review, not a warranty or issuer determination.

## B31-20 — BLOCKED

The deterministic paired scorer emitted per-question, category, and overall results for the 18-question run. Against the dynamic OpenRouter `openrouter/free` route, Field Atlas scored `0.027778` versus `0.194444` (ratio `0.142859`, or 14.3%). This exploratory result does not meet the greater-than-50-percent bar, and the router is not a pinned model/provider; no winner-quality claim is made. The recorded interpretation is in `docs/evidence/benchmark-free-router-2026-09-24/README.md`.
