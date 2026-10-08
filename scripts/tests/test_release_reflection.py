import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "verify_release_reflection.py"
SPEC = importlib.util.spec_from_file_location("release_reflection", SCRIPT)
reflection = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reflection)


class ReleaseReflectionTest(unittest.TestCase):
    def verify_dump(self, dump):
        actual_run = subprocess.run

        def emit_dump(command, **kwargs):
            return actual_run(
                [sys.executable, "-c", f"import sys; sys.stdout.buffer.write({dump!r})"],
                **kwargs,
            )

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk, mapping = root / "test.apk", root / "mapping.txt"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", b"fixture")
            mapping.write_text(f"{reflection.REFLECTIVE_CLASS} -> a.b:\n", encoding="utf-8")
            with patch.object(reflection.subprocess, "run", side_effect=emit_dump):
                reflection.verify(apk, mapping, root / "dexdump")

    def test_modified_utf8_literal_does_not_break_ascii_metadata_audit(self):
        self.verify_dump(
            b"unrelated literal: \xc0\x80\nClass #0\nClass descriptor  : 'La/b;'\n"
            b"name          : '<init>'\nClass #1\n"
        )

    def test_replacement_decoding_still_rejects_abstract_class(self):
        with self.assertRaisesRegex(RuntimeError, "became abstract"):
            self.verify_dump(b"Class descriptor  : 'La/b;'\nABSTRACT\nname          : '<init>'\n")

    def test_replacement_decoding_still_rejects_missing_constructor(self):
        with self.assertRaisesRegex(RuntimeError, "lost its constructor"):
            self.verify_dump(b"Class descriptor  : 'La/b;'\nClass #1\n")


if __name__ == "__main__":
    unittest.main()
