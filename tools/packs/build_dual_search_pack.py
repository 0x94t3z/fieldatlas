#!/usr/bin/env python3
"""Build one-content knowledge DB with upstream Leviathan and legacy search indexes.

Text packs and their explicitly validated source-provenance tables are supported.
Reject vectors, maps and unknown auxiliary tables rather than silently losing them.
Input files are immutable; output is new.
The two posting indexes share original text in records.doc (no second text corpus).
This creates content.sqlite, not an independently installable .fapack archive.
"""

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import tempfile


PROVENANCE = {
    'source_provenance': ('document_id', 'metadata_json'),
    'dataset_provenance': ('metadata_json',),
}


def provenance_rows(database, tables):
    """Copy data, never source DDL/triggers, from the two documented metadata tables."""
    result = {}
    for table, columns in PROVENANCE.items():
        if table not in tables:
            continue
        info = database.execute(f'PRAGMA table_info({table})').fetchall()
        if tuple(row[1] for row in info) != columns or any(row[2].upper() != 'TEXT' for row in info):
            raise ValueError('Unsupported provenance table layout')
        rows = database.execute(f'SELECT {",".join(columns)} FROM {table}').fetchall()
        for row in rows:
            if any(not isinstance(value, str) or not value or len(value.encode()) > 1_048_576 for value in row):
                raise ValueError('Invalid provenance value')
            try:
                metadata = json.loads(row[-1])
            except ValueError as error:
                raise ValueError('Invalid provenance JSON') from error
            if not isinstance(metadata, dict):
                raise ValueError('Provenance metadata must be an object')
        if table == 'source_provenance':
            ids = [row[0] for row in rows]
            expected = {row[0] for row in database.execute('SELECT DISTINCT document_id FROM chunks_fts')}
            if len(ids) != len(set(ids)) or set(ids) != expected:
                raise ValueError('Provenance must cover each source document exactly once')
        result[table] = rows
    return result


def build(source: Path, index: Path, output: Path):
    source, index = source.resolve(strict=True), index.resolve(strict=True)
    output = output.resolve()
    if output.exists():
        raise ValueError('Refusing to replace existing output')
    with closing(sqlite3.connect(source.as_uri() + '?mode=ro', uri=True)) as original:
        original.execute('BEGIN')
        tables = {r[0] for r in original.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        allowed = {'chunks_fts', 'chunks_fts_data', 'chunks_fts_idx', 'chunks_fts_content', 'chunks_fts_docsize', 'chunks_fts_config'}
        if tables - allowed - PROVENANCE.keys() or original.execute('PRAGMA user_version').fetchone()[0] != 1:
            raise ValueError('Only schema-1 text packs with known provenance are supported; no auxiliary data is discarded')
        provenance = provenance_rows(original, tables)
        schema = original.execute("SELECT sql FROM sqlite_master WHERE name='chunks_fts'").fetchone()[0]
        if 'tokenize' in schema.lower() and "tokenize='porter unicode61'" not in schema:
            raise ValueError('Unsupported source tokenizer')
        tokenizer = 'porter unicode61' if 'tokenize' in schema.lower() else 'unicode61'
        output.parent.mkdir(parents=True, exist_ok=True)
        # Exclusive staging in the same filesystem; output never exposes a partial DB.
        with tempfile.TemporaryDirectory(prefix='.dual-search-', dir=output.parent) as temporary:
            staged = Path(temporary) / 'content.sqlite'
            with closing(sqlite3.connect(index.as_uri() + '?mode=ro', uri=True)) as upstream, closing(sqlite3.connect(staged)) as db:
                upstream.execute('BEGIN')
                metadata = dict(upstream.execute('SELECT key,value FROM meta'))
                if metadata.get('schema_version') != '2' or metadata.get('leviathan_version') != '0.2.0' or metadata.get('skipped_lines') != '0':
                    raise ValueError('Expected a complete Leviathan 0.2.0 index')
                if not upstream.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='record_fts'").fetchone():
                    raise ValueError('Missing upstream posting index')
                upstream.backup(db)
                for table, rows in provenance.items():
                    if table == 'source_provenance':
                        db.execute('CREATE TABLE source_provenance(document_id TEXT PRIMARY KEY, metadata_json TEXT NOT NULL)')
                        db.executemany('INSERT INTO source_provenance VALUES (?,?)', rows)
                    else:
                        db.execute('CREATE TABLE dataset_provenance(metadata_json TEXT NOT NULL)')
                        db.executemany('INSERT INTO dataset_provenance VALUES (?)', rows)
                records = db.execute('SELECT rowid,id,doc FROM records ORDER BY rowid')
                seen = 0
                for _, identity, raw in records:
                    doc = json.loads(raw)
                    rowid = doc.get('source_rowid')
                    if not isinstance(rowid, int) or rowid <= 0:
                        raise ValueError('Invalid source row identity')
                    expected = original.execute('SELECT chunk_id,document_id,title,source,text FROM chunks_fts WHERE rowid=?', (rowid,)).fetchone()
                    actual = tuple(doc.get(k) for k in ('id', 'document_id', 'title', 'source', 'text'))
                    if not expected or actual != expected or identity != expected[0]:
                        raise ValueError('Index differs from original source')
                    seen += 1
                if seen == 0 or seen != original.execute('SELECT count(*) FROM chunks_fts').fetchone()[0]:
                    raise ValueError('Index must cover all source passages')
                db.executescript('''
                    CREATE UNIQUE INDEX fieldatlas_source_rows ON records(json_extract(doc, '$.source_rowid'));
                    CREATE VIEW fieldatlas_chunks AS SELECT
                        json_extract(doc, '$.source_rowid') AS rowid,
                        id AS chunk_id,
                        json_extract(doc, '$.document_id') AS document_id,
                        json_extract(doc, '$.title') AS title,
                        json_extract(doc, '$.source') AS source,
                        json_extract(doc, '$.text') AS text FROM records;
                ''')
                db.execute(f"CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED, document_id UNINDEXED, title, source UNINDEXED, text, content='fieldatlas_chunks', content_rowid='rowid', tokenize='{tokenizer}')")
                db.execute("INSERT INTO chunks_fts(chunks_fts) VALUES ('rebuild')")
                db.execute("INSERT INTO chunks_fts(chunks_fts,rank) VALUES ('integrity-check',1)")
                db.execute('CREATE TABLE fieldatlas_search(format INTEGER NOT NULL, engine TEXT NOT NULL)')
                db.execute("INSERT INTO fieldatlas_search VALUES(1,'leviathan-0.2.0')")
                # Build-machine paths are not source provenance and must not travel in packs.
                db.execute('PRAGMA secure_delete=ON')
                db.execute("DELETE FROM meta WHERE key='source_manifest'")
                db.execute('PRAGMA user_version=1')
                db.commit()
                db.execute('VACUUM')
                if db.execute('PRAGMA quick_check').fetchone() != ('ok',):
                    raise ValueError('Database integrity failed')
                # Every legacy view row must hydrate unchanged, including non-contiguous rowids.
                for row in original.execute('SELECT rowid,chunk_id,document_id,title,source,text FROM chunks_fts'):
                    found = db.execute('SELECT rowid,chunk_id,document_id,title,source,text FROM chunks_fts WHERE rowid=?', (row[0],)).fetchone()
                    if row != found:
                        raise ValueError('Fallback source hydration changed')
            # link is exclusive (unlike rename/replace), so a concurrent output is never overwritten.
            import os
            os.link(staged, output)
    return {'records': seen, 'bytes': output.stat().st_size, 'fullContentCopies': 1, 'postingIndexes': 2}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--index', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.source, args.index, args.output)))
