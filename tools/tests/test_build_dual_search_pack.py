import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from tools.packs.build_dual_search_pack import build


class DualSearchPackTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source, self.index, self.output = [self.root / f'{name}.sqlite' for name in ('source', 'index', 'output')]
        with sqlite3.connect(self.source) as db:
            db.execute('PRAGMA user_version=1')
            db.execute('CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED, document_id UNINDEXED,title,source UNINDEXED,text)')
            db.execute("INSERT INTO chunks_fts(rowid,chunk_id,document_id,title,source,text) VALUES(7,'c1','d1','Water','Local','Water freezes into ice.')")
        with sqlite3.connect(self.index) as db:
            db.execute('CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)')
            db.executemany('INSERT INTO meta VALUES(?,?)', [('schema_version','2'),('leviathan_version','0.2.0'),('skipped_lines','0'),('source_manifest','private build path')])
            db.execute('CREATE VIRTUAL TABLE record_fts USING fts5(title,body)')
            db.execute('CREATE TABLE records(rowid INTEGER PRIMARY KEY,id TEXT UNIQUE,doc TEXT)')
            db.execute('INSERT INTO records VALUES(1,?,?)', ('c1', json.dumps(dict(id='c1', document_id='d1',title='Water',source='Local',text='Water freezes into ice.',source_rowid=7))))

    def test_single_content_and_legacy_identity(self):
        report = build(self.source, self.index, self.output)
        self.assertEqual(report['records'], 1)
        with sqlite3.connect(self.output) as db:
            self.assertEqual(db.execute("SELECT rowid,text FROM chunks_fts WHERE chunks_fts MATCH 'ice'").fetchall(), [(7,'Water freezes into ice.')])
            self.assertFalse(db.execute("SELECT 1 FROM sqlite_master WHERE name='chunks_fts_content'").fetchone())
            self.assertFalse(db.execute("SELECT 1 FROM meta WHERE key='source_manifest'").fetchone())
        with self.assertRaises(ValueError): build(self.source, self.index, self.output)

    def test_mismatched_source_is_not_published(self):
        with sqlite3.connect(self.index) as db:
            db.execute("UPDATE records SET doc=json_set(doc,'$.text','Altered')")
        with self.assertRaises(ValueError): build(self.source,self.index,self.output)
        self.assertFalse(self.output.exists())

    def test_auxiliary_data_is_never_silently_dropped(self):
        with sqlite3.connect(self.source) as db: db.execute('CREATE TABLE place_points(lat REAL)')
        with self.assertRaises(ValueError): build(self.source,self.index,self.output)
        self.assertFalse(self.output.exists())

    def test_map_points_and_vectors_survive_unchanged(self):
        with sqlite3.connect(self.source) as db:
            db.execute('CREATE TABLE place_points (rowid INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL, category TEXT)')
            db.execute('CREATE INDEX place_points_lat ON place_points(lat)')
            db.execute("INSERT INTO place_points VALUES (7, 38.71, -9.14, 'pharmacy')")
            db.execute('CREATE TABLE chunk_vectors(rowid INTEGER PRIMARY KEY, quant BLOB)')
            db.execute('INSERT INTO chunk_vectors VALUES (7, ?)', (bytes([1, 255, 0, 128]),))
        report = build(self.source, self.index, self.output)
        self.assertEqual(report['auxiliary'], ['chunk_vectors', 'place_points'])
        with sqlite3.connect(self.output) as db:
            self.assertEqual(db.execute('SELECT rowid,lat,lon,category FROM place_points').fetchall(), [(7, 38.71, -9.14, 'pharmacy')])
            self.assertEqual(db.execute('SELECT rowid,quant FROM chunk_vectors').fetchall(), [(7, bytes([1, 255, 0, 128]))])
            self.assertTrue(db.execute("SELECT 1 FROM sqlite_master WHERE type='index' AND name='place_points_lat'").fetchone())
            # The map row still joins the passage it belongs to.
            self.assertEqual(db.execute('SELECT c.title FROM place_points p JOIN chunks_fts c ON c.rowid = p.rowid').fetchall(), [('Water',)])

    def test_map_points_without_a_passage_are_rejected(self):
        with sqlite3.connect(self.source) as db:
            db.execute('CREATE TABLE place_points (rowid INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL)')
            db.execute('INSERT INTO place_points VALUES (8, 1.0, 2.0)')
        with self.assertRaises(ValueError): build(self.source, self.index, self.output)
        self.assertFalse(self.output.exists())

    def test_unknown_index_is_rejected(self):
        with sqlite3.connect(self.source) as db:
            db.execute('CREATE TABLE chunk_vectors(rowid INTEGER PRIMARY KEY, quant BLOB)')
            db.execute('CREATE INDEX vectors_quant ON chunk_vectors(quant)')
        with self.assertRaises(ValueError): build(self.source, self.index, self.output)
        self.assertFalse(self.output.exists())

    def test_missing_rows_rejected(self):
        with sqlite3.connect(self.source) as db:
            db.execute("INSERT INTO chunks_fts VALUES('c2','d2','Other','Local','Another passage.')")
        with self.assertRaises(ValueError): build(self.source,self.index,self.output)
        self.assertFalse(self.output.exists())

    def test_rights_and_source_provenance_survive_unchanged(self):
        source = '{"license":"CC-BY-4.0","title":"Source’s original title"}'
        dataset = '{"source_revision":"abc","review":"candidate"}'
        with sqlite3.connect(self.source) as db:
            db.execute('CREATE TABLE source_provenance(document_id TEXT PRIMARY KEY, metadata_json TEXT NOT NULL)')
            db.execute('INSERT INTO source_provenance VALUES (?,?)', ('d1', source))
            db.execute('CREATE TABLE dataset_provenance(metadata_json TEXT NOT NULL)')
            db.execute('INSERT INTO dataset_provenance VALUES (?)', (dataset,))
        build(self.source, self.index, self.output)
        with sqlite3.connect(self.output) as db:
            self.assertEqual(db.execute('SELECT * FROM source_provenance').fetchall(), [('d1', source)])
            self.assertEqual(db.execute('SELECT * FROM dataset_provenance').fetchall(), [(dataset,)])

    def test_incomplete_provenance_is_rejected(self):
        with sqlite3.connect(self.source) as db:
            db.execute('CREATE TABLE source_provenance(document_id TEXT, metadata_json TEXT)')
            db.execute('INSERT INTO source_provenance VALUES (?,?)', ('missing-source', '{}'))
        with self.assertRaises(ValueError): build(self.source, self.index, self.output)
        self.assertFalse(self.output.exists())

    def test_unexpected_provenance_layout_is_rejected(self):
        with sqlite3.connect(self.source) as db:
            db.execute('CREATE TABLE dataset_provenance(metadata_json TEXT, extra TEXT)')
        with self.assertRaises(ValueError): build(self.source, self.index, self.output)

    def test_malformed_provenance_is_rejected(self):
        with sqlite3.connect(self.source) as db:
            db.execute('CREATE TABLE dataset_provenance(metadata_json TEXT)')
            db.execute("INSERT INTO dataset_provenance VALUES ('not json')")
        with self.assertRaises(ValueError): build(self.source, self.index, self.output)
