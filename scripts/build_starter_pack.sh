#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
output_dir="$repo_root/build/packs/starter"

if [[ -e "$output_dir" ]]; then
  echo "Refusing to replace existing output: $output_dir" >&2
  echo "Move or remove that generated directory, then rerun." >&2
  exit 1
fi

cd "$repo_root"
python3 -m packtool.build_pack \
  --input fixtures/starter/documents.jsonl \
  --output "$output_dir" \
  --id fieldatlas-starter \
  --version 1.0.0 \
  --title "Field Atlas Starter Evidence" \
  --license CC0-1.0 \
  --source-url https://creativecommons.org/publicdomain/zero/1.0/legalcode \
  --coverage-level demo \
  --coverage-summary "Four short demo notes about seasons, water safety, solar storage, and comparing evidence." \
  --example-question "Why do Earth's hemispheres have opposite seasons?" \
  --example-question "What does boiling water remove, and what can remain?" \
  --example-question "When can battery storage help a solar-heavy grid?"

(cd "$output_dir" && shasum -a 256 -c SHA256SUMS)
