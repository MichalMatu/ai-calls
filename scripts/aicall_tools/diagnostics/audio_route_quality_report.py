#!/usr/bin/env python3
"""Append and compare privacy-safe quality measurements for AI Calls audio routes.

No device access, recording, transcript, call number or automatic Git publication.
Only explicit, aggregated test measurements are accepted.
"""
from __future__ import annotations

import argparse
import json
import math
import re
from collections import defaultdict
from pathlib import Path

INPUTS = ("ANDROID_STT", "PARAKEET_PL", "GEMMA_AUDIO_DIRECT")
MODELS = ("GEMMA_4_E2B", "USER_LITERT")
ENGINES = ("ANDROID_TTS", "PIPER")
MODES = ("synthetic_offcall", "real_call")
OPTIONAL = {
    "voice_id", "wer_errors", "reference_words", "asr_ms", "model_ms", "tts_ms",
    "end_to_end_ms", "voice_quality_1_to_5", "stop_ok", "barge_in_ok",
    "end_call_ok", "evidence_id",
}
REQUIRED = {
    "run_id", "source_sha", "input", "model", "tts_engine", "mode",
    "cases", "passed",
}
FIELDS = REQUIRED | OPTIONAL
ID = re.compile(r"[A-Za-z0-9._:-]{1,100}\Z")
SHA = re.compile(r"[0-9a-f]{7,40}\Z")
ROOT = Path(__file__).resolve().parents[3]
DEFAULT_LEDGER = ROOT / "benchmarks" / "audio_route_quality_runs.jsonl"


def validate(record: object) -> dict:
    if not isinstance(record, dict) or not REQUIRED.issubset(record):
        raise ValueError("missing_required_fields")
    extra = set(record) - FIELDS
    if extra:
        raise ValueError("unexpected_fields:" + ",".join(sorted(extra)))
    for key in ("run_id", "source_sha"):
        if not isinstance(record[key], str):
            raise ValueError("invalid_" + key)
    if not ID.fullmatch(record["run_id"]) or not SHA.fullmatch(record["source_sha"]):
        raise ValueError("invalid_run_id_or_sha")
    if record["input"] not in INPUTS or record["model"] not in MODELS:
        raise ValueError("unsupported_route")
    if record["tts_engine"] not in ENGINES or record["mode"] not in MODES:
        raise ValueError("unsupported_engine_or_mode")
    if record["input"] == "GEMMA_AUDIO_DIRECT" and record["model"] != "GEMMA_4_E2B":
        raise ValueError("unsupported_gemma_audio_model")
    for key in ("cases", "passed"):
        if type(record[key]) is not int or record[key] < 0:
            raise ValueError("invalid_" + key)
    if record["cases"] < 1 or record["passed"] > record["cases"]:
        raise ValueError("invalid_case_counts")
    if ("wer_errors" in record) != ("reference_words" in record):
        raise ValueError("wer_fields_must_be_paired")
    for key in ("wer_errors", "reference_words"):
        if key in record and (type(record[key]) is not int or record[key] < 0):
            raise ValueError("invalid_" + key)
    if record.get("reference_words") == 0:
        raise ValueError("reference_words_must_be_positive")
    for key in ("asr_ms", "model_ms", "tts_ms", "end_to_end_ms"):
        if key in record and (type(record[key]) not in (int, float)
                              or not math.isfinite(record[key]) or record[key] < 0):
            raise ValueError("invalid_" + key)
    score = record.get("voice_quality_1_to_5")
    if score is not None and (type(score) not in (int, float)
                              or not math.isfinite(score) or not 1 <= score <= 5):
        raise ValueError("invalid_voice_quality")
    for key in ("stop_ok", "barge_in_ok", "end_call_ok"):
        if key in record and record[key] is not None and type(record[key]) is not bool:
            raise ValueError("invalid_" + key)
    for key in ("voice_id", "evidence_id"):
        if key in record and (not isinstance(record[key], str)
                             or not ID.fullmatch(record[key])):
            raise ValueError("invalid_" + key)
    return record


def read_ledger(path: Path) -> list[dict]:
    if not path.exists():
        return []
    rows = []
    with path.open(encoding="utf-8") as source:
        for line_no, line in enumerate(source, 1):
            if line.strip():
                try:
                    rows.append(validate(json.loads(line)))
                except (ValueError, TypeError, json.JSONDecodeError) as exc:
                    raise ValueError(f"invalid_ledger_line_{line_no}: {exc}") from exc
    ids = [r["run_id"] for r in rows]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate_ledger_run_id")
    return rows


def add(path: Path, record: dict) -> None:
    record = validate(record)
    rows = read_ledger(path)
    if record["run_id"] in {r["run_id"] for r in rows}:
        raise ValueError("duplicate_run_id")
    path.parent.mkdir(parents=True, exist_ok=True)
    # Append only aggregated metrics; never place call content in the ledger.
    with path.open("a", encoding="utf-8") as target:
        target.write(json.dumps(record, sort_keys=True, ensure_ascii=False) + "\n")


def p95(values: list[float]) -> float:
    return sorted(values)[math.ceil(len(values) * 0.95) - 1]


def summary(rows: list[dict]) -> str:
    groups: dict[tuple[str, str, str, str], list[dict]] = defaultdict(list)
    for row in rows:
        groups[(row["input"], row["model"], row["tts_engine"], row["mode"])].append(row)
    lines = [
        "# AI Calls — porównanie jakości torów audio",
        "",
        "Wyniki tylko z pomiarów. Brak danych ≠ błąd ani zaliczony test.",
        "",
        "| Wejście | Model | TTS | Test | Próby PASS | WER | E2E p50/p95 | Głos 1–5 | STOP / przerwanie / koniec |",
        "|---|---|---|---|---:|---:|---:|---:|---|",
    ]
    for inp in INPUTS:
        for engine in ENGINES:
            model = "GEMMA_4_E2B"
            for mode in MODES:
                group = groups.get((inp, model, engine, mode), [])
                count = sum(r["cases"] for r in group)
                passed = sum(r["passed"] for r in group)
                wc = sum(r.get("wer_errors", 0) for r in group)
                wr = sum(r.get("reference_words", 0) for r in group)
                times = [r["end_to_end_ms"] for r in group if "end_to_end_ms" in r]
                scores = [r["voice_quality_1_to_5"] for r in group if r.get("voice_quality_1_to_5") is not None]
                def flag(name: str) -> str:
                    vals = [r[name] for r in group if r.get(name) is not None]
                    return "—" if not vals else f"{sum(vals)}/{len(vals)}"
                def fmt(value: float) -> str:
                    return f"{value:.0f}"
                lat = "—" if not times else f"{fmt(sorted(times)[len(times)//2])}/{fmt(p95(times))} ms"
                quality = "—" if not scores else f"{sum(scores)/len(scores):.1f}"
                wer = "—" if not wr else f"{100*wc/wr:.1f}%"
                pass_rate = "nieprzetestowane" if not count else f"{passed}/{count}"
                lines.append(
                    f"| {inp} | {model} | {engine} | {mode} | {pass_rate} | {wer} | "
                    f"{lat} | {quality} | {flag('stop_ok')} / {flag('barge_in_ok')} / {flag('end_call_ok')} |"
                )
    # Imported models remain separate from built-in Gemma, never silently pooled.
    imported = sorted(k for k in groups if k[1] == "USER_LITERT")
    for key in imported:
        group = groups[key]
        lines.append(f"| {key[0]} | USER_LITERT | {key[2]} | {key[3]} | "
                     f"{sum(r['passed'] for r in group)}/{sum(r['cases'] for r in group)} | "
                     "osobny model | osobny model | — | — |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("record", "report"))
    parser.add_argument("--ledger", type=Path, default=DEFAULT_LEDGER)
    parser.add_argument("--input", type=Path, help="JSON with aggregate numeric test measurements")
    args = parser.parse_args()
    try:
        if args.action == "record":
            if args.input is None:
                parser.error("record requires --input")
            add(args.ledger, json.loads(args.input.read_text(encoding="utf-8")))
            print("AUDIO_ROUTE_QUALITY_SAVED")
        else:
            print(summary(read_ledger(args.ledger)), end="")
    except (OSError, ValueError, TypeError, json.JSONDecodeError) as error:
        print(f"AUDIO_ROUTE_QUALITY_ERROR: {error}", file=__import__("sys").stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
