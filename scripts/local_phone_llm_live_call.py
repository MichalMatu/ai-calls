#!/usr/bin/env python3
"""One bounded allowlisted cellular call using the S22 local text-call pipeline."""

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
LOCAL_PHONE_PROVIDER = "LOCAL_PHONE_LLM"
EDGE_GALLERY_PROVIDER = "EDGE_GALLERY"
LIVE_TEXT_PROVIDERS = frozenset({LOCAL_PHONE_PROVIDER, EDGE_GALLERY_PROVIDER})
DEFAULT_SERIAL = "RFCT70L7E8J"
REPORT_PATH = "files/local-phone-llm-live-call-report.txt"
PROBE_TIMEOUT_SECONDS = 100.0
GATE_C_APPROVED_TEXT = "Dzień dobry."
GATE_C_IGNORABLE_PREROLLS = frozenset({"orange"})
MAX_GATE_C_PREROLL_RETRIES = 2


def normalize_allowlisted_target(raw: str) -> str:
    number = normalize_number(raw)
    if number not in ALLOWLIST:
        raise ValueError("target is not in the operator-defined live-test allowlist")
    return number


def normalize_provider(raw: str) -> str:
    provider = raw.strip().upper()
    if provider not in LIVE_TEXT_PROVIDERS:
        raise ValueError("provider is not enabled for the bounded local live-call path")
    return provider


def build_probe_start_args(
    serial: str,
    provider: str = LOCAL_PHONE_PROVIDER,
    *,
    gate_c_fast_path: bool = False,
    target: Optional[str] = None,
) -> list[str]:
    selected_provider = normalize_provider(provider)
    args = [
        "adb", "-s", serial, "shell", "am", "start", "-W", "-n", PROBE_ACTIVITY,
        "--ez", "run_local_phone_llm_live_call_probe", "true",
        "--es", "text_llm_provider", selected_provider,
    ]
    if gate_c_fast_path:
        if selected_provider != LOCAL_PHONE_PROVIDER:
            raise ValueError("Gate C live probe is restricted to LOCAL_PHONE_LLM diagnostics")
        selected_target = normalize_allowlisted_target(target or "")
        args += [
            "--ez", "gate_c_fast_path", "true",
            "--es", "live_call_target", selected_target,
        ]
    return args


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


def _require_gate_c_fast_path_report(report: dict[str, str]) -> None:
    if report.get("gate_c_fast_path") != "true":
        raise RuntimeError("Gate C live report did not confirm fast-path mode")
    if report.get("gate_c_call_plan_bound") != "true":
        raise RuntimeError("Gate C live report did not confirm bound CallPlan")
    try:
        backend_generate_calls = int(report.get("backend_generate_calls", "-1"))
    except ValueError as error:
        raise RuntimeError("Gate C live report has invalid backend generation count") from error
    if backend_generate_calls != 0:
        raise RuntimeError(
            f"Gate C live path reached forbidden backend generation: {backend_generate_calls}"
        )
    if report.get("approved_text") != GATE_C_APPROVED_TEXT:
        raise RuntimeError("Gate C live path did not approve the exact reviewed response")


def _is_ignorable_gate_c_preroll(report: dict[str, str]) -> bool:
    """Return true only for an exact observed, authority-neutral Orange branding pre-roll."""
    if report.get("gate_c_fast_path") != "true":
        return False
    if report.get("gate_c_call_plan_bound") != "true":
        return False
    if report.get("local_text_llm_live_call_success") != "false":
        return False
    if report.get("failure_reason") != "gate_c_take_over":
        return False
    try:
        if int(report.get("backend_generate_calls", "-1")) != 0:
            return False
    except ValueError:
        return False
    transcript = report.get("stt_text", "").strip().casefold()
    return transcript in GATE_C_IGNORABLE_PREROLLS


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
            print("--- local text live report ---")
            print(text, end="" if text.endswith("\n") else "\n")
            print("--- end local text live report ---")
            return parsed
        time.sleep(0.25)
    raise TimeoutError("local text live probe did not produce a terminal report")


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
    if not 0 < capture_ms < 60_000:
        raise RuntimeError(f"live endpoint capture reached the 60 s watchdog: {capture_ms} ms")
    latency_ms = int(report.get("end_of_speech_to_first_tx_ms", "-1"))
    if latency_ms < 0:
        raise RuntimeError("live report did not include end-of-speech to first-TX latency")
    print(f"live_endpointing_proven_s22=true,capture_ms:{capture_ms},eos_to_first_tx_ms:{latency_ms}")


def _start_probe(
    adb: Adb,
    serial: str,
    provider: str,
    *,
    gate_c_fast_path: bool,
    target: str,
) -> dict[str, str]:
    _remove_report(adb)
    adb.shell(["am", "force-stop", PACKAGE_NAME], check=False)
    subprocess.run(
        build_probe_start_args(
            serial,
            provider,
            gate_c_fast_path=gate_c_fast_path,
            target=target if gate_c_fast_path else None,
        ),
        check=True,
    )
    return _wait_report(adb)


def run_orange_support_once(
    serial: str = DEFAULT_SERIAL,
    provider: str = LOCAL_PHONE_PROVIDER,
    *,
    gate_c_fast_path: bool = False,
) -> dict[str, str]:
    selected_provider = normalize_provider(provider)
    number = normalize_allowlisted_target(ORANGE_SUPPORT_NUMBER)
    if gate_c_fast_path and selected_provider != LOCAL_PHONE_PROVIDER:
        raise ValueError("Gate C live probe is restricted to LOCAL_PHONE_LLM diagnostics")
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
        print(f"text_llm_provider={selected_provider}")
        print(f"gate_c_fast_path={str(gate_c_fast_path).lower()}")
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

        max_probe_turns = 1 + (MAX_GATE_C_PREROLL_RETRIES if gate_c_fast_path else 0)
        report: dict[str, str] | None = None
        for probe_turn in range(1, max_probe_turns + 1):
            report = _start_probe(
                adb,
                serial,
                selected_provider,
                gate_c_fast_path=gate_c_fast_path,
                target=number,
            )
            if report.get("text_llm_provider") != selected_provider:
                raise RuntimeError("live probe provider mismatch")

            success = report.get(
                "local_text_llm_live_call_success",
                report.get("local_phone_llm_live_call_success"),
            )
            if success == "true":
                break

            can_retry_preroll = (
                gate_c_fast_path and
                probe_turn < max_probe_turns and
                _is_ignorable_gate_c_preroll(report)
            )
            if can_retry_preroll:
                print(
                    "gate_c_preroll_ignored=true," +
                    f"turn:{probe_turn},transcript:{report.get('stt_text', '')}"
                )
                continue

            raise RuntimeError(
                "local text live probe reported failure: " + report.get("failure_reason", "unknown")
            )

        if report is None:
            raise RuntimeError("live probe produced no report")
        success = report.get(
            "local_text_llm_live_call_success",
            report.get("local_phone_llm_live_call_success"),
        )
        if success != "true":
            raise RuntimeError("local text live probe did not reach a successful terminal turn")
        if report.get("stt_transcript_nonblank") != "true":
            raise RuntimeError("live STT transcript was blank")
        if report.get("approved_text_nonblank") != "true":
            raise RuntimeError("live response was blank or not approved")
        if gate_c_fast_path:
            _require_gate_c_fast_path_report(report)
        if int(report.get("telephony_tx_pcm_bytes", "0")) <= 0:
            raise RuntimeError("live TTS produced no telephony TX bytes")
        _require_endpointing(report)
        print(f"local_text_llm_orange_live_turn_proven_s22=true,provider:{selected_provider}")
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
    gate_c_fast_path = False
    if "--gate-c-fast-path" in args:
        gate_c_fast_path = True
        args.remove("--gate-c-fast-path")
    if len(args) > 2:
        print(
            "usage: local_phone_llm_live_call.py [adb-serial] [LOCAL_PHONE_LLM|EDGE_GALLERY] "
            "[--gate-c-fast-path]",
            file=sys.stderr,
        )
        return 2
    serial = args[0] if args else DEFAULT_SERIAL
    provider = args[1] if len(args) == 2 else LOCAL_PHONE_PROVIDER
    try:
        run_orange_support_once(serial, provider, gate_c_fast_path=gate_c_fast_path)
        return 0
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"local phone live call failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
