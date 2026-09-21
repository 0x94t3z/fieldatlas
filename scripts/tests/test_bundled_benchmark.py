import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class BundledBenchmarkTest(unittest.TestCase):
    def test_android_benchmark_copy_matches_frozen_source(self):
        source = json.loads((ROOT / "benchmarks/questions-v1.json").read_text(encoding="utf-8"))
        bundled = json.loads(
            (ROOT / "app/src/main/assets/benchmark/questions-v1.json").read_text(encoding="utf-8")
        )
        self.assertEqual(source, bundled)


if __name__ == "__main__":
    unittest.main()
