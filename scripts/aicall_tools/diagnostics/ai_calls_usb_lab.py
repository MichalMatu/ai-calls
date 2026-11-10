#!/usr/bin/env python3
"""AI Calls Lab + Telecom USB diagnostic console. Mac-side; no TCP server.

ADB transports every request to the DUMP-protected debug receiver; user text
travels through stdin into an app-private mailbox, never via an Intent extra,
GitHub task, logcat or shell command argument. ADB 'run-as' needs a debug APK.

The channel does NOT place calls, accept new call grants, bypass Android/Telecom
permissions, change current voice SIM, or disconnect an unowned Call.
"""
from __future__ import annotations

import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import uuid

try:
    from .ai_calls_usb_debug import (
        PACKAGE, SERIAL, ACTIVITY, Device, DiagnosticError, invoke, run_bridge,
    )
except ImportError:  # direct execution remains supported
    from ai_calls_usb_debug import (
        PACKAGE, SERIAL, ACTIVITY, Device, DiagnosticError, invoke, run_bridge,
    )

RECEIVER = PACKAGE + "/.debug.UsbLabControlReceiver"
PRIVATE_ROOT = "files/usb-lab-control"
PRIVATE_RESPONSE = PRIVATE_ROOT + "/response.json"
PRIVATE_TRANSCRIPT = PRIVATE_ROOT + "/transcript.json"
PRIVATE_LOCK = Path.home() / "Documents/AI-Calls/private-exports/.usb-lab-channel.lock"
ACTIONS = frozenset({
    "status", "call_status", "config_get", "lab_start", "lab_send",
    "lab_status", "lab_stop", "call_takeover", "call_resume",
    "call_stop_owned", "incoming_ai_disable", "incoming_config",
    "appointment_stage",
})
INTERVENTIONS = frozenset({
    "call_takeover", "call_resume", "call_stop_owned", "incoming_ai_disable",
})


def private_directory() -> Path:
    directory = Path.home() / "Documents/AI-Calls/private-exports"
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.chmod(0o700)
    return directory


def device_request(device: Device, action: str, **params: object) -> dict:
    if action not in ACTIONS:
        raise DiagnosticError("invalid_diagnostic_action")
    nonce = uuid.uuid4().hex
    payload = {"protocol": 1, "nonce": nonce, "action": action, **params}
    request = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    # A cross-process Mac lock protects one shared app-private request/response
    # mailbox without imposing a turn/time limit on conversations.
    directory = private_directory()
    lock = directory / ".usb-lab-channel.lock"
    with lock.open("a+b") as handle:
        os.chmod(lock, 0o600)
        fcntl.flock(handle, fcntl.LOCK_EX)
        try:
            shell = (
                "umask 077; mkdir -p " + PRIVATE_ROOT + "; "
                "cat > " + PRIVATE_ROOT + "/request.json.new && "
                "mv " + PRIVATE_ROOT + "/request.json.new "
                + PRIVATE_ROOT + "/request.json"
            )
            command = [device.adb, "-s", device.endpoint,
                       "shell", "run-as", PACKAGE, "sh", "-c", shlex.quote(shell)]
            try:
                write = subprocess.run(command, input=request, capture_output=True,
                                       text=True, timeout=30, check=False)
            except (OSError, subprocess.TimeoutExpired) as exc:
                raise DiagnosticError("private_request_write_failed") from exc
            if write.returncode:
                raise DiagnosticError("private_request_write_failed")
            broadcast = device.call(
                "shell", "am", "broadcast", "--receiver-foreground",
                "-n", RECEIVER, "--es", "usb_lab_nonce", nonce,
                timeout=30,
            )
            if broadcast.returncode or "Error:" in (
                broadcast.stdout + broadcast.stderr
            ):
                raise DiagnosticError("protected_receiver_unavailable")
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                response = device.call(
                    "shell", "run-as", PACKAGE, "cat", PRIVATE_RESPONSE,
                    timeout=15,
                )
                if response.returncode == 0:
                    try:
                        parsed = json.loads(response.stdout)
                    except ValueError:
                        parsed = {}
                    if (parsed.get("nonce") == nonce and
                            parsed.get("action") == action and
                            parsed.get("protocol") == 1 and
                            isinstance(parsed.get("ok"), bool)):
                        return parsed
                time.sleep(0.2)
            raise DiagnosticError("diagnostic_response_missing")
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


def save_private_transcript(device: Device) -> dict:
    response = device.call(
        "shell", "run-as", PACKAGE, "cat", PRIVATE_TRANSCRIPT, timeout=35,
    )
    if response.returncode:
        raise DiagnosticError("private_transcript_not_available")
    try:
        data = json.loads(response.stdout)
    except ValueError as exc:
        raise DiagnosticError("private_transcript_invalid") from exc
    session = data.get("session_id")
    if not isinstance(session, str) or not re.fullmatch(r"[0-9a-f-]{36}", session):
        raise DiagnosticError("private_session_id_invalid")
    root = private_directory() / "lab-sessions"
    root.mkdir(mode=0o700, exist_ok=True)
    root.chmod(0o700)
    path = root / ("session-" + session + "-" + uuid.uuid4().hex[:8] + ".json")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    fd = os.open(path, flags, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as out:
        out.write(response.stdout)
        out.write("\n")
    return {
        "ok": True, "code": "private_lab_transcript_saved_on_mac",
        "data": {
            "session_id": session,
            "turns": len(data.get("turns", [])),
            "file_saved_private": True,
            "bytes": path.stat().st_size,
        },
    }


def ram_sample(device: Device) -> dict:
    available = device.call("shell", "cat", "/proc/meminfo", timeout=35)
    app = device.call("shell", "dumpsys", "meminfo", PACKAGE, timeout=35)
    available_kb = re.search(r"^MemAvailable:\s+(\d+)\s+kB",
                             available.stdout, re.MULTILINE)
    app_pss = re.search(r"TOTAL PSS:\s+([\d,]+)", app.stdout)
    return {
        "system_mem_available_kb": int(available_kb.group(1))
        if available_kb else None,
        "app_pss_kb": int(app_pss.group(1).replace(",", ""))
        if app_pss else None,
    }


def run_once(device: Device, args: argparse.Namespace) -> dict:
    name = args.command.replace("-", "_")
    if name == "appointment_stage":
        device.safe_preflight()
        try:
            draft = json.load(sys.stdin)
        except (ValueError, TypeError) as error:
            raise DiagnosticError("appointment_draft_json_invalid") from error
        allowed = {"target", "goal", "from", "until", "terms"}
        if (not isinstance(draft, dict) or set(draft) != allowed or
                not all(isinstance(v, str) for v in draft.values())):
            raise DiagnosticError("appointment_draft_fields_invalid")
        result = device_request(device, name, **draft)
        # Never echo private purpose, target, date or term in a response.
        return {"ok": result.get("ok") is True,
                "code": result.get("code", "appointment_stage_unknown")}
    if name == "appointment_open":
        device.safe_preflight()
        response = device.call(
            "shell", "am", "start", "-n", ACTIVITY,
            "--ez", "open_appointment_editor", "true", timeout=30,
        )
        if response.returncode or "Error:" in response.stdout + response.stderr:
            raise DiagnosticError("appointment_editor_launch_failed")
        return {"ok": True, "code": "appointment_editor_open_requested",
                "call_started": False}
    if name == "lab_save":
        return save_private_transcript(device)
    if name == "ram":
        return {"ok": True, "code": "sample", "data": ram_sample(device)}
    if name.startswith("profile_"):
        # Reuse the existing vetted profile configuration bridge. Profile
        # changes are already guarded by its own off-call preflight.
        device.safe_preflight()
        op = name
        config = {}
        if op in {"profile_check", "profile_set"}:
            config = {"input": args.input, "dialogue": args.dialogue,
                      "output": args.output, "voice": args.voice}
        result = run_bridge(device, op, config)
        return {"ok": result.get("ok") is True,
                "code": result.get("code", "profile_status_unknown"),
                "data": result.get("data", {})}
    if name == "lab_send":
        if not args.session_id:
            raise DiagnosticError("session_id_required")
        text = sys.stdin.read()
        if not text.strip():
            raise DiagnosticError("nonempty_text_expected_on_stdin")
        return device_request(device, name, session_id=args.session_id, text=text)
    if name == "lab_start":
        return device_request(device, name, context_tokens=args.context_tokens,
                              preset=args.preset)
    if name == "lab_stop":
        if not args.session_id:
            raise DiagnosticError("session_id_required")
        return device_request(device, name, session_id=args.session_id)
    if name == "incoming_config":
        if args.record is None:
            raise DiagnosticError("explicit_recording_choice_required")
        params = {"mode": args.mode, "record": args.record == "yes"}
        if args.account_index is not None:
            params["account_index"] = args.account_index
        return device_request(device, name, **params)
    if name in ACTIONS:
        return device_request(device, name)
    raise DiagnosticError("unsupported_command")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=sorted(
        {k.replace("_", "-") for k in ACTIONS}
        | {"lab-save", "ram", "profile-check", "profile-set", "profile-reset", "watch", "appointment-open"}
    ))
    parser.add_argument("--session-id")
    parser.add_argument("--context-tokens", type=int, default=4096)
    parser.add_argument("--preset", default="natural")
    parser.add_argument("--input", default="ANDROID_STT")
    parser.add_argument("--dialogue", default="GEMMA_4_E2B")
    parser.add_argument("--output", default="ANDROID_TTS_PL")
    parser.add_argument("--voice", default="auto")
    parser.add_argument("--mode", choices=("manual", "ai"), default="manual")
    parser.add_argument("--account-index", type=int)
    parser.add_argument("--record", choices=("yes", "no"))
    parser.add_argument("--interval", type=float, default=2.0)
    parser.add_argument("--adb", default=os.environ.get(
        "ADB", str(Path.home() / "Library/Android/sdk/platform-tools/adb")))
    args = parser.parse_args()
    try:
        device = Device(args.adb)
        if args.command == "watch":
            if args.interval <= 0:
                raise DiagnosticError("invalid_watch_interval")
            try:
                while True:
                    result = device_request(device, "call_status")
                    result["data"]["ram"] = ram_sample(device)
                    print(json.dumps(result, ensure_ascii=False, separators=(",", ":")),
                          flush=True)
                    time.sleep(args.interval)
            except KeyboardInterrupt:
                return 0
        result = run_once(device, args)
        # No private prompts, replies, accounts, phone numbers, WAV or PII.
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
        return 0 if result.get("ok") is True else 2
    except (DiagnosticError, OSError, ValueError) as error:
        print(json.dumps({"ok": False, "code": str(error)}, ensure_ascii=False))
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
