import os
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
class LabSafetyTests(unittest.TestCase):
    def test_emulator_guard(self):
        s=(ROOT/"scripts/bin/run-ui-lab.sh").read_text()
        for guard in ("^emulator-[0-9]+$","ro.kernel.qemu","UI_LAB_REFUSED=PHYSICAL_DEVICE",
                      "instrumentation_result.py","AndroidUiPolishContractTest"):
            self.assertIn(guard,s)
        for risky in ("ACTION_CALL","KEYCODE_CALL","start-s8-gsm-shell"):
            self.assertNotIn(risky,s)
    def test_explicit_profiles(self):
        s=(ROOT/"scripts/bin/test-profile.sh").read_text()
        self.assertIn("lab-ui-prepare)",s)
        self.assertIn("lab-ui)",s)
        self.assertIn("bash scripts/bin/run-ui-lab.sh",s)

    def test_runner_refuses_non_emulators_before_gradle_or_install(self):
        for serial, expected in (
            ("PHYSICAL_TEST_DEVICE", "PHYSICAL_DEVICE"),
            ("emulator-5432", "NOT_EMULATED"),
        ):
            with self.subTest(serial=serial), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                adb = root / "adb"
                adb.write_text(
                    '#!/bin/sh\n'
                    'case "$3" in\n'
                    '  get-state) echo device ;;\n'
                    '  shell) [ "$4 $5" = "getprop ro.kernel.qemu" ] || exit 42; echo 0 ;;\n'
                    '  *) exit 42 ;;\n'
                    'esac\n'
                )
                adb.chmod(0o700)
                result = subprocess.run(
                    ["bash", str(ROOT / "scripts/bin/run-ui-lab.sh")],
                    env={**os.environ, "PATH": f"{temp}{os.pathsep}{os.environ['PATH']}",
                         "ANDROID_SERIAL": serial, "GRADLE_BIN": str(root / "must-not-run")},
                    check=False, capture_output=True, text=True, timeout=5,
                )
                self.assertEqual(5, result.returncode, result.stderr)
                self.assertIn(f"UI_LAB_REFUSED={expected}", result.stdout)
if __name__=="__main__":unittest.main()
