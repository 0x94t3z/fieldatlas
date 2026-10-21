# Field Atlas 1.4.0: full change notes

A public release that takes on the three weaknesses Vitalik named on 26 September: speed,
difficult questions, and "Tell me the best vegan restaurants in [city I am currently in]". It also
brings one Encyclopedia download of English and Simple English Wikipedia searched by a native
engine on the phone, cited explanations checked against their sources, a new Biology & longevity
collection, and downloads from Hugging Face at pinned revisions. On 60 questions written after the
build was frozen and graded blind against internet search plus a frontier model, Field Atlas with
its default model reaches 34% of the reference answers, with 1.02 factual errors per answer: below
the bounty's "more than 50%" bar. The optional Qwen3.5 4B, offered on 8 GB phones, reaches 35% on the 55 questions without a phone position (the 2B 32% on those) with about half the factual errors. The honest reading is in the evaluation; the
looked-at sets score higher and are reported as development data.
Notes for the previous version: [1.3.0](field-atlas-1.3.0.md).

## Vitalik's three points

- **"The best vegan restaurants in the city I am currently in."** Asked near the phone, the answer
  now names the city the phone is in and ranks that city's places rather than listing the nearest
  six: fully vegan places first, then those the city's Wikivoyage guide also recommends, sit-down
  restaurants before cafés and fast food, independent places before chains. There are no ratings
  offline, and the answer says so. Each place has its distance, compass direction, address or
  coordinates and the date the map recorded it. The location comes from Android's own fused, GPS
  and network providers, without Google Play Services; a fix older than ten minutes is used only
  with its age stated. Asked from Berlin and from Los Angeles on questions written after the change,
  it scored 7.5 and 7 out of 10 against 9 for the web reference; on the release build's 60 fresh questions, from Sydney and Bangkok, it scored 5 and 6 out of 10 against 7 and 8 for the web reference.
- **Difficult questions.** The explanation, comparison and synthesis questions a 1B model breaks on are where Field Atlas is
  weakest. On the release build's 60 fresh questions the default 2B reached 30% of the reference on
  explanations, 23% on comparisons and 31% on synthesis (the optional 4B: 36%, 23% and 43%). What helped: the
  fitting section of the English Wikipedia article quoted as the opening, each subject of a
  comparison read, and the model's inventions removed (warnings a guide never gives, medicine and
  recipe amounts). What did not: the 2B's own prose, which still contradicts the passage it was given.
- **Speed.** On a Redmi 13C, every answer after the first starts about 20 seconds sooner: the
  model keeps the instructions it has already read (heat pump question 38.9 s to 18.5 s to the
  first word). Answers that need no model start at once even while it loads: near-me and "best
  in the city I'm in", "Where am I?", phrases from the phrasebooks, emergency guides and numbers,
  and calculations. On the Redmi 13C in this release's checks, in airplane mode: a phrasebook line 4 s, a fuel-cost
  split 0 s, the heat-stroke guide with the model's steps 40–52 s, an explanation quoting the English
  article's section 1 m 45 s, "Tell me about Japan" 2 m 24 s, the best vegan restaurants in Berlin
  3 s ([device record](../evidence/physical/redmi-13c-android13/README.md)).

## Changes

- **Optional Qwen3.5 4B on 8 GB phones.** Library offers Qwen3.5 4B (3.0 GB) on phones that report
  at least 7,000,000,000 bytes of memory. Graded blind on 96 questions it reached 57% of the
  reference against 48% for the 2B, with about half the factual errors, but it answers about 2.5
  times slower. It needs about 3.5 GB resident: on a 6 GB Redmi 13C Android stopped the app as it
  loaded, so 6 GB phones are not offered it, and no 8 GB phone has run it yet. If the app ends
  while loading a model other than the 2B, the next load uses the 2B and Library says why.
- **One Encyclopedia.** The Encyclopedia is now a single download that combines Simple English
  Wikipedia, English Wikipedia's 49,822 vital articles by section and English Wikipedia from the
  4 October 2026 dump: 8.39 million passages, 10.64 GB to download and 24.35 GB installed. It
  replaces the separate Encyclopedia and Encyclopedia in depth collections. The phone searches it
  with a native engine (Leviathan, built into the app from pinned source) beside the keyword index;
  in a twelve-question pilot on a Redmi 13C over a 1.39 GB test corpus the median search-and-answer
  time fell from 18 s to 6 s, and more answers were complete and supported.
  When an explanation's subject is found ("Heat pump", "Jet lag", "Onion"), the app reads that article's sections and
  adds the ones that fit the question ("Operation" for how, "Causes" for why, "Eye irritation" for
  why onions make you cry), within a reading budget sized to the phone's speed, and quotes the
  fitting section as the opening instead of the definition. Graded blind side by side with the same
  build without it: the fork's 31 lookup questions went from 45% to 55% of the reference, our own
  from 57% to 59%, with fewer factual errors. A Simple English one-line stub ("A hurricane is a
  tropical cyclone") is followed to the full article; a casual lookup ("tell me about Georgia, the
  country") reads its article whatever collections are installed.
- **Cited explanations.** Where saved passages answer a question, the model writes an explanation
  that cites them; before it is shown, the model reviews whether each claim is supported by the
  passage it cites, with one bounded repair. A draft the review does not confirm is never shown:
  the question continues to the guide quote, phrasebook line, saved excerpts or source lead with a
  model explanation labelled as unverified, and a request for saved sources only gets the gap
  stated. A first version withheld every unconfirmed answer and scored 21% on fresh questions; the
  fallback took it to 34% (see Measured). The review is the same small model, not independent
  fact-checking.
- **Biology & longevity.** The fork's 1.2.0 biology download (1.5 GB, PubMed abstracts and the
  Fight Aging! archive under a "public-data" label) is replaced by an 8 MB collection whose sources
  were checked one by one: OpenStax Biology 2e (CC BY 4.0), MedlinePlus health topic summaries (US
  government work), Human Ageing Genomic Resources records (CC BY 3.0) and six open-access ageing
  reviews (CC BY 4.0), with an ATTRIBUTION.txt that collection details show offline. On 30 held-out
  biology questions graded blind it gave about twice as many correct answers as 1.2.0 and none
  flagged as harmful. A phone that has 1.2.0 keeps it, switched off; Library offers the new edition.
- **Downloads.** Every collection now downloads from Hugging Face at a pinned revision; each
  public copy was streamed and hashed in full before the catalog pointed to it, and a test keeps
  every link pinned. Download sources in Library and setup let a user choose another verified
  mirror. On a phone whose connection dropped four times, the biology download resumed each time and
  installed after its checksum passed.
- **Setup, Library and More.** The screens were reworked for public release: one knowledge order
  in setup and Library, one icon style per subject, licences shown by name ("CC BY 4.0, US
  government work"), a fast-start switch for the model, and redesigned offline-readiness and
  app-and-device sheets.
- **Comparisons and choices.** "DEET vs picaridin, which is better?", "ibuprofen or paracetamol?"
  and "what's the difference between a hurricane, a typhoon and a cyclone" now read each named
  subject's article (up to three), and a choice keeps the model's answer under the quoted guide
  section instead of a quote alone. Casual spelling ("whats", "im") routes like the spelled-out
  form.
- **Places in a named city.** A place search stays inside the city the question names
  ("Mexico City" also covers "Mexico City/Centro"), the title boosts that ranked "Cookstown — TIME"
  for "first time in Mexico City" are off for listing packs, and a listing counts as relevant only
  by its own name, type, cuisine, description or address, never by a template label. The
  Essentials search in a city fell from 14–40 s to under a second. Words no listing nearby carries
  ("hungry", "cheap") no longer empty a whole area: the places that fit the rest are listed and the
  gap is stated; "not Euronet" leaves that brand out; "a proper bank one" puts banks' machines first;
  a place without an address shows its coordinates; the "best" ordering claims the travel guide only
  when a listed place is in it.
- **Travel guides by section.** The Travel guides collection is now Wikivoyage's guides read by
  section ("Japan — Buy > Cash", "Hanoi — Stay safe > Traffic"), with travel topics (Passport, Jet
  lag) and phrasebooks. A question naming a place and a practical topic quotes that place's own
  section: a visa from "Entry requirements", a metro ticket from "By public transport", a SIM card
  from "Mobile phones", a budget from "Costs". A beach or district without a guide is read through
  the place its overview names, and so is a place whose guide lacks the section asked ("Flying into
  Bali, visa on arrival?" reads Indonesia's entry requirements). A journey "Lisbon to Porto by
  train" reads the country's rail advice; "<city> airport" adds the airport's own guide; a transit
  card question finds the fares section; a safety question quotes the safety section, never the
  metro timetable, and "Is Rio safe for a solo traveller?" opens with the guide's own verdict; a
  dress-code question for temples quotes both what to wear and what not to do; a budget question
  quotes only a section about costs. A question that names no place reads the topic guide for its
  topic ("pay in euros or my home currency?" reads Money's section on dynamic currency conversion;
  "why do my ears pop on planes?" reads Flying and health), as a lead above the model.
- **Phrases.** "How do I say thank you in Japanese?" or "Basic phrases for Vietnam" is answered
  with the phrasebook's own lines, in the script with the pronunciation it gives. A line is the
  whole answer only when it says each asked word and nothing else ("can you help me" is not
  "Help!"; "check in" is not the bill); otherwise it leads and the model adds the rest. When the
  phrasebook has only a related line ("I'm a vegetarian" for "I'm vegan"), the answer says so.
- **"Where am I?"** names the town from the addresses of the nearest mapped places, gives the
  coordinates and the three closest mapped places with distance and direction.
- **Emergencies.** A jellyfish sting that "burns" no longer opens the burns guide, whose
  cold-water rinse can fire remaining stingers; an embedded tick and frostbite told by its signs
  in the cold ("two fingers white, hard and numb after skiing") open their guides. A carbon monoxide guide (42 guides in all), and the police, ambulance and fire
  numbers of 242 countries and territories, read from a bundled table, cited. Everyday accounts
  ("someone fainted in the heat", "broke her ankle on the trail", "swallowed a button battery",
  "what is the fastest way to stop a seizure?") reach their guide; a question that only asks to
  explain a hazard ("what causes tsunamis?") is researched instead. The guide section that fits the
  person's signs leads: "collapsed in the heat, confused, stopped sweating" opens with Heat Stroke
  (call emergency services, do not give fluids), not the guide's introduction or heat cramps; a
  long guide quotes the paragraph for the case; sentences pointing to a website are left out of
  the quote. A "do not" the guide never gives is removed from the model's steps, and a home-remedy
  recipe amount the sources do not state is removed; the model is asked to answer a yes/no or
  "when should I worry" question first. No breathing, no pulse or unresponsive opens CPR (or
  drowning in water) whatever else is mentioned; hives after peanuts stays with anaphylaxis.
- **Calculations on the phone.** Fuel economy and range, a trip's fuel cost split among the
  people, walking or driving time against the daylight left, hiking time, money at a given rate
  with a tip, changing money at a quoted rate less a commission or flat fee ("25,400 dong per
  dollar, 3% commission"), water for a group over the days given ("until the end of day 2"),
  power-bank watt-hours and the number of phone charges at a stated efficiency, temperatures, the time in another city, a landing time across time zones
  when the offsets are given, recipes, boiling point at altitude, the time between two clock times,
  a clock time plus or minus a duration ("when do I leave to arrive by 9:00?"), and a price per
  night times the nights with a discount or tax. Every number in the question must be used and the
  asking clause must name the calculation, or the result is a lead for the model rather than the
  answer; a head count without a unit ("3 of us"), a charge without its percentage, a trip that
  names places (time zones) or an ambiguous figure returns no calculation rather than a wrong one.
- **Cryptography & Ethereum collection.** 22,805 passages from the EIPs, ERCs and consensus
  specifications, NIST's post-quantum standards and 123 Wikipedia articles, with vector search. A
  question naming a proposal quotes its own summary.
- **Model answers.** The model builds on the saved passages and adds its own knowledge only for
  basic facts; an amount of medicine never appears in its text; it no longer remarks that the
  person is offline, and never sends them online.
- **Places near you, in walking words.** "A pharmacy I can walk to from here" and "any museum
  within walking distance of me" use the phone's position. Museums and sights are travel-guide
  listings without map positions, so a nearby request for them is answered from the listings of the
  city the phone is in. The note on words no listing carries no longer reports trip words or times.
- **Places.** A place stays in its own city when its address names a large city nearby (the
  5 October map rebuilds); a restaurant's branches are listed once; opening hours are read from
  the OpenStreetMap specification, so a split shift ("Mo-Sa 09:00-12:00, Mo-Fr 14:00-19:00") is
  open in both shifts.
- **Library.** A collection installed in a newer version switches the older one off; a paused
  download of a replaced version is removed, while one whose catalog entry only changed its wording
  or address resumes. Model downloads resume, and the 4B's download, pause and errors show on its
  own card.
- **First screen.** The examples are paths that answer offline at once: the best vegan
  restaurants in the city you're in, an explanation that opens with its encyclopedia article, an
  emergency, and a phrase.
- **Licences.** More → Licences shows every component and data source with its licence, and the
  full licence texts the app ships. The LICENSE file is now the verbatim Apache-2.0 text, a NOTICE
  file names the copyright holders, and the README credits the vbuterin/fieldatlas fork merged in
  4967a49.
- **Opening hours.** The opening-hours reader was rewritten from the OpenStreetMap `opening_hours`
  specification, with a tokenizer and rules resolved per weekday.
- **Fixes.** A near-me question no longer waits the full 45 seconds when no location provider
  can get a fix. "Ohm's law" and other names with a possessive are found by title. A paused
  download of a replaced collection version no longer holds its space. "Vegan bar near me" in
  Berlin finds the five bars within 5 km again (a category lookup had read only the nearest 800
  points). Short search terms match their own forms ("ears" no longer finds earthquakes, "tides"
  finds "tide"). A long emergency guide no longer stops the app on the phone (a blank title in its
  paragraph ranking), and two calculator patterns that Android's regex engine rejects were
  rewritten.

## Measured

Blind grading against a frontier model's web-researched answers, each answer on its own, graded
twice ([method](../evaluation.md#140-release-build-on-fresh-questions-october-13); every answer and
grade in [results-1.4.0](../fieldtest/results-1.4.0/)). Configuration: this build's code, Qwen3.5 2B,
every collection the app offers (byte-identical to the published files), the phone's native search,
on the desktop pipeline.

| Questions | Qwen3.5 2B (default) | Factual errors per answer |
| --- | --- | --- |
| 60 fresh questions, written after this build was frozen (set 6) | **34%** of the reference | 1.02 |
| 60 fresh questions measured on the previous fix (set 5, now development data) | 34% | 0.83 |
| 60 fresh questions on the first 1.4.0 candidate (set 4, now development data) | 21% | 0.41 |
| The 7 October candidate on its own 60 fresh questions (set 3) | 38% | 1.98 |
| The optional Qwen3.5 4B, set 6 without the five phone-position questions | 35% (the 2B: 32% on the same 55) | 0.50 |
By category on set 6, the 2B: Vitalik's question 73%, casual lookups 57%, practical travel 35%,
places 32%, synthesis 31%, explanation 30%, reasoning with numbers 27%, emergencies 26%,
comparison 23%, cryptography 18%. The 7 October candidate scored a little higher on its own fresh
set with about twice the factual errors; on the same set 4 questions (development data) it scored
32% against 39% for this release's answer path. References and graders are frontier models of one family, so these are
development measures, not an independent score.

## Verification

1,291 JVM tests run with none failing (54 opt-in tests, among them the desktop runs against the real collections, are skipped); 78
script and 246 tool tests pass. The release build was installed over earlier 1.4.0 test builds on a
Redmi 13C with its data kept, the model and collections in place, and checked in airplane mode with
Wi-Fi off: a cited answer took 2 m 47 s and a labelled model explanation 3 m 43 s from the question
to the full answer, and "what are senolytics?" was answered from the new biology collection in
43 s. Through setup, the phone downloaded and installed the model and six collections (4.1 GB in
about 35 minutes over USB tethering) after each checksum passed, and the biology collection, on a
weak Wi-Fi earlier, resumed after four dropped connections. Details in
[verification.md](../verification.md#v140-release-build-october-13).

## Known limitations

- **Below the bounty's bar on new questions.** On 60 fresh questions the default 2B reaches 34% of
  the reference. The optional 4B reaches 35% on the 55 questions without a phone position, with about half the 2B's factual errors, and answers about 2.5 times slower. Cryptography (18%), comparisons (23%), emergencies (26%) and reasoning
  with numbers (27%) are weakest: a wording the guide matcher, guide-section rules or calculators do
  not cover falls to the 2B, which gets arithmetic, dates and consequences wrong. Where the app
  answers without the model (lookups, places near the phone, emergency guides and numbers, phrases,
  calculations it recognises) it is far better than the model's own text.
- **Answers take minutes.** A cited explanation is checked before it is shown, so it appears whole
  rather than word by word; on a Redmi 13C answers that use the model took 2–4 minutes. Answers
  the app composes itself (near-me places, guides, phrases, calculations) take seconds.
- **The review is not fact-checking.** The same 2B model reviews its own citations; it rejects
  many good drafts and can pass a wrong one. Unconfirmed answers are labelled as unverified.
- **Travel-guide quotes** are chosen by section and wording, and can quote a loosely related
  paragraph (a taxi section for a bus question) above the model's labelled explanation.
- **Hardware.** Tested on a Redmi 13C (6 GB, Android 13) and an Infinix X6840 (4 GB, Android 16).
  Not yet run on a Pixel, on GrapheneOS, or on any 8 GB phone, so the optional 4B has not run on a
  phone that is offered it.
- **Storage.** The Encyclopedia needs 10.64 GB to download and 24.35 GB installed, and room for both
  while it installs; with every collection and both models the total stays under the 50 GB budget.
- **Map data.** Places tagged fully vegan that serve dairy, and places filed under a nearby city,
  can still appear; the tags are as mapped. Hours and menus can be out of date. Museums and sights
  near the phone are listed by city, not by distance.
- **Emergency guides** are information, not training, and many are written for the United States;
  there is no bundled guide for jellyfish or sea-urchin stings; severe bleeding has no bundled
  tourniquet guidance; the choking and drowning guides are Wikipedia text. See
  [Emergency knowledge](../emergency.md#limits-you-should-know).
- **Not checked for this release:** network traffic during research, the Encyclopedia's full
  download and install on a phone (it was imported), and a clean install of the public APK
  following only the README.

## Installation

Download `fieldatlas.apk` and check `fieldatlas.apk.sha256`. Requires Android 13+ and an arm64
device. It installs over 1.3.0 with data kept. Models and collections are downloaded in the app
(or imported as `.fapack`); queries run locally after setup. Downloads and catalog refresh need a
connection. The emergency guides and numbers ship inside the app.

App version: 1.4.0 (19). GitHub channel: release (`v1.4.0`).

## Release assets

- App release `v1.4.0`: `fieldatlas.apk`, `fieldatlas.apk.sha256`.
- Collections are downloaded from the [Field Atlas Knowledge Packs](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge)
  and [Field Atlas Encyclopedia](https://huggingface.co/datasets/0x94t3z/fieldatlas-encyclopedia)
  datasets at the pinned revisions in [DATASETS.md](../../DATASETS.md#collections-the-app-offers), each
  checked against its size and SHA-256 before it is installed.
