"""Read-only S22 telecom capability evidence; never authorize a phone call.

The default dialer role and a running Shizuku process are diagnostics, NOT proof
that AI Calls owns a specific Telecom Call or can selectively disconnect it.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from collections.abc import Callable, Sequence
from typing import Any

from aicall_tools.device.s22_adb_discovery import resolve_s22_serial

APP_PACKAGE = "pl.michalmatu.aicallbridge"
DIALER_ROLE = "android.app.role.DIALER"
PACKAGE = re.compile(r"^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+$")
CALL_STATE = re.compile(r"\bmCallState\s*=\s*([0-9]+)\b")

Runner = Callable[[Sequence[str]], subprocess.CompletedProcess[str]]


def run_adb(args: Sequence[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(list(args), capture_output=True, text=True, timeout=15, check=False)


def parse_role_holders(raw: str) -> list[str]:
    """Return distinct package names from 'cmd role holders'; unknown is empty."""
    names: list[str] = []
    for line in raw.splitlines():
        value = line.strip()
        if PACKAGE.fullmatch(value) and value not in names:
            names.append(value)
    return names


def parse_call_state(raw: str) -> str:
    matches = set(CALL_STATE.findall(raw))
    return (
        "IDLE" if matches == {"0"} else
        "OFFHOOK_OR_RINGING" if matches and matches.issubset({"1", "2"}) else
        "UNKNOWN"
    )


def inspect_phone(serial: str, *, runner: Runner = run_adb) -> dict[str, Any]:
    """Inspect only allowlisted ADB shell read commands on a verified S22."""
    if not serial or any(character.isspace() for character in serial):
        raise ValueError("invalid_adb_serial")

    def read(*args: str) -> tuple[int, str]:
        result = runner(["adb", "-s", serial, "shell", *args])
        return result.returncode, result.stdout if result.returncode == 0 else ""

    sdk_rc, sdk_raw = read("getprop", "ro.build.version.sdk")
    role_rc, role_raw = read("cmd", "role", "get-role-holders", DIALER_ROLE)
    telecom_rc, telecom_raw = read("cmd", "telecom", "get-default-dialer")
    app_rc, app_raw = read("pm", "path", APP_PACKAGE)
    call_rc, call_raw = read("dumpsys", "telephony.registry")
    ps_rc, ps_raw = read("ps", "-A")

    sdk_text = sdk_raw.strip()
    sdk = int(sdk_text) if sdk_rc == 0 and sdk_text.isascii() and sdk_text.isdigit() else None
    holders = parse_role_holders(role_raw) if role_rc == 0 else []
    telecom_packages = parse_role_holders(telecom_raw) if telecom_rc == 0 else []
    # Prefer independent agreement. A stock Telecom fallback is useful on
    # vendor builds that do not expose RoleManager's command interface.
    if role_rc == 0:
        default_dialer = (
            holders[0] if len(holders) == 1 and holders == telecom_packages
            else "UNKNOWN"
        )
    else:
        default_dialer = telecom_packages[0] if len(telecom_packages) == 1 else "UNKNOWN"
    state = parse_call_state(call_raw) if call_rc == 0 else "UNKNOWN"
    app_installed = app_rc == 0 and any(
        row.startswith("package:") for row in app_raw.splitlines()
    )
    shizuku_running = ps_rc == 0 and any(
        "shizuku_server" in row.split()[-1] for row in ps_raw.splitlines() if row.split()
    )

    return {
        "probe": "s22_call_capabilities",
        "sdk": sdk,
        "role_query_succeeded": role_rc == 0,
        "telecom_query_succeeded": telecom_rc == 0,
        "default_dialer": default_dialer,
        "app_is_default_dialer": default_dialer == APP_PACKAGE,
        "ai_calls_installed": app_installed,
        "call_state": state,
        "shizuku_process_visible": shizuku_running,
        # Neither role eligibility nor Shizuku media grant supplies a specific
        # Call handle. Proving this is a separate native implementation/acceptance.
        "exact_call_controller_proven": False,
        "autonomous_dial_authorized": False,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="Explicit ADB transport, verified against S22 hardware")
    args = parser.parse_args(argv)
    try:
        serial = resolve_s22_serial(args.serial)
        record = inspect_phone(serial)
    except (RuntimeError, ValueError, subprocess.TimeoutExpired, OSError) as error:
        print(f"call_capability_preflight_failed={type(error).__name__}", file=sys.stderr)
        return 2
    print(json.dumps(record, sort_keys=True))
    # Status unknown/active is not a clean device preflight.
    return 0 if record["call_state"] == "IDLE" else 3


if __name__ == "__main__":
    raise SystemExit(main())
