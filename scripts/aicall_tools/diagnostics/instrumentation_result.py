"""Conservative AndroidJUnitRunner result gate for Android and Samsung stream formats."""
from __future__ import annotations

import argparse
import re
import sys

FAILURE_MARKERS = ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED")
SKIPPED_STATUS = re.compile(r"(?m)^INSTRUMENTATION_STATUS_CODE:[ \t]*-[34][ \t]*$")

def passed_test_count(output: str) -> int | None:
    text = output.replace("\r", "")
    if any(marker in text for marker in FAILURE_MARKERS) or SKIPPED_STATUS.search(text):
        return None
    counts = re.findall(r"(?m)^OK \(([1-9][0-9]*) tests?\)[ \t]*$", text)
    if len(counts) != 1:
        return None
    codes = re.findall(r"(?m)^INSTRUMENTATION_CODE:[ \t]*(.*?)[ \t]*$", text)
    if codes and codes != ["-1"]:
        return None
    return int(counts[0])

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-count", type=int)
    args = parser.parse_args()
    count = passed_test_count(sys.stdin.read())
    if count is None:
        print("UI_LAB_INSTRUMENTATION_FAIL=true",file=sys.stderr)
        return 1
    if args.expected_count is not None and count != args.expected_count:
        print(f"INCOMPLETE_UI_TEST_COUNT=expected_{args.expected_count}_got_{count}", file=sys.stderr)
        return 1
    print(f"UI_LAB_PASS_TEST_COUNT={count}")
    return 0

if __name__=="__main__":
    raise SystemExit(main())
