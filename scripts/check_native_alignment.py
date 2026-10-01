#!/usr/bin/env python3
"""Fail closed on non-16-KB-aligned ELF64 libraries in an APK (or AAR).

Checks PT_LOAD headers, not ZIP alignment or runtime compatibility. Android:
https://developer.android.com/guide/practices/page-sizes#elf-alignment
"""
import argparse
import struct
import zipfile


def check_elf(data: bytes) -> None:
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("expected little-endian ELF64")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    if size != 56 or not count or offset < 64 or offset + size * count > len(data):
        raise ValueError("invalid program header table")
    loads = 0
    for index in range(count):
        kind, _, file_offset, address, _, file_size, memory_size, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, offset + index * size)
        if kind != 1:
            continue
        loads += 1
        if file_size > memory_size or file_offset + file_size > len(data):
            raise ValueError("invalid LOAD segment size")
        if alignment < 16384 or alignment & (alignment - 1):
            raise ValueError(f"LOAD alignment {alignment} is not 16-KB compatible")
        if file_offset % alignment != address % alignment:
            raise ValueError("LOAD offset/address alignment mismatch")
    if not loads:
        raise ValueError("no LOAD segments")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive")
    args = parser.parse_args()
    failures = []
    with zipfile.ZipFile(args.archive) as archive:
        libraries = [n for n in archive.namelist()
                     if n.endswith(".so") and ("/arm64-v8a/" in n or "/x86_64/" in n)]
        if not libraries:
            parser.error("no 64-bit native libraries found")
        for name in libraries:
            try:
                check_elf(archive.read(name))
                print(f"PASS {name}")
            except ValueError as error:
                failures.append(name)
                print(f"FAIL {name}: {error}")
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
