import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class BundledBenchmarkTest(unittest.TestCase):
    def test_canonical_benchmark_is_valid_for_scorer(self):
        from benchmarks.score_results import _validate_benchmark
        source = json.loads((ROOT / "benchmarks/questions.json").read_text(encoding="utf-8"))
        questions = _validate_benchmark(source)
        self.assertTrue(questions)
        self.assertEqual(len(questions), len({q["id"] for q in questions}))


if __name__ == "__main__":
    unittest.main()
