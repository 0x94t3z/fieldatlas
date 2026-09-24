# Field Atlas paired benchmark — 2026-09-24

This is the recorded exploratory comparison for the current Field Atlas
Reference build (`1.1.6`, Qwen3 1.7B Q4_K_M) against OpenRouter's dynamic
`openrouter/free` route.

## Result

The same frozen 18-question set in `benchmarks/questions-v1.json` was run on
the Infinix device and then sent to the online baseline with temperature 0.
The repository scorer reported:

| Run | Overall score |
| --- | ---: |
| Field Atlas offline | 0.027778 |
| OpenRouter `openrouter/free` | 0.194444 |
| Offline / baseline ratio | 0.142859 (14.3%) |

Category means and per-question details are in the local generated artifact
`build/evidence/paired-score.json`. The raw device export is
`build/evidence/device-run.json`; the scorer inputs are
`build/evidence/offline-results.json` and
`build/evidence/baseline-results.json`.

## Interpretation

This run is transparent exploratory evidence, not an issuer determination.
`openrouter/free` is a dynamic router whose provider/model can change and can
be rate-limited. The result does not establish the bounty's greater-than-50%
quality bar; B31-20 therefore remains blocked. A future pinned-model run should
record the exact model/provider and preserve the same raw artifacts.
