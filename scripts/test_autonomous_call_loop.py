import subprocess
import tempfile
import unittest
from pathlib import Path

from autonomous_call_loop import (
    ProbeMetrics,
    audio_signal_present,
    parse_probe_metrics,
    run_once,
)


class ProbeMetricsTest(unittest.TestCase):
    def test_parses_probe_metrics(self):
        output = """samples_read=4000
non_zero_samples=3900
peak=15370
rms=2339.125
read_errors=0
"""
        self.assertEqual(
            ProbeMetrics(samples_read=4000, non_zero_samples=3900, peak=15370, rms=2339.125, read_errors=0),
            parse_probe_metrics(output),
        )

    def test_rejects_incomplete_probe_metrics(self):
        with self.assertRaises(ValueError):
            parse_probe_metrics("peak=10\nrms=1.2\n")

    def test_off_call_noise_is_not_signal(self):
        metrics = ProbeMetrics(
            samples_read=16000,
            non_zero_samples=11401,
            peak=17,
            rms=1.85,
            read_errors=0,
        )
        self.assertFalse(audio_signal_present(metrics))

    def test_live_call_audio_is_signal(self):
        metrics = ProbeMetrics(
            samples_read=32000,
            non_zero_samples=30615,
            peak=15370,
            rms=2339.0,
            read_errors=0,
        )
        self.assertTrue(audio_signal_present(metrics))

    def test_read_error_never_counts_as_signal(self):
        metrics = ProbeMetrics(
            samples_read=4000,
            non_zero_samples=3999,
            peak=20000,
            rms=5000.0,
            read_errors=1,
        )
        self.assertFalse(audio_signal_present(metrics))


class FailSafeTest(unittest.TestCase):
    def test_post_dial_adb_failure_still_requests_hangup(self):
        class FaultyAdb:
            def __init__(self):
                self.call_state_reads = 0
                self.dialed = False
                self.hangup_called = False

            def call_state(self):
                self.call_state_reads += 1
                if self.call_state_reads == 1:
                    return 0
                raise subprocess.CalledProcessError(1, ["adb", "shell", "dumpsys"])

            def dial(self, number):
                self.dialed = True

            def hangup(self):
                self.hangup_called = True

        adb = FaultyAdb()
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(subprocess.CalledProcessError):
                run_once(
                    adb,
                    "510100100",
                    Path(tmp) / "capture.wav",
                    capture_ms=3000,
                    active_timeout_seconds=1,
                    signal_timeout_seconds=1,
                    max_call_seconds=5,
                    transcribe=False,
                    locale="pl-PL",
                )
        self.assertTrue(adb.dialed)
        self.assertTrue(adb.hangup_called)


if __name__ == "__main__":
    unittest.main()
