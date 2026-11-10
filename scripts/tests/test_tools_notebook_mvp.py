"""Static no-call security contracts for universal notebook tools."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"
class NotebookMvpContract(unittest.TestCase):
    def test_both_directions_save_with_their_own_call_authority(self):
        incoming=(ROOT/"incoming/AndroidIncomingAssistant.kt").read_text()
        outgoing=(ROOT/"tools/OutgoingConversationTools.kt").read_text()
        for code in (incoming,outgoing):
            self.assertIn("appendNote(",code)
            self.assertIn("requestEndCall(",code)
        for name in ("ScenarioOutgoingConversationScenario.kt","AppointmentMvpConversationScenario.kt"):
            self.assertIn("ToolCallingConversationSession(", (ROOT/"campaign"/name).read_text())
        self.assertIn("ConversationResponseFormat.TOOL_REPLY",
            (ROOT/"campaign/AppOwnedCampaignService.kt").read_text())
    def test_exact_call_and_local_encryption_gate(self):
        outbound=(ROOT/"campaign/AppOwnedClirCampaignExecutor.kt").read_text()
        self.assertIn("ownedCalls.isCurrent(ownedCall)",outbound)
        self.assertIn("authorizationStore.isAuthorizedInFlight(grant.grantId, request)",outbound)
        store=(ROOT/"conversation/AndroidConversationPreparationStore.kt").read_text()
        self.assertIn("writeRecord(notebookRecordId, json)",store)
        for forbidden in ("startActivity(", "sendTextMessage(", "insertCalendarEvent("):
            self.assertNotIn(forbidden,store)
    def test_owner_review_and_future_actions_are_inert(self):
        screen=(ROOT/"tools/ConversationToolsActivity.kt").read_text()
        self.assertIn('tag = "tools-notebook-list"',screen)
        self.assertIn("ConversationNoteKind.CALENDAR_PROPOSAL",screen)
        self.assertIn("ConversationNoteKind.MESSAGE_DRAFT",screen)
        self.assertIn("notebook_idempotency_conflict",
            (ROOT/"conversation/ConversationNotebook.kt").read_text())
if __name__=="__main__":unittest.main()
