#!/usr/bin/env python3
"""Mac-only event-driven recovery of the exact authorized S22+ GSM Binder host.

The Android app cannot acquire shell UID 2000 after a reboot. An already trusted
Mac ADB transport can restore the host on a device reconnect. No periodic job,
Shizuku, root, new APK install, phone call, or change to Android permissions.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import sys
import time

LABEL = "pl.michalmatu.aicalls.s8-gsm-reconnect"
APP = "pl.michalmatu.aicallbridge.s8lab"
URI = "content://" + APP + ".gsmbinder"
MODEL = "SM-S906B"
SERIAL_PATTERN = re.compile(r"[A-Za-z0-9._:-]{3,128}\Z")
BASE = Path(__file__).resolve().parent
BOOTSTRAP = BASE / "start-s8-gsm-shell.sh"


def log(message: str) -> None:
    # Never log device identifiers, private conversations, local file contents.
    print("S8_GSM_RECONNECT=" + message, flush=True)


def run(command: list[str], timeout: int = 15) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, text=True, capture_output=True, timeout=timeout,
                          check=False)


def adb_cmd(adb: str, serial: str, *args: str, timeout: int = 15) -> subprocess.CompletedProcess[str]:
    return run([adb, "-s", serial, *args], timeout=timeout)


def shell(adb: str, serial: str, *args: str, timeout: int = 15) -> str:
    process = adb_cmd(adb, serial, "shell", *args, timeout=timeout)
    return process.stdout.strip() if process.returncode == 0 else ""


def parse_snapshot(lines: list[str]) -> dict[str, str]:
    """Parse an entire adb track-devices frame (not a single changed line)."""
    devices: dict[str, str] = {}
    for line in lines:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] in {"device", "offline", "unauthorized", "recovery"}:
            devices[parts[0]] = parts[1]
    return devices


def newly_online(previous: dict[str, str], current: dict[str, str], serial: str) -> bool:
    return current.get(serial) == "device" and previous.get(serial) != "device"


def host_registered(adb: str, serial: str) -> bool:
    result = adb_cmd(adb, serial, "shell", "content", "call", "--uri", URI,
                     "--method", "status")
    return result.returncode == 0 and "host_registered=true" in result.stdout


def recover(adb: str, serial: str, max_boot_checks: int = 90,
            boot_delay: float = 2.0) -> bool:
    # This bounded wait is *only* triggered by an ADB reconnect event.
    # It does not periodically scan for devices or retry failed authorizations.
    for attempt in range(max_boot_checks):
        boot = shell(adb, serial, "getprop", "sys.boot_completed")
        if boot == "1":
            break
        if attempt + 1 == max_boot_checks:
            log("BOOT_NOT_READY")
            return False
        time.sleep(boot_delay)
    if shell(adb, serial, "getprop", "ro.product.model") != MODEL:
        log("MODEL_MISMATCH")
        return False
    if not shell(adb, serial, "pm", "path", APP).startswith("package:"):
        log("LAB_NOT_INSTALLED")
        return False

    # Never interfere with an ongoing call, even if the old host died.
    telephony = shell(adb, serial, "dumpsys", "telephony.registry")
    audio = shell(adb, serial, "dumpsys", "audio")
    states = re.findall(r"mCallState=(\d)", telephony)
    modes = re.findall(r"Actual mode = (MODE_[A-Z_]+)", audio)
    if not states or states[0] != "0" or not modes or modes[0] != "MODE_NORMAL":
        log("CALL_ACTIVE_OR_UNDETERMINED_SKIP")
        return False
    if host_registered(adb, serial):
        log("HOST_ALREADY_READY")
        return True
    if not BOOTSTRAP.is_file():
        log("BOOTSTRAP_MISSING")
        return False
    env = os.environ.copy()
    env["ANDROID_SERIAL"] = serial
    env["PATH"] = str(Path(adb).parent) + os.pathsep + env.get("PATH", "/usr/bin:/bin")
    result = subprocess.run(["/bin/bash", str(BOOTSTRAP), "--start"], env=env,
                            capture_output=True, text=True, timeout=50, check=False)
    if result.returncode == 0 and host_registered(adb, serial):
        log("HOST_RESTORED_BINDER_VERIFIED")
        return True
    log("HOST_RECOVERY_FAILED")
    return False


def watch(adb: str, serial: str) -> None:
    """Block on ADB device notifications. No cron, fixed-interval or scheduler."""
    log("WATCHER_STARTED")
    while True:
        try:
            with subprocess.Popen([adb, "track-devices"], stdout=subprocess.PIPE,
                                  stderr=subprocess.DEVNULL, text=True, bufsize=1) as process:
                previous: dict[str, str] = {}
                frame: list[str] = []
                assert process.stdout is not None
                for line in process.stdout:
                    if line.strip():
                        frame.append(line)
                        continue
                    current = parse_snapshot(frame)
                    frame.clear()
                    if newly_online(previous, current, serial):
                        log("AUTHORIZED_DEVICE_RECONNECTED")
                        recover(adb, serial)
                    previous = current
        except (OSError, subprocess.SubprocessError):
            log("ADB_EVENT_STREAM_INTERRUPTED")
        # Restart only a broken *event stream*, not device scans.
        time.sleep(2)


def agent_path() -> Path:
    return Path.home() / "Library" / "LaunchAgents" / (LABEL + ".plist")


def service_target() -> str:
    return "gui/" + str(os.getuid()) + "/" + LABEL


def setup_service(adb: str, serial: str) -> None:
    if sys.platform != "darwin":
        raise RuntimeError("macOS_required")
    if shell(adb, serial, "getprop", "ro.product.model") != MODEL:
        raise RuntimeError("approved_S22_not_connected")
    if not BOOTSTRAP.is_file():
        raise RuntimeError("missing_bootstrap")
    launchctl = shutil.which("launchctl") or "/bin/launchctl"
    directory = agent_path().parent
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    logs = Path.home() / "Library" / "Logs" / "AI-Calls"
    logs.mkdir(mode=0o700, parents=True, exist_ok=True)
    body = {
        "Label": LABEL,
        "ProgramArguments": [sys.executable, str(Path(__file__).resolve()),
                             "--watch", "--serial", serial, "--adb", adb],
        "EnvironmentVariables": {"PATH": str(Path(adb).parent) + ":/usr/bin:/bin"},
        "RunAtLoad": True,
        "KeepAlive": True,
        "ProcessType": "Background",
        "ThrottleInterval": 15,
        "StandardOutPath": str(logs / "s8-gsm-reconnect.log"),
        "StandardErrorPath": str(logs / "s8-gsm-reconnect-error.log"),
    }
    path = agent_path()
    # Restrict private ADB serial in plist to the owning macOS account.
    with path.open("wb") as output:
        os.fchmod(output.fileno(), 0o600)
        plistlib.dump(body, output)
    scope = "gui/" + str(os.getuid())
    run([launchctl, "bootout", service_target()], timeout=10)
    started = run([launchctl, "bootstrap", scope, str(path)], timeout=15)
    if started.returncode != 0:
        raise RuntimeError("launchctl_bootstrap_failed")
    status = run([launchctl, "print", service_target()], timeout=10)
    if status.returncode != 0:
        raise RuntimeError("launchctl_service_not_registered")
    log("WATCHER_INSTALLED_EVENT_DRIVEN")


def remove_service() -> None:
    launchctl = shutil.which("launchctl") or "/bin/launchctl"
    run([launchctl, "bootout", service_target()])
    agent_path().unlink(missing_ok=True)
    log("WATCHER_REMOVED")


def main() -> int:
    parser = argparse.ArgumentParser()
    actions = parser.add_mutually_exclusive_group(required=True)
    for action in ("once", "watch", "install", "uninstall", "status"):
        actions.add_argument("--" + action, action="store_true")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--adb", default=shutil.which("adb"))
    args = parser.parse_args()
    if args.uninstall:
        remove_service()
        return 0
    if args.status:
        result = run(["/bin/launchctl", "print", service_target()])
        log("WATCHER_INSTALLED" if result.returncode == 0 else "WATCHER_NOT_INSTALLED")
        return 0
    if not args.serial or not SERIAL_PATTERN.fullmatch(args.serial):
        parser.error("a validated device serial is required")
    if not args.adb or not Path(args.adb).is_file():
        parser.error("an absolute ADB executable is required")
    if args.install:
        setup_service(args.adb, args.serial)
    elif args.once:
        return 0 if recover(args.adb, args.serial) else 1
    elif args.watch:
        watch(args.adb, args.serial)
    return 0


if __name__ == "__main__":
    sys.exit(main())
