# Answers run for the Prealgebra collection (issue #26)

This run was made on the first build, 0.1.0. The hosted collection is a later build; `DATASETS.md`
lists what changed.

The 30 sealed test questions are in `prealgebra-questions.txt` in this
directory. Their SHA-256 is
`929c6063086d1897332cb3775f3b3d6f5e77f5ff0a54518662e453f83d003571`, the
hash posted on issue #26 before the build started. The questions file was
copied into this repository byte-identical and the hash was re-verified
in place after copying — and re-verified again after the pinned-revision
rebuild below.

Full per-question detail is in `prealgebra-answers.jsonl`: the question,
the expected answer, the answer given without the collection, the answer
given with the collection, the passages' document ids, and a correct flag
for each run.

## Scores

- Without the collection: **30/30 correct**
- With the collection: **30/30 correct**

Honest context for the identical scores: the answering agent is strong at
prealgebra arithmetic, so the from-memory run made no slips, and none have
been invented for the sake of a better story. All 30 expected answers were
produced by solving the problems independently first; the 26 mechanically
checkable ones were additionally re-verified with exact fraction/integer
arithmetic in a separate audit (all passed), and the other 4 are
definitional (the phrase-translation table, the absolute-value definition,
the commutative property stated verbatim in the book, and coefficient-wise
polynomial addition). What the collection run adds is not a higher score
on these questions but grounding: every with-collection answer is tied to
the retrieved section that teaches it, cited by document id in the JSONL.

## Method

Order of work, kept deliberately:

1. **Solve and answer from memory first.** All 30 expected answers and all
   30 without-collection answers were written to a separate working file
   before any retrieval query was run against the pack.
2. **Retrieve.** The built pack
   `openstax-maths-stats-0001-0.1.0.fapack` (3,232,356 bytes, SHA-256
   `c87484527d6d666296a874d947b10c7352aaddbbf9d2f40f36668222fbd6cf78`,
   built 9 November 2026 from the pinned corpus SHA-256
   `4ba6b16bd39c5b07eb2a631916026ae01a3e5cf74273825cdcf12f2d72bb3e86`)
   was unpacked and its `content.sqlite` queried directly. The pack's only
   index is the FTS5 virtual table

   ```sql
   CREATE VIRTUAL TABLE chunks_fts USING fts5(
     chunk_id UNINDEXED, document_id UNINDEXED, title,
     source UNINDEXED, text, tokenize='porter unicode61')
   ```

   (1,398 chunks over the 60 documents). Each question was queried with
   its key terms as an OR of words/phrases, ranked by the FTS5 bm25
   `rank`:

   ```sql
   SELECT chunk_id, document_id, title, rank
   FROM chunks_fts
   WHERE chunks_fts MATCH '<key terms>'
   ORDER BY rank
   LIMIT 40;
   ```

   The top 5 distinct `document_id`s per question were collected; the top
   chunks' `text` was then read from `chunks_fts` itself, and the
   with-collection answer was written from those passages. No grep over
   `corpus.jsonl` was used for retrieval. One question (Q7) also used a
   supplementary subtraction-focused query, disclosed below.

   Primary queries (question: MATCH string):

   - Q1 `round OR rounding OR nearest OR thousand`
   - Q2 `"prime factorization" OR prime OR factor OR tree`
   - Q3 `"least common multiple" OR multiple OR multiples`
   - Q4 `translate OR phrase OR expression OR "more than" OR twice`
   - Q5 `"order of operations" OR evaluate OR parentheses`
   - Q6 `"absolute value" OR absolute`
   - Q7 `add OR integers OR negative OR sum`
   - Q8 `multiply OR divide OR integers OR sign`
   - Q9 `temperature OR "below zero" OR integers`
   - Q10 `add OR fractions OR denominators`
   - Q11 `divide OR fractions OR reciprocal`
   - Q12 `"complex fraction" OR complex OR simplify`
   - Q13 `decimal OR fraction OR convert OR write`
   - Q14 `round OR rounding OR hundredth`
   - Q15 `multiply OR decimals OR decimal`
   - Q16 `percent OR proportion OR amount`
   - Q17 `percent OR "what percent" OR proportion`
   - Q18 `discount OR "sale price" OR markdown OR percent`
   - Q19 `commutative OR property OR addition`
   - Q20 `simplify OR distributive OR "like terms"`
   - Q21 `solve OR equation OR subtraction OR addition OR equality`
   - Q22 `solve OR equation OR division OR multiplication OR equality`
   - Q23 `solve OR equation OR "two steps" OR undo`
   - Q24 `solve OR equation OR fraction OR coefficients`
   - Q25 `perimeter OR rectangle`
   - Q26 `area OR triangle`
   - Q27 `add OR polynomials OR subtract`
   - Q28 `multiply OR monomials OR exponents`
   - Q29 `slope OR rise OR run`
   - Q30 `intercept OR intercepts OR graphing`

## Retrieval quality

The section that teaches each question ranked #1 for 23 of 30 questions,
and inside the top 3 for 29 of 30. A few questions were near-direct hits:
Q3's passage works the LCM of 12 and 18 itself; Q9's passage is a worked
temperature problem (-7 + 12) of exactly the asked shape; Q13's passages
pair 3/8 with 0.375 in both directions; Q16's top passage works
"What number is 45% of 80?" as its example; Q19's passage states
a + b = b + a as the named property.

Exceptions and near misses, honestly noted:

- **Q7 (-8 + 5 - (-3)) is the one clear retrieval miss.** The primary
  query surfaced Add Integers (`openstax-prealgebra-m81276`) at #1, which
  covers the -8 + 5 step, but Subtract Integers — the section that teaches
  subtracting a negative, document `openstax-prealgebra-m81277`, the
  document that should also have matched — did not appear in the primary
  query's top 5 at all. A supplementary query
  (`subtract OR subtraction OR "subtracting" OR opposite`) ranked it #1,
  and the with-collection answer cites both documents; that supplementary
  step is disclosed here rather than hidden.
- **Q4**: the translation section (`openstax-prealgebra-m81270`) ranked
  #3, behind Multiply Whole Numbers (its "twice" wording) — retrieved and
  used, but not the top hit.
- **Q12**: the complex-fractions section (`openstax-prealgebra-m81287`)
  ranked #2; the #1 hit (`openstax-prealgebra-m81289`) does discuss
  simplifying complex fractions, so both are cited.
- **Q21**: the top hit was Solve Equations with Fractions
  (`openstax-prealgebra-m81291`), off-topic for x + 9 = 23; the canonical
  section (`openstax-prealgebra-m81271`) ranked #2 and grounds the answer.
- **Q23 (3a - 5 = 16)**: not a retrieval failure — the book has no
  standalone two-step-equations section. Retrieval returned the two
  component sections at #1 and #2 (division/multiplication properties,
  `openstax-prealgebra-m81321`; subtraction/addition properties,
  `openstax-prealgebra-m81320`), and the method is one step from each.
- **Q27**: the canonical Add and Subtract Polynomials
  (`openstax-prealgebra-m81334`) ranked #2 behind the factoring chapter's
  exercise chunk, which itself contains worked polynomial additions.

## Re-run against the pinned pack (9 November 2026) — this run supersedes the first

The first answers run was executed against the pack built from the
main-revision corpus (pack SHA-256
`9052e96c867d1c384b10cd8d83d8cf3da26955e30a862b5e6b3b1d259b4ae749`).
Per the maintainer's instruction on #26 the corpus was rebuilt from the
pinned revision `7fcdabf0364f3b308c0190557dfc961b579b374c` (new corpus
SHA-256 `4ba6b16bd39c5b07eb2a631916026ae01a3e5cf74273825cdcf12f2d72bb3e86`,
new pack SHA-256
`c87484527d6d666296a874d947b10c7352aaddbbf9d2f40f36668222fbd6cf78`,
built 9 November 2026). That first run is superseded by this one; its
pack SHA-256 is recorded here for the record only.

The re-run used the identical primary queries above against the pinned
pack's `content.sqlite`, with the without-collection answers reused
verbatim from the first run (they are pack-independent — the memory
answers were written before any retrieval in either run). Every cited
source document was verified present in the new pack's top-5 retrieval
for its question (Q7's Subtract Integers via the supplementary query).

**Result: the re-run reproduced the first run exactly.** Scores are
30/30 without and 30/30 with the collection, the cited source set is
identical for all 30 questions, the teaching section still ranks #1 for
23 of 30 and inside the top 3 for 29 of 30, and the Q7 miss is
unchanged. The only ranking difference of any kind is Q24's top-5
order: Solve Equations with Fractions and Solve Equations with Fraction
Coefficients swap #1/#2; both are cited, as before.

### The six questions tied to changed sections

The pinned rebuild changed the text of 8 of the 60 sections; six
questions cite a changed section. Each was re-checked against the
pinned corpus, and no expected answer changed:

- **Q12** (complex fraction, `openstax-prealgebra-m81287`): the change
  is a typo in an unrelated worked example (a missing `n` in "the
  difference of $m$ and $n$"). The complex-fraction method — rewrite
  as a division problem — is intact. Answer still **2/3**; the section
  still ranks #2 behind `openstax-prealgebra-m81289`, as before.
- **Q18** (sale price, `openstax-prealgebra-m81307`): the change is a
  display-math marker in an unrelated sales-tax exercise answer
  ($84 boots at 5% tax, total $88.20). The discount worked example
  and the sale-price formula are intact. Answer still **$48**; the
  section still ranks #1.
- **Q25** (rectangle perimeter, `openstax-prealgebra-m81332`): the
  change is a missing space in a rug-area sentence ("is$6$"). Answer
  still **10 cm**; the section still ranks #1.
- **Q26** (triangle area, `openstax-prealgebra-m81332`): same one-word
  typo as Q25. The area formula $A=\frac{1}{2}bh$ is intact. Answer
  still **63 square inches**; the section still ranks #1.
- **Q29** (slope, `openstax-prealgebra-m81354`): the changes are
  placeholder figure alt text ("[Image: ...]") and two blanked exercise
  answer options. The slope formula $m=\frac{y_2-y_1}{x_2-x_1}$ and
  the rise/run teaching are intact. Answer still **2**; the section
  still ranks #1.
- **Q30** (intercepts, `openstax-prealgebra-m81350`): the change is
  placeholder alt text on one table image. The intercept method (let
  $y=0$ for the x-intercept, let $x=0$ for the y-intercept) is intact.
  Answer still **x-intercept 6, y-intercept 4**; the section still
  ranks #1.

The other two changed sections (`openstax-prealgebra-m81244`,
`openstax-prealgebra-m81345`, plus the non-question parts of the six
above) cite no question. The six expected answers above were
re-verified with exact fraction/integer arithmetic against the pinned
text.
