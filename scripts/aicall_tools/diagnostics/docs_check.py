#!/usr/bin/env python3
"""Fast local Markdown contract: one current status, existing relative links, no new archives."""
from __future__ import annotations

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
# Simple Markdown links; anchors and external URLs are intentionally not fetched.
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")


def check(root: Path) -> list[str]:
    errors: list[str] = []
    for name in REQUIRED:
        if not (root / name).is_file():
            errors.append(name + ": required current documentation missing")
    for file in (root / "docs").rglob("*.md"):
        if file.relative_to(root).as_posix() not in DOCS:
            errors.append(file.relative_to(root).as_posix() + ": unregistered documentation; update the single index first")
    for file in [root / "README.md", root / "AGENTS.md", *(root / "docs").rglob("*.md")]:
        if not file.is_file():
            continue
        rel = file.relative_to(root).as_posix()
        for url in LINK.findall(file.read_text(encoding="utf-8")):
            if url.startswith(("http:", "https:", "mailto:", "#")):
                continue
            target = url.split("#", 1)[0]
            if not target:
                continue
            resolved = (file.parent / target).resolve()
            if not resolved.is_relative_to(root.resolve()) or not resolved.is_file():
                errors.append(rel + ": broken relative link: " + url)
    if (root / "BLOCKERS.md").exists():
        errors.append("BLOCKERS.md: legacy ledger must not be reintroduced")
    if list((root / "docs").glob("checkpoints/*.md")):
        errors.append("docs/checkpoints: historical Markdown must not be reintroduced")
    return errors


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[3]
    issues = check(root)
    for item in issues:
        print("DOC_CONTRACT_ERROR: " + item)
    if issues:
        raise SystemExit(1)
    print("DOC_CONTRACT_PASS=true")
