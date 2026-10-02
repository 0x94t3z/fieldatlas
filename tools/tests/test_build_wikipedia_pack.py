from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import build_wikipedia_pack as wp  # noqa: E402

LEAD = "Albedo is the fraction of sunlight that is diffusely reflected by a body, measured from zero to one."
BODY = " ".join(["Terrestrial albedo varies with surface cover and season across the planet."] * 12)


def page(pid, title, lead, links, ns=0, templates=(), redirects=(), text=None):
    return {"page_id": pid, "version": 1000 + pid, "title": title, "namespace": ns, "incoming_links": links,
            "opening_text": lead, "text": text if text is not None else lead + " " + BODY,
            "template": list(templates), "redirect": [{"namespace": 0, "title": r} for r in redirects]}


PAGES = [
    page(39, "Albedo", LEAD, 3647, redirects=("Albedo effect", "albedo")),
    page(12, "Anarchism", "Anarchism is a political philosophy and movement that seeks to abolish hierarchy.", 50),
    page(13, "Mercury", "Mercury may refer to several things including a planet and an element.", 900,
         templates=("Template:Disambiguation",)),
    page(14, "Stub", "Too short.", 10),
    page(15, "Talk:Albedo", LEAD, 5000, ns=1),
]


def write_dump(path: Path):
    with gzip.open(path, "wt", encoding="utf-8") as handle:
        for p in PAGES:
            handle.write(json.dumps({"index": {"_id": str(p["page_id"])}}) + "\n")
            handle.write(json.dumps(p) + "\n")


class WikipediaPackTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = Path(self.dir.name)
        self.dump = self.root / "enwiki.json.gz"
        write_dump(self.dump)
        self.spool = self.root / "spool.jsonl.gz"
        wp.spool(argparse.Namespace(source=str(self.dump), out=self.spool, body_min_links=wp.BODY_MIN_LINKS, body_chars=wp.BODY_CHARS))

    def tearDown(self):
        self.dir.cleanup()

    def test_spool_keeps_real_articles_and_records_the_dump_hash(self):
        with gzip.open(self.spool, "rt") as handle:
            records = {r["id"]: r for r in map(json.loads, handle)}
        self.assertEqual({39, 12}, set(records))
        self.assertEqual(["Albedo effect"], records[39]["aliases"])
        self.assertTrue(records[39]["body"].startswith("Terrestrial albedo"))
        self.assertNotIn("body", records[12])
        meta = json.loads(self.spool.with_suffix(".meta.json").read_text())
        self.assertEqual(hashlib.sha256(self.dump.read_bytes()).hexdigest(), meta["sha256"])
        self.assertEqual(5, meta["pages"])

    def test_budget_keeps_the_most_linked_first_and_drops_bodies_before_leads(self):
        self.assertEqual({39: True, 12: False}, wp.select(self.spool, 10_000))
        tight = wp.select(self.spool, len(LEAD) + 5)
        self.assertEqual({39: False}, tight)

    def test_build_writes_overview_and_details_and_is_reproducible(self):
        hashes = []
        for name in ("a", "b"):
            args = argparse.Namespace(spool=self.spool, out=self.root / name, version="2025.12.29", text_budget=10_000, pack_id="wikipedia-en", title="Encyclopedia (Wikipedia)", host="en.wikipedia.org", id_prefix="wp", copyright_page="Wikipedia:Copyrights", coverage="Lead sections of {count} articles.", example=["What is albedo?"])
            self.assertEqual(0, wp.build(args))
            pack = self.root / name / "wikipedia-en" / "wikipedia-en-2025.12.29.fapack"
            hashes.append(hashlib.sha256(pack.read_bytes()).hexdigest())
        self.assertEqual(hashes[0], hashes[1])
        db = sqlite3.connect(self.root / "a" / "wikipedia-en" / "content.sqlite")
        rows = db.execute("SELECT chunk_id, title, source, text FROM chunks_fts ORDER BY chunk_id").fetchall()
        titles = {r[1] for r in rows}
        self.assertIn("Albedo — Overview", titles)
        self.assertIn("Albedo — Details", titles)
        overview = next(r for r in rows if r[0] == "wp-39-0000:0000")
        self.assertEqual("https://en.wikipedia.org/w/index.php?oldid=1039", overview[2])
        self.assertIn("\n\nAlso known as: Albedo effect", overview[3])
        hit = db.execute("SELECT title FROM chunks_fts WHERE chunks_fts MATCH 'reflected'").fetchall()
        self.assertIn(("Albedo — Overview",), hit)


    def test_template_errors_are_repaired_or_dropped(self):
        nihongo = "Lua error in Module:Nihongo at line 88: attempt to call field '_transl' (a nil value)."
        package = "Lua error in package.lua at line 80: module 'Module:Pagetype/setindex' not found. "
        self.assertEqual("Sapporo is the capital city of Hokkaido. It is a port.",
                         wp.clean_text(package + nihongo + " is the capital city of Hokkaido. It is a port.", "Sapporo"))
        self.assertEqual("Shinto has kami. Eight million means infinity.",
                         wp.clean_text("Shinto has kami. It is said there are " + nihongo + ". Eight million means infinity.", "Shinto"))
        self.assertEqual("Plain text.", wp.clean_text("Plain text.", "Title"))


if __name__ == "__main__":
    unittest.main()
