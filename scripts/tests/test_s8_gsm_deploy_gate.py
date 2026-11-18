"""Regression gate: a test APK install must never strand S8 GSM without its ADB host."""
from pathlib import Path
from unittest import TestCase

ROOT = Path(__file__).resolve().parents[2]


class S8GsmDeployGateTest(TestCase):
    def test_install_routes_to_verified_binder_host_after_apk_replace(self):
        script = (ROOT / "scripts/bin/install-s8-gsm-lab.sh").read_text()
        install = 'adb -s "$ANDROID_SERIAL" install -r "$apk"'
        bootstrap = 'bash "$root/scripts/bin/start-s8-gsm-shell.sh" --start'
        self.assertIn(install, script)
        self.assertIn(bootstrap, script)
        self.assertLess(script.index(install), script.index(bootstrap))
        self.assertIn("host_binder_uid_verified=true", script)
        self.assertIn("probe_complete=true", script)
        self.assertIn("MODE_NORMAL", script)
        self.assertIn("SM-S906B", script)
        self.assertNotIn("pm uninstall", script)
        self.assertNotIn("force-stop", script)

    def test_one_shot_bootstrap_cannot_kill_active_call_or_duplicate_host(self):
        script = (ROOT / "scripts/bin/start-s8-gsm-shell.sh").read_text()
        self.assertIn("MODE_NORMAL", script)
        self.assertIn("SM-S906B", script)
        self.assertIn('host_registered=true', script)
        self.assertIn('S8_GSM_BINDER_HOST_ALREADY_REGISTERED=true', script)
        self.assertIn('S8_GSM_BINDER_HOST_READY=true', script)
        self.assertNotIn("Shizuku.requestPermission", script)
