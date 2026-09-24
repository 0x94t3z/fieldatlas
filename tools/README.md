# fapack toolkit

Everything the app installs (`.fapack` files) is regenerated from source by the
scripts in this directory. Packs themselves are not committed — only these tools
and the pinned model/registry metadata are.

All scripts are stdlib-only (numpy speeds `build_vector_pack.py` up when present)
and deterministic: the same inputs always produce byte-identical packs.

| tool | input | output |
| --- | --- | --- |
| `stream_pack.py` | JSONL spool | the byte-format builder shared by everything below (same output as `packtool.build_pack`, constant memory) |
| `content2fapack.py` | raw corpora under `world_knowledge/content` | KNOWLEDGE packs (wikipedia, biology, crypto, …) |
| `wiki_mini_build.py` | Wikipedia dump (or an extracted text tree) + vital snapshot | the wikipedia-mini KNOWLEDGE pack, end to end |
| `build_model_packs.py` | `models/compact-*.example.json` registry + GGUFs | MODEL / AUDIO packs (Qwen3, Qwen3.5, MiniCPM5, Vosk) |
| `fapack_convert.py` | any JSONL document spool | any KNOWLEDGE pack (generic converter, usage documented in its docstring) |
| `build_vector_pack.py` | fapack + embeddings TSV + encoder GGUF | vector-capable KNOWLEDGE pack (int8 `chunk_vectors` + embedded query encoder) |
| `e2e_battery.py` | fapacks + llama-server | full-pipeline answer battery (planner → retrieval → prompt → answer) |

Paths follow the working-tree convention: `REPO` = the fieldatlas repository (holds
`packtool/`, `models/`), `WORK = REPO.parent` = the data working directory holding
`fapacks/`, `model-cache/`, `content2/`. Every default can be overridden by CLI flags.

## Typical flows

```bash
# model + audio packs (downloads are sha256-pinned; --url ID=file://... to go offline):
python3 tools/build_model_packs.py --pack all

# wikipedia mini from scratch (clones the Wikimedia dump, runs WikiExtractor, selects
# vital + filler articles under a pinned text budget):
python3 tools/wiki_mini_build.py --fetch-vital

# embeddings: run the corpus embedder, then quantize + inject + restamp:
python3 tools/build_vector_pack.py \
    --base-fapack ../fapacks/world-knowledge-biology/world-knowledge-biology-1.1.0.fapack \
    --vectors bio_bge_full.tsv --encoder bge-small-q8.gguf \
    --encoder-url https://huggingface.co/QuantFactory/bge-small-en-v1.5-GGUF \
    --version 1.2.0
```

`wiki_vital_titles.json` is the pinned snapshot of Wikipedia's Level-5 vital-article
list (~50k titles) used by `wiki_mini_build.py`; `--fetch-vital` refreshes it from
the live API via the Level-5 index and its topic subpages.

## The on-disk format (what the phone validates)

A `.fapack` is a zip with `manifest.json` (+ artifacts). KNOWLEDGE packs carry
`content.sqlite` whose `chunks_fts` FTS5 table (`porter unicode61` tokenizer for
prose) is what the app searches; vector packs additionally carry a `chunk_vectors`
table (`rowid` aligned with `chunks_fts`, blob = float32 scale + 384 int8) and an
`encoder.gguf`, described by the manifest's optional `embedding` block. The app
re-hashes every artifact against the manifest before installing, so the builders'
hash bookkeeping must match the final bytes exactly — `fapack_convert.verify_pack`
replays those checks after every build.
