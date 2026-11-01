"""Discover and verify the exact S22 through current USB or Wireless Debugging ADB.

No pairing, telephony, dialing, settings changes or caller-data reads are performed.
Never trust a saved TCP port or a device-list label without checking hardware identity.
"""
from __future__ import annotations

import argparse
import ipaddress
import os
import subprocess
import sys
from typing import Callable, Sequence

from aicall_tools.device.s22_call_control import TARGET_MODEL, select_target_serial

EXPECTED_HARDWARE_SERIAL = "RFCT70L7E8J"
MAX_DISCOVERY_ENDPOINTS = 12
Runner = Callable[[Sequence[str]], subprocess.CompletedProcess[str]]


def mdns_connect_endpoints(output: str) -> list[str]:
    """Only accept advertised private/link-local IP TCP endpoints for TLS ADB."""
    found: list[str] = []
    for line in output.splitlines():
        if "_adb-tls-connect._tcp" not in line:
            continue
        candidate = line.split()[-1]
        host, sep, port_text = candidate.rpartition(":")
        if not sep or not port_text.isascii() or not port_text.isdigit():
            continue
        if not 1 <= int(port_text) <= 65535:
            continue
        try:
            address = ipaddress.ip_address(host)
        except ValueError:
            continue
        if (not address.is_private and not address.is_link_local) or address.is_loopback:
            continue
        if address.version != 4:
            continue  # v6 addressing requires bracket-aware ADB serial handling
        if candidate not in found:
            found.append(candidate)
    return found[:MAX_DISCOVERY_ENDPOINTS]


def _run(args: Sequence[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(args), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        check=False, timeout=12,
    )


def _checked(runner: Runner, argv: Sequence[str]) -> str:
    result = runner(list(argv))
    if result.returncode != 0:
        raise RuntimeError("adb_command_failed:" + " ".join(argv[:2]))
    return result.stdout


def _attached(devices: str, serial: str) -> bool:
    return any(
        len(parts := row.strip().split()) >= 2 and
        parts[0] == serial and parts[1] == "device" and
        f"model:{TARGET_MODEL}" in parts[2:]
        for row in devices.splitlines()
    )


def resolve_s22_serial(
    preferred: str | None = None,
    *,
    runner: Runner = _run,
    expected_hardware_serial: str = EXPECTED_HARDWARE_SERIAL,
) -> str:
    if preferred is not None and (not preferred.strip() or any(c.isspace() for c in preferred)):
        raise ValueError("invalid_adb_serial")
    _checked(runner, ["adb", "start-server"])
    devices = _checked(runner, ["adb", "devices", "-l"])

    def select(devices_text: str) -> str | None:
        if preferred is not None:
            return preferred if _attached(devices_text, preferred) else None
        try:
            return select_target_serial(devices_text)
        except RuntimeError as error:
            # Ambiguity is not a reason to scan or connect more candidates.
            if "multiple" in str(error):
                raise
            return None

    selected = select(devices)
    if selected is None:
        advertised = runner(["adb", "mdns", "services"])
        if advertised.returncode == 0:
            for endpoint in mdns_connect_endpoints(advertised.stdout):
                runner(["adb", "connect", endpoint])  # ADB itself verifies pairing.
        devices = _checked(runner, ["adb", "devices", "-l"])
        selected = select(devices)
    if selected is None or not _attached(devices, selected):
        raise RuntimeError("exact_s22_adb_transport_unavailable")

    model = _checked(runner, ["adb", "-s", selected, "shell", "getprop", "ro.product.model"]).strip()
    hardware_serial = _checked(runner, ["adb", "-s", selected, "shell", "getprop", "ro.serialno"]).strip()
    if model != "SM-S906B" or hardware_serial != expected_hardware_serial:
        raise RuntimeError("exact_s22_hardware_identity_mismatch")
    return selected


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("AICALL_SERIAL"))
    args = parser.parse_args(argv)
    try:
        serial = resolve_s22_serial(args.serial)
    except (ValueError, RuntimeError, subprocess.TimeoutExpired, OSError) as error:
        print(f"s22_adb_discovery_failed={error}", file=sys.stderr)
        return 2
    print(serial)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
