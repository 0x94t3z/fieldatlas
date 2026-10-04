# Field Atlas 1.3.0 (prepared, not yet published)

These notes describe the 1.3.0 source. No signed public APK has been published yet; the current
release is [1.2.1](current.md). The known limitations below still apply, and this is not a claim
that answer quality or bounty acceptance criteria have been met.

## Changes

- **Emergency questions are answered from published guides, offline.** Ask Research what to do
  (“someone is choking”, “I got bitten by a snake, what should I do?”, “is it safe to drink
  stream water?”) and the model answers from the matching bundled guide only, citing it, with
  the guide's key steps shown word for word above its answer the moment the question is asked.
  41 guides cover first aid, heat and cold, outdoors and survival, and disasters, from CDC,
  NIOSH, NIH, the National Weather Service, the National Park Service, FEMA and the U.S. Fire
  Administration (public domain) and four Wikipedia articles (CC BY-SA 4.0). They ship inside
  the app, so they work before any download; if the model cannot run, the guide is shown as
  published. See [Emergency knowledge](../emergency.md).
- **Questions that need no model no longer wait for it.** Near-me lookups, and emergency guides
  when the model is not ready, start immediately, even while the model is loading or if it
  cannot load on a low-memory phone.
- **Near-me help reaches farther.** For hospitals, clinics, pharmacies, police and drinking water,
  the search widens from 15 km to 50 km and then 100 km when little is close.
- **“Open now” is checked against the phone's clock.** Asked which places are open now, the app
  reads each place's mapped opening hours, marks it open (and until when) or closed at the
  phone's current time, and lists open places first. A near-me search keeps widening while
  everything close is closed, so a 24-hour pharmacy farther away is found at night. Hours it
  cannot read are not guessed, and for a named city the answer says it assumed the phone is set
  to that city's time. Recorded hours can still be out of date.
- **Museum questions list museums.** “Which museums are listed in London?” lists only places
  mapped as museums or galleries in that city, so a zoo, or a museum in Oxford, no longer
  appears; a trip question asking for a food stop and a museum gets both.
- **Places without an address show coordinates.** Most Tokyo listings in the map data have no
  address; they now give coordinates that any offline map app finds, and a Japanese address
  without a street name is shown whole on its card.
- **More files can be attached.** Word (.docx), PowerPoint (.pptx), OpenDocument (.odt, .odp) and
  EPUB books, HTML as readable text, HEIC/GIF/BMP photos, UTF-16 without a byte-order mark and
  Windows-1252 text. Older Office formats, spreadsheets and archives get a message saying how to
  convert them.
- **PDFs are read from their text layer on every Android version.** Before, Android 13–14 ran text
  recognition on every page (an 8-page PDF now reads in about 3 seconds on a Redmi 13C), and
  Android 15+ appended recognized text to the text layer, nearly doubling it. Recognition now runs
  only on pages without real text, at most 30 per file.
- **Long files are read in part instead of refused.** Up to 50 MB, 300,000 characters and 1,000
  PDF pages; what was not read is listed in the preview and in the answer.
- **Answers about files use better excerpts.** Passages break at paragraphs and sentences, and are
  ranked by the question's distinctive words (part codes such as K-9 and K9 match). Summary
  questions take passages from across the whole file. When an answer used only part of a file,
  the app itself adds a line saying how many passages it used and from which pages.
- **The amount of file text fits the phone.** Prompt size is now measured in tokens with an
  estimate calibrated against the real tokenizer (it never undercounted in testing), not in bytes.
  The app learns how fast the phone reads a prompt and sizes file evidence so the wait before the
  first word stays near 40 seconds: fast phones get up to about 1,800 tokens of file text, slow
  phones the best few passages. Instructions that cannot apply to a question are left out.
- **Attached files survive the app being closed.** If Android closes Field Atlas while a picker or
  the camera is open, the files are restored and read again with the question.
- **Answers are easier to read and pass on.** Citations are small numbered marks attached to the
  words they support. Copy and Share sit at the top of every answer and include the sources;
  sharing works without a connection (Bluetooth, nearby share, SMS). A place answer opens with its
  result in one sentence, with the caveats beneath it, and each place card shows open or closed,
  diet and distance as labels. History can be searched and each saved answer shared.
- **Answers start about 25% sooner on 8-core phones.** The model now uses six threads instead of
  four. On a Redmi 13C, the same question's first word came at 71.7 s instead of 94.7 s, and
  writing went from 3.69 to 4.06 tokens a second; eight threads were slower at writing, so two
  cores stay free for the phone. See [the measurements](../../scripts/patches/README.md#thread-count).
- **Answer details show where the time went.** “Prompt reading” gives the tokens the model read
  and how long that took; “Writing speed” now counts the writing only (it divided by the whole
  wait, showing 1.6 tokens a second for a model writing at 4.4).
- **“Tell me Japan history” starts with its source.** Everyday phrasings (“tell me …”, “what do you
  know about …”, “give me an overview of …”) and titles such as “History of Japan” for “Japan
  history” now open the answer with the encyclopedia's own words, cited, while the model's
  explanation is still being written.
- **Research no longer looks stuck while the model reads and writes.** The reading bar moves between
  the engine's block reports at the speed measured so far, never ahead of it, with the time left
  ("about 20 s left"); the live draft follows the newest text instead of freezing once an answer
  grew past its first lines.
- **New 3D app icon and opening animation.** The launcher icon is the 3D compass coin, and on
  launch the coin turns once and settles face-on while the app starts (about 0.75 s, no added
  delay; cold start on the Redmi 13C stayed at 0.7–0.75 s). Themed icons keep the flat outline.
- **More library questions find their overview passage.** A question such as “Explain autophagy
  and distinguish …” now uses the saved passage that defines the topic, as “Explain autophagy and
  how …” already did, instead of being answered by the model alone.
- **Fixes.** Stopping voice input while the recogniser was still decoding could crash the app (it freed the recogniser under the recording thread; found with Android's malloc debug, the kind of bug a hardened allocator such as GrapheneOS's turns into a crash). A very large file could crash the app (out of memory); a camera photo could not be
  retried after a storage error; every photo was named “Camera photo.jpg”; a full disk said “Could
  not read this file”; the text preview stuttered on long files; nested list steps showed flat.

## Verification

- 551 JVM unit tests pass (10 opt-in desktop tests skipped), including real-tokenizer counts,
  generated DOCX/PPTX/ODT/EPUB files, a zip bomb, emergency-question routing against all bundled
  guides, questions that must not be routed to a guide, and opening-hours rules (split shifts,
  past midnight, holidays, open-ended closing times, unreadable hours).
- Desktop, the 24-question release suite (same model, seed and collections as 1.2.0): 15 pass,
  8 partial, 1 fail; judged about as useful as an online frontier answer on 16½ of 23 (72%),
  up from 65% before the travel and retrieval fixes. The reviewer is the author's assistant and
  the questions were used to find the problems, so this is not a held-out score. See
  [Evaluation](../evaluation.md#after-the-retrieval-and-travel-fixes-october-4).
- Desktop, Qwen3.5 2B, 7 attachment questions × 5 seeds: 33/35 passed the fixture checks (1.2.1:
  32/35). Keyword checks, not an accuracy score.
- Desktop, Qwen3.5 2B, 6 emergency questions × 2 seeds: 10 of 12 model answers were clean; two
  contained one garbled sentence each, beside the verbatim source excerpt.
- Redmi 13C, Android 13, airplane mode, installed over 1.2.1 with data kept:
  - snakebite question: the CDC/NIOSH source excerpt on screen in 4 s, cited model answer in 1 min 4 s;
  - 8-page text PDF ready in about 3 s; answer cited to page 6 in 53 s once the phone's speed was
    learned (2 min 31 s before the evidence budget was sized to the phone);
  - DOCX 39 s, EPUB (cited to chapter 3) 44 s, PPTX (cited to slides 2 and 3) 41 s, photo of a
    printed sign read with OCR 36 s, all correct; nearest-hospital lookup about 1 s;
  - after the travel fixes, installed over the earlier 1.3.0 build: “Which pharmacies near me are
    open now?” in 2 s, widening to 50 km in a rural area and listing two 24/7 pharmacies and one
    open until 21:30 first, then the three nearer pharmacies with no recorded hours; “Which vegan
    restaurants in Berlin are open now?” marked two of six open at the phone's 10:39 and stated
    the Berlin-time assumption. Each card showed its open or closed status.
- Device tests on the same phone: all 68 instrumented tests pass, including the 9 attachment-reader
  tests (a 3-page text PDF reads in 852 ms). Eleven of them had been failing
  on 1.2.1 as well because they expected older wording and layouts (for example licence details
  now in a pack's About sheet, and citations that open a preview before the full source), the
  microphone test never granted the microphone, and the offline test still forbade the internet
  permission that downloads use; they were updated to the current interface and to the policy in
  `scripts/verify_offline.sh`. One real defect found on the way (the original-file viewer could
  keep its loading spinner after a file was read) was fixed by publishing the loaded file on the
  main thread.
- Memory-safety check for GrapheneOS-class hardware, on the same phone: the device suite and a
  full model answer were run with Android's malloc debug (guard bytes, freed-memory tracking,
  pointer checks) on every native library: llama.cpp, Tesseract, Vosk and SQLite. It found the
  voice crash above and nothing else; after the fix the suite passed with no reports.
- Release lint and the offline packaging audit pass. APK size 108.1 MB (1.2.1: 100.7 MB); most of
  the increase is the PDF text library, its fonts and the BouncyCastle crypto library it uses for encrypted PDFs.
- Not yet done: a public-key signed build.

## Known limitations

- The guides are information, not training, and many are written for the United States. Severe
  bleeding has no bundled tourniquet guidance; the choking and drowning guides are Wikipedia text.
  These need review by a clinician. The model's part of an emergency answer can misstate a step;
  the verbatim source excerpt above it keeps the guide's own wording. See [Emergency knowledge](../emergency.md#limits-you-should-know).
- On a slow phone a long file is answered from a few passages; the added coverage line says so.
- The 1.2.1 limitations on citation repair and model reasoning still apply.

## Installation

Not yet published. App version: 1.3.0 (17).
