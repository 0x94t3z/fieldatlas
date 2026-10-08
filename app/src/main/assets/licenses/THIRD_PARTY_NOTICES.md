# Third-party notices

This file is a navigation index for significant source and runtime components. It does not replace the license texts and notices distributed by their copyright holders.

The app shows this list and the full licence texts it ships (Apache-2.0, llama.cpp's MIT licence, Bouncy Castle, LLVM, the Source Serif 4 font licence and the OCR components' licences) under **More → Licences**.

| Component | Pinned version | License | Upstream license |
| --- | --- | --- | --- |
| `vbuterin/fieldatlas` fork, branch `vector-search` (retrieval, history, voice, pack toolchain, Biology pack; see README Acknowledgements) | commits 337d334–769e85d, merged in 4967a49 | carries this repository's Apache-2.0 LICENSE | [vbuterin/fieldatlas](https://github.com/vbuterin/fieldatlas/tree/vector-search) |
| `ggml-org/llama.cpp` | commit `60081bb2b5b3294165a4d67c5cbeebe74c868014` | MIT | [llama.cpp LICENSE](https://github.com/ggml-org/llama.cpp/blob/60081bb2b5b3294165a4d67c5cbeebe74c868014/LICENSE) |
| `Qwen/Qwen3.5-2B` / bartowski GGUF | revisions pinned in `models/compact-qwen3.5-2b.example.json` | Apache-2.0 | [Qwen3.5-2B model card](https://huggingface.co/Qwen/Qwen3.5-2B) |
| `Qwen/Qwen3.5-4B` / bartowski GGUF (optional download) | revisions pinned in `models/compact-qwen3.5-4b.example.json` | Apache-2.0 | [Qwen3.5-4B model card](https://huggingface.co/Qwen/Qwen3.5-4B) |
| `openbmb/MiniCPM5-1B` | revisions pinned in `models/compact-minicpm5-1b*.example.json` | Apache-2.0 | [MiniCPM5-1B model card](https://huggingface.co/openbmb/MiniCPM5-1B) |
| Vosk Android | `0.3.75` | Apache-2.0 | [Vosk API repository](https://github.com/alphacep/vosk-api) |
| Vosk small English speech model | `vosk-model-small-en-us-0.15` | Apache-2.0 | [Vosk models](https://alphacephei.com/vosk/models) |
| BGE-small vector encoder | selected and pinned by each vector fapack manifest | MIT | [BGE-small model card](https://huggingface.co/BAAI/bge-small-en-v1.5) |
| Source Serif 4 | current Google Fonts source | SIL Open Font License 1.1 | [`docs/licenses/source-serif-4-OFL.txt`](docs/licenses/source-serif-4-OFL.txt) |
| Manrope (bundled interface font) | Google Fonts variable font | SIL Open Font License 1.1 | [`docs/licenses/manrope-OFL.txt`](docs/licenses/manrope-OFL.txt) |
| AndroidX and Jetpack Compose | versions in `gradle/libs.versions.toml` | Apache-2.0 | [AndroidX license notice](https://source.android.com/docs/setup/about/licenses) |
| Kotlin | `2.1.0` | Apache-2.0 | [Kotlin license](https://github.com/JetBrains/kotlin/blob/v2.1.0/license/LICENSE.txt) |
| kotlinx.serialization | `1.4.0` | Apache-2.0 | [kotlinx.serialization LICENSE](https://github.com/Kotlin/kotlinx.serialization/blob/v1.4.0/LICENSE.txt) |
| kotlinx.coroutines | `1.7.3` | Apache-2.0 | [kotlinx.coroutines LICENSE](https://github.com/Kotlin/kotlinx.coroutines/blob/1.7.3/LICENSE.txt) |
| Apache Commons Compress | `1.28.0` | Apache-2.0 | [Commons Compress LICENSE](https://github.com/apache/commons-compress/blob/rel/commons-compress-1.28.0/LICENSE.txt) |
| Tesseract4Android (Tesseract OCR) | `4.9.0` | Apache-2.0 | [Tesseract4Android repository](https://github.com/adaptech-cz/Tesseract4Android) |
| Tesseract English model (`eng.traineddata`, bundled in the APK) | SHA-256 pinned in `ImageTextReader.kt` | Apache-2.0 | [tessdata repository](https://github.com/tesseract-ocr/tessdata) |
| PdfBox-Android (port of Apache PDFBox) | `2.0.27.0` | Apache-2.0 | [PdfBox-Android repository](https://github.com/TomRoush/PdfBox-Android) |
| Liberation Sans Regular (bundled by PdfBox-Android) | as shipped in PdfBox-Android `2.0.27.0` | SIL Open Font License 1.1 | [Liberation fonts](https://github.com/liberationfonts/liberation-fonts) |
| Adobe Glyph List and AFM metrics (bundled by PdfBox-Android) | as shipped in PdfBox-Android `2.0.27.0` | BSD-style Adobe licenses | [Adobe Glyph List](https://github.com/adobe-type-tools/agl-aglfn) |
| Bouncy Castle (`bcprov`, `bcpkix`, `bcutil`, used by PdfBox-Android for encrypted PDFs) | `1.72` (`jdk15to18`) | Bouncy Castle Licence (MIT) | [Bouncy Castle licence](https://www.bouncycastle.org/licence.html) |
| Apache Commons Codec, IO and Lang (used by Commons Compress) | `1.19.0`, `2.20.0`, `3.18.0` | Apache-2.0 | [Apache Commons](https://commons.apache.org/) |
| JNA (`libjnidispatch.so`, used by Vosk) | `5.18.1` | Apache-2.0 (dual-licensed with LGPL-2.1; used under Apache-2.0) | [JNA repository](https://github.com/java-native-access/jna) |
| SQLite (bundled by `androidx.sqlite:sqlite-bundled`, `libsqliteJni.so`) | as shipped in `2.7.1` | Public domain | [SQLite copyright](https://www.sqlite.org/copyright.html) |
| LLVM OpenMP runtime (`libomp.so`, from the Android NDK, used by llama.cpp) | as shipped in the pinned NDK | Apache-2.0 WITH LLVM-exception | [LLVM licence](https://llvm.org/LICENSE.txt) |
| Leptonica, libjpeg and libpng (`libleptonica.so`, `libjpeg.so`, `libpngx.so`, bundled by Tesseract4Android) | as shipped in `4.9.0` | Leptonica licence (BSD-2-Clause style), IJG licence, PNG Reference Library licence | [Tesseract4Android repository](https://github.com/adaptech-cz/Tesseract4Android) |

Exact model file names, SHA-256 digests, quantization, sources, and build procedures are recorded in `MODELS.md`, the `models/*.example.json` registries, and vector-pack manifests. Answer-model weights are not committed to Git; the small offline Vosk speech model is bundled in the APK and carries its own provenance record.

The four project-authored starter documents are dedicated to the public domain under CC0-1.0. Their per-document provenance is recorded in `fixtures/starter/LICENSES.md`.

## Data in the bundled files and the downloadable collections

| Data | Used in | License | Credit and source |
| --- | --- | --- | --- |
| OpenStreetMap | Vegan places and Essentials collections | ODbL 1.0 | © OpenStreetMap contributors; [copyright](https://www.openstreetmap.org/copyright). Every listing and answer carries the credit. |
| Wikipedia (English and Simple English) | Encyclopedia collection, Encyclopedia in depth collection (49,822 English vital articles by section, each at a pinned revision), 123 articles in Cryptography & Ethereum, four bundled emergency guides, the bundled emergency-number table (revision 1377218654) | CC BY-SA 4.0 | Wikipedia contributors; every passage links to its revision. |
| Wikivoyage (English) | Travel guides and Travel places collections | CC BY-SA 4.0 | Wikivoyage contributors; every passage links to its revision. |
| Ethereum Improvement Proposals, ERCs and consensus specifications | Cryptography & Ethereum collection | CC0 1.0 | `ethereum/EIPs`, `ethereum/ERCs` and `ethereum/consensus-specs` at the commits in `DATASETS.md`; two proposals licensed CC BY-NC-SA are left out. |
| NIST FIPS 203, 204 and 205, SP 800-208, IR 8547 | Cryptography & Ethereum collection | US government work (public domain in the United States) | National Institute of Standards and Technology |
| PubMed abstracts, Fight Aging! archive, Human Ageing Genomic Resources | Biology & longevity collection (from the `vbuterin/fieldatlas` fork) | as declared by the pack (`public-data`) | NCBI, fightaging.org and genomics.senescence.info; see `DATASETS.md`. |
| US federal first-aid and safety pages | bundled emergency guides | public domain (US government works) | CDC, NIOSH, NIH/NLM, NIH/NIAMS, NWS/NOAA, NPS, FEMA and the U.S. Fire Administration |

The bundled emergency guides (`app/src/main/assets/emergency/guides.json`) quote US federal government pages (CDC, NIOSH, NIH/NLM, NIH/NIAMS, NWS/NOAA, NPS, FEMA and the U.S. Fire Administration), which are public-domain works of the US government, and four English Wikipedia articles at pinned revisions under CC BY-SA 4.0 (credit: Wikipedia contributors; each guide records its URL, revision and retrieval date). Sources, checksums and the build procedure are in `DATASETS.md` and `tools/build_emergency_data.py`.
