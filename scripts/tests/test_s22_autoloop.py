"""Pure parser regressions for the Mac-only S22 diagnostic runner."""
import unittest

from aicall_tools.diagnostics.s22_autoloop import (
    authorized_devices,
    call_states,
    read_report,
    cycle_verdict,
    classify_runtime_status,
    diagnose_phases,
    parse_pipeline_metrics,
    technical_pipeline_evidence,
    LocalCycle,
)


class S22AutoloopParsingTests(unittest.TestCase):
    def test_only_authorized_device_records_count(self):
        devices = (
            "List of devices attached\n"
            "RFCT70L7E8J\tdevice\n"
            "other\toffline\n"
            "simulated\tunauthorized\n"
        )
        self.assertEqual(authorized_devices(devices), ["RFCT70L7E8J"])

    def test_call_states_are_not_call_ownership_proof(self):
        self.assertEqual(
            call_states("mCallState=0\nmCallState=2"),
            {"0", "2"},
        )
        self.assertEqual(call_states("no phone state"), set())

    def test_build_and_idle_are_not_live_call_pass(self):
        report = {
            "final_idle": True,
            "build_install": "PASS",
            "probes": {
                "speech": "CALLBACK_OBSERVED",
                "local_ai": "CALLBACK_OBSERVED",
                "shizuku": "CALLBACK_OBSERVED",
            },
            "campaign_readiness": "READY",
            "start_command": "DISPATCHED_TO_INTERNAL_SERVICE",
            "offhook_observed": True,
            "owned_stop": "DISPATCHED_TO_INTERNAL_SERVICE",
            "after_autonomous_idle": True,
        }
        self.assertEqual(
            cycle_verdict(report, call_requested=True, probes_skipped=False),
            "LIVE_AUDIO_ACCEPTANCE_UNVERIFIED",
        )
        self.assertEqual(
            cycle_verdict(report, call_requested=False, probes_skipped=False),
            "PASS_OFFCALL_ONLY",
        )
        report["offhook_observed"] = False
        self.assertEqual(
            cycle_verdict(report, call_requested=True, probes_skipped=False),
            "INCOMPLETE_OFFHOOK_NOT_OBSERVED",
        )

    def test_in_call_media_failures_are_classified_without_personal_data(self):
        private_number = "+48123456789"
        xml = (
            '<map><string name="status">Call activation timeout; '
            'exact_active=true; audio_mode=MODE_IN_COMMUNICATION; '
            'cleanup=targeted_disconnect; number=' + private_number +
            '</string></map>'
        )
        phase = classify_runtime_status(xml)
        self.assertEqual(phase, "ACTIVATION_TIMEOUT_MODE_IN_COMMUNICATION")
        self.assertNotIn(private_number, phase)
        self.assertEqual(
            diagnose_phases(["DIAL_STARTED", phase]),
            "CELLULAR_AUDIO_MODE_NOT_IN_CALL",
        )
        self.assertEqual(classify_runtime_status("not xml"), "UNAVAILABLE")

    def test_runtime_started_does_not_mean_live_rx_tx_pass(self):
        self.assertEqual(
            classify_runtime_status(
                '<map><string name="status">Exact owned cellular call active; '
                'starting campaign runtime</string></map>'
            ),
            "LOCAL_RUNTIME_STARTED",
        )
        self.assertEqual(
            diagnose_phases(["DIAL_STARTED", "LOCAL_RUNTIME_STARTED"]),
            "LOCAL_RUNTIME_STARTED_RX_TX_NOT_PROVEN",
        )

    def test_real_pipeline_counters_prove_technical_path_not_remote_success(self):
        raw = (
            "telephony_rx_pcm_bytes=1142400\n"
            "telephony_tx_pcm_bytes=322986\n"
            "gemma_action_decision_count=6\n"
            "turns_completed=5\n"
            "turn_1_stt_chars=31\n"
            "turn_1_approved_chars=9\n"
            "probe_complete=true\n"
            "failure_reason=backend_supervisor_relay_disabled\n"
            "session_id=private-123\n"
            "transcript=private contents\n"
        )
        metrics = parse_pipeline_metrics(raw)
        self.assertTrue(technical_pipeline_evidence(metrics))
        self.assertEqual(metrics["stt_turns"], 1)
        self.assertEqual(metrics["approved_turns"], 1)
        self.assertNotIn("transcript", metrics)
        self.assertNotIn("session_id", metrics)
        report = {
            "final_idle": True,
            "probes": {},
            "campaign_readiness": "READY",
            "start_command": "DISPATCHED_TO_INTERNAL_SERVICE",
            "offhook_observed": True,
            "after_autonomous_idle": True,
            "pipeline_metrics": metrics,
        }
        self.assertEqual(
            cycle_verdict(report, call_requested=True, probes_skipped=True),
            "LIVE_TECHNICAL_PIPELINE_VERIFIED_EXTERNAL_RESULT_OPEN",
        )
        report["dialogue_failure_code"] = "backend_supervisor_relay_disabled"
        self.assertEqual(
            cycle_verdict(report, call_requested=True, probes_skipped=True),
            "FAIL_LOCAL_ONLY_DIALOGUE_FALLBACK",
        )

    def test_observer_window_never_forces_campaign_stop(self):
        import inspect
        code = inspect.getsource(LocalCycle.authorized_call)
        self.assertNotIn('app_command("run_campaign_agent_stop")', code)
        self.assertIn("NOT_SENT_AUTONOMOUS_CALL_LIFECYCLE", code)

    def test_no_authority_and_incomplete_probe_cannot_pass(self):
        report = {
            "final_idle": True,
            "campaign_readiness": "NO_VALID_EXACT_GRANT",
            "probes": {"speech": "CALLBACK_OBSERVED"},
        }
        self.assertEqual(
            cycle_verdict(report, call_requested=True, probes_skipped=True),
            "NOT_EXECUTED_NO_VALID_CAMPAIGN_AUTHORITY",
        )
        self.assertEqual(
            cycle_verdict(report, call_requested=False, probes_skipped=False),
            "INCOMPLETE_OFFCALL_PROBES",
        )

    def test_private_report_accepts_only_machine_statuses(self):
        report = (
            "campaign_agent_readiness=READY\n"
            "campaign_agent_start=NOT_REQUESTED\n"
            "target=+48123123123\n"
            "transcript=Hello world\n"
            "campaign_agent_scope=EXISTING_GRANT_ONLY\n"
        )
        self.assertEqual(
            read_report(report),
            {
                "campaign_agent_readiness": "READY",
                "campaign_agent_start": "NOT_REQUESTED",
                "campaign_agent_scope": "EXISTING_GRANT_ONLY",
            },
        )


if __name__ == "__main__":
    unittest.main()
