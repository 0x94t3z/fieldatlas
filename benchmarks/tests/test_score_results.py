import unittest

from benchmarks.score_results import BenchmarkError, score_pair


class ScoreResultsTest(unittest.TestCase):
    def device_result(self, state, answer):
        return {
            "schemaVersion": 1, "runId": "physical-run", "startedAt": "2026-09-26T00:00:00Z",
            "artifacts": [], "diagnosticsSha256": "0" * 64,
            "results": [{
                "questionId": "q1", "prompt": "Question?", "state": state, "answer": answer,
                "evidenceChunkIds": [], "metrics": None,
                "error": "No matching evidence" if state == "INSUFFICIENT" else None,
            }],
        }

    def question(self, identifier="q1", category="factual", answerable=True):
        return {
            "id": identifier,
            "category": category,
            "prompt": "Question?",
            "answerable": answerable,
            "requiredEvidence": ["alpha", "beta"] if answerable else [],
            "prohibitedClaims": ["gamma"],
            "scoringNotes": "Fixture rubric",
        }

    def test_category_aggregation_citation_and_prohibited_penalties(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question(), self.question("q2", "comparison")]}
        offline = {"schemaVersion": 1, "results": [
            {"questionId": "q1", "answer": "Alpha and beta [S1]."},
            {"questionId": "q2", "answer": "Alpha and beta but gamma."},
        ]}
        baseline = {"schemaVersion": 1, "results": [
            {"questionId": "q1", "answer": "Alpha and beta [S1]."},
            {"questionId": "q2", "answer": "Alpha and beta [S2]."},
        ]}
        scored = score_pair(benchmark, offline, baseline)
        self.assertEqual(1.0, scored["offline"]["categories"]["factual"])
        self.assertLess(scored["offline"]["categories"]["comparison"], 0.75)
        self.assertEqual(0.625, scored["offline"]["overall"])
        self.assertTrue(scored["not_an_issuer_determination"])

    def test_unanswerable_rewards_explicit_abstention(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question(answerable=False)]}
        abstains = {"schemaVersion": 1, "results": [{"questionId": "q1", "answer": "Insufficient evidence to determine this."}]}
        guesses = {"schemaVersion": 1, "results": [{"questionId": "q1", "answer": "Definitely gamma."}]}
        scored = score_pair(benchmark, abstains, guesses)
        self.assertEqual(1.0, scored["offline"]["overall"])
        self.assertEqual(0.0, scored["baseline"]["overall"])
        self.assertIsNone(scored["offline_to_baseline_ratio"])

    def test_missing_extra_or_invalid_schema_is_rejected(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question()]}
        valid = {"schemaVersion": 1, "results": [{"questionId": "q1", "answer": "alpha"}]}
        invalid_cases = [
            {"schemaVersion": 1, "results": []},
            {"schemaVersion": 1, "results": valid["results"] + [{"questionId": "extra", "answer": "x"}]},
            {"schemaVersion": 2, "results": valid["results"]},
        ]
        for invalid in invalid_cases:
            with self.subTest(invalid=invalid), self.assertRaises(BenchmarkError):
                score_pair(benchmark, invalid, valid)

    def test_empty_required_evidence_for_answerable_question_is_rejected(self):
        question = self.question()
        question["requiredEvidence"] = []
        benchmark = {"schemaVersion": 1, "questions": [question]}
        results = {"schemaVersion": 1, "results": [{"questionId": "q1", "answer": "answer"}]}
        with self.assertRaises(BenchmarkError):
            score_pair(benchmark, results, results)

    def test_android_device_export_is_accepted_directly(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question()]}
        device = {
            "schemaVersion": 1,
            "runId": "physical-run",
            "startedAt": "2026-09-26T00:00:00Z",
            "artifacts": [],
            "diagnosticsSha256": "0" * 64,
            "results": [{
                "questionId": "q1",
                "prompt": "Question?",
                "state": "COMPLETE",
                "answer": "Alpha and beta [S1].",
                "evidenceChunkIds": ["doc:1"],
                "metrics": None,
                "error": None,
            }],
        }
        baseline = {"schemaVersion": 1, "results": [{"questionId": "q1", "answer": "Alpha and beta [S1]."}]}
        self.assertEqual(1.0, score_pair(benchmark, device, baseline)["offline"]["overall"])

    def test_incomplete_android_device_export_is_rejected(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question()]}
        device = {
            "schemaVersion": 1,
            "runId": "physical-run",
            "startedAt": "2026-09-26T00:00:00Z",
            "artifacts": [],
            "diagnosticsSha256": "0" * 64,
            "results": [{
                "questionId": "q1", "prompt": "Question?", "state": "FAILED", "answer": "",
                "evidenceChunkIds": [], "metrics": None, "error": "failed",
            }],
        }
        with self.assertRaises(BenchmarkError):
            score_pair(benchmark, device, device)

    def test_android_insufficient_terminal_result_can_score_explicit_abstention(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question(answerable=False)]}
        device = self.device_result("INSUFFICIENT", "Insufficient evidence: no saved passage matches.")
        scored = score_pair(benchmark, device, device)
        self.assertEqual(1.0, scored["offline"]["overall"])
        self.assertTrue(scored["offline"]["questions"][0]["abstained"])
        self.assertEqual("INSUFFICIENT", scored["offline"]["questions"][0]["device_state"])

    def test_insufficient_cannot_earn_answerable_credit_from_echoed_terms(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question()]}
        device = self.device_result("INSUFFICIENT", "Insufficient evidence about alpha and beta [S1].")
        self.assertEqual(0.0, score_pair(benchmark, device, device)["offline"]["overall"])

    def test_insufficient_state_without_explicit_abstention_text_is_not_full_credit(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question(answerable=False)]}
        for answer in ("", "Definitely gamma."):
            with self.subTest(answer=answer):
                device = self.device_result("INSUFFICIENT", answer)
                self.assertEqual(0.0, score_pair(benchmark, device, device)["offline"]["overall"])

    def test_other_device_states_remain_rejected(self):
        benchmark = {"schemaVersion": 1, "questions": [self.question(answerable=False)]}
        for state in ("FAILED", "CANCELLED", "RUNNING", "complete", "UNKNOWN", None):
            with self.subTest(state=state), self.assertRaises(BenchmarkError):
                device = self.device_result(state, "Insufficient evidence.")
                score_pair(benchmark, device, device)


if __name__ == "__main__":
    unittest.main()
