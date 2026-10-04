from __future__ import annotations

import json
from pathlib import Path
import re
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_emergency_data as ed

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/emergency"
CACHE = OUT / "cache"
GUIDES = OUT / "guides.json"
NUMBERS = OUT / "numbers.json"
SOURCES = ROOT / "tools/emergency_sources.json"

PAGE = """<html><body><nav><a>Home</a> Skip to main content</nav>
<main>
  <section class="cdc-page-title-bar"><time>Sept. 15, 2025</time></section>
  <h1>Venomous Snakes</h1>
  <div class="cdc-callout"><h3>Warning</h3><div>You <strong>cannot</strong> make fuel-contaminated water safe.</div></div>
  <h2>Symptoms</h2>
  <p>Signs vary.<span class="cdc-references-cite"><a href="#cdcreference_2">2</a></span> Around the wound:</p>
  <ul><li>Puncture marks</li><li>Trouble breathing<ul><li>In extreme cases, breathing may stop</li></ul></li></ul>
  <p>After a bite, you may experience nausea.</p>
  <figure><img src="x.png"><figcaption>Photo of a snake</figcaption></figure>
  <h2>First Aid</h2>
  <h3>If a snake bites you</h3>
  <ol><li>Keep <b>c</b>alm.</li><li>Inform your supervisor.</li><li>Remove rings<br>and watches.</li></ol>
  <div class="table" role="table"><div role="rowheader">Adults</div><div role="cell"><li>Shivering</li><li>Confusion</li></div></div>
  <table><tr><td>Data table text</td></tr></table>
  <h3><a class="toggle" href="#"><span class="control">+</span>Outside</a></h3>
  <p>Find shelter.</p>
  <span class="sr-only">Opens in new window</span>
  <div class="page-content-sources">Sources and Page Info Print Share</div>
  <footer>Page last reviewed: today</footer>
</main></body></html>"""

WIKI = """<div class="mw-parser-output"><div class="hatnote">For other uses, see X.</div>
<p>Choking is a blockage.<sup class="reference"><a href="#cite_note-1">[1]</a></sup> It can kill.<sup class="noprint Inline-Template">[<i>citation needed</i>]</sup></p>
<div class="mw-heading mw-heading2"><h2>First aid</h2><span class="mw-editsection">[edit]</span></div>
<p>Use back blows in women since <span class="frac">7<span class="sr-only">+</span><span class="num">1</span>&#8260;<span class="den">2</span></span> months.</p>
<table class="wikitable"><tr><td>table cell</td></tr></table>
<div class="mw-heading mw-heading2"><h2>References</h2></div><ol class="references"><li>Cite</li></ol></div>"""

NPS_LAYOUT = """<div id="main"><h1>Acute Mountain Sickness</h1><table class="CS_Layout_Table"><tr><td>
<div><p>The most effective treatment is simply to GO DOWN.</p></div></td></tr></table></div>"""

NUMBERS_HTML = """<div class="mw-parser-output">
<div class="mw-heading mw-heading2"><h2>Europe</h2></div>
<table class="wikitable sortable"><tbody>
<tr><th>Country</th><th>Police</th><th>Ambulance</th><th>Fire</th><th>Notes</th></tr>
<tr><td>United Kingdom</td><td colspan="3">999 or 112<sup class="reference">[5]</sup></td><td>Non-emergency police – 101; Power outages – 105.</td></tr>
<tr><td>Germany</td><td>110</td><td colspan="2">112</td><td>Non-emergency medical on-call duty: 116 117.</td></tr>
<tr><td>Kosovo</td><td>192</td><td>194</td><td>193</td><td></td></tr>
</tbody></table>
<div class="mw-heading mw-heading2"><h2>Asia</h2></div>
<table class="wikitable"><tbody>
<tr><th>Country</th><th>Police</th><th>Ambulance</th><th>Fire</th><th>Notes</th></tr>
<tr><td>Indonesia</td><td colspan="3">112</td><td>Police – 110; Ambulance – 118 or 119; Fire – 113 or 1131; Search &amp; rescue – 115.</td></tr>
<tr><td>Australia</td><td colspan="3">000</td><td>Mobile phones – 112 or 000; State Emergency Service – 132 500.</td></tr>
<tr><td>Namibia</td><td>10111</td><td colspan="2">depends on town/city</td><td></td></tr>
</tbody></table></div>"""


def all_texts(guide):
    for section in guide["sections"]:
        if section["heading"]:
            yield section["heading"]
        yield from section["paragraphs"]
        yield from section["steps"]


class ExtractionTest(unittest.TestCase):
    def setUp(self):
        self.blocks = ed.html_blocks(PAGE, "main")
        self.texts = [ed._block_text(b) for b in self.blocks]

    def test_keeps_guidance_and_drops_page_chrome(self):
        joined = "\n".join(self.texts)
        for noise in ("Skip to main content", "Sept. 15, 2025", "Photo of a snake", "Data table text",
                      "Opens in new window", "Sources and Page Info", "Page last reviewed"):
            self.assertNotIn(noise, joined)
        self.assertIn("You cannot make fuel-contaminated water safe.", self.texts)

    def test_inline_markup_joins_like_a_browser_and_citations_are_removed(self):
        self.assertIn("Keep calm.", self.texts)
        self.assertIn("Signs vary. Around the wound:", self.texts)
        self.assertIn("Remove rings and watches.", self.texts)

    def test_lists_keep_order_nesting_and_numbering(self):
        items = [b for b in self.blocks if b[0] == "li"]
        self.assertEqual(("li", "Trouble breathing", False, 1), items[1])
        self.assertEqual(("li", "In extreme cases, breathing may stop", False, 2), items[2])
        self.assertTrue(all(b[2] for b in items if b[1] in ("Keep calm.", "Inform your supervisor.")))
        self.assertIn(("li", "Shivering", False, 1), items)

    def test_accordion_glyph_is_not_part_of_heading(self):
        self.assertIn(("h", 3, "Outside"), self.blocks)

    def test_wikipedia_references_sections_and_fractions(self):
        blocks = ed.html_blocks(WIKI, ".mw-parser-output")
        texts = [ed._block_text(b) for b in blocks]
        self.assertEqual("Choking is a blockage. It can kill.", texts[0])
        self.assertIn("Use back blows in women since 7 1⁄2 months.", texts)
        self.assertNotIn("table cell", " ".join(texts))
        self.assertNotIn("For other uses", " ".join(texts))
        self.assertIn(("h", 2, "First aid"), blocks)

    def test_layout_tables_are_unwrapped_not_dropped(self):
        blocks = ed.html_blocks(NPS_LAYOUT, "#main")
        self.assertIn(("p", "The most effective treatment is simply to GO DOWN."), blocks)

    def test_clean_text_only_normalises_whitespace_and_citations(self):
        self.assertEqual("Cool the burn. Then cover it.", ed.clean_text(" Cool the burn [1] .\n Then cover it.[citation needed]"))
        self.assertEqual("Stop and breath – Take a moment", ed.clean_text("Stop and breath – Take a moment"))

    def test_text_problems_flags_leftovers(self):
        self.assertTrue(ed.text_problems("Apply pressure.[2]"))
        self.assertTrue(ed.text_problems("Use <b>cool</b> water"))
        self.assertTrue(ed.text_problems("This page was last updated on this date."))
        self.assertTrue(ed.text_problems("Sept. 23, 2026"))
        self.assertTrue(ed.text_problems("{{cite web}}"))
        self.assertEqual([], ed.text_problems("Call 9-1-1 right away if you or someone else has any of these symptoms."))


class SelectionTest(unittest.TestCase):
    def setUp(self):
        self.blocks = ed.html_blocks(PAGE, "main")

    def sections(self, selectors, omit=()):
        return ed.blocks_to_sections(self.blocks, ed.apply_omit(self.blocks, ed.select_spans(self.blocks, selectors), list(omit)))

    def test_heading_section_with_continuation_keeps_lead_lines_with_lists(self):
        sections = self.sections([{"heading": "Symptoms"}])
        self.assertEqual("Symptoms", sections[0]["heading"])
        self.assertEqual(["Signs vary. Around the wound:"], sections[0]["paragraphs"])
        self.assertEqual([1, 1, 2], sections[0]["step_levels"])
        self.assertFalse(sections[0]["numbered"])
        self.assertEqual("", sections[1]["heading"])
        self.assertEqual(["After a bite, you may experience nausea."], sections[1]["paragraphs"])

    def test_omit_inside_list_does_not_split_the_section(self):
        sections = self.sections([{"heading": "If a snake bites you", "context": True}], omit=["Inform your supervisor."])
        self.assertEqual(["First Aid", "If a snake bites you"], [s["heading"] for s in sections[:2]])
        self.assertEqual(["Keep calm.", "Remove rings and watches."], sections[1]["steps"][:2])
        self.assertTrue(sections[1]["numbered"])

    def test_selectors_fail_loudly_on_drift(self):
        with self.assertRaisesRegex(ValueError, "matched 0 times"):
            ed.select_spans(self.blocks, [{"heading": "Treatment"}])
        with self.assertRaisesRegex(ValueError, "matched nothing"):
            ed.apply_omit(self.blocks, ed.select_spans(self.blocks, [{"heading": "Symptoms"}]), ["Not there"])
        with self.assertRaisesRegex(ValueError, "overlaps"):
            ed.select_spans(self.blocks, [{"heading": "First Aid"}, {"heading": "If a snake bites you"}])

    def test_from_through_range_and_nested_false(self):
        spans = ed.select_spans(self.blocks, [{"from": "After a bite", "through": "After a bite"},
                                              {"heading": "First Aid", "nested": False}])
        sections = ed.blocks_to_sections(self.blocks, spans)
        self.assertEqual("", sections[0]["heading"])
        self.assertEqual(["After a bite, you may experience nausea."], sections[0]["paragraphs"])
        # "First Aid" has no body of its own and its subsections were excluded, so it is not shown.
        self.assertEqual(1, len(sections))
        nested = ed.blocks_to_sections(self.blocks, ed.select_spans(self.blocks, [{"heading": "First Aid"}]))
        self.assertEqual(["First Aid", "If a snake bites you"], [s["heading"] for s in nested[:2]])
        self.assertEqual([1, 2], [s["level"] for s in nested[:2]])


class NumbersParserTest(unittest.TestCase):
    def test_parse_colspans_notes_and_unmapped(self):
        countries, unmapped, complex_cells = ed.parse_numbers(NUMBERS_HTML)
        self.assertEqual(["999", "112"], countries["GB"]["general"])
        self.assertEqual(["999", "112"], countries["GB"]["police"])
        self.assertNotIn("101", countries["GB"]["police"])
        self.assertEqual(["110"], countries["DE"]["police"])
        self.assertEqual(["112"], countries["DE"]["fire"])
        self.assertEqual([], countries["DE"]["general"])
        self.assertEqual(["112", "118", "119"], countries["ID"]["ambulance"])
        self.assertEqual(["112", "110"], countries["ID"]["police"])
        self.assertNotIn("115", sum((countries["ID"][f] for f in ("police", "ambulance", "fire", "general")), []))
        self.assertEqual(["112", "000"], countries["AU"]["mobile"])
        self.assertNotIn("132 500", countries["AU"]["general"] + countries["AU"]["mobile"])
        self.assertEqual("Europe", countries["GB"]["region"])
        self.assertEqual(["Kosovo"], unmapped)
        self.assertEqual({"country": "Namibia", "service": "ambulance", "text": "depends on town/city"}, complex_cells[0])
        self.assertEqual("depends on town/city", countries["NA"]["listed"]["fire"])

    def test_note_labels_must_start_a_clause(self):
        self.assertEqual([], ed.labelled_note_numbers("Non-emergency police – 101; Federal police – 194"))
        self.assertEqual([], ed.labelled_note_numbers("State Emergency Service – 132 500"))
        self.assertEqual([("police", "100"), ("ambulance", "108")],
                         ed.labelled_note_numbers("Women Helpline – 181 Police – 100 Ambulance – 108"))

    def test_strict_cells(self):
        self.assertEqual(["112", "15"], ed.split_numbers("112 or 15"))
        self.assertEqual(["10 111"], ed.split_numbers("10 111"))
        self.assertEqual(["(+683) 4333"], ed.split_numbers("(+683) 4333"))
        self.assertIsNone(ed.split_numbers("McMurdo Station: 911 (fire and medical emergency dispatch)"))

    def test_iso_table_is_complete_and_unambiguous(self):
        self.assertEqual(249, len(ed.ISO_3166))
        self.assertTrue(all(re.fullmatch(r"[A-Z]{2}", code) for code in ed.ISO_3166))
        names = [name.casefold() for name in ed.ISO_3166.values()]
        self.assertEqual(len(names), len(set(names)))
        self.assertFalse(set(ed.NAME_ALIASES) & set(names))
        self.assertTrue(set(ed.NAME_ALIASES.values()) <= set(ed.ISO_3166))


class SourceListTest(unittest.TestCase):
    def test_source_list_is_valid_and_only_uses_allowed_publishers(self):
        data = ed.load_sources(SOURCES)
        for source in data["sources"]:
            if source["kind"] == "html":
                self.assertIn(re.match(r"https://([^/]+)/", source["url"]).group(1), ed.FEDERAL_HOSTS)
                self.assertNotIn("/ency/", source["url"], "MedlinePlus A.D.A.M. encyclopedia pages are copyrighted")
            else:
                self.assertIsInstance(source["revision"], int)
        banned = re.compile(r"redcross|heart\.org|mayoclinic|webmd|sja\.org|stjohn", re.I)
        self.assertFalse([s for s in data["sources"] if banned.search(s.get("url", ""))])


@unittest.skipUnless(GUIDES.exists() and NUMBERS.exists(), "run build_emergency_data.py build first")
class BuiltFilesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guides = json.loads(GUIDES.read_bytes())
        cls.numbers = json.loads(NUMBERS.read_bytes())

    def test_guides_schema(self):
        doc = self.guides
        self.assertRegex(doc["version"], r"^\d{4}\.\d{2}\.\d{2}$")
        self.assertRegex(doc["generated"], r"^\d{4}-\d{2}-\d{2}$")
        self.assertLess(GUIDES.stat().st_size, 600 * 1024)
        ids = [g["id"] for g in doc["guides"]]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertGreaterEqual(len(ids), 35)
        for guide in doc["guides"]:
            with self.subTest(guide=guide["id"]):
                self.assertRegex(guide["id"], r"^[a-z0-9-]+$")
                self.assertIn(guide["category"], ed.CATEGORIES)
                self.assertIsInstance(guide["urgent"], bool)
                self.assertIn(guide["region"], (None, "US"))
                self.assertTrue(guide["keywords"] and all(isinstance(k, str) and k for k in guide["keywords"]))
                self.assertTrue(guide["sections"])
                for section in guide["sections"]:
                    self.assertEqual({"heading", "level", "paragraphs", "steps"},
                                     set(section) - {"numbered", "step_levels"})
                    if "step_levels" in section:
                        self.assertEqual(len(section["steps"]), len(section["step_levels"]))

    def test_every_guide_has_a_licensed_hashed_source(self):
        for guide in self.guides["guides"]:
            with self.subTest(guide=guide["id"]):
                source = guide["source"]
                self.assertIn(source["license"], ed.LICENSES)
                self.assertRegex(source["sha256"], r"^[0-9a-f]{64}$")
                self.assertRegex(source["retrieved"], r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")
                self.assertTrue(source["publisher"] and source["title"] and source["url"].startswith("https://"))
                if source["license"] == ed.CC_BY_SA:
                    self.assertIsInstance(source["revision"], int)
                    self.assertIn(f"oldid={source['revision']}", source["url"])
                    self.assertIn("Wikipedia contributors", source["attribution"])
                else:
                    self.assertIsNone(source["revision"])
                    self.assertIn(re.match(r"https://([^/]+)/", source["url"]).group(1), ed.FEDERAL_HOSTS)

    def test_no_markup_citations_or_boilerplate_in_guide_text(self):
        for guide in self.guides["guides"]:
            for text in all_texts(guide):
                with self.subTest(guide=guide["id"], text=text[:60]):
                    self.assertEqual([], ed.text_problems(text))
                    self.assertNotRegex(text, r"\[\s*(?:\d+|citation needed|edit)\s*\]")
                    self.assertNotRegex(text, r"(?i)^(?:download|print|share|return to top|back to top|"
                                              r"related pages|on this page|resources)$")

    def test_guides_naming_911_are_marked_us(self):
        for guide in self.guides["guides"]:
            if any(re.search(r"\b9-?1-?1\b|1-800-222-1222", t) for t in all_texts(guide)):
                self.assertEqual("US", guide["region"], guide["id"])

    def test_life_threatening_topics_are_urgent(self):
        urgent = {g["id"] for g in self.guides["guides"] if g["urgent"]}
        for gid in ("cpr", "choking", "severe-bleeding", "anaphylaxis", "stroke", "heart-attack",
                    "heat-illness", "hypothermia", "drowning"):
            self.assertIn(gid, urgent)

    @unittest.skipUnless((CACHE / "sources.lock.json").exists(), "snapshot cache not present")
    def test_guide_text_is_verbatim_from_the_cached_source(self):
        data = ed.load_sources(SOURCES)
        sources = {s["id"]: s for s in data["sources"]}
        lock = json.loads((CACHE / "sources.lock.json").read_bytes())
        by_url = {ed.source_url(s): s for s in data["sources"]}
        for guide in self.guides["guides"]:
            source = by_url[guide["source"]["url"]]
            raw = ed.read_cached(CACHE, source, lock["sources"][source["id"]])
            self.assertEqual(guide["source"]["sha256"], ed.digest(raw))
            container = ".mw-parser-output" if source["kind"] == "wikipedia" else source["container"]
            available = {ed._block_text(b) for b in ed.html_blocks(ed.source_html(source, raw), container)}
            for text in all_texts(guide):
                with self.subTest(guide=guide["id"], text=text[:60]):
                    self.assertIn(text, available)
        self.assertTrue(sources)

    def test_numbers_schema(self):
        doc = self.numbers
        self.assertEqual(["112", "911"], doc["default"])
        self.assertIn("GSM", doc["default_note"])
        self.assertEqual(ed.CC_BY_SA, doc["source"]["license"])
        self.assertIsInstance(doc["source"]["revision"], int)
        self.assertRegex(doc["source"]["sha256"], r"^[0-9a-f]{64}$")
        self.assertGreater(len(doc["countries"]), 200)
        for code, entry in doc["countries"].items():
            with self.subTest(code=code):
                self.assertIn(code, ed.ISO_3166)
                for field in ("police", "ambulance", "fire", "general", "mobile"):
                    self.assertTrue(all(re.fullmatch(ed.NUMBER_TOKEN, n) for n in entry[field]))
                self.assertEqual({"police", "ambulance", "fire"}, set(entry["listed"]))

    def services(self, code, *fields):
        entry = self.numbers["countries"][code]
        fields = fields or ("police", "ambulance", "fire", "general", "mobile")
        return {n for f in fields for n in entry[f]}

    def test_known_emergency_numbers(self):
        self.assertIn("911", self.services("US", "general"))
        self.assertTrue({"999", "112"} <= self.services("GB", "general"))
        self.assertIn("112", self.services("DE", "fire"))
        self.assertIn("112", self.services("DE", "ambulance"))
        self.assertIn("110", self.services("DE", "police"))
        self.assertTrue({"112", "15", "17", "18"} <= self.services("FR"))
        self.assertIn("17", self.services("FR", "police"))
        self.assertIn("15", self.services("FR", "ambulance"))
        self.assertIn("18", self.services("FR", "fire"))
        self.assertIn("110", self.services("JP", "police"))
        self.assertIn("119", self.services("JP", "fire"))
        self.assertIn("119", self.services("JP", "ambulance"))
        self.assertIn("112", self.services("IN", "general"))
        self.assertIn("000", self.services("AU", "general"))
        self.assertIn("112", self.services("AU"))
        self.assertIn("112", self.services("ID", "general"))
        self.assertIn("110", self.services("ID", "police"))
        self.assertTrue({"118", "119"} <= self.services("ID", "ambulance"))
        self.assertIn("111", self.services("NZ", "general"))
        self.assertIn("110", self.services("CN", "police"))
        self.assertIn("120", self.services("CN", "ambulance"))
        self.assertIn("119", self.services("CN", "fire"))
        self.assertIn("190", self.services("BR", "police"))
        self.assertIn("192", self.services("BR", "ambulance"))
        self.assertIn("193", self.services("BR", "fire"))


@unittest.skipUnless((CACHE / "sources.lock.json").exists(), "snapshot cache not present")
class ReproducibleBuildTest(unittest.TestCase):
    def test_build_is_offline_and_byte_identical(self):
        def no_network(*args, **kwargs):
            raise AssertionError("build must not use the network")
        with tempfile.TemporaryDirectory() as directory, patch("urllib.request.urlopen", no_network):
            first, second = Path(directory) / "a", Path(directory) / "b"
            with patch("sys.stdout"):
                ed.build(SOURCES, CACHE, first)
                ed.build(SOURCES, CACHE, second)
            for name in ("guides.json", "numbers.json"):
                self.assertEqual((first / name).read_bytes(), (second / name).read_bytes())
                if (OUT / name).exists():
                    self.assertEqual((OUT / name).read_bytes(), (first / name).read_bytes(),
                                     f"{name} is stale; rerun the build")

    def test_corrupted_cache_is_rejected(self):
        data = ed.load_sources(SOURCES)
        lock = json.loads((CACHE / "sources.lock.json").read_bytes())
        source = data["sources"][0]
        row = dict(lock["sources"][source["id"]], sha256="0" * 64)
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            ed.read_cached(CACHE, source, row)


if __name__ == "__main__":
    unittest.main()
