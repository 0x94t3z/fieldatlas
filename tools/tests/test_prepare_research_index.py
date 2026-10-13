import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('prepare_research_index', Path(__file__).resolve().parents[1] / 'evaluation/prepare_research_index.py')
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class ResearchIndexTests(unittest.TestCase):
    def fixture(self, root):
        source = root / 'source.sqlite'
        index = root / 'index.sqlite'
        binary = root / 'binary'
        binary.write_bytes(b'pinned tool')
        with sqlite3.connect(source) as sql:
            sql.execute('PRAGMA user_version=1')
            sql.execute('CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)')
            sql.execute("INSERT INTO chunks_fts VALUES('id','doc','title','source','full passage')")
        with sqlite3.connect(index) as sql:
            sql.execute('CREATE TABLE records(id TEXT,doc TEXT)')
            sql.execute("INSERT INTO records VALUES('id','{}')")
        data = dict(schemaVersion=1, scope='desktop_full_pack_experiment', productionAdopted=False, records=1,
                    source=tool.identity(source), index=tool.identity(index), binary=tool.identity(binary))
        manifest = root / 'manifest.json'
        manifest.write_text(json.dumps(data))
        return source, index, binary, manifest

    def test_pinned_manifest_accepts_full_pack_and_rejects_wrong_pack(self):
        with tempfile.TemporaryDirectory() as directory:
            source, _, _, manifest = self.fixture(Path(directory))
            self.assertEqual(1, tool.validate_manifest(manifest, [source])['records'])
            for databases in [[], [source, source], [source.parent / 'other.sqlite']]:
                with self.assertRaises(ValueError): tool.validate_manifest(manifest, databases)

    def test_changed_source_index_or_binary_is_rejected(self):
        for key in ['source', 'index', 'binary']:
            with tempfile.TemporaryDirectory() as directory:
                source, index, binary, manifest = self.fixture(Path(directory))
                path = dict(source=source, index=index, binary=binary)[key]
                with path.open('ab') as stream: stream.write(b'changed')
                with self.assertRaisesRegex(ValueError, key): tool.validate_manifest(manifest, [source])

    def test_duplicate_ids_empty_pack_and_wrong_schema_are_rejected(self):
        for sql in ["INSERT INTO chunks_fts SELECT * FROM chunks_fts", 'DELETE FROM chunks_fts', 'PRAGMA user_version=0']:
            with tempfile.TemporaryDirectory() as directory:
                source, _, _, _ = self.fixture(Path(directory))
                with sqlite3.connect(source) as db: db.execute(sql)
                with self.assertRaises(ValueError): tool.validate_source(source)

    def test_existing_work_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            source, _, binary, _ = self.fixture(Path(directory))
            with patch.object(tool.subprocess, 'run') as run:
                with self.assertRaisesRegex(ValueError, 'overwrite'): tool.prepare(source, binary, Path(directory))
                run.assert_not_called()

    def test_subset_index_is_rejected_even_with_valid_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            source, index, _, manifest = self.fixture(Path(directory))
            with sqlite3.connect(index) as db: db.execute('DELETE FROM records')
            data = json.loads(manifest.read_text()); data['index'] = tool.identity(index)
            manifest.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, 'entire pack'): tool.validate_manifest(manifest, [source])
