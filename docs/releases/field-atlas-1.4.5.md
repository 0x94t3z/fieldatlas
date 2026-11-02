# Field Atlas 1.4.5

A safety update. Field Atlas answers questions on your Android phone without an internet connection;
1.4.5 opens the right first-aid guide for more of the ways people describe an emergency, answers a
new question after an emergency as a new question, and makes long answers quicker to get through.

## What's new

- **More emergencies find their guide.** A small classifier, built into the app, reads a first-aid
  message the keyword rules found no guide for. "my friends face went lopsided on the train and she
  cant lift her right arm" now opens the stroke guide, and "kid left in the car in the sun 20 min,
  very red and hot and floppy" the heat guide, in under a second. It never opens the drinking-water
  or signalling guides, which travel and phone questions ("is the tap water safe", "no signal") resemble.
- **A new question after an emergency is answered as one.** A follow-up was read together with the
  question before it, so a question about supplements asked under a heat stroke guide got the
  emergency reply. A follow-up with its own subject is now read on its own. One about the person,
  getting help or waiting for it still stays with the emergency.
- **Jump to the end.** A long answer shows a small arrow button above the follow-up box; one tap
  goes to the end of the answer, its sources and the follow-up box. It is hidden while the answer is
  written or read aloud.
- **A warning for very large models.** Importing your own model file over 6.6 GB, or over 45% of the
  phone's memory, now shows a warning in Library. On a 16 GB phone, the largest model file that
  loaded was 6.55 GB; larger ones crashed or did not work, in any app.

## How much better?

On 160 new messages written for this test (120 emergencies and 40 that only look like one), compared
with 1.4.4:

| | 1.4.4 | 1.4.5 |
|---|---|---|
| Emergencies with no guide | 33 of 120 | 16 of 120 |
| Guides that would be wrong to follow | 0 | 0 |
| Non-emergencies given a guide | 6 of 40 | 7 of 40 |

These are our own measurements, not an independent score. The classifier was trained on messages
written for each guide, never on these 160.

## Good to know

- **Answers are otherwise unchanged.** The same models, prompts and collections.
- **Tested on** a 12 GB Android emulator. Not yet on a real phone.
- **Answers can be wrong.** Nothing here is medical advice. In an emergency, call your local emergency
  number.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.4 and keeps your data.

## More detail

- [1.4.4 release notes](field-atlas-1.4.4.md)
- [Every change since 1.4.4](https://github.com/0x94t3z/fieldatlas/compare/v1.4.4...v1.4.5)
- [Install guide](../installation.md)
