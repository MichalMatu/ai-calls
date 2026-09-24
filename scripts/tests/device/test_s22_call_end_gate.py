import unittest

from aicall_tools.device.s22_call_end_gate import (
    build_device_observer_script,
    parse_call_end_result,
    validate_call_end_result,
)


GREEN_RESULT = """boot_before=11111111-2222-3333-4444-555555555555
start_uptime=100.000
call_state_before=2
call_assistant_started_before=1
call_end_seen=1
call_end_observed_ms=1000
call_state_after=0
call_assistant_stop_seen=1
media_stop_after_call_end_ms=75
shizuku_server_alive=1
boot_after=11111111-2222-3333-4444-555555555555
boot_same=1
done=1
"""


class CallEndObserverParsingTest(unittest.TestCase):
    def test_parses_complete_green_result(self):
        result = parse_call_end_result(GREEN_RESULT)
        self.assertEqual(2, result.call_state_before)
        self.assertTrue(result.call_assistant_started_before)
        self.assertTrue(result.call_end_seen)
        self.assertEqual(1000, result.call_end_observed_ms)
        self.assertEqual(0, result.call_state_after)
        self.assertTrue(result.call_assistant_stop_seen)
        self.assertEqual(75, result.media_stop_after_call_end_ms)
        self.assertTrue(result.shizuku_server_alive)
        self.assertTrue(result.boot_same)
        self.assertTrue(result.done)

    def test_rejects_missing_or_invalid_boolean_field(self):
        with self.assertRaises(ValueError):
            parse_call_end_result(GREEN_RESULT.replace("done=1\n", ""))
        with self.assertRaises(ValueError):
            parse_call_end_result(GREEN_RESULT.replace("boot_same=1", "boot_same=yes"))


class CallEndObserverGateTest(unittest.TestCase):
    def test_green_result_passes_strict_gate(self):
        result = parse_call_end_result(GREEN_RESULT)
        self.assertEqual([], validate_call_end_result(result, max_cleanup_ms=3000))

    def test_requires_active_call_and_started_call_assistant_before_end(self):
        result = parse_call_end_result(
            GREEN_RESULT
            .replace("call_state_before=2", "call_state_before=0")
            .replace("call_assistant_started_before=1", "call_assistant_started_before=0")
        )
        errors = validate_call_end_result(result, max_cleanup_ms=3000)
        self.assertIn("call_state_before is not active", errors)
        self.assertIn("call_assistant_started_before is false", errors)

    def test_requires_idle_and_media_stop_within_deadline(self):
        result = parse_call_end_result(
            GREEN_RESULT
            .replace("call_state_after=0", "call_state_after=2")
            .replace("media_stop_after_call_end_ms=75", "media_stop_after_call_end_ms=3001")
        )
        errors = validate_call_end_result(result, max_cleanup_ms=3000)
        self.assertIn("call_state_after is not idle", errors)
        self.assertIn("media_stop_after_call_end_ms exceeds 3000", errors)

    def test_rejects_missing_end_or_stop_and_environment_loss(self):
        result = parse_call_end_result(
            GREEN_RESULT
            .replace("call_end_seen=1", "call_end_seen=0")
            .replace("call_assistant_stop_seen=1", "call_assistant_stop_seen=0")
            .replace("media_stop_after_call_end_ms=75", "media_stop_after_call_end_ms=-1")
            .replace("shizuku_server_alive=1", "shizuku_server_alive=0")
            .replace("boot_same=1", "boot_same=0")
        )
        errors = validate_call_end_result(result, max_cleanup_ms=3000)
        self.assertIn("call_end_seen is false", errors)
        self.assertIn("call_assistant_stop_seen is false", errors)
        self.assertIn("media_stop_after_call_end_ms is invalid", errors)
        self.assertIn("shizuku_server_alive is false", errors)
        self.assertIn("boot_same is false", errors)


class CallEndObserverScriptTest(unittest.TestCase):
    def test_device_script_observes_but_does_not_trigger_hangup(self):
        script = build_device_observer_script()
        self.assertIn("dumpsys telephony.registry", script)
        self.assertIn("USAGE_CALL_ASSISTANT", script)
        self.assertIn("/proc/uptime", script)
        self.assertIn("HELPER_PID", script)
        self.assertIn('mv "$TMP" "$OUT"', script)
        self.assertNotIn("KEYCODE_ENDCALL", script)
        self.assertNotIn("input keyevent", script)


if __name__ == "__main__":
    unittest.main()
