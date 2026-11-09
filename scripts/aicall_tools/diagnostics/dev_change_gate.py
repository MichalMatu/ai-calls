"""Change-aware test runner. No APK packaging, Android device access or remote IO."""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys

UNIT_TASKS = {
    "app": ":app:testDebugUnitTest",
    "conversation-core": ":conversation-core:test",
    "conversation-tools": ":conversation-tools:test",
    "audio-bridge": ":audio-bridge:testDebugUnitTest",
    "privileged-helper": ":privileged-helper:testDebugUnitTest",
    "realtime-client": ":realtime-client:testDebugUnitTest",
}
BUILD_FILES = {"gradlew", "gradlew.bat", "gradle.properties", "build.gradle.kts", "settings.gradle.kts"}


def paired_python_test(path: str, root: Path = Path(".")) -> str | None:
    if path.startswith("scripts/tests/") and path.endswith(".py"):
        relative = path[len("scripts/"):]
        return relative[:-3].replace("/", ".") if Path(path).name.startswith("test_") else None
    if not path.startswith("scripts/aicall_tools/") or not path.endswith(".py"):
        return None
    relative = path[len("scripts/aicall_tools/"):]
    parts = relative.split("/")
    if parts[-1] == "__init__.py":
        return None
    parts[-1] = "test_" + parts[-1]
    candidate = root / "scripts" / "tests" / Path(*parts)
    if candidate.is_file():
        return "tests." + ".".join(parts)[:-3]
    if len(parts) > 1:
        fallback = root / "scripts" / "tests" / parts[-1]
        if fallback.is_file():
            return "tests." + parts[-1][:-3]
    return None


def select(paths: list[str], root: Path = Path(".")) -> tuple[list[str], list[str], list[str], list[str]]:
    """Unit Gradle tasks, targeted Python modules, shell syntax checks, unsafe changes."""
    gradle: list[str] = []
    python: list[str] = []
    shell: list[str] = []
    escalations: list[str] = []
    for path in sorted(set(paths)):
        top = path.split("/", 1)[0]
        if (path in BUILD_FILES or path.startswith(("gradle/", ".github/"))
                or path.endswith("/build.gradle.kts")):
            escalations.append(path)
        elif top in UNIT_TASKS:
            # Android instrumented tests package only on an authorized device-build.
            if "/src/androidTest/" in path:
                print("DEV_ANDROID_TEST_DEFERRED=" + path, flush=True)
                continue
            task = UNIT_TASKS[top]
            if task not in gradle:
                gradle.append(task)
        elif path.startswith("service-packs/"):
            for task in (UNIT_TASKS["conversation-tools"], UNIT_TASKS["app"]):
                if task not in gradle:
                    gradle.append(task)
        elif path.startswith("scripts/"):
            if path.endswith(".py"):
                matched = paired_python_test(path, root)
                if matched:
                    if matched not in python:
                        python.append(matched)
                else:
                    escalations.append("UNMAPPED_PYTHON:" + path)
            elif path.endswith(".sh") or path.startswith("scripts/bin/"):
                if path not in shell:
                    shell.append(path)
                # Tooling may affect shared execution; preserve focused contract.
                for module in ("tests.test_verify_host_layout", "tests.test_development_fast_gate"):
                    if module not in python:
                        python.append(module)
            elif path.endswith(".md"):
                pass
            else:
                escalations.append(path)
        elif path.startswith("benchmarks/"):
            # Versioned benchmark fixtures require explicit scenario selection.
            escalations.append(path)
        elif top in {"docs", "README.md", "AGENTS.md", "BLOCKERS.md", ".gitignore"}:
            pass
        else:
            escalations.append(path)
    return gradle, python, shell, escalations


def changes_since(base: str) -> list[str]:
    subprocess.run(["git", "rev-parse", "--verify", "-q", base + "^{commit}"],
                   check=True, stdout=subprocess.DEVNULL)
    files: set[str] = set()
    for args in (
        ("diff", "--name-only", "-z", base, "HEAD", "--"),
        ("diff", "--name-only", "-z", "--"),
        ("diff", "--cached", "--name-only", "-z", "--"),
        ("ls-files", "--others", "--exclude-standard", "-z"),
    ):
        result = subprocess.check_output(("git", *args))
        files.update(x.decode("utf-8", errors="surrogateescape")
                     for x in result.split(b"\0") if x)
    return sorted(files)


def main(base: str) -> int:
    try:
        paths = changes_since(base)
    except subprocess.CalledProcessError:
        print("DEV_AUTO_INVALID_BASE=" + base, file=sys.stderr)
        return 2
    subprocess.run(["bash", "scripts/bin/test-profile.sh", "preflight"], check=True)
    if not paths:
        print("DEV_AUTO_NO_CHANGES=true")
        return 0
    gradle, python, shell, escalation = select(paths)
    if escalation:
        print("DEV_AUTO_SCOPE_ESCALATION=" + ",".join(escalation), file=sys.stderr)
        print("Choose exact affected tests; no automatic build or full suite.", file=sys.stderr)
        return 3
    for script in shell:
        if Path(script).exists():
            subprocess.run(["bash", "-n", script], check=True)
    if python:
        print("DEV_AUTO_PYTHON_TESTS=" + ",".join(python), flush=True)
        subprocess.run([sys.executable, "-m", "unittest", *python], check=True)
    if gradle:
        print("DEV_AUTO_GRADLE_UNIT_TASKS=" + ",".join(gradle), flush=True)
        subprocess.run([os.environ.get("GRADLE_BIN", "./gradlew"), *gradle], check=True)
    print("DEV_AUTO_PASS=true")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "HEAD^"))
