import bz2
import hashlib
import importlib.util
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.packs import audit_library_batch as audit
from tools.packs import build_library_batch as builder
from tools.packs import prepare_wikipedia_shard as converter


class LibraryAuditTests(unittest.TestCase):
    def prepare(self, root, complete=True, raw=False):
        if raw:
            records = []
            for number in (9, 2):
                records.extend([{"index": {"_id": number}}, dict(page_id=number,
                    wiki="enwiki", namespace=0, title=f"Topic {number}", version=100 + number,
                    category=[], text=("Original café article with a qualification. " * 45))])
            raw_path = root / "raw.json.bz2"
            raw_path.write_bytes(bz2.compress(b"".join((json.dumps(row) + "\n").encode() for row in records)))
            corpus = root / "corpus"
            provenance = converter.convert(raw_path, corpus, audit._digest(raw_path)[1],
                raw_path.stat().st_size,
                "https://dumps.wikimedia.org/other/cirrus_search_index/20261004/index_name%3Denwiki_content/enwiki_content-20261004-00001.json.bz2")
            source = corpus / "documents.jsonl"
            urls, license_id = provenance["sourceUrls"], "CC-BY-SA-4.0"
        else:
            source = root / "documents.jsonl"
            rows = [dict(document_id=f"article-{number}", title=f"Topic {number}",
                source=f"https://example.org/{number}?oldid=100", license="CC0-1.0",
                text="Original café text. The qualification is retained. " * 45) for number in (9, 2)]
            source.write_text("".join(json.dumps(row) + "\n" for row in rows))
            urls, license_id = ["https://example.org/snapshot/100"], "CC0-1.0"
        library = root / "library"
        builder.build_batch(source, library, audit._digest(source)[1], "audit-test", "1.0.0",
            "Audit test", license_id, urls, module_text_bytes=1000,
            max_modules=10 if complete else 1)
        return dict(library_dir=library, source_path=source, output_path=root / "audit.json")

    def tamper_payload(self, args, sql):
        library = args["library_dir"]
        progress_path = library / "batch-progress.json"
        progress = json.loads(progress_path.read_text())
        module = progress["modules"][0]
        path = library / module["archive"]
        database = library / "scratch-test.sqlite"
        with zipfile.ZipFile(path) as archive:
            manifest = json.loads(archive.read("manifest.json"))
            database.write_bytes(archive.read("content.sqlite"))
        with sqlite3.connect(database) as db:
            db.execute(sql)
            db.commit()
        payload_bytes, payload_hash = audit._digest(database)
        manifest["artifacts"][0]["bytes"] = payload_bytes
        manifest["artifacts"][0]["sha256"] = payload_hash
        manifest_bytes = json.dumps(manifest).encode()
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("manifest.json", manifest_bytes)
            archive.write(database, "content.sqlite")
        database.unlink()
        (path.parent / "manifest.json").write_bytes(manifest_bytes)
        module["archiveBytes"], module["sha256"] = audit._digest(path)
        module["installedBytes"], module["databaseSha256"] = payload_bytes, payload_hash
        receipt_path = path.parent / "module-build.json"
        receipt = json.loads(receipt_path.read_text())
        receipt["module"] = module
        receipt_path.write_text(json.dumps(receipt))
        sums = f"{audit._digest(path.parent / 'manifest.json')[1]}  manifest.json\n{module['sha256']}  {path.name}\n"
        (path.parent / "SHA256SUMS").write_text(sums)
        progress["installedBytes"] = sum(item["installedBytes"] for item in progress["modules"])
        progress["archiveBytes"] = sum(item["archiveBytes"] for item in progress["modules"])
        progress_path.write_text(json.dumps(progress))

    def test_complete_batch_reconstructs_every_original_and_keeps_payloads_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            report = audit.audit_batch(**args)
            self.assertTrue(report["mechanicalSourceIntegrityPassed"])
            self.assertEqual(2, report["documentsChecked"])
            self.assertGreater(report["chunksChecked"], 2)
            self.assertFalse(report["fullRawSourceMappingPassed"])
            self.assertFalse(report["semanticAccuracyAccepted"])
            self.assertFalse(list(args["library_dir"].glob(".module-audit-*")))

    def test_incomplete_batch_cannot_be_reported_as_audited(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory), complete=False)
            with self.assertRaisesRegex(ValueError, "complete batch"):
                audit.audit_batch(**args)
            self.assertFalse(args["output_path"].exists())

    def test_existing_report_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            args["output_path"].write_text("owner data")
            with self.assertRaisesRegex(ValueError, "already exists"):
                audit.audit_batch(**args)
            self.assertEqual("owner data", args["output_path"].read_text())

    def test_tampered_source_index_and_extra_module_directory_fail(self):
        for change in ("source", "index", "extra"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                args = self.prepare(Path(directory))
                if change == "extra":
                    (args["library_dir"] / "modules/unknown").mkdir()
                else:
                    path = args["source_path"] if change == "source" else args["library_dir"] / "offsets.sqlite"
                    with path.open("ab") as out:
                        out.write(b"changed")
                with self.assertRaises(ValueError):
                    audit.audit_batch(**args)
                self.assertFalse(args["output_path"].exists())

    def test_rehashed_changed_text_url_title_missing_and_extra_chunks_fail(self):
        statements = [
            "UPDATE chunks_fts SET text='changed original' WHERE rowid=1",
            "UPDATE chunks_fts SET source='https://wrong.example/source' WHERE rowid=1",
            "UPDATE chunks_fts SET title='Wrong title' WHERE rowid=1",
            "DELETE FROM chunks_fts WHERE rowid=1",
            "INSERT INTO chunks_fts(chunk_id,document_id,title,source,text) VALUES('extra:0000','extra','Extra','local','Extra')",
            "UPDATE chunks_fts SET chunk_id='wrong:0000' WHERE rowid=1",
        ]
        for sql in statements:
            with self.subTest(sql=sql), tempfile.TemporaryDirectory() as directory:
                args = self.prepare(Path(directory))
                self.tamper_payload(args, sql)
                with self.assertRaises(ValueError):
                    audit.audit_batch(**args)
                self.assertFalse(args["output_path"].exists())
                self.assertFalse(list(args["library_dir"].glob(".module-audit-*")))

    def test_progress_module_mapping_and_source_urls_must_match_checkpoint(self):
        for change in ("module", "urls"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                args = self.prepare(Path(directory))
                path = args["library_dir"] / "batch-progress.json"
                progress = json.loads(path.read_text())
                if change == "module":
                    progress["modules"].reverse()
                else:
                    progress["identity"]["sourceUrls"] = ["https://wrong.example"]
                path.write_text(json.dumps(progress))
                with self.assertRaises(ValueError):
                    audit.audit_batch(**args)

    def test_changed_source_after_modules_is_detected_before_report(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            module = audit._module
            def change_source(*positional, **keywords):
                result = module(*positional, **keywords)
                with args["source_path"].open("ab") as out:
                    out.write(b"changed")
                return result
            with patch.object(audit, "_module", side_effect=change_source):
                with self.assertRaises(ValueError):
                    audit.audit_batch(**args)
            self.assertFalse(args["output_path"].exists())

    def test_raw_shard_alignment_proves_every_retained_original(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root, raw=True)
            report = audit.audit_batch(**args, raw_shard=root / "raw.json.bz2", provenance=root / "corpus/SOURCE.json")
            self.assertTrue(report["fullRawSourceMappingPassed"])
            self.assertEqual(2, report["rawSourceAudit"]["matchedArticles"])
            self.assertFalse(report["rawSourceAudit"]["selectionFiltersIndependentlyRecomputed"])

    def test_raw_options_must_be_paired_and_wrong_raw_pin_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root, raw=True)
            with self.assertRaisesRegex(ValueError, "supplied together"):
                audit.audit_batch(**args, raw_shard=root / "raw.json.bz2")
            with (root / "raw.json.bz2").open("ab") as out:
                out.write(b"changed")
            with self.assertRaisesRegex(ValueError, "Raw shard checksum"):
                audit.audit_batch(**args, raw_shard=root / "raw.json.bz2", provenance=root / "corpus/SOURCE.json")

    def test_rehashed_raw_original_change_is_not_hidden_by_a_new_pin(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root, raw=True)
            raw_path = root / "raw.json.bz2"
            expanded = bz2.decompress(raw_path.read_bytes()).replace(b"Original", b"Tampered")
            raw_path.write_bytes(bz2.compress(expanded))
            provenance_path = root / "corpus/SOURCE.json"
            provenance = json.loads(provenance_path.read_text())
            provenance["sourceBytes"], provenance["sourceSha256"] = audit._digest(raw_path)
            provenance["counts"]["expandedBytes"] = len(expanded)
            provenance_path.write_text(json.dumps(provenance))
            with self.assertRaisesRegex(ValueError, "differs from its complete raw original"):
                audit.audit_batch(**args, raw_shard=raw_path, provenance=provenance_path)

    def test_cli_available_outside_repository(self):
        result = subprocess.run([sys.executable, str(Path(audit.__file__).resolve()), "--help"],
            cwd=tempfile.gettempdir(), capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("--raw-shard", result.stdout)


if __name__ == "__main__":
    unittest.main()
