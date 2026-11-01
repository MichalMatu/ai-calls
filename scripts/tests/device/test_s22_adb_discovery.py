import subprocess
import unittest
from unittest import mock

from aicall_tools.device.s22_adb_discovery import (
    mdns_connect_endpoints, resolve_s22_serial,
)

USB = "RFCT70L7E8J"
WIFI = "192.168.0.100:46769"
DEVICES_BOTH = (
    "List of devices attached\n"
    f"{USB} device model:SM_S906B product:g0sxeea\n"
    f"{WIFI} device product:g0sxeea model:SM_S906B\n"
)
DEVICES_NONE = "List of devices attached\n"
DEVICES_WIFI = "List of devices attached\n" + WIFI + " device model:SM_S906B\n"


def result(text="", rc=0):
    return subprocess.CompletedProcess([], rc, text, "")


class FakeRunner:
    def __init__(self, devices, *, after_connect=None, model="SM-S906B", hw=USB):
        self.devices = devices
        self.after_connect = after_connect
        self.model = model
        self.hw = hw
        self.calls = []

    def __call__(self, argv):
        self.calls.append(argv)
        if argv == ["adb", "start-server"]:
            return result()
        if argv == ["adb", "devices", "-l"]:
            return result(self.devices)
        if argv == ["adb", "mdns", "services"]:
            return result(
                "List of discovered mdns services\n"
                f"adb-device\t_adb-tls-connect._tcp\t{WIFI}\n"
            )
        if argv[:2] == ["adb", "connect"]:
            if self.after_connect is not None:
                self.devices = self.after_connect
            return result("connected")
        if argv[-2:] == ["getprop", "ro.product.model"]:
            return result(self.model + "\n")
        if argv[-2:] == ["getprop", "ro.serialno"]:
            return result(self.hw + "\n")
        raise AssertionError(argv)


class S22AdbDiscoveryTest(unittest.TestCase):
    def test_accepts_only_private_tls_adb_endpoints(self):
        raw = (
            "_adb-tls-connect._tcp 192.168.0.100:46769\n"
            "_adb-tls-connect._tcp 192.168.0.100:46769\n"
            "_adb-tls-pairing._tcp 192.168.0.101:3000\n"
            "_adb-tls-connect._tcp example.com:1234\n"
            "_adb-tls-connect._tcp 8.8.8.8:5555\n"
            "_adb-tls-connect._tcp 127.0.0.1:5555\n"
            "_adb-tls-connect._tcp 192.168.0.12:99999\n"
        )
        self.assertEqual([WIFI], mdns_connect_endpoints(raw))

    def test_prefers_connected_wireless_over_usb_without_mdns(self):
        runner = FakeRunner(DEVICES_BOTH)
        self.assertEqual(WIFI, resolve_s22_serial(runner=runner))
        self.assertNotIn(["adb", "mdns", "services"], runner.calls)

    def test_reconnects_paired_wireless_from_live_mdns(self):
        runner = FakeRunner(DEVICES_NONE, after_connect=DEVICES_WIFI)
        self.assertEqual(WIFI, resolve_s22_serial(runner=runner))
        self.assertIn(["adb", "connect", WIFI], runner.calls)

    def test_explicit_usb_serial_is_supported(self):
        runner = FakeRunner(DEVICES_BOTH)
        self.assertEqual(USB, resolve_s22_serial(USB, runner=runner))

    def test_explicit_missing_transport_fails_without_usb_fallback(self):
        runner = FakeRunner(DEVICES_BOTH)
        with self.assertRaisesRegex(RuntimeError, "unavailable"):
            resolve_s22_serial("192.168.0.100:9999", runner=runner)

    def test_offline_wireless_never_counts_as_connected(self):
        runner = FakeRunner("List of devices attached\n" + WIFI + " offline model:SM_S906B\n")
        with self.assertRaisesRegex(RuntimeError, "unavailable"):
            resolve_s22_serial(WIFI, runner=runner)

    def test_rejects_wrong_hardware_identity(self):
        runner = FakeRunner(DEVICES_WIFI, hw="WRONG")
        with self.assertRaisesRegex(RuntimeError, "identity_mismatch"):
            resolve_s22_serial(runner=runner)

    def test_rejects_wrong_model_even_when_list_says_s22(self):
        runner = FakeRunner(DEVICES_WIFI, model="SM-G991B")
        with self.assertRaisesRegex(RuntimeError, "identity_mismatch"):
            resolve_s22_serial(runner=runner)

    def test_rejects_ambiguous_wireless_s22_devices(self):
        devices = DEVICES_WIFI + "192.168.0.101:44777 device model:SM_S906B\n"
        runner = FakeRunner(devices)
        with self.assertRaisesRegex(RuntimeError, "multiple"):
            resolve_s22_serial(runner=runner)
        self.assertNotIn(["adb", "mdns", "services"], runner.calls)

    def test_refuses_serial_whitespace_injection(self):
        runner = FakeRunner(DEVICES_WIFI)
        with self.assertRaises(ValueError):
            resolve_s22_serial("device --arg", runner=runner)
        self.assertEqual([], runner.calls)


if __name__ == "__main__":
    unittest.main()
