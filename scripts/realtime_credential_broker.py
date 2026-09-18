#!/usr/bin/env python3
"""Loopback-only development broker for OpenAI Realtime client secrets.

The Android app authenticates to this broker with a separate development bearer token. The
long-lived OpenAI API key exists only in the host process environment and is used server-side to
mint one short-lived client secret. The broker returns only ``value`` and ``expires_at`` and never
logs credentials or upstream response bodies.

Expose this loopback listener through an authenticated HTTPS tunnel when a physical Android device
needs access. Do not bind it directly to an untrusted network interface.
"""

from __future__ import annotations

import argparse
import hmac
import json
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Mapping, Optional


OPENAI_CLIENT_SECRET_URL = "https://api.openai.com/v1/realtime/client_secrets"
DEFAULT_MODEL = "gpt-realtime-2.1"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8765
MAX_CLIENT_BODY_BYTES = 1024
MAX_UPSTREAM_RESPONSE_BYTES = 64 * 1024
TOKEN_MIN_CHARS = 32
MODEL_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


class BrokerUpstreamError(RuntimeError):
    """Sanitized upstream failure that never includes credentials or response bodies."""


@dataclass(frozen=True, repr=False)
class BrokerConfig:
    openai_api_key: str
    client_bearer_token: str
    model: str = DEFAULT_MODEL
    safety_identifier: Optional[str] = None

    def __post_init__(self) -> None:
        if not self.openai_api_key or not self.openai_api_key.strip():
            raise ValueError("OPENAI_API_KEY must be set")
        if len(self.client_bearer_token) < TOKEN_MIN_CHARS:
            raise ValueError(
                f"AI_CALL_BRIDGE_BROKER_TOKEN must be at least {TOKEN_MIN_CHARS} characters"
            )
        if hmac.compare_digest(self.client_bearer_token, self.openai_api_key):
            raise ValueError("broker client token must be distinct from OPENAI_API_KEY")
        if not MODEL_PATTERN.fullmatch(self.model):
            raise ValueError("OPENAI_REALTIME_MODEL contains unsupported characters")
        if self.safety_identifier is not None:
            value = self.safety_identifier.strip()
            if not value or len(value) > 256 or any(ord(ch) < 32 for ch in value):
                raise ValueError("OPENAI_SAFETY_IDENTIFIER is invalid")
            object.__setattr__(self, "safety_identifier", value)

    @classmethod
    def from_env(cls, env: Mapping[str, str] = os.environ) -> "BrokerConfig":
        api_key = env.get("OPENAI_API_KEY", "").strip()
        token = env.get("AI_CALL_BRIDGE_BROKER_TOKEN", "").strip()
        model = env.get("OPENAI_REALTIME_MODEL", DEFAULT_MODEL).strip() or DEFAULT_MODEL
        safety_identifier = env.get("OPENAI_SAFETY_IDENTIFIER")
        return cls(api_key, token, model, safety_identifier)

    def __repr__(self) -> str:
        safety = "set" if self.safety_identifier else "unset"
        return f"BrokerConfig(openai_api_key=REDACTED, client_bearer_token=REDACTED, model={self.model!r}, safety_identifier={safety})"


def build_openai_request(config: BrokerConfig) -> urllib.request.Request:
    payload = json.dumps(
        {
            "session": {
                "type": "realtime",
                "model": config.model,
            }
        },
        separators=(",", ":"),
    ).encode("utf-8")
    request = urllib.request.Request(
        OPENAI_CLIENT_SECRET_URL,
        data=payload,
        method="POST",
    )
    request.add_header("Authorization", f"Bearer {config.openai_api_key}")
    request.add_header("Content-Type", "application/json")
    if config.safety_identifier:
        request.add_header("OpenAI-Safety-Identifier", config.safety_identifier)
    return request


def _read_limited(response, limit: int) -> bytes:
    data = response.read(limit + 1)
    if len(data) > limit:
        raise BrokerUpstreamError("OpenAI client-secret response exceeded size limit")
    return data


def _minimal_secret_payload(raw: bytes) -> dict[str, object]:
    try:
        decoded = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BrokerUpstreamError("OpenAI client-secret response was invalid JSON") from error
    if not isinstance(decoded, dict):
        raise BrokerUpstreamError("OpenAI client-secret response had invalid shape")

    value = decoded.get("value")
    expires_at = decoded.get("expires_at")
    if not isinstance(value, str) or not value.strip():
        raise BrokerUpstreamError("OpenAI client-secret response was missing value")
    if isinstance(expires_at, bool) or not isinstance(expires_at, int) or expires_at <= 0:
        raise BrokerUpstreamError("OpenAI client-secret response was missing expires_at")
    return {"value": value.strip(), "expires_at": expires_at}


def mint_client_secret(
    config: BrokerConfig,
    *,
    opener: Callable[..., object] = urllib.request.urlopen,
) -> dict[str, object]:
    request = build_openai_request(config)
    try:
        with opener(request, timeout=15) as response:
            status = getattr(response, "status", None)
            if status != 200:
                raise BrokerUpstreamError("OpenAI client-secret request failed")
            raw = _read_limited(response, MAX_UPSTREAM_RESPONSE_BYTES)
    except BrokerUpstreamError:
        raise
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError) as error:
        raise BrokerUpstreamError("OpenAI client-secret request failed") from error
    return _minimal_secret_payload(raw)


def is_client_authorized(authorization_header: Optional[str], expected_token: str) -> bool:
    if not authorization_header:
        return False
    scheme, separator, provided = authorization_header.partition(" ")
    if not separator or scheme.lower() != "bearer" or not provided:
        return False
    return hmac.compare_digest(provided, expected_token)


def validate_client_body(body: bytes) -> None:
    if len(body) > MAX_CLIENT_BODY_BYTES:
        raise ValueError("request body is too large")
    if not body or not body.strip():
        return
    try:
        parsed = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("request body must be an empty JSON object") from error
    if not isinstance(parsed, dict) or parsed:
        raise ValueError("request body must be an empty JSON object")


def _handler_class(config: BrokerConfig):
    class CredentialBrokerHandler(BaseHTTPRequestHandler):
        server_version = "AI-Call-Bridge-Credential-Broker"
        sys_version = ""

        def log_message(self, _format: str, *_args) -> None:
            # Never let the default HTTP logger accidentally grow into credential/body logging.
            return

        def do_POST(self) -> None:
            if self.path != "/v1/realtime/client-secret":
                self._json_response(404, {"error": "not_found"})
                return
            if not is_client_authorized(self.headers.get("Authorization"), config.client_bearer_token):
                self._json_response(401, {"error": "unauthorized"})
                return

            try:
                raw_length = self.headers.get("Content-Length", "0")
                content_length = int(raw_length)
                if content_length < 0 or content_length > MAX_CLIENT_BODY_BYTES:
                    raise ValueError("invalid content length")
                body = self.rfile.read(content_length) if content_length else b""
                validate_client_body(body)
                payload = mint_client_secret(config)
            except ValueError:
                self._json_response(400, {"error": "invalid_request"})
                return
            except BrokerUpstreamError:
                self._json_response(502, {"error": "credential_upstream_failed"})
                return
            except Exception:
                self._json_response(500, {"error": "credential_broker_failed"})
                return

            self._json_response(200, payload)

        def do_GET(self) -> None:
            self._json_response(405, {"error": "method_not_allowed"})

        def _json_response(self, status: int, payload: Mapping[str, object]) -> None:
            encoded = json.dumps(payload, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Pragma", "no-cache")
            self.end_headers()
            self.wfile.write(encoded)

    return CredentialBrokerHandler


def serve(config: BrokerConfig, *, host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> None:
    if host not in {"127.0.0.1", "::1", "localhost"}:
        raise ValueError("development credential broker must remain loopback-only")
    if not (1 <= port <= 65535):
        raise ValueError("port must be in range 1..65535")
    server = ThreadingHTTPServer((host, port), _handler_class(config))
    print(f"Realtime credential broker listening on http://{host}:{port}", file=sys.stderr)
    print("Expose it only through an authenticated HTTPS tunnel.", file=sys.stderr)
    try:
        server.serve_forever()
    finally:
        server.server_close()


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=int(os.environ.get("AI_CALL_BRIDGE_BROKER_PORT", DEFAULT_PORT)))
    args = parser.parse_args(argv)
    try:
        config = BrokerConfig.from_env()
        serve(config, port=args.port)
    except (ValueError, BrokerUpstreamError) as error:
        print(f"credential broker configuration/startup failed: {error}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
