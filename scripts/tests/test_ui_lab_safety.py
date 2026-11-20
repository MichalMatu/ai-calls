from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class LabSafetyTests(unittest.TestCase):
    def test_emulator_guard(self):
        s=(ROOT/"scripts/bin/run-ui-lab.sh").read_text()
        for guard in ("^emulator-[0-9]+$","ro.kernel.qemu","UI_LAB_REFUSED=PHYSICAL_DEVICE",
                      "INSTRUMENTATION_CODE: -1","AndroidUiPolishContractTest"):
            self.assertIn(guard,s)
        for risky in ("ACTION_CALL","KEYCODE_CALL","start-s8-gsm-shell"):
            self.assertNotIn(risky,s)
    def test_explicit_profiles(self):
        s=(ROOT/"scripts/bin/test-profile.sh").read_text()
        self.assertIn("lab-ui-prepare)",s)
        self.assertIn("lab-ui)",s)
        self.assertIn("bash scripts/bin/run-ui-lab.sh",s)
if __name__=="__main__":unittest.main()
