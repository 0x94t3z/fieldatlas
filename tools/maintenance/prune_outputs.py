#!/usr/bin/env python3
"""List, and on request delete, older versions of local visual outputs: mockups, previews, renders and demo videos.

Only the folders where these outputs are made are read (OUTPUT_DIRS), one level deep. Entries whose names
differ only by a version marker form one family: "answer-compact", "answer-compact-v2" ... "-v4"; "history-v1"
... "-v6"; "field-atlas-1.2.0-film" and "field-atlas-1.3.0-film". The newest of each family is kept: the
highest release number, then the highest -vN (a name without one counts as v1, "-prev"/"-old" as v0), then the
newest modification time. Files tracked by git are never touched (git keeps their history), and nothing outside
OUTPUT_DIRS is read, so releases, packs, APK backups and test evidence are out of reach.

    python3 tools/maintenance/prune_outputs.py            # list what would be deleted
    python3 tools/maintenance/prune_outputs.py --delete   # delete it after typing "yes"; writes a receipt
"""
import argparse, json, os, pathlib, re, shutil, subprocess, time
from collections import defaultdict

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUTPUT_DIRS = ["build/demo", "build/charts", "build/charts-animated", "build/x-article", "build/mockups", "build/design",
               "build/screenshots", "tools/demo-film"]
RECEIPTS = pathlib.Path.home() / ".fieldatlas/archive/prune-receipts"

RELEASE = re.compile(r"(\d+)\.(\d+)\.(\d+)")
VERSION = re.compile(r"[-_]v(\d+)(?=$|[-_.])")
OLD = re.compile(r"[-_](prev|old)(?=$|[-_.])")


def family(name):
    """The name with its version markers taken out, and how new this one is."""
    rel = RELEASE.search(name)
    ver = VERSION.search(name)
    old = OLD.search(name)
    key = OLD.sub("", VERSION.sub("", RELEASE.sub("#", name)))
    rank = (tuple(int(x) for x in rel.groups()) if rel else (0, 0, 0), int(ver.group(1)) if ver else (0 if old else 1))
    return key, rank, bool(rel or ver or old)


def size(path):
    if path.is_file() or path.is_symlink():
        return path.lstat().st_size
    return sum(f.lstat().st_size for f in path.rglob("*") if f.is_file() and not f.is_symlink())


def tracked(path):
    out = subprocess.run(["git", "-C", str(ROOT), "ls-files", "--", str(path.relative_to(ROOT))], capture_output=True, text=True).stdout
    return bool(out.strip())


def candidates():
    found = []
    for d in OUTPUT_DIRS:
        base = ROOT / d
        if not base.is_dir():
            continue
        groups = defaultdict(list)
        for entry in base.iterdir():
            key, rank, versioned = family(entry.name)
            groups[key].append((rank, entry.lstat().st_mtime, entry, versioned))
        for members in groups.values():
            if len(members) < 2 or not any(m[3] for m in members):
                continue
            members.sort(key=lambda m: (m[0], m[1]), reverse=True)
            keep = members[0][2]
            for _, _, entry, _ in members[1:]:
                if not tracked(entry):
                    found.append((entry, keep))
    return found


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--delete", action="store_true", help="delete the listed entries after confirmation")
    args = ap.parse_args()
    found = candidates()
    total = 0
    for entry, keep in found:
        s = size(entry); total += s
        print(f"{s / 1e6:9.1f} MB  {entry.relative_to(ROOT)}   (newest kept: {keep.name})")
    print(f"{len(found)} older versions, {total / 1e9:.2f} GB")
    if not args.delete or not found:
        return
    if input('Type "yes" to delete them: ').strip() != "yes":
        print("Nothing deleted.")
        return
    receipt = []
    for entry, keep in found:
        s = size(entry)
        shutil.rmtree(entry) if entry.is_dir() and not entry.is_symlink() else entry.unlink()
        receipt.append({"path": str(entry), "bytes": s, "kept": str(keep), "deleted": not entry.exists()})
    RECEIPTS.mkdir(parents=True, exist_ok=True)
    out = RECEIPTS / time.strftime("prune-%Y%m%d-%H%M%S.json")
    out.write_text(json.dumps(receipt, indent=1))
    print(f"Deleted {sum(r['deleted'] for r in receipt)} entries ({sum(r['bytes'] for r in receipt) / 1e9:.2f} GB); receipt {out}")


if __name__ == "__main__":
    main()
