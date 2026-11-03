"""Pure parser regressions for the Mac-only S22 diagnostic runner."""
import unittest

from aicall_tools.diagnostics.s22_autoloop import (
    authorized_devices,
    call_states,
    read_report,
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
