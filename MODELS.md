# Field Atlas models

## Default compact candidate

Field Atlas is configured for `Qwen3-1.7B-Q4_K_M.gguf` from `ggml-org/Qwen3-1.7B-GGUF`, pinned at Hugging Face revision `daeb8e2d528a760970442092f6bf1e55c3b659eb`.

- Upstream model: `Qwen/Qwen3-1.7B`
- License: Apache-2.0
- GGUF size: approximately 1.28 GB
- Expected SHA-256: `d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5`
- Quantization: Q4_K_M, published by ggml-org
- Runtime: llama.cpp commit `60081bb2b5b3294165a4d67c5cbeebe74c868014`
- Runtime context: 8,192 tokens, hard-coded by that pinned Android example
- Sampling: temperature 0.3, inherited from the pinned Android example

Download and verify on a desktop:

```sh
curl -L --fail \
  -o Qwen3-1.7B-Q4_K_M.gguf \
  'https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/daeb8e2d528a760970442092f6bf1e55c3b659eb/Qwen3-1.7B-Q4_K_M.gguf?download=true'
printf '%s  %s\n' \
  d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5 \
  Qwen3-1.7B-Q4_K_M.gguf | shasum -a 256 -c -
python3 scripts/model_manifest.py \
  --model Qwen3-1.7B-Q4_K_M.gguf \
  --config models/compact-qwen3-1.7b.example.json \
  --output qwen3-1.7b-q4-k-m-1.0.0.fapack
```

The output file to copy to Android is `qwen3-1.7b-q4-k-m-1.0.0.fapack`. Field Atlas imports this model pack through **Choose model pack**. The raw `Qwen3-1.7B-Q4_K_M.gguf` is only an input to the pack builder.

The builder streams the model, rejects a hash mismatch, and stores the verified GGUF without compression or a managed-memory copy. Model weights are not committed to Git.

## Limits and validation status

The 1.7B model is a speed/quality compromise, not a frontier model. It can miss nuance, make reasoning errors, or fail citation formatting. Retrieval grounding reduces but does not eliminate hallucination. The pinned runtime's 8,192-token KV cache is material on a 4 GB device. It completed the cited radios-off acceptance query on an Infinix SMART 20 / X6840; the measured run is preserved in [`docs/evidence/physical/infinix-x6840-android16/`](docs/evidence/physical/infinix-x6840-android16/). No GrapheneOS result or frontier-quality claim is inferred from that device run.

## Additional verified pack definitions

Field Atlas can install multiple model packs and select one in Library. The following
registry files pin the exact upstream revision, artifact SHA-256, license, quantization,
and runtime profile used by the deterministic pack builder:

- `models/compact-qwen3.5-2b.example.json` — Qwen3.5 2B Q4_K_M, Apache-2.0,
  approximately 1.4 GB. The pinned pack was checksum-verified, imported, selected, and
  prepared on the Infinix; no full answer run is claimed for it yet.
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
