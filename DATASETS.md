# Field Atlas datasets

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
