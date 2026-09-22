from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
PROHIBITED = (
    "thinking",
    "magical",
    "superhuman",
    "genius",
    "unlock",
    "revolutionize",
    "seamless",
    "effortless",
    "confidence score",
)


def product_copy() -> str:
    ui_root = ROOT / "app/src/main/java/xyz/fieldatlas/ui"
    kotlin_literals = []
    for path in sorted(ui_root.rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        kotlin_literals.extend(re.findall(r'"(?:\\.|[^"\\])*"', source))
    resources = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
    return "\n".join(kotlin_literals) + "\n" + resources


class ProductCopyTest(unittest.TestCase):
    def test_product_copy_has_no_hype_or_routine_exclamation_marks(self):
        text = product_copy().casefold()
        for phrase in PROHIBITED:
            self.assertNotIn(phrase, text)
        self.assertNotIn("!", text)

    def test_navigation_resources_use_research_library_and_more(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
        self.assertIn('name="nav_research"', strings)
        self.assertIn('name="nav_library"', strings)
        self.assertIn('name="nav_more"', strings)
        self.assertNotIn('name="nav_proof"', strings)
        self.assertNotIn('name="nav_diagnostics"', strings)

    def test_public_android_name_has_the_human_readable_space(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
        self.assertIn('<string name="app_name">Field Atlas</string>', strings)
        self.assertNotIn('<string name="app_name">FieldAtlas</string>', strings)

    def test_public_candidate_has_release_version(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("versionCode = 3", gradle)
        self.assertIn('versionName = "1.1.0"', gradle)

    def test_readme_uses_approved_identity_without_hype(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertTrue(readme.startswith("# Field Atlas\n"))
        self.assertIn("Offline Research for Android", readme)
        self.assertIn("Answers grounded in what you carry.", readme)
        self.assertNotIn("AI-powered", readme.casefold())

    def test_public_markdown_uses_spaced_field_atlas_name(self):
        markdown = [
            path
            for path in ROOT.rglob("*.md")
            if not any(
                part in {".git", ".gradle", ".superpowers", ".worktrees", "build", "third_party"}
                for part in path.relative_to(ROOT).parts
            )
        ]
        offenders = [str(path.relative_to(ROOT)) for path in markdown if "FieldAtlas" in path.read_text()]
        self.assertEqual([], offenders)

    def test_fixture_sources_use_spaced_field_atlas_name(self):
        fixture = (ROOT / "fixtures/starter/documents.jsonl").read_text()
        self.assertIn('"source":"Field Atlas project-authored fixture"', fixture)
        self.assertNotIn('"source":"FieldAtlas', fixture)

    def test_public_tool_copy_uses_spaced_field_atlas_name(self):
        scripts = (
            ROOT / "scripts/build_starter_pack.sh",
            ROOT / "scripts/install_toolchain.sh",
            ROOT / "scripts/android_sdk.py",
            ROOT / "scripts/model_manifest.py",
        )
        offenders = [
            str(path.relative_to(ROOT))
            for path in scripts
            if "FieldAtlas" in path.read_text(encoding="utf-8")
        ]
        self.assertEqual([], offenders)

    def test_release_docs_do_not_contain_local_credentials(self):
        text = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (ROOT / "docs").glob("*.md")
        )
        self.assertNotIn("/Users/", text)
        self.assertNotIn("poidh-bounty31-android-signing", text)

    def test_public_tree_excludes_internal_plans_and_empty_evidence_root(self):
        self.assertFalse((ROOT / "docs/superpowers").exists())
        self.assertFalse((ROOT / "evidence").exists())
        self.assertTrue((ROOT / "docs/evidence").is_dir())

    def test_generated_evaluation_outputs_use_ignored_build_tree(self):
        evaluation = (ROOT / "docs/evaluation.md").read_text(encoding="utf-8")
        self.assertIn("build/evidence/", evaluation)
        self.assertNotIn(" evidence/", evaluation)


if __name__ == "__main__":
    unittest.main()
