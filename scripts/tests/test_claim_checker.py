import importlib.util
import json
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("claim_checker", SCRIPTS / "check_claim_support.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class ClaimCheckerTests(unittest.TestCase):
    def verdict(self, **changes):
        value = {"verdict": "supported", "reason": "The note states the mass.",
                 "quotes": [{"source": 1, "text": "A capsule has a mass of 8 grams."}]}
        value.update(changes)
        return json.dumps(value)

    def test_only_cited_passages_are_supplied(self):
        case = {"passages": ["correct source", "different source"]}
        self.assertEqual({2: "different source"}, checker.cited_passages(case, {"citations": [2]}))
        for ids in [[], [0], [3], [-1], [True], [1, 1]]:
            self.assertIsNone(checker.cited_passages(case, {"citations": ids}))

    def test_expected_labels_do_not_enter_prompt(self):
        request = checker.request_for("A claim", {2: "A passage"})
        payload = json.loads(request["messages"][1]["content"].removeprefix("/no_think\n"))
        self.assertEqual({"claim": "A claim", "passages": {"2": "A passage"}}, payload)

    def test_exact_quote_validation_is_attribution_not_entailment(self):
        passages = {1: "A capsule has a mass of 8 grams."}
        self.assertEqual("supported", checker.parse_verdict(self.verdict(), passages)["verdict"])
        # No claim enters this parser; a correct quotation cannot certify a paraphrase.
        self.assertEqual("insufficient", checker.parse_verdict(self.verdict(verdict="insufficient", quotes=[]), passages)["verdict"])

    def test_malformed_unknown_and_unmapped_responses_fail_closed(self):
        passages = {1: "A capsule has a mass of 8 grams."}
        bad = ["not JSON", "```json\n" + self.verdict() + "\n```", "[]", "{}",
               self.verdict(verdict="verified"), self.verdict(quotes=[]),
               self.verdict(verdict="insufficient"), self.verdict(reason=""),
               self.verdict(quotes=[{"source": 2, "text": passages[1]}]),
               self.verdict(quotes=[{"source": True, "text": passages[1]}]),
               self.verdict(quotes=[{"source": 1, "text": "A capsule has a mass of 80 grams."}]),
               self.verdict().replace('"verdict": "supported"', '"verdict": "insufficient", "verdict": "supported"')]
        for raw in bad:
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                checker.parse_verdict(raw, passages)

    def test_false_support_and_abstention_are_not_hidden_in_completion(self):
        summary = checker.summarize([
            {"expected": "insufficient", "actual": "supported"},
            {"expected": "supported", "actual": "invalid-response"},
            {"expected": "contradicted", "actual": "contradicted"},
        ])
        self.assertEqual(1, summary["falseSupport"])
        self.assertEqual(1, summary["missedSupport"])
        self.assertEqual(1, summary["labelMatches"])
        self.assertIn("NOT VALIDATED", summary["productionDecision"])

    def test_additional_fixtures_load_without_duplicate_ids(self):
        cases = checker.load_cases(checker.ROOT / "tools/claim_checker_cases.json")
        self.assertEqual(6, len(cases))


if __name__ == "__main__":
    unittest.main()
