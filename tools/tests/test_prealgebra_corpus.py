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
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

from tools.packs import build_prealgebra_pack as builder
from tools.packs import extract_openstax_prealgebra as extractor


ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "tools" / "packs" / "prealgebra" / "corpus.jsonl"
CORPUS_SHA256 = "8cdc11cd558c6da9058c3def92c9c55fcf6dfe3209eadd9fbde54a6591369a75"
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

    def test_every_latex_command_is_one_the_converter_writes(self):
        # A command that ran into the next letter ("\\pir", "\\cdotx", "\\geb") is not in this set.
        known = {"frac", "text", "cdot", "div", "sqrt", "begin", "end", "times", "ne", "pi", "approx", "ge", "le",
                 "dots", "Delta", "pm", "infty", "theta", "left", "right", "circ", "overline"}
        found = {name for row in self.rows for name in re.findall(r"\\([A-Za-z]+)", row["text"])}
        self.assertEqual(set(), found - known)
        text = "\n".join(row["text"] for row in self.rows)
        for formula in ("$C=2\\pi r$", "V=s\\cdot s\\cdot s", "$a\\ge b$"):
            self.assertIn(formula, text)

    def test_sections_carry_their_glossary(self):
        with_glossary = [row for row in self.rows if "\n\nGlossary\n" in row["text"]]
        self.assertEqual(39, len(with_glossary))
        fractions = next(row["text"] for row in self.rows if row["document_id"] == "openstax-prealgebra-m81285")
        self.assertIn("equivalent fractions\nEquivalent fractions are two or more fractions that have the same value.",
                      fractions.split("\n\nGlossary\n", 1)[1])


    def test_part_labels_and_repeating_decimals_read_plainly(self):
        text = "\n".join(row["text"] for row in self.rows)
        self.assertIn("Which of the following are (a) counting numbers? (b) whole numbers?", text)
        self.assertIsNone(re.search("[\u24d0-\u24e9\u00ba]", text))
        self.assertIn("$0.58\\overline{3}$", text)
        self.assertIsNone(re.search(r"\^\{(?:\\text\{)?[\u2013\u2014\u00af]", text))


class PrealgebraConverterTest(unittest.TestCase):
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


class PrealgebraChunkTest(unittest.TestCase):
    def test_every_passage_starts_at_a_line_or_word_and_nothing_is_lost(self):
        from packtool.schema import Document
        rows = [json.loads(line) for line in CORPUS.read_text(encoding="utf-8").splitlines()]
        for row in rows[:8] + rows[-4:]:
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


class PrealgebraPackTest(unittest.TestCase):
    def test_the_pack_carries_its_own_id_and_its_credits(self):
        with tempfile.TemporaryDirectory() as folder:
            report = builder.build(CORPUS, Path(folder) / "pack", "0.1.1")
            pack = Path(folder) / "pack" / report["pack"]
            with zipfile.ZipFile(pack) as archive:
                manifest = json.loads(archive.read("manifest.json"))
                credits = archive.read("ATTRIBUTION.txt")
            self.assertEqual(("openstax-maths-stats", "0.1.1", "CC-BY-4.0"),
                             (manifest["id"], manifest["version"], manifest["license"]))
            self.assertEqual(credits, builder.ATTRIBUTION.read_bytes())
            listed = {a["path"]: a for a in manifest["artifacts"]}
            self.assertEqual(hashlib.sha256(credits).hexdigest(), listed["ATTRIBUTION.txt"]["sha256"])
            self.assertEqual(credits, (pack.parent / "ATTRIBUTION.txt").read_bytes())
            self.assertIn(b"Lynn Marecek", credits)
            self.assertIn(b"CC BY 4.0", credits)
            self.assertEqual(report["installedBytes"], sum(a["bytes"] for a in manifest["artifacts"]))


if __name__ == "__main__":
    unittest.main()
