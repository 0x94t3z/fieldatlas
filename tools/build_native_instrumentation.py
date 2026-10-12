#!/usr/bin/env python3
"""Build a tiny unsigned instrumentation APK for the NORMAL app; sign with its existing key."""
import argparse
from pathlib import Path
import shutil
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--fixture', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--expected-results', type=Path, help='Pinned full-index parity JSON for optional fullPack instrumentation')
    args = parser.parse_args()
    if args.output.exists(): raise ValueError('Use a new build directory')
    root = Path(__file__).resolve().parent / 'android_native_smoke'
    work = args.output.resolve()
    classes, dex, assets = [work / d for d in ('classes','dex','assets')]
    for directory in [classes,dex,assets]: directory.mkdir(parents=True)
    shutil.copy2(args.fixture, assets / 'fixture.sqlite')
    if args.expected_results: shutil.copy2(args.expected_results, assets / 'expected-results.json')
    android = args.sdk / 'platforms/android-36/android.jar'
    sdktools = args.sdk / 'build-tools/36.0.0'
    subprocess.run(['javac', '-source', '8', '-target', '8', '-classpath', str(android), '-d', str(classes),
        str(root / 'NativeSmokeInstrumentation.java')], check=True)
    subprocess.run([str(sdktools/'d8'),'--min-api','33','--lib',str(android),'--output',str(dex),
        *map(str,classes.rglob('*.class'))],check=True)
    unsigned = work / 'unaligned.apk'
    subprocess.run([str(sdktools/'aapt2'),'link','-I',str(android),'--manifest',str(root/'AndroidManifest.xml'),
        '-A',str(assets),'-o',str(unsigned)],check=True)
    with zipfile.ZipFile(unsigned,'a') as archive:
        for file in dex.glob('*.dex'):archive.write(file,file.name)
    subprocess.run([str(sdktools/'zipalign'),'-P','16','4',str(unsigned),str(work/'unsigned.apk')],check=True)
    print(work/'unsigned.apk')


if __name__=='__main__':main()
