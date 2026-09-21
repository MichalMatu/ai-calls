#!/usr/bin/env python3
"""One bounded allowlisted cellular call using the S22 local text-call pipeline."""

from __future__ import annotations

import subprocess
import sys
import time
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
GATE_C_IGNORABLE_PREROLLS = frozenset({"orange", "jakości orange", "5g jakości orange"})
MAX_GATE_C_PREROLL_RETRIES = 2

ORANGE_ACTION_GREETING = "greeting"
ORANGE_ACTION_LIST_CAPABILITIES = "list_capabilities"
ORANGE_ACTION_INVOICE_STATUS = "invoice_status"
ORANGE_ACTION_OBSERVE_ONLY = "observe_only"
ORANGE_LIVE_ACTIONS = frozenset({
    ORANGE_ACTION_GREETING,
    ORANGE_ACTION_LIST_CAPABILITIES,
    ORANGE_ACTION_INVOICE_STATUS,
    ORANGE_ACTION_OBSERVE_ONLY,
})
GATE_C_ROOT_ACQUISITION_ACTIONS = frozenset({
    ORANGE_ACTION_GREETING,
    ORANGE_ACTION_LIST_CAPABILITIES,
    ORANGE_ACTION_INVOICE_STATUS,
})
ORANGE_REVIEWED_RESPONSES = {
    ORANGE_ACTION_GREETING: "Dzień dobry.",
    ORANGE_ACTION_LIST_CAPABILITIES: "Jakie sprawy możesz załatwić?",
    ORANGE_ACTION_INVOICE_STATUS: "Chcę sprawdzić fakturę.",
}


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


def normalize_orange_action(raw: Optional[str]) -> str:
    action = (raw or ORANGE_ACTION_GREETING).strip().lower()
    if action not in ORANGE_LIVE_ACTIONS:
        raise ValueError("Orange live action is not in the reviewed explorer action set")
    return action


def build_probe_start_args(
    serial: str,
    provider: str = LOCAL_PHONE_PROVIDER,
    *,
    gate_c_fast_path: bool = False,
    target: Optional[str] = None,
    orange_action: Optional[str] = None,
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
        selected_action = normalize_orange_action(orange_action)
        args += [
            "--ez", "gate_c_fast_path", "true",
            "--es", "live_call_target", selected_target,
            "--es", "orange_live_action", selected_action,
        ]
    elif orange_action is not None:
        raise ValueError("Orange live action requires Gate C fast-path mode")
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


def _backend_generate_calls(report: dict[str, str]) -> int:
    try:
        return int(report.get("backend_generate_calls", "-1"))
    except ValueError as error:
        raise RuntimeError("Gate C live report has invalid backend generation count") from error


def _require_gate_c_base(report: dict[str, str], expected_action: str) -> None:
    if report.get("gate_c_fast_path") != "true":
        raise RuntimeError("Gate C live report did not confirm fast-path mode")
    if report.get("gate_c_call_plan_bound") != "true":
        raise RuntimeError("Gate C live report did not confirm bound CallPlan")
    if report.get("orange_live_action") != expected_action:
        raise RuntimeError("Gate C live report action does not match requested Orange action")
    backend_generate_calls = _backend_generate_calls(report)
    if backend_generate_calls != 0:
        raise RuntimeError(
            f"Gate C live path reached forbidden backend generation: {backend_generate_calls}"
        )


def _require_gate_c_fast_path_report(
    report: dict[str, str],
    expected_action: str = ORANGE_ACTION_GREETING,
) -> None:
    selected_action = normalize_orange_action(expected_action)
    if selected_action == ORANGE_ACTION_OBSERVE_ONLY:
        raise RuntimeError("observe-only action cannot be validated as a speech-producing Gate C turn")
    _require_gate_c_base(report, selected_action)
    expected_text = ORANGE_REVIEWED_RESPONSES[selected_action]
    if report.get("approved_text") != expected_text:
        raise RuntimeError("Gate C live path did not approve the exact reviewed response")


def _require_gate_c_observation_report(report: dict[str, str]) -> None:
    _require_gate_c_base(report, ORANGE_ACTION_OBSERVE_ONLY)
    if report.get("local_text_llm_live_call_success") != "false":
        raise RuntimeError("observe-only Gate C turn unexpectedly reported success")
    if report.get("failure_reason") != "gate_c_take_over":
        raise RuntimeError("observe-only Gate C turn did not fail closed to TAKE_OVER")
    if report.get("stt_transcript_nonblank") != "true" or not report.get("stt_text", "").strip():
        raise RuntimeError("observe-only Gate C turn produced no final transcript")
    if report.get("endpointing") != "trailing_silence":
        raise RuntimeError("observe-only Gate C turn did not advertise trailing-silence endpointing")
    if report.get("endpoint_reason") != "trailing_silence":
        raise RuntimeError("observe-only Gate C turn did not end on trailing silence")
    if report.get("endpoint_speech_detected") != "true":
        raise RuntimeError("observe-only Gate C turn did not classify speech")
    try:
        capture_ms = int(report.get("endpoint_capture_ms", "0"))
    except ValueError as error:
        raise RuntimeError("observe-only Gate C report has invalid capture duration") from error
    if not 0 < capture_ms < 60_000:
        raise RuntimeError(f"observe-only capture reached the 60 s watchdog: {capture_ms} ms")


def _is_ignorable_gate_c_preroll(report: dict[str, str]) -> bool:
    """Return true only for an exact observed, authority-neutral Orange root-acquisition fragment."""
    if report.get("gate_c_fast_path") != "true":
        return False
    if report.get("gate_c_call_plan_bound") != "true":
        return False
    if report.get("orange_live_action") not in GATE_C_ROOT_ACQUISITION_ACTIONS:
        return False
    if report.get("local_text_llm_live_call_success") != "false":
        return False
    if report.get("failure_reason") != "gate_c_take_over":
        return False
    try:
        if _backend_generate_calls(report) != 0:
            return False
    except RuntimeError:
        return False
    transcript = report.get("stt_text", "").strip().casefold()
    return transcript in GATE_C_IGNORABLE_PREROLLS


def _is_retryable_gate_c_root_no_match(report: dict[str, str]) -> bool:
    """Retry only SpeechRecognizer NO_MATCH after real speech during root acquisition."""
    if report.get("gate_c_fast_path") != "true":
        return False
    if report.get("gate_c_call_plan_bound") != "true":
        return False
    if report.get("orange_live_action") not in GATE_C_ROOT_ACQUISITION_ACTIONS:
        return False
    if report.get("local_text_llm_live_call_success") != "false":
        return False
    if report.get("failure_reason") != "stt_recognition_error_7":
        return False
    if report.get("endpoint_reason") != "trailing_silence":
        return False
    if report.get("endpoint_speech_detected") != "true":
        return False
    try:
        return _backend_generate_calls(report) == 0
    except RuntimeError:
        return False


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
    orange_action: Optional[str] = None,
) -> dict[str, str]:
    _remove_report(adb)
    adb.shell(["am", "force-stop", PACKAGE_NAME], check=False)
    subprocess.run(
        build_probe_start_args(
            serial,
            provider,
            gate_c_fast_path=gate_c_fast_path,
            target=target if gate_c_fast_path else None,
            orange_action=orange_action,
        ),
        check=True,
    )
    return _wait_report(adb)


def run_orange_support_once(
    serial: str = DEFAULT_SERIAL,
    provider: str = LOCAL_PHONE_PROVIDER,
    *,
    gate_c_fast_path: bool = False,
    orange_action: Optional[str] = None,
    observe_next: bool = False,
) -> dict[str, str]:
    selected_provider = normalize_provider(provider)
    number = normalize_allowlisted_target(ORANGE_SUPPORT_NUMBER)
    selected_action = normalize_orange_action(orange_action) if gate_c_fast_path else None
    if gate_c_fast_path and selected_provider != LOCAL_PHONE_PROVIDER:
        raise ValueError("Gate C live probe is restricted to LOCAL_PHONE_LLM diagnostics")
    if not gate_c_fast_path and (orange_action is not None or observe_next):
        raise ValueError("Orange Explorer actions require Gate C fast-path mode")
    if selected_action == ORANGE_ACTION_OBSERVE_ONLY and observe_next:
        raise ValueError("observe-only primary action cannot request another observe-next turn")

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
        if selected_action is not None:
            print(f"orange_live_action={selected_action}")
        print(f"orange_observe_next={str(observe_next).lower()}")
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
                orange_action=selected_action,
            )
            if report.get("text_llm_provider") != selected_provider:
                raise RuntimeError("live probe provider mismatch")

            success = report.get(
                "local_text_llm_live_call_success",
                report.get("local_phone_llm_live_call_success"),
            )
            if success == "true":
                break

            can_retry_root_acquisition = (
                gate_c_fast_path and
                probe_turn < max_probe_turns and
                (
                    _is_ignorable_gate_c_preroll(report) or
                    _is_retryable_gate_c_root_no_match(report)
                )
            )
            if can_retry_root_acquisition:
                print(
                    "gate_c_root_acquisition_retry=true," +
                    f"turn:{probe_turn},reason:{report.get('failure_reason', '')}," +
                    f"transcript:{report.get('stt_text', '')}"
                )
                continue

            if selected_action == ORANGE_ACTION_OBSERVE_ONLY:
                _require_gate_c_observation_report(report)
                print(f"orange_observation_text={report.get('stt_text', '').strip()}")
                return report

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
            assert selected_action is not None
            _require_gate_c_fast_path_report(report, selected_action)
        if int(report.get("telephony_tx_pcm_bytes", "0")) <= 0:
            raise RuntimeError("live TTS produced no telephony TX bytes")
        _require_endpointing(report)
        print(f"local_text_llm_orange_live_turn_proven_s22=true,provider:{selected_provider}")

        if observe_next:
            if adb.call_state() != 2:
                raise RuntimeError("Orange call ended before observe-next turn")
            observation = _start_probe(
                adb,
                serial,
                selected_provider,
                gate_c_fast_path=True,
                target=number,
                orange_action=ORANGE_ACTION_OBSERVE_ONLY,
            )
            _require_gate_c_observation_report(observation)
            print(f"orange_observation_text={observation.get('stt_text', '').strip()}")
            report = dict(report)
            report["orange_observation_text"] = observation.get("stt_text", "").strip()
            report["orange_observation_capture_ms"] = observation.get("endpoint_capture_ms", "")

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


def _pop_option(args: list[str], name: str) -> Optional[str]:
    if name not in args:
        return None
    index = args.index(name)
    if index + 1 >= len(args):
        raise ValueError(f"{name} requires a value")
    value = args[index + 1]
    del args[index:index + 2]
    return value


def main(argv: Optional[list[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    try:
        gate_c_fast_path = False
        if "--gate-c-fast-path" in args:
            gate_c_fast_path = True
            args.remove("--gate-c-fast-path")

        observe_next = False
        if "--observe-next" in args:
            observe_next = True
            args.remove("--observe-next")

        orange_action = _pop_option(args, "--orange-action")
        if len(args) > 2:
            raise ValueError(
                "usage: local_phone_llm_live_call.py [adb-serial] "
                "[LOCAL_PHONE_LLM|EDGE_GALLERY] [--gate-c-fast-path] "
                "[--orange-action greeting|list_capabilities|invoice_status|observe_only] [--observe-next]"
            )
        serial = args[0] if args else DEFAULT_SERIAL
        provider = args[1] if len(args) == 2 else LOCAL_PHONE_PROVIDER
        run_orange_support_once(
            serial,
            provider,
            gate_c_fast_path=gate_c_fast_path,
            orange_action=orange_action,
            observe_next=observe_next,
        )
        return 0
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"local phone live call failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
