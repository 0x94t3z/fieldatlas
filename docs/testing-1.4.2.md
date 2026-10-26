# How Field Atlas 1.4.2 was tested

This page collects every test behind the 1.4.2 release, in plain words. It covers the three answer
models, everyday questions, emergencies, speed, memory, storage, and the changes that were tried and
not kept. Every question set, answer and grade it mentions is in [docs/fieldtest](fieldtest/), so
anyone can check the numbers.

All of it is our own measurement, made with AI graders and AI-written reference answers from one
model family. It is not an independent score, and none of these numbers shows that the bounty's bar
is met: that is for the judges.

## How a test works

1. **Fresh questions, sealed first.** A set of 40 questions is written by an AI writer that has not
   seen the app's code or its earlier answers, in the way people type on a phone: casual, sometimes
   with typos, often with several parts. The file is sealed before the app sees it (the hashes of sets
   37, 40, 41 and 42 are given in the evaluation), and a set's own scores are never re-measured after fixes
   made from it: such fixes are checked on the next fresh set or listed as not yet re-tested.
2. **A reference answer per question.** Five AI agents with web access write the best answer a
   careful assistant with the internet would give. These are graded too, as a yardstick.
3. **Blind grading, twice.** Each answer goes to an AI grader that does not know which setup wrote
   it; a grader never sees two answers to the same question. The grader checks the important facts
   on the web, scores the answer 0 to 10, lists the problems it finds, and for emergencies marks whether
   following it could plausibly harm someone. Most sets are graded in two independent rounds with
   different graders; on sets 44 to 55 the rounds agreed on whether an answer was good 92 to 97% of the time
   (93 to 98% counting the reference answers). Sets 42 and 45, and the first grading of set 46, had one round.
4. **What we count.** A **good answer** scores 7 or more from both rounds. **Grader annotations** is
   the average number of problems the graders listed per answer. Most are wrong facts, but they also
   include omissions and points both rounds noted; they are not independently confirmed errors, and a
   higher count does not by itself prove more factual errors. For emergencies, **unsafe** counts
   answers both rounds flagged. Two setups are compared by question with a sign test; a change is
   kept only if it does not make answers worse on fresh questions.
5. **On real devices.** Builds of the release's code signed with a local test key (the 23 October
   candidate, and the 24 October builds for the speed table) were installed on a Redmi 13C (6 GB,
   Android 13) and on a 12 GB Android emulator, both in airplane mode with Wi-Fi and mobile data off, and
   asked emergency and everyday questions. The public-key release file itself ran only on an emulator:
   the previous candidate was set up from downloads inside the app on a fresh one with no Google apps,
   and the first release file (build 21) was installed over it with the data kept; on the 12 GB emulator,
   builds 22 and 23 were then installed in turn over build 21 with the data kept. The Redmi was updated to build 23's
   local-test build on 26 October and opened, but asked nothing; see [verification](verification.md).

The desktop runs use the app's own answering code and the same models, collections and prompts as the
phone; only the hardware differs. Times quoted from the desktop are not phone times.

## Everyday questions

Four sealed sets of 40 mixed questions (facts, explanations, comparisons, multi-part questions,
obscure subjects, and questions an offline app cannot fully answer), answered with the release's
prompts, by the app's own answering code on a computer, 19 to 23 October. The 2B answers from the saved
passages it cites when its draft passes the check and review, and otherwise with a model explanation
labelled as unverified (18 of its 40 answers on set 37).

| Set | Gemma 4 26B (12 GB phones) | Qwen3.5 4B (recommended from 8 GB) | Qwen3.5 2B (recommended below 8 GB) |
| --- | ---: | ---: | ---: |
| Set 37, 19 October | 27 of 40 (68%) good, 0.44 grader annotations per answer | 8 of 40 (20%), 1.93 | 2 of 40 (5%), 0.69 |
| Set 46, rerun 22 October | 29 of 40 (72%), 0.34 | 6 of 40 (15%), 2.21 | 11 of 40 (28%), 0.69 |
| Set 49, 22 October | 25 of 40 (62%), 0.38 | 5 of 40 (12%), 2.51 | 9 of 40 (22%), 1.15 |
| Set 51, 22 October | 34 of 40 (85%), 0.36 | 8 of 40 (20%), 2.12 | 13 of 40 (32%), 0.80 |
| **All four sets** | **115 of 160 (72%)**, 0.38 | **27 of 160 (17%)**, 2.19 | **35 of 160 (22%)**, 0.83 |
| Average score, share of the reference answers' | 77% | 45% | 40% |
| Web reference answers | 39 to 40 of 40 (98 to 100%), 0.03 | | |

The 2B ran sets 46, 49 and 51 on 23 October, after the other models, with web reference answers written
that day and two fresh grading rounds (the two rounds agreed within two points on 119 of 120). To check
the new graders against October's, they re-graded 20 of the 2B's set-37 answers: mean 3.6 against 3.2,
3 good against 2, no clear difference (those 20 re-grades are not in the published files). The 2B's share differs between sets because of the mix: on
the 32 single-fact questions of sets 46 to 51 it gave a good answer to 22, on the 22 explanations to 1.
Over the 160 questions the 2B's cited route gives slightly more good answers than the 4B's from memory
(35 against 27) with far fewer grader annotations (0.83 against 2.19); the 4B leads on explanations and
comparisons, the 2B on facts the collections hold. On average score the 4B is ahead (45% against 40% of
the reference). These sets were answered before the 4B read notes for single facts, where the gap to
the 2B was (10 of 32 fact questions good on sets 46 to 51, against the 2B's 22). Since October 24 the 2B is recommended below 8 GB, for speed on a 6 GB phone; the 4B can
still be chosen there.

Where each model is weakest: the 4B's losses are wrong facts from memory (over 4 grader annotations
per answer on obscure subjects); Gemma's are questions that need live data (prices, recent events):
no offline app can answer them, and its answers did not always say so (2 of the 11 on sets 46 to 51
were good, against 0 for the 4B, 1 for the 2B and 9 for the web reference). Then come a few
explanations where it simplified a mechanism.

### Facts from the saved encyclopedia (4B)

The 4B's wrong facts led to one change made after the sets above were graded. A question for a single
fact (who designed, when did, what year, how tall, how deep, what is the capital of) now has the 4B
read three short notes from the saved encyclopedia before it answers, as it already did for rare
subjects. From memory it opened the Sydney Opera House on 26 October; with its notes it gives
20 October 1973. Gemma reasons before it answers and keeps answering from memory, and questions about
the asker's own plans ("how much rice do I need") or follow-ups ("how much does that cost?") stay with memory.

The change was measured on the 149 fact questions in earlier sealed sets. Those sets had been answered
and graded for earlier releases, so they are not fresh, but this rule was not written against them. On
the desktop harness it changed the 4B's answer to 105 of them. This test was graded differently from the
rest of this page: each grader saw the two answers to a question side by side, unlabelled and in random
order, with no reference answer, checked the facts on the web and scored each answer 0 to 10, in two
rounds with fresh orders. The graders did not see the saved passages, so whether each cited sentence is
supported by its note was not checked.

| 4B on the 105 changed fact questions | From memory | With encyclopedia notes |
| --- | ---: | ---: |
| Good answers | 38 of 105 (36%) | 66 of 105 (63%) |
| Grader annotations per answer | 2.79 | 1.69 |
| Unsafe | 0 | 0 |

The notes version scored higher on 62 questions and lower on 33 (sign test p = 0.004). The annotations
are the graders' listed problems, averaged over both rounds; side by side, a grader may list a fault in
one answer because the other gets it right, so the counts compare the two versions, not error rates. Its
losses are mostly the encyclopedia's own mistakes passed on: the saved Simple English article gives the Burj
Khalifa as 428 m tall (it is 828 m). The four-set table above was graded before this change, so its 4B
column does not include it.

## Emergencies

### What 1.4.2 does

When a question describes someone hurt or unwell now, the app does not search the library. It opens
with a call line, **"Call your local emergency number now"** when the message describes a life
threat, a swallowed poison, or a situation the app cannot match to a guide, and **"If this is an
emergency, call your local emergency number first"** for the rest. Then:

- **Gemma 4 26B** writes an answer for the exact situation, with the official guide as its only
  authority. While it writes, the official guide is on screen at once, so nobody waits with
  a blank page. Every line it writes is checked against the guide as it streams; a step the guide
  forbids never reaches the screen, and the finished answer is checked again. Two extra rules were
  added after testing: someone who does not respond and is not breathing normally needs CPR, not
  the recovery position; and no aspirin for someone a doctor has told to avoid it.
- **Qwen3.5 4B and 2B** are not trusted to write first aid. They show the official guide as
  published, with the call line above it. When no guide fits, they give a short reply that says so
  and lists only the basics the saved CPR, recovery-position and anaphylaxis guides support. That
  reply appears at once; it no longer waits for a library search it never used.
- **51 guides** are bundled: the 42 of 1.4.1 (CDC, FEMA, NIH, the National Weather Service, the
  National Park Service and Wikipedia) and 9 added in 1.4.2, mostly from the NHS website and the CDC under
  their open licences: poisoning steps, asthma attack, low blood sugar, bleeding in pregnancy, fever in
  children, a knocked-out tooth, opioid overdose, eye injury and the recovery position. Each
  guide has a recording in the natural voice, so Read aloud plays at once on any phone, offline,
  with no voice downloaded.

### Results on fresh questions

Each set is 40 situations written as people type them: someone who cannot be woken, something
swallowed, a decision about emergency medicine, serious cases no leaflet covers, situations whose
words point to the wrong topic, and 8 calm questions where no emergency call is needed.

**Gemma 4 26B.** On the set where the change was first tried (set 44, 36 questions), the 1.4.1
behaviour (guide shown as published) gave a good answer to 12 and three unsafe ones; Gemma writing
from the guide gave 28 good and none unsafe. On five later fresh sets, Gemma writing from the guide
(each set ran on the code of its day; the rules added after a set found a problem were not re-scored on
that set):

| Set | Good answers | Grader annotations per answer | Unsafe (both graders) |
| --- | ---: | ---: | ---: |
| Set 50 | 29 of 40 (72%) | 0.17 | 1 |
| Set 52 | 24 of 40 (60%) | 0.39 | 1 |
| Set 53 | 29 of 40 (72%) | 0.28 | 0 |
| Set 54 | 31 of 40 (78%) | 0.23 | 3 |
| Set 55 | 30 of 40 (75%) | 0.26 | 1 |

The unsafe answers were: an unresponsive cyclist with blood in his mouth not turned on his side; a
hypo treated in the wrong order (that question now reaches the low blood sugar guide); someone
breathing three times a minute, and someone with a gurgling rattle and a barely moving chest, left in
the recovery position instead of CPR (now covered by a prompt rule); aspirin advised to a man told
to avoid it (also now a rule); and a cord prolapse where Gemma said to drive to hospital and that the
cord might be pushed back, which is wrong. That last one is a knowledge gap in the model and is
listed under limits below.

**Qwen3.5 4B and 2B.** These models do not write first aid, so a good answer needs the right guide
to be found. The work in 1.4.2 went into finding it, and into the call line when it is not found.

| Set | 4B good | 4B unsafe | 2B good | 2B unsafe |
| --- | ---: | ---: | ---: | ---: |
| Set 50, before the 1.4.2 matching fixes | 10 of 40 (25%) | 6 | 0 of 40 (0%) | 16 |
| Set 50, after them | 3 of 40 (8%) | 5 | 0 of 40 (0%) | 7 |
| Set 52 | 0 of 40 (0%) | 7 | 1 of 40 (2%) | 8 |
| Set 53 | 6 of 40 (15%) | 2 | 3 of 40 (8%) | 3 |
| Set 54 | 2 of 40 (5%) | 7 | 1 of 40 (2%) | 9 |
| Set 55 | 2 of 40 (5%) | 1 | 2 of 40 (5%) | 3 |

Two things to read from this table. First, the small models give few good answers in emergencies
because a guide as published rarely answers the exact question asked; what they do give is the
official text and a call line. Second, unsafe answers did not fall steadily: they went up and down
between sets (2B 7, 8, 3, 9, 3; 4B 5, 7, 2, 7, 1 after the fixes), and not to zero: on set 55 the remaining ones were a cord prolapse and a neck injury
answered from memory with no call line, and a hay-fever reaction sent to the fever guide. Each was
fixed afterwards and checked on the phone, but no fresh set has been graded since those last fixes, nor
since the routing changes of October 24.

**Routing check.** A separate list of 320 emergency and look-alike questions, each labelled with
the guide to show and the guides that would mislead (the choking manoeuvre for a heart attack,
cooling water for a jellyfish sting), runs in the unit tests; the matching rules were developed against it, so it is a regression check,
not a blind score. The release's code answers 231 of 320 (72%) well (1.4.1: 129) and opens no
misleading guide. The matching rules reach every guide the NHS and CDC sources list
as a reason to call for help, in the words people use: gasping or a rattle, a meter reading under
4, a silent chest, a rash that does not fade, pinpoint pupils, "waters just went", and so on.

## On the phone and the emulator

A local-test-signed build of the 23 October candidate (version 1.4.2, build 21) was installed on
both devices with app data kept, and both were put fully offline. The speed table further down used
local-test builds of the 24 October code.

**Redmi 13C, 6 GB, Qwen3.5 4B.** Nine questions: five emergencies, a splinter, a CPR question for a
course, and two everyday questions.

| Question | Time | Opened with | Shown |
| --- | ---: | --- | --- |
| A collapse with gasps every few seconds | 11 s | call now | CPR guide |
| A toddler found with an opened methadone bottle | 12 s | call now | NHS poisoning guide |
| An asthmatic child gone quiet, pale lips | 12 s | call now | NHS asthma guide |
| A probable cord prolapse | 12 s | call now | no guide fits, the basics |
| A hypo with a meter reading of 3.4 | 11 s | call now | NHS low blood sugar guide |
| A splinter that does not hurt much | 12 s | if this is an emergency | no guide fits |
| How deep to push for CPR, before a course | 375 s | — | an answer: 100–120 a minute, 5–6 cm |
| Who painted Girl with a Pearl Earring | 466 s | — | Vermeer, about 1665, the Mauritshuis |
| Why the Moon looks bigger near the horizon | 278 s | — | the Moon illusion |

Read aloud on the CPR guide started within three seconds, playing the recording.

An everyday answer from the 4B takes about four minutes on this phone from a clean start (238 and
261 s on the same question, the 23 October and 20 October builds). It took over 20 minutes once
during testing, when background apps and a run of searches had pushed the model out of memory; after
closing those apps it returned to four minutes. Memory measured on the 12 GB emulator on 18 October,
on an earlier 1.4.2 build, with every collection installed: the 2B peaks at 3.45 GB and the 4B at 6.83 GB while answering.

**Speed on the Redmi 13C, October 24.** Six new questions, three simple and three that ask for an
explanation, a comparison or a trip plan, each asked twice in airplane mode, timed from asking to the
complete answer (any wait included):

| Question | Qwen3.5 2B | Qwen3.5 4B |
| --- | ---: | ---: |
| Who painted The Starry Night, and in what year? | 88 s, 88 s | 210 s, 362 s |
| What is the capital of Australia? | 115 s, 139 s | 194 s, 200 s |
| How long is the Great Wall of China? | 153 s, 144 s | 318 s, 331 s |
| Why do we have leap years, and what would happen without them? | 195 s, 351 s | 300 s, 321 s |
| Compare nuclear fission and nuclear fusion | 266 s, 261 s | 357 s, 298 s |
| Three days in Kyoto in autumn: main sights, getting around | 290 s, 279 s | 284 s, 300 s |

The 2B cited every answer; the 4B answered from its own knowledge. Read by us, not graded blind: the
simple answers were right on both models; the 2B's first leap-year answer left out the century rule
(its second had it), one 2B fission answer gave the wrong reason fusion is hard, and the 2B named a
station exit and Kyoto Tower as Kyoto's main sights, where the 4B named the shrines and temples. With
Wi-Fi on and other apps open, one 4B answer took 47 minutes while Android was closing apps for
memory, so 1.4.2 recommends the 2B below 8 GB. The phone's thermal state stayed normal (battery
33–42 °C).

**12 GB emulator, Gemma 4 26B.** The same emergency questions, written by Gemma from the guides:
each opened with the right call line and the right guide, and the finished answers were correct (the
hypo answer said more fast sugar first, then the sandwich; the cord prolapse said call, lie on the
left side or knees to chest). Writing took 3 to 7 minutes per emergency answer while the host
computer was busy, and 8 everyday questions took a median of 92 s with the host idle. Gemma has not
been timed on a real 12 GB phone.

**Storage.** With all three models, every collection and the voice installed, Android reports 47.58
GB for the app on the 12 GB emulator. A long session on 1.4.1 (120 questions in one sitting on the Redmi) showed no drift
in answer quality and one process throughout.

## Tried and not kept

Three changes to how answers are written were measured and rejected, because each made some answers
worse on fresh questions.

| Change | Sets | Result |
| --- | --- | --- |
| An adaptive length rule: short for facts, as long as needed otherwise | 46 (one round), rerun 46 | Gemma 32 vs 32 and 29 vs 31 good answers (p = 0.70, 0.86). Facts improved, comparisons lost parts of the question. |
| Plain-language wording | 49 | Gemma 24 vs 25 good; on 8 explanations the average score fell from 8.7 to 7.8 (7 good to 6) as simplifying added mistakes. |
| One sentence for single-fact questions only | 51 | Gemma 32 vs 34 good (p = 1.0). Fact answers halved in length at the same score, but comparisons still lost parts. |

On the 4B the length rule doubled good answers on the rerun of set 46 (12 against 6; 7 to 1 where they
differed, p = 0.07, not significant) and the one-sentence rule gave 11 against 8 on set 51 (p = 0.45); plain wording
gave 4 against 5. None was kept: Gemma, which the rules were written for, did not gain, and none was
confirmed on a further set. The release keeps the 1.4.1 wording; the length rule for the 4B is worth a
fresh test.

## Limits you should know

- **The small models do not write first aid.** With Qwen3.5 2B or 4B (any phone without
  Gemma 4 26B in use) an emergency answer is the official guide and a call line, or a short reply that no guide fits. Minor problems (a splinter, a
  blister) get that reply too, which is safe but not helpful.
- **Gemma can be wrong in an emergency it has no guide for.** The cord-prolapse answer above is the
  known case. Guides for a possible spinal injury, a tourniquet and helmet removal are still missing.
- **"Call now" is a rule, not a diagnosis.** It fires on the signs the sources list and on any
  first-aid situation no guide matches. It can fire for a problem that turns out to be minor.
- **The recordings open with "If this is an emergency…"** even when the screen says "call now". The
  spoken advice is right, only softer. A "now" recording for each guide is a later job.
- **Gemma emergency answers take minutes** on the emulator; the guide on screen meanwhile is the
  answer to act on until Gemma's is checked.
- **Answers can still be wrong.** The grades above come from AI graders checking the web; they
  caught errors in the reference answers too.
- **No live data.** The models and collections are snapshots. Asked about this year's events or
  today's prices, an answer can be out of date without saying so.

## Run the set on your own phone

**More › Performance check** asks the phone the 40 questions of set 37, the everyday set above, one
after another, and exports the answers with their times as a JSON file. Grade them by the method at
the top of this page, or run `python3 benchmarks/score_results.py` for the quick key-phrase check
described in [evaluation.md](evaluation.md#the-benchmark-file-and-its-scorer). The questions are in
[benchmarks/questions.json](../benchmarks/questions.json).

## Data

- Questions: `docs/fieldtest/heldout44.jsonl` to `heldout55.jsonl`, each sealed before use.
- Grades, set by set, as readable tables: [results-1.4.2](fieldtest/results-1.4.2/README.md), with
  every question, each setup's scores and what the graders found wrong. The raw files next to it
  (`heldoutNN-grades.jsonl`) hold the same data with the graders' notes.
- The fact-notes test: [fact-notes-grades.jsonl](fieldtest/results-1.4.2/fact-notes-grades.jsonl), one line per
  changed question, with the set it came from and both versions' scores and noted mistakes.
- The 320-question routing list: `app/src/test/resources/emergency-labelled.jsonl`, run by the unit
  tests on every build.
- Earlier releases' tests: [evaluation](evaluation.md).
