# Field Atlas 1.3.0 — prerelease (draft, not yet published)

This is an early-access research build, not a claim that answer quality or bounty
acceptance criteria have been met. When it is published, this file replaces
`current.md`.

## Changes

- **Vegan places.** A new downloadable collection (40 MB) of 52,064 restaurants,
  cafés and shops that OpenStreetMap tags as fully vegan or with vegan options,
  worldwide. City questions list fully vegan places first, with the address and
  each listing's check date. Map data © OpenStreetMap contributors (ODbL).
- **Encyclopedia.** A new downloadable collection (316 MB) of 211,159 Simple English
  Wikipedia articles from the December 2025 dump: every article's lead, with longer excerpts
  for widely linked topics. Each passage links to the exact revision it came from.
- **Travel guides.** A new downloadable collection (375 MB) of 25,330 Wikivoyage destination
  guides (understand, get in, get around, eat, sleep, stay safe), alongside the existing
  Travel places listings.
- **Collections by category.** Library and setup group collections as Encyclopedia, Places,
  Travel and Science.
- **Named articles first.** Questions that name an article ("Who was Marie Curie?", "Tell me
  about Japan's history") now lead with that article instead of a similarly named one, and
  "What is the capital of Australia?"-style questions are no longer filtered to nothing.
- **Near me.** Questions such as "vegan restaurants near me" use the phone's own
  location through Android's location service, without Google Play Services, and
  list matching places by distance (within 1, 3 and 10 km). Location permission is
  requested only when such a question is asked, and the location never leaves the
  phone. Without a location fix, the app says how to turn it on or name a city.
- **Faster search.** Keyword ranking reads only the search index before loading
  passages: "Tell me about local area network" went from 19.6 s to 7.0 s of
  retrieval on the same 4 GB phone. Place questions now search only place
  collections, and collections are searched in parallel.
- **Source-first overviews.** "What is X?" questions show a verbatim quote from the
  saved reference while the model is still reading, labelled separately from the
  model's unverified explanation.
- **Refreshed interface.** A three-step research tracker; structured source
  excerpts as label/value rows with Previous and Next; a Library storage summary
  and per-pack menu; History grouped by day with Ask again; dialogs, menus and
  switches on the app palette; Inter for reading text; a green launch screen.

## Known limitations

- Model-only answers can contain factual errors. Finding or citing a passage does
  not prove that it supports a claim; check the saved passage.
- OpenStreetMap tags record what mappers entered and when. They cannot establish
  current opening hours, menus, prices, quality or a "best" ranking.
- Near me covers only collections with coordinates, currently Vegan places.
- The place-only routing speed-up was measured on desktop (1.5 s to 0.3 s for a
  cold Berlin lookup across four collections); the phone measurement is pending.
- Not yet tested on a 12 GB GrapheneOS phone. No new bounty accuracy score is claimed.

## Installation

Download `fieldatlas.apk` and check `fieldatlas.apk.sha256`. Requires Android 13+
and an arm64 device. Model and knowledge packs are installed separately; queries
run locally after setup. Downloads and catalog refresh require a connection.

App version: 1.3.0 (15). GitHub channel: prerelease (`v1.3.0-rc.1`).

APK SHA-256: added when the signed APK is built.

## Release assets

- App release `v1.3.0-rc.1`: `fieldatlas.apk`, `fieldatlas.apk.sha256`.
- Knowledge release `knowledge-vegan-places-2026.10.02`:
  `osm-vegan-places-2026.10.02.fapack` (SHA-256
  `6cc944185c18de577060833645ee91a4da511c2c32df77ffc3b4dbfaf2dd8f7c`),
  `OVERPASS-LOCK.json`, `osm-vegan-overpass-cache-2026.10.02.tar.gz` (the raw
  Overpass responses, so anyone can rebuild the pack byte for byte) and `SHA256SUMS`.
- Knowledge release `knowledge-simplewiki-2025.12.29`: `simplewiki-2025.12.29.fapack`
  (SHA-256 `add1984a60ff3cfd2298668e7e40b061a8581aeb24023edf3bf5370eef39a82a`),
  `SOURCE.json`, the build spool `simplewiki-20251229.spool.jsonl.gz` with its
  `.meta.json` (Wikimedia removes dumps after a few weeks; the spool rebuilds the pack byte
  for byte) and `SHA256SUMS`.
- Knowledge release `knowledge-wikivoyage-guides-2025.12.29`:
  `wikivoyage-guides-2025.12.29.fapack` (SHA-256
  `2684b5b664aedb15aafb905a2dffccdd059dc589d10a382fdddec17c8bef60b2`), `SOURCE.json`, the
  spool `enwikivoyage-20251229.spool.jsonl.gz` with its `.meta.json` and `SHA256SUMS`.
