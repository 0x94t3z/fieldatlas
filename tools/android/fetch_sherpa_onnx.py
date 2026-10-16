#!/usr/bin/env python3
"""Fetch the pinned sherpa-onnx Android release and keep only the two ARM64 libraries the reading voice needs.

The archive is checked against its SHA-256 before anything is extracted, then cached under build/download-cache so a
clean build works offline. Only libsherpa-onnx-jni.so and libonnxruntime.so are copied: the C and C++ API libraries
are for apps that do not use JNI (see the archive's own README).
"""

import argparse
import hashlib
import shutil
import tarfile
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERSION = '1.13.8'
URL = f'https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/sherpa-onnx-v{VERSION}-android.tar.bz2'
SHA256 = '2ff63469a71cb6009aa2e3ed5f4a670f8abdcbe4bb9ffd23776afc792a6b4f44'
LIBRARIES = ('libsherpa-onnx-jni.so', 'libonnxruntime.so')
CACHE = ROOT / 'build/download-cache' / f'sherpa-onnx-v{VERSION}-android.tar.bz2'


def digest(path: Path) -> str:
    sha = hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda: handle.read(1 << 20), b''):
            sha.update(block)
    return sha.hexdigest()


def fetch(offline: bool) -> Path:
    if CACHE.is_file() and digest(CACHE) == SHA256:
        return CACHE
    if offline:
        raise SystemExit(f'{CACHE} is missing or changed and Gradle is offline; run once with a connection')
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=CACHE.parent, delete=False) as partial:
        with urllib.request.urlopen(URL, timeout=60) as response:
            shutil.copyfileobj(response, partial)
    partial_path = Path(partial.name)
    actual = digest(partial_path)
    if actual != SHA256:
        partial_path.unlink()
        raise SystemExit(f'sherpa-onnx archive checksum mismatch: expected {SHA256}, got {actual}')
    partial_path.replace(CACHE)
    return CACHE


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='jniLibs directory to fill (arm64-v8a is created)')
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    archive = fetch(args.offline)
    target = args.output / 'arm64-v8a'
    if args.output.exists():
        shutil.rmtree(args.output)
    target.mkdir(parents=True)
    with tarfile.open(archive, 'r:bz2') as tar:
        for name in LIBRARIES:
            member = tar.getmember(f'./jniLibs/arm64-v8a/{name}')
            if not member.isfile():
                raise SystemExit(f'{name} is not a regular file in the archive')
            with tar.extractfile(member) as source, (target / name).open('wb') as copy:
                shutil.copyfileobj(source, copy)


if __name__ == '__main__':
    main()
