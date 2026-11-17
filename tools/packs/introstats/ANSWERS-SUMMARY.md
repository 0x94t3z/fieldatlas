# Answers run for the Introductory Statistics collection (POIDH Base #1554)

This run was made on the first build, 0.1.0. The 5 test questions are in
`introstats-questions.txt` in this directory. Full per-question detail is
in `introstats-answers.jsonl`: the question, the expected answer, the
answer given with the collection, the passages' document ids, and a
correct flag for each run. The retrieval hits behind the answers are in
`introstats-retrieval.json`, produced by `answers_run.py`.

## Scores

- With the collection: **5/5 correct**

## Method

Order of work, kept deliberately:

1. **Solve and answer from memory first.** All 5 expected answers were
   written (in `answers_run.py`) before any retrieval query was run
   against the pack. The arithmetic in Q2 (sqrt(3.75) = 1.9365) was
   computed independently.
2. **Retrieve.** The built pack
   `openstax-maths-introstats-0.1.0.fapack` (3,426,593 bytes, SHA-256
   `e13a9d0acc157b1e7e89c685ed8256e92c27c9d03eb8ca3a90b947174791e53f`,
   built from the pinned corpus SHA-256
   `bd073cdc920228a63f5d5c9b5ab31108136516b161ac1de5293275ed43f7f755`)
   was unpacked and its `content.sqlite` queried directly. The pack's only
   index is the FTS5 virtual table `chunks_fts` (porter tokenizer), so a
   query is an FTS5 MATCH over the chunk text; the top 5 hits were read
   and each answer was checked against the retrieved passage.
3. **Cite.** Every answer is tied to the retrieved section that teaches
   it, cited by document id and by the section's OpenStax source URL.

## Honest notes

- Q1: the relative-frequency formula `RF = f/n` is stated in the Chapter
  2 Histograms section (m46924), not in the Chapter 1 Frequency section
  (which uses relative frequency in tables without stating the formula).
  The first query (`"relative frequency"`) hit the Chapter 1 section;
  the query was refined to `"relative frequency" f n` to surface the
  section that states the formula.
- Q5: the teaching section (Test of Independence, m47077) ranks 4th in
  the FTS5 top hits for the query, behind the Facts and Comparison
  sections. The test statistic `(O-E)^2/E` and the null hypothesis are
  verified in the section text itself and cited from there.
- Q2: the pack gives the formulas (mu = np, sigma = sqrt(npq)); the
  substitution and arithmetic are the solver's own.
- No "without the collection" run was made: the bounty asks whether the
  pack answers questions with citations, not for a with/without
  comparison, and the answers are all grounded in retrieved passages.
