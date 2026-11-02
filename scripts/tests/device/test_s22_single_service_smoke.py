"""Contract tests for one real-call edge plus user-approved global cleanup.

No test accesses ADB, dials a phone or plays audio.
"""
import unittest
from unittest.mock import patch

from aicall_tools.device import s22_single_service_smoke as smoke


class FakeS22:
    serial = smoke.EXACT_SERIAL

    def __init__(self, states=(0, 0, 2), hangup_result=True, hangup_error=None):
        self.states = iter(states)
        self.last_state = 0
        self.commands = []
        self.hangup_calls = []
        self.hangup_result = hangup_result
        self.hangup_error = hangup_error

    def shell(self, args):
        self.commands.append(tuple(args))
        if args == ["getprop", "ro.serialno"]:
            return smoke.EXACT_SERIAL
        if args == ["getprop", "ro.product.model"]:
            return smoke.EXACT_MODEL
        if args[:4] == ["am", "start", "-a", "android.intent.action.CALL"]:
            return "Starting: Intent"
        raise AssertionError("unexpected adb command")

    def call_state(self):
        self.last_state = next(self.states, self.last_state)
        return self.last_state

    def hangup(self, *, allow_global=False):
        self.hangup_calls.append(allow_global)
        if self.hangup_error is not None:
            raise self.hangup_error
        return self.hangup_result


class OneRealCallSmokeTest(unittest.TestCase):
    def fake_diagnostic(self, serial):
        self.assertEqual(smoke.EXACT_SERIAL, serial)
        return {
            "probe": "supervised_call_readiness",
            "telecom_in_call": "true",
            "telecom_managed_call": "true",
            "audio_mode": "MODE_IN_COMMUNICATION",
            "failure_reason": "audio_route_not_ready",
            "report_complete": "true",
        }

    def test_one_orange_call_with_no_click_mode_snapshot_and_global_cleanup(self):
        adb = FakeS22()
        result = smoke.run_one(adb, diagnostic=self.fake_diagnostic)
        self.assertTrue(result.launched)
        self.assertTrue(result.became_offhook)
        self.assertTrue(result.cleanup_verified_idle)
        self.assertEqual("MODE_IN_COMMUNICATION", result.diagnostic["audio_mode"])
        self.assertEqual([True], adb.hangup_calls)
        dial = [cmd for cmd in adb.commands if cmd[:2] == ("am", "start")]
        self.assertEqual(1, len(dial))
        self.assertEqual(("am", "start", "-a", "android.intent.action.CALL",
                          "-d", "tel:%2A100"), dial[0])

    def test_alternate_explicit_support_number_is_allowed_once(self):
        adb = FakeS22()
        smoke.run_one(adb, "510100100", diagnostic=self.fake_diagnostic)
        self.assertIn(("am", "start", "-a", "android.intent.action.CALL",
                       "-d", "tel:510100100"), adb.commands)
        self.assertEqual([True], adb.hangup_calls)

    def test_unapproved_target_never_dials_or_hangs_up(self):
        adb = FakeS22()
        for target in ("112", "+48123456789", "", "*#31#", "510100101"):
            with self.subTest(target=target), self.assertRaisesRegex(
                ValueError, "support_target_not_allowlisted"
            ):
                smoke.run_one(adb, target)
        self.assertEqual([], adb.commands)
        self.assertEqual([], adb.hangup_calls)

    def test_preexisting_offhook_refuses_dial_and_global_hangup(self):
        adb = FakeS22(states=(2,))
        with self.assertRaisesRegex(RuntimeError, "test_requires_idle_before_dial"):
            smoke.run_one(adb)
        self.assertEqual([], adb.hangup_calls)
        self.assertFalse(any(c[:2] == ("am", "start") for c in adb.commands))

    def test_call_changed_before_dial_does_not_hang_up(self):
        adb = FakeS22(states=(0, 2))
        with self.assertRaisesRegex(RuntimeError, "call_state_changed_before_dial"):
            smoke.run_one(adb)
        self.assertEqual([], adb.hangup_calls)

    def test_incoming_state_after_dial_fails_and_cleanup_error_is_surfaced(self):
        adb = FakeS22(states=(0, 0, 1), hangup_error=RuntimeError("global_hangup_requires_offhook"))
        with self.assertRaisesRegex(RuntimeError, "global_cleanup_unverified"):
            smoke.run_one(adb)
        self.assertEqual([True], adb.hangup_calls)

    def test_diagnostic_failure_still_attempts_one_global_cleanup(self):
        adb = FakeS22()
        def failing(_serial):
            raise RuntimeError("diagnostic_failed")
        with self.assertRaisesRegex(RuntimeError, "diagnostic_failed"):
            smoke.run_one(adb, diagnostic=failing)
        self.assertEqual([True], adb.hangup_calls)

    def test_cleanup_failure_takes_priority_over_diagnostic_success(self):
        adb = FakeS22(hangup_error=RuntimeError("not_confirmed_idle"))
        with self.assertRaisesRegex(RuntimeError, "global_cleanup_unverified"):
            smoke.run_one(adb, diagnostic=self.fake_diagnostic)
        self.assertEqual([True], adb.hangup_calls)

    def test_dial_sends_exactly_one_call_edge_even_if_start_fails(self):
        adb = FakeS22()
        def failed_dial(args):
            if args[:2] == ["am", "start"]:
                raise RuntimeError("permission_denied")
            return FakeS22.shell(adb, args)
        with patch.object(adb, "shell", side_effect=failed_dial):
            with self.assertRaisesRegex(RuntimeError, "permission_denied"):
                smoke.run_one(adb, diagnostic=self.fake_diagnostic)
        self.assertEqual([True], adb.hangup_calls)

    def test_cli_requires_explicit_single_real_call_flag(self):
        with patch.object(smoke, "Adb", side_effect=AssertionError("device accessed")):
            self.assertEqual(2, smoke.main([]))


if __name__ == "__main__":
    unittest.main()
