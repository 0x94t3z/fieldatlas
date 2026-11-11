import hashlib
import json
import os
import shutil
from pathlib import Path
import unittest
from unittest import mock

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


class PracticalSkillsPhotoLabelTest(unittest.TestCase):
    """USDA Guide 4, page 4-17: the photo label "Pumpkin and Winter Squash" is printed into the
    procedure, so the layout reading runs it into the entry's lines. The entry used to be left out
    whole, and with it "Caution: Do not mash or puree." (a phone answered that mashed pumpkin may
    be canned at home). The excerpt is the page as poppler 26.01 reads it: layout, plain reading
    and word boxes."""
    LAYOUT = (
        '       PUMPKINS AND WINTER SQUASH\u2014CUBED\n'
        '       Quantity: An average of 16 pounds is needed per canner load of 7 quarts; an average of 10\n'
        '       pounds is needed per canner load of 9 pints\u2014an average of 2-1/4 pounds per quart.\n'
        '\n'
        '       Quality: Pumpkins and squash should have a hard rind and stringless, mature pulp of ideal\n'
        '       quality for cooking fresh. Small size pumpkins (sugar or pie varieties) make better products.\n'
        '\n'
        '       Procedure: Wash,     remove\n'
        '                    Pumpkin and    seeds, cut\n'
        '                                 Winter Squashinto 1-inch-wide slices, and peel. Cut flesh into 1-inch cubes.\n'
        '       Boil 2 minutes in water. Caution: Do not mash or puree. Fill hot jars with cubes and cooking\n'
        '       liquid, leaving 1-inch headspace. Remove air bubbles and adjust headspace if needed. Wipe rims\n'
        '       of jars with a dampened clean paper towel. Adjust lids and process.\n'
        '\n'
        '       For making pies, drain jars and strain or sieve the cubes at preparation time.\n'
        '                  Recommended\ufffdprocess\ufffdtime\ufffdfor\ufffdPumpkin\ufffdand\ufffdWinter\ufffdSquash\ufffdin\ufffda\n')
    PLAIN = (
        'PUMPKINS AND WINTER SQUASH\u2014CUBED\n'
        'Quantity: An average of 16 pounds is needed per canner load of 7 quarts; an average of 10\n'
        'pounds is needed per canner load of 9 pints\u2014an average of 2-1/4 pounds per quart.\n'
        'Quality: Pumpkins and squash should have a hard rind and stringless, mature pulp of ideal\n'
        'quality for cooking fresh. Small size pumpkins (sugar or pie varieties) make better products.\n'
        'Procedure: Wash,\n'
        'remove\n'
        'seeds, cut\n'
        'into 1-inch-wide slices, and peel. Cut flesh into 1-inch cubes.\n'
        'Pumpkin and\n'
        'Winter\n'
        'Squash\n'
        'Boil 2 minutes in water. Caution: Do not mash or puree. Fill hot jars with cubes and cooking\n'
        'liquid, leaving 1-inch headspace. Remove air bubbles and adjust headspace if needed. Wipe rims\n'
        'of jars with a dampened clean paper towel. Adjust lids and process.\n'
        'For making pies, drain jars and strain or sieve the cubes at preparation time.\n'
        'Recommended\ufffdprocess\ufffdtime\ufffdfor\ufffdPumpkin\ufffdand\ufffdWinter\ufffdSquash\ufffdin\ufffda\n')
    # x, y, width, height and text of each word box on the page (pdftotext -tsv).
    BOXES = """\
63.00 65.39 60.31 16.08 PUMPKINS
125.70 65.39 25.54 16.08 AND
153.64 65.39 44.60 16.08 WINTER
200.63 65.39 99.80 16.08 SQUASH\u2014CUBED
63.00 86.65 48.46 14.25 Quantity:
116.22 87.08 13.39 13.82 An
132.04 87.08 38.07 13.82 average
172.55 87.08 9.67 13.82 of
184.66 87.08 11.80 13.82 16
198.89 87.08 36.62 13.82 pounds
237.95 87.08 7.25 13.82 is
247.63 87.08 36.64 13.82 needed
286.71 87.08 16.07 13.82 per
305.21 87.08 32.98 13.82 canner
340.63 87.08 21.06 13.82 load
364.13 87.08 9.67 13.82 of
376.24 87.08 5.90 13.82 7
384.57 87.08 33.13 13.82 quarts;
420.14 87.08 11.93 13.82 an
434.51 87.08 38.07 13.82 average
475.01 87.08 9.67 13.82 of
487.12 87.08 11.80 13.82 10
63.00 100.88 36.62 13.82 pounds
102.05 100.88 7.25 13.82 is
111.74 100.88 36.64 13.82 needed
150.81 100.88 16.07 13.82 per
169.32 100.88 32.98 13.82 canner
204.74 100.88 21.06 13.82 load
228.23 100.88 9.67 13.82 of
240.34 100.88 5.90 13.82 9
248.68 100.88 47.36 13.82 pints\u2014an
298.47 100.88 38.06 13.82 average
338.98 100.88 9.67 13.82 of
351.09 100.88 23.79 13.82 2-1/4
376.63 100.88 36.62 13.82 pounds
415.68 100.88 16.07 13.82 per
434.18 100.88 28.54 13.82 quart.
63.00 128.05 40.73 14.25 Quality:
108.61 128.48 47.63 13.82 Pumpkins
158.68 128.48 18.41 13.82 and
179.53 128.48 33.84 13.82 squash
215.81 128.48 32.79 13.82 should
251.04 128.48 23.01 13.82 have
276.48 128.48 5.54 13.82 a
284.47 128.48 22.06 13.82 hard
308.96 128.48 19.37 13.82 rind
330.76 128.48 18.41 13.82 and
351.61 128.48 47.51 13.82 stringless,
401.56 128.48 34.64 13.82 mature
438.63 128.48 22.14 13.82 pulp
463.21 128.48 9.67 13.82 of
475.32 128.48 23.20 13.82 ideal
63.00 142.28 33.08 13.82 quality
98.52 142.28 13.28 13.82 for
114.24 142.28 38.80 13.82 cooking
155.48 142.28 26.08 13.82 fresh.
184.00 142.28 26.23 13.82 Small
212.67 142.28 17.85 13.82 size
232.96 142.28 48.23 13.82 pumpkins
283.63 142.28 29.89 13.82 (sugar
315.95 142.28 10.07 13.82 or
328.46 142.28 15.00 13.82 pie
345.90 142.28 43.37 13.82 varieties)
391.70 142.28 26.29 13.82 make
420.43 142.28 29.37 13.82 better
452.24 142.28 45.24 13.82 products.
63.00 169.45 55.56 14.25 Procedure:
123.02 169.88 28.21 13.82 Wash,
153.67 169.88 36.41 13.82 remove
192.51 169.88 29.37 13.82 seeds,
224.32 169.88 15.29 13.82 cut
242.05 169.88 19.08 13.82 into
263.57 169.88 56.91 13.82 1-inch-wide
322.92 169.88 27.61 13.82 slices,
352.97 169.88 18.41 13.82 and
373.82 169.88 23.05 13.82 peel.
399.30 169.88 16.68 13.82 Cut
418.42 169.88 22.71 13.82 flesh
443.57 169.88 19.08 13.82 into
465.08 169.88 30.04 13.82 1-inch
497.56 169.88 30.60 13.82 cubes.
116.00 175.39 43.34 10.92 Pumpkin
162.34 175.39 17.33 10.92 and
182.67 175.39 33.32 10.92 Winter
219.00 175.39 34.67 10.92 Squash
63.00 183.68 18.00 13.82 Boil
83.44 183.68 5.90 13.82 2
91.77 183.68 39.05 13.82 minutes
133.27 183.68 9.07 13.82 in
144.78 183.68 28.94 13.82 water.
176.16 183.25 43.24 14.25 Caution:
221.72 183.25 14.64 14.25 Do
238.68 183.25 17.60 14.25 not
258.60 183.25 27.69 14.25 mash
288.62 183.25 11.01 14.25 or
301.94 183.25 32.81 14.25 puree.
337.19 183.68 13.42 13.82 Fill
353.05 183.68 16.50 13.82 hot
371.99 183.68 16.65 13.82 jars
391.08 183.68 21.34 13.82 with
414.86 183.68 28.35 13.82 cubes
445.65 183.68 18.41 13.82 and
466.50 183.68 38.80 13.82 cooking
63.00 197.48 29.66 13.82 liquid,
95.10 197.48 34.96 13.82 leaving
132.49 197.48 30.04 13.82 1-inch
164.97 197.48 53.90 13.82 headspace.
223.75 197.48 39.00 13.82 Remove
265.18 197.48 11.99 13.82 air
279.61 197.48 39.00 13.82 bubbles
321.05 197.48 18.41 13.82 and
341.90 197.48 29.52 13.82 adjust
373.86 197.48 51.66 13.82 headspace
427.95 197.48 6.05 13.82 if
436.44 197.48 38.90 13.82 needed.
477.37 197.48 24.54 13.82 Wipe
504.35 197.48 20.64 13.82 rims
63.00 211.28 9.67 13.82 of
75.11 211.28 16.65 13.82 jars
94.20 211.28 21.34 13.82 with
117.98 211.28 5.54 13.82 a
125.96 211.28 52.56 13.82 dampened
180.96 211.28 25.55 13.82 clean
208.95 211.28 28.15 13.82 paper
239.54 211.28 29.06 13.82 towel.
271.04 211.28 30.88 13.82 Adjust
304.35 211.28 16.44 13.82 lids
323.23 211.28 18.41 13.82 and
344.08 211.28 38.71 13.82 process.
63.00 238.88 15.32 13.82 For
80.76 238.88 36.23 13.82 making
119.42 238.88 21.80 13.82 pies,
143.66 238.88 24.81 13.82 drain
170.90 238.88 16.65 13.82 jars
189.99 238.88 18.41 13.82 and
210.84 238.88 26.68 13.82 strain
239.96 238.88 10.07 13.82 or
252.47 238.88 24.18 13.82 sieve
279.10 238.88 15.95 13.82 the
297.48 238.88 28.35 13.82 cubes
328.27 238.88 9.30 13.82 at
340.01 238.88 56.43 13.82 preparation
398.88 238.88 24.09 13.82 time.
116.00 259.85 257.52 9.97 Recommended\ufffdprocess\ufffdtime\ufffdfor\ufffdPumpkin\ufffdand\ufffdWinter\ufffdSquash\ufffdin\ufffda
"""
    PROCEDURE = ("Procedure: Wash, remove seeds, cut into 1-inch-wide slices, and peel. Cut flesh into 1-inch cubes. "
                 "Boil 2 minutes in water. Caution: Do not mash or puree. Fill hot jars with cubes and cooking liquid, "
                 "leaving 1-inch headspace. Remove air bubbles and adjust headspace if needed. Wipe rims of jars with "
                 "a dampened clean paper towel. Adjust lids and process.")

    @staticmethod
    def words(boxes):
        rows = ["level\tpage_num\tpar_num\tblock_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext"]
        for line in boxes.strip("\n").split("\n"):
            x, y, w, h, text = line.split(" ", 4)
            rows.append("\t".join(["5", "1", "0", "0", "0", "0", x, y, w, h, "100", text]))
        return practical.parse_tsv("\n".join(rows) + "\n")

    def read(self, layout=None, plain=None, boxes=None):
        entries, stats, layout_text = practical.entries_from_layout(
            {"id": "usda-canning-guide-4"}, [layout or self.LAYOUT], plain or self.PLAIN,
            self.words(self.BOXES if boxes is None else boxes))
        return entries, stats, layout_text

    def test_the_photo_label_is_found_by_its_type_and_place(self):
        labels = practical.figure_labels(self.words(self.BOXES))
        self.assertEqual([label["text"] for label in labels], ["Pumpkin and Winter Squash"])
        self.assertTrue(practical.label_names_entry("Pumpkin and Winter Squash", "PUMPKINS AND WINTER SQUASH\u2014CUBED"))
        self.assertFalse(practical.label_names_entry("Canning Guide Tables", "ASPARAGUS\u2014SPEARS OR PIECES"))

    def test_the_pumpkin_entry_is_recovered_whole(self):
        entries, stats, _ = self.read()
        self.assertEqual([e["heading"] for e in entries], ["Pumpkins And Winter Squash\u2014Cubed"])
        text = entries[0]["text"]
        self.assertIn("Caution: Do not mash or puree.", text)
        self.assertIn(self.PROCEDURE, text.split("\n\n"))
        self.assertNotIn("Pumpkin and", text)
        self.assertNotIn("Winter Squash", text)
        self.assertEqual(stats["entries_recovered"], [{"name": "PUMPKINS AND WINTER SQUASH\u2014CUBED",
                                                       "labels": ["Pumpkin and Winter Squash"]}])

    def test_the_recovered_entry_reads_word_for_word_in_the_source(self):
        entries, _, layout_text = self.read()
        text = entries[0]["text"]
        # The publisher's plain reading, word by word, with only the label's three lines taken out.
        source = [w for line in self.PLAIN.split("\n") if line not in ("Pumpkin and", "Winter", "Squash") for w in line.split()]
        recovered = text.split()
        start = source.index("PUMPKINS")
        self.assertEqual(recovered, source[start:start + len(recovered)])
        self.assertEqual(source[start + len(recovered)], "Recommended\ufffdprocess\ufffdtime\ufffdfor\ufffdPumpkin\ufffdand\ufffdWinter\ufffdSquash\ufffdin\ufffda")
        # And extract()'s proof against the layout reading, in which only the label is blanked.
        for paragraph in text.split("\n\n"):
            self.assertIn(practical.proof_key(paragraph), practical.proof_key(layout_text))

    def test_without_word_boxes_the_entry_stays_out(self):
        entries, stats, _ = practical.entries_from_layout({"id": "usda-canning-guide-4"}, [self.LAYOUT], self.PLAIN, [])
        self.assertEqual(entries, [])
        self.assertEqual(stats["entries_rejected"][0]["problems"][0], "second column on a line")

    def test_a_real_second_column_is_not_taken_out(self):
        # Positive control: the same stray words set in the running text's type on the running
        # text's line are a second column, not a photo label, and the entry stays out.
        boxes = "\n".join(line.replace(" 175.39 ", " 169.88 ").replace(" 10.92 ", " 13.82 ")
                          for line in self.BOXES.strip("\n").split("\n"))
        self.assertEqual(practical.figure_labels(self.words(boxes)), [])
        entries, stats, _ = self.read(boxes=boxes)
        self.assertEqual(entries, [])
        self.assertIn("a second column that is not this entry's photo label", stats["entries_rejected"][0]["problems"])
        # With the type-and-place test switched off, the same words would have been taken out.
        found = practical.figure_labels(self.words(self.BOXES))
        with mock.patch.object(practical, "figure_labels", lambda words: found):
            self.assertEqual(len(self.read(boxes=boxes)[0]), 1)

    def test_a_label_that_is_not_the_entry_name_is_not_taken_out(self):
        # Positive control: a photo label of another food in the same place stays a second column.
        swap = {"Pumpkin": "Peaches", "Winter": "Cherry", "Squash": "Grapes"}
        def rename(line):
            x, y, w, h, text = line.split(" ", 4)
            return " ".join([x, y, w, h, swap.get(text, text)])
        layout = self.LAYOUT.replace("Pumpkin and    seeds", "Peaches and    seeds").replace("Winter Squashinto", "Cherry Grapesinto")
        plain = self.PLAIN.replace("Pumpkin and\n", "Peaches and\n").replace("Winter\nSquash\n", "Cherry\nGrapes\n")
        boxes = "\n".join(rename(line) if line.split(" ")[3] == "10.92" else line for line in self.BOXES.strip("\n").split("\n"))
        self.assertEqual([label["text"] for label in practical.figure_labels(self.words(boxes))], ["Peaches and Cherry Grapes"])
        entries, stats, _ = self.read(layout, plain, boxes)
        self.assertEqual(entries, [])
        self.assertIn("a second column that is not this entry's photo label", stats["entries_rejected"][0]["problems"])
        # With the name test switched off, the other food's label would have been taken out.
        with mock.patch.object(practical, "label_names_entry", lambda label, name: True):
            self.assertEqual(len(self.read(layout, plain, boxes)[0]), 1)

    def test_the_plain_reading_must_agree(self):
        # Positive control for the second proof: a plain reading in which "remove" follows "seeds,
        # cut" disagrees with the layout's line. Without that proof the layout side alone would
        # take the entry (recover_entry returns its lines); with it the entry stays out.
        plain = self.PLAIN.replace("remove\nseeds, cut\n", "seeds, cut\nremove\n")
        lines = self.BOXES.strip("\n").split("\n")
        i = next(k for k, line in enumerate(lines) if line.endswith(" remove"))
        boxes = "\n".join(lines[:i] + lines[i + 1:i + 3] + [lines[i]] + lines[i + 3:])
        words = self.words(boxes)
        rows = self.LAYOUT.split("\n")[1:15]
        recovered, _, _, reason = practical.recover_entry(
            "PUMPKINS AND WINTER SQUASH\u2014CUBED", rows, [1] * len(rows), [[] for _ in rows],
            practical.figure_labels(words), set(plain.split()))
        self.assertEqual(reason, "photo label removed")
        self.assertIsNotNone(recovered)
        entries, stats, _ = self.read(plain=plain, boxes=boxes)
        self.assertEqual(entries, [])
        self.assertIn("photo label removed, but the text does not read word for word in the plain reading",
                      stats["entries_rejected"][0]["problems"])

    def test_a_recipe_cut_short_by_a_table_is_refused(self):
        # Pickled Sweet Green Tomatoes as it reached the 2026.11.09 build: the process table beside
        # the recipe ended it at the table's title, before its procedure.
        cut = ("PICKLED SWEET GREEN TOMATOES\n\n10 to 11 lbs of green tomatoes (16 cups sliced)\n\n2 cups sliced onions\n\n"
               "1/4 cup canning or pickling salt\n\n3 cups brown sugar\n\n4 cups vinegar (5%)\n\nYield: About 9 pints")
        self.assertEqual(practical.entry_problem(cut), "recipe without its procedure")
        self.assertEqual(practical.entry_problem("FIGS\n\nQuantity: An average of 16 pounds is needed per canner load of 7 quarts."),
                         "food entry without its procedure")
        entries, _, _ = self.read()
        self.assertIsNone(practical.entry_problem(entries[0]["text"]))
        self.assertIsNone(practical.entry_problem("PEPPERS\n\nPeppers range from mild to scorching in taste."))


CACHE = Path(os.environ.get("FIELDATLAS_PRACTICAL_CACHE", ROOT / "build/practical-skills/cache"))


@unittest.skipUnless((CACHE / "sources.lock.json").exists() and shutil.which("pdftotext"),
                     "set FIELDATLAS_PRACTICAL_CACHE to a fetched practical-skills cache (needs pdftotext)")
class PracticalSkillsGuideLabelTest(unittest.TestCase):
    """The locked USDA guides themselves: which entries come back once their photo label is out."""
    RECOVERED = {
        "usda-canning-guide-4": ["PUMPKINS AND WINTER SQUASH\u2014CUBED"],
        "usda-canning-guide-5": ["GROUND OR CHOPPED MEAT"],
        "usda-canning-guide-6": ["PICKLED DILLED BEANS", "CHAYOTE AND PEAR RELISH", "REDUCED-SODIUM SLICED DILL PICKLES"],
        "usda-canning-guide-7": ["PEAR-APPLE JAM"],
    }

    def test_entries_with_their_own_photo_label_come_back_proven(self):
        lock = json.loads((CACHE / "sources.lock.json").read_bytes())
        for source_id, names in self.RECOVERED.items():
            with self.subTest(source=source_id):
                raw = practical.read_cached(CACHE, lock, source_id)
                entries, stats, layout_text = practical.layout_entries({"id": source_id}, raw)
                self.assertEqual([r["name"] for r in stats["entries_recovered"]], names)
                reference = practical.proof_key(layout_text)
                for entry in entries:
                    for paragraph in entry["text"].split("\n\n"):
                        self.assertIn(practical.proof_key(paragraph), reference)
                if source_id == "usda-canning-guide-4":
                    pumpkin = next(e["text"] for e in entries if e["heading"].startswith("Pumpkins"))
                    self.assertIn("Caution: Do not mash or puree.", pumpkin)
                    self.assertIn(PracticalSkillsPhotoLabelTest.PROCEDURE, pumpkin.split("\n\n"))


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


def practical_section(markdown: str) -> str:
    start = markdown.index("\n## Practical skills")
    end = markdown.find("\n## ", start + 1)
    return markdown[start:] if end < 0 else markdown[start:end]


def missing_links(section: str, base: Path) -> tuple[list[str], int]:
    """Relative Markdown links in the section that name no file under base, and how many were read."""
    import re
    targets = [t.split("#")[0] for t in re.findall(r"\]\(([^)\s]+)\)", section)
               if not re.match(r"[a-z][a-z0-9+.-]*:|#", t)]
    return [t for t in targets if not (base / t).is_file()], len(targets)


class PracticalSkillsDatasetsLinkTest(unittest.TestCase):
    def test_every_relative_link_in_the_section_names_a_file(self):
        missing, read = missing_links(practical_section((ROOT / "DATASETS.md").read_text(encoding="utf-8")), ROOT)
        self.assertGreater(read, 0)
        self.assertEqual(missing, [])

    def test_a_link_relative_to_the_wrong_folder_is_found(self):
        section = "\n## Practical skills\n([questions](fieldtest/practical-skills.jsonl)) [ok](docs/fieldtest/practical-skills.jsonl)"
        self.assertEqual(missing_links(section, ROOT), (["fieldtest/practical-skills.jsonl"], 2))


if __name__ == "__main__":
    unittest.main()
