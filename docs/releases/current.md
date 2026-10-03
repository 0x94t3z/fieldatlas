# Field Atlas 1.2.1

A public release that fixes problems found after 1.2.0 on a Xiaomi phone and in an offline
emulator test. It is not a claim that answer quality or bounty acceptance criteria have been
met; the known limitations below still apply. Notes for the previous version:
[1.2.0](field-atlas-1.2.0.md).

## Changes

- **Attaching a file no longer closes the app on Xiaomi phones.** With the model loaded, opening
  the file, photo or camera picker put Field Atlas in the background holding about 2.4 GB, and on
  a Redmi 13C the system's memory service closed it within about 20 seconds, every time; the file
  was lost. The app now keeps its foreground notification while a picker is open, as it already
  did during research.
- **Answers about attached files are checked against the files.**
  - A citation that points at the wrong file is moved to the one file the claim's specific
    details (a labelled revision, a part code, a number) come from. A correct citation is never
    moved.
  - A question for something the files never mention ("the Wi-Fi password at Cedar Lodge") gets
    a one-line reply saying so, instead of unrelated details from the file.
  - A 12-hour time written beside the 24-hour time a file states is corrected when the two
    disagree ("10:30 PM (21:30)" becomes "9:30 PM (21:30)").
  - The instructions now put exceptions and holds ahead of general rules, keep numbers and
    negations as written, and ask for converted times beside the original. A source number
    written as plain text ("from S1") becomes a tappable citation.
- **Essentials.** A new downloadable collection (1.78 GB) of 2,981,021 pharmacies, hospitals,
  clinics, police stations, embassies, ATMs, currency exchanges, train, bus and ferry stations,
  toilets, drinking water, supermarkets and hostels that OpenStreetMap maps worldwide, for
  questions such as "nearest pharmacy in Jakarta" or "ATM near me". Map data © OpenStreetMap
  contributors (ODbL).

## Known limitations

- The 2B model still makes reasoning slips the checks cannot catch: an opening sentence that
  contradicts its own conclusion, wrong time arithmetic, or one revision's values attributed to
  another. On the Redmi 13C the reported attachment questions gave one clear pass, one pass with
  a wrong aside, and two answers with the right conclusion but a contradictory sentence; see
  [evaluation](../evaluation.md#attachment-reasoning-feedback-october-3).
- Hostels are in Essentials but are found only by ordinary keyword search, not by near-me lookups.
- Tags in the place collections record what mappers entered and when; they cannot confirm
  today's opening hours, prices or availability.
- Not yet tested on a 12 GB GrapheneOS phone. No new bounty accuracy score is claimed.

## Installation

Download `fieldatlas.apk` and check `fieldatlas.apk.sha256`. Requires Android 13+ and an arm64
device. It installs over 1.2.0 with data kept. Model and knowledge packs are installed
separately; queries run locally after setup. Downloads and catalog refresh require a connection.

App version: 1.2.1 (16). GitHub channel: release (`v1.2.1`).

APK SHA-256: added when the signed APK is built.

## Release assets

- App release `v1.2.1`: `fieldatlas.apk`, `fieldatlas.apk.sha256`.
- Knowledge release `knowledge-essentials-2026.10.02`: `osm-essentials-2026.10.02.fapack`
  (SHA-256 `e04fb8b89f627ef1cd8d0fb37f961d5d6b51acc7bc64d933d2b932db76bd6d62`),
  `OVERPASS-LOCK.json`, `osm-essentials-overpass-cache-2026.10.02.tar.gz` (the raw Overpass
  responses, so the pack rebuilds byte for byte) and `SHA256SUMS`.
- The other collections are unchanged from 1.2.0 and stay on their existing knowledge releases.
