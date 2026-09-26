from __future__ import annotations

import bz2
import sqlite3
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_wikivoyage_eat_pack import dump_documents  # noqa: E402
from stream_pack import build_pack_streaming  # noqa: E402


class WikivoyageEatPackTest(unittest.TestCase):
    def test_eat_listing_keeps_city_diet_and_revision_without_non_eat_records(self):
        xml = """<mediawiki xmlns="http://www.mediawiki.org/xml/export-0.11/">
          <page><title>Chiang Mai</title><ns>0</ns><id>123</id>
            <revision><id>456</id><timestamp>2026-09-20T10:20:30Z</timestamp>
              <text xml:space="preserve">{{eat|name=Green Plate|address=Old Town|
                content=Vegan Thai food with [[tofu|soy]] dishes and {{lang|th|อาหาร}}.|
                lastedit=2026-08-01}}
                {{see|name=Old Gate|content=Historic city gate}}
                {{listing|type=eat|name=River Cafe|content=Vegetarian options}}
              </text></revision></page>
          <page><title>Talk:Chiang Mai</title><ns>1</ns><id>124</id>
            <revision><id>457</id><timestamp>2026-09-20T10:20:30Z</timestamp>
              <text>{{eat|name=Talk Cafe|content=Not a city listing}}</text></revision></page>
        </mediawiki>"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dump = root / "pages.xml"
            dump.write_text(xml, encoding="utf-8")
            documents = list(dump_documents(dump))
            compressed = root / "pages.xml.bz2"
            compressed.write_bytes(bz2.compress(xml.encode("utf-8")))
            self.assertEqual(documents, list(dump_documents(compressed)))
            self.assertEqual(2, len(documents))
            green = documents[0]
            self.assertEqual("Chiang Mai — Green Plate", green["title"])
            self.assertIn("Vegan Thai food with soy dishes", green["text"])
            self.assertIn("Listing last checked: 2026-08-01", green["text"])
            self.assertIn("Source page revision: 2026-09-20", green["text"])
            self.assertEqual("https://en.wikivoyage.org/w/index.php?oldid=456", green["source"])

            spool = root / "documents.jsonl"
            import json
            spool.write_text("".join(json.dumps(row) + "\n" for row in documents), encoding="utf-8")
            built = build_pack_streaming(
                input_path=spool, output_dir=root / "pack", pack_id="wikivoyage-eat",
                version="1.0.0", title="Places to eat", license_id="CC-BY-SA-4.0",
                source_urls=["https://en.wikivoyage.org"], fts_tokenizer="porter unicode61",
            )
            with sqlite3.connect(built.database) as database:
                rows = database.execute(
                    "SELECT title FROM chunks_fts WHERE chunks_fts MATCH ?",
                    ('"vegan" AND "chiang" AND "mai"',),
                ).fetchall()
            self.assertEqual([("Chiang Mai — Green Plate",)], rows)


if __name__ == "__main__":
    unittest.main()
