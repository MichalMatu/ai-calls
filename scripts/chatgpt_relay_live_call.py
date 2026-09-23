#!/usr/bin/env python3
"""Controlled Orange live-call bridge: S22 STT -> interactive ChatGPT -> S22 TTS.

This is developer tooling. It never calls an OpenAI API. A transient Git branch is used only as a
human-visible mailbox between a running Local Agent task and the current interactive ChatGPT chat.
The branch is deleted during cleanup and raw transcripts are not copied into durable test results.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Callable, Optional

import chat_relay_protocol as protocol
from autonomous_call_loop import wait_for_active_call, wait_for_audio_signal
from realtime_live_call_smoke import validate_live_preflight
from realtime_network_smoke import PACKAGE_NAME, is_direct_usb_target
from s22_call_control import Adb, normalize_number

ORANGE_SUPPORT_NUMBER = "510100100"
ALLOWLIST = frozenset({ORANGE_SUPPORT_NUMBER})
DEFAULT_SERIAL = "RFCT70L7E8J"
PROBE_ACTIVITY = f"{PACKAGE_NAME}/.developerrelay.ChatRelayProbeActivity"
REQUEST_PATH = "files/chat-relay/request.txt"
RESPONSE_PATH = "files/chat-relay/response.txt"
REPORT_PATH = "files/chat-relay-live-call-report.txt"
MAX_TURNS = 5
MAX_SESSION_SECONDS = 240.0
RESPONSE_TIMEOUT_SECONDS = 100.0


def normalize_allowlisted_target(raw: str) -> str:
    number = normalize_number(raw)
    if number not in ALLOWLIST:
        raise ValueError("target is not in the operator-defined live-test allowlist")
    return number


def relay_branch_name(session_id: str) -> str:
    protocol.validate_session_id(session_id)
    return f"chat-relay/{session_id}"


def delete_remote_branch_verified(repo_root: Path, branch: str, attempts: int = 3) -> bool:
    if not branch.startswith("chat-relay/"):
        raise ValueError("refusing_to_delete_non_relay_branch")
    protocol.validate_session_id(branch.split("/", 1)[1])
    if not 1 <= attempts <= 5:
        raise ValueError("invalid_cleanup_attempts")
    for attempt in range(attempts):
        subprocess.run(
            ["git", "push", "origin", "--delete", branch],
            cwd=repo_root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        probe = subprocess.run(
            ["git", "ls-remote", "--heads", "origin", f"refs/heads/{branch}"],
            cwd=repo_root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        if probe.returncode != 0 or not probe.stdout.strip():
            return True
        if attempt + 1 < attempts:
            time.sleep(0.2)
    return False


def format_probe_metric_lines(report: dict[str, str]) -> list[str]:
    lines: list[str] = []
    turns = int(report.get("turns_completed", "0") or 0)
    lines.append(f"turns_completed={turns}")
    suffixes = (
        "stt_ready_elapsed_ms",
        "endpoint_capture_ms",
        "estimated_eos_elapsed_ms",
        "stt_elapsed_ms",
        "approved_elapsed_ms",
        "output_pcm_ready_elapsed_ms",
        "tts_pcm_duration_ms",
        "post_tx_hold_ms",
        "post_write_hold_ms",
        "first_tx_elapsed_ms",
        "eos_to_first_tx_ms",
        "tx_write_wall_ms",
        "complete_elapsed_ms",
        "rx_pcm_bytes",
        "tx_pcm_bytes",
    )
    for turn in range(1, turns + 1):
        for suffix in suffixes:
            key = f"turn_{turn}_{suffix}"
            if key in report:
                lines.append(f"{key}={report[key]}")
    for key in ("telephony_rx_pcm_bytes", "telephony_tx_pcm_bytes"):
        if key in report:
            lines.append(f"{key}={report[key]}")
    return lines


def build_probe_start_args(serial: str, session_id: str, max_turns: int) -> list[str]:
    protocol.validate_session_id(session_id)
    if not 1 <= max_turns <= MAX_TURNS:
        raise ValueError("invalid_relay_max_turns")
    return [
        "adb", "-s", serial, "shell", "am", "start", "-W", "-n", PROBE_ACTIVITY,
        "--es", "relay_session_id", session_id,
        "--ei", "relay_max_turns", str(max_turns),
    ]


class AdbRelayMailbox:
    """Reads/writes the app-private relay mailbox without placing payload text in ADB argv."""

    def __init__(self, serial: str):
        self.serial = serial

    def clear(self) -> None:
        self._shell_run_as(["rm", "-rf", "files/chat-relay", REPORT_PATH], check=False)

    def read_request(self) -> Optional[protocol.Envelope]:
        result = self._shell_run_as(["cat", REQUEST_PATH], check=False)
        if result.returncode != 0 or not result.stdout.strip():
            return None
        return protocol.decode("request", result.stdout.replace("\r\n", "\n"))

    def write_response(self, envelope: protocol.Envelope) -> None:
        envelope.validate(600)
        raw = protocol.encode("response", envelope)
        temp_path = RESPONSE_PATH + ".tmp"
        subprocess.run(
            ["adb", "-s", self.serial, "shell", "run-as", PACKAGE_NAME, "tee", temp_path],
            input=raw,
            text=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            check=True,
        )
        subprocess.run(
            ["adb", "-s", self.serial, "shell", "run-as", PACKAGE_NAME, "mv", temp_path, RESPONSE_PATH],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )

    def read_report(self) -> str:
        result = self._shell_run_as(["cat", REPORT_PATH], check=False)
        return result.stdout.replace("\r", "") if result.returncode == 0 else ""

    def _shell_run_as(self, args: list[str], *, check: bool) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["adb", "-s", self.serial, "shell", "run-as", PACKAGE_NAME, *args],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=check,
        )


class GitChatRelayTransport:
    """Transient Git branch mailbox visible to the current ChatGPT conversation."""

    def __init__(self, repo_root: Path, session_id: str):
        self.repo_root = repo_root.resolve()
        self.session_id = protocol.validate_session_id(session_id)
        self.branch = relay_branch_name(session_id)
        self._temp_root: Optional[Path] = None
        self._worktree: Optional[Path] = None
        self._remote_created = False

    def open(self) -> None:
        if self._worktree is not None:
            raise RuntimeError("relay transport already open")
        self._temp_root = Path(tempfile.mkdtemp(prefix="aicall-chat-relay-"))
        self._worktree = self._temp_root / "worktree"
        self._git(self.repo_root, "worktree", "add", "--detach", str(self._worktree), "HEAD")
        self._git(self._worktree, "checkout", "-b", self.branch)
        relay_dir = self._worktree / "relay"
        relay_dir.mkdir(parents=True, exist_ok=True)
        (relay_dir / "SESSION.txt").write_text(
            "Temporary interactive ChatGPT relay. Raw turn files are deleted with this branch.\n",
            encoding="utf-8",
        )
        self._git(self._worktree, "add", "relay/SESSION.txt")
        self._git(self._worktree, "commit", "-m", f"relay: open {self.session_id}")
        self._git(self._worktree, "push", "origin", f"HEAD:refs/heads/{self.branch}")
        self._remote_created = True

    def publish_request(self, envelope: protocol.Envelope) -> str:
        self._require_open()
        if envelope.session_id != self.session_id:
            raise ValueError("relay_request_session_mismatch")
        self._sync_from_remote()
        relative = self._turn_path(envelope.turn_id, "request.txt")
        path = self._worktree / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(protocol.encode("request", envelope), encoding="utf-8")
        self._git(self._worktree, "add", str(relative))
        self._git(self._worktree, "commit", "-m", f"relay: publish turn {envelope.turn_id} request")
        self._git(self._worktree, "push", "origin", f"HEAD:refs/heads/{self.branch}")
        return relative.as_posix()

    def wait_response(
        self,
        turn_id: int,
        timeout_seconds: float,
        abort_check: Optional[Callable[[], None]] = None,
    ) -> protocol.Envelope:
        self._require_open()
        deadline = time.monotonic() + timeout_seconds
        relative = self._turn_path(turn_id, "response.txt")
        while time.monotonic() < deadline:
            if abort_check is not None:
                abort_check()
            self._git(
                self._worktree,
                "fetch",
                "origin",
                f"refs/heads/{self.branch}:refs/remotes/origin/{self.branch}",
            )
            show = subprocess.run(
                [
                    "git", "show",
                    f"refs/remotes/origin/{self.branch}:{relative.as_posix()}",
                ],
                cwd=self._worktree,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
            )
            if show.returncode == 0:
                response = protocol.decode("response", show.stdout)
                if response.session_id != self.session_id or response.turn_id != turn_id:
                    raise RuntimeError("relay_response_identity_mismatch")
                response.validate(600)
                return response
            time.sleep(0.5)
        raise TimeoutError(f"timed out waiting for interactive ChatGPT response to turn {turn_id}")

    def close(self) -> bool:
        worktree = self._worktree
        remote_deleted = True
        try:
            if self._remote_created:
                remote_deleted = delete_remote_branch_verified(
                    worktree or self.repo_root, self.branch
                )
        finally:
            if worktree is not None:
                subprocess.run(
                    ["git", "worktree", "remove", "--force", str(worktree)],
                    cwd=self.repo_root,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    check=False,
                )
            if self._temp_root is not None:
                shutil.rmtree(self._temp_root, ignore_errors=True)
            self._worktree = None
            self._temp_root = None
            self._remote_created = False
        return remote_deleted

    def _sync_from_remote(self) -> None:
        self._git(
            self._worktree,
            "fetch",
            "origin",
            f"refs/heads/{self.branch}:refs/remotes/origin/{self.branch}",
        )
        self._git(self._worktree, "reset", "--hard", f"refs/remotes/origin/{self.branch}")

    def _turn_path(self, turn_id: int, name: str) -> Path:
        if not 1 <= turn_id <= 10_000:
            raise ValueError("invalid_relay_turn_id")
        return Path("relay") / f"turn-{turn_id:04d}" / name

    def _require_open(self) -> None:
        if self._worktree is None:
            raise RuntimeError("relay transport is not open")

    @staticmethod
    def _git(cwd: Path, *args: str) -> str:
        result = subprocess.run(
            ["git", *args],
            cwd=cwd,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=True,
        )
        return result.stdout


def parse_probe_report(text: str) -> Optional[dict[str, str]]:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values if values.get("probe_complete") == "true" else None


def _devices_output() -> str:
    return subprocess.run(
        ["adb", "devices", "-l"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    ).stdout


def _wait_bluetooth(adb: Adb, expected: str, timeout: float = 8.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = adb.shell(["settings", "get", "global", "bluetooth_on"], check=False).strip()
        if value == expected:
            return
        time.sleep(0.25)
    raise RuntimeError(f"Bluetooth did not reach expected state {expected}")


def _set_bluetooth(adb: Adb, enabled: bool) -> None:
    action = "enable" if enabled else "disable"
    output = adb.shell(["cmd", "bluetooth_manager", action], check=False)
    if "Unknown command" in output or "not found" in output:
        raise RuntimeError(f"could not {action} Bluetooth through shell")
    _wait_bluetooth(adb, "1" if enabled else "0")


def call_state_or_none(adb: Adb) -> int | None:
    """Return a known telephony state, treating transient dumpsys failures as unknown."""
    try:
        return adb.call_state()
    except (subprocess.CalledProcessError, RuntimeError):
        return None


def best_effort_hangup(adb: Adb) -> bool:
    """Always request hangup once dial was requested; never let cleanup mask the primary error."""
    try:
        adb.hangup()
        return True
    except Exception as error:
        print(f"hangup_error={error}", file=sys.stderr)
        return False


def wait_for_idle_best_effort(adb: Adb, timeout_seconds: float = 10.0) -> bool:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        state = call_state_or_none(adb)
        if state == 0:
            return True
        time.sleep(0.25)
    return call_state_or_none(adb) == 0


def wait_for_audio_signal_resilient(adb: Adb, *, timeout_seconds: float):
    """Retry only transient ADB/registry failures while preserving the overall signal deadline."""
    deadline = time.monotonic() + timeout_seconds
    last_error: subprocess.CalledProcessError | None = None
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            if last_error is not None:
                raise RuntimeError("telephony registry stayed unavailable while waiting for audio") from last_error
            raise TimeoutError("downlink audio wait budget exhausted")
        try:
            return wait_for_audio_signal(adb, timeout_seconds=remaining)
        except subprocess.CalledProcessError as error:
            command = error.cmd
            tokens = [str(token) for token in command] if isinstance(command, (list, tuple)) else []
            if not ("dumpsys" in tokens and "telephony.registry" in tokens):
                raise
            last_error = error
            print("call_state_probe_transient_error=true", file=sys.stderr)
            time.sleep(min(0.25, max(0.0, remaining)))


class _ConfirmedOffhookAdbView:
    """Narrow ADB view for the first audio probe after OFFHOOK was already observed."""

    def __init__(self, adb: Adb):
        self._adb = adb

    def call_state(self) -> int:
        return 2

    def __getattr__(self, name: str):
        return getattr(self._adb, name)


def wait_for_initial_audio_signal_after_confirmed_offhook(adb: Adb, *, timeout_seconds: float):
    """Probe real downlink audio without re-reading transient telephony.registry state."""
    return wait_for_audio_signal_resilient(
        _ConfirmedOffhookAdbView(adb),
        timeout_seconds=timeout_seconds,
    )


def _require_preflight(adb: Adb, *, known_call_state: int) -> None:
    devices = _devices_output()
    if not is_direct_usb_target(devices, adb.serial or ""):
        raise RuntimeError("ChatGPT relay requires exact direct USB S22 target")
    bluetooth = adb.shell(["settings", "get", "global", "bluetooth_on"]).strip()
    audio_dump = adb.shell(["dumpsys", "audio"])
    snapshot = validate_live_preflight(
        serial=adb.serial or "",
        devices_output=devices,
        bluetooth_setting=bluetooth,
        call_state=known_call_state,
        audio_dump=audio_dump,
    )
    print(
        "live_preflight=true," +
        f"mode:{snapshot.audio_mode},device:{snapshot.active_device_type},muted:{snapshot.voice_call_muted}"
    )


def run_orange_chat_relay(
    *,
    serial: str,
    session_id: str,
    max_turns: int,
    repo_root: Path,
) -> dict[str, str]:
    number = normalize_allowlisted_target(ORANGE_SUPPORT_NUMBER)
    protocol.validate_session_id(session_id)
    if not 1 <= max_turns <= MAX_TURNS:
        raise ValueError("invalid_relay_max_turns")

    adb = Adb(serial)
    mailbox = AdbRelayMailbox(serial)
    transport = GitChatRelayTransport(repo_root, session_id)
    if not is_direct_usb_target(_devices_output(), serial):
        raise RuntimeError("target S22 is not connected through exact direct USB ADB")
    initial_call_state = call_state_or_none(adb)
    if initial_call_state != 0:
        raise RuntimeError(
            "refusing to dial because cellular call state is not confirmed IDLE"
        )

    original_bt = adb.shell(["settings", "get", "global", "bluetooth_on"], check=False).strip()
    if original_bt not in {"0", "1"}:
        raise RuntimeError("could not determine Bluetooth state")
    changed_bt = False
    dialed = False
    muted = False
    started_at = time.monotonic()
    last_turn = 0
    try:
        transport.open()
        print(f"relay_session={session_id}")
        print(f"relay_branch={transport.branch}")
        print("relay_raw_text_retention=transient_branch_only")

        if original_bt == "1":
            _set_bluetooth(adb, False)
            changed_bt = True
            print("bluetooth_disabled_for_test=true")

        print(f"allowlisted_target={number}")
        adb.dial(number)
        dialed = True
        print("dial_requested=true")
        wait_for_active_call(adb, 30.0)

        adb.shell(["cmd", "audio", "adj-mute", "0"])
        muted = True
        time.sleep(0.3)
        _require_preflight(adb, known_call_state=2)
        signal = wait_for_initial_audio_signal_after_confirmed_offhook(
            adb,
            timeout_seconds=25.0,
        )
        print(f"orange_downlink_signal=true,rms:{signal.rms:.3f},peak:{signal.peak}")

        mailbox.clear()
        subprocess.run(build_probe_start_args(serial, session_id, max_turns), check=True)

        while time.monotonic() - started_at < MAX_SESSION_SECONDS:
            state = call_state_or_none(adb)
            if state is not None and state != 2:
                raise RuntimeError("cellular call ended during ChatGPT relay")
            report = parse_probe_report(mailbox.read_report())
            if report is not None:
                if report.get("chat_relay_live_call_success") != "true":
                    raise RuntimeError("relay probe failed: " + report.get("failure_reason", "unknown"))
                if int(report.get("turns_completed", "0")) < 1:
                    raise RuntimeError("relay probe completed without a spoken turn")
                print(f"relay_probe_complete=true,turns:{report.get('turns_completed')}")
                for metric in format_probe_metric_lines(report):
                    print(f"relay_probe_metric={metric}")
                return report

            request = mailbox.read_request()
            if request is None or request.turn_id <= last_turn:
                time.sleep(0.2)
                continue
            if request.session_id != session_id or request.turn_id != last_turn + 1:
                raise RuntimeError("unexpected relay request identity/order")
            request.validate(1_500)
            publish_started = time.monotonic()
            request_path = transport.publish_request(request)
            publish_ms = int((time.monotonic() - publish_started) * 1000)
            print(f"relay_request_published=true,turn:{request.turn_id},path:{request_path}")

            def ensure_call_active() -> None:
                state = call_state_or_none(adb)
                if state is not None and state != 2:
                    raise RuntimeError("cellular call ended while waiting for ChatGPT")

            response_wait_started = time.monotonic()
            response = transport.wait_response(
                request.turn_id,
                RESPONSE_TIMEOUT_SECONDS,
                abort_check=ensure_call_active,
            )
            response_wait_ms = int((time.monotonic() - response_wait_started) * 1000)
            delivery_started = time.monotonic()
            mailbox.write_response(response)
            delivery_ms = int((time.monotonic() - delivery_started) * 1000)
            print(f"relay_response_delivered=true,turn:{response.turn_id},chars:{len(response.text)}")
            print(
                f"relay_transport_metric=turn:{response.turn_id},"
                f"publish_ms:{publish_ms},response_wait_ms:{response_wait_ms},"
                f"adb_delivery_ms:{delivery_ms}"
            )
            last_turn = request.turn_id

        raise TimeoutError("bounded ChatGPT relay call budget exhausted")
    finally:
        primary_error_active = sys.exc_info()[0] is not None
        subprocess.run(
            ["adb", "-s", serial, "shell", "am", "force-stop", PACKAGE_NAME],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        try:
            mailbox.clear()
        except Exception as error:
            print(f"mailbox_cleanup_error={error}", file=sys.stderr)
        if dialed:
            hangup_requested = best_effort_hangup(adb)
            print(f"hangup_requested={str(hangup_requested).lower()}")
            idle = wait_for_idle_best_effort(adb, 10.0)
            print(f"idle_after_hangup={idle}")
        if muted:
            try:
                adb.shell(["cmd", "audio", "adj-unmute", "0"], check=False)
                print("voice_call_unmute_cleanup_requested=true")
            except Exception as error:
                print(f"voice_call_unmute_cleanup_error={error}", file=sys.stderr)
        if changed_bt:
            try:
                _set_bluetooth(adb, True)
                print("bluetooth_restored=true")
            except Exception as error:
                print(f"bluetooth_restore_error={error}", file=sys.stderr)
        branch_deleted = False
        try:
            branch_deleted = transport.close()
        except Exception as error:
            print(f"relay_branch_cleanup_error={error}", file=sys.stderr)
        print("relay_transient_branch_cleanup_requested=true")
        print(f"relay_transient_branch_deleted={str(branch_deleted).lower()}")
        if not branch_deleted and not primary_error_active:
            raise RuntimeError("relay_transient_branch_cleanup_failed")


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default=DEFAULT_SERIAL)
    parser.add_argument("--session", required=True)
    parser.add_argument("--max-turns", type=int, default=2)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    args = parser.parse_args(argv)
    try:
        run_orange_chat_relay(
            serial=args.serial,
            session_id=args.session,
            max_turns=args.max_turns,
            repo_root=args.repo_root,
        )
        return 0
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"ChatGPT relay live call failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
