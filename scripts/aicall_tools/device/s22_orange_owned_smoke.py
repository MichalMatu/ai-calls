#!/usr/bin/env python3
"""One user-authorized Orange acceptance call on the exact S22, with owned STOP.

Never uses device-global endCall, KEYCODE_ENDCALL, recordings, or user data.
Requires the exact default voice SIM, debuggable app and one protected USB action.
If Telecom does not bind our Call, fail closed and require a human end-call.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import time

from aicall_tools.diagnostics.ai_calls_usb_debug import Device, DiagnosticError
from aicall_tools.diagnostics.ai_calls_usb_lab import device_request
from aicall_tools.device.s22_supervised_readiness import collect

TARGET = "510100100"
MAX_PROBE_SECONDS = 21.0


def _metadata(device: Device) -> dict:
    record = device_request(device, "orange_smoke_status")
    if record.get("ok") is not True or not isinstance(record.get("data"), dict):
        raise DiagnosticError("probe_status_unavailable")
    safe = record["data"]
    for field in ("call_idle", "session_present", "exact_call_bound",
                  "exact_call_active", "stop_requested", "watchdog_fired"):
        if not isinstance(safe.get(field), bool):
            raise DiagnosticError("invalid_probe_status")
    return safe


def run_once(device: Device) -> dict:
    device.safe_preflight()
    if not _metadata(device)["call_idle"]:
        raise DiagnosticError("phone_not_idle")
    started = False
    bound = False
    became_active = False
    audio = "not_observed"
    last = "none"
    stop_ok = False
    final_idle = False
    begin = time.monotonic()
    try:
        start = device_request(device, "orange_smoke_start",
                               target=TARGET, approved=True)
        last = str(start.get("code", "unknown"))
        if start.get("ok") is not True:
            return {"started": False, "start_code": last, "phone_idle_after": True}
        started = True
        print("DIAL_REQUESTED=true", flush=True)
        while time.monotonic() - begin < MAX_PROBE_SECONDS:
            state = _metadata(device)
            last = str(state.get("last_code", "unknown"))
            bound |= state["exact_call_bound"]
            became_active |= state["exact_call_active"]
            if bound and became_active:
                # Audio routing is only inspected, never streamed or stored.
                try:
                    route = collect(device.endpoint, timeout_seconds=7)
                    audio = route["audio_mode"]
                    print("LIVE_AUDIO_MODE=" + audio, flush=True)
                    print("SHIZUKU_IN_CALL=" + route["shizuku_ready"], flush=True)
                    print("TELECOM_MANAGED_CALL=" + route["telecom_managed_call"], flush=True)
                except Exception as exc:
                    audio = "unavailable_" + type(exc).__name__
                break
            if state["call_idle"] and time.monotonic() - begin > 6:
                break
            time.sleep(0.6)
    finally:
        if started:
            try:
                stopped = device_request(device, "orange_smoke_stop")
                stop_ok = stopped.get("ok") is True
                print("EXACT_STOP_REQUESTED=" + str(stop_ok).lower(), flush=True)
            except Exception as exc:
                print("EXACT_STOP_ERROR=" + type(exc).__name__, flush=True)
            for _ in range(20):
                try:
                    if _metadata(device)["call_idle"] and device.idle():
                        final_idle = True
                        break
                except Exception:
                    pass
                time.sleep(0.45)
            print("POSTCALL_IDLE=" + str(final_idle).lower(), flush=True)
            if not final_idle:
                print("MANUAL_CALL_END_REQUIRED=true", flush=True)
    return {
        "started": started,
        "exact_bound": bound,
        "exact_active": became_active,
        "audio_mode": audio,
        "stop_requested": stop_ok,
        "phone_idle_after": final_idle,
        "last_code": last,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-one-real-call", action="store_true")
    parser.add_argument("--adb", default=os.environ.get(
        "ADB", str(Path.home() / "Library/Android/sdk/platform-tools/adb")))
    args = parser.parse_args()
    if not args.allow_one_real_call:
        print("ERROR=explicit_real_call_opt_in_required")
        return 2
    try:
        result = run_once(Device(args.adb))
    except (RuntimeError, ValueError, OSError) as exc:
        print("SMOKE_FAILED=" + type(exc).__name__)
        return 1
    # No phone number, transcript, SIM identity or audio enters the receipt.
    print("SAFE_RESULT=" + json.dumps(result, sort_keys=True))
    return 0 if result.get("started") and result.get("exact_bound") and \
        result.get("exact_active") and result.get("phone_idle_after") else 1


if __name__ == "__main__":
    raise SystemExit(main())
