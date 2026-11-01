# Field Atlas 1.4.4

A speed update. Field Atlas answers questions on your Android phone without an internet connection;
1.4.4 puts something useful on the screen within seconds, answers greetings at once, and stops an
answer that is stuck instead of leaving you waiting.

## What's new

- **Something to read straight away.** While an answer is researched, the best passage found in your
  collections so far appears on the page: its title, its opening sentences and where it is saved. It
  moves to the passage the answer is written from once that is chosen, and stays until the answer's
  own words arrive. First-aid questions and place lookups keep their guides and lists instead.
- **Greetings answered at once.** "hello", "thanks", "how are you" and "who are you" get an
  immediate reply, without searching the collections or waiting for the model. A message with a
  question in it ("hi, my friend is choking") still goes to its guide or its answer.
- **A stuck answer stops.** If the model reads nothing and writes nothing for 3 minutes, the answer
  stops with a message saying so, and the model stays ready for the next question. Stop now ends a
  long prompt straight away; before, the next question waited until the old prompt was read.
- **Clearer progress.** The page says "Checking related articles" while the app reads the articles
  around the best match, instead of a full search bar. An emergency guide shown while the model is
  busy with another answer no longer says the model is unavailable.

## How much faster?

On a 12 GB Android emulator, with the same ten questions as before:

| | 1.4.3 | 1.4.4 |
|---|---|---|
| First useful text on the page (model answers) | 9 to 56 s | 4 to 7 s (16 s for one rare subject) |
| "hello" | 13 to 31 s | 0.1 s |
| Complete answers | as before | as before |

The best passage appears sooner; the finished answer takes as long as it did, and is written the
same way. These are our own measurements on an emulator, which runs faster than a phone; on a phone
every figure is larger, the order the same.

## Good to know

- **Answers are unchanged.** The same models, prompts and collections; what changed is what you see
  while you wait.
- **Tested on** a 12 GB Android emulator. Not yet on a real phone.
- **Answers can be wrong.** Nothing here is medical advice. In an emergency, call your local emergency
  number.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.3 and keeps your data.

## More detail

- [1.4.3 release notes](field-atlas-1.4.3.md)
- [Every change since 1.4.3](https://github.com/0x94t3z/fieldatlas/compare/v1.4.3...v1.4.4)
- [Install guide](../installation.md)
