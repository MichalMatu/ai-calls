"""No-device contract tests for one-shot supervised readiness collection."""

import subprocess
import unittest
from unittest.mock import patch

from aicall_tools.device import s22_supervised_readiness as diagnostics


def report(audio="MODE_IN_COMMUNICATION", failure="audio_route_not_ready"):
    values = {
        "probe": "supervised_call_readiness",
        "microphone_permission": "true",
        "phone_state_permission": "true",
        "shizuku_ready": "true",
        "telecom_in_call": "true",
        "telecom_managed_call": "true",
        "audio_mode": audio,
        "failure_reason": failure,
        "report_complete": "true",
    }
    return "\n".join(k + "=" + values[k] for k in diagnostics.FIELDS) + "\n"


class ReadinessReportTests(unittest.TestCase):
    def test_real_call_mode_conflict_is_preserved_as_evidence(self):
        result = diagnostics.parse_report(report())
        self.assertEqual("MODE_IN_COMMUNICATION", result["audio_mode"])
        self.assertEqual("audio_route_not_ready", result["failure_reason"])
        self.assertEqual("true", result["telecom_in_call"])

    def test_no_call_is_an_observation_not_a_credential(self):
        result = diagnostics.parse_report(
            report(audio="MODE_NORMAL", failure="no_active_cellular_call").replace(
                "telecom_in_call=true", "telecom_in_call=false"
            )
        )
        self.assertEqual("false", result["telecom_in_call"])

    def test_free_text_or_phone_number_cannot_be_published(self):
        for raw in (
            report() + "caller=510100100\n",
            report().replace("audio_mode=MODE_IN_COMMUNICATION", "audio_mode=510 100 100"),
            report().replace("report_complete=true", "report_complete=false"),
            report().replace("failure_reason=audio_route_not_ready", "failure_reason=Call from John"),
            report().replace("telecom_in_call=true", "telecom_in_call=maybe"),
            report().replace("audio_mode=MODE_IN_COMMUNICATION", "audio_mode=MODE_NORMAL\ncaller=secret"),
            "",
        ):
            with self.subTest(raw=raw[:32]), self.assertRaises(ValueError):
                diagnostics.parse_report(raw)

    def test_reject_unexpected_s22_target_and_timeout(self):
        with self.assertRaises(ValueError):
            diagnostics._adb("a-different-phone", "devices")
        with self.assertRaises(ValueError):
            diagnostics.collect(timeout_seconds=0)

    def test_bounded_run_uses_shell_diagnostics_without_call_actions(self):
        observed = []

        def fake_adb(serial, *args, **kwargs):
            observed.append(args)
            value = ""
            if args[-2:] == ("getprop", "ro.serialno"):
                value = diagnostics.EXPECTED_SERIAL
            elif args[-2:] == ("getprop", "ro.product.model"):
                value = diagnostics.EXPECTED_MODEL
            elif args[-2:] == ("cat", diagnostics.REPORT_PATH):
                value = report()
            return subprocess.CompletedProcess(args, 0, value, "")

        with patch.object(diagnostics, "_adb", side_effect=fake_adb):
            result = diagnostics.collect()
        self.assertEqual("MODE_IN_COMMUNICATION", result["audio_mode"])
        self.assertTrue(any("SupervisedCallReadinessProbeActivity" in " ".join(c) for c in observed))
        self.assertFalse(any("ACTION_CALL" in " ".join(c) for c in observed))
        self.assertFalse(any("endCall" in " ".join(c) for c in observed))


if __name__ == "__main__":
    unittest.main()
