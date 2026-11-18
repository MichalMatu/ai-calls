"""Non-calling regressions for event-triggered S8 GSM host recovery on macOS."""
import importlib.util
from pathlib import Path
from unittest import TestCase
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/bin/s8_gsm_reconnect.py"
spec = importlib.util.spec_from_file_location("s8_gsm_reconnect", SCRIPT)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class S8GsmReconnectTest(TestCase):
    def test_native_adb_event_waits_pin_exact_serial(self):
        child = __import__("unittest.mock", fromlist=["MagicMock"]).MagicMock()
        child.wait.return_value = 0
        with patch.object(mod.subprocess, "Popen", return_value=child) as factory:
            self.assertTrue(mod.wait_for_adb_state("/usr/bin/adb", "TEST_S22", "device"))
            self.assertTrue(mod.wait_for_adb_state("/usr/bin/adb", "TEST_S22", "disconnect"))
            self.assertEqual(["/usr/bin/adb", "-s", "TEST_S22", "wait-for-device"],
                             factory.call_args_list[0].args[0])
            self.assertEqual(["/usr/bin/adb", "-s", "TEST_S22", "wait-for-disconnect"],
                             factory.call_args_list[1].args[0])
            self.assertIsNone(mod._active_adb)

    def test_signal_cleanup_terminates_adb_child(self):
        child = __import__("unittest.mock", fromlist=["MagicMock"]).MagicMock()
        child.poll.return_value = None
        with patch.object(mod, "_active_adb", child):
            with self.assertRaises(SystemExit):
                mod.stop_watcher(15, None)
            child.terminate.assert_called_once()
            child.wait.assert_called_once()

    def test_ready_host_does_not_restart_an_existing_privileged_process(self):
        def fake_shell(_adb, _serial, *args, **_kwargs):
            key = " ".join(args)
            if "sys.boot_completed" in key:
                return "1"
            if "ro.product.model" in key:
                return mod.MODEL
            if "pm path" in key:
                return "package:/data/app/test/base.apk"
            if "dumpsys telephony.registry" in key:
                return "mCallState=0"
            if "dumpsys audio" in key:
                return "- Actual mode = MODE_NORMAL"
            return ""
        with patch.object(mod, "shell", side_effect=fake_shell), \
             patch.object(mod, "host_registered", return_value=True), \
             patch.object(mod.subprocess, "run") as runner:
            self.assertTrue(mod.recover("/usr/bin/adb", "TEST_S22_SERIAL", max_boot_checks=1))
            runner.assert_not_called()

    def test_busy_call_never_starts_helper(self):
        def fake_shell(_adb, _serial, *args, **_kwargs):
            key = " ".join(args)
            if "sys.boot_completed" in key:
                return "1"
            if "ro.product.model" in key:
                return mod.MODEL
            if "pm path" in key:
                return "package:/data/app/test/base.apk"
            if "dumpsys telephony.registry" in key:
                return "mCallState=2"
            if "dumpsys audio" in key:
                return "- Actual mode = MODE_IN_CALL"
            return ""
        with patch.object(mod, "shell", side_effect=fake_shell), \
             patch.object(mod, "host_registered") as registered, \
             patch.object(mod.subprocess, "run") as runner:
            self.assertFalse(mod.recover("/usr/bin/adb", "TEST_S22_SERIAL", max_boot_checks=1))
            registered.assert_not_called()
            runner.assert_not_called()

    def test_no_root_shizuku_scheduler_or_autodial_code(self):
        text = SCRIPT.read_text()
        for forbidden in ("Shizuku.requestPermission", "su -c", "pm install", "adb reboot",
                          "crontab", "scheduleAtFixedRate", "placeCall("):
            self.assertNotIn(forbidden, text)
        self.assertIn('"wait-for-" + state', text)
        self.assertIn('"RunAtLoad": True', text)
        self.assertIn('"KeepAlive": True', text)
        self.assertIn("BOOTSTRAP", text)
