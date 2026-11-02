# Field Atlas 1.4.6

A safety and accuracy update. Field Atlas answers questions on your Android phone without an internet
connection; 1.4.6 opens the poisoning guide for two kinds of emergency it missed, reads the right part of
the saved encyclopedia for short fact questions, and makes very long questions easier to read.

## What's new

- **Two emergencies now open the poisoning guide.** Fumes from cleaning products, such as "i mixed bleach
  and ammonia cleaning the bathroom and my chest is tight", opened the heart attack guide (#8). A small thing
  put in the mouth and now gone, such as "my 2 year old put a little round remote control battery in her
  mouth and it is gone", opened no guide, so the model wrote the answer (#9). Both now show the call line and
  the NHS poisoning guide in under a second. A splash in the eye, choking, or a question before anything has
  happened ("is it safe to mix bleach and vinegar") keep their own routes. Thanks to swiftbotlabs for both
  reports.
- **Short fact questions read the right passage.** With Qwen3.5 4B:
  - "Why is X called Y", "who won", and where a name comes from are now looked up in the saved
    encyclopedia instead of answered from memory.
  - A question about one year's event finds that event's article: "how many electoral votes did Trump have
    in 2016" now answers 304, from the article on the 2016 election.
  - A note longer than the model reads now shows the part with the asked words, and a passage the collection
    split mid-word reads on into its next part.
  - A section the question names by its heading is read first, and a band named with "The" ("The Strokes")
    is looked up by that name, not as a word ("stroke").
- **Dates and citations stay with the passage.** A date the model gives to the day where the passage gives
  only the month is shown to the month, and a citation is kept only where its passage states the figures of
  that sentence.
- **Long questions are easier to read.** A question longer than three lines is set a size smaller and cut at
  three lines, with **Show full question**.

## How much better?

Our own measurements, not an independent score:

| | 1.4.5 | 1.4.6 |
|---|---|---|
| Emergency messages routed right, on 60 new messages written for this test | 24 of 60 | 34 of 60 |
| Of those, a guide that would be wrong to follow | 3 | 1 |
| Earlier test messages (2,438) whose guide changed | | 2, the two reported ones |
| Fact answers with the 4B rated good, among the 12 of 60 new questions whose answer changed | 1 of 12 | 5 of 12 |
| Wrong facts per changed answer | 3.04 | 1.67 |

The fact measurement was made before the last two changes (the passage cut and "The" names); those were
checked on four questions a tester reported, on an emulator. The 2B answers fact questions as before.

## Good to know

- **The model still answers from memory where the saved passages do not say it**, and can still be wrong.
- **Tested on** a 12 GB Android emulator. Not yet on a real phone.
- **Answers can be wrong.** Nothing here is medical advice. In an emergency, call your local emergency number.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.5 and keeps your data.

## More detail

- [1.4.5 release notes](field-atlas-1.4.5.md)
- [Every change since 1.4.5](https://github.com/0x94t3z/fieldatlas/compare/v1.4.5...v1.4.6)
- [Install guide](../installation.md)
