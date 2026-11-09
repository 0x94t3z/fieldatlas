"""Corpus checks for the Prealgebra 2e collection (issue #26).

The corpus at tools/packs/prealgebra/corpus.jsonl is the pinned input of
tools/packs/build_prealgebra_pack.py: one passage per numbered section of
OpenStax *Prealgebra 2e*, extracted whole from
openstax/osbooks-prealgebra-bundle by
tools/packs/extract_openstax_prealgebra.py. These checks keep the committed
corpus identical to what was extracted and reviewed: if the corpus changes,
its SHA-256 changes, the build pin must change with it, and this test is
where that shows up first.
"""

import hashlib
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "tools" / "packs" / "prealgebra" / "corpus.jsonl"
CORPUS_SHA256 = "4ba6b16bd39c5b07eb2a631916026ae01a3e5cf74273825cdcf12f2d72bb3e86"
LICENSE_ID = "CC-BY-4.0"
SOURCE_PATTERN = re.compile(
    r"^https://openstax\.org/books/prealgebra-2e/pages/\d+-\d+-[a-z0-9]+(-[a-z0-9]+)*$")
INLINE_MATH = re.compile(r"\$[^$\n]+\$")
# Markup of the CNXML/MathML source must never survive into a passage; a
# bare "<" can (inequalities inside LaTeX), closing tags and named source
# elements cannot.
SOURCE_MARKUP = re.compile(
    r"</|<(para|section|example|exercise|problem|solution|figure|table|link|"
    r"term|note|list|item|media|image|caption|math|equation|glossary|"
    r"definition|meaning)\b")


class PrealgebraCorpusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = CORPUS.read_bytes()
        cls.rows = [json.loads(line) for line in cls.raw.decode("utf-8").splitlines()]

    def test_corpus_matches_the_build_pin(self):
        self.assertEqual(CORPUS_SHA256, hashlib.sha256(self.raw).hexdigest())

    def test_one_row_per_numbered_section(self):
        self.assertEqual(60, len(self.rows))

    def test_rows_have_exactly_the_pack_fields(self):
        for row in self.rows:
            self.assertEqual({"document_id", "title", "source", "license", "text"}, set(row))
            for value in row.values():
                self.assertIsInstance(value, str)
                self.assertTrue(value.strip())

    def test_document_ids_are_unique(self):
        ids = [row["document_id"] for row in self.rows]
        self.assertEqual(len(ids), len(set(ids)))
        for document_id in ids:
            self.assertTrue(document_id.startswith("openstax-prealgebra-"), document_id)

    def test_every_row_carries_the_collection_licence(self):
        self.assertEqual({LICENSE_ID}, {row["license"] for row in self.rows})

    def test_source_links_point_at_the_book_sections(self):
        for row in self.rows:
            self.assertRegex(row["source"], SOURCE_PATTERN)

    def test_text_is_plain_with_latex_formulas(self):
        for row in self.rows:
            self.assertRegex(row["text"], INLINE_MATH, row["document_id"])
            self.assertIsNone(SOURCE_MARKUP.search(row["text"]), row["document_id"])


if __name__ == "__main__":
    unittest.main()
