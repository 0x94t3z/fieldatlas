import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "run_claim_support.py"
spec = importlib.util.spec_from_file_location("claim_support", SCRIPT)
suite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(suite)


class ClaimSupportTests(unittest.TestCase):
    def test_model_override_changes_only_model_argument(self):
        cases = suite.load_cases(suite.FIXTURES)
        directory = Path("build/comparison")
        database = directory / "content.sqlite"
        default = suite.evaluation_command(directory, database, "app", cases)
        explicit = suite.evaluation_command(directory, database, "app", cases, Path("build/model.gguf"))
        index = explicit.index("--model")
        self.assertEqual(str(Path("build/model.gguf").resolve()), explicit[index + 1])
        self.assertEqual(default, explicit[:index] + explicit[index + 2:])
        self.assertNotIn("--model", default)

    def test_fixtures_cover_support_failures_not_just_valid_citation_numbers(self):
        cases = suite.load_cases(suite.FIXTURES)
        labels = {p["label"] for c in cases for p in c["probes"]}
        self.assertEqual({"supported", "unsupported", "contradicted", "invalid-citation"}, labels)
        wrong = next(c for c in cases if c["id"] == "wrong-source")
        self.assertEqual(wrong["probes"][0]["claim"], wrong["probes"][1]["claim"])
        self.assertNotEqual(wrong["probes"][0]["label"], wrong["probes"][1]["label"])

    def test_preparation_preserves_passages_and_never_overwrites(self):
        cases = suite.load_cases(suite.FIXTURES)
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / "run"
            database = suite.prepare(cases, directory)
            with sqlite3.connect(database) as db:
                actual = [r[0] for r in db.execute("SELECT text FROM chunks_fts ORDER BY rowid")]
                self.assertEqual(1, db.execute("PRAGMA user_version").fetchone()[0])
            self.assertEqual([p for c in cases for p in c["passages"]], actual)
            selected = json.loads((directory / "selection.json").read_text())
            self.assertEqual([], selected[next(c["question"] for c in cases if c["id"] == "no-evidence")])
            with self.assertRaises(FileExistsError):
                suite.prepare(cases, directory)

    def test_completion_and_missing_runs_never_become_semantic_passes(self):
        cases = suite.load_cases(suite.FIXTURES)
        sheet = suite.review_sheet(cases, {"runs": [{"question": cases[0]["question"], "status": "COMPLETED_UNSCORED"}]})
        self.assertEqual("MISSING", sheet["cases"][1]["executionStatus"])
        self.assertTrue(all(check["verdict"] == "unreviewed" for c in sheet["cases"] for check in c["checks"]))


if __name__ == "__main__":
    unittest.main()
