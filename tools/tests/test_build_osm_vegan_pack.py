from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import build_osm_vegan_pack as osm  # noqa: E402

DATE = "2026-09-28T00:00:00Z"

PLACES = [
    {"type": "node", "id": 1, "lat": 52.5200, "lon": 13.4050,
     "tags": {"place": "city", "name": "Berlin", "population": "3,662,381"}},
    {"type": "node", "id": 2, "lat": 52.4000, "lon": 13.0600,
     "tags": {"place": "city", "name": "Potsdam", "population": "185000"}},
    {"type": "node", "id": 3, "lat": 50.0000, "lon": 10.0000,
     "tags": {"place": "town", "name": "Kleinstadt", "population": "8000"}},
    {"type": "node", "id": 4, "lat": 35.6895, "lon": 139.6917,
     "tags": {"place": "city", "name": "東京都", "name:en": "Tokyo", "population": "14000000"}},
]

VEGAN = [
    # Kreuzberg: nearer to no town, but well inside Berlin's metro radius.
    {"type": "node", "id": 10, "lat": 52.4986, "lon": 13.4030,
     "tags": {"amenity": "restaurant", "name": "Green Garden", "diet:vegan": "only",
              "cuisine": "thai;vietnamese", "addr:street": "Oranienstraße", "addr:housenumber": "1",
              "addr:postcode": "10999", "addr:city": "Berlin", "opening_hours": "Mo-Su 12:00-22:00",
              "check_date": "2026-09-08"}},
    # A restaurant mapped as a building outline: coordinates come from Overpass "center".
    {"type": "way", "id": 11, "center": {"lat": 52.53, "lon": 13.41},
     "tags": {"amenity": "cafe", "name": "Bean Room", "diet:vegan": "yes"}},
    # cuisine=vegan without a diet tag still describes a vegan kitchen.
    {"type": "node", "id": 12, "lat": 50.01, "lon": 10.01,
     "tags": {"amenity": "fast_food", "name": "Plant Snack", "cuisine": "vegan"}},
    {"type": "node", "id": 13, "lat": 35.66, "lon": 139.70,
     "tags": {"shop": "bakery", "name": "やさい", "name:en": "Yasai Bakery", "diet:vegan": "limited"}},
    # Far from any settlement: falls back to the address city.
    {"type": "node", "id": 14, "lat": -10.0, "lon": -60.0,
     "tags": {"amenity": "restaurant", "name": "River Lodge", "diet:vegan": "yes", "addr:city": "Remoto"}},
    {"type": "node", "id": 15, "lat": 52.52, "lon": 13.40, "tags": {"amenity": "restaurant", "diet:vegan": "only"}},
    {"type": "node", "id": 16, "lat": 52.52, "lon": 13.40,
     "tags": {"amenity": "vending_machine", "name": "Snack Box", "diet:vegan": "yes"}},
]


def write_cache(root: Path, tiles: dict[str, tuple[str, list[dict]]]) -> Path:
    cache = root / "cache"
    lock = {"date": DATE, "endpoint": osm.DEFAULT_ENDPOINT, "tiles": {}}
    for key, (kind, elements) in tiles.items():
        payload = json.dumps({"version": 0.6, "elements": elements}).encode()
        path = cache / (key + ".json")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)
        lock["tiles"][key] = {"kind": kind, "bbox": [], "query": "fixture",
                              "sha256": hashlib.sha256(payload).hexdigest(),
                              "bytes": len(payload), "elements": len(elements)}
    (cache / "lock.json").write_text(json.dumps(lock))
    return cache


class OsmVeganPackTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        # The Berlin restaurant arrives in two tiles that share an edge.
        self.cache = write_cache(self.root, {
            "place-city/world": ("place-city", [p for p in PLACES if p["tags"]["place"] == "city"]),
            "place-town/world": ("place-town", [p for p in PLACES if p["tags"]["place"] == "town"]),
            "vegan-only/a": ("vegan-only", VEGAN),
            "vegan-only/b": ("vegan-only", VEGAN[:1]),
            "vegan-yes/world": ("vegan-yes", []),
            "vegan-limited/world": ("vegan-limited", []),
            "cuisine-vegan/world": ("cuisine-vegan", VEGAN[2:3]),
        })

    def tearDown(self):
        self.directory.cleanup()

    def by_id(self):
        return {document["document_id"]: document for document in osm.documents(self.cache)}

    def test_places_get_destinations_categories_and_honest_labels(self):
        docs = self.by_id()
        self.assertEqual(["osm-place-n10", "osm-place-n12", "osm-place-n13", "osm-place-n14", "osm-place-w11"],
                         sorted(docs))
        green = docs["osm-place-n10"]["text"].splitlines()
        self.assertEqual("Destination: Berlin", green[0])
        self.assertIn("Category: Eat", green)
        self.assertIn("Place: Green Garden", green)
        self.assertIn("Vegan: fully vegan", green)
        self.assertIn("Cuisine: thai, vietnamese", green)
        self.assertIn("Address: Oranienstraße 1, 10999 Berlin", green)
        self.assertIn("Listing last checked: 2026-09-08", green)
        self.assertIn("Map data snapshot: 2026-09-28 (© OpenStreetMap contributors)", green)
        self.assertEqual("Berlin — Green Garden", docs["osm-place-n10"]["title"])
        self.assertEqual("https://www.openstreetmap.org/node/10", docs["osm-place-n10"]["source"])
        self.assertEqual("ODbL-1.0", docs["osm-place-n10"]["license"])

        bean = docs["osm-place-w11"]["text"].splitlines()
        self.assertIn("Vegan: vegan options", bean)
        self.assertIn("Latitude: 52.530000", bean)
        self.assertEqual("https://www.openstreetmap.org/way/11", docs["osm-place-w11"]["source"])
        self.assertIn("Vegan: fully vegan", docs["osm-place-n12"]["text"])
        self.assertIn("Destination: Kleinstadt", docs["osm-place-n12"]["text"])

        yasai = docs["osm-place-n13"]["text"].splitlines()
        self.assertEqual("Destination: Tokyo", yasai[0])
        self.assertIn("Category: Buy", yasai)
        self.assertIn("Place: Yasai Bakery", yasai)
        self.assertIn("Local name: やさい", yasai)
        self.assertIn("Vegan: limited vegan options", yasai)
        self.assertIn("Destination: Remoto", docs["osm-place-n14"]["text"])
        self.assertNotIn("Listing last checked", docs["osm-place-w11"]["text"])

    def test_largest_nearby_city_wins_over_a_nearer_smaller_city(self):
        gazetteer = osm.Gazetteer([
            osm.Settlement(52.5200, 13.4050, "Berlin", "city", 3_662_381),
            osm.Settlement(52.4000, 13.0600, "Potsdam", "city", 185_000),
        ])
        # About 5 km from Potsdam and 26 km from Berlin.
        self.assertEqual("Berlin", gazetteer.destination(52.41, 13.13))
        self.assertIsNone(gazetteer.destination(0.0, 0.0))

    def test_gazetteer_wraps_the_antimeridian(self):
        gazetteer = osm.Gazetteer([osm.Settlement(-16.5, 179.9, "Labasa", "town", 30_000)])
        self.assertEqual("Labasa", gazetteer.destination(-16.5, -179.95))

    def test_tampered_cache_is_rejected(self):
        (self.cache / "vegan-only/a.json").write_text('{"elements": []}')
        with self.assertRaises(SystemExit):
            osm.documents(self.cache)

    def test_a_missing_query_kind_stops_the_build(self):
        lock_path = self.cache / "lock.json"
        lock = json.loads(lock_path.read_text())
        del lock["tiles"]["vegan-limited/world"]
        lock_path.write_text(json.dumps(lock))
        with self.assertRaises(SystemExit):
            osm.documents(self.cache)

    def test_settlements_without_coordinates_stop_the_build(self):
        # Overpass "out tags;" returns nodes without lat/lon; the build must not silently
        # fall back to address tags for every place.
        stripped = [{k: v for k, v in p.items() if k not in ("lat", "lon")} for p in PLACES]
        cache = write_cache(self.root / "untagged", {
            "place-city/world": ("place-city", [p for p in stripped if p["tags"]["place"] == "city"]),
            "place-town/world": ("place-town", [p for p in stripped if p["tags"]["place"] == "town"]),
            "vegan-only/a": ("vegan-only", VEGAN),
            "vegan-yes/world": ("vegan-yes", []),
            "vegan-limited/world": ("vegan-limited", []),
            "cuisine-vegan/world": ("cuisine-vegan", []),
        })
        with self.assertRaises(SystemExit):
            osm.documents(cache)

    def test_fetch_resumes_inside_tiles_an_earlier_run_split(self):
        cache = self.root / "resume"
        cache.mkdir()
        quarters = list(osm.split(osm.WORLD))
        tiles = {}
        for bbox in quarters:
            key = osm.tile_key("vegan-only", bbox)
            payload = json.dumps({"elements": [], "osm3s": {"timestamp_osm_base": "2026-10-02T00:00:00Z"}}).encode()
            (cache / (key + ".json")).parent.mkdir(parents=True, exist_ok=True)
            (cache / (key + ".json")).write_bytes(payload)
            tiles[key] = {"kind": "vegan-only", "bbox": list(bbox), "query": osm.QUERIES["vegan-only"](None, bbox, 900),
                          "sha256": hashlib.sha256(payload).hexdigest(), "bytes": len(payload), "elements": 0}
        (cache / "lock.json").write_text(json.dumps({"date": None, "endpoint": osm.DEFAULT_ENDPOINT, "tiles": tiles}))
        asked = []
        original = osm.overpass
        osm.overpass = lambda endpoint, query: asked.append(query) or b"{}"
        try:
            osm.fetch(argparse.Namespace(cache_dir=cache, date=None, endpoint=osm.DEFAULT_ENDPOINT,
                                         kinds=["vegan-only"], timeout=900, pause=0))
        finally:
            osm.overpass = original
        self.assertEqual([], asked)

    def test_queries_use_exact_tag_values_at_the_pinned_date(self):
        query = osm.QUERIES["vegan-only"](DATE, osm.WORLD, 900)
        self.assertEqual('[out:json][timeout:900][date:"2026-09-28T00:00:00Z"];'
                         'nwr["diet:vegan"="only"](-90.0,-180.0,90.0,180.0);out center tags;', query)
        self.assertNotIn("~", "".join(q(DATE, osm.WORLD, 900) for q in osm.QUERIES.values()))
        self.assertEqual('[out:json][timeout:900];node["place"="town"]["name"](-90.0,-180.0,90.0,180.0);out;',
                         osm.QUERIES["place-town"](None, osm.WORLD, 900))

    def test_unpinned_snapshot_uses_the_newest_overpass_timestamp(self):
        lock = {"date": None, "tiles": {"a": {"osm_base": "2026-10-01T23:58:00Z"},
                                        "b": {"osm_base": "2026-10-02T00:32:34Z"}}}
        self.assertEqual("2026-10-02", osm.snapshot_day(lock))
        with self.assertRaises(SystemExit):
            osm.snapshot_day({"date": None, "tiles": {"a": {}}})

    def test_build_is_byte_reproducible_and_searchable(self):
        packs = []
        for run in ("one", "two"):
            out = self.root / run
            osm.build(argparse.Namespace(cache_dir=self.cache, out=out, version="2026.09.28"))
            pack_dir = out / "osm-vegan-places"
            packs.append(next(pack_dir.glob("*.fapack")).read_bytes())
            self.assertTrue((pack_dir / "OVERPASS-LOCK.json").is_file())
        self.assertEqual(packs[0], packs[1])
        database = next((self.root / "one" / "osm-vegan-places").glob("**/content.sqlite"))
        with sqlite3.connect(database) as connection:
            rows = connection.execute(
                "SELECT document_id FROM chunks_fts WHERE chunks_fts MATCH 'vegan AND berlin AND restaurant'"
            ).fetchall()
        self.assertEqual([("osm-place-n10",)], rows)
        with sqlite3.connect(database) as connection:
            points = connection.execute(
                "SELECT c.document_id, p.lat, p.lon FROM place_points p JOIN chunks_fts c ON c.rowid = p.rowid "
                "ORDER BY c.document_id").fetchall()
        self.assertEqual(5, len(points))
        self.assertIn(("osm-place-n10", 52.4986, 13.403), points)
        self.assertIn(("osm-place-w11", 52.53, 13.41), points)



class OverpassTimeoutTest(unittest.TestCase):
    def test_repeated_client_timeouts_split_the_tile_instead_of_failing(self):
        from unittest import mock
        calls = []

        def slow(*_args, **_kwargs):
            calls.append(1)
            raise TimeoutError("The read operation timed out")

        with mock.patch.object(osm.urllib.request, "urlopen", slow), mock.patch.object(osm.time, "sleep"):
            with self.assertRaises(osm.TileTooLarge):
                osm.overpass("https://example.invalid/api", "[out:json];node(1);out;")
        self.assertEqual(osm.CLIENT_TIMEOUTS_BEFORE_SPLIT, len(calls))

if __name__ == "__main__":
    unittest.main()
