import unittest

from s22_app_death_gate import (
    build_device_observer_script,
    parse_observer_result,
    validate_observer_result,
)


GREEN_RESULT = """termination_method=kill-pid
termination_rc=0
boot_before=11111111-2222-3333-4444-555555555555
start_uptime=100.000
app_process_gone=1
app_cleanup_ms=75
userservice_process_gone=1
helper_cleanup_ms=2050
call_assistant_stop_seen=1
media_stop_observed_ms=2075
call_state_after=2
shizuku_server_alive=1
boot_after=11111111-2222-3333-4444-555555555555
boot_same=1
done=1
"""


class AppDeathObserverParsingTest(unittest.TestCase):
    def test_parses_complete_green_result(self):
        result = parse_observer_result(GREEN_RESULT)
        self.assertEqual("kill-pid", result.termination_method)
        self.assertEqual(0, result.termination_rc)
        self.assertTrue(result.app_process_gone)
        self.assertEqual(75, result.app_cleanup_ms)
        self.assertTrue(result.userservice_process_gone)
        self.assertEqual(2050, result.helper_cleanup_ms)
        self.assertTrue(result.call_assistant_stop_seen)
        self.assertEqual(2075, result.media_stop_observed_ms)
        self.assertEqual(2, result.call_state_after)
        self.assertTrue(result.shizuku_server_alive)
        self.assertTrue(result.boot_same)
        self.assertTrue(result.done)

    def test_rejects_missing_required_field(self):
        with self.assertRaises(ValueError):
            parse_observer_result(GREEN_RESULT.replace("done=1\n", ""))

    def test_rejects_invalid_boolean_field(self):
        with self.assertRaises(ValueError):
            parse_observer_result(GREEN_RESULT.replace("boot_same=1", "boot_same=yes"))

    def test_rejects_duplicate_field(self):
        with self.assertRaises(ValueError):
            parse_observer_result(GREEN_RESULT + "done=1\n")


class AppDeathObserverGateTest(unittest.TestCase):
    def test_green_result_passes_strict_gate(self):
        result = parse_observer_result(GREEN_RESULT)
        self.assertEqual([], validate_observer_result(result, max_cleanup_ms=3000))

    def test_rejects_helper_cleanup_over_deadline(self):
        result = parse_observer_result(GREEN_RESULT.replace("helper_cleanup_ms=2050", "helper_cleanup_ms=3001"))
        self.assertIn("helper_cleanup_ms exceeds 3000", validate_observer_result(result, max_cleanup_ms=3000))

    def test_rejects_missing_call_assistant_stop(self):
        result = parse_observer_result(
            GREEN_RESULT
            .replace("call_assistant_stop_seen=1", "call_assistant_stop_seen=0")
            .replace("media_stop_observed_ms=2075", "media_stop_observed_ms=-1")
        )
        errors = validate_observer_result(result, max_cleanup_ms=3000)
        self.assertIn("call_assistant_stop_seen is false", errors)
        self.assertIn("media_stop_observed_ms is invalid", errors)

    def test_rejects_call_end_shizuku_loss_or_reboot(self):
        result = parse_observer_result(
            GREEN_RESULT
            .replace("call_state_after=2", "call_state_after=0")
            .replace("shizuku_server_alive=1", "shizuku_server_alive=0")
            .replace("boot_same=1", "boot_same=0")
        )
        errors = validate_observer_result(result, max_cleanup_ms=3000)
        self.assertIn("call_state_after is not active", errors)
        self.assertIn("shizuku_server_alive is false", errors)
        self.assertIn("boot_same is false", errors)

    def test_rejects_failed_termination_or_surviving_processes(self):
        result = parse_observer_result(
            GREEN_RESULT
            .replace("termination_rc=0", "termination_rc=1")
            .replace("app_process_gone=1", "app_process_gone=0")
            .replace("userservice_process_gone=1", "userservice_process_gone=0")
        )
        errors = validate_observer_result(result, max_cleanup_ms=3000)
        self.assertIn("termination_rc is not zero", errors)
        self.assertIn("app_process_gone is false", errors)
        self.assertIn("userservice_process_gone is false", errors)


class AppDeathObserverScriptTest(unittest.TestCase):
    def test_device_script_is_session_independent_and_atomic(self):
        script = build_device_observer_script()
        self.assertNotIn("nohup", script)
        self.assertIn("/proc/$APP_PID", script)
        self.assertIn("/proc/$HELPER_PID", script)
        self.assertIn("mv \"$TMP\" \"$OUT\"", script)
        self.assertIn("cut -d' ' -f1 /proc/uptime", script)

    def test_device_script_supports_explicit_kill_and_force_stop_modes(self):
        script = build_device_observer_script()
        self.assertIn('"kill-pid")', script)
        self.assertIn('kill -9 "$APP_PID"', script)
        self.assertIn('"force-stop")', script)
        self.assertIn('am force-stop "$PKG"', script)
        self.assertNotIn("fallback", script.casefold())

    def test_device_script_tracks_exact_call_assistant_audioflinger_removal(self):
        script = build_device_observer_script()
        self.assertIn("dumpsys audio", script)
        self.assertIn("USAGE_CALL_ASSISTANT", script)
        self.assertIn("call_assistant_piid=", script)
        self.assertIn("call_assistant_session_id=", script)
        self.assertIn("dumpsys media.audio_flinger", script)
        self.assertIn("removeTrack_l", script)
        self.assertNotIn("logcat -d", script)


if __name__ == "__main__":
    unittest.main()
