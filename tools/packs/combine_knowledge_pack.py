"""Build one keyword-only .fapack from pinned databases; never install/delete inputs.

Preserves exact chunk text/IDs/citations. Exact repeated chunks collapse; conflicting
IDs fail closed. This is a host build, not Android retrieval or migration acceptance.
"""

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
from contextlib import closing
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import sys
import tempfile
import zipfile

from tools.packs.stream_pack import _build_database_streaming
from packtool.build_pack import _canonical_json, _digest, _zip_info, IDENTIFIER


def check_pin(path, expected):
    if path.is_symlink() or not path.is_file() or _digest(path) != (expected['bytes'], expected['sha256']):
        raise ValueError('Input checksum or size differs: ' + str(path))


def copy_bounded(source, target, size):
    with target.open('xb') as out:
        left = size
        while left:
            block = source.read(min(left, 1024 * 1024))
            if not block:
                raise ValueError('Truncated input')
            out.write(block)
            left -= len(block)
        if source.read(1):
            raise ValueError('Input exceeds declared size')


def combine(config, output):
    output = Path(output)
    if output.exists() or output.is_symlink():
        raise ValueError('Output already exists')
    for name in ('id', 'version'):
        if not IDENTIFIER.fullmatch(config[name]) or config[name] in ('.', '..'):
            raise ValueError('Unsafe pack identity')
    if not config['title'].strip() or not 1 <= len(config['inputs']) <= 500:
        raise ValueError('Title and bounded inputs required')
    output.parent.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(output.parent).free < 50_000_000_000:
        raise ValueError('Keep 50 GB host working space for consolidation')
    origins, urls, licenses = [], set(), set()
    expected = hashlib.sha256()
    counts = {'inputChunks': 0, 'outputChunks': 0, 'exactDuplicateChunks': 0}
    with tempfile.TemporaryDirectory(prefix='.combine-owned-', dir=output.parent) as work:
        work = Path(work)
        destination = work / 'result'
        destination.mkdir()
        with closing(sqlite3.connect(work / 'seen.sqlite')) as seen:
            seen.execute('PRAGMA cache_size=-8192')
            seen.execute('CREATE TABLE seen (id TEXT PRIMARY KEY, digest BLOB NOT NULL) WITHOUT ROWID')

            def rows():
                identities = set()
                for item in config['inputs']:
                    path = Path(item['path'])
                    check_pin(path, item)
                    with tempfile.TemporaryDirectory(prefix='input-', dir=work) as temporary:
                        temporary = Path(temporary)
                        if item['kind'] == 'database':
                            manifest_path = Path(item['manifest']['path'])
                            check_pin(manifest_path, item['manifest'])
                            manifest = json.loads(manifest_path.read_text())
                            database = path
                        elif item['kind'] in ('archive', 'gzip-archive'):
                            archive_path = path
                            if item['kind'] == 'gzip-archive':
                                archive_path = temporary / 'input.fapack'
                                with gzip.open(path, 'rb') as stream:
                                    copy_bounded(stream, archive_path, item['archiveBytes'])
                                check_pin(archive_path, {'bytes': item['archiveBytes'], 'sha256': item['archiveSha256']})
                            with zipfile.ZipFile(archive_path) as archive:
                                if archive.namelist() != ['manifest.json', 'content.sqlite']:
                                    raise ValueError('Only single-database keyword packs can be consolidated')
                                if not 1 <= archive.getinfo('manifest.json').file_size <= 1_048_576:
                                    raise ValueError('Unbounded manifest')
                                manifest = json.loads(archive.read('manifest.json'))
                                database = temporary / 'content.sqlite'
                                with archive.open('content.sqlite') as stream:
                                    copy_bounded(stream, database, archive.getinfo('content.sqlite').file_size)
                        else:
                            raise ValueError('Unknown input kind')
                        if manifest.get('type') != 'KNOWLEDGE' or manifest.get('schemaVersion') != 1 or manifest.get('embedding'):
                            raise ValueError('Unsupported knowledge format')
                        artifacts = manifest['artifacts']
                        if len(artifacts) != 1 or artifacts[0]['path'] != 'content.sqlite':
                            raise ValueError('Would discard non-database artifacts')
                        check_pin(database, artifacts[0])
                        identity = (manifest['id'], manifest['version'])
                        if identity in identities:
                            raise ValueError('Repeated source pack')
                        identities.add(identity)
                        licenses.add(manifest['license'])
                        if len(licenses) != 1:
                            raise ValueError('Mixed licenses need a separately reviewed attribution policy')
                        urls.update(manifest['sourceUrls'])
                        # A distributable pack must not expose the builder's local paths.
                        origins.append({'input': {key: item[key] for key in
                            ('kind', 'bytes', 'sha256', 'archiveBytes', 'archiveSha256') if key in item},
                            'manifest': manifest})
                        with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True)) as db:
                            tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
                            allowed = {'chunks_fts', 'chunks_fts_data', 'chunks_fts_idx', 'chunks_fts_content', 'chunks_fts_docsize', 'chunks_fts_config'}
                            if tables != allowed or db.execute('PRAGMA quick_check').fetchone() != ('ok',):
                                raise ValueError('Unsupported auxiliary tables or corrupt database')
                            for row in db.execute('SELECT chunk_id,document_id,title,source,text FROM chunks_fts ORDER BY rowid'):
                                if any(not isinstance(value, str) or not value for value in row):
                                    raise ValueError('Invalid source chunk')
                                encoded = _canonical_json(row)
                                digest = hashlib.sha256(encoded).digest()
                                prior = seen.execute('SELECT digest FROM seen WHERE id=?', (row[0],)).fetchone()
                                counts['inputChunks'] += 1
                                if prior:
                                    if prior[0] != digest:
                                        raise ValueError('Conflicting chunk identity: ' + row[0])
                                    counts['exactDuplicateChunks'] += 1
                                    continue
                                seen.execute('INSERT INTO seen VALUES (?,?)', (row[0], digest))
                                expected.update(encoded)
                                counts['outputChunks'] += 1
                                yield row
                        check_pin(database, artifacts[0])
                        check_pin(path, item)
                        if item['kind'] == 'database':
                            check_pin(manifest_path, item['manifest'])
                        seen.commit()
                        print(json.dumps({'completedInput': manifest['id'], **counts}), flush=True)

            database = destination / 'content.sqlite'
            _build_database_streaming(database, rows(), 'porter unicode61')
        if not counts['outputChunks']:
            raise ValueError('Empty combined database')
        actual = hashlib.sha256()
        with closing(sqlite3.connect(database)) as db:
            db.execute("INSERT INTO chunks_fts(chunks_fts) VALUES ('integrity-check')")
            db.commit()
            for row in db.execute('SELECT chunk_id,document_id,title,source,text FROM chunks_fts ORDER BY rowid'):
                actual.update(_canonical_json(row))
        if actual.digest() != expected.digest():
            raise ValueError('Combined database differs from accepted original chunks')
        size, digest = _digest(database)
        if size > 35_000_000_000:
            raise ValueError('Combined database exceeds the conservative 35 GB payload budget')
        attribution = destination / 'origins.json'
        attribution.write_bytes(_canonical_json({'sources': origins, 'counts': counts,
            'sourceChunkDigest': expected.hexdigest(), 'androidRetrievalAccepted': False}))
        artifacts = [{'path': p.name, 'bytes': _digest(p)[0], 'sha256': _digest(p)[1]} for p in (database, attribution)]
        manifest = {'schemaVersion': 1, 'id': config['id'], 'version': config['version'],
            'type': 'KNOWLEDGE', 'title': config['title'], 'license': next(iter(licenses)),
            'sourceUrls': sorted(urls), 'artifacts': artifacts}
        manifest_path = destination / 'manifest.json'
        manifest_path.write_bytes(_canonical_json(manifest))
        pack = destination / (config['id'] + '-' + config['version'] + '.fapack')
        with zipfile.ZipFile(pack, 'x', allowZip64=True) as archive:
            for path in (manifest_path, database, attribution):
                with path.open('rb') as source, archive.open(_zip_info(path.name, path.stat().st_size), 'w', force_zip64=True) as target:
                    shutil.copyfileobj(source, target, 1024 * 1024)
        report = {'counts': counts, 'databaseBytes': size, 'databaseSha256': digest,
            'packBytes': pack.stat().st_size, 'packSha256': _digest(pack)[1],
            'exactSourceChunkAuditPassed': True, 'androidRetrievalAccepted': False,
            'phoneMigrationAccepted': False, 'inputPacksDeleted': False}
        (destination / 'build-report.json').write_bytes(_canonical_json(report))
        os.rename(destination, output)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('config', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    print(json.dumps(combine(json.loads(args.config.read_text()), args.output), indent=2))
