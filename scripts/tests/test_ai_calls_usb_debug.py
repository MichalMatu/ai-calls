"""Focused invariants for the single owner USB diagnostic bridge."""
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "aicall_tools" / "diagnostics"))
import ai_calls_usb_debug as bridge  # noqa: E402


class UsbDebugProtocolTests(unittest.TestCase):
    def test_actions_are_narrow_and_no_authority_bypass(self):
        for action in ("status", "profile_set", "profile_reset", "grant_revoke",
                       "evidence_status", "evidence_disable", "profile_check"):
            self.assertIn(action, bridge.BRIDGE_ACTIONS)
        for action in ("grant_replace", "disable_stop", "override_permissions",
                       "place_call", "raw_prefs_write"):
            self.assertNotIn(action, bridge.BRIDGE_ACTIONS)
            with self.assertRaises(bridge.DiagnosticError):
                bridge.bridge_extras(action, "0123456789abcdef", {})

    def test_nonce_validation_prevents_stale_result_or_replay(self):
        args = bridge.bridge_extras("status", "0123456789abcdef", {})
        self.assertEqual(args[:3], ["--es", "usb_debug_action", "status"])
        with self.assertRaises(bridge.DiagnosticError):
            bridge.bridge_extras("status", "abc", {})
        ok = {"protocol": 1, "nonce": "0123456789abcdef",
              "action": "status", "ok": True, "code": "ok"}
        self.assertIsNotNone(bridge.validated_result(json.dumps(ok),
                                                      "0123456789abcdef", "status"))
        self.assertIsNone(bridge.validated_result(json.dumps(ok),
                                                 "another-0123456789", "status"))
        self.assertIsNone(bridge.validated_result(json.dumps(ok),
                                                 "0123456789abcdef", "grant_revoke"))

    def test_extras_block_arbitrary_prefs_or_output_paths(self):
        with self.assertRaises(bridge.DiagnosticError):
            bridge.bridge_extras("profile_set", "0123456789abcdef",
                                 {"grant_id": "forged"})
        with self.assertRaises(bridge.DiagnosticError):
            bridge.bridge_extras("status", "0123456789abcdef",
                                 {"file": "/tmp/raw.wav"})

    def test_no_private_data_exposed_on_invalid_result(self):
        self.assertIsNone(bridge.validated_result("{bad", "0123456789abcdef", "status"))
        self.assertIsNone(bridge.validated_result(
            json.dumps({"protocol": 99, "nonce": "0123456789abcdef",
                        "action": "status", "ok": True}),
            "0123456789abcdef", "status"))


if __name__ == "__main__":
    unittest.main()
