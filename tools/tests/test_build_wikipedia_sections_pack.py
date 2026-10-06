import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_wikipedia_sections_pack as tool  # noqa: E402


class FormulaTest(unittest.TestCase):
    def test_prose_after_a_formula_is_kept(self):
        text = ("For any affine transformation of the complex plane,\n  z\n  ↦\n  {\\displaystyle z\\mapsto az+b,}\n  \n"
                "   a triangle is transformed but does not change its shape.")
        out = tool.clean(text)
        self.assertIn("z\\mapsto az+b,", out)
        self.assertIn("a triangle is transformed but does not change its shape.", out)

    def test_prose_between_two_formulas_is_kept(self):
        text = ("Commonly, a tuple is written as\n  (\n  1\n  )\n  {\\displaystyle (1,2).}\n  \n"
                "  When using functional notation, one usually omits the parentheses, writing  \n  f\n  {\\displaystyle f(x)}\n  \n"
                " instead of \n  f\n  {\\displaystyle f((x)).}\n")
        out = tool.clean(text)
        self.assertIn("When using functional notation, one usually omits the parentheses, writing", out)
        self.assertLess(out.index("(1,2)."), out.index("When using"))
        self.assertLess(out.index("When using"), out.index("f(x)"))

    def test_a_pause_holds_back_a_worker_waiting_for_its_slot(self):
        import threading, time
        # Two requests a second: the worker's slot is half a second away when the pause comes.
        pace = tool.Pace(2.0)
        pace.wait()
        waited = []
        def worker():
            start = time.monotonic(); pace.wait(); waited.append(time.monotonic() - start)
        thread = threading.Thread(target=worker)
        thread.start()
        pace.pause(1.0)
        thread.join()
        self.assertGreaterEqual(waited[0], 0.9)

    def test_textstyle_is_written_as_latex(self):
        out = tool.clean("The sum\n  x\n  +\n  {\\textstyle x+y}\n is small.")
        self.assertEqual("The sum x+y is small.", out)


class DocumentsTest(unittest.TestCase):
    def record(self, extract):
        return {"title": "Screwdriver", "pageid": 194603, "revid": 1, "aliases": [], "extract": extract}

    def test_a_short_lead_is_kept_so_the_article_can_be_reached(self):
        docs = list(tool.article_documents(self.record("A screwdriver is a tool for turning screws.\n\n== Description ==\n" + "x" * 200)))
        self.assertEqual("enw-194603-0000", docs[0]["document_id"])
        self.assertEqual("Screwdriver — Overview", docs[0]["title"])

    def test_an_article_without_a_lead_is_left_out(self):
        self.assertEqual([], list(tool.article_documents(self.record("\n\n== Description ==\n" + "x" * 200))))


class SpoolTest(unittest.TestCase):
    def test_a_long_cut_off_line_does_not_wipe_the_spool(self):
        with tempfile.TemporaryDirectory() as folder:
            spool = Path(folder) / "spool.jsonl"
            complete = json.dumps({"requested": "A", "pageid": 1, "title": "A", "extract": "x" * 300_000}) + "\n"
            spool.write_bytes(complete.encode() * 3 + b'{"requested": "B", "extract": "' + b"y" * 1_050_000)
            tool.truncate_partial(spool, step=100_000)
            self.assertEqual(complete.encode() * 3, spool.read_bytes())

    def test_index_keeps_the_first_record_of_each_page_in_title_order(self):
        with tempfile.TemporaryDirectory() as folder:
            spool = Path(folder) / "spool.jsonl"
            lines = [{"requested": "B", "pageid": 2, "title": "B", "revid": 5, "extract": "b"},
                     {"requested": "A", "pageid": 1, "title": "A", "revid": 6, "extract": "a"},
                     {"requested": "B", "pageid": 2, "title": "B", "revid": 9, "extract": "later"}]
            spool.write_bytes(b"".join((json.dumps(r) + "\n").encode() for r in lines) + "{\"cut\": \"é".encode()[:-1])
            index, digest = tool.spool_index(spool)
            self.assertEqual(["A", "B"], [title for title, _ in index])
            self.assertEqual([6, 5], [r["revid"] for r in tool.spool_records(spool, index)])
            self.assertEqual(64, len(digest))


if __name__ == "__main__":
    unittest.main()
