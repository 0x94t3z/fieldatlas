import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).parents[1] / "model_manifest.py"
SPEC = importlib.util.spec_from_file_location("model_manifest", SCRIPT)
model_manifest = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(model_manifest)


class ModelManifestTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.model = self.root / "tiny.gguf"
        self.model.write_bytes(b"GGUF deterministic fixture")
        self.config = self.root / "model.json"
        self.config.write_text(
            json.dumps(
                {
                    "converter": "pinned converter",
                    "expectedSha256": hashlib.sha256(self.model.read_bytes()).hexdigest(),
                    "id": "test-model",
                    "license": "Apache-2.0",
                    "llamaCppCommit": "a" * 40,
                    "profile": "test only",
                    "quantizer": "Q4_K_M",
                    "sourceUrls": ["https://example.test/model"],
                    "title": "Test model",
                    "upstreamModel": "example/test",
                    "version": "1.0.0",
                }
            ),
            encoding="utf-8",
        )

    def tearDown(self):
        self.temporary.cleanup()

    def test_two_builds_are_identical_and_importer_compatible(self):
        first = self.root / "first.fapack"
        second = self.root / "second.fapack"
        manifest = model_manifest.build_pack(self.model, self.config, first)
        model_manifest.build_pack(self.model, self.config, second)

        self.assertEqual(first.read_bytes(), second.read_bytes())
        self.assertEqual("MODEL", manifest["type"])
        with zipfile.ZipFile(first) as archive:
            entries = archive.infolist()
            self.assertEqual(["manifest.json", "model.gguf", "provenance.json"], [entry.filename for entry in entries])
            self.assertTrue(all(entry.compress_type == zipfile.ZIP_STORED for entry in entries))
            self.assertTrue(all(entry.date_time == (1980, 1, 1, 0, 0, 0) for entry in entries))
            archived_manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual(manifest, archived_manifest)

    def test_unknown_config_fields_are_rejected(self):
        config = json.loads(self.config.read_text(encoding="utf-8"))
        config["unexpected"] = True
        self.config.write_text(json.dumps(config), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "unknown=\\['unexpected'\\]"):
            model_manifest.build_pack(self.model, self.config, self.root / "bad.fapack")

    def test_empty_model_is_rejected_without_output(self):
        self.model.write_bytes(b"")
        output = self.root / "empty.fapack"
        with self.assertRaisesRegex(ValueError, "must not be empty"):
            model_manifest.build_pack(self.model, self.config, output)
        self.assertFalse(output.exists())

    def test_wrong_expected_hash_is_rejected(self):
        config = json.loads(self.config.read_text(encoding="utf-8"))
        config["expectedSha256"] = "0" * 64
        self.config.write_text(json.dumps(config), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            model_manifest.build_pack(self.model, self.config, self.root / "wrong.fapack")

    def test_unsafe_identifier_is_rejected(self):
        config = json.loads(self.config.read_text(encoding="utf-8"))
        config["id"] = "../escape"
        self.config.write_text(json.dumps(config), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "safe pack identifier"):
            model_manifest.build_pack(self.model, self.config, self.root / "unsafe.fapack")

    def test_duplicate_config_fields_are_rejected(self):
        original = self.config.read_text(encoding="utf-8").rstrip()
        self.config.write_text(original[:-1] + ', "id": "shadowed-model"}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "duplicate JSON field: id"):
            model_manifest.build_pack(self.model, self.config, self.root / "duplicate.fapack")


if __name__ == "__main__":
    unittest.main()
