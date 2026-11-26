"""Corpus checks for the Introductory Statistics 2e collection.

The corpus at tools/packs/introstats/corpus.jsonl is the pinned input of
tools/packs/build_introstats_pack.py: one passage per numbered section of
OpenStax *Introductory Statistics 2e*, extracted whole from
openstax/osbooks-introductory-statistics-bundle by
tools/packs/extract_openstax_introstats.py, plus the two appendices that
hold the formula tables (Appendix F and Appendix H). These checks keep the
committed corpus identical to what was extracted and reviewed: if the
corpus changes, its SHA-256 changes, the build pin must change with it,
and this test is where that shows up first.
"""

import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

from tools.packs import build_introstats_pack as builder
from tools.packs import extract_openstax_introstats as extractor


ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "tools" / "packs" / "introstats" / "corpus.jsonl"
CORPUS_SHA256 = "bd073cdc920228a63f5d5c9b5ab31108136516b161ac1de5293275ed43f7f755"
LICENSE_ID = "CC-BY-4.0"
SOURCE_PATTERN = re.compile(
    r"^https://openstax\.org/books/introductory-statistics-2e/pages/"
    r"(\d+-\d+-[a-z0-9]+(-[a-z0-9]+)*|[fh]-[a-z0-9]+(-[a-z0-9]+)*)$")
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

    def test_one_row_per_numbered_section(self):
        self.assertEqual(82, len(self.rows))

    def test_the_two_formula_table_appendices_are_included(self):
        appendices = [row for row in self.rows if row["title"].startswith("Appendix")]
        self.assertEqual(["Appendix F \u2014 Mathematical Phrases, Symbols, and Formulas",
                          "Appendix H \u2014 Tables"],
                         [row["title"] for row in appendices])
        self.assertIn("Formulas", appendices[0]["text"])
        self.assertIn("NIST", appendices[1]["text"])

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
        # Most sections carry formulas as LaTeX; a few do not, and that is the
        # book, not the converter: the lab/activity sections (m502xx) and the
        # purely conceptual sections (Experimental Design and Ethics, Box
        # Plots, Skewness, Scatter Plots) state their content in words and
        # figures, and Appendix H is only links. The no-math set is pinned
        # so a converter regression that dropped formulas shows up here.
        no_math = [row["document_id"] for row in self.rows
                   if not INLINE_MATH.search(row["text"])]
        self.assertEqual(["openstax-introstats-m46919", "openstax-introstats-m50252",
                          "openstax-introstats-m46920", "openstax-introstats-m46931",
                          "openstax-introstats-m50256", "openstax-introstats-m50274",
                          "openstax-introstats-m50277", "openstax-introstats-m50278",
                          "openstax-introstats-m50280", "openstax-introstats-m47109",
                          "openstax-introstats-m50283", "openstax-introstats-m50285",
                          "openstax-introstats-m50287", "openstax-introstats-m47875"],
                         no_math)
        for row in self.rows:
            self.assertIsNone(SOURCE_MARKUP.search(row["text"]), row["document_id"])

    def test_every_latex_command_is_one_the_converter_writes(self):
        # A command that ran into the next letter ("\\pir", "\\cdotx", "\\geb") is not in this set.
        # Exactly the commands this book's MathML produces (checked against the corpus).
        known = {"frac", "text", "cdot", "div", "sqrt", "begin", "end", "times", "ne", "pi", "approx", "ge", "le",
                 "pm", "left", "right", "overline"}
        found = {name for row in self.rows for name in re.findall(r"\\([A-Za-z]+)", row["text"])}
        self.assertEqual(set(), found - known)
        text = "\n".join(row["text"] for row in self.rows)
        for formula in ("$$\\text{RF}=\\frac{f}{n}$$", "$\\frac{996}{2000}$", "$\\frac{22}{40}$"):
            self.assertIn(formula, text)

    def test_sections_carry_their_glossary(self):
        with_glossary = [row for row in self.rows if "\n\nGlossary\n" in row["text"]]
        self.assertEqual(38, len(with_glossary))
        definitions = next(row["text"] for row in self.rows if row["document_id"] == "openstax-introstats-m46909")
        self.assertIn("Average\nalso called mean; a number that describes the central tendency of the data",
                      definitions.split("\n\nGlossary\n", 1)[1])


    def test_part_labels_and_repeating_decimals_read_plainly(self):
        text = "\n".join(row["text"] for row in self.rows)
        self.assertIn("(a)", text)
        self.assertIsNone(re.search("[\u24d0-\u24e9\u00ba]", text))
        self.assertIn("$\\overline{x}$", text)
        # Unlike Prealgebra, this book writes negative exponents with the
        # en dash as the minus sign ("{e}^{\u2013\\frac{1}{8}}"), which
        # the converter keeps verbatim; it is not a repeating-decimal bar.
        self.assertIn("{e}^{\u2013\\frac{1}{8}}", text)


class IntrostatsConverterTest(unittest.TestCase):
    def latex(self, mathml: str) -> str:
        return extractor.mathml_to_latex(ET.fromstring(
            '<math xmlns="http://www.w3.org/1998/Math/MathML">' + mathml + "</math>"))

    def test_a_command_never_runs_into_the_next_letter(self):
        self.assertEqual("2\\pi r", self.latex("<mn>2</mn><mi>\u03c0</mi><mi>r</mi>"))
        self.assertEqual("s\\cdot s", self.latex("<mi>s</mi><mo>\u22c5</mo><mi>s</mi>"))
        self.assertEqual("a\\ge b", self.latex("<mrow><mi>a</mi><mo>\u2265</mo><mi>b</mi></mrow>"))
        # Controls: a digit, a brace or another command after a command needs no space.
        self.assertEqual("4\\cdot3", self.latex("<mn>4</mn><mo>\u22c5</mo><mn>3</mn>"))
        self.assertEqual("\\pi{r}^{2}", self.latex("<mi>\u03c0</mi><msup><mi>r</mi><mn>2</mn></msup>"))

    def test_a_bar_over_digits_is_an_overline(self):
        self.assertEqual("0.\\overline{3}", self.latex("<mn>0.</mn><mover><mn>3</mn><mo>\u2013</mo></mover>"))
        self.assertEqual("0.\\overline{16}", self.latex("<mn>0.</mn><mover><mn>16</mn><mtext>\u2014</mtext></mover>"))
        # Control: a carried digit written over a column stays a superscript.
        self.assertEqual("{4}^{1}", self.latex("<mover><mn>4</mn><mn>1</mn></mover>"))


class IntrostatsChunkTest(unittest.TestCase):
    def test_every_passage_starts_at_a_line_or_word_and_nothing_is_lost(self):
        from packtool.schema import Document
        rows = [json.loads(line) for line in CORPUS.read_text(encoding="utf-8").splitlines()]
        # Appendix H is 447 characters: a single chunk, checked separately below.
        for row in rows[:8] + rows[-4:-1]:
            text = row["text"]
            chunks = builder.line_chunks(Document(**row))
            self.assertGreater(len(chunks), 1)
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

    def test_the_short_appendix_is_a_single_chunk(self):
        from packtool.schema import Document
        rows = [json.loads(line) for line in CORPUS.read_text(encoding="utf-8").splitlines()]
        appendix_h = rows[-1]
        self.assertEqual("openstax-introstats-m47875", appendix_h["document_id"])
        self.assertEqual(1, len(builder.line_chunks(Document(**appendix_h))))


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
