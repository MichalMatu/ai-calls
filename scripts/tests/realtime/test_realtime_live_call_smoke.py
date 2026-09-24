import unittest
from dataclasses import dataclass

from aicall_tools.realtime.realtime_live_call_smoke import (
    LIVE_RESULT_PREFIX,
    build_probe_start_args,
    parse_probe_result,
    run_live_smoke,
    validate_live_preflight,
)
from aicall_tools.realtime.realtime_network_smoke import SmokeEnvironment


SAFE_DEVICES = """List of devices attached
RFCT70L7E8J device usb:18874368X product:g0sxeea model:SM_S906B device:g0s transport_id:3
"""
SAFE_AUDIO = """- STREAM_VOICE_CALL:
   Muted: true
   Muted Internally: false
  Active communication device: AudioDeviceAttributes: role:output type:earpiece addr: name:SM-S906B
  mAudioModeOwner: AudioModeInfo: mMode=MODE_IN_CALL, mPid=1759, mUid=1000
"""


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

    def test_safe_live_preflight_requires_exact_physical_conditions(self):
        snapshot = validate_live_preflight(
            serial="RFCT70L7E8J",
            devices_output=SAFE_DEVICES,
            bluetooth_setting="0\n",
            call_state=2,
            audio_dump=SAFE_AUDIO,
        )

        self.assertTrue(snapshot.direct_usb)
        self.assertFalse(snapshot.bluetooth_enabled)
        self.assertEqual(2, snapshot.call_state)
        self.assertEqual("MODE_IN_CALL", snapshot.audio_mode)
        self.assertEqual("earpiece", snapshot.active_device_type)
        self.assertTrue(snapshot.voice_call_muted)

    def test_live_preflight_fails_closed_for_each_unsafe_condition(self):
        cases = [
            (
                "direct USB",
                dict(devices_output="RFCT70L7E8J device product:g0sxeea model:SM_S906B\n"),
            ),
            ("Bluetooth OFF", dict(bluetooth_setting="1\n")),
            ("CALL_STATE=2", dict(call_state=0)),
            ("MODE_IN_CALL", dict(audio_dump=SAFE_AUDIO.replace("MODE_IN_CALL", "MODE_NORMAL"))),
            ("earpiece", dict(audio_dump=SAFE_AUDIO.replace("type:earpiece", "type:speaker"))),
            ("voice-call stream muted", dict(audio_dump=SAFE_AUDIO.replace("Muted: true", "Muted: false", 1))),
        ]

        for expected, changes in cases:
            values = dict(
                serial="RFCT70L7E8J",
                devices_output=SAFE_DEVICES,
                bluetooth_setting="0\n",
                call_state=2,
                audio_dump=SAFE_AUDIO,
            )
            values.update(changes)
            with self.subTest(expected=expected):
                with self.assertRaisesRegex(RuntimeError, expected):
                    validate_live_preflight(**values)

    def test_non_active_call_refuses_before_secret_staging(self):
        runner = ScriptedRunner(call_state=0)
        env = environment()

        with self.assertRaisesRegex(RuntimeError, "CALL_STATE=2"):
            run_live_smoke("RFCT70L7E8J", env, runner=runner, duration_ms=5_000)

        joined_calls = [" ".join(call.args) for call in runner.calls]
        self.assertTrue(any("adb devices -l" in call for call in joined_calls))
        self.assertTrue(any("settings get global bluetooth_on" in call for call in joined_calls))
        self.assertTrue(any("dumpsys telephony.registry" in call for call in joined_calls))
        self.assertFalse(any("cat > files/realtime-network-smoke.json" in call for call in joined_calls))
        self.assertFalse(any(env.broker_token in call for call in joined_calls))

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
        runner = ScriptedRunner(call_state=2)
        with self.assertRaises(ValueError):
            run_live_smoke("RFCT70L7E8J", environment(), runner=runner, duration_ms=60_000)
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
    def __init__(self, *, call_state=2, bluetooth="0", audio_dump=SAFE_AUDIO, devices=SAFE_DEVICES):
        self.call_state = call_state
        self.bluetooth = bluetooth
        self.audio_dump = audio_dump
        self.devices = devices
        self.calls: list[RecordedCall] = []

    def run_bytes(self, args, *, input_bytes=b"", check=True):
        args = list(args)
        self.calls.append(RecordedCall(args, bytes(input_bytes)))
        joined = " ".join(args)
        if args == ["adb", "devices", "-l"]:
            return self.devices.encode()
        if "settings get global bluetooth_on" in joined:
            return f"{self.bluetooth}\n".encode()
        if "dumpsys telephony.registry" in joined:
            return f"mCallState={self.call_state}\n".encode()
        if "dumpsys audio" in joined:
            return self.audio_dump.encode()
        return b""


if __name__ == "__main__":
    unittest.main()
