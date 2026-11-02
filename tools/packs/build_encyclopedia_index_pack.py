#!/usr/bin/env python3
"""Meaning-search companion packs for the installed Encyclopedia, travel and biology packs.

The app searches those packs by keyword only, so a question such as "when did The Strokes
release Is This It" can rank the medical article "Stroke" first. Each companion pack holds
passages copied byte for byte from an installed pack (same document_id, chunk_id, title,
source and text) plus bge-small vectors. The app searches it as one more knowledge pack,
and its passages merge with the installed pack's own copies through the shared
(documentId, chunkId, source) identity, so the companion adds no duplicate evidence.

  encyclopedia-index       the lead of every Simple English Wikipedia article
  wikivoyage-guides-index  every section of the Wikivoyage destination guides
  biology-index            OpenStax Biology 2e, MedlinePlus and HAGR (no PMC papers)

Stages (outputs under build/<pack-id>/, which git ignores):

  1. base    copy the passages into a schema-1 text pack (packtool format, deterministic)
  2. embed   tools/packs/embed_pack.py with the crypto pack's encoder
  3. vectors tools/packs/build_vector_pack.py (quantization guard + read-back checks)
  4. native  Leviathan 0.2.0 index + dual-search database + schema-2 compressed pack,
             the same packaging as the shipped Encyclopedia and crypto packs

  P=encyclopedia-index
  python3 tools/packs/build_encyclopedia_index_pack.py base --pack $P --version 2026.11.02-text
  python3 tools/packs/embed_pack.py --database build/$P/$P/content.sqlite \\
      --encoder build/phase0/packs/crypto-ethereum/encoder.gguf --output build/$P/vectors.tsv
  python3 tools/packs/build_vector_pack.py \\
      --base-fapack build/$P/$P/$P-2026.11.02-text.fapack --vectors build/$P/vectors.tsv \\
      --encoder build/phase0/packs/crypto-ethereum/encoder.gguf \\
      --encoder-url https://huggingface.co/QuantFactory/bge-small-en-v1.5-GGUF \\
      --version 2026.11.02-vectors --work-dir build/$P/vector-work
  python3 tools/packs/build_encyclopedia_index_pack.py native --pack $P \\
      --vector-fapack build/$P/$P/$P-2026.11.02-vectors.fapack --version 2026.11.02-leviathan

None of the packs declares discovery. MultiKnowledgeRetriever.shouldSearchVectors scans a
pack of more than 10,000 vectors only when the question shares a non-stopword term with
the pack's discovery summary or examples, and no summary can share a term with every
question these packs answer; without discovery every question with a term of three or more
letters is scanned. Each pack's intended description is kept in its build record for the
catalog.

Nothing here publishes, uploads, installs or edits a catalog. Source files are opened
read-only.
"""
from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
from contextlib import closing
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import time
import zipfile

from packtool.chunking import Chunk
from tools.packs.fapack_convert import verify_pack
from tools.packs.stream_pack import build_pack_streaming

ROOT = Path(__file__).resolve().parents[2]
PACKS_DIR = ROOT / "build/phase0/packs"
ENCODER_URL = "https://huggingface.co/QuantFactory/bge-small-en-v1.5-GGUF"
# A range scan on the records id index; the chunks_fts view would read every passage.
RANGE_SQL = """
    SELECT id,
           json_extract(doc, '$.document_id'),
           json_extract(doc, '$.title'),
           json_extract(doc, '$.source'),
           json_extract(doc, '$.text')
    FROM records
    WHERE id >= ? AND id < ?
"""


@dataclass(frozen=True)
class PackSpec:
    pack_id: str
    title: str
    source_pack: str
    # [low, high) ranges of chunk ids in the source pack's records table.
    ranges: tuple[tuple[str, str], ...]
    expected: int
    # The passages' licences, then MIT for the bge-small encoder build_vector_pack adds.
    license_id: str
    spool_license: str
    source_urls: tuple[str, ...]
    description: str
    leads_only: bool = False
    provenance: bool = False
    attribution: bool = False
    selection: str = ""
    notes: tuple[str, ...] = field(default_factory=tuple)


PACKS = {
    spec.pack_id: spec for spec in (
        PackSpec(
            pack_id="encyclopedia-index", title="Encyclopedia Index", source_pack="encyclopedia",
            ranges=(("swp-", "swp."),), expected=211_112, leads_only=True,
            license_id="CC-BY-SA-4.0 AND MIT", spool_license="CC-BY-SA-4.0",
            source_urls=(
                "https://dumps.wikimedia.org/other/cirrussearch/20251229/simplewiki-20251229-cirrussearch-content.json.gz",
                "https://simple.wikipedia.org/wiki/Wikipedia:Copyrights", ENCODER_URL),
            description="The opening of every Simple English Wikipedia article (211,112 subjects), "
                        "with meaning-based search",
            selection="chunk ids from swp- that end ':0000' with titles ending ' — Overview'"),
        PackSpec(
            pack_id="wikivoyage-guides-index", title="Travel Guides Index", source_pack="wikivoyage-guides",
            ranges=(("", "\U0010ffff"),), expected=363_815,
            license_id="CC-BY-SA-4.0 AND MIT", spool_license="CC-BY-SA-4.0",
            source_urls=(
                "https://dumps.wikimedia.org/enwikivoyage/20260901/enwikivoyage-20260901-pages-articles.xml.bz2",
                "https://en.wikivoyage.org/wiki/Wikivoyage:Copyleft", ENCODER_URL),
            description="Every section of 31,672 Wikivoyage destination guides, with meaning-based search",
            selection="every passage of the installed travel guides pack"),
        PackSpec(
            pack_id="biology-index", title="Biology Index", source_pack="biology",
            ranges=(("hagr-", "hagr."), ("medlineplus-", "medlineplus."), ("openstax-", "openstax.")),
            expected=22_053, provenance=True, attribution=True,
            license_id="CC-BY-4.0 AND CC-BY-3.0 AND LicenseRef-US-Government-Work AND MIT",
            spool_license="See source_provenance",
            source_urls=("https://openstax.org/details/books/biology-2e", "https://medlineplus.gov/",
                         "https://medlineplus.gov/genetics/", "https://genomics.senescence.info/", ENCODER_URL),
            description="OpenStax Biology 2e, MedlinePlus health and genetics summaries and Human Ageing "
                        "Genomic Resources, with meaning-based search",
            selection="document ids from hagr-, medlineplus- and openstax-; PMC papers left out",
            notes=("Passages over 1,800 characters (title and text) are embedded from their first 1,800 "
                   "characters, as embed_pack.py does for every pack.",)),
    )
}
# Sections of the biology pack's ATTRIBUTION.txt that credit only PMC papers, not copied.
PMC_SECTIONS = ("SELECTED PMC REVIEW ARTICLES", "PUBMED CENTRAL OPEN-ACCESS PAPERS")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def canonical(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def read_passages(spec: PackSpec, source: Path) -> list[tuple[str, str, str, str, str]]:
    with closing(sqlite3.connect(source.resolve(strict=True).as_uri() + "?mode=ro", uri=True)) as db:
        rows = [row for low, high in spec.ranges for row in db.execute(RANGE_SQL, (low, high))]
    if spec.leads_only:
        rows = [row for row in rows if row[0].endswith(":0000") and row[2].endswith(" — Overview")]
    by_document: dict[str, list] = {}
    for row in rows:
        by_document.setdefault(row[1], []).append(row)
    for document_id, chunks in by_document.items():
        # The pack writer numbers nothing itself, so ids must already be the document's own.
        ids = sorted(chunk[0] for chunk in chunks)
        if spec.leads_only:
            if ids != [f"{document_id}:0000"]:
                raise ValueError(f"{document_id}: a lead must be its document's first passage")
        elif ids != [f"{document_id}:{index:04d}" for index in range(len(chunks))]:
            raise ValueError(f"{document_id}: passages are not numbered :0000 onward")
    if len(rows) != spec.expected:
        raise ValueError(f"Expected {spec.expected} passages, found {len(rows)}")
    return rows


def passage_digest(rows) -> str:
    """Digest of (chunk_id, document_id, title, source, text) in chunk_id byte order."""
    digest = hashlib.sha256()
    for row in sorted(rows, key=lambda r: r[0].encode("utf-8")):
        digest.update(canonical(list(row)))
    return digest.hexdigest()


def exact_chunker(rows):
    """Emit each document's source passages unchanged.

    packtool normalizes spool text (NFKC, trimmed) and would renumber chunks; the source
    passages must keep their exact bytes and ids for the app's dedupe, so the spool only
    orders documents and the passages come from the source rows.
    """
    by_document: dict[str, list] = {}
    for chunk_id, document_id, title, url, text in rows:
        by_document.setdefault(document_id, []).append(
            Chunk(chunk_id=chunk_id, document_id=document_id, title=title, source=url, text=text))

    def chunker(document):
        chunks = by_document.pop(document.document_id)
        return sorted(chunks, key=lambda chunk: chunk.chunk_id.encode("utf-8"))

    return chunker, by_document


def provenance_extension(source: Path, document_ids: set[str]):
    """Copy the source pack's credit rows for the selected documents; build_dual_search_pack keeps them."""
    with closing(sqlite3.connect(source.resolve(strict=True).as_uri() + "?mode=ro", uri=True)) as db:
        documents = sorted((row for row in db.execute("SELECT document_id, metadata_json FROM source_provenance")
                            if row[0] in document_ids), key=lambda row: row[0].encode("utf-8"))
        dataset = db.execute("SELECT metadata_json FROM dataset_provenance ORDER BY rowid").fetchall()
    if {row[0] for row in documents} != document_ids:
        raise ValueError("Source provenance must cover every selected document")

    def extend(database):
        database.execute("CREATE TABLE source_provenance(document_id TEXT PRIMARY KEY, metadata_json TEXT NOT NULL)")
        database.executemany("INSERT INTO source_provenance VALUES (?, ?)", documents)
        database.execute("CREATE TABLE dataset_provenance(metadata_json TEXT NOT NULL)")
        database.executemany("INSERT INTO dataset_provenance VALUES (?)", dataset)

    return extend, len(documents), len(dataset)


def subset_attribution(text: str) -> str:
    """The source credits with the PMC-only sections removed; each heading is a line of capitals."""
    kept, skipping = [], False
    for line in text.splitlines(keepends=True):
        heading = line.strip()
        if heading and heading == heading.upper() and any(c.isalpha() for c in heading) and len(heading) < 120:
            skipping = heading.startswith(PMC_SECTIONS)
        if not skipping:
            kept.append(line)
    if any(section in "".join(kept) for section in PMC_SECTIONS):
        raise ValueError("PMC credits left in the subset attribution")
    note = ("This Biology Index pack holds only the OpenStax, MedlinePlus and HAGR passages of the "
            "Biology & longevity pack; its PMC papers are not included.\n\n")
    return note + "".join(kept).rstrip("\n") + "\n"


def build_base(spec: PackSpec, out_root: Path, version: str) -> dict:
    started = time.monotonic()
    source = PACKS_DIR / spec.source_pack / "content.sqlite"
    out_root.mkdir(parents=True, exist_ok=True)
    pack_dir = out_root / spec.pack_id
    spool = out_root / "documents.jsonl"
    if pack_dir.exists() or spool.exists():
        raise ValueError("Base output already exists; remove it or choose a new output root")
    rows = read_passages(spec, source)
    expected = passage_digest(rows)
    first: dict[str, tuple] = {}
    for row in sorted(rows, key=lambda r: r[0].encode("utf-8")):
        first.setdefault(row[1], row)
    with spool.open("w", encoding="utf-8", newline="\n") as out:
        for document_id in sorted(first, key=lambda d: d.encode("utf-8")):
            _, _, title, url, text = first[document_id]
            out.write(json.dumps({"document_id": document_id, "title": title, "source": url,
                                  "license": spec.spool_license, "text": text},
                                 ensure_ascii=False, sort_keys=True) + "\n")
    chunker, unused = exact_chunker(rows)
    extend, provenance = None, None
    if spec.provenance:
        extend, documents, datasets = provenance_extension(source, set(first))
        provenance = {"documents": documents, "datasetRows": datasets}
    artifacts = build_pack_streaming(spool, pack_dir, spec.pack_id, version, spec.title, spec.license_id,
                                     list(spec.source_urls), fts_tokenizer="porter unicode61",
                                     chunker=chunker, extend_database=extend)
    if unused:
        raise ValueError(f"{len(unused)} documents were not written")
    summary = verify_pack(artifacts.pack)
    with closing(sqlite3.connect(artifacts.database.as_uri() + "?mode=ro", uri=True)) as db:
        built = db.execute("SELECT chunk_id, document_id, title, source, text FROM chunks_fts").fetchall()
    if len(built) != len(rows) or passage_digest(built) != expected:
        raise ValueError("Built passages differ from the source pack's")
    if spec.attribution:
        credits = subset_attribution((source.parent / "ATTRIBUTION.txt").read_text(encoding="utf-8"))
        (out_root / "ATTRIBUTION.txt").write_text(credits, encoding="utf-8", newline="\n")
    record = {
        "status": "BUILT_NOT_ANSWER_VALIDATED", "id": spec.pack_id, "version": version,
        "passages": len(rows), "documents": len(first), "passagesSha256": expected,
        "description": spec.description, "discovery": None, "sourceSelection": spec.selection,
        "sourcePackManifestSha256": sha256_file(source.parent / "manifest.json"),
        "provenance": provenance, "notes": list(spec.notes),
        "builderSha256": sha256_file(Path(__file__)), "spoolSha256": sha256_file(spool),
        "packSha256": sha256_file(artifacts.pack), "packBytes": artifacts.pack.stat().st_size,
        "databaseBytes": artifacts.database.stat().st_size, "seconds": round(time.monotonic() - started, 1),
        "ftsSample": summary["sample_hit"],
    }
    (out_root / "base-record.json").write_bytes(canonical(record))
    return record


def verify_native(pack: Path) -> dict:
    """The app's import checks for the pack, plus both search paths and the vectors."""
    work = pack.parent / f".verify-{pack.stem}"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir()
    try:
        with zipfile.ZipFile(pack) as archive:
            names = archive.namelist()
            if names[0] != "manifest.json":
                raise ValueError("manifest.json must be the first entry")
            manifest = json.loads(archive.read("manifest.json"))
            declared = {item["path"]: item for item in manifest["artifacts"]}
            if set(names[1:]) != set(declared) or len(names) != len(set(names)):
                raise ValueError("ZIP entries differ from declared artifacts")
            for name in names[1:]:
                target = work / name
                with archive.open(name) as src, target.open("wb") as dst:
                    shutil.copyfileobj(src, dst, 1 << 20)
                item = declared[name]
                if target.stat().st_size != item["bytes"] or sha256_file(target) != item["sha256"]:
                    raise ValueError(f"{name} does not match its manifest pin")
        if manifest["type"] != "KNOWLEDGE" or manifest["schemaVersion"] not in (1, 2):
            raise ValueError("Not an importable knowledge manifest")
        embedding = manifest["embedding"]
        if sha256_file(work / embedding["encoderPath"]) != embedding["encoderSha256"]:
            raise ValueError("Encoder does not match its pin")
        with closing(sqlite3.connect(work / "content.sqlite")) as db:
            if db.execute("PRAGMA quick_check").fetchone() != ("ok",):
                raise ValueError("quick_check failed")
            if db.execute("SELECT format, engine FROM fieldatlas_search").fetchall() != [(1, "leviathan-0.2.0")]:
                raise ValueError("Native search marker missing")
            passages = db.execute("SELECT count(*) FROM chunks_fts").fetchone()[0]
            records = db.execute("SELECT count(*) FROM records").fetchone()[0]
            vectors = db.execute("SELECT count(*) FROM chunk_vectors").fetchone()[0]
            orphans = db.execute("SELECT count(*) FROM chunk_vectors WHERE rowid NOT IN (SELECT rowid FROM chunks_fts)").fetchone()[0]
            word = manifest["title"].split()[0]
            hit = db.execute("SELECT title FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts) LIMIT 1",
                             (f'"{word}"',)).fetchone()
        if not passages == records == vectors == embedding["count"] or orphans:
            raise ValueError(f"Counts disagree: passages={passages} records={records} vectors={vectors}")
        return {"passages": passages, "vectors": vectors, "ftsSample": hit[0] if hit else None,
                "installedBytes": sum(item["bytes"] for item in manifest["artifacts"])}
    finally:
        shutil.rmtree(work, ignore_errors=True)


def build_native(spec: PackSpec, vector_pack: Path, out_root: Path, version: str, leviathan: Path) -> dict:
    from tools.packs import build_dual_search_pack, index_pack_for_leviathan, package_search_upgrade

    started = time.monotonic()
    native = out_root / "native"
    if native.exists():
        raise ValueError("Native output already exists; remove it or choose a new output root")
    unpacked = native / "vector-pack"
    unpacked.mkdir(parents=True)
    with zipfile.ZipFile(vector_pack) as archive:
        for name in archive.namelist():
            if Path(name).name != name:
                raise ValueError("Unexpected nested entry in the vector pack")
            with archive.open(name) as src, (unpacked / name).open("wb") as dst:
                shutil.copyfileobj(src, dst, 1 << 20)
    if spec.attribution:
        # build_vector_pack keeps only the database and encoder; the credits travel pinned beside them.
        manifest = json.loads((unpacked / "manifest.json").read_bytes())
        shutil.copyfile(out_root / "ATTRIBUTION.txt", unpacked / "ATTRIBUTION.txt")
        manifest["artifacts"].append({"bytes": (unpacked / "ATTRIBUTION.txt").stat().st_size,
                                      "path": "ATTRIBUTION.txt", "sha256": sha256_file(unpacked / "ATTRIBUTION.txt")})
        (unpacked / "manifest.json").write_bytes(canonical(manifest))
    index_record = index_pack_for_leviathan.index(unpacked / "content.sqlite", leviathan, native / "leviathan.sqlite")
    dual = build_dual_search_pack.build(unpacked / "content.sqlite", native / "leviathan.sqlite",
                                        native / "content.sqlite")
    packaged = package_search_upgrade.package(unpacked / "manifest.json", native / "content.sqlite",
                                              native / f"{spec.pack_id}-{version}.fapack", version, compressed=True)
    pack = Path(packaged["pack"])
    (native / "manifest.json").write_bytes(canonical(packaged["manifest"]))
    checks = verify_native(pack)
    record = {"pack": pack.name, "packBytes": packaged["bytes"], "packSha256": packaged["sha256"],
              "installedBytes": packaged["installedBytes"], "leviathanIndexSeconds": index_record["indexSeconds"],
              "dualSearch": dual, "checks": checks, "seconds": round(time.monotonic() - started, 1)}
    (native / "native-record.json").write_bytes(canonical(record))
    return record


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="action", required=True)
    base = sub.add_parser("base", help="copy the passages into a schema-1 text pack")
    base.add_argument("--version", required=True)
    native = sub.add_parser("native", help="add the Leviathan index and package as schema 2")
    native.add_argument("--vector-fapack", type=Path, required=True)
    native.add_argument("--version", required=True)
    native.add_argument("--leviathan", type=Path, default=Path.home() / ".local/bin/leviathan")
    for command in (base, native):
        command.add_argument("--pack", choices=sorted(PACKS), required=True)
        command.add_argument("--output-root", type=Path, help="default: build/<pack-id>")
    args = parser.parse_args()
    spec = PACKS[args.pack]
    out_root = args.output_root or ROOT / "build" / spec.pack_id
    if args.action == "base":
        print(json.dumps(build_base(spec, out_root, args.version), indent=2))
    else:
        print(json.dumps(build_native(spec, args.vector_fapack, out_root, args.version, args.leviathan), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
