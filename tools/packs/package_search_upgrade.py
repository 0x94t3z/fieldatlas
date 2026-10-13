#!/usr/bin/env python3
"""Package a locally validated dual-search DB, preserving the knowledge pack's credits.

Does not publish, edit a catalog, install, replace or delete an existing pack.
"""

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import zipfile


def digest(path):
    with path.open('rb') as file: return hashlib.file_digest(file, 'sha256').hexdigest()


def package(original_manifest, database, output, version, compressed=False):
    if output.exists() or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,63}', version):
        raise ValueError('Use a new output and a valid version')
    manifest = json.loads(original_manifest.read_text())
    if manifest.get('type') != 'KNOWLEDGE' or manifest.get('schemaVersion') != 1 or manifest.get('embedding'):
        raise ValueError('Only plain knowledge manifests are supported')
    if version == manifest['version']: raise ValueError('New database requires a distinct immutable version')
    artifacts = {}
    original_pins = {}
    for item in manifest['artifacts']:
        name = item['path']
        if Path(name).name != name or name in artifacts or name == 'manifest.json':
            raise ValueError('Expected unique flat artifact names')
        source = original_manifest.parent / name
        if source.stat().st_size != item['bytes'] or digest(source) != item['sha256']:
            raise ValueError('Original artifact identity mismatch')
        artifacts[name] = database if name == 'content.sqlite' else source
        original_pins[name] = dict(item)
    if 'content.sqlite' not in artifacts: raise ValueError('No knowledge database')
    with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True)) as db:
        if db.execute('SELECT format,engine FROM fieldatlas_search').fetchall() != [(1,'leviathan-0.2.0')]:
            raise ValueError('Not a supported dual-search pack')
        if db.execute("SELECT 1 FROM meta WHERE key='source_manifest'").fetchone():
            raise ValueError('Build-machine metadata must not be distributed')
    manifest['version'] = version
    manifest['schemaVersion'] = 2 if compressed else 1
    manifest['artifacts'] = [dict(path=name, bytes=path.stat().st_size, sha256=digest(path))
                             if name == 'content.sqlite' else original_pins[name] for name,path in artifacts.items()]
    if sum(item['bytes'] for item in manifest['artifacts']) > 35_000_000_000:
        raise ValueError('Pack exceeds 35GB working budget; account for all other app/model/data before release')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.search-pack-',dir=output.parent) as temp:
        staged = Path(temp)/'pack.fapack'
        with zipfile.ZipFile(staged,'x',allowZip64=True) as archive:
            archive.writestr(zipfile.ZipInfo('manifest.json',(1980,1,1,0,0,0)),json.dumps(manifest,sort_keys=True,separators=(',',':')))
            for name,path in artifacts.items():
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.file_size=path.stat().st_size
                if compressed:
                    info.compress_type=zipfile.ZIP_DEFLATED
                    info._compresslevel=1
                with path.open('rb') as source, archive.open(info,'w',force_zip64=True) as dest:
                    shutil.copyfileobj(source,dest,1024*1024)
        with zipfile.ZipFile(staged) as archive:
            for item in manifest['artifacts']:
                with archive.open(item['path']) as source: actual=hashlib.file_digest(source,'sha256').hexdigest()
                if actual!=item['sha256']:raise ValueError('Pack read-back digest mismatch')
        sha=digest(staged)
        os.link(staged,output)
    return dict(pack=str(output),bytes=output.stat().st_size,installedBytes=sum(a['bytes'] for a in manifest['artifacts']),
                sha256=sha,manifest=manifest,published=False,installed=False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest',type=Path,required=True)
    parser.add_argument('--database',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--version',required=True)
    parser.add_argument('--compressed',action='store_true',help='Schema 2 knowledge pack; requires a compatible app and installedBytes in download catalogs')
    args=parser.parse_args()
    print(json.dumps(package(args.manifest,args.database,args.output,args.version,args.compressed),indent=2))
