from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class CiWorkflowTest(unittest.TestCase):
    def test_android_setup_does_not_request_removed_tools_package(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        setup = re.search(
            r"- uses: android-actions/setup-android@v3\n(?P<inputs>(?:\s{8,}.*\n)*)",
            workflow,
        )
        self.assertIsNotNone(setup)
        inputs = setup.group("inputs")
        self.assertIn("packages: platform-tools", inputs)
        self.assertNotRegex(inputs, r"packages:.*(?:^|\s)tools(?:\s|$)")


if __name__ == "__main__":
    unittest.main()
