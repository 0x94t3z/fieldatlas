import hashlib
import json
from pathlib import Path
import sys
import subprocess
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_modular_library import build_library, verified_archive
from stream_pack import _index_documents
from packtool.schema import DocumentError


class ModularLibraryTest(unittest.TestCase):
    def test_low_build_space_rejects_before_indexing_and_preserves_input(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch("build_modular_library.shutil.disk_usage", return_value=SimpleNamespace(free=0)), \
                    patch("build_modular_library._index_documents") as index:
                with self.assertRaisesRegex(ValueError, "Insufficient build disk space"):
                    self.build(root)
                index.assert_not_called()
            self.assertEqual(["source.jsonl"], sorted(p.name for p in root.iterdir()))

    def test_later_space_failure_removes_only_owned_incomplete_build(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch("build_modular_library.require_build_space", side_effect=[None, ValueError("Insufficient build disk space")]):
                with self.assertRaisesRegex(ValueError, "Insufficient build disk space"):
                    self.build(root)
            self.assertEqual(["source.jsonl"], sorted(p.name for p in root.iterdir()))
            self.assertTrue(root.joinpath("source.jsonl").read_bytes())

    def test_command_line_runs_without_a_preconfigured_python_path(self):
        with tempfile.TemporaryDirectory() as directory:
            tool = Path(__file__).resolve().parents[1] / "build_modular_library.py"
            result = subprocess.run([sys.executable, str(tool), "--help"], cwd=directory,
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("--input-sha256", result.stdout)

    def build(self, root, output="library", rows=None, **options):
        rows = rows if rows is not None else [dict(document_id=f"wiki-{n}", title=f"Topic {n}",
            source=f"https://example.org/topic/{n}?oldid=123", license="CC0-1.0",
            text="A full supporting passage. A qualification is preserved. " * 10) for n in (4, 1, 3, 2)]
        path = root / "source.jsonl"
        path.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        pin = hashlib.sha256(path.read_bytes()).hexdigest()
        args = dict(input_path=path, output_dir=root / output, expected_sha256=pin,
                    pack_id="general-reference", version="1.0.0", title="General reference",
                    license_id="CC0-1.0", source_urls=["https://example.org/snapshot/123"], module_text_bytes=600)
        return build_library(**(args | options))

    def test_modules_are_preindexed_verified_and_never_marked_as_public_downloads(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = self.build(root)
            self.assertEqual(4, len(report["modules"]))
            self.assertFalse(report["releaseReady"])
            self.assertFalse(report["meetsTwentyDecimalGBTarget"])
            self.assertEqual(sum(m["installedBytes"] for m in report["modules"]), report["installedBytes"])
            for module in report["modules"]:
                pack = root / "library" / module["archive"]
                manifest = verified_archive(pack)
                self.assertEqual(module["sha256"], hashlib.sha256(pack.read_bytes()).hexdigest())
                self.assertEqual("CC0-1.0", manifest["license"])
                self.assertEqual("general-keyword", module["retrievalMode"])
                self.assertFalse((pack.parent / "content.sqlite").exists())
                self.assertNotIn("url", module)
                with zipfile.ZipFile(pack) as archive:
                    self.assertEqual(module["installedBytes"], archive.getinfo("content.sqlite").file_size)

    def test_rebuilds_are_byte_reproducible_without_duplicate_or_padded_records(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first, second = self.build(root, "one"), self.build(root, "two")
            self.assertEqual(first, second)
            self.assertEqual(4, sum(m["documents"] for m in first["modules"]))
            for module in first["modules"]:
                self.assertEqual((root / "one" / module["archive"]).read_bytes(),
                                 (root / "two" / module["archive"]).read_bytes())

    def test_checksum_sidecar_references_only_retained_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = self.build(root)
            for module in report["modules"]:
                directory = (root / "library" / module["archive"]).parent
                lines = (directory / "SHA256SUMS").read_text().splitlines()
                self.assertEqual(2, len(lines))
                for line in lines:
                    digest, name = line.split("  ", 1)
                    self.assertTrue((directory / name).is_file(), name)
                    self.assertEqual(digest, hashlib.sha256((directory / name).read_bytes()).hexdigest())

    def test_wrong_input_hash_does_not_create_an_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                self.build(root, expected_sha256="0" * 64)
            self.assertEqual(["source.jsonl"], sorted(p.name for p in root.iterdir()))

    def test_existing_output_is_never_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "library").mkdir()
            sentinel = root / "library" / "keep.txt"
            sentinel.write_text("Keep my file")
            with self.assertRaisesRegex(ValueError, "already exists"):
                self.build(root)
            self.assertEqual("Keep my file", sentinel.read_text())

    def test_size_limit_failure_has_no_partial_library(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "exceeds"):
                self.build(root, max_installed_bytes=1)
            self.assertEqual(["source.jsonl"], sorted(p.name for p in root.iterdir()))

    def test_source_licence_cannot_be_silently_relabeled(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "licence differs"):
                self.build(root, license_id="CC-BY-SA-4.0")
            self.assertFalse((root / "library").exists())

    def test_source_changed_after_indexing_cannot_allocate_an_unbounded_record(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def change_after_indexing(path, index_path):
                _index_documents(path, index_path)
                path.write_bytes(b"x" * 1001)

            with patch("build_modular_library._index_documents", side_effect=change_after_indexing), \
                    patch("build_modular_library.MAX_SOURCE_RECORD_BYTES", 1000):
                with self.assertRaisesRegex(DocumentError, "document record exceeds"):
                    self.build(root, rows=[dict(document_id="doc-1", title="Topic",
                        source="https://example.org/1", license="CC0-1.0", text="One original passage.")])
            self.assertEqual(["source.jsonl"], sorted(p.name for p in root.iterdir()))

    def test_article_section_inputs_are_not_claimed_as_general_search_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rows = [dict(document_id="enw-123-0000", title="Topic — Lead", source="https://example.org/123",
                         license="CC0-1.0", text="Original article section.")]
            report = self.build(root, rows=rows)
            self.assertEqual("article-sections", report["modules"][0]["retrievalMode"])

    def test_article_sections_never_split_between_modules(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rows = [dict(document_id=f"enw-{page}-{section:04d}", title="Topic — Overview" if section == 0 else "Topic — Details",
                         source=f"https://example.org/{page}", license="CC0-1.0", text="Original passage. " * 20)
                    for page in (123, 456) for section in (0, 1)]
            report = self.build(root, rows=rows, module_text_bytes=400)
            self.assertEqual(2, len(report["modules"]))
            self.assertEqual([2, 2], [m["documents"] for m in report["modules"]])
            self.assertEqual([1, 1], [m["articleGroups"] for m in report["modules"]])
            self.assertTrue(all(m["articleBoundariesPreserved"] for m in report["modules"]))

    def test_article_without_its_original_lead_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rows = [dict(document_id="enw-123-0001", title="Topic — Details", source="https://example.org/123",
                         license="CC0-1.0", text="Original section without its lead.")]
            with self.assertRaisesRegex(ValueError, "0000 lead"):
                self.build(root, rows=rows)
            self.assertFalse((root / "library").exists())

    def test_section_and_ordinary_records_cannot_be_misclassified_as_one_library(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rows = [dict(document_id=identifier, title="Topic", source="https://example.org/123",
                         license="CC0-1.0", text="Original section.") for identifier in ("enw-123-0000", "other-1")]
            with self.assertRaisesRegex(ValueError, "separate libraries"):
                self.build(root, rows=rows)
            self.assertFalse((root / "library").exists())


if __name__ == "__main__":
    unittest.main()
