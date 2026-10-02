#!/usr/bin/env python3
"""Build an offline pack of OpenStreetMap places tagged as serving vegan food.

Two steps keep the network and the build separate:

  fetch  Query Overpass for current OpenStreetMap data, kind by kind. Every raw
         response is cached with its SHA-256 and Overpass's data timestamp
         (timestamp_osm_base) in lock.json. Those archived responses are the
         reproducible input: publishing them with the pack lets anyone rebuild it
         byte for byte without the ~95 GB planet file. --date requests a
         date-pinned ("attic") query instead; it is far slower on the public server
         (a worldwide attic query timed out after 900 s where current data took 81 s).
  build  Offline and deterministic: turns the cached responses into a
         keyword-searchable .fapack in the same listing format as the
         Wikivoyage places pack.

Each place is assigned a destination from OpenStreetMap city/town nodes fetched at
the same timestamp: the most populous large city nearby, else the nearest city or
town, else the address city. Tags describe what mappers recorded; they cannot
establish current opening, menus, quality, or a "best" ranking.
"""

from __future__ import annotations

import argparse
import hashlib
import http.client
import json
import math
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
# stream_pack imports packtool from the repository root.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from stream_pack import build_pack_streaming  # noqa: E402

DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"
USER_AGENT = "FieldAtlas-pack-builder/0.1 (+https://github.com/0x94t3z/fieldatlas)"
LICENSE = "ODbL-1.0"
COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
ATTRIBUTION = "© OpenStreetMap contributors"

VEGAN_VALUES = {"only": "fully vegan", "yes": "vegan options", "limited": "limited vegan options"}
EAT = {"restaurant", "fast_food", "cafe", "ice_cream", "food_court"}
DRINK = {"bar", "pub", "biergarten"}
AMENITY_LABELS = {"fast_food": "fast food", "ice_cream": "ice cream", "food_court": "food court"}

# A large city absorbs nearby places ("Kreuzberg" questions are asked as "in Berlin").
METRO_RADIUS_KM = 30.0
METRO_MIN_POPULATION = 100_000
LOCAL_RADIUS_KM = 20.0
# Tag filters use Overpass's tag index, so one worldwide query usually succeeds. A tile the
# server cannot answer is split into quadrants rather than guessed at in advance.
MAX_SPLIT_DEPTH = 6


def tag_query(selector: str, elements: str = "nwr", output: str = "out center tags;"):
    """Exact tag values use Overpass's tag index; value regexes scan the key across the
    planet and made a date-pinned worldwide query time out after 900 s."""
    def query(date: str | None, bbox: tuple[float, float, float, float], timeout: int) -> str:
        south, west, north, east = bbox
        pinned = f'[date:"{date}"]' if date else ""
        return (f'[out:json][timeout:{timeout}]{pinned};'
                f'{elements}{selector}({south},{west},{north},{east});{output}')
    return query


# Each kind is fetched and cached separately. cuisine lists such as "vegan;thai" are not
# matched exactly; such places are normally also tagged diet:vegan.
QUERIES = {
    "vegan-only": tag_query('["diet:vegan"="only"]'),
    "vegan-yes": tag_query('["diet:vegan"="yes"]'),
    "vegan-limited": tag_query('["diet:vegan"="limited"]'),
    "cuisine-vegan": tag_query('["cuisine"="vegan"]'),
    # "out;" keeps each node's coordinates; "out tags;" drops them and leaves nothing to
    # measure distances to, so every place would fall back to its address tag.
    "place-city": tag_query('["place"="city"]["name"]', elements="node", output="out;"),
    "place-town": tag_query('["place"="town"]["name"]', elements="node", output="out;"),
}
VEGAN_KINDS = ("vegan-only", "vegan-yes", "vegan-limited", "cuisine-vegan")
PLACE_KINDS = ("place-city", "place-town")


def tile_key(kind: str, bbox: tuple[float, float, float, float]) -> str:
    return f"{kind}/" + "_".join(f"{value:g}" for value in bbox)


WORLD = (-90.0, -180.0, 90.0, 180.0)


def inside(inner, outer) -> bool:
    """Whether bbox inner (south, west, north, east) lies within outer."""
    return inner[0] >= outer[0] and inner[1] >= outer[1] and inner[2] <= outer[2] and inner[3] <= outer[3]


def split(bbox: tuple[float, float, float, float]):
    south, west, north, east = bbox
    mid_lat, mid_lon = (south + north) / 2, (west + east) / 2
    yield (south, west, mid_lat, mid_lon)
    yield (south, mid_lon, mid_lat, east)
    yield (mid_lat, west, north, mid_lon)
    yield (mid_lat, mid_lon, north, east)


class TileTooLarge(Exception):
    """The server could not answer this tile within its limits; split it."""


def overpass(endpoint: str, query: str, attempts: int = 6) -> bytes:
    body = urllib.parse.urlencode({"data": query}).encode()
    delay = 30.0
    for attempt in range(attempts):
        request = urllib.request.Request(endpoint, data=body, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=1200) as response:
                payload = response.read()
        except urllib.error.HTTPError as error:
            # 429/5xx are load signals from a shared public service. A gateway timeout that
            # persists through every retry is treated as a tile that is too large.
            if error.code == 504 and attempt + 1 == attempts:
                raise TileTooLarge("gateway timeout on every attempt") from error
            if error.code in (429, 502, 503, 504) and attempt + 1 < attempts:
                time.sleep(delay)
                delay = min(delay * 2, 600)
                continue
            raise
        except (urllib.error.URLError, TimeoutError, ConnectionError, http.client.IncompleteRead):
            # A dropped connection mid-stream leaves a truncated body; it is never cached.
            if attempt + 1 < attempts:
                time.sleep(delay)
                delay = min(delay * 2, 600)
                continue
            raise
        try:
            document = json.loads(payload)
        except json.JSONDecodeError:
            if attempt + 1 < attempts:
                time.sleep(delay)
                delay = min(delay * 2, 600)
                continue
            raise
        remark = document.get("remark", "")
        if "runtime error" in remark or "Query timed out" in remark or "out of memory" in remark:
            raise TileTooLarge(remark)
        return payload
    raise RuntimeError("unreachable")


def fetch(args: argparse.Namespace, queries: dict | None = None) -> int:
    queries = QUERIES if queries is None else queries
    cache = args.cache_dir
    cache.mkdir(parents=True, exist_ok=True)
    lock_path = cache / "lock.json"
    lock = json.loads(lock_path.read_text()) if lock_path.exists() else {
        "date": args.date, "endpoint": args.endpoint, "tiles": {},
    }
    if lock["date"] != args.date:
        raise SystemExit(f"cache was fetched for {lock['date']}, not {args.date}; use a new cache directory")

    def save_lock():
        temporary = lock_path.with_suffix(".tmp")
        temporary.write_text(json.dumps(lock, indent=2, sort_keys=True) + "\n")
        temporary.replace(lock_path)

    for kind in args.kinds:
        pending = [(WORLD, 0)]
        while pending:
            bbox, depth = pending.pop(0)
            key = tile_key(kind, bbox)
            entry = lock["tiles"].get(key)
            path = cache / (key + ".json")
            query = queries[kind](args.date, bbox, args.timeout)
            # A response cached for a different query (e.g. an older output mode) is refetched.
            if entry and entry["query"] == query and path.exists() and sha256_file(path) == entry["sha256"]:
                continue
            # An earlier run split this tile; resume inside its parts instead of asking for the
            # whole tile again, which duplicated work after an interrupted fetch.
            if entry is None and any(other["kind"] == kind and other["bbox"] != list(bbox) and inside(other["bbox"], bbox)
                                     for other in lock["tiles"].values()):
                pending[:0] = [(child, depth + 1) for child in split(bbox)]
                continue
            try:
                payload = overpass(args.endpoint, query)
            except TileTooLarge as error:
                if depth >= MAX_SPLIT_DEPTH:
                    raise SystemExit(f"{key} still too large at depth {depth}: {error}")
                print(f"split {key}: {error}", flush=True)
                pending[:0] = [(child, depth + 1) for child in split(bbox)]
                continue
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(payload)
            document = json.loads(payload)
            elements = len(document["elements"])
            lock["tiles"][key] = {
                # Per response, because a run may finish on a mirror when the main server refuses.
                "kind": kind, "bbox": list(bbox), "query": query, "endpoint": args.endpoint,
                "osm_base": document.get("osm3s", {}).get("timestamp_osm_base"),
                "sha256": hashlib.sha256(payload).hexdigest(), "bytes": len(payload), "elements": elements,
            }
            save_lock()
            print(f"{key}: {elements} elements, {len(payload)} bytes", flush=True)
            time.sleep(args.pause)
    return 0


def sha256_file(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


@dataclass(frozen=True)
class Settlement:
    lat: float
    lon: float
    name: str
    place: str
    population: int


def parse_population(value: str | None) -> int:
    if not value:
        return 0
    digits = "".join(character for character in value.split(";")[0] if character.isdigit())
    return int(digits) if digits else 0


def display_name(tags: dict) -> str | None:
    return (tags.get("name:en") or tags.get("name") or "").strip() or None


def distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    a = (math.sin((phi2 - phi1) / 2) ** 2 +
         math.cos(phi1) * math.cos(phi2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2)
    return 6371.0 * 2 * math.asin(min(1.0, math.sqrt(a)))


class Gazetteer:
    """1-degree grid of settlements; a 30 km radius never spans more than the 3x3 cells
    around a point except near the poles, where the extra longitude cells are scanned."""

    def __init__(self, settlements: list[Settlement]):
        self.cells: dict[tuple[int, int], list[Settlement]] = {}
        for settlement in settlements:
            self.cells.setdefault((math.floor(settlement.lat), math.floor(settlement.lon)), []).append(settlement)

    def near(self, lat: float, lon: float, radius_km: float):
        lat_cells = math.ceil(radius_km / 111.0)
        lon_span = radius_km / max(1.0, 111.0 * math.cos(math.radians(min(abs(lat), 89.0))))
        lon_cells = math.ceil(lon_span)
        for dlat in range(-lat_cells, lat_cells + 1):
            for dlon in range(-lon_cells, lon_cells + 1):
                cell_lon = (math.floor(lon) + dlon + 180) % 360 - 180
                for settlement in self.cells.get((math.floor(lat) + dlat, cell_lon), ()):
                    distance = distance_km(lat, lon, settlement.lat, settlement.lon)
                    if distance <= radius_km:
                        yield distance, settlement

    def destination(self, lat: float, lon: float) -> str | None:
        nearby = list(self.near(lat, lon, METRO_RADIUS_KM))
        metros = [(distance, s) for distance, s in nearby
                  if s.place == "city" and s.population >= METRO_MIN_POPULATION]
        if metros:
            # Largest first; distance and name make ties deterministic.
            return min(metros, key=lambda item: (-item[1].population, item[0], item[1].name))[1].name
        local = [(distance, s) for distance, s in nearby if distance <= LOCAL_RADIUS_KM]
        if local:
            return min(local, key=lambda item: (item[0], item[1].name))[1].name
        return None


def load_elements(cache: Path, kinds: tuple[str, ...]) -> list[dict]:
    lock = json.loads((cache / "lock.json").read_text())
    missing = [kind for kind in kinds if not any(entry["kind"] == kind for entry in lock["tiles"].values())]
    if missing:
        raise SystemExit(f"cache has no responses for: {', '.join(missing)}; run fetch first")
    seen: dict[tuple[str, int], dict] = {}
    for key, entry in sorted(lock["tiles"].items()):
        if entry["kind"] not in kinds:
            continue
        path = cache / (key + ".json")
        if sha256_file(path) != entry["sha256"]:
            raise SystemExit(f"cached response changed since fetch: {path}")
        for element in json.loads(path.read_bytes())["elements"]:
            # Tiles share edges, so the same element can arrive twice.
            seen.setdefault((element["type"], element["id"]), element)
    return [seen[key] for key in sorted(seen)]


def coordinates(element: dict) -> tuple[float, float] | None:
    if "lat" in element and "lon" in element:
        return element["lat"], element["lon"]
    center = element.get("center")
    if center:
        return center["lat"], center["lon"]
    return None


def category(tags: dict) -> tuple[str, str] | None:
    amenity = tags.get("amenity")
    if amenity in EAT:
        return "Eat", AMENITY_LABELS.get(amenity, amenity)
    if amenity in DRINK:
        return "Drink", amenity
    shop = tags.get("shop")
    if shop:
        return "Buy", f"{shop.replace('_', ' ')} shop"
    return None


def address(tags: dict) -> str | None:
    street = " ".join(part for part in (tags.get("addr:street"), tags.get("addr:housenumber")) if part)
    locality = " ".join(part for part in (tags.get("addr:postcode"), tags.get("addr:city")) if part)
    joined = ", ".join(part for part in (street, locality) if part)
    return joined or tags.get("addr:full")


def place_document(element: dict, gazetteer: Gazetteer, snapshot_date: str) -> dict | None:
    tags = element.get("tags", {})
    name = display_name(tags)
    position = coordinates(element)
    kind = category(tags)
    if name is None or position is None or kind is None:
        return None
    vegan = tags.get("diet:vegan")
    if vegan not in VEGAN_VALUES:
        # cuisine=vegan without a diet tag describes a vegan kitchen.
        vegan = "only"
    lat, lon = position
    destination = gazetteer.destination(lat, lon) or (tags.get("addr:city") or "").strip() or None
    group, label = kind
    lines = []
    if destination:
        lines.append(f"Destination: {destination}")
    lines += [f"Category: {group}", f"Place: {name}", f"Type: {label}",
              f"Vegan: {VEGAN_VALUES[vegan]}"]
    if tags.get("name") and tags.get("name") != name:
        lines.append(f"Local name: {tags['name']}")
    if tags.get("cuisine"):
        lines.append("Cuisine: " + tags["cuisine"].replace("_", " ").replace(";", ", "))
    if tags.get("description"):
        lines.append(f"Description: {tags['description']}")
    location = address(tags)
    if location:
        lines.append(f"Address: {location}")
    if tags.get("opening_hours"):
        lines.append(f"Hours in source: {tags['opening_hours']}")
    lines += [f"Latitude: {lat:.6f}", f"Longitude: {lon:.6f}"]
    checked = tags.get("check_date") or tags.get("survey:date")
    if checked:
        lines.append(f"Listing last checked: {checked}")
    lines.append(f"Map data snapshot: {snapshot_date} ({ATTRIBUTION})")
    element_type = element["type"]
    return {
        "document_id": f"osm-place-{element_type[0]}{element['id']}",
        "title": f"{destination} — {name}" if destination else name,
        "source": f"https://www.openstreetmap.org/{element_type}/{element['id']}",
        "license": LICENSE,
        "text": "\n".join(lines),
    }


def snapshot_day(lock: dict) -> str:
    """The pinned date, else the newest data timestamp among the cached responses."""
    if lock.get("date"):
        return lock["date"][:10]
    stamps = [entry["osm_base"] for entry in lock["tiles"].values() if entry.get("osm_base")]
    if not stamps:
        raise SystemExit("cached responses carry no Overpass data timestamp")
    return max(stamps)[:10]


def gazetteer(cache: Path) -> Gazetteer:
    """City and town lookup from the place-city/place-town responses in a cache."""
    settlements = []
    for element in load_elements(cache, PLACE_KINDS):
        tags = element.get("tags", {})
        name = display_name(tags)
        if name and "lat" in element:
            settlements.append(Settlement(element["lat"], element["lon"], name, tags.get("place", ""),
                                          parse_population(tags.get("population"))))
    if not settlements:
        raise SystemExit("settlement responses carry no coordinates; re-run fetch for place-city and place-town")
    return Gazetteer(settlements)


def documents(cache: Path) -> list[dict]:
    lock = json.loads((cache / "lock.json").read_text())
    snapshot_date = snapshot_day(lock)
    places = gazetteer(cache)
    return [document for element in load_elements(cache, VEGAN_KINDS)
            if (document := place_document(element, places, snapshot_date)) is not None]


def add_place_points(database) -> None:
    """Coordinates per search row, so the app can find places near the phone's location
    without parsing every listing. Rows are aligned with chunks_fts by rowid."""
    database.execute("CREATE TABLE place_points (rowid INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL)")
    rows = []
    for rowid, text in database.execute("SELECT rowid, text FROM chunks_fts ORDER BY rowid"):
        fields = dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)
        try:
            rows.append((rowid, float(fields["Latitude"]), float(fields["Longitude"])))
        except (KeyError, ValueError):
            continue
    database.executemany("INSERT INTO place_points(rowid, lat, lon) VALUES (?, ?, ?)", rows)
    database.execute("CREATE INDEX place_points_lat ON place_points(lat)")


def build(args: argparse.Namespace) -> int:
    lock = json.loads((args.cache_dir / "lock.json").read_text())
    docs = documents(args.cache_dir)
    if not docs:
        raise SystemExit("No vegan places found; fetch the cache first")
    args.out.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".jsonl",
                                     prefix="osm-vegan-", dir=args.out, delete=False) as spool:
        spool_path = Path(spool.name)
        for document in docs:
            spool.write(json.dumps(document, ensure_ascii=False, separators=(",", ":")) + "\n")
    try:
        artifacts = build_pack_streaming(
            input_path=spool_path,
            output_dir=args.out / "osm-vegan-places",
            pack_id="osm-vegan-places",
            version=args.version,
            title="Vegan places (OpenStreetMap)",
            license_id=LICENSE,
            source_urls=[lock["endpoint"], COPYRIGHT_URL],
            coverage_summary="Restaurants, cafes, and shops that OpenStreetMap tags as fully vegan or with vegan options, worldwide; details may be stale.",
            example_questions=[
                "Tell me the best vegan restaurants in Berlin",
                "Which vegan cafes are listed in Chiang Mai?",
                "Where can I find vegan food in Tokyo?",
            ],
            coverage_level="focused",
            fts_tokenizer="porter unicode61",
            extend_database=add_place_points,
        )
    finally:
        spool_path.unlink(missing_ok=True)
    # The lock records every query, tile and response hash behind this pack.
    (artifacts.pack.parent / "OVERPASS-LOCK.json").write_text(
        json.dumps(lock, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{len(docs)} places -> {artifacts.pack}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    fetch_parser = commands.add_parser("fetch", help="query Overpass and archive the responses (network)")
    fetch_parser.add_argument("--cache-dir", required=True, type=Path)
    fetch_parser.add_argument("--date", help="optional date-pinned (attic) query, e.g. 2026-09-28T00:00:00Z")
    fetch_parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    fetch_parser.add_argument("--kinds", nargs="+", default=list(QUERIES), choices=list(QUERIES))
    fetch_parser.add_argument("--timeout", type=int, default=900, help="Overpass server-side timeout (s)")
    fetch_parser.add_argument("--pause", type=float, default=5.0, help="seconds between queries")
    build_parser = commands.add_parser("build", help="build the pack from a fetched cache (offline)")
    build_parser.add_argument("--cache-dir", required=True, type=Path)
    build_parser.add_argument("--out", required=True, type=Path)
    build_parser.add_argument("--version", required=True, help="pack version, e.g. 2026.09.28")
    args = parser.parse_args()
    return fetch(args) if args.command == "fetch" else build(args)


if __name__ == "__main__":
    raise SystemExit(main())
