# Field Atlas native search bridge

Leviathan 0.2.0 is vendored from upstream commit
`f1070fe525527eec8ed52bfaf014b9e927224889`, Apache-2.0. Its LICENSE,
NOTICE and THIRD_PARTY.md remain in `third_party/leviathan`.
The sole upstream code change exposes SQLite's thread-safe interrupt handle.
Query parsing, ranking and the index format are unchanged.

This read-only JNI library uses upstream's library API with default features
disabled. It does not launch a CLI, enable network code, or offer index writes.
Integer handles refer to reference-counted stores; close cannot free an in-flight
query. JVM entry points contain Rust panics and convert errors to exceptions.

Build prerequisites: Rust 1.90 with `aarch64-linux-android`, Python 3, NDK
29.0.13113456. Put cargo/rustc/rustdoc on PATH. Gradle builds the library from
source using Cargo.lock. No binary is checked in. ARM64 linker pages use 16KiB.

Only databases with the validated `fieldatlas_search` marker use native search.
Old packs keep their original SQL path. A native failure falls back to the same
pack's legacy posting index. Every knowledge pack can be built for it with
`tools/packs/index_pack_for_leviathan.py`. A place pack's index carries a
`places[]` filter (each listing's destination and its parent destinations,
lowercase without accents), so a question naming a city searches only that
city's listings; a pack without the filter keeps its keyword search for those.
Near-me lookups still read `place_points`. Returned cards select IDs; original
full records supply evidence. Each record comes back with Leviathan's own BM25
relevance, which the app uses only to weigh one pack's matches against
another's, as it does keyword scores.

`tools/packs/build_dual_search_pack.py` creates a transitional single-content database:
one original record copy and two posting indexes. It carries the rowid-aligned
`place_points` and `chunk_vectors` tables unchanged and rejects any other
auxiliary table rather than dropping it. It does not create or publish
an installable `.fapack`; packaging, checksum updates and measured storage remain
separate release steps. Do not modify an installed pack in place.

Large-pack provisioning

`tools/packs/package_search_upgrade.py --compressed` produces a schema-2 knowledge
pack with a STORED first manifest and STORED/DEFLATED artifacts. Schema 1 remains
STORED-only; schema 2 is rejected for model/audio packs. Expanded artifact byte
counts and SHA-256 checks are unchanged. Download catalogs must specify
`installedBytes` for schema-2 archives; admission reserves remaining download
bytes plus expanded bytes and verifies that count against the manifest.

Seekable local document URIs are read directly rather than copied into staging.
The source archive still counts conservatively against the 50 GB import budget,
even when it lives outside app storage, and is never deleted by the importer.
Pipe/cloud providers retain the spool-and-verify fallback and may require more
space. Copy/extraction loops check coroutine cancellation between reads, remove
their staging session on failure, and preserve downloaded archives for retry.
This is format support, not evidence of completed full-size device acceptance.

Android timing

`tools/android/run_leviathan_timing.py` times both indexes of each pack on a phone with
`app_process`, from a scratch folder it deletes afterwards; the installed app and its data are not
touched. Redmi 13C (23108RN04Y, 6 GB), 14 October 2026, 20 results per search, milliseconds:

| Pack | Passages | First search | Later searches (median) | Keyword index (median) | One destination (median) |
|---|---:|---:|---:|---:|---:|
| Biology & longevity | 9,260 | 132 | 70 | 147 | |
| Cryptography & Ethereum | 22,805 | 142 | 102 | 663 | |
| Vegan places | 52,064 | 364 | 309 | 804 | 60 |
| Travel guides | 363,815 | 500 | 185 | 1,406 | |
| Travel places | 405,849 | 307 | 257 | 419 | 21 |
| Essentials | 2,981,023 | 1,491 | 979 | 3,187 | 219 |

Leviathan was faster than the keyword index on every pack. A search within one destination, as the
app runs for a named city, stays under a quarter of a second even in Essentials. The slowest
whole-pack search was 3.2 s (Essentials, "drinking water toilet"). The Encyclopedia was not timed
this way: its 24 GB database does not fit beside the installed copy.
