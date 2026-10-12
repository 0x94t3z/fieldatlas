import bz2
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sqlite3
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_wikipedia_shard as tool

URL = "https://dumps.wikimedia.org/other/cirrus_search_index/20261004/index_name%3Denwiki_content/enwiki_content-20261004-00000.json.bz2"


class WikipediaShardTest(unittest.TestCase):
    def article(self, page=1, **changes):
        return dict(page_id=page, namespace=0, wiki="enwiki", title="A subject", version=123,
                    text="A full original passage. It does NOT apply at lower pressure.\nAnother qualification.",
                    template=[], category=[], **changes)

    def write_source(self, root, rows):
        data = b"".join((json.dumps(row, ensure_ascii=False) + "\n").encode() for row in rows)
        source = root / "source.bz2"
        source.write_bytes(bz2.compress(data))
        return source

    def pairs(self, articles):
        return [row for article in articles for row in ({"index": {"_id": article["page_id"]}}, article)]

    def convert(self, root, rows=None, output="corpus", **options):
        source = self.write_source(root, rows if rows is not None else self.pairs([self.article()]))
        return tool.convert(source, root / output, hashlib.sha256(source.read_bytes()).hexdigest(),
                            source.stat().st_size, URL, **options)

    def test_exact_complete_text_oldid_and_no_auxiliary_duplication(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            article = self.article()
            article.update(text="  Complete naïve prose, NOT safe without water.\n\nFinal exception.  ",
                           source_text="Do not index wikitext", auxiliary_text="Do not index templates",
                           heading=["Fake inferred section"])
            report = self.convert(root, self.pairs([article]))
            rows = list(tool.records(root / "corpus/documents.jsonl"))
            self.assertEqual(1, len(rows))
            self.assertEqual(article["text"], rows[0]["text"])
            self.assertEqual("https://en.wikipedia.org/w/index.php?oldid=123", rows[0]["source"])
            self.assertEqual("wp-1-0000", rows[0]["document_id"])
            self.assertFalse(report["releaseReady"])
            self.assertFalse(report["accuracyAccepted"])
            self.assertFalse(report["publisherAuthenticatedChecksum"])
            self.assertFalse(report["sectionOffsetsAvailable"])
            self.assertEqual(["SOURCE.json", "documents.jsonl"], sorted(p.name for p in (root / "corpus").iterdir()))

    def test_reproducible_output_with_different_local_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertEqual(self.convert(root, output="one"), self.convert(root, output="two"))
            self.assertEqual((root / "one/documents.jsonl").read_bytes(), (root / "two/documents.jsonl").read_bytes())

    def test_excludes_existing_vital_and_completed_new_corpora(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            spool = root / "vital.jsonl"
            spool.write_text(json.dumps(dict(pageid=1)) + "\n" + json.dumps(dict(missing=True)) + "\n")
            docs = root / "previous.jsonl"
            docs.write_text(json.dumps(dict(document_id="wp-2-0000", source="https://en.wikipedia.org/w/index.php?oldid=234")) + "\n")
            report = self.convert(root, self.pairs([self.article(n) for n in (1, 2, 3)]),
                                  exclude_spools=[spool], exclude_documents=[docs])
            self.assertEqual(2, report["counts"]["excludedArticles"])
            self.assertEqual("wp-3-0000", next(tool.records(root / "corpus/documents.jsonl"))["document_id"])
            self.assertTrue(spool.exists())
            self.assertTrue(docs.exists())

    def test_gzip_exclusions_match_plain_without_expanded_temporary_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            spool = root / "vital.jsonl.gz"
            docs = root / "previous.jsonl.gz"
            spool.write_bytes(gzip.compress(b'{"pageid":1}\n', mtime=0))
            docs.write_bytes(gzip.compress(
                b'{"document_id":"wp-2-0000","source":"https://en.wikipedia.org/w/index.php?oldid=234"}\n',
                mtime=0))
            pins = {path: path.read_bytes() for path in (spool, docs)}
            report = self.convert(root, self.pairs([self.article(n) for n in (1, 2, 3)]),
                                  exclude_spools=[spool], exclude_documents=[docs])
            self.assertEqual(2, report["counts"]["excludedArticles"])
            self.assertEqual("wp-3-0000", next(tool.records(root / "corpus/documents.jsonl"))["document_id"])
            self.assertEqual(["jsonl-gzip", "jsonl-gzip"], [p["format"] for p in report["exclusions"]])
            for path, content in pins.items():
                self.assertEqual(content, path.read_bytes())
                self.assertFalse(path.with_suffix("").exists())

    def test_gzip_exclusion_corruption_discards_only_owned_stage(self):
        for content in (b"not gzip", gzip.compress(b'{"pageid":1}\n', mtime=0)[:-4],
                        gzip.compress(b'{"pageid":1}', mtime=0)):
            with self.subTest(content=content), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                spool = root / "vital.jsonl.gz"
                spool.write_bytes(content)
                with self.assertRaises((OSError, EOFError, ValueError)):
                    self.convert(root, exclude_spools=[spool])
                self.assertEqual(["source.bz2", "vital.jsonl.gz"], sorted(p.name for p in root.iterdir()))
                self.assertEqual(content, spool.read_bytes())

    def test_exclusions_have_expanded_and_record_limits_for_plain_and_gzip(self):
        for compressed in (False, True):
            with self.subTest(compressed=compressed), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / ("records.jsonl.gz" if compressed else "records.jsonl")
                content = b'{"pageid":1}\n' * 3
                path.write_bytes(gzip.compress(content, mtime=0) if compressed else content)
                with self.assertRaisesRegex(ValueError, "Expanded exclusion"):
                    list(tool.records(path, max_expanded_bytes=len(content) - 1))
                with patch.object(tool, "MAX_RECORD", 4):
                    with self.assertRaisesRegex(ValueError, "Oversized"):
                        list(tool.records(path))

    def test_gzip_exclusion_crc_failure_is_not_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            spool = root / "vital.jsonl.gz"
            content = bytearray(gzip.compress(b'{"pageid":1}\n', mtime=0))
            content[-8] ^= 1
            spool.write_bytes(content)
            with self.assertRaises(OSError):
                self.convert(root, exclude_spools=[spool])
            self.assertFalse((root / "corpus").exists())

    def test_filters_redirect_namespace_disambiguation_and_empty_whole_articles(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            articles = [self.article(n) for n in range(1, 7)]
            articles[0]["namespace"] = 1
            articles[1]["redirect_target"] = "Destination"
            articles[2]["title"] = "Term (disambiguation)"
            articles[3]["category"] = ["All disambiguation pages"]
            articles[4]["text"] = "  "
            counts = self.convert(root, self.pairs(articles))["counts"]
            self.assertEqual(1, counts["keptArticles"])
            self.assertEqual(2, counts["nonContentRecords"])
            self.assertEqual(2, counts["disambiguationArticles"])
            self.assertEqual(1, counts["emptyArticles"])

    def failure(self, rows, message, **options):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex((ValueError, OSError, EOFError), message):
                self.convert(root, rows, **options)
            self.assertEqual(["source.bz2"], sorted(p.name for p in root.iterdir()))

    def test_duplicate_source_page_fails_instead_of_inflating_knowledge(self):
        self.failure(self.pairs([self.article(), self.article()]), "Duplicate source page")

    def test_identity_pair_mismatch_and_incomplete_pair_fail(self):
        self.failure([{"index": {"_id": 2}}, self.article()], "identity mismatch")
        self.failure([self.article()], "identity mismatch")
        self.failure(self.pairs([self.article()]) + [{"index": {"_id": 2}}], "Incomplete")
        self.failure([{"index": {"_id": 1}}, {"index": {"_id": 2}}], "pairing")

    def test_other_wiki_and_bad_revision_fail(self):
        article = self.article()
        article["wiki"] = "simplewiki"
        self.failure(self.pairs([article]), "different wiki")
        article = self.article()
        article["version"] = True
        self.failure(self.pairs([article]), "revision")

    def test_byte_limits_fail_atomically_not_silent_truncation(self):
        self.failure(self.pairs([self.article()]), "corpus exceeds", max_output_bytes=1)
        self.failure(self.pairs([self.article()]), "Expanded source", max_expanded_bytes=1)

    def test_checksum_failure_never_creates_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = self.write_source(root, self.pairs([self.article()]))
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                tool.convert(source, root / "corpus", "0" * 64, source.stat().st_size, URL)
            self.assertEqual(["source.bz2"], sorted(p.name for p in root.iterdir()))

    def test_source_changed_during_conversion_discards_only_own_stage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original_digest = tool._digest
            calls = 0
            def changing_digest(path):
                nonlocal calls
                if Path(path).name == "source.bz2":
                    calls += 1
                    if calls == 2:
                        return 1, "0" * 64
                return original_digest(path)
            with patch.object(tool, "_digest", side_effect=changing_digest):
                with self.assertRaisesRegex(ValueError, "Source changed"):
                    self.convert(root)
            self.assertEqual(["source.bz2"], sorted(p.name for p in root.iterdir()))

    def test_converter_changed_during_conversion_discards_own_stage(self):
        with patch.object(tool, "builder_identity", side_effect=[{"prepare_wikipedia_shard.py": "one"},
                                                                  {"prepare_wikipedia_shard.py": "two"}]):
            self.failure(self.pairs([self.article()]), "builder changed")

    def test_existing_output_and_symlink_are_never_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sentinel = root / "corpus"
            sentinel.mkdir()
            (sentinel / "keep").write_text("Owner file")
            with self.assertRaisesRegex(ValueError, "already exists"):
                self.convert(root)
            self.assertEqual("Owner file", (sentinel / "keep").read_text())
            (root / "link").symlink_to(sentinel, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, "already exists"):
                self.convert(root, output="link")

    def test_low_space_fails_before_output(self):
        with patch.object(tool, "require_build_space", side_effect=ValueError("Insufficient build disk space")):
            self.failure(self.pairs([self.article()]), "Insufficient")

    def test_duplicate_json_keys_and_oversized_records_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Duplicate JSON field"):
            tool.strict_json(b'{"title":"A","title":"B"}')
        with patch.object(tool, "MAX_RECORD", 20):
            self.failure(self.pairs([self.article()]), "Oversized")

    def test_corrupt_bzip_data_does_not_leave_valid_looking_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.bz2"
            source.write_bytes(b"not bz2")
            with self.assertRaises(OSError):
                tool.convert(source, root / "corpus", hashlib.sha256(source.read_bytes()).hexdigest(),
                             source.stat().st_size, URL)
            self.assertEqual(["source.bz2"], sorted(p.name for p in root.iterdir()))

    def test_snapshot_mismatch_and_nonofficial_url_are_rejected(self):
        for bad in (URL.replace("20261004-00000", "20260927-00000"), URL + "?secret=1",
                    URL.replace("https:", "http:"), URL.replace("dumps.wikimedia.org", "example.org")):
            with self.assertRaisesRegex(ValueError, "official"):
                tool.snapshot_url(bad)

    def test_cli_help_from_outside_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, str(Path(tool.__file__)), "--help"], cwd=directory,
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("--exclude-documents", result.stdout)

    def test_new_format_corpus_builds_a_real_queryable_pack(self):
        from build_library_batch import build_batch
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = self.convert(root)
            built = build_batch(root / "corpus/documents.jsonl", root / "library",
                                report["documentsSha256"], "reference-shard", "2026.10.04",
                                "Reference shard", tool.LICENSE, report["sourceUrls"], 1000, 1)
            self.assertEqual(1, len(built["modules"]))
            module = built["modules"][0]
            self.assertEqual("general-keyword", module["retrievalMode"])
            with zipfile.ZipFile(root / "library" / module["archive"]) as archive:
                (root / "payload.sqlite").write_bytes(archive.read("content.sqlite"))
            with sqlite3.connect(root / "payload.sqlite") as db:
                row = db.execute("SELECT document_id,title,source,text FROM chunks_fts "
                                 "WHERE chunks_fts MATCH ?", ('"lower" AND "pressure"',)).fetchone()
                self.assertEqual("wp-1-0000", row[0])
                self.assertIn("NOT apply", row[3])
                self.assertEqual("https://en.wikipedia.org/w/index.php?oldid=123", row[2])

    def test_invalid_category_and_namespace_metadata_fail_closed(self):
        for field, value in (("category", [123]), ("namespace", True)):
            article = self.article()
            article[field] = value
            self.failure(self.pairs([article]), "Invalid")

    def test_shared_disambiguation_dependencies_do_not_drop_ordinary_articles(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            article = self.article()
            article["template"] = ["Module:Pagetype/disambiguation", "Module:Disambiguation/templates",
                                   "Template:Disambiguation needed"]
            article["category"] = ["Articles with hatnote templates targeting a nonexistent page",
                                   "Articles needing disambiguation from January 2026"]
            report = self.convert(root, self.pairs([article]))
            self.assertEqual(1, report["counts"]["keptArticles"])
            self.assertEqual(0, report["counts"]["disambiguationArticles"])

    def test_set_index_articles_are_excluded_by_exact_tracking_category(self):
        article = self.article()
        article["category"] = ["All set index articles"]
        self.failure(self.pairs([article]), "No new articles")

    def test_partial_json_line_is_not_silently_skipped(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.bz2"
            raw = (json.dumps({"index": {"_id": 1}}) + "\n" + json.dumps(self.article())).encode()
            source.write_bytes(bz2.compress(raw))
            with self.assertRaisesRegex(ValueError, "incomplete source record"):
                tool.convert(source, root / "corpus", hashlib.sha256(source.read_bytes()).hexdigest(),
                             source.stat().st_size, URL)
            self.assertFalse((root / "corpus").exists())


if __name__ == "__main__":
    unittest.main()
