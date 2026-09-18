#!/usr/bin/env python3
"""Run the complete protected OpenAI Realtime off-call lab from one host command.

The only secret the operator provides is OPENAI_API_KEY in the host environment. This launcher:
- generates an independent one-shot broker bearer in memory;
- starts the loopback-only credential broker;
- exposes it through a temporary Cloudflare Quick Tunnel over HTTPS;
- waits until the public broker endpoint is actually reachable and protected;
- invokes the existing S22 off-call smoke with only the tunnel endpoint + broker bearer;
- tears down tunnel and broker processes on every exit path.

The long-lived OpenAI API key is passed only to the broker child environment. It is never placed in
ADB argv, Android config, cloudflared, the smoke child environment, or launcher output.
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
from typing import Callable, Mapping, Optional, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
BROKER_SCRIPT = SCRIPT_DIR / "realtime_credential_broker.py"
SMOKE_SCRIPT = SCRIPT_DIR / "realtime_network_smoke.py"
BROKER_PATH = "/v1/realtime/client-secret"
TOKEN_BYTES = 48
BROKER_READY_TIMEOUT_SECONDS = 5.0
TUNNEL_READY_TIMEOUT_SECONDS = 30.0
PUBLIC_BROKER_READY_TIMEOUT_SECONDS = 30.0
TRY_CLOUDFLARE_URL_RE = re.compile(
    r"https://[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.trycloudflare\.com\b",
    re.IGNORECASE,
)

PopenFactory = Callable[..., subprocess.Popen]
RunFactory = Callable[..., subprocess.CompletedProcess]


def require_openai_api_key(env: Mapping[str, str]) -> str:
    value = env.get("OPENAI_API_KEY", "").strip()
    if not value:
        raise ValueError(
            "OPENAI_API_KEY is not set in the host environment; "
            "do not paste it into the app, repo, Intent, or ADB command"
        )
    return value


def generate_broker_token() -> str:
    while True:
        token = secrets.token_urlsafe(TOKEN_BYTES)
        if len(token) >= 32 and not token.lower().startswith("sk-"):
            return token


def require_cloudflared(which: Callable[[str], Optional[str]] = shutil.which) -> str:
    binary = which("cloudflared")
    if not binary:
        raise RuntimeError("cloudflared is required (install with: brew install cloudflared)")
    return binary


def pick_loopback_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def build_broker_environment(
    host_env: Mapping[str, str],
    *,
    api_key: str,
    broker_token: str,
) -> dict[str, str]:
    child = dict(host_env)
    child["OPENAI_API_KEY"] = api_key
    child["AI_CALL_BRIDGE_BROKER_TOKEN"] = broker_token
    child.pop("AI_CALL_BRIDGE_BROKER_HTTPS_URL", None)
    return child


def build_tunnel_environment(host_env: Mapping[str, str]) -> dict[str, str]:
    child = dict(host_env)
    child.pop("OPENAI_API_KEY", None)
    child.pop("AI_CALL_BRIDGE_BROKER_TOKEN", None)
    child.pop("AI_CALL_BRIDGE_BROKER_HTTPS_URL", None)
    return child


def build_smoke_environment(
    host_env: Mapping[str, str],
    *,
    broker_token: str,
    credential_endpoint: str,
) -> dict[str, str]:
    child = dict(host_env)
    child.pop("OPENAI_API_KEY", None)
    child["AI_CALL_BRIDGE_BROKER_TOKEN"] = broker_token
    child["AI_CALL_BRIDGE_BROKER_HTTPS_URL"] = credential_endpoint
    return child


def parse_quick_tunnel_url(line: str) -> Optional[str]:
    match = TRY_CLOUDFLARE_URL_RE.search(line)
    return match.group(0).lower() if match else None


def wait_for_loopback(
    process: subprocess.Popen,
    port: int,
    *,
    timeout_seconds: float = BROKER_READY_TIMEOUT_SECONDS,
) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("credential broker exited before becoming ready")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.05)
    raise TimeoutError("credential broker did not become ready")


def wait_for_quick_tunnel_url(
    process: subprocess.Popen,
    *,
    timeout_seconds: float = TUNNEL_READY_TIMEOUT_SECONDS,
) -> str:
    if process.stderr is None:
        raise RuntimeError("cloudflared stderr pipe is unavailable")

    lines: queue.Queue[Optional[str]] = queue.Queue()

    def read_lines() -> None:
        try:
            for line in process.stderr:
                lines.put(line)
        finally:
            lines.put(None)

    threading.Thread(target=read_lines, name="cloudflared-url-reader", daemon=True).start()
    deadline = time.monotonic() + timeout_seconds
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("cloudflared did not publish a Quick Tunnel URL")
        try:
            line = lines.get(timeout=min(0.25, remaining))
        except queue.Empty:
            if process.poll() is not None:
                raise RuntimeError("cloudflared exited before publishing a Quick Tunnel URL")
            continue
        if line is None:
            raise RuntimeError("cloudflared exited before publishing a Quick Tunnel URL")
        url = parse_quick_tunnel_url(line)
        if url is not None:
            return url


def wait_for_public_broker(
    credential_endpoint: str,
    *,
    timeout_seconds: float = PUBLIC_BROKER_READY_TIMEOUT_SECONDS,
    opener: Callable[..., object] = urllib.request.urlopen,
) -> None:
    request = urllib.request.Request(
        credential_endpoint,
        data=b"{}",
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            with opener(request, timeout=3) as response:
                if getattr(response, "status", None) == 401:
                    return
        except urllib.error.HTTPError as error:
            if error.code == 401:
                return
        except (urllib.error.URLError, TimeoutError, OSError):
            pass
        time.sleep(0.25)
    raise TimeoutError("public credential broker did not become reachable and protected")


def stop_process(process: Optional[subprocess.Popen]) -> None:
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def run_lab(
    serial: str,
    *,
    host_env: Optional[Mapping[str, str]] = None,
    popen: PopenFactory = subprocess.Popen,
    run: RunFactory = subprocess.run,
    which: Callable[[str], Optional[str]] = shutil.which,
    port_picker: Callable[[], int] = pick_loopback_port,
    broker_waiter: Callable[[subprocess.Popen, int], None] = wait_for_loopback,
    tunnel_waiter: Callable[[subprocess.Popen], str] = wait_for_quick_tunnel_url,
    public_waiter: Callable[[str], None] = wait_for_public_broker,
) -> int:
    target = serial.strip()
    if not target:
        raise ValueError("ADB serial must not be blank")

    environment = dict(os.environ if host_env is None else host_env)
    api_key = require_openai_api_key(environment)
    cloudflared = require_cloudflared(which)
    broker_token = generate_broker_token()
    port = port_picker()

    broker: Optional[subprocess.Popen] = None
    tunnel: Optional[subprocess.Popen] = None
    try:
        broker_args = [
            sys.executable,
            str(BROKER_SCRIPT),
            "--port",
            str(port),
        ]
        broker = popen(
            broker_args,
            env=build_broker_environment(
                environment,
                api_key=api_key,
                broker_token=broker_token,
            ),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        broker_waiter(broker, port)

        tunnel_args = [
            cloudflared,
            "tunnel",
            "--url",
            f"http://127.0.0.1:{port}",
            "--no-autoupdate",
        ]
        tunnel = popen(
            tunnel_args,
            env=build_tunnel_environment(environment),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        tunnel_url = tunnel_waiter(tunnel)
        credential_endpoint = tunnel_url.rstrip("/") + BROKER_PATH
        public_waiter(credential_endpoint)

        smoke_args = [sys.executable, str(SMOKE_SCRIPT), target]
        completed = run(
            smoke_args,
            env=build_smoke_environment(
                environment,
                broker_token=broker_token,
                credential_endpoint=credential_endpoint,
            ),
            check=False,
        )
        return int(completed.returncode)
    finally:
        stop_process(tunnel)
        stop_process(broker)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 1 or not args[0].strip():
        print("usage: realtime_offcall_lab.py <adb-serial>", file=sys.stderr)
        return 2

    try:
        return run_lab(args[0])
    except (ValueError, RuntimeError, TimeoutError, OSError) as error:
        print(f"Realtime off-call lab failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
