import unittest

from autonomous_call_loop import ProbeMetrics, audio_signal_present, parse_probe_metrics


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


if __name__ == "__main__":
    unittest.main()
