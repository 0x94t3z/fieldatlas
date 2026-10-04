# Field Atlas runtime patches

`third_party/llama.cpp` is pinned to an **unmodified upstream commit**
(`60081bb2b5b3294165a4d67c5cbeebe74c868014`). Every change Field Atlas makes to the
inference runtime lives here as a patch instead of a fork commit, so the pinned
binary remains auditable against upstream.

## Applying

```bash
cd third_party/llama.cpp
git status --porcelain     # must be clean at the pinned commit
git apply ../../scripts/patches/ai-chat-generation-fixes.patch
```

The Gradle build compiles the working tree, so a fresh clone must apply the patch
before the first build (the APK's .so silently lacks the features otherwise).

## What the patch contains

The current patch also removes native prompt/token text logging, so private attachment contents are not intentionally written to logcat. Keep this patch applied when building the attachment feature.

`ai-chat-generation-fixes.patch` (three files, in the Android example library):

* **ai_chat.cpp** — conversation/generation correctness: chat-template application
  fixes, prompt overflow handling, batch decode settings, a thread cap of six (measured
  below), plus the **embedding
  encoder entry points** (`loadEncoderNative` / `embedNative` / `unloadEncoderNative`)
  used by vector-capable knowledge packs — a second, independent small model
  (BGE-small GGUF) loaded alongside the chat model.
* **InferenceEngine.kt** — public surface additions: `promptProgress`
  ("x/y tokens read" prefill progress), `resetConversation`, `setSamplerSeed`
  (dual-seed keyword planning), and the encoder functions.
* **InferenceEngineImpl.kt** — JNI externals and Kotlin plumbing for the above.

## Regenerating after local edits

```bash
cd third_party/llama.cpp
git diff > ../../scripts/patches/ai-chat-generation-fixes.patch
```

## Thread count

Inference uses the online cores minus two, capped at six (it was four). Two cores stay free
because the threads wait on each other: when the interface or the system takes a core from one
of them, all of them stall. Measured on a Redmi 13C (2× Cortex-A75, 6× Cortex-A55), Qwen3.5 2B
Q4_K_M:

| Threads | `llama-bench` prompt reading | `llama-bench` writing | In the app: first word / writing |
| --- | --- | --- | --- |
| 4 | 13.6 tok/s | 4.55 tok/s | 94.7 s / 3.69 tok/s |
| 6 | 18.6 tok/s | 5.19 tok/s | 71.7 s / 4.06 tok/s |
| 8 | 19.8 tok/s | 3.79 tok/s | not used |

The in-app runs asked the same question ("Compare mitosis and meiosis") after two minutes idle,
without any screen polling. A 512-token batch read more slowly than 256 (16.8 vs 18.6 tok/s at
six threads), so the batch stays at 256.

