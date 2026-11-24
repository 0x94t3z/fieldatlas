"""Corpus checks for the Introductory Statistics 2e collection.

The corpus at tools/packs/introductory_statistics/corpus.jsonl is the pinned input of
tools/packs/build_introductory_statistics_pack.py: one passage per module of OpenStax
*Introductory Statistics 2e* (chapter introductions, numbered sections with their
glossaries, and the two appendices of formula tables), extracted whole from
openstax/osbooks-introductory-statistics-bundle by
tools/packs/extract_openstax_introductory_statistics.py. If the corpus changes, its
SHA-256 changes, the build pin must change with it, and this test is where that shows
up first.
"""

import hashlib
import json
from pathlib import Path
import re
import unittest

from tools.packs import build_introductory_statistics_pack as builder
from tools.packs import extract_openstax_introductory_statistics as extractor


ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "tools" / "packs" / "introductory_statistics"
CORPUS = HERE / "corpus.jsonl"
CORPUS_SHA256 = "0e2585510ea0ae1bfac2ff389a16fd9b60d8018d7f4a89bdacd11ae2c9e75fc2"
QUESTIONS_SHA256 = "93249c8a12ab855a0a2c4f065c601b506135c4130a069f28a398593eb9c4c128"
SECTION_SOURCE = re.compile(
    r"^https://openstax\.org/books/introductory-statistics-2e/pages/"
    r"(\d+-\d+-[a-z0-9]+(-[a-z0-9]+)*|\d+-introduction|[a-h]-[a-z0-9]+(-[a-z0-9]+)*)$")
SOURCE_MARKUP = re.compile(
    r"</|<(para|section|example|exercise|problem|solution|figure|table|link|"
    r"term|note|list|item|media|image|caption|math|equation|glossary|"
    r"definition|meaning)\b")


class IntroductoryStatisticsCorpusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = CORPUS.read_bytes()
        cls.rows = [json.loads(line) for line in cls.raw.decode("utf-8").splitlines()]
        cls.by_title = {row["title"]: row for row in cls.rows}

    def test_corpus_matches_the_build_pin(self):
        self.assertEqual(CORPUS_SHA256, hashlib.sha256(self.raw).hexdigest())
        self.assertEqual(CORPUS_SHA256, builder.CORPUS_SHA256)

    def test_thirteen_introductions_eighty_numbered_sections_and_two_appendices(self):
        intros = [row for row in self.rows if row["title"].endswith("\u2014 Introduction")]
        appendices = [row for row in self.rows if row["title"].startswith("Appendix \u2014 ")]
        self.assertEqual(13, len(intros))
        self.assertEqual(2, len(appendices))
        self.assertEqual(95, len(self.rows))

    def test_rows_have_exactly_the_pack_fields(self):
        for row in self.rows:
            self.assertEqual({"document_id", "title", "source", "license", "text"}, set(row))
            for value in row.values():
                self.assertIsInstance(value, str)
                self.assertTrue(value.strip())

    def test_ids_licence_and_source_links(self):
        ids = [row["document_id"] for row in self.rows]
        self.assertEqual(len(ids), len(set(ids)))
        for row in self.rows:
            self.assertTrue(row["document_id"].startswith(extractor.ID_PREFIX), row["document_id"])
            self.assertEqual("CC-BY-4.0", row["license"])
            self.assertRegex(row["source"], SECTION_SOURCE)
        self.assertEqual("https://openstax.org/books/introductory-statistics-2e/pages/6-1-the-standard-normal-distribution",
                         self.by_title["The Normal Distribution \u2014 The Standard Normal Distribution"]["source"])
        self.assertEqual("https://openstax.org/books/introductory-statistics-2e/pages/1-5-data-collection-experiment",
                         self.by_title["Sampling and Data \u2014 Data Collection Experiment"]["source"])

    def test_text_is_plain_with_latex_formulas(self):
        text = "\n".join(row["text"] for row in self.rows)
        self.assertIsNone(SOURCE_MARKUP.search(text))
        for formula in ("$$z=\\frac{x\u2013\u03bc}{\u03c3}$$", "$b=r(\\frac{{s}_{y}}{{s}_{x}})$",
                        "$$\u03bc=\u2211(x\u2219P\\left( x \\right))$$"):
            self.assertIn(formula, text)

    def test_no_noise_characters_survive(self):
        text = "\n".join(row["text"] for row in self.rows)
        self.assertIsNone(re.search("[\u200b\ue000-\uf8ff]", text))
        self.assertNotIn("\\text{\u200b}", text)
        # Control: the fixes are needed, the raw conversion carries all three.
        self.assertEqual("\u2211x", extractor.clean("{\u2211}^{\\text{\u200b}}x"))
        self.assertEqual("\u03b1 > p-value", extractor.clean("\uf061 > p-value"))

    def test_sections_keep_examples_exercises_reviews_and_glossary(self):
        spread = self.by_title["Descriptive Statistics \u2014 Measures of the Spread of the Data"]["text"]
        for part in ("Example", "Try It", "Chapter Review", "Formula Review", "\n\nGlossary\n"):
            self.assertIn(part, spread)
        self.assertEqual(44, sum("\n\nGlossary\n" in row["text"] for row in self.rows))
        binomial = self.by_title["Discrete Random Variables \u2014 Binomial Distribution"]["text"]
        self.assertIn("There are three characteristics of a binomial experiment.", binomial)

    def test_formula_tables_are_kept(self):
        formulas = self.by_title["Appendix \u2014 Mathematical Phrases, Symbols, and Formulas"]["text"]
        self.assertIn("X is at least 4. | X \u2265 4", formulas)
        self.assertIn("Formula 3: Binomial Distribution", formulas)

    def test_questions_file_matches_its_hash(self):
        self.assertEqual(QUESTIONS_SHA256, hashlib.sha256((HERE / "statistics-questions.txt").read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
