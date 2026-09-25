#!/usr/bin/env python3
"""build_vector_pack — add int8 embedding vectors (+ a query encoder) to a KNOWLEDGE fapack.

Takes an existing knowledge pack (content.sqlite with chunks_fts) and a vectors file —
TSV lines "chunk_id<TAB>v1,v2,...,v384" produced by vecbench with BAAI/bge-small-en-v1.5
— and emits a new pack version whose content.sqlite carries an extra table:

    CREATE TABLE chunk_vectors(rowid INTEGER PRIMARY KEY, quant BLOB)

rowid is the rowid of the same chunk in chunks_fts; quant is 4-byte little-endian float32
scale followed by 384 int8 values: value[j] ≈ scale * quant_int8[j] after L2-normalizing
the original vector. Cosine similarity is therefore dot(q, v) * scale_q * scale_v divided
by the (unit) norms — pure integer dot products in the app, ~388 bytes per chunk.

The pack manifest gains an optional "embedding" block (model, dim, quant, threshold,
queryPrefix, count, table, encoder artifact info). The pinned bge-small GGUF is embedded
in the pack as encoder.gguf so the phone can encode queries with the same model that made
the corpus vectors — keyword search stays the fallback when the encoder cannot run.

The tool verifies before shipping: every vector key must join against chunks_fts; the
int8-quantized vectors must still rank a vector as its own best match (quality guard);
the finished zip must pass the phone's acceptance checks (hashes + quick_check + FTS).

Run with a numpy-capable python for speed (pure-python fallback included):
    /tmp/ggufenv/bin/python tools/build_vector_pack.py \
        --base-fapack fapacks/world-knowledge-biology/world-knowledge-biology-1.1.0.fapack \
        --vectors /tmp/bio_bge_full.tsv \
        --encoder /tmp/bge-small-q8.gguf \
        --encoder-url https://huggingface.co/QuantFactory/bge-small-en-v1.5-GGUF \
        --version 1.2.0
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sqlite3
import struct
import sys
import time
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent                 # fieldatlas repo (packtool/)
sys.path.insert(0, str(REPO))
sys.path.insert(0, str(REPO / "tools"))

try:
    import numpy as _np
except ImportError:      # pragma: no cover — pure-python fallback
    _np = None

from packtool.build_pack import _canonical_json, _digest, _zip_info   # noqa: E402

MODEL_NAME = "BAAI/bge-small-en-v1.5"
QUERY_PREFIX = "Represent this sentence for searching relevant passages: "
REJECT_BELOW = 0.68     # from the quality gate: true in-pack hits >= 0.78, foreign queries <= 0.67
DIM = 384


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_vector(line: str) -> list[float]:
    values = [float(x) for x in line.split(",")]
    if len(values) != DIM:
        raise ValueError(f"expected {DIM} components, got {len(values)}")
    return values


def quantize(vec: list[float] | "_np.ndarray") -> bytes:
    """L2-normalize, then symmetric int8: blob = float32 scale + 384 signed bytes."""
    if _np is not None:
        v = np.asarray(vec, dtype=_np.float64)
        norm = float(_np.linalg.norm(v))
        if norm > 0:
            v = v / norm
        peak = max(1e-9, float(_np.max(_np.abs(v))))
        scale = peak / 127.0
        q = _np.round(v / scale).astype(_np.int8)
        return struct.pack("<f", scale) + q.tobytes()
    norm = sum(x * x for x in vec) ** 0.5
    scaled = [x / norm for x in vec] if norm > 0 else list(vec)
    peak = max(1e-9, max(abs(x) for x in scaled))
    scale = peak / 127.0
    return struct.pack("<f", scale) + bytes(int(round(x / scale)) & 0xFF for x in scaled)


def dequantize(blob: bytes) -> list[float]:
    scale = struct.unpack("<f", blob[:4])[0]
    return [scale * (b - 256 if b > 127 else b) for b in blob[4:]]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base-fapack", type=Path, required=True)
    parser.add_argument("--vectors", type=Path, required=True)
    parser.add_argument("--encoder", type=Path, required=True, help="query encoder GGUF to embed")
    parser.add_argument("--encoder-url", required=True, help="provenance URL for the encoder GGUF")
    parser.add_argument("--version", required=True, help="new pack version to stamp")
    parser.add_argument("--work-dir", type=Path, default=Path("/tmp/vector-pack"))
    args = parser.parse_args(argv)

    started = time.time()
    pack_dir = args.base_fapack.parent
    pack_id = pack_dir.name
    out_pack = pack_dir / f"{pack_id}-{args.version}.fapack"
    if out_pack.exists():
        sys.exit(f"refusing to overwrite {out_pack}")

    with zipfile.ZipFile(args.base_fapack) as archive:
        base_manifest = json.loads(archive.read("manifest.json"))
        assert base_manifest["type"] == "KNOWLEDGE", "vector packs are KNOWLEDGE packs"
        assert "embedding" not in base_manifest, "base pack already has embeddings"
        work = args.work_dir
        if work.exists():
            shutil.rmtree(work)
        work.mkdir(parents=True)
        db_path = work / "content.sqlite"
        with archive.open("content.sqlite") as src, db_path.open("wb") as dst:
            shutil.copyfileobj(src, dst, 1 << 20)

    db = sqlite3.connect(db_path)
    db.execute("PRAGMA page_size=4096")
    total_chunks = db.execute("SELECT count(*) FROM chunks_fts").fetchone()[0]
    rowid_of = {row[0]: row[1] for row in db.execute("SELECT chunk_id, rowid FROM chunks_fts")}

    print(f"[1/5] streaming {args.vectors} against {total_chunks} chunks...", flush=True)
    rows: list[tuple[int, bytes]] = []
    seen: set[str] = set()
    unmatched = 0
    with args.vectors.open() as handle:
        for number, line in enumerate(handle, 1):
            line = line.rstrip("\n")
            if not line:
                continue
            key, _, vector = line.partition("\t")
            if key in seen:
                sys.exit(f"line {number}: duplicate chunk_id {key}")
            seen.add(key)
            rowid = rowid_of.get(key)
            if rowid is None:
                unmatched += 1
                continue
            try:
                rows.append((rowid, quantize(parse_vector(vector))))
            except ValueError as error:
                sys.exit(f"line {number}: {error}")
    if unmatched:
        sys.exit(f"{unmatched} vector keys do not join against chunks_fts — mismatched inputs")
    if len(rows) != total_chunks:
        sys.exit(f"vectors cover {len(rows)} of {total_chunks} chunks — refusing partial coverage")
    print(f"      all {len(rows)} chunks covered", flush=True)

    print("[2/5] writing chunk_vectors table...", flush=True)
    db.execute("DROP TABLE IF EXISTS chunk_vectors")
    db.execute("CREATE TABLE chunk_vectors(rowid INTEGER PRIMARY KEY, quant BLOB)")
    with db:
        for start in range(0, len(rows), 50_000):
            db.executemany("INSERT INTO chunk_vectors(rowid, quant) VALUES (?, ?)",
                           rows[start:start + 50_000])
    db.execute("VACUUM")
    if db.execute("PRAGMA quick_check").fetchone()[0] != "ok":
        sys.exit("quick_check failed after adding vectors")

    # quality guard: with quantized vectors each vector's own row must still be its best match
    print("[3/5] quantization guard (self-rank preservation)...", flush=True)
    rng_sample = rows[:: max(1, len(rows) // 500)][:500]
    blobs = {rid: blob for rid, blob in rng_sample}
    import random
    random.seed(17)
    failures = 0
    for rid, blob in random.sample(rng_sample, 20):
        q = dequantize(blob)
        best_rid, best = None, -2.0
        for other_rid, other_blob in blobs.items():
            o = dequantize(other_blob)
            dot = sum(a * b for a, b in zip(q, o))
            if dot > best:
                best, best_rid = dot, other_rid
        if best_rid != rid:
            failures += 1
    if failures:
        sys.exit(f"quantization guard: {failures}/20 self-matches lost")

    print("[4/5] manifest + pack zip...", flush=True)
    encoder_bytes = args.encoder.read_bytes()
    encoder_sha = hashlib.sha256(encoder_bytes).hexdigest()
    db_bytes = db_path.read_bytes()
    db.close()
    db_sha = hashlib.sha256(db_bytes).hexdigest()

    manifest = {
        "artifacts": [
            {"bytes": len(db_bytes), "path": "content.sqlite", "sha256": db_sha},
            {"bytes": len(encoder_bytes), "path": "encoder.gguf", "sha256": encoder_sha},
        ],
        "embedding": {
            "model": MODEL_NAME,
            "dim": DIM,
            "quant": "int8-symmetric-per-vector",
            "normalized": True,
            "table": "chunk_vectors",
            "count": len(rows),
            "rejectBelow": REJECT_BELOW,
            "queryPrefix": QUERY_PREFIX,
            "encoderPath": "encoder.gguf",
            "encoderSha256": encoder_sha,
            "encoderSourceUrl": args.encoder_url,
        },
        "id": base_manifest["id"],
        "license": base_manifest["license"],
        "schemaVersion": base_manifest["schemaVersion"],
        "sourceUrls": base_manifest["sourceUrls"],
        "title": base_manifest["title"],
        "type": "KNOWLEDGE",
        "version": args.version,
    }
    if "discovery" in base_manifest:
        manifest["discovery"] = base_manifest["discovery"]
    manifest_bytes = _canonical_json(manifest)

    with zipfile.ZipFile(out_pack, "w", allowZip64=True) as archive:
        archive.writestr(_zip_info("manifest.json", len(manifest_bytes)), manifest_bytes)
        archive.writestr(_zip_info("content.sqlite", len(db_bytes)), db_bytes)
        archive.writestr(_zip_info("encoder.gguf", len(encoder_bytes)), encoder_bytes)
    pack_sha = sha256_file(out_pack)

    print("[5/5] verifying pack...", flush=True)
    with zipfile.ZipFile(out_pack) as archive:
        check = json.loads(archive.read("manifest.json"))
        for artifact in check["artifacts"]:
            blob = archive.read(artifact["path"])
            assert len(blob) == artifact["bytes"] and hashlib.sha256(blob).hexdigest() == artifact["sha256"], artifact["path"]
        reopened = sqlite3.connect(":memory:")
        reopened.execute("ATTACH DATABASE ? AS pack", (str(work / "verify.sqlite"),))
        (work / "verify.sqlite").write_bytes(archive.read("content.sqlite"))
        count = reopened.execute("SELECT count(*) FROM pack.chunk_vectors").fetchone()[0]
        assert count == len(rows), f"vector table has {count} rows, expected {len(rows)}"
        reopened.close()

    print(f"built {out_pack} ({out_pack.stat().st_size / 1e6:.0f} MB) sha256 {pack_sha[:16]}… "
          f"in {time.time() - started:.0f}s")
    return 0


if __name__ == "__main__":
    if _np is not None:
        np = _np
    sys.exit(main())
