#!/usr/bin/env python3
"""Embed every chunk of a knowledge pack for build_vector_pack.py.

Writes "chunk_id<TAB>v1,...,v384" lines using the same encoder the phone uses for queries
(a BAAI/bge-small-en-v1.5 GGUF, CLS pooling, L2-normalized), served by the pinned desktop
llama.cpp build. The phone encodes questions with the query prefix; passages are encoded as
"title\\ntext" with no prefix, as bge-small expects.

Usage:
  python3 tools/embed_pack.py --database build/crypto-pack/out/pack/content.sqlite \\
      --encoder build/crypto-pack/encoder/encoder.gguf --output build/crypto-pack/vectors.tsv
"""
from __future__ import annotations

import argparse
import json
import socket
import sqlite3
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
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
    args = parser.parse_args()

    port = free_port()
    server = subprocess.Popen([str(args.server), "-m", str(args.encoder), "--embedding", "--pooling", "cls",
                               "--embd-normalize", "2", "-c", "8192", "-b", "8192", "-ub", "512", "-np", "16",
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
        db = sqlite3.connect(str(args.database))
        rows = db.execute("SELECT chunk_id, title, text FROM chunks_fts ORDER BY rowid").fetchall()
        started, done = time.time(), 0
        with open(args.output, "w") as out:
            for i in range(0, len(rows), BATCH):
                batch = rows[i:i + BATCH]
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
                    out.write(chunk_id + "\t" + ",".join(f"{v:.6f}" for v in vector) + "\n")
                done += len(batch)
                if done % 2048 < BATCH:
                    print(f"{done}/{len(rows)} chunks, {done / (time.time() - started):.0f}/s", flush=True)
        print(f"wrote {done} vectors to {args.output}")
    finally:
        server.terminate()
        server.wait(timeout=30)
    return 0


if __name__ == "__main__":
    sys.exit(main())
