#!/usr/bin/env python3
"""Build the pinned offline-only ARM64 JNI library; requires Rust 1.90 and Android NDK 29."""

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
import json
from html.parser import HTMLParser
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--assets', type=Path, required=True)
    args = parser.parse_args()
    hosts = list((args.ndk / 'toolchains/llvm/prebuilt').glob('*'))
    if len(hosts) != 1:
        raise ValueError('Expected one NDK host toolchain')
    binaries = hosts[0] / 'bin'
    linker = binaries / 'aarch64-linux-android33-clang'
    if not linker.is_file():
        raise ValueError('Android API 33 ARM64 compiler is missing')
    cargo = shutil.which('cargo')
    if cargo is None:
        raise ValueError('Install Rust 1.90 and aarch64-linux-android target, then put cargo on PATH')
    env = os.environ.copy()
    env.update(CARGO_TARGET_DIR=str(ROOT / 'build/leviathan-native'),
               CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=str(linker),
               CC_aarch64_linux_android=str(linker), AR_aarch64_linux_android=str(binaries / 'llvm-ar'),
               CARGO_BUILD_JOBS='2',
               CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384')
    subprocess.run([cargo, 'build', '--locked', '--release', '--target', 'aarch64-linux-android',
                    '--manifest-path', str(ROOT / 'native/leviathan/Cargo.toml')], env=env, check=True)
    target = args.output / 'arm64-v8a/libfieldatlas_leviathan.so'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / 'build/leviathan-native/aarch64-linux-android/release/libfieldatlas_leviathan.so', target)
    metadata = json.loads(subprocess.check_output([cargo, 'metadata', '--locked', '--offline',
        '--format-version', '1', '--filter-platform', 'aarch64-linux-android',
        '--manifest-path', str(ROOT / 'native/leviathan/Cargo.toml')], env=env, text=True))
    notices = ['# Native search dependencies\n\nLeviathan 0.2.0, with a Field Atlas interrupt-handle accessor.\n']
    for package in sorted(metadata['packages'], key=lambda p: (p['name'], p['version'])):
        directory = Path(package['manifest_path']).parent
        files = {p for p in directory.iterdir() if p.is_file() and p.name.lower().startswith(('license', 'licence', 'copying', 'copyright', 'notice'))}
        if package.get('license_file'): files.add(directory / package['license_file'])
        notices.append(f"\n## {package['name']} {package['version']}\n\nLicense: {package.get('license') or 'see included license'}\n")
        for file in sorted(files):
            notices.append('\n' + file.name + '\n\n```text\n' + file.read_text(errors='replace') + '\n```\n')
    notices.append('\n## Upstream dependency notice\n\n' + (ROOT / 'third_party/leviathan/THIRD_PARTY.md').read_text())
    license_path = args.assets / 'licenses/NATIVE_SEARCH_NOTICES.md'
    license_path.parent.mkdir(parents=True, exist_ok=True)
    license_path.write_text('\n'.join(notices))
    # Rust's standard library is statically linked too, outside Cargo's dependency graph.
    sysroot = Path(subprocess.check_output(['rustc', '--print', 'sysroot'], env=env, text=True).strip())
    copyright_html = sysroot / 'share/doc/rust/COPYRIGHT-library.html'
    if not copyright_html.is_file():
        raise ValueError('Rust standard-library copyright notice missing from toolchain')
    class TextNotice(HTMLParser):
        def __init__(self): super().__init__(); self.hidden=0; self.parts=[]
        def handle_starttag(self,tag,attrs):
            if tag in ('style','script'): self.hidden+=1
        def handle_endtag(self,tag):
            if tag in ('style','script'): self.hidden-=1
            elif tag in ('p','div','li','h1','h2','h3','pre','tr'):self.parts.append('\n')
        def handle_data(self,data):
            if not self.hidden:self.parts.append(data)
    notice=TextNotice();notice.feed(copyright_html.read_text())
    (license_path.parent/'rust-runtime-LICENSE.txt').write_text(''.join(notice.parts))


if __name__ == '__main__':
    main()
