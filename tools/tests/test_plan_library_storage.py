import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.maintenance.plan_library_storage import (DEVICE_HEADROOM, LIMIT, METADATA_RESERVE,
                                  measure_archive, plan, simulate)


def operation(**changes):
    return dict(id="topic", version="1", type="KNOWLEDGE", archiveBytes=1000,
                installedBytes=800, mode="download", partialBytes=0) | changes


def pack(path, payload=b"Full cited passage.", **changes):
    manifest = dict(schemaVersion=1, id="topic", version="1", type="KNOWLEDGE",
                    license="CC0-1.0", artifacts=[dict(path="content.sqlite", bytes=len(payload),
                        sha256=hashlib.sha256(payload).hexdigest())]) | changes
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as archive:
        archive.writestr("manifest.json", json.dumps(manifest))
        archive.writestr("content.sqlite", payload)
    return hashlib.sha256(path.read_bytes()).hexdigest()


class StoragePlanTests(unittest.TestCase):
    def test_compressed_pack_reserves_expanded_payload_not_archive_twice(self):
        op = operation(packSchemaVersion=2, archiveBytes=10_643_533_492, installedBytes=24_346_003_429)
        result = simulate(2_000_000_000, LIMIT, 1_000_000_000, [op])
        self.assertEqual(36_990_585_497, result["plannedPeakBytes"])
        self.assertTrue(result["totalLimitAndFreeSpacePlanPass"])
        self.assertTrue(result["selectedKnowledgeMeetsTwentyDecimalGB"])
        self.assertFalse(simulate(16_000_000_000, LIMIT, 1, [op])["totalLimitAndFreeSpacePlanPass"])

    def test_schema_two_deflated_payload_is_fully_verified(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "compressed.fapack"
            payload = b"A cited passage. " * 10000
            manifest = dict(schemaVersion=2, id="topic", version="1", type="KNOWLEDGE",
                            license="CC0-1.0", artifacts=[dict(path="content.sqlite", bytes=len(payload),
                                sha256=hashlib.sha256(payload).hexdigest())])
            for schema in (2, 1):
                manifest["schemaVersion"] = schema
                with zipfile.ZipFile(archive, "w") as zip:
                    zip.writestr("manifest.json", json.dumps(manifest))
                    zip.writestr("content.sqlite", payload, compress_type=zipfile.ZIP_DEFLATED)
                pin = hashlib.sha256(archive.read_bytes()).hexdigest()
                if schema == 1:
                    with self.assertRaisesRegex(ValueError, "compression"):
                        measure_archive(archive, pin)
                else:
                    measured = measure_archive(archive, pin)
                    self.assertEqual(len(payload), measured["installedBytes"])
                    self.assertEqual(2, measured["packSchemaVersion"])
                    self.assertTrue(measured["archiveAndEveryPayloadVerified"])

    def test_counts_download_and_extraction_together(self):
        result = simulate(100, LIMIT, 1, [operation()])
        self.assertEqual(100 + 1000 + 800 + METADATA_RESERVE, result["operations"][0]["peakBytes"])
        self.assertEqual(100 + 2000 + METADATA_RESERVE, result["plannedPeakBytes"])
        self.assertEqual(100 + 800 + METADATA_RESERVE, result["plannedFinalBytes"])
        self.assertTrue(result["totalLimitAndFreeSpacePlanPass"])
        self.assertFalse(result["runtimeAcceptanceComplete"])

    def test_existing_partial_is_not_counted_twice_and_released_only_after_import(self):
        result = simulate(600, LIMIT, 1, [operation(partialBytes=500)])
        self.assertEqual(600 + 500 + 800 + METADATA_RESERVE, result["operations"][0]["peakBytes"])
        self.assertEqual(600 + 800 + METADATA_RESERVE - 500, result["plannedFinalBytes"])

    def test_verified_complete_archive_needs_no_more_download_but_still_needs_extraction(self):
        result = simulate(1100, LIMIT, 1, [operation(partialBytes=1000)])
        self.assertEqual(0, result["operations"][0]["remainingDownloadBytes"])
        self.assertEqual(1100 + 800 + METADATA_RESERVE - 1000, result["plannedFinalBytes"])

    def test_old_and_disabled_editions_are_never_subtracted(self):
        result = simulate(10_000_000_000, LIMIT, 1, [operation()])
        self.assertEqual(10_000_000_000 + 800 + METADATA_RESERVE, result["plannedFinalBytes"])

    def test_later_installation_peaks_include_every_previous_installed_module(self):
        result = simulate(100, LIMIT, 1, [operation(), operation(id="second")])
        self.assertEqual(100 + 800 + 2 * METADATA_RESERVE + 2000, result["plannedPeakBytes"])

    def test_local_model_import_counts_spooled_archive_and_payload(self):
        result = simulate(100, LIMIT, 1, [operation(type="MODEL", mode="local-import")])
        self.assertEqual(100 + 1000 + 800 + METADATA_RESERVE, result["plannedPeakBytes"])
        self.assertEqual(0, result["selectedKnowledgePayloadBytes"])

    def test_peak_can_fail_even_when_final_size_fits(self):
        result = simulate(LIMIT - METADATA_RESERVE - 1500, LIMIT, 1, [operation()])
        self.assertLess(result["plannedFinalBytes"], LIMIT)
        self.assertFalse(result["totalLimitAndFreeSpacePlanPass"])
        self.assertIn("exceeds_total_limit_with_growth_reserve", result["operations"][0]["rejectionReasons"])

    def test_total_cap_boundary_includes_growth_reserve(self):
        current = LIMIT - METADATA_RESERVE - 2000 - 500
        self.assertTrue(simulate(current, LIMIT, 500, [operation()])["totalLimitAndFreeSpacePlanPass"])
        self.assertFalse(simulate(current + 1, LIMIT, 500, [operation()])["totalLimitAndFreeSpacePlanPass"])

    def test_disk_free_boundary_includes_device_and_growth_reserves(self):
        free = 2000 + METADATA_RESERVE + 500 + DEVICE_HEADROOM
        self.assertTrue(simulate(100, free, 500, [operation()])["totalLimitAndFreeSpacePlanPass"])
        result = simulate(100, free - 1, 500, [operation()])
        self.assertFalse(result["totalLimitAndFreeSpacePlanPass"])
        self.assertEqual(free, result["requiredAdditionalFreeBytesWithReserves"])

    def test_twenty_gb_does_not_count_model_or_archive_bytes(self):
        result = simulate(100, LIMIT, 1, [operation(type="MODEL", mode="local-import",
                            archiveBytes=20_000_000_100, installedBytes=20_000_000_000)])
        self.assertFalse(result["selectedKnowledgeMeetsTwentyDecimalGB"])
        self.assertTrue(simulate(100, LIMIT, 1, [operation(mode="local-import",
            archiveBytes=20_000_000_100, installedBytes=20_000_000_000)])["selectedKnowledgeMeetsTwentyDecimalGB"])

    def test_invalid_sizes_and_unreserved_exact_limit_rejected(self):
        for value in (-1, True, 1.5, "1", 2**63):
            with self.subTest(value=value), self.assertRaises(ValueError):
                simulate(value, LIMIT, 1, [operation()])
        with self.assertRaises(ValueError):
            simulate(0, LIMIT, 0, [operation()])

    def test_duplicate_selections_and_editions_do_not_pad_knowledge(self):
        for second in (operation(), operation(version="2")):
            with self.assertRaisesRegex(ValueError, "Duplicate selection"):
                simulate(0, LIMIT, 1, [operation(), second])

    def test_unsupported_transfer_and_partial_states_fail_closed(self):
        for op in (operation(mode="other"), operation(partialBytes=1001),
                   operation(mode="local-import", partialBytes=1), operation(type="MODEL"),
                   operation(type="UNKNOWN"), operation(installedBytes=1001)):
            with self.subTest(op=op), self.assertRaises(ValueError):
                simulate(2000, LIMIT, 1, [op])
        with self.assertRaisesRegex(ValueError, "included in currentBytes"):
            simulate(0, LIMIT, 1, [operation(partialBytes=1)])

    def test_actual_pack_is_pinned_and_every_payload_is_verified(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "test.fapack"
            pin = pack(archive)
            measured = measure_archive(archive, pin)
            self.assertEqual(archive.stat().st_size, measured["archiveBytes"])
            self.assertEqual(len(b"Full cited passage."), measured["installedBytes"])
            self.assertTrue(measured["archiveAndEveryPayloadVerified"])
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                measure_archive(archive, "0" * 64)

    def test_rehashed_corrupt_payload_does_not_pass_manifest_verification(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "test.fapack"
            pin = pack(archive, artifacts=[dict(path="content.sqlite", bytes=19, sha256="0" * 64)])
            with self.assertRaisesRegex(ValueError, "Payload checksum"):
                measure_archive(archive, pin)

    def test_undeclared_payloads_and_oversized_manifest_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "test.fapack"
            pin = pack(archive, artifacts=[])
            with self.assertRaises(ValueError):
                measure_archive(archive, pin)
            with zipfile.ZipFile(archive, "w") as zip:
                zip.writestr("manifest.json", b" " * 1_048_577)
            with self.assertRaisesRegex(ValueError, "bounded size"):
                measure_archive(archive, hashlib.sha256(archive.read_bytes()).hexdigest())

    def test_compressed_archives_and_boolean_schema_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "test.fapack"
            pin = pack(archive, schemaVersion=True)
            with self.assertRaisesRegex(ValueError, "Unsupported manifest"):
                measure_archive(archive, pin)
            with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as zip:
                zip.writestr("manifest.json", b"{}")
            with self.assertRaisesRegex(ValueError, "STORED"):
                measure_archive(archive, hashlib.sha256(archive.read_bytes()).hexdigest())

    def test_model_provenance_is_included_in_measured_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "test.fapack"
            payload = b"Full cited passage."
            provenance = b"Pinned conversion metadata"
            artifacts = [dict(path=name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
                         for name, data in (("content.sqlite", payload), ("provenance.json", provenance))]
            pack(archive, type="MODEL", artifacts=artifacts)
            with zipfile.ZipFile(archive, "a") as zip:
                zip.writestr("provenance.json", provenance)
            measured = measure_archive(archive, hashlib.sha256(archive.read_bytes()).hexdigest())
            self.assertEqual(len(payload) + len(provenance), measured["installedBytes"])

    def test_malformed_manifest_and_plan_are_rejected_as_validation_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "test.fapack"
            with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_STORED) as zip:
                zip.writestr("manifest.json", b"[]")
            with self.assertRaisesRegex(ValueError, "Unsupported manifest"):
                measure_archive(archive, hashlib.sha256(archive.read_bytes()).hexdigest())
            config = root / "plan.json"
            config.write_text("[]")
            with self.assertRaisesRegex(ValueError, "JSON object"):
                plan(config)

    def test_relative_paths_resolve_from_config_not_calling_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "test.fapack"
            pin = pack(archive)
            config = root / "plan.json"
            config.write_text(json.dumps(dict(currentBytes=100, freeBytes=LIMIT,
                growthReserveBytes=1, operations=[dict(archive="test.fapack", sha256=pin, mode="download")])))
            result = plan(config)
            self.assertTrue(result["totalLimitAndFreeSpacePlanPass"])
            tool = Path(__file__).resolve().parents[1] / "maintenance/plan_library_storage.py"
            process = subprocess.run([sys.executable, str(tool), str(config)], cwd=root,
                                     capture_output=True, text=True)
            self.assertEqual(0, process.returncode, process.stderr)
            self.assertFalse(json.loads(process.stdout)["runtimeAcceptanceComplete"])
            original = archive.read_bytes()
            self.assertEqual(pin, hashlib.sha256(original).hexdigest())

    def test_cli_over_budget_is_nonzero_and_does_not_install_or_delete(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "test.fapack"
            pin = pack(archive)
            config = root / "plan.json"
            config.write_text(json.dumps(dict(currentBytes=LIMIT, freeBytes=LIMIT,
                growthReserveBytes=1, operations=[dict(archive="test.fapack", sha256=pin, mode="download")])))
            tool = Path(__file__).resolve().parents[1] / "maintenance/plan_library_storage.py"
            process = subprocess.run([sys.executable, str(tool), str(config)], capture_output=True, text=True)
            self.assertEqual(1, process.returncode)
            self.assertFalse(json.loads(process.stdout)["totalLimitAndFreeSpacePlanPass"])
            self.assertEqual(["plan.json", "test.fapack"], sorted(p.name for p in root.iterdir()))


if __name__ == "__main__":
    unittest.main()
