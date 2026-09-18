import json
import unittest
from dataclasses import dataclass

from realtime_network_smoke import (
    PACKAGE_NAME,
    PROBE_ACTIVITY,
    SmokeEnvironment,
    build_probe_start_args,
    build_private_config_payload,
    parse_probe_result,
    stage_private_config,
)


class RealtimeNetworkSmokeTest(unittest.TestCase):
    def test_environment_requires_https_endpoint_and_separate_strong_bearer(self):
        env = SmokeEnvironment.from_mapping(
            {
                "AI_CALL_BRIDGE_BROKER_HTTPS_URL": "https://broker.example.test/v1/realtime/client-secret",
                "AI_CALL_BRIDGE_BROKER_TOKEN": "broker-token-" + "x" * 24,
            }
        )
        self.assertEqual(
            "https://broker.example.test/v1/realtime/client-secret",
            env.credential_endpoint,
        )
        self.assertNotIn(env.broker_token, repr(env))

        with self.assertRaises(ValueError):
            SmokeEnvironment.from_mapping(
                {
                    "AI_CALL_BRIDGE_BROKER_HTTPS_URL": "http://broker.example.test/token",
                    "AI_CALL_BRIDGE_BROKER_TOKEN": "x" * 32,
                }
            )
        with self.assertRaises(ValueError):
            SmokeEnvironment.from_mapping(
                {
                    "AI_CALL_BRIDGE_BROKER_HTTPS_URL": "https://api.openai.com/v1/realtime/client_secrets",
                    "AI_CALL_BRIDGE_BROKER_TOKEN": "x" * 32,
                }
            )
        with self.assertRaises(ValueError):
            SmokeEnvironment.from_mapping(
                {
                    "AI_CALL_BRIDGE_BROKER_HTTPS_URL": "https://broker.example.test/token",
                    "AI_CALL_BRIDGE_BROKER_TOKEN": "too-short",
                }
            )
        with self.assertRaises(ValueError):
            SmokeEnvironment.from_mapping(
                {
                    "AI_CALL_BRIDGE_BROKER_HTTPS_URL": "https://broker.example.test/token",
                    "AI_CALL_BRIDGE_BROKER_TOKEN": "sk-proj-this-is-not-a-broker-token-123456",
                }
            )

    def test_private_config_payload_has_only_endpoint_and_bearer(self):
        payload = build_private_config_payload(
            "https://broker.example.test/v1/realtime/client-secret",
            "broker-token-" + "x" * 24,
        )
        parsed = json.loads(payload.decode("utf-8"))
        self.assertEqual(
            {"credential_endpoint", "broker_token"},
            set(parsed),
        )

    def test_stage_sends_secret_only_over_stdin_not_adb_arguments(self):
        runner = RecordingRunner()
        token = "broker-token-" + "x" * 24
        endpoint = "https://broker.example.test/v1/realtime/client-secret"

        stage_private_config(runner, "SERIAL", endpoint, token)

        self.assertEqual(1, len(runner.calls))
        call = runner.calls[0]
        joined_args = " ".join(call.args)
        self.assertNotIn(token, joined_args)
        self.assertNotIn(endpoint, joined_args)
        self.assertEqual(
            [
                "adb",
                "-s",
                "SERIAL",
                "shell",
                "run-as",
                PACKAGE_NAME,
                "sh",
                "-c",
                "cat > files/realtime-network-smoke.json",
            ],
            call.args,
        )
        parsed = json.loads(call.input_bytes.decode("utf-8"))
        self.assertEqual(token, parsed["broker_token"])
        self.assertEqual(endpoint, parsed["credential_endpoint"])

    def test_probe_start_intent_contains_only_boolean_trigger(self):
        args = build_probe_start_args("SERIAL")
        joined = " ".join(args)
        self.assertIn(PROBE_ACTIVITY, joined)
        self.assertIn("run_realtime_network_off_call_smoke", joined)
        self.assertNotIn("credential_endpoint", joined)
        self.assertNotIn("broker_token", joined)

    def test_parse_probe_result_extracts_pass_fail_and_redacted_trace_without_log_noise(self):
        text = """noise
I AiCallBridge: realtime_network_off_call_smoke_result:
I AiCallBridge: realtime_network_off_call_smoke=PASS
I AiCallBridge: reason=realtime_connected_off_call_media_rejected
I AiCallBridge: states=FETCHING_CREDENTIAL>CONNECTING_REALTIME>STARTING_MEDIA>FAILED
I AiCallBridge: trace=1@0:CONNECT_START;2@15:CONNECT_SUCCESS;3@17:CLOSED
"""
        result = parse_probe_result(text)
        self.assertEqual("PASS", result.status)
        self.assertEqual("realtime_connected_off_call_media_rejected", result.reason)
        self.assertIn("STARTING_MEDIA", result.states)
        self.assertEqual(
            "1@0:CONNECT_START;2@15:CONNECT_SUCCESS;3@17:CLOSED",
            result.trace,
        )
        self.assertIn("trace=1@0:CONNECT_START", result.render())

        self.assertIsNone(parse_probe_result("no terminal result yet"))


@dataclass
class RecordedCall:
    args: list[str]
    input_bytes: bytes


class RecordingRunner:
    def __init__(self):
        self.calls: list[RecordedCall] = []

    def run_bytes(self, args, *, input_bytes=b"", check=True):
        self.calls.append(RecordedCall(list(args), bytes(input_bytes)))
        return b""


if __name__ == "__main__":
    unittest.main()
