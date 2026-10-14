#!/usr/bin/env python3
"""Build the upstream Leviathan 0.2.0 index for one schema-1 knowledge pack.

Every pack gets the mapping the Encyclopedia ships with (id, title, text, the
document as group, the source URL on each card). A pack of place listings
("Lisbon — Vegan Story", "Edmonton/South — Huma") also gets a `places[]`
filter holding the listing's destination and each parent destination, folded
the way the app folds them (lowercase, no accents), so the app can search one
city's listings inside Leviathan instead of the whole pack.

Input files are opened read-only; the index and its build record are new
files. Pair the index with build_dual_search_pack.py to make the pack DB.
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
from pathlib import Path
import sqlite3
import subprocess
import tempfile
import time
import unicodedata


PLACE_PREFIXES = ('wv-place-', 'osm-place-')
PLACE_FILTER = 'places[]'
TEXT_SQL = 'SELECT chunk_id AS id, document_id, title, source, text, rowid AS source_rowid FROM chunks_fts'
PLACE_SQL = 'SELECT id, document_id, title, source, text, source_rowid, places FROM passages'


def identity(path):
    path = path.resolve(strict=True)
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return dict(name=path.name, bytes=path.stat().st_size, sha256=digest.hexdigest())


def fold(text):
    """Matches DestinationScope.fold in the app: NFD, combining marks removed, lowercase."""
    return ''.join(c for c in unicodedata.normalize('NFD', text) if not unicodedata.category(c).startswith('M')).lower()


def places(title):
    """'Edmonton/South — Huma' -> ['edmonton', 'edmonton/south']; a title without a destination has none."""
    if ' — ' not in title:
        return []
    parts = fold(title.split(' — ', 1)[0]).split('/')
    return ['/'.join(parts[:end]) for end in range(1, len(parts) + 1) if parts[end - 1].strip()]


def is_place_listing(db):
    """Packs are built from one source each; the app decides from the first and last rows, and so does this."""
    ids = [row[0] for row in db.execute(
        'SELECT document_id FROM chunks_fts WHERE rowid IN ((SELECT min(rowid) FROM chunks_fts), (SELECT max(rowid) FROM chunks_fts))')]
    return bool(ids) and all(i.startswith(PLACE_PREFIXES) for i in ids)


def validate(db):
    if db.execute('PRAGMA user_version').fetchone()[0] != 1:
        raise ValueError('Expected app knowledge schema version 1')
    columns = [row[1] for row in db.execute('PRAGMA table_info(chunks_fts)')]
    if columns != ['chunk_id', 'document_id', 'title', 'source', 'text']:
        raise ValueError('Expected the app chunks_fts table')
    count = db.execute('SELECT count(*) FROM chunks_fts').fetchone()[0]
    if count == 0 or db.execute('SELECT chunk_id FROM chunks_fts GROUP BY chunk_id HAVING count(*) > 1 LIMIT 1').fetchone():
        raise ValueError('Pack must contain passages with unique chunk IDs')
    return count


def stage_places(db, staged):
    with closing(sqlite3.connect(staged)) as out:
        out.execute('CREATE TABLE passages(id TEXT, document_id TEXT, title TEXT, source TEXT, text TEXT, source_rowid INTEGER, places TEXT)')
        batch = []
        for rowid, chunk, document, title, source, text in db.execute(
                'SELECT rowid, chunk_id, document_id, title, source, text FROM chunks_fts ORDER BY rowid'):
            batch.append((chunk, document, title, source, text, rowid, json.dumps(places(title), ensure_ascii=False)))
            if len(batch) >= 50_000:
                out.executemany('INSERT INTO passages VALUES (?,?,?,?,?,?,?)', batch)
                batch.clear()
        out.executemany('INSERT INTO passages VALUES (?,?,?,?,?,?,?)', batch)
        out.commit()


def index(database, binary, output):
    database, binary, output = database.resolve(strict=True), binary.resolve(strict=True), output.resolve()
    if output.exists():
        raise ValueError('Refusing to replace an existing index')
    source, executable = identity(database), identity(binary)
    version = subprocess.run([str(binary), '--version'], check=True, capture_output=True, text=True, timeout=10).stdout.strip()
    if version != 'leviathan 0.2.0':
        raise ValueError(f'Expected leviathan 0.2.0, found {version!r}')
    with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as db:
        records = validate(db)
        place_listing = is_place_listing(db)
        output.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='.leviathan-index-', dir=output.parent) as temporary:
            work = Path(temporary)
            mapping = ['--id', 'id', '--title', 'title', '--text', 'text', '--display', 'source', '--group', 'document_id']
            if place_listing:
                staged = work / 'passages.sqlite'
                stage_places(db, staged)
                command = [str(binary), 'index', str(staged), '--format', 'sqlite', '--sql', PLACE_SQL, *mapping, '--filter', PLACE_FILTER]
            else:
                command = [str(binary), 'index', str(database), '--format', 'sqlite', '--sql', TEXT_SQL, *mapping]
            built = work / 'leviathan.sqlite'
            command += ['--index', str(built), '--strict', '--quiet']
            start = time.monotonic()
            subprocess.run(command, cwd=work, check=True, capture_output=True, text=True, timeout=7200)
            seconds = time.monotonic() - start
            with closing(sqlite3.connect(built.as_uri() + '?mode=ro', uri=True)) as upstream:
                meta = dict(upstream.execute('SELECT key, value FROM meta'))
                if upstream.execute('SELECT count(*) FROM records').fetchone()[0] != records or meta.get('skipped_lines') != '0':
                    raise ValueError('Index must cover every passage')
            if identity(database) != source or identity(binary) != executable:
                raise ValueError('Source or executable changed during indexing')
            import os
            os.link(built, output)
    # Paths are left out on purpose: build-machine paths are not provenance.
    record = dict(schemaVersion=1, leviathan=version, binary=executable, source=source, index=identity(output),
                  records=records, placeFilter=PLACE_FILTER if place_listing else None, indexSeconds=round(seconds, 1),
                  mapping=[part for part in command[2:] if not part.startswith('/')])
    output.with_suffix('.json').write_text(json.dumps(record, indent=2) + '\n')
    return record


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', required=True, type=Path)
    parser.add_argument('--leviathan', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(index(args.database, args.leviathan, args.output), indent=2))
