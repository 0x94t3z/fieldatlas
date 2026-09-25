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

`ai-chat-generation-fixes.patch` (three files, in the Android example library):

* **ai_chat.cpp** — conversation/generation correctness: chat-template application
  fixes, prompt overflow handling, batch decode settings, plus the **embedding
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
