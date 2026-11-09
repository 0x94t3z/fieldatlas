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


class PracticalSkillsTableFragmentTest(unittest.TestCase):
    """Flattened table cells from plain reading order, as they reached the 2026.11.09 build."""
    SYRUP = ["10", "6-1/2", "3/4", "10-1/2", "1-1/4", "Light", "20", "5-3/4", "1-1/2", "2-1/4",
             "Very sweet fruit. Try a small amount the first time to see if your family likes it.",
             "Medium", "30", "5-1/4", "2-1/4", "8-1/4", "3-3/4"]
    ASPARAGUS = ["6,001\u2013 8,000 ft", "Pints", "30 min", "11 lb", "12 lb", "13 lb", "14 lb", "Quarts", "40",
                 "11", "12", "13", "14",
                 "Recommended process time for Asparagus in a weighted-gauge pressure canner Style of Pack",
                 "Pints", "30 min", "10 lb", "15 lb"]
    CRAB_APPLES = ["0\u2013 1,000 ft 15 min", "1,001\u2013 3,000 ft 20", "3,001\u2013 6,000 ft 20", "Above 6,000 ft 25",
                   "20", "25", "30", "35", "Fruit and Fruit Products",
                   "Recommended process time for Spiced Apple Rings in a boiling-water canner", "Jar Size Pints",
                   "0\u2013 1,000 ft 20 min", "1,001\u2013 3,000 ft 25", "3,001\u2013 6,000 ft 30"]
    PROSE = ("Bring clear water to a rolling boil for 1 minute (at elevations above 6,500 feet, "
             "boil for 3 minutes).")

    def test_flattened_tables_are_dropped(self):
        for cells in (self.SYRUP, self.ASPARAGUS, self.CRAB_APPLES):
            paragraphs = [("p", "Fill hot jars with the fruit and cover with hot syrup, leaving 1/2-inch headspace.")] + \
                [("p", c) for c in cells] + [("p", "Remove air bubbles and adjust headspace if needed.")]
            kept, dropped = practical.drop_table_fragments(paragraphs)
            texts = [text for _, text in kept]
            self.assertEqual(texts, [paragraphs[0][1], paragraphs[-1][1]], texts)
            self.assertEqual(dropped, len(cells))

    def test_prose_with_numbers_is_kept(self):
        paragraphs = [("p", self.PROSE), ("p", "Let the boiled water cool."), ("p", "Store the boiled water in clean sanitized containers with tight covers.")]
        self.assertFalse(practical.is_cell(self.PROSE))
        self.assertEqual(practical.drop_table_fragments(paragraphs), (paragraphs, 0))

    def test_a_lone_heading_is_kept(self):
        paragraphs = [("h", "Pressure canners"), ("p", "Pressure canners for use in the home have been extensively redesigned in recent years.")]
        self.assertEqual(practical.drop_table_fragments(paragraphs), (paragraphs, 0))


class PracticalSkillsRecipeTest(unittest.TestCase):
    # Passage usda-canning-guide-6-0024 of the first 2026.11.09 build: a yield and a procedure whose
    # ingredient list the cell filter had removed ("Mix all remaining ingredients").
    INCOMPLETE = 'Yield: About 9 half-pints Procedure: Select your favorite pepper. Caution: If you select hot peppers, wear plastic or rubber gloves and do not touch your face while handling or cutting hot peppers. If you do not wear gloves, wash hands thoroughly with soap and water before touching your face or eyes. Peppers may be left whole. Large peppers may be quartered. Wash, slash two to four slits in each pepper, and blanch in boiling water or blister skins on tough-skinned hot peppers using one of these two methods: Oven or broiler method to blister skins – Place peppers in a hot oven (400°F) or under a broiler for 6 to 8 minutes until skins blister. Range-top method to blister skins – Cover hot burner (either gas or electric) with heavy wire mesh. Place peppers on burner for several minutes until skins blister. After blistering skins, place peppers in a pan and cover with a damp cloth. (This will make peeling Marinated Peppers the peppers easier.) Cool several minutes; peel off skins. Flatten whole peppers. Mix all remaining ingredients in a saucepan and heat to boiling. Place 1/4 garlic clove (optional) and 1/4 teaspoon salt in each hot half-pint jar or 1/2 teaspoon per pint. Fill hot jars with peppers. Add hot, wellmixed oil/pickling solution over peppers, leaving 1/2-inch headspace. Remove air bubbles and adjust headspace if needed. Wipe rims of jars with a dampened clean paper towel. Adjust lids and process.'
    # Cantaloupe Pickles, No Sugar Added, read whole from the layout reading.
    WHOLE = 'CANTALOUPE PICKLES, NO SUGAR ADDED\n\n6 lbs of one-inch cantaloupe cubes (about 3 medium underripe* cantaloupe)\n\n1 tsp crushed red pepper flakes\n\n2 one-inch cinnamon sticks\n\n2 tsp ground cloves\n\n1 tsp ground ginger\n\n4-1/2 cups cider vinegar (5%)\n\n2 cups water\n\n3 cups Splenda®\n\n*Select cantaloupe that are full size but almost fully green and firm to the touch in all areas including the stem area.\n\nYield: About 4 pint jars\n\nProcedure: Follow the directions for Cantaloupe Pickles, weighing out 6 pounds of prepared cantaloupe cubes instead and substituting Splenda® when sugar would be added.'

    def test_a_recipe_without_its_amounts_is_refused(self):
        self.assertEqual(practical.recipe_problem(self.INCOMPLETE), "recipe without its ingredient amounts")

    def test_a_whole_recipe_passes(self):
        self.assertIsNone(practical.recipe_problem(self.WHOLE))

    def test_a_procedure_naming_the_ingredients_needs_them(self):
        self.assertIsNotNone(practical.recipe_problem("Procedure: Combine all ingredients and boil gently for 30 minutes."))
        self.assertIsNone(practical.recipe_problem("Procedure: Wash jars in hot soapy water and keep them hot until filled."))

    def test_a_procedure_that_follows_another_recipe_names_it(self):
        self.assertEqual(practical.recipe_references(self.WHOLE), ["Cantaloupe Pickles"])
        self.assertEqual(practical.recipe_references("Procedure: Wash and chop vegetables."), [])


class PracticalSkillsWebChromeTest(unittest.TestCase):
    def test_page_chrome_is_refused(self):
        for text in ("Available in English, Spanish, and Vietnamese.", "Available in English and Spanish.",
                     "Page last reviewed: May 13, 2024", "Download Infographic PDF: English | Español", "Print"):
            self.assertIn("navigation boilerplate", practical.check_passage("cdc-water-make-safe", text), text)

    def test_guidance_that_mentions_signing_up_is_kept(self):
        self.assertEqual(practical.check_passage("fema-are-you-ready",
                         "Sign up for alerts on current avalanche danger, snowpack, and mountain weather conditions."), [])


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
