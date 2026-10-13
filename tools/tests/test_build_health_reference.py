import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import zipfile

from tools.packs.build_health_reference import build, load_topics, SummaryText

class HealthReferenceTest(unittest.TestCase):
    def test_tables_preserve_cells_and_publisher_typo_preserves_attribution(self):
        parser = SummaryText()
        parser.feed('<table><tr><th>Measure</th><th>Meaning</th></tr><tr><td>Low</td><td>Not always safe</td></tr></table><p.>NIH attribution</p.><img src="unlicensed.png"/>')
        text = ''.join(parser.parts)
        self.assertIn('Measure | Meaning |',text)
        self.assertIn('Low | Not always safe |',text)
        self.assertIn('\nNIH attribution\n',text)
        self.assertNotIn('unlicensed',text)

    def fixture(self, root, body=None):
        body = body or '''<health-topic id="1" language="English" title="Health &#8212; aging" url="https://medlineplus.gov/aging.html"><full-summary>&lt;p&gt;Benefits are uncertain &amp;amp; vary.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Do not stop treatment.&lt;/li&gt;&lt;/ul&gt;</full-summary><site>LICENSED LINKED BODY</site></health-topic>'''
        path = root/'input.zip'
        with zipfile.ZipFile(path,'w') as z:
            z.writestr('topics.xml','<health-topics total="1" date-generated="10/10/2026">'+body+'</health-topics>')
        return path,hashlib.sha256(path.read_bytes()).hexdigest()

    def test_build_preserves_qualification_attribution_and_provenance(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive,sha = self.fixture(root)
            result = build(archive,sha,root/'pack','test1')
            with sqlite3.connect(result.database) as db:
                title,source,text = db.execute('SELECT title,source,text FROM chunks_fts LIMIT 1').fetchone()
                self.assertEqual(title,'Health: aging')
                self.assertIn('medlineplus.gov',source)
                self.assertIn('uncertain & vary',text)
                self.assertIn('Do not stop treatment.',text)
                self.assertNotIn('LICENSED LINKED BODY',text)
                self.assertIn('National Library of Medicine',text)
                meta = json.loads(db.execute('SELECT metadata_json FROM source_provenance').fetchone()[0])
                self.assertIsNone(meta['medical_review_date'])
                self.assertEqual(meta['original_title'],'Health \u2014 aging')
            with zipfile.ZipFile(result.pack) as z:
                manifest = json.loads(z.read('manifest.json'))
                self.assertEqual(hashlib.sha256(z.read('content.sqlite')).hexdigest(),manifest['artifacts'][0]['sha256'])
            rebuilt = build(archive,sha,root/'rebuilt','test1')
            self.assertEqual(result.pack.read_bytes(),rebuilt.pack.read_bytes())
            with self.assertRaisesRegex(ValueError,'already exists'):
                build(archive,sha,root/'pack','test1')

    def test_bad_hash_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive,_ = self.fixture(Path(tmp))
            with self.assertRaisesRegex(ValueError,'checksum'):
                load_topics(archive,'0'*64)

    def test_duplicate_topic_ids_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            topic = '<health-topic id="1" language="English" title="X" url="https://medlineplus.gov/x.html"><full-summary>Text</full-summary></health-topic>'
            archive = root/'input.zip'
            with zipfile.ZipFile(archive,'w') as z:
                z.writestr('topics.xml','<health-topics total="2" date-generated="2026">'+topic*2+'</health-topics>')
            with self.assertRaisesRegex(ValueError,'duplicate'):
                load_topics(archive,hashlib.sha256(archive.read_bytes()).hexdigest())

    def test_entity_declaration_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp)/'input.zip'
            with zipfile.ZipFile(archive,'w') as z:
                z.writestr('topics.xml','<!DOCTYPE health-topics [<!ENTITY x "unsafe">]><health-topics total="0"/>')
            with self.assertRaisesRegex(ValueError,'entity'):
                load_topics(archive,hashlib.sha256(archive.read_bytes()).hexdigest())

    def test_unexpected_active_markup_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive,sha = self.fixture(Path(tmp),'<health-topic id="1" language="English" title="X" url="https://medlineplus.gov/x.html"><full-summary>&lt;script&gt;bad&lt;/script&gt;</full-summary></health-topic>')
            with self.assertRaisesRegex(ValueError,'markup'):
                load_topics(archive,sha)

    def test_licensed_encyclopedia_url_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive,sha = self.fixture(Path(tmp),'<health-topic id="1" language="English" title="X" url="https://medlineplus.gov/ency/x.html"><full-summary>Text</full-summary></health-topic>')
            with self.assertRaisesRegex(ValueError,'eligible'):
                load_topics(archive,sha)

    def test_no_english_summaries_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive,sha = self.fixture(Path(tmp),'<health-topic id="1" language="Spanish"/>')
            with self.assertRaisesRegex(ValueError,'No eligible'):
                load_topics(archive,sha)

if __name__ == '__main__':
    unittest.main()
