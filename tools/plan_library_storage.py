#!/usr/bin/env python3
"""Offline, checksum-pinned storage planning. Does not install or download anything.

Verifies existing archives, then models sequential imports/downloads while retaining
old versions. Input currentBytes must include APK/native libraries, all app data,
disabled packs, partial downloads and caches. This is NOT a runtime peak measurement.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import zipfile

LIMIT = 50_000_000_000
METADATA_RESERVE = 1_048_576
DEVICE_HEADROOM = 512_000_000
PIN = re.compile(r"[0-9a-f]{64}\Z")


def integer(value, name: str, minimum: int = 0) -> int:
    if type(value) is not int or not minimum <= value <= 2**63 - 1:
        raise ValueError(f"Invalid {name}: expected an integer byte count >= {minimum}")
    return value


def digest(stream) -> tuple[int, str]:
    size, result = 0, hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b""):
        size += len(block)
        result.update(block)
    return size, result.hexdigest()


def measure_archive(path: Path, expected_sha256: str) -> dict:
    if not isinstance(expected_sha256, str) or not PIN.fullmatch(expected_sha256):
        raise ValueError("A pinned lowercase SHA-256 is required")
    with path.open("rb") as stream:
        archive_bytes, archive_hash = digest(stream)
    if archive_hash != expected_sha256:
        raise ValueError("Archive checksum mismatch")
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        if not entries or len(entries) > 100_001 or entries[0].filename != "manifest.json":
            raise ValueError("Manifest must be first; archive entry count is bounded")
        if not 1 <= entries[0].file_size <= 1_048_576:
            raise ValueError("Manifest exceeds its bounded size")
        names = [entry.filename for entry in entries]
        if len(set(names)) != len(names):
            raise ValueError("Duplicate archive entry")
        for entry in entries:
            name = PurePosixPath(entry.filename)
            if (entry.is_dir() or entry.compress_type != zipfile.ZIP_STORED or entry.flag_bits & 1
                    or stat.S_ISLNK(entry.external_attr >> 16) or name.is_absolute()
                    or ".." in name.parts or "\\" in entry.filename
                    or str(name) != entry.filename):
                raise ValueError("Archive requires regular, safe, unencrypted STORED entries")
        manifest = json.loads(archive.read("manifest.json"))
        if (not isinstance(manifest, dict) or type(manifest.get("schemaVersion")) is not int or manifest.get("schemaVersion") != 1
                or manifest.get("type") not in ("MODEL", "KNOWLEDGE", "AUDIO")
                or any(not isinstance(manifest.get(key), str) or not manifest[key].strip()
                       for key in ("id", "version", "license"))):
            raise ValueError("Unsupported manifest identity")
        artifacts = manifest.get("artifacts")
        if not isinstance(artifacts, list) or not artifacts or any(not isinstance(a, dict) for a in artifacts):
            raise ValueError("Missing manifest artifacts")
        declared = [artifact["path"] for artifact in artifacts]
        if len(set(declared)) != len(declared) or set(declared) != set(names[1:]):
            raise ValueError("Archive entries do not match declared artifacts")
        installed = 0
        for artifact in artifacts:
            size = integer(artifact["bytes"], "artifact bytes", 1)
            if not isinstance(artifact["sha256"], str) or not PIN.fullmatch(artifact["sha256"]):
                raise ValueError("Invalid artifact checksum")
            with archive.open(artifact["path"]) as stream:
                measured = digest(stream)
            if measured != (size, artifact["sha256"]):
                raise ValueError("Payload checksum/size mismatch")
            installed += size
    # Fail if an input changed during the verification pass.
    with path.open("rb") as stream:
        if digest(stream) != (archive_bytes, archive_hash):
            raise ValueError("Archive changed during verification")
    return dict(id=manifest["id"], version=manifest["version"], type=manifest["type"],
                archiveBytes=archive_bytes, installedBytes=installed, sha256=archive_hash,
                archiveAndEveryPayloadVerified=True)


def simulate(current_bytes: int, free_bytes: int, growth_reserve_bytes: int,
             operations: list[dict]) -> dict:
    current = initial = integer(current_bytes, "currentBytes")
    free = integer(free_bytes, "freeBytes")
    reserve = integer(growth_reserve_bytes, "growthReserveBytes", 1)
    identities, topics, steps = set(), set(), []
    peak, required_free, knowledge = current, 0, 0
    if sum(integer(op.get("partialBytes", 0), "partialBytes") for op in operations) > initial:
        raise ValueError("Staged partial downloads must already be included in currentBytes")
    for operation in operations:
        identity = (operation["id"], operation["version"])
        if identity in identities or operation["id"] in topics:
            raise ValueError("Duplicate selection or multiple selected editions of one topic")
        identities.add(identity)
        topics.add(operation["id"])
        archive = integer(operation["archiveBytes"], "archiveBytes", 1)
        installed = integer(operation["installedBytes"], "installedBytes", 1)
        if installed > archive:
            raise ValueError("STORED payload bytes cannot exceed archive bytes")
        partial = integer(operation.get("partialBytes", 0), "partialBytes")
        mode = operation.get("mode")
        if mode not in ("download", "local-import") or partial > archive:
            raise ValueError("Invalid mode or partial byte count")
        if mode == "local-import" and partial:
            raise ValueError("Local imports cannot reuse a partial download")
        if operation["type"] not in ("KNOWLEDGE", "MODEL", "AUDIO"):
            raise ValueError("Unsupported pack type")
        # Current native model downloads are raw GGUF, not MODEL fapacks.
        if mode == "download" and operation["type"] != "KNOWLEDGE":
            raise ValueError("Only knowledge fapacks support this download simulation; import model/audio locally")
        # A downloaded .part already counts in currentBytes, and disappears only
        # after registration succeeds. Local SAF imports spool a full ZIP first.
        extra = archive - partial + installed + METADATA_RESERVE
        step_peak = current + extra
        conservative_admission = current + (archive - partial) + archive + METADATA_RESERVE
        if mode == "local-import":
            conservative_admission = step_peak
        peak = max(peak, step_peak, conservative_admission)
        required_free = max(required_free, step_peak - initial,
                            conservative_admission - initial)
        reasons = []
        if max(step_peak, conservative_admission) + reserve > LIMIT:
            reasons.append("exceeds_total_limit_with_growth_reserve")
        if max(step_peak, conservative_admission) - initial + reserve + DEVICE_HEADROOM > free:
            reasons.append("insufficient_free_space_with_reserves")
        current += installed + METADATA_RESERVE - partial
        if operation["type"] == "KNOWLEDGE":
            knowledge += installed
        steps.append(dict(operation, peakBytes=step_peak,
                          conservativeAdmissionBytes=conservative_admission,
                          remainingDownloadBytes=archive - partial,
                          finalBytes=current, allowed=not reasons, rejectionReasons=reasons))
    baseline_allowed = initial + reserve <= LIMIT and reserve + DEVICE_HEADROOM <= free
    return dict(schemaVersion=1, scope="offline_sequential_storage_plan_not_runtime_acceptance",
        currentBytes=initial, suppliedFreeBytes=free, growthReserveBytes=reserve,
        deviceFreeHeadroomBytes=DEVICE_HEADROOM, metadataReservePerOperationBytes=METADATA_RESERVE,
        totalLimitBytes=LIMIT, selectedKnowledgePayloadBytes=knowledge,
        selectedKnowledgeMeetsTwentyDecimalGB=knowledge >= 20_000_000_000,
        plannedFinalBytes=current, plannedPeakBytes=peak,
        requiredAdditionalFreeBytesWithReserves=required_free + reserve + DEVICE_HEADROOM,
        totalLimitAndFreeSpacePlanPass=baseline_allowed and all(step["allowed"] for step in steps),
        runtimeAcceptanceComplete=False, operations=steps,
        limits=["currentBytes/freeBytes are supplied measurements, not measured by this tool",
                "currentBytes must already include old/disabled editions, staged partials, APK/native libraries and all app data",
                "Sequential operations only; concurrent provisioning needs a separate peak measurement",
                "Old editions are retained; no deletion credit or deduplication is assumed",
                "Growth and metadata reserves are planning allowances, not permanent OS quotas",
                "Verified bytes do not establish coverage, publisher authenticity or Android import success"])


def plan(input_path: Path) -> dict:
    if input_path.stat().st_size > 1_048_576:
        raise ValueError("Plan input exceeds 1 MB")
    config = json.loads(input_path.read_text(encoding="utf-8"))
    if not isinstance(config, dict):
        raise ValueError("Plan input must be a JSON object")
    operations = config["operations"]
    if (not isinstance(operations, list) or not 1 <= len(operations) <= 500
            or any(not isinstance(operation, dict) for operation in operations)):
        raise ValueError("Select between 1 and 500 operations")
    integer(config["currentBytes"], "currentBytes")
    integer(config["freeBytes"], "freeBytes")
    integer(config["growthReserveBytes"], "growthReserveBytes", 1)
    measured = []
    for operation in operations:
        archive = Path(operation["archive"])
        if not archive.is_absolute():
            archive = input_path.resolve().parent / archive
        item = measure_archive(archive, operation["sha256"])
        measured.append(item | dict(archive=str(archive.resolve()), mode=operation["mode"],
                                   partialBytes=operation.get("partialBytes", 0)))
    return simulate(config["currentBytes"], config["freeBytes"], config["growthReserveBytes"], measured)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("plan", type=Path, help="JSON config; archive paths are relative to this file")
    args = parser.parse_args()
    try:
        result = plan(args.plan)
    except (ValueError, KeyError, TypeError, OSError, zipfile.BadZipFile) as error:
        parser.exit(2, f"Storage plan rejected: {error}\n")
    print(json.dumps(result, indent=2))
    return 0 if result["totalLimitAndFreeSpacePlanPass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
