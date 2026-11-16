# Answers run for the Mathematics: Introductory statistics collection

The 12 test questions are in `statistics-questions.txt` in this directory (SHA-256
`93249c8a12ab855a0a2c4f065c601b506135c4130a069f28a398593eb9c4c128`, asserted by
`tools/tests/test_introductory_statistics_corpus.py`). They were written before the first build, one
per area of the book: the normal distribution, discrete distributions, hypothesis testing,
descriptive statistics, the central limit theorem, confidence intervals, probability, chi-square,
regression and ANOVA, and sampling.

`statistics-answers.jsonl` holds, for each question: the answer, the section of the book that
teaches it (title, document id and the openstax.org page), and the three passages the pack's
keyword index (FTS5, the app's keyword search) returns first for the question's content words,
run on `openstax-introductory-statistics` 0.1.0. Every answer was checked against the cited
section's own text.

## Result

- The cited section is among the first three passages for **10 of 12** questions; the same
  sections come first in the `0.1.0-leviathan` build's Leviathan index.
- Question 6 (central limit theorem): the first three are the formula appendix's CLT rows, the
  chapter introduction and "Using the Central Limit Theorem", which state the same theorem; the
  section "The Central Limit Theorem for Sample Means (Averages)" is fourth.
- Question 10 (slope of the least-squares line): the first passages are exercises from "Outliers";
  the formula $b=r(\frac{{s}_{y}}{{s}_{x}})$ is in "The Regression Equation", which keyword search
  ranks lower because the exercises repeat the question's words. The text is in the pack; the
  ranking is the limit, and it is reported as it came out.
