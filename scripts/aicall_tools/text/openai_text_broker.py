#!/usr/bin/env python3
"""Loopback-only development broker for provider-neutral OpenAI text turns.

Android authenticates with a separate broker bearer. The long-lived OpenAI API key remains only in
the host process environment. The broker forwards text only to the Responses API, requests
``store=false``, returns only final output text, and never logs request/response bodies or secrets.

Expose this loopback listener only through an authenticated HTTPS tunnel for physical-device use.
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

OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"
DEFAULT_MODEL = "gpt-5.6-luna"
DEFAULT_REASONING_EFFORT = "none"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8766
MAX_CLIENT_BODY_BYTES = 16 * 1024
MAX_INPUT_CHARS = 4_000
MAX_UPSTREAM_RESPONSE_BYTES = 256 * 1024
MAX_OUTPUT_CHARS = 2_000
MAX_OUTPUT_TOKENS = 160
TOKEN_MIN_CHARS = 32
MODEL_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")
ALLOWED_REASONING_EFFORTS = frozenset({"none", "low"})

SYSTEM_INSTRUCTIONS = (
    "Jesteś asystentem reprezentującym użytkownika w rozmowie telefonicznej po polsku. "
    "Wejście jest automatycznym transkryptem wypowiedzi drugiej strony i może być niepełne. "
    "Odpowiadaj krótko i naturalnie, najwyżej jednym zdaniem. Nie wymyślaj faktów, nazw, ofert "
    "ani intencji. Jeśli transkrypt jest niejasny lub wygląda jak fragment powitania albo IVR, "
    "poproś krótko o kontynuowanie lub doprecyzowanie. Nie składaj zamówień, nie akceptuj umów, "
    "nie ujawniaj danych wrażliwych i nie podejmuj zobowiązań. Autoryzacja zobowiązań należy do aplikacji."
)


class TextBrokerUpstreamError(RuntimeError):
    """Sanitized upstream failure without response body or credential material."""


@dataclass(frozen=True, repr=False)
class TextBrokerConfig:
    openai_api_key: str
    client_bearer_token: str
    model: str = DEFAULT_MODEL
    reasoning_effort: str = DEFAULT_REASONING_EFFORT
    safety_identifier: Optional[str] = None

    def __post_init__(self) -> None:
        api_key = self.openai_api_key.strip()
        token = self.client_bearer_token.strip()
        model = self.model.strip()
        effort = self.reasoning_effort.strip().lower()
        if not api_key:
            raise ValueError("OPENAI_API_KEY must be set")
        if len(token) < TOKEN_MIN_CHARS:
            raise ValueError(f"AI_CALL_BRIDGE_TEXT_BROKER_TOKEN must be at least {TOKEN_MIN_CHARS} characters")
        if hmac.compare_digest(token, api_key):
            raise ValueError("text broker client token must be distinct from OPENAI_API_KEY")
        if not MODEL_PATTERN.fullmatch(model):
            raise ValueError("OPENAI_TEXT_MODEL contains unsupported characters")
        if effort not in ALLOWED_REASONING_EFFORTS:
            raise ValueError("OPENAI_TEXT_REASONING_EFFORT must be none or low")
        object.__setattr__(self, "openai_api_key", api_key)
        object.__setattr__(self, "client_bearer_token", token)
        object.__setattr__(self, "model", model)
        object.__setattr__(self, "reasoning_effort", effort)
        if self.safety_identifier is not None:
            value = self.safety_identifier.strip()
            if not value or len(value) > 256 or any(ord(ch) < 32 for ch in value):
                raise ValueError("OPENAI_SAFETY_IDENTIFIER is invalid")
            object.__setattr__(self, "safety_identifier", value)

    @classmethod
    def from_env(cls, env: Mapping[str, str] = os.environ) -> "TextBrokerConfig":
        return cls(
            env.get("OPENAI_API_KEY", ""),
            env.get("AI_CALL_BRIDGE_TEXT_BROKER_TOKEN", ""),
            env.get("OPENAI_TEXT_MODEL", DEFAULT_MODEL) or DEFAULT_MODEL,
            env.get("OPENAI_TEXT_REASONING_EFFORT", DEFAULT_REASONING_EFFORT) or DEFAULT_REASONING_EFFORT,
            env.get("OPENAI_SAFETY_IDENTIFIER"),
        )

    def __repr__(self) -> str:
        safety = "set" if self.safety_identifier else "unset"
        return (
            "TextBrokerConfig(openai_api_key=REDACTED, client_bearer_token=REDACTED, "
            f"model={self.model!r}, reasoning_effort={self.reasoning_effort!r}, safety_identifier={safety})"
        )


def is_client_authorized(authorization_header: Optional[str], expected_token: str) -> bool:
    if not authorization_header:
        return False
    scheme, separator, provided = authorization_header.partition(" ")
    return bool(separator and scheme.lower() == "bearer" and provided and hmac.compare_digest(provided, expected_token))


def parse_client_body(body: bytes) -> str:
    if not body or len(body) > MAX_CLIENT_BODY_BYTES:
        raise ValueError("invalid request body size")
    try:
        parsed = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("request body must be JSON") from error
    if not isinstance(parsed, dict) or set(parsed) != {"input"}:
        raise ValueError("request body must contain only input")
    value = parsed.get("input")
    if not isinstance(value, str):
        raise ValueError("input must be a string")
    value = value.strip()
    if not value or len(value) > MAX_INPUT_CHARS or any(ord(ch) == 0 for ch in value):
        raise ValueError("input is invalid")
    return value


def build_openai_request(config: TextBrokerConfig, user_text: str) -> urllib.request.Request:
    payload = json.dumps(
        {
            "model": config.model,
            "reasoning": {"effort": config.reasoning_effort},
            "instructions": SYSTEM_INSTRUCTIONS,
            "input": user_text,
            "max_output_tokens": MAX_OUTPUT_TOKENS,
            "store": False,
        },
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    request = urllib.request.Request(OPENAI_RESPONSES_URL, data=payload, method="POST")
    request.add_header("Authorization", f"Bearer {config.openai_api_key}")
    request.add_header("Content-Type", "application/json")
    if config.safety_identifier:
        request.add_header("OpenAI-Safety-Identifier", config.safety_identifier)
    return request


def _read_limited(response, limit: int) -> bytes:
    data = response.read(limit + 1)
    if len(data) > limit:
        raise TextBrokerUpstreamError("OpenAI text response exceeded size limit")
    return data


def extract_output_text(raw: bytes) -> str:
    try:
        decoded = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise TextBrokerUpstreamError("OpenAI text response was invalid JSON") from error
    if not isinstance(decoded, dict) or decoded.get("status") not in {None, "completed"}:
        raise TextBrokerUpstreamError("OpenAI text response did not complete")
    parts: list[str] = []
    output = decoded.get("output")
    if isinstance(output, list):
        for item in output:
            if not isinstance(item, dict) or item.get("type") != "message":
                continue
            content = item.get("content")
            if not isinstance(content, list):
                continue
            for part in content:
                if isinstance(part, dict) and part.get("type") == "output_text" and isinstance(part.get("text"), str):
                    text = part["text"].strip()
                    if text:
                        parts.append(text)
    text = "\n".join(parts).strip()
    if not text:
        fallback = decoded.get("output_text")
        if isinstance(fallback, str):
            text = fallback.strip()
    if not text:
        raise TextBrokerUpstreamError("OpenAI text response contained no output text")
    if len(text) > MAX_OUTPUT_CHARS:
        raise TextBrokerUpstreamError("OpenAI text output exceeded size limit")
    return text


def generate_text(
    config: TextBrokerConfig,
    user_text: str,
    *,
    opener: Callable[..., object] = urllib.request.urlopen,
) -> str:
    request = build_openai_request(config, user_text)
    try:
        with opener(request, timeout=20) as response:
            if getattr(response, "status", None) != 200:
                raise TextBrokerUpstreamError("OpenAI text request failed")
            raw = _read_limited(response, MAX_UPSTREAM_RESPONSE_BYTES)
    except TextBrokerUpstreamError:
        raise
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError) as error:
        raise TextBrokerUpstreamError("OpenAI text request failed") from error
    return extract_output_text(raw)


def _handler_class(config: TextBrokerConfig):
    class TextBrokerHandler(BaseHTTPRequestHandler):
        server_version = "AI-Call-Bridge-Text-Broker"
        sys_version = ""

        def log_message(self, _format: str, *_args) -> None:
            return

        def do_POST(self) -> None:
            if self.path != "/v1/call-text-turn":
                self._json_response(404, {"error": "not_found"})
                return
            if not is_client_authorized(self.headers.get("Authorization"), config.client_bearer_token):
                self._json_response(401, {"error": "unauthorized"})
                return
            try:
                content_length = int(self.headers.get("Content-Length", "0"))
                if content_length <= 0 or content_length > MAX_CLIENT_BODY_BYTES:
                    raise ValueError("invalid content length")
                user_text = parse_client_body(self.rfile.read(content_length))
                text = generate_text(config, user_text)
            except ValueError:
                self._json_response(400, {"error": "invalid_request"})
                return
            except TextBrokerUpstreamError:
                self._json_response(502, {"error": "text_upstream_failed"})
                return
            except Exception:
                self._json_response(500, {"error": "text_broker_failed"})
                return
            self._json_response(200, {"text": text, "model": config.model})

        def do_GET(self) -> None:
            self._json_response(405, {"error": "method_not_allowed"})

        def _json_response(self, status: int, payload: Mapping[str, object]) -> None:
            encoded = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Pragma", "no-cache")
            self.end_headers()
            self.wfile.write(encoded)

    return TextBrokerHandler


def serve(config: TextBrokerConfig, *, host: str = DEFAULT_HOST, port: int = DEFAULT_PORT) -> None:
    if host not in {"127.0.0.1", "::1", "localhost"}:
        raise ValueError("development text broker must remain loopback-only")
    if not 1 <= port <= 65535:
        raise ValueError("port must be in range 1..65535")
    server = ThreadingHTTPServer((host, port), _handler_class(config))
    print(f"OpenAI text broker listening on http://{host}:{port}", file=sys.stderr)
    print("Expose it only through an authenticated HTTPS tunnel.", file=sys.stderr)
    try:
        server.serve_forever()
    finally:
        server.server_close()


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=int(os.environ.get("AI_CALL_BRIDGE_TEXT_BROKER_PORT", DEFAULT_PORT)))
    args = parser.parse_args(argv)
    try:
        serve(TextBrokerConfig.from_env(), port=args.port)
    except ValueError as error:
        print(f"text broker configuration/startup failed: {error}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
