import unittest

from aicall_tools.device.s22_resource_telemetry import (
    ProcessMetrics,
    TelemetrySample,
    _count_ps_threads,
    _parse_ps_rss,
    parse_call_assistant_state,
    parse_process_metrics,
    summarize_samples,
)


class ProcessMetricsParsingTest(unittest.TestCase):
    def test_parses_complete_process_metrics(self):
        metrics = parse_process_metrics(
            "pid=1234\nvmrss_kb=45678\nfd_count=91\nthread_count=17\n"
        )
        self.assertEqual(
            ProcessMetrics(pid=1234, vmrss_kb=45678, fd_count=91, thread_count=17),
            metrics,
        )

    def test_missing_process_is_explicit_not_zero(self):
        self.assertIsNone(parse_process_metrics("process_missing=1\n"))

    def test_rejects_partial_or_invalid_metrics(self):
        with self.assertRaises(ValueError):
            parse_process_metrics("pid=1234\nvmrss_kb=45678\nfd_count=91\n")
        with self.assertRaises(ValueError):
            parse_process_metrics(
                "pid=1234\nvmrss_kb=45678\nfd_count=-1\nthread_count=17\n"
            )


class PsMetricsParsingTest(unittest.TestCase):
    def test_parses_rss_for_exact_pid(self):
        output = """  PID    RSS NAME
 1234  45678 pl.example.app
 2222  12000 pl.example.app:helper
"""
        self.assertEqual(45678, _parse_ps_rss(output, 1234))
        self.assertEqual(12000, _parse_ps_rss(output, 2222))
        self.assertIsNone(_parse_ps_rss(output, 9999))

    def test_counts_threads_for_exact_pid(self):
        output = """  PID   TID NAME
 1234  1234 pl.example.app
 1234  1235 RenderThread
 2222  2222 pl.example.app:helper
"""
        self.assertEqual(2, _count_ps_threads(output, 1234))
        self.assertEqual(1, _count_ps_threads(output, 2222))
        self.assertEqual(0, _count_ps_threads(output, 9999))


class CallAssistantStateParsingTest(unittest.TestCase):
    def test_uses_last_matching_state_for_helper_pid(self):
        logcat = """
AudioTrack  I  u/pid:2000/222 state:started usage=USAGE_CALL_ASSISTANT
AudioTrack  I  u/pid:2000/333 state:started usage=USAGE_CALL_ASSISTANT
AudioTrack  I  u/pid:2000/222 state:stopped usage=USAGE_CALL_ASSISTANT
"""
        self.assertEqual("stopped", parse_call_assistant_state(logcat, 222))
        self.assertEqual("started", parse_call_assistant_state(logcat, 333))
        self.assertEqual("unknown", parse_call_assistant_state(logcat, 444))

    def test_requires_call_assistant_usage_on_same_line(self):
        logcat = "u/pid:2000/222 state:started usage=USAGE_MEDIA\nUSAGE_CALL_ASSISTANT\n"
        self.assertEqual("unknown", parse_call_assistant_state(logcat, 222))


class TelemetrySummaryTest(unittest.TestCase):
    def test_summarizes_start_end_peak_and_delta(self):
        samples = [
            TelemetrySample(
                device_uptime_s=100.0,
                call_state=2,
                call_assistant_state="started",
                app=ProcessMetrics(1001, 30000, 40, 8),
                helper=ProcessMetrics(2001, 12000, 20, 5),
            ),
            TelemetrySample(
                device_uptime_s=101.0,
                call_state=2,
                call_assistant_state="started",
                app=ProcessMetrics(1001, 31000, 44, 9),
                helper=ProcessMetrics(2001, 13500, 24, 6),
            ),
            TelemetrySample(
                device_uptime_s=102.0,
                call_state=2,
                call_assistant_state="stopped",
                app=ProcessMetrics(1001, 30500, 41, 8),
                helper=ProcessMetrics(2001, 12500, 21, 5),
            ),
        ]

        summary = summarize_samples(samples)
        self.assertEqual(3, summary["sample_count"])
        self.assertEqual(100.0, summary["start_uptime_s"])
        self.assertEqual(102.0, summary["end_uptime_s"])
        self.assertEqual(
            {"start": 12000, "end": 12500, "peak": 13500, "delta": 500},
            summary["helper"]["vmrss_kb"],
        )
        self.assertEqual(
            {"start": 20, "end": 21, "peak": 24, "delta": 1},
            summary["helper"]["fd_count"],
        )
        self.assertEqual(
            {"start": 5, "end": 5, "peak": 6, "delta": 0},
            summary["helper"]["thread_count"],
        )

    def test_summary_ignores_missing_process_samples_without_fabricating_zero(self):
        samples = [
            TelemetrySample(100.0, 2, "started", None, ProcessMetrics(2001, 12000, 20, 5)),
            TelemetrySample(101.0, 2, "started", None, None),
            TelemetrySample(102.0, 0, "stopped", None, ProcessMetrics(2001, 12100, 20, 5)),
        ]
        summary = summarize_samples(samples)
        self.assertIsNone(summary["app"])
        self.assertEqual(2, summary["helper"]["observed_samples"])
        self.assertEqual(100, summary["helper"]["vmrss_kb"]["delta"])


if __name__ == "__main__":
    unittest.main()
