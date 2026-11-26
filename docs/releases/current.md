# Field Atlas 1.4.11

A maths textbook you carry is now found when you ask how to do something, and its formulas are shown as text you can
read. Field Atlas answers questions on your Android phone without an internet connection; 1.4.11 is a small release
for the Mathematics: Prealgebra collection and changes nothing else you see.

## What's new

- **Textbook steps are found.** With Mathematics: Prealgebra installed, "How do I add decimals?" found nothing in the
  book. The app keeps a passage for a "how" question only when it explains something, and worked steps do not read as
  an explanation. A "how do I" question now also keeps a textbook section whose title names the task. The rule applies
  to textbook collections only; every other collection is checked as in 1.4.10.
- **Formulas in passages are written out.** The textbook's saved text holds its formulas as LaTeX, and the app showed
  them as saved: `$\frac{70}{100}$`. Source cards, quotes in an answer, the source sheet and the full source now show
  "70/100", and a sum set out in columns as "3.7; +12.4". The source sheet says "Saved text, its formulas written
  out." when it has done so.
- **The bar over repeating decimals.** An answer that writes 7/12 as a decimal now shows 0.583̅, not the LaTeX for it.
- **Two collections in the built-in list.** Practical skills and Mathematics: Prealgebra have been offered through
  Refresh since 14 November. They are now listed before the first Refresh too.

## How it was checked

Our own checks, not an independent score.

- Two review passes before release. The first found that the textbook rule let unrelated Encyclopedia passages
  through, and that writing formulas out damaged text that is not maths: a name such as "Joey Bada$$", prices, code.
  Both were fixed. The textbook rule now leaves the Encyclopedia with the passages it kept in 1.4.10, and text with no
  LaTeX in it is shown as saved.
- The app's tests, the release checks and the Python and website suites pass.

## Good to know

- **The larger model still writes from its own knowledge.** On a 12 GB device the "add decimals" answer came from the
  model, with the textbook passages listed under it as found and not used. The foot of the answer says so.
- **Sums are not checked.** A model can add wrongly: one run gave 17.00 for 4.39 + 12.6, which is 16.99. This release
  does not correct that. Check a figure that matters.
- **Some formulas are still shown as saved.** About 200 of the 1,313 Prealgebra passages hold plain numbers between
  dollar signs with no LaTeX command, such as `$17$`. They are left alone so that prices elsewhere are never rewritten.
- **Raw recognised text stays raw.** Text read from a photo or scan is shown as it was read.
- **Nothing else changed.** Search, emergency guides, places, downloads and the models are the same as in 1.4.10.
- **Tested on** a 12 GB Android emulator. Not yet on a real phone.
- **Answers can be wrong.** Nothing here is medical advice. In an emergency, call your local emergency number.

## Install

1. Download `fieldatlas.apk` below (Android 13 or later, arm64). `fieldatlas.apk.sha256` lets you check it.
2. Open it and allow the installation. It updates 1.4.10 and keeps your data.

## More detail

- [1.4.10 release notes](field-atlas-1.4.10.md)
- [Every change since 1.4.10](https://github.com/0x94t3z/fieldatlas/compare/v1.4.10...v1.4.11)
- [Install guide](../installation.md)
