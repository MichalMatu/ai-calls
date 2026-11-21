from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]

class SafeS22LabTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.sh=(ROOT/"scripts/bin/run-s22-safe-ui-lab.sh").read_text()
    def test_requires_explicit_approval_and_exact_s22(self):
        for token in ('--approved-idle','ANDROID_SERIAL','SM-S906B','get-state'):
            self.assertIn(token,self.sh)
    def test_guards_idle_and_binder_pre_and_post(self):
        for token in ('mCallState=','MODE_NORMAL','host_registered=true',
                      'phone_idle ||','host_ready ||','trap postflight EXIT'):
            self.assertIn(token,self.sh)
    def test_recovery_after_idle_not_during_call(self):
        i=self.sh.index('if ! phone_idle; then',self.sh.index('postflight()'))
        j=self.sh.index('start-s8-gsm-shell.sh --start',i)
        self.assertLess(i,j)
    def test_safe_ui_and_storage_only(self):
        for name in ('AndroidAppBottomNavigationFlowTest','AndroidUiPolishContractTest',
                     'AndroidLabDialogStorageAcceptanceTest'):
            self.assertIn(name,self.sh)
        for forbidden in ('ACTION_CALL','KEYCODE_CALL','am start','pm clear','uninstall','reboot'):
            self.assertNotIn(forbidden,self.sh)
    def test_sha_and_nonempty_results(self):
        for token in ('shasum -a 256','instrumentation_result.py',
                      '--expected-count 9','S22_SAFE_UI_LAB_ACCEPTANCE_PASS=true'):
            self.assertIn(token,self.sh)
if __name__=="__main__":unittest.main()
