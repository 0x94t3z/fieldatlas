import fcntl
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_library_batch as batch
from build_modular_library import build_library
from packtool.schema import DocumentError


class LibraryBatchTest(unittest.TestCase):
    def prepare(self, root, rows=None, **options):
        rows = rows if rows is not None else [dict(document_id=f"doc-{number}", title=f"Topic {number}",
            source=f"https://example.org/{number}?oldid=123", license="CC0-1.0",
            text="An original supporting sentence. Its qualification remains. " * 4) for number in (4, 1, 3, 2)]
        source = root / "source.jsonl"
        source.write_text("".join(json.dumps(row) + "\n" for row in rows))
        return dict(input_path=source, output_dir=root / "batch", expected_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
            pack_id="general-reference", version="1.0.0", title="General reference",
            license_id="CC0-1.0", source_urls=["https://example.org/snapshot/123"], module_text_bytes=250) | options

    def test_resumes_one_atomic_module_at_a_time_and_matches_existing_builder_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            old = build_library(**(args | {"output_dir": root / "atomic"}))
            first = batch.build_batch(**args)
            self.assertEqual(1, len(first["modules"]))
            self.assertFalse(first["complete"])
            self.assertFalse(first["releaseReady"])
            self.assertTrue(first["sourceAndFinishedArchivesStillRequired"])
            for count in (2, 3, 4):
                report = batch.build_batch(**args, resume=True)
                self.assertEqual(count, len(report["modules"]))
            self.assertTrue(report["complete"])
            self.assertFalse(report["meetsTwentyDecimalGBTarget"])
            self.assertEqual(old["installedBytes"], report["installedBytes"])
            for expected, actual in zip(old["modules"], report["modules"]):
                self.assertEqual((root / "atomic" / expected["archive"]).read_bytes(), (root / "batch" / actual["archive"]).read_bytes())
                self.assertFalse((root / "batch" / actual["archive"]).parent.joinpath("content.sqlite").exists())
            self.assertEqual(report, batch.build_batch(**args, resume=True))

    def test_interrupted_next_module_retains_already_verified_module(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            first = batch.build_batch(**args)
            archive = root / "batch" / first["modules"][0]["archive"]
            original = archive.read_bytes()
            with patch("build_library_batch.build_pack_streaming", side_effect=RuntimeError("interrupted")):
                with self.assertRaisesRegex(RuntimeError, "interrupted"):
                    batch.build_batch(**args, resume=True)
            self.assertEqual(original, archive.read_bytes())
            self.assertFalse((root / "batch/.pending").exists())
            self.assertEqual(2, len(batch.build_batch(**args, resume=True)["modules"]))

    def test_commit_survives_a_failed_progress_report_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            write = batch._atomic_json
            def fail_progress(path, value):
                if path.name == "batch-progress.json":
                    raise OSError("report interrupted")
                write(path, value)
            with patch("build_library_batch._atomic_json", side_effect=fail_progress):
                with self.assertRaisesRegex(OSError, "report interrupted"):
                    batch.build_batch(**args)
            committed = root / "batch/modules/general-reference-0001"
            self.assertTrue((committed / "module-build.json").exists())
            self.assertEqual(2, len(batch.build_batch(**args, resume=True)["modules"]))

    def test_partial_owned_stage_is_cleaned_but_unknown_stage_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            batch.build_batch(**args)
            stage = root / "batch/.pending"
            stage.mkdir()
            (stage / "owner.txt").write_text("someone else")
            (stage / "keep.txt").write_text("Keep this file")
            with self.assertRaisesRegex(ValueError, "Unrecognized pending"):
                batch.build_batch(**args, resume=True)
            self.assertTrue((stage / "keep.txt").exists())
            checkpoint = json.loads((root / "batch/checkpoint.json").read_text())
            (stage / "owner.txt").write_text(checkpoint["token"])
            report = batch.build_batch(**args, resume=True)
            self.assertEqual(2, len(report["modules"]))
            self.assertFalse(stage.exists())

    def test_changed_options_source_or_builder_cannot_resume(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            batch.build_batch(**args)
            with self.assertRaisesRegex(ValueError, "Resume identity changed"):
                batch.build_batch(**(args | {"module_text_bytes": 500}), resume=True)
            with patch("build_library_batch._builder_identity", return_value={"changed": "0" * 64}):
                with self.assertRaisesRegex(ValueError, "Resume identity changed"):
                    batch.build_batch(**args, resume=True)
            args["input_path"].write_text("changed")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                batch.build_batch(**args, resume=True)

    def test_tampered_archive_plan_or_index_is_rejected(self):
        for target in ("archive", "plan", "index"):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                args = self.prepare(root)
                report = batch.build_batch(**args)
                if target == "archive":
                    with (root / "batch" / report["modules"][0]["archive"]).open("ab") as stream:
                        stream.write(b"tampered")
                elif target == "plan":
                    path = root / "batch/checkpoint.json"
                    state = json.loads(path.read_text())
                    state["plan"] = state["plan"][:1]
                    path.write_text(json.dumps(state))
                else:
                    with (root / "batch/offsets.sqlite").open("ab") as stream:
                        stream.write(b"tampered")
                with self.assertRaises(ValueError):
                    batch.build_batch(**args, resume=True)

    def test_low_space_before_next_module_preserves_completed_checkpoints(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            first = batch.build_batch(**args)
            with patch("build_library_batch.require_build_space", side_effect=ValueError("Insufficient build disk space")):
                with self.assertRaisesRegex(ValueError, "Insufficient build disk space"):
                    batch.build_batch(**args, resume=True)
            self.assertTrue((root / "batch" / first["modules"][0]["archive"]).exists())
            self.assertFalse((root / "batch/.pending").exists())

    def test_indexing_checks_space_incrementally_and_keeps_input_on_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            with patch("stream_pack._BATCH", 1), patch("build_library_batch.require_build_space", side_effect=[None, None, ValueError("Insufficient build disk space")]):
                with self.assertRaisesRegex(ValueError, "Insufficient build disk space"):
                    batch.build_batch(**args)
            self.assertEqual(["source.jsonl"], sorted(path.name for path in root.iterdir()))

    def test_license_and_global_duplicate_validation_precedes_any_module_commit(self):
        for kind in ("license", "duplicate"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                rows = [dict(document_id="a", title="Topic", source="local", license="CC0-1.0", text="Original text."),
                        dict(document_id="z", title="Topic", source="local", license="CC-BY-SA-4.0", text="Original text.")]
                if kind == "duplicate":
                    rows[1] = rows[0].copy()
                args = self.prepare(root, rows=rows)
                with self.assertRaises((ValueError, DocumentError)):
                    batch.build_batch(**args)
                self.assertFalse((root / "batch").exists())

    def test_whole_article_groups_stay_in_one_module(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rows = [dict(document_id=f"enw-{article}-{section:04d}", title="Topic — Overview", source="local",
                license="CC0-1.0", text="Original section. " * 20) for article in (12, 30) for section in (0, 1)]
            args = self.prepare(root, rows=rows)
            first = batch.build_batch(**args)
            self.assertEqual(2, first["modules"][0]["documents"])
            report = batch.build_batch(**args, resume=True)
            self.assertTrue(report["complete"])
            self.assertEqual([2, 2], [module["documents"] for module in report["modules"]])
            self.assertTrue(all(module["articleBoundariesPreserved"] for module in report["modules"]))

    def test_existing_unknown_directory_and_symlink_are_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            output = root / "batch"
            output.mkdir()
            (output / "keep.txt").write_text("Keep")
            for resume in (False, True):
                with self.assertRaisesRegex(ValueError, "already exists"):
                    batch.build_batch(**args, resume=resume)
            link = root / "linked"
            link.symlink_to(output, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, "symlink"):
                batch.build_batch(**(args | {"output_dir": link}))
            self.assertEqual("Keep", (output / "keep.txt").read_text())

    def test_workspace_lock_prevents_a_second_writer(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            batch.build_batch(**args)
            with (root / "batch/.build.lock").open("a+b") as lock:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                with self.assertRaisesRegex(ValueError, "Another batch builder"):
                    batch.build_batch(**args, resume=True)

    def test_cli_help_is_available_without_python_path_configuration(self):
        tool = Path(__file__).resolve().parents[1] / "build_library_batch.py"
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, str(tool), "--help"], cwd=directory, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("--resume", result.stdout)

    def test_source_changed_during_build_is_not_committed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            build = batch.build_pack_streaming
            def change_source(*positional, **keywords):
                result = build(*positional, **keywords)
                args["input_path"].write_text("Changed source")
                return result
            with patch("build_library_batch.build_pack_streaming", side_effect=change_source):
                with self.assertRaisesRegex(ValueError, "Source or builder changed"):
                    batch.build_batch(**args)
            self.assertEqual([], list((root / "batch/modules").iterdir()))
            self.assertFalse((root / "batch/.pending").exists())

    def test_limit_failure_preserves_the_previous_module(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            measured = batch.build_batch(**(args | {"output_dir": root / "measure"}))
            args["max_installed_bytes"] = measured["installedBytes"]
            first = batch.build_batch(**args)
            with self.assertRaisesRegex(ValueError, "installed library exceeds"):
                batch.build_batch(**args, resume=True)
            self.assertTrue((root / "batch" / first["modules"][0]["archive"]).exists())
            self.assertFalse((root / "batch/.pending").exists())

    def test_failure_after_atomic_commit_can_be_resumed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.prepare(root)
            with patch("build_library_batch._stage_cleanup", side_effect=[None, OSError("cleanup interrupted")]):
                with self.assertRaisesRegex(OSError, "cleanup interrupted"):
                    batch.build_batch(**args)
            self.assertTrue((root / "batch/modules/general-reference-0001/module-build.json").exists())
            self.assertTrue((root / "batch/.pending/owner.txt").exists())
            report = batch.build_batch(**args, resume=True)
            self.assertEqual(2, len(report["modules"]))
            self.assertFalse((root / "batch/.pending").exists())

    def test_symlinked_lock_module_and_unknown_files_are_preserved_not_followed(self):
        for target in ("lock", "module", "unknown"):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                args = self.prepare(root)
                batch.build_batch(**args)
                keep = root / "keep.txt"
                keep.write_text("Keep")
                if target == "lock":
                    lock = root / "batch/.build.lock"
                    lock.unlink()
                    lock.symlink_to(keep)
                elif target == "module":
                    (root / "batch/modules/general-reference-0002").symlink_to(root / "absent", target_is_directory=True)
                else:
                    (root / "batch/modules/user-file.txt").write_text("Keep")
                with self.assertRaises(ValueError):
                    batch.build_batch(**args, resume=True)
                self.assertEqual("Keep", keep.read_text())


if __name__ == "__main__":
    unittest.main()
