from __future__ import annotations

import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_general_reference as gr
from packtool.schema import Document


HTML = '''<div class="mw-parser-output"><p>Demo material is an invented test fixture.</p>
<div class="hatnote">Navigation noise</div><h2>Mechanism</h2>
<p>Heat moves between objects.<sup class="reference">[1]</sup> It is not created.</p>
<h3>Limitations</h3><p>This statement has a qualification.</p>
<table><tr><td>Table not represented</td></tr></table>
<p>Formula <math>x</math> must not become broken prose.</p>
<h2>References</h2><ul><li>Citation noise</li></ul>
<h3>Books</h3><p>More bibliography noise</p></div>'''


class GeneralReferenceTest(unittest.TestCase):
    def test_distinct_pack_identity_preserves_pilot_defaults(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, _ = self.fixture(root)
            gr.build(topics, cache, root / "new", "0.1.0", True,
                     "everyday-reference", "Everyday reference · Preview")
            manifest = json.loads((root / "new/pack/manifest.json").read_text())
            self.assertEqual("everyday-reference", manifest["id"])
            self.assertEqual("Everyday reference · Preview", manifest["title"])
            self.assertEqual(topics.read_bytes(), (root / "new/topics.json").read_bytes())
            self.assertIn("Wikipedia contributors", (root / "new/ATTRIBUTION.txt").read_text())
            with self.assertRaisesRegex(ValueError, "identity"):
                gr.build(topics, cache, root / "invalid", "0.1.0", pack_id="../escape")
            self.assertFalse((root / "invalid").exists())

    def test_seed_preserves_pinned_revisions_and_leaves_additions_missing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, row = self.fixture(root)
            topics.write_bytes(gr.canonical({"topics": {"test": ["Demo", "New"]}}))
            target = root / "expanded"
            gr.seed_snapshot(topics, target, cache)
            lock = json.loads((target / "sources.lock.json").read_bytes())
            self.assertEqual([row], lock["sources"])
            self.assertEqual(gr.digest(topics.read_bytes()), lock["topics_sha256"])
            self.assertEqual((cache / row["file"]).read_bytes(), (target / row["file"]).read_bytes())
            with self.assertRaisesRegex(ValueError, "already exists"):
                gr.seed_snapshot(topics, target, cache)

    def test_seed_rejects_corruption_before_creating_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, row = self.fixture(root)
            (cache / row["file"]).write_text("corrupt")
            target = root / "expanded"
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                gr.seed_snapshot(topics, target, cache)
            self.assertFalse(target.exists())

    def test_paragraph_chunks_are_lossless_and_never_start_midword(self):
        text = "Long opening sentence " * 90 + ".\n\nHowever, this is only one trial.\n\nA different independent paragraph."
        doc = Document("demo", "Title", "Local", "CC0", text)
        chunks = gr.paragraph_chunks(doc)
        self.assertEqual(text, "\n\n".join(c.text for c in chunks))
        self.assertIn("However, this is only one trial.", chunks[0].text)
        self.assertEqual("A different independent paragraph.", chunks[1].text)
        self.assertEqual(["demo:0000", "demo:0001"], [c.chunk_id for c in chunks])

    def test_candidate_build_is_reproducible_without_changing_default_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, _ = self.fixture(root)
            for name in ("one", "two"):
                gr.build(topics, cache, root / name, "0.2.0", preserve_paragraphs=True)
            self.assertEqual(next((root / "one/pack").glob("*.fapack")).read_bytes(),
                             next((root / "two/pack").glob("*.fapack")).read_bytes())
            report = json.loads((root / "one/quality-report.json").read_text())
            self.assertEqual("whole-paragraph-v1", report["chunk_policy"])

    def fixture(self, root):
        topics = root / "topics.json"
        topics.write_bytes(gr.canonical({"topics": {"test": ["Demo"]}}))
        cache = root / "snapshot"
        cache.mkdir()
        raw = gr.canonical({"revid": 2, "text": HTML})
        (cache / "1-2.json").write_bytes(raw)
        row = {"requested": "Demo", "category": "test", "title": "Demo", "pageid": 1,
               "revision": 2, "timestamp": "2026-01-01T00:00:00Z", "file": "1-2.json",
               "sha256": gr.digest(raw), "license": gr.LICENSE,
               "url": "https://en.wikipedia.org/w/index.php?oldid=2"}
        lock = {"schema": 1, "topics_sha256": gr.digest(topics.read_bytes()), "sources": [row]}
        (cache / "sources.lock.json").write_bytes(gr.canonical(lock))
        return topics, cache, row

    def test_sections_preserve_hierarchy_and_qualifications(self):
        sections, removed = gr.sections(HTML)
        self.assertEqual(["Overview", "Mechanism", "Mechanism > Limitations"], [x[0] for x in sections])
        self.assertEqual("Heat moves between objects. It is not created.", sections[1][1])
        self.assertIn("qualification", sections[2][1])
        self.assertEqual({"tables": 1, "math": 1}, removed)
        self.assertNotIn("noise", str(sections))
        self.assertNotIn("broken prose", str(sections))

    def test_reject_missing_article_body(self):
        with self.assertRaisesRegex(ValueError, "article body"):
            gr.sections("<html>Service unavailable</html>")

    def test_offline_build_is_deterministic_and_searchable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, _ = self.fixture(root)
            with patch.object(gr, "request", side_effect=AssertionError("Build must stay offline")):
                gr.build(topics, cache, root / "one", "0.1.0")
                gr.build(topics, cache, root / "two", "0.1.0")
            first = next((root / "one/pack").glob("*.fapack"))
            second = next((root / "two/pack").glob("*.fapack"))
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with sqlite3.connect(root / "one/pack/content.sqlite") as db:
                rows = db.execute("SELECT title, source, text FROM chunks_fts WHERE chunks_fts MATCH ?", ('"heat"',)).fetchall()
            self.assertEqual(1, len(rows))
            self.assertEqual("Demo — Mechanism", rows[0][0])
            self.assertIn("oldid=2", rows[0][1])
            self.assertIn("not created", rows[0][2])
            report = json.loads((root / "one/quality-report.json").read_bytes())
            self.assertEqual("BUILT_NOT_ANSWER_VALIDATED", report["status"])
            self.assertIn("Wikipedia contributors", (root / "one/ATTRIBUTION.txt").read_text())

    def test_corrupt_snapshot_rejected_before_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, row = self.fixture(root)
            (cache / row["file"]).write_text("corrupt")
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                gr.build(topics, cache, root / "output", "0.1.0")
            self.assertFalse((root / "output").exists())

    def test_missing_source_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, _ = self.fixture(root)
            lock_file = cache / "sources.lock.json"
            lock = json.loads(lock_file.read_bytes())
            lock["sources"] = []
            lock_file.write_bytes(gr.canonical(lock))
            with self.assertRaisesRegex(ValueError, "Incomplete"):
                gr.build(topics, cache, root / "output", "0.1.0")

    def test_duplicate_topics_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "topics.json"
            path.write_bytes(gr.canonical({"topics": {"test": ["Demo", "demo"]}}))
            with self.assertRaisesRegex(ValueError, "Duplicate"):
                gr.topic_entries(path)

    def test_completed_fetch_reuses_verified_snapshot_without_network(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, _ = self.fixture(root)
            with patch.object(gr, "request", side_effect=AssertionError("Unexpected refetch")):
                self.assertEqual(1, len(gr.fetch(topics, cache)["sources"]))

    def test_mismatched_revision_is_rejected_even_with_matching_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            _, cache, row = self.fixture(Path(directory))
            row["revision"] = 3
            with self.assertRaisesRegex(ValueError, "revision"):
                gr.read_snapshot(cache, row)

    def test_build_refuses_existing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            topics, cache, _ = self.fixture(root)
            out = root / "out"
            out.mkdir()
            marker = out / "user-data.txt"
            marker.write_text("preserve")
            with self.assertRaisesRegex(ValueError, "already exists"):
                gr.build(topics, cache, out, "0.1.0")
            self.assertEqual("preserve", marker.read_text())

    def test_cache_path_traversal_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            gr.read_snapshot(Path("."), {"file": "../secrets.json"})


if __name__ == "__main__":
    unittest.main()
