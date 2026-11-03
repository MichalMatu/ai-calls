"""Pure parser regressions for the Mac-only S22 diagnostic runner."""
import unittest

from aicall_tools.diagnostics.s22_autoloop import (
    authorized_devices,
    call_states,
    read_report,
    cycle_verdict,
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
            "after_owned_stop_idle": True,
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
