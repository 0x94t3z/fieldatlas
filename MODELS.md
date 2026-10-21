# Field Atlas models

[Back to the overview](README.md) · [Phone setup](docs/installation.md)

A **model** is the large local file that lets Field Atlas generate answers: the `.gguf` file its publisher offers, or a `.fapack` built from it. A file Field Atlas tested is checked against its pinned size and SHA-256 and keeps its tested settings; from v1.4.2, any other GGUF model installs as your own, untested, when the phone has the memory for it. Choose the instructions that match your APK:

| App | Model to use | Where to get instructions |
| --- | --- | --- |
| [Public signed v1.1.10](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10) | Qwen3 1.7B | [Model instructions at the v1.1.10 tag](https://github.com/0x94t3z/fieldatlas/blob/v1.1.10/MODELS.md) |
| [v1.2.0](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.2.0) to [v1.4.2](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.4.2) | Qwen3.5 2B, the default | Download in setup, or the build steps below |
| [v1.4.0](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.4.0) on a phone with 8 GB of memory or more, and [v1.4.2](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.4.2) from 6 GB | Qwen3.5 4B, optional in 1.4.0, offered first in 1.4.2 setup | Download in setup or Library; see the [notes below](#qwen35-4b-in-v140-and-v142) |
| [v1.4.2](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.4.2), on a phone with 12 GB of memory | Gemma 4 26B-A4B (UD-Q2_K_XL), offered first in setup | Download in setup or Library; see [Gemma 4 26B in v1.4.2](#gemma-4-26b-in-v142) |

No model pack is bundled with the APK or attached to the public release. Version 1.2.0 can instead download the recommended pinned GGUF directly during setup, verify its exact size and SHA-256, and install it in app-private storage. From v1.4.2, a phone that cannot download can import the model's `.gguf` itself, downloaded on another device from the link below; building a `.fapack` is optional and needs a computer. Once installed, the model runs without internet; a knowledge pack is optional and adds inspectable local sources. The other model definitions below are alternatives for developers, not a claim that they all passed the full phone benchmark.

## Recommended answer model

The current Field Atlas setup uses `Qwen_Qwen3.5-2B-Q4_K_M.gguf`, published by bartowski from `Qwen/Qwen3.5-2B`. The GGUF is pinned at revision `7d26695454df6de5fbcce2e58681e62dae06ce43`.

- Upstream model: `Qwen/Qwen3.5-2B`
- License: Apache-2.0
- GGUF size: approximately 1.40 GB
- Expected GGUF SHA-256: `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8`
- Expected `.fapack` SHA-256: `50326d8d18578faef80cdfd4b72ade8d090085dc57847b58bf385753eaec94c8`
- Quantization: Q4_K_M, published by bartowski
- Runtime: llama.cpp commit `60081bb2b5b3294165a4d67c5cbeebe74c868014`
- Runtime context and sampling: configured by the current app and pinned runtime, not by the model pack

Build the verified pack on a computer from the repository root:

```sh
python3 tools/packs/build_model_packs.py \
  --pack qwen3.5-2b-q4-k-m \
  --out-dir build/packs/model \
  --cache-dir build/model-cache
printf '%s  %s\n' \
  50326d8d18578faef80cdfd4b72ade8d090085dc57847b58bf385753eaec94c8 \
  build/packs/model/qwen3.5-2b-q4-k-m-1.0.0.fapack | shasum -a 256 -c -
```

Copy `build/packs/model/qwen3.5-2b-q4-k-m-1.0.0.fapack` to Android and import it through **Import model pack**, or use **Download recommended model** in setup. Do not select a raw `.gguf` in the file picker.

The builder downloads the pinned GGUF, rejects a hash mismatch, and stores it uncompressed in a deterministic pack. Model weights are not committed to Git. The exact upstream sources and runtime profile are in [`models/compact-qwen3.5-2b.example.json`](models/compact-qwen3.5-2b.example.json).

## Gemma 4 E2B, tried and not offered

Google's Gemma 4 E2B (QAT Q4_0, pinned at `google/gemma-4-E2B-it-qat-q4_0-gguf` revision
`675cff42a74c774d6cb76f76d8eacb49b48c9b93`, GGUF SHA-256
`fa401b55b07ee70a54c6dae3903c783a6e65064312529ea57175cb5f8dec6634`) was tried as a second choice
after 1.4.0 and was never released.

- On 180 development questions it seemed better than Qwen3.5 2B, with half as many factual
  errors. Those questions had been seen during
  development.
- On a Redmi 13C, 20 fresh questions were asked in the installed app with each model and
  graded blind by two graders. Qwen3.5 2B scored 4.65 of 10 against 3.75. Gemma still made fewer
  factual errors (0.12 per answer against 0.50), but scored 3 or less on 12 of the 20. Where the
  saved passages did not state every part of the answer, it often replied that the excerpts do
  not contain it, or gave one line, while Qwen3.5 2B explained.

Gemma 4 E2B is therefore not offered.

## Qwen3.5 4B in v1.4.0 and v1.4.2

Version 1.4.0 offered Qwen3.5 4B on phones with 8 GB of memory, and 1.4.1 did not. Version 1.4.2
offers it from 6 GB, first in setup, with Qwen3.5 2B still listed below it; on a phone with less
memory only the 2B is offered. Citing the saved passages, on 120 new questions graded blind, the
4B made half the factual errors of the 2B (0.30 against 0.62 per answer) and gave a good answer to 28 of them against 27
([details](docs/evaluation.md#142-release-candidate-on-fresh-questions-october-17-18)). In 1.4.2 it
answers ordinary questions from its own knowledge instead, with the passages listed under the
answer: on 40 newer questions (set 37) it scored 41.8% against 26.0% for the cited 2B, with more
wrong details (1.94 against 0.71 per answer)
([details](docs/evaluation.md#answers-from-the-models-own-knowledge-and-a-model-for-12-gb-phones-october-19-20)).
It has not been timed on an 8 GB phone; on a 6 GB Redmi 13C, read from its file, five answers took
170 to 480 seconds (median 277). A phone that already has it keeps it and can choose it in Library. Its pinned
file and pack are described in
[`models/compact-qwen3.5-4b.example.json`](models/compact-qwen3.5-4b.example.json) and in the
[1.4.0 model notes](https://github.com/0x94t3z/fieldatlas/blob/v1.4.0/MODELS.md#optional-larger-model).

## Gemma 4 26B in v1.4.2

On a phone with 12 GB of memory, setup offers Gemma 4 26B-A4B first: unsloth's UD-Q2_K_XL file of
`google/gemma-4-26B-A4B-it` (10.5 GB, Apache-2.0, SHA-256
`2a1d26dfe6ea00a467940a5728316af6edb366bbdba950d65b85d232392fb658`), described in
[`models/compact-gemma-4-26b-a4b.example.json`](models/compact-gemma-4-26b-a4b.example.json). It reasons
before it answers and answers from its own knowledge: a good answer to 27 of the 40 questions of set 37,
with 0.44 wrong details per answer, on the desktop pipeline. It has run on a 12 GB Android emulator
(median 100 seconds an answer), not on a 12 GB phone, and it has no `.fapack` yet: setup downloads
the pinned file and checks its size and SHA-256.

## Limits and validation status

Qwen3.5 2B is a compact offline model, not a frontier model. It can miss nuance, make reasoning errors, or produce unsupported claims. Retrieval grounding reduces but does not eliminate hallucination. The Qwen3.5 pack was checksum-verified, imported, selected, and prepared on an Infinix SMART 20 / X6840; this alone does not establish answer quality or GrapheneOS compatibility. The older Qwen3 1.7B acceptance result remains identified as historical in [`docs/evidence/physical/infinix-x6840-android16/`](docs/evidence/physical/infinix-x6840-android16/).

## Additional verified pack definitions

Field Atlas can install multiple model packs and select one in Library. The registry files in
`models/` pin the exact upstream revision, artifact SHA-256, license, quantization, and
runtime profile used by the deterministic pack builder.

Build any pinned model pack with `tools/packs/build_model_packs.py`. These definitions document
compatible candidates; they are not claims that every candidate has passed the complete
physical-device benchmark.

## Speech and vector encoders

The APK bundles Vosk's `vosk-model-small-en-us-0.15` English speech model for optional
offline dictation. Its archive source, SHA-256, and Apache-2.0 license are recorded in
`app/src/main/assets/vosk-model-en-us-015/PROVENANCE.txt`. Larger Vosk models can be
packaged as AUDIO fapacks by `tools/packs/build_model_packs.py`.

Vector-capable knowledge packs include their own query encoder GGUF and declare its model,
dimension, hash, source URL, and similarity threshold in the pack manifest. The current
builder targets BGE-small English embeddings with int8 corpus vectors. Field Atlas verifies
the encoder as a declared pack artifact and falls back to keyword retrieval if it cannot be
loaded.
