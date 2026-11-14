"""Focused invariants for the single owner USB diagnostic bridge."""
import json
import subprocess
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "aicall_tools" / "diagnostics"))
import ai_calls_usb_debug as bridge  # noqa: E402


class UsbDebugProtocolTests(unittest.TestCase):
    def test_adb_control_does_not_consume_private_lab_send_stdin(self):
        completed = subprocess.CompletedProcess(["adb", "devices"], 0, "", "")
        with patch.object(bridge.subprocess, "run", return_value=completed) as fake:
            result = bridge.invoke(["adb", "devices"])
        self.assertEqual(result.returncode, 0)
        self.assertEqual(fake.call_args.kwargs["stdin"], subprocess.DEVNULL)

    def test_audio_quality_metrics_exclude_transcripts_and_unknown_fields(self):
        output = "\n".join([
            "INSTRUMENTATION_STATUS: stream=AUDIO_QA_GEMMA_CASE_1_WORD_ERRORS=2",
            "AUDIO_QA_PARAKEET_CASE_3_INIT_MS=1490",
            "AUDIO_QA_GEMMA_CASE_1_PASSED=0",
            "AUDIO_QA_GEMMA_CASE_1_PREWARM_OVERLAPPED_TTS=1",
            "AUDIO_QA_GEMMA_CASE_1_TRANSCRIPT=private text",
            "AUDIO_QA_PHONE=123456789",
            "AUDIO_QA_GEMMA_CASE_4_WORD_ERRORS=1",
            "AUDIO_QA_GEMMA_CASE_1_REF_WORDS=invalid",
        ])
        self.assertEqual(bridge.parse_audio_quality_metrics(output), {
            "GEMMA_CASE_1_WORD_ERRORS": 2,
            "PARAKEET_CASE_3_INIT_MS": 1490,
            "GEMMA_CASE_1_PASSED": 0,
            "GEMMA_CASE_1_PREWARM_OVERLAPPED_TTS": 1,
        })

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



class UsbDeviceDiscoveryFailures(unittest.TestCase):
    def test_unplugged_and_unauthorized_are_actionable_not_guessed(self):
        for status, expected in [
            ("List of devices attached\n", "adb_no_connected_device_check_usb"),
            ("List of devices attached\nusb1 unauthorized\n", "adb_authorization_required"),
            ("List of devices attached\nusb1 offline\n", "adb_device_offline"),
        ]:
            fake = subprocess.CompletedProcess(["adb", "devices"], 0, status, "")
            with patch.object(bridge, "invoke", return_value=fake):
                with self.assertRaises(bridge.DiagnosticError) as error:
                    bridge.Device("adb")
            self.assertEqual(str(error.exception), expected)

    def test_does_not_choose_a_random_device_when_two_match(self):
        def execute(argv, timeout=30):
            if argv[-2:] == ["devices", "-l"]:
                return subprocess.CompletedProcess(argv, 0,
                    "List of devices attached\nusb1 device\nusb2 device\n", "")
            return subprocess.CompletedProcess(argv, 0,
                bridge.MODEL + "\n" if argv[-1] == "ro.product.model"
                else bridge.SERIAL + "\n", "")
        with patch.object(bridge, "invoke", side_effect=execute):
            with self.assertRaises(bridge.DiagnosticError) as error:
                bridge.Device("adb")
        self.assertEqual(str(error.exception), "exact_s22_not_uniquely_available")

if __name__ == "__main__":
    unittest.main()
