#!/usr/bin/env python3
"""Pair app/Leviathan diagnostic runs; report observations, never an accuracy score."""
import argparse
import json
from pathlib import Path


def compare(app, experimental):
    if app.get('retrievalBackend') != 'app' or experimental.get('retrievalBackend') != 'leviathan':
        raise ValueError('Expected app baseline and Leviathan experiment')
    for key in ['identities', 'answerPolicy', 'settings']:
        if key not in app or app[key] != experimental.get(key):
            raise ValueError(f'Unpaired {key}')
    for key in ['appKotlinSha256', 'llamaCommit', 'diagnosticToolSha256', 'desktopOptions']:
        if key not in app['provenance'] or app['provenance'][key] != experimental['provenance'].get(key):
            raise ValueError(f'Unpaired {key}')
    def keyed(report):
        rows = report['runs']
        keyed = {(row['question'], row['seed']): row for row in rows}
        if not rows or len(keyed) != len(rows):
            raise ValueError('Empty or duplicate question/seed pairs')
        if any(row['status'] != 'COMPLETED_UNSCORED' for row in rows) or report.get('status') == 'ERROR':
            raise ValueError('Cannot summarize failed or incomplete runs as a completed comparison')
        return keyed
    baseline, alternative = keyed(app), keyed(experimental)
    if baseline.keys() != alternative.keys():
        raise ValueError('Question/seed pairs differ')
    pairs = []
    for key, row in baseline.items():
        other = alternative[key]
        def summary(run):
            return dict(retrievalMs=run['metrics']['retrievalMillis'], totalMs=run['metrics']['totalMillis'],
                        sourceChunkIds=[source['chunkId'] for source in run['sources']],
                        inferenceCalls=len(run['calls']), hasUnmappedCitation=run['metrics']['hasUnmappedCitation'],
                        visibleAnswer=run['visibleAnswer'])
        engine = [trace for trace in other['experimentalSearches'] if trace['backend'] == 'leviathan']
        if any(trace.get('fullPassagesVerified') is not True for trace in engine):
            raise ValueError('Unverified supporting passages')
        pairs.append(dict(question=key[0], seed=key[1], app=summary(row), leviathan=summary(other),
                          engineCalls=len(engine), candidateMs=sum(t['candidateMs'] for t in engine),
                          hydrationMs=sum(t['hydrationMs'] for t in engine),
                          appRankingMs=sum(t.get('appRankingMs', 0) for t in engine),
                          appTitleAnchorIds=[i for t in engine for i in t.get('appTitleAnchorIds', [])],
                          cardChars=sum(t['cardChars'] for t in engine),
                          fullPassageChars=sum(t['fullPassageChars'] for t in engine),
                          sameVisibleAnswer=row['visibleAnswer'] == other['visibleAnswer']))
    return dict(scope='desktop_current_app_pipeline_single_pack_development_queries', productionAdopted=False,
                qualityVerdict='UNSCORED: completion, citation presence and timing are not correctness',
                limitations=['Single run per question/seed; no latency distribution or controlled cache/CPU study.',
                             'Specialized app routes remain app-backed; engineCalls reports actual Leviathan calls.',
                             'When appTitleAnchorIds is nonempty, selection is app-assisted title ranking, not pure upstream search. appRankingMs includes that cost.',
                             'Full original passages enter unchanged app prompt packing; compact-card size is not inference-token savings.',
                             'Not Android/JNI integration, multi-pack or 20–50GB acceptance; index storage must count toward 50GB.',
                             'Development queries, not an independent held-out accuracy set.'], pairs=pairs)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--experimental', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(compare(json.loads(args.app.read_text()), json.loads(args.experimental.read_text())), indent=2))
