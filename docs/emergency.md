# Emergency knowledge

Field Atlas answers first-aid, outdoor and disaster questions offline, from the first launch: no collection download and no internet, and on phones that cannot run a model yet the guides still open. Ask in **Research** as usual, for example “someone is choking”, “my uncle collapsed and is only gasping”, “I got bitten by a snake, what should I do?” or “is it safe to drink stream water?”.

## How the answer is made

The app reads the situation, not just the topic: the signs the official guides list (a collapse with gasps or a chest that barely moves, a child's fever, something swallowed just now, a low blood-sugar reading, an asthma attack gone quiet, a reaction after a food) open the guide for that situation, and a learning question (“how deep do you push for CPR?”) is answered as a question, not as an emergency: Gemma 4 26B and the 4B answer it from what they know, and Qwen3.5 2B, which has no such route, shows the official guide the question is about instead of paraphrasing a passage. The answer has up to three parts:

1. **The call line, first.** “Call your local emergency number now (112 works on mobile phones in many countries)” for anything life-threatening; “If this is an emergency, call your local emergency number first” otherwise.
2. **The official guide, as published.** It appears at once, in about twelve seconds on a 6 GB phone, with its source cited. On Qwen3.5 2B and 4B this is the whole answer: a small model once reversed an instruction while paraphrasing a guide, so these models never rewrite one.
3. **An answer for the situation, on 12 GB phones.** Gemma 4 26B writes for the question as asked (“my child burned her hand on a hot pan”) with the guide as its only source, under the call line; the published guide stays on screen while it writes and under the answer when it is done. It is told to add no treatment the guide does not give and to say what the guide does not cover. On 40 sealed situations graded blind in two rounds (set 55), 30 of its 40 (75%) answers were good and one was judged unsafe; the details are in [How Field Atlas 1.4.2 was tested](testing-1.4.2.md).

When no saved guide covers the situation, the answer says so and gives no treatment steps, with the call line first; the CPR, recovery-position and anaphylaxis guides are listed under it as the basics that still apply. Ordinary questions that only mention a topic (“history of the 2004 tsunami”, “how to cut onions”) are researched as usual, and questions about your attached files always use the files.

Every guide has a recorded reading, so **Read aloud** on an emergency answer plays at once instead of waiting for the voice to be synthesised.

51 guides cover CPR, someone unconscious but breathing (the recovery position), choking, bleeding, shock, burns, a chemical or object in the eye, fractures, head injury, heart attack, stroke, allergic reactions, asthma attacks, seizures, poisoning and what to do after it, opioid overdose and naloxone, low blood sugar, fever in children, a knocked-out tooth, bleeding in pregnancy, carbon monoxide, bites and stings, drowning, hypothermia, frostbite, heat illness, dehydration, altitude sickness, lightning, sunburn, safe drinking water, being lost or injured outdoors, signalling for help, emergency shelter, and earthquakes, floods, wildfires, tsunamis, storms, tornadoes, power outages and extreme cold.

Sources: CDC, NIOSH, NIH (MedlinePlus health-topic summaries and NIAMS), the National Weather Service, the National Park Service, FEMA (Ready.gov) and the U.S. Fire Administration, all US public domain; six NHS pages (Open Government Licence v3.0; asthma, fever in children, a knocked-out tooth, low blood sugar, poisoning and bleeding in pregnancy) and a CDC page on naloxone; and five English Wikipedia articles at pinned revisions (choking, emergency bleeding control, drowning, distress signals and the recovery position; CC BY-SA 4.0). Each answer cites the guide; see [DATASETS.md](../DATASETS.md#emergency-guides-bundled) for the build.

“What emergency number do I call in Thailand?” is answered from a bundled table of numbers for 242 countries and territories (Wikipedia's “List of emergency telephone numbers”, pinned revision), cited and without the model.

“Nearest hospital near me” and similar near-me questions also run without the model, from the Essentials collection; for health, police and drinking water the search widens up to 100 km when little is close.

## Limits you should know

- **These are information, not training.** A guide does not replace a first-aid course or a dispatcher's instructions. Call your local emergency number first.
- **Many guides are written for the United States or the United Kingdom** and say so in the answer: phone numbers differ, and some advice is country-specific. Snakebite, spider and insect guides describe North American species; for example, Australian elapid and funnel-web bites are treated with a pressure-immobilization bandage, which the US guide does not cover.
- **Severe bleeding is thin.** The bundled public-domain guide (U.S. Fire Administration) covers direct pressure only; no public-domain page with tourniquet steps could be bundled. The Wikipedia bleeding-control article is encyclopedic and plays down tourniquets, which differs from current “Stop the Bleed” teaching. There is no guide for a suspected spinal injury or for removing a helmet.
- **The choking, drowning and recovery position guides are Wikipedia text**, not an official protocol. The recovery position guide explains why and how an unconscious, breathing person goes on their side, with principles rather than numbered steps. **Shock** lists symptoms without steps. **Burns** (Ready.gov) says to cool a burn for 10–15 minutes; many current guidelines say 20.
- **Gemma's answer can still miss something the guide does not say.** In the blind test it treated a cord prolapse as ordinary bleeding in pregnancy, because no guide covers it; the call line was still first. The published guide under the answer keeps the exact wording; follow it when the two differ.
- **Gemma takes minutes.** The call line and the guide are on screen in seconds; the written answer follows at the model's pace, a few minutes on a 12 GB emulator; Gemma has not been timed on a 12 GB phone.
- Matching reads signs and phrasing; an unusual wording may get a normal research answer instead of a guide. On 320 labelled questions the right guide opened 231 times, with none misleading.
