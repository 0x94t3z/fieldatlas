import importlib.util
from pathlib import Path
import sqlite3
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("source_audit", Path(__file__).resolve().parents[1] / "audit_research_sources.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class SourceAuditTests(unittest.TestCase):
    def test_real_fts_lookup_uses_title_index_and_still_checks_all_identity_fields(self):
        with sqlite3.connect(":memory:") as db:
            db.execute("CREATE VIRTUAL TABLE chunks_fts USING fts5(document_id UNINDEXED,chunk_id UNINDEXED,title,source UNINDEXED,text)")
            source = dict(documentId="d", chunkId="c", title='A "quoted" title', source="https://example.org", text="Original.")
            db.execute("INSERT INTO chunks_fts VALUES(?,?,?,?,?)", tuple(source.values()))
            statements = []
            db.set_trace_callback(statements.append)
            self.assertEqual({"chunkIds": ["c"], "transformation": "none"}, audit.original_mapping(db, source))
            self.assertTrue(any("chunks_fts MATCH" in s for s in statements))
            source["source"] = "https://different.example"
            self.assertIsNone(audit.original_mapping(db, source))

    def test_non_token_title_keeps_exact_lookup(self):
        with sqlite3.connect(":memory:") as db:
            db.execute("CREATE VIRTUAL TABLE chunks_fts USING fts5(document_id UNINDEXED,chunk_id UNINDEXED,title,source UNINDEXED,text)")
            source = dict(documentId="d", chunkId="c", title="😀", source="Local", text="Original.")
            db.execute("INSERT INTO chunks_fts VALUES(?,?,?,?,?)", tuple(source.values()))
            self.assertEqual("none", audit.original_mapping(db, source)["transformation"])
    def fixture(self, root, text="Original supported fact. A limitation follows."):
        path = root / "content.sqlite"
        with sqlite3.connect(path) as db:
            db.execute("CREATE TABLE chunks_fts(document_id,chunk_id,title,source,text)")
            db.execute("INSERT INTO chunks_fts VALUES(?,?,?,?,?)", ("d", "c", "Title", "https://example.org", text))
        source = dict(documentId="d", chunkId="c", title="Title", source="https://example.org", text=text)
        prompt = "policy\n\nEVIDENCE:\n[S1]\nTitle: Title\nSource: https://example.org\nExcerpt: " + text + "\n\nQUESTION:\nq"
        report = {"runs": [dict(status="COMPLETED_UNSCORED", question="q", sources=[source],
            visibleAnswer="Original supported fact. [S1]", calls=[{"request": {"messages": [{"content": prompt}]}}]) ]}
        return path, report

    def test_preserves_answer_and_original_passages_without_quality_claim(self):
        with tempfile.TemporaryDirectory() as folder:
            path, report = self.fixture(Path(folder))
            result = audit.audit(report, [path])
            self.assertTrue(result["sourceIntegrityPassed"])
            self.assertFalse(result["automaticAnswerQualityPass"])
            self.assertFalse(result["releaseReady"])
            row = result["runs"][0]
            self.assertEqual(report["runs"][0]["visibleAnswer"], row["visibleAnswer"])
            self.assertEqual([1], row["blocks"][0]["citationIds"])
            self.assertEqual("unreviewed", row["blocks"][0]["supportReview"])

    def test_mutated_title_source_id_or_text_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path, report = self.fixture(Path(folder))
            for key in ("documentId", "chunkId", "title", "source", "text"):
                original = report["runs"][0]["sources"][0][key]
                report["runs"][0]["sources"][0][key] = "tampered"
                with self.assertRaisesRegex(ValueError, "differs"):
                    audit.audit(report, [path])
                report["runs"][0]["sources"][0][key] = original

    def test_unmapped_and_zero_citations_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path, report = self.fixture(Path(folder))
            for marker in ("[S2]", "[S0]", "[999]", "[s999999999999999999999]"):
                report["runs"][0]["visibleAnswer"] = "Claim " + marker
                with self.assertRaisesRegex(ValueError, "unmapped"):
                    audit.audit(report, [path])

    def test_changed_excerpt_and_missing_prompt_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path, report = self.fixture(Path(folder))
            prompt = report["runs"][0]["calls"][0]["request"]["messages"][0]
            prompt["content"] = prompt["content"].replace("Excerpt: Original", "Excerpt: Invented")
            with self.assertRaisesRegex(ValueError, "unchanged"):
                audit.audit(report, [path])
            report["runs"][0]["calls"] = []
            with self.assertRaisesRegex(ValueError, "count"):
                audit.audit(report, [path])

    def test_no_sources_is_not_answer_quality_success(self):
        report = {"runs": [dict(status="COMPLETED_UNSCORED", question="q", sources=[], calls=[], visibleAnswer="No supporting sources.")]}
        result = audit.audit(report, [])
        self.assertFalse(result["automaticAnswerQualityPass"])
        self.assertEqual([], result["runs"][0]["blocks"][0]["citedPassages"])

    def test_deterministic_answer_requires_explicit_original_packing_record(self):
        with tempfile.TemporaryDirectory() as folder:
            path, report = self.fixture(Path(folder))
            row = report["runs"][0]
            row["packedPrompt"] = row["calls"][0]["request"]["messages"][0]["content"]
            row["calls"] = []
            result = audit.audit(report, [path])
            self.assertTrue(result["sourceIntegrityPassed"])
            self.assertFalse(result["automaticAnswerQualityPass"])
            row["packedPrompt"] = row["packedPrompt"].replace("Excerpt: Original", "Excerpt: Invented")
            with self.assertRaisesRegex(ValueError, "unchanged"):
                audit.audit(report, [path])

    def test_observer_record_cannot_hide_actual_model_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            path, report = self.fixture(Path(folder))
            row = report["runs"][0]
            row["packedPrompt"] = row["calls"][0]["request"]["messages"][0]["content"] + " altered"
            with self.assertRaisesRegex(ValueError, "actual inference"):
                audit.audit(report, [path])

    def test_prompt_metadata_cannot_point_to_a_different_source(self):
        with tempfile.TemporaryDirectory() as folder:
            path, report = self.fixture(Path(folder))
            message = report["runs"][0]["calls"][0]["request"]["messages"][0]
            message["content"] = message["content"].replace("Title: Title", "Title: Wrong title")
            with self.assertRaisesRegex(ValueError, "metadata"):
                audit.audit(report, [path])

    def test_metadata_bound_matches_android_utf16_without_split_surrogates(self):
        self.assertEqual("a" * 96, audit.compact_metadata("a" * 96))
        self.assertEqual("a" * 95 + "…", audit.compact_metadata("a" * 97))
        self.assertEqual("a" * 94 + "…", audit.compact_metadata("a" * 94 + "😀tail"))

    def test_only_cited_sources_are_attached_to_each_review_block(self):
        with tempfile.TemporaryDirectory() as folder:
            path, report = self.fixture(Path(folder))
            report["runs"][0]["visibleAnswer"] = "Claim [S1].\n\nAnother uncited claim."
            rows = audit.audit(report, [path])["runs"][0]["blocks"]
            self.assertEqual(1, len(rows[0]["citedPassages"]))
            self.assertEqual([], rows[1]["citedPassages"])

    def test_excerpt_keeps_order_and_removes_only_metadata_and_boundary_ellipses(self):
        self.assertTrue(audit.excerpt_from_original("… Fact. However, exception. …", "Intro.\nFact.\nHowever, exception.\nMeSH: keyword\nEnd."))
        self.assertFalse(audit.excerpt_from_original("Exception. Fact.", "Fact. Exception."))
        self.assertFalse(audit.excerpt_from_original("keyword", "Fact.\nMeSH: keyword"))
        self.assertFalse(audit.excerpt_from_original("… …", "Fact."))

    def test_partial_runs_are_not_accepted(self):
        for report in ({"runs": []}, {"runs": [{"status": "ERROR"}]}):
            with self.assertRaises(ValueError):
                audit.audit(report, [])

    def test_article_depth_reconstructs_only_consecutive_original_chunks(self):
        with tempfile.TemporaryDirectory() as folder:
            path, _ = self.fixture(Path(folder))
            source = dict(documentId="wp-1-0001", chunkId="wp-1-0001:0000", title="Details", source="https://example.org", text="First. Second.")
            with sqlite3.connect(path) as db:
                db.executemany("INSERT INTO chunks_fts VALUES(?,?,?,?,?)", [
                    (source["documentId"], source["chunkId"], source["title"], source["source"], "First."),
                    (source["documentId"], "wp-1-0001:0001", source["title"], source["source"], "Second.")])
                mapping = audit.original_mapping(db, source)
                self.assertEqual(["wp-1-0001:0000", "wp-1-0001:0001"], mapping["chunkIds"])
                source["text"] = "First. Invented."
                self.assertIsNone(audit.original_mapping(db, source))
                source["text"] = "Second. First."
                self.assertIsNone(audit.original_mapping(db, source))

    def test_article_alias_removal_is_not_general_source_rewriting(self):
        with tempfile.TemporaryDirectory() as folder:
            path, _ = self.fixture(Path(folder))
            source = dict(documentId="wp-1-0000", chunkId="wp-1-0000:0000", title="Overview", source="https://example.org", text="Original fact.")
            with sqlite3.connect(path) as db:
                db.execute("INSERT INTO chunks_fts VALUES(?,?,?,?,?)", (source["documentId"], source["chunkId"], source["title"], source["source"], "Original fact.\n\nAlso known as: Alias"))
                self.assertEqual("article-alias-line-removed", audit.original_mapping(db, source)["transformation"])
                source["text"] = "Changed fact."
                self.assertIsNone(audit.original_mapping(db, source))

    def test_lead_cut_requires_original_sentence_boundary_and_minimum_length(self):
        with tempfile.TemporaryDirectory() as folder:
            path, _ = self.fixture(Path(folder))
            prefix = "Original wording " * 15 + "."
            source = dict(documentId="wp-2-0000", chunkId="wp-2-0000:0000",
                title="Overview", source="https://example.org", text=prefix)
            with sqlite3.connect(path) as db:
                db.execute("INSERT INTO chunks_fts VALUES(?,?,?,?,?)", (
                    source["documentId"], source["chunkId"], source["title"], source["source"],
                    prefix + " More context follows."))
                self.assertEqual("article-lead-sentence-cut", audit.original_mapping(db, source)["transformation"])
                for cut in ("Original wording", prefix[:-1], prefix + " More context", "Invented sentence."):
                    source["text"] = cut
                    self.assertIsNone(audit.original_mapping(db, source))


if __name__ == "__main__":
    unittest.main()
