import unittest

from packtool.chunking import chunk_document
from packtool.schema import Document


class ChunkingTest(unittest.TestCase):
    def document(self, text):
        return Document("doc", "Title", "Source", "CC0-1.0", text)

    def test_chunks_are_bounded_overlap_and_have_stable_ids(self):
        text = " ".join(f"token-{index:04d}" for index in range(500))
        chunks = chunk_document(self.document(text), max_chars=1200, overlap_chars=150)
        self.assertGreater(len(chunks), 2)
        self.assertTrue(all(len(chunk.text) <= 1200 for chunk in chunks))
        self.assertEqual([f"doc:{index:04d}" for index in range(len(chunks))], [chunk.chunk_id for chunk in chunks])
        for left, right in zip(chunks, chunks[1:]):
            self.assertEqual(left.text[-150:], right.text[:150])

    def test_prefers_paragraph_boundary(self):
        first = "a" * 700
        second = "b" * 700
        chunks = chunk_document(self.document(first + "\n\n" + second), max_chars=1200, overlap_chars=150)
        self.assertEqual(first, chunks[0].text)

    def test_rejects_invalid_chunk_settings(self):
        with self.assertRaises(ValueError):
            chunk_document(self.document("text"), max_chars=100, overlap_chars=100)


if __name__ == "__main__":
    unittest.main()
