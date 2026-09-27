from __future__ import annotations

import bz2
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_wikivoyage_places_pack import dump_documents  # noqa: E402
from stream_pack import build_pack_streaming  # noqa: E402


class WikivoyagePlacesPackTest(unittest.TestCase):
    def test_multiple_place_types_keep_category_city_dates_and_source(self):
        xml = """<mediawiki xmlns="http://www.mediawiki.org/xml/export-0.11/">
          <page><title>Berlin/Mitte</title><ns>0</ns><id>123</id>
            <revision><id>456</id><timestamp>2026-09-20T10:20:30Z</timestamp>
              <text xml:space="preserve">{{see|name=City Museum|address=Main Street|
                content=History museum|lastedit=2026-08-01}}
                {{eat|name=Local Kitchen|content=German dishes}}
                {{listing|type=sleep|name=River Hotel|content=Rooms near the station}}
                {{listing|type=other|name=Not a place}}
              </text></revision></page>
          <page><title>Talk:Berlin</title><ns>1</ns><id>124</id>
            <revision><id>457</id><timestamp>2026-09-20T10:20:30Z</timestamp>
              <text>{{buy|name=Talk Shop}}</text></revision></page>
        </mediawiki>"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dump = root / "pages.xml.bz2"
            dump.write_bytes(bz2.compress(xml.encode("utf-8")))
            documents = list(dump_documents(dump))
            self.assertEqual(3, len(documents))
            self.assertEqual(["See", "Eat", "Sleep"], [
                row["text"].splitlines()[1].removeprefix("Category: ") for row in documents
            ])
            self.assertTrue(all(row["document_id"].startswith("wv-place-123-") for row in documents))
            self.assertIn("Listing last checked: 2026-08-01", documents[0]["text"])
            self.assertIn("Source page revision: 2026-09-20", documents[0]["text"])
            self.assertEqual("https://en.wikivoyage.org/w/index.php?oldid=456", documents[0]["source"])

            spool = root / "documents.jsonl"
            spool.write_text("".join(json.dumps(row) + "\n" for row in documents), encoding="utf-8")
            built = build_pack_streaming(
                input_path=spool, output_dir=root / "pack", pack_id="wikivoyage-places",
                version="1.0.0", title="Wikivoyage places", license_id="CC-BY-SA-4.0",
                source_urls=["https://en.wikivoyage.org"], fts_tokenizer="porter unicode61",
            )
            with sqlite3.connect(built.database) as database:
                museum = database.execute(
                    "SELECT title FROM chunks_fts WHERE chunks_fts MATCH ?",
                    ('"museum" AND "berlin"',),
                ).fetchall()
            self.assertEqual([("Berlin/Mitte — City Museum",)], museum)


if __name__ == "__main__":
    unittest.main()
