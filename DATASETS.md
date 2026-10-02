# Field Atlas datasets

## Everyday reference preview (October 1)

### Local semantic-search experiment — 0.3.0

The local 0.3.0 preview adds a search index to the existing 0.2.0 content;
all 2,633 source passages, titles and revision URLs were compared and are unchanged.
It uses the same hash-verified BGE-small-en-v1.5 query encoder as the installed
biology pack. This is a development artifact, not a published catalog update.

- Archive: `build/everyday-reference/final-preview/pack/pack-0.3.0.fapack`
- Size: 42,108,940 bytes; SHA-256:
  `c0142831da0017d6755b6f52c1648212c3c66b2925150abc1204e53d4a598a4a`.
- Encoder SHA-256:
  `f046db1dc724cf4f6f0a0c5917e922823b73eb1d27b8f9a9c2797f7866974804`.
- Encoding: CLS pooling, L2 normalization, 384 dimensions, int8 storage;
  query instruction applied only to queries. Three passage embedding inputs
  exceeded 512 tokens and were capped, keeping their special tokens. The actual
  source text was not shortened. Corpus text retains its original attribution;
  the [BGE encoder](https://huggingface.co/BAAI/bge-small-en-v1.5) is MIT-licensed.

The frozen probe, vectors, original database identity and query vectors are retained
under `build/reasoning-diagnosis/`. `tools/build_vector_pack.py` packaged the index
and ran its quantization self-match guard. A separate run used the actual Kotlin
vector search, fusion, relevance filtering and prompt packing. The buoyancy
passage became the leading result, but generated explanations still contained
errors. Retrieval similarity is not a factual-support score or a quality pass.

### Original keyword-only preview — 0.2.0

A separate, independently selected 60-article collection now covers physical
science, Earth/space, biology, evidence/statistics, computing and history/society.
It is fetched from original Wikimedia revisions, not another submission's corpus.
The 26 earlier pinned source records are retained unchanged. This preview is not
a complete encyclopedia or a claim that generated answers meet the bounty bar.

```sh
python3 tools/build_general_reference.py fetch \
  --topics tools/everyday_reference_topics.json \
  --cache build/everyday-reference/practical-snapshot
python3 tools/build_general_reference.py build \
  --topics tools/everyday_reference_topics.json \
  --cache build/everyday-reference/practical-snapshot \
  --output build/everyday-reference/final-preview \
  --version 0.2.0 --preserve-paragraphs \
  --pack-id everyday-reference --title 'Everyday reference · Preview'
```

Use an existing hash-verified snapshot to reproduce the exact tested dataset;
a fresh fetch selects newer revisions. Build refuses to overwrite an existing
output. The local artifact is
`build/everyday-reference/final-preview/pack/everyday-reference-0.2.0.fapack`:
4,337,718 bytes, 1,484 sections and 2,633 chunks. Complete source paragraphs are
preserved; tables, images and math-containing paragraphs are still omitted.
There are no embeddings. Attribution, source revisions, original topic selection
and extraction counts accompany the build. The manifest carries license and
revision URLs for the imported pack.

`tools/everyday_reference_checks.json` contains eight frozen development questions.
Paired baseline/preview tests retain original retrieved passages and generated
answers; source coverage and answer correctness are reviewed separately. See the
[architecture review](docs/evidence/candidate-architecture-review-2026-10-01.md).
This pack is not automatically recommended or published in the download catalog.

## General Reference pilot (development only)

`tools/build_general_reference.py` builds a separate, independently selected
24-article English Wikipedia pilot across six subject areas. It does not use
another submission's corpus. This is a bounded pipeline experiment, **not** a
general encyclopedia, published collection, or answer-quality certification.

```sh
python3 -m pip install -r tools/general_reference_requirements.txt
python3 tools/build_general_reference.py fetch
python3 tools/build_general_reference.py build
python3 -m unittest discover -s tools/tests -p 'test_general_reference.py'
```

Fetch uses Wikimedia's API sequentially with an identifying User-Agent, pins
each article revision and timestamp, and records response SHA-256 values in
`build/general-reference/snapshot/sources.lock.json`. Interrupted fetches reuse
hash-checked completed articles. A new topic selection requires a new cache.
Keep the entire snapshot cache: it is the reproducible input, not just the topic
list. A fresh fetch without that cache selects newer revisions and is a different
dataset. Rendered templates can also change independently of an article revision.

Build is offline, rejects incomplete/corrupt input, and refuses to overwrite an
existing output directory. It produces the app-compatible `.fapack` under
`build/general-reference/pilot/pack/`, a document spool, attribution file, source
lock, and extraction report. Text remains CC BY-SA 4.0, credited to Wikipedia
contributors through the original revision URLs; the report records transformation
and extraction limits. See [Wikimedia reuse terms](https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use#7._Licensing_of_Content).

Article sections become separate documents with the article and heading path in
their titles. Navigation, reference lists, media, tables and math-containing
paragraphs are omitted. Nested lists are flattened; long paragraphs still use the
existing bounded pack chunker. Consequently this is **not** full mathematical,
table or procedure preservation. The corpus has no embeddings. Packaging tests
do not validate Android retrieval or generated answers.

`tools/general_reference_checks.json` freezes four development questions and
manual review criteria before the pilot's first inference comparison. Compare
model-only against the same model with the pilot, using the existing desktop
runner; do not substitute these known cases for the unchanged bounty benchmark
or call completion an accuracy pass. Phone testing and source/license review are
required before catalog publication. The website and download catalog intentionally
do not advertise this pilot.

Initial development comparison (30 September 2026): the 24-article snapshot built
690 section documents and 1,484 searchable chunks, approximately 2.46 MB packaged.
Four identical prompts were run with and without the pack through the actual Kotlin
desktop pipeline and the saved Qwen3.5 2B model. Reports are under
`build/general-reference/evaluation/{with-pack,model-only}/report.json`.
**The pilot did not pass qualitative review.** In the agriculture answer, irrelevant
writing-history evidence contaminated the explanation. The first three pack-backed
responses had no accepted citations. The current-information question refused to
invent an earthquake but needlessly cited unrelated passages. Model-only answers
also contained errors; neither configuration is an accuracy baseline.

The next gate is retrieval precision and evidence use, not more downloads. The
lexical filter can accept incidental overlap among generic instruction words;
its short-definition and two-single-word-comparison guards do not cover these
longer questions. Prompt instructions alone did not prevent subject contamination.
This is an English, keyword-only desktop diagnosis, not a measured phone result.

### Targeted retrieval follow-up

The next run, retained at
`build/general-reference/evaluation/retrieval-fix/report.json`, uses the same
snapshot, model and four questions. Search no longer treats `which`, `only`,
`any`, `each`, `every` or `using` as content terms. For sectioned reference
articles, explicitly named parent subjects constrain which other passages can
join the evidence; multiple named subjects and cross-article mentions remain
eligible. This is a lexical precision guard, not semantic verification. It does
not solve indirect references, ambiguous names, synonyms or passage-level support.

Observed: the writing-history claim disappeared from the agriculture answer,
and the current-events question returned an evidence-gap message without unrelated
citations. The agriculture response still digressed and the networking answer
still needs factual review. The first three answers remained uncited model
explanations. The pilot is still not approved for publication; a targeted retrieval
regression fix is not a general research-quality pass.

Verification for this follow-up: the ordinary JVM suite and 11 connected Android
retrieval tests passed, including a real SQLite subject-contamination fixture.
The optimized release build passed. The existing Biology/Travel desktop regression
retained Berlin's four cited listings and the fictional-city evidence gap; the
biology and seasons model explanations still contain limitations and must not be
reported as quality passes. These checks do not constitute on-phone generated-answer
validation of the new pilot, which remains uninstalled and unpublished.

### Expanded coverage candidate (October 1)

`tools/general_reference_expanded_topics.json` adds Spreadsheet and P-value to the
24-article development pilot. `fetch --seed-cache` copies and validates the original
pinned snapshots into a new cache, then fetches only additions. The original topic
list and cache are unchanged. Builds remain offline and include revision attribution.

The local 0.3.0 candidate has 26 articles and 741 sections; its pack SHA-256 is
`dd4acf112ed6d99d776b9b1f26047cea9ce9e760a54a61cae3806c2d323779ba`.
It is not published or added to the download catalog. All 24 original source records
are identical. Added revisions: P-value 1364624459 and Spreadsheet 1376895772.

Eleven known development questions were rerun with the unchanged 2B model. Added
coverage brings p-value limitations into the selected passages, but coverage alone
does not fix generated answers. For explicit two-subject comparisons, title nomination
now prefers each exact subject's Overview over shorter History/Types sections. This
restores both Database and Spreadsheet introductions; the other ten answers stayed
byte-identical in that selection-only comparison. The database answer still treats
relational properties as universal, partly reflecting overbroad source wording.
The algorithm answer still contradicts its source. Neither is a quality pass.

Local reports: `build/general-reference/evaluation/expanded-coverage/report.json`
and `build/general-reference/evaluation/comparison-coverage/report.json`.

### Difference-question relevance follow-up

The October 1 local follow-up extends the existing single-word comparison gate
to questions such as "How does a battery differ from a capacitor?" and "How is a
battery different from a capacitor?" It also recognizes definitions preceded by
a short `In …,` qualifier without removing that qualifier from the actual source.
Ambiguous multiword comparisons and study-specific trailing clauses retain the
ordinary relevance path. This is an English lexical heuristic, not semantic
verification, and it does not alter model selection or answer instructions.

With the same 2B model and paragraph-pack database, the database/spreadsheet
question now selects **Database — Overview** instead of a document-oriented
database definition and a desktop-software history passage. The other six known
development cases retain byte-identical requests and visible answers. Results:
`build/general-reference/evaluation/qualified-difference-subjects/report.json`.
The earlier syntax-only attempt is preserved at `difference-subjects/report.json`;
it excluded the qualified definition and fell back to model knowledge, so it is
not the retained implementation by itself.

This fixes evidence selection for this question, **not its overall answer**.
The resulting model explanation still overgeneralizes database support for
unstructured content and spreadsheet updates. The pilot has no dedicated
spreadsheet article, so comparison coverage is incomplete. All generated
comparison prose remains labelled unverified. Do not publish the candidate pack
or claim a quality pass from this follow-up. Biology/Travel regression results are
at `build/desktop-evaluation/difference-subjects-regression/report.json`; Berlin's
dated listings and the fictional-fact evidence gap remain intact, while the
known biology explanation problems remain unresolved.

Verification: the full JVM suite reported 371 tests, zero failures/errors and two
skips; `:app:assembleRelease` completed successfully offline. Phone testing and
installation remain deferred; this is not a published release.

### Rejected ranking and answer-composition experiments

Further local trials on 30 September 2026 tested section-title ranking, section
diversity, shorter answer instructions, a four-passage reference limit, and an
explicit question-coverage instruction. None justified replacing the preceding
retrieval fix. Depending on the variant, answers incorrectly denied heat transfer
through a vacuum, omitted convection, reversed the network-outage scenario, or
added unsupported historical details. All of these experimental production changes
were reverted; the targeted retrieval follow-up above remains intact.

Local reports are under `build/general-reference/evaluation/` in
`section-ranking`, `section-ranking-refined`, `concise-composition`,
`bounded-reference-retry`, and `coverage-candidate`. The earlier
`bounded-reference` run had a Gradle result-writing collision with a concurrent
test run and is not a successful execution; its retry ran sequentially. These
are known development questions, not an independent benchmark score.

After restoring the prior implementation, three fresh questions about the water
cycle, databases versus spreadsheets, and genes versus environment completed in
`fresh-checks-stable/report.json`. All remained labelled unverified model
explanations. Review still found overly absolute statements about evaporation and
when databases are required. Completion does not establish accuracy or citation
support. The ordinary JVM suite passed after restoration.

Phone validation and installation were deferred at the user's request; these
local experiments do not establish Android answer quality.

### Controlled manual-evidence comparison

Two of the same development questions (heat transfer and networking) were run
with manually selected, unmodified pilot passages and again with automatic
retrieval. The reports are `build/general-reference/evaluation/manual-evidence/report.json`
and `build/general-reference/evaluation/automatic-evidence-control/report.json`;
the selection is `build/general-reference/evaluation/manual-selection.json`.
Model/database hashes, app-source hash and server command match between runs.
The relevance gate and prompt packing were not bypassed. All five selected heat
passages and all three selected network passages reached the recorded prompts
as excerpts. No production app code changed for this comparison.

The manual heat answer incorrectly said all three mechanisms can transfer heat
through a vacuum, despite receiving the explicit radiation/vacuum passage and
the conduction/convection material requirements. The automatic answer correctly
distinguished vacuum transfer but described photons as opposed to particles.
This is evidence of a downstream generation problem, not only missing retrieval.
It does not isolate model capability from prompt design or prove a universal
failure rate from two questions and one fixed seed.

For networking, manual selection removed distracting overlay/protocol passages
and produced a clearer local/global distinction, but the answer overgeneralized
offline video conferencing and added a confusing final connectivity qualification.
Automatic retrieval included peripheral overlay/VPN/ATM passages; its answer
introduced an unnecessary overlay explanation and framed local networking as a
fallback. Neither answer is a verified source-backed quality pass. All four
responses remained labelled unverified model explanations, consistent with the
current mixed-answer policy, which permits background knowledge and makes exact
source quotations optional. Lack of citations alone therefore does not identify
a retrieval failure.

The following investigation compares answer policies using fixed evidence,
before expanding the corpus or changing the default model. The
[diagnostic instructions](docs/evaluation.md#manual-evidence-diagnostic) explain
how to reproduce this test without changing the Android app.

### Fixed-evidence answer-policy comparison

The same two questions and selected passages were tested with the existing strict
source-only instructions and one test-only partial-answer candidate. Reports are
`build/general-reference/evaluation/source-only-policy/report.json` and
`build/general-reference/evaluation/source-partial-policy/report.json`. Against
the preceding `manual-evidence` control, model/database identities, production
source hash, packed evidence/question suffix, system message, and other inference
request settings match. Only the user-message instruction prefix changed.

The existing source-only policy corrected the heat/vacuum distinction, but refused
the entire networking question despite the supplied network and internet
definitions. The partial-answer candidate asked for supported parts plus specific
gaps. It answered both questions and preserved the vacuum distinction, but its
network answer then contradicted its own explanation with an overly broad gap
statement. Heat examples introduced details absent from their cited excerpts.
Both policies also retained an overly narrow solids-only conduction description
from the selected context. A source-number marker is not evidence of entailment.

These observations are from two known development questions and one fixed seed,
not a pass rate. The partial-answer candidate was designed after inspecting the
strict run and therefore is not held-out validation. Neither policy is approved
as a production replacement. More data alone is not demonstrated to solve these
generation, evidence-use, and source-context limitations.

The override changes inference instructions only. Existing mixed-answer display
processing strips paraphrase citations and retains its unverified-model label;
the raw answers and displayed answers were reviewed separately. Shipping a future
policy requires consistent attribution behavior as well as independently reviewed
claim support and fresh-question checks. Test tooling checks and the ordinary JVM
suite pass; no app-default, corpus, model, or phone-installation change was made.

### Paragraph-context implementation candidate

The local prompt builder now prefers an intact paragraph of at most 1,200
characters over its previous three-sentence window for passages longer than 700
characters. It chooses by query-term coverage and retains adjoining English
qualification/anaphora paragraphs (for example, "However" or "These"). A connected
unit that cannot fit is omitted rather than clipped. Longer unstructured
paragraphs retain the prior sentence-window fallback. Original source text and
citation mapping remain unchanged; this cannot recover text already cut by the
pack chunker and is not a semantic-support check.

Four new unit tests cover later definitions, cross-paragraph qualifications,
dependent paragraphs, and omission of oversized connected units. The eight
claim-support cases in `build/claim-support/paragraph-context/report.json` produced
raw and visible answers identical to `app-baseline`; their short passages are
unaffected. The four real-source development questions were also rerun in
`build/general-reference/evaluation/paragraph-context/report.json`.

The heat answer retained all three mechanisms and the correct vacuum distinction.
The network answer still overstates proximity/built-in capabilities as sufficient
for local communication. Agriculture still digresses into a food-loss statistic;
the current-events question still returns an evidence gap. Consequently the
context-preservation change is a **local implementation candidate, not an overall
answer-quality pass or approved release**. It does not change ranking, policies,
models, downloads or catalog publication. Phone testing and installation remain
deferred. Further quality work must address these failures before promotion.

### Whole-paragraph pack candidate (not published)

The reference builder has an opt-in `--preserve-paragraphs` mode. It groups whole
paragraphs toward a soft 1,200-character target without character overlap; English
dependent/qualification paragraphs stay with the preceding paragraph even when
the unit exceeds the target. It cannot guarantee cross-section or semantic
completeness. The legacy default and Biology/Travel builds are unchanged.

```sh
python3 tools/build_general_reference.py build --preserve-paragraphs \
  --version 0.2.0 --output build/general-reference/paragraph-pilot
```

The existing snapshot yields 1,307 chunks (longest 2,350 characters), versus 1,484
legacy chunks. Full rebuilds produced identical candidate SHA-256 values:
`13770f1ae8d2f1ea68741b8af0c4a1961902bca34662294ac5790371da9b76e5`.
A legacy rebuild still matches the original pack SHA-256
`7866c3a563c0cdbe300325a3d09368d3dc2a3d9770ceb62881b23f1de14583e9`.
Tests check lossless paragraph grouping, qualifications, deterministic packaging,
and unchanged default behavior. No competitor dataset is used.

Seven development questions were run in
`build/general-reference/evaluation/whole-paragraph-pack/report.json`. Removing
artificial chunk boundaries did not resolve generation errors: the heat answer
still uses an inaccurate photons-versus-particles contrast and omits a requested
radiation example; networking still overstates proximity as sufficient; the genes
answer introduces unrelated correlation material; and the database answer
overgeneralizes one database category. The current-information request still
returns an evidence gap. These failures block catalog publication. A structurally
better pack is not necessarily a better answer source under the current model
and retrieval pipeline.

## Adding future collections

The development app accepts independently produced knowledge packs using the supported fapack manifest and SQLite schema. A topic need not have a hardcoded app entry. The canonical download catalog is `app/src/main/assets/knowledge/catalog.json`; publishing compatible entries to that file on the project repository's main branch lets users discover them through explicit **Refresh collections**. Refresh is optional provisioning, not background research networking. Installed packs work independently of catalog availability.

Keyword search is the compatibility fallback. The current vector reader supports normalized `int8-symmetric-per-vector` blobs in `chunk_vectors`. Packs using another layout or an encoder incompatible with the active encoder remain keyword-only. Encoder identity is checked again when encoding to avoid mixing vector spaces if enabled packs change mid-query. This is not support for arbitrary model/embedding formats. Pack content does not override citation or answer policies.

[Back to the overview](README.md) · [Phone setup](docs/installation.md)

A **knowledge pack** is a saved collection the app can search when answering. It is optional: the model can still answer without one, but there will be no local source citation. A pack only covers the topics in its own data; it does not refresh itself while the phone is offline.

| Collection | What it covers | Included in the current APK? | Main caution |
| --- | --- | --- | --- |
| Biology vector pack | Biology and longevity passages | No; import separately | Not general or travel coverage. |
| Wikivoyage places to eat | Dated restaurant listings from a 2026 dump | No; build and import separately | Listings and opening details can be stale. |
| Wikivoyage places | Dated See, Do, Eat, Drink, Sleep, and Buy listings | No; download separately | Wider travel scope, still not a current recommendation service. |
| Vegan places (OpenStreetMap) | Restaurants, cafés, and shops tagged fully vegan or with vegan options, worldwide | No; build and import separately | Tags and hours can be stale; no ratings or “best” ranking. |
| Wikipedia mini (keyword search) | General background from selected articles | No; build and import separately | Snapshot coverage; no live or private facts. |
| Historical Reference and Starter fixtures | Small sets used to reproduce older tests | No longer the current built-in knowledge | Not a broad encyclopedia. |

**For non-developers:** the public v1.1.10 release is different from the current source build: it bundles its own small Reference collection. Use the [version-matched install guide](docs/installation.md) before choosing a pack. The sections below give source, license, checksum, and build details for people who want to inspect or recreate the data.

## Biology vector pack (optional)

The [fork's biology vector pack](https://github.com/vbuterin/fieldatlas/tree/vector-search/releases/packs) is a separate 1.2.0 `.fapack` with local int8 passage embeddings and a local query encoder. It covers biology and longevity sources, not general travel or all of Wikipedia. Its published SHA-256 is `0cc4cfddc2eb6660707e3388e5e2a6f687c334cd1bb74c4f0621d4becb091d01`. The current development build lists it as an optional recommended download in Library; it checks the full archive hash, then verifies every artifact during import. The public v1.1.10 app still needs a separately obtained compatible pack. It is not bundled into the APK. The exact corpus scope and source attributions remain in that pack's manifest and passages. The downloadable list is defined in `app/src/main/assets/knowledge/catalog.json`; adding a future hosted collection requires its URL, exact size, checksum, and a new app build. Only hosted collections are listed; the broader Travel places pack is now published, while Eat-only and Wikipedia mini remain unavailable as in-app downloads.

## Historical Reference fixture

`fixtures/reference/documents.jsonl` contains twelve compact project-authored CC0 reference notes covering seasons, water, solar storage, evidence comparison, Formula One, cars, batteries, climate, computing, networks, health information, and historical evidence. This remains a reproducibility fixture for the historical v2 benchmark; it is no longer bundled into the Android app. It is focused coverage, not a comprehensive encyclopedia.

Build it with:

```sh
./scripts/build_reference_pack.sh
```

The build is deterministic and preserves document IDs, titles, provenance, license, and source passages for local citations.

## Wikivoyage places to eat (optional build)

`tools/build_wikivoyage_eat_pack.py` converts the pinned [1 September 2026 English Wikivoyage pages dump](https://dumps.wikimedia.org/enwikivoyage/20260901/enwikivoyage-20260901-pages-articles.xml.bz2) into one local document per `Eat` listing. Each document retains its destination, venue name, description, listing update date when present, page revision date, and a permalink to that revision. The text license is [CC BY-SA 4.0](https://en.wikivoyage.org/wiki/Wikivoyage:Copyleft). The dump SHA-256 is `c6cebf6b109c31698e736858fd1d8dec1c41d87437aa4c4cafd7b0df88777773`.

The verified local build contains 79,566 listings in a 55,047,040-byte `.fapack` (SHA-256 `adf90e6b4dfbf1da0e538862400130071c49dc56cf5d81ea005e0f5a7a141e73`). This pack is an optional build artifact, not bundled with the APK or published as a release asset. Listings can be incomplete or stale; a dated mention of a vegan option does not establish current opening, menu, or quality. The exact build command is in [tools/README.md](tools/README.md).

## Broader travel places (keyword-only builder)

`tools/build_wikivoyage_places_pack.py` uses the same pinned English Wikivoyage dump and extracts six listing types: See, Do, Eat, Drink, Sleep, and Buy. Each place keeps its destination, category, address or directions, description, available hours and price, coordinates when supplied, listing check date when supplied, page revision date, and source permalink. The resulting `.fapack` uses FTS5 keyword search—**no embedding or vector build is required**. The app has a fast, source-only path for simple restaurant, café, museum, hotel, bar, and shop lookups; complex questions still use the normal research path.

The [Travel places download](https://github.com/0x94t3z/fieldatlas/releases/tag/knowledge-travel-2026.09.1) contains 401,826 listings and 405,849 searchable chunks in a 351,581,170-byte `.fapack` (SHA-256 `1aa075cb2f5dd57415589b3a8e083d701e63f9bb87498f3da97ccbda1c3567ac`). Download it and import it from Library. The local app catalog also includes its download URL; that Android update is not published yet. Pack verification passed, and earlier offline Infinix checks returned cited café listings for Chiang Mai and museum listings for Berlin. These checks do not establish broad answer quality. The [build command](tools/README.md#typical-flows) and source dump hash above make it reproducible. Listings cannot establish current opening, quality, or a present-day “best” ranking. The collection includes restaurant listings, so the overlapping Eat-only pack is unnecessary for most users.

## Vegan places from OpenStreetMap (keyword-only builder)

`tools/build_osm_vegan_pack.py` builds one searchable listing per OpenStreetMap place tagged
`diet:vegan=only` (fully vegan), `diet:vegan=yes` (vegan options), `diet:vegan=limited`, or
`cuisine=vegan`. It exists because Wikivoyage covers few vegan venues (four Berlin listings,
one in Tokyo), while OpenStreetMap records about 2,000 vegan-tagged places in the Berlin area alone.

- **Source and licence:** OpenStreetMap data via the Overpass API, © OpenStreetMap
  contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright). Every listing and the
  app's answer carry this attribution. The pack is a derived database under the same licence.
- **What a listing contains:** destination, category (Eat, Drink, Buy), name (English name
  when tagged, with the local name), vegan label, cuisine, address, hours as tagged,
  coordinates, the mapper's `check_date` when present, the data snapshot date, and a link to
  the OpenStreetMap element.
- **Destination:** the most populous city of at least 100,000 people within 30 km, else the
  nearest city or town within 20 km, else the tagged address city. Settlements come from
  OpenStreetMap `place=city`/`place=town` nodes fetched in the same run. Places with no
  destination are kept for keyword search but never answer "in <city>" questions.
- **Reproducibility:** `fetch` runs six exact-tag Overpass queries and stores every raw
  response with its SHA-256, query and Overpass `timestamp_osm_base` in `lock.json`. `build`
  is offline and byte-reproducible from that cache, and copies the lock into the pack output as
  `OVERPASS-LOCK.json`. Each response also records the Overpass endpoint it came from. Publishing
  the cache with the pack lets anyone rebuild it exactly;
  re-running `fetch` later gives newer data. `--date` requests date-pinned (attic) queries, but
  on the public server a worldwide attic query timed out after 900 s, against 81 s for current
  data.
- **Near me:** the pack adds a `place_points` table (latitude/longitude per search row). For
  "near me", "nearby" or "the city I'm in" questions, the app reads the phone's location from
  Android's `LocationManager` (GPS works offline and without Google Play Services), lists the
  nearest matching places within 15 km with straight-line distances, and keeps the location on
  the phone. Location permission is requested only when such a question is asked; without it,
  the app says how to enable location or name a city instead of guessing.
- **`cuisine=vegan`:** queried for completeness but returned no elements; OpenStreetMap tags
  vegan venues with `diet:vegan=only`.
- **Limits:** tags record what mappers entered and when; they cannot establish current opening,
  menus, prices, quality, or a "best" ranking. The app lists fully vegan places first and states
  each listing's check date or that none was recorded.

**Local build, 2 October 2026.** 52,064 places: 3,715 fully vegan, 47,056 with vegan
options, 1,293 limited; 48,270 Eat, 1,369 Drink, 2,425 Buy. 350 places have no destination.
Examples: Berlin 2,058, London 1,486, Paris 762, New York 690, Tokyo 183, Bangkok 123,
Singapore 102, Chiang Mai 85. The `.fapack` is 40,088,517 bytes, SHA-256
`6cc944185c18de577060833645ee91a4da511c2c32df77ffc3b4dbfaf2dd8f7c`, version `2026.10.02`;
two builds from the same cache were byte-identical. The cache is 88 MB of raw Overpass JSON.
Data timestamps run from `2026-10-02T00:35:39Z` (vegan queries, `overpass-api.de`) to
`2026-10-02T03:39:03Z` (`place=town`, fetched from the `maps.mail.ru` Overpass mirror after
the main server refused further connections). It is not published yet; the planned release tag is `knowledge-vegan-places-2026.10.02`, carrying the `.fapack`, `OVERPASS-LOCK.json`, the raw-response cache (`osm-vegan-overpass-cache-2026.10.02.tar.gz`, 18.5 MB) and `SHA256SUMS`. The in-app download catalog already lists this URL.

An earlier build of the same day fetched settlements with `out tags;`, which omits node
coordinates, so every place fell back to its address city and Tokyo had no listings. The
settlement queries now use `out;`, a changed query invalidates its cached response, and the
build stops if settlements arrive without coordinates.

On the Infinix X6840 (offline), "Tell me the best vegan restaurants in Berlin" and "…in
Tokyo" each returned six cited, fully vegan OpenStreetMap places in 15–17 s without running
the model. "Best vegan restaurants near me" obtained a location fix and reported that no
mapped place lies within 15 km of the test location.

## Encyclopedia and travel guides from Wikimedia dumps (keyword-only builder)

`tools/build_wikipedia_pack.py` builds keyword-searchable packs from Wikimedia CirrusSearch
"content" dumps, which carry each page's plain text, lead section, redirects, incoming-link
count and revision id. The same tool builds the Simple English encyclopedia and the
Wikivoyage travel guides.

- **Two steps:** `spool` streams the dump without saving it, records its SHA-256 and size,
  and keeps each article's lead, up to eight redirects as aliases and, for articles with at
  least 300 incoming links, up to 6,000 characters of body text. Disambiguation pages and
  leads under 80 characters are skipped. `build` is offline and byte-reproducible from the
  spool: it takes articles by incoming links, most linked first, until a text budget, and
  writes an "Article — Overview" passage (the lead, then aliases in a separate paragraph)
  and an "Article — Details" passage (the body excerpt). Each source links to the exact
  dumped revision (`/w/index.php?oldid=…`).
- **Template errors:** some dump text contains rendered Lua template failures. A failed
  Japanese-name template at the start of a lead is replaced by the article title ("Sapporo is
  the capital city of Hokkaidō"); other affected sentences are dropped. 4,628 Simple English
  passages contained one before this repair.
- **Licence:** CC BY-SA 4.0, with the revision link kept on every passage.
- **Limits:** a snapshot, not live. Simple English articles are shorter than English
  Wikipedia's. The full English Wikipedia dump (about 43 GB compressed) uses the same tool
  but was not built here; the connection used for this work streamed Wikimedia dumps at
  about 330 KB/s.

**Simple English Wikipedia, local build, 2 October 2026.** Dump
`simplewiki-20251229-cirrussearch-content.json.gz` (636,486,826 bytes, SHA-256
`b0fbbcc3d5055c025b51ae86c91fe7deb9e93629794e48ce49c218e49136fe27`): 278,283 pages, 211,159
articles kept, 245,100 passages. The `.fapack` (`simplewiki`, version `2025.12.29`) is
316,396,542 bytes, SHA-256
`5360e42b3d08abb7edb8003015910b2520ccb4b692864e2110469dfc32794eff`. On desktop, with this pack installed alongside
Biology, Vegan places, Travel places and Travel guides, the first keyword pass took 30–300 ms
per question after the packs were open, and "What is photosynthesis?", "Tell me about Japan's
history" and "Explain climate change" each led with the named article's overview.

**Wikivoyage travel guides, local build, 2 October 2026.** Dump
`enwikivoyage-20251229-cirrussearch-content.json.gz` (234,324,021 bytes, SHA-256
`6c854abe7226ab1efb748ac672db91fb26f45d94b17e1e53f6a7e932fc6fb10a`): 33,845 pages, 25,330
guides kept, 50,495 passages, built with `--copyright-page Wikivoyage:Copyleft`. The
`.fapack` (`wikivoyage-guides`, version `2025.12.29`) is 374,600,710 bytes, SHA-256
`2684b5b664aedb15aafb905a2dffccdd059dc589d10a382fdddec17c8bef60b2`. It complements the
existing Travel places pack (one listing per venue) with each destination's prose guide.

Both packs were rebuilt a second time from their spools and were byte-identical. Neither is
published yet; the planned release tags are `knowledge-simplewiki-2025.12.29` and
`knowledge-wikivoyage-guides-2025.12.29`, each carrying the `.fapack`, `SOURCE.json`, the
spool and its `.meta.json` (Wikimedia keeps CirrusSearch dumps for only a few weeks), and
`SHA256SUMS`. The in-app download catalog already lists both URLs.

## Wikipedia mini (keyword-only builder)

`tools/wiki_mini_build.py` selects a bounded set of articles from a Wikipedia dump and creates a general-background `.fapack` with FTS5 search. It does not require generating embeddings. See the [tool instructions](tools/README.md#typical-flows). It is a builder, not a bundled or published pack; its actual size, content, phone performance, and answer quality must be checked on the resulting artifact before making coverage claims. The source text is CC BY-SA 4.0, with attribution retained per document.

## Starter evidence pack

`fixtures/starter/documents.jsonl` contains four small, project-authored CC0 documents. It exists to prove deterministic pack construction, Android import, FTS5 retrieval, citation display, the required seasons smoke query, and the offline workflow. It is not broad coverage. Questions outside this pack can still be answered by the offline model, but those answers are uncited and are not treated as source-backed research.

Its manifest declares `demo` coverage, a short scope summary, and three example questions that are answerable from those documents. Field Atlas uses this metadata only for disclosure and query suggestions; it never treats it as model instructions.

The Starter pack is a reproducibility fixture rather than a current release asset. Build it locally with the command below; the deterministic result is 25,500 bytes with SHA-256 `51769d845dc163aa4a56a15d9ad68eb3d65f12f1649d56b2105a906c9e0c453b`.

Every JSONL row retains its document ID, title, source attribution, license, and original text. The builder applies Unicode NFKC normalization, orders documents by UTF-8 document ID, and creates paragraph-first chunks of at most 1,200 characters with 150 characters of overlap. Chunk IDs have the form `<document_id>:<zero-padded-index>`.

Build it with:

```sh
./scripts/build_starter_pack.sh
```

The command creates `build/packs/starter/content.sqlite`, canonical `manifest.json`, `SHA256SUMS`, and `fieldatlas-starter-1.0.0.fapack`. ZIP entries are uncompressed, ordered, and timestamped at the ZIP epoch so two clean builds from identical source bytes are identical.

The authoritative per-document provenance table is [fixtures/starter/LICENSES.md](fixtures/starter/LICENSES.md). No downloaded, scraped, paywalled, or third-party corpus is embedded in this starter pack.
