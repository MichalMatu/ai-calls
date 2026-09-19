#!/usr/bin/env python3
"""One bounded allowlisted cellular call using the local S22 STT -> LLM -> TTS pipeline."""

from __future__ import annotations

import subprocess
import sys
import time
from pathlib import Path
from typing import Optional

from autonomous_call_loop import wait_for_active_call, wait_for_audio_signal, wait_for_idle
from realtime_live_call_smoke import validate_live_preflight
from realtime_network_smoke import PACKAGE_NAME, PROBE_ACTIVITY, is_direct_usb_target
from s22_call_control import Adb, normalize_number

ORANGE_SUPPORT_NUMBER = "510100100"
ALLOWLIST = frozenset({ORANGE_SUPPORT_NUMBER})
DEFAULT_SERIAL = "RFCT70L7E8J"
REPORT_PATH = "files/local-phone-llm-live-call-report.txt"
PROBE_TIMEOUT_SECONDS = 55.0


def normalize_allowlisted_target(raw: str) -> str:
    number = normalize_number(raw)
    if number not in ALLOWLIST:
        raise ValueError("target is not in the operator-defined live-test allowlist")
    return number


def build_probe_start_args(serial: str) -> list[str]:
    return [
        "adb", "-s", serial, "shell", "am", "start", "-W", "-n", PROBE_ACTIVITY,
        "--ez", "run_local_phone_llm_live_call_probe", "true",
    ]


def parse_probe_report(text: str) -> Optional[dict[str, str]]:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    if values.get("probe_complete") != "true":
        return None
    return values


def _devices_output() -> str:
    return subprocess.run(
        ["adb", "devices", "-l"], check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    ).stdout


def _wait_bluetooth(adb: Adb, expected: str, timeout: float = 8.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = adb.shell(["settings", "get", "global", "bluetooth_on"], check=False).strip()
        if value == expected:
            return
        time.sleep(0.25)
    raise RuntimeError(f"Bluetooth did not reach expected state {expected}")


def _set_bluetooth(adb: Adb, enabled: bool) -> None:
    action = "enable" if enabled else "disable"
    output = adb.shell(["cmd", "bluetooth_manager", action], check=False)
    if "Unknown command" in output or "not found" in output:
        raise RuntimeError(f"could not {action} Bluetooth through shell")
    _wait_bluetooth(adb, "1" if enabled else "0")


def _read_report(adb: Adb) -> str:
    return adb.shell(["run-as", PACKAGE_NAME, "cat", REPORT_PATH], check=False).replace("\r", "")


def _remove_report(adb: Adb) -> None:
    adb.shell(["run-as", PACKAGE_NAME, "rm", "-f", REPORT_PATH], check=False)


def _wait_report(adb: Adb, timeout: float = PROBE_TIMEOUT_SECONDS) -> dict[str, str]:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if adb.call_state() != 2:
            raise RuntimeError("cellular call ended before local live probe completed")
        text = _read_report(adb)
        parsed = parse_probe_report(text)
        if parsed is not None:
            print("--- local phone live report ---")
            print(text, end="" if text.endswith("\n") else "\n")
            print("--- end local phone live report ---")
            return parsed
        time.sleep(0.25)
    raise TimeoutError("local phone live probe did not produce a terminal report")


def _require_preflight(adb: Adb) -> None:
    devices = _devices_output()
    if not is_direct_usb_target(devices, adb.serial or ""):
        raise RuntimeError("local phone live call requires exact direct USB S22 target")
    bluetooth = adb.shell(["settings", "get", "global", "bluetooth_on"]).strip()
    call_state = adb.call_state()
    audio_dump = adb.shell(["dumpsys", "audio"])
    snapshot = validate_live_preflight(
        serial=adb.serial or "",
        devices_output=devices,
        bluetooth_setting=bluetooth,
        call_state=call_state if call_state is not None else -1,
        audio_dump=audio_dump,
    )
    print(
        "live_preflight=true," +
        f"mode:{snapshot.audio_mode},device:{snapshot.active_device_type},muted:{snapshot.voice_call_muted}"
    )


def _require_endpointing(report: dict[str, str]) -> None:
    if report.get("endpointing") != "trailing_silence":
        raise RuntimeError("live probe did not advertise trailing-silence endpointing")
    if report.get("endpoint_reason") != "trailing_silence":
        raise RuntimeError("live turn did not end on trailing silence")
    if report.get("endpoint_speech_detected") != "true":
        raise RuntimeError("live endpoint detector did not classify speech")
    capture_ms = int(report.get("endpoint_capture_ms", "0"))
    if not 0 < capture_ms < 8_000:
        raise RuntimeError(f"live endpoint capture was not early: {capture_ms} ms")
    latency_ms = int(report.get("end_of_speech_to_first_tx_ms", "-1"))
    if latency_ms < 0:
        raise RuntimeError("live report did not include end-of-speech to first-TX latency")
    print(f"live_endpointing_proven_s22=true,capture_ms:{capture_ms},eos_to_first_tx_ms:{latency_ms}")


def run_orange_support_once(serial: str = DEFAULT_SERIAL) -> dict[str, str]:
    number = normalize_allowlisted_target(ORANGE_SUPPORT_NUMBER)
    adb = Adb(serial)
    if not is_direct_usb_target(_devices_output(), serial):
        raise RuntimeError("target S22 is not connected through exact direct USB ADB")
    if adb.call_state() != 0:
        raise RuntimeError("refusing to dial because cellular call state is not IDLE")

    original_bt = adb.shell(["settings", "get", "global", "bluetooth_on"], check=False).strip()
    if original_bt not in {"0", "1"}:
        raise RuntimeError("could not determine Bluetooth state")
    changed_bt = False
    dialed = False
    muted = False
    started_at = time.monotonic()
    try:
        if original_bt == "1":
            _set_bluetooth(adb, False)
            changed_bt = True
            print("bluetooth_disabled_for_test=true")

        print(f"allowlisted_target={number}")
        adb.dial(number)
        dialed = True
        print("dial_requested=true")
        wait_for_active_call(adb, 30.0)

        adb.shell(["cmd", "audio", "adj-mute", "0"])
        muted = True
        time.sleep(0.3)
        _require_preflight(adb)

        signal = wait_for_audio_signal(adb, timeout_seconds=25.0)
        print(f"orange_downlink_signal=true,rms:{signal.rms:.3f},peak:{signal.peak}")

        if time.monotonic() - started_at > 60.0:
            raise TimeoutError("bounded call budget exhausted before AI turn")

        _remove_report(adb)
        adb.shell(["am", "force-stop", PACKAGE_NAME], check=False)
        subprocess.run(build_probe_start_args(serial), check=True)
        report = _wait_report(adb)
        if report.get("local_phone_llm_live_call_success") != "true":
            raise RuntimeError("local phone live probe reported failure: " + report.get("failure_reason", "unknown"))
        if report.get("stt_transcript_nonblank") != "true":
            raise RuntimeError("live STT transcript was blank")
        if report.get("approved_text_nonblank") != "true":
            raise RuntimeError("live LLM response was blank or not approved")
        if int(report.get("telephony_tx_pcm_bytes", "0")) <= 0:
            raise RuntimeError("live TTS produced no telephony TX bytes")
        _require_endpointing(report)
        print("local_phone_llm_orange_live_turn_proven_s22=true")
        return report
    finally:
        adb.shell(["am", "force-stop", PACKAGE_NAME], check=False)
        if dialed and adb.call_state() != 0:
            adb.hangup()
            print("hangup_requested=true")
            print(f"idle_after_hangup={wait_for_idle(adb, 10.0)}")
        if muted:
            adb.shell(["cmd", "audio", "adj-unmute", "0"], check=False)
            print("voice_call_unmute_cleanup_requested=true")
        if changed_bt:
            try:
                _set_bluetooth(adb, True)
                print("bluetooth_restored=true")
            except Exception as error:
                print(f"bluetooth_restore_error={error}", file=sys.stderr)


def main(argv: Optional[list[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) > 1:
        print("usage: local_phone_llm_live_call.py [adb-serial]", file=sys.stderr)
        return 2
    serial = args[0] if args else DEFAULT_SERIAL
    try:
        run_orange_support_once(serial)
        return 0
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"local phone live call failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
