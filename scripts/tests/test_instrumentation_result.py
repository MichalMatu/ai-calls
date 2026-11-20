"""JUnit parser must not mistake a nonzero/empty instrumentation result for success."""
import unittest
from scripts.aicall_tools.diagnostics.instrumentation_result import passed_test_count

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
    def test_plain_message_is_not_success(self):
        self.assertIsNone(passed_test_count("No test results available\n"))
if __name__=="__main__":unittest.main()
