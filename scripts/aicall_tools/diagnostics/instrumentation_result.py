"""Conservative AndroidJUnitRunner result gate for Android and Samsung stream formats."""
from __future__ import annotations

import re
import sys

FAILURE_MARKERS=("FAILURES!!!","INSTRUMENTATION_FAILED","INSTRUMENTATION_ABORTED")

def passed_test_count(output: str) -> int | None:
    text=output.replace("\r","")
    if any(marker in text for marker in FAILURE_MARKERS):
        return None
    counts=re.findall(r"(?m)^OK \(([1-9][0-9]*) tests?\)[ \t]*$", text)
    if not counts:
        return None
    codes=re.findall(r"(?m)^INSTRUMENTATION_CODE:[ \t]*(-?[0-9]+)[ \t]*$",text)
    if codes and any(code!="-1" for code in codes):
        return None
    return int(counts[-1])

def main() -> int:
    count=passed_test_count(sys.stdin.read())
    if count is None:
        print("UI_LAB_INSTRUMENTATION_FAIL=true",file=sys.stderr)
        return 1
    print("UI_LAB_PASS_TEST_COUNT="+str(count))
    return 0

if __name__=="__main__":
    raise SystemExit(main())
