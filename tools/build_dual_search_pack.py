#!/usr/bin/env python3
"""Build one-content knowledge DB with upstream Leviathan and legacy search indexes.

Only plain text packs are supported for now. Reject vectors, maps and other auxiliary
tables rather than silently losing them. Input files are immutable; output is new.
The two posting indexes share original text in records.doc (no second text corpus).
This creates content.sqlite, not an independently installable .fapack archive.
"""
import argparse
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import tempfile


def build(source: Path, index: Path, output: Path):
    source, index = source.resolve(strict=True), index.resolve(strict=True)
    output = output.resolve()
    if output.exists():
        raise ValueError('Refusing to replace existing output')
    with closing(sqlite3.connect(source.as_uri() + '?mode=ro', uri=True)) as original:
        original.execute('BEGIN')
        tables = {r[0] for r in original.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        allowed = {'chunks_fts', 'chunks_fts_data', 'chunks_fts_idx', 'chunks_fts_content', 'chunks_fts_docsize', 'chunks_fts_config'}
        if tables - allowed or original.execute('PRAGMA user_version').fetchone()[0] != 1:
            raise ValueError('Only plain schema-1 text packs are supported; no auxiliary data is discarded')
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
