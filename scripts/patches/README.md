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
  (BGE-small GGUF) loaded alongside the chat model. It also keeps the **model state after
  the fixed instructions** that open each answer prompt and restores it for the next prompt
  that opens with the same tokens (see below).
* **InferenceEngine.kt** — public surface additions: `promptProgress`
  ("x/y tokens read" prefill progress), `resetConversation`, `setSamplerSeed`
  (dual-seed keyword planning), `setThinking` (whether the chat template opens the model's
  thinking turn, used only for a model chosen to reason before it answers), `setRepack`
  (whether a load copies the weights into llama.cpp's repacked layouts; off keeps a large
  model mapped from its file, which Android can reclaim), the encoder
  functions, and `sendUserPrompt`'s `savedPrefix` with `reusedPromptTokens()`.
* **InferenceEngineImpl.kt** — JNI externals and Kotlin plumbing for the above.

Evidence paraphrases use `setSampler(seed, 1.0f)` so technical terms and quantities
can repeat without a sampling penalty. Each ordinary or review turn explicitly
restores `1.10f`; the gateway's independent echo-loop guard stays enabled.

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

## Saved instructions

Every answer prompt opens with the same fixed instructions: 396 tokens of system message and
rules for most answers. The app clears the model before each question, so it used to read them
again every time. Now the app passes the length of that opening with the prompt. The engine
reads it once, saves the sequence state (`llama_state_seq_get_data_ext`) and, for the next
prompt that opens with exactly the same tokens, restores it (`llama_state_seq_set_data_ext`)
and reads only the evidence and question. Qwen3.5 keeps recurrent state that cannot be trimmed
back to a shorter prompt, so the whole sequence state is saved; for the 2B model it is 23.9 MiB.
Any mismatch or failed restore falls back to reading the whole prompt.

Both paths read the part after the instructions in the same batches, so a restored state gives
exactly the same result as reading the instructions again. A desktop check with the pinned
library (two real field-test prompts, Qwen3.5 2B on the CPU) found a largest logit difference
of 0 and identical 48-token greedy continuations. Compared with the old single pass over the
whole prompt, the split can change a token in some answers, as a different seed would.

Measured in the app on a Redmi 13C (Qwen3.5 2B, airplane mode), the same three questions in
a row before and after the change, as the app's Answer details report them:

| Question | First word before | First word after | Prompt tokens read before / after |
| --- | --- | --- | --- |
| What causes ocean tides (first question, saves the state) | 110.9 s | 109.4 s | 1,688 / 1,688 |
| How does a heat pump heat a house | 38.9 s | 18.5 s | 584 / 188 |
| Why did the Silk Road matter for trade | 77.0 s | 55.8 s | 1,235 / 839 |

The log shows "saved state after 396 prompt tokens (25077644 bytes)" on the first question and
"restored 396 prompt tokens" on the next two. Every answer after the first in a session starts
about 20 s sooner on this phone; the restore itself takes well under a second.

