import unittest

from aicall_tools.calls.live_call_readiness import parse_readiness_report


class LiveCallReadinessTest(unittest.TestCase):
    def test_parses_complete_readiness_report(self):
        report = parse_readiness_report(
            "\n".join(
                [
                    "probe=live_call_readiness",
                    "record_audio_granted=true",
                    "shizuku_binder_available=true",
                    "shizuku_supported=true",
                    "shizuku_permission_granted=true",
                    "live_call_readiness=true",
                    "failure_reason=none",
                    "probe_complete=true",
                ]
            )
        )

        self.assertIsNotNone(report)
        self.assertEqual("true", report["live_call_readiness"])
        self.assertEqual("none", report["failure_reason"])

    def test_rejects_incomplete_or_wrong_probe_report(self):
        self.assertIsNone(
            parse_readiness_report(
                "probe=live_call_readiness\nlive_call_readiness=true\nprobe_complete=false\n"
            )
        )
        self.assertIsNone(
            parse_readiness_report(
                "probe=other\nlive_call_readiness=true\nprobe_complete=true\n"
            )
        )


if __name__ == "__main__":
    unittest.main()
