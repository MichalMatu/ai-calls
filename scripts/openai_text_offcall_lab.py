#!/usr/bin/env python3
"""Run the complete protected OPENAI_TEXT off-call lab from one host command.

Only this launcher and the loopback broker ever receive OPENAI_API_KEY. The S22 smoke child receives
only a random independent broker bearer plus the temporary HTTPS endpoint. Cloudflared receives
neither secret. All child processes are torn down on exit.
"""

from __future__ import annotations

import os
import queue
import re
import secrets
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Mapping, Optional

SCRIPT_DIR = Path(__file__).resolve().parent
BROKER_SCRIPT = SCRIPT_DIR / "openai_text_broker.py"
SMOKE_SCRIPT = SCRIPT_DIR / "openai_text_offcall_smoke.py"
BROKER_PATH = "/v1/call-text-turn"
DEFAULT_SERIAL = "RFCT70L7E8J"
TOKEN_BYTES = 48
QUICK_TUNNEL_ATTEMPTS = 3
TRY_CLOUDFLARE_URL_RE = re.compile(
    r"https://[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.trycloudflare\.com(?![A-Za-z0-9.-])",
    re.IGNORECASE,
)
BASE_ENV_KEYS = (
    "PATH", "HOME", "TMPDIR", "LANG", "LC_ALL", "SSL_CERT_FILE", "SSL_CERT_DIR",
    "ANDROID_HOME", "ANDROID_SDK_ROOT", "ADB_VENDOR_KEYS",
)


def require_openai_api_key(env: Mapping[str, str]) -> str:
    key = env.get("OPENAI_API_KEY", "").strip()
    if not key:
        raise ValueError(
            "OPENAI_API_KEY is not set in the host environment; do not place it in Android config, Intent, ADB argv, or repo"
        )
    return key


def generate_broker_token() -> str:
    while True:
        token = secrets.token_urlsafe(TOKEN_BYTES)
        if len(token) >= 32 and not token.lower().startswith("sk-"):
            return token


def base_child_env(host_env: Mapping[str, str]) -> dict[str, str]:
    return {key: value for key in BASE_ENV_KEYS if (value := host_env.get(key))}


def broker_environment(host_env: Mapping[str, str], api_key: str, broker_token: str) -> dict[str, str]:
    child = base_child_env(host_env)
    child["OPENAI_API_KEY"] = api_key
    child["AI_CALL_BRIDGE_TEXT_BROKER_TOKEN"] = broker_token
    for name in ("OPENAI_TEXT_MODEL", "OPENAI_TEXT_REASONING_EFFORT", "OPENAI_SAFETY_IDENTIFIER"):
        value = host_env.get(name)
        if value:
            child[name] = value
    return child


def smoke_environment(host_env: Mapping[str, str], broker_token: str, endpoint: str) -> dict[str, str]:
    child = base_child_env(host_env)
    child["AI_CALL_BRIDGE_TEXT_BROKER_TOKEN"] = broker_token
    child["AI_CALL_BRIDGE_TEXT_BROKER_HTTPS_URL"] = endpoint
    if "OPENAI_API_KEY" in child:
        raise AssertionError("standard OpenAI key leaked into smoke environment")
    return child


def require_cloudflared() -> str:
    binary = shutil.which("cloudflared")
    if not binary:
        raise RuntimeError("cloudflared is required for the physical OPENAI_TEXT off-call lab")
    return binary


def pick_loopback_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def wait_for_loopback(process: subprocess.Popen, port: int, timeout_seconds: float = 5.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("OpenAI text broker exited before becoming ready")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.05)
    raise TimeoutError("OpenAI text broker did not become ready")


def wait_for_tunnel_url(process: subprocess.Popen, timeout_seconds: float = 30.0) -> str:
    if process.stderr is None:
        raise RuntimeError("cloudflared stderr unavailable")
    lines: queue.Queue[Optional[str]] = queue.Queue()

    def reader() -> None:
        try:
            for line in process.stderr:
                lines.put(line)
        finally:
            lines.put(None)

    threading.Thread(target=reader, name="openai-text-cloudflared-reader", daemon=True).start()
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            line = lines.get(timeout=0.25)
        except queue.Empty:
            if process.poll() is not None:
                raise RuntimeError("cloudflared exited before publishing a tunnel URL")
            continue
        if line is None:
            raise RuntimeError("cloudflared exited before publishing a tunnel URL")
        match = TRY_CLOUDFLARE_URL_RE.search(line)
        if match:
            return match.group(0).lower()
    raise TimeoutError("cloudflared did not publish a tunnel URL")


def wait_for_public_broker(endpoint: str, timeout_seconds: float = 35.0) -> None:
    time.sleep(4.0)
    request = urllib.request.Request(
        endpoint,
        data=b'{"input":"probe"}',
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                if getattr(response, "status", None) == 401:
                    return
        except urllib.error.HTTPError as error:
            if error.code == 401:
                return
        except (urllib.error.URLError, TimeoutError, OSError):
            pass
        time.sleep(0.25)
    raise TimeoutError("public OpenAI text broker did not become reachable and protected")


def stop_process(process: Optional[subprocess.Popen]) -> None:
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def start_protected_tunnel(cloudflared: str, port: int, host_env: Mapping[str, str]) -> tuple[subprocess.Popen, str]:
    last_error: Optional[BaseException] = None
    for _ in range(QUICK_TUNNEL_ATTEMPTS):
        tunnel = subprocess.Popen(
            [cloudflared, "tunnel", "--url", f"http://127.0.0.1:{port}", "--no-autoupdate"],
            env=base_child_env(host_env),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        try:
            endpoint = wait_for_tunnel_url(tunnel).rstrip("/") + BROKER_PATH
            wait_for_public_broker(endpoint)
            return tunnel, endpoint
        except (RuntimeError, TimeoutError, OSError) as error:
            last_error = error
            stop_process(tunnel)
    raise RuntimeError("Quick Tunnel failed to become protected and reachable") from last_error


def run_lab(serial: str = DEFAULT_SERIAL, *, host_env: Optional[Mapping[str, str]] = None) -> int:
    env = dict(os.environ if host_env is None else host_env)
    api_key = require_openai_api_key(env)
    cloudflared = require_cloudflared()
    broker_token = generate_broker_token()
    port = pick_loopback_port()
    broker: Optional[subprocess.Popen] = None
    tunnel: Optional[subprocess.Popen] = None
    try:
        broker = subprocess.Popen(
            [sys.executable, str(BROKER_SCRIPT), "--port", str(port)],
            env=broker_environment(env, api_key, broker_token),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        wait_for_loopback(broker, port)
        tunnel, endpoint = start_protected_tunnel(cloudflared, port, env)
        print("openai_text_broker_public_protected=true")
        completed = subprocess.run(
            [sys.executable, str(SMOKE_SCRIPT), serial],
            env=smoke_environment(env, broker_token, endpoint),
            check=False,
        )
        return completed.returncode
    finally:
        stop_process(tunnel)
        stop_process(broker)


def main(argv: Optional[list[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) > 1:
        print("usage: openai_text_offcall_lab.py [adb-serial]", file=sys.stderr)
        return 2
    try:
        return run_lab(args[0] if args else DEFAULT_SERIAL)
    except (ValueError, RuntimeError, TimeoutError, OSError) as error:
        print(f"OPENAI_TEXT off-call lab failed: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
