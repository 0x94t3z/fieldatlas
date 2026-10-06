# Field Atlas 1.4.0

A public release that takes on the three weaknesses Vitalik named on 26 September: speed,
difficult questions, and "Tell me the best vegan restaurants in [city I am currently in]". On 60
questions written after the last change and graded blind against internet search plus a frontier
model, Field Atlas with its default model reaches 38% of the reference answers: below the bounty's "more than 50%" bar with the default model, and 52% with the optional 4B on the questions without a phone position. The honest reading is in the evaluation; the earlier, looked-at sets score higher and are reported as development data.
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
  it scored 7.5 and 7 out of 10 against 9 for the web reference; on the 60 fresh questions, from two more cities, it scored 6.0 and 7.0 out of 10 against 8.0 and 8.0 for the web reference.
- **Difficult questions.** The explanation, comparison and synthesis questions a 1B model breaks on are where Field Atlas is
  weakest. On the 60 fresh questions the default 2B reached 51% of the reference on explanations,
  24% on comparisons and 30% on synthesis (the optional 4B: 64%, 55% and 39%). What helped: the
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
- **Encyclopedia in depth.** A new optional collection: 49,822 English Wikipedia vital articles
  by section (1.4 GB). It is never searched. When an explanation's subject is found in the
  Encyclopedia ("Heat pump", "Jet lag", "Onion"), the app reads that article's sections here and
  adds the ones that fit the question ("Operation" for how, "Causes" for why, "Eye irritation" for
  why onions make you cry), within a reading budget sized to the phone's speed, and quotes the
  fitting section as the opening instead of the definition. Graded blind side by side with the same
  build without it: the fork's 31 lookup questions went from 45% to 55% of the reference, our own
  from 57% to 59%, with fewer factual errors. A Simple English one-line stub ("A hurricane is a
  tropical cyclone") is followed to the full article; a casual lookup ("tell me about Georgia, the
  country") reads its article whatever collections are installed.
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
- **Emergencies.** A carbon monoxide guide (42 guides in all), and the police, ambulance and fire
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
  power-bank watt-hours, temperatures, the time in another city, a landing time across time zones
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
- **Provenance fix.** 1.3.0's opening-hours reader followed another submission's MIT-licensed
  parser closely, and some unit tests used its question texts; both were replaced (see the
  [release audit](../compliance/release-audit.md#provenance-audit-of-other-submissions-material-5-october-2026)).
- **Fixes.** A near-me question no longer waits the full 45 seconds when no location provider
  can get a fix. "Ohm's law" and other names with a possessive are found by title. A paused
  download of a replaced collection version no longer holds its space. "Vegan bar near me" in
  Berlin finds the five bars within 5 km again (a category lookup had read only the nearest 800
  points). Short search terms match their own forms ("ears" no longer finds earthquakes, "tides"
  finds "tide"). A long emergency guide no longer stops the app on the phone (a blank title in its
  paragraph ranking), and two calculator patterns that Android's regex engine rejects were
  rewritten.

## Measured

Blind grading against a frontier model's web-researched answers, each answer on its own, two
graders per answer ([method](../evaluation.md#140-on-60-new-held-out-questions-october-7); every answer
and grade in [results-1.4.0](../fieldtest/results-1.4.0/)):

| Questions | Qwen3.5 2B (default) | Qwen3.5 4B (optional, 8 GB phones) |
| --- | --- | --- |
| 60 held-out questions written after the last change | 38% of the reference | 52% on the 55 without a phone position |
| The 48 questions of 6 October (diagnosed, now development data) | 58% (34% before the fixes) | – |
| Our 61 development questions | 61% | – |
| The fork's 31 lookup questions (development) | 54% | – |
| The 42 earlier held-out questions (development) | 54% | – |

By category on the fresh questions, the 2B: Vitalik's question 81%, casual lookups 64%, explanation 51%,
emergencies 47%, places 45%, synthesis 30%, cryptography 26%, comparison 24%, practical travel 20%,
reasoning with numbers 14%. References and graders are frontier models of one family, so these are
development measures, not an independent score.

## Verification

799 JVM tests pass (16 opt-in desktop tests run separately against the real collections); 147 Python
tests pass; an instrumentation test on the Redmi 13C compiles every regular-expression literal of the
app with Android's engine and runs the calculators there. The build was installed over 1.3.0 on a
Redmi 13C with its data kept, the five 1.4.0 collections imported, and checked in airplane mode with
Wi-Fi and mobile data off ([device record](../evidence/physical/redmi-13c-android13/README.md)).
Details in [verification.md](../verification.md#v140-october-67).

## Known limitations

- **Below the bounty's bar on new questions with the default model.** On 60 fresh questions the default 2B reaches 38% of the reference, the optional 4B 52% on the 55
  without a phone position. Practical travel (20%) and reasoning with numbers (14%) are weakest: a
  wording the guide-section rules or the calculators do not cover falls to the 2B, which gets
  arithmetic, dates and consequences wrong, and a guide section read for the wrong part of a
  question can still be quoted. Where the app answers without the model (lookups, places near the
  phone and in a named city, emergency guides and numbers, phrases, calculations it recognises) it
  is far better than the model's own text.
- **Hardware.** Tested on a Redmi 13C (6 GB, Android 13) and an Infinix X6840 (4 GB, Android 16).
  Not yet run on a Pixel, on GrapheneOS, or on any 8 GB phone, so the optional 4B has not run on a
  phone that is offered it.
- **Map data.** Places tagged fully vegan that serve dairy, and places filed under a nearby city,
  can still appear; the tags are as mapped. Hours and menus can be out of date.
- **Emergency guides** are information, not training, and many are written for the United States;
  severe bleeding has no bundled tourniquet guidance; the choking and drowning guides are Wikipedia
  text. See [Emergency knowledge](../emergency.md#limits-you-should-know).
- **Not checked for this release:** network traffic during research, the in-app model download on a
  phone, and a clean install of the public APK following only the README.

## Installation

Download `fieldatlas.apk` and check `fieldatlas.apk.sha256`. Requires Android 13+ and an arm64
device. It installs over 1.3.0 with data kept. Models and collections are downloaded in the app
(or imported as `.fapack`); queries run locally after setup. Downloads and catalog refresh need a
connection. The emergency guides and numbers ship inside the app.

App version: 1.4.0 (18). GitHub channel: release (`v1.4.0`).

## Release assets

- App release `v1.4.0`: `fieldatlas.apk`, `fieldatlas.apk.sha256`.
- Collections, each with its `.fapack`, `SHA256SUMS` and source lock:
  `knowledge-wikivoyage-guides-2026.09.01`, `knowledge-vegan-places-2026.10.05`,
  `knowledge-essentials-2026.10.05`, `knowledge-crypto-ethereum-2026.10.05` and the new
  `knowledge-wikipedia-en-vital-2026.10.06`. The Encyclopedia, Travel places and Biology
  collections are unchanged.
