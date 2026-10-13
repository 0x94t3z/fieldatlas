#!/usr/bin/env python3
"""Build a pinned, full-pack Leviathan index for opt-in desktop research only."""

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
from contextlib import closing
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess
import time


def identity(path):
    path = path.resolve(strict=True)
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return dict(path=str(path), bytes=path.stat().st_size, sha256=digest.hexdigest())


def validate_source(path):
    with closing(sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)) as db:
        if db.execute('PRAGMA user_version').fetchone()[0] != 1:
            raise ValueError('Expected app knowledge schema version 1')
        columns = [row[1] for row in db.execute('PRAGMA table_info(chunks_fts)')]
        definition = db.execute("SELECT sql FROM sqlite_master WHERE name='chunks_fts'").fetchone()
        if columns != ['chunk_id', 'document_id', 'title', 'source', 'text'] or not definition or 'using fts5' not in definition[0].lower():
            raise ValueError('Expected the app chunks_fts virtual table')
        count = db.execute('SELECT count(*) FROM chunks_fts').fetchone()[0]
        invalid = db.execute("SELECT 1 FROM chunks_fts WHERE chunk_id IS NULL OR trim(chunk_id)='' OR document_id IS NULL OR trim(document_id)='' OR title IS NULL OR trim(title)='' OR source IS NULL OR trim(source)='' OR text IS NULL OR trim(text)='' LIMIT 1").fetchone()
        duplicate = db.execute('SELECT chunk_id FROM chunks_fts GROUP BY chunk_id HAVING count(*) > 1 LIMIT 1').fetchone()
        if count == 0 or invalid or duplicate:
            raise ValueError('Pack must contain nonempty passages with unique chunk IDs')
        return count


def validate_manifest(path, databases):
    data = json.loads(path.read_text())
    if data.get('schemaVersion') != 1 or data.get('scope') != 'desktop_full_pack_experiment' or data.get('productionAdopted') is not False:
        raise ValueError('Not a supported experimental retrieval manifest')
    if len(databases) != 1 or str(databases[0].resolve()) != data['source']['path']:
        raise ValueError('Leviathan comparison requires exactly its pinned source pack')
    for key in ['source', 'index', 'binary']:
        if identity(Path(data[key]['path'])) != data[key]:
            raise ValueError(f'{key} identity changed; rebuild the experiment')
    if validate_source(databases[0]) != data['records']:
        raise ValueError('Source record count changed')
    with closing(sqlite3.connect(Path(data['index']['path']).as_uri() + '?mode=ro', uri=True)) as db:
        if db.execute('SELECT count(*) FROM records').fetchone()[0] != data['records']:
            raise ValueError('Index must cover the entire pack')
    return data


def prepare(database, binary, work):
    database, binary, work = database.resolve(strict=True), binary.resolve(strict=True), work.resolve()
    if work.exists():
        raise ValueError('Refusing to overwrite an existing experiment')
    count = validate_source(database)
    source, executable = identity(database), identity(binary)
    version = subprocess.run([str(binary), '--version'], check=True, capture_output=True, text=True, timeout=10).stdout.strip()
    work.mkdir(parents=True)
    index = work / 'leviathan.sqlite'
    command = [str(binary), 'index', str(database), '--index', str(index), '--format', 'sqlite', '--sql',
               'SELECT chunk_id AS id, document_id, title, source, text, rowid AS source_rowid FROM chunks_fts',
               '--id', 'id', '--title', 'title', '--text', 'text', '--display', 'source', '--group', 'document_id', '--strict', '--quiet']
    start = time.monotonic()
    subprocess.run(command, cwd=work, check=True, capture_output=True, text=True, timeout=1800)
    seconds = time.monotonic() - start
    if identity(database) != source or identity(binary) != executable:
        raise ValueError('Source or executable changed during indexing')
    manifest = dict(schemaVersion=1, scope='desktop_full_pack_experiment', productionAdopted=False,
                    source=source, index=identity(index), binary=executable, binaryVersion=version,
                    records=count, indexSeconds=seconds, indexCommand=command,
                    evidencePolicy='Cards select IDs only; verify and expand the full original passage before app prompt packing.')
    path = work / 'manifest.json'
    path.write_text(json.dumps(manifest, indent=2) + '\n')
    validate_manifest(path, [database])
    return path


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', required=True, type=Path)
    parser.add_argument('--leviathan', required=True, type=Path)
    parser.add_argument('--work', required=True, type=Path)
    args = parser.parse_args()
    print(prepare(args.database, args.leviathan, args.work))
