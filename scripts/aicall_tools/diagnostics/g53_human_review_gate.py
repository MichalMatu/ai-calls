#!/usr/bin/env python3
"""Local-only G5.3 review intake. Never infers a human verdict or outputs transcript text.

The human reviewer explicitly attests that they listened to a complete
synthetic dialogue. This verifies data integrity, not the reviewer's identity.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import uuid

from scripts.aicall_tools.diagnostics.g53_private_audio_review import DIMENSIONS, load

REVIEW_SCHEMA = "g53-human-review-v1"
PRIVATE_REVIEW_NAME = "human-review.json"


def validate_review(corpus: Path, content: bytes) -> dict[str, str | int]:
    # Load the complete session first: manifest and all WAV must be valid.
    _, turns = load(corpus)
    if not isinstance(content, bytes):
        raise ValueError("review_not_bytes")
    try:
        review = json.loads(content.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("invalid_review_json") from exc
    if not isinstance(review, dict):
        raise ValueError("invalid_review_object")
    if (review.get("schema") != REVIEW_SCHEMA
            or review.get("source") != "human"
            or review.get("reviewer_confirmed_listened") is not True):
        raise ValueError("missing_review_attestation")
    fingerprint = hashlib.sha256((corpus / "manifest.json").read_bytes()).hexdigest()
    if review.get("manifest_sha256") != fingerprint:
        raise ValueError("review_corpus_fingerprint_mismatch")
    records = review.get("reviews")
    if not isinstance(records, list) or len(records) != len(DIMENSIONS):
        raise ValueError("review_dimension_count")
    seen = set()
    failures = 0
    valid_turns = {turn["turn"] for turn in turns}
    for item in records:
        if not isinstance(item, dict):
            raise ValueError("invalid_review_record")
        dimension = item.get("dimension")
        if dimension not in DIMENSIONS or dimension in seen:
            raise ValueError("invalid_or_duplicate_dimension")
        seen.add(dimension)
        verdict = item.get("verdict")
        if verdict not in ("PASS", "FAIL"):
            raise ValueError("unassessed_or_invalid_verdict")
        turn = item.get("turn")
        if type(turn) is not int or turn not in valid_turns:
            raise ValueError("unknown_review_evidence_turn")
        note = item.get("note")
        if not isinstance(note, str) or not note.strip():
            raise ValueError("missing_review_evidence_note")
        if verdict == "FAIL":
            failures += 1
    if seen != set(DIMENSIONS):
        raise ValueError("missing_review_dimensions")
    # Review is self-attested, not independently authenticated.
    status = "RECORDED_FAIL" if failures else "RECORDED_PASS_CANDIDATE"
    return {"status": status, "dimensions": len(records), "failures": failures,
            "turns": len(turns)}


def import_review(corpus: Path, review_input: Path) -> dict[str, str | int]:
    if review_input.is_symlink() or not review_input.is_file():
        raise ValueError("invalid_review_input_file")
    content = review_input.read_bytes()
    result = validate_review(corpus, content)
    destination = corpus / PRIVATE_REVIEW_NAME
    if destination.exists() or destination.is_symlink():
        raise FileExistsError("private_review_already_exists")
    if stat.S_IMODE(corpus.stat().st_mode) != 0o700:
        raise ValueError("private_review_directory_permissions")
    temp = corpus / (".human-review-" + uuid.uuid4().hex + ".tmp")
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, "wb") as output:
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        # Hard-link provides no-overwrite atomic publication even on retries.
        os.link(temp, destination, follow_symlinks=False)
    finally:
        temp.unlink(missing_ok=True)
    return result


def scan(root: Path) -> dict[str, int]:
    if root.is_symlink() or not root.is_dir():
        raise ValueError("invalid_private_review_root")
    sessions = 0
    accepted = 0
    failed_reviews = 0
    missing = 0
    invalid = 0
    for folder in root.iterdir():
        if folder.is_symlink() or not folder.is_dir() or not folder.name.startswith("session-"):
            continue
        sessions += 1
        path = folder / PRIVATE_REVIEW_NAME
        if not path.exists():
            missing += 1
            continue
        try:
            if path.is_symlink() or not path.is_file():
                raise ValueError("invalid_private_review")
            result = validate_review(folder, path.read_bytes())
            accepted += 1
            failed_reviews += int(result["failures"] > 0)
        except (ValueError, OSError, KeyError, TypeError, UnicodeError):
            invalid += 1
    return {"sessions": sessions, "recorded": accepted, "review_failures": failed_reviews,
            "unassessed": missing, "invalid": invalid}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path)
    parser.add_argument("--review-input", type=Path)
    parser.add_argument("--root", type=Path)
    args = parser.parse_args()
    if args.root is not None:
        if args.corpus is not None or args.review_input is not None:
            parser.error("--root cannot be combined with --corpus/--review-input")
        result = scan(args.root)
        for key, value in result.items():
            print("G53_REVIEW_" + key.upper() + "=" + str(value))
    elif args.corpus is not None and args.review_input is not None:
        result = import_review(args.corpus, args.review_input)
        for key, value in result.items():
            print("G53_REVIEW_" + key.upper() + "=" + str(value))
        print("G53_REVIEW_PRIVATE_LOCAL_ONLY=true")
    else:
        parser.error("specify --root or both --corpus and --review-input")


if __name__ == "__main__":
    main()
