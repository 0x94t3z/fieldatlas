#!/usr/bin/env python3
"""Opt-in physical Android shell research/inference pilot, NOT installed-app acceptance."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import subprocess
import time
import zipfile

from prepare_research_index import identity, validate_manifest

ROOT = Path(__file__).resolve().parents[1]


def class_files(directory):
    pattern = re.compile(r'(AndroidResearchPilot|DesktopLeviathanRetriever(?:Kt)?)(?:\$.*)?\.class')
    files = [p for p in sorted(directory.rglob('*.class')) if pattern.fullmatch(p.name)]
    if not any(p.name == 'AndroidResearchPilot.class' for p in files):
        raise ValueError('Compile app debug unit tests before running the pilot')
    return files


def validate_arm_binary(binary):
    with binary.open('rb') as stream:
        header = stream.read(20)
    if len(header) != 20 or header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 183:
        raise ValueError('Expected Android ARM64 little-endian ELF binary')


def checked_remote(path):
    if re.fullmatch(r'/data/local/tmp/fieldatlas-retrieval\.[A-Za-z0-9]+', path) is None:
        raise ValueError('Unexpected remote scratch directory; refusing to use it')
    return path


def app_digest():
    digest = hashlib.sha256()
    for path in sorted((ROOT / 'app/src/main/java').rglob('*.kt')):
        digest.update(str(path.relative_to(ROOT)).encode()); digest.update(path.read_bytes())
    return digest.hexdigest()


def stop_owned_processes(shell, remote, config_path):
    """Never stop an installed app or a process selected merely by a generic class name."""
    checked_remote(remote)
    for line in shell('ps', '-A', '-o', 'PID,ARGS').splitlines()[1:]:
        fields = line.strip().split(None, 1)
        if len(fields) != 2 or not fields[0].isdigit(): continue
        command = fields[1]
        pilot = 'xyz.fieldatlas.desktop.AndroidResearchPilot' in command and config_path in command
        child = remote + '/binary' in command and remote + '/index' in command
        if pilot or child:
            shell('kill', '-KILL', fields[0])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--android-leviathan', type=Path, required=True)
    parser.add_argument('--model', type=Path, default=ROOT / 'build/model-cache/Qwen_Qwen3.5-2B-Q4_K_M.verified.gguf')
    parser.add_argument('--debug-apk', type=Path, default=ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
    parser.add_argument('--question', action='append', required=True)
    parser.add_argument('--output', type=Path, required=True, help='New experiment directory under ignored build/')
    parser.add_argument('--staged-remote', help='Reuse an owned scratch directory only after all immutable transfers are reverified')
    parser.add_argument('--sdk', type=Path, default=Path('/opt/homebrew/share/android-commandlinetools'))
    args = parser.parse_args()
    work = args.output.resolve()
    if not work.is_relative_to(ROOT / 'build') or work.exists():
        parser.error('Expected a NEW experiment directory under build/; no overwriting')
    if any(not q.strip() for q in args.question): parser.error('Questions must not be blank')
    data = json.loads(args.manifest.read_text())
    source, index = Path(data['source']['path']), Path(data['index']['path'])
    validate_manifest(args.manifest, [source])
    validate_arm_binary(args.android_leviathan)
    classes = ROOT / 'app/build/tmp/kotlin-classes/debugUnitTest'
    files = class_files(classes)
    apk, model, binary = args.debug_apk.resolve(strict=True), args.model.resolve(strict=True), args.android_leviathan.resolve(strict=True)
    d8 = args.sdk / 'build-tools/36.0.0/d8'
    android_jar = args.sdk / 'platforms/android-36/android.jar'
    work.mkdir(parents=True)
    source_jar, dex = work / 'pilot-classes.jar', work / 'pilot.dex.jar'
    with zipfile.ZipFile(source_jar, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in files: archive.write(path, path.relative_to(classes))
    env = dict(os.environ)
    env.setdefault('JAVA_HOME', '/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
    with (work / 'd8.log').open('w') as log:
        subprocess.run([str(d8), '--min-api', '33', '--lib', str(android_jar), '--classpath',
                        str(ROOT / 'app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar'),
                        '--output', str(dex), str(source_jar)], env=env, check=True, stdout=log, stderr=subprocess.STDOUT)
    native = work / 'native'
    native.mkdir()
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            if entry.filename.startswith('lib/arm64-v8a/') and entry.filename.endswith('.so'):
                target = native / Path(entry.filename).name
                with archive.open(entry) as src, target.open('wb') as dst:
                    while block := src.read(1024 * 1024): dst.write(block)
    if not (native / 'libai-chat.so').is_file() or not (native / 'libsqliteJni.so').is_file():
        raise ValueError('APK lacks the ARM inference/SQLite libraries')
    adb = ['adb', '-s', args.serial]
    def shell(*words, timeout=120):
        return subprocess.check_output(adb + ['shell', shlex.join(words)], text=True, timeout=timeout).strip()
    if subprocess.check_output(adb + ['get-state'], text=True).strip() != 'device':
        raise RuntimeError('Android device unavailable')
    remote = checked_remote(args.staged_remote) if args.staged_remote else checked_remote(shell('mktemp', '-d', '/data/local/tmp/fieldatlas-retrieval.XXXXXXXX'))
    (work / 'remote-path.txt').write_text(remote + '\n')
    if not args.staged_remote: shell('mkdir', remote + '/native')
    shell('mkdir', '-p', remote + '/tmp')
    payload = secrets.token_hex(8)
    dex_name = 'pilot.' + payload + '.dex.jar'
    config_name, report_name = 'config.' + payload + '.json', 'report.' + payload + '.json'
    transfers = {'source': source, 'index': index, 'model': model, 'binary': binary}
    pins = {}
    for name, path in {**transfers, 'app.apk': apk, dex_name: dex}.items():
        target = remote + '/' + name
        pin = identity(path)
        if not args.staged_remote or name == dex_name:
            print(f'Transferring pinned {name}', flush=True)
            subprocess.run(adb + ['push', str(path), target], check=True, stdout=subprocess.DEVNULL)
        if shell('sha256sum', target, timeout=180).split()[0] != pin['sha256']:
            raise ValueError('Remote identity mismatch: ' + name)
        if name in transfers: pins[name] = {**pin, 'path': target}
    if not args.staged_remote:
        subprocess.run(adb + ['push', str(native) + '/.', remote + '/native/'], check=True, stdout=subprocess.DEVNULL)
    for path in sorted(native.iterdir()):
        if shell('sha256sum', remote + '/native/' + path.name).split()[0] != identity(path)['sha256']:
            raise ValueError('Native library identity mismatch')
    shell('chmod', '700', remote + '/binary')
    assert shell(remote + '/binary', '--version') == data['binaryVersion']
    config = dict(files=pins, nativeDir=remote + '/native', output=remote + '/' + report_name, questions=args.question,
                  provenance=dict(appKotlinSha256=app_digest(), debugApk=identity(apk), pilotDex=identity(dex),
                      sourceManifest=identity(args.manifest), androidBinary=identity(binary),
                      diagnosticToolSha256={str(p.relative_to(ROOT)): identity(p)['sha256'] for p in [
                          Path(__file__).resolve(), ROOT / 'app/src/test/java/xyz/fieldatlas/desktop/AndroidResearchPilot.kt',
                          ROOT / 'app/src/test/java/xyz/fieldatlas/desktop/DesktopLeviathanRetriever.kt']}))
    config_path = work / 'config.json'
    config_path.write_text(json.dumps(config, indent=2))
    subprocess.run(adb + ['push', str(config_path), remote + '/' + config_name], check=True, stdout=subprocess.DEVNULL)
    command = ['env', 'CLASSPATH=' + remote + '/app.apk:' + remote + '/' + dex_name,
               'LD_LIBRARY_PATH=' + remote + '/native', '/system/bin/app_process',
               '-Djava.library.path=' + remote + '/native', '-Djava.io.tmpdir=' + remote + '/tmp',
               '/system/bin', 'xyz.fieldatlas.desktop.AndroidResearchPilot', remote + '/' + config_name]
    print('Running real Android research + native model; normal app data is not used.', flush=True)
    with (work / 'device.log').open('w') as log:
        process = subprocess.Popen(adb + ['shell', shlex.join(command)], stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 1800
        try:
            while process.poll() is None:
                if time.monotonic() > deadline:
                    raise TimeoutError('Pilot exceeded 30 minutes; inspect owned scratch directory and device.log')
                time.sleep(1)
        finally:
            if process.poll() is None:
                try: stop_owned_processes(shell, remote, remote + '/' + config_name)
                finally:
                    process.terminate()
                    try: process.wait(timeout=10)
                    except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=10)
        if process.returncode:
            present = subprocess.run(adb + ['shell', shlex.join(['test', '-f', remote + '/' + report_name])], capture_output=True)
            if present.returncode == 0:
                subprocess.run(adb + ['pull', remote + '/' + report_name, str(work / 'failed-report.json')], check=True, stdout=subprocess.DEVNULL)
            raise RuntimeError(f'Android pilot failed ({process.returncode}); inspect {work / "device.log"}')
    subprocess.run(adb + ['pull', remote + '/' + report_name, str(work / 'report.json')], check=True, stdout=subprocess.DEVNULL)
    report = json.loads((work / 'report.json').read_text())
    if len(report['runs']) != len(args.question) * 2 or any(r['status'] != 'COMPLETED_UNSCORED' for r in report['runs']):
        raise RuntimeError('Partial or failed report; do not claim acceptance')
    for name, path in transfers.items():
        if identity(path)['sha256'] != pins[name]['sha256']: raise ValueError('Host input changed during pilot')
    (work / 'completion.json').write_text(json.dumps(dict(status='COMPLETED_UNSCORED', productionAdopted=False,
        normalAppDataTouched=False, remoteScratch=remote, retainedForAudit=True, deviceCommand=command), indent=2))
    print(f'Pilot report: {work / "report.json"}\nCompletion is NOT an accuracy result.', flush=True)


if __name__ == '__main__':
    main()
