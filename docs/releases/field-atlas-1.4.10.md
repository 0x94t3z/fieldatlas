# Field Atlas 1.4.10

An emergency the app does not recognise now still opens with the call line. Field Atlas answers questions on your
Android phone without an internet connection; 1.4.10 is a small safety release and changes nothing else you see.

## What's new

- **The call line for emergencies the rules miss.** Field Atlas recognises an emergency by keyword rules, and some
  urgent messages match none of them: "theres a baby strapped in a car seat in a locked car in the supermarket car
  park, windows up, 34 degrees out, the baby is bright red and has stopped crying" got an ordinary answer with no word
  about calling for help. A small model bundled in the app (66 KB) now reads the whole message. When it is sure someone
  is in trouble now, the answer opens with "If this is an emergency, call your local emergency number first (112 works
  on mobile phones in many countries)."
- **The answer under the line is unchanged.** The model opens no guide, changes no route and replaces no answer. A
  message the rules do recognise is handled by the rules alone, as in 1.4.9.
- **Where the line is not added.** A follow-up, a message of fewer than four words, a question asked to learn ("before
  my course"), and a question the app answers itself: a greeting, a phrase, a sum, your position, places near you.

## How much better?

Our own check, not an independent score: 170 messages written by an AI agent that had read none of our files, 80
urgent and 90 not, each answered by the app's own code.

| | 1.4.9 | 1.4.10 |
|---|---|---|
| Urgent messages whose answer opened with no call line | 18 of 80 | 1 of 80 |
| Messages that are not urgent and were given the line | — | 2 of 90 |

The first row is not a blind number: one rule was changed after the first run of this check. Across this set, the
model's training messages and a reviewer's own, 7,276 in all, every answer was either the same as in 1.4.9 or the same
with the line above it.

## Good to know

- **The line is the whole gain.** For the baby in the hot car, the answer under the line is as weak as in 1.4.9: the
  smaller model quotes an unrelated passage. This release does not fix that.
- **Ordinary trouble can get the line too.** "I lost my passport while travelling in Bangkok, what do I do now" opens
  with it. Of 38 such messages a reviewer wrote to break the model, 21 get the line above their unchanged answer.
- **It was built to do more and cut back.** A first design sent these messages to the emergency reply in place of
  their answer. Reviews before release found it would have done the same to the lost passport, so 1.4.10 only adds the
  line. The record is in [the model's notes](../../tools/emergency-classifier/README.md).
- **One part is switched off.** The app now has the code for a model trained to answer from passages in one pass. No
  model the app offers uses it, so nothing changes for you.
- **Nothing else changed.** Search, sums, guides, packs and downloads are the same as in 1.4.9.
- **Tested on** a 12 GB Android emulator, with both models. Not yet on a real phone.
- **Answers can be wrong.** Nothing here is medical advice. In an emergency, call your local emergency number.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.9 and keeps your data.

## More detail

- [1.4.9 release notes](field-atlas-1.4.9.md)
- [Every change since 1.4.9](https://github.com/0x94t3z/fieldatlas/compare/v1.4.9...v1.4.10)
- [Install guide](../installation.md)
