"""Independent, private inventory/archiver for Android visual-audit JPEGs.

Use an isolated AndroidTest run:
  adb shell am instrument -w -e uiAuditRunId ux12-20261108-a ...AndroidJUnitRunner
  adb pull /sdcard/Android/data/pl.michalmatu.aicallbridge/files/ui-audit-ux12-20261108-a PRIVATE_FOLDER/
  python3 scripts/aicall_tools/diagnostics/ui_screenshot_inventory.py \
      PRIVATE_FOLDER/ui-audit-ux12-20261108-a --run-id ux12-20261108-a --archive PRIVATE_FOLDER/audit.zip

Never parse 'UI_AUDIT=' from instrumentation stdout: it is diagnostic only.
Do not upload private images or screenshot text to Git.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
from zipfile import ZIP_DEFLATED, ZipFile

SCREENS = (
    "home", "calls", "lab", "settings", "agent-ai", "general-settings",
    "engines", "models", "incoming", "preparation", "skills", "tools",
    "developer", "usb-diagnostics", "phone-enrollment", "vault", "campaigns",
)
_CAPTURE = re.compile(r"^([a-z][a-z0-9-]*)-([0-2])\.jpg$")


def inventory(directory: Path, expected: tuple[str, ...] = SCREENS) -> dict:
    """Validate 1–3 contiguous screenshot pages per named screen.

    The receipt contains only screen labels, counts and error categories.
    Neither pixel data, unrecognized filenames nor directory names are logged.
    """
    pages: dict[str, dict[int, Path]] = {name: {} for name in expected}
    errors: list[str] = []
    if not directory.is_dir():
        return {"ok": False, "screens": {}, "total": 0, "errors": ["folder_missing"]}
    for file in directory.iterdir():
        if not file.is_file() or file.suffix.lower() != ".jpg":
            continue
        match = _CAPTURE.fullmatch(file.name)
        if match is None or match.group(1) not in pages:
            errors.append("unexpected_jpeg")
            continue
        label, index = match.group(1), int(match.group(2))
        data = file.read_bytes()
        if len(data) <= 256 or not data.startswith(b"\xff\xd8") or not data.endswith(b"\xff\xd9"):
            errors.append("invalid_jpeg:" + label)
            continue
        pages[label][index] = file
    for name, indexed in pages.items():
        if not indexed:
            errors.append("missing_screen:" + name)
        elif sorted(indexed) != list(range(len(indexed))):
            errors.append("noncontiguous_pages:" + name)
    return {
        "ok": not errors,
        "screens": {label: len(indexed) for label, indexed in pages.items()},
        "total": sum(map(len, pages.values())),
        "errors": errors,
    }


def make_archive(directory: Path, destination: Path, receipt: dict) -> None:
    if not receipt["ok"]:
        raise ValueError("Cannot archive an invalid visual audit")
    if destination.exists():
        raise FileExistsError("Refusing to overwrite an existing private audit archive")
    # Do not write partial output when the destination cannot be created.
    with ZipFile(destination, "x", compression=ZIP_DEFLATED) as archive:
        for name in SCREENS:
            for index in range(receipt["screens"][name]):
                file = directory / f"{name}-{index}.jpg"
                archive.write(file, arcname=file.name)
        archive.writestr("manifest.json", json.dumps(receipt, sort_keys=True))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate/collect private Android visual-audit screenshots")
    parser.add_argument("directory", type=Path, help="Private folder pulled from Android app storage")
    parser.add_argument("--run-id", help="Unique AndroidTest -e uiAuditRunId; also verifies folder name")
    parser.add_argument("--archive", type=Path, help="Optional private ZIP path; never overwrites")
    args = parser.parse_args(argv)
    if args.run_id is not None:
        if not re.fullmatch(r"[A-Za-z0-9_-]{6,48}", args.run_id) or (
            args.directory.name != "ui-audit-" + args.run_id
        ):
            print("UI_CAPTURE_RECEIPT=FAIL; reason=run_id_mismatch")
            return 2
    receipt = inventory(args.directory)
    if not receipt["ok"]:
        print("UI_CAPTURE_RECEIPT=FAIL; errors=" + ",".join(receipt["errors"]))
        return 2
    if args.archive is not None:
        try:
            make_archive(args.directory, args.archive, receipt)
        except (OSError, ValueError) as exc:
            print("UI_CAPTURE_RECEIPT=FAIL; reason=archive_error:" + type(exc).__name__)
            return 2
    print("UI_CAPTURE_RECEIPT=PASS; screens=" + str(len(SCREENS)) +
          "; pages=" + str(receipt["total"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
