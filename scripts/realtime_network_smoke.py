#!/usr/bin/env python3
"""Run the bounded S22+ Realtime network/off-call smoke without dialing.

Secrets are read from the host environment and staged into the app-private one-shot config only via
ADB stdin. Neither the broker bearer nor its endpoint is placed in an Intent or adb argv. The Android
probe deletes the config before any network work and must keep the cellular call state idle.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import Mapping, Optional, Protocol, Sequence
from urllib.parse import urlsplit


PACKAGE_NAME = "pl.michalmatu.aicallbridge"
PROBE_ACTIVITY = f"{PACKAGE_NAME}/.DiagnosticProbeActivity"
CONFIG_PATH = "files/realtime-network-smoke.json"
RESULT_PREFIX = "realtime_network_off_call_smoke="
TOKEN_MIN_CHARS = 32
CALL_STATE_RE = re.compile(r"mCallState=(\d+)")


@dataclass(frozen=True, repr=False)
class SmokeEnvironment:
    credential_endpoint: str
    broker_token: str

    @classmethod
    def from_mapping(cls, env: Mapping[str, str]) -> "SmokeEnvironment":
        endpoint = env.get("AI_CALL_BRIDGE_BROKER_HTTPS_URL", "").strip()
        token = env.get("AI_CALL_BRIDGE_BROKER_TOKEN", "").strip()

        parsed = urlsplit(endpoint)
        if parsed.scheme.lower() != "https" or not parsed.hostname:
            raise ValueError("AI_CALL_BRIDGE_BROKER_HTTPS_URL must be an HTTPS URL")
        if parsed.username is not None or parsed.password is not None:
            raise ValueError("broker URL must not contain userinfo")
        if parsed.fragment:
            raise ValueError("broker URL must not contain a fragment")
        if parsed.hostname.lower() == "api.openai.com":
            raise ValueError("Android must use the developer credential broker, not api.openai.com")
        if len(token) < TOKEN_MIN_CHARS:
            raise ValueError(f"AI_CALL_BRIDGE_BROKER_TOKEN must be at least {TOKEN_MIN_CHARS} characters")
        if token.lower().startswith("sk-"):
            raise ValueError("AI_CALL_BRIDGE_BROKER_TOKEN must not be an OpenAI API key")
        if any(ord(ch) < 33 or ord(ch) == 127 for ch in token):
            raise ValueError("AI_CALL_BRIDGE_BROKER_TOKEN contains unsupported characters")

        return cls(endpoint, token)

    def __repr__(self) -> str:
        return "SmokeEnvironment(credential_endpoint=REDACTED, broker_token=REDACTED)"


@dataclass(frozen=True)
class SmokeResult:
    status: str
    reason: str
    states: tuple[str, ...]
    detail: Optional[str] = None

    def render(self) -> str:
        rendered = [
            f"{RESULT_PREFIX}{self.status}",
            f"reason={self.reason}",
            f"states={'>'.join(self.states) if self.states else 'none'}",
        ]
        if self.detail:
            rendered.append(f"detail={self.detail}")
        return "\n".join(rendered)


class ByteRunner(Protocol):
    def run_bytes(
        self,
        args: Sequence[str],
        *,
        input_bytes: bytes = b"",
        check: bool = True,
    ) -> bytes: ...


class SubprocessRunner:
    def run_bytes(
        self,
        args: Sequence[str],
        *,
        input_bytes: bytes = b"",
        check: bool = True,
    ) -> bytes:
        completed = subprocess.run(
            list(args),
            input=input_bytes,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=check,
        )
        return completed.stdout


def build_private_config_payload(endpoint: str, broker_token: str) -> bytes:
    return json.dumps(
        {
            "credential_endpoint": endpoint,
            "broker_token": broker_token,
        },
        separators=(",", ":"),
    ).encode("utf-8")


def stage_private_config(
    runner: ByteRunner,
    serial: str,
    endpoint: str,
    broker_token: str,
) -> None:
    payload = build_private_config_payload(endpoint, broker_token)
    runner.run_bytes(
        [
            "adb",
            "-s",
            serial,
            "shell",
            "run-as",
            PACKAGE_NAME,
            "sh",
            "-c",
            f"cat > {CONFIG_PATH}",
        ],
        input_bytes=payload,
    )


def build_probe_start_args(serial: str) -> list[str]:
    return [
        "adb",
        "-s",
        serial,
        "shell",
        "am",
        "start",
        "-n",
        PROBE_ACTIVITY,
        "--ez",
        "run_realtime_network_off_call_smoke",
        "true",
    ]


def parse_probe_result(text: str) -> Optional[SmokeResult]:
    status: Optional[str] = None
    reason: Optional[str] = None
    states: tuple[str, ...] = ()
    detail: Optional[str] = None

    for raw_line in text.splitlines():
        marker = raw_line.find(RESULT_PREFIX)
        if marker >= 0:
            status = raw_line[marker + len(RESULT_PREFIX) :].strip()
            continue
        for key in ("reason=", "states=", "detail="):
            marker = raw_line.find(key)
            if marker < 0:
                continue
            value = raw_line[marker + len(key) :].strip()
            if key == "reason=":
                reason = value
            elif key == "states=":
                states = () if value in {"", "none"} else tuple(value.split(">"))
            else:
                detail = value
            break

    if status is None or reason is None:
        return None
    return SmokeResult(status=status, reason=reason, states=states, detail=detail)


def _adb_text(runner: ByteRunner, serial: str, *shell_args: str) -> str:
    return runner.run_bytes(["adb", "-s", serial, "shell", *shell_args]).decode(
        "utf-8", errors="replace"
    )


def _call_state(runner: ByteRunner, serial: str) -> int:
    registry = _adb_text(runner, serial, "dumpsys", "telephony.registry")
    match = CALL_STATE_RE.search(registry)
    if match is None:
        raise RuntimeError("could not determine cellular call state")
    return int(match.group(1))


def run_smoke(
    serial: str,
    environment: SmokeEnvironment,
    *,
    runner: Optional[ByteRunner] = None,
    timeout_seconds: float = 35.0,
) -> SmokeResult:
    executor = runner or SubprocessRunner()
    if _call_state(executor, serial) != 0:
        raise RuntimeError("Realtime network smoke requires CALL_STATE=0")

    stage_private_config(
        executor,
        serial,
        environment.credential_endpoint,
        environment.broker_token,
    )
    executor.run_bytes(["adb", "-s", serial, "logcat", "-c"])
    executor.run_bytes(build_probe_start_args(serial))

    deadline = time.monotonic() + timeout_seconds
    result: Optional[SmokeResult] = None
    while time.monotonic() < deadline:
        output = executor.run_bytes(
            ["adb", "-s", serial, "logcat", "-d", "-s", "AiCallBridge:I", "*:S"]
        ).decode("utf-8", errors="replace")
        result = parse_probe_result(output)
        if result is not None:
            break
        time.sleep(0.25)

    if result is None:
        raise TimeoutError("Realtime network smoke did not produce a terminal result")
    if _call_state(executor, serial) != 0:
        raise RuntimeError("cellular call state changed during Realtime network smoke")

    exists = executor.run_bytes(
        [
            "adb",
            "-s",
            serial,
            "shell",
            "run-as",
            PACKAGE_NAME,
            "sh",
            "-c",
            f"test -e {CONFIG_PATH}; echo $?",
        ],
        check=False,
    ).decode("utf-8", errors="replace").strip()
    if exists.endswith("0"):
        raise RuntimeError("one-shot Realtime smoke config was not deleted")

    return result


def main(argv: Optional[list[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 1 or not args[0].strip():
        print("usage: realtime_network_smoke.py <adb-serial>", file=sys.stderr)
        return 2

    try:
        environment = SmokeEnvironment.from_mapping(os.environ)
        result = run_smoke(args[0].strip(), environment)
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"realtime network smoke failed: {error}", file=sys.stderr)
        return 1

    print(result.render())
    return 0 if result.status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
