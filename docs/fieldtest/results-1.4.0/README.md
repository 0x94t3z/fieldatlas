# Field test results, 1.4.0

Every answer behind the 1.4.0 figures in [evaluation.md](../../evaluation.md#140-on-60-new-held-out-questions-october-7), with the
reference answer and both blind grades. One JSON object per line:

- `id`, `category`, `question`, and `gps` for a question asked from a position (latitude,
  longitude), read through the same path as a phone's fix;
- `field_atlas`: the answer as shown on the phone, from the desktop pipeline running the phone's
  code with Qwen3.5 2B (or the optional 4B where the file says so), every collection the app offers
  in 1.4.0 and article depth on, as in the app;
- `field_atlas_score`: its grade from 0 to 10, the mean of two graders, each grading the answer
  on its own without the reference beside it;
- `field_atlas_errors`: the factual errors the graders listed;
- `reference` and `reference_score`: the answer of a frontier model with web search, standing in
  for "internet search plus a frontier AI model", graded the same way by other graders.

A set's share is the sum of `field_atlas_score` over the sum of `reference_score`.

| File | Questions |
| --- | --- |
| [`heldout3.jsonl`](heldout3.jsonl) | 60 held-out questions written after the last change, the 2B ([questions](../heldout3.jsonl)) |
| [`heldout3-4b.jsonl`](heldout3-4b.jsonl) | The same questions without the near-phone ones, the optional Qwen3.5 4B |
| [`heldout2.jsonl`](heldout2.jsonl) | The 48 questions of 6 October, now development data ([questions](../heldout2.jsonl)) |
| [`questions.jsonl`](questions.jsonl) | Our 61 development questions ([questions](../questions.jsonl)) |
| [`heldout.jsonl`](heldout.jsonl) | The 42 earlier held-out questions, now development data ([questions](../heldout.jsonl)) |
| [`vitalik.jsonl`](vitalik.jsonl) | The 31 questions of the vbuterin fork's end-to-end tests ([`e2e_cases.json`](../../../tools/e2e_cases.json), [`e2e_battery_cases.json`](../../../tools/e2e_battery_cases.json)) |

References and graders are frontier AI models of one family, so these are development measures,
not an independent score. A grader scores the same text differently from run to run, by about
2 points on one answer.
