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
  not stop it. The Research home shows it as "Still answering", the next question waits its turn
  (a first-aid guide, an emergency number or the no-guide reply never waits: it opens at once),
  and a note says when it is ready if you are in the app at that moment; otherwise the row on the
  Research home says it. An answer you watched finish is not announced again.
- **Research on the answer page.** The page opens as soon as you start. The dino walks from
  Search to Read to Write while the answer forms in place, Stop sits in the question box, and the
  passages found appear as they come in.
- **Read aloud.** Answers and emergency guides can be read aloud with the phone's voice, or with a
  natural voice, Supertonic 3 (about 146 MB), offered as an optional second step in setup, after the
  answer model. Both work with no signal. The phone's voice is used only with an English voice that the
  phone's speech engine reports as installed and working without a connection; an engine offering
  only voices that need the internet is not used. Supertonic 3 reads instead if it is there;
  otherwise the app says what to install. The app relies on the engine's own report of its voices. Supertonic 3 counts toward the 50 GB storage limit like a model or a collection.
- **More emergencies found.** An emergency told as a statement ("tornado siren going off and we're
  in a motel room") now gets its guide. On 45 new emergencies the right guide was shown for 14,
  against 9 in 1.4.1, with no unsafe guide. Nine guides are new, 51 in all, each with a recorded
  reading for Read aloud: from the NHS (Open Government Licence) asthma attacks, fever in children, a
  knocked-out tooth, low blood sugar, what to do after a poisoning and bleeding in pregnancy; from
  the CDC opioid overdose and naloxone; a chemical or object in the eye; and someone unconscious but
  breathing, who is now put on their side (the recovery position)
  instead of left lying down or sent to CPR, which stays for anyone not breathing. An object stuck
  in a wound ("a branch went into his thigh, it's still sticking out") opens the bleeding guide that
  says to leave it in and pad around it; it used to open the drowning guide, whose text shares the
  words "branch" and "pull out". Signs told as people see them now open their guide: "grabbing at
  his throat, can't make a sound", "one side of her face has slid down, talking gibberish", "grey
  and clammy" after a crash, "dragged him out of the pool", a pounding headache at 4,500 m, a
  funnel or a green sky, a sea that has sucked back, hair standing up on a ridge, a toddler who ate
  a cigarette butt, "everything shaking, shelves falling", "whole body jerking, eyes rolled back", a
  head that hit the kerb, an ankle bent sideways, an orange sky with ash falling. On two blind sets
  of 60 routing questions the right guide was shown for 14 (56%) and 19 (76%) of 25 covered emergencies, against
  11 and 10 before, with nothing lost
  ([details](../evaluation.md#set-38-and-passages-as-a-check-on-figures-october-20)).
- **First aid opens with the call line, and is written for your situation on 12 GB phones.** An
  emergency answer now starts with "Call your local emergency number now" for anything
  life-threatening (or "call first" otherwise) and the official guide, in about twelve seconds on a
  6 GB phone. The signs the guides list open their guide: a collapse with gasps or a chest that
  barely moves is CPR, not the recovery position; a child's fever, something swallowed just now, a
  low blood-sugar reading, an asthma attack gone quiet, a reaction after a food. On a 12 GB phone
  Gemma 4 26B then writes an answer for the question as asked with the guide as its only source,
  while the published guide stays on screen; on 40 new situations graded blind it gave a good answer
  to 30, one judged unsafe. The smaller models show the guide as published and never rewrite it.
  Where no saved guide covers the situation, the answer says so and gives no treatment steps. A
  learning question ("how deep do you push for CPR?") is answered as a question by the 4B and Gemma;
  the 2B shows the guide it is about instead of paraphrasing a passage. On 320 labelled
  questions the right guide opens 231 times, with none misleading
  ([details](../testing-1.4.2.md)).
- **Any model file.** **Import a file** in setup or Library accepts a model's own `.gguf` as well as
  a `.fapack`: a file Field Atlas tested keeps its tested settings, any other installs as your own.
- **If Android closes the app while a model loads**, Qwen3.5 2B answers instead and a note stays
  in Library saying so, with the way back ("Choose Gemma 4 26B here to try it again"), until you
  choose a model again.
- **Answer details stay with the answer.** The time an answer took sits under the question with a
  clock, and the fold with its retrieval, reading and writing times, tokens and citations is kept in
  History, so an answer opened again shows them as on the day.
- **Rare subjects are read from the collections.** A subject few readers meet — one the Simple
  English edition lacks and that is named in few saved passages, or one a specialist collection
  holds — is answered from the saved sources, cited sentence by sentence, instead of from the
  model's memory, which invents on such subjects. On 40 obscure questions graded blind the 4B gave a good answer to 7 instead of 2 (18% instead of 5%),
  with fewer grader annotations; famous subjects are untouched
  ([details](../evaluation.md#reading-the-sources-for-subjects-few-readers-meet-october-20)).
- **Single facts are read too, on the 4B.** A question for one fact (who designed, when did, what
  year, how tall, how deep, what is the capital of) has Qwen3.5 4B read three saved Encyclopedia notes
  before it answers and cite them: from memory it opened the Sydney Opera House on 26 October, with
  its notes on 20 October 1973. On 105 earlier sealed fact questions whose answer this changed, compared
  blind side by side in two rounds (graders did not see the notes), good answers went from 38 to 66 (36% to 63%) and grader annotations per answer from 2.79 to 1.69,
  none unsafe; the header then says "1 cited local source" beside the time. Gemma 4 26B, which reasons
  first, keeps to its knowledge. A mistake in a saved article is now passed on: the Simple English
  article gives the Burj Khalifa as 428 m tall (it is 828 m)
  ([details](../testing-1.4.2.md#facts-from-the-saved-encyclopedia-4b)).
- **Places around a landmark, drawn as cards.** "A hospital near the Colosseum" is measured from the
  Colosseum, where the travel guide puts it, and lists hospitals 690 m away instead of ones 8 km
  across Rome; "coffee near Shibuya Crossing" keeps to places within a walk. "Vegan restaurants in
  Berlin" lists them. Each place is now one card: its name with how far and which way, what it is,
  whether it is open, and the guide's words, with a tile coloured by kind (red is kept for health).
  A list of hospitals opens with "Emergency? Call 112 or your local number." and the nearest hospital
  recorded with an emergency department, which opens on a tap: near the Colosseum it was sixth of six
  by distance. A place's sheet shows its address large enough to show a driver, full screen on a tap.
- **Every answer in one shape, sources at the foot.** An answer written from the model's own knowledge
  opens with a short paragraph that answers outright, then **Key facts** as bullets, then **Caveats**
  when something varies. The explanation comes first; the passages found are listed under it as
  sources, and a sentence whose figure one of them also states is marked with that passage's number,
  so the collections show where they back the answer. On 40 questions graded blind the shape changed
  how answers read, not what they got right (better on 12, worse on 9, the same on 19)
  ([details](../evaluation.md#one-shape-for-every-answer-sources-at-the-foot-october-20)). A fare
  with a rate ("12 euros plus 2 per km, how much is 15 km") is worked out from its own figures. A
  "near me" question says whether Location is off, not allowed, or on without a fix yet, and waits
  less for GPS; the progress line gives the measured range of recent answers ("Usually 3–4 min").
- **A much larger Biology & longevity collection.** It now holds 48,286 open-access ageing and
  longevity papers from PubMed Central, each under CC BY or CC0, with genetics summaries and three
  more ageing databases: 900,714 passages, a 1.08 GB download (2.86 GB installed). On 40 new longevity questions graded blind, good answers went from 2 to 7 (5% to 18%) with
  Qwen3.5 2B and from 4 to 6 (10% to 15%) with Qwen3.5 4B. Phones with the earlier edition are offered it as an update in Library.
- **Questions about your own files answer far more often.** A question asked with a file reads more of
  it, and a correct answer is no longer withheld by the model's own review. On 24 new file questions
  graded blind, refusals went from 7 to 1 and good answers from 4 to 5, with more problems noted
  by the graders
  ([details](../evaluation.md#questions-about-your-own-files-october-18)). Asking to "use my library"
  in the question adds the downloaded collections as before. A question about taking a medicine in
  the file (a dose, how often, until when, a missed dose) also shows the file's own lines on it, word
  for word, under the answer, and says first when the question or the answer has a medicine's
  frequency wrong ("your file gives METHOTREXATE once a week, not daily"; daily against weekly only).
  The answer itself is not changed, so a wrong sentence in it stays: read the quoted lines.
- **An answer instead of "the excerpts do not say".** When none of the passages found is about the
  question, the model now answers from what it knows, labelled as not checked against your sources, instead of saying
  the passages miss it. On the 8 answers this changed, graded blind, 5 of the 8 became good answers, where none had been, with more wrong
  details to check
  ([details](../evaluation.md#answering-when-the-excerpts-miss-the-question-october-19)). A
  swallowed battery, however it is described, opens the poisoning guide.
- **Word problems that name places** ("leaves Tokyo at 17:05 … UTC+9") are worked from their own
  figures.
- **Better passages for the model.** A place typed in lowercase counts as a place, a narrower
  article that search found ("Life on Titan") replaces a generic one ("Life"), and no question
  reaches the model without a passage. On 80 questions written for these cases, the 30 answers that
  changed went from 1.9 to 3.9 of 10.
- **Answers from the model's own knowledge.** With Qwen3.5 4B or Gemma 4 26B, an ordinary question
  is answered from what the model knows, with one line under it saying so and what was checked
  ("From the model's knowledge; 2 figures match your sources"), and the passages found listed under
  it to check. On 40 new questions graded blind, the 4B gave a good answer to 8 instead
  of 4 (20% instead of 10%), and Gemma 4 26B, which reasons before it answers, to 27 (68%)
  ([details](../evaluation.md#answers-from-the-models-own-knowledge-and-a-model-for-12-gb-phones-october-19-20)).
  These answers draw more grader annotations than cited ones, so check the passages. Qwen3.5 2B still
  answers from the passages, with citations. Emergencies, places, files, phrases and calculations
  keep their own answers.
- **Gemma 4 26B for phones with 12 GB of memory** (10.5 GB download). Setup offers it first there.
  On a 12 GB Android emulator it answered in a median of 92 seconds and left 9.4 GB of memory free.
- **Qwen3.5 4B from 8 GB of memory, Qwen3.5 2B below.** Setup recommends the 4B on 8 GB phones and the
  2B on phones with less memory. A 6 GB phone can still choose the 4B: setup and Library mark it slow
  there, and a 4B downloaded on such a phone waits in Library until it is chosen. On a 6 GB Redmi 13C the
  4B took 3 to 6 minutes an answer, and once 47 minutes with Wi-Fi on and other apps open. A phone
  already using the 4B keeps it.
- **Time across cities** reads "9 in the morning", the month (for daylight saving) and the weekday:
  9 am on a Monday in New York in January is 11 pm Monday in Tokyo.
- **Sources numbered as the answer cites them.** The first source an answer cites is 1, so a
  cited "2" no longer sits above an uncited "1". Each passage has its own row with its opening words.
- **Library and setup.** Discover lists each collection and model in two short lines, with an
  About sheet for each, and a search field with All, Models, Knowledge and Voices to find one as the
  catalogue grows. Setup lists three collections A to Z, with "See all" and "Show fewer". A green bar
  above the tabs shows what is downloading. Importing a
  collection from a file counts only installed collections against the 50 GB limit, so a 24 GB
  Encyclopedia file is no longer refused. Download sources include the voice.
- **Fixes:** a question about food poisoning, a blood pressure reading, a diver's dizziness after a fast
  ascent, a pan on fire, a nosebleed, hyperventilation, a bad back, someone pinned under a shelf, a
  heart attack "back in March", an inbox "flooded" with deadlines or water for hurricane season no
  longer opens a guide written for something else (the model answers instead), "90 minutes from the nearest hospital" or "take her to a doctor" no longer turn a question
  into a search for a place, a download paused while it waited for Wi-Fi stays paused when the app reopens, places named without their accents are found, Ethereum standards stay out of
  questions that are not about crypto, an answer the model wrote twice is shown once, the
  "Answer ready" note follows the light theme, and the research page opens with "Ask the atlas."
  Preparing to make stream water safe on a hike no longer opens with "call your local emergency number
  now", someone ill after drinking stream or lake water is no longer shown how to treat water (the
  dehydration guide or the no-guide reply answers), and the old progress card no longer flashes on the
  Research home. The time shown for an answer now includes any wait for an earlier one.
  "Summarize the causes of the First World War" and "according to historians" questions are answered
  instead of refused for want of a saved source; "how do I treat a minor burn at home" gets the burns
  guide; "what should I know about altitude sickness before trekking to Everest Base Camp" is answered
  as a trip still ahead instead of opening with the call line; the Read aloud sample is offered only
  when the phone has an offline voice to play it; and a place source shows its own words, not its
  data fields.

## How good is it?

- **With the new answer models:** on 40 new questions graded blind (set 37), Gemma 4 26B gives a good answer to
  **27 of 40 (68%)** and Qwen3.5 4B to **8 (20%)**, against 4 (10%) for the 4B and 2 (5%) for the 2B answering from the passages
  ([details](../evaluation.md#answers-from-the-models-own-knowledge-and-a-model-for-12-gb-phones-october-19-20)).
  The 4B's answers draw more grader annotations than the 2B's cited ones (1.94 per answer against 0.71);
  Gemma's have fewer (0.44). These scores measure what each model knows: its answer is written
  without the saved passages, which are listed under it to check, so they do not show the
  collections improving those answers. They come from runs on a computer, not on a phone.
- **Citing the passages:** on 120 new questions (two sets written after the changes and
  graded blind twice), 1.4.2 gives a good answer to **27 (22%)** with Qwen3.5 2B and **28 (23%)** with
  Qwen3.5 4B, the 4B with half the grader annotations (0.30 against 0.62 per answer). That is below the bounty's bar
  ([details](../evaluation.md#142-release-candidate-on-fresh-questions-october-17-18)).
- **Single questions are where 1.4.1 left them.** 1.4.1 scored 29% on an earlier, harder set. On
  the same 60 questions, 1.4.1 scores 37.3% and 1.4.2 36.3%. The gains above are in follow-ups,
  emergencies, word problems and the 4B.
- **Longevity research** with the new Biology collection: 35.6% with the 2B and 35.9% with the 4B
  on 40 new longevity questions, against 23.0% and 22.3% with the earlier edition
  ([details](../evaluation.md#biology--longevity-on-40-longevity-questions-october-18)).
- **Strongest:** quick facts (55%) and emergencies (54%). **Weakest:** places in a named city
  (14%), cryptography (22%), explanations and synthesis (23%).

Every question, answer and grade is published in
[docs/fieldtest/results-1.4.2](../fieldtest/results-1.4.2/).

## Good to know

- **Speed, from asking to the complete answer.** On a 6 GB Redmi 13C in airplane mode (October 24,
  six questions asked twice), Qwen3.5 2B took 88 to 153 seconds for simple questions and 195 to 351
  seconds for explanations, comparisons and a trip plan; Qwen3.5 4B took 3 to 6 minutes. Gemma 4 26B
  took about 1.5 to 3 minutes on a 12 GB emulator, which runs at a computer's speed; an emergency answer
  written by Gemma takes a few minutes after the guide is on screen. Answers built from saved data
  (places, guides, phrases, calculations) take seconds. No 8 GB or 12 GB phone has been timed.
- **Answers can be wrong.** Open the cited passages to check them. Nothing here is medical advice.
  Answers about a medical letter or drug sheet you attach can get details wrong: read the file itself
  before acting on a dose or a result. The file's own lines on a medicine are quoted under the answer
  to make that quicker; they are matched by their words, so a line can be missed, and they do not
  correct the answer's own sentences.
  The emergency guides are information, not training, and many were written for the United States.
- **Map data and opening hours can be out of date.**
- **Storage.** The 2B model and every collection take about 33.7 GB, or 35.3 GB with the 4B
  instead and about 43 GB with Gemma 4 26B; the natural voice adds 145 MB. Installing the
  Encyclopedia last needs room for its download and installed copy at once: about 44.3 GB at the
  peak, inside the 50 GB budget. With Gemma, install the Encyclopedia first.
- **Within the bounty's limits.** On an Android emulator with 12 GB of memory, the two Qwen models,
  all seven collections and the voice take 37.0 GB of the 50 GB allowed, or about 47.5 GB with
  Gemma 4 26B as well; a 12 GB phone needs only Gemma, about 43 GB.
  Answering in airplane mode, the app used at most 3.5 GB of memory with the 2B and 6.8 GB with the
  4B, and all 40 test answers finished offline, including 6 research questions with a follow-up
  under each, per model
  ([details](../evaluation.md#142-at-the-bountys-limits-on-an-android-emulator-october-18)).
- **Tested on** a Redmi 13C (6 GB, Android 13) and the emulator above. Not yet tested on a Pixel,
  on GrapheneOS or on a real 8 GB phone.

## Install

1. Download `fieldatlas.apk` (and `fieldatlas.apk.sha256` if you want to check it) below.
   Requires Android 13 or later on an arm64 phone.
2. Open the APK and allow the installation. It installs over 1.4.1 and keeps your data.
3. In setup, download the model it offers (Qwen3.5 2B, 1.4 GB, on a 4 GB phone; Qwen3.5 4B, 3.0 GB,
   on a 6 or 8 GB phone; Gemma 4 26B, 10.5 GB, on a 12 GB phone), and later any collections you
   want and, if you like, the voice.
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
