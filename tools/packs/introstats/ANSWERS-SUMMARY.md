# Answers run for the Introductory Statistics collection

This run was made on the first build, 0.1.0 (`openstax-maths-introstats`).

The five test questions are in `introstats-questions.txt` in this
directory, written before the run. Full per-question detail is in
`introstats-answers.jsonl`: the question, the answer worked from the
collection, the expected answer, the passages retrieved for the question,
and the citation document ids. The run itself is `answers_run.py`; it
reads the built pack's `content.sqlite` — the same FTS5 index the app
searches.

## Scores

- Answers run: **5/5 correct**, each worked from the formula stated in
  the cited section; for each of the five questions the teaching
  section is the #1 passage the pack's own search retrieves.
- Retrieval check (separate, in `introstats-retrieval.json`): 12
  ordinary statistics questions, key terms run as an FTS5 MATCH ranked
  by bm25 — a chunk from the section that teaches the concept is in the
  top three for **11 of 12**.

## The five questions

1. **Relative frequency** — 45 of 200 students take the bus:
   45 / 200 = 0.225. Cited: §1.3 Frequency, Frequency Tables, and
   Levels of Measurement (`openstax-introstats-m46882`).
2. **Binomial mean and standard deviation** — X ~ B(20, 0.3):
   μ = np = 6, σ = √(npq) = √4.2 ≈ 2.049. The section states the
   formulas in so many words: "μ = np and σ2 = npq … σ = $\sqrt{npq}$".
   Cited: §4.3 Binomial Distribution (`openstax-introstats-m46950`),
   §4.2 (`openstax-introstats-m46959`).
3. **Empirical Rule** — μ = 170, σ = 10: 150 = μ − 2σ and
   190 = μ + 2σ, so about 95%. Cited: §6.1 The Standard Normal Distribution
   (`openstax-introstats-m46980`).
4. **Confidence interval for a mean, σ known** — n = 36, x̄ = 82,
   σ = 12: EBM = 1.96 × 12/√36 = 3.92, interval (78.08, 85.92).
   Cited: §8.1 (`openstax-introstats-m47002`).
5. **Chi-square test of independence** — 2×2 table (rows 50/50,
   columns 40/60): expected frequencies 20, 30, 20, 30 by
   E = (row total)(column total)/(total surveyed); χ² = 16.67,
   df = 1. Cited: §11.3 Test of Independence
   (`openstax-introstats-m47077`).

## Method

Order of work, kept deliberately:

1. **Compute the expected answers independently first** (exact
   arithmetic: 45/200, np and √(npq), 1.96 × 12/6, the four
   (O−E)²/E terms), before any retrieval.
2. **Retrieve from the built pack** for each question and work the
   answer from the retrieved section's stated formula, citing the
   document id.
3. **Compare.** All five worked answers match the expected answers.

## Honest notes

- The one retrieval miss: for "regression equation", the top three
  chunks come from §12.6 Outliers, Appendix F and §12.1 Linear
  Equations; the first chunk of §12.3 The Regression Equation ranks
  fourth (it is the second distinct document). Appendix F ranking at
  all is legitimate — it carries the regression formulas in its
  formula tables.
- Two more near-misses worth recording: for the chi-square question,
  §11.3's chunk is third (behind §11.5 and §11.1), and for the central
  limit theorem, §7.1's chunk is third (behind Appendix F and §7.3).
  Both still pass the top-three check.
- The relative-frequency formula is stated in §1.3's frequency-table
  section and again in §2.2 Histograms; both rank in the top three
  for the relative-frequency questions.
