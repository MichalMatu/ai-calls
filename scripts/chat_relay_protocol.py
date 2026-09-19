#!/usr/bin/env python3
"""Shared text envelope used by the Android developer relay and host bridge."""

from __future__ import annotations

from dataclasses import dataclass
import re

MAGIC = "AICALL_CHAT_RELAY_V1"
MAX_TEXT_CHARS = 4_000
_SESSION_RE = re.compile(r"^[A-Za-z0-9._-]{1,80}$")
_REQUIRED_FIELDS = ("kind", "session", "turn", "text_chars")


@dataclass(frozen=True)
class Envelope:
    session_id: str
    turn_id: int
    text: str

    def validate(self, max_text_chars: int = MAX_TEXT_CHARS) -> None:
        if not _SESSION_RE.fullmatch(self.session_id):
            raise ValueError("invalid_relay_session_id")
        if not 1 <= self.turn_id <= 10_000:
            raise ValueError("invalid_relay_turn_id")
        if not self.text.strip():
            raise ValueError("relay_text_must_not_be_blank")
        if len(self.text) > max_text_chars:
            raise ValueError("relay_text_too_long")


def encode(kind: str, envelope: Envelope) -> str:
    _validate_kind(kind)
    envelope.validate()
    return (
        f"{MAGIC}\n"
        f"kind={kind}\n"
        f"session={envelope.session_id}\n"
        f"turn={envelope.turn_id}\n"
        f"text_chars={len(envelope.text)}\n"
        "\n"
        f"{envelope.text}"
    )


def decode(expected_kind: str, raw: str) -> Envelope:
    _validate_kind(expected_kind)
    normalized = raw.replace("\r\n", "\n")
    header, separator, text = normalized.partition("\n\n")
    if not separator:
        raise ValueError("relay_envelope_missing_body")
    lines = header.splitlines()
    if not lines or lines[0] != MAGIC:
        raise ValueError("relay_envelope_bad_magic")

    fields: dict[str, str] = {}
    for line in lines[1:]:
        if "=" not in line:
            raise ValueError("relay_envelope_bad_header")
        key, value = line.split("=", 1)
        if key not in _REQUIRED_FIELDS:
            raise ValueError("relay_envelope_unknown_header")
        if key in fields:
            raise ValueError("relay_envelope_duplicate_header")
        fields[key] = value
    if tuple(fields.keys()) != _REQUIRED_FIELDS:
        raise ValueError("relay_envelope_missing_header")
    if fields["kind"] != expected_kind:
        raise ValueError("relay_envelope_wrong_kind")
    try:
        turn_id = int(fields["turn"])
        text_chars = int(fields["text_chars"])
    except ValueError as error:
        raise ValueError("relay_envelope_bad_number") from error
    if len(text) != text_chars:
        raise ValueError("relay_envelope_text_length_mismatch")
    envelope = Envelope(fields["session"], turn_id, text)
    envelope.validate()
    return envelope


def validate_session_id(session_id: str) -> str:
    Envelope(session_id, 1, "probe").validate()
    return session_id


def _validate_kind(kind: str) -> None:
    if kind not in {"request", "response"}:
        raise ValueError("invalid_relay_kind")
