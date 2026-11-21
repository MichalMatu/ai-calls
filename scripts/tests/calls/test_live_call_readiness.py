import unittest

from aicall_tools.calls.live_call_readiness import parse_readiness_report


class LiveCallReadinessTest(unittest.TestCase):
    @staticmethod
    def report(mic="true", host="true", ready="true", reason="none"):
        return "\n".join([
            "probe=live_call_readiness",
            f"record_audio_granted={mic}",
            "media_transport=gsm_shell_adb",
            f"gsm_shell_host_available={host}",
            f"live_call_readiness={ready}",
            f"failure_reason={reason}",
            "probe_complete=true",
        ]) + "\n"

    def test_accepts_gsm_host_readiness_with_exact_fields(self):
        report = parse_readiness_report(self.report())
        self.assertIsNotNone(report)
        self.assertEqual("true", report["live_call_readiness"])
        self.assertEqual("gsm_shell_adb", report["media_transport"])

    def test_fails_closed_when_gsm_host_is_missing(self):
        report = parse_readiness_report(self.report(
            host="false", ready="false", reason="gsm_shell_host_unavailable"))
        self.assertEqual("false", report["live_call_readiness"])

    def test_rejects_incomplete_forged_and_old_transport_claims(self):
        cases = (
            self.report().replace("probe_complete=true", "probe_complete=false"),
            self.report().replace("media_transport=gsm_shell_adb", "media_transport=other"),
            self.report().replace("gsm_shell_host_available=true", "shizuku_ready=true"),
            self.report().replace("live_call_readiness=true", "live_call_readiness=false"),
            self.report() + "caller=555123456\n",
            self.report() + "record_audio_granted=false\n",
            "probe=other\nprobe_complete=true\n",
        )
        for raw in cases:
            with self.subTest(raw=raw[:70]):
                self.assertIsNone(parse_readiness_report(raw))


if __name__ == "__main__":
    unittest.main()
