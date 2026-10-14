import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tempfile
import unittest
from tools.packs.index_pack_for_leviathan import fold, index, places


class PlaceFilterValuesTest(unittest.TestCase):
    def test_folds_like_the_app(self):
        self.assertEqual(fold('Zürich'), 'zurich')
        self.assertEqual(fold('São Paulo'), 'sao paulo')

    def test_destination_and_its_parents(self):
        self.assertEqual(places('Edmonton/South — Huma'), ['edmonton', 'edmonton/south'])
        self.assertEqual(places('Lisbon — Vegan Story'), ['lisbon'])
        self.assertEqual(places('Untitled listing'), [])


@unittest.skipUnless(os.environ.get('FIELDATLAS_LEVIATHAN'), 'set FIELDATLAS_LEVIATHAN to a verified leviathan 0.2.0 binary')
class LeviathanIndexTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.binary = Path(os.environ['FIELDATLAS_LEVIATHAN'])

    def pack(self, rows):
        path = self.root / 'content.sqlite'
        with sqlite3.connect(path) as db:
            db.execute('PRAGMA user_version=1')
            db.execute("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED, document_id UNINDEXED, title, source UNINDEXED, text, tokenize='porter unicode61')")
            db.executemany('INSERT INTO chunks_fts(rowid,chunk_id,document_id,title,source,text) VALUES (?,?,?,?,?,?)', rows)
        return path

    def search(self, output, *args):
        result = subprocess.run([str(self.binary), 'search', *args, '--index', str(output), '--json'],
                                capture_output=True, text=True, timeout=30, check=True)
        return [card['id'] for card in json.loads(result.stdout)['results']]

    def test_place_pack_searches_within_one_city(self):
        database = self.pack([
            (3, 'osm-place-n1:0000', 'osm-place-n1', 'Lisbon — Vegan Story', 'https://www.openstreetmap.org/node/1', 'Destination: Lisbon\nType: bakery'),
            (9, 'osm-place-n2:0000', 'osm-place-n2', 'Zürich/Altstadt — Hiltl', 'https://www.openstreetmap.org/node/2', 'Destination: Zürich\nType: bakery'),
        ])
        output = self.root / 'leviathan.sqlite'
        record = index(database, self.binary, output)
        self.assertEqual(record['placeFilter'], 'places[]')
        self.assertEqual(record['records'], 2)
        self.assertNotIn(str(self.root), json.dumps(record))
        self.assertEqual(self.search(output, 'bakery', '--where', 'places[]=zurich'), ['osm-place-n2:0000'])
        self.assertEqual(self.search(output, 'bakery', '--where', 'places[]=lisbon'), ['osm-place-n1:0000'])
        with sqlite3.connect(output) as db:
            doc = json.loads(db.execute("SELECT doc FROM records WHERE id='osm-place-n2:0000'").fetchone()[0])
        self.assertEqual(doc['source_rowid'], 9)
        with self.assertRaises(ValueError): index(database, self.binary, output)

    def test_text_pack_has_no_place_filter(self):
        database = self.pack([(1, 'c1', 'd1', 'Water — Overview', 'https://example.test/water', 'Water freezes into ice.')])
        record = index(database, self.binary, self.root / 'leviathan.sqlite')
        self.assertIsNone(record['placeFilter'])
        self.assertEqual(self.search(self.root / 'leviathan.sqlite', 'ice'), ['c1'])


if __name__ == '__main__':
    unittest.main()
