# Field Atlas models

[Back to the overview](README.md) · [Phone setup](docs/installation.md)

A **model pack** is the large local file that lets Field Atlas generate answers. The app accepts a verified `.fapack`, not a raw `.gguf`. Choose the instructions that match your APK:

| App | Model to use | Where to get instructions |
| --- | --- | --- |
| [Public signed v1.1.10](https://github.com/0x94t3z/fieldatlas/releases/tag/v1.1.10) | Qwen3 1.7B | [Model instructions at the v1.1.10 tag](https://github.com/0x94t3z/fieldatlas/blob/v1.1.10/MODELS.md) |
| Current source/debug build | Qwen3.5 2B | The build steps below |

No model pack is bundled with the APK or attached to the public release. Building a pack currently needs a computer. Once installed, the model runs without internet; a knowledge pack is optional and adds inspectable local sources. The other model definitions below are alternatives for developers, not a claim that they all passed the full phone benchmark.

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
python3 tools/build_model_packs.py \
  --pack qwen3.5-2b-q4-k-m \
  --out-dir build/packs/model \
  --cache-dir build/model-cache
printf '%s  %s\n' \
  50326d8d18578faef80cdfd4b72ade8d090085dc57847b58bf385753eaec94c8 \
  build/packs/model/qwen3.5-2b-q4-k-m-1.0.0.fapack | shasum -a 256 -c -
```

Copy `build/packs/model/qwen3.5-2b-q4-k-m-1.0.0.fapack` to Android and import it through **Choose model pack**. Do not select the raw `.gguf` in the app.

The builder downloads the pinned GGUF, rejects a hash mismatch, and stores it uncompressed in a deterministic pack. Model weights are not committed to Git. The exact upstream sources and runtime profile are in [`models/compact-qwen3.5-2b.example.json`](models/compact-qwen3.5-2b.example.json).

## Limits and validation status

Qwen3.5 2B is a compact offline model, not a frontier model. It can miss nuance, make reasoning errors, or produce unsupported claims. Retrieval grounding reduces but does not eliminate hallucination. The Qwen3.5 pack was checksum-verified, imported, selected, and prepared on an Infinix SMART 20 / X6840; this alone does not establish answer quality or GrapheneOS compatibility. The older Qwen3 1.7B acceptance result remains identified as historical in [`docs/evidence/physical/infinix-x6840-android16/`](docs/evidence/physical/infinix-x6840-android16/).

## Additional verified pack definitions

Field Atlas can install multiple model packs and select one in Library. The following
registry files pin the exact upstream revision, artifact SHA-256, license, quantization,
and runtime profile used by the deterministic pack builder:

- `models/compact-minicpm5-1b.example.json` — MiniCPM5 1B Q4_K_M, Apache-2.0;
  upstream thinking remains enabled and can consume the answer budget.
- `models/compact-minicpm5-1b-nothink.example.json` — the same published MiniCPM5
  weights with a locally adjusted, equal-length chat-template setting that suppresses
  thinking. The registry records the resulting artifact hash.

Build any pinned model pack with `tools/build_model_packs.py`. These definitions document
compatible candidates; they are not claims that every candidate has passed the complete
physical-device benchmark.

## Speech and vector encoders

The APK bundles Vosk's `vosk-model-small-en-us-0.15` English speech model for optional
offline dictation. Its archive source, SHA-256, and Apache-2.0 license are recorded in
`app/src/main/assets/vosk-model-en-us-015/PROVENANCE.txt`. Larger Vosk models can be
packaged as AUDIO fapacks by `tools/build_model_packs.py`.

Vector-capable knowledge packs include their own query encoder GGUF and declare its model,
dimension, hash, source URL, and similarity threshold in the pack manifest. The current
builder targets BGE-small English embeddings with int8 corpus vectors. Field Atlas verifies
the encoder as a declared pack artifact and falls back to keyword retrieval if it cannot be
loaded.
