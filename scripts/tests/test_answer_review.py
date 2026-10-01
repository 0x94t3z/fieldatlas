import importlib.util
import hashlib
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("answer_review", Path(__file__).resolve().parents[1] / "prepare_answer_review.py")
reviewer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reviewer)


class AnswerReviewTests(unittest.TestCase):
    def test_review_is_bound_to_original_answers_and_evidence(self):
        raw = json.dumps({"runs": [{"status": "COMPLETED_UNSCORED", "question": "q", "visibleAnswer": "Original answer.", "calls": []}]}).encode()
        review = reviewer.prepare(json.loads(raw))
        review["inputSha256"] = hashlib.sha256(raw).hexdigest()
        reviewer.validate_origin(review, raw)
        review["answers"][0]["answer"] = "Changed answer."
        with self.assertRaises(ValueError):
            reviewer.validate_origin(review, raw)
        with self.assertRaises(ValueError):
            reviewer.validate_origin(review, b"{}")

    def test_blocks_preserve_every_nonwhitespace_character_and_bullet_context(self):
        answer = "## Model explanation—not verified against saved sources\n\n  Two claims. Another claim!\n- A bullet\n- Another bullet  \n\nΩ final."
        blocks = reviewer.blocks(answer)
        self.assertEqual(3, len(blocks))
        self.assertEqual("not-a-claim", blocks[0]["label"])
        for block in blocks:
            self.assertEqual(block["text"], answer[block["start"]:block["end"]])
        self.assertIn("Another claim!", blocks[1]["text"])
        self.assertIn("- Another bullet", blocks[1]["text"])

    def test_export_refuses_missing_labels_and_dropped_text(self):
        answer = {"id": "a", "question": "q", "answer": "One claim.\n\nAnother claim.",
                  "passages": ["Source text."], "coverageReview": "reviewed"}
        answer["blocks"] = reviewer.blocks(answer["answer"])
        with self.assertRaises(ValueError):
            reviewer.export_checks({"answers": [answer]})
        for block in answer["blocks"]:
            block.update(label="unsupported", reason="Missing source support")
        self.assertEqual(2, len(reviewer.export_checks({"answers": [answer]})["cases"][0]["probes"]))
        answer["blocks"].pop()
        with self.assertRaises(ValueError):
            reviewer.export_checks({"answers": [answer]})

    def test_packed_passages_use_only_visible_excerpt_not_full_source(self):
        prompt = "policy\n\nEVIDENCE:\n[S1]\nTitle: Title\nSource: Local\nExcerpt: Only this excerpt.\n\nQUESTION:\nq"
        run = {"calls": [{"request": {"messages": [{"content": prompt}]}}]}
        self.assertEqual(["Only this excerpt."], reviewer.packed_passages(run))
        run["calls"][0]["request"]["messages"][0]["content"] = prompt.replace("[S1]", "[S2]")
        with self.assertRaises(ValueError):
            reviewer.packed_passages(run)

    def test_no_evidence_is_not_sent_as_a_verified_case(self):
        answer = {"id": "a", "question": "q", "answer": "Unknown.", "passages": [],
                  "coverageReview": "No source evidence", "blocks": reviewer.blocks("Unknown.")}
        answer["blocks"][0].update(label="unsupported", reason="No source evidence")
        self.assertEqual([], reviewer.export_checks({"answers": [answer]})["cases"])


if __name__ == "__main__":
    unittest.main()
