# Candidate architecture review and independent reference collection

## Scope

Read-only review of public documentation and selected retrieval/routing source.
No competitor corpus, topic list, implementation, benchmark questions or answers
were imported. We did not install or independently benchmark either competitor.
Performance numbers in their repositories are author-reported, not Field Atlas
measurements, and devices/models differ.

Reviewed repository heads:

- AndroidLM: `5abca020a65dea01a04d52131943e440a5e6f5cf`.
- BOAR: `6b53ee8398510fdc2b90e5db27c5a20d3e4406f9`.

The stored [bounty requirements](../compliance/bounty-31-source.md) call for useful
offline research within 12 GB RAM and 50 GB total storage. Extreme MoE is a
suggested direction, not mandatory. A pack size or model parameter count alone
does not establish the requested quality bar. The live bounty page could not be
read in this session; no new judging interpretation is inferred.

## What we learned

| Candidate | Observed approach | Field Atlas implication |
| --- | --- | --- |
| AndroidLM | Broad Wikipedia coverage, specialist libraries, title/redirect resolution; disk-streamed MoE. Its search investigation measures expensive stages and verifies unchanged retrieval after optimization. | Build broad foundations separately from specialist collections. Profile retrieval before changing it; preserve answer/source behavior when optimizing. Do not assume its model fits or runs equally fast on our device. |
| BOAR | Optional tiered knowledge packs, keyword and embedding retrieval, contribution limits per article, explicit relevance rejection; routing accounts for available models and work required. | A larger corpus needs selection controls and coverage metadata. Optional packs should remain independently removable, reproducible and clearly scoped. More model calls are not automatically better research. |

Primary review sources:
[AndroidLM README](https://github.com/Phineas1500/AndroidLM/blob/5abca020a65dea01a04d52131943e440a5e6f5cf/README.md),
[AndroidLM search measurements](https://github.com/Phineas1500/AndroidLM/blob/5abca020a65dea01a04d52131943e440a5e6f5cf/notes/2026-09-25-search-speed.md),
[BOAR pack design](https://github.com/rferrari/boar-app/blob/6b53ee8398510fdc2b90e5db27c5a20d3e4406f9/docs/KNOWLEDGE_PACKS.md),
[BOAR retrieval](https://github.com/rferrari/boar-app/blob/6b53ee8398510fdc2b90e5db27c5a20d3e4406f9/src/rag/retrieve.ts),
[BOAR routing](https://github.com/rferrari/boar-app/blob/6b53ee8398510fdc2b90e5db27c5a20d3e4406f9/src/routing/router.ts).
Some BOAR documentation has inconsistent historical status/default-model statements;
we do not treat every README claim as a current verified result.

## Implemented independently

- Created `tools/everyday_reference_topics.json`: a bounded, independent selection
  across physical science, Earth/space, biology, evidence/statistics, computing and
  history/society. Original Wikipedia revisions are fetched directly from Wikimedia.
- Extended our existing builder with a separately validated pack ID/title. The
  older pilot defaults remain unchanged; this collection cannot overwrite an
  existing output directory or silently replace the pilot's identity.
- Reused our verified source snapshots, retained revision URLs/license attribution,
  and preserved complete paragraphs. No synthetic answer snippets were inserted.
- Froze eight development questions and review criteria before the first fetch.
  The app/model/prompt policy remain unchanged for the paired comparison. Reports
  include original retrieved passages and generated output, not just scores.

The first 58-article candidate is retained under `build/everyday-reference/preview/`.
It produced 1,463 sections and 2,600 chunks in a 4,280,262-byte pack; repeat builds
were byte-identical. After inspecting coverage, Boat and File sharing were added
as practical topic articles, not hardcoded answers. This is development-set
iteration and must not be called held-out validation.

## Limitations that must stay visible

The extraction omits tables, media and math-containing paragraphs. It is not a
complete encyclopedia, mathematical textbook or current-information service.
Keyword-only retrieval can miss equivalent wording. Presence of a relevant
source does not guarantee the model interprets it correctly: the first comparison
admitted genuine LAN definitions but generated a contradictory conclusion.
Publication and automatic recommendation require more than a successful build.

No public release, catalog change or replacement model was made. Phone import
requires a connected device; none was available during this work.

## Final local candidate and paired review

The 60-article 0.2.0 preview is in `build/everyday-reference/final-preview/`:
1,484 sections, 2,633 chunks, 4,337,718-byte pack, SHA-256
`1a874462b97877909d1ddca21ce59481cdd4b786f5a4f213fe10a02b67f0bbc9`.
Its package is byte-identical to the separate `practical-preview/` build. All
26 original source records remain identical; 34 articles were added directly
from Wikimedia. The extraction report records 678 removed tables and 685 math
elements in the input; these counts are not retained-content completeness claims.

One independent app fix resulted from testing: an explicitly named multiword
reference topic can retain its real definition even if question consequence words
do not overlap. It requires an Overview section, exact topic phrase, all topic
terms in the body, and an explanatory statement. A matching title alone cannot
qualify; source-only requests retain their separate behavior. This fixes the
observed sampling-bias definition rejection, not general semantic retrieval.

Final paired reports:
`build/everyday-reference/evaluation/{final-baseline,final-preview}/report.json`.
Both use identical app/tool hashes, model and question order; the candidate adds
only the new database to existing Biology/Travel. Qwen3.5 2B, desktop keyword-only
retrieval and non-streaming inference are unchanged. These are developer-reviewed
development cases, not independent grading or phone performance measurements.

| Question | Baseline/candidate source counts | Candidate observation |
| --- | --- | --- |
| Boats | 0 / 1 | Added boat/raft passage. Steel-density error disappears, but answer still equates displaced volume with weight; linked raft-history quote does not support the mechanism. |
| LAN file sharing | 0 / 3 | Internet, LAN and file-sharing definitions selected. Correct main yes answer; unnecessary removable-media/BitTorrent discussion remains. |
| Mitosis/meiosis | 0 / 6 | Actual reference definitions selected, but universal ploidy/gamete wording and imprecise crossing-over stage remain. |
| Seasons | 0 / 4 | Relevant seasons passages and correct core opposite-illumination explanation; extra distance/thermal-lag generalizations still need qualification. |
| Thermal insulation | 0 / 5 | Relevant overview/transfer passages; answer gives a barrier explanation but drifts toward greenhouse/aerospace examples. |
| DNS/local sharing | 0 / 1 | File-sharing overview selected, not the installed DNS definition. General name-resolution explanation remains incomplete; internal names can also depend on DNS. |
| Sampling bias | 0 / 1 | Actual definition is now retained; model adds an unsound causal example. |
| Live road closures | 0 / 0 | Same deterministic offline limitation, no invented closure or model generation. |

Selection counts are not correctness scores. The new pack improves available
reference coverage but does **not** pass a generated-answer accuracy release gate.
Keep it an optional preview; do not advertise it as a tested replacement for
internet research or proof of the bounty's >50% bar. Neither candidate review nor
these runs establish that Field Atlas is better than the other submissions.

Verification: 390 regular JVM tests passed, two opt-in tests skipped; 17 builder/
tools tests, 12 pack-format tests and 57 script tests passed. Release build, lint,
instrumentation compilation, offline packaging and native alignment checks passed.
The signed normal app update is prepared but not installed because ADB reported
no connected phone. The new pack remains a local importable preview, not an
automatically installed or publicly published collection.

### Subsequent phone follow-up

After reconnection, installed the verified signed update and imported/enabled the
preview through Library. Sampling-bias and LAN queries retrieved the new sources;
the source viewer opened local text with its revision URL. LAN generation failed
by answering both no and yes in the same response. Query totals were 89.72 s and
105.16 s, respectively; no active default network was reported. See the
[release audit](../compliance/release-audit.md) for exact hashes, timings and raw
evidence locations. Installation is now complete; accuracy approval is not.
