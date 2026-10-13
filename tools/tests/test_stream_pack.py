import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.packs.stream_pack import _index_documents, _rows_in_order, build_pack_streaming
from packtool.build_pack import build_pack
from packtool.schema import DocumentError


class DiskBackedPackBuilderTest(unittest.TestCase):
    def rows(self):
        return [dict(document_id=key, title="Café — " + key, source="https://example.org/revision/123",
                     license="CC0-1.0", text="A supporting passage. Its qualification must not disappear.\n\n" * 20)
                for key in ("z-last", "a2", "a10", "a.2", "a-2", "a")]

    def spool(self, root, rows):
        path = root / "documents.jsonl"
        path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")
        return path

    def test_archive_database_and_checksums_match_reference_byte_for_byte(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            spool = self.spool(root, self.rows())
            args = dict(input_path=spool, pack_id="reference", version="1.0.0", title="Reference",
                        license_id="CC0-1.0", source_urls=["https://example.org/revision/123"])
            expected = build_pack(output_dir=root / "reference", **args)
            actual = build_pack_streaming(output_dir=root / "streamed", **args)
            for name in ("database", "manifest", "checksums", "pack"):
                self.assertEqual(getattr(expected, name).read_bytes(), getattr(actual, name).read_bytes(), name)
            self.assertFalse((root / "streamed" / ".document-index.sqlite").exists())

    def test_disk_index_sorts_utf8_bytes_and_retains_original_line_numbers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            spool = self.spool(root, self.rows())
            index = root / "offsets.sqlite"
            _index_documents(spool, index)
            self.assertEqual(sorted(row["document_id"] for row in self.rows()),
                             [doc.document_id for doc in _rows_in_order(spool, index)])
            with sqlite3.connect(index) as database:
                self.assertEqual((1,), database.execute("SELECT line FROM offsets WHERE id=?", (b"z-last",)).fetchone())

    def test_duplicate_ids_across_committed_batches_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            row = self.rows()[0]
            rows = [dict(row, document_id=f"doc-{n}") for n in range(2100)] + [dict(row, document_id="doc-0")]
            with self.assertRaisesRegex(DocumentError, "line 2101: duplicate document_id"):
                _index_documents(self.spool(root, rows), root / "offsets.sqlite")

    def test_failure_does_not_leave_a_partial_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            row = self.rows()[0]
            spool = self.spool(root, [row, row])
            with self.assertRaises(DocumentError):
                build_pack_streaming(spool, root / "failed", "reference", "1", "Reference", "CC0-1.0", ["https://example.org"])
            self.assertEqual(["documents.jsonl"], sorted(path.name for path in root.iterdir()))

    def test_empty_input_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(DocumentError, "input contains no documents"):
                _index_documents(self.spool(root, []), root / "offsets.sqlite")

    def test_oversized_record_is_rejected_before_unbounded_allocation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch("tools.packs.stream_pack.MAX_SOURCE_RECORD_BYTES", 10):
                with self.assertRaisesRegex(DocumentError, "document record exceeds"):
                    _index_documents(self.spool(root, self.rows()), root / "offsets.sqlite")


if __name__ == "__main__":
    unittest.main()
