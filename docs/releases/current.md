# Field Atlas 1.4.8

Search by meaning for every reading collection. Field Atlas answers questions on your Android phone without an
internet connection; 1.4.8 finds passages in the Encyclopedia, the travel guides and Biology by what a question
means, even when it uses other words, and shows the closest one within about a second.

## What's new

- **Search by meaning for the Encyclopedia, travel guides and Biology.** Three new downloads in Library, one for
  each collection: Encyclopedia (889 MB), Travel guides (77 MB) and Biology & longevity (134 MB). Each finds
  passages by meaning across the whole collection: all 8,386,572 Encyclopedia passages, 363,815 travel guide
  sections and 900,714 Biology passages. They hold no text of their own; each points into the collection already
  on the phone, so the Encyclopedia gains meaning search without a second copy of its 24 GB. Library offers each
  one once its collection is installed.
- **The first card uses meaning too.** The passage shown within about a second can now be the closest one in
  meaning: "is it ok to drink from the faucet in hanoi" shows the Drink section of the Hanoi guide, and "how do i get
  from the airport to bangkok by bus" shows Bangkok's Get in by bus section. The card stays while the answer is
  written instead of switching to the search's top passage.
- **Questions that ask for a judgement keep their subject.** In "is it ok to drink from the faucet in hanoi" or
  "is it rude to tip in japan", the words "ok" and "rude" are no longer looked up as articles of their own.

## How much better?

Our own measurements on an Android emulator, not an independent score:

| | Without meaning packs | With all three |
|---|---|---|
| Search time, median of 10 everyday questions | 2.3 s | 2.5 s |
| Slowest search of the 10 | 10.4 s | 8.0 s |
| Questions worded differently from the guide that found the right section (travel guides, 10 questions, on a Mac) | 6 of 10 | 10 of 10 |

## Good to know

- **The earlier "Encyclopedia: search by meaning"** (the opening of each Simple English article, 244 MB) is no
  longer offered: the new Encyclopedia download covers every passage. A phone that has it keeps it; it can be
  deleted in Library.
- **Tested on** a 12 GB Android emulator. Not yet on a real phone.
- **Answers can be wrong.** Nothing here is medical advice. In an emergency, call your local emergency number.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.7 and keeps your data.

## More detail

- [1.4.7 release notes](field-atlas-1.4.7.md)
- [Every change since 1.4.7](https://github.com/0x94t3z/fieldatlas/compare/v1.4.7...v1.4.8)
- [Install guide](../installation.md)
