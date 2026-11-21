# Answers run for the Introductory Statistics collection

This run was made on build 0.1.0 of `openstax-maths-introstats`:

- pack `openstax-maths-introstats-0.1.0.fapack`
- bytes `3,427,170`
- SHA-256 `aa32ee2675df1f3c657baf61945a13da91f065140182ce73a59834e99c1c7189`
- corpus SHA-256 `69544fba5cced43f4c52ba1e42f4b6e6a8ebd818a76dcf05bf9558f2702848ae`
- 82 documents (80 numbered sections plus Appendices F and H), 1,522 passages
- `verify_pack` passes (manifest, artifact bytes and hashes, SQLite `quick_check`, FTS match)
- a second build produced the same SHA-256 (byte-for-byte reproducible)

The ten questions are in `introstats-questions.txt`. They were written before
the retrieval run. The answers and the document id cited for each are in
`introstats-answers.jsonl`; the full top-five document list per question is in
`introstats-retrieval.json` and is reproducible with
`python3 tools/packs/introstats/retrieval_check.py`.

## Method

The built pack was unzipped and its `content.sqlite` queried directly. The
pack's only index is the FTS5 virtual table

```sql
CREATE VIRTUAL TABLE chunks_fts USING fts5(
  chunk_id UNINDEXED, document_id UNINDEXED, title,
  source UNINDEXED, text, tokenize='porter unicode61')
```

Each question was queried with its key terms as an OR/AND expression, ranked
by the FTS5 bm25 `rank`:

```sql
SELECT document_id, title, source, bm25(chunks_fts)
FROM chunks_fts
WHERE chunks_fts MATCH '<key terms>'
ORDER BY bm25(chunks_fts) LIMIT 5;
```

The top passages were read from the pack itself; no grep over
`corpus.jsonl` was used for retrieval. The with-collection answers below
were written from those passages.

## Result

The section that teaches each question ranked **#1 for all 10 questions**,
and inside the top three for all 10.

| # | Question | Cited section | Rank |
|---|----------|---------------|------|
| 1 | Population vs. sample | Sampling and Data — Definitions of Statistics, Probability, and Key Terms | #1 |
| 2 | Finding the median | Descriptive Statistics — Measures of the Center of the Data | #1 |
| 3 | Mean of a binomial distribution | Discrete Random Variables — Binomial Distribution | #1 |
| 4 | 95% confidence interval, known σ | Confidence Intervals — A Single Population Mean using the Normal Distribution | #1 |
| 5 | Rejecting the null hypothesis | Hypothesis Testing with One Sample — Rare Events, the Sample, Decision and Conclusion | #1 |
| 6 | Central limit theorem for sample means | The Central Limit Theorem — The Central Limit Theorem for Sample Means (Averages) | #1 |
| 7 | Chi-square goodness-of-fit statistic | The Chi-Square Distribution — Goodness-of-Fit Test | #1 |
| 8 | The regression equation | Linear Regression and Correlation — The Regression Equation | #1 |
| 9 | What the F distribution is used for | F Distribution and One-Way ANOVA — The F Distribution and the F-Ratio | #1 |
| 10 | Type I vs. Type II error | Hypothesis Testing with One Sample — Outcomes and the Type I and Type II Errors | #1 |

## The answers

1. A **population** is the whole group being studied; a **sample** is a subset
   of it. A **parameter** describes the population, a **statistic** is computed
   from a sample.
2. Order the data smallest to largest, then locate position **(n+1)/2**. If n
   is odd the median is the middle value; if n is even it is the mean of the two
   middle values.
3. For X ~ B(n, p), the mean is **μ = np**; the standard deviation is
   **σ = sqrt(npq)**, with q = 1 − p.
4. With σ known the interval is **(x̄ − EBM, x̄ + EBM)** where
   **EBM = z(σ/√n)**; for 95% confidence **z = 1.96**.
5. **Reject H0 when the p-value is less than α**; do not reject when the
   p-value is greater than α.
6. For sufficiently large n the sample means are approximately normal with
   mean **μ** and standard error **σ/√n**.
7. **χ² = Σ (O − E)²/E**, with each expected value at least five.
8. The least-squares line is **ŷ = a + bx**, where b is the slope and a the
   y-intercept.
9. The F distribution is used in **ANOVA** to test whether three or more
   population means are equal; the test statistic is the **F-ratio** (variance
   between samples over variance within samples).
10. A **Type I error** rejects a true null hypothesis (probability α); a
    **Type II error** fails to reject a false null hypothesis (probability β).

Every answer above is grounded in the pack's own text for the cited section;
the exact formula in each answer (μ = np, EBM = zσ/√n, χ² = Σ(O−E)²/E,
ŷ = a + bx) appears in that section's passage.
