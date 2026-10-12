import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import retain_artifact as tool


class RetainArtifactTests(unittest.TestCase):
    def prepare(self, root):
        source = root / "module.fapack"
        source.write_bytes(bytes(range(256)) * 32 + b"exact original archive bytes")
        retained = root / "retained"
        retained.mkdir()
        args = dict(source=source, output=retained / "module.fapack.gz",
                    expected_bytes=source.stat().st_size,
                    expected_sha256=hashlib.sha256(source.read_bytes()).hexdigest())
        return args

    def test_roundtrip_restores_exact_bytes_and_keeps_both_copies_by_default(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            original = args["source"].read_bytes()
            report = tool.retain_artifact(**args)
            self.assertFalse(report["originalRemoved"])
            result = tool.restore_artifact(args["output"], root / "restored.fapack")
            self.assertEqual(original, (root / "restored.fapack").read_bytes())
            self.assertEqual(original, args["source"].read_bytes())
            self.assertTrue(args["output"].is_file())
            self.assertTrue(result["compressedBackupRetained"])

    def test_explicit_removal_occurs_only_after_retention_and_restoration_remains_possible(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            original = args["source"].read_bytes()
            report = tool.retain_artifact(**args, remove_original=True)
            self.assertTrue(report["originalRemoved"])
            self.assertFalse(args["source"].exists())
            tool.restore_artifact(args["output"], args["source"])
            self.assertEqual(original, args["source"].read_bytes())

    def test_compression_is_deterministic_without_source_filename_or_time(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            tool.retain_artifact(**args)
            second = args | dict(output=root / "retained/other.gz")
            tool.retain_artifact(**second)
            self.assertEqual(args["output"].read_bytes(), second["output"].read_bytes())
            self.assertEqual(b"\x00\x00\x00\x00", args["output"].read_bytes()[4:8])

    def test_wrong_pin_fails_without_changing_source(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            with self.assertRaisesRegex(ValueError, "Original artifact"):
                tool.retain_artifact(**(args | dict(expected_sha256="0" * 64)), remove_original=True)
            self.assertTrue(args["source"].is_file())
            self.assertFalse(args["output"].exists())

    def test_unknown_output_or_receipt_is_never_overwritten(self):
        for receipt in (False, True):
            with self.subTest(receipt=receipt), tempfile.TemporaryDirectory() as directory:
                args = self.prepare(Path(directory))
                target = Path(str(args["output"]) + ".retention.json") if receipt else args["output"]
                target.write_bytes(b"owner data")
                with self.assertRaisesRegex(ValueError, "Destination exists"):
                    tool.retain_artifact(**args, remove_original=True)
                self.assertEqual(b"owner data", target.read_bytes())
                self.assertTrue(args["source"].is_file())

    def test_symlink_source_and_destination_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            link = root / "link"
            link.symlink_to(args["source"])
            with self.assertRaisesRegex(ValueError, "regular file"):
                tool.retain_artifact(**(args | dict(source=link)))
            args["output"].symlink_to(root / "missing")
            with self.assertRaisesRegex(ValueError, "Destination exists"):
                tool.retain_artifact(**args)

    def test_receipt_failure_keeps_original_and_verified_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            with patch.object(tool, "_write_receipt", side_effect=OSError("disk full")):
                with self.assertRaisesRegex(OSError, "disk full"):
                    tool.retain_artifact(**args, remove_original=True)
            self.assertTrue(args["source"].is_file())
            self.assertEqual(args["source"].read_bytes(), gzip.decompress(args["output"].read_bytes()))
            self.assertFalse(list(args["output"].parent.glob(".retention-*")))

    def test_failed_compression_verification_keeps_original_and_discards_only_scratch(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            with patch.object(tool, "_verify_gzip", side_effect=ValueError("corrupted")):
                with self.assertRaisesRegex(ValueError, "corrupted"):
                    tool.retain_artifact(**args, remove_original=True)
            self.assertTrue(args["source"].is_file())
            self.assertEqual([], list(args["output"].parent.iterdir()))

    def test_original_changed_before_removal_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            write_receipt = tool._write_receipt
            def change_original(path, report):
                write_receipt(path, report)
                args["source"].write_bytes(b"new owner content")
            with patch.object(tool, "_write_receipt", side_effect=change_original):
                with self.assertRaisesRegex(ValueError, "Original artifact changed"):
                    tool.retain_artifact(**args, remove_original=True)
            self.assertEqual(b"new owner content", args["source"].read_bytes())

    def test_changed_receipt_or_archive_cannot_authorize_original_removal(self):
        for change in ("receipt", "archive", "receipt_symlink"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                args = self.prepare(root)
                write_receipt = tool._write_receipt
                def damage_retention(path, report):
                    write_receipt(path, report)
                    if change == "receipt":
                        path.write_text("{}")
                    elif change == "archive":
                        args["output"].write_bytes(b"changed gzip")
                    else:
                        replacement = root / "other-receipt"
                        replacement.write_text(path.read_text())
                        path.unlink()
                        path.symlink_to(replacement)
                with patch.object(tool, "_write_receipt", side_effect=damage_retention):
                    with self.assertRaises(ValueError):
                        tool.retain_artifact(**args, remove_original=True)
                self.assertTrue(args["source"].is_file())

    def test_corrupt_or_truncated_archive_cannot_restore(self):
        for damage in (lambda value: value[:-4], lambda value: b"bad gzip bytes"):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                args = self.prepare(root)
                tool.retain_artifact(**args)
                args["output"].write_bytes(damage(args["output"].read_bytes()))
                with self.assertRaisesRegex(ValueError, "Compressed artifact"):
                    tool.restore_artifact(args["output"], root / "restored")
                self.assertFalse((root / "restored").exists())

    def test_rehashed_corrupt_gzip_and_wrong_restoration_pin_are_rejected(self):
        for change in ("truncate", "original_hash", "original_size"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                args = self.prepare(root)
                result = tool.retain_artifact(**args)
                receipt = Path(result["receipt"])
                report = json.loads(receipt.read_text())
                if change == "truncate":
                    args["output"].write_bytes(args["output"].read_bytes()[:-4])
                    report["compressedBytes"], report["compressedSha256"] = tool._digest(args["output"])
                elif change == "original_hash":
                    report["originalSha256"] = "0" * 64
                else:
                    report["originalBytes"] = 1
                receipt.write_text(json.dumps(report))
                with self.assertRaises((ValueError, EOFError)):
                    tool.restore_artifact(args["output"], root / "restored")
                self.assertFalse((root / "restored").exists())
                self.assertFalse(list(root.glob(".retention-*")))

    def test_restore_preserves_existing_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            tool.retain_artifact(**args)
            original = args["source"].read_bytes()
            with self.assertRaisesRegex(ValueError, "Destination exists"):
                tool.restore_artifact(args["output"], args["source"])
            self.assertEqual(original, args["source"].read_bytes())

    def test_archive_changed_during_restoration_does_not_publish_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            tool.retain_artifact(**args)
            verify = tool._verify_gzip
            def change_archive(*positional, **keywords):
                verify(*positional, **keywords)
                with args["output"].open("ab") as stream:
                    stream.write(b"changed after verification")
            with patch.object(tool, "_verify_gzip", side_effect=change_archive):
                with self.assertRaisesRegex(ValueError, "changed during restoration"):
                    tool.restore_artifact(args["output"], root / "restored")
            self.assertFalse((root / "restored").exists())
            self.assertFalse(list(root.glob(".retention-*")))

    def test_destination_appearing_during_commit_is_never_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            commit = tool._commit
            def conflict(temporary, destination):
                destination.write_bytes(b"concurrent owner data")
                commit(temporary, destination)
            with patch.object(tool, "_commit", side_effect=conflict):
                with self.assertRaises(FileExistsError):
                    tool.retain_artifact(**args, remove_original=True)
            self.assertTrue(args["source"].is_file())
            self.assertEqual(b"concurrent owner data", args["output"].read_bytes())

    def test_hardlinked_source_retains_other_link_and_reports_link_count(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            original = args["source"].read_bytes()
            other = root / "second-link"
            other.hardlink_to(args["source"])
            report = tool.retain_artifact(**args, remove_original=True)
            self.assertEqual(2, report["originalLinkCount"])
            self.assertFalse(args["source"].exists())
            self.assertEqual(original, other.read_bytes())

    def test_low_space_keeps_source_and_creates_no_retention(self):
        with tempfile.TemporaryDirectory() as directory:
            args = self.prepare(Path(directory))
            with patch.object(tool, "_space", side_effect=ValueError("Insufficient space")):
                with self.assertRaisesRegex(ValueError, "Insufficient space"):
                    tool.retain_artifact(**args, remove_original=True)
            self.assertTrue(args["source"].is_file())
            self.assertEqual([], list(args["output"].parent.iterdir()))

    def test_does_not_change_strict_builder_module_layout(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            with self.assertRaisesRegex(ValueError, "separate retention directory"):
                tool.retain_artifact(**(args | dict(output=root / "module.fapack.gz")))

    def test_cli_help_works_without_python_path(self):
        result = subprocess.run([sys.executable, str(Path(tool.__file__).resolve()), "--help"],
                                cwd=tempfile.gettempdir(), capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("restore", result.stdout)


if __name__ == "__main__":
    unittest.main()
