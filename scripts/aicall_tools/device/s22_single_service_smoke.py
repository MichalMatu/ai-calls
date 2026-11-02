#!/usr/bin/env python3
"""One bounded real Orange support call on the exact S22, with global lab cleanup.

This developer-only scenario does not play, record or transmit call audio. It
collects the Android-app audio mode while the test call is OFFHOOK and always
attempts one global end-call in cleanup. This is NOT an owned-call controller:
a concurrent incoming/changed call can make device-global hangup inappropriate.
Use only on the designated single-purpose test device with explicit permission.
"""

from __future__ import annotations

import argparse
import sys
import time
from dataclasses import dataclass
from typing import Callable, Optional, Sequence
from urllib.parse import quote

from aicall_tools.device.s22_call_control import Adb
from aicall_tools.device.s22_supervised_readiness import collect as collect_readiness

EXACT_SERIAL = "RFCT70L7E8J"
EXACT_MODEL = "SM-S906B"
ALLOWED_TARGETS = frozenset({"*100", "510100100"})
MAX_OFFHOOK_WAIT_S = 25.0
MAX_CONNECTED_S = 20.0
POLL_INTERVAL_S = 0.25


@dataclass(frozen=True)
class ServiceCallSmokeResult:
    launched: bool
    became_offhook: bool
    diagnostic: dict[str, str]
    cleanup_verified_idle: bool
    cleanup_error: str | None = None


def _device_checked(adb: Adb) -> None:
    serial = adb.shell(["getprop", "ro.serialno"]).strip()
    model = adb.shell(["getprop", "ro.product.model"]).strip()
    if serial != EXACT_SERIAL or model != EXACT_MODEL:
        raise RuntimeError("wrong_s22_for_service_smoke")


def run_one(
    adb: Adb,
    target: str = "*100",
    *,
    diagnostic: Callable[[str], dict[str, str]] = collect_readiness,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> ServiceCallSmokeResult:
    """Exactly one dial edge, no DTMF/service mutations, one cleanup attempt."""
    if target not in ALLOWED_TARGETS:
        raise ValueError("support_target_not_allowlisted")
    if adb.serial != EXACT_SERIAL:
        raise RuntimeError("test_requires_exact_usb_s22")
    _device_checked(adb)
    if adb.call_state() != 0:
        raise RuntimeError("test_requires_idle_before_dial")

    launched = False
    offhook = False
    fields: dict[str, str] = {}
    cleanup_verified = False
    cleanup_error: str | None = None
    failure: BaseException | None = None
    try:
        # Recheck immediately before placing the one authorized call.
        if adb.call_state() != 0:
            raise RuntimeError("call_state_changed_before_dial")
        launched = True  # ADB may fail after Android accepted the dial intent.
        adb.shell([
            "am", "start", "-a", "android.intent.action.CALL",
            "-d", "tel:" + quote(target, safe=""),
        ])

        deadline = monotonic() + MAX_OFFHOOK_WAIT_S
        while monotonic() < deadline:
            state = adb.call_state()
            if state == 2:
                offhook = True
                break
            if state not in (0, 2):
                raise RuntimeError("call_state_not_idle_or_offhook")
            sleep(POLL_INTERVAL_S)
        if not offhook:
            raise RuntimeError("call_did_not_enter_offhook")

        # Collect only whitelisted metadata: Telecom mode, Shizuku and permissions.
        fields = diagnostic(EXACT_SERIAL)
    except BaseException as error:
        failure = error
    finally:
        if launched:
            # Global end-call is explicitly authorized by the test user; the
            # function still revalidates exact hardware and OFFHOOK state.
            try:
                cleanup_verified = adb.hangup(allow_global=True)
            except (OSError, RuntimeError, ValueError) as error:
                cleanup_error = type(error).__name__ + ":" + str(error)[:80]

    if cleanup_error is not None:
        raise RuntimeError("global_cleanup_unverified:" + cleanup_error) from failure
    if failure is not None:
        raise failure
    return ServiceCallSmokeResult(
        launched=launched,
        became_offhook=offhook,
        diagnostic=fields,
        cleanup_verified_idle=cleanup_verified,
    )


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=EXACT_SERIAL)
    parser.add_argument("--target", default="*100", choices=sorted(ALLOWED_TARGETS))
    parser.add_argument("--allow-one-real-call", action="store_true", required=False)
    args = parser.parse_args(argv)
    if not args.allow_one_real_call:
        print("ERROR=explicit_real_call_opt_in_required")
        return 2
    try:
        result = run_one(Adb(args.serial), args.target)
    except (OSError, RuntimeError, ValueError) as error:
        print("SMOKE_FAILED=" + str(error).split(":", 1)[0])
        return 1
    print("DIAL_ATTEMPTED=" + str(result.launched).lower())
    print("OFFHOOK_CONFIRMED=" + str(result.became_offhook).lower())
    for key in ("probe", "telecom_in_call", "telecom_managed_call", "audio_mode", "failure_reason", "report_complete"):
        print(key + "=" + result.diagnostic.get(key, "missing"))
    print("GLOBAL_CLEANUP_IDLE=" + str(result.cleanup_verified_idle).lower())
    print("NO_PCM_NO_DTMF_NO_SERVICE_MUTATIONS=true")
    return 0 if result.cleanup_verified_idle else 1


if __name__ == "__main__":
    raise SystemExit(main())
