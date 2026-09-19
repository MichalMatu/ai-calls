#!/usr/bin/env python3
"""Run one protected OPENAI_TEXT off-call proof on the physical S22+.

The standard OpenAI API key must not exist in this process environment. This script receives only a
separate development broker bearer and HTTPS endpoint, stages them into an app-private one-shot file
through ADB stdin, starts the diagnostic probe with a boolean-only Intent, and verifies cleanup.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import Mapping, Optional
from urllib.parse import urlsplit

PACKAGE_NAME = "pl.michalmatu.aicallbridge"
PROBE_ACTIVITY = f"{PACKAGE_NAME}/.DiagnosticProbeActivity"
CONFIG_PATH = "files/openai-text-smoke.json"
REPORT_PATH = "files/openai-text-speech-pipeline-report.txt"
DEFAULT_SERIAL = "RFCT70L7E8J"
TOKEN_MIN_CHARS = 32
TARGET_ADB_MODEL = "SM_S906B"
CALL_STATE_RE = re.compile(r"mCallState=(\d+)")


@dataclass(frozen=True, repr=False)
class SmokeEnvironment:
    text_endpoint: str
    broker_token: str

    @classmethod
    def from_mapping(cls, env: Mapping[str, str]) -> "SmokeEnvironment":
        if env.get("OPENAI_API_KEY", "").strip():
            raise ValueError("OPENAI_API_KEY must not be inherited by the Android smoke process")
        endpoint = env.get("AI_CALL_BRIDGE_TEXT_BROKER_HTTPS_URL", "").strip()
        token = env.get("AI_CALL_BRIDGE_TEXT_BROKER_TOKEN", "").strip()
        parsed = urlsplit(endpoint)
        if parsed.scheme.lower() != "https" or not parsed.hostname:
            raise ValueError("AI_CALL_BRIDGE_TEXT_BROKER_HTTPS_URL must be an HTTPS URL")
        if parsed.username is not None or parsed.password is not None or parsed.query or parsed.fragment:
            raise ValueError("text broker URL must not contain userinfo, query, or fragment")
        if parsed.hostname.lower() == "api.openai.com":
            raise ValueError("Android must use the developer text broker, not api.openai.com")
        if parsed.path != "/v1/call-text-turn":
            raise ValueError("text broker URL must use /v1/call-text-turn")
        if len(token) < TOKEN_MIN_CHARS:
            raise ValueError(f"AI_CALL_BRIDGE_TEXT_BROKER_TOKEN must be at least {TOKEN_MIN_CHARS} characters")
        if token.lower().startswith("sk-"):
            raise ValueError("AI_CALL_BRIDGE_TEXT_BROKER_TOKEN must not be an OpenAI API key")
        if any(ord(ch) < 33 or ord(ch) == 127 for ch in token):
            raise ValueError("AI_CALL_BRIDGE_TEXT_BROKER_TOKEN contains unsupported characters")
        return cls(endpoint, token)

    def __repr__(self) -> str:
        return "SmokeEnvironment(text_endpoint=REDACTED, broker_token=REDACTED)"


def run_bytes(args: list[str], *, input_bytes: bytes = b"", check: bool = True) -> bytes:
    return subprocess.run(
        args,
        input=input_bytes,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=check,
    ).stdout


def adb_text(serial: str, *shell_args: str, check: bool = True) -> str:
    return run_bytes(["adb", "-s", serial, "shell", *shell_args], check=check).decode(
        "utf-8", errors="replace"
    )


def is_direct_usb_target(devices_output: str, serial: str) -> bool:
    for raw_line in devices_output.splitlines():
        parts = raw_line.split()
        if not parts or parts[0] != serial:
            continue
        return (
            len(parts) >= 2
            and parts[1] == "device"
            and any(part.startswith("usb:") for part in parts[2:])
            and f"model:{TARGET_ADB_MODEL}" in parts[2:]
        )
    return False


def call_state(serial: str) -> int:
    match = CALL_STATE_RE.search(adb_text(serial, "dumpsys", "telephony.registry"))
    if match is None:
        raise RuntimeError("could not determine cellular call state")
    return int(match.group(1))


def stage_private_config(serial: str, environment: SmokeEnvironment) -> None:
    payload = json.dumps(
        {"text_endpoint": environment.text_endpoint, "broker_token": environment.broker_token},
        separators=(",", ":"),
    ).encode("utf-8")
    run_bytes(
        [
            "adb", "-s", serial, "shell", "run-as", PACKAGE_NAME,
            "sh", "-c", f"cat > {CONFIG_PATH}",
        ],
        input_bytes=payload,
    )


def remove_report(serial: str) -> None:
    adb_text(serial, "run-as", PACKAGE_NAME, "rm", "-f", REPORT_PATH, check=False)


def read_report(serial: str) -> str:
    return adb_text(serial, "run-as", PACKAGE_NAME, "cat", REPORT_PATH, check=False).replace("\r", "")


def parse_report(text: str) -> Optional[dict[str, str]]:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values if values.get("probe_complete") == "true" else None


def config_exists(serial: str) -> bool:
    output = adb_text(
        serial, "run-as", PACKAGE_NAME, "sh", "-c", f"test -e {CONFIG_PATH}; echo $?", check=False,
    ).strip()
    return output.endswith("0")


def run_smoke(serial: str, environment: SmokeEnvironment, *, timeout_seconds: float = 70.0) -> dict[str, str]:
    devices = run_bytes(["adb", "devices", "-l"]).decode("utf-8", errors="replace")
    if not is_direct_usb_target(devices, serial):
        raise RuntimeError("OPENAI_TEXT smoke requires exact direct USB S22 target")
    if call_state(serial) != 0:
        raise RuntimeError("OPENAI_TEXT off-call smoke requires CALL_STATE=0")

    remove_report(serial)
    stage_private_config(serial, environment)
    adb_text(serial, "am", "force-stop", PACKAGE_NAME, check=False)
    run_bytes([
        "adb", "-s", serial, "shell", "am", "start", "-W", "-n", PROBE_ACTIVITY,
        "--ez", "run_openai_text_speech_pipeline_probe", "true",
    ])

    deadline = time.monotonic() + timeout_seconds
    report: Optional[dict[str, str]] = None
    raw = ""
    while time.monotonic() < deadline:
        if call_state(serial) != 0:
            raise RuntimeError("cellular call state changed during OPENAI_TEXT off-call smoke")
        raw = read_report(serial)
        report = parse_report(raw)
        if report is not None:
            break
        time.sleep(0.25)
    if report is None:
        raise TimeoutError("OPENAI_TEXT off-call probe did not produce a terminal report")
    if config_exists(serial):
        raise RuntimeError("one-shot OPENAI_TEXT config was not deleted")
    if call_state(serial) != 0:
        raise RuntimeError("cellular call state changed during OPENAI_TEXT off-call smoke")

    required = {
        "probe": "openai_text_speech_pipeline",
        "call_required": "false",
        "openai_api_used": "true",
        "raw_audio_to_openai": "false",
        "api_key_on_android": "false",
        "backend_location": "developer_https_broker",
        "approval_policy": "application_owned",
        "backend_config_valid": "true",
        "stt_transcript_nonblank": "true",
        "backend_complete_response": "true",
        "approved_text_nonblank": "true",
        "approved_output_pcm_nonempty": "true",
        "openai_text_speech_pipeline_success": "true",
    }
    for key, expected in required.items():
        if report.get(key) != expected:
            raise RuntimeError(f"OPENAI_TEXT proof failed {key}: {report.get(key)!r}")
    if int(report.get("transcript_to_approved_ms", "-1")) < 0:
        raise RuntimeError("OPENAI_TEXT proof did not report transcript_to_approved_ms")

    print("--- OpenAI text off-call report ---")
    print(raw, end="" if raw.endswith("\n") else "\n")
    print("--- end OpenAI text off-call report ---")
    print("openai_text_offcall_proven_s22=true")
    return report


def main(argv: Optional[list[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) > 1:
        print("usage: openai_text_offcall_smoke.py [adb-serial]", file=sys.stderr)
        return 2
    try:
        environment = SmokeEnvironment.from_mapping(os.environ)
        run_smoke(args[0] if args else DEFAULT_SERIAL, environment)
        return 0
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"OPENAI_TEXT off-call smoke failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
