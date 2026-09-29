# Field Atlas 1.2.0 — prerelease

This is an early-access research build, not a claim that answer quality or bounty
acceptance criteria have been met. The previous stable release remains available.

## Changes

- Offline camera/photo/file text extraction and attachment research.
- Refined setup, Library, More, research progress, answer, and source screens.
- Explicit knowledge-catalog refresh and independent pack updates.
- Improved passage relevance, source-only evidence-gap responses, and separation
  of unverified model explanations from supported quotations.
- Citation normalization for parenthesized source references and grouped numbers.
- Native inference stability fixes and one maintained benchmark question file.

## Known limitations

- Model-only answers can contain factual errors and ignore length instructions.
  The Japan-history desktop check still failed manual quality review.
- Current downloadable collections are Travel Places and Biology & Longevity,
  not a general encyclopedia. Travel listings can be old or incomplete.
- Retrieval relevance is heuristic. Finding or citing a passage does not prove
  that it supports a claim. Check the actual saved passage.
- Desktop checks use keyword retrieval and do not establish Android/vector-search
  parity, peak device resource use, or multilingual answer quality.
- Model/Biology download recovery and the full connected UI test suite have not
  been independently verified for this release. No new bounty accuracy score is claimed.

## Installation

Download `fieldatlas.apk` and check `fieldatlas.apk.sha256`. Requires Android 13+
and an arm64 device. Model and knowledge packs are installed separately; queries
run locally after setup. Downloads and catalog refresh require a connection.

The public APK uses the original project release key, matching previous public
releases. It cannot update builds signed with the private local-test key. Do not
uninstall a test build without first preserving its local data.

App version: 1.2.0 (14). GitHub channel: prerelease (`v1.2.0-rc.1`).

APK SHA-256: `6d688a3632aa77650996aec3ae7ff4e887b7d772d9f2995ea0d8e67b71837b7f`.

Verification: JVM tests, Python tooling tests, release build, APK packaging,
reflection retention, and 16 KB ZIP alignment checks. Android UI tests are
compiled, not executed. Detailed limitations are in `docs/evaluation.md` and
`docs/compliance/release-audit.md`.
