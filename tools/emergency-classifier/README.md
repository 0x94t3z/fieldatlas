# Emergency guide classifier

`app/src/main/assets/emergency/classifier.bin` reads a first-aid message the app's keyword rules found no guide for,
and names the guide that fits, or none. The app uses its answer only when it is at least 0.3 likely, never for a past
event, never for the drinking-water or signalling guides, and opens the pregnancy-bleeding guide only when the message
mentions blood (`ResearchOrchestrator.classifiedGuide`).

## Rebuild

```bash
python3 tools/emergency-classifier/export.py app/src/main/assets/emergency/classifier.bin \
    app/src/test/resources/emergency-classifier-parity.json
```

Training is deterministic, so this reproduces the shipped file byte for byte (SHA-256 in `DATASETS.md`), and
`EmergencyClassifierTest` checks that the Kotlin port gives the script's answers on the parity vectors.

## Data

- `data/train/gen-*.jsonl`: messages written by AI agents for this purpose on 2 November 2026: 40 for each of the 52
  guides, told in everyday words with typos, slang and some mixed languages, and 800 look-alikes that need no guide
  (figurative uses, past events, study questions, travel and pregnancy logistics, normal labour, phone signal).
- `data/labelled.jsonl`: earlier labelled test sets (16-18, two labellers each; 58-60), also written by AI agents, used
  for development and then added to the training data.
- `data/held61/`: the sealed check. 160 fresh messages by a separate agent that saw no training data, labelled by two
  more agents independently (they agreed on all 160). The questions and the predictions of the matcher and the model
  were fixed and hashed before the labels existed (`SEALED.sha256`, `PREDICTIONS.sha256`).

## Result on the sealed set (as the app routes it, for Qwen3.5 4B)

| | Right | Unsafe | False alarms | Emergencies left with no guide |
| --- | --- | --- | --- | --- |
| Keyword rules (1.4.4) | 116 / 160 | 0 | 6 / 40 | 33 / 120 |
| With the classifier | 132 / 160 | 0 | 7 / 40 | 16 / 120 |

All messages, training and test alike, were written by AI agents, not taken from real users; real messages may read
differently.
