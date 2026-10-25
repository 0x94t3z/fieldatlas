# Field Atlas 1.4.2

Field Atlas answers questions on your Android phone without an internet connection. Download a model
and the collections you want while you are online. After that, search and answers run on the phone,
with no account and nothing sent anywhere.

## What's new

- **Follow-up questions.** Ask under an answer by typing, speaking or adding a file. The follow-up is
  read together with the question before it.
- **Topics in History.** A question and its follow-ups stay together, and you can pick a topic up
  where you left it.
- **Answers keep going in the background.** Leave the page and the answer still finishes; the Research
  home tells you when it is ready.
- **Read aloud** with the phone's voice or the optional natural voice, Supertonic 3 (146 MB). Both work
  with no signal.
- **Better emergency help.** 51 first-aid guides, 9 of them new. Emergencies described in everyday
  words now find their guide, and life-threatening ones open with "Call your local emergency number now".
- **A model for every phone.** Gemma 4 26B on 12 GB phones, Qwen3.5 4B from 8 GB, Qwen3.5 2B below.
  Setup recommends one for your phone.
- **Answers from the model's own knowledge** with the 4B and Gemma, with the passages found listed
  underneath to check. For single facts the 4B reads the Encyclopedia first and cites it.
- **Places as cards.** "A hospital near the Colosseum" is measured from the Colosseum, and a list of
  hospitals starts with the nearest emergency department.
- **A larger Biology & longevity collection:** 48,286 open-access papers, a 1.08 GB download.
- **Your own files.** Answers about an attached file are refused far less often, and the files you
  send stay with the question and its follow-ups.
- **Import any model file** (`.gguf`), as well as collections (`.fapack`).
- **Many fixes** to emergency matching, places, downloads and screen-reader labels.

## How good is it?

On 160 new questions graded blind against web-researched answers, Gemma 4 26B gave a good answer
(7 or more out of 10) to **115**, Qwen3.5 4B to **27** and Qwen3.5 2B to **35**, with 0.38, 2.19 and
0.83 problems noted per answer. These are our own AI-graded measurements, not an independent score;
every question and grade is published.

## Good to know

- **Speed.** On a 6 GB Redmi 13C, the 2B takes about 1½ to 6 minutes an answer and the 4B 3 to 6
  minutes. Emergency guides, places and calculations take seconds.
- **Answers can be wrong.** Open the cited passages to check them. Nothing here is medical advice.
- **Storage.** The 2B and every collection take about 34 GB, inside the 50 GB limit.
- **Tested on** a Redmi 13C (Android 13) and Android emulators. Not yet on a Pixel, GrapheneOS, or a
  real 8 GB or 12 GB phone.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.1 and keeps your data.
3. In setup, download the recommended model, then any collections you want.
4. Turn on airplane mode and start asking.

## More detail

- [Full release notes](field-atlas-1.4.2-details.md)
- [How 1.4.2 was tested](../testing-1.4.2.md)
- [Every change since 1.4.1](https://github.com/0x94t3z/fieldatlas/compare/v1.4.1...v1.4.2)
- [Collections: sources, sizes and licences](../../DATASETS.md#collections-the-app-offers)
- [Install guide](../installation.md)

App version 1.4.2 (23), updated 25 October: sent files stay with their question, and the follow-up
box stays above the keyboard.
