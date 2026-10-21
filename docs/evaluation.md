# Evaluation

## Good answers, and storage with everything installed (October 21)

From here on, results lead with two counts: how many questions get a **good answer**, one that the
blind graders score 7 or more out of 10, and how many **wrong details** the graders note per answer.
The sections below this one also give each setup's total score as a share of the reference
answers' total, as it was recorded at the time.

| Set 37, 40 questions, two rounds of eight graders | Good answers | Wrong details per answer |
| --- | ---: | ---: |
| Qwen3.5 2B, cites the passages (4 GB phones) | 2 | 0.71 |
| Qwen3.5 4B, from its own knowledge (6 and 8 GB) | 8 | 1.94 |
| Gemma 4 26B, reasons first (12 GB) | 27 | 0.44 |

| Same model and questions, only what it could read changed | Good answers before → after | Wrong details per answer |
| --- | ---: | ---: |
| Set 30, 2B, Biology collection of 13 October → 18 October | 2 → 7 of 40 | 0.60 → 0.47 |
| Set 30, 4B, the same | 4 → 6 of 40 | 0.15 → 0.15 |
| Set 42 long-tail, 4B, from memory → reading the Encyclopedia | 2 → 7 of 40 | 3.27 → 2.42 |

A reasoning model is now given the instruction without the answer layout the other models use, and
an answer its reasoning cuts short is asked for again with reasoning off; Gemma's set 37 answers were
written that way.

**Storage with everything installed.** On the 12 GB Android emulator, with Qwen3.5 2B, Qwen3.5 4B,
Gemma 4 26B, all seven collections and the natural voice installed through the app, Android's own
Settings › Apps › Field Atlas › Storage showed 47.58 GB: app 173 MB, user data 47.40 GB, cache
618 kB. The Encyclopedia is a 10.64 GB download that unpacks to 24.35 GB, and for a while it holds
both; installed after all three models it would pass 50 GB, so the app refuses it before
downloading, and says that a model can be removed and added back afterwards.

## Reading the sources for subjects few readers meet (October 20)

A model that answers from memory knows the Eiffel Tower and invents the Pantelegraph. On the earlier sets the 4B's
memory beat reading passages for every mainstream subject, so the question was never "memory or sources" but
"which subjects". The collections now draw that line themselves. A question's subject is read from the saved
sources, as numbered notes inside the model's own answer that it is asked to take names, dates and figures from
and to cite as [S1], when the subject is held only by the full English encyclopedia (not the Simple English
edition, whose quarter of a million articles are the subjects most readers meet) and is named in at most 400 of
the saved passages — "cast iron" and "sky blue" are named in thousands, "Pantelegraph" in eight, "Hanaoka Seishū"
in five — or when a specialist collection (biology, cryptography) leads the passages. The count comes from the
full-text index in a few milliseconds; the subject must be named in the question itself (a lookup that lands on
"Goods" for a honeybee question is not a subject), a rare subject counts even beside a common one the question
also names, and a rare article the search itself led with counts when no subject was looked up. Everything else,
travel guides included, stays with the model's memory; the one line under the answer then reads "From the model's
knowledge and your saved sources; N sentences cite them."

Measured on a sealed set of 60 written blind ([set 42](fieldtest/heldout42.jsonl), SHA-256
`03b2adef8d8e8bf5fc0a00551d50352db90040388797f42a7fb7e76806915df0`): 40 long-tail subjects that have an English
Wikipedia article (the Caste War of Yucatán, the Huia, Borgward, alkaptonuria, Hertha Ayrton) and 20 mainstream
controls, web references by five agents, both arms (memory as shipped, router) and the reference graded blind by
nine graders, one grader per answer, two rounds. The router read 16 of the 40 long-tail questions and none of the
20 controls; 14 of the 40 subjects are not in our 470,000-article edition at all, and the rest were mainstream
enough by the count. Round b, the shipped rules: long-tail 22.3% to 35.6% of the reference (errors noted per
answer 3.27 to 2.42; by question 17 better, 7 worse, 16 the same, p=0.06); on the 16 questions it read, 14
better and 2 worse, mean 2.4 to 5.7 of 10, errors 2.9 to 1.2; the 20 controls got identical answers (88.0% and
85.5% are the two graders' noise). Over the whole set, 44.8% to 52.6%. Round a, with stricter rules that read 10,
had shown the same direction (22.1% to 26.4%; on the 10 read, 8 better, 2 worse). Per-question records:
[results-1.4.2/heldout42-router.jsonl](fieldtest/results-1.4.2/heldout42-router.jsonl). On set 38 (mainstream)
the shipped rules read nothing. Cost: three passages of up to 700 characters in the prompt, about 40 s more on the
Redmi, only for the subjects it reads.

## One shape for every answer, sources at the foot (October 20)

A run-through on the Redmi 13C (6 GB, Qwen3.5 4B, offline) showed what the owner then asked to change: a from-memory
answer opened with a stack of "Saved passages" cards above the explanation (for a taxi-fare sum, three passages on
Italian prescription costs filled the screen before the answer), and the same passages were quoted again as "Saved
excerpts" inside it. Now every answer written from the model's own knowledge takes one shape, asked for in the
prompt: a short paragraph that answers outright, then **Key facts:** as bullets with a bold label each, then
**Caveats:** only when something varies or may have changed; a bold label the model runs on from a sentence is put on
its own line. The explanation comes first on the screen, whatever route wrote it; the passages found are the
Sources list under it, as the cited route already showed them, with "N other retrieved passages" folded away.
Instead of quoting them, the app marks a sentence of the answer with a passage's number where that passage states
the same figure, or the same year of the same event, as the sentence (the fact-check's own matching, run for
agreement; one mark per sentence, the lowest-numbered passage), and a line under the answer says what a mark
means. Where a passage gives a different figure the note from the fact-check stays. The pill reads "From the
model's knowledge · N passages agree". The model-only route (nothing found) takes the same shape; the cited route
for 4 GB phones is unchanged. The "Model explanation / Not verified" header inside the answer, the badge
and the pill row are gone from a from-memory answer, which the owner found cluttered: the answer starts under Read
aloud, and one line at its foot says where it came from and what was checked ("From the model's knowledge; 2
figures match your sources", "… not checked against your sources", "… nothing saved on this phone covers it");
a figure a passage gives differently is still quoted under it. The time moved into Answer details.

Measured on set 38, desktop 4B, the same forty questions and web references as the rounds above, graded blind by
eight graders who saw the answers without their marks or notes: the shaped answers carry a Key facts line in 36
of 40 and a Caveats line in 36, a mark in 6 answers (9 marks) and a fact-check note in 3; median length 170 to
180 words, median time on the Mac 12.6 to 14.5 s. Against the earlier 4B rounds question by question: better on
12, worse on 9, the same on 19 (within half a point), mean +0.4 points, sign test p=0.66; errors noted per answer
2.21 to 2.55, the bullets adding a few more claims and with them a few more wrong details: the shape changes how an
answer reads, not what it gets right. Shipped with the run-through's other fixes:
a priced question with a rate ("12 euros plus 2 euros per km, how much is 15 km") is worked from its own figures
even on a phone whose travel packs have a page about taxis, and a heading line of a medicine list ("Medicines to
take at home:") is no longer quoted as a medicine line.

**From the rest of the run-through.** A "near me" question on a phone whose Location is on but has no fix (indoors,
airplane mode) waited 25 s and then said to turn Location on. The GPS wait is now 15 s, and the reply says what was
the matter: no fix yet (ask again shortly, step outside, or name the city), Location off, or permission not given.
The readiness banner on a phone with no offline voice no longer says "voice" runs on it. The progress line "Usually
about 3 min" was the median of the last five answers while the last ones took 3.5 to 4.5 min; it now gives the range
of the inner three ("Usually 3–4 min"). And a passage that spans the years a thing began and ended ("from 1961 to
1989"), or says it was taken down, now backs the answer's start and end dates, so the marks under a from-memory
answer fire a little more often: replayed over set 38's forty 4B answers, one more answer gains a mark ("Concorde
stopped flying in 2003", 6 to 7 of 40) and the three fact-check notes stay the same.

## Set 38, and passages as a check on figures (October 20)

A second sealed set of 40 questions in set 37's category mix (set 38, [questions](fieldtest/heldout38.jsonl),
SHA-256 `2d9243351eeb1ff0722b1076c1ecf82de2820fd75dba90edfceab0f7650e8b23`), written by a model that read only
the earlier question files, answered on the desktop pipeline by the two setups phones get, with web-and-AI
references and two blind rounds of eight graders
([every answer and grade](fieldtest/results-1.4.2/heldout38-knowledge.jsonl)):

| Set 38, 40 questions | Share of the reference | Rounds | Factual errors per answer |
| --- | ---: | ---: | ---: |
| Qwen3.5 4B, own knowledge (6 and 8 GB phones) | 38.5% | 39.0% / 38.1% | 2.21 |
| Gemma 4 26B, reasoning first (12 GB phones) | **74.1%** | 74.8% / 73.5% | 0.24 |

Set 37 gave 41.8% and 70.2%, so the two sets agree. By category, Gemma scores 84-97% of the reference on
facts, explanations, comparisons, travel and reasoning, and 51% on health and 28% on emergencies; the 4B
3% on emergencies. The emergency and health losses come from the shared routing, not the models: of the ten
questions in those categories, both models scored 0 on four because the question opened the wrong bundled
guide ("food poisoning" opened the poisoning guide for a question about reheated rice; a blood pressure
reading opened CPR through the text ranking; a diver's dizziness and tingling arm opened the stroke guide;
oil on fire in a frying pan opened the burns guide) and on one because "90 minutes from the nearest
hospital", said of a woman in labour, was read as a request to find one and failed on the phone's
location. Two emergencies had no guide at all (an avalanche burial and a smoke-filled hotel corridor): Gemma
answered them acceptably, the 4B dangerously. "Take her to a doctor" sent a child's fever down the venue
route, where the 4B refused.

**Routing fixes (same day).** A question about food poisoning or a stomach bug now opens no guide (the
poison-control guide is for swallowed chemicals and overdoses) unless someone has stopped breathing;
the CPR guide can no longer be chosen by the text ranking, only by its own rule for a person who is not
breathing, and the stroke guide needs a stroke sign (a face, speech, an arm, one side) in the message
before the text ranking may pick it; a diver's signs after a fast ascent rule the stroke guide out; a pan,
stove, bin or appliance on fire with nobody burned rules the burns guide out; "what's a normal …", "is that
high", "difference between" and study words are not first aid; and "90 minutes from the nearest
hospital" or "take her to a doctor" no longer make a question a search for a place. Set 38's six cases
each have a test beside the case the guide is still for. The 320 labelled questions still score 217 with
no unsafe guide; of the 1,207-question harness, 5 picks changed, all to the right guide. On a further
sealed set of 60 routing questions written blind
([set 39](fieldtest/routing39.jsonl), SHA-256
`dffae158327f4fe4dc50434fe8078352d513f11db91b59a9b8e6e05b27feb912`: 25 emergencies a bundled guide
covers, 15 none covers, 20 look-alikes), the guide chosen is the same before and after for all 60, and the
two look-alikes that had been sent to the place search are no longer. The same set shows what is still
open: the matcher opens the right guide for 16 of the 25 covered emergencies (a toddler "grabbing at his
throat, can't make a sound, going blue" opens nothing, nor does "one side of her face has slid down and
she's talking gibberish", a funnel cloud with sirens, or a sea that has drawn back after an earthquake),
opens no guide for 12 of the 15 uncovered ones and 19 of the 20 look-alikes, and opens an unsafe guide
four times (a heavy nosebleed gets the tourniquet guide; hyperventilation and a man pinned under a
shelving unit get the choking guide; "how long after a heart attack is it ok to fly" gets the heart
attack guide). Those were the next fixes.

**Second routing pass (same day).** Signs told as people see them, in whatever inflection, now name a guide
like one of its own phrases: grabbing or clutching at the throat and "can't make a sound" (choking); a face
that has slid, dropped or sagged, or "talking gibberish" (stroke, unless the person is diabetic or low
sugar); grey and clammy skin after a crash, a fall or a bleed (shock, but chest, jaw or arm pain with no
injury stays the heart); dragging someone out of a pool and "was under for" (drowning); a pounding headache,
stumbling or walking "like he's drunk" at a height (altitude sickness, still only with a height or mountain
word); a funnel seen, a green sky or a tornado siren (tornado); a sea that has sucked, drawn or pulled back
or a wall of water (tsunami); hair standing up with a storm, a ridge or a summit in the message (lightning);
a child who ate or drank a cigarette, vape liquid, a detergent pod, mouthwash, iron tablets and the like
(poisoning). On the other side, a nosebleed in any wording opens neither bleeding guide nor the head-injury
guide unless the head was hit; hyperventilation, stress and tingling hands rule the choking guide out and
leave the text ranking alone, as a panic attack already did; someone pinned or crushed gets no guide
(nothing bundled covers it, and the choking and fracture guides would have the helper do the wrong thing);
and an emergency placed in the past ("had one in March", "2 years ago") with nothing happening now opens no
guide. Set 39's 13 failures each have a test beside a neighbour that keeps its guide. The 320 labelled
questions go from 217 to 223 right, still with no unsafe guide; of the 1,207-question harness, 16 picks
changed, all to the right guide or to none where none covers it. Set 39 itself, which these fixes were
written against, is 60 of 60. The blind check is a further sealed set of 60 written the same way
([set 40](fieldtest/routing40.jsonl), SHA-256
`27ac7b4d3efe238e3825ab80b77f03777ed863ebcbea69bdc18df2624c7dc06d`: 25 covered emergencies, 15 none
covers, 20 look-alikes). Before this pass it scored 41 of 60 (11 of the 25 covered emergencies, 5 unsafe
guides); the pass as first written scored 42, having opened the shock guide for an elephant on the chest
with grey, clammy skin (a heart attack) and the stroke guide for a sweaty, shaky diabetic talking nonsense.
Those two guards were added afterwards, so set 40 counts as seen for them; with them it scores 44 of 60 (14
of 25 covered, the same 5 unsafe guides as before, nothing lost). What set 40 leaves open, for a further
pass against a fresh blind set: 11 covered emergencies still get no guide (a whole family waking with
headaches and a wobbly dog, "everything shaking, shelves falling", a man slumped at the table not answering,
a seizure told as "whole body jerking, foam at his mouth", "nothing looks familiar" off the trail, a coin
battery gone from a remote, "hair on my arms is standing up", an ankle bent sideways, an orange sky with
ash and flames on the ridge, two puncture holes after something struck a calf in long grass, which opens
the lost guide, and the back of a head hitting a kerb), and 5 unsafe guides remain (a nosebleed "pouring
blood" on blood thinners gets the tourniquet guide; a panic attack with a tight chest gets the heart attack
guide; a metaphorical "storm of Slack messages" gets the lightning guide; a wrist broken last winter gets the
fracture guide; storing water for hurricane season gets the drinking-water guide).

**Third routing pass (same day).** Written against set 40 and checked blind on a further sealed set of 60
([set 41](fieldtest/routing41.jsonl), SHA-256
`832c9615371c40feb120b485ad68f5adc1e770d2fbedf206f933fff1b12d2ef9`, same make-up). The 30-word research
gate now runs after scoring, so a long message that describes the signs as they happen still counts; no breath
felt at the mouth ("I put my ear by his mouth and there's nothing") or a chest not moving is no sign of life;
people typed without apostrophes ("weve", "hes"), "my 18 month old", "mate", "guy on the train" put someone in
the situation. New signs: everything or the whole building shaking (earthquake); the whole body jerking, eyes
rolled back, foam at the mouth, or a fit told as over with the person groggy (seizures); nothing looks familiar,
can't find the trail, "keep walking or stay put" (lost, which now needs a lost sign, so a hike alone no longer
opens it); a limb bent sideways or at an angle, can't put weight on it (fractures); an orange sky, ash falling,
flames on the ridge, smoke getting in (wildfire); two puncture holes or something that struck a calf in long
grass (snakebite); a head that hit the kerb, no helmet, the same question over and over with a blow in the
message (head injury); hair on the arms standing up, trekking poles buzzing (lightning); a coin battery gone
from a remote with a toddler, a bitten detergent pod (poisoning). Guards: a nose "pouring blood"; fast
breathing with tingling hands is hyperventilation and, when it has happened before, rules the heart attack guide
out; "last winter", "in 2024", "two years on" are the past; work talk (inbox, deadlines, Slack), study work
(essay, school poster) and season planning (hurricane season, stockpiling, how often to rotate) open no guide;
stored water is not water to treat. The first draft of these signs misfired five times on the 1,207-question
harness (a detergent pod's foam read as a seizure, "won't answer me properly" as not breathing, a purple finger
after a snakebite and a bad back as fractures, confusion after a fit as a head injury); each is fixed and kept
as a test. Set 40, written against, is 60 of 60. The 320 labelled questions go from 223 to 228 right, still no
unsafe guide; 27 harness picks changed, all to the right guide or to none. Blind set 41: 34 of 60 before, 45
after (covered emergencies 10 to 19 of 25; unsafe guides 9 to 8; nothing lost). Left open on set 41, for a
further pass against a fresh blind set: six covered emergencies get no guide or the wrong one (a hotel
"shaking and swaying" in Kathmandu, a petrol "genny" in the porch with four banging headaches, an angle grinder
through a thigh with "blood pumping out in spurts", shivering "so hard she can't talk" which opens the choking
guide, a thumping headache at 4,300 m which opens the CPR guide, a chewed dishwasher tablet "under the sink"),
a molar abscess gets the head-injury guide from the text ranking, and seven look-alikes still open a guide (a
comedian called Earthquake, a wedding-speech pun about being struck by lightning, a Lightning cable, a startup
"bleeding cash", an ankle broken in 2024 "two years on", an episode of a sitcom with a heart attack, heat
exhaustion against heat stroke for a school poster): things named, quoted or joked about, let through by the
"should I" and "help" wording the matcher trusts.

**Passages as a check on figures.** The 4B and Gemma write ordinary answers from their own knowledge, with
the passages found listed under them unread. A new step compares the numbers, units and years in such an
answer with those passages and, where a passage about the asked subject states a different figure for the
same unit, quotes that sentence with its citation above the excerpts, adding nothing else and changing no
word of the answer (on set 34, handing the model three passages as notes had made its answers three times
longer and wronger). It is deliberately strict: the passage sentence must carry two of the question's subject
words, units are never converted, ranges and "about" are allowed for, a year counts only for the same event,
comparison questions and worked sums are skipped, and shares or counts are compared only when the question
asks for one. Replayed over set 37's 120 from-memory answers and set 38's 80, it fires twice, both on wrong
figures the graders had listed as errors: the 4B's Hubble altitude of "540 miles (870 km)" against the
Encyclopedia's 600 km (the orbit is about 540 km), and the 4B's Grand Canyon depth of "over 8,000 feet"
against the passage's 6,000 feet (about 6,000 feet at the deepest). Gemma's answers drew none. An earlier
draft fired on 12 of those 200 answers, 10 of them wrongly (a year of an arrest read as a year of death, a
list of predator cycles, a reign of 9 years against an age of 19, a 747 figure against an A380 passage); the
rules above were fitted to those cases, so this precision is not a fresh measurement. With one changed answer
in 80, set 38 was graded without a separate arm for the check: the one note falls on an answer graded 4
and 5 of 10 for that very figure.

## Fixes after an outside audit (October 20)

An audit of the 1.4.2 work found seven issues. Each was checked against the code first; what was
found, changed and measured is below. "Desktop" means unit tests and the matcher run on a computer;
"emulator" the 12 GB Android emulator on a Mac; "phone" the Redmi 13C.

**An object stuck in a wound opened the drowning guide.** Held-out case h37-31 ("a broken branch
went into his thigh, its still sticking out. do we pull it out??") matched no guide's keywords, so
the guides' full text was ranked, and the drowning guide's rescue "with a broad branch" and "pull …
out" won. An object told as lodged in someone's body (named, and stuck in a body part or left in
someone) now opens the bleeding-control guide, which says to leave it in and pad around it, and the
text ranking may no longer pick the drowning, eye or carbon monoxide guide when the message has no
water, eye or fuel in it. Desktop: the question and 9 paraphrases open the bleeding guide; 17 look-alikes keep their
own guide or none (pain that "went into my left arm", a car stuck in mud, a tick, a fish bone, a
knife that went in with blood pouring out, which keeps the tourniquet guide); the 320 labelled
emergency questions still score 217 with no unsafe guide; of 1,207 questions (labelled, fresh sets
and 425 non-emergencies) 2 changed, both chest pressure now opening the heart attack guide.
"His bone is sticking out" now opens the fracture guide. Emulator: the h37-31 question opens
"Bleeding control" in the app.

**The phone's voice could be a network voice.** The engine's default voice was used without a check.
Answers are now read only with an English voice the engine reports as installed and needing no
connection; otherwise Supertonic 3 reads, or the app says what to install. Desktop: the choice is
unit-tested. Phone: Google's engine on the Redmi chose `en-US-language` and read the sample. The case
of an engine with only network voices was not available on a device.

**Health files (partial).** On sets 31 and 32 the model let "carry on daily" stand for a once-a-week
methotrexate sheet, said a scan letter gave no plan where it recommends alendronate 70 mg weekly,
turned "35 days" into six weeks, and for "what dose on a Thursday" removed the dose (the dose check)
and pointed to a package leaflet. The answer is now followed by the file's own lines on the
medicine asked about, word for word, and a first line when the question or answer says daily for
a weekly medicine or the reverse. Nothing is withheld. On the 48 recorded file answers it is added
to the 4 medication ones only. Two blind graders compared those 4 answers with and without it:
both preferred it 4 times out of 4 (scores 3.5 to 7.0 and 3.75 to 7.25 of 10), and serious errors
left uncorrected went from 4 and 3 to 0, though the model's wrong sentences are still there, now
contradicted by the file's lines under them. These 4 are the cases it was built on. On fresh sets
written by models that did not see the code ([set A](fieldtest/medicine-lines/set-a.json), tuned on;
[set B](fieldtest/medicine-lines/set-b.json), untouched,
[notes](fieldtest/medicine-lines/set-b-notes.txt)), it was added to 11 of 11 and 11 of 13 medicine
questions, showed 22 of 28 and 18 of 29 of the lines needed, and was added to 0 of 9 and 3 of 11
other questions (two on a baby-feeding chart, quoting the lines that answer them; one quoting a
viral load beside unrelated dose lines). The frequency line caught daily against weekly (2 of 2 on
set A); weekly against monthly, twice against once a day and every 4 against every 6 hours are not
detected (0 of 3 on set B), though the right line was quoted for two of those three. It matches
words, not meaning: a line in other words ("agitated" against "agitation") can be missed. This is
a partial safeguard: the quoted lines sit under the model's answer and do not correct its wrong
sentences, which stay as written.

A second review reproduced two faults in it, now fixed. Two attachments with the same name were
treated as one file, so "Methotrexate 10 mg" from one sheet.txt was quoted with "Take twice daily"
from the other; each attachment is now its own document throughout, and repeated names are shown as
"sheet.txt (attached file 2)". And a frequency anywhere in the question was applied to every
medicine, so "methotrexate once weekly and folic acid daily", which matches the file, drew two
false corrections. Now each "daily" or "once a week" belongs to the medicine named in its clause,
or in its sentence, or the only one the text names; a negated one ("I shouldn't take it daily") or
one that could be about two medicines is no claim, and no correction is written unless the mismatch
is clear. Of the warnings above, one is now withheld as ambiguous: "the vitamin d 50000 every
morning with my lisinopril" names two medicines in one clause; its weekly line is still quoted.
A third review found two more, also fixed. With no blank line between "Calmavin 5 mg daily" and
"Zorbitrex 20 mg once weekly", the second line was read as the first one's schedule, so "is
zorbitrex daily?" corrected Calmavin; a line that names its own medicine before an amount now
starts a new entry, and a claim is matched to a medicine by its name, not by a line. And "when
taking methotrexate, should I exercise daily?" was read as a claim about the medicine; a "daily"
or "once a week" now counts only when it is said of the medicine itself, a dose, or taking it
("take it daily", "is zorbitrex daily", "20 mg every morning"), not of exercise, walks or checks.
On the sets above, the lines quoted and the warnings given did not change.

**The 4B's description.** It said "about half the factual errors of Qwen3.5 2B", which was true
when the 4B cited passages (0.30 against 0.62, sets 28 and 29) and is not now: from its own
knowledge it makes 1.94 against the cited 2B's 0.71 (set 37). Setup, Library, README and MODELS.md
now give each model's trade-off as measured; setup's notes come from the model, not its place in the list.

**Model knowledge and the collections.** Set 37's scores for the 4B and Gemma measure what the model
knows: their answers are written with no passage in the prompt. The section below now says so, and the
excerpts under such an answer say they were found for checking and were not its source. Whether
answering from the passages can do better on fresh questions was not re-tested; the policy is unchanged.

**A paused download could resume by itself.** Held while the phone was off Wi-Fi, a download keeps its
request to resume; paused by the person in that state, the request was not dropped, so the next app
start could fetch it again. Pausing or removing a download that is not running now drops it. Desktop:
unit tests for a download held and then paused, one waiting from the start, and one taken out of the
line. Emulator: the restart did not reproduce. Reading the saved request at each step (old and new
builds alike), it was already gone when the download was held, most likely because the connection
broke when the network changed and the transfer ended as a failure, which clears it; so the fix covers
the case where the stop wins that race. The same reading shows a download held for Wi-Fi does not
resume by itself after the app is closed; it waits for Resume.

**The voice and the 50 GB limit.** The voice's download checked free space but not the 50 GB limit
models and collections share, and did not take their provisioning lock. It now does both: at the
limit it starts, one byte over it is refused with the limit's message before anything is written, and
it waits while an import or another download claims space. Desktop: unit tests with a pretend
footprint; no installed data was touched.

**Release links.** v1.4.1 is the latest published release; v1.4.2 has no tag, release or assets
yet. The pushed docs and website link only to releases, assets and files GitHub serves. The local
docs and website prepared for 1.4.2 link to it 20 times, which is right once it is published. A tag
is not a release (GitHub shows a page for a tag with no release, as for v1.1.9), so
`tools/maintenance/check_release_links.py` asks GitHub for what a reader would get: a download must
be served (an uploaded asset of a published release), a release page must have a published, non-draft
release, and a file at a tag must exist. Nothing stops a local push: CI runs the check after each
push in a job of its own, which fails without stopping the Android build and tests, and the
website's build command runs the same check
(`website/tools/check-release-links.mjs`) and does not build a page that links to an unpublished
release or a missing asset, so Vercel does not deploy it. Run it before pushing.

## Answers from the model's own knowledge, and a model for 12 GB phones (October 19-20)

Kept to the retrieved passages, a small model often refused, quoted the wrong article ("Mountain"
for Africa's tallest mountain), or wrote a short cited answer that missed most of the question, and
reading the passages is what makes a slow phone wait minutes. With the 4B or Gemma 4 26B, an
ordinary question is now answered from the model's own knowledge in one pass, labelled as unverified,
with the passages found listed under it. Emergencies, calculations, files, phrasebooks, source-only
requests and places to eat, sleep, drink or shop keep their own routes.

Each answer model has its setting, chosen by the result below:

| Phone memory | Model | Answers from | Reasons first | Weights |
| --- | --- | --- | --- | --- |
| 4 GB | Qwen3.5 2B | the passages, cited | no | repacked |
| 6 GB | Qwen3.5 4B | its own knowledge | no | mapped from the file |
| 8 GB | Qwen3.5 4B | its own knowledge | no | repacked |
| 12 GB | Gemma 4 26B-A4B (UD-Q2_K_XL, 10.5 GB, Apache-2.0) | its own knowledge | yes | mapped from the file |

An AI model that read only earlier question files wrote a sealed set of 40 new questions (set 37,
[questions](fieldtest/heldout37.jsonl), SHA-256
`7038683e668d4ed3f6a494ed397d2335ea2e2607cfaf110bb03735c463a328fd`). Each setup answered on the
desktop pipeline, and graders scored every answer beside a web-and-AI reference without knowing
which setup wrote it, in two rounds of eight graders. The "own knowledge" rows measure what the
model knows: such an answer is written with no saved passage in its prompt, and the passages that
search finds in the collections are listed under it to check, without changing it. These results
therefore do not show that the collections or Leviathan improved those answers; only the cited
rows answer from them.

| Set 37, 40 questions | Share of the reference | Better / worse than today's model on that phone | Factual errors per answer |
| --- | ---: | ---: | ---: |
| Qwen3.5 2B, cited (1.4.2) | 26.0% | | 0.71 |
| Qwen3.5 2B, own knowledge (not adopted) | 20.6% | 12 / 17 | 2.77 |
| Qwen3.5 4B, cited (1.4.2) | 33.7% | | 0.64 |
| Qwen3.5 4B, own knowledge | 41.8% | 20 / 14 (p = 0.39) | 1.94 |
| Gemma 4 26B, own knowledge | 66.5% | 29 / 6 against the 4B | 0.65 |
| Gemma 4 26B, reasoning first | **70.2%** | 33 / 5 against the 4B | **0.44** |

Reasoning first beat answering straight away 20 to 8 (p = 0.036); it helped most with word problems
(50% to 87%), comparisons and travel. The 2B knows too little to answer on its own and keeps citing
the passages. On an earlier sealed set of 40 (set 34), the 4B went from 39.8% to 56-58% answering
from its own knowledge and Gemma reached 71-72%; giving either model three of the passages as notes
made its answers worse (4B 53.2%, Gemma 64.1%).

**The cost.** An answer from memory has more wrong details than a cited one: 1.94 per answer for the
4B against 0.64, which is why it is labelled unverified and the passages stay under it. The 4B also
did worse on travel (12% against 35%). Emergencies still open the published guides word for word,
so they stay near 40% with every model. Every answer and grade:
[set 37](fieldtest/results-1.4.2/heldout37-knowledge.jsonl).

**Speed and memory.** llama.cpp copies a model's weights into faster layouts when it loads them, and
Android cannot reclaim that copy. On a 12 GB Android emulator Gemma's copy took 5.9 GB and the app
was killed while loading; mapped from the file instead, it needed 0.56 GB and wrote as fast (13.6
against 13.8 tokens a second). In the app on that emulator, with the Encyclopedia installed, 8
answers took a median 99.5 s, with 9.4 GB of memory still free. The emulator is an arm64 Android
image on a Mac, so these times are the Mac's, not a phone's: no 12 GB phone has run Gemma yet. On a Redmi 13C (6 GB) the 4B's copy left 0.43 GB free;
mapped, it left 3.2 GB, read 3.3 and wrote 2.2 tokens a second (8.0 and 2.6 with the copy), and 5
answers in the app took 170 to 480 s, median 277 s.

**Storage.** Gemma is a 10.5 GB download. With every collection and the voice, a 12 GB phone that
installs Gemma uses about 43 GB, or 47.5 GB with the 2B and 4B kept too, within the 50 GB budget.
Installing the Encyclopedia needs room for its download and its unpacked copy at once, so on a phone
with Gemma it goes in before the model.

## 1.4.2 release candidate on fresh questions (October 17-18)

Two new sets of 60 questions each (set 28, [questions](fieldtest/heldout28.jsonl), SHA-256
`a8ff18fa32e8075f3853ee153b050e5055831b58d9d18e3dc14c7f721298d3e5`; set 29,
[questions](fieldtest/heldout29.jsonl), SHA-256
`44da8fe3074ccba62a2700a4d359ce9a9d370b5d6dd5d422174376187ce1b74c`) were written by AI models that
read no repository file and avoided every earlier topic, and sealed before any run. The 1.4.2 answer
code (4db1e6b; the commits after it change screens, not answers, except dropping a repeated copy
of an answer, which none of these 240 answers had) answered them on the desktop pipeline at the
phone's reading speed, with the seven collections and their Leviathan indexes, once with Qwen3.5 2B
and once with Qwen3.5 4B. Web-researched frontier-model references and two blind grading rounds of
eight graders followed for each set, each answer graded on its own
([set 28](fieldtest/results-1.4.2/heldout28.jsonl), [set 29](fieldtest/results-1.4.2/heldout29.jsonl):
every answer, reference and grade).

| 120 questions, 4 grading rounds | Share of the reference | Per round | Factual errors per answer |
| --- | --- | --- | --- |
| 1.4.2 with Qwen3.5 2B (phones under 8 GB) | 36.9% | 35.3, 35.1, 38.3, 38.8 | 0.62 |
| 1.4.2 with Qwen3.5 4B (offered first on 8 GB phones) | 38.2% | 37.9, 38.7, 38.3, 37.9 | 0.30 |
| Reference | | | 0.10 |

By category, both sets and both rounds:

| Category | Questions | 2B (mean /10) | 2B share | 4B (mean /10) | 4B share |
| --- | ---: | ---: | ---: | ---: | ---: |
| Lookups | 24 | 5.3 | 55% | 5.2 | 54% |
| Emergencies | 10 | 5.0 | 54% | 5.1 | 54% |
| Comparisons | 12 | 3.9 | 41% | 3.9 | 41% |
| Travel | 16 | 3.7 | 41% | 3.9 | 43% |
| Reasoning with numbers | 10 | 4.0 | 40% | 4.8 | 48% |
| Explanations | 20 | 2.2 | 23% | 2.6 | 27% |
| Synthesis | 8 | 2.0 | 23% | 2.1 | 24% |
| Cryptography | 8 | 2.1 | 22% | 1.9 | 20% |
| Places | 12 | 1.2 | 14% | 1.0 | 12% |

These scores are higher than 1.4.1's 29% on set 9, but sets differ: the same 1.4.1 build scored
37.3% on set 26. Compared on the same questions, the two versions answer single questions about
equally well. Set 26 ([questions](fieldtest/heldout26.jsonl)) was answered by 1.4.1 and by the 1.4.2
code with Qwen3.5 2B and graded blind in two rounds
([both builds' answers and grades](fieldtest/results-1.4.2/heldout26-141-vs-142.jsonl)):

| Set 26, same graders | Share of the reference | Per round | Factual errors per answer |
| --- | --- | --- | --- |
| 1.4.1 | 37.3% | 37.6, 36.9 | 0.75 |
| 1.4.2 code | 36.3% | 36.0, 36.5 | 0.72 |

15 of the 60 answers differ; 1.4.2 scored higher on 3, lower on 6 and the same on 6. What 1.4.2
improves is measured in the sections below: follow-up questions, emergencies told as statements,
word problems, and the passage that reaches the model. Citing the passages (since 19 October it answers from its own knowledge, above), the 4B makes half the factual errors of the
2B, and scored higher on three of the four sets it was tried on (sets 26 and 27 by 5.3 and
3.3 points, set 28 by 3.1), lower on set 29 by 0.5.

Two changes were measured on these sets and not adopted:

- **Every edition of an article.** The Encyclopedia holds an English and a Simple English edition
  of many articles; search reads the first one found. Reading both raised set 28 (2B 35.2% to 37.9%,
  4B 38.3% to 41.8%) but not set 29 (2B 38.6% to 37.8%, 4B 38.1% to 37.7%). Over the answers it
  changed, 28 got better and 19 worse, a split that could be chance (p = 0.24). The second edition's
  extra passages pushed out the one asked about: a second "Sculpture" overview pushed out the Statue
  of Liberty's, so the dedication year was lost, and the English article's 1950-present section
  made the 2B answer that Hillary and Fuchs were first at the South Pole, in 1958. Its answers are in
  the result files as `rejected_edition_fix`.
- **Answering from general knowledge when the passages miss** (a prompt change, set 27): the 2B fell
  from 40.5% to 39.2% and the 4B rose from 43.8% to 45.5%, 17 better and 11 worse, p = 0.35.

Sets 26 to 29 are now development data. References and graders are frontier AI models of one
family, and a grader scores the same text differently from run to run.

### The passage that reaches the model (set 25)

A yardstick over 456 development questions, each labelled with the passage that answers it, found
the right passage among search's candidates 77.6% of the time but in the phone's prompt only 36.9%.
Subject overviews named by a generic word in the question ("Town", "Night", "Adult") filled the
prompt's two or three places ahead of search's best hit, a place typed in lowercase ("hoi an") did
not count as a place, and some questions reached the model with no passage at all. 1.4.2 keeps a
lowercase place, lets a narrower article that search found ("Life on Titan") replace a generic
subject, and looks up the question's own nouns when nothing else is found.

On 80 questions written for these failures after the change and sealed (set 25,
[questions](fieldtest/heldout25.jsonl), SHA-256
`ecea5e0ced68fd158cdd043344dd5376eaaae3e446e6fde4edffe6fbbf666333`), 30 answers changed. Graded blind,
each on its own, they went from 1.90 to 3.93 of 10 on average: 18 better, 5 worse, p = 0.01
([answers and grades](fieldtest/results-1.4.2/heldout25-passages.jsonl)). Casual travel questions
gained most; questions reaching the model with no passage went from 3 to 0. On the untailored set
26 the same change moved little, as the table above shows.

## Answering when the excerpts miss the question (October 19)

When the cited answer fails its review, Field Atlas writes a model explanation labelled as
unverified. That prompt keeps the model to the retrieved excerpts, so when none of them is about the
question the model said so and stopped, above unrelated saved excerpts: "No excerpt supports
information about swallowed batteries" over a stock-car race report. On set 33 (24 questions and 8
follow-ups, asked on the 12 GB emulator), 7 of 32 answers were such refusals, among them a swallowed
button battery, Mexico City's tap water, high blood pressure and muscle loss with age.

Such a reply is now asked again without the excerpts and shown under the same unverified label,
with no excerpt list. A second refusal keeps the first reply. Answers under a quoted passage, from
files, or limited to saved sources are unchanged.

Qwen3.5 4B answered set 33 and a new sealed set of 40 (set 34,
[questions](fieldtest/heldout34.jsonl), SHA-256
`1176dd52d76db3e61561cda8c950c4778ddde4b893ad5d90d694a440392cfa78`, written by an AI model that read
no repository file) on the desktop pipeline, before and after. The change altered 7 answers of set 33
and 1 of set 34 ("does cracking your knuckles give you arthritis"). Graders scored those 8 answers,
before and after, beside a web-and-AI reference, without knowing which was which, in two rounds:

| The 8 answers that changed | Before | After |
| --- | ---: | ---: |
| Share of the reference answers | 10.2% | 67.3% |
| Better / worse | | 7 / 0 (p = 0.016) |
| Factual errors per answer | 0.38 | 0.88 |

**The cost.** A refusal cannot be wrong; an answer can. "9 am Monday in New York is what time in
Tokyo" was answered "6 hours ahead" (it is 14), and the battery answer added a step no first-aid
guide gives. A swallowed battery now opens the poisoning guide instead: the emergency check knew
"swallowed a button battery" but not "swallowed one of those small round watch batteries", and now
lets words that describe the battery sit between.

Every answer and grade: [the 8 changed answers](fieldtest/results-1.4.2/excerpt-refusals.jsonl).

## Questions about your own files (October 18)

Field Atlas answers questions about a file the person attaches (a note, a letter, a results sheet,
a contract). To measure that, AI models that read no repository file wrote two sealed sets of 24
text files, each with one question: set 31 ([questions and files](fieldtest/heldout31/), SHA-256
`09288c123cdf80a4399e65a32b2e79ac4322b7043e86e6da470b57a32830d73e`) and set 32
([questions and files](fieldtest/heldout32/), SHA-256
`1bc21b4fc994cd493126b5e98fe5c2a0719c6a362b50f882107066802b4def34`). They ask for facts in the
file, sums and deadlines worked from it, summaries, and explanations of terms it uses. Qwen3.5 2B
answered them on the desktop pipeline at the Redmi 13C's reading speed; graders saw each answer on
its own beside the file and a web-and-AI reference answer, without knowing which was which, in two
rounds.

**What was wrong.** Set 31 showed that 13 of 24 answers said the excerpts were not enough. A slow
phone's evidence budget, sized for a quick first word, let in about a third of a one-page file, so
the line asked about was often left out; and the model's review of each draft rejected most
correct ones ("your T-score is -2.7 [S1]"), with no other route for a file question. Two changes:
a question with files now reads at least 900 tokens of them, and an answer from the files alone
skips the model's review (its citation, scope and dose checks still apply). The review took about
as long as the extra reading.

| Share of the reference answers | Before | After | Answers better / worse |
| --- | ---: | ---: | --- |
| Set 31 (used to find the problem) | 11.6% | 31.9% | 15 / 2 (p = 0.002) |
| Set 32 (fresh, sealed before the change was tested) | 32.1% | 41.4% | 15 / 5 (p = 0.04) |

Answers that said the excerpts were not enough went from 13 to 3 on set 31 and from 7 to 1 on set
32. Summaries gained most on set 32 (1.6 to 5.4 of 10); sums and deadlines worked from a file
stayed weak (0.5 of 10 either way).

**The cost.** More answers means more wrong details: on set 32 the graders found 1.15 errors per
answer given, against 0.65 before. On the 14 questions about health care (a discharge letter, a
drug sheet, blood results), answers went from 11% to 35% of the reference, but they included a
methotrexate answer that missed that the drug is taken weekly and called a MoCA of 23/30 "not
bad". Keeping the review for health files was tried and scored 17%, with errors of its own, so it
was not kept. The answer cites the file it read; check it there.

**On the phone.** On the Redmi 13C with Qwen3.5 2B, in airplane mode, a one-page invoicing note
from set 32 was attached through the app. "What is the hourly rate" was answered in 1 min 17 s
(the rate right, with a wrong condition added) and "summarise the key points of these notes" in
2 min 12 s from four passages of the note (useful, with two details wrong: who is not VAT-registered,
and the hours of a site visit).

**Collections as well as the file.** A switch under attached files that searched the collections
too was tried. On set 31 it lowered answers from 11.0% to 2.7% of the reference (8 worse, none
better): unrelated passages crowded out the file. It was removed; a question that asks to "use my
library" still adds the collections.

Every question, answer and grade: [set 31](fieldtest/results-1.4.2/heldout31-files.jsonl),
[set 32](fieldtest/results-1.4.2/heldout32-files.jsonl).

## Biology & longevity on 40 longevity questions (October 18)

Field Atlas is a research tool, and ageing and longevity are among the topics it is most likely to
be asked about, so the Biology & longevity collection was rebuilt around 48,286 open-access
ageing and longevity papers ([DATASETS.md](../DATASETS.md#biology--longevity-october-18)). To
measure it, 40 new longevity questions were written by AI models that read no repository file
and sealed before any run (set 30, [questions](fieldtest/heldout30.jsonl), SHA-256
`690c867ab93dfd90bf8ddb7303bafd1f428a714a7e2fb711f9fda242798d6cf0`): evidence for an
intervention (metformin, rapamycin, NAD+ boosters, senolytics), mechanisms, genes, comparisons
and numbers such as the longest-lived animals. The 1.4.2 answer code answered each one on the
desktop pipeline with all seven collections, once with the October 13 Biology edition and once
with the October 18 one, with each model. Graders saw the four Field Atlas answers and a
web-and-AI reference answer in random order, without knowing which was which, and scored them in
two independent rounds of 8 graders.

| Model | October 13 edition | October 18 edition |
| --- | ---: | ---: |
| Qwen3.5 2B | 23.0% | **35.6%** |
| Qwen3.5 4B | 22.3% | **35.9%** |

Scores are the share of the reference answers' total score. Of the answers that changed, 16 got
better and 6 worse with the 2B (sign test p = 0.05), and 20 better and 3 worse with the 4B
(p < 0.001). The gains were largest where the earlier edition had nothing to quote: questions
about genes went from 8-14% to 37-40%, and questions asking for a number from 28-36% to 54-55%.
Questions weighing human evidence gained least (12-17% to 23-24%): the papers often describe mouse
results, and the models do not always say that the human evidence is thinner. Every question,
answer and grade is in
[heldout30-biology.jsonl](fieldtest/results-1.4.2/heldout30-biology.jsonl).

## 1.4.2 at the bounty's limits on an Android emulator (October 18)

The bounty allows up to 12 GB of memory and 50 GB of assets. We set up an Android 15 arm64 emulator
with exactly 12 GB of memory, 8 cores and 64 GB of storage, installed a 1.4.2 build (the release
code, signed with our test key), and downloaded everything the app offers through the app itself:
both answer models, all seven collections and the natural voice. Then we turned on airplane mode,
set the location to central Berlin, and asked 8 everyday questions with each model
([questions, answers and measurements](fieldtest/results-1.4.2/emulator-bounty-limits.jsonl)).

**Storage, everything installed:**

| What | Size |
| --- | ---: |
| Qwen3.5 2B and Qwen3.5 4B | 4.41 GB |
| Seven collections | 32.30 GB |
| Natural voice | 145 MB |
| The app itself | 173 MB |
| **Total** | **37.0 GB of the 50 GB allowed** |

Android's own figure agrees: Settings › Apps › Field Atlas › Storage shows 37.06 GB in total (app
173 MB, user data 36.88 GB, cache 57 kB), which also counts the October 13 Biology edition (27 MB)
that Library keeps after the update until it is removed. The table adds up the app's folders on the
emulator, without that edition. Sizes here are decimal (1 GB = 1,000,000,000 bytes), as Android
shows them; 37.0 GB is 34.5 GiB. No temporary download files were left behind. This was measured
again after the Biology & longevity collection grew to 2.86 GB installed (the October 13 edition
gave 34.2 GB). Installing the Encyclopedia last briefly needs its 10.64 GB download beside its
installed copy, so the highest point while setting up is about 47.7 GB with everything, worked out
from the file sizes rather than measured.

**Memory while answering.** The bounty asks for a "12GB RAM environment", so what matters is that the
app answers on a 12 GB device without running it out of memory. On the same emulator, with every
collection (the October 18 Biology edition included) and airplane mode on, each model answered a
research question, a follow-up under it, a longevity question and a places question, and for each
answer we recorded
([measurements](fieldtest/results-1.4.2/emulator-memory.jsonl), measured with
[scripts/measure_answer_memory.py](../scripts/measure_answer_memory.py)):

- the app's **peak resident memory**, from the kernel's own high-water mark (VmHWM), reset before each
  answer, so no peak between samples is missed. It counts every shared page in full: the worst case;
- Android's **PSS**, its measure of "how much memory this app is responsible for", read every second
  (Android adds the app's share of memory moved to swap; here that stayed under 1 MB);
- the **least memory the whole emulator had available** while the answer was written.

| Model (4 answers each) | Peak resident memory | Peak PSS | Least memory available on the 12 GB device |
| --- | ---: | ---: | ---: |
| Qwen3.5 2B | 3.45 GB | 3.30 GB | 9.19 GB |
| Qwen3.5 4B | 6.83 GB | 6.67 GB | 7.37 GB |

All 8 answers finished offline in the same app process, and Android's record of why the app last
stopped (`dumpsys activity exit-info`) shows no stop for low memory, only our own installs and
restarts. Most of the figure is the model file, which is memory-mapped: Android can drop those
pages and read them again from storage, so these figures overstate what the app strictly needs.
Sizes are decimal GB, converted from the kilobytes (1,024 bytes) the kernel reports.

The 20-answer runs below were measured earlier with PSS alone, read every 2 seconds, and reported
as 3.3 GB and 6.5 GB. Those figures mixed units: corrected, they are 3.4 GB and 6.8 GB, the same as
the measurement above.

**Offline:** with airplane mode on, all 16 answers finished, with no connection. Places near you
and the fuel word problem came from saved data in seconds, and with the 2B so did the wallet answer,
quoted from a saved passage. The rest were written by the model from the collections and cite them.

**Research questions with follow-ups.** Field Atlas is an offline research tool, so on the same
emulator, still in airplane mode, we asked 6 research questions (explain, compare, check a claim)
and one follow-up under each, with each model: 24 answers
([questions and answers](fieldtest/results-1.4.2/emulator-research-followups.jsonl)).

- All 24 answers finished offline, and every follow-up was read as part of its topic: "what happens
  to a validator that cheats?" under a question about proof of stake was answered with slashing,
  and "how did governments respond?" under the 2008 financial crisis with the bailouts and stimulus.
- Good answers: how CRISPR works and, with the 4B, that the FDA approved the first CRISPR treatments
  (Casgevy and Lyfgenia) in December 2023; the causes of the 2008 crisis; how proof of stake
  secures Ethereum.
- Weak answers: "Compare nuclear fission and fusion" found only a passage about fission, so neither
  model compared them, and the follow-up about radioactive waste quoted the same passage. "Is it
  true we only use 10 percent of our brains?" found general passages about the brain; the 4B said
  they do not cover the claim, and the 2B said it is false with a weak reason, then gave a wrong
  origin for the myth (a passage about the inner ear). Under calorie restriction and ageing, "does
  it work in humans or only in animals?" was not answered directly.

These are the same limits the scores above show: when search finds the right passage the answer is
usually right, and when it does not, the models say so (the 4B more often) or get it wrong.

The 4B answers above were asked with the app's animations on. During the 2B research run the
emulator slowed to about 9 prompt tokens a second with animations on, against about 220 with them
off, because it draws every frame of the research page's animation on the Mac's processor, so the
2B research run was asked with animations off. On a real phone the screen is drawn by its graphics
chip; on the Redmi 13C the animations cost about a tenth of the reading speed.

**The 4B takes over after it is downloaded.** With the 2B in use, we deleted the 4B and downloaded
it again in Library: once it was installed, Library showed the 4B in use without being asked. The
research questions above were then asked again with it, animations on: 11 of 12 finished as before,
in about the same time. The twelfth was cut short by the emulator's text input ("What does research
say about") and was still being written after 10 minutes. During that answer the emulator read the
prompt at about 5 tokens a second instead of about 55, and then recovered by itself; the same
slowdown came and went earlier in the day with the 2B, so we do not treat these minutes as the app's.
Asked again by hand on the same emulator, the same cut-short question finished in 51 seconds.

**Timings on the emulator are not phone timings.** The emulator runs on a Mac's processor: an
answer written by the model took 17 to 29 seconds with the 2B and 24 to 63 seconds with the 4B,
which is much faster than a phone. The phone timings are in the release notes.

**What went wrong:**

- "My friend fainted and is breathing but not waking up" got a passage about fainting from the
  Biology collection, and both models said to keep the person lying down for 10 to 15 minutes.
  Neither said to call the emergency number or put the person on their side; set 28 asked nearly
  the same question and it scored 2 of 10 with both models. Fixed after this run: a 44th guide,
  the recovery position from English Wikipedia (revision 1316139441), now answers someone breathing
  but not waking, opening with the call to the local emergency number like every urgent guide, and
  "not breathing" still opens CPR. Over the 1,207 emergency and look-alike questions kept for these
  checks, three answers change, all to the new guide (passed out drunk on his back and snoring,
  vodka and breathing slow, snoring after a seizure), and none of the 320 labelled questions
  changes. On the emulator the fainting question now shows the guide in 4 seconds.
- "How do I get from Narita airport to central Tokyo?" read the guide for the city of Narita, so
  the answers describe the local train to Narita town (¥250) before the Narita Express.

## 1.4.2: follow-up questions, emergencies told as statements, word problems (October 15)

Every number below compares 1.4.1 with the 1.4.2 code on questions written after the changes by
AI models that read no repository file, sealed by SHA-256 before any run, and graded blind. The
answers, readings, guides and grades are in [results-1.4.2](fieldtest/results-1.4.2/).

### Follow-up questions

A question asked under an answer is now read together with the question before it. "What about
X", "how about X" and "and in/for/during X" swap in the new subject ("tipping customs in Japan",
then "what about South Korea?", is searched as "tipping customs in South Korea"); anything else
keeps both questions. The screen shows that reading while it searches, and Edit corrects it.

30 pairs of a first question and the follow-up a person would type next were answered twice by the
same build: the follow-up alone, as 1.4.1 asks it, and as read. Six graders scored the 60 answers in
two rounds, each seeing the conversation and one answer, never which version wrote it
([pairs and grades](fieldtest/results-1.4.2/followups.jsonl)).

| Follow-up kind | Pairs | Asked alone (1.4.1) | As read (1.4.2) |
| --- | ---: | ---: | ---: |
| Pointing back ("which one is cheapest?") | 8 | 0.75 | 2.50 |
| New subject ("what about Peru?") | 8 | 0.06 | 2.81 |
| Going deeper ("why?", "is that safe?") | 7 | 0.86 | 1.50 |
| Practical next step ("how do I get there?") | 7 | 0.14 | 2.07 |
| **All** | **30** | **0.45 of 10** | **2.25 of 10** |

The reading was better on 19 pairs, worse on 4 and the same on 7; the two rounds agreed (0.47 and
0.43 against 2.27 and 2.23). Asked alone, "which of those are free?" after Tallinn's sights was
answered about free algebras, and "which ones got executed?" after Henry VIII's wives about a Texas
prison escape. The level stays low: the model is the same, and a follow-up such as "which one is
closest to the castle?" lists the places without measuring from the castle.

A follow-up that points back at the answer ("when was it…", "which of those…") now also reads the
passages that answer read. On the phone, "when was it turned back into a mosque?" after Hagia Sophia
answered 1453: its own search found the 2020 passage, but subject overviews ("Hagia Sophia —
Overview", "Mosque — Overview") filled the phone's short prompt first. Up to three of the earlier
answer's passages that share the follow-up's words now go ahead of them, and the answer is July 2020.

Measured on the same 30 pairs at the phone's reading speed, answered with and without the earlier
passages by one build and graded blind in two rounds by six new graders
([answers and grades](fieldtest/results-1.4.2/followups-carried-sources.jsonl)):

| Same 30 follow-ups, phone-sized prompts | Mean of 10 |
| --- | ---: |
| Without the earlier passages | 1.53 |
| Carrying them for every follow-up (first version) | 1.52 |
| Carrying them only when the follow-up points back | 1.63 |

The first version helped 6 of the 11 answers it changed and hurt 3, one badly: "and from the moon?"
after the Sun's light was given the Sun's passages and answered that moonlight never reaches Earth.
Carrying only for follow-ups that point back changes 4 answers, the same texts the graders scored:
3 better, 1 slightly worse. The gain is small; these graders scored lower than the first six (1.53
here, 2.25 above, for the same reading).

A follow-up can now also be spoken or asked about a file: the box under an answer has the same
microphone and + as the question box. Checked on the Redmi: a dictated follow-up arrived word for
word, and a menu file added to a follow-up was read and cited.

### Emergencies told as statements

On new emergency questions 1.4.1 found the right guide for 9 of 45. Most misses named the hazard
but did not ask anything ("tornado siren going off and we're in a motel room"), and a final check
dropped them. A guide named by its own term, with someone in the situation, is now shown; "should
we" reads like "should I"; plants, berries or mushrooms eaten before falling ill open the poisoning
guide; the outdoor-injury guide needs an injury or an accident; questions about trials and
evidence go to research.

| Set (80 questions, 45 emergencies, 35 look-alikes) | Build | Right guide | Look-alikes given no guide | Unsafe guide |
| --- | --- | ---: | ---: | ---: |
| A ([questions](fieldtest/results-1.4.2/emergency-a.jsonl)), used while making the changes | 1.4.1 | 9 | 35 | 0 |
| | 1.4.2 | 14 | 35 | 0 |
| **B** ([questions](fieldtest/results-1.4.2/emergency-b.jsonl)), written after them | 1.4.1 | 9 | 30 | 0 |
| | **1.4.2** | **14** | **31** | **0** |

The 320-question labelled set (development data) goes from 129 to 152 answered well, with no
unsafe guide. Most new emergencies still get no guide: symptoms described without the hazard's
name ("her face went lopsided", "went stiff and started jerking") match no word list. Matching them
by the words of each guide's own text was tried and rejected: half of what it added was the wrong
guide (CPR for a stroke, the earthquake guide for a seizure).

### Word problems that name places

A question with four or more of its own figures and an arithmetic ask is worked from its figures
even when it names places ("leaves Tokyo at 17:05 … lands in San Francisco at 10:20 … UTC+9 …
UTC-7"), and "which is cheaper", "by how much" and "what's my daily budget" count as arithmetic
asks. Over the 876 earlier questions, 9 change route, each a self-contained word problem.

### Single questions: set 11

60 new questions ([questions](fieldtest/heldout11.jsonl)) answered by 1.4.1 and the 1.4.2 code with
the same collections, graded blind in two rounds against web-researched frontier answers. 57 of
the 60 answers are identical; of the three that differ, one is a pharmacy list read at another
minute, one is a question about omega-3 trials that no longer gets the heart-attack guide, and one
(a car off the road with the driver trapped) lost the outdoor guide, which was fixed before release.

| Set 11 | Share of the reference | Per round |
| --- | --- | --- |
| 1.4.1 | 27.3% | 28.1%, 26.4% |
| 1.4.2 code | 27.3% | 28.3%, 26.3% |

Single questions are where they were: the changes help follow-ups and emergencies, not the
answers the model writes.

### History keeps every answer

In the long session below, History kept only the newest 100 answers, so the first 20 of 120 were
gone by the end with no notice. 1.4.2 keeps every answer until the reader deletes it. The History
screen warns instead when the phone has less than 300 MB free, or when an answer could not be saved.

## 1.4.1 release build on fresh questions (October 15)

The published 1.4.1 build was measured on a new set, the same way 1.4.0 was measured on set 6. An
AI model that read no repository file and avoided every earlier question wrote 60 questions (set 9,
[questions](fieldtest/heldout9.jsonl)), sealed by SHA-256
`a938fbf13b77e8e7f4f57f57f40b5ae9b981c52d0acf6ee61375c388eebe8dd8` before the run. The release
commit (2817ce8) answered them on the desktop pipeline with the phone's code, Qwen3.5 2B and the
seven 1.4.1 collections with their Leviathan indexes, the files published to Hugging Face, with the
phone's native search loaded. Web-researched frontier-model references and two independent blind
grading rounds followed, each answer graded on its own
([every answer and grade](fieldtest/results-1.4.1/heldout9.jsonl)).

| Set | Build | Share of the reference | Factual errors per answer |
| --- | --- | --- | --- |
| Set 9 ([questions](fieldtest/heldout9.jsonl)) | The 1.4.1 release build | 29% (29% and 28%) | 0.67 (reference 0.07) |

By category, both rounds together:

| Category | Questions | Field Atlas (mean /10) | Share of the reference |
| --- | ---: | ---: | ---: |
| Vegan restaurants in the city you are in | 2 | 7.5 | 83% |
| Lookups | 8 | 5.4 | 58% |
| Places | 8 | 2.9 | 33% |
| Cryptography | 4 | 2.9 | 30% |
| Travel | 9 | 2.5 | 29% |
| Explanations | 6 | 2.3 | 25% |
| Reasoning with numbers | 6 | 1.9 | 19% |
| Comparisons | 6 | 1.2 | 13% |
| Synthesis | 5 | 1.0 | 11% |
| Emergencies | 6 | 0.9 | 10% |

1.4.0 scored 34% on set 6, but set 6 holds different questions. So the published 1.4.0 build
(65851ba, with its own collections, search library and runtime patch) answered set 9 as well. Six
new graders then scored both builds' answers and the references in two more blind rounds. Each
question's three answers went to three different graders
([both builds' answers and grades](fieldtest/results-1.4.1/heldout9-140-vs-141.jsonl)).

| Same 60 questions, same graders | Share of the reference | Factual errors per answer |
| --- | --- | --- |
| 1.4.1 | 29.7% (29.6% and 29.8%) | 0.69 |
| 1.4.0 | 28.6% (29.0% and 28.1%) | 0.76 |

On the same questions the two builds score about the same, 1.4.1 a point higher in both rounds.
39 of the 60 answers differ between them: 1.4.1 scored higher on 14, lower on 12 and the same on
13. With the new graders 1.4.1 scored 29.7%, close to the 29% above. Failures seen in 1.4.1's
answers:

- **The wrong emergency guide, or none.** Wild mushrooms eaten at a campsite got the guide for
  injuries outdoors, and a question about the PREDIMED diet trial got the heart-attack signs guide.
  An earthquake in a hotel got no guide, although the app has one for earthquakes. A fish hook in a
  finger and a knocked-out tooth have no guide and got no usable answer.
- **Unrelated passages instead of an answer.** "Wise or Revolut card in Colombia" showed the
  encyclopedia entry for wisdom; "mineral vs chemical sunscreen" the entry for minerals; the 1918
  flu question the entry for young adults.
- **Word problems the calculators did not take.** Five of six (a flight across time zones, a
  currency quote, a budget in percentages, a trail gradient, a river current) reached the model,
  which refused or got them wrong; the sandwich count for a group was answered fully.
- **Travel answers quoting the wrong paragraph of the right section:** crowding on the Kandy to
  Ella train instead of how to reserve a seat, extending a Brazilian tourist visa instead of whether
  Canadians now need one.

Set 9 is now development data. References and graders are frontier AI models of one family; a
grader scores the same text differently from run to run, by about 2 points on one answer.

## 1.4.1 over a long session: 120 questions in a row on the phone (October 15)

The bounty asks for consistent answer quality across longer sessions. To test that, an AI model
that read no repository file wrote 100 new questions in ten categories and 10 follow-up pairs
([questions](fieldtest/heldout10.jsonl)). They were sealed by SHA-256
(`bdea73c4900392254ff795991205bdee16e778201e375eda0fbe27cf73917ac4` for the 100, `afd4dc65…`
for the pairs) and put in a shuffled order, so early and late questions are alike. The 1.4.1 build
on the Redmi 13C answered all 120 in one sitting, typed into the app one after another, from 06:51
to 16:36.

The phone held up for the whole session:

| Session quarter | Questions | Median time per answer | Median memory (PSS) | Median battery temperature |
| --- | --- | --- | --- | --- |
| 1 | 1-30 | 199 s | 1,687 MB | 40.5 °C |
| 2 | 31-60 | 204 s | 1,694 MB | 42.0 °C |
| 3 | 61-90 | 188 s | 1,717 MB | 42.4 °C |
| 4 | 91-120 | 262 s | 1,713 MB | 42.6 °C |

- One app process answered all 120 questions, with no crash or restart.
- Median memory stayed flat: about 26 MB more in the last quarter than the first, after ten
  hours.
- Answers did not slow down. The last quarter's higher median comes from its question types: against
  the median time of its own question type, every quarter is within 10 seconds, and the rank
  correlation between position and time is -0.04.

Quality was graded the same way as set 9. Five web-researching frontier-model agents wrote
reference answers, and six blind graders scored each answer on its own, with a question's two
answers always going to different graders. Follow-ups were graded with the earlier question shown.
One answer is missing: the screen dump skipped its text and History had already dropped it.
([Every answer, reference and grade](fieldtest/results-1.4.1/heldout10-session.jsonl);
[memory and temperature every 30 seconds](fieldtest/results-1.4.1/heldout10-memory.json).)

| Set 10 | Questions | Field Atlas (mean /10) | Reference (mean /10) | Share of the reference |
| --- | --- | --- | --- | --- |
| All | 119 | 2.15 | 9.18 | 23% |
| Without the follow-up second questions | 109 | 2.34 | 9.19 | 25% |
| First half of the session | 54 | 2.39 | 9.17 | 26% |
| Second half of the session | 55 | 2.29 | 9.22 | 25% |

Quality did not drift. The trend across the session is -0.6 points per 100 questions, and a
permutation test puts it well within chance (p = 0.34). By quarter the share moves between 19% and
31%, about what 27 questions per quarter give by chance.

| Category | Questions | Field Atlas (mean /10) | Share of the reference |
| --- | ---: | ---: | ---: |
| Lookups | 14 | 4.9 | 51% |
| Follow-up pairs, first question | 10 | 4.4 | 47% |
| Explanations | 10 | 3.0 | 33% |
| Travel | 15 | 2.1 | 24% |
| Emergencies | 10 | 2.0 | 21% |
| Comparisons | 10 | 1.6 | 18% |
| Synthesis | 8 | 1.6 | 18% |
| Crypto | 7 | 1.0 | 11% |
| Reasoning with numbers | 10 | 1.1 | 11% |
| Places | 12 | 0.9 | 10% |
| Vitalik's vegan test | 3 | 1.0 | 13% |
| Follow-up pairs, second question | 10 | 0.1 | 1% |

1.4.1 has no follow-ups, so every second question was read alone: "which of them are free to
visit?" after a question about Kyoto got an answer about VisIt, a visualization program. 1.4.2 adds
follow-ups (see above). The 23% is not a controlled comparison with set 9's 29%: the questions
differ, and these answers came from the phone rather than the desktop pipeline.

Two things about the method. The answers were read from the phone's screen dump, which misses text
drawn with inline citation marks, so 7 answers were transcribed from History or from screenshots.
25 answers whose explanation sits above quoted passages were first graded without the passages; set
9 graded them with the passages, so these 25 were graded again with them (mean 0.88 before, 1.04
after). References and graders are frontier AI models of one family.

## 1.4.1 on the phone: answers, speed, tokens and failures (October 14-15)

Raw data for everything below is in [results-1.4.1](fieldtest/results-1.4.1/). These are our own
measurements: the questions, the graders and the reference answers all come from AI models.

### Answers on a real phone

20 questions from a new set (two per category, written by an AI model that never saw the app,
[set 7](fieldtest/results-1.4.1/set7-questions.jsonl)) were typed into the installed app on a Redmi
13C (6 GB) in airplane mode, once with each model. Two graders scored all 40 answers 0-10 without
knowing which model wrote them, checking claims on the web; their scores agreed closely
(correlation 0.99). The build was the 14 October 1.4.1 candidate with the Leviathan collections.

| | Qwen3.5 2B | Gemma 4 E2B |
| --- | ---: | ---: |
| Mean score (0-10) | **4.65** | 3.75 |
| Answers scoring 7 or more | 5 of 20 | 7 of 20 |
| Answers scoring 3 or less | 7 of 20 | 12 of 20 |
| Factual errors per answer | 0.50 | 0.12 |
| Median time for an answer the model wrote | 240 s | 195 s |

Qwen3.5 2B stays the only model offered. Gemma made fewer mistakes but often gave up when the
saved passages did not state the whole answer ("The provided excerpts do not contain..."), or gave
one line.

What Qwen3.5 2B did, by kind of question (score of each of the two questions):

- **Word problems** (fuel cost split four ways, a flight across time zones): 10 and 10, worked out
  by the app's calculators.
- **Emergencies** (snake bite, heat stroke): 8 and 6, from the built-in guides.
- **Quick facts** (Ada Lovelace, Lake Baikal): 5.5 and 7.5.
- **Explanations, synthesis, cryptography and Ethereum ideas:** 3.5 to 7.5, apart from the seed-words
  question below (0).
- **Travel know-how** (tipping in Egypt, trains in India): 2 and 0. **Named places** (Edinburgh's
  Royal Mile, pubs in Prague): 1 and 3. **Comparisons** (alligator or crocodile, dengue painkillers):
  0 and 2. These are the weakest areas.

Failures seen:

- **Unrelated passages instead of an answer.** "How do 12 seed words turn into wallet addresses?"
  showed a token standard's motivation and a dictionary entry for "wallet" with both models (0).
- **Travel questions answered from the wrong passages**, so the guide's own advice never reached the
  answer.
- **Short answers that restate the question** ("On a clear day, the sky appears blue.").

### Vitalik's test: best vegan restaurants in the city you are in

"Tell me the best vegan restaurants in the city I am currently in" with a phone position in Lisbon,
Berlin, Bangkok, Chiang Mai, Tokyo, Buenos Aires, Singapore and Nairobi, and the same question
naming each city: all 16 answers listed six vegan places (fully vegan where the city has them) with
address or coordinates, hours and, near the phone, distance and direction, from the OpenStreetMap
collection. This ran on a Mac with the app's own code and collections, not on the phone.

### Where the time goes

Each stage of three questions, timed on the Redmi from tapping Start research
([timings](fieldtest/results-1.4.1/phone-stage-timings.json)):

| Question | Finding evidence | Reading sources | Writing | Checking | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| Why is the sky blue... | 30 s | 36 s | 17 s | 17 s | 103 s |
| How deep is Lake Baikal... | 19 s | 37 s | 15 s | 124 s | 198 s |
| How do vaccines train... | 27 s | 30 s | 30 s | 139 s | 229 s |

- **Checking** is short when the model's draft passes its own review. When it fails, the app writes a
  new draft and reviews it again, which took about two minutes in two of the three questions.
- **Finding evidence:** with the Encyclopedia switched off, the vaccines question found its
  evidence in 9 s instead of 27 s. On a Mac most of the Encyclopedia's share is a title lookup that
  reads the title of every passage holding any word of the question.
- **Search itself is fast.** Leviathan's median search took 0.07-1 s per collection on the phone and
  was faster than the old index in every collection timed ([table](../native/leviathan/README.md)).

### Tokens per answer

60 questions of a second new set ([set 8](fieldtest/results-1.4.1/set8-questions.jsonl)) answered
on a Mac with the same prompts the phone builds, Qwen3.5 2B. 52 needed the model:

- 3.25 model calls per answer: the draft, its review, and in many answers a second draft and review.
- 3,656 prompt tokens read and 388 written per answer on average (median read 3,860).
- At the Redmi's measured speed (about 16 tokens a second reading, 5.3 writing) that is about five
  minutes of model time per answer, which matches the 2-6 minutes seen on the phone.

### A faster check that was not adopted

When the review rejected a draft, the app would keep the sentences the review accepted instead of
writing a new draft. On set 8 it cut the estimated model time per answer by a fifth (median 304 s
to 239 s), but the 19 answers it changed scored 3.63 against 4.68, and 11 of them were worse: the
kept sentences lost their context ("In contrast, an ERC-721 token is..."). It was reverted. Every
answer and grade is in [claim-review-experiment.jsonl](fieldtest/results-1.4.1/claim-review-experiment.jsonl).

## 1.4.0 release build on fresh questions (October 13)

Between 7 and 13 October the answer path changed (one consolidated Encyclopedia searched by a
native engine, cited explanations reviewed before they are shown, a new biology collection), so the
release build was measured again, on new questions, three times. Each set was written by a writer
that read no repository file and avoided every earlier question, sealed by its SHA-256 before the
run (set 4 `6057cad0e1732c36f960b96f4ca93733632d577cc55fb19c83bbb969096607a9`, set 5
`b2f2c383778f9022635b8a726e71d334b6891e6ff1857e2f44bc6bc923ac42b4`, set 6
`10646ea742d03f705a45ac095dc276c71bdc231af5114b5e8bc47f11c82a6b92`, the published question files), answered by a frozen build, given web-researched frontier-model references, and graded blind
in two independent rounds (each answer on its own, as in the field test below; the share of the
reference is the sum of Field Atlas's scores over the reference's).

Configuration: Qwen3.5 2B, every collection the app offers in 1.4.0, byte-identical to the
published files ([DATASETS.md](../DATASETS.md#collections-the-app-offers)), in the phone's pack
order, on the desktop pipeline with the phone's code. The phone's native search (Leviathan) was
loaded on the desktop from the same pinned source, so the Encyclopedia and Biology collections were
searched as on the phone; the 7 October measurement used keyword search only.

| Set | Build | Share of the reference | Factual errors per answer |
| --- | --- | --- | --- |
| Set 4 ([questions](fieldtest/heldout4.jsonl)) | First 1.4.0 release candidate | 21% (22% and 21% per round) | 0.41 |
| Set 5 ([questions](fieldtest/heldout5.jsonl)) | After the answer-path fix | 34% (33% and 34%) | 0.83 |
| **Set 6** ([questions](fieldtest/heldout6.jsonl)) | **The 1.4.0 release build** | **34% (33% and 34%)** | **1.02** |
| Set 6 without the five phone-position questions | The optional Qwen3.5 4B on the release build | 35% (the 2B: 32% on the same 55) | 0.50 |

**Set 4: the candidate withheld 25 of 60 answers.** Where saved passages matched, the model wrote a
cited draft and then reviewed whether the passages supported it; on an unconfirmed draft the app
showed "I couldn't produce a properly cited explanation" and skipped every route that answered
before. The 2B's review rejected 32 of the 44 drafts it saw, among them correct ones and some that
were wrong (two drafts on removing a tick gave unsafe advice). The fix keeps the review and never
shows a rejected draft, but lets the question continue to a travel guide's section, a phrasebook
line, the saved excerpts, or a source lead with a model explanation labelled as unverified. To see
what changed, set 4 was also answered by the 7 October build, with its own collections and keyword
search: graded blind together (against the reference grades of the candidate's two rounds), that
build reached 32%, the fixed path 39%, with 1.56 and 0.93 errors per answer
([7 October build](fieldtest/results-1.4.0/heldout4-7oct-build.jsonl),
[fixed path](fieldtest/results-1.4.0/heldout4-fixed-path.jsonl)) (development data: four
emergency matcher fixes and a power-bank calculator were made with these questions in view).

**Set 5 found place bugs.** "Is there a pharmacy I can walk to from here?" and "any museum within
walking distance of me?" were not read as near-me questions and went to a worldwide search;
museums near the phone could not be found by position at all, because travel-guide listings carry
no map index. Both were fixed, with the note on words no listing carries, and set 5 became
development data.

**Set 6 is the release number:** 34% of the reference with the default model, with about half
the factual errors per answer of the 7 October candidate on its own set (1.02 against 1.98). Every
answer, reference and grade is in [`fieldtest/results-1.4.0/`](fieldtest/results-1.4.0/)
(`heldout6.jsonl`, and `heldout5.jsonl` and `heldout4-candidate.jsonl` for the earlier sets).

By category on set 6:

| Group | n | Field Atlas, Qwen3.5 2B (mean /10) | Reference (mean /10) | Share of reference |
| --- | --- | --- | --- | --- |
| Vitalik's question | 2 | 5.50 | 7.50 | 73% |
| Casual lookups | 8 | 5.31 | 9.31 | 57% |
| Practical travel | 9 | 3.06 | 8.83 | 35% |
| Places | 8 | 2.75 | 8.56 | 32% |
| Synthesis | 5 | 2.80 | 9.10 | 31% |
| Explanation | 6 | 2.75 | 9.17 | 30% |
| Reasoning with numbers | 6 | 2.67 | 10.00 | 27% |
| Emergencies | 6 | 2.33 | 9.08 | 26% |
| Comparison | 6 | 2.08 | 9.00 | 23% |
| Cryptography | 4 | 1.62 | 8.88 | 18% |

Compared with the 7 October candidate's fresh set, practical travel (20% to 35%) and reasoning
(14% to 27%) rose, comparison held (24% to 23%), and explanation (51% to 30%), emergencies (47% to
26%), places (45% to 32%), lookups (64% to 57%) and cryptography (26% to 18%) fell; the sets
differ, so a category's change is not a measured effect of one change. References and graders are frontier models of one family, so these
are development measures, not an independent score.

## The 7 October 1.4.0 candidate on 60 new held-out questions (October 7)

The questions behind the earlier figures were looked at while the app was changed, the first
held-out set included: its travel misroutes (a metro ticket answered from river boats, a visa from
yacht permits) led to fixes. A second set of 48, written on 6 October and measured once on the
build of that evening, scored 34%; its answers were then diagnosed and fixed too (below), so
it is development data as well. The final build was therefore measured on a third set of 60
questions, written after the last change by a writer that did not read the repository and not
looked at by anyone until the build was frozen ([`fieldtest/heldout3.jsonl`](fieldtest/heldout3.jsonl)):
8 places (3 near the phone), Vitalik's question from two more cities, 9 travel, 6 emergencies,
6 explanation, 6 comparison, 5 synthesis, 6 reasoning, 4 cryptography and 8 casual lookups. Their
reference answers were written by a frontier model with web search, as before, and every answer
was graded blind, twice, as the field test below describes.

Configuration: Qwen3.5 2B, the default model, with every collection the app offers in 1.4.0 (the
published versions, [DATASETS.md](../DATASETS.md#collections-the-app-offers), the Encyclopedia in
depth included), article depth on as in the app, on the desktop pipeline with the phone's code.
Every answer, reference and grade is in [`fieldtest/results-1.4.0/`](fieldtest/results-1.4.0/).

| Questions | Field Atlas, Qwen3.5 2B (share of the reference) | Optional Qwen3.5 4B | Factual errors per answer, 2B |
| --- | --- | --- | --- |
| 60 new held-out questions | 38% | 52% on the 55 questions without a phone position (the 2B scored 37% in that grading) | 1.98 |
| The 48 questions of 6 October (now development data) | 58% (was 34% before the fixes) | – | 1.19 |
| Our 61 questions (development) | 61% | – | 1.00 |
| Vitalik's 31 lookup questions from the vbuterin fork (development) | 54% | – | 1.19 |
| The 42 earlier held-out questions (now development data) | 54% | – | 1.21 |

By category on the new questions:

| Group | n | Field Atlas (mean /10) | Reference (mean /10) | Share of reference |
| --- | --- | --- | --- | --- |
| Vitalik's question | 2 | 6.50 | 8.00 | 81% |
| Casual lookups | 8 | 6.12 | 9.56 | 64% |
| Explanation | 6 | 4.92 | 9.67 | 51% |
| Emergencies | 6 | 4.42 | 9.42 | 47% |
| Places | 8 | 3.94 | 8.81 | 45% |
| Synthesis | 5 | 2.80 | 9.40 | 30% |
| Cryptography | 4 | 2.50 | 9.50 | 26% |
| Comparison | 6 | 2.17 | 9.17 | 24% |
| Practical travel | 9 | 1.83 | 9.11 | 20% |
| Reasoning with numbers | 6 | 1.42 | 10.00 | 14% |

The gap to the earlier sets is the cost of fixing what those sets contained: on questions whose
wording the rules do not cover, the 2B writes the answer, and it gets a train's arrival time, a hotel
bill or a time-zone day wrong, invents sauna etiquette and under-tips in Egypt. Answers the app
composes itself hold up: Vitalik's question from two more cities, casual lookups with the
Encyclopedia in depth, places in a named city. The 4B halves the distance on explanation,
comparison and cryptography and is the model to use on a phone that can run it.

**Article depth and the Encyclopedia in depth collection, kept.** An explanation also reads more
of its subject's article: the Simple English "Details" passages, and the sections of an English
Wikipedia article that fit the question ("Operation" for how, "Causes" for why), from a
49,822-article collection built for it ([DATASETS.md](../DATASETS.md#english-wikipedia-vital-articles-by-section-october-6)).
Casual lookups ("who won the First World War", "tell me about Japan") also read the articles they
name. Graded side by side with the same build without it, before the fixes below:

| Questions | Without | With |
| --- | --- | --- |
| Our 56 (without the near-phone ones) | 56.8% | 58.6% (+9.5 points, better on 9, worse on 4; errors per answer 1.14 to 0.99) |
| Vitalik's 31 | 45.3% | 55.2% (+29.5 points, better on 18, worse on 6) |

The gain is largest on lookups and explanations, where the English article's sections replace the
model's own account. On a Redmi 13C the extra reading costs about 20 seconds before the first word
when the collection is installed; the budget shrinks with the phone's measured speed.

**What the 48 questions of 6 October found, and what changed (October 6-7):**
- **Calculations.** Four reasoning questions scored 0-3: a fuel cost split among three people, a
  currency exchange with a commission, water for two days ("until the end of day 2" was read as one
  day) and a landing time across time zones all went to the model, which got the arithmetic wrong
  or refused. Calculators for a trip's fuel cost, exchange at a quoted rate less a fee, and a
  landing time from given UTC offsets were added; the water calculator reads the days as written;
  every number in a question must be used and the asking clause must name the calculation before a
  result is shown as complete. A review of the clock calculator found "when do I leave to arrive by
  9:00?" answered 09:25 (shown as calculated); the time's role is now read from the words next to
  it, and a trip that names places returns no calculation.
- **Places in a named city.** "Street food in Mexico City" quoted a street-food etiquette guide;
  "beaches in Cape Town" went to the model, which invented warm water; Madrid's museums missed the
  Prado. Listing-pack searches had title boosts that ranked "Cookstown — TIME" for "first time";
  they are off, searches stay inside the named city, and relevance is judged on a listing's own
  fields. A near-me question in Seoul found nothing within 15 km because every conversational word
  ("hungry", "aren't too expensive") had to appear in a listing; such words now order rather than
  filter, and the answer states which words no listing carries.
- **Travel.** "Flying into Bali" took "Flying" as the place; a guide without the section asked now
  falls back to its country's; journeys, airport guides, transit cards, dress codes and safety
  verdicts have their own rules; a question with no place reads the topic guide for its topic.
- **Emergencies.** Heat stroke with a collapsed, confused person opened with the guide's
  introduction and cramps; the section that names the person's signs now leads, long guides quote
  the paragraph for the case, and the model's unsupported "do not" lines and invented recipe amounts
  are removed. "Broke her ankle" and "swallowed a button battery" find their guides.
- **Explanations and lookups.** Choice questions ("DEET vs picaridin") and casual spellings
  ("whats the difference") now look up each subject; a one-line stub or a travel topic's overview is
  followed to the full article; the fitting section is quoted as the opening for how and why
  questions; short search terms match their own forms ("ears" no longer found earthquakes).
- **Phrases.** A phrasebook line is the whole answer only when it says each asked word and nothing
  else.
These fixes were made with the 48 questions in view, which is why they are development data and
the final figure comes from the third set.

## Field test: 61 questions of our own, and 42 held out (October 5-6)

The bounty's bar is an offline tool ">50% as good as internet search + frontier AI models" for
casual lookups and research while travelling, including explanation, comparison, synthesis and
reasoning. This test measures exactly that, on questions written for this project.

- **Questions** ([`fieldtest/questions.jsonl`](fieldtest/questions.jsonl)):
  - 2 in Vitalik's own wording: "best vegan restaurants in the city I am currently in", asked
    from Amsterdam and Melbourne;
  - 10 places, including vegan food in named cities, a pharmacy and a hospital near the phone,
    and museums;
  - 10 practical travel;
  - 8 emergencies;
  - 8 explanation, 6 comparison and 5 synthesis;
  - 6 reasoning with numbers;
  - 6 cryptography.
- **Held-out questions** ([`fieldtest/heldout.jsonl`](fieldtest/heldout.jsonl)): 42 more,
  written the same way and not looked at while the app was changed: 6 places (2 near the
  phone), 8 travel, 6 emergencies, 5 explanation, 5 comparison, 4 synthesis, 4 reasoning and
  4 cryptography. They show how much of a gain on our own questions carries over to new ones.
- **Field Atlas:** the desktop pipeline with the phone's code, Qwen3.5 2B Q4_K_M, seed 17,
  temperature 0.3, and every collection installed: Simple English Wikipedia, the Wikivoyage
  guides by section, Wikivoyage places, OpenStreetMap vegan places and essentials (the
  5 October rebuilds), biology, and Cryptography & Ethereum. Near-phone questions use the given
  position, read through the same path as a GPS fix.
- **Reference:** for each question, an answer written by a frontier model with web search,
  standing in for "internet search plus a frontier AI model".
- **Grading:**
  - Every answer is graded on its own, without the other answer to the same question. The two
    answers to a question always go to different graders, under opaque ids, in shuffled order.
  - Graders check facts and places on the web, score each answer 0-10 for a person asking on a
    phone (correct, complete, useful, and safe for emergencies), and list its factual errors.
  - The result is Field Atlas's total score as a share of the reference's.
  - Graders score the same text differently from run to run, by about 2 points on one answer.
    Runs are therefore compared against the reference total averaged over every graded run,
    and two versions of one change are graded side by side: each question's answers (version A,
    version B and the reference) go to three different graders in one session.

References and graders are frontier AI models of one family, so this is a development measure,
not an independent score.

### Result

| Build | Our 61 questions | 42 held-out questions | Factual errors in our 61 answers |
| --- | --- | --- | --- |
| First run (October 5) | 46% | not run | 93 |
| October 6, with the changes below except the last travel one | 53% | 39% | 62 |
| Same, with travel guides quoted as the answer | 55% | 40% | not counted |

The last row is estimated from the answers the change touches (travel answers), graded side by
side with their earlier versions. On the held-out set every other answer was checked to be
byte-identical between the two builds.

On our own questions Field Atlas now passes the bounty's 50% bar; on the held-out questions it
does not. Part of the gain on our questions came from fixes for the cases they contain, so
**40% is the honest estimate for a new question.** No answer sent the person online. On desktop
the median answer took 12-16 s.

By category, on our questions (October 6 build, before the travel change below, which added 8
points out of the reference's 91 on the travel questions when graded side by side):

| Group | n | Field Atlas (mean /10) | Reference (mean /10) | Share of reference |
| --- | --- | --- | --- | --- |
| Reasoning with numbers | 6 | 9.17 | 10.00 | 92% |
| Places | 10 | 5.70 | 9.30 | 61% |
| Emergencies | 8 | 5.88 | 9.88 | 59% |
| Vitalik's question | 2 | 4.50 | 8.50 | 53% |
| Explanation | 8 | 5.25 | 10.00 | 52% |
| Comparison | 6 | 4.00 | 9.67 | 41% |
| Cryptography | 6 | 4.00 | 10.00 | 40% |
| Practical travel | 10 | 3.50 | 9.30 | 38% |
| Synthesis | 5 | 3.00 | 9.60 | 31% |

On the held-out questions, answers the app composes itself score far above answers the model
writes: places near the phone 89% of the reference (2 answers), emergency guides with the
model's steps 51% (5), the model's own text with or without a quoted passage 32-39% (32).

**What changed (October 5-6):**
- **Safety:** an amount of medicine never appears in the model's own text; the app removes it and
  says to follow the package leaflet or ask a pharmacist. The model had turned a source's
  overdose figure, 150 mg/kg, into a recommended dose of paracetamol.
- **Instructions:** the model now builds its explanation on the saved passages, adds its own
  knowledge only for basic facts and never carries a phone number, law or price over from
  another place. With the code unchanged, this raised our questions from 52% to 54% and removed
  answers such as "hot and cold wallets do not exist".
- **Travel guides:**
  - a topic guide answers practical questions that name no place ("Passport — What if I lose
    it while travelling?", "Jet lag");
  - a beach or district with no guide of its own is read through the place it lies in
    ("Bondi Beach is a beach in Sydney" leads to Sydney's beach safety section);
  - each picked section is a different one (Italy's "Rules" section, which says to validate a
    ticket, no longer loses to a second passage of "Getting tickets");
  - a question that names no place no longer reads one destination's guide or map listings (a
    hardware wallet question had read "Kigali — Stay safe"; a debit card question "Iran — Buy",
    whose sanctions the model gave as general advice);
  - the guide's own section is quoted as the answer, without the model, unless the question
    has numbers to work out.
- **Reference lookups:** a subject named only inside a section ("Hardware wallets" in
  "Cryptocurrency wallet") or only mentioned in passing (danksharding) is found; a shorter piece
  of a name must match the word that narrows it (a leather "Wallet" article no longer answers a
  hardware wallet question).
- **Calculations:** the time between two clock times ("sunrise 6:10, sunset 18:40") is computed.
- **Places:** a restaurant's branches and duplicate map entries are listed once, so the answer
  offers different places; a pharmacy or ATM chain keeps every branch.

**Tried and not kept:**
- Quoting an encyclopedia definition as the whole answer, without the model: 47% against 50%
  for the same build with the model's explanation. A definition does not explain how or why.
- Showing an emergency guide without the model's steps for the situation: 53% against 60%.
- In the held-out run, quoting a travel guide also took over a calculation question and dropped
  it from 8 points to 0. Questions with numbers now keep the model. This guard was added after
  seeing that held-out result, so the held-out figure after it is no longer a clean test.

**What still falls short, most impact first:**
- **The 2B model's prose.** Where its text is the answer, it scores about a third of the
  reference on new questions: a correct quoted passage followed by a garbled or wrong
  explanation (the 2008 crisis dated to September 2008, the Silk Road as direct trade between
  China and Europe, a 13-hour time difference between New York and Singapore in summer). Qwen3.5
  4B answers better but about 2.5 times slower (next section), so it is an optional download.
- **Synthesis and explanation evidence.** Simple English Wikipedia articles are short; they
  define a subject but rarely explain causes or mechanisms in enough depth.
- **Map tags.** Places tagged fully vegan that serve dairy, and places outside the city they are
  filed under, still reach answers; the tags are as mapped.

### Qwen3.5 4B as an optional model (October 6)

The same code ran with Qwen3.5 4B Q4_K_M on every question that does not use the phone's
position: 56 of ours and 40 held-out. Each question's 2B answer, 4B answer and reference went to
three different graders in one session, twice, as above.

| Questions | Qwen3.5 2B | Qwen3.5 4B | Factual errors per answer, 2B / 4B | 4B better / worse |
| --- | --- | --- | --- | --- |
| Our 56 | 55.2% | 59.9% | 1.08 / 0.57 | 25 / 10 |
| Held-out 40 | 37.1% | 52.7% | 1.30 / 0.53 | 22 / 4 |
| All 96 | 47.6% | 56.9% | 1.17 / 0.55 | 47 / 14 |

The 4B gains most where the model writes the answer: explanation 43% to 63% of the reference,
comparison 38% to 55%, synthesis 34% to 44%. Two graders scoring the same answer differed by
0.39 points on average over 261 answers. The 4B's held-out share is only just above the bar, and
40 questions is a small sample.

On a Redmi 13C (6 GB), measured with llama-bench, the 4B read 6.6-7.3 tokens a second and wrote
2.3, against 16-17.5 and 5.2-5.45 for the 2B. At those speeds the 70 answers that use the model
take a median 204 s of model time instead of 81 s (90th percentile 336 s against 130 s). Keeping
the instructions already read ([`scripts/patches/README.md`](../scripts/patches/README.md))
saves 396 tokens of reading from the second answer on: about 20 s for the 2B, as measured on the
Redmi, and by the same arithmetic 55-60 s for the 4B. An Infinix X6840 (4 GB) was killed twice
running the 4B, and the third attempt restarted the phone.

In the app on the same Redmi, the 4B loaded and 0.2 s later Android killed the app for memory,
after closing 49 other processes during the load. The 4B needs about 3.5 GB resident: llama.cpp
keeps a 2.7 GB repacked copy of the weights besides the mapped file, plus 0.58 GB of context and
compute buffers.

So the 2B stays the default, and the 4B is an optional download in Library on phones that report
at least 7,000,000,000 bytes of memory, which 8 GB phones do. No 8 GB phone was available to test
it on. The app does not load the 4B on a phone with less memory, and if the app ends while
loading it or in the 15 s after, the next load uses the 2B, when installed, and makes it the
selected model; Library says why.

## 1.3.0 review against the bounty bar (October 4)

The [24 frozen questions](#24-question-release-suite) were rerun unchanged through the 1.3.0
desktop pipeline with the same model (Qwen3.5 2B Q4_K_M, seed 17), the same five collections and
the same settings as the [1.2.0 run](#v120-24-question-suite-october-2). 15 answers are identical
to 1.2.0; the other 9 differ because 1.2.1 changed the strict-answer policy and some retrieval,
not because of 1.3.0 (1.3.0 does not change any library-research prompt). The reviewer is the
author's assistant, not an independent human; no online baseline was run.

Each answer was judged twice: **correct** (pass, partial or fail on facts, citations and honesty)
and **useful compared with internet search plus a frontier model** answering the same question
(1 = about as useful, ½ = useful but clearly weaker, 0 = not useful).

| Group | Correct | Useful vs. online frontier |
| --- | --- | --- |
| Travel (4) | 3 pass, 1 fail (London lists an Oxford museum) | 2 of 4 (½ each: no ranking; Tokyo has no addresses) |
| Explanation (4) | 2 pass, 2 partial | 3 of 4 |
| Comparison (4) | 2 pass, 2 partial | 3 of 4 |
| Synthesis (4) | 2 pass, 2 partial | 3 of 4 |
| Limits (4) | 4 pass (honest) | 1½ of 3 (location needs a phone; “today's study” is 0) |
| General (4) | 2 pass, 1 partial, 1 fail | 2½ of 4 |
| **Total** | **15 pass, 7 partial, 2 fail** | **15 of 23 = 65%** |

Answers about attached files and emergency questions are outside this suite; they were checked on
desktop and on a Redmi 13C and are reported in the [1.3.0 release notes](releases/field-atlas-1.3.0.md#verification).

What keeps answers below the bar, most impact first:

1. **Model explanations without sources carry most of the errors.** “Yeast lacks many metabolic
   pathways of eukaryotic cells” (yeast are eukaryotes), senescent cells “generally eliminated
   before becoming permanent” (they accumulate with age), a muddled battery example. These are 2B
   model limits; the 4B model did better on the attachment fixtures (34/35 vs 32/35) but is about
   ten times slower on a low-end phone (about 2.5 times as measured on October 6; see above).
2. **Relevant saved passages are often not found.** Autophagy, study types, yeast-to-human and
   the speed question retrieved no sources although the Encyclopedia covers these topics, so the
   model answered alone and unverified.
3. **Travel answers cannot rank and sometimes misfile places.** “Best” lists are unranked, Tokyo
   listings have no addresses, a zoo was offered as a museum and an Oxford museum as a London one.
   (Misfiled museums and missing locations are addressed [below](#after-the-retrieval-and-travel-fixes-october-4).)
4. **Some answerable questions are only partly answered.** “Open right now” lists recorded hours
   without using the phone's clock to say which places those hours mark as open (addressed below).
5. **One question was answered about the wrong topic** (“what can an offline app know” answered
   from a navigation-app article).

Report: `build/desktop-evaluation/v130-suite24/report.json` (ignored build directory).

### After the retrieval and travel fixes (October 4)

The same 24 questions were rerun with the same model, seed, collections and settings after
three changes: museum questions keep only places mapped as museums or galleries in the named
city, “open now” checks each place's recorded opening hours against the phone's clock, and
places without an address show coordinates. An “Explain X and distinguish …” question now
finds the topic's overview passage. 19 answers are identical to the first 1.3.0 run; the
5 that changed were graded again by the same reviewer:

| ID | Correct | Usefulness | What changed |
| --- | --- | --- | --- |
| travel-london | fail → partial | 0 → ½ | Four London museums with what each source says they focus on; no Oxford museum. The RAF Museum's directions are garbled (so is the source's), and “art and design” for V&A East is not in its listing. |
| synthesis-trip | partial → partial | +½ | A saved fully vegan stop (Kopps) and a real museum (Musical Instrument Museum, Tiergartenstraße 1). The Kopps menu is paraphrased confusingly. |
| limits-hours | pass → pass | ½ → 1 | Two of six places marked open at the clock's time, with closing times; places without readable hours are not guessed. |
| travel-tokyo | pass → pass | unchanged (½) | Coordinates for every listing (none has an address in the map data); still no ranking. |
| biology-autophagy | partial → partial | unchanged | Four relevant sources are now found, but the strict mixed-answer rule keeps a citation only on verbatim quotes, so the answer still shows as an unverified model explanation. The model's draft cited them correctly. |

| | Correct | Useful vs. online frontier |
| --- | --- | --- |
| First 1.3.0 run | 15 pass, 7 partial, 2 fail | 15 of 23 = 65% |
| After these fixes | 15 pass, 8 partial, 1 fail | 16½ of 23 = 72% |

The first review recorded usefulness per group, not per question; the changes above are the
reviewer's judgement of how much each answer improved. These questions were used to find the
problems the fixes address, so 72% is a development result on known questions, not a held-out
score, and the reviewer is still the author's assistant rather than an independent judge. The phone's clock is used as local time: for a
named city the answer says it assumed the phone is set to that city's time.

Reports: `build/desktop-evaluation/v130-final/report.json`, and `hours.json` for the open-now
answer after holiday days and open-ended closing times (“10:00-18:00+”) were made readable
(ignored build directory).

### Speed: thread count kept, smaller library prompts rejected (October 4)

On a Redmi 13C the model reads a prompt at about 17–18 tokens a second and writes about 4.5, so
the prompt is most of the wait (a 1,127-token prompt took 65.6 s to read). Raising inference from
four to six threads brought the first word of the same question from 94.7 s to 71.7 s and was
kept ([measurements](../scripts/patches/README.md#thread-count)).

Library prompts are not sized to the phone's reading speed, as file evidence is. Capping them was
tried on the 24 questions, simulating this phone's learned speed (desktop runs with
`FIELDATLAS_DESKTOP_PREFILL_TPS`):

| Target wait | Prompt tokens (24 questions) | Answers changed | Worse | Better |
| --- | --- | --- | --- | --- |
| none (current) | 21,697 | – | – | – |
| 60 s | 15,718 | 10 | 2 (trip loses its vegan stop; two-studies answer garbled) | 1 (vegetarian vs vegan) |
| 40 s | 11,611 | 13 | 5 (Jakarta: "no hotels listed"; trip gives a museum's address for an ice-cream shop; telomeres; autophagy; vegetarian) | 2 (senescence, battery) |

Passages are cut in rank order, and a question with several parts (a food stop and a museum)
ranks the second part's passages lower, so they go first. Neither cap was kept. Reports:
`build/desktop-evaluation/v130-budget/` and `v130-budget60/` (ignored build directory).

## Attachment reasoning feedback (October 3)

An offline 8 GB emulator test of the 1.2.0 release reported seven attachment questions: three
passes, two mixed and two failures. File attachments, source previews and history worked
without crashes. The failures: a citation pointed at Revision A while the claim came from
Revision B; a maintenance hold was described as watered "despite the hold"; a question for a
Wi-Fi password got unrelated travel details; and 21:30 was converted to 10:30 PM.

Synthetic files reproducing these cases are in
[`fixtures/attachment-reasoning/`](../fixtures/attachment-reasoning/README.md), and the desktop
runner now accepts text attachments (`--attachments`) through the app's own attachment path.
The swapped citation reproduced on the release code; the app numbers attachments in display
order, so the model, not the numbering, attached the wrong source.

Changes, each with tests in `AnswerChecksTest`:

- **Citation repair.** For evidence-only answers, a citation moves to another source only when
  every distinctive detail in its sentence (labelled identifiers such as "Revision B", part codes
  such as K-9, numbers) is unique to that one source and none is unique to the cited source. A
  colon does not split the sentence, and a list item carries the line that introduces its list,
  so "Revision B service interval: 400 hours [S2]", and the same value as a bullet under
  "Revision B:", keep their citation rather than moving to Revision A, which would make the
  wrong 400 look sourced.
- **Missing items.** A question for a password, phone number or email address that no file
  contains gets a one-sentence reply that the files don't mention it, without running the model.
  Presence is judged by the item's own noun: "free Wi-Fi in the lobby" does not count as a Wi-Fi
  password. Addresses, prices, times and other items can be written in too many ways to call
  them absent, so they are left to the model.
- **Time pairs.** A 12-hour time written beside a 24-hour time the source states is corrected
  when they disagree ("10:30 PM (21:30)" becomes "9:30 PM (21:30)").
- **Requested 12-hour times.** When the question asks for 12-hour time, the app writes the
  conversion beside every 24-hour time in the answer, outside quotations.
- **Exception note.** A decision question about files that contain exception wording
  ("exception", "hold", "unless", "override") ends with a note to check each decision against
  those exceptions. It does not judge the answer.
- **Instructions.** Answer only what was asked, under 80 words, with no notes or reminders;
  decide several items one by one (rule, then any exception or hold, then the decision); keep
  numbers and negations as written; do not substitute other details for a missing item. The
  attachment instructions are 1,392 bytes against 1,356 in 1.2.0, within the 4,096-token limit.

Desktop runs on the seven fixture questions (Qwen3.5 2B, seed 17, author review):

| Question | Release code | With these changes |
| --- | --- | --- |
| Helios serial 2345, compare revisions | fail: Revision B cited as [S1] | pass: cited [S2], K-9, M5, 600 h |
| Helios serial 1500 | pass | pass |
| Garden irrigation, East and West | pass | partial: right decisions, one false remark that both plots have holds |
| Wi-Fi password at Cedar Lodge | partial: long non-answer asking for more files | pass: one-line gap reply |
| Phone number for Cedar Lodge | pass | pass: one-line gap reply |
| Last shuttle, 12-hour format | pass | pass: 21:30 (9:30 PM) |
| Train 90 minutes late | fail: Station Hotel "can assist with transportation" | partial: right conclusion, wrong arrival time (22:00 for 22:40) |

Remaining weaknesses are the model's: time arithmetic and occasional false side remarks. The
checks above do not detect those. An independent review of these checks found four cases that
earlier versions got wrong: a wrong value under the right revision label was moved to the other
revision, both on one line and as a bullet under a "Revision B:" line; a stated address was
reported missing; and a mention of Wi-Fi without a password skipped the gap reply. Each is now a
regression test, and the checks were narrowed as described.
The reported failures are improved, not resolved: the model can still state wrong values. These are fixed development questions, not a held-out score.

On a Redmi 13C (Android 13, 6 GB, airplane mode, six collections) the same build answered the
Wi-Fi password question with the one-line gap reply in 0 s; the shuttle question with 21:30
(9:30 PM) [1] but a wrong aside ("one hour after" 21:10); the garden question with the right
per-plot reasons and an opening sentence saying both plots should be watered; and the Helios
question with the right final values and citations (K-9, M5, 600 h [2]) after a muddled middle
that attributed Revision A's values to "this revision" and called 2345 "greater than 2999". The
phone samples at the same temperature as the desktop with a random seed, so answers vary
between runs. The same session found that opening the file picker let MIUI kill the app; see
the [release audit](compliance/release-audit.md#v121-release-handoff-3-october-2026).

### Repeated runs

One run per question hides how much a 2B model varies, so the desktop runner now takes
`--seeds` and `--temperature`, and `fixtures/attachment-reasoning/score.py` scores each answer
with rules taken from the files (keyword checks for the reported mistakes, not proof of a
correct answer). Seven questions, five seeds each, temperature 0.3 as on the phone:

| Build | Passing answers | Garden (exception) | Shuttle (12-hour) |
| --- | --- | --- | --- |
| 1.2.0 instructions with the checks above, before the changes below | 27/35 | 2/5 | 1/5 |
| Shorter answers, per-item decisions, app-side 12-hour times | 32/35 | 3/5 | 5/5 |
| Same at temperature 0.1 | 33/35 | 3/5 | 5/5 |
| "Quote the exception first" instruction (not kept) | 30/35 | 1/5 | 4/5 |
| Qwen3.5 4B instead of 2B, same instructions | 34/35 | 4/5 | 5/5 |

The shipped build is the second row. The garden question is a model limit: the 2B model applied
the maintenance hold in three of five runs whatever the instructions. Qwen3.5 4B did better but
took a median 42 s per answer against 4 s for 2B on the same Mac, so it would take minutes on
the test phones; the bounty sets no parameter limit, but it does require usable speed. 1.2.1
keeps 2B and adds the exception note above.

Rerun on the final 1.2.1 build (versionCode 16), same phone and settings, one run each: the
Helios answer cited Revision B for K-9, M5 and 600 h, with one wrong aside that Revision A "also
specifies these components"; the garden answer said not to water East because of the hold and to
water West, with no contradictory sentence; the shuttle answer gave 21:30 (9:30 PM) [1] with no
wrong aside; the Wi-Fi question gave the gap reply in 0 s. One run per question does not show the
earlier slips are gone, only that they did not recur here.

## v1.2.0 24-question suite (October 2)

The [24 questions below](#24-question-release-suite) were run once, unchanged, through the
app's own research pipeline on desktop (Kotlin orchestrator, keyword retrieval, prompt
packing, venue routing and answer attribution) with Qwen3.5 2B Q4_K_M and five collections:
Encyclopedia, Travel guides, Travel places, Vegan places and Biology & Longevity. The
author reviewed each answer against its sources; this is not an independent human review.
Desktop has no location fix and no biology vector search, and a phone is several times
slower, so the times below are not phone measurements.

| Group | First run | After search fixes |
| --- | --- | --- |
| Travel (4) | 3 pass, 1 fail | 3 pass, 1 fail |
| Explanation (4) | 3 pass, 1 partial | 3 pass, 1 partial |
| Comparison (4) | 4 pass | 4 pass |
| Synthesis (4) | 1 pass, 3 fail | 3 pass, 1 partial |
| Limits (4) | 2 pass, 2 partial | 4 pass |
| General (4) | 2 pass, 2 fail | 2 pass, 1 partial, 1 fail |
| **Total** | **15 pass, 3 partial, 6 fail** | **19 pass, 3 partial, 2 fail** |

Clear passes went from 15/24 (62.5%) to 19/24 (79%); counting partials as half, from
16.5/24 (69%) to 20.5/24 (85%). The search fixes were made after reading the first run's
failures, so the second figure is a fixed-question development result, not unseen
validation. Each fix has a regression test in `SuiteFailureRegressionTest`:

- Instruction words ("summarize", "separate", "findings") no longer outrank the subject
  in keyword search; the rapamycin question now retrieves three relevant papers.
- Questions about today's news or the latest research get a fixed, honest reply without
  running the model.
- A food-stop question that also asks for a museum keeps the Eat listings, and a trip
  question naming a city also searches the place collections for fully vegan food.
- Papers that share only research vocabulary ("study", "outcomes") with the question are
  no longer treated as relevant.
- "Open right now" answers say plainly that live hours cannot be confirmed, then list the
  saved places with their recorded hours.

Remaining failures and partials:

| ID | Result | Problem |
| --- | --- | --- |
| travel-london | fail | Lists Oxford's Bate Collection as a London museum and conflates two others |
| general-offline | fail | Answers about GPS navigation apps instead of what an offline app can know |
| biology-telomeres | partial | Right distinction between marker and cause, with muddled supporting claims |
| synthesis-trip | partial | Correct saved vegan stops (Daizu, Chay Viet); the museum suggestion is wrong |
| general-energy | partial | Capacity and power defined correctly; the practical example has errors |

Desktop time per answer: 1.5–1.8 s for place lookups, 11–35 s for model answers.
Reports with every answer, packed source and model request are kept under the ignored
`build/desktop-evaluation/v120-suite24/` (first run), `v120-suite24-fix1/` and
`v120-fix2-subset/`. No online baseline was run against this suite, so it does not
establish the bounty's greater-than-50-percent comparison.

## Answer root-cause checks (October 1, local development)

Artifacts are under `build/reasoning-diagnosis/`; none are a held-out accuracy score.
Raw requests and responses were retained, including rejected experiments.

- Excerpt selection used exact tokens even though retrieval accepted word variants.
  A regression test reproduced selecting introductory history instead of the
  explanatory paragraph. Excerpt paragraph/sentence ranking now uses the existing
  retrieval term matching, keeping adjacent qualifications and original source text.
- The original reference pack had the buoyancy explanation but no semantic index.
  The new index retrieves it at approximately 0.79 cosine similarity. A later
  lexical gate wrongly discarded strong semantic matches; that gate now honors
  the pre-existing 0.78 threshold. Travel exclusions and explicit overview/comparison
  constraints remain. Indexes of at most 10,000 entries may be searched without
  matching a discovery-summary keyword; larger indexes retain scope routing.
- `semantic-app/report.json` exercises actual Kotlin retrieval and generation on
  eight existing reference questions plus the Berlin lookup. It retains the live
  information refusal and travel behavior. The buoyancy core explanation improves,
  but the model reverses the supplied boat/raft distinction. Other generated
  inaccuracies remain. Better retrieval is not sufficient to certify generation.

Three inference experiments were stopped early after failures, not completed
benchmarks: enabling thinking produced no final answer within 1,536 tokens on
all six completed thinking trials (`paired.json`); a shorter instruction prefix
reproduced the LAN contradiction (`prompt-paired.json`, three completed pairs);
a native 256-token reasoning budget still produced dimensional errors and
irrelevant claims (`bounded.json`, two completed trials). No candidate setting
was promoted. [Qwen's model card](https://huggingface.co/Qwen/Qwen3.5-2B) documents
the thinking switch and warns about thinking loops in this model size.

An additional model-only substitution recorded 18 responses: the same seven
automatic-reference requests and two previous complete-context controls, each
with seeds 17 and 29. The official LiquidAI LFM2.5-1.2B-Instruct Q6_K artifact
matches SHA-256 `c5e895c191a066f6b26a8f09f10e94cdb799e579216f87df61a7e27beacd9a2b`
at revision `8ed288026e23958ad9dfa92d53ed773a8eee7125`.
`alternative-model.json` preserves inputs, sampling settings and complete outputs.
The LAN answers improve, but database generalization, cell-division wording and
heat-transfer claims still fail review. It is **not** a validated default replacement,
and was not installed on the phone. Its [model card](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct)
recommends retrieval tasks but cautions against knowledge-intensive use.

For semantic diagnostics, `scripts/research/run_desktop_research.py --vector-fixture FILE`
accepts frozen query vectors, embedding metadata and a database SHA-256. The actual
Kotlin vector search/fusion/filtering still runs; source selection is not supplied
by the fixture. Encoder queries are replayed, not recomputed by this desktop adapter.
Unknown queries fall back to keywords. This is not full Android inference parity.

`benchmarks/questions.json` freezes 18 historical questions aligned with the former Field Atlas Reference pack: three each for factual retrieval, explanation, comparison, synthesis, multi-step reasoning, and unanswerable requests. That pack is no longer bundled, so a fresh run with only the current model or with the optional biology pack is a different test configuration. Do not compare its score to Reference-pack results as if the evidence were unchanged. Freeze offline outputs before obtaining the named online baseline so baseline knowledge cannot influence the local run.

Each answer is scored for required evidence phrases, prohibited claims, citations, and appropriate abstention. This mechanical rubric is deliberately reproducible but cannot replace human review of correctness or prose quality.

The published 2026-09-24 free-router result is a historical v1 exploratory run,
not a score for the current source or current prompt set. A comparable current result
requires a fresh complete device export from the exact build under review and a
named online baseline over the same prompts. No greater-than-50-percent claim is
established by the existing artifacts.

```sh
python3 benchmarks/score_results.py \
  --benchmark benchmarks/questions.json \
  --offline build/evidence/fieldatlas-benchmark-DEVICE-TIMESTAMP.json \
  --baseline build/evidence/baseline.json \
  --output build/evidence/paired-score.json
```

Report every response, category mean, overall mean, and the offline-to-baseline ratio. A zero baseline denominator produces `null`, never infinity. The scorer also marks the result as a mechanical measurement rather than an independent quality judgment.

## Capture a local-device run

### Current research-quality check (2026-09-29)

The desktop check uses the saved Qwen3.5 2B model with Biology and Travel,
covering a history overview, biology comparison, Berlin vegan listings, and
an unsupported source-specific question. Its current local output is
`build/desktop-evaluation/report.json`; completion is not an accuracy score.

Regression fixes cover possessive overview questions, source-request boilerplate,
and author names in structured scientific headers being mistaken for topical
evidence. Original source text and citation attribution are preserved. The
relevance checks remain heuristics, not semantic verification.
When a source-only request has no matching evidence and no attachments, the app
now returns a short evidence-gap response without generating a model answer.

The history output still fails manual quality review after irrelevant passages
are removed: unverified model text contains internal inconsistencies and exceeds
the requested length. Do not claim that every answer is research-quality or
that these checks establish release/bounty accuracy. Desktop keyword retrieval
does not validate device inference, vector retrieval, or all languages.

### Desktop development loop (no phone)

#### Claim-support development suite

`tools/data/claim_support_cases.json` contains eight project-authored, fictional CC0
cases: direct support, partial support, negation, conflicting sources, wrong-source
citations, stale evidence, absent evidence, and partially unsupported compound
claims. Each includes review criteria and labelled claim/citation probes. Labels
describe support by the supplied passages, not whether a claim is true in the world.
An in-range citation can still be irrelevant, contradicted, or insufficient.

```sh
python3 -m unittest discover -s scripts/tests -p 'test_claim_support.py'
python3 scripts/research/run_claim_support.py --output-dir build/claim-support/new-run
```

Use a new output directory for each run; existing directories are never overwritten.
`--prepare-only` builds the local fixtures without inference. `--answer-policy`
accepts `app`, `source-only`, or `source-partial` for the existing diagnostic modes.
No downloads or phone installation are performed. The tiny database is a test
fixture, not a distributable knowledge pack. Its example.invalid source URLs are
identifiers, not websites to fetch.

The runner reuses the desktop app pipeline with manually selected passages, saves
a fixture snapshot, and produces `report.json` plus `review.json`. Review is bound
to fixture/report SHA-256 hashes. The review sheet starts **unreviewed**, even for
completed runs. For each criterion, record pass/fail/uncertain with answer spans
and source IDs; inspect the actual prompt to detect any relevance/packing omissions.
Assess raw claim support, completeness, contradictions, and displayed attribution
separately. Probe citation indices use fixture passage order; model citation indices
use the actual packed prompt and may differ. Never substitute marker counts,
substring overlap, or model self-grading for semantic review. These known development
cases do not establish bounty accuracy or generalization to fresh questions.

Initial app-policy baseline: `build/claim-support/app-baseline/report.json`
(SHA-256 `82823eeb9e73f2b471a5daf24d4eb14e6b12ac82d8ab64b2b62036b2133d47e6`),
fixture snapshot SHA-256
`ffc64ab8642b39d12d11fb8227c743a063262e998cd5a48485b7845fb82c1349`.
All eight executions completed; agent inspection found the requested core facts
and uncertainty handling present. This is provisional inspection, not independent
human validation; the generated review sheet remains unreviewed. The supplied
short passages reached the answer prompts unchanged. The empty-evidence case
used the deterministic evidence-gap response after a search-planning call, so its
planner output must not be mistaken for a generated answer.

Observed limitations to retain in future comparisons:

- The parcel answer correctly compares 7 kg with 4 kg, then adds an unnecessary
  caveat that the sources do not explicitly compare their weights.
- Mixed-answer rendering removes paraphrase citations and leaves awkward
  wording such as "According to Source,". The conflicting-notes question follows
  the app's existing source-only route and retains correctly mapped citations.
- The lamp quotation lacks a citation on its own line; the sensor quotation uses
  an inline heading. Neither becomes a verified clickable quotation under the
  current mixed-answer format rules.
- The bridge answer refuses to infer today's opening status, but introduces a
  current-date assertion not supplied by the fixture. Correct abstention on the
  main question is not proof that every sentence is evidence-supported.

These minimal fixtures isolate basic behaviors. Passing their requested content
checks cannot erase the failures on longer real-source excerpts recorded in
`DATASETS.md`. Before promoting a change, compare both suites and have claim-level
review completed; do not tune only to these fictional examples.

Attribution follow-up: `build/claim-support/attribution-fix/report.json` reproduces
all eight raw baseline outputs exactly. Rendering now recognizes inline source
headings and links an unnumbered quotation only when its complete text has one
unique exact match in the available passages. Explicit incorrect citations are
never reassigned; ambiguous, fabricated, incomplete, and overflowing-number
references cannot create inferred links. The lamp and sensor cases now have
correct quotation links, and the parcel answer no longer leaves "According to
Source," after removing an unsupported paraphrase citation. These are attribution
and display fixes, not a model-accuracy improvement. Unit tests cover these cases.

The Biology/Travel regression at
`build/desktop-evaluation/final-local-review/report.json` retains Berlin's four
dated cited listings and the evidence-gap answer for a fictional current fact.
The mitosis/meiosis explanation remains unverified; its categorical statement
about recombination is not a general quality pass. The ordinary JVM suite, release
build, 34 script tests, 14 tools tests and 12 pack-format tests passed in this local
review. Device behavior remains untested because installation was deferred.

The initial larger-model comparison attempt could not complete its download. It selected
[bartowski's Qwen3.5 4B Q4_K_M](https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF),
pinned to revision `4168f45a16a1290d65a4ec0fa312ae917a4c15d6`, with expected
3,013,027,808 bytes and SHA-256
`13c16f426047e2de38cd075bdade4a7bcbc8c774384876f677740cda65f8a983`.
The direct download failed with HTTP/2 stream cancellation; the resumable-provider
attempt stalled at approximately 10 MB and was stopped. Neither incomplete file
was loaded or treated as checksum-verified. Provenance and a checksum-verifying
retry script remain in ignored `build/model-cache/`. No claim about 4B answer
quality, device performance or compatibility follows from this attempt. The app's
recommended 2B model, model URL and checksum remain unchanged.

### Completed 2B/4B desktop diagnostic

The follow-up download completed using bounded parallel byte ranges, followed by
the full-file size and SHA-256 checks above. The verified 4B GGUF remains private
under `build/model-cache/`; it is not a new app default or published download.
The same local server loaded it successfully. Its log reports unused additional
MTP tensors, so this run does not establish MTP support or Android compatibility.

Both models completed eight authored support fixtures and seven known
general-reference development questions. Fresh 2B baselines and 4B outputs live
under `build/claim-support/model-comparison-{2b,4b}/` and
`build/general-reference/evaluation/model-comparison-{2b,4b}/`. Each suite's
`model-comparison-controls.json` confirms matching app/tool hashes, database
hashes, answer policy, llama commit, and **identical actual requests for all 15
questions**, including supplied passages and sampling settings. The server binary
was unchanged (`3c8deb8c4971ed4fa10577950c271dd8b7148b20df214baa80d5c3a6c03cbc0d`).
The plan was frozen before 4B generation in
`build/model-cache/comparison-plan.json`.

Qualitative agent review, not independent human scoring or an accuracy benchmark:

- Heat transfer: 4B supplies all three examples and the correct vacuum mechanism;
  2B omitted a radiation example and contrasted photons with particles.
- Local networks: 4B still introduces confusing overlay/address-resolution
  language and an unnecessary gateway condition. This is not a clean answer.
- Agriculture: 4B removes the baseline's fabricated quotation and explicitly
  rejects universal benefit, but still makes broad historical generalizations.
- Current earthquake/road closures: both correctly return the same evidence gap.
- Water cycle: 4B explicitly explains the release of latent heat in condensation,
  improving the baseline's explanation.
- Database/spreadsheet: 4B retains the misleading emphasis on semi-structured or
  unstructured data as the defining database category. The failure is unresolved.
- Genes/environment: 4B drops the baseline's unrelated CO2/heart-attack material.
  Its percentages appear in the supplied passage, but its framing of heritability
  still needs care; source agreement is not proof that an explanation is complete
  or scientifically precise.
- Small support fixtures: both retain the main facts, conflicts and unknowns.
  4B is often more repetitive; its sensor answer adds "only" beyond what the note
  establishes. The lamp/sensor quotation links seen with 2B are not reproduced
  in 4B's visible format, while 4B provides a linked historical bridge quotation.

In these single desktop runs, model-call time for the six generated research
answers was approximately 6–8 seconds with 2B versus 27–46 seconds with 4B. These
are observations, not controlled device-speed benchmarks: there were no repeated
timing trials, thermal controls, or Android memory/battery measurements. The
no-evidence case's model-call time is planning, not a generated final answer.

Decision: do not promote 4B or the candidate reference pack. Larger capacity helps
some cases but does not resolve evidence relevance, overgeneralization or output
format compliance. Keep the default model unchanged and prioritize evidence
selection and claim-level review before any model-default decision. Phone testing
and installation remain explicitly deferred. All owned download/inference
processes exited after this comparison.

The claim-support runner now accepts `--model /absolute/path/to/model.gguf` for
reproducible alternatives; omitting it preserves the existing default. Its new
argument-forwarding test passes with the full 35-test script suite. The 14
knowledge-builder tests also pass. No app production code changed in this
comparison, and no new release was published.

### Rejected grounding-policy candidates (October 1)

Two additional opt-in desktop policies were tested after the difference-question
retrieval fix, with 2B and the same paragraph-pack database. They are **diagnostic
only**, not production instructions:

- `--answer-policy evidence-first`: request exact quotations before a short
  source-bounded explanation. Reports:
  `build/general-reference/evaluation/evidence-first/report.json`.
- `--answer-policy bounded-summary`: remove the quotation task and request a
  short explanation of supported parts plus explicit gaps. Reports:
  `build/general-reference/evaluation/bounded-summary/report.json`.

Both ran all seven known development questions. Checks confirmed identical
production-code hashes, model/database identities, generation settings, and
packed evidence and question suffixes against `qualified-difference-subjects`.
Only the mixed-answer instructions changed; source-only requests, planning and
model-only calls were untouched. Unit tests protect those boundaries.

Neither candidate is acceptable for promotion. Evidence-first correctly notices
the missing spreadsheet definition but alters quotations with ellipses or changed
wording, retains irrelevant genetics material and adds questionable agriculture
claims. The existing exact-quote attribution gate keeps those altered quotations
uncited; it does not validate the remaining explanation. Bounded-summary produces
a direct contradiction in the heat answer: "All three mechanisms can occur in a
vacuum". It also supplies spreadsheet claims despite the missing evidence rather
than identifying that gap. Shorter answers and more source-related wording are
not correctness improvements by themselves.

The app's answer policies, model and attribution behavior remain unchanged. These
negative results do not complete the grounding work. A future claim-checking
stage needs its own support/contradiction/gap tests; a matching citation number or
quoted substring must not be treated as verification of generated paraphrases.
No new app release or phone installation follows from this experiment.

### Experimental claim-support checker

`scripts/research/check_claim_support.py` tests a separate local inference call for one
claim and its cited passages. It does not generate an answer, split an answer
into claims, rewrite claims, or run inside the Android app. Expected labels and
review criteria never enter inference requests. Default fixtures combine the
13 probes in `tools/data/claim_support_cases.json` with 13 additional probes in
`tools/data/claim_checker_cases.json` (scope, conditions, uncertainty, contradictions,
conflicting reports and source-embedded instructions).

```sh
python3 scripts/research/check_claim_support.py \
  --output-dir build/claim-checker/new-run
```

Use a new output directory for every run. `--fixtures` accepts repeatable authored
fixture files and `--model` selects a local GGUF without changing the app default.
Reports retain exact requests, raw responses, fixture copies/hashes, model and
server hashes, and partial results on failure. The runner owns a loopback-only
server and stops only that process. A busy port is rejected; `--port` can choose
another port without reusing a running server.

The checker rejects invalid source numbers before inference. Its response parser
accepts only a strict three-way JSON verdict: supported, contradicted or
insufficient. Supported/contradicted verdicts require exact quotations from the
specified passages. Malformed JSON, duplicate keys, fabricated quotations and
unmapped source IDs become invalid responses, never support. **This validates
format and attribution, not entailment.** A model can still quote a real passage
while drawing an unsupported conclusion. Such failures are counted explicitly.

Two runs use the original 26 authored probes at
`build/claim-checker/{2b,4b}-first-pass/report.json`. Another 12 probes were frozen
in `build/claim-checker/packed-regressions.json` using the exact excerpts from six
known research requests, with the originating report hash retained. Their runs
are at `build/claim-checker/{2b,4b}-packed-regressions/report.json`. The source
passages remain private build artifacts, not a new published dataset. These are
development checks, not independent validation or a score for whole answers.

Completed comparison (same requests, labels, fixture hashes and checker code for
both models; no prompt tuning between models):

| Model | Checks | Full-label matches | False support | Missed supported claims | Invalid responses |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2B | 38 | 24 | 5 | 3 | 5 |
| 4B | 38 | 33 | 0 | 1 | 2 |

Each total includes one deterministic invalid-citation check, rather than an
inference call. "False support" means an unsupported or contradicted claim was
accepted after response validation. Invalid responses fail closed and count as
misses when the claim was supported; the columns therefore overlap. Full-label
matches also distinguish contradiction from insufficient evidence, which neither
model handles perfectly.

2B falsely accepts an undated capacity as the latest value, an unmeasured pump
as silent, a subtype property as universal, thermal conduction/convection through
a perfect vacuum, and a primary energy source as the only possible source.
The first three occur despite exact, correctly attributed quotations. It catches
the simplified fictional mechanism contradiction but fails the comparable
real-excerpt case, so short-fixture success does not generalize to longer context.

4B produces no accepted false support in these cases, but a supported database
claim fails because its quotation introduces an ellipsis that is not in the
passage. Its subtype-gap response also violates the response contract. This is
promising development evidence, not verification reliability: the cases were
authored/reviewed by the agent, the actual research requests are already-known
regressions, and neither claim extraction, answer coverage/relevance, independent
holdouts nor phone performance was tested. A cited source can itself be wrong.

Decision: retain the checker as an experimental development tool. Do not enable
automatic answer acceptance, rewriting, deletion or a "verified" badge. The app
continues using its existing 2B model and attribution behavior. All owned local
inference processes stopped after the comparison. The 41 script tests pass,
including strict verdict parsing and false-support accounting; no new app build,
publication or installation was needed for this test-only work.

### Frozen inference controls (October 1)

After consulting [Qwen's model card](https://huggingface.co/Qwen/Qwen3.5-2B#best-practices)
and the [Sufficient Context study](https://arxiv.org/abs/2411.06037), two known failures
(database/spreadsheet and algorithm/program) were frozen before generation. This is
diagnosis, not an unseen accuracy benchmark. `scripts/research/run_inference_controls.py`
replays exact final requests, retaining complete raw responses, input/model/server
hashes, token budgets and seeds. It never promotes a model or assigns an accuracy score.

The first matrix has three conditions (model-only, automatic retrieval, manually
selected passages still passed through production filtering/packing), two samplers,
and seeds 17/29/43: 36 generations. The existing settings are temperature 0.3,
top-k 40, top-p .95, min-p .05, repetition penalty 1.10. The alternative uses Qwen's
non-thinking text sampling values: temperature 1, top-k 20, top-p 1, min-p 0,
presence penalty 2 and repetition penalty 1. The existing 128-token penalty window
remains; this is an adaptation to llama.cpp, not framework-independent parity.
Messages, output limits, thinking configuration and evidence are identical between
samplers for each condition. Model-only uses the app's model-only policy, so it is
not a pure evidence-removal ablation with otherwise identical instructions.

Preflight exposed a selection limitation: the database's non-relational classification
and spreadsheet use cases disappeared even from manually selected candidates, because
the overview relevance gate required a defining sentence. An additional frozen
control bypassed filtering and excerpt selection, inserting the four complete original
database/spreadsheet chunks within the existing evidence budget. The two algorithm
chunks were already complete. This added 12 generations; its six algorithm requests
and answers duplicate the previous manual-context condition and are not independent
new cases. Total: 48 recorded generations on **two** known questions.

Agent review observations (not independent human scoring):

- Both sampling configurations continued producing materially wrong claims. Automatic
  database answers still generalized relational structures to databases overall.
- Complete database context helped some answers acknowledge advanced spreadsheet uses
  or avoid the blanket multiple-table definition, but did not reliably eliminate
  overgeneralization. Existing sampler seed 29 still described manual recalculation
  despite supplied text explaining that manual recalculation is unnecessary.
- Manual algorithm context did not cure source interpretation: existing sampler seed
  17 reversed the termination distinction; recommended sampler seed 29 invented named
  references and attributed absent statements to S1. Other seeds also contained errors.
- All 48 responses ended with `stop`, not output-budget truncation. No latency conclusion:
  this was desktop inference, with local build activity during part of the run.
- **Decision:** no sampling or model change in the app. Improve contextual selection
  and source quality, but do not assume these alone fix generation. Automatic judging
  remains disabled. The prior release gate is not passed.

Private reproducibility artifacts: `build/release-controls/plan.json`,
`selection.json`, `comparison/report.json`, `complete-context-plan.json`,
`complete-context-requests.json`, and `complete-comparison/report.json`.
The complete-context input is explicitly marked FROZEN_REQUEST, not a successful
app run. Its preparation retains original chunk text and records database/selection
hashes. No remotely generated answer was used.

### Coverage follow-up and release gate (October 1)

Coverage work uses the seven existing general-reference queries plus all four
`tools/data/answer_review_questions.json` queries. These are used development cases,
not an unseen benchmark. The 0.3.0 candidate adds two articles without refreshing
the original 24 revisions. An initial coverage-only run and a separate title-selection
run isolate data changes from retrieval changes; reports are recorded in DATASETS.md.

Mixed-answer UI now puts original retrieved passage previews before the generated
explanation. Previews render plain source text, retain full context through the source
viewer, and explicitly do not certify the explanation. The first two appear initially;
the remaining passages are expandable. For the supported narrow comparison grammar,
a missing subject word produces a gap notice based on passage text, not the title.
Absence is a lexical warning; presence never earns a complete-coverage badge.

The candidate cannot be promoted until all of these release checks pass:

- Review every complete answer, not selected claims: no material contradictions,
  invented evidence, or unjustified universal claims on the fixed development set.
- Judge question coverage separately: missing comparisons or requested examples
  fail usefulness even if the answer honestly states a gap.
- Review support against actual packed excerpts, and factual correctness separately.
  Exact quotation and a model checker cannot certify either alone.
- Preserve the missing-current-information refusal and existing travel behavior.
- Pass unit tests, release build, and on-device source navigation, accessibility,
  offline performance and cancellation checks. Device work remains deferred.
- Before public quality claims, freeze a separate holdout and obtain independent
  review. Do not relabel repeatedly tuned development questions as a holdout.

Current gate is **NOT PASSED**: database generalization and the algorithm contradiction
remain; device UI tests are not executed. The candidate pack stays development-only.

Verification: the three existing biology/travel/missing-source regression answers
are byte-identical to their previous run. Two offline candidate builds produced
byte-identical `.fapack` files. Script tests (46) and reference-builder tests (14)
passed. The final full JVM suite and release build passed; the Android UI test
compiled but was not run on a device. Local evaluation servers were stopped.

### Fresh complete-answer check (October 1)

`tools/data/answer_review_questions.json` froze four new development questions before
generation: ecosystem roles, algorithms versus programs, atomic structure/mass,
and p-value limits. They were not selected or changed after seeing outputs.
They are now used development cases, not an untouched holdout. The unchanged 2B
app pipeline generated their answers against the same paragraph reference pilot:
`build/answer-review/fresh-2b/report.json`.

`scripts/research/prepare_answer_review.py` prepares a review sheet from completed reports.
It preserves every non-whitespace character in answer blocks with exact offsets,
keeps adjacent bullets together, and automatically excludes only the two known
app display headings from factual review. **It is not an atomic-claim extractor.**
A block can contain several assertions, all of which require support. This avoids
selecting only convenient claims but does not solve semantic decomposition.

For export, every block requires a label and reason, and every answer requires
a separate question-coverage review. `--source-report` checks the original hash,
question, answer and packed passages; changed or dropped text is rejected. Empty
evidence is not silently counted as a verified case. Labels are agent judgements,
not independent human review. The atom answer's provisional supported label is
based on agreement with the supplied excerpts, including their mass wording;
its scientific precision and loose orbit/cloud language need separate review.

The reviewed answers are at `build/answer-review/fresh-2b/review.json`. Their
labels were assigned before checker inference; exported probes are at
`build/answer-review/fresh-2b/checks.json`. Re-export with origin validation at
`bound-checks.json` is byte-identical. Checker inputs contain the complete answer
blocks and all actually packed passages, but no gold labels or review reasons.
This tests whole-block support, not whether each generated citation is correctly
placed. The 4B checker output is at
`build/answer-review/fresh-4b-checker/report.json`.

Results: one unsupported block accepted, three invalid responses, and the one
provisionally supported block missed. In particular:

- The p-value answer adds general claims not established by the single packed
  coin-toss example. 4B nevertheless marks the whole block supported and supplies
  valid quotations. Exact attribution therefore does not establish full support.
- The algorithm answer says an implementation description gives exact machine
  states. The passage explicitly says it does not. The raw checker verdict still
  says supported; only its malformed quotation causes rejection by the parser.
- The ecosystem and atom blocks also fail quote validation, through changed
  wording, ellipses or incorrect source mapping. These are not successful semantic
  detections and must not be scored as such.

Decision: the earlier isolated-claim results do **not** justify automatic checking
of complete answers. Keep the checker experimental, with no verified badge,
rewriting, claim removal, Android integration or model-default change. Supported
sentences inside rejected blocks have not been separately certified. The 46
script tests pass, including lossless block coverage and origin binding; this
does not constitute an answer-quality pass. All owned inference processes exited.
Phone installation/performance testing remains deferred.

Use `scripts/research/run_desktop_research.py` to run the current app's Kotlin orchestrator,
multi-pack keyword retrieval, prompt builder, venue routing, and answer attribution
on the host JVM. This is not the older Python pipeline replica in `tools/evaluation/e2e_test.py`
or `tools/evaluation/e2e_battery.py`; those legacy scripts do not represent the current app.

Build the pinned local inference server once (CMake and a C++ toolchain required):

```sh
cmake -S third_party/llama.cpp -B build/desktop-llama \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=ON -DLLAMA_CURL=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build build/desktop-llama --target llama-server -j 4
python3 scripts/research/run_desktop_research.py \
  --question 'Compare mitosis and meiosis. Explain how their different outcomes support growth and sexual reproduction.' \
  --question 'Tell me the best vegan restaurants in Berlin'
```

Defaults use the existing saved GGUF, extracted Biology database in
`build/biology-retrieval-audit/`, and Travel database in
`build/packs/travel/wikivoyage-places/`. On another machine pass `--model`,
`--server`, and one `--database /path/to/content.sqlite` per enabled collection.
Use `--model-only` for the no-evidence comparison. Missing files fail explicitly;
this runner does not download models/packs or alter phone data.

The first Gradle run may fetch desktop SQLite JNI jars; subsequent runs can use
`--offline-gradle`. An Android SDK is still required to compile the existing project,
but no emulator, connected device, APK build, installation, or Android Studio is needed.
Inference binds to `127.0.0.1` only. The launcher verifies the loaded model path
and shuts down its own server afterward.

One current report is maintained at `build/desktop-evaluation/report.json`, with
server logs and configuration alongside it. It includes artifact hashes, source
identity, candidate passages, packed sources, complete inference requests, raw
model responses, and the visible answer. Reports are private/ignored by Git.
Use a separate `--output build/.../report.json` only when deliberately retaining a
comparison. A completed run is marked **COMPLETED_UNSCORED**, never an accuracy pass.

Limits: this initial runner is **keyword-only**, without the pack's vector encoder.
It uses the same app pipeline code but a desktop inference adapter, fixed answer
seed 17, and non-streaming output. It does not reproduce Android's native
repetition-loop stopping. First-token and token-count fields from the orchestrator
are therefore not meaningful desktop performance measurements; use server usage/
timings for desktop diagnostics only. It does not validate attachments/OCR, UI,
phone memory, battery, network isolation, or phone latency. Final phone checks
remain required. Model self-grading is not used as proof of correctness.

#### Manual-evidence diagnostic

To isolate retrieval from downstream generation, pass
`--manual-selection build/path/selection.json` with exactly one `--database`.
The JSON object maps each exact requested question to an ordered array of chunk
IDs from that database. Empty arrays explicitly supply no evidence. The launcher
rejects missing or duplicate IDs and copies the original passage and metadata
without rewriting them. The report records the selection hash and evidence mode.

This bypasses automatic retrieval only: the app's relevance gate, result limit,
excerpt selection, prompt budget, generation, and answer attribution still apply.
Inspect the recorded inference request, not just the supplied passage list, to
verify what reached the model. Manual selection is not guaranteed sufficient or
unbiased and is not a retrieval-quality score. Keep questions, model, database,
app source and inference settings identical in the automatic control; save each
report in its own directory. Run Gradle evaluations and unit tests sequentially
because they share Gradle's test-result files.

For an instruction-only comparison, add `--answer-policy source-only`. This
test-only switch substitutes the existing `PromptBuilder` strict policy for the
instructions before the evidence disclaimer. The complete packed evidence,
question, system message and generation settings remain unchanged. Planning and
model-only calls are not modified. Default `--answer-policy app` leaves all
prompts unchanged. The report records the selected policy and actual requests.
`--answer-policy source-partial` is a separate test-only candidate requiring
supported parts to be answered and unsupported parts to be identified individually.
It does not change evidence selection or supply additional facts. Treat results
on the development questions used to design it as exploratory, not held-out validation.

This does **not** switch the production answer-attribution mode: a question that
normally uses mixed answers still goes through mixed-answer display processing.
Review `calls[].rawAnswer` for generation and citation support, and `visibleAnswer`
separately for the UI result. A source number does not establish that the cited
passage supports the claim. This diagnostic is not a shippable policy/UI change.

### Overview-relevance development checks

The current overview filter is an English precision heuristic, not a semantic
verifier. For short definition requests and unambiguous single-word comparisons,
it favors sentences directly explaining the subject rather than mentions inside
experimental descriptions. It handles basic inflections, articles, appositives,
and acronyms; source-specific requests keep the ordinary relevance path. Unknown
language forms fall back to lexical matching using a weak English-marker heuristic.
This can still reject useful multilingual/indirect explanations or admit terse
incidental claims. It must not be described as understanding or verifying sources.

Planner expansion no longer rescues a detailed question from only one shared
content word. Auxiliary verbs `have`, `has`, and `had` are excluded from FTS terms,
including the reproduced `have` prefix matching the unrelated name “Haven”.

The frozen six-question development comparison is retained privately at
`build/desktop-evaluation/before/report.json` and
`build/desktop-evaluation/after/report.json`. It covers mitosis/meiosis, cellular
senescence, batteries/capacitors, hemispheric seasons, Berlin vegan listings, and
a fictional-city lookup. These are known development cases, not an unseen benchmark.
The canonical 18-question benchmark is unchanged. Inspect raw responses as well
as displayed answers; absence of citation or prompt-format errors is not a factual
accuracy score. See the maintained release audit for outcomes and remaining faults.

### Android evidence capture

1. Install the verified model and any knowledge packs being evaluated, prepare the model from Research, then open **More → Open device benchmark**.
2. Confirm the exact frozen prompt shown for the next row and tap **Run next**. The app advances only one question per tap. A stopped row retains its partial answer and evidence identifiers as `CANCELLED`.
3. Complete all 18 rows and use **Export benchmark evidence**. The exported `BenchmarkRun` includes the frozen prompt, user-visible answer, evidence chunk identifiers, measurements, installed artifact hashes, and a SHA-256 identity for the diagnostics snapshot. Mixed-answer output includes attribution labels and removes unsupported citation markers; it is not the raw model token stream. The unmapped-citation measurement also flags discarded citation markers.
4. Preserve that rich export as device evidence. The scorer accepts the Android export directly,
   rejects incomplete rows, and reads only each immutable question ID and answer.

The export records whether each pack was enabled and which model was active. That distinguishes
the model and knowledge configuration actually tested from assets that merely happened to be
installed on the phone.

## Capture the online comparison

Use the same frozen prompt file and a named model ID. Do not use the dynamic `openrouter/free` router
for a release comparison because its provider can change between rows. The runner records the
model, provider endpoint, temperature, timestamps, and prompt-set path beside the answers.

```sh
python3 scripts/research/run_online_baseline.py \
  --benchmark benchmarks/questions.json \
  --model 'NAMED_PROVIDER/MODEL:free' \
  --output build/evidence/baseline.json \
  --resume --timeout 300
```

The app never runs ordinary research and a benchmark row concurrently. Exports preserve evidence identifiers and are not, by themselves, a quality judgment. In mixed answers, source links require an exact quotation found in the corresponding saved passage. This checks attribution, not truth, relevance, completeness, or preservation of every qualification. Attachment and source-only answer paths retain their separate policies and do not use this quotation guard.

## 24-question release suite

These questions were frozen before the v1.2.0 run [reported above](#v120-24-question-suite-october-2). They are not yet the contents of `benchmarks/questions.json`, which still holds the earlier questions; the device counter measures recorded questions, not correct answers, and a cancelled row can also be recorded.

### Purpose

Compare releases on useful offline answers, supporting evidence, honesty about missing/current information, and phone performance. A completed run is not a quality score or proof of the bounty's greater-than-50-percent bar.

Freeze prompts, rubric, model/pack hashes, and settings before evaluating. Run the same prompts against a named online baseline, keeping its internet access and tool settings explicit. Preserve all answers, including failures. Do not compare scores from different suites as if they were the same test.

### Questions

Four questions per group. Berlin, Tokyo, and mitosis/meiosis are known development regressions, not unseen validation examples. The other questions must not be selected or removed based on whether this build happens to answer them well.

| ID | Question |
| --- | --- |
| travel-berlin | Tell me the best vegan restaurants in Berlin. |
| travel-tokyo | Tell me the best vegan restaurants in Tokyo. |
| travel-jakarta | Which hotels are listed in Jakarta? Include locations where the saved sources provide them. |
| travel-london | Which museums are listed in London, and what does each focus on? |
| biology-division | Compare mitosis and meiosis. Explain how their different outcomes support growth and sexual reproduction. |
| biology-senescence | Explain cellular senescence and how it differs from a cell dying. |
| biology-autophagy | Explain autophagy and distinguish a proposed role in aging from evidence that an intervention extends human life. |
| biology-telomeres | What can telomere length tell us about aging, and what can it not establish? |
| comparison-study-types | Compare observational studies and randomized trials when evaluating a longevity claim. |
| comparison-species | Why might a result in yeast or mice not predict the same result in humans? |
| comparison-diet | Is a vegetarian restaurant necessarily suitable for a vegan traveller? Explain using the saved listings if relevant. |
| comparison-evidence | How should conflicting studies be compared before deciding which conclusion is better supported? |
| synthesis-rapamycin | Summarize what saved sources say about rapamycin and aging. Separate reported findings from remaining uncertainty. |
| synthesis-cell-aging | How might cellular senescence and inflammation be connected in aging? Distinguish observations from causal claims. |
| synthesis-trip | Using saved Berlin listings, suggest a vegan food stop and a museum visit. Explain what cannot be verified offline; do not invent travel times. |
| synthesis-research | Two studies report different lifespan outcomes. What study details would you need before trying to combine their conclusions? |
| limits-hours | Which of the saved Berlin vegan restaurants is open right now? |
| limits-news | What important longevity study was published today? |
| limits-location | What is the nearest vegan restaurant to my current location? |
| limits-missing-place | Recommend a vegan restaurant in Fictional Test City ZXQ-417. |
| general-seasons | Why do Earth's hemispheres have opposite seasons? |
| general-energy | Compare battery capacity and power with a practical example. |
| general-offline | What can an offline app know from saved files, and what needs fresh information? |
| general-performance | Why does a faster processor not always make an app faster? |

### Evaluation rules

- Record model, enabled collections, artifact hashes, device RAM/OS, and app commit/APK hash.
- Judge correctness, task usefulness, citation support, and uncertainty separately. A citation counts only if its actual passage supports the associated claim.
- A general question can be answered correctly without a citation if clearly labelled model-generated. A request specifically asking for saved-source findings must not fabricate evidence.
- Missing coverage is a product limitation even when the app honestly abstains; give credit for honesty without awarding full usefulness credit.
- An offline listing date is not proof a venue remains open. A page revision date is not necessarily the date its listing was checked.
- Static-list lookup and generated answers must have separate latency summaries. Record first-token time, total time, cancellation/failure, and peak memory where measurable.
- Human review is needed for paraphrases, contradictions, and source support. The existing exact-phrase scorer must not be presented as a sufficient judge of this new suite.
- Keep a small, separately frozen holdout set that is not used to tune retrieval. Do not claim unseen generalization from the known regression questions.

### Separate functional checks

Camera/OCR, image preview, PDF/text attachments, download/import recovery, and persistence need repeatable fixtures and UI checks. They are not covered merely by completing these 24 text questions. Run them as a separate acceptance checklist rather than silently inflating the text benchmark count.

### Suggested public wording

“24 questions recorded” describes progress. Show quality scores only after scoring, with the suite version and method visible. A shorter smoke-test subset can be used during development; the full frozen suite is for release comparisons.

Next: write the per-question rubric down, run a named online baseline on the same questions, and keep a separate holdout set before replacing the contents of `benchmarks/questions.json`.

The maintained filename stays `benchmarks/questions.json`. Identify future changes by Git commit and the file's SHA-256, not by creating numbered files. Android generates its bundled copy from this single source during the build.
