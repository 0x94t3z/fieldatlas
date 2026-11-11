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

# Urgency model

`app/src/main/assets/emergency/urgent.bin` reads whether a message is about someone who is hurt, ill or in danger now.
It has two labels, "urgent" and "no", and uses the guide classifier's features and file format.

The app uses it for one thing (`ResearchOrchestrator.readsUrgent`): when the keyword rules pass a message over and the
model is at least 0.7 sure, the answer opens with "If this is an emergency, call your local emergency number first".
The answer under that line is the one the app would have given anyway. The model changes no route, opens no guide and
replaces no answer, and a message the rules match is read by the rules alone. The line is not added to a question asked
to learn, a follow-up, a message of fewer than four words, or a question the app answers itself or reads as asking for a place (a
greeting, a phrase, a sum, the phone's position, places near you). Without the model the rules decide alone.

It was first built to do more: to send such a message to the emergency reply in place of its answer, and to drop a
rule match it scored very low. Three reviews before release found why not. It reads a message as a whole, so "My friend
Chad is hurt badly. What is the ambulance number?" scored 0.09; and it reads ordinary trouble as an emergency: "I lost
my passport while travelling in Bangkok, what do I do now" scored 0.85. Adding one line costs such a question nothing
but the line.

## Rebuild

```bash
python3 tools/emergency-classifier/export_urgent.py app/src/main/assets/emergency/urgent.bin \
    app/src/test/resources/emergency-urgent-parity.json
```

Training is deterministic (SHA-256 in `DATASETS.md`), and `UrgencyRoutingTest` checks that the Kotlin port gives the
script's numbers on the parity vectors.

## Data

`data/urgent/train.jsonl`, 6,951 messages, each with `y` and where it is `from`:

- `fieldtest/…`: 1,688 questions of the public field tests (`docs/fieldtest`), labelled by their set's category: the
  emergency categories and the routing sets' groups A and B are "urgent", everything else "no". Minor injuries happening
  now (the "calm" category) are left out, because either reading is acceptable for them.
- `guides/…`: the guide classifier's generated messages (a guide is "urgent", a look-alike "no") and the guide rows of
  its labelled sets.
- `generated/…`: 1,894 messages written by AI agents on 10 November 2026 that read no file: ordinary travel questions,
  ordinary knowledge questions, health and first-aid questions that are not emergencies, and emergencies well beyond the
  guides' topics.

The data has no ordinary travel trouble (a lost passport, a missed flight, a lockout), which is why the model scores it
high.

## Checks

Fresh check (`data/urgent/fresh-check.jsonl`, 170 messages by a separate agent that read no file, SHA-256 `8712e168…`),
each message answered by the app's own code with and without the model:

| | Urgent, answer opens with no call line | Not urgent, call line added |
| --- | --- | --- |
| Keyword rules alone | 18 / 80 | |
| With the model | 1 / 80 | 2 / 90 |

The first column is not a blind number: the keyword rules' past-event words were taken off the model after the first
run ("haven't been able to pee since last night" is happening now). The urgent message still without a line is read as
a place question (a wife with dementia missing from the hotel). The two calm messages given the line are a closed cut
("is it ok to swim in the sea?") and a fear of snakes before a hike.

Ordinary trouble, 38 messages written by a reviewer to break the model (lost passport, lockout, missed flight): 21 get
the line above their unchanged answer.

Across these sets and the training data, 7,276 messages, every answer with the model was either the same as without it
or the same with the line above it.

Cross-check on the field-test questions, each scored by a model that never saw a question from its own set: of 30
urgent questions the rules give no call line, the model adds it to 25, and it adds it to none of the 1,171 that are not urgent.

All messages, training and test alike, were written by AI agents or for the field tests, not taken from real users.
