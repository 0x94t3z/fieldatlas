# Field Atlas 1.4.0

Field Atlas answers questions on your Android phone without an internet connection. While you are
online, download an answer model and the collections you want. After that, search and answers run
on the phone, with no account and nothing sent anywhere.

## What's new

- **One encyclopedia.** English and Simple English Wikipedia in a single download: 8.39 million
  passages, 10.64 GB to download and 24.35 GB on the phone. It replaces the two encyclopedia
  downloads of earlier versions.
- **Faster search on the phone.** The Encyclopedia and Biology are searched by a search engine
  built into the app (Leviathan).
- **Answers that show their sources.** When your collections cover a question, the answer cites
  the passages it used, and you can open each one. The app checks a cited answer before showing
  it. If the check fails, you still get an answer, labelled as not verified.
- **A new Biology & longevity collection.** A biology textbook, MedlinePlus health topics, ageing
  gene data and six research reviews, with every source credited. It is 8 MB and replaces the
  earlier 1.5 GB pack, whose sources could not be checked.
- **Simpler setup.** The model and every collection download inside the app and are checked
  before they install. You can point any download at your own server, or import a `.fapack` file.
- **Places.** "Best vegan restaurants in the city I'm in", "a pharmacy I can walk to from here"
  and "a museum near me" use the phone's location.
- **Emergencies.** 42 first-aid and safety guides and the emergency numbers of 242 countries are
  built into the app and work before any download.
- **Calculations.** Fuel cost, money at a given rate, time zones, water for a group, power-bank
  charges and more are worked out on the phone, without the model.
- **Optional larger model.** Phones with 8 GB of memory can download Qwen3.5 4B, which makes
  about half as many factual errors but answers more slowly.

## How good is it?

We tested it on 60 new questions written after this version was finished, and compared each
answer with one from a web search plus a frontier AI model. Graders scored every answer blind.

- With the default model, Field Atlas scores **34%** of the web-and-AI answers. The bounty asks
  for more than 50%, so it is **not there yet**. The optional 4B model scores 35%.
- It is strongest where it answers from saved data: places near you, vegan restaurants in your
  city, quick facts, emergency guides, phrases and calculations.
- It is weakest where the small model has to explain things itself: comparisons, cryptography and
  multi-step reasoning.

Every question, answer and grade is published in
[results-1.4.0](../fieldtest/results-1.4.0/). The [evaluation](../evaluation.md#140-release-build-on-fresh-questions-october-13)
explains the method.

## Good to know

- **Answers that use the model take 2 to 4 minutes** on a 6 GB phone. Answers built from saved
  data (places, guides, phrases, calculations) take seconds.
- **Answers can be wrong.** Open the cited passages to check them. Nothing here is medical advice.
  The emergency guides are information, not training, and many were written for the United States.
- **Map data and opening hours can be out of date.**
- **Storage.** The model and every collection take about 32 GB. Installing the Encyclopedia needs
  room for the download and the installed copy at the same time, about 43 GB at the peak, which
  fits the 50 GB budget.
- **Tested phones.** Redmi 13C (6 GB, Android 13) and Infinix X6840 (4 GB, Android 16). Not yet
  tested on a Pixel, on GrapheneOS or on an 8 GB phone.

## Install

1. Download `fieldatlas.apk` (and `fieldatlas.apk.sha256` if you want to check it) below.
   Requires Android 13 or later on an arm64 phone.
2. Open the APK and allow the installation. It installs over 1.3.0 and keeps your data.
3. In setup, download the Qwen3.5 2B model (1.4 GB) and any collections you want.
4. Turn on airplane mode and start asking.

A phone running a build signed with a different (test) key cannot be updated with this APK.
Uninstalling removes saved answers, so keep that build if you need its data.

## More detail

- [Full change notes](field-atlas-1.4.0-details.md)
- [Collections: sources, sizes, checksums and licences](../../DATASETS.md#collections-the-app-offers)
- [How it was tested](../verification.md#v140-release-build-october-13)
- [Install guide](../installation.md)
- Notes for the previous version: [1.3.0](field-atlas-1.3.0.md)

App version 1.4.0 (19).
