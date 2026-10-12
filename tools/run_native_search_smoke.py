#!/usr/bin/env python3
"""Run isolated ARM JNI checks without installing a debug app or modifying app data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import sqlite3
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--fixture', type=Path, required=True)
    parser.add_argument('--corpus', type=Path)
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    args = parser.parse_args()
    work = args.work.resolve()
    if work.exists() or not work.is_relative_to(ROOT / 'build'):
        raise ValueError('Use a new directory under build')
    work.mkdir(parents=True)
    classes = ROOT / 'app/build/tmp/kotlin-classes/debugUnitTest'
    files = sorted(classes.rglob('AndroidNativeSearchSmoke*.class'))
    if not files: raise ValueError('Compile unit test classes first')
    jar, dex = work / 'classes.jar', work / 'smoke.dex.jar'
    with zipfile.ZipFile(jar, 'w') as out:
        for file in files: out.write(file, file.relative_to(classes))
    subprocess.run([str(args.sdk / 'build-tools/36.0.0/d8'), '--min-api', '33', '--lib',
        str(args.sdk / 'platforms/android-36/android.jar'), '--output', str(dex), str(jar)], check=True)
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    native = work / 'native'
    native.mkdir()
    with zipfile.ZipFile(apk) as archive:
        for name in ['libfieldatlas_leviathan.so','libsqliteJni.so']:
            (native / name).write_bytes(archive.read('lib/arm64-v8a/' + name))
    adb = ['adb', '-s', args.serial]
    def shell(*words): return subprocess.check_output(adb + ['shell', shlex.join(words)], text=True, timeout=180).strip()
    remote = shell('mktemp', '-d', '/data/local/tmp/fieldatlas-native.XXXXXXXX')
    if not re.fullmatch(r'/data/local/tmp/fieldatlas-native\.[A-Za-z0-9]+', remote):
        raise ValueError('Unexpected temporary path')
    (work / 'remote-path.txt').write_text(remote)
    shell('mkdir', remote + '/native')
    payload = {'app.apk': apk, 'smoke.dex.jar': dex, 'fixture.sqlite': args.fixture, **{'native/' + f.name: f for f in native.iterdir()}}
    if args.corpus:
        payload['corpus.sqlite'] = args.corpus
        bad = work / 'unsupported-native.sqlite'
        shutil.copy2(args.fixture, bad)
        with sqlite3.connect(bad) as db: db.execute("UPDATE meta SET value='999' WHERE key='schema_version'")
        payload['unsupported-native.sqlite'] = bad
    pins = {}
    for name, path in payload.items():
        with path.open('rb') as source: sha = hashlib.file_digest(source, 'sha256').hexdigest()
        subprocess.run(adb + ['push', str(path), remote + '/' + name], check=True, stdout=subprocess.DEVNULL)
        assert shell('sha256sum', remote + '/' + name).split()[0] == sha
        pins[name] = sha
    command = ['env', f'CLASSPATH={remote}/app.apk:{remote}/smoke.dex.jar',
               f'LD_LIBRARY_PATH={remote}/native', '/system/bin/app_process',
               f'-Djava.library.path={remote}/native', '/system/bin',
               'xyz.fieldatlas.desktop.AndroidNativeSearchSmoke', remote + '/fixture.sqlite']
    if args.corpus: command.extend([remote + '/corpus.sqlite', remote + '/unsupported-native.sqlite'])
    run = subprocess.run(adb + ['shell', shlex.join(command)], text=True, capture_output=True, timeout=180)
    (work / 'device.log').write_text(run.stdout + run.stderr)
    (work / 'report.json').write_text(json.dumps(dict(returncode=run.returncode, pins=pins, remote=remote,
        installedAppTouched=False, networkChanged=False), indent=2))
    print(run.stdout + run.stderr)
    run.check_returncode()
    for name, sha in pins.items(): assert shell('sha256sum', remote + '/' + name).split()[0] == sha
    # The validated exact scratch path contains only these generated/copied inputs.
    shell('rm', '-rf', remote)
    (work / 'cleanup.txt').write_text('Removed owned scratch after successful checks; retained Mac evidence.\n')


if __name__ == '__main__': main()
