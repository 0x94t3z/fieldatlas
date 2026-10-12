import json
import gzip
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from combine_knowledge_pack import combine
from packtool.build_pack import build_pack, _digest


class CombinedKnowledgeTest(unittest.TestCase):
    def make(self, root, name, doc='one', text='Original qualification remains.'):
        source = root / (name + '.jsonl')
        source.write_text(json.dumps(dict(document_id=doc, title=doc, source='https://example.org/' + doc,
            license='CC0-1.0', text=text)) + '\n')
        result = build_pack(source, root / name, name, '1', name, 'CC0-1.0', ['https://example.org'])
        size, digest = _digest(result.pack)
        return dict(kind='archive', path=str(result.pack), bytes=size, sha256=digest)

    def config(self, inputs):
        return dict(id='combined', version='1', title='One theme', inputs=inputs)

    def test_one_pack_preserves_exact_rows_and_source_attribution(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            inputs = [self.make(root, 'a'), self.make(root, 'b', 'two', 'A separate source, with a caveat.')]
            report = combine(self.config(inputs), root / 'out')
            self.assertEqual(2, report['counts']['outputChunks'])
            self.assertTrue(report['exactSourceChunkAuditPassed'])
            self.assertFalse(report['phoneMigrationAccepted'])
            with zipfile.ZipFile(root / 'out/combined-1.fapack') as archive:
                self.assertEqual(['manifest.json', 'content.sqlite', 'origins.json'], archive.namelist())
                self.assertEqual(2, len(json.loads(archive.read('origins.json'))['sources']))
                self.assertNotIn(str(root), archive.read('origins.json').decode())
            for item in inputs:
                self.assertEqual((item['bytes'], item['sha256']), _digest(Path(item['path'])))

    def test_exact_duplicate_chunks_are_not_added_twice(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            report = combine(self.config([self.make(root, 'a'), self.make(root, 'b')]), root / 'out')
            self.assertEqual(dict(inputChunks=2, outputChunks=1, exactDuplicateChunks=1), report['counts'])

    def test_conflicting_chunk_ids_fail_without_losing_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            inputs = [self.make(root, 'a'), self.make(root, 'b', text='Conflicting content.')]
            with self.assertRaisesRegex(ValueError, 'Conflicting chunk'):
                combine(self.config(inputs), root / 'out')
            self.assertFalse((root / 'out').exists())
            self.assertTrue(all(Path(item['path']).is_file() for item in inputs))

    def test_bad_pin_and_existing_destination_fail_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            item = self.make(root, 'a')
            with self.assertRaisesRegex(ValueError, 'checksum'):
                combine(self.config([item | {'sha256': '0' * 64}]), root / 'out')
            (root / 'out').mkdir()
            with self.assertRaisesRegex(ValueError, 'already exists'):
                combine(self.config([item]), root / 'out')

    def test_repeated_pack_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            item = self.make(root, 'a')
            with self.assertRaisesRegex(ValueError, 'Repeated source'):
                combine(self.config([item, item]), root / 'out')

    def test_gzip_archive_and_direct_database_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            first = self.make(root, 'a')
            compressed = root / 'a.fapack.gz'
            compressed.write_bytes(gzip.compress(Path(first['path']).read_bytes()))
            size, digest = _digest(compressed)
            compressed_input = dict(kind='gzip-archive', path=str(compressed), bytes=size, sha256=digest,
                archiveBytes=first['bytes'], archiveSha256=first['sha256'])
            self.make(root, 'b', 'two')
            database = root / 'b/content.sqlite'
            manifest = root / 'b/manifest.json'
            direct = dict(kind='database', path=str(database), bytes=_digest(database)[0], sha256=_digest(database)[1],
                manifest=dict(path=str(manifest), bytes=_digest(manifest)[0], sha256=_digest(manifest)[1]))
            report = combine(self.config([compressed_input, direct]), root / 'out')
            self.assertEqual(2, report['counts']['outputChunks'])
