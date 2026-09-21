# Field Atlas models

## Compact candidate

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

The builder streams the model, rejects a hash mismatch, and stores the verified GGUF without compression or a managed-memory copy. Model weights are not committed to Git.

## Limits and validation status

The 1.7B model is a speed/quality compromise, not a frontier model. It can miss nuance, make reasoning errors, or fail citation formatting. Retrieval grounding reduces but does not eliminate hallucination. The pinned runtime's 8,192-token KV cache may be material on a 4 GB device. Memory, thermal behavior, token rate, cancellation, and reload must be measured on the Infinix Smart 20 before claiming compatibility; no such result is inferred from model size alone.
