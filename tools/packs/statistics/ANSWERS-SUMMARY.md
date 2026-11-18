# Answers summary — 30 statistics questions, answered from the pack only

Questions: `build/statistics/questions.txt` (SHA-256
`3262eab72ee030c1e47da8611d6a06171e2c03d9b7a7214832f2f7ffed67302e`),
written before the build. Answers: `build/statistics/answers.jsonl`
(SHA-256
`f67ae74c58cc46bac6d502c015402d1f0c563327504612703bc95ff0f2ac40d9`),
each with `document_id`, section title, source URL and a verbatim quote
from the pack. Search: FTS5 MATCH + leviathan 0.2.0 CLI on the built
leviathan release; raw hits in `build/statistics/qa-hits.txt`.

## Result: 30 / 30 answered with a citation to the right section

| # | Question (short) | Answer | Section |
|---|---|---|---|
| 1 | Population vs sample | Population = all of interest; sample = subset studied | 1.1 Definitions |
| 2 | Level of measurement for hair color | Nominal (colors are nominal data) | 1.3 Frequency, Levels of Measurement |
| 3 | Explanatory vs response variable | Explanatory is manipulated; response is measured | 1.4 Experimental Design and Ethics |
| 4 | Median of 2, 4, 6, 8, 10 | 6 (computed from the (n+1)/2 rule) | 2.5 Measures of the Center |
| 5 | Five values of a box plot | Min, Q1, median, Q3, max | 2.4 Box Plots |
| 6 | Right-skewed: mean vs median | Mean greater than median | 2.6 Skewness and the Mean, Median, and Mode |
| 7 | P(A AND B), independent, 0.3 and 0.4 | 0.12 (computed: 0.3 × 0.4) | 3.2 Independent and Mutually Exclusive Events |
| 8 | Addition rule | P(A OR B) = P(A) + P(B) − P(A AND B) | 3.3 Two Basic Rules of Probability |
| 9 | What is a contingency table for | Portraying data to calculate probabilities / conditional probabilities | 3.4 Contingency Tables |
| 10 | Sum of a discrete PDF | 1 | 4.1 PDF for a Discrete Random Variable |
| 11 | Binomial mean, n=10, p=0.5 | 5 (μ = np) | 4.3 Binomial Distribution |
| 12 | Poisson mean | The parameter itself, μ (or λ) | 4.6 Poisson Distribution |
| 13 | Uniform PDF on [a, b] | f(x) = 1/(b−a) | 5.2 The Uniform Distribution |
| 14 | Exponential mean, decay m | 1/m | 5.3 The Exponential Distribution |
| 15 | z-score formula | z = (x − μ)/σ | 6.1 The Standard Normal Distribution |
| 16 | Area between z = −1 and z = 1 | About 68% | 6.1 The Standard Normal Distribution |
| 17 | z for the 90th percentile | ≈ 1.28 (derived — see note) | 6.2 Using the Normal Distribution |
| 18 | Central Limit Theorem | For large n, sample means are ~normal: x̄ ~ N(μx, σx/√n) | 7.1 CLT for Sample Means |
| 19 | Standard error of the mean | σ/√n | 7.1 CLT for Sample Means |
| 20 | Error bound, mean, σ known | EBM = (z_{α/2})(σ/√n) | 8.1 A Single Population Mean (Normal) |
| 21 | z for a 95% confidence interval | 1.96 | 8.1 A Single Population Mean (Normal) |
| 22 | Sample size up → CI width | Narrower | 8.1 A Single Population Mean (Normal) |
| 23 | Type I error | Rejecting a true null hypothesis | 9.2 Outcomes and the Type I and Type II Errors |
| 24 | Null hypothesis | Statement of no difference (status quo) | 9.1 Null and Alternative Hypotheses |
| 25 | Common significance level; p < α | α = 0.05; reject the null | 12.4 Testing the Significance of r |
| 26 | Two independent means, σ unknown | Aspin-Welch t-test | 10.1 Two Population Means, Unknown SDs |
| 27 | Matched-pairs test | Paired data; the differences form the sample | 10.4 Matched or Paired Samples |
| 28 | Chi-square goodness-of-fit | Assess whether data fit a specific distribution | 11.2 Goodness-of-Fit Test |
| 29 | Chi-square independence df | (r − 1)(c − 1) | 11.3 Test of Independence |
| 30 | Least-squares line; what r measures | y = a + bx; strength and direction of the linear association | 12.3 The Regression Equation |

## Notes on three answers

- **Q2:** the exact phrase "hair color" is not in the book; the section
  says "Categories, colors, names, labels and favorite foods ... are
  examples of nominal level data", and hair color is a color/category, so
  the answer follows from the book's own rule.
- **Q4 and Q7** are computations from the pack's rules, not quotes: Q4
  applies the (n+1)/2 median-position rule from 2.5; Q7 applies the
  multiplication rule P(A AND B) = P(A)P(B) from 3.2.
- **Q17 (derived, not printed):** the value 1.28 as a z-score is not printed
  in any included section — it is read off the standard-normal table, which
  is an appendix the Prealgebra template excludes. It is computed here from
  the pack's own numbers: the 6.2 worked example gives the 90th percentile
  of N(63, 5) as k = 69.4 (invNorm(0.90, 63, 5)), and the 6.1 z-score
  formula gives z = (69.4 − 63)/5 = 1.28. If the reviewer wants the table
  itself in the pack, Appendix G (Tables) can be added with a one-line
  change to the extractor.
