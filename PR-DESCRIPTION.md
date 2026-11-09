Build a Prealgebra collection from OpenStax

Closes #26

Base address: 0xfFDd2f83555c64d1CaA17f5646C08b80013ED38b

## What this adds

A new collection, `openstax-maths-stats`: the whole text of OpenStax
*Prealgebra 2e*, one passage per numbered book section — 60 passages
covering all 11 chapters (Whole Numbers; The Language of Algebra;
Integers; Fractions; Decimals; Percents; The Properties of Real Numbers;
Solving Linear Equations; Math Models and Geometry; Polynomials; Graphs).
Every passage is the section's own text from
`openstax/osbooks-prealgebra-bundle` (pinned at commit
`7fcdabf0364f3b308c0190557dfc961b579b374c`, 19 February 2026 — the
last revision on main before OpenStax moved the book to CC BY-NC-SA),
nothing rewritten or
summarised; formulas are kept as LaTeX converted deterministically from
the book's MathML (the CNXML carries no LaTeX alttext), and each passage
carries its `openstax.org` source link.

Files (first commit):

- `tools/packs/extract_openstax_prealgebra.py` — the extractor
  (CNXML → corpus rows, including the MathML→LaTeX converter).
- `tools/packs/prealgebra/corpus.jsonl` — the pinned corpus: 60 rows,
  1,429,444 bytes, SHA-256
  `4ba6b16bd39c5b07eb2a631916026ae01a3e5cf74273825cdcf12f2d72bb3e86`.
- `tools/packs/build_prealgebra_pack.py` — build wrapper around
  `tools/packs/build_modular_library.py`.
- `tools/packs/prealgebra/ATTRIBUTION.txt` — credits the book by title,
  edition, authors and link, with no OpenStax logo; the same file travels
  beside the built pack.
- `tools/tests/test_prealgebra_corpus.py` — corpus checks (row shape,
  pin, licence field, source links, LaTeX present, no markup leftovers).
- `DATASETS.md` — the collection's row: module
  `openstax-maths-stats-0001`, version 0.1.0, **3,232,356 bytes**,
  SHA-256 `c87484527d6d666296a874d947b10c7352aaddbbf9d2f40f36668222fbd6cf78`,
  60 documents.

Files (second commit — the sealed test questions and the answers run):

- `tools/packs/prealgebra/prealgebra-questions.txt` — the 30 test
  questions, byte-identical to the file whose SHA-256
  (`929c6063086d1897332cb3775f3b3d6f5e77f5ff0a54518662e453f83d003571`)
  was posted on #26 before the build started; the hash was re-verified
  in place in this repo.
- `tools/packs/prealgebra/prealgebra-answers.jsonl` — one row per
  question: expected answer, answer without the collection, answer with
  the collection, the retrieved source document ids, and both verdicts.
- `tools/packs/prealgebra/ANSWERS-SUMMARY.md` — scores, the exact
  retrieval method, and the retrieval misses, named.

The `.fapack` itself is not committed; the pull request adds what
rebuilds it, and the maintainers rebuild and host it.

## Rebuild

```sh
python3 tools/packs/build_prealgebra_pack.py   # writes build/prealgebra-pack/
```

Expected output: `openstax-maths-stats-0001-0.1.0.fapack`, 3,232,356
bytes, SHA-256
`c87484527d6d666296a874d947b10c7352aaddbbf9d2f40f36668222fbd6cf78`,
with `ATTRIBUTION.txt` copied beside it. To regenerate the corpus, run
`tools/packs/extract_openstax_prealgebra.py` against the pinned bundle
commit above; the output is deterministic (byte-identical on a rerun).
Corpus checks: `tools/tests/test_prealgebra_corpus.py`. With the
collection installed, the emergency questions in the routing tests still
open the same guides — this change adds a new pack and touches no
existing pack, index, or routing data.

## What was left out, and why

15 of the book's 75 modules are excluded, each by its class in the
book's own CNXML:

- The **Preface** (`m81241`) — front matter, not a book section.
- The **11 chapter Introduction pages** (`m81242`, `m81266`, `m81274`,
  `m81284`, `m81292`, `m81305`, `m81314`, `m81319`, `m81331`, `m81333`,
  `m81338`) — unnumbered chapter openers (a photo caption and a
  paragraph or two), not numbered sections; several duplicate the
  title/content of the chapter's first real section. Excluding them is
  the strict reading of "every passage is one whole book section"; they
  can be added in a follow-up if you want them in.
- The **3 appendix modules** (`m56252` Cumulative Review — review
  exercises, `m56244` Powers and Roots Tables, `m56248` Geometric
  Formulas — reference tables) — back matter, not teaching sections.

What remains is exactly the 60 numbered sections.

## Answers run (issue #26, point 4)

The 30 questions were answered twice: once from knowledge alone, with
all 30 answers written before any retrieval was run, and once after
querying the built pack's own keyword index (`chunks_fts` in the pack's
`content.sqlite`, FTS5, bm25-ranked) and reading the retrieved passages.

- Without the collection: **30/30 correct**
- With the collection: **30/30 correct**

Stated plainly: the answering agent is strong at prealgebra, so the
memory run made no slips — the with-collection run's value here is
grounding, not a score jump. Retrieval put the section that teaches each
question at #1 for 23 of 30 questions and inside the top 3 for 29 of 30;
several passages are near-direct hits (the LCM passage works 12 and 18
itself; the percent passage's worked example is "What number is 45% of
80?"). The one clear miss is named in `ANSWERS-SUMMARY.md`: for
-8 + 5 - (-3), the primary query surfaced Add Integers but not Subtract
Integers (`openstax-prealgebra-m81277`), which a supplementary
subtraction query then ranked #1; both documents are cited for that
answer and the extra step is disclosed in the summary.

This answers run was done against the pinned pack (SHA-256
`c87484527d6d666296a874d947b10c7352aaddbbf9d2f40f36668222fbd6cf78`).
The pinned-revision rebuild changed the text of sections cited by
questions 12, 18, 25, 26, 29 and 30; each was re-checked against the
pinned text and no expected answer changed (the changes are typos,
placeholder figure alt text, and one display-math marker in unrelated
lines). The re-run reproduced the first run exactly: same scores, same
cited sources, same retrieval ranking pattern — see
`ANSWERS-SUMMARY.md` for the per-question comparison.

## Licence — resolved on #26: ship under CC BY 4.0, from a pinned revision

The maintainer confirmed on #26 (9 November 2026): ship it under
**CC BY 4.0, from a pinned revision**. OpenStax moved Prealgebra 2e to
CC BY-NC-SA on 12 March 2026, but a CC licence cannot be taken back, so
the text published before that change stays CC BY 4.0. The last revision
on main before the change is
`7fcdabf0364f3b308c0190557dfc961b579b374c` (19 February 2026): its LICENSE,
the collection's licence field and the Preface all say CC BY 4.0, and
that is verified in this pull request (see `ATTRIBUTION.txt`).

only the pinned revision is used, not later revisions

Note for the reviewer: the pinned February text differs from the current
main text in 8 of the 60 sections — later edits fixed typos ("se" → "so",
a missing "n", "chose" → "choose"), filled in figure alt text that was
"..." in February, and corrected one worked number. Those later fixes are
deliberately NOT included: nothing from later revisions goes in.
