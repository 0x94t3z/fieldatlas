"""Keep reorganized command entry points usable outside the repository."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ToolLayoutTests(unittest.TestCase):
    def test_commands_support_help_from_another_directory(self):
        commands = (
            "tools/packs/build_dual_search_pack.py",
            "tools/android/build_leviathan_native.py",
            "tools/evaluation/compare_research_runs.py",
            "tools/maintenance/plan_library_storage.py",
            "scripts/research/check_claim_support.py",
        )
        with tempfile.TemporaryDirectory() as cwd:
            for command in commands:
                with self.subTest(command=command):
                    result = subprocess.run(
                        [sys.executable, str(ROOT / command), "--help"],
                        cwd=cwd, capture_output=True, text=True, timeout=30,
                    )
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertIn("usage:", result.stdout.lower())

    def test_native_instrumentation_assets_exist(self):
        for name in ("AndroidManifest.xml", "NativeSmokeInstrumentation.java"):
            self.assertTrue((ROOT / "tools/android/native_smoke" / name).is_file())


if __name__ == "__main__":
    unittest.main()
