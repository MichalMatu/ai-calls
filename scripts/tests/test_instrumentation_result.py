"""JUnit parser must not mistake a nonzero/empty instrumentation result for success."""
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
import unittest
from unittest.mock import patch

from scripts.aicall_tools.diagnostics.instrumentation_result import main, passed_test_count

class InstrumentationResultTests(unittest.TestCase):
    def test_samsung_junit_stream_without_code_is_success(self):
        self.assertEqual(2,passed_test_count("Test:..\nTime: 3.557\n\nOK (2 tests)\n"))
    def test_standard_android_runner_is_success(self):
        self.assertEqual(5,passed_test_count("OK (5 tests)\nINSTRUMENTATION_CODE: -1\n"))
    def test_crlf_is_accepted(self):
        self.assertEqual(1,passed_test_count("OK (1 test)\r\n"))
    def test_failures_are_never_success(self):
        self.assertIsNone(passed_test_count("FAILURES!!!\nOK (2 tests)\n"))
    def test_aborted_or_failed_is_never_success(self):
        self.assertIsNone(passed_test_count("INSTRUMENTATION_FAILED\nOK (3 tests)\n"))
    def test_no_executed_tests_is_failure(self):
        self.assertIsNone(passed_test_count("OK (0 tests)\n"))
        self.assertIsNone(passed_test_count("INSTRUMENTATION_CODE: -1\n"))
    def test_wrong_code_is_failure(self):
        self.assertIsNone(passed_test_count("OK (2 tests)\nINSTRUMENTATION_CODE: 0\n"))
    def test_malformed_status_is_failure_even_with_a_junit_footer(self):
        self.assertIsNone(passed_test_count("OK (2 tests)\nINSTRUMENTATION_CODE: broken\n"))
    def test_skipped_and_assumed_tests_are_not_passes(self):
        for code in (-3, -4):
            with self.subTest(code=code):
                self.assertIsNone(passed_test_count(
                    f"INSTRUMENTATION_STATUS_CODE: {code}\nOK (2 tests)\n"
                    "INSTRUMENTATION_CODE: -1\n"
                ))
    def test_multiple_summaries_cannot_be_used_as_one_result(self):
        self.assertIsNone(passed_test_count("OK (2 tests)\nOK (3 tests)\n"))
    def test_cli_rejects_partial_suite(self):
        with patch("sys.argv", ["instrumentation_result", "--expected-count", "12"]), \
             patch("sys.stdin", StringIO("OK (11 tests)\n")), \
             redirect_stdout(StringIO()) as stdout, redirect_stderr(StringIO()) as stderr:
            self.assertEqual(1, main())
        self.assertEqual("", stdout.getvalue())
        self.assertIn("expected_12_got_11", stderr.getvalue())
    def test_plain_message_is_not_success(self):
        self.assertIsNone(passed_test_count("No test results available\n"))
if __name__=="__main__":unittest.main()
