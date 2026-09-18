#!/usr/bin/env python3
"""Run a bounded Realtime probe inside an already-active cellular call.

This tool never dials, answers or hangs up. Before any secret is staged it requires the proven lab
shape: direct USB ADB, Bluetooth off, active cellular call, MODE_IN_CALL, earpiece route and a muted
VOICE_CALL stream. On host-side failure it force-stops only this app as a final injection fail-safe;
the cellular call remains owned by the phone/dialer.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import Optional

from realtime_network_smoke import (
    ByteRunner,
    CALL_STATE_RE,
    CONFIG_PATH,
    PACKAGE_NAME,
    PROBE_ACTIVITY,
    SmokeEnvironment,
    SubprocessRunner,
    is_direct_usb_target,
    stage_private_config,
)

LIVE_RESULT_PREFIX = "realtime_live_call_smoke="
DEFAULT_DURATION_MS = 10_000
MIN_DURATION_MS = 3_000
MAX_DURATION_MS = 30_000
HELPER_PROCESS = f"{PACKAGE_NAME}:call_media"
_AUDIO_MODE_RE = re.compile(r"mAudioModeOwner:.*?mMode=([A-Z_]+)")
_ACTIVE_DEVICE_RE = re.compile(r"Active communication device:.*?\btype:([A-Za-z0-9_]+)")
_MUTED_RE = re.compile(r"^\s*Muted:\s*(true|false)\s*$", re.IGNORECASE)


@dataclass(frozen=True)
class LiveSmokeResult:
    status: str
    reason: str
    states: tuple[str, ...]
    detail: Optional[str] = None
    trace: Optional[str] = None

    def render(self) -> str:
        lines = [
            f"{LIVE_RESULT_PREFIX}{self.status}",
            f"reason={self.reason}",
            f"states={'>'.join(self.states) if self.states else 'none'}",
        ]
        if self.detail:
            lines.append(f"detail={self.detail}")
        if self.trace:
            lines.append(f"trace={self.trace}")
        return "\n".join(lines)


@dataclass(frozen=True)
class LivePreflightSnapshot:
    direct_usb: bool
    bluetooth_enabled: bool
    call_state: int
    audio_mode: str
    active_device_type: str
    voice_call_muted: bool


def validate_duration_ms(duration_ms: int) -> int:
    if duration_ms < MIN_DURATION_MS or duration_ms > MAX_DURATION_MS:
        raise ValueError(
            f"live Realtime probe duration must be {MIN_DURATION_MS}..{MAX_DURATION_MS} ms"
        )
    return duration_ms


def build_probe_start_args(serial: str, duration_ms: int) -> list[str]:
    duration = validate_duration_ms(duration_ms)
    return [
        "adb",
        "-s",
        serial,
        "shell",
        "am",
        "start",
        "-n",
        PROBE_ACTIVITY,
        "--ez",
        "run_realtime_live_call_smoke",
        "true",
        "--el",
        "realtime_live_duration_ms",
        str(duration),
    ]


def parse_probe_result(text: str) -> Optional[LiveSmokeResult]:
    status: Optional[str] = None
    reason: Optional[str] = None
    states: tuple[str, ...] = ()
    detail: Optional[str] = None
    trace: Optional[str] = None

    for raw_line in text.splitlines():
        marker = raw_line.find(LIVE_RESULT_PREFIX)
        if marker >= 0:
            status = raw_line[marker + len(LIVE_RESULT_PREFIX) :].strip()
            continue
        for key in ("reason=", "states=", "detail=", "trace="):
            marker = raw_line.find(key)
            if marker < 0:
                continue
            value = raw_line[marker + len(key) :].strip()
            if key == "reason=":
                reason = value
            elif key == "states=":
                states = () if value in {"", "none"} else tuple(value.split(">"))
            elif key == "detail=":
                detail = value
            else:
                trace = value
            break

    if status is None or reason is None:
        return None
    return LiveSmokeResult(status=status, reason=reason, states=states, detail=detail, trace=trace)


def _voice_call_muted(audio_dump: str) -> Optional[bool]:
    lines = audio_dump.splitlines()
    for index, raw_line in enumerate(lines):
        if raw_line.strip() != "- STREAM_VOICE_CALL:":
            continue
        for candidate in lines[index + 1 : index + 16]:
            match = _MUTED_RE.match(candidate)
            if match is not None:
                return match.group(1).lower() == "true"
        return None
    return None


def validate_live_preflight(
    *,
    serial: str,
    devices_output: str,
    bluetooth_setting: str,
    call_state: int,
    audio_dump: str,
) -> LivePreflightSnapshot:
    direct_usb = is_direct_usb_target(devices_output, serial)
    if not direct_usb:
        raise RuntimeError("Realtime live-call smoke requires direct USB ADB to the target S22+")

    bluetooth_value = bluetooth_setting.strip()
    if bluetooth_value not in {"0", "1"}:
        raise RuntimeError("could not determine Bluetooth state")
    bluetooth_enabled = bluetooth_value == "1"
    if bluetooth_enabled:
        raise RuntimeError("Realtime live-call smoke requires Bluetooth OFF")

    if call_state != 2:
        raise RuntimeError("Realtime live-call smoke requires CALL_STATE=2")

    mode_match = _AUDIO_MODE_RE.search(audio_dump)
    audio_mode = mode_match.group(1) if mode_match is not None else "UNKNOWN"
    if audio_mode != "MODE_IN_CALL":
        raise RuntimeError("Realtime live-call smoke requires MODE_IN_CALL")

    device_match = _ACTIVE_DEVICE_RE.search(audio_dump)
    active_device_type = device_match.group(1).lower() if device_match is not None else "unknown"
    if active_device_type != "earpiece":
        raise RuntimeError("Realtime live-call smoke requires active communication device earpiece")

    voice_call_muted = _voice_call_muted(audio_dump)
    if voice_call_muted is not True:
        raise RuntimeError("Realtime live-call smoke requires voice-call stream muted")

    return LivePreflightSnapshot(
        direct_usb=direct_usb,
        bluetooth_enabled=bluetooth_enabled,
        call_state=call_state,
        audio_mode=audio_mode,
        active_device_type=active_device_type,
        voice_call_muted=voice_call_muted,
    )


def _adb_text(runner: ByteRunner, serial: str, *shell_args: str, check: bool = True) -> str:
    return runner.run_bytes(
        ["adb", "-s", serial, "shell", *shell_args],
        check=check,
    ).decode("utf-8", errors="replace")


def _call_state(runner: ByteRunner, serial: str) -> int:
    registry = _adb_text(runner, serial, "dumpsys", "telephony.registry")
    match = CALL_STATE_RE.search(registry)
    if match is None:
        raise RuntimeError("could not determine cellular call state")
    return int(match.group(1))


def _require_live_preflight(runner: ByteRunner, serial: str) -> LivePreflightSnapshot:
    devices_output = runner.run_bytes(["adb", "devices", "-l"]).decode(
        "utf-8", errors="replace"
    )
    if not is_direct_usb_target(devices_output, serial):
        raise RuntimeError("Realtime live-call smoke requires direct USB ADB to the target S22+")

    bluetooth_setting = _adb_text(runner, serial, "settings", "get", "global", "bluetooth_on")
    bluetooth_value = bluetooth_setting.strip()
    if bluetooth_value not in {"0", "1"}:
        raise RuntimeError("could not determine Bluetooth state")
    if bluetooth_value != "0":
        raise RuntimeError("Realtime live-call smoke requires Bluetooth OFF")

    call_state = _call_state(runner, serial)
    if call_state != 2:
        raise RuntimeError("Realtime live-call smoke requires CALL_STATE=2")

    audio_dump = _adb_text(runner, serial, "dumpsys", "audio")
    return validate_live_preflight(
        serial=serial,
        devices_output=devices_output,
        bluetooth_setting=bluetooth_setting,
        call_state=call_state,
        audio_dump=audio_dump,
    )


def _config_exists(runner: ByteRunner, serial: str) -> bool:
    output = runner.run_bytes(
        [
            "adb",
            "-s",
            serial,
            "shell",
            "run-as",
            PACKAGE_NAME,
            "sh",
            "-c",
            f"test -e {CONFIG_PATH}; echo $?",
        ],
        check=False,
    ).decode("utf-8", errors="replace").strip()
    return output.endswith("0")


def _remove_private_config(runner: ByteRunner, serial: str) -> None:
    runner.run_bytes(
        [
            "adb",
            "-s",
            serial,
            "shell",
            "run-as",
            PACKAGE_NAME,
            "rm",
            "-f",
            CONFIG_PATH,
        ],
        check=False,
    )


def _helper_pid(runner: ByteRunner, serial: str) -> str:
    return _adb_text(runner, serial, "pidof", HELPER_PROCESS, check=False).strip()


def _force_stop_app(runner: ByteRunner, serial: str) -> None:
    runner.run_bytes(
        ["adb", "-s", serial, "shell", "am", "force-stop", PACKAGE_NAME],
        check=False,
    )


def run_live_smoke(
    serial: str,
    environment: SmokeEnvironment,
    *,
    runner: Optional[ByteRunner] = None,
    duration_ms: int = DEFAULT_DURATION_MS,
    timeout_seconds: float = 45.0,
) -> LiveSmokeResult:
    duration = validate_duration_ms(duration_ms)
    executor = runner or SubprocessRunner()
    _require_live_preflight(executor, serial)

    staged = False
    try:
        stage_private_config(
            executor,
            serial,
            environment.credential_endpoint,
            environment.broker_token,
        )
        staged = True
        executor.run_bytes(["adb", "-s", serial, "logcat", "-c"])
        executor.run_bytes(build_probe_start_args(serial, duration))

        deadline = time.monotonic() + timeout_seconds
        result: Optional[LiveSmokeResult] = None
        while time.monotonic() < deadline:
            output = executor.run_bytes(
                ["adb", "-s", serial, "logcat", "-d", "-s", "AiCallBridge:I", "*:S"]
            ).decode("utf-8", errors="replace")
            result = parse_probe_result(output)
            if result is not None:
                break
            time.sleep(0.25)

        if result is None:
            raise TimeoutError("Realtime live-call smoke did not produce a terminal result")
        if _call_state(executor, serial) != 2:
            raise RuntimeError("cellular call did not remain active during Realtime live-call smoke")
        if _config_exists(executor, serial):
            raise RuntimeError("one-shot Realtime smoke config was not deleted")
        if _helper_pid(executor, serial):
            raise RuntimeError("call-media helper remained alive after Realtime live-call smoke")
        return result
    except BaseException:
        if staged:
            try:
                _force_stop_app(executor, serial)
            except BaseException:
                pass
        raise
    finally:
        if staged:
            try:
                _remove_private_config(executor, serial)
            except BaseException:
                pass


def main(argv: Optional[list[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) not in {1, 2} or not args[0].strip():
        print("usage: realtime_live_call_smoke.py <adb-serial> [duration-ms]", file=sys.stderr)
        return 2

    try:
        duration_ms = DEFAULT_DURATION_MS if len(args) == 1 else int(args[1])
        environment = SmokeEnvironment.from_mapping(os.environ)
        result = run_live_smoke(args[0].strip(), environment, duration_ms=duration_ms)
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"realtime live-call smoke failed: {error}", file=sys.stderr)
        return 1

    print(result.render())
    return 0 if result.status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
