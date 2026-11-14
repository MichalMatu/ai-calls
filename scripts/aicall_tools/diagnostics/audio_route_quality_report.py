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
from statistics import median
from collections import defaultdict
from pathlib import Path

INPUTS = ("ANDROID_STT", "PARAKEET_PL", "GEMMA_AUDIO_DIRECT")
MODELS = ("GEMMA_4_E2B", "USER_LITERT")
ENGINES = ("ANDROID_TTS", "PIPER")
MODES = ("synthetic_offcall", "real_call")
OPTIONAL = {
    "voice_id", "wer_errors", "reference_words", "asr_ms", "model_ms", "tts_ms",
    "end_to_end_ms", "voice_quality_1_to_5", "stop_ok", "barge_in_ok",
    "end_call_ok", "evidence_id", "model_identity", "tts_package",
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
    if record["model"] == "USER_LITERT" and not re.fullmatch(
        r"user-[0-9a-f]{64}", record.get("model_identity", "")
    ):
        raise ValueError("imported_model_identity_required")
    if record["model"] != "USER_LITERT" and "model_identity" in record:
        raise ValueError("unexpected_model_identity")
    if "tts_package" in record and not re.fullmatch(
        r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+", record["tts_package"]
    ):
        raise ValueError("invalid_tts_package")
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
                             or not re.fullmatch(r"[A-Za-z0-9._:#-]{1,160}", record[key])):
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
    # Never pool different APK revisions, TTS packages/voices, imported models or test modes.
    groups: dict[tuple[str, ...], list[dict]] = defaultdict(list)
    for row in rows:
        key = (
            row["input"], row["model"], row.get("model_identity", ""),
            row["tts_engine"], row.get("tts_package", ""),
            row.get("voice_id", ""), row["source_sha"], row["mode"],
        )
        groups[key].append(row)
    lines = [
        "# AI Calls — porównanie jakości torów audio",
        "",
        "Wyniki tylko z pomiarów; brak danych ≠ błąd ani zaliczony test.",
        "",
        "| Wejście | Model | TTS / głos | Wersja | Tryb | Próby PASS | WER | E2E p50 / p95 | Głos 1–5 | STOP / przerwanie / koniec |",
        "|---|---|---|---|---|---:|---:|---:|---:|---|",
    ]

    def append_row(key: tuple[str, ...], group: list[dict]) -> None:
        inp, model, model_id, engine, package, voice, sha, mode = key
        model_label = model if not model_id else model + ":" + model_id[:13]
        tts_label = engine + (":" + package if package else "") + ("/" + voice if voice else "")
        count = sum(r["cases"] for r in group)
        passed = sum(r["passed"] for r in group)
        wc = sum(r.get("wer_errors", 0) for r in group)
        wr = sum(r.get("reference_words", 0) for r in group)
        times = [r["end_to_end_ms"] for r in group if "end_to_end_ms" in r]
        scores = [r["voice_quality_1_to_5"] for r in group if "voice_quality_1_to_5" in r]

        def flag(name: str) -> str:
            vals = [r[name] for r in group if r.get(name) is not None]
            return "—" if not vals else f"{sum(vals)}/{len(vals)}"

        lat = "—" if not times else f"{median(times):.0f} / {p95(times):.0f} ms"
        quality = "—" if not scores else f"{sum(scores)/len(scores):.1f}"
        wer = "—" if not wr else f"{100 * wc / wr:.1f}%"
        passed_text = "nieprzetestowane" if not count else f"{passed}/{count}"
        lines.append(
            f"| {inp} | {model_label} | {tts_label} | {sha[:8] or '—'} | "
            f"{mode} | {passed_text} | {wer} | {lat} | {quality} | "
            f"{flag('stop_ok')} / {flag('barge_in_ok')} / {flag('end_call_ok')} |"
        )

    for inp in INPUTS:
        for engine in ENGINES:
            for mode in MODES:
                matching = sorted(k for k in groups if k[0] == inp and k[1] == "GEMMA_4_E2B"
                                  and k[3] == engine and k[7] == mode)
                if not matching:
                    append_row((inp, "GEMMA_4_E2B", "", engine, "", "", "", mode), [])
                else:
                    for key in matching:
                        append_row(key, groups[key])
    for key in sorted(k for k in groups if k[1] == "USER_LITERT"):
        append_row(key, groups[key])
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
