import copy
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import run_inference_controls as controls


class InferenceControlsTest(unittest.TestCase):
    def test_prepared_request_is_allowed_only_for_explicit_manual_control(self):
        row = {"question": "q", "status": "FROZEN_REQUEST", "request": {"messages": [{"content": "q"}]}}
        for name in ("automatic", "manual-complete-context"):
            plan = {"questions": ["q"], "conditions": [name]}
            report = {name: {"runs": [row]}}
            if name == "automatic":
                with self.assertRaisesRegex(ValueError, "Incomplete"):
                    controls.inputs(plan, report)
            else:
                self.assertEqual(1, len(controls.inputs(plan, report)))

    def test_sampler_changes_neither_evidence_nor_token_budget(self):
        request = {"messages": [{"role": "user", "content": "exact evidence"}],
                   "max_tokens": 1536, "chat_template_kwargs": {"enable_thinking": False}}
        original = copy.deepcopy(request)
        for sampler in controls.SAMPLERS:
            actual = controls.payload(request, sampler, 29)
            for key in original:
                self.assertEqual(original[key], actual[key])
            self.assertEqual(29, actual["seed"])
        self.assertEqual(original, request)

    def test_missing_condition_rejected(self):
        with self.assertRaisesRegex(ValueError, "all frozen conditions"):
            controls.inputs({"questions": ["q"], "conditions": ["automatic"]}, {})

    def test_incomplete_run_rejected(self):
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            controls.inputs({"questions": ["q"], "conditions": ["automatic"]},
                {"automatic": {"runs": [{"question": "q", "status": "ERROR"}]}})

    def test_selects_final_question_call_not_planner(self):
        request = {"messages": [{"content": "QUESTION: q"}]}
        cases = controls.inputs({"questions": ["q"], "conditions": ["automatic"]},
            {"automatic": {"runs": [{"question": "q", "status": "COMPLETED_UNSCORED",
                "calls": [{"request": {}}, {"request": request}]}]}})
        self.assertEqual(request, cases[0]["request"])
