import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
spec = importlib.util.spec_from_file_location('android_research_pilot', Path(__file__).resolve().parents[1] / 'run_android_research_pilot.py')
tool = importlib.util.module_from_spec(spec); spec.loader.exec_module(tool)


class AndroidPilotTests(unittest.TestCase):
    def test_scratch_directory_validation_rejects_broad_or_injected_targets(self):
        self.assertEqual('/data/local/tmp/fieldatlas-retrieval.Abcd1234', tool.checked_remote('/data/local/tmp/fieldatlas-retrieval.Abcd1234'))
        for path in ['/', '/data/local/tmp', '/data/local/tmp/fieldatlas-retrieval.x/..', '/data/local/tmp/fieldatlas-retrieval.x;rm', '/data/local/tmp/fieldatlas-retrieval.']:
            with self.assertRaises(ValueError): tool.checked_remote(path)

    def test_only_pilot_and_adapter_classes_are_dexed_not_junit_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            names = ['AndroidResearchPilot.class', 'AndroidResearchPilot$run$1.class', 'DesktopLeviathanRetriever.class',
                     'DesktopLeviathanRetrieverKt.class', 'DesktopLeviathanRetrieverTest.class', 'DesktopResearchTest.class']
            for name in names: (root / name).touch()
            self.assertEqual(sorted(names[:4]), sorted(p.name for p in tool.class_files(root)))

    def test_missing_compiled_pilot_is_explicit(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'Compile'): tool.class_files(Path(directory))

    def test_rejects_host_executable_and_non_arm_elf(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / 'binary'
            for header in [b'not an ELF', b'\x7fELF\x02\x01' + bytes(12) + (62).to_bytes(2, 'little')]:
                binary.write_bytes(header)
                with self.assertRaises(ValueError): tool.validate_arm_binary(binary)
            binary.write_bytes(b'\x7fELF\x02\x01' + bytes(12) + (183).to_bytes(2, 'little'))
            tool.validate_arm_binary(binary)

    def test_cleanup_only_stops_owned_exact_pilot_and_index_child(self):
        root = '/data/local/tmp/fieldatlas-retrieval.Abcd1234'
        shell = Mock(return_value='PID ARGS\n10 xyz.fieldatlas\n11 xyz.fieldatlas.desktop.AndroidResearchPilot /elsewhere/config\n'
            f'12 xyz.fieldatlas.desktop.AndroidResearchPilot {root}/config.x.json\n13 {root}/binary search --index {root}/index\n')
        tool.stop_owned_processes(shell, root, root + '/config.x.json')
        self.assertEqual([('kill', '-KILL', '12'), ('kill', '-KILL', '13')], [call.args for call in shell.call_args_list[1:]])
