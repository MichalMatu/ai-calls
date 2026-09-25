import subprocess
import unittest
from pathlib import Path
from unittest import mock

from aicall_tools.relay import chat_relay_protocol as protocol
from aicall_tools.calls import chatgpt_relay_live_call as live


class ChatRelayProtocolTest(unittest.TestCase):
    def test_round_trip_unicode_and_newlines(self):
        envelope = protocol.Envelope("orange-demo_1", 2, "Dzień dobry.\nW czym mogę pomóc?")
        raw = protocol.encode("request", envelope)
        self.assertIn("AICALL_CHAT_RELAY_V1", raw)
        self.assertIn("Dzień dobry.", raw)
        self.assertEqual(envelope, protocol.decode("request", raw))

    def test_rejects_wrong_kind_and_unsafe_session(self):
        envelope = protocol.Envelope("session-1", 1, "tekst")
        raw = protocol.encode("request", envelope)
        with self.assertRaises(ValueError):
            protocol.decode("response", raw)
        with self.assertRaises(ValueError):
            protocol.Envelope("../escape", 1, "tekst").validate()


class ChatGptRelayLiveCallTest(unittest.TestCase):
    def test_orange_target_is_explicitly_allowlisted(self):
        self.assertEqual("*100", live.normalize_allowlisted_target("*100"))
        with self.assertRaises(ValueError):
            live.normalize_allowlisted_target("510 100 100")
        with self.assertRaises(ValueError):
            live.normalize_allowlisted_target("123456789")

    def test_relay_branch_name_is_session_scoped(self):
        self.assertEqual("chat-relay/orange-demo_1", live.relay_branch_name("orange-demo_1"))
        with self.assertRaises(ValueError):
            live.relay_branch_name("../bad")

    def test_probe_start_args_use_dedicated_activity(self):
        args = live.build_probe_start_args("RFCT70L7E8J", "orange-demo_1", 3)
        joined = " ".join(args)
        self.assertIn("ChatRelayProbeActivity", joined)
        self.assertIn("orange-demo_1", joined)
        self.assertIn("3", joined)
        self.assertNotIn("response_text", joined)

    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.subprocess.run")
    def test_response_publish_is_atomic_and_payload_stays_on_stdin(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "", "")
        mailbox = live.AdbRelayMailbox("RFCT70L7E8J")
        envelope = protocol.Envelope("orange-demo_1", 1, "To jest odpowiedź z czatu.")

        mailbox.write_response(envelope)

        self.assertEqual(2, run.call_count)
        write_call, publish_call = run.call_args_list
        write_argv = write_call.args[0]
        publish_argv = publish_call.args[0]
        self.assertNotIn(envelope.text, " ".join(write_argv + publish_argv))
        self.assertIn(envelope.text, write_call.kwargs["input"])
        self.assertIn("tee", write_argv)
        self.assertIn(live.RESPONSE_PATH + ".tmp", write_argv)
        self.assertNotIn("sh", write_argv)
        self.assertNotIn("-c", write_argv)
        self.assertIn("mv", publish_argv)
        self.assertIn(live.RESPONSE_PATH + ".tmp", publish_argv)
        self.assertIn(live.RESPONSE_PATH, publish_argv)
        self.assertIsNone(publish_call.kwargs.get("input"))

    def test_metric_formatter_whitelists_only_non_text_timing_fields(self):
        report = {
            "turns_completed": "1",
            "turn_1_endpoint_capture_ms": "1460",
            "turn_1_stt_elapsed_ms": "1900",
            "turn_1_approved_elapsed_ms": "4100",
            "turn_1_output_pcm_ready_elapsed_ms": "4300",
            "turn_1_post_write_hold_ms": "850",
            "turn_1_eos_to_first_tx_ms": "3500",
            "turn_1_complete_elapsed_ms": "5900",
            "turn_1_stt_text": "TAJNY TRANSCRIPT",
            "approved_text": "TAJNA ODPOWIEDZ",
        }
        lines = live.format_probe_metric_lines(report)
        joined = "\n".join(lines)
        self.assertIn("turn_1_endpoint_capture_ms=1460", joined)
        self.assertIn("turn_1_post_write_hold_ms=850", joined)
        self.assertIn("turn_1_eos_to_first_tx_ms=3500", joined)
        self.assertNotIn("TAJNY TRANSCRIPT", joined)
        self.assertNotIn("TAJNA ODPOWIEDZ", joined)
        self.assertNotIn("stt_text", joined)

    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.subprocess.run")
    def test_remote_branch_cleanup_is_verified(self, run):
        run.side_effect = [
            subprocess.CompletedProcess([], 0, "", ""),
            subprocess.CompletedProcess([], 2, "", ""),
        ]
        self.assertTrue(
            live.delete_remote_branch_verified(Path("."), "chat-relay/orange-demo_1", attempts=1)
        )
        self.assertEqual(2, run.call_count)
        self.assertIn("--delete", run.call_args_list[0].args[0])
        self.assertIn("ls-remote", run.call_args_list[1].args[0])

    def test_call_state_or_none_treats_transient_adb_failure_as_unknown(self):
        adb = mock.Mock()
        adb.call_state.side_effect = subprocess.CalledProcessError(1, ["adb", "dumpsys"])

        self.assertIsNone(live.call_state_or_none(adb))
        adb.call_state.assert_called_once_with()

    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.time.sleep")
    def test_best_effort_hangup_retries_then_uses_telecom_fallback(self, sleep):
        adb = mock.Mock()
        adb.hangup.side_effect = RuntimeError("synthetic hangup failure")
        adb.shell.return_value = "Call ended"

        self.assertTrue(live.best_effort_hangup(adb))
        self.assertEqual(3, adb.hangup.call_count)
        adb.shell.assert_called_once_with(["cmd", "telecom", "end-call"], check=False)
        self.assertEqual(2, sleep.call_count)

    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.time.sleep")
    def test_adb_shell_retry_recovers_from_transient_audio_probe_failure(self, sleep):
        adb = mock.Mock()
        adb.shell.side_effect = [
            subprocess.CalledProcessError(255, ["adb", "shell", "dumpsys", "audio"]),
            "Audio mode: MODE_IN_CALL",
        ]

        self.assertEqual(
            "Audio mode: MODE_IN_CALL",
            live.adb_shell_retry(adb, ["dumpsys", "audio"]),
        )
        self.assertEqual(2, adb.shell.call_count)
        sleep.assert_called_once_with(0.25)

    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.time.sleep")
    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.wait_for_audio_signal")
    def test_audio_signal_wait_retries_transient_registry_failure(self, wait_signal, sleep):
        marker = object()
        wait_signal.side_effect = [
            subprocess.CalledProcessError(
                1,
                ["adb", "-s", "RFCT70L7E8J", "shell", "dumpsys", "telephony.registry"],
            ),
            marker,
        ]

        self.assertIs(marker, live.wait_for_audio_signal_resilient(mock.Mock(), timeout_seconds=5.0))
        self.assertEqual(2, wait_signal.call_count)
        sleep.assert_called_once()

    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.time.sleep")
    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.time.monotonic", side_effect=[0.0, 0.0, 1.0])
    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.wait_for_audio_signal")
    def test_audio_signal_wait_does_not_retry_non_registry_adb_failure(
        self,
        wait_signal,
        monotonic,
        sleep,
    ):
        error = subprocess.CalledProcessError(
            1,
            ["adb", "-s", "RFCT70L7E8J", "shell", "pm", "path", "pl.michalmatu.aicallbridge"],
        )
        wait_signal.side_effect = error

        with self.assertRaises(subprocess.CalledProcessError) as raised:
            live.wait_for_audio_signal_resilient(mock.Mock(), timeout_seconds=0.5)

        self.assertIs(error, raised.exception)
        wait_signal.assert_called_once()
        sleep.assert_not_called()

    @mock.patch("aicall_tools.calls.chatgpt_relay_live_call.wait_for_audio_signal_resilient")
    def test_initial_audio_wait_after_confirmed_offhook_skips_redundant_registry_probe(self, wait_signal):
        real_adb = mock.Mock()
        real_adb.call_state.side_effect = AssertionError("real call_state must not be re-read here")
        delegated_marker = object()
        real_adb.delegated_marker = delegated_marker
        audio_marker = object()

        def capture(adb_view, *, timeout_seconds):
            self.assertEqual(2, adb_view.call_state())
            self.assertIs(delegated_marker, adb_view.delegated_marker)
            self.assertEqual(25.0, timeout_seconds)
            return audio_marker

        wait_signal.side_effect = capture

        self.assertIs(
            audio_marker,
            live.wait_for_initial_audio_signal_after_confirmed_offhook(
                real_adb,
                timeout_seconds=25.0,
            ),
        )
        real_adb.call_state.assert_not_called()
        wait_signal.assert_called_once()


    def test_service_number_normalization_is_runtime_safe_and_strict(self):
        self.assertEqual("123456789", live.normalize_service_number("123 456 789"))
        self.assertEqual("48123456789", live.normalize_service_number("+48 (123) 456-789"))
        self.assertIsNone(live.normalize_service_number(None))
        with self.assertRaises(ValueError):
            live.normalize_service_number("12345")
        with self.assertRaises(ValueError):
            live.normalize_service_number("abc123456789")

    def test_authorized_phone_control_is_answered_from_runtime_value(self):
        response = live.automatic_orange_supervisor_response(
            live.DISCLOSE_PHONE_CONTROL,
            service_number="123456789",
        )
        self.assertEqual("Numer usługi to 1 2 3 4 5 6 7 8 9.", response)

    def test_authorized_phone_control_requires_runtime_value(self):
        with self.assertRaises(RuntimeError):
            live.automatic_orange_supervisor_response(
                live.DISCLOSE_PHONE_CONTROL,
                service_number=None,
            )

    def test_natural_language_is_not_interpreted_by_host_dialogue_script(self):
        self.assertIsNone(
            live.automatic_orange_supervisor_response(
                "Podaj dowolny numer twojej usługi.",
                service_number="123456789",
            )
        )


if __name__ == "__main__":
    unittest.main()
