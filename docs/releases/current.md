# Field Atlas 1.4.2

Field Atlas answers questions on your Android phone without an internet connection. While you are
online, download the answer model and the collections you want. After that, search and answers run
on the phone, with no account and nothing sent anywhere.

## What's new

- **Follow-up questions.** Ask under an answer, by typing, speaking or adding a file. A follow-up
  is read together with the question before it ("what about Peru?" after a question about tipping
  in Japan is searched as tipping in Peru), the line under it shows how it was read, and Edit
  changes that. The model also sees the earlier questions and answers. On 30 follow-ups graded
  blind, answers went from 0.45 to 2.25 of 10.
- **Topics in History.** A question and its follow-ups stay in one card. Tap it, or its Continue
  strip, to pick the topic up where it stopped. History now keeps every answer until you delete it
  (1.4.1 kept the newest 100) and warns when the phone is short of storage.
- **Answers keep going off the screen.** Back or New topic while an answer is being written does
  not stop it. The Research home shows it as "Still answering", the next question waits its turn,
  and a note says when it is ready. An answer you watched finish is not announced again.
- **Research on the answer page.** The page opens as soon as you start. The dino walks from
  Search to Read to Write while the answer forms in place, Stop sits in the question box, and the
  passages found appear as they come in.
- **Read aloud.** Answers and emergency guides can be read aloud with the phone's voice, or with a
  natural voice (Supertonic 3, about 146 MB) offered as an optional third step in setup. Both work
  with no signal.
- **More emergencies found.** An emergency told as a statement ("tornado siren going off and we're
  in a motel room") now gets its guide. On 45 new emergencies the right guide was shown for 14,
  against 9 in 1.4.1, with no unsafe guide. Two guides are new: a chemical or object in the eye,
  and someone unconscious but breathing, who is now put on their side (the recovery position)
  instead of left lying down or sent to CPR, which stays for anyone not breathing.
- **Word problems that name places** ("leaves Tokyo at 17:05 … UTC+9") are worked from their own
  figures.
- **Better passages for the model.** A place typed in lowercase counts as a place, a narrower
  article that search found ("Life on Titan") replaces a generic one ("Life"), and no question
  reaches the model without a passage. On 80 questions written for these cases, the 30 answers that
  changed went from 1.9 to 3.9 of 10.
- **Qwen3.5 4B on phones with 8 GB of memory.** Setup offers it first there, and Qwen3.5 2B
  everywhere else; downloading the 4B on a phone that used the 2B switches to it. On 120 new
  questions the 4B made half the factual errors of the 2B (0.30 against 0.62 per answer).
- **Sources numbered as the answer cites them.** The first source an answer cites is 1, so a
  cited "2" no longer sits above an uncited "1". Each passage has its own row with its opening words.
- **Library and setup.** Discover lists each collection and model in two short lines, with an
  About sheet for each, and a green bar above the tabs shows what is downloading. Importing a
  collection from a file counts only installed collections against the 50 GB limit, so a 24 GB
  Encyclopedia file is no longer refused. Download sources include the voice.
- **Fixes:** places named without their accents are found, Ethereum standards stay out of
  questions that are not about crypto, an answer the model wrote twice is shown once, and the
  "Answer ready" note follows the light theme.

## How good is it?

- **Against web-and-AI answers:** on 120 new questions (two sets written after the changes and
  graded blind twice), 1.4.2 scores **36.9%** of the web-and-AI answers with Qwen3.5 2B and
  **38.2%** with Qwen3.5 4B. That is below the bounty's bar
  ([details](../evaluation.md#142-release-candidate-on-fresh-questions-october-17-18)).
- **Single questions are where 1.4.1 left them.** 1.4.1 scored 29% on an earlier, harder set. On
  the same 60 questions, 1.4.1 scores 37.3% and 1.4.2 36.3%. The gains above are in follow-ups,
  emergencies, word problems and the 4B.
- **Strongest:** quick facts (55%) and emergencies (54%). **Weakest:** places in a named city
  (14%), cryptography (22%), explanations and synthesis (23%).

Every question, answer and grade is published in
[docs/fieldtest/results-1.4.2](../fieldtest/results-1.4.2/).

## Good to know

- **Answers that use the model take about 2 to 6 minutes** on a 6 GB phone with Qwen3.5 2B; most
  of that is the model reading the passages and checking its answer. Answers built from saved data
  (places, guides, phrases, calculations) take seconds. The 4B has not been timed on an 8 GB phone
  yet; on the 6 GB Redmi 13C one answer took 8.5 minutes against 2.3 for the 2B, which is why it
  is not offered there.
- **Answers can be wrong.** Open the cited passages to check them. Nothing here is medical advice.
  The emergency guides are information, not training, and many were written for the United States.
- **Map data and opening hours can be out of date.**
- **Storage.** The 2B model and every collection take about 30.9 GB, or 32.5 GB with the 4B
  instead; the natural voice adds 145 MB. Installing the Encyclopedia last needs room for its download and installed
  copy at once: about 41.5 GB at the peak, inside the 50 GB budget.
- **Within the bounty's limits.** On an Android emulator with 12 GB of memory, everything the app
  offers (both models, all seven collections and the voice) takes 34.2 GB of the 50 GB allowed.
  Answering in airplane mode, the app used at most 3.3 GB of memory with the 2B and 6.5 GB with the
  4B, and all 40 test answers finished offline, including 6 research questions with a follow-up
  under each, per model
  ([details](../evaluation.md#142-at-the-bountys-limits-on-an-android-emulator-october-18)).
- **Tested on** a Redmi 13C (6 GB, Android 13) and the emulator above. Not yet tested on a Pixel,
  on GrapheneOS or on a real 8 GB phone.

## Install

1. Download `fieldatlas.apk` (and `fieldatlas.apk.sha256` if you want to check it) below.
   Requires Android 13 or later on an arm64 phone.
2. Open the APK and allow the installation. It installs over 1.4.1 and keeps your data.
3. In setup, download the model it offers (Qwen3.5 2B, 1.4 GB, or Qwen3.5 4B, 3.0 GB, on an 8 GB
   phone), and later any collections you want and, if you like, the voice.
4. Turn on airplane mode and start asking.

A phone running a build signed with a different (test) key cannot be updated with this APK.
Uninstalling removes saved answers, so keep that build if you need its data.

## More detail

- [Every change since 1.4.1](https://github.com/0x94t3z/fieldatlas/compare/v1.4.1...v1.4.2)
- [How follow-ups, emergencies and word problems were measured](../evaluation.md#142-follow-up-questions-emergencies-told-as-statements-word-problems-october-15)
- [Collections: sources, sizes, checksums and licences](../../DATASETS.md#collections-the-app-offers)
- [Install guide](../installation.md)
- Notes for earlier versions: [1.4.1](field-atlas-1.4.1.md), [1.4.0](field-atlas-1.4.0.md)

App version 1.4.2 (21).
