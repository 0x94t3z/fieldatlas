# fapack toolkit

## Modular libraries and retrieval experiments

`build_modular_library.py` builds independently importable, pre-indexed modules
from a checksum-pinned JSONL corpus in the packtool document format. It preserves
source identifiers and attribution, keeps whole documents together, and refuses
to overwrite existing outputs. The shared streaming builder uses a disk-backed
document index instead of retaining every identifier in RAM.

```sh
python3 tools/build_modular_library.py corpus.jsonl \
  --input-sha256 ACTUAL_64_CHARACTER_SHA256 \
  --out build/new-library --id general-reference --version 1.0.0 \
  --title "General reference" --license CC-BY-SA-4.0 \
  --source-url https://example.org/pinned-source-snapshot \
  --module-text-bytes 250000000
```

Use the corpus's actual licence and source snapshot, not the example values.
The output report records archive and installed payload sizes, hashes, builder
sources and retrieval mode. It does not add offers to the app catalog or claim a
20 GB library was built. Archives are capped at 1.5 decimal GB each and installed
library payloads at 35 decimal GB; reduce the text target if a module exceeds its
archive limit. The 50 GB app limit still includes models, retained editions,
staging, indexes, app files and caches. Verify hosted artifacts, Android import
peaks, citation access and useful retrieval before offering a configuration.
Article-section inputs remain article-section collections, not ordinary-search
coverage. Synthetic size tests are not knowledge modules.
Keep all supplied sections of an article in one module: the builder groups
`enw-PAGEID-SECTION` records and requires the original `0000` lead. It rejects
mixed article-section/ordinary corpora to prevent runtime misclassification.
This preserves the input's article boundaries, not completeness of the source
encyclopedia: any upstream passage selection still needs its own evaluation.

`benchmark_retrieval.py` compares the actual app SQL, compact candidate previews
and an explicitly supplied upstream Leviathan CLI over identical saved records:

```sh
python3 tools/benchmark_retrieval.py --database content.sqlite \
  --cases fixed-cases.json --work-dir build/new-retrieval-experiment \
  --leviathan /path/to/verified/leviathan --records 5000 --repetitions 5
```

Cases are a JSON array with `id`, `query` and `goldChunkIds`; an empty gold list
explicitly labels missing evidence. Use a fresh work directory. This experiment
is offline and read-only with respect to the source database. It reports its
subset, engine versions, timings, index sizes, source references and limits.
Desktop CLI/process-start timings are not Android performance, and character
counts are not Qwen token counts. Compact previews are selection aids only:
retrieve the original full passage before reasoning about its qualifications.
The experiment never enables a different production retriever.

`plan_library_storage.py` verifies pinned local `.fapack` archives and every
declared payload, then calculates sequential download/import and retained-version
storage peaks without extraction, installation or networking:

```sh
python3 tools/plan_library_storage.py /path/to/storage-plan.json
```

The JSON input has `currentBytes`, `freeBytes`, a positive `growthReserveBytes`,
and `operations`: each contains `archive`, `sha256`, `mode` (`download` for
knowledge packs or `local-import` for any pack), and optional `partialBytes`.
Archive paths are relative to the JSON file. Supply the complete current app
footprint including APK/native libraries, old and disabled packs, app data and
staged partials; partial bytes must already be included in `currentBytes`.
Downloaded packs reserve archive size for extraction, matching the app's
conservative admission check. Completed ZIPs remain counted through extraction
and are released only after registration. Old editions are never subtracted.
The plan reserves metadata per operation, growth room and device free-space
headroom. Exit status is 1 for a budget/free-space failure and 2 for malformed
inputs or failed integrity checks. Raw GGUF model downloads are not simulated;
model `.fapack` local imports are. Model and archive bytes cannot inflate the
reported selected knowledge payload. This is a planning check, not evidence of
Android runtime peaks, complete footprint measurements, useful coverage or
release readiness. Keep owner-specific plans and measurement reports private.

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
| `build_model_packs.py` | `models/compact-*.example.json` registry + GGUFs | MODEL / AUDIO packs (Qwen3.5 recommended; MiniCPM5 and Vosk optional) |
| `fapack_convert.py` | any JSONL document spool | any KNOWLEDGE pack (generic converter, usage documented in its docstring) |
| `build_vector_pack.py` | fapack + embeddings TSV + encoder GGUF | vector-capable KNOWLEDGE pack (int8 `chunk_vectors` + embedded query encoder) |
| `build_wikivoyage_eat_pack.py` | English Wikivoyage pages-articles XML dump | dated, individually searchable restaurant listings |
| `build_wikivoyage_places_pack.py` | the same pinned English Wikivoyage dump | keyword-only See, Do, Eat, Drink, Sleep, and Buy listings |
| `build_osm_vegan_pack.py` | Overpass API responses (archived with SHA-256) | worldwide OpenStreetMap places tagged fully vegan or with vegan options (ODbL) |
| `build_osm_essentials_pack.py` | Overpass API responses (archived with SHA-256) | worldwide pharmacies, hospitals, clinics, ATMs, currency exchange, toilets, drinking water, police, embassies, stations, supermarkets and hostels, with a category per place for near-me lookups (ODbL) |
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

# offline travel coverage from an extracted Wikivoyage corpus. This uses the same verified
# knowledge-pack format; restaurant/POI claims remain limited to the dump's publication date:
python3 tools/content2fapack.py wikivoyage --out ../fapacks --keep-jsonl

# For city-specific restaurant lookups, build one document per Wikivoyage Eat listing.
# Download the source on a computer, then transfer the resulting .fapack to the phone.
curl -L https://dumps.wikimedia.org/enwikivoyage/20260901/enwikivoyage-20260901-pages-articles.xml.bz2 \
  -o enwikivoyage-20260901-pages-articles.xml.bz2
python3 tools/build_wikivoyage_eat_pack.py \
  --dump enwikivoyage-20260901-pages-articles.xml.bz2 --out ../fapacks --version 2026.09.1 \
  --source-url https://dumps.wikimedia.org/enwikivoyage/20260901/enwikivoyage-20260901-pages-articles.xml.bz2 \
  --expected-sha256 c6cebf6b109c31698e736858fd1d8dec1c41d87437aa4c4cafd7b0df88777773

# For wider travel coverage, build the keyword-only general places pack from the same
# verified dump. No embedding run or vector encoder is needed. The full artifact has
# not yet been measured on a physical device.
python3 tools/build_wikivoyage_places_pack.py \
  --dump enwikivoyage-20260901-pages-articles.xml.bz2 --out ../fapacks --version 2026.09.1 \
  --source-url https://dumps.wikimedia.org/enwikivoyage/20260901/enwikivoyage-20260901-pages-articles.xml.bz2 \
  --expected-sha256 c6cebf6b109c31698e736858fd1d8dec1c41d87437aa4c4cafd7b0df88777773

# Vegan places from OpenStreetMap. fetch needs network and archives every raw response with
# its SHA-256 in lock.json; build is offline and byte-reproducible from that cache.
python3 tools/build_osm_vegan_pack.py fetch --cache-dir build/osm-vegan/cache
python3 tools/build_osm_vegan_pack.py build --cache-dir build/osm-vegan/cache \
  --out build/osm-vegan/pack --version 2026.10.02
# Essentials reuse the vegan cache's city and town lists for destinations.
python3 tools/build_osm_essentials_pack.py fetch --cache-dir build/osm-essentials/cache
python3 tools/build_osm_essentials_pack.py build --cache-dir build/osm-essentials/cache \
  --settlements-cache build/osm-vegan/cache --out build/osm-essentials/pack --version 2026.10.02

# Encyclopedia and the older flat travel guides from Wikimedia CirrusSearch dumps: spool streams
# the dump once (network), build is offline and byte-reproducible from the spool. Wikimedia keeps
# these dumps for a few weeks; the knowledge releases carry the spools.
python3 tools/build_wikipedia_pack.py spool \
  --source https://dumps.wikimedia.org/other/cirrussearch/20251229/simplewiki-20251229-cirrussearch-content.json.gz \
  --out build/wikipedia/simplewiki.spool.jsonl.gz
python3 tools/build_wikipedia_pack.py build --spool build/wikipedia/simplewiki.spool.jsonl.gz \
  --out build/wikipedia/pack --version 2025.12.29

# Travel guides by section ("Japan — Buy > Cash") from the pinned pages-articles dump above.
python3 tools/build_wikivoyage_guides_pack.py --dump enwikivoyage-20260901-pages-articles.xml.bz2 \
  --out build/wikivoyage-guides-sections --version 2026.09.01

# Cryptography & Ethereum: fetch pins every source (EIP/ERC/spec commits, NIST PDFs, Wikipedia
# revisions) in a lock; build checks the lock, then embed_pack.py and build_vector_pack.py add vectors.
python3 tools/build_crypto_pack.py fetch
python3 tools/build_crypto_pack.py build --version 2026.10.05

# English Wikipedia's vital articles by section, read through the MediaWiki API at most 3 requests
# a second; fetch resumes after an interruption, build is offline and byte-reproducible.
python3 tools/build_wikipedia_sections_pack.py fetch --spool build/wikipedia-en/vital.spool.jsonl --workers 4
python3 tools/build_wikipedia_sections_pack.py build --spool build/wikipedia-en/vital.spool.jsonl \
  --out build/wikipedia-en/full --version 2026.10.06

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
