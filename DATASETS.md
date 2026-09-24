# Field Atlas datasets

## Starter evidence pack

`fixtures/starter/documents.jsonl` contains four small, project-authored CC0 documents. It exists to prove deterministic pack construction, Android import, FTS5 retrieval, citation display, the required seasons smoke query, and the offline workflow. It is not broad coverage. Questions outside this pack can still be answered by the offline model, but those answers are uncited and are not treated as source-backed research.

Its manifest declares `demo` coverage, a short scope summary, and three example questions that are answerable from those documents. Field Atlas uses this metadata only for disclosure and query suggestions; it never treats it as model instructions.

The deterministic ready-to-import artifact is published as [`releases/fieldatlas-starter-1.0.0.fapack`](releases/fieldatlas-starter-1.0.0.fapack), 25,500 bytes, with SHA-256 `51769d845dc163aa4a56a15d9ad68eb3d65f12f1649d56b2105a906c9e0c453b`.

Every JSONL row retains its document ID, title, source attribution, license, and original text. The builder applies Unicode NFKC normalization, orders documents by UTF-8 document ID, and creates paragraph-first chunks of at most 1,200 characters with 150 characters of overlap. Chunk IDs have the form `<document_id>:<zero-padded-index>`.

Build it with:

```sh
./scripts/build_starter_pack.sh
```

The command creates `build/packs/starter/content.sqlite`, canonical `manifest.json`, `SHA256SUMS`, and `fieldatlas-starter-1.0.0.fapack`. ZIP entries are uncompressed, ordered, and timestamped at the ZIP epoch so two clean builds from identical source bytes are identical.

The authoritative per-document provenance table is [fixtures/starter/LICENSES.md](fixtures/starter/LICENSES.md). No downloaded, scraped, paywalled, or third-party corpus is embedded in this starter pack.
