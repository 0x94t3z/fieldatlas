#!/usr/bin/env python3
"""Cryptography & Ethereum knowledge pack from primary, openly licensed sources.

Sources, each pinned in a lock file at fetch time:
- Ethereum Improvement Proposals (ethereum/EIPs) and ERCs (ethereum/ERCs), CC0-1.0;
- the Ethereum consensus specifications (ethereum/consensus-specs), CC0-1.0;
- NIST FIPS 203, 204 and 205 (ML-KEM, ML-DSA, SLH-DSA), SP 800-208 (stateful hash-based
  signatures) and IR 8547 (transition to post-quantum cryptography), works of the US federal
  government and so in the public domain in the United States;
- selected English Wikipedia articles (tools/data/crypto_reference_topics.json) at pinned
  revisions, CC-BY-SA-4.0, through the general reference builder's snapshot code.

Usage:
  python3 tools/packs/build_crypto_pack.py fetch --cache build/crypto-pack/cache
  python3 tools/packs/build_crypto_pack.py build --cache build/crypto-pack/cache --output build/crypto-pack/out --version 2026.10.05

fetch needs the network; build never does and checks every cached file against the lock.
No other submission's corpus, code or answers are used.
"""
from __future__ import annotations

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))


import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
from tools.packs import build_general_reference as wiki  # noqa: E402
from tools.packs.stream_pack import build_pack_streaming  # noqa: E402
from tools.packs.fapack_convert import verify_pack  # noqa: E402

USER_AGENT = wiki.USER_AGENT
CC0 = "CC0-1.0"
PUBLIC_DOMAIN = "Public domain (US federal government work)"
REPOS = {
    "eips": "https://github.com/ethereum/EIPs.git",
    "ercs": "https://github.com/ethereum/ERCs.git",
    "consensus-specs": "https://github.com/ethereum/consensus-specs.git",
}
NIST = {
    "fips-203": ("https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.203.pdf",
                 "FIPS 203: Module-Lattice-Based Key-Encapsulation Mechanism Standard (ML-KEM, formerly CRYSTALS-Kyber)"),
    "fips-204": ("https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.204.pdf",
                 "FIPS 204: Module-Lattice-Based Digital Signature Standard (ML-DSA, formerly CRYSTALS-Dilithium)"),
    "fips-205": ("https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.205.pdf",
                 "FIPS 205: Stateless Hash-Based Digital Signature Standard (SLH-DSA, formerly SPHINCS+)"),
    "sp-800-208": ("https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-208.pdf",
                   "NIST SP 800-208: Recommendation for Stateful Hash-Based Signature Schemes (LMS, HSS, XMSS, XMSS^MT)"),
    "ir-8547": ("https://nvlpubs.nist.gov/nistpubs/ir/2024/NIST.IR.8547.ipd.pdf",
                "NIST IR 8547 (initial public draft): Transition to Post-Quantum Cryptography Standards"),
}
MAX_PDF = 40 * 1024 * 1024


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


# ---------- fetch ----------
def fetch(cache: Path, topics: Path) -> None:
    cache.mkdir(parents=True, exist_ok=True)
    wiki.fetch(topics, cache / "wikipedia")
    lock_path = cache / "sources.lock.json"
    lock = json.loads(lock_path.read_bytes()) if lock_path.exists() else {"schema": 1, "repos": {}, "nist": {}}
    for name, url in REPOS.items():
        target = cache / "git" / name
        if name not in lock["repos"]:
            target.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(["git", "clone", "--depth", "1", "--quiet", url, str(target)], check=True)
            commit = subprocess.run(["git", "-C", str(target), "rev-parse", "HEAD"], check=True,
                                    capture_output=True, text=True).stdout.strip()
            lock["repos"][name] = {"url": url, "commit": commit}
            lock_path.write_bytes(canonical(lock))
            print(f"Cloned {name} at {commit}", flush=True)
    for key, (url, _) in NIST.items():
        if key in lock["nist"]:
            continue
        time.sleep(0.5)
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read(MAX_PDF + 1)
        if len(data) > MAX_PDF or not data.startswith(b"%PDF"):
            raise ValueError(f"Unexpected download for {key}")
        (cache / "nist").mkdir(parents=True, exist_ok=True)
        (cache / "nist" / f"{key}.pdf").write_bytes(data)
        lock["nist"][key] = {"url": url, "sha256": sha(data), "bytes": len(data)}
        lock_path.write_bytes(canonical(lock))
        print(f"Downloaded {key} ({len(data):,} bytes)", flush=True)


# ---------- markdown sources ----------
FRONT = re.compile(r"\A---\n(.*?)\n---\n", re.S)
# Sections that carry no knowledge a question could need (licence waivers, test vectors, link lists).
SKIP_HEADINGS = re.compile(r"(?i)^(copyright|test cases?|test vectors?|references)\b")


def front_matter(text: str) -> tuple[dict, str]:
    match = FRONT.match(text)
    if not match:
        return {}, text
    fields = {}
    for line in match.group(1).splitlines():
        if ":" in line and not line.startswith(" "):
            key, value = line.split(":", 1)
            fields[key.strip().lower()] = value.strip().strip("'\"")
    return fields, text[match.end():]


def clean_markdown(text: str) -> str:
    """Prose a keyword search can use: code blocks, HTML, images and link targets removed."""
    text = re.sub(r"<!--.*?-->", " ", text, flags=re.S)
    text = re.sub(r"```.*?```", " ", text, flags=re.S)
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", " ", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def markdown_sections(body: str) -> list[tuple[str, str]]:
    """Split at level-2/3 headings; each section keeps its heading path."""
    sections, heading, lines, top = [], "Overview", [], ""
    for line in body.splitlines():
        match = re.match(r"^(#{1,4})\s+(.*)$", line)
        if match:
            text = clean_markdown("\n".join(lines))
            if len(text) >= 40:
                sections.append((heading, text))
            level, title = len(match.group(1)), match.group(2).strip().strip("#").strip()
            if level <= 2:
                top = title
                heading = title
            else:
                heading = f"{top} > {title}" if top else title
            lines = []
        else:
            lines.append(line)
    text = clean_markdown("\n".join(lines))
    if len(text) >= 40:
        sections.append((heading, text))
    return sections


COPYRIGHT = re.compile(r"^##\s+Copyright\s*$(.*?)(?=^##\s|\Z)", re.M | re.S | re.I)
MARKDOWN_LINK = re.compile(r"\[([^\]]+)\]\([^)]*\)")


def proposal_licence(body: str) -> str | None:
    """CC0 when the proposal waives its rights or says nothing (the repository's LICENSE.md is CC0);
    None for anything else: EIP-1355 and EIP-1485, for example, are CC BY-NC-SA."""
    section = COPYRIGHT.search(body)
    if section is None or "CC0" in section.group(1):
        return CC0
    return None


def proposal_documents(repo: Path, folder: str, prefix: str, commit: str, skipped: list[str]) -> list[dict]:
    documents = []
    github = {"eip": "ethereum/EIPs", "erc": "ethereum/ERCs"}[prefix]
    for path in sorted((repo / folder).glob(f"{prefix}-*.md"), key=lambda p: int(re.sub(r"\D", "", p.stem) or 0)):
        number = re.sub(r"\D", "", path.stem)
        fields, body = front_matter(path.read_text(encoding="utf-8"))
        if fields.get("status", "").lower() == "moved":
            continue  # a pointer to the ERCs repository, whose own copy is read instead
        label = f"{prefix.upper()}-{number}"
        if proposal_licence(body) is None:
            skipped.append(label)
            continue
        name = f"{label}: {fields.get('title', '').strip()}".rstrip(": ")
        status = "; ".join(f"{k.title()}: {fields[k]}" for k in ("status", "type", "category", "created", "requires") if fields.get(k))
        header = f"{name}\n{status}" + (f"\n{fields['description']}" if fields.get("description") else "")
        kept = [(h, t) for h, t in markdown_sections(body) if not SKIP_HEADINGS.match(h.split(" > ")[0])]
        for index, (heading, text) in enumerate(kept):
            heading = MARKDOWN_LINK.sub(r"\1", heading)
            documents.append({"document_id": f"{prefix}-{int(number):05d}-{index:03d}", "title": f"{name} — {heading}",
                              # The exact text that was indexed, at the locked commit.
                              "source": f"https://github.com/{github}/blob/{commit}/{folder}/{path.name}",
                              "license": CC0, "text": f"{header}\n\n{text}" if index == 0 else f"{name} ({heading})\n\n{text}"})
    return documents


def spec_documents(repo: Path, commit: str) -> list[dict]:
    documents = []
    for path in sorted((repo / "specs").rglob("*.md")):
        relative = path.relative_to(repo / "specs")
        fork = relative.parts[0] if len(relative.parts) > 1 else "general"
        stem = re.sub(r"[^a-z0-9]+", "-", "-".join(relative.with_suffix("").parts).lower()).strip("-")
        name = f"Consensus specs ({fork}): {relative.with_suffix('').as_posix()}"
        for index, (heading, text) in enumerate(markdown_sections(path.read_text(encoding="utf-8"))):
            heading = MARKDOWN_LINK.sub(r"\1", heading)
            documents.append({"document_id": f"spec-{stem[:100]}-{index:03d}", "title": f"{name} — {heading}",
                              "source": f"https://github.com/ethereum/consensus-specs/blob/{commit}/specs/{relative.as_posix()}",
                              "license": CC0, "text": text})
    return documents


# ---------- NIST publications ----------
def nist_documents(cache: Path, lock: dict) -> list[dict]:
    from pypdf import PdfReader
    documents = []
    for key, (url, title) in NIST.items():
        path = cache / "nist" / f"{key}.pdf"
        data = path.read_bytes()
        if sha(data) != lock["nist"][key]["sha256"]:
            raise ValueError(f"Cached {key} changed since fetch")
        reader = PdfReader(str(path))
        pages = [re.sub(r"[ \t]+", " ", (page.extract_text() or "")).strip() for page in reader.pages]
        # Three pages per document keeps a definition with its explanation without huge records.
        for start in range(0, len(pages), 3):
            text = "\n\n".join(p for p in pages[start:start + 3] if p)
            if len(text) < 200:
                continue
            documents.append({"document_id": f"nist-{key}-p{start + 1:04d}", "title": f"{title} — pages {start + 1}-{min(start + 3, len(pages))}",
                              "source": url, "license": PUBLIC_DOMAIN, "text": text})
    return documents


def wikipedia_documents(cache: Path, topics: Path) -> tuple[list[dict], list[dict]]:
    lock = json.loads((cache / "wikipedia" / "sources.lock.json").read_bytes())
    if lock["topics_sha256"] != wiki.digest(topics.read_bytes()):
        raise ValueError("Wikipedia snapshot does not match the topic selection")
    documents = []
    for row in sorted(lock["sources"], key=lambda r: r["pageid"]):
        article = wiki.read_snapshot(cache / "wikipedia", row)
        extracted, _ = wiki.sections(article["text"])
        for index, (heading, text) in enumerate(extracted):
            documents.append({"document_id": f"wp-{row['pageid']}-{index:04d}", "title": f"{row['title']} — {heading}",
                              "source": row["url"], "license": wiki.LICENSE, "text": text})
    return documents, lock["sources"]


# ---------- build ----------
def build(cache: Path, topics: Path, output: Path, version: str) -> None:
    if output.exists():
        raise ValueError("Output already exists; choose a new build directory")
    lock = json.loads((cache / "sources.lock.json").read_bytes())
    for name, entry in lock["repos"].items():
        head = subprocess.run(["git", "-C", str(cache / "git" / name), "rev-parse", "HEAD"], check=True,
                              capture_output=True, text=True).stdout.strip()
        if head != entry["commit"]:
            raise ValueError(f"{name} checkout is not the locked commit")
    documents, skipped = [], []
    documents += proposal_documents(cache / "git" / "eips", "EIPS", "eip", lock["repos"]["eips"]["commit"], skipped)
    documents += proposal_documents(cache / "git" / "ercs", "ERCS", "erc", lock["repos"]["ercs"]["commit"], skipped)
    documents += spec_documents(cache / "git" / "consensus-specs", lock["repos"]["consensus-specs"]["commit"])
    documents += nist_documents(cache, lock)
    wiki_docs, wiki_sources = wikipedia_documents(cache, topics)
    documents += wiki_docs
    ids = [d["document_id"] for d in documents]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate document ids")
    output.mkdir(parents=True)
    spool = output / "documents.jsonl"
    spool.write_text("".join(json.dumps(d, ensure_ascii=False, sort_keys=True) + "\n" for d in sorted(documents, key=lambda d: d["document_id"])), encoding="utf-8")
    sources = [e["url"] for e in lock["repos"].values()] + [e["url"] for e in lock["nist"].values()] + [r["url"] for r in wiki_sources]
    artifacts = build_pack_streaming(
        spool, output / "pack", "crypto-ethereum", version, "Cryptography & Ethereum",
        # NIST works are public domain in the United States; the concept-search encoder added by
        # build_vector_pack.py (BAAI bge-small-en-v1.5) is MIT.
        "CC0-1.0 AND CC-BY-SA-4.0 AND LicenseRef-US-Government-Work AND MIT", sources,
        coverage_summary="Ethereum EIPs/ERCs and consensus specs, NIST post-quantum standards (ML-KEM, ML-DSA, SLH-DSA, LMS/XMSS) and 123 Wikipedia articles.",
        example_questions=["What does ML-KEM protect against?", "How do blobs make rollups cheaper?",
                           "Which NIST standard defines SLH-DSA?"],
        coverage_level="focused", fts_tokenizer="porter unicode61", chunker=wiki.paragraph_chunks)
    verify_pack(artifacts.pack)
    (output / "sources.lock.json").write_bytes(canonical({**lock, "wikipedia": wiki_sources}))
    counts = {}
    for d in documents:
        counts[d["document_id"].split("-")[0]] = counts.get(d["document_id"].split("-")[0], 0) + 1
    (output / "quality-report.json").write_bytes(canonical({
        "status": "BUILT_NOT_ANSWER_VALIDATED", "documents": len(documents), "documents_by_source": counts,
        "builder_sha256": sha(Path(__file__).read_bytes()), "documents_sha256": sha(spool.read_bytes()),
        "pack_sha256": sha(artifacts.pack.read_bytes()),
        "skipped_not_cc0": skipped,
        "limitations": ["Code blocks, tables in PDFs and equations are not preserved as prose.",
                        "PDF text is machine-extracted; layout and symbols can be garbled.",
                        "A snapshot: EIP statuses and specs change after the locked commits."]}))
    (output / "ATTRIBUTION.txt").write_text(
        "Cryptography & Ethereum pack.\n"
        "Ethereum EIPs, ERCs and consensus specifications: CC0-1.0, at the commits in sources.lock.json.\n"
        "NIST FIPS 203/204/205, SP 800-208, IR 8547: US federal government works, public domain in the United States.\n"
        f"Wikipedia: adapted text by Wikipedia contributors, {wiki.LICENSE} {wiki.LICENSE_URL}; selected sections, "
        "references, navigation, media, tables and math paragraphs removed. Revision links:\n" +
        "\n".join(f"{r['title']} | {r['timestamp']} | {r['url']}" for r in wiki_sources) + "\n", encoding="utf-8")
    print(f"Built {artifacts.pack}: {len(documents)} documents {counts}; skipped as not CC0: {skipped}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["fetch", "build"])
    parser.add_argument("--cache", type=Path, default=ROOT / "build/crypto-pack/cache")
    parser.add_argument("--topics", type=Path, default=ROOT / "tools/data/crypto_reference_topics.json")
    parser.add_argument("--output", type=Path, default=ROOT / "build/crypto-pack/out")
    parser.add_argument("--version", default="2026.10.05")
    args = parser.parse_args()
    if args.action == "fetch":
        fetch(args.cache, args.topics)
    else:
        build(args.cache, args.topics, args.output, args.version)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
