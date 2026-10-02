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
sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_osm_essentials_pack as ess  # noqa: E402
from test_build_osm_vegan_pack import PLACES, write_cache  # noqa: E402

ESSENTIALS = {
    "pharmacy": [
        {"type": "node", "id": 100, "lat": 52.521, "lon": 13.41,
         "tags": {"amenity": "pharmacy", "name": "Apotheke am Platz", "opening_hours": "Mo-Sa 08:00-20:00",
                  "phone": "+49 30 123", "addr:street": "Alexanderplatz", "addr:housenumber": "2"}},
    ],
    "hospital": [
        {"type": "way", "id": 101, "center": {"lat": 52.526, "lon": 13.377},
         "tags": {"amenity": "hospital", "name": "Charité", "emergency": "yes"}},
    ],
    "atm": [
        # No name: described by its operator instead.
        {"type": "node", "id": 102, "lat": 52.52, "lon": 13.405, "tags": {"amenity": "atm", "operator": "Sparkasse"}},
    ],
    "toilets": [
        {"type": "node", "id": 103, "lat": 52.5205, "lon": 13.4045,
         "tags": {"amenity": "toilets", "fee": "yes", "wheelchair": "yes", "access": "yes"}},
    ],
    "embassy": [
        {"type": "node", "id": 104, "lat": 52.516, "lon": 13.38,
         "tags": {"amenity": "embassy", "name": "Embassy of Japan", "country": "JP"}},
        # No name: described by the country it represents.
        {"type": "way", "id": 107, "center": {"lat": 52.51, "lon": 13.37},
         "tags": {"amenity": "embassy", "country": "NL"}},
        # The same embassy mapped again as a point at the same centre is listed once.
        {"type": "node", "id": 108, "lat": 52.51, "lon": 13.37,
         "tags": {"amenity": "embassy", "country": "NL"}},
    ],
    "diplomatic": [
        # The same embassy tagged the newer way is kept once; a consulate keeps its label.
        {"type": "node", "id": 104, "lat": 52.516, "lon": 13.38,
         "tags": {"office": "diplomatic", "diplomatic": "embassy", "name": "Embassy of Japan"}},
        {"type": "node", "id": 105, "lat": 52.50, "lon": 13.35,
         "tags": {"office": "diplomatic", "diplomatic": "consulate", "name": "Consulate of Chile"}},
        {"type": "node", "id": 106, "lat": 52.50, "lon": 13.35,
         "tags": {"office": "diplomatic", "diplomatic": "liaison", "name": "Liaison office"}},
    ],
}


class OsmEssentialsPackTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.settlements = write_cache(self.root / "places", {
            "place-city/world": ("place-city", [p for p in PLACES if p["tags"]["place"] == "city"]),
            "place-town/world": ("place-town", [p for p in PLACES if p["tags"]["place"] == "town"]),
        })
        self.cache = write_cache(self.root / "ess", {f"{k}/world": (k, v) for k, v in ESSENTIALS.items()})

    def tearDown(self):
        self.directory.cleanup()

    def by_id(self):
        docs, kinds = ess.documents(self.cache, self.settlements)
        return {d["document_id"]: d for d in docs}, kinds

    def test_listings_carry_category_type_and_practical_details(self):
        docs, kinds = self.by_id()
        self.assertEqual(["pharmacy", "hospital", "embassy", "diplomatic", "atm", "toilets"], kinds)
        pharmacy = docs["osm-place-n100"]["text"].splitlines()
        self.assertEqual("Destination: Berlin", pharmacy[0])
        self.assertIn("Category: Health", pharmacy)
        self.assertIn("Type: pharmacy", pharmacy)
        self.assertIn("Phone: +49 30 123", pharmacy)
        self.assertIn("Hours in source: Mo-Sa 08:00-20:00", pharmacy)
        self.assertIn("Emergency department: yes", docs["osm-place-w101"]["text"])
        self.assertIn("Place: ATM · Sparkasse", docs["osm-place-n102"]["text"])
        toilets = docs["osm-place-n103"]["text"]
        self.assertIn("Place: Toilets", toilets)
        self.assertIn("Fee: yes", toilets)
        self.assertIn("Wheelchair access: yes", toilets)
        self.assertNotIn("Phone:", toilets)
        self.assertIn("Country represented: JP", docs["osm-place-n104"]["text"])
        self.assertIn("Type: consulate", docs["osm-place-n105"]["text"])
        self.assertNotIn("osm-place-n106", docs)
        netherlands = [d for d in docs.values() if "Country represented: NL" in d["text"]]
        self.assertEqual(1, len(netherlands))
        self.assertIn("Place: Embassy · NL", netherlands[0]["text"])

    def test_an_element_matching_two_kinds_is_kept_once(self):
        docs, _ = ess.documents(self.cache, self.settlements)
        ids = [d["document_id"] for d in docs]
        self.assertEqual(len(ids), len(set(ids)))

    def test_queries_use_exact_tags(self):
        self.assertEqual('[out:json][timeout:900];nwr["amenity"="pharmacy"](-90.0,-180.0,90.0,180.0);out center tags;',
                         ess.QUERIES["pharmacy"](None, (-90.0, -180.0, 90.0, 180.0), 900))
        self.assertIn("place-town", ess.QUERIES)

    def test_coverage_summary_fits_the_pack_limit_with_every_kind(self):
        summary = ess.coverage_summary(set(ess.KINDS))
        self.assertLessEqual(len(summary), 160)
        for name in ("pharmacies", "ATMs", "toilets", "water", "hostels"):
            self.assertIn(name, summary)
        self.assertEqual(1, summary.count("embassies"))

    def test_build_is_reproducible_and_records_categories(self):
        hashes = []
        for name in ("one", "two"):
            args = argparse.Namespace(cache_dir=self.cache, settlements_cache=self.settlements,
                                      out=self.root / name, version="2026.10.02")
            self.assertEqual(0, ess.build(args))
            pack = self.root / name / "osm-essentials" / "osm-essentials-2026.10.02.fapack"
            hashes.append(hashlib.sha256(pack.read_bytes()).hexdigest())
        self.assertEqual(hashes[0], hashes[1])
        db = sqlite3.connect(self.root / "one" / "osm-essentials" / "content.sqlite")
        rows = dict(db.execute("SELECT c.document_id, p.category FROM place_points p "
                               "JOIN chunks_fts c ON c.rowid = p.rowid").fetchall())
        self.assertEqual("Health", rows["osm-place-n100"])
        self.assertEqual("Money", rows["osm-place-n102"])
        lock = json.loads((self.root / "one" / "osm-essentials" / "OVERPASS-LOCK.json").read_text())
        self.assertIn("settlements", lock)


if __name__ == "__main__":
    unittest.main()
