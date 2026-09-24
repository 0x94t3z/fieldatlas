#!/usr/bin/env python3
"""Build Field Atlas MODEL and AUDIO fapacks from the pinned model registry.

Every pack is reproducible from its specification:

* MODEL packs are generated from fieldatlas/models/compact-*.example.json — those files are
  the registry (id, title, license, pinned Hugging Face URLs, expected upstream sha256). The
  GGUF is downloaded from the pinned commit URL (blob URL rewritten to resolve/), verified
  against expectedSha256, and stored UNCOMPRESSED next to a manifest.json and provenance.json.
* AUDIO packs use the AUDIO_REGISTRY below (official Vosk model archives): the pinned .zip is
  downloaded, hash-verified, unpacked, and its tree placed under audio-model/ (top-level
  folder stripped).

Zip layout matches what the app installs: STORED entries, fixed 1980 timestamps, sorted-key
JSON, so rebuilds of the same inputs are byte-identical. After building, every artifact is
re-hashed from the finished zip and compared against the manifest (the same check the phone
performs before accepting a pack).

Examples:
  tools/build_model_packs.py --pack all                       # build all, download as needed
  tools/build_model_packs.py --pack qwen3.5-2b-q4-k-m         # one pack
  # Offline test: fake the downloads with local files and a scratch cache:
  tools/build_model_packs.py --pack all --cache-dir /tmp/fapack-cache \
      --url qwen3-1.7b-q4-k-m=file:///home/v/fieldatlas/model-cache/Qwen3-1.7B-Q4_K_M.gguf ...
"""
import argparse
import hashlib
import io
import json
import re
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent                # fieldatlas repo (models/, packtool/)
WORK = REPO.parent                                             # working dir: fapacks/, model-cache/
MODELS_DIR = REPO / "models"
DEFAULT_OUT = WORK / "fapacks"
DEFAULT_CACHE = WORK / "model-cache"
SCHEMA_VERSION = 1
JSON_KW = dict(sort_keys=True, separators=(", ", ": "))

# Audio packs, pinned the same way as models: exact URL + exact sha256 of the official archive.
AUDIO_REGISTRY = [
    {
        "converter": "Official Vosk large-graph English acoustic model archive (lgraph variant, tuned for CPU inference)",
        "expectedSha256": "d9838b4aaa82a75c4a17f5aca300eaca129aaab2a7cbf951bafbb500eb9c4334",
        "id": "vosk-en-us-022-lgraph",
        "license": "Apache-2.0",
        "profile": "On-device dictation model; ~130 MB, runs in-process alongside the answer model; replaces the APK-bundled small model when installed",
        "sourceUrls": ["https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip"],
        "title": "Vosk English dictation — large graph",
        "version": "1.0.0",
        # internals
        "url": "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip",
        "archive_topdir": "vosk-model-en-us-0.22-lgraph",
        "dest_prefix": "audio-model",
    },
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(url: str, target: Path, expected_sha: str) -> Path:
    """Download url -> target (unless already present and verified), enforce the pinned sha256."""
    if target.is_file() and sha256_file(target) == expected_sha:
        print(f"    cached: {target.name}")
        return target
    if url.startswith("file://"):
        source = Path(url.removeprefix("file://"))
        if source.is_file() and sha256_file(source) == expected_sha:
            print(f"    copying pre-downloaded {source} (download cut short)")
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target.with_suffix(target.suffix + ".part"))
            target.with_suffix(target.suffix + ".part").replace(target)
            return target
    print(f"    downloading {url}")
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(target.suffix + ".part")
    import sys
    with urllib.request.urlopen(url) as response, partial.open("wb") as out:
        total = response.headers.get("Content-Length")
        done = 0
        while True:
            chunk = response.read(1 << 20)
            if not chunk:
                break
            out.write(chunk)
            done += len(chunk)
            if total and sys.stdout.isatty():
                print(f"\r      {done / (1 << 20):7.1f} / {int(total) / (1 << 20):.1f} MB", end="", flush=True)
    if total and sys.stdout.isatty():
        print()
    actual = sha256_file(partial)
    if actual != expected_sha:
        partial.unlink()
        raise SystemExit(
            f"SHA-256 mismatch for {url}\n  expected {expected_sha}\n  got      {actual}"
            + ("\n  note: this pack's GGUF is produced locally (chat-template patch) — provide it via "
               "--url ID=file:///path or place it in the cache dir" if expected_sha.startswith("9241af62") else "")
        )
    partial.replace(target)
    return target


def write_zip(entries: list[tuple[str, Path | bytes]], out_path: Path) -> None:
    """Deterministic zip: STORED entries, 1980 timestamps, caller-provided order."""
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out_path, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, source in entries:
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = 0o644 << 16
            if isinstance(source, bytes):
                archive.writestr(info, source)
            else:
                with source.open("rb") as handle, archive.open(info, "w") as dest:
                    shutil.copyfileobj(handle, dest, 1 << 20)


def artifact_entry(name: str, source: Path | bytes) -> dict:
    if isinstance(source, bytes):
        size, digest = len(source), hashlib.sha256(source).hexdigest()
    else:
        size, digest = source.stat().st_size, sha256_file(source)
    return {"path": name, "bytes": size, "sha256": digest}


def build_model_pack(spec: dict, out_dir: Path, cache_dir: Path, url_override: str | None) -> Path:
    blob_url = next(u for u in spec["sourceUrls"] if "/blob/" in u and u.endswith(".gguf"))
    download_url = (url_override or blob_url.replace("/blob/", "/resolve/"))
    filename = blob_url.rsplit("/", 1)[-1]
    print(f"[model] {spec['id']}: obtaining {filename}")
    gguf = fetch(download_url, cache_dir / spec["id"] / filename, spec["expectedSha256"])

    model_art = artifact_entry("model.gguf", gguf)
    if model_art["sha256"] != spec["expectedSha256"]:
        raise SystemExit(f"cached GGUF hash {model_art['sha256']} != pinned {spec['expectedSha256']}")
    provenance_bytes = json.dumps({**spec, "sha256": model_art["sha256"]}, **JSON_KW).encode()
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "id": spec["id"],
        "version": spec["version"],
        "type": "MODEL",
        "title": spec["title"],
        "license": spec["license"],
        "sourceUrls": spec["sourceUrls"],
        "artifacts": [model_art, None],
    }
    prov_art = artifact_entry("provenance.json", provenance_bytes)
    manifest["artifacts"][1] = prov_art
    manifest_bytes = json.dumps(manifest, **JSON_KW).encode()
    out_path = out_dir / f"{spec['id']}-{spec['version']}.fapack"
    write_zip(
        [("manifest.json", manifest_bytes), ("model.gguf", gguf), ("provenance.json", provenance_bytes)],
        out_path,
    )
    verify_pack(out_path)
    return out_path


def build_audio_pack(spec: dict, out_dir: Path, cache_dir: Path, url_override: str | None) -> Path:
    url = url_override or spec["url"]
    filename = url.rsplit("/", 1)[-1].split("?")[0] or Path(spec["url"]).name
    print(f"[audio] {spec['id']}: obtaining {filename}")
    archive = fetch(url, cache_dir / spec["id"] / filename, spec["expectedSha256"])

    entries: list[tuple[str, Path | bytes]] = []          # (zip name, content)
    artifacts: list[dict] = []
    with zipfile.ZipFile(archive) as source:
        members = [m for m in source.namelist() if not m.endswith("/") and "__pycache__" not in m]

        def rel(member: str) -> str | None:
            top = member.split("/", 1)
            if len(top) == 2 and top[0] == spec["archive_topdir"]:
                return top[1]
            if len(top) == 1:
                return top[0]
            return None

        # Shipping-pack layout: root files first, then directories in archive order with
        # files sorted inside each directory (that is how the pack was first assembled).
        ordered: list[str] = sorted(m for m in members if (rel(m) or "").count("/") == 0)
        seen_dirs: list[str] = []
        for member in members:
            r = rel(member)
            if r is None or r.count("/") == 0:
                continue
            directory = r.split("/")[0]
            if directory not in seen_dirs:
                seen_dirs.append(directory)
        for directory in seen_dirs:
            def member_key(m: str) -> tuple:
                r = rel(m) or ""
                tail = r.split("/", 1)[1] if "/" in r else ""   # strip the top directory segment
                return (1 if "/" in tail else 0, r)              # plain files first, subdirs after
            ordered.extend(
                sorted((m for m in members if (r := rel(m)) and r.split("/")[0] == directory), key=member_key)
            )

    with zipfile.ZipFile(archive) as source:
        for member in ordered:
            if member.endswith("/") or "__pycache__" in member:
                continue
            top = member.split("/", 1)
            if len(top) == 2 and top[0] == spec["archive_topdir"]:
                relative = top[1]
            elif len(top) == 1:
                relative = top[0]
            else:
                continue  # stray file outside the pinned top folder
            if not relative:
                continue
            content = source.read(member)
            name = f"{spec['dest_prefix']}/{relative}"
            art = artifact_entry(name, content)
            artifacts.append(art)
            entries.append((name, content))
    if not artifacts:
        raise SystemExit(f"archive {archive} contained no files under {spec['archive_topdir']}/")

    provenance = {k: v for k, v in spec.items() if not k.startswith(("url", "archive_topdir", "dest_prefix"))}
    provenance_bytes = json.dumps(provenance, **JSON_KW).encode()
    prov_art = artifact_entry("provenance.json", provenance_bytes)
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "id": spec["id"],
        "version": spec["version"],
        "type": "AUDIO",
        "title": spec["title"],
        "license": spec["license"],
        "sourceUrls": spec["sourceUrls"],
        "artifacts": artifacts + [prov_art],
    }
    manifest_bytes = json.dumps(manifest, **JSON_KW).encode()
    out_path = out_dir / f"{spec['id']}-{spec['version']}.fapack"
    write_zip(
        [("manifest.json", manifest_bytes)] + entries + [("provenance.json", provenance_bytes)],
        out_path,
    )
    verify_pack(out_path)
    return out_path


def verify_pack(path: Path) -> None:
    """Re-hash every artifact out of the finished zip — the phone's acceptance check."""
    with zipfile.ZipFile(path) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        assert manifest["schemaVersion"] == SCHEMA_VERSION, path
        assert manifest["type"] in ("MODEL", "AUDIO"), path
        assert "discovery" not in manifest, f"{path}: AUDIO/MODEL packs must not carry discovery"
        assert manifest["artifacts"], path
        for art in manifest["artifacts"]:
            content = archive.read(art["path"])
            assert len(content) == art["bytes"], f"{path}:{art['path']} size mismatch"
            assert hashlib.sha256(content).hexdigest() == art["sha256"], f"{path}:{art['path']} sha mismatch"
        provenance = json.loads(archive.read("provenance.json"))
        assert provenance["id"] == manifest["id"] and provenance["version"] == manifest["version"], path
    print(f"    verified {path.name} ({len(manifest['artifacts'])} artifacts)")


def load_model_registry() -> list[dict]:
    specs = []
    for path in sorted(MODELS_DIR.glob("compact-*.example.json")):
        spec = json.loads(path.read_text())
        assert "expectedSha256" in spec and any("/blob/" in u for u in spec["sourceUrls"]), path
        specs.append(spec)
    return specs


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--pack", default="all", help="pack id, or 'all'")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE)
    parser.add_argument(
        "--url", action="append", default=[], metavar="ID=URL",
        help="override a pack's download URL (file:// works) — used to test without the internet",
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="resolve specs and show download sources, then stop where a download would begin",
    )
    args = parser.parse_args(argv)
    overrides = dict(kv.split("=", 1) for kv in args.url)

    model_specs, audio_specs = load_model_registry(), AUDIO_REGISTRY
    chosen = [
        ("model", spec) for spec in model_specs
        if args.pack in ("all", spec["id"])
    ] + [
        ("audio", spec) for spec in audio_specs
        if args.pack in ("all", spec["id"])
    ]
    if not chosen:
        known = ", ".join(s["id"] for _, s in model_specs + audio_specs)
        parser.error(f"unknown pack '{args.pack}' (known: {known})")

    for kind, spec in chosen:
        if args.dry_run:
            url = (next(u for u in spec["sourceUrls"] if "/blob/" in u).replace("/blob/", "/resolve/")
                   if kind == "model" else spec.get("url"))
            print(f"[{kind}] {spec['id']}: would download {overrides.get(spec['id'], url)}")
            continue
        out_path = (
            build_model_pack(spec, args.out_dir, args.cache_dir, overrides.get(spec["id"]))
            if kind == "model"
            else build_audio_pack(spec, args.out_dir, args.cache_dir, overrides.get(spec["id"]))
        )
        print(f"    -> {out_path} ({out_path.stat().st_size / 1e6:.1f} MB)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
