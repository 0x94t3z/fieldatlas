# Field Atlas 1.4.3

A safety update. Field Atlas answers questions on your Android phone without an internet connection;
1.4.3 makes its emergency help find the right guide more often, and stops offering a model that did
not answer on a real phone.

## What's new

- **Severe bleeding is recognised as people describe it.** "Cut his hand on broken glass, it's
  spurting and won't stop, the towel is soaked" now opens the severe bleeding guide. Before, a message
  that never said "bleeding" could get no guide at all.
- **A new electric shock guide** from the CDC, with its read-aloud recording: look first and do not
  touch someone who may still be in contact with the source, call for help, turn off the power.
  Messages such as "got shocked by the cord and he's still touching the lamp" or "the hair dryer fell
  in the bath" now open it. A shock felt as a nerve sensation, shock therapy, or being shocked by a
  bill does not. 52 first-aid guides in all.
- **When no guide fits, the first step is still there.** The reply for an emergency no guide covers
  now starts with "do not touch them while they may still be in contact with the source" when
  electricity may be involved, and "cover the wound and keep pressing" when there is a wound, each
  cited to its guide.
- **Gemma 4 26B is no longer offered.** On a 16 GB phone it gave no answer in over 50 minutes, while
  Qwen3.5 4B on the same phone answered in about 8 seconds; it had only been timed on an emulator.
  Qwen3.5 4B is now recommended on phones with 8 GB or more, including 12 GB phones. If Gemma is
  already on your phone it stays installed; we suggest choosing Qwen3.5 4B instead, and you can remove
  Gemma in Library to free 10.55 GB.

## How much better?

On 90 new messages written by someone who had not seen the code (bleeding, electric shocks, and
look-alikes such as nosebleeds, pregnancy bleeding and "budget cuts"), each labelled with the guide
that should open, the right guide opened for **59** against **37** in 1.4.2, and guides that would
mislead fell from **2** to **1**. No message got a worse result. Across the 2,196 questions of
earlier tests, 11 get a different guide or none, each a better result. These are our own
measurements. About four in ten bleeding and shock messages told in unusual words still find no guide;
for those, the reply above gives the first step.

## Good to know

- **Speed, storage and answers** are otherwise as in 1.4.2.
- **Tested on** a 12 GB Android emulator, with the matching checked on Android's own engine. Not yet
  on a real phone.
- **Answers can be wrong.** Nothing here is medical advice. In an emergency, call your local emergency
  number.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.2 and keeps your data.

## More detail

- [1.4.2 release notes](field-atlas-1.4.2.md)
- [Every change since 1.4.2](https://github.com/0x94t3z/fieldatlas/compare/v1.4.2...v1.4.3)
- [Install guide](../installation.md)
