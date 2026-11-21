"""UX-03 source architecture: synthetic Skill Lab owns no Telecom authority."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge/conversation"

class SkillLabRunnerSourceContract(unittest.TestCase):
    def test_activity_is_presentation_only(self):
        source = (ROOT / "ConversationSkillLabActivity.kt").read_text()
        for fragment in ("SkillLabRunner(", "runner.startManual(", "runner.stop()", "renderState("):
            self.assertIn(fragment, source)
        self.assertIn("runner.startAdaptive(", source)
        for forbidden in ("CountDownLatch", "Thread({", ".generate(", "TextCallAgentBackend"):
            self.assertNotIn(forbidden, source)

    def test_android_factory_retains_existing_backend_and_approval_boundary(self):
        source = (ROOT / "AndroidSkillLabSessionFactory.kt").read_text()
        for fragment in ("AudioEngineProfileStore", "loadSkillLibrary()",
                         "library.snapshot(presetId)", "ConversationInstructionBuilder.build(",
                         "ConversationResponseFormat.TOOL_REPLY", "setOf(ConversationToolId.END_CALL)"):
            self.assertIn(fragment, source)
        for forbidden in ("TelecomManager", "placeCall(", "endCall(", "executeTool("):
            self.assertNotIn(forbidden, source)

if __name__ == "__main__":
    unittest.main()
