"""Validate Orange probe ownership and evidence using a fake USB channel."""

from contextlib import redirect_stdout
from io import StringIO
from unittest import TestCase
from unittest.mock import Mock, call, patch

from aicall_tools.device import s22_orange_audio_flow as orange


class OrangeAudioFlowTest(TestCase):
    def setUp(self):
        self.device = Mock()
        self.device.idle.return_value = True
        self.idle = {"call_idle": True}
        self.active = {"call_idle": False, "exact_call_bound": True, "exact_call_active": True}

    def test_transport_only_uses_exact_target_and_never_opens_audio(self):
        with patch.object(orange, "checked", return_value="host_registered=true") as adb, \
                patch.object(orange, "snapshot", side_effect=[self.idle, self.active, self.idle]), \
                patch.object(orange, "device_request", return_value={"ok": True}) as request, \
                patch.object(orange, "read_call_technical_summary") as media, redirect_stdout(StringIO()):
            report = orange.flow(self.device, transport_only=True)

        self.device.safe_preflight.assert_called_once_with(orange.S8_PACKAGE)
        adb.assert_called_once_with(self.device, "shell", "content", "call", "--uri", orange.HOST_URI,
                                    "--method", "status")
        self.assertEqual(request.call_args_list, [
            call(self.device, "orange_smoke_start", target="510100100", approved=True),
            call(self.device, "orange_smoke_stop"),
        ])
        media.assert_not_called()
        self.assertTrue(report["exact_active"] and report["exact_stop_accepted"] and report["phone_idle_after"])
        self.assertFalse(report["pilot_started"])

    def test_unconfirmed_dial_response_still_requests_exact_teardown(self):
        with patch.object(orange, "checked", return_value="host_registered=true"), \
                patch.object(orange, "snapshot", side_effect=[self.idle, self.idle]), \
                patch.object(orange, "device_request", side_effect=[
                    orange.DiagnosticError("diagnostic_response_missing"), {"ok": True},
                ]) as request, redirect_stdout(StringIO()):
            with self.assertRaisesRegex(orange.DiagnosticError, "diagnostic_response_missing"):
                orange.flow(self.device, transport_only=True)

        request.assert_called_with(self.device, "orange_smoke_stop")

    def test_prior_pilot_report_cannot_prove_a_new_audio_session(self):
        old_report = "pilot-previous", {"available": True, "telephony_tx_pcm_bytes": 256}
        with patch.object(orange, "checked", return_value="host_registered=true"), \
                patch.object(orange, "snapshot", side_effect=[self.idle, self.active, self.idle, self.idle]), \
                patch.object(orange, "device_request", return_value={"ok": True}), \
                patch.object(orange, "read_call_technical_summary", return_value=old_report), \
                patch.object(orange, "controls", return_value={
                    orange.START: (10, 10, False), orange.SCRIPT: (10, 20, False), orange.STOP: (10, 30, False),
                }), patch.object(orange, "tap"), patch.object(orange.time, "sleep"), redirect_stdout(StringIO()):
            report = orange.flow(self.device)

        self.assertFalse(report["report_available"])
        self.assertFalse(report["tx_positive"])
