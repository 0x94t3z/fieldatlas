#!/usr/bin/env python3
"""Time Leviathan and keyword search on a phone with app_process, without installing anything or touching app data."""

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--pack', action='append', required=True, help='ID=path to a dual-search content.sqlite')
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    args = parser.parse_args()
    work = args.work.resolve()
    if work.exists() or not work.is_relative_to(ROOT / 'build'):
        raise ValueError('Use a new directory under build')
    work.mkdir(parents=True)
    classes = ROOT / 'app/build/tmp/kotlin-classes/debugUnitTest'
    files = sorted(classes.rglob('AndroidLeviathanTiming*.class'))
    if not files:
        raise ValueError('Compile unit test classes first')
    jar, dex = work / 'classes.jar', work / 'timing.dex.jar'
    with zipfile.ZipFile(jar, 'w') as out:
        for file in files:
            out.write(file, file.relative_to(classes))
    subprocess.run([str(args.sdk / 'build-tools/36.0.0/d8'), '--min-api', '33', '--lib',
                    str(args.sdk / 'platforms/android-36/android.jar'), '--output', str(dex), str(jar)], check=True)
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    native = work / 'native'
    native.mkdir()
    with zipfile.ZipFile(apk) as archive:
        for name in ['libfieldatlas_leviathan.so', 'libsqliteJni.so']:
            (native / name).write_bytes(archive.read('lib/arm64-v8a/' + name))
    adb = ['adb', '-s', args.serial]

    def shell(*words, timeout=600):
        return subprocess.check_output(adb + ['shell', shlex.join(words)], text=True, timeout=timeout).strip()

    remote = shell('mktemp', '-d', '/data/local/tmp/fieldatlas-timing.XXXXXXXX')
    if not re.fullmatch(r'/data/local/tmp/fieldatlas-timing\.[A-Za-z0-9]+', remote):
        raise ValueError('Unexpected temporary path')
    try:
        shell('mkdir', remote + '/native')
        payload = {'app.apk': apk, 'timing.dex.jar': dex, **{'native/' + f.name: f for f in native.iterdir()}}
        packs = []
        for item in args.pack:
            pack, path = item.split('=', 1)
            if not re.fullmatch(r'[a-z0-9-]+', pack):
                raise ValueError('Unexpected pack id')
            payload[pack + '.sqlite'] = Path(path)
            packs.append(f'{pack}={remote}/{pack}.sqlite')
        for name, path in payload.items():
            with path.open('rb') as source:
                sha = hashlib.file_digest(source, 'sha256').hexdigest()
            subprocess.run(adb + ['push', str(path), remote + '/' + name], check=True, stdout=subprocess.DEVNULL)
            if shell('sha256sum', remote + '/' + name).split()[0] != sha:
                raise ValueError('Pushed copy differs: ' + name)
        model = shell('getprop', 'ro.product.model')
        command = ['env', f'CLASSPATH={remote}/app.apk:{remote}/timing.dex.jar', f'LD_LIBRARY_PATH={remote}/native',
                   '/system/bin/app_process', f'-Djava.library.path={remote}/native', '/system/bin',
                   'xyz.fieldatlas.desktop.AndroidLeviathanTiming', *packs]
        run = subprocess.run(adb + ['shell', shlex.join(command)], text=True, capture_output=True, timeout=1800)
        (work / 'device.log').write_text(run.stdout + run.stderr)
        rows = [json.loads(line) for line in run.stdout.splitlines() if line.startswith('{')]
        (work / 'timings.json').write_text(json.dumps(dict(device=model, rows=rows), indent=1))
        print(run.stdout + run.stderr)
        run.check_returncode()
    finally:
        # Only this run's own scratch directory is removed; the installed app is never touched.
        shell('rm', '-rf', remote)


if __name__ == '__main__':
    main()
