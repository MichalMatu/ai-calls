"""Non-calling regressions for event-triggered S8 GSM host recovery on macOS."""
import importlib.util
from pathlib import Path
from unittest import TestCase
from unittest.mock import patch
from subprocess import CompletedProcess

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/bin/s8_gsm_reconnect.py"
spec = importlib.util.spec_from_file_location("s8_gsm_reconnect", SCRIPT)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class S8GsmReconnectTest(TestCase):
    def test_pinned_serial_and_device_state_transitions(self):
        serial = "TEST_S22_SERIAL"
        self.assertTrue(mod.newly_online({}, {serial: "device"}, serial))
        self.assertTrue(mod.newly_online({serial: "offline"}, {serial: "device"}, serial))
        self.assertFalse(mod.newly_online({serial: "device"}, {serial: "device"}, serial))
        self.assertFalse(mod.newly_online({}, {"OTHER": "device"}, serial))
        self.assertFalse(mod.newly_online({}, {serial: "unauthorized"}, serial))

    def test_track_devices_frame_parsing(self):
        frame = ["List of devices attached\n", "TEST_S22_SERIAL\tdevice product:test\n",
                 "OTHER\toffline\n", "noise\n"]
        self.assertEqual({"TEST_S22_SERIAL": "device", "OTHER": "offline"},
                         mod.parse_snapshot(frame))

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
        for forbidden in ("Shizuku", "su -c", "pm install", "adb reboot",
                          "cron", "scheduleAtFixedRate", "placeCall("):
            self.assertNotIn(forbidden, text)
        self.assertIn('"track-devices"', text)
        self.assertIn('"RunAtLoad": True', text)
        self.assertIn('"KeepAlive": True', text)
        self.assertIn("BOOTSTRAP", text)
