from copy import deepcopy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('compare_research_runs', Path(__file__).resolve().parents[1] / 'evaluation/compare_research_runs.py')
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class PairedResearchTests(unittest.TestCase):
    def reports(self):
        app = dict(retrievalBackend='app', identities=['model+source pins'], answerPolicy='app', settings={'temperature': .3},
                   provenance=dict(appKotlinSha256='code', llamaCommit='llama', diagnosticToolSha256={'harness': 'sha'}, desktopOptions={}),
                   runs=[dict(question='question', seed=17, status='COMPLETED_UNSCORED', sources=[], calls=[], visibleAnswer='answer',
                              metrics=dict(retrievalMillis=1, totalMillis=2, hasUnmappedCitation=False), experimentalSearches=[])])
        experimental = deepcopy(app); experimental['retrievalBackend'] = 'leviathan'
        experimental['runs'][0]['experimentalSearches'] = [dict(backend='leviathan', fullPassagesVerified=True,
            candidateMs=2, hydrationMs=1, cardChars=100, fullPassageChars=1000)]
        return app, experimental

    def test_reports_observations_without_claiming_accuracy(self):
        result = tool.compare(*self.reports())
        self.assertFalse(result['productionAdopted'])
        self.assertEqual(1, result['pairs'][0]['engineCalls'])
        self.assertIn('UNSCORED', result['qualityVerdict'])

    def test_refuses_different_model_settings_or_pipeline(self):
        for key in ['identities', 'settings', 'answerPolicy']:
            app, experimental = self.reports(); experimental[key] = 'changed'
            with self.assertRaisesRegex(ValueError, key): tool.compare(app, experimental)
        for key in ['appKotlinSha256', 'llamaCommit', 'diagnosticToolSha256', 'desktopOptions']:
            app, experimental = self.reports(); experimental['provenance'][key] = 'changed'
            with self.assertRaisesRegex(ValueError, key): tool.compare(app, experimental)

    def test_refuses_failed_duplicate_or_different_question_runs(self):
        for change in [lambda r: r['runs'][0].update(status='ERROR'), lambda r: r['runs'].append(deepcopy(r['runs'][0])),
                       lambda r: r['runs'][0].update(question='different'), lambda r: r.update(status='ERROR')]:
            app, experimental = self.reports(); change(experimental)
            with self.assertRaises(ValueError): tool.compare(app, experimental)

    def test_delegated_routes_are_not_reported_as_engine_wins(self):
        app, experimental = self.reports(); experimental['runs'][0]['experimentalSearches'] = [dict(backend='app')]
        self.assertEqual(0, tool.compare(app, experimental)['pairs'][0]['engineCalls'])

    def test_unverified_passages_are_rejected(self):
        app, experimental = self.reports()
        experimental['runs'][0]['experimentalSearches'][0]['fullPassagesVerified'] = False
        with self.assertRaisesRegex(ValueError, 'Unverified'): tool.compare(app, experimental)
