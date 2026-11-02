#!/usr/bin/env python3
"""Build a meaning pack: clustered, compressed vectors for the passages of an installed knowledge pack.

A meaning pack carries no text. Each vector points at a passage of its passage pack (a `records` rowid of
that exact pack id and version), so the full Encyclopedia gains meaning search without a second copy of
its 24 GB of text. The phone compares a question with the cluster centres, reads only the closest
clusters, and scores their compressed vectors from one lookup table per question, so the cost barely
grows with the number of passages.

Layout of content.sqlite (manifest embedding quant "ivf-pq", table "ivf_lists"):

  meta(key, value)                      format, dim, clusters, subvectors, passage pack id and version
  ivf_centroids(cluster, vector)        float32 little-endian, L2-normalised cluster centres
  pq_codebooks(sub, centroids)          float32, 256 codewords of dim/subvectors values per subvector
  ivf_lists(cluster, refs, codes)       refs: uint32 records rowids; codes: one byte per subvector per row
  chunks_fts                            empty, so the pack opens as an ordinary knowledge database

Codes compress the residual (vector minus its cluster centre). For a unit query q the score of a row is
q . centre + sum over subvectors of q_sub . codeword, which is the cosine of q with the reconstructed vector.

    python3 tools/packs/build_meaning_pack.py --passages build/phase0/packs/encyclopedia \\
        --vectors build/encyclopedia-meaning/vectors --encoder build/phase0/packs/crypto-ethereum/encoder.gguf \\
        --id encyclopedia-meaning --version 2026.11.03 --title "Encyclopedia: search by meaning" --out build/encyclopedia-meaning

--vectors takes the --binary output of embed_pack.py (PATH.ids + PATH.f16) or its text output (PATH ending .tsv).
--check runs only the accuracy check against exact search and writes nothing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import struct
import sys
import time
import zipfile
from contextlib import closing
from pathlib import Path

import numpy as np

DIM = 384
CODEWORDS = 256
QUERY_PREFIX = "Represent this sentence for searching relevant passages: "
ENCODER_URL = "https://huggingface.co/QuantFactory/bge-small-en-v1.5-GGUF"
FORMAT = "ivf-pq-1"
RNG = np.random.default_rng(20261103)


def load_vectors(path: Path) -> tuple[list[str], np.ndarray]:
    if path.suffix == ".tsv":
        ids, rows = [], []
        with open(path) as f:
            for line in f:
                chunk_id, values = line.rstrip("\n").split("\t")
                ids.append(chunk_id)
                rows.append(np.array(values.split(","), dtype=np.float32))
        return ids, np.vstack(rows)
    ids = open(path.with_suffix(".ids")).read().split("\n")[:-1]
    vectors = np.fromfile(path.with_suffix(".f16"), dtype=np.float16).reshape(-1, DIM)
    if len(ids) != len(vectors):
        raise SystemExit(f"{len(ids)} ids but {len(vectors)} vectors")
    return ids, vectors


def normalise(x: np.ndarray) -> np.ndarray:
    return x / np.maximum(np.linalg.norm(x, axis=1, keepdims=True), 1e-12)


def chunks(n: int, size: int):
    for start in range(0, n, size):
        yield start, min(n, start + size)


def assign(vectors: np.ndarray, centres: np.ndarray, block: int = 65536) -> np.ndarray:
    """Nearest centre by inner product (all unit length), in blocks so millions of rows fit in memory."""
    out = np.empty(len(vectors), dtype=np.int32)
    for a, b in chunks(len(vectors), block):
        out[a:b] = np.argmax(np.asarray(vectors[a:b], dtype=np.float32) @ centres.T, axis=1)
    return out


def spherical_kmeans(sample: np.ndarray, k: int, rounds: int) -> np.ndarray:
    centres = sample[RNG.choice(len(sample), k, replace=False)].copy()
    for _ in range(rounds):
        labels = assign(sample, centres)
        sums = np.zeros_like(centres)
        np.add.at(sums, labels, sample)
        empty = np.linalg.norm(sums, axis=1) == 0
        # A centre that lost all its rows restarts on a random row, so no cluster stays empty.
        sums[empty] = sample[RNG.choice(len(sample), int(empty.sum()), replace=False)]
        centres = normalise(sums)
    return centres


def kmeans(sample: np.ndarray, k: int, rounds: int) -> np.ndarray:
    """Plain (Euclidean) k-means for one subvector's codebook."""
    centres = sample[RNG.choice(len(sample), k, replace=False)].copy()
    for _ in range(rounds):
        d = (sample ** 2).sum(1)[:, None] - 2 * sample @ centres.T + (centres ** 2).sum(1)[None, :]
        labels = d.argmin(1)
        for j in range(k):
            members = sample[labels == j]
            centres[j] = members.mean(0) if len(members) else sample[RNG.integers(len(sample))]
    return centres


def encode(residuals: np.ndarray, books: np.ndarray) -> np.ndarray:
    m, _, dsub = books.shape
    codes = np.empty((len(residuals), m), dtype=np.uint8)
    for s in range(m):
        part = residuals[:, s * dsub:(s + 1) * dsub]
        d = (part ** 2).sum(1)[:, None] - 2 * part @ books[s].T + (books[s] ** 2).sum(1)[None, :]
        codes[:, s] = d.argmin(1)
    return codes


def train(vectors: np.ndarray, clusters: int, subvectors: int, sample_size: int):
    started = time.time()
    sample = normalise(np.asarray(vectors[np.sort(RNG.choice(len(vectors), min(sample_size, len(vectors)), replace=False))], dtype=np.float32))
    centres = spherical_kmeans(sample, clusters, rounds=12)
    print(f"  {clusters} centres from {len(sample)} rows in {time.time() - started:.0f} s", flush=True)
    residual = sample - centres[assign(sample, centres)]
    dsub = DIM // subvectors
    books = np.stack([kmeans(residual[:100_000, s * dsub:(s + 1) * dsub], CODEWORDS, rounds=15) for s in range(subvectors)])
    print(f"  {subvectors} codebooks in {time.time() - started:.0f} s", flush=True)
    return centres.astype(np.float32), books.astype(np.float32)


def search(q: np.ndarray, centres, books, lists, probe: int, limit: int):
    """The phone's search, in numpy: closest clusters, then one lookup table per question."""
    m, _, dsub = books.shape
    table = np.einsum("skd,sd->sk", books, q.reshape(m, dsub))
    near = np.argsort(-(centres @ q))[:probe]
    rows, scores = [], []
    for c in near:
        refs, codes = lists[c]
        if len(refs) == 0: continue
        rows.append(refs)
        scores.append(float(centres[c] @ q) + table[np.arange(m), codes].sum(1))
    if not rows: return np.array([], dtype=np.int64)
    rows, scores = np.concatenate(rows), np.concatenate(scores)
    best = np.argsort(-scores)[:limit]
    return rows[best], scores[best]


def check(vectors, centres, books, labels, codes, probes, queries):
    """Of the exact top 10 for each query, how many the clustered search also returns."""
    lists = {c: (np.flatnonzero(labels == c), codes[labels == c]) for c in range(len(centres))}
    full = np.asarray(vectors, dtype=np.float32)
    for probe in probes:
        found, best, started = [], [], time.time()
        for q in queries:
            order = np.argsort(-(full @ q))
            exact = set(order[:10].tolist())
            got = set(search(q, centres, books, lists, probe, 10)[0].tolist())
            found.append(len(exact & got) / 10); best.append(order[0] in got)
        print(f"  probe {probe}: {np.mean(found):.0%} of the exact top 10, best match kept {np.mean(best):.0%} "
              f"({(time.time() - started) / len(queries) * 1000:.0f} ms a query in numpy)", flush=True)


def record_rowids(passages: Path, ids: list[str]) -> np.ndarray:
    with closing(sqlite3.connect(passages.joinpath("content.sqlite").resolve().as_uri() + "?mode=ro", uri=True)) as db:
        rowid = {chunk_id: r for r, chunk_id in db.execute("SELECT rowid, id FROM records")}
    missing = [c for c in ids[:1000] if c not in rowid] + [c for c in ids if c not in rowid][:5]
    if missing:
        raise SystemExit(f"chunk ids missing from the passage pack, e.g. {missing[:5]}")
    out = np.fromiter((rowid[c] for c in ids), dtype=np.int64, count=len(ids))
    if out.max() >= 2 ** 32:
        raise SystemExit("records rowid does not fit uint32")
    return out


def write_pack(args, ids, vectors, centres, books, passage_manifest):
    out = args.out; out.mkdir(parents=True, exist_ok=True)
    refs = record_rowids(args.passages, ids)
    db_path = out / "content.sqlite"
    db_path.unlink(missing_ok=True)
    labels = np.empty(len(vectors), dtype=np.int32)
    codes = np.empty((len(vectors), books.shape[0]), dtype=np.uint8)
    for a, b in chunks(len(vectors), 262_144):
        block = normalise(np.asarray(vectors[a:b], dtype=np.float32))
        labels[a:b] = assign(block, centres)
        codes[a:b] = encode(block - centres[labels[a:b]], books)
        print(f"  encoded {b}/{len(vectors)}", flush=True)
    with closing(sqlite3.connect(db_path)) as db:
        db.executescript("""
            PRAGMA page_size = 4096;
            CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE ivf_centroids(cluster INTEGER PRIMARY KEY, vector BLOB NOT NULL);
            CREATE TABLE pq_codebooks(sub INTEGER PRIMARY KEY, centroids BLOB NOT NULL);
            CREATE TABLE ivf_lists(cluster INTEGER PRIMARY KEY, refs BLOB NOT NULL, codes BLOB NOT NULL);
            CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED, document_id UNINDEXED, title, source UNINDEXED, text);
            PRAGMA user_version = 1;
        """)
        meta = {"format": FORMAT, "dim": DIM, "clusters": len(centres), "subvectors": books.shape[0],
                "passagesId": passage_manifest["id"], "passagesVersion": passage_manifest["version"], "count": len(vectors)}
        db.executemany("INSERT INTO meta VALUES (?, ?)", [(k, str(v)) for k, v in meta.items()])
        db.executemany("INSERT INTO ivf_centroids VALUES (?, ?)", [(c, centres[c].astype("<f4").tobytes()) for c in range(len(centres))])
        db.executemany("INSERT INTO pq_codebooks VALUES (?, ?)", [(s, books[s].astype("<f4").tobytes()) for s in range(books.shape[0])])
        order = np.argsort(labels, kind="stable")
        bounds = np.searchsorted(labels[order], np.arange(len(centres) + 1))
        for c in range(len(centres)):
            rows = order[bounds[c]:bounds[c + 1]]
            rows = rows[np.argsort(refs[rows])]
            db.execute("INSERT INTO ivf_lists VALUES (?, ?, ?)",
                       (c, refs[rows].astype("<u4").tobytes(), np.ascontiguousarray(codes[rows]).tobytes()))
        db.commit()
        db.execute("VACUUM")
    encoder_sha = hashlib.sha256(args.encoder.read_bytes()).hexdigest()
    db_sha = hashlib.sha256(db_path.read_bytes()).hexdigest()
    manifest = {
        "schemaVersion": 1, "id": args.id, "version": args.version, "type": "KNOWLEDGE", "title": args.title,
        "license": args.license or (passage_manifest.get("license", "") + " AND MIT"),
        "sourceUrls": passage_manifest.get("sourceUrls", []) + [ENCODER_URL],
        "artifacts": [{"path": "content.sqlite", "bytes": db_path.stat().st_size, "sha256": db_sha},
                      {"path": "encoder.gguf", "bytes": args.encoder.stat().st_size, "sha256": encoder_sha}],
        "embedding": {"model": "BAAI/bge-small-en-v1.5", "dim": DIM, "quant": "ivf-pq", "normalized": True,
                      "table": "ivf_lists", "count": len(vectors), "rejectBelow": args.reject_below,
                      "queryPrefix": QUERY_PREFIX, "encoderPath": "encoder.gguf", "encoderSha256": encoder_sha,
                      "encoderSourceUrl": ENCODER_URL, "clusters": len(centres), "subvectors": books.shape[0],
                      "probe": args.probe, "passagesFrom": {"id": passage_manifest["id"], "version": passage_manifest["version"]}},
    }
    pack = out / f"{args.id}-{args.version}.fapack"
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_STORED, allowZip64=True) as archive:
        archive.writestr(zipfile.ZipInfo("manifest.json", (1980, 1, 1, 0, 0, 0)), json.dumps(manifest, sort_keys=True, separators=(",", ":")))
        for name, src in (("content.sqlite", db_path), ("encoder.gguf", args.encoder)):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.file_size = src.stat().st_size
            with archive.open(info, "w", force_zip64=True) as dst, open(src, "rb") as f:
                while block := f.read(1 << 20): dst.write(block)
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1, sort_keys=True))
    sha = hashlib.sha256(pack.read_bytes()).hexdigest()
    print(f"{pack} {pack.stat().st_size} bytes sha256 {sha}; installed {db_path.stat().st_size + args.encoder.stat().st_size} bytes")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--passages", type=Path, required=True, help="installed passage pack folder (content.sqlite + manifest.json)")
    ap.add_argument("--vectors", type=Path, required=True)
    ap.add_argument("--encoder", type=Path, required=True)
    ap.add_argument("--id"); ap.add_argument("--version"); ap.add_argument("--title"); ap.add_argument("--license")
    ap.add_argument("--out", type=Path)
    ap.add_argument("--clusters", type=int, help="default: about 2 x sqrt(rows), a power of two")
    ap.add_argument("--subvectors", type=int, default=96)
    ap.add_argument("--probe", type=int, default=32)
    ap.add_argument("--reject-below", type=float, default=0.68)
    ap.add_argument("--sample", type=int, default=300_000)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if DIM % args.subvectors:
        raise SystemExit("--subvectors must divide 384")
    passage_manifest = json.loads(args.passages.joinpath("manifest.json").read_text())
    ids, vectors = load_vectors(args.vectors)
    clusters = args.clusters or int(2 ** round(np.log2(2 * np.sqrt(len(vectors)))))
    print(f"{len(vectors)} vectors for {passage_manifest['id']} {passage_manifest['version']}; {clusters} clusters, {args.subvectors} subvectors", flush=True)
    centres, books = train(vectors, clusters, args.subvectors, args.sample)
    if args.check:
        rows = np.sort(RNG.choice(len(vectors), min(len(vectors), 400_000), replace=False))
        subset = normalise(np.asarray(vectors[rows], dtype=np.float32))
        labels = assign(subset, centres)
        codes = encode(subset - centres[labels], books)
        # Queries: other passages' vectors, nudged so a passage is not trivially its own best match.
        queries = normalise(subset[RNG.choice(len(subset), 100, replace=False)] + RNG.normal(0, 0.03, (100, DIM)).astype(np.float32))
        check(subset, centres, books, labels, codes, [16, 32], queries)
        return 0
    if not (args.id and args.version and args.title and args.out):
        raise SystemExit("--id, --version, --title and --out are needed to write a pack")
    write_pack(args, ids, vectors, centres, books, passage_manifest)
    return 0


if __name__ == "__main__":
    sys.exit(main())
