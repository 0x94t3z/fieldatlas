#!/usr/bin/env python3
"""Build a deterministic Field Atlas model pack from a verified GGUF file."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import zipfile

CHUNK_BYTES = 1024 * 1024
FIXED_TIME = (1980, 1, 1, 0, 0, 0)
CONFIG_FIELDS = {
    "id",
    "version",
    "title",
    "license",
    "sourceUrls",
    "profile",
    "upstreamModel",
    "converter",
    "quantizer",
    "llamaCppCommit",
    "expectedSha256",
}
IDENTIFIER = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z")


def file_digest(path: Path) -> tuple[int, str]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as source:
        while chunk := source.read(CHUNK_BYTES):
            size += len(chunk)
            digest.update(chunk)
    return size, digest.hexdigest()


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()


def strict_json_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    value: dict[str, object] = {}
    for key, item in pairs:
        if key in value:
            raise ValueError(f"duplicate JSON field: {key}")
        value[key] = item
    return value


def zip_info(name: str, size: int) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    info.file_size = size
    return info


def validate_config(config: dict[str, object]) -> None:
    unknown = set(config) - CONFIG_FIELDS
    missing = CONFIG_FIELDS - set(config)
    if unknown or missing:
        raise ValueError(f"config fields mismatch; missing={sorted(missing)}, unknown={sorted(unknown)}")
    for field in ("id", "version", "title", "license", "profile", "upstreamModel", "converter", "quantizer", "llamaCppCommit"):
        if not isinstance(config[field], str) or not str(config[field]).strip():
            raise ValueError(f"{field} must be a non-blank string")
    for field in ("id", "version"):
        if not IDENTIFIER.fullmatch(str(config[field])) or config[field] in (".", ".."):
            raise ValueError(f"{field} must be a safe pack identifier")
    commit = str(config["llamaCppCommit"])
    if len(commit) != 40 or any(character not in "0123456789abcdef" for character in commit):
        raise ValueError("llamaCppCommit must be a 40-character lowercase Git hash")
    urls = config["sourceUrls"]
    if not isinstance(urls, list) or not urls or not all(
        isinstance(url, str) and url.startswith("https://") for url in urls
    ):
        raise ValueError("sourceUrls must be a non-empty HTTPS URL list")
    expected_hash = config["expectedSha256"]
    if not isinstance(expected_hash, str) or len(expected_hash) != 64 or any(
        character not in "0123456789abcdef" for character in expected_hash
    ):
        raise ValueError("expectedSha256 must be 64 lowercase hexadecimal characters")


def build_pack(model: Path, config_path: Path, output: Path) -> dict[str, object]:
    if not model.is_file():
        raise ValueError(f"model is not a file: {model}")
    config = json.loads(config_path.read_text(encoding="utf-8"), object_pairs_hook=strict_json_object)
    if not isinstance(config, dict):
        raise ValueError("config must be a JSON object")
    validate_config(config)
    model_size, model_hash = file_digest(model)
    if model_size <= 0:
        raise ValueError("model must not be empty")
    if model_hash != config["expectedSha256"]:
        raise ValueError(f"model SHA-256 mismatch: expected {config['expectedSha256']}, got {model_hash}")

    provenance = dict(config)
    provenance.update({"bytes": model_size, "sha256": model_hash})
    provenance_bytes = canonical_json(provenance)
    provenance_hash = hashlib.sha256(provenance_bytes).hexdigest()
    manifest = {
        "artifacts": [
            {"bytes": model_size, "path": "model.gguf", "sha256": model_hash},
            {"bytes": len(provenance_bytes), "path": "provenance.json", "sha256": provenance_hash},
        ],
        "id": config["id"],
        "license": config["license"],
        "schemaVersion": 1,
        "sourceUrls": config["sourceUrls"],
        "title": config["title"],
        "type": "MODEL",
        "version": config["version"],
    }
    manifest_bytes = canonical_json(manifest)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix=output.name, suffix=".tmp", dir=output.parent, delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(temporary_path, "w", allowZip64=True) as archive:
            archive.writestr(zip_info("manifest.json", len(manifest_bytes)), manifest_bytes)
            with archive.open(zip_info("model.gguf", model_size), "w", force_zip64=True) as destination:
                with model.open("rb") as source:
                    shutil.copyfileobj(source, destination, CHUNK_BYTES)
            archive.writestr(zip_info("provenance.json", len(provenance_bytes)), provenance_bytes)
        os.replace(temporary_path, output)
    finally:
        temporary_path.unlink(missing_ok=True)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    manifest = build_pack(args.model, args.config, args.output)
    print(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2))


if __name__ == "__main__":
    main()
