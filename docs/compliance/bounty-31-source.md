# Build the Best Offline AI Research App for Android ⛺

Source: [POIDH bounty 31](https://poidh.xyz/mainnet/bounty/31)

## What Vitalik is looking for

Build a casual info lookup and research tool that runs entirely offline on Android and is >50% as good as internet search + frontier AI models. People travel with their phones into offline situations all the time, and if you're literally offline, remote inference doesn't help.

His past attempts got ~10 tokens/sec from 1B models that break on anything interesting. He suggests extreme MoE might be the right architecture for phones (including newer variants like n-gram models): something like ~100B params, most living on disk, with <1B activated per token.

That's one promising direction, not a requirement. Any architecture that gets there is welcome.

OG post: [Vitalik Buterin on X](https://x.com/VitalikButerin/status/2100695863026954698)

*Note: This is an independent community bounty inspired by Vitalik Buterin's post. He is not affiliated with it or involved in judging unless he chooses to weigh in.*

## Requirements

The submission must:

- run on Android and compatible GrapheneOS hardware
- operate within a maximum **12GB RAM environment**
- use no more than **50GB total** for the app, model weights, indexes, databases, and other offline assets (streaming weights from disk is expected)
- work completely offline once installed
- make no API calls, remote inference requests, web searches, or other network requests during use
- not require Google Play Services for core offline functionality
- handle useful research questions beyond simple factual recall, including explanation, comparison, synthesis, and reasoning
- respond at speeds that feel usable for real lookups on a phone
- be published in a public GitHub repository
- include all code, assets, dependencies, and instructions needed to reproduce the submission
- clearly document the models, datasets, indexes, and other resources used

Architecture is open. LLMs, RAG, MoE, n-grams, compressed knowledge bases, custom retrieval systems, or other approaches are allowed.

There is no parameter-count limit.

## Real-device requirement

The app must work on real Android hardware at the time of submission.

Someone with a compatible Android or GrapheneOS device should be able to visit the GitHub repo, follow the instructions, and get the app running locally within a few minutes without substantial debugging.

Any required models, databases, indexes, or other assets must either be included or have clear download and installation instructions.

## Proof

Post a public demo on **X or Farcaster** showing:

- the app running offline
- several example queries and responses (include ones a 1B model would fail on)
- a link to the public GitHub repo
- a brief explanation of the approach

Submit a relevant screenshot to POIDH along with a link to the post and the GitHub repository.

The GitHub repository must contain the functional submitted version when the POIDH claim is made.

## Winner 🏆

Submissions are judged against the requirements above and the bar described in Vitalik's post: a useful, fully offline research tool that's >50% as good as internet + frontier models.

If Vitalik publicly confirms that a submitted build meets that bar, that submission wins the entire prize pot, and the bounty creator will accept the confirmed claim. The confirmation must clearly refer to the submitted project or repository.

Otherwise, if no submission has been confirmed by October 31st, 2026, the creator and bounty contributors will either:

- wind down the bounty and return contributed funds
- select a winner they believe best satisfies the requirements and spirit of Vitalik's request

Submissions that are fraudulent, malicious, plagiarized, materially different from the version reviewed, or in violation of the requirements above may be disqualified.
