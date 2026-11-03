# Contributing to Field Atlas

Field Atlas answers questions on an Android phone with no signal: the model, the search and the
collections all run on the phone. Help is welcome, whether you write code or not.

## Ways to help

- **Report a wrong or unsafe answer.** These are the most useful reports we get. Include the
  question exactly as you typed it, the model (Qwen3.5 2B, Qwen3.5 4B, or the name of a model you imported), the
  collections you had installed, the app version from **More**, and a screenshot of the answer
  and its sources. Mark it **safety** if it concerns first aid or an emergency.
- **Share how it runs on your phone.** The phone model, its memory, Android version, the model you
  used, and how long a few answers took from asking to the complete answer, in airplane mode. The
  [timing template](docs/fieldtest/device-timings/TEMPLATE.md) has the questions to ask.
  Only a 6 GB Redmi 13C has been timed properly so far; numbers from other phones, especially 4 GB, 8 GB
  and 12 GB ones, help everyone choose a model.
- **Build a knowledge collection.** A `.fapack` for a region, a field or a trail network. The
  [pack tools](tools/README.md) and [DATASETS.md](DATASETS.md) show how the current collections
  were built and checked. Sources must allow redistribution (for example CC BY, CC0 or the Open
  Government Licence) and every pack carries its attribution.
- **Fix something.** Bugs, accessibility, speed on phones with 6 GB of memory or less, and clearer
  wording are all good places to start. Open an issue first for anything larger than a small fix,
  so we can agree on the approach before you spend time on it.
- **Improve the docs or the website.** The site lives in [`website/`](website/) and deploys when
  `main` changes.

## Ground rules

These keep Field Atlas what it says it is.

- **Offline first.** Nothing a person asks, reads or attaches leaves the phone. Network use is
  limited to downloads the person starts and the collection list refresh. A change that adds any
  other network call will not be merged.
- **Answers show their sources.** A change to how answers are found or written keeps the citations
  and the checks against the saved passages.
- **Emergency content comes from official public guides** (for example NHS, CDC, MedlinePlus),
  quoted as published. Do not write or paraphrase first-aid steps yourself.
- **No accounts, ads or tracking.**
- **Measure before claiming better.** Changes meant to improve answers are judged on fresh questions
  the change was not developed against; see [how 1.4.2 was tested](docs/testing-1.4.2.md).

## Building and testing

Follow [Build Field Atlas from source](docs/building.md): JDK 17, Android SDK 36, NDK
29.0.13113456, Rust 1.90.0, and the pinned llama.cpp submodule with its patch applied once.

Before opening a pull request, run what applies to your change:

```sh
./gradlew --no-configuration-cache :app:testDebugUnitTest :app:lintRelease   # app changes
python3 -m unittest discover -s scripts/tests                                 # docs and scripts
python3 -m unittest discover -s tools/tests                                   # pack tools
(cd website && npm test)                                                      # website
(cd tools/rewards && npm ci && npm test)                                      # contributor rewards
```

A debug build (`xyz.fieldatlas.debug`) installs beside the published app and does not replace it.

## Pull requests

- Keep each pull request to one change, with a short description of what it changes and how you
  checked it (tests run, phone and model used).
- Add or update tests for behaviour you change.
- Large files (models, collections, recordings) do not go in the repository; document their source
  and checksum instead.
- By contributing you agree that your work is released under this repository's
  [Apache License 2.0](LICENSE).

## Contributor rewards

ATLAS is a token on Base (contract `0xb462a5039a540883508ee37b5cf3999647557ba3`), launched by the
community on Bankr, with its creator fees and creator allocation going to the Field Atlas dev.
**Field Atlas adopted it to reward contributors; it did not create or sell it, and no token is needed
to use the app.** Nothing here is financial advice or says anything about its price.

**Disclosure:** the dev's wallets receive the ATLAS creator fees and the launch's creator allocation
(15 billion ATLAS vesting until 26 September 2027). 1 billion ATLAS from the dev's holdings was put into the
separate contributor pool. The rest belongs to the dev, to use as they choose, including funding further
work on Field Atlas.

**A second token.** On 3 November 2026 the maintainer launched a separate token, also named ATLAS, on
Robinhood Chain through Pons (contract `0xe7eeD5f62d4813073Ff38784dc775e723dfc165c`). It is not the Base
ATLAS and does not pay contributor rewards. Its 2% creator fee goes to the maintainer's wallet
`0xc698865c38eC12b475AA55764d447566dd54c758` and funds building Field Atlas: tools, servers, maintenance,
testing and app store publishing. The maintainer bought 112,707,299 of its 1,000,000,000 tokens at launch
for 0.22 ETH and 16,830,285 more the same day, then burned all 129,537,584 on 3 November 2026
([79,537,584](https://robinhoodchain.blockscout.com/tx/0x9c878e918db33da6a85eadffeb04df6aa8ecc626db53caec4430c93fa93201eb),
[50,000,000](https://robinhoodchain.blockscout.com/tx/0x799065760fa3bdfeddab2a3ec0794e355e8002e56683c39ef9a7b0ff0920265c))
and holds none of it. No token is needed to use the app, and nothing here says anything about its price.

ATLAS may be used to thank contributors, under these rules:

- **One pool pays.** 1,000,000,000 ATLAS was put into a separate, public pool wallet on 1 November 2026,
  and rewards come only from it, never from the dev's own holding. The pool wallet is a Safe on Base:
  `0x527C6fb80013882280CE4DB88E720F0fd8b1AEa2`. Only the maintainer signs for it. It is kept apart so anyone
  can check its balance and payments, but it is not locked. Check the whole address before trusting it, and
  never copy it from a transaction history.
- **Only accepted work is rewarded:** a merged pull request, an accepted knowledge collection, or a
  wrong-answer, safety or device report that leads to a fix. Posts, promotion and follower counts
  are never rewarded.
- **Amounts are announced before the work, not after.** Rewarded issues are opened by the maintainer,
  carry the `reward` label, say what makes the work accepted, and end with a line such as
  `Reward: 5,000,000 ATLAS`. For a larger change agreed in a discussion, the maintainer opens a new `reward`
  issue that links it. Reports (below) are the one exception: they are paid the table's amount once they
  lead to a fix.
- **Usual amounts.** The issue's amount is what is paid; as a guide:

  | Work | ATLAS |
  |---|---|
  | A small fix, a docs or wording fix, or a phone timing report (through its `reward` issue) | 5,000,000 |
  | A feature or speed fix, or a wrong-answer or safety report that leads to a fix | 20,000,000 |
  | An accepted knowledge collection, or a larger change agreed in its issue first | 50,000,000 |
- **One person per task.** Comment "taking this" on the issue and wait to be assigned; only the one
  assigned person is paid. If there is no pull request within 7 days, the maintainer unassigns it and
  the task opens up again. A pull request opened before its author is assigned is not paid.
- **A real phone, shown.** A timing or device report must come from a real phone, not an emulator. When
  the maintainer assigns the task, the assignment comment gives a code word. The pull request then adds,
  besides the template's screenshots:
  - a photo of the phone taken with another camera, showing Field Atlas open with the code word typed in
    the question box, the phone's edge or back in view;
  - a screen recording of under a minute: **Settings › About phone** (model and memory), then Field Atlas
    **More** (version and phone), then the code word typed in the question box.

  Every screenshot, photo and recording is uploaded to the pull request on GitHub (drag it into the
  description); links to images hosted elsewhere are not accepted, since they can change. Black out the
  phone number, serial number and IMEI wherever they show.
- **To be paid,** put these two lines in your pull request's description, as plain lines (not inside a
  comment or a code block, or the check cannot see them). The pull request template has a place for them.

      Closes #<issue number>
      Base address: 0x<your Base address>

  Use a Base address you control, not an exchange deposit address, and give only one. When the pull
  request is merged into `main`, a check confirms the rules below, queues the payment in the pool wallet
  and comments on the pull request; if a rule fails, the comment says which. The maintainer then confirms
  the payment. One pull request is paid for one `reward` issue. A pull request that changes
  `website/rewards.json`, `tools/rewards/` or anything under `.github/`, renames a file, or changes more than
  100 files, is not paid.
  The zero address, the token and the pool itself cannot receive a reward.
  Nobody from Field Atlas will ever ask for a key, a seed phrase or a payment.
- **Reports.** A wrong-answer or safety report has no pull request. When one leads to a merged
  fix, the maintainer says so on the report; reply there with `Base address: 0x…`. The maintainer pays it
  by hand from the pool (20,000,000 ATLAS, as in the table) and lists it in the ledger like every other
  payment.
- **Limits.** At most 50,000,000 ATLAS for one task; at most 50,000,000 ATLAS to one person (one GitHub
  account or one address) in a calendar month; and 100,000,000 ATLAS to everyone in a month, so the pool
  lasts. A payment counts in the calendar month (UTC) in which it is queued; one refused for a monthly limit
  is checked again on request. Maintainers are not paid from the pool.
- **Every payment is public.** Payments are listed in [`website/rewards.json`](website/rewards.json),
  each with the work it is for and its Base transaction, and shown on the
  [website](https://www.getfieldatlas.com/#community) with the contributor's GitHub name (the address is
  in `rewards.json`).
  The first entry, 1,000 ATLAS sent back to the maintainer's own wallet on 1 November 2026, was a test
  of the payment path.
- **The maintainer decides** whether work qualifies, and may change or end the program at any time.
  Unpaid amounts are not owed.
- **Taxes are your own responsibility** where you live.

Beware of anyone claiming to sell an "official" Field Atlas token or asking you to send funds.

## Security

If you find a security problem (for example a pack that escapes its folder on import, or data
leaving the phone), please report it privately through GitHub's
[private vulnerability reporting](https://github.com/0x94t3z/fieldatlas/security) rather than in a
public issue.

## Conduct

Be kind and specific. Critique the change, not the person. Maintainers may close discussions that
turn hostile.
