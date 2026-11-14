"""Deterministic non-speech PCM/WAV fixtures for Gemma Audio I/O stress tests.

This module never reads private call evidence or downloads model files.
Generate on demand; do not commit produced binary artifacts.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 16_000
MAX_SECONDS = 30


def _sample(kind: str, index: int) -> int:
    if kind == "silence":
        return 0
    if kind == "tone_440":
        return round(13_000 * math.sin(2 * math.pi * 440 * index / SAMPLE_RATE))
    if kind == "bursts":
        active = (index // 3_200) % 2 == 0
        return round(9_000 * math.sin(2 * math.pi * 220 * index / SAMPLE_RATE)) if active else 0
    if kind == "clipped":
        return 32_767 if index % 320 < 160 else -32_768
    raise ValueError("unsupported_fixture_kind")


def pcm16(kind: str, seconds: int = 1) -> bytes:
    if not 1 <= seconds <= MAX_SECONDS:
        raise ValueError("seconds_out_of_bounds")
    return b"".join(struct.pack("<h", _sample(kind, n))
                    for n in range(SAMPLE_RATE * seconds))


def generate(output_dir: Path) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    cases = [("silence", 1), ("tone_440", 1), ("bursts", 2), ("clipped", 1)]
    manifest = {"sample_rate_hz": SAMPLE_RATE, "channels": 1, "bits_per_sample": 16,
                "synthetic_non_speech": True, "cases": []}
    for name, seconds in cases:
        data = pcm16(name, seconds)
        target = output_dir / (name + ".wav")
        with wave.open(str(target), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(SAMPLE_RATE)
            out.writeframes(data)
        manifest["cases"].append({
            "name": name, "seconds": seconds, "frames": SAMPLE_RATE * seconds,
            "pcm_sha256": hashlib.sha256(data).hexdigest(),
            "wav_sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        })
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = generate(args.output)
    print(json.dumps({"synthetic_cases": len(result["cases"]),
                      "sample_rate_hz": SAMPLE_RATE, "non_speech": True}))


if __name__ == "__main__":
    main()
