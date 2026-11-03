"""Regression for the canonical host layout gate; no Gradle, device or network."""

from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
VERIFY_HOST = ROOT / "scripts" / "verify_host.sh"


def allowed_entries(section: str) -> set[str]:
    source = VERIFY_HOST.read_text(encoding="utf-8")
    match = re.search(rf"^{re.escape(section)}=\(\n(.*?)^\)", source, flags=re.M | re.S)
    if not match:
        raise AssertionError(f"missing verify_host.sh allowlist: {section}")
    return {line.strip() for line in match.group(1).splitlines() if line.strip()}


def tracked_paths() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    return [entry.decode("utf-8") for entry in result.stdout.split(b"\0") if entry]


class VerifyHostLayoutTest(unittest.TestCase):
    def test_required_canonical_policy_files_are_accepted(self):
        allowed = allowed_entries("allowed_root_entries")
        self.assertTrue({"AGENTS.md", "BLOCKERS.md", "README.md"}.issubset(allowed))

    def test_secret_scan_distinguishes_key_prefix_from_word_suffix(self):
        source = VERIFY_HOST.read_text(encoding="utf-8")
        match = re.search(r"^production_secret_pattern='([^']+)'$", source, re.M)
        self.assertIsNotNone(match)
        pattern = re.compile(match.group(1))
        self.assertIsNone(pattern.search("task-approved speech can exceed 600 chars"))
        self.assertIsNone(pattern.search("risk-approved is not an access key"))
        self.assertIsNotNone(pattern.search("secret: " + "sk-" + "a" * 18))
        self.assertIsNotNone(pattern.search("OPENAI_API_KEY"))

    def test_tracked_root_and_scripts_entries_are_accepted(self):
        paths = tracked_paths()
        roots = {path.split("/", 1)[0] for path in paths}
        scripts = {
            path.split("/", 2)[1]
            for path in paths
            if path.startswith("scripts/") and "/" in path
        }
        self.assertEqual(sorted(roots - allowed_entries("allowed_root_entries")), [])
        self.assertEqual(sorted(scripts - allowed_entries("allowed_scripts_entries")), [])


if __name__ == "__main__":
    unittest.main()
