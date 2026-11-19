"""Corpus checks for the Introductory Statistics 2e collection.

The corpus at tools/packs/introstats/corpus.jsonl is the pinned input of
tools/packs/build_introstats_pack.py: one passage per numbered section of
OpenStax *Introductory Statistics 2e*, plus Appendix F and Appendix H, in
the book's own words, extracted whole from
openstax/osbooks-introductory-statistics-bundle at the last CC BY 4.0
revision by tools/packs/extract_openstax_introstats.py. These checks keep
the committed corpus identical to what was extracted and reviewed: if the
corpus changes, its SHA-256 changes, the build pin must change with it, and
this test is where that shows up first.
"""

import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest
import zipfile

from tools.packs import build_introstats_pack as builder


ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "tools" / "packs" / "introstats" / "corpus.jsonl"
CORPUS_SHA256 = "69544fba5cced43f4c52ba1e42f4b6e6a8ebd818a76dcf05bf9558f2702848ae"
LICENSE_ID = "CC-BY-4.0"
SOURCE_PATTERN = re.compile(
    r"^https://openstax\.org/books/introductory-statistics-2e/pages/"
    r"(?:\d+-\d+-[a-z0-9]+(-[a-z0-9]+)*|[a-h]-[a-z0-9]+(-[a-z0-9]+)*)$")
INLINE_MATH = re.compile(r"\$[^$\n]+\$")
# Markup of the CNXML/MathML source must never survive into a passage; a
# bare "<" can (inequalities inside LaTeX), closing tags and named source
# elements cannot.
SOURCE_MARKUP = re.compile(
    r"</|<(para|section|example|exercise|problem|solution|figure|table|link|"
    r"term|note|list|item|media|image|caption|math|equation|glossary|"
    r"definition|meaning)\b")


class IntrostatsCorpusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = CORPUS.read_bytes()
        cls.rows = [json.loads(line) for line in cls.raw.decode("utf-8").splitlines()]

    def test_corpus_matches_the_build_pin(self):
        self.assertEqual(CORPUS_SHA256, hashlib.sha256(self.raw).hexdigest())

    def test_one_row_per_numbered_section_plus_the_two_formula_appendices(self):
        self.assertEqual(82, len(self.rows))
        self.assertEqual(80, sum(1 for row in self.rows
                                 if re.search(r"/pages/\d+-\d+-", row["source"])))
        appendices = [row for row in self.rows if re.search(r"/pages/[fh]-", row["source"])]
        self.assertEqual(2, len(appendices))
        self.assertEqual({"Appendix F: Mathematical Phrases, Symbols, and Formulas",
                          "Appendix H: Tables"}, {row["title"] for row in appendices})

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
            self.assertTrue(document_id.startswith("openstax-introstats-"), document_id)

    def test_every_row_carries_the_collection_licence(self):
        self.assertEqual({LICENSE_ID}, {row["license"] for row in self.rows})

    def test_source_links_point_at_the_book_sections(self):
        for row in self.rows:
            self.assertRegex(row["source"], SOURCE_PATTERN)

    def test_text_is_plain_with_latex_formulas(self):
        for row in self.rows:
            self.assertIsNone(SOURCE_MARKUP.search(row["text"]), row["document_id"])

    def test_every_latex_command_is_one_the_converter_writes(self):
        # A command that ran into the next letter ("\\pir", "\\cdotx", "\\geb") is not in this set.
        known = {"frac", "text", "cdot", "div", "sqrt", "begin", "end", "times", "ne", "pi", "approx", "ge", "le",
                 "dots", "Delta", "pm", "infty", "theta", "left", "right", "circ", "overline"}
        found = {name for row in self.rows for name in re.findall(r"\\([A-Za-z]+)", row["text"])}
        self.assertEqual(set(), found - known)
        text = "\n".join(row["text"] for row in self.rows)
        for formula in ("$\\overline{x}=\\frac{1+1+1+2+2+3+4+4+4+4+4}{11}=2.7$",
                        "$z=\\frac{x-\\overline{x}}{s}$",
                        "$P(X=x)=(\\begin{matrix} n \\\\ x \\end{matrix}){p}^{x}{q}^{n-x}$"):
            self.assertIn(formula, text)

    def test_sections_carry_their_glossary(self):
        with_glossary = [row for row in self.rows if "\n\nGlossary\n" in row["text"]]
        self.assertEqual(38, len(with_glossary))
        sample = next(row["text"] for row in self.rows
                      if row["document_id"] == "openstax-introstats-m46928")
        self.assertIn("Glossary\n", sample)

    def test_labs_and_worked_examples_are_kept(self):
        text = "\n".join(row["text"] for row in self.rows)
        self.assertIn("Data Collection Experiment", text)
        self.assertIn("Lab 2: Chi-Square Test of Independence", text)
        self.assertIn("Try It", text)
        self.assertIn("Example", text)


class IntrostatsChunkTest(unittest.TestCase):
    def test_every_passage_starts_at_a_line_or_word_and_nothing_is_lost(self):
        from packtool.schema import Document
        rows = [json.loads(line) for line in CORPUS.read_text(encoding="utf-8").splitlines()]
        for row in rows[:6] + rows[-4:]:
            text = row["text"]
            chunks = builder.line_chunks(Document(**row))
            self.assertGreaterEqual(len(chunks), 1)
            position = 0
            for chunk in chunks:
                self.assertLessEqual(len(chunk.text), 1200)
                found = text.find(chunk.text, max(0, position - 1200))
                self.assertGreaterEqual(found, 0, chunk.chunk_id)
                self.assertTrue(found == 0 or text[found - 1] in "\n ", chunk.chunk_id)
                # No gap: this passage starts at or before the end of the text already covered.
                self.assertLessEqual(found, position + 2, chunk.chunk_id)
                position = max(position, found + len(chunk.text))
            self.assertEqual(len(text), position)


class IntrostatsPackTest(unittest.TestCase):
    def test_the_pack_carries_its_own_id_and_its_credits(self):
        with tempfile.TemporaryDirectory() as folder:
            report = builder.build(CORPUS, Path(folder) / "pack", "0.1.0")
            pack = Path(folder) / "pack" / report["pack"]
            with zipfile.ZipFile(pack) as archive:
                manifest = json.loads(archive.read("manifest.json"))
                credits = archive.read("ATTRIBUTION.txt")
            self.assertEqual(("openstax-maths-introstats", "0.1.0", "CC-BY-4.0"),
                             (manifest["id"], manifest["version"], manifest["license"]))
            self.assertEqual(credits, builder.ATTRIBUTION.read_bytes())
            listed = {a["path"]: a for a in manifest["artifacts"]}
            self.assertEqual(hashlib.sha256(credits).hexdigest(), listed["ATTRIBUTION.txt"]["sha256"])
            self.assertEqual(credits, (pack.parent / "ATTRIBUTION.txt").read_bytes())
            self.assertIn(b"Barbara Illowsky", credits)
            self.assertIn(b"CC BY 4.0", credits)
            self.assertEqual(report["installedBytes"], sum(a["bytes"] for a in manifest["artifacts"]))


if __name__ == "__main__":
    unittest.main()
