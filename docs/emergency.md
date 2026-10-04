# Emergency knowledge

Field Atlas answers first-aid, outdoor and disaster questions offline, from the first launch: no model, no collection download and no internet. Ask in **Research** as usual, for example “someone is choking”, “I got bitten by a snake, what should I do?”, “signs of hypothermia” or “is it safe to drink stream water?”.

## How the answer is made

The app finds the matching bundled guide and gives its most relevant sections to the language model as its only evidence. The answer has three parts:

1. **Source excerpt, as published.** The guide's key section, word for word with its source cited, appears at once, before the model starts writing.
2. **For this situation.** The model's answer for the question as asked (“my child burned her hand on a hot pan”), in cited steps. It is told to use only the guide, to add no treatments, medicines or doses, and to say what the guide does not cover.
3. **A safety line written by the app:** call your local emergency number first, plus a note when the guide was written for the United States.

If the model cannot run (still loading, failed to load, or failed mid-answer), the guide is shown as published instead. Ordinary questions that only mention a topic (“history of the 2004 tsunami”, “how to cut onions”) are researched as usual, and questions about your attached files always use the files.

41 guides cover CPR, choking, bleeding, shock, burns, fractures, head injury, heart attack, stroke, allergic reactions, seizures, poisoning, bites and stings, drowning, hypothermia, frostbite, heat illness, dehydration, altitude sickness, lightning, sunburn, safe drinking water, being lost or injured outdoors, signalling for help, emergency shelter, and earthquakes, floods, wildfires, tsunamis, storms, tornadoes, power outages and extreme cold.

Sources: CDC, NIOSH, NIH (MedlinePlus health-topic summaries and NIAMS), the National Weather Service, the National Park Service, FEMA (Ready.gov) and the U.S. Fire Administration, all US public domain, and four English Wikipedia articles at pinned revisions (choking, emergency bleeding control, drowning and distress signals; CC BY-SA 4.0). Each answer cites the guide; see [DATASETS.md](../DATASETS.md#emergency-guides-bundled) for the build.

“Nearest hospital near me” and similar near-me questions also run without the model, from the Essentials collection; for health, police and drinking water the search widens up to 100 km when little is close.

## Limits you should know

- **These are information, not training.** A guide does not replace a first-aid course or a dispatcher's instructions. Call your local emergency number first.
- **Many guides are written for the United States** and say so in the answer: phone numbers differ, and some advice is US-specific. Snakebite, spider and insect guides describe North American species; for example, Australian elapid and funnel-web bites are treated with a pressure-immobilization bandage, which the US guide does not cover.
- **Severe bleeding is thin.** The bundled public-domain guide (U.S. Fire Administration) covers direct pressure only; no public-domain page with tourniquet steps could be bundled. The Wikipedia bleeding-control article is encyclopedic and plays down tourniquets, which differs from current “Stop the Bleed” teaching.
- **The choking and drowning guides are Wikipedia text**, not an official protocol. **Shock** lists symptoms without steps. **Burns** (Ready.gov) says to cool a burn for 10–15 minutes; many current guidelines say 20.
- The model's part can still condense or misstate a step: in desktop tests with the 2B model, 10 of 12 emergency answers were clean, and two contained a garbled sentence (for example “do not boil cloudy water”, where the guide says to filter it first). The source excerpt above the model's answer keeps the guide's exact wording; follow it when the two differ.
- Matching uses keywords and phrasing; an unusual wording may get a normal research answer instead of a guide.
