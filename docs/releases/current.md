# Field Atlas 1.4.1

Field Atlas answers questions on your Android phone without an internet connection. While you are
online, download the answer model and the collections you want. After that, search and answers run
on the phone, with no account and nothing sent anywhere.

## What's new

- **Leviathan search in every collection.** Travel guides, Travel places, Vegan places, Essentials
  and Cryptography & Ethereum now have the same built-in search engine as the Encyclopedia and
  Biology. On a Redmi 13C it was faster than the old index in all six collections timed: about
  1 s instead of 3.2 s in Essentials (3 million places) and 0.2 s instead of 1.4 s in Travel guides.
- **Places are searched one city at a time.** A question that names a city, like "vegan pastries in
  Lisbon", searches only that city's listings, in under a quarter of a second even in Essentials.
- **Better "best vegan restaurants near me".** Among places that are equally good by every signal
  the map has, the nearer one now comes first: a fully vegan place 110 m away is no longer listed
  under one 4.4 km away. Asking for pastries, not only bakeries, finds the bakeries.
- **New collection versions.** The five collections above download as their `-leviathan` versions.
  Downloads are smaller and the installed size is larger:

  | Collection | Download | Installed |
  | --- | ---: | ---: |
  | Essentials | 986 MB | 3.62 GB |
  | Travel guides | 290 MB | 660 MB |
  | Travel places | 215 MB | 650 MB |
  | Cryptography & Ethereum | 59 MB | 92 MB |
  | Vegan places | 24 MB | 77 MB |

  A version you already have stays installed and keeps working until you delete it in Library.
- **Clearer progress.** "Write the answer" now shows how much the model has written and says when it
  checks the answer against your sources, instead of sitting at "100% read". The progress bars have
  no stray dot at the end.
- **Readable formulas.** Chemistry and maths the model writes in LaTeX show as text: CO₂, H₂O, 3 × 10⁸.
- **Online or Offline.** The home screen shows whether the phone is online; the dinosaur walks when it is.
- **One answer model.** Qwen3.5 2B is the only download. Version 1.4.0 also offered Qwen3.5 4B on
  8 GB phones; a phone that has it keeps it. Google's Gemma 4 E2B was tried and not offered: asked 20
  new questions in the app on a phone and graded blind, it scored 3.75 of 10 against 4.65 for
  Qwen3.5 2B, often answering that the sources did not cover a question Qwen explained.
- **Fixes from two review rounds** of 1.4.0: calculators that answer right or not at all (fuel,
  money and time zones), the emergency number for where you are, place and travel answers
  only from what the sources say, storage freed after interrupted installs, saved answers and
  positions kept on the phone, and Android-safe patterns for every text rule.

## How good is it?

- **Against web-and-AI answers:** on 60 new questions written after the release and graded blind
  twice, 1.4.1 scores **29%** of the web-and-AI answers with the default model, below the bounty's
  bar and below 1.4.0's 34% on its own set of questions. Strongest on vegan restaurants in the city
  you are in (83%) and quick facts (58%); weakest on emergencies (10%), synthesis (11%),
  comparisons (13%) and reasoning with numbers (19%)
  ([details](../evaluation.md#141-release-build-on-fresh-questions-october-15)).

Also measured for 1.4.1, with every answer and grade published ([details](../evaluation.md#141-on-the-phone-answers-speed-tokens-and-failures-october-14-15)):

- **On the phone:** 20 new questions asked in the app on a Redmi 13C scored 4.65 of 10 on average
  with Qwen3.5 2B. Word problems and emergencies scored highest (6 to 10). Travel know-how, named
  places and comparisons scored lowest (0 to 3).
- **Vitalik's test:** "best vegan restaurants in the city I am currently in" listed real vegan
  places with addresses and hours in all 8 cities tried, and in the same 8 cities when named.
- **Search:** the right article reached the model as often with Leviathan in every collection as
  before (106 of 135 development questions).
- **Speed:** an answer the model writes takes about 3,700 prompt tokens over three model calls; on
  the phone that is 2 to 6 minutes, mostly the model reading and checking its answer.

## Good to know

- **Answers that use the model take about 2 to 6 minutes, typically 4,** on a 6 GB phone; most of
  that is the model reading the passages and checking its answer. Answers built from saved data (places, guides, phrases, calculations)
  take seconds.
- **Answers can be wrong.** Open the cited passages to check them. Nothing here is medical advice.
  The emergency guides are information, not training, and many were written for the United States.
- **Map data and opening hours can be out of date.**
- **Storage.** The model and every collection take about 30.9 GB. Installing the Encyclopedia last
  needs room for its download and installed copy at once: about 41.5 GB at the peak, inside the
  50 GB budget.
- **Tested phones.** Redmi 13C (6 GB, Android 13) and Infinix X6840 (4 GB, Android 16). Not yet
  tested on a Pixel, on GrapheneOS or on an 8 GB phone.

## Install

1. Download `fieldatlas.apk` (and `fieldatlas.apk.sha256` if you want to check it) below.
   Requires Android 13 or later on an arm64 phone.
2. Open the APK and allow the installation. It installs over 1.4.0 and keeps your data.
3. In setup or Library, download the Qwen3.5 2B model (1.4 GB) and any collections you want.
4. Turn on airplane mode and start asking.

A phone running a build signed with a different (test) key cannot be updated with this APK.
Uninstalling removes saved answers, so keep that build if you need its data.

## More detail

- [Every change since 1.4.0](https://github.com/0x94t3z/fieldatlas/compare/v1.4.0...v1.4.1)
- [Collections: sources, sizes, checksums and licences](../../DATASETS.md#collections-the-app-offers)
- [Leviathan timings on a phone](../../native/leviathan/README.md)
- [Install guide](../installation.md)
- Notes for the previous version: [1.4.0](field-atlas-1.4.0.md)

App version 1.4.1 (20).
