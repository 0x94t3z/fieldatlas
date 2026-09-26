# Field Atlas datasets

## Bundled Reference pack

`fixtures/reference/documents.jsonl` contains twelve compact project-authored CC0 reference notes covering seasons, water, solar storage, evidence comparison, Formula One, cars, batteries, climate, computing, networks, health information, and historical evidence. It is the pack bundled into the current Android build. It is focused reference coverage, not a comprehensive encyclopedia.

Build it with:

```sh
./scripts/build_reference_pack.sh
```

The build is deterministic and preserves document IDs, titles, provenance, license, and source passages for local citations.

## Wikivoyage places to eat (optional build)

`tools/build_wikivoyage_eat_pack.py` converts the pinned [1 September 2026 English Wikivoyage pages dump](https://dumps.wikimedia.org/enwikivoyage/20260901/enwikivoyage-20260901-pages-articles.xml.bz2) into one local document per `Eat` listing. Each document retains its destination, venue name, description, listing update date when present, page revision date, and a permalink to that revision. The text license is [CC BY-SA 4.0](https://en.wikivoyage.org/wiki/Wikivoyage:Copyleft). The dump SHA-256 is `c6cebf6b109c31698e736858fd1d8dec1c41d87437aa4c4cafd7b0df88777773`.

The verified local build contains 79,566 listings in a 55,047,040-byte `.fapack` (SHA-256 `adf90e6b4dfbf1da0e538862400130071c49dc56cf5d81ea005e0f5a7a141e73`). This pack is an optional build artifact, not bundled with the APK or published as a release asset. Listings can be incomplete or stale; a dated mention of a vegan option does not establish current opening, menu, or quality. The exact build command is in [tools/README.md](tools/README.md).

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
