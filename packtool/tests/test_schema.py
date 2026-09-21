import json
from pathlib import Path
import tempfile
import unittest

from packtool.schema import DocumentError, read_documents


class SchemaTest(unittest.TestCase):
    def write(self, rows, newline="\n"):
        root = tempfile.TemporaryDirectory()
        path = Path(root.name) / "documents.jsonl"
        path.write_text(newline.join(json.dumps(row, ensure_ascii=False) for row in rows) + newline, encoding="utf-8", newline="")
        self.addCleanup(root.cleanup)
        return path

    def valid(self, **changes):
        row = {
            "document_id": "doc-1",
            "title": "A title",
            "source": "Original author — https://example.test/source",
            "license": "CC-BY-4.0",
            "text": "First paragraph.\n\nSecond paragraph.",
        }
        row.update(changes)
        return row

    def test_lf_and_crlf_are_equivalent_and_rows_are_sorted(self):
        rows = [self.valid(document_id="z-doc"), self.valid(document_id="a-doc")]
        lf = read_documents(self.write(rows, "\n"))
        crlf = read_documents(self.write(rows, "\r\n"))
        self.assertEqual(lf, crlf)
        self.assertEqual(["a-doc", "z-doc"], [document.document_id for document in lf])

    def test_nfkc_normalizes_unicode_without_dropping_attribution(self):
        document = read_documents(self.write([self.valid(title="Ｆｉｅｌｄ", text="cafe\u0301")]))[0]
        self.assertEqual("Field", document.title)
        self.assertEqual("café", document.text)
        self.assertIn("Original author", document.source)

    def test_rejects_duplicate_blank_unknown_and_invalid_rows_with_line_numbers(self):
        cases = [
            ([self.valid(), self.valid()], "line 2: duplicate document_id"),
            ([self.valid(title="  ")], "line 1: title must be non-blank"),
            ([dict(self.valid(), extra=True)], "line 1: fields mismatch"),
            ([self.valid(document_id="contains spaces")], "line 1: invalid document_id"),
        ]
        for rows, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(DocumentError, message):
                read_documents(self.write(rows))

    def test_rejects_malformed_json_and_blank_lines(self):
        root = tempfile.TemporaryDirectory()
        self.addCleanup(root.cleanup)
        malformed = Path(root.name) / "malformed.jsonl"
        malformed.write_text('{"document_id":}\n', encoding="utf-8")
        with self.assertRaisesRegex(DocumentError, "line 1: invalid JSON"):
            read_documents(malformed)
        blank = Path(root.name) / "blank.jsonl"
        blank.write_text(json.dumps(self.valid()) + "\n\n", encoding="utf-8")
        with self.assertRaisesRegex(DocumentError, "line 2: blank lines are not allowed"):
            read_documents(blank)

    def test_rejects_duplicate_json_fields(self):
        root = tempfile.TemporaryDirectory()
        self.addCleanup(root.cleanup)
        path = Path(root.name) / "duplicate.jsonl"
        path.write_text(
            '{"document_id":"a","document_id":"b","title":"t","source":"s","license":"l","text":"x"}\n',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(DocumentError, "line 1: duplicate JSON field: document_id"):
            read_documents(path)


if __name__ == "__main__":
    unittest.main()
