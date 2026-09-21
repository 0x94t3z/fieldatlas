from pathlib import Path
import unittest

import tempfile

from scripts.android_sdk import discover_sdk_root, render_local_properties, write_local_properties


class AndroidSdkTest(unittest.TestCase):
    def test_android_home_wins_and_properties_round_trip_special_path(self):
        root = Path("/tmp/Android SDK:stable")
        self.assertEqual(root, discover_sdk_root({"ANDROID_HOME": str(root)}, None))
        self.assertEqual("sdk.dir=/tmp/Android\\ SDK\\:stable\n", render_local_properties(root))

    def test_sdkmanager_infers_parent_of_cmdline_tools(self):
        executable = Path("/opt/android/cmdline-tools/latest/bin/sdkmanager")
        self.assertEqual(Path("/opt/android"), discover_sdk_root({}, executable))

    def test_conflicting_explicit_roots_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "disagree"):
            discover_sdk_root({"ANDROID_HOME": "/a", "ANDROID_SDK_ROOT": "/b"}, None)

    def test_local_properties_is_written_without_temporary_file_left_behind(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "local.properties"
            write_local_properties(Path("/tmp/Android SDK:stable"), destination)
            self.assertEqual("sdk.dir=/tmp/Android\\ SDK\\:stable\n", destination.read_text())
            self.assertEqual([destination], list(Path(directory).iterdir()))


if __name__ == "__main__":
    unittest.main()
