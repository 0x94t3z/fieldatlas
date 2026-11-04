# Reader benchmark

Can a small model answer a question from the passages it is given, cite them, and say plainly when they do not
answer it? This set measures that for the passages Field Atlas actually finds on a phone.

`dev.jsonl` is public, for development. A sealed test set built the same way is kept private and is used only for
judging; its file hash is published with the results.

## How it was built

1. **Questions.** 60 questions from earlier Field Atlas field tests in this folder, mixed across facts,
   explanations, comparisons, travel, longer multi-part questions, Ethereum, ageing research, everyday health, and
   questions no saved source can answer (prices today, private or future facts).
2. **Passages.** Each question was run through the app's own search on a desktop, with every current collection
   installed (Encyclopedia, Travel guides, Places, Biology, Ethereum, OpenStreetMap essentials). The passages are
   the sources the app chose for that question, in its order, at most 8. Nothing was hand-picked: some are off topic,
   and the model has to ignore those.
3. **References.** Each reference answer was written from the passages only, never from outside knowledge.
   `answerable` says whether the passages answer the question fully (`yes`), in part (`partial`) or not at all
   (`no`). `keyFacts` lists the facts a correct answer must contain, each as a list of accepted wordings, and `cites`
   the passages that hold them.

## Format

One JSON object per line:

```json
{"id": "dev-01", "category": "fact", "question": "...",
 "passages": [{"n": 1, "title": "Colosseum — Overview", "text": "...", "chunkId": "...", "source": "https://..."}],
 "answerable": "yes", "reference": "... [S1]", "keyFacts": [["80 AD", "AD 80"], ["50,000"]], "cites": [1]}
```

## Running and scoring

Serve the model with any OpenAI-compatible endpoint, for example llama.cpp's `llama-server -m model.gguf --port 8080`,
then:

```sh
python3 tools/evaluation/reader_bench.py run   --data docs/fieldtest/reader/dev.jsonl --out preds.jsonl
python3 tools/evaluation/reader_bench.py score --data docs/fieldtest/reader/dev.jsonl --pred preds.jsonl
```

`run` sends the same instructions to every model (see `INSTRUCTIONS` in the script, or
`reader_bench.py prompt --data … --id dev-01`). A different prompt is fine for your own experiments, but report
scores with this one.

An answer counts as **correct** when:

- for `yes` and `partial`: it contains every key fact, cites at least one passage as `[S#]`, cites no passage that
  does not exist, and states no number that appears in neither the passages nor the question; a `yes` answer must
  also not claim the sources are silent;
- for `no`: it says the sources do not answer, and states no such number.

The score also reports key-fact recall, gaps admitted, false refusals, answers with numbers from nowhere, median
length and median time per answer. It is strict on purpose and only a guide: final judging also reads every
answer blind.

## Baseline

The model Field Atlas ships today, Qwen3.5 2B (Q4_K_M, the app's pinned file), run with the commands above through
llama.cpp on a Mac, with thinking off and temperature 0. Its answers are in
[`baseline-qwen3.5-2b.jsonl`](baseline-qwen3.5-2b.jsonl).

| Measure | Qwen3.5 2B |
|---|---|
| Correct | 36.7% |
| Key facts found (answerable items) | 76.2% |
| Gaps admitted (`no` items) | 94.4% |
| False refusals (`yes` items) | 38.1% |
| Answers with a number from nowhere | 1.7% |

Most of its misses state the right facts but cite no passage.

## Licences

Passages keep the `source` link of the page or paper they come from. Encyclopedia and travel-guide text is from
Wikipedia and Wikivoyage under CC BY-SA 4.0; ageing-research passages are from PubMed Central papers under CC BY or
CC0; Ethereum passages are from EIPs (CC0) and the Ethereum docs; place listings are © OpenStreetMap contributors
under the ODbL. Details are in [DATASETS.md](../../../DATASETS.md). The questions and references are released
with this repository.
