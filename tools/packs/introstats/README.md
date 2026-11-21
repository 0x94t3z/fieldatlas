# Mathematics: Introductory Statistics

The source of the `openstax-maths-introstats` collection: OpenStax
**Introductory Statistics 2e** as an offline Field Atlas `.fapack`, built the
same way as the *Mathematics: Prealgebra* collection.

## What is in it

- **82 documents**: the 80 numbered sections of the 13 chapters, plus
  **Appendix F** (Mathematical Phrases, Symbols, and Formulas) and
  **Appendix H** (Tables), which hold the book's formula and statistical
  tables.
- Sections are kept whole, in OpenStax's own words: worked examples, try-it
  exercises, chapter review, formula review, practice, homework and the
  book's solutions, each followed by its glossary (38 sections carry one).
- Formulas are LaTeX converted from the book's MathML; tables are one row per
  line; images are left out, with their captions and descriptions kept.
- 1,522 search passages, indexed with the FTS5 `porter unicode61` tokenizer.
- Credits and licence are in `ATTRIBUTION.txt`, inside the pack and beside it.

## Build

```sh
git clone https://github.com/openstax/osbooks-introductory-statistics-bundle
git -C osbooks-introductory-statistics-bundle checkout 50ec91749080bb040b57bb40c34337cacc994cba
python3 tools/packs/extract_openstax_introstats.py --osbooks osbooks-introductory-statistics-bundle
python3 tools/packs/build_introstats_pack.py
python3 -m unittest tools.tests.test_introstats_corpus
```

`corpus.jsonl` is committed and pinned by SHA-256
`69544fba5cced43f4c52ba1e42f4b6e6a8ebd818a76dcf05bf9558f2702848ae`,
so the build does not need the OpenStax checkout. The extraction is
deterministic: the pinned checkout gives these bytes, and the build is
byte-for-byte reproducible.

Pack `0.1.0` is 3,427,170 bytes and installs to 3,425,871 bytes;
SHA-256 `aa32ee2675df1f3c657baf61945a13da91f065140182ce73a59834e99c1c7189`
(SQLite 3.53.4; another SQLite version writes the same passages into
different bytes). A public copy of the built file is kept beside the source
dump for convenience:
<https://github.com/ccheckx402/fieldatlas-introductory-statistics/releases/tag/v0.1.0>.
The app's download catalog is not changed by this collection; hosting is the
maintainer's decision.

## Sample questions

`introstats-questions.txt` holds ten questions written before the retrieval
run. `introstats-retrieval.json` records the top five documents returned by
the pack's own FTS5 index for each, and `introstats-answers.jsonl` and
`ANSWERS-SUMMARY.md` give the answer with the section that teaches it. The
teaching section ranks first for all ten questions. Reproduce the ranking
with:

```sh
python3 tools/packs/introstats/retrieval_check.py build/introstats-pack/openstax-maths-introstats-0.1.0.fapack
```

## Source and licence

*Introductory Statistics 2e*, by Barbara Illowsky and Susan Dean (senior
contributing authors) with contributing authors, published by OpenStax, Rice
University: <https://openstax.org/details/books/introductory-statistics-2e>.

Licence: **Creative Commons Attribution 4.0 International (CC BY 4.0)**. See
`ATTRIBUTION.txt` for the full credit.

Source revision: `openstax/osbooks-introductory-statistics-bundle` at commit
`50ec91749080bb040b57bb40c34337cacc994cba` (17 March 2026), the last revision
published under CC BY 4.0 before the book moved to CC BY-NC-SA on 19 March
2026. Only that pinned revision is used, never a later one.

OpenStax does not endorse this collection or Field Atlas.
