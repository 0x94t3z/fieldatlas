import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('reader_bench', ROOT / 'tools/evaluation/reader_bench.py')
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)

ITEM = dict(id='t-1', category='fact', question='When did Krakatoa erupt?', answerable='yes',
            passages=[dict(n=1, title='Krakatoa — Overview', text='The 1883 eruption killed 36,417 people.'),
                      dict(n=2, title='Java — Overview', text='Java is an island of Indonesia.')],
            keyFacts=[['1883']])


class ReaderBenchTests(unittest.TestCase):
    def test_a_cited_answer_from_the_passage_is_correct(self):
        row = tool.score_item(ITEM, 'Krakatoa erupted in 1883 and killed 36417 people [S1].')
        self.assertTrue(row['correct'])
        self.assertEqual([], row['unsupportedNumbers'])

    def test_a_figure_from_memory_or_a_missing_citation_fails(self):
        self.assertEqual(['1884'], tool.score_item(ITEM, 'It erupted in 1883, and again in 1884 [S1].')['unsupportedNumbers'])
        self.assertFalse(tool.score_item(ITEM, 'It erupted in 1883.')['correct'])
        self.assertTrue(tool.score_item(ITEM, 'It erupted in 1883 [S3].')['badCite'])

    def test_a_gap_must_be_admitted(self):
        gap = dict(ITEM, answerable='no', keyFacts=[])
        self.assertTrue(tool.score_item(gap, "The saved sources don't say.")['correct'])
        self.assertTrue(tool.score_item(gap, 'The passages do not mention the bakery.')['correct'])
        self.assertFalse(tool.score_item(gap, 'It opens at 7 am.')['correct'])
        self.assertTrue(tool.score_item(ITEM, "The saved sources don't say.")['abstained'])

    def test_every_published_item_is_well_formed(self):
        for path in [ROOT / 'docs/fieldtest/reader/dev.jsonl']:
            for line in path.read_text(encoding='utf-8').splitlines():
                item = json.loads(line)
                with self.subTest(item=item['id']):
                    numbers = [p['n'] for p in item['passages']]
                    self.assertEqual(list(range(1, len(numbers) + 1)), numbers)
                    self.assertLessEqual(len(numbers), 8)
                    self.assertIn(item['answerable'], ('yes', 'partial', 'no'))
                    self.assertTrue(set(item['cites']) <= set(numbers))
                    if item['answerable'] == 'no':
                        self.assertEqual([], item['keyFacts'])
                    else:
                        self.assertTrue(item['keyFacts'])
                    # The reference must pass its own scorer, or the item cannot be answered as written.
                    self.assertTrue(tool.score_item(item, item['reference'])['correct'], item['reference'])


if __name__ == '__main__':
    unittest.main()
