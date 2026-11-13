#!/usr/bin/env python3
"""One approved S22 Orange call: exact Telecom owner + supervised STT/TTS + STOP.

Never uses global hangup, saves/transmits audio or logs raw UI/text/phone data.
A UI failure aborts the pilot and still requests exact-owned call teardown.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import time
import uuid
import xml.etree.ElementTree as ET

from aicall_tools.diagnostics.ai_calls_usb_debug import Device, DiagnosticError
from aicall_tools.diagnostics.ai_calls_usb_lab import (
    device_request, last_call_technical_summary,
)

PACKAGE = "pl.michalmatu.aicallbridge"
PILOT = PACKAGE + "/.developerrelay.SupervisedCallPilotActivity"
START = "Uruchom AI w trwającej rozmowie"
STOP = "STOP AI — wróć do rozmowy ręcznej"
SCRIPT = "Rozmowa z Gemmą 4 (lokalnie)"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def checked(device: Device, *args: str, timeout: int = 35) -> str:
    result = device.call(*args, timeout=timeout)
    if result.returncode:
        raise DiagnosticError("device_ui_command_failed")
    return result.stdout


def controls(device: Device) -> dict[str, tuple[int, int, bool]]:
    filename = "/data/local/tmp/aicalls-orange-ui-" + uuid.uuid4().hex + ".xml"
    try:
        checked(device, "shell", "uiautomator", "dump", filename, timeout=40)
        raw = checked(device, "shell", "cat", filename)
        root = ET.fromstring(raw.strip())
        found = {}
        for node in root.iter("node"):
            title = node.attrib.get("text", "")
            if title not in {START, STOP, SCRIPT} or node.attrib.get("enabled") != "true":
                continue
            box = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
            if box:
                x1, y1, x2, y2 = map(int, box.groups())
                if x2>x1 and y2>y1:
                    found[title] = ((x1+x2)//2, (y1+y2)//2,
                                    node.attrib.get("checked") == "true")
        return found
    finally:
        device.call("shell", "rm", "-f", filename, timeout=15)


def tap(device: Device, at: tuple[int, int, bool]) -> None:
    checked(device, "shell", "input", "tap", str(at[0]), str(at[1]))


def snapshot(device: Device) -> dict:
    response = device_request(device, "orange_smoke_status")
    data = response.get("data")
    if response.get("ok") is not True or not isinstance(data, dict):
        raise DiagnosticError("probe_state_unavailable")
    return data


def flow(device: Device, *, gemma: bool = False) -> dict:
    device.safe_preflight()
    if not snapshot(device).get("call_idle"): raise DiagnosticError("phone_not_idle")
    began = False
    pilot_started = False
    pilot_stopped = False
    exact_bound = False
    exact_active = False
    ended_idle = False
    report: dict = {"available": False}
    begin = time.monotonic()
    try:
        response = device_request(device, "orange_smoke_start",
                                  target="510100100", approved=True)
        if response.get("ok") is not True:
            print("DIAL_REFUSED=" + str(response.get("code", "unknown")), flush=True)
            return {"dial_accepted": False, "refusal": str(response.get("code", "unknown"))}
        began = True
        print("DIAL_ACCEPTED=true", flush=True)
        for _ in range(22):
            state = snapshot(device)
            exact_bound |= state.get("exact_call_bound") is True
            exact_active |= state.get("exact_call_active") is True
            if exact_bound and exact_active: break
            if state.get("call_idle") and time.monotonic()-begin > 6: break
            time.sleep(0.35)
        print("EXACT_BOUND="+str(exact_bound).lower(),flush=True)
        print("EXACT_ACTIVE="+str(exact_active).lower(),flush=True)
        if not exact_bound or not exact_active:
            raise DiagnosticError("exact_call_not_active_no_audio_attempt")

        checked(device, "shell", "am", "start", "-W", "-n", PILOT, timeout=25)
        widgets = {}
        for _ in range(3):
            widgets = controls(device)
            if START in widgets: break
            time.sleep(0.5)
        if START not in widgets: raise DiagnosticError("supervised_pilot_ui_not_visible")
        checkbox = widgets.get(SCRIPT)
        if checkbox is None:
            raise DiagnosticError("gemma_mode_checkbox_missing")
        if checkbox[2] != gemma:
            tap(device, checkbox)
            widgets = controls(device)
        tap(device, widgets[START])
        pilot_started = True
        print("PILOT_MODE="+("GEMMA_4" if gemma else "SCRIPT"),flush=True)
        print("PILOT_START_TAPPED=true",flush=True)
        # A bounded live period; only technical report counters are collected.
        # Allow an actual IVR utterance to finish before expecting STT/TTS.
        # The Android-owned watchdog independently caps this call at 55 seconds.
        for _ in range(46):
            time.sleep(0.65)
            if snapshot(device).get("call_idle"): break
        widgets = controls(device)
        if STOP in widgets:
            tap(device, widgets[STOP])
            pilot_stopped = True
        print("PILOT_STOP_TAPPED="+str(pilot_stopped).lower(),flush=True)
        # A prior report may exist. Do not infer a new live RX/TX session
        # from a historical artifact if the current STOP has not persisted.
        time.sleep(1)
        report = last_call_technical_summary(device)
        print("SAFE_AUDIO_METRICS="+str({k:v for k,v in report.items()
            if k in {"available","turns_completed","telephony_rx_pcm_bytes",
                     "telephony_tx_pcm_bytes","chat_relay_live_call_success",
                     "conversation_end_reason","probe_complete",
                     "supervised_gemma_replies","supervised_script_fallback_replies"}}),flush=True)
    finally:
        if began:
            try:
                stopping = device_request(device, "orange_smoke_stop")
                print("EXACT_CALL_STOP_REQUESTED="+str(stopping.get("ok") is True).lower(),flush=True)
            except Exception as error:
                print("EXACT_STOP_ERROR="+type(error).__name__,flush=True)
            for _ in range(24):
                try:
                    if snapshot(device).get("call_idle") is True and device.idle():
                        ended_idle = True
                        break
                except Exception:
                    pass
                time.sleep(0.4)
            print("POSTCALL_IDLE="+str(ended_idle).lower(),flush=True)
            if not ended_idle: print("MANUAL_END_CALL_REQUIRED=true",flush=True)
    return {"dial_accepted": began, "exact_bound": exact_bound,
            "exact_active": exact_active, "pilot_started": pilot_started,
            "pilot_stopped": pilot_stopped, "phone_idle_after": ended_idle,
            "report_available": report.get("available") is True,
            "rx_positive": (report.get("telephony_rx_pcm_bytes", 0) > 0 or
                            report.get("telephony_rx_partial_pcm_bytes", 0) > 0),
            "tx_positive": report.get("telephony_tx_pcm_bytes", 0) > 0,
            "gemma_replies": report.get("supervised_gemma_replies", 0),
            "gemma_fallback_replies": report.get("supervised_script_fallback_replies", 0)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-one-real-call", action="store_true")
    parser.add_argument("--gemma", action="store_true")
    parser.add_argument("--adb", default=os.environ.get("ADB",
        str(Path.home() / "Library/Android/sdk/platform-tools/adb")))
    args = parser.parse_args()
    if not args.allow_one_real_call:
        print("ERROR=explicit_real_call_opt_in_required")
        return 2
    try:
        outcome = flow(Device(args.adb), gemma=args.gemma)
    except (OSError, RuntimeError, ValueError, ET.ParseError) as error:
        print("AUDIO_FLOW_FAILED="+type(error).__name__)
        return 1
    print("SAFE_AUDIO_FLOW_RESULT="+str(outcome))
    media_ok = all(outcome.get(k) for k in (
        "dial_accepted", "exact_bound", "exact_active",
        "pilot_started", "pilot_stopped", "phone_idle_after",
        "rx_positive", "tx_positive"))
    return 0 if media_ok and (not args.gemma or outcome.get("gemma_replies", 0) > 0) else 1


if __name__ == "__main__":
    raise SystemExit(main())
