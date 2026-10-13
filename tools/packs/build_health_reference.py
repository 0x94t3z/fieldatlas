#!/usr/bin/env python3
"""Build a private health-summary candidate from a checksum-pinned MedlinePlus XML ZIP.

Only NLM full-summary fields are included, never licensed linked article bodies.
This is a foundational component, not a complete biology/longevity replacement.
"""

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import sys
import tempfile
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET
import zipfile

from tools.packs.stream_pack import build_pack_streaming

POLICY = 'https://medlineplus.gov/about/using/usingcontent/'
LICENSE = 'Public domain (U.S. government work)'
MAX_XML = 64 * 1024 * 1024

class SummaryText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []
    def handle_starttag(self, tag, attrs):
        if tag == 'p.':
            tag = 'p'  # Observed publisher typo; preserve its text and paragraph boundary.
        if tag == 'img':
            # Image rights differ from summary text. Never fetch or embed images.
            return
        if tag not in {'p','a','ul','ol','li','h1','h2','h3','h4','br','strong','b','em','i','u','span','sup','sub','table','thead','tbody','tr','td','th','caption'}:
            raise ValueError(f'Unexpected summary markup: {tag}')
        if tag in {'p','li','h1','h2','h3','h4','br','tr','caption'}:
            self.parts.append('\n')
        if tag == 'li':
            self.parts.append('* ')
    def handle_endtag(self, tag):
        if tag == 'p.':
            tag = 'p'
        if tag in {'td','th'}:
            self.parts.append(' | ')
        if tag in {'p','li','h1','h2','h3','h4','tr','caption'}:
            self.parts.append('\n')
    def handle_data(self, data):
        self.parts.append(data)

def load_topics(archive, expected_hash):
    # Bounded input; no extraction and no external XML entity resolution.
    if archive.stat().st_size > MAX_XML:
        raise ValueError('Archive exceeds size limit')
    raw = archive.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_hash:
        raise ValueError('Archive checksum mismatch')
    with zipfile.ZipFile(archive) as z:
        if len(z.infolist()) != 1:
            raise ValueError('Expected one XML member')
        info = z.infolist()[0]
        if not info.filename.endswith('.xml') or info.file_size > MAX_XML:
            raise ValueError('Invalid or oversized XML member')
        xml = z.read(info)
    if b'<!ENTITY' in xml.upper():
        raise ValueError('XML entity declarations are not allowed')
    root = ET.fromstring(xml)
    if root.tag != 'health-topics' or len(root) != int(root.attrib['total']):
        raise ValueError('Topic count or root mismatch')
    generated = root.attrib['date-generated']
    rows, metadata, skipped, seen = [], [], [], set()
    for topic in root:
        if topic.tag != 'health-topic':
            raise ValueError('Unexpected XML record')
        if topic.attrib.get('language') != 'English':
            continue
        ident, original = topic.attrib['id'], topic.attrib['title']
        if not re.fullmatch(r'\d+',ident) or ident in seen:
            raise ValueError('Invalid or duplicate topic ID')
        seen.add(ident)
        url = topic.attrib['url']
        parsed = urlsplit(url)
        if parsed.scheme != 'https' or parsed.netloc != 'medlineplus.gov' or not parsed.path.endswith('.html') or parsed.path.startswith(('/ency/','/druginfo/')):
            raise ValueError('Not an eligible health topic URL')
        html = topic.findtext('full-summary','')
        parser = SummaryText()
        parser.feed(html)
        parser.close()
        body = '\n'.join(line.strip() for line in ''.join(parser.parts).splitlines() if line.strip())
        if not body:
            skipped.append(ident)
            continue
        did = 'medlineplus-' + ident
        rows.append(dict(document_id=did,title=re.sub(r'\s*\u2014\s*', ': ', original),source=url,
                         license=LICENSE,text='Source: MedlinePlus, National Library of Medicine.\n\n'+body))
        metadata.append((did,json.dumps(dict(original_title=original,url=url,
            publisher='MedlinePlus, National Library of Medicine',license=LICENSE,rights_url=POLICY,
            dataset_generated=generated,medical_review_date=None,evidence_type='health topic summary',
            groups=[g.text for g in topic.findall('group')],
            original_summary_sha256=hashlib.sha256(html.encode()).hexdigest()),sort_keys=True)))
    if not rows:
        raise ValueError('No eligible summaries')
    return rows,metadata,dict(archive_sha256=expected_hash,dataset_generated=generated,
                             included=len(rows),skipped_empty=skipped,rights_url=POLICY)

def build(archive, expected_hash, output, version):
    if output.exists():
        raise ValueError('Output already exists')
    rows,metadata,report = load_topics(archive,expected_hash)
    def extend(db):
        db.execute('CREATE TABLE source_provenance (document_id TEXT PRIMARY KEY, metadata_json TEXT NOT NULL)')
        db.executemany('INSERT INTO source_provenance VALUES (?,?)',metadata)
        db.execute('CREATE TABLE dataset_provenance (metadata_json TEXT NOT NULL)')
        db.execute('INSERT INTO dataset_provenance VALUES (?)',(json.dumps(report,sort_keys=True),))
    with tempfile.TemporaryDirectory(prefix='fieldatlas-health-') as temp:
        documents = Path(temp)/'documents.jsonl'
        documents.write_text(''.join(json.dumps(row,ensure_ascii=False)+'\n' for row in rows),encoding='utf-8')
        return build_pack_streaming(documents,output,'biology-health-candidate',version,
            'Biology & Healthy Aging',LICENSE,['https://medlineplus.gov/',POLICY],
            coverage_summary='Health topic summaries only. Development candidate, not a complete biology or longevity research collection.',
            example_questions=['What is healthy aging?','How can exercise help older adults?'],
            coverage_level='focused',fts_tokenizer='porter unicode61',extend_database=extend)

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--archive',type=Path,required=True)
    p.add_argument('--sha256',required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--version',required=True)
    a = p.parse_args()
    result = build(a.archive,a.sha256,a.output,a.version)
    print(result.pack)
