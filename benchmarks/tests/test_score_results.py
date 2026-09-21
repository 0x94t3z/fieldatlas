import unittest

from benchmarks.score_results import BenchmarkError, score_pair


class ScoreResultsTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
