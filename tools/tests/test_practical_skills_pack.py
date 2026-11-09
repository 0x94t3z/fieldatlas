import hashlib
import json
from pathlib import Path
import unittest

from tools.packs import build_practical_skills_pack as practical

ROOT = Path(__file__).resolve().parents[2]


class PracticalSkillsScopeTest(unittest.TestCase):
    def test_in_scope_text_passes(self):
        for text in ("Add 2 tablespoons of bottled lemon juice per quart of tomatoes.",
                     "Store at least 1 gallon of water per person, per day for 3 days.",
                     "If you are trapped, send a text or bang on a pipe or wall.",
                     "Tinder is laid on a dry base to avoid the fine fibers from soaking up moisture."):
            self.assertEqual(practical.check_passage("cdc-water-store", text), [], text)

    def test_medicine_and_dosing_are_refused(self):
        for text in ("Take 500 mg every six hours.", "Most antibiotics come in 250-milligram tablets.",
                     "Learn first aid and CPR.", "Watch for signs of hypothermia and frostbite.",
                     "Seek medical attention right away."):
            self.assertTrue(any(p.startswith("medical") for p in practical.check_passage("fema-are-you-ready", text)), text)

    def test_wild_food_is_refused(self):
        for text in ("Many berries are edible.", "Never eat wild mushrooms.", "Set snares along game trails.",
                     "Apply the universal edibility test."):
            self.assertTrue(any(p.startswith("wild food") for p in practical.check_passage("army-atp-3-50-21", text)), text)

    def test_atp_plant_water_and_weapons_are_refused_only_for_the_manual(self):
        text = "Cut the vine and let the water drip into a container."
        self.assertTrue(any(p.startswith("atp exclusion") for p in practical.check_passage("army-atp-3-50-21", text)))
        self.assertEqual(practical.check_passage("nws-flood-before", text), [])

    def test_a_garbled_text_layer_is_refused(self):
        self.assertIn("garbled text layer", practical.check_passage("army-atp-3-50-21", "\x14\x1b\x036HSWHPEHU 2018"))


class PracticalSkillsVerbatimTest(unittest.TestCase):
    SOURCE = "If a lid fails to seal on a jar, remove the lid and check the jar-sealing surface for tiny nicks."

    def test_whitespace_and_hyphenation_are_the_only_freedoms(self):
        reference = practical.proof_key("If a lid fails to seal on a jar, remove the lid and check the jar-\nsealing\n surface for tiny nicks.")
        self.assertIn(practical.proof_key(self.SOURCE), reference)
        self.assertIn(practical.proof_key("If a lid fails to seal on a jar, remove the lid and check the jarsealing surface"), reference)

    def test_a_reworded_passage_fails_the_proof(self):
        reference = practical.proof_key(self.SOURCE)
        for changed in ("If a lid fails to seal on a jar, remove the lid and check the jar-sealing surface for small nicks.",
                        "If a lid fails to seal on a jar remove the lid and check the jar-sealing surface for tiny nicks.",
                        "If the lid fails to seal on a jar, remove the lid and check the jar-sealing surface for tiny nicks."):
            self.assertNotIn(practical.proof_key(changed), reference, changed)

    def test_hyphen_is_dropped_only_for_a_word_the_source_uses(self):
        self.assertEqual(practical.join_lines(["home-", "canned food"], {"homecanned"}), "homecanned food")
        self.assertEqual(practical.join_lines(["up-", "to-date"], {"other"}), "up-to-date")


class PracticalSkillsSelectionTest(unittest.TestCase):
    def test_an_excluded_heading_that_is_never_seen_fails_closed(self):
        source = {"id": "fema-are-you-ready", "exclude_sections": ["Pandemics"]}
        with self.assertRaises(ValueError):
            practical.group_sections(source, [("h", "Flood"), ("p", "Move to higher ground.")])

    def test_excluded_sections_are_left_out_and_kept_sections_stay(self):
        source = {"id": "fema-are-you-ready", "exclude_sections": ["Pandemics"]}
        sections = practical.group_sections(source, [("h", "Flood"), ("p", "Move to higher ground."),
                                                     ("h", "Pandemics"), ("p", "Wash your hands.")])
        self.assertEqual(sections, [("Flood", "Move to higher ground.")])

    def test_keep_sections_drops_everything_else(self):
        source = {"id": "usgs-fs-2025-3050", "keep_sections": ["Preparing for Future Earthquakes"]}
        sections = practical.group_sections(source, [("p", "Abstract."), ("h", "Preparing for Future Earthquakes"),
                                                     ("p", "Practice Drop, Cover, and Hold On.")])
        self.assertEqual(sections, [("Preparing for Future Earthquakes", "Practice Drop, Cover, and Hold On.")])

    def test_source_list_pins_federal_hosts_and_the_atp_exclusions(self):
        sources = {s["id"]: s for s in practical.load_sources(practical.DEFAULT_SOURCES)}
        atp = sources["army-atp-3-50-21"]["pdf"]
        for page in (19, 54, 71, 116):  # chapter 2 (survival medicine) and chapter 4 (food)
            self.assertTrue(practical.in_ranges(page, atp["exclude_pages"]), page)
            self.assertFalse(practical.in_ranges(page, atp["pages"]) and not practical.in_ranges(page, atp["exclude_pages"]))
        for page in (55, 117, 135, 153):  # water, fire, shelter, navigation
            self.assertTrue(practical.in_ranges(page, atp["pages"]), page)

    def test_an_off_list_host_is_refused(self):
        data = json.loads(practical.DEFAULT_SOURCES.read_bytes())
        data["sources"][0] = {**data["sources"][0], "url": "https://example.com/guide.pdf"}
        path = ROOT / "build" / "test-practical-sources.json"
        path.parent.mkdir(exist_ok=True)
        try:
            path.write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaises(ValueError):
                practical.load_sources(path)
        finally:
            path.unlink(missing_ok=True)


class PracticalSkillsProcessTableTest(unittest.TestCase):
    HEADER = "         Style        Jar          Process      0–          2,001–"

    def test_cells_keep_their_columns(self):
        columns = [start for start, _ in practical.table_cells(self.HEADER)]
        self.assertEqual(practical.align_row(practical.table_cells("         Hot          Pints        30\ufffdmin       11\ufffdlb        12\ufffdlb"), columns),
                         ["Hot", "Pints", "30 min", "11 lb", "12 lb"])
        # A row with no style keeps its jar size under "Jar", not under "Style".
        self.assertEqual(practical.align_row(practical.table_cells("                      Quarts       35           11           12"), columns),
                         ["", "Quarts", "35", "11", "12"])

    def test_a_slipped_cell_rejects_the_row(self):
        columns = [start for start, _ in practical.table_cells(self.HEADER)]
        self.assertIsNone(practical.align_row(practical.table_cells("         Hot                   Pints   30 min"), columns))
        self.assertIsNone(practical.align_row([(9, "Hot"), (10, "Cold")], columns))


class PracticalSkillsQuestionsTest(unittest.TestCase):
    def test_the_sealed_question_file_is_unchanged(self):
        path = ROOT / "docs/fieldtest/practical-skills.jsonl"
        data = path.read_bytes()
        self.assertEqual(hashlib.sha256(data).hexdigest(),
                         "131d9882a4c78a5eea7060ad37e044ee4ebafa8fb4cd8e56e9224ff82fa61469")
        rows = [json.loads(line) for line in data.decode("utf-8").splitlines()]
        self.assertEqual(len(rows), 30)


if __name__ == "__main__":
    unittest.main()
