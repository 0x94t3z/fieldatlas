from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ReleaseSigningTest(unittest.TestCase):
    def test_release_signing_is_environment_only_and_all_or_nothing(self):
        source = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        for name in (
            "FIELDATLAS_KEYSTORE_PATH",
            "FIELDATLAS_STORE_PASSWORD",
            "FIELDATLAS_KEY_ALIAS",
            "FIELDATLAS_KEY_PASSWORD",
        ):
            self.assertIn(name, source)
        self.assertIn("Incomplete Field Atlas release signing configuration", source)
        self.assertNotIn("/Users/", source)
        self.assertNotIn("poidh-bounty31-android-signing", source)

    def test_private_signing_file_patterns_remain_ignored(self):
        ignores = (ROOT / ".gitignore").read_text(encoding="utf-8")
        self.assertIn("*.keystore", ignores)
        self.assertIn("*.jks", ignores)
        self.assertIn("keystore.properties", ignores)

    def test_unsigned_release_is_audited_but_never_presented_as_installable(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        installation = (ROOT / "docs/installation.md").read_text(encoding="utf-8")
        unsigned = "app/build/outputs/apk/release/app-release-unsigned.apk"
        self.assertIn(f"./scripts/verify_offline.sh {unsigned}", workflow)
        self.assertIn(unsigned, workflow)
        self.assertIn("unsigned release artifact exists only for reproducible inspection", installation)
        self.assertIn("never present it as installable", installation)


if __name__ == "__main__":
    unittest.main()
