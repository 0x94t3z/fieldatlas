# Contributing to Field Atlas

Field Atlas answers questions on an Android phone with no signal. The model, the search and the collections all run
on the phone. You can help whether you write code or not.

- [Ways to help](#ways-to-help)
- [Ground rules](#ground-rules)
- [Build and test](#build-and-test)
- [Pull requests](#pull-requests)
- [Contributor rewards](#contributor-rewards)
- [Security](#security)
- [Conduct](#conduct)

## Ways to help

### Report a wrong or unsafe answer

These are the most useful reports we get. Open an issue with:

- the question, exactly as you typed it
- the model: Qwen3.5 2B, Qwen3.5 4B, or the name of a model you imported
- the collections you had installed
- the app version, shown in **More**
- a screenshot of the answer and its sources

If it is about first aid or an emergency, mark it **safety**.

### Time the app on your phone

Tell us your phone model, its memory, its Android version and the model you used. Then time a few answers in airplane
mode, from asking to the complete answer. The [timing template](docs/fieldtest/device-timings/TEMPLATE.md) lists the
questions to ask.

So far only one phone has been timed properly: a Redmi 13C with 6 GB. Timings from other phones, especially ones with
4 GB, 8 GB or 12 GB, help people choose a model.

### Build a knowledge collection

A collection is a `.fapack` file, for example for a region, a field of study or a trail network. The
[pack tools](tools/README.md) and [DATASETS.md](DATASETS.md) show how the current collections were built and checked.
The sources must allow sharing (for example CC BY, CC0 or the Open Government Licence), and every pack credits them.

### Fix something

Good places to start: bugs, accessibility, speed on phones with 6 GB of memory or less, and clearer wording. For
anything bigger than a small fix, open an issue first so we can agree on the approach before you spend time on it.

### Improve the docs or the website

The website lives in [`website/`](website/). It goes live when `main` changes.

## Ground rules

These keep Field Atlas what it says it is.

- **Offline first.** Nothing a person asks, reads or attaches leaves the phone. The app only uses the network for
  downloads the person starts and for refreshing the list of collections. A change that adds any other network call
  will not be merged.
- **Answers show their sources.** If you change how answers are found or written, keep the citations and the checks
  against the saved passages.
- **First-aid steps come from official public guides**, such as the NHS, the CDC and MedlinePlus, quoted as they were
  published. Do not write or reword first-aid steps yourself.
- **No accounts, ads or tracking.**
- **Measure before you claim something is better.** A change meant to improve answers is tested on new questions that
  were not used while building it. See [how 1.4.2 was tested](docs/testing-1.4.2.md) for an example.

## Build and test

Follow [Build Field Atlas from source](docs/building.md). You need JDK 17, Android SDK 36, NDK 29.0.13113456,
Rust 1.90.0, and the pinned llama.cpp submodule with its patch applied once.

Before you open a pull request, run the tests for the part you changed:

```sh
./gradlew --no-configuration-cache :app:testDebugUnitTest :app:lintRelease   # the app
python3 -m unittest discover -s scripts/tests                                 # docs and scripts
python3 -m unittest discover -s tools/tests                                   # pack tools
(cd website && npm test)                                                      # website
(cd tools/rewards && npm ci && npm test)                                      # contributor rewards
```

A debug build (`xyz.fieldatlas.debug`) installs next to the published app and does not replace it.

## Pull requests

- One change per pull request. Say what it changes and how you checked it: the tests you ran, and the phone and
  model you used.
- Add or update tests for any behaviour you change.
- Keep large files (models, collections, recordings) out of the repository. Write down where they come from and
  their checksum instead.
- By contributing, you agree that your work is released under this repository's [Apache License 2.0](LICENSE).

## Contributor rewards

Some work is paid in ATLAS, a token on Base. You never need a token to use the app. Nothing in this section is
financial advice or says anything about the token's price.

### Where ATLAS comes from

ATLAS on Base (contract `0xb462a5039a540883508ee37b5cf3999647557ba3`) was launched by the community on Bankr. Its
creator fees and creator allocation go to the Field Atlas dev. Field Atlas did not create or sell it; it adopted the
token to pay contributors.

**Disclosure.** The dev's wallets receive the creator fees and the creator allocation: 15 billion ATLAS, vesting until
26 September 2027. 1 billion ATLAS from the dev's holdings was moved into a separate contributor pool. The rest
belongs to the dev, who may use it as they choose, including to fund more work on Field Atlas.

**A second token.** On 3 November 2026 the maintainer launched a different token, also called ATLAS, on Robinhood Chain
through Pons (contract `0xe7eeD5f62d4813073Ff38784dc775e723dfc165c`). It is not the Base ATLAS and is never used for
rewards. Its 2% creator fee goes to the maintainer's wallet: half buys and burns the token, half pays for work on
Field Atlas. The launch was paid for by selling Base ATLAS.

The maintainer's wallets are:

- `0xc698865c38eC12b475AA55764d447566dd54c758`: main wallet
- `0x72d14fb194624b835a91b914e8dbc2abac250a6a`: Bankr wallet, which receives the Base creator fees
- `0xA06e309E264C9D3b77CE6CD91AC992ba1D719756`: second wallet

Their holdings and trades in both tokens are public on [Basescan](https://basescan.org) and the
[Robinhood Chain explorer](https://robinhoodchain.blockscout.com).

### The pool

Rewards are paid only from the contributor pool, never from the dev's own holdings. On 1 November 2026,
1,000,000,000 ATLAS was put into the pool, a Safe wallet on Base:

`0x527C6fb80013882280CE4DB88E720F0fd8b1AEa2`

Only the maintainer signs for it. It is kept separate so anyone can check its balance and payments, but it is not
locked. Check the whole address before you trust it, and never copy it from a transaction history.

### What gets paid

- A merged pull request for a `reward` issue
- An accepted knowledge collection
- A wrong-answer, safety or device report that leads to a fix

Posts, promotion and follower counts are never paid.

### How much

The amount is set before the work starts. The maintainer opens a rewarded issue with the `reward` label, says what
makes the work accepted, and ends it with a line like `Reward: 5,000,000 ATLAS`. That amount is what is paid. For a
larger change agreed in a discussion, the maintainer opens a new `reward` issue that links to it.

Reports are the one exception: they have no issue in advance and are paid the amount below once they lead to a fix.

Usual amounts:

| Work | ATLAS |
|---|---|
| A small fix, a docs or wording fix, or a phone timing report (through its `reward` issue) | 5,000,000 |
| A feature or speed fix, or a wrong-answer or safety report that leads to a fix | 20,000,000 |
| An accepted knowledge collection, or a larger change agreed in its issue first | 50,000,000 |

### Taking a task

1. Comment "taking this" on the issue.
2. Wait to be assigned. Only the assigned person is paid.
3. Open your pull request within 7 days. If there is none by then, the maintainer unassigns you and the task opens
   up again.

A pull request opened before its author was assigned is not paid.

### Proof from a real phone

A timing or device report must come from a real phone, not an emulator. When the maintainer assigns you, the
assignment comment gives you a code word. Besides the template's screenshots, your pull request adds:

- **A photo of the phone, taken with another camera.** Field Atlas is open with the code word typed in the question
  box, and the edge or back of the phone is in view.
- **A screen recording under one minute.** Show **Settings › About phone** (model and memory), then Field Atlas
  **More** (version and phone), then the code word typed in the question box.

Upload every screenshot, photo and recording to the pull request on GitHub by dragging it into the description. Links
to images hosted elsewhere are not accepted, because they can change. Black out the phone number, serial number and
IMEI wherever they show.

### Getting paid for a pull request

Put these two lines in your pull request's description. They must be plain lines, not inside a comment or a code
block, or the check cannot see them. The pull request template has a place for them.

    Closes #<issue number>
    Base address: 0x<your Base address>

Use one Base address that you control, not an exchange deposit address.

When the pull request is merged into `main`, a check confirms the rules on this page, queues the payment in the pool
and comments on the pull request. If a rule fails, the comment says which one. The maintainer then confirms the
payment.

A pull request is not paid if it:

- changes `website/rewards.json`, `tools/rewards/` or anything under `.github/`
- renames a file
- changes more than 100 files

One pull request is paid for one `reward` issue. The zero address, the token contract and the pool itself cannot
receive a reward.

### Getting paid for a report

A report has no pull request. When it leads to a merged fix, the maintainer says so on the report. Reply there with
`Base address: 0x…`. The maintainer pays 20,000,000 ATLAS by hand from the pool and lists it with every other
payment.

### Limits

These keep the pool going:

- at most 50,000,000 ATLAS for one task
- at most 50,000,000 ATLAS to one person (one GitHub account or one address) in a calendar month
- at most 100,000,000 ATLAS to everyone in a calendar month

A payment counts in the calendar month (UTC) in which it is queued. If a payment is refused because a monthly limit
was reached, ask and it will be checked again in a later month. Maintainers are not paid from the pool.

### Every payment is public

Every payment is listed in [`website/rewards.json`](website/rewards.json) with the work it paid for, the address and
its Base transaction. The [website](https://www.getfieldatlas.com/#community) shows each one with the contributor's
GitHub name. The first entry, 1,000 ATLAS sent back to the maintainer's own wallet on 1 November 2026, was a test of
the payment path.

### Also note

- The maintainer decides whether work qualifies, and may change or end the program at any time. Unpaid amounts are
  not owed.
- Taxes are your own responsibility where you live.
- Nobody from Field Atlas will ever ask for a key, a seed phrase or a payment.
- Be wary of anyone who claims to sell an "official" Field Atlas token or asks you to send funds.

## Security

If you find a security problem, for example a pack that escapes its folder on import or data leaving the phone,
report it privately through GitHub's [private vulnerability reporting](https://github.com/0x94t3z/fieldatlas/security)
instead of opening a public issue.

## Conduct

Be kind and specific. Criticise the change, not the person. Maintainers may close discussions that turn hostile.
