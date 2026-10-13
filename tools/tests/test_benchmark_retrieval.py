from pathlib import Path
import sys
import subprocess
import tempfile
import unittest
from contextlib import closing
import json
import sqlite3
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.evaluation.benchmark_retrieval import compact_cards, run, score


class RetrievalExperimentTest(unittest.TestCase):
    def test_command_line_runs_outside_the_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            tool = Path(__file__).resolve().parents[1] / "evaluation/benchmark_retrieval.py"
            result = subprocess.run([sys.executable, str(tool), "--help"], cwd=directory,
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("--leviathan", result.stdout)

    def test_compact_cards_keep_reference_identity_and_require_full_passages(self):
        passage = "A selected preview. " * 30 + "Important exception: this does not apply to children."
        card = compact_cards([("doc", "doc:0001", "Original title", "https://example.org/revision/123", passage, -2.0)])[0]
        self.assertEqual("doc:0001", card["chunkId"])
        self.assertEqual("https://example.org/revision/123", card["source"])
        self.assertEqual(passage[:300], card["preview"])
        self.assertTrue(card["requiresFullPassage"])
        self.assertNotIn("evidence", card)

    def test_missing_information_is_scored_separately_from_retrieval_hits(self):
        self.assertEqual(dict(expectedMissing=True, noResults=True), score([], []))
        self.assertEqual(dict(expectedMissing=True, noResults=False), score(["unrelated"], []))
        self.assertEqual(dict(hitAt1=False, hitAt5=True), score(["unrelated", "gold"], ["gold"]))
        self.assertEqual(dict(hitAt1=True, hitAt5=True), score(["gold"], ["gold"]))

    def test_gold_outside_the_actual_subset_is_not_scored_as_a_retrieval_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, cases = root / "source.sqlite", root / "cases.json"
            with closing(sqlite3.connect(source)) as database:
                database.execute("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id, document_id, title, source, text)")
                database.execute("INSERT INTO chunks_fts VALUES ('included:0000', 'included', 'Title', 'https://example.org', 'Original passage')")
                database.commit()
            cases.write_text(json.dumps([dict(id="sample", query="Title", goldChunkIds=["excluded:0000"])]))
            with patch("tools.evaluation.benchmark_retrieval.subprocess.run", return_value=SimpleNamespace(stdout="test engine version")) as command:
                with self.assertRaisesRegex(ValueError, "outside the frozen subset"):
                    run(source, cases, root / "experiment", root / "unused-engine", records=1)
            self.assertEqual(1, command.call_count)  # version only; no misleading index/results
            self.assertFalse((root / "experiment" / "comparison.json").exists())


if __name__ == "__main__":
    unittest.main()
