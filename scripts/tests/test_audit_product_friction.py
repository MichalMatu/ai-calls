"""Regression/mutation tests for the anti-friction tripwire."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase

from scripts.aicall_tools.diagnostics.audit_product_friction import audit


ROOT = Path(__file__).resolve().parents[2]
MAIN = "app/src/main/kotlin/pl/michalmatu/aicallbridge/"
REQUIRED = (
    "AGENTS.md", "docs/PROJECT.md", "docs/architecture/SECURITY.md",
    MAIN + "campaign/CampaignToolActivity.kt",
    MAIN + "campaign/CampaignAuthorizationGrant.kt",
    MAIN + "campaign/CampaignAuthorizationStore.kt",
    MAIN + "campaign/CampaignContinuationStore.kt",
    MAIN + "campaign/CampaignStartReconciliationPolicy.kt",
    MAIN + "campaign/AppOwnedCampaignService.kt",
    MAIN + "textagent/BackendOpenAiTextBackend.kt",
    "scripts/aicall_tools/text/openai_text_broker.py",
    MAIN + "campaign/AppOwnedClirCampaignExecutor.kt",
    MAIN + "developerrelay/CallSessionEvidenceRecorder.kt",
    MAIN + "developerrelay/SupervisedCallPilotActivity.kt",
    MAIN + "conversation/AndroidConversationRuntime.kt",
    "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/campaign/"
    "AndroidCampaignAuthorizationStoreContractTest.kt",
    "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/campaign/CampaignContinuationStoreContractTest.kt",
    "app/src/test/kotlin/pl/michalmatu/aicallbridge/developerrelay/CallDiagnosticEvidenceTest.kt",
)


class AntiFrictionGateTest(TestCase):
    def test_current_sources_are_free_of_known_regressions(self):
        self.assertEqual([], audit(ROOT))

    def test_mutation_of_per_call_confirmation_is_caught(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            for name in REQUIRED:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text((ROOT / name).read_text(encoding="utf-8"), encoding="utf-8")
            ui = root / (MAIN + "campaign/CampaignToolActivity.kt")
            ui.write_text(ui.read_text(encoding="utf-8") + "\n// armNextCall\n", encoding="utf-8")
            errors = audit(root)
            self.assertTrue(any("armNextCall" in error for error in errors), errors)

    def test_missing_current_gate_is_caught_without_historical_ledger(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            for name in REQUIRED:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text((ROOT / name).read_text(encoding="utf-8"), encoding="utf-8")
            current = root / "docs/PROJECT.md"
            current.write_text(current.read_text(encoding="utf-8").replace("## Otwarte", "## Legacy"))
            errors = audit(root)
            self.assertTrue(any("docs/PROJECT.md" in error and "## Otwarte" in error
                                for error in errors), errors)
