import importlib.util
import pathlib
import unittest
from unittest.mock import Mock, patch
import tempfile
import io
import json

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "run_desktop_research.py"
spec = importlib.util.spec_from_file_location("desktop_research", SCRIPT)
desktop = importlib.util.module_from_spec(spec)
spec.loader.exec_module(desktop)


class DesktopResearchTests(unittest.TestCase):
    def test_missing_inputs_fail_before_starting_server(self):
        with self.assertRaisesRegex(ValueError, "Model"):
            desktop.validate_inputs(pathlib.Path("/missing/model.gguf"), [], pathlib.Path("/missing/server"))

    def test_preserves_spaces_and_binds_only_loopback(self):
        command = desktop.server_command(pathlib.Path("/path with spaces/llama-server"), pathlib.Path("/model name.gguf"), 8127)
        self.assertEqual(command[0], "/path with spaces/llama-server")
        self.assertEqual(command[command.index("-m") + 1], "/model name.gguf")
        self.assertEqual(command[command.index("--host") + 1], "127.0.0.1")
        self.assertEqual(command[command.index("--parallel") + 1], "1")

    def run_launcher(self, failure):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory).resolve()
            model, server = root / "model.gguf", root / "llama-server"
            model.touch(); server.touch()
            output = root / "build/report.json"
            process = Mock()
            process.poll.return_value = None
            health = io.BytesIO(b'{}')
            health.status = 200
            props = io.BytesIO(json.dumps({"model_path": str(model)}).encode())
            argv = ["run", "--question", "Explain cells", "--model-only", "--model", str(model),
                    "--server", str(server), "--output", str(output), "--port", "0"]
            replies = RuntimeError("startup failed") if failure == "startup" else [health, props]
            with patch.object(desktop, "ROOT", root), patch("sys.argv", argv), \
                 patch.object(desktop.subprocess, "check_output", return_value="commit"), \
                 patch.object(desktop.subprocess, "Popen", return_value=process), \
                 patch.object(desktop.subprocess, "run", return_value=Mock(returncode=1)), \
                 patch.object(desktop.urllib.request, "urlopen", side_effect=replies):
                if failure == "startup":
                    with self.assertRaisesRegex(RuntimeError, "startup failed"):
                        desktop.main()
                else:
                    self.assertEqual(1, desktop.main())
            process.terminate.assert_called_once()
            process.wait.assert_called_once()
            report = json.loads(output.read_text())
            self.assertEqual("ERROR", report["status"])
            self.assertEqual("commit", report["provenance"]["gitCommit"])

    def test_failed_gradle_marks_report_error_and_stops_owned_server(self):
        self.run_launcher("gradle")

    def test_startup_failure_keeps_provenance_and_stops_owned_server(self):
        self.run_launcher("startup")


if __name__ == "__main__":
    unittest.main()
