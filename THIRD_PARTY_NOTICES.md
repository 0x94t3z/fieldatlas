# Third-party notices

This file is a navigation index for significant source and runtime components. It does not replace the license texts and notices distributed by their copyright holders.

| Component | Pinned version | License | Upstream license |
| --- | --- | --- | --- |
| `ggml-org/llama.cpp` | commit `60081bb2b5b3294165a4d67c5cbeebe74c868014` | MIT | [llama.cpp LICENSE](https://github.com/ggml-org/llama.cpp/blob/60081bb2b5b3294165a4d67c5cbeebe74c868014/LICENSE) |
| `ggml-org/Qwen3-1.7B-GGUF` | revision `daeb8e2d528a760970442092f6bf1e55c3b659eb` | Apache-2.0 | [pinned model card](https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/blob/daeb8e2d528a760970442092f6bf1e55c3b659eb/README.md) |
| `Qwen/Qwen3.5-2B` / bartowski GGUF | revisions pinned in `models/compact-qwen3.5-2b.example.json` | Apache-2.0 | [Qwen3.5-2B model card](https://huggingface.co/Qwen/Qwen3.5-2B) |
| `openbmb/MiniCPM5-1B` | revisions pinned in `models/compact-minicpm5-1b*.example.json` | Apache-2.0 | [MiniCPM5-1B model card](https://huggingface.co/openbmb/MiniCPM5-1B) |
| Vosk Android | `0.3.47` | Apache-2.0 | [Vosk API repository](https://github.com/alphacep/vosk-api) |
| Vosk small English speech model | `vosk-model-small-en-us-0.15` | Apache-2.0 | [Vosk models](https://alphacephei.com/vosk/models) |
| BGE-small vector encoder | selected and pinned by each vector fapack manifest | MIT | [BGE-small model card](https://huggingface.co/BAAI/bge-small-en-v1.5) |
| AndroidX and Jetpack Compose | versions in `gradle/libs.versions.toml` | Apache-2.0 | [AndroidX license notice](https://source.android.com/docs/setup/about/licenses) |
| Kotlin | `2.1.0` | Apache-2.0 | [Kotlin license](https://github.com/JetBrains/kotlin/blob/v2.1.0/license/LICENSE.txt) |
| kotlinx.serialization | `1.4.0` | Apache-2.0 | [kotlinx.serialization LICENSE](https://github.com/Kotlin/kotlinx.serialization/blob/v1.4.0/LICENSE.txt) |
| kotlinx.coroutines | `1.7.3` | Apache-2.0 | [kotlinx.coroutines LICENSE](https://github.com/Kotlin/kotlinx.coroutines/blob/1.7.3/LICENSE.txt) |
| Apache Commons Compress | `1.28.0` | Apache-2.0 | [Commons Compress LICENSE](https://github.com/apache/commons-compress/blob/rel/commons-compress-1.28.0/LICENSE.txt) |

Exact model file names, SHA-256 digests, quantization, sources, and build procedures are recorded in `MODELS.md`, the `models/*.example.json` registries, and vector-pack manifests. Answer-model weights are not committed to Git; the small offline Vosk speech model is bundled in the APK and carries its own provenance record.

The four project-authored starter documents are dedicated to the public domain under CC0-1.0. Their per-document provenance is recorded in `fixtures/starter/LICENSES.md`.
