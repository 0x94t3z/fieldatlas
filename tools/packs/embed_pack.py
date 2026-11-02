#!/usr/bin/env python3
"""Embed every chunk of a knowledge pack for build_vector_pack.py.

Writes "chunk_id<TAB>v1,...,v384" lines using the same encoder the phone uses for queries
(a BAAI/bge-small-en-v1.5 GGUF, CLS pooling, L2-normalized), served by the pinned desktop
llama.cpp build. The phone encodes questions with the query prefix; passages are encoded as
"title\\ntext" with no prefix, as bge-small expects.

Usage:
  python3 tools/packs/embed_pack.py --database build/crypto-pack/out/pack/content.sqlite \\
      --encoder build/crypto-pack/encoder/encoder.gguf --output build/crypto-pack/vectors.tsv
"""
from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))


import argparse
import json
import socket
import sqlite3
import struct
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# The phone truncates at the encoder's 512-token context minus 4; about 4 characters per token
# for English prose keeps passages inside it, and a passage that still overflows is shortened.
MAX_CHARS = 1800
BATCH = 32


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def embed(endpoint: str, texts: list[str]) -> list[list[float]]:
    body = json.dumps({"input": texts}).encode()
    request = urllib.request.Request(endpoint + "/v1/embeddings", data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=300) as response:
        data = json.load(response)["data"]
    return [row["embedding"] for row in sorted(data, key=lambda r: r["index"])]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--encoder", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--server", type=Path, default=ROOT / "build/desktop-llama/bin/llama-server")
    parser.add_argument("--binary", action="store_true",
                        help="write OUTPUT.ids (one chunk id a line) and OUTPUT.f16 (384 float16 values a row) "
                             "instead of text, and resume from them after an interruption")
    args = parser.parse_args()

    port = free_port()
    server = subprocess.Popen([str(args.server), "-m", str(args.encoder), "--embedding", "--pooling", "cls",
                               "--embd-normalize", "2", "-c", "8192", "-b", "8192", "-ub", "512", "-np", "16",
                               # The prompt cache is searched on every request; filled with one-off passages it
                               # slows a long run about tenfold.
                               "--cache-ram", "0",
                               "--host", "127.0.0.1", "--port", str(port)],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    endpoint = f"http://127.0.0.1:{port}"
    try:
        for _ in range(120):
            try:
                with urllib.request.urlopen(endpoint + "/health", timeout=2) as r:
                    if json.load(r).get("status") == "ok":
                        break
            except Exception:
                time.sleep(0.5)
        else:
            raise SystemExit("embedding server did not start")
        db = sqlite3.connect(args.database.resolve().as_uri() + "?mode=ro", uri=True)
        total = db.execute("SELECT count(*) FROM chunks_fts").fetchone()[0]
        skip = 0
        if args.binary:
            ids_path, f16_path = args.output.with_suffix(".ids"), args.output.with_suffix(".f16")
            if ids_path.exists() and f16_path.exists():
                skip = min(sum(1 for _ in open(ids_path)), f16_path.stat().st_size // (384 * 2))
                # Drop a half-written last batch so both files end on the same row.
                with open(ids_path, "r+") as f:
                    lines = f.readlines()[:skip]; f.seek(0); f.writelines(lines); f.truncate()
                with open(f16_path, "r+b") as f:
                    f.truncate(skip * 384 * 2)
            ids_out, vec_out = open(ids_path, "a"), open(f16_path, "ab")
        else:
            text_out = open(args.output, "w")
        # Streamed in rowid order: a pack of millions of passages does not fit in memory at once.
        cursor = db.execute("SELECT chunk_id, title, text FROM chunks_fts ORDER BY rowid LIMIT -1 OFFSET ?", (skip,))
        started, done = time.time(), skip
        try:
            while batch := cursor.fetchmany(BATCH):
                texts = [f"{title}\n{text}"[:MAX_CHARS] for _, title, text in batch]
                try:
                    vectors = embed(endpoint, texts)
                except Exception:
                    # A dense passage can still exceed 512 tokens: embed one by one, halving if needed.
                    vectors = []
                    for text in texts:
                        while True:
                            try:
                                vectors.append(embed(endpoint, [text])[0]); break
                            except Exception:
                                if len(text) < 200: raise
                                text = text[: len(text) // 2]
                for (chunk_id, _, _), vector in zip(batch, vectors):
                    if len(vector) != 384:
                        raise SystemExit(f"unexpected dimension {len(vector)} for {chunk_id}")
                if args.binary:
                    flat = [v for vector in vectors for v in vector]
                    vec_out.write(struct.pack(f"<{len(flat)}e", *flat)); ids_out.write("".join(c + "\n" for c, _, _ in batch))
                else:
                    for (chunk_id, _, _), vector in zip(batch, vectors):
                        text_out.write(chunk_id + "\t" + ",".join(f"{v:.6f}" for v in vector) + "\n")
                done += len(batch)
                if done % 2048 < BATCH:
                    print(f"{done}/{total} chunks, {(done - skip) / (time.time() - started):.0f}/s", flush=True)
        finally:
            for f in ((ids_out, vec_out) if args.binary else (text_out,)):
                f.close()
        print(f"wrote {done} vectors to {args.output}")
    finally:
        server.terminate()
        server.wait(timeout=30)
    return 0


if __name__ == "__main__":
    sys.exit(main())
