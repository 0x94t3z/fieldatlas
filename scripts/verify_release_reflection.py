#!/usr/bin/env python3
"""Verify reflection-instantiated ZIP extra fields survive release shrinking."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


REFLECTIVE_CLASS = "org.apache.commons.compress.archivers.zip.AsiExtraField"


def obfuscated_descriptor(mapping: str, original_class: str) -> str:
    match = re.search(
        rf"^{re.escape(original_class)} -> ([^:]+):$",
        mapping,
        flags=re.MULTILINE,
    )
    if match is None:
        raise RuntimeError(f"Missing R8 mapping for {original_class}")
    return "L" + match.group(1).replace(".", "/") + ";"


def class_section(dump: str, descriptor: str) -> str:
    marker = f"Class descriptor  : '{descriptor}'"
    start = dump.find(marker)
    if start < 0:
        raise RuntimeError(f"Missing DEX class {descriptor}")
    next_class = dump.find("\nClass #", start)
    return dump[start : next_class if next_class >= 0 else None]


def verify(apk: Path, mapping: Path, dexdump: Path) -> None:
    descriptor = obfuscated_descriptor(mapping.read_text(encoding="utf-8"), REFLECTIVE_CLASS)
    with tempfile.TemporaryDirectory(prefix="fieldatlas-release-") as temp_dir:
        with zipfile.ZipFile(apk) as archive:
            archive.extract("classes.dex", temp_dir)
        result = subprocess.run(
            [str(dexdump), "-d", str(Path(temp_dir) / "classes.dex")],
            check=True,
            capture_output=True,
            text=True,
        )
    section = class_section(result.stdout, descriptor)
    if "ABSTRACT" in section:
        raise RuntimeError(f"{REFLECTIVE_CLASS} became abstract after R8")
    if "name          : '<init>'" not in section:
        raise RuntimeError(f"{REFLECTIVE_CLASS} lost its constructor after R8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("mapping", type=Path)
    parser.add_argument("dexdump", type=Path)
    args = parser.parse_args()
    verify(args.apk, args.mapping, args.dexdump)
    print("Release reflection audit passed")


if __name__ == "__main__":
    main()
