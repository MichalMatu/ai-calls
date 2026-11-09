package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

class LabToolProposalPolicyTest {
    @Test fun noToolIsNotAnAction() {
        val reply = ConversationReplyProtocol.decode("""{"reply":"Kontynuujmy.","tool":"none"}""")
        val result = LabToolProposalPolicy.validate(reply, setOf(ConversationToolId.END_CALL))
        assertEquals(LabToolDisposition.NONE, result.disposition)
        assertNull(result.acceptedProposal)
        assertFalse(result.executed)
    }

    @Test fun endCallIsTypedProposalButNotAuthorizedOrExecutedInLab() {
        val reply = ConversationReplyProtocol.decode("""{"reply":"Do widzenia.","tool":"end_call"}""")
        val result = LabToolProposalPolicy.validate(reply, setOf(ConversationToolId.END_CALL))
        assertEquals(ConversationToolId.END_CALL, result.acceptedProposal)
        assertEquals(LabToolDisposition.PROPOSED_ONLY, result.disposition)
        assertFalse(result.executed)
    }

    @Test fun disabledToolNeverEntersAcceptedProposal() {
        val reply = ConversationReplyProtocol.decode("""{"reply":"Do widzenia.","tool":"end_call"}""")
        val result = LabToolProposalPolicy.validate(reply, emptySet())
        assertEquals(LabToolDisposition.REJECTED_UNAVAILABLE, result.disposition)
        assertNull(result.acceptedProposal)
        assertFalse(result.executed)
    }

    @Test fun unimplementedOrMalformedToolCannotReachPolicy() {
        assertTrue(runCatching { ConversationReplyProtocol.decode(
            """{"reply":"Gotowe.","tool":"dial"}""") }.isFailure)
        assertTrue(runCatching { ConversationReplyProtocol.decode(
            """{"reply":"Gotowe.","tool":"end_call","number":"123"}""") }.isFailure)
    }
}
