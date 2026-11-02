#!/usr/bin/env python3
"""One-shot no-touch S22 cellular route diagnostics (never dials or handles PCM).

Starts a DUMP-permission-gated Android Activity that captures app-context
Telecom/AudioManager readiness and exits immediately. The only output is a
strictly whitelisted, app-private metadata report.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import time
from typing import Callable, Optional, Sequence

PACKAGE = "pl.michalmatu.aicallbridge"
COMPONENT = PACKAGE + "/.developerrelay.SupervisedCallReadinessProbeActivity"
REPORT_PATH = "files/supervised-call-readiness-report.txt"
EXPECTED_SERIAL = "RFCT70L7E8J"
EXPECTED_MODEL = "SM-S906B"
FIELDS = (
    "probe",
    "microphone_permission",
    "phone_state_permission",
    "shizuku_ready",
    "telecom_in_call",
    "telecom_managed_call",
    "audio_mode",
    "failure_reason",
    "report_complete",
)
BOOL_FIELDS = (
    "microphone_permission",
    "phone_state_permission",
    "shizuku_ready",
    "telecom_in_call",
    "telecom_managed_call",
    "report_complete",
)
AUDIO_MODES = {
    "MODE_IN_CALL",
    "MODE_IN_COMMUNICATION",
    "MODE_NORMAL",
    "MODE_RINGTONE",
    "UNAVAILABLE",
}
REASONS = {
    "none",
    "permission_required",
    "shizuku_not_ready",
    "no_active_cellular_call",
    "audio_route_not_ready",
}


def parse_report(raw: str) -> dict[str, str]:
    """Reject malformed or content-bearing reports before rendering any output."""
    result: dict[str, str] = {}
    for line in raw.replace("\r", "").splitlines():
        if not line or "=" not in line:
            raise ValueError("invalid_diagnostic_report")
        key, value = line.split("=", 1)
        if key not in FIELDS or key in result:
            raise ValueError("unexpected_or_duplicate_diagnostic_field")
        result[key] = value
    if tuple(result) != FIELDS:
        raise ValueError("incomplete_or_reordered_diagnostic_report")
    if result["probe"] != "supervised_call_readiness":
        raise ValueError("invalid_diagnostic_probe")
    if any(result[k] not in ("true", "false") for k in BOOL_FIELDS):
        raise ValueError("invalid_diagnostic_boolean")
    audio = result["audio_mode"]
    if audio not in AUDIO_MODES and not re.fullmatch(r"OTHER_-?[0-9]{1,9}", audio):
        raise ValueError("invalid_audio_mode")
    if result["failure_reason"] not in REASONS:
        raise ValueError("invalid_failure_reason")
    if result["report_complete"] != "true":
        raise ValueError("incomplete_diagnostic_report")
    return result


def _adb(serial: str, *args: str, timeout: float = 18) -> subprocess.CompletedProcess[str]:
    if serial != EXPECTED_SERIAL:
        raise ValueError("unexpected_device_serial")
    return subprocess.run(
        ["adb", "-s", serial, *args],
        capture_output=True,
        text=True,
        timeout=timeout,
        check=True,
    )


def collect(serial: str = EXPECTED_SERIAL, timeout_seconds: float = 8) -> dict[str, str]:
    if not (0 < timeout_seconds <= 30):
        raise ValueError("invalid_diagnostic_timeout")
    device_serial = _adb(serial, "shell", "getprop", "ro.serialno").stdout.strip()
    device_model = _adb(serial, "shell", "getprop", "ro.product.model").stdout.strip()
    if device_serial != EXPECTED_SERIAL or device_model != EXPECTED_MODEL:
        raise ValueError("hardware_identity_mismatch")

    # Discard the last attempt so an interrupted launch cannot be mistaken for new evidence.
    _adb(serial, "shell", "run-as", PACKAGE, "rm", "-f", REPORT_PATH)
    _adb(serial, "shell", "am", "start", "-W", "-n", COMPONENT)
    end = time.monotonic() + timeout_seconds
    while time.monotonic() < end:
        try:
            output = _adb(serial, "shell", "run-as", PACKAGE, "cat", REPORT_PATH).stdout
            return parse_report(output)
        except (subprocess.CalledProcessError, ValueError):
            time.sleep(0.1)
    raise TimeoutError("diagnostic_report_unavailable")


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=EXPECTED_SERIAL)
    parser.add_argument("--timeout", type=float, default=8.0)
    args = parser.parse_args(argv)
    try:
        result = collect(args.serial, args.timeout)
    except (OSError, ValueError, TimeoutError, subprocess.SubprocessError) as error:
        print("DIAGNOSTIC_FAILED=" + type(error).__name__)
        return 1
    for key in FIELDS:
        print(key + "=" + result[key])
    print("NO_DIAL_NO_HANGUP_NO_PCM=true")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
