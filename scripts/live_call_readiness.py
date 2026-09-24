#!/usr/bin/env python3
"""No-call app readiness check used before any developer live-call runner may dial."""

from __future__ import annotations

import argparse
import subprocess
import sys
import time
from typing import Optional

from realtime_network_smoke import PACKAGE_NAME

PROBE_ACTIVITY = f"{PACKAGE_NAME}/.LiveCallReadinessProbeActivity"
REPORT_PATH = "files/live-call-readiness-report.txt"
DEFAULT_TIMEOUT_SECONDS = 8.0


def parse_readiness_report(text: str) -> Optional[dict[str, str]]:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    if values.get("probe") != "live_call_readiness":
        return None
    if values.get("probe_complete") != "true":
        return None
    return values


def _run_as(serial: str, args: list[str], *, check: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["adb", "-s", serial, "shell", "run-as", PACKAGE_NAME, *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=check,
    )


def run_live_call_readiness(serial: str, timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS) -> dict[str, str]:
    if not serial.strip():
        raise ValueError("serial is required")
    if timeout_seconds <= 0:
        raise ValueError("timeout_seconds must be positive")

    _run_as(serial, ["rm", "-f", REPORT_PATH], check=False)
    subprocess.run(
        ["adb", "-s", serial, "shell", "am", "start", "-W", "-n", PROBE_ACTIVITY],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
    )

    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        result = _run_as(serial, ["cat", REPORT_PATH], check=False)
        if result.returncode == 0 and result.stdout.strip():
            report = parse_readiness_report(result.stdout.replace("\r", ""))
            if report is not None:
                if report.get("live_call_readiness") != "true":
                    reason = report.get("failure_reason", "unknown")
                    raise RuntimeError(f"live-call readiness failed: {reason}")
                return report
        time.sleep(0.1)
    raise TimeoutError("timed out waiting for live-call readiness probe")


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    args = parser.parse_args(argv)
    try:
        report = run_live_call_readiness(args.serial, args.timeout)
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"live_call_readiness_failed={error}", file=sys.stderr)
        return 1

    for key in (
        "record_audio_granted",
        "shizuku_binder_available",
        "shizuku_supported",
        "shizuku_permission_granted",
        "live_call_readiness",
    ):
        print(f"{key}={report.get(key, '')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
