#!/usr/bin/env python3
"""Build an offline pack of everyday essentials from OpenStreetMap.

Pharmacies, hospitals and clinics; ATMs and currency exchange; toilets and drinking
water; police and embassies; train, bus and ferry stations; supermarkets and hostels.
These are what a traveller without a connection most often needs to find nearby.

It shares the vegan-places pipeline (tools/build_osm_vegan_pack.py): the same archived
Overpass responses with SHA-256 and data timestamps in lock.json, the same tile
splitting and retries, and the same destination lookup. Settlements can be read from
another cache (--settlements-cache) so the city and town lists are not fetched twice.

  fetch  Query Overpass kind by kind, in priority order (network).
  build  Offline and deterministic. Builds from every kind present in the cache and
         records which kinds were included in the pack's coverage summary.
"""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_osm_vegan_pack as osm  # noqa: E402
from stream_pack import build_pack_streaming  # noqa: E402

PACK_ID = "osm-essentials"
TITLE = "Essentials (OpenStreetMap)"

# kind -> (Overpass selector, category, type label). Order is fetch priority.
KINDS = {
    "pharmacy": ('["amenity"="pharmacy"]', "Health", "pharmacy"),
    "hospital": ('["amenity"="hospital"]', "Health", "hospital"),
    "clinic": ('["amenity"="clinic"]', "Health", "clinic"),
    "police": ('["amenity"="police"]', "Safety", "police"),
    "embassy": ('["amenity"="embassy"]', "Safety", "embassy"),
    "diplomatic": ('["office"="diplomatic"]', "Safety", "embassy"),
    "atm": ('["amenity"="atm"]', "Money", "ATM"),
    "exchange": ('["amenity"="bureau_de_change"]', "Money", "currency exchange"),
    "railway-station": ('["railway"="station"]["name"]', "Transport", "train station"),
    "bus-station": ('["amenity"="bus_station"]', "Transport", "bus station"),
    "ferry": ('["amenity"="ferry_terminal"]', "Transport", "ferry terminal"),
    "toilets": ('["amenity"="toilets"]', "Toilets", "toilets"),
    "drinking-water": ('["amenity"="drinking_water"]', "Water", "drinking water"),
    "supermarket": ('["shop"="supermarket"]', "Buy", "supermarket"),
    "hostel": ('["tourism"="hostel"]', "Sleep", "hostel"),
}
QUERIES = {kind: osm.tag_query(selector) for kind, (selector, _, _) in KINDS.items()}
QUERIES.update({kind: osm.QUERIES[kind] for kind in osm.PLACE_KINDS})

# Many essentials carry no name; these keys describe them instead.
UNNAMED = ("brand", "operator", "network")
YES_NO = {"yes": "yes", "no": "no"}


def place_name(tags: dict, label: str) -> str:
    name = osm.display_name(tags)
    if name:
        return name
    for key in UNNAMED:
        value = (tags.get(key) or "").strip()
        if value:
            return f"{label[0].upper() + label[1:]} · {value}"
    return label[0].upper() + label[1:]


def essential_document(element: dict, kind: str, places: osm.Gazetteer, snapshot_date: str) -> dict | None:
    tags = element.get("tags", {})
    position = osm.coordinates(element)
    if position is None:
        return None
    _, group, label = KINDS[kind]
    if kind == "diplomatic" and tags.get("diplomatic") not in {"embassy", "consulate", "high_commission"}:
        return None
    if kind == "diplomatic" and tags.get("diplomatic") == "consulate":
        label = "consulate"
    lat, lon = position
    destination = places.destination(lat, lon) or (tags.get("addr:city") or "").strip() or None
    name = place_name(tags, label)
    lines = []
    if destination:
        lines.append(f"Destination: {destination}")
    lines += [f"Category: {group}", f"Place: {name}", f"Type: {label}"]
    if tags.get("name") and osm.display_name(tags) != tags["name"]:
        lines.append(f"Local name: {tags['name']}")
    for key, title in (("brand", "Brand"), ("operator", "Operator")):
        value = (tags.get(key) or "").strip()
        if value and value not in name:
            lines.append(f"{title}: {value}")
    if kind in {"embassy", "diplomatic"} and tags.get("country"):
        lines.append(f"Country represented: {tags['country']}")
    if kind == "hospital" and tags.get("emergency") in YES_NO:
        lines.append(f"Emergency department: {YES_NO[tags['emergency']]}")
    if kind in {"toilets", "drinking-water"}:
        if tags.get("fee") in YES_NO:
            lines.append(f"Fee: {YES_NO[tags['fee']]}")
        if tags.get("access"):
            lines.append(f"Access: {tags['access']}")
    if tags.get("wheelchair") in {"yes", "no", "limited"}:
        lines.append(f"Wheelchair access: {tags['wheelchair']}")
    if kind in {"hospital", "clinic", "pharmacy", "police", "embassy", "diplomatic"}:
        phone = (tags.get("phone") or tags.get("contact:phone") or "").strip()
        if phone:
            lines.append(f"Phone: {phone}")
    location = osm.address(tags)
    if location:
        lines.append(f"Address: {location}")
    if tags.get("opening_hours"):
        lines.append(f"Hours in source: {tags['opening_hours']}")
    lines += [f"Latitude: {lat:.6f}", f"Longitude: {lon:.6f}"]
    checked = tags.get("check_date") or tags.get("survey:date")
    if checked:
        lines.append(f"Listing last checked: {checked}")
    lines.append(f"Map data snapshot: {snapshot_date} ({osm.ATTRIBUTION})")
    element_type = element["type"]
    return {
        "document_id": f"osm-place-{element_type[0]}{element['id']}",
        "title": f"{destination} — {name}" if destination else name,
        "source": f"https://www.openstreetmap.org/{element_type}/{element['id']}",
        "license": osm.LICENSE,
        "text": "\n".join(lines),
    }


def cached_kinds(cache: Path) -> list[str]:
    lock = json.loads((cache / "lock.json").read_text())
    present = {entry["kind"] for entry in lock["tiles"].values()}
    return [kind for kind in KINDS if kind in present]


def documents(cache: Path, settlements_cache: Path) -> tuple[list[dict], list[str]]:
    snapshot_date = osm.snapshot_day(json.loads((cache / "lock.json").read_text()))
    places = osm.gazetteer(settlements_cache)
    kinds = cached_kinds(cache)
    if not kinds:
        raise SystemExit("cache has no essentials responses; run fetch first")
    seen, docs = set(), []
    for kind in kinds:
        for element in osm.load_elements(cache, (kind,)):
            key = (element["type"], element["id"])
            # One element can match two kinds (an embassy tagged both ways); keep the first.
            if key in seen:
                continue
            document = essential_document(element, kind, places, snapshot_date)
            if document is not None:
                seen.add(key)
                docs.append(document)
    return docs, kinds


def add_place_points(database) -> None:
    """Rowid-aligned coordinates plus the category, so the app can filter nearby places
    by kind from the index alone instead of reading every listing in a dense city."""
    database.execute("CREATE TABLE place_points (rowid INTEGER PRIMARY KEY, lat REAL NOT NULL, "
                     "lon REAL NOT NULL, category TEXT)")
    rows = []
    for rowid, text in database.execute("SELECT rowid, text FROM chunks_fts ORDER BY rowid"):
        fields = dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)
        try:
            rows.append((rowid, float(fields["Latitude"]), float(fields["Longitude"]), fields.get("Category")))
        except (KeyError, ValueError):
            continue
    database.executemany("INSERT INTO place_points(rowid, lat, lon, category) VALUES (?, ?, ?, ?)", rows)
    database.execute("CREATE INDEX place_points_lat ON place_points(lat)")


def build(args: argparse.Namespace) -> int:
    lock = json.loads((args.cache_dir / "lock.json").read_text())
    settlements = args.settlements_cache or args.cache_dir
    docs, kinds = documents(args.cache_dir, settlements)
    labels = sorted({KINDS[kind][2] for kind in kinds})
    args.out.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".jsonl",
                                     prefix="osm-essentials-", dir=args.out, delete=False) as spool:
        spool_path = Path(spool.name)
        for document in docs:
            spool.write(json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n")
    try:
        artifacts = build_pack_streaming(
            input_path=spool_path,
            output_dir=args.out / PACK_ID,
            pack_id=PACK_ID,
            version=args.version,
            title=TITLE,
            license_id=osm.LICENSE,
            source_urls=[lock["endpoint"], osm.COPYRIGHT_URL],
            coverage_summary="Places OpenStreetMap maps worldwide for everyday needs: " + ", ".join(labels) +
                             ". Hours, fees and access can be stale.",
            example_questions=[
                "Where is the nearest pharmacy?",
                "Find an ATM near me",
                "Is there a hospital with an emergency department nearby?",
            ],
            coverage_level="focused",
            fts_tokenizer="porter unicode61",
            extend_database=add_place_points,
        )
    finally:
        spool_path.unlink(missing_ok=True)
    settlement_lock = json.loads((settlements / "lock.json").read_text())
    record = dict(lock)
    record["settlements"] = {key: entry for key, entry in settlement_lock["tiles"].items()
                             if entry["kind"] in osm.PLACE_KINDS}
    (artifacts.pack.parent / "OVERPASS-LOCK.json").write_text(
        json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{len(docs)} places from {len(kinds)} kinds -> {artifacts.pack}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    fetch_parser = commands.add_parser("fetch", help="query Overpass and archive the responses (network)")
    fetch_parser.add_argument("--cache-dir", required=True, type=Path)
    fetch_parser.add_argument("--date", help="optional date-pinned (attic) query")
    fetch_parser.add_argument("--endpoint", default=osm.DEFAULT_ENDPOINT)
    fetch_parser.add_argument("--kinds", nargs="+", default=list(KINDS), choices=list(QUERIES))
    fetch_parser.add_argument("--timeout", type=int, default=900, help="Overpass server-side timeout (s)")
    fetch_parser.add_argument("--pause", type=float, default=10.0, help="seconds between queries")
    build_parser = commands.add_parser("build", help="build the pack from a fetched cache (offline)")
    build_parser.add_argument("--cache-dir", required=True, type=Path)
    build_parser.add_argument("--settlements-cache", type=Path,
                              help="cache holding place-city/place-town responses (default: --cache-dir)")
    build_parser.add_argument("--out", required=True, type=Path)
    build_parser.add_argument("--version", required=True)
    args = parser.parse_args()
    return osm.fetch(args, QUERIES) if args.command == "fetch" else build(args)


if __name__ == "__main__":
    raise SystemExit(main())
