import unittest
from dataclasses import dataclass

from realtime_live_call_smoke import (
    LIVE_RESULT_PREFIX,
    build_probe_start_args,
    parse_probe_result,
    run_live_smoke,
)
from realtime_network_smoke import SmokeEnvironment


class RealtimeLiveCallSmokeTest(unittest.TestCase):
    def test_probe_start_contains_only_trigger_and_bounded_duration(self):
        args = build_probe_start_args("SERIAL", 10_000)
        joined = " ".join(args)

        self.assertIn("run_realtime_live_call_smoke", joined)
        self.assertIn("realtime_live_duration_ms", joined)
        self.assertIn("10000", joined)
        self.assertNotIn("credential_endpoint", joined)
        self.assertNotIn("broker_token", joined)
        for forbidden in ("tel:", "CALL", "ENDCALL", "input keyevent 5", "input keyevent 6"):
            self.assertNotIn(forbidden, joined)

    def test_non_active_call_refuses_before_secret_staging(self):
        runner = ScriptedRunner(call_states=[0])
        env = environment()

        with self.assertRaisesRegex(RuntimeError, "CALL_STATE=2"):
            run_live_smoke("SERIAL", env, runner=runner, duration_ms=5_000)

        self.assertEqual(1, len(runner.calls))
        self.assertIn("dumpsys", runner.calls[0].args)
        self.assertNotIn(env.broker_token, " ".join(runner.calls[0].args))
        self.assertFalse(any("cat > files/realtime-network-smoke.json" in " ".join(call.args) for call in runner.calls))

    def test_parse_probe_result_reads_pass_and_trace(self):
        text = """noise
I AiCallBridge: realtime_live_call_smoke_result:
I AiCallBridge: realtime_live_call_smoke=PASS
I AiCallBridge: reason=controlled_live_session_completed
I AiCallBridge: states=FETCHING_CREDENTIAL>CONNECTING_REALTIME>STARTING_MEDIA>ACTIVE>STOPPING>TAKEN_OVER
I AiCallBridge: trace=1@0:CONNECT_START;2@15:CONNECT_SUCCESS;3@5000:CLOSED
"""
        result = parse_probe_result(text)

        self.assertIsNotNone(result)
        self.assertEqual("PASS", result.status)
        self.assertEqual("controlled_live_session_completed", result.reason)
        self.assertIn("ACTIVE", result.states)
        self.assertEqual("1@0:CONNECT_START;2@15:CONNECT_SUCCESS;3@5000:CLOSED", result.trace)
        self.assertTrue(result.render().startswith(f"{LIVE_RESULT_PREFIX}PASS"))

    def test_duration_is_bounded_before_any_adb_work(self):
        runner = ScriptedRunner(call_states=[2])
        with self.assertRaises(ValueError):
            run_live_smoke("SERIAL", environment(), runner=runner, duration_ms=60_000)
        self.assertEqual([], runner.calls)


def environment() -> SmokeEnvironment:
    return SmokeEnvironment.from_mapping(
        {
            "AI_CALL_BRIDGE_BROKER_HTTPS_URL": "https://broker.example.test/v1/realtime/client-secret",
            "AI_CALL_BRIDGE_BROKER_TOKEN": "broker-token-" + "x" * 24,
        }
    )


@dataclass
class RecordedCall:
    args: list[str]
    input_bytes: bytes


class ScriptedRunner:
    def __init__(self, *, call_states):
        self.call_states = list(call_states)
        self.calls: list[RecordedCall] = []

    def run_bytes(self, args, *, input_bytes=b"", check=True):
        args = list(args)
        self.calls.append(RecordedCall(args, bytes(input_bytes)))
        if "dumpsys" in args and "telephony.registry" in args:
            state = self.call_states.pop(0)
            return f"mCallState={state}\n".encode()
        return b""


if __name__ == "__main__":
    unittest.main()
