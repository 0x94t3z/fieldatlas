#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
output_dir="$repo_root/build/packs/reference"
if [[ -e "$output_dir" ]]; then
  echo "Refusing to replace existing output: $output_dir" >&2
  exit 1
fi
cd "$repo_root"
python3 -m packtool.build_pack \
  --input fixtures/reference/documents.jsonl \
  --output "$output_dir" \
  --id fieldatlas-reference \
  --version 1.0.0 \
  --title "Field Atlas Reference" \
  --license CC0-1.0 \
  --source-url https://creativecommons.org/publicdomain/zero/1.0/legalcode \
  --coverage-level focused \
  --coverage-summary "Twelve compact CC0 reference notes across science, practical safety, transport, computing, health, and history." \
  --example-question "Why do Earth's hemispheres have opposite seasons?" \
  --example-question "How does Formula One work?" \
  --example-question "What is the difference between weather and climate?"
(cd "$output_dir" && shasum -a 256 -c SHA256SUMS)
