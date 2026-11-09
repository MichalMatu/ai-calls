#!/usr/bin/env python3
"""Offline documentation quality gate: single current state, small and modular docs."""
from __future__ import annotations

from difflib import SequenceMatcher
from pathlib import Path
import re

DOCS = (
    "docs/README.md", "docs/PROJECT.md",
    "docs/architecture/SYSTEM.md", "docs/architecture/SECURITY.md",
    "docs/modules/CONVERSATION.md", "docs/modules/INCOMING_OUTGOING.md",
    "docs/modules/AUDIO_HISTORY.md", "docs/modules/SKILLS_LAB.md",
    "docs/modules/BOOKING_EFFECTS.md", "docs/modules/UI.md",
    "docs/operations/DEVELOPMENT.md", "docs/operations/LOCAL_AGENT.md",
    "docs/operations/DEVICE.md", "docs/operations/EXTERNAL_REUSE.md",
)
REQUIRED = ("README.md", "AGENTS.md", *DOCS)

TOTAL_WARN_BYTES = 42 * 1024
TOTAL_MAX_BYTES = 50 * 1024
FILE_WARN_BYTES = 6 * 1024
FILE_MAX_BYTES = 8 * 1024
AGENTS_MAX_BYTES = 6 * 1024

LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
HEADING = re.compile(r"^(#{1,3})[ \t]+(.+?)[ \t]*$")
ARCHIVE_HEADING = re.compile(
    r"^#{1,3}\s+(?:20\d{2}-\d{2}-\d{2}(?:\s|$)|"
    r"(?:historia|archiwum|kronika|dziennik|checkpoint|handoff)"
    r"(?:\s|:|—|$))", re.I,
)
STATUS_HEADING = re.compile(
    r"^#{1,3}\s+(?:otwarte|roadmapa|priorytety|następny sprint|"
    r"stan projektu|status projektu)(?:\s|:|—|$)", re.I,
)
BULLET = re.compile(r"^(?:[-*]|\d+[.)])\s+")
MIN_DUPLICATE_CHARS = 240
NEAR_DUPLICATE_RATIO = 0.97


def _prose_blocks(source: str) -> list[tuple[int, str]]:
    """Ignore short boilerplate, headings, tables and fenced code."""
    blocks: list[tuple[int, str]] = []
    words: list[str] = []
    start = 0
    fence: str | None = None
    fence_marker = chr(96) * 3

    def flush() -> None:
        nonlocal words
        if words:
            plain = " ".join(words).casefold().replace("**", "").replace(chr(96), "")
            plain = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", plain)
            plain = re.sub(r"\s+", " ", plain).strip()
            if len(plain) >= MIN_DUPLICATE_CHARS:
                blocks.append((start, plain))
            words = []

    for number, line in enumerate(source.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith((fence_marker, "~~~")):
            flush()
            marker = stripped[:3]
            if fence is None:
                fence = marker
            elif fence == marker:
                fence = None
            continue
        if fence is not None:
            continue
        if not stripped or stripped.startswith(("#", "|")):
            flush()
            continue
        if BULLET.match(stripped):
            flush()
        if not words:
            start = number
        words.append(stripped)
    flush()
    return blocks


def audit(root: Path) -> tuple[list[str], list[str], int]:
    """Return (fatal violations, soft warnings, total UTF-8 bytes)."""
    errors: list[str] = []
    warnings: list[str] = []
    root = root.resolve()
    docs_root = root / "docs"
    files = [root / p for p in REQUIRED if (root / p).is_file()]
    known = set(REQUIRED)

    for name in REQUIRED:
        if not (root / name).is_file():
            errors.append(f"{name}: required current documentation missing")
    for file in docs_root.rglob("*.md"):
        rel = file.relative_to(root).as_posix()
        if rel not in DOCS:
            errors.append(f"{rel}: unregistered documentation; update the single index first")
    for file in root.glob("*.md"):
        if file.name not in known:
            errors.append(f"{file.name}: unexpected root Markdown")
    for module in ("app", "conversation-core", "conversation-tools",
                   "audio-bridge", "privileged-helper", "realtime-client",
                   "scripts", "service-packs"):
        for file in (root / module).glob("*.md"):
            errors.append(f"{file.relative_to(root)}: module documentation belongs in docs/")
    if (root / "scripts/local_agent/README.md").exists():
        errors.append("scripts/local_agent/README.md: duplicate documentation location")

    total = sum(f.stat().st_size for f in files)
    if total > TOTAL_MAX_BYTES:
        errors.append(f"documentation budget exceeded: {total} > {TOTAL_MAX_BYTES} bytes")
    elif total > TOTAL_WARN_BYTES:
        warnings.append(f"documentation budget nearing limit: {total}/{TOTAL_MAX_BYTES} bytes")
    paragraphs: list[tuple[str, int, str]] = []
    for file in files:
        rel = file.relative_to(root).as_posix()
        size = file.stat().st_size
        cap = AGENTS_MAX_BYTES if rel == "AGENTS.md" else FILE_MAX_BYTES
        if size > cap:
            errors.append(f"{rel}: file budget exceeded: {size} > {cap} bytes")
        elif size > FILE_WARN_BYTES:
            warnings.append(f"{rel}: file nearing size limit: {size}/{cap} bytes")
        source = file.read_text(encoding="utf-8")
        h1_count = 0
        headings: set[tuple[int, str]] = set()
        fence: str | None = None
        for line_no, line in enumerate(source.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith((chr(96) * 3, "~~~")):
                marker = stripped[:3]
                if fence is None:
                    fence = marker
                elif fence == marker:
                    fence = None
                continue
            if fence is not None:
                continue
            match = HEADING.match(line)
            if not match:
                continue
            depth = len(match.group(1))
            key = (depth, match.group(2).casefold().strip())
            if depth == 1:
                h1_count += 1
            elif key in headings:
                errors.append(f"{rel}:{line_no}: duplicate section heading: {match.group(2)}")
            headings.add(key)
            if ARCHIVE_HEADING.match(line):
                errors.append(f"{rel}:{line_no}: chronological/archive heading in active docs")
            if rel != "docs/PROJECT.md" and STATUS_HEADING.match(line):
                errors.append(f"{rel}:{line_no}: project status belongs only in docs/PROJECT.md")
        if h1_count != 1:
            errors.append(f"{rel}: expected exactly one top-level heading (found {h1_count})")
        if fence is not None:
            errors.append(f"{rel}: unclosed Markdown code fence")
        for link in LINK.findall(source):
            if link.startswith(("http:", "https:", "mailto:", "tel:", "#")):
                continue
            path = link.split("#", 1)[0]
            if not path:
                continue
            resolved = (file.parent / path).resolve()
            if not resolved.is_relative_to(root) or not resolved.is_file():
                errors.append(f"{rel}: broken relative link: {link}")
        paragraphs.extend((rel, line_no, text) for line_no, text in _prose_blocks(source))

    index = root / "docs/README.md"
    if index.is_file():
        linked: set[str] = set()
        for href in LINK.findall(index.read_text(encoding="utf-8")):
            if href.startswith(("http:", "https:", "#")):
                continue
            target = (index.parent / href.split("#", 1)[0]).resolve()
            if target.is_relative_to(root):
                linked.add(target.relative_to(root).as_posix())
        for name in DOCS:
            if name != "docs/README.md" and name not in linked:
                errors.append(f"docs/README.md: missing index entry for {name}")
    project = root / "docs/PROJECT.md"
    if project.is_file():
        source = project.read_text(encoding="utf-8")
        for heading in ("## Otwarte", "## Następny sprint"):
            if heading not in source:
                errors.append(f"docs/PROJECT.md: missing current state section: {heading}")

    # Compare substantive paragraphs only. Repeated terminology is not duplication.
    for i, (left_path, left_line, left) in enumerate(paragraphs):
        for right_path, right_line, right in paragraphs[i + 1:]:
            if abs(len(left) - len(right)) > max(len(left), len(right)) * 0.10:
                continue
            if left == right or SequenceMatcher(None, left, right, autojunk=False).ratio() >= NEAR_DUPLICATE_RATIO:
                errors.append(
                    f"{right_path}:{right_line}: duplicate prose paragraph from "
                    f"{left_path}:{left_line}; keep one canonical explanation"
                )
    if (root / "BLOCKERS.md").exists():
        errors.append("BLOCKERS.md: legacy ledger must not be reintroduced")
    if list(docs_root.glob("checkpoints/*.md")):
        errors.append("docs/checkpoints: historical Markdown must not be reintroduced")
    return errors, warnings, total


def check(root: Path) -> list[str]:
    """Compatibility for existing fatal-only regression tests."""
    return audit(root)[0]


if __name__ == "__main__":
    issues, notices, total = audit(Path(__file__).resolve().parents[3])
    print(f"DOC_BUDGET_BYTES={total}/{TOTAL_MAX_BYTES}")
    for warning in notices:
        print("DOC_CONTRACT_WARN: " + warning)
    for issue in issues:
        print("DOC_CONTRACT_ERROR: " + issue)
    if issues:
        raise SystemExit(1)
    print("DOC_CONTRACT_PASS=true")
