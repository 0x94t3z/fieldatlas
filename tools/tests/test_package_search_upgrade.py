import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import zipfile
from tools.packs.package_search_upgrade import package, digest


class SearchUpgradePackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        old=self.root/'old';old.mkdir()
        (old/'content.sqlite').write_bytes(b'original payload identity fixture')
        (old/'origins.json').write_text('{"credit":"synthetic source"}')
        self.manifest=old/'manifest.json'
        self.manifest.write_text(json.dumps(dict(schemaVersion=1,id='synthetic',version='1',type='KNOWLEDGE',title='Synthetic',
            license='CC0-1.0',sourceUrls=['https://example.test/reference'],artifacts=[
                dict(path=p.name,bytes=p.stat().st_size,sha256=digest(p)) for p in [old/'content.sqlite',old/'origins.json']
            ])))
        self.database=self.root/'converted.sqlite'
        with sqlite3.connect(self.database) as db:
            db.execute('CREATE TABLE fieldatlas_search(format INTEGER,engine TEXT)')
            db.execute("INSERT INTO fieldatlas_search VALUES(1,'leviathan-0.2.0')")
            db.execute('CREATE TABLE meta(key TEXT,value TEXT)')
        self.output=self.root/'new.fapack'

    def test_stored_archive_preserves_identity_and_credits(self):
        report=package(self.manifest,self.database,self.output,'2-preview')
        self.assertFalse(report['published']);self.assertFalse(report['installed'])
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(archive.namelist(),['manifest.json','content.sqlite','origins.json'])
            self.assertTrue(all(i.compress_type==zipfile.ZIP_STORED for i in archive.infolist()))
            manifest=json.loads(archive.read('manifest.json'))
            self.assertEqual(manifest['id'],'synthetic')
            self.assertEqual(manifest['version'],'2-preview')
            self.assertEqual(archive.read('origins.json'),(self.manifest.parent/'origins.json').read_bytes())
        with self.assertRaises(ValueError):package(self.manifest,self.database,self.output,'3')

    def test_refuses_changed_provenance_or_same_version(self):
        with self.assertRaises(ValueError):package(self.manifest,self.database,self.output,'1')
        (self.manifest.parent/'origins.json').write_text('changed')
        with self.assertRaises(ValueError):package(self.manifest,self.database,self.output,'2')
        self.assertFalse(self.output.exists())

    def test_compressed_schema_two_preserves_expanded_sizes_and_credits(self):
        report=package(self.manifest,self.database,self.output,'2-compressed',compressed=True)
        self.assertEqual(report['manifest']['schemaVersion'],2)
        self.assertEqual(report['installedBytes'],sum(a['bytes'] for a in report['manifest']['artifacts']))
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(archive.getinfo('manifest.json').compress_type,zipfile.ZIP_STORED)
            self.assertEqual(archive.getinfo('content.sqlite').compress_type,zipfile.ZIP_DEFLATED)
            self.assertEqual(archive.read('content.sqlite'),self.database.read_bytes())
            self.assertEqual(archive.read('origins.json'),(self.manifest.parent/'origins.json').read_bytes())

    def test_refuses_build_machine_metadata(self):
        with sqlite3.connect(self.database) as db:db.execute("INSERT INTO meta VALUES('source_manifest','private path')")
        with self.assertRaises(ValueError):package(self.manifest,self.database,self.output,'2')
        self.assertFalse(self.output.exists())
