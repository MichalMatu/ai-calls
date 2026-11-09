"""Fast gate selection and no-build regression; no Gradle/device needed."""
from pathlib import Path
import tempfile
import unittest

from scripts.aicall_tools.diagnostics.dev_change_gate import paired_python_test, select

ROOT = Path(__file__).resolve().parents[2]


class DeveloperFastGateTests(unittest.TestCase):
    def test_docs_only_does_not_compile(self):
        self.assertEqual(select(["docs/ROADMAP.md", "BLOCKERS.md"]), ([], [], [], []))

    def test_changed_only_modules(self):
        tasks, py, shell, escalation = select(["app/src/main/X.kt", "conversation-core/src/main/Y.kt"])
        self.assertEqual(tasks, [":app:testDebugUnitTest", ":conversation-core:test"])
        self.assertFalse(py or shell or escalation)

    def test_single_script_runs_only_matching_test(self):
        tasks, py, shell, escalation = select(["scripts/aicall_tools/diagnostics/g53_human_review_gate.py"], ROOT)
        self.assertEqual(py, ["scripts.tests.test_g53_human_review_gate"])
        self.assertFalse(tasks or shell or escalation)

    def test_unmapped_script_fails_safe(self):
        _, _, _, problems = select(["scripts/aicall_tools/diagnostics/uncovered_new_module.py"], ROOT)
        self.assertEqual(problems, ["UNMAPPED_PYTHON:scripts/aicall_tools/diagnostics/uncovered_new_module.py"])

    def test_test_file_runs_just_test_module(self):
        _, py, _, issues = select(["scripts/tests/test_g53_private_audio_review.py"], ROOT)
        self.assertEqual(py, ["scripts.tests.test_g53_private_audio_review"])
        self.assertFalse(issues)

    def test_new_dev_gate_has_explicit_self_test(self):
        self.assertEqual(paired_python_test("scripts/aicall_tools/diagnostics/dev_change_gate.py", ROOT),
                         "scripts.tests.test_development_fast_gate")

    def test_instrumented_tests_deferred(self):
        tasks, py, shell, issues = select(["app/src/androidTest/Trial.kt"])
        self.assertFalse(tasks or py or shell or issues)

    def test_build_configuration_requires_explicit_selection(self):
        _, _, _, issues = select(["gradle.properties", "app/build.gradle.kts"])
        self.assertEqual(len(issues), 2)

    def test_shell_hook_and_test_script_have_small_gate(self):
        _, py, shell, issues = select(["scripts/bin/test-profile.sh"], ROOT)
        self.assertEqual(shell, ["scripts/bin/test-profile.sh"])
        self.assertEqual(py, ["scripts.tests.test_verify_host_layout", "scripts.tests.test_development_fast_gate"])
        self.assertFalse(issues)

    def test_focus_and_auto_discover_same_host_sdk_as_full_gate(self):
        profile = (ROOT / "scripts/bin/test-profile.sh").read_text()
        full = (ROOT / "scripts/verify_host.sh").read_text()
        for expected in ("$HOME/Library/Android/sdk", "$HOME/Android/Sdk",
                         'export ANDROID_HOME=', 'export ANDROID_SDK_ROOT='):
            self.assertIn(expected, profile)
            self.assertIn(expected, full)
        self.assertLess(profile.index("export ANDROID_HOME="), profile.index('case "$mode" in'))

    def test_packaging_only_explicit_profile(self):
        profile = (ROOT / "scripts/bin/test-profile.sh").read_text()
        full = (ROOT / "scripts/verify_host.sh").read_text()
        hook = (ROOT / "scripts/bin/git-hooks/pre-commit").read_text()
        self.assertNotIn(":app:assembleDebug", full)
        self.assertNotIn("--no-daemon", profile + full)
        self.assertIn("device-build)", profile)
        self.assertIn(":app:assembleDebug :app:assembleDebugAndroidTest", profile)
        self.assertNotIn("gradlew", hook)
        self.assertNotIn("assemble", hook)
        self.assertIn("preflight", hook)


if __name__ == "__main__":
    unittest.main()
