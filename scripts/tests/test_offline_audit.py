import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OfflineAuditTest(unittest.TestCase):
    def audit(self, permission):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            analyzer = root / "cmdline-tools/latest/bin/apkanalyzer"
            analyzer.parent.mkdir(parents=True)
            analyzer.write_text('''#!/bin/sh
case "$1 $2" in
  "manifest print") echo 'android:usesCleartextTraffic="false" android:extractNativeLibs="true"' ;;
  "manifest permissions") printf 'android.permission.INTERNET\\n%s\\n' "$TEST_EXTRA_PERMISSION" ;;
  "dex packages") echo 'xyz.fieldatlas' ;;
  "files list") printf '/lib/arm64-v8a/libai-chat.so\\n/lib/arm64-v8a/libggml-cpu-test.so\\n' ;;
esac
''')
            analyzer.chmod(0o755)
            apk = root / "test.apk"
            apk.write_bytes(b"fixture")
            return subprocess.run(["bash", str(ROOT / "scripts/verify_offline.sh"), str(apk)],
                env={**os.environ, "ANDROID_HOME": str(root), "TEST_EXTRA_PERMISSION": permission},
                text=True, capture_output=True)

    def test_read_only_connectivity_observation_is_allowed(self):
        result = self.audit("android.permission.ACCESS_NETWORK_STATE")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("network behavior still requires device verification", result.stdout)

    def test_connectivity_control_remains_rejected(self):
        for permission in ("CHANGE_NETWORK_STATE", "CHANGE_WIFI_STATE", "WRITE_SETTINGS"):
            with self.subTest(permission=permission):
                result = self.audit("android.permission." + permission)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("control permission", result.stderr)
