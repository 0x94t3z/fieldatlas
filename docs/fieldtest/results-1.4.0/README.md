# Field test results, 1.4.0

Every answer behind the 1.4.0 figures in [evaluation.md](../../evaluation.md#140-release-build-on-fresh-questions-october-13), with the
reference answer and both blind grades. One JSON object per line:

- `id`, `category`, `question`, and `gps` for a question asked from a position (latitude,
  longitude), read through the same path as a phone's fix;
- `field_atlas`: the answer as shown on the phone, from the desktop pipeline running the phone's
  code with Qwen3.5 2B (or the optional 4B where the file says so), every collection the app offered
  at the time and article depth on, as in the app (sets 4 to 6 also with the phone's native search);
- `field_atlas_score`: its grade from 0 to 10, the mean of two graders, each grading the answer
  on its own without the reference beside it (sets 4 to 6: the mean of two independent grading rounds);
- `field_atlas_errors`: the factual errors the graders listed;
- `reference` and `reference_score`: the answer of a frontier model with web search, standing in
  for "internet search plus a frontier AI model", graded the same way by other graders.

A set's share is the sum of `field_atlas_score` over the sum of `reference_score`. The files hold
each answer's mean over the two rounds; the per-round shares in evaluation.md were computed from the
rounds' own grades, which are not published separately.

| File | Questions |
| --- | --- |
| [`heldout6.jsonl`](heldout6.jsonl) | **The 1.4.0 release build** on 60 questions written after it was frozen (set 6), the 2B ([questions](../heldout6.jsonl)) |
| [`heldout6-4b.jsonl`](heldout6-4b.jsonl) | The release build, set 6 without the near-phone questions, the optional Qwen3.5 4B |
| [`heldout5.jsonl`](heldout5.jsonl) | Set 5, measured on the build before the last place fixes; now development data ([questions](../heldout5.jsonl)) |
| [`heldout4-candidate.jsonl`](heldout4-candidate.jsonl) | Set 4, measured on the first 1.4.0 release candidate, which withheld 25 answers; now development data ([questions](../heldout4.jsonl)) |
| [`heldout4-7oct-build.jsonl`](heldout4-7oct-build.jsonl) | Set 4 answered again by the 7 October candidate with its own collections and keyword search, graded blind together with the next file (each score the mean of two graders; `reference_score` is the candidate file's) |
| [`heldout4-fixed-path.jsonl`](heldout4-fixed-path.jsonl) | Set 4 answered by the build with the answer-path fix, graded with the file above |
| [`heldout3.jsonl`](heldout3.jsonl) | The 7 October candidate on its 60 held-out questions (set 3), the 2B ([questions](../heldout3.jsonl)) |
| [`heldout3-4b.jsonl`](heldout3-4b.jsonl) | The 7 October candidate, set 3 without the near-phone questions, the optional Qwen3.5 4B |
| [`heldout2.jsonl`](heldout2.jsonl) | The 48 questions of 6 October, now development data ([questions](../heldout2.jsonl)) |
| [`questions.jsonl`](questions.jsonl) | Our 61 development questions ([questions](../questions.jsonl)) |
| [`heldout.jsonl`](heldout.jsonl) | The 42 earlier held-out questions, now development data ([questions](../heldout.jsonl)) |
| [`vitalik.jsonl`](vitalik.jsonl) | The 31 questions of the vbuterin fork's end-to-end tests ([`e2e_cases.json`](../../../tools/data/e2e_cases.json), [`e2e_battery_cases.json`](../../../tools/data/e2e_battery_cases.json)) |

References and graders are frontier AI models of one family, so these are development measures,
not an independent score. A grader scores the same text differently from run to run, by about
2 points on one answer.
