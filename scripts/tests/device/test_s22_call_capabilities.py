import subprocess
import unittest
from unittest import mock

from aicall_tools.device.s22_call_capabilities import (
    APP_PACKAGE, inspect_phone, parse_call_state, parse_role_holders, main,
)

SERIAL = "192.168.0.100:41703"


def outcome(output, rc=0):
    return subprocess.CompletedProcess([], rc, output, "")


class FakeAdb:
    def __init__(self, *, sdk="36", roles="com.samsung.android.dialer\n",
                 app="package:/data/app/ai-calls/base.apk\n", call="mCallState=0",
                 ps="shell 412 1 S shizuku_server\n"):
        self.entries = {
            ("getprop", "ro.build.version.sdk"): outcome(sdk),
            ("cmd", "role", "get-role-holders", "android.app.role.DIALER"): outcome(roles),
            ("cmd", "telecom", "get-default-dialer"): outcome(
                roles.splitlines()[0] + "\\n" if roles.splitlines() else ""
            ),
            ("pm", "path", APP_PACKAGE): outcome(app),
            ("dumpsys", "telephony.registry"): outcome(call),
            ("ps", "-A"): outcome(ps),
        }
        self.calls = []

    def __call__(self, args):
        self.calls.append(tuple(args))
        assert args[:4] == ["adb", "-s", SERIAL, "shell"]
        return self.entries[tuple(args[4:])]


class CallCapabilityTests(unittest.TestCase):
    def test_default_dialer_is_not_call_ownership(self):
        fake = FakeAdb(roles=APP_PACKAGE + "\n")
        result = inspect_phone(SERIAL, runner=fake)
        self.assertTrue(result["app_is_default_dialer"])
        self.assertFalse(result["exact_call_controller_proven"])
        self.assertFalse(result["autonomous_dial_authorized"])

    def test_shizuku_running_is_not_call_ownership(self):
        result = inspect_phone(SERIAL, runner=FakeAdb())
        self.assertTrue(result["shizuku_process_visible"])
        self.assertFalse(result["exact_call_controller_proven"])

    def test_stock_dialer_stays_unchanged(self):
        result = inspect_phone(SERIAL, runner=FakeAdb())
        self.assertEqual("com.samsung.android.dialer", result["default_dialer"])
        self.assertFalse(result["app_is_default_dialer"])

    def test_no_mutating_adb_command_is_sent(self):
        fake = FakeAdb()
        inspect_phone(SERIAL, runner=fake)
        self.assertEqual(6, len(fake.calls))
        self.assertEqual({("getprop", "ro.build.version.sdk"),
                          ("cmd", "role", "get-role-holders", "android.app.role.DIALER"),
                          ("cmd", "telecom", "get-default-dialer"),
                          ("pm", "path", APP_PACKAGE),
                          ("dumpsys", "telephony.registry"),
                          ("ps", "-A")},
                         {x[4:] for x in fake.calls})

    def test_unavailable_role_fails_closed(self):
        fake = FakeAdb(roles=APP_PACKAGE + "\n")
        fake.entries[("cmd", "role", "get-role-holders", "android.app.role.DIALER")] = outcome("", 1)
        result = inspect_phone(SERIAL, runner=fake)
        self.assertEqual(APP_PACKAGE, result["default_dialer"])
        self.assertTrue(result["app_is_default_dialer"])
        self.assertFalse(result["exact_call_controller_proven"])

    def test_role_telecom_disagreement_fails_closed(self):
        fake = FakeAdb(roles=APP_PACKAGE + "\\n")
        fake.entries[("cmd", "telecom", "get-default-dialer")] = outcome("com.google.android.dialer\\n")
        result = inspect_phone(SERIAL, runner=fake)
        self.assertEqual("UNKNOWN", result["default_dialer"])
        self.assertFalse(result["app_is_default_dialer"])

    def test_failed_both_queries_fail_closed(self):
        fake = FakeAdb()
        fake.entries[("cmd", "role", "get-role-holders", "android.app.role.DIALER")] = outcome("", 255)
        fake.entries[("cmd", "telecom", "get-default-dialer")] = outcome("", 255)
        result = inspect_phone(SERIAL, runner=fake)
        self.assertEqual("UNKNOWN", result["default_dialer"])

    def test_multiple_role_holders_are_ambiguous(self):
        result = inspect_phone(SERIAL, runner=FakeAdb(roles=APP_PACKAGE + "\nother.app\n"))
        self.assertEqual("UNKNOWN", result["default_dialer"])
        self.assertFalse(result["app_is_default_dialer"])

    def test_malformed_sdk_is_not_assumed(self):
        result = inspect_phone(SERIAL, runner=FakeAdb(sdk="unknown"))
        self.assertIsNone(result["sdk"])

    def test_not_installed_fails_closed(self):
        result = inspect_phone(SERIAL, runner=FakeAdb(app="", ps=""))
        self.assertFalse(result["ai_calls_installed"])
        self.assertFalse(result["shizuku_process_visible"])

    def test_call_state_parser(self):
        self.assertEqual("IDLE", parse_call_state("mCallState=0"))
        self.assertEqual("OFFHOOK_OR_RINGING", parse_call_state(" mCallState=2 "))
        self.assertEqual("OFFHOOK_OR_RINGING", parse_call_state(" mCallState=1 "))
        self.assertEqual("UNKNOWN", parse_call_state("mCallState=0\nmCallState=2"))
        self.assertEqual("UNKNOWN", parse_call_state("mCallState=?"))
        self.assertEqual("UNKNOWN", parse_call_state(""))

    def test_role_holder_parser_ignores_errors_and_duplicates(self):
        self.assertEqual(["com.samsung.android.dialer"],
                         parse_role_holders("Role holders:\ncom.samsung.android.dialer\n"
                                            "com.samsung.android.dialer\nError: other\n"))

    def test_serial_argument_cannot_inject_shell(self):
        fake = FakeAdb()
        with self.assertRaises(ValueError):
            inspect_phone("abc shell rm", runner=fake)
        self.assertEqual([], fake.calls)

    @mock.patch("aicall_tools.device.s22_call_capabilities.inspect_phone")
    @mock.patch("aicall_tools.device.s22_call_capabilities.resolve_s22_serial")
    def test_cli_no_call_and_unknown_state_nonzero(self, resolver, inspect):
        resolver.return_value = SERIAL
        inspect.return_value = {"call_state": "UNKNOWN"}
        self.assertEqual(3, main([]))
        resolver.assert_called_once_with(None)


if __name__ == "__main__":
    unittest.main()
