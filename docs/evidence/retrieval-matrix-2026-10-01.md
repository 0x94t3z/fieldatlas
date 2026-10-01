# October 1 retrieval regression review

## Scope and limits

Fixed questions and review criteria: `tools/retrieval_regression_matrix.json`, written
before this run. Current Qwen3.5 2B model with the saved Biology/longevity and Travel
databases. Raw generations, passages, configuration and hashes remain in ignored
`build/retrieval-matrix-oct1/report.json`. All 16 cases completed. This is developer
review of a development set, not an independent holdout, benchmark percentage,
medical validation or release pass. Existing cases are explicitly identified in
the matrix. Desktop timing is not phone latency.

## Case review

| Case | Sources admitted | Observation |
| --- | ---: | --- |
| Ice flotation | 0 | Basic density/lattice explanation; no irrelevant passages. Unverified model knowledge, not grounded research. Unnecessary uncertainty boilerplate remains. |
| Metal vs wood temperature | 3 | Core heat-transfer explanation, but unrelated clinic temperature, materials annealing and growing-season studies admitted. Relevance fails. |
| Boat flotation | 6 | Aging articles contain the idiom “a rising tide floats all boats.” All keywords match but none explain buoyancy. Answer acknowledges mismatch yet omits a useful displacement explanation. Relevance/completeness fail. |
| Opposite seasons | 0 | Axial-tilt explanation, followed by an unjustified claim of constancy for billions of years. Not a clean correctness pass. |
| Plants and light | 3 | Specialized plant studies and an unrelated vaccine paper replace general explanation. Answer drifts into mutant phenotypes and stress responses. Relevance/completeness fail. |
| Mitosis/meiosis | 0 | Basic distinction present, but wording places crossing over with a second division and assumes all mitotic outcomes are diploid. Correctness/qualification fail. |
| Battery/capacitor | 0 | Main storage distinction present; categorical “instantaneous” recharge and “short bursts only” claims are overbroad. Qualification fails. |
| LAN file sharing | 6 | Incorrectly says ordinary Windows SMB requires internet access; references a ryokan's Wi-Fi description. Severe correctness and relevance failure. |
| Genes/environment/height | 3 | Drug nephrotoxicity and height/dementia association are misapplied to growth. Unsupported universal heritability claim. Correctness/relevance fail. |
| Seed germination | 3 | Related specialized studies found, but extrapolation from species-specific coat/permeability findings makes a weak general overview. Partial, not a complete supported explanation. |
| Berlin vegan listings | 4 | Bounded saved-listing response: date and ranking limits, source-backed entries. This does not establish current venue status or exhaustive coverage. |
| Tokyo museums | 4 | Bounded dated/unranked listings including a television mini-museum. Does not verify current opening/access. |
| Peanut-allergy guarantee | 2 | Correctly declines guarantee; unrelated epidemiology/simulation studies still admitted and cited. Refusal behavior passes, retrieval relevance fails. |
| Current local road closures | 4 | Declines to invent current closures, but unrelated accident/pollution/trauma studies are cited. Refusal passes, retrieval/routing fail. |
| Missing personal report | 3 | Does not invent personal blood results, but searches unrelated clinical papers despite no attachment. Refusal passes; should ask for the missing report without library retrieval. |
| Atlantis population | 0 | Deterministic evidence-gap response. No invented population or sources. |

Primary references used to check two specific errors:
[Microsoft's LAN file-sharing guidance](https://support.microsoft.com/en-us/windows/experience/connectivity-networking/file-sharing-over-a-network-in-windows)
distinguishes local-network sharing from internet sharing;
[OpenStax meiosis](https://openstax.org/books/biology-2e/pages/11-1-the-process-of-meiosis)
places crossing over in prophase I. These references are review evidence, not
silently added training or app knowledge content.

## Decision

### Paired model-only controls

Three follow-up controls used the same questions/model/sampler without collections;
raw report: `build/retrieval-matrix-oct1/model-only-controls/report.json`.

- LAN: model-only correctly permits local SMB file sharing without internet, unlike
  the retrieved-context answer. It still adds an unjustified blanket software-license
  requirement. This suggests context-induced deterioration in this case, not a
  universally reliable model-only fallback.
- Boats: model-only mentions displacement but falsely says water is denser than
  steel and gives an unreliable stability explanation. Rejecting metaphorical
  passages alone does not repair this model-knowledge failure. Compare
  [OpenStax's density table](https://openstax.org/books/college-physics-2e/pages/11-2-density)
  and [buoyancy explanation](https://openstax.org/books/university-physics-volume-1/pages/14-4-archimedes-principle-and-buoyancy).
- Height: model-only avoids the irrelevant renal discussion but invents specific
  genetics details and keeps overconfident growth claims. It places FGFR3 on
  chromosome 1, whereas [NCBI identifies chromosome 4](https://www.ncbi.nlm.nih.gov/gene/2261).
  Not a clean correctness pass.

Do not label the current candidate release-ready. Do not fix the boat case with an
idiom blacklist or mistake stricter keyword overlap for semantic verification.

Priorities supported by this run:

1. Route missing attachments and inherently unavailable personal/live facts before
   broad library retrieval. Preserve useful saved-document requests and explicit
   questions about historical material.
2. Separate general explanations from venue listings and narrow scientific studies.
   Evaluate intent-aware selection against both positive and negative examples;
   current conservative rules can reject useful synonym passages too.
3. Add a rights-checked, versioned foundational reference collection and test it
   against the same questions plus a separately held-out set. More specialized
   article volume alone will not fill the demonstrated coverage gaps.
4. Run paired relevant-context/model-only controls for correctness failures such
   as LAN sharing before changing model settings. The current model can be wrong
   even when attribution is explicitly labelled unverified.

Recall has **not** been measured: this run has no exhaustive gold passage set.
Source counts do not measure relevance, and refusals with unrelated citations are
not counted as fully successful research answers.

## Repeated phone checks

Installed APK hash verified directly on the Infinix:
`620224c51c6c59d5ec79680a2ddbd4e2a5c2d2d50bbd7c62c4b616984f0ea104`.
No app code, model, collections or radio settings changed in this evaluation.
Question matrix SHA-256:
`75c4fc2e7c156c5ec072f9a25fbc729b036a33aa17b27ad0e8e8e9e90b26e669`.

| Question / state | Retrieval | First word | Total | Output |
| --- | ---: | ---: | ---: | --- |
| Ice / process restarted | 14.59 s | 32.06 s | 82.10 s | 189 tokens, no supporting sources |
| Ice / warm | 14.60 s | 31.96 s | 67.07 s | 136 tokens, no supporting sources |
| Berlin / process restarted | 11.07 s | 11.08 s | 11.08 s | Four cited listings, no model generation |
| Berlin / warm | 11.04 s | 11.05 s | 11.05 s | Four cited listings, no model generation |

These are app-reported query timings, **excluding model preparation and test input**.
The harness measured launch/navigation-to-ready intervals including typing and UI
inspection of 40.81/13.74 s for ice and 37.55/14.07 s for Berlin. Those mixed
intervals are not pure model-load times. Process restart does not flush OS caches;
two observations per question cannot establish a latency distribution. Differing
ice output lengths prevent attributing its total-time difference solely to warming.

The first harness attempt preserved an ERROR after waiting for Start research while
the UI offered Prepare for research. This was a missing harness action, not a
proven app crash. A separate Berlin retry handled preparation explicitly; the
original attempt remains in `build/phone-release-check/repeated-matrix.json` and
the completed retry in `berlin-repeated-matrix.json`. No history or user data was
cleared. All four intended query runs completed across these two records.

Android reported no active default network, no ANR since boot, and no Field Atlas
fatal exception in the inspected crash buffer. This does not replace an offline
traffic audit or long-duration stability test. Desktop servers and phone harness
processes exited. Phone left on the final Berlin answer-details screen.
