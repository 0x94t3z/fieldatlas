#!/usr/bin/env python3
"""Retain a pinned local artifact as verified gzip, or restore its exact bytes.

This is host storage only: the gzip is not an Android-importable .fapack. Finish
and audit a shard before removing its original artifacts; resumable builders
still require their exact original files. No publishing or automatic cleanup.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import tempfile

BLOCK = 1024 * 1024
FREE_RESERVE = 1_000_000_000
OWNER = "fieldatlas-artifact-retention-v1"


def _pin(size, digest):
    if type(size) is not int or not 0 < size < 2**63 or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
        raise ValueError("Expected positive byte count and lowercase SHA-256")
    return size, digest


def _regular(path):
    path = Path(path)
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode):
        raise ValueError("Input must be a regular file, not a symlink")
    return info


def _digest(path):
    _regular(path)
    size, digest = 0, hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(BLOCK), b""):
            size += len(block)
            digest.update(block)
    return size, digest.hexdigest()


def _identity(path):
    info = _regular(path)
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns


def _destination(path):
    path = Path(path).absolute()
    if path.exists() or path.is_symlink():
        raise ValueError("Destination exists; refusing to replace any file")
    # Resolve only the parent (including macOS /var aliases), never the leaf.
    return path.parent.resolve(strict=True) / path.name


def _space(parent, incoming):
    if shutil.disk_usage(parent).free < incoming + FREE_RESERVE:
        raise ValueError("Insufficient space; retain 1 GB free during restoration/compression")


def _sync_directory(directory):
    descriptor = os.open(directory, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _commit(temporary, destination):
    # A same-filesystem hard link publishes atomically and fails if the target
    # appeared meanwhile. rename/replace could overwrite that other file.
    os.link(temporary, destination)
    _sync_directory(destination.parent)


def _write_receipt(path, report):
    descriptor, name = tempfile.mkstemp(prefix=".retention-receipt-", dir=path.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as out:
            json.dump(report, out, indent=2, sort_keys=True)
            out.write("\n")
            out.flush()
            os.fsync(out.fileno())
        _commit(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _verify_gzip(path, expected, destination=None):
    size, digest = 0, hashlib.sha256()
    with gzip.open(path, "rb") as restored:
        for block in iter(lambda: restored.read(BLOCK), b""):
            size += len(block)
            if size > expected[0]:
                raise ValueError("Restored bytes exceed the pinned size")
            digest.update(block)
            if destination is not None:
                destination.write(block)
    if (size, digest.hexdigest()) != expected:
        raise ValueError("Restored artifact size/checksum mismatch")


def retain_artifact(source, output, expected_bytes, expected_sha256, remove_original=False):
    expected = _pin(expected_bytes, expected_sha256)
    source = Path(source).absolute()
    source_identity = _identity(source)
    if _digest(source) != expected:
        raise ValueError("Original artifact size/checksum mismatch")
    output = _destination(output)
    receipt = _destination(str(output) + ".retention.json")
    if output.parent == source.parent.resolve():
        raise ValueError("Use a separate retention directory; preserve strict builder layouts")
    # gzip can be slightly larger than incompressible input.
    _space(output.parent, expected_bytes + expected_bytes // 1000 + BLOCK)
    descriptor, name = tempfile.mkstemp(prefix=".retention-gzip-", dir=output.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as out, source.open("rb") as incoming:
            with gzip.GzipFile(fileobj=out, mode="wb", filename="", mtime=0, compresslevel=6) as compressed:
                for block in iter(lambda: incoming.read(BLOCK), b""):
                    _space(output.parent, len(block) + BLOCK)
                    compressed.write(block)
            out.flush()
            os.fsync(out.fileno())
        _verify_gzip(temporary, expected)
        compressed_size, compressed_hash = _digest(temporary)
        if _identity(source) != source_identity or _digest(source) != expected:
            raise ValueError("Original artifact changed during retention")
        report = dict(owner=OWNER, originalPath=str(source), originalBytes=expected_bytes,
            originalSha256=expected_sha256, compressedBytes=compressed_size,
            compressedSha256=compressed_hash, format="gzip", restorationByteIdentityVerified=True,
            originalRemovalRequested=bool(remove_original), originalLinkCount=source.stat().st_nlink,
            compressedFile=output.name,
            note="Lossless host retention only; restore the original before Android import or builder resume")
        _commit(temporary, output)
        _write_receipt(receipt, report)
        # Both durable copies must be rechecked before explicit source removal.
        if _digest(output) != (compressed_size, compressed_hash) or _regular(receipt).st_size > 64 * 1024 or json.loads(receipt.read_text()) != report:
            raise ValueError("Retained artifact or receipt changed after commit")
        if remove_original:
            if _identity(source) != source_identity or _digest(source) != expected:
                raise ValueError("Original artifact changed; retained it rather than removing")
            source.unlink()
            _sync_directory(source.parent)
        return report | dict(receipt=str(receipt), originalRemoved=bool(remove_original))
    finally:
        temporary.unlink(missing_ok=True)


def restore_artifact(archive, output, receipt=None):
    archive = Path(archive).absolute()
    _regular(archive)
    receipt = Path(receipt) if receipt is not None else Path(str(archive) + ".retention.json")
    if _regular(receipt).st_size > 64 * 1024:
        raise ValueError("Retention receipt exceeds its bounded size")
    report = json.loads(receipt.read_text(encoding="utf-8"))
    if not isinstance(report, dict) or report.get("owner") != OWNER or report.get("format") != "gzip" or report.get("restorationByteIdentityVerified") is not True:
        raise ValueError("Unrecognized retention receipt")
    expected = _pin(report.get("originalBytes"), report.get("originalSha256"))
    compressed = _pin(report.get("compressedBytes"), report.get("compressedSha256"))
    if _digest(archive) != compressed:
        raise ValueError("Compressed artifact size/checksum mismatch")
    output = _destination(output)
    _space(output.parent, expected[0])
    descriptor, name = tempfile.mkstemp(prefix=".retention-restore-", dir=output.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as out:
            _verify_gzip(archive, expected, out)
            out.flush()
            os.fsync(out.fileno())
        if _digest(archive) != compressed:
            raise ValueError("Compressed artifact changed during restoration")
        _commit(temporary, output)
        return dict(restoredPath=str(output), bytes=expected[0], sha256=expected[1],
                    restorationByteIdentityVerified=True, compressedBackupRetained=True)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    retain = commands.add_parser("compress", help="Verify and retain one pinned artifact")
    retain.add_argument("source", type=Path)
    retain.add_argument("--out", type=Path, required=True)
    retain.add_argument("--expected-bytes", type=int, required=True)
    retain.add_argument("--expected-sha256", required=True)
    retain.add_argument("--remove-original", action="store_true",
                        help="Remove only this original after durable verified gzip and receipt exist")
    restore = commands.add_parser("restore", help="Restore exact original bytes; keep compressed backup")
    restore.add_argument("archive", type=Path)
    restore.add_argument("--out", type=Path, required=True)
    restore.add_argument("--receipt", type=Path)
    args = parser.parse_args()
    if args.command == "compress":
        report = retain_artifact(args.source, args.out, args.expected_bytes, args.expected_sha256, args.remove_original)
    else:
        report = restore_artifact(args.archive, args.out, args.receipt)
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
