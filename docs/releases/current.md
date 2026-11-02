# Field Atlas 1.4.7

A speed and safety update. Field Atlas answers questions on your Android phone without an internet connection;
1.4.7 shows the saved article that answers a question within about a second, recommends the smaller and faster
model on most phones, and opens the right first-aid guide for four more kinds of emergency.

## What's new

- **The answer comes up first.** Before the full search runs, the article the question names (or the year's
  event it asks about) is looked up by its title and shown at once, with the sentence that answers marked:
  "how tall is mount everest" shows "It is about 8,848 metres (29,029 feet) high" while the model is still to
  write. The model's own answer follows as before.
- **Qwen3.5 2B is recommended below 12 GB of memory.** The 4B is recommended from 12 GB; on smaller phones it
  stays in Library, marked slow. Contributors timed the 4B at up to 5.5 minutes an answer on a 12 GB phone; a
  model you already use is not changed.
- **Four more emergencies open their guide.**
  - An object with no air going in ("baby has a coin stuck she cant cry or breathe lips going blue") opens choking.
  - A fuel burning in a closed space with someone unwell ("running the generator in the garage… everyone has a
    headache") opens carbon monoxide.
  - A household chemical in the eye opens eye injury; a corrosive one burning the skin opens burns.

  Someone who can still breathe and talk, a question asked before anything has happened, and a scene in a story
  keep their own routes.

## How much better?

Our own measurements on an Android emulator, not an independent score:

| | 1.4.6 | 1.4.7 |
|---|---|---|
| Something to read after asking a fact question | after the full search, a few seconds | about 1 second, with the answer marked |
| Emergency messages routed right, on 60 messages written for testing | 35 of 60 | 43 of 60 |
| Of those, a guide that would be wrong to follow | 1 | 1 |
| Earlier test messages (2,438) whose guide changed | | 4, all from no guide to the right one |

The 60 messages had been used while building the earlier 1.4.6 fix, so they are not a fresh test.

## Good to know

- **Meaning-based search is coming as collections, not in this app update.** Indexes for the Encyclopedia,
  Wikivoyage guides and Biology will appear in Library once they are published; 1.4.x already reads them.
- **Tested on** a 12 GB Android emulator. Not yet on a real phone.
- **Answers can be wrong.** Nothing here is medical advice. In an emergency, call your local emergency number.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.6 and keeps your data.

## More detail

- [1.4.6 release notes](field-atlas-1.4.6.md)
- [Every change since 1.4.6](https://github.com/0x94t3z/fieldatlas/compare/v1.4.6...v1.4.7)
- [Install guide](../installation.md)
