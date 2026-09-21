import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import zipfile

from packtool.build_pack import build_pack


class BuildPackTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.input = self.root / "documents.jsonl"
        rows = [
            {"document_id": "water", "title": "Water", "source": "Project fixture", "license": "CC0-1.0", "text": "Boiling inactivates many pathogens but does not remove dissolved metals."},
            {"document_id": "solar", "title": "Solar", "source": "Project fixture", "license": "CC0-1.0", "text": "Battery storage shifts solar energy into evening demand."},
        ]
        self.input.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")

    def tearDown(self):
        self.temporary.cleanup()

    def test_database_schema_search_and_pack_layout(self):
        output = self.root / "one"
        artifacts = build_pack(self.input, output, "starter", "1.0.0", "Starter", "CC0-1.0", ["https://example.test"])
        database = sqlite3.connect(output / "content.sqlite")
        try:
            columns = [row[1] for row in database.execute("PRAGMA table_info(chunks_fts)")]
            self.assertEqual(["chunk_id", "document_id", "title", "source", "text"], columns)
            self.assertEqual(1, database.execute("PRAGMA user_version").fetchone()[0])
            result = database.execute("SELECT document_id FROM chunks_fts WHERE chunks_fts MATCH 'pathogens'").fetchone()
            self.assertEqual(("water",), result)
            self.assertEqual(("ok",), database.execute("PRAGMA quick_check").fetchone())
        finally:
            database.close()
        with zipfile.ZipFile(artifacts.pack) as archive:
            entries = archive.infolist()
            self.assertEqual(["manifest.json", "content.sqlite"], [entry.filename for entry in entries])
            self.assertTrue(all(entry.compress_type == zipfile.ZIP_STORED for entry in entries))
            self.assertTrue(all(entry.date_time == (1980, 1, 1, 0, 0, 0) for entry in entries))
            manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual("KNOWLEDGE", manifest["type"])
        sums = (output / "SHA256SUMS").read_text(encoding="ascii")
        self.assertIn(hashlib.sha256((output / "content.sqlite").read_bytes()).hexdigest(), sums)

    def test_two_builds_are_byte_identical_without_paths_or_timestamps(self):
        first = build_pack(self.input, self.root / "first", "starter", "1.0.0", "Starter", "CC0-1.0", ["https://example.test"])
        second = build_pack(self.input, self.root / "second", "starter", "1.0.0", "Starter", "CC0-1.0", ["https://example.test"])
        self.assertEqual(first.database.read_bytes(), second.database.read_bytes())
        self.assertEqual(first.pack.read_bytes(), second.pack.read_bytes())
        pack_bytes = first.pack.read_bytes()
        self.assertNotIn(str(self.root).encode(), pack_bytes)
        self.assertNotIn(str(Path.cwd()).encode(), pack_bytes)

    def test_manifest_contains_canonical_discovery_metadata(self):
        artifacts = build_pack(
            self.input,
            self.root / "discovery",
            "starter",
            "1.0.0",
            "Starter",
            "CC0-1.0",
            ["https://example.test"],
            coverage_summary="Four demo notes",
            example_questions=["Why do seasons change?"],
            coverage_level="demo",
        )
        manifest = json.loads(artifacts.manifest.read_text())
        self.assertEqual("demo", manifest["discovery"]["coverageLevel"])
        self.assertEqual(
            ["Why do seasons change?"],
            manifest["discovery"]["exampleQuestions"],
        )

    def test_unsafe_pack_id_is_rejected_without_writing_outside_output(self):
        with self.assertRaisesRegex(ValueError, "safe pack identifier"):
            build_pack(
                self.input, self.root / "output", "../escape", "1.0.0", "Starter", "CC0-1.0",
                ["https://example.test"],
            )
        self.assertFalse((self.root / "escape-1.0.0.fapack").exists())


if __name__ == "__main__":
    unittest.main()
