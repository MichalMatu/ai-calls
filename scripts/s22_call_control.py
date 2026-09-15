#!/usr/bin/env python3
"""Bounded developer helper for controlling the physical S22+ over ADB.

This is test tooling, not product dialer code. It deliberately uses the currently
installed dialer's UI for DTMF so the app does not need to become the default dialer.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Iterable, Sequence


CALL_STATE_NAMES = {0: "IDLE", 1: "RINGING", 2: "OFFHOOK"}
EMERGENCY_NUMBERS = {"112", "911", "997", "998", "999"}
KEYPAD_LABELS = (
    "klawiatura",
    "pokaż klawiaturę",
    "pokaz klawiature",
    "keypad",
    "show keypad",
    "dialpad",
    "show dialpad",
)
BOUNDS_RE = re.compile(r"^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$")
CALL_STATE_RE = re.compile(r"mCallState=(\d+)")


def call_state_from_registry(text: str) -> int | None:
    match = CALL_STATE_RE.search(text)
    return int(match.group(1)) if match else None


def center_from_bounds(bounds: str) -> tuple[int, int]:
    match = BOUNDS_RE.match(bounds)
    if not match:
        raise ValueError(f"invalid bounds: {bounds}")
    left, top, right, bottom = (int(value) for value in match.groups())
    return ((left + right) // 2, (top + bottom) // 2)


def find_node_bounds(xml_text: str, labels: Iterable[str], *, exact: bool = False) -> str | None:
    wanted = [label.casefold() for label in labels]
    root = ET.fromstring(xml_text)
    for node in root.iter("node"):
        candidates = (
            node.attrib.get("text", "").strip(),
            node.attrib.get("content-desc", "").strip(),
            node.attrib.get("resource-id", "").strip(),
        )
        for candidate in candidates:
            folded = candidate.casefold()
            if not folded:
                continue
            matched = any(folded == label for label in wanted) if exact else any(
                label in folded for label in wanted
            )
            if matched:
                bounds = node.attrib.get("bounds")
                if bounds:
                    return bounds
    return None


def normalize_dtmf(raw: str) -> str:
    compact = "".join(raw.split())
    if not compact:
        raise ValueError("DTMF sequence is empty")
    if any(char not in "0123456789*#" for char in compact):
        raise ValueError("DTMF accepts only 0-9, * and #")
    return compact


def normalize_number(raw: str) -> str:
    number = raw.strip().replace(" ", "").replace("-", "")
    if not re.fullmatch(r"\+?\d+", number):
        raise ValueError("phone number must contain only digits and an optional leading +")
    national = number.lstrip("+")
    if len(national) < 4 or national in EMERGENCY_NUMBERS:
        raise ValueError("short/emergency numbers are blocked by this test helper")
    return number


@dataclass
class Adb:
    serial: str | None = None

    def _prefix(self) -> list[str]:
        command = ["adb"]
        if self.serial:
            command += ["-s", self.serial]
        return command

    def run(self, args: Sequence[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [*self._prefix(), *args],
            check=check,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )

    def shell(self, args: Sequence[str], *, check: bool = True) -> str:
        return self.run(["shell", *args], check=check).stdout

    def call_state(self) -> int | None:
        output = self.shell(["dumpsys", "telephony.registry"])
        return call_state_from_registry(output)

    def dial(self, number: str) -> None:
        normalized = normalize_number(number)
        self.shell(
            ["am", "start", "-a", "android.intent.action.CALL", "-d", f"tel:{normalized}"]
        )

    def hangup(self) -> None:
        self.shell(["input", "keyevent", "KEYCODE_ENDCALL"])

    def dump_ui(self) -> str:
        path = "/sdcard/aicallbridge-window.xml"
        self.shell(["uiautomator", "dump", path])
        xml_text = self.shell(["cat", path])
        self.shell(["rm", "-f", path], check=False)
        return xml_text

    def tap_bounds(self, bounds: str) -> None:
        x, y = center_from_bounds(bounds)
        self.shell(["input", "tap", str(x), str(y)])

    def open_keypad(self) -> str:
        xml_text = self.dump_ui()
        if any(find_node_bounds(xml_text, [digit], exact=True) for digit in "1234567890"):
            return xml_text

        bounds = find_node_bounds(xml_text, KEYPAD_LABELS)
        if bounds is None:
            raise RuntimeError("could not find the in-call keypad control")
        self.tap_bounds(bounds)
        time.sleep(0.4)
        return self.dump_ui()

    def send_dtmf(self, sequence: str, *, tone_gap_seconds: float = 0.18) -> None:
        if self.call_state() != 2:
            raise RuntimeError("DTMF requires an active call (mCallState=2)")

        digits = normalize_dtmf(sequence)
        xml_text = self.open_keypad()
        for digit in digits:
            bounds = find_node_bounds(xml_text, [digit], exact=True)
            if bounds is None:
                xml_text = self.dump_ui()
                bounds = find_node_bounds(xml_text, [digit], exact=True)
            if bounds is None:
                raise RuntimeError(f"could not find DTMF key {digit!r} in dialer UI")
            self.tap_bounds(bounds)
            time.sleep(tone_gap_seconds)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ADB_SERIAL"))
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("status")

    dial = sub.add_parser("dial")
    dial.add_argument("number")

    dtmf = sub.add_parser("dtmf")
    dtmf.add_argument("sequence")

    sub.add_parser("hangup")
    sub.add_parser("dump-ui")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    adb = Adb(args.serial)

    try:
        if args.command == "status":
            state = adb.call_state()
            print(f"call_state={state} name={CALL_STATE_NAMES.get(state, 'UNKNOWN')}")
        elif args.command == "dial":
            adb.dial(args.number)
            print("dial_requested=true")
        elif args.command == "dtmf":
            adb.send_dtmf(args.sequence)
            print(f"dtmf_sent={normalize_dtmf(args.sequence)}")
        elif args.command == "hangup":
            adb.hangup()
            print("hangup_requested=true")
        elif args.command == "dump-ui":
            print(adb.dump_ui())
        else:
            raise AssertionError(args.command)
        return 0
    except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"error={error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
