# Field Atlas 1.3.0 — prerelease (draft, not yet published)

This is an early-access research build, not a claim that answer quality or bounty
acceptance criteria have been met. When it is published, this file replaces
`current.md`.

## Changes

- **Vegan places.** A new downloadable collection (40 MB) of 52,064 restaurants,
  cafés and shops that OpenStreetMap tags as fully vegan or with vegan options,
  worldwide. City questions list fully vegan places first, with the address and
  each listing's check date. Map data © OpenStreetMap contributors (ODbL).
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
  switches on the app palette; Source Sans 3 for reading text; a green launch screen.

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
