package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class ConversationReplyProtocolTest {
    @Test fun incomingExplainGoalSkillDoesNotReintroduceMaleRoleOrSecondGreeting() {
        val preparation = ConversationPreparation.incoming().withSettings("", setOf(ConversationSkill.EXPLAIN_GOAL), emptySet())
        val prompt = ConversationInstructionBuilder.build(preparation, "Przyjmij wiadomość", ConversationResponseFormat.TOOL_REPLY)
        assertFalse(prompt.contains("Przedstaw rolę asystenta odbierającego"))
        assertTrue(prompt.contains("Powitanie aplikacji przedstawiło już rolę asystentki"))
        assertFalse(prompt.contains("Przy pierwszej odpowiedzi jasno przedstaw konkretny cel dzwoniącego"))
    }

    @Test fun decodesSpeechSeparatelyFromTypedToolProposal() {
        val reply = ConversationReplyProtocol.decode("""{"reply":"Dziękuję, do widzenia.","tool":"end_call"}""")
        assertEquals("Dziękuję, do widzenia.", reply.text)
        assertEquals(ConversationToolId.END_CALL, reply.tool)
        assertFalse(reply.toString().contains("Dziękuję"))
        assertNull(ConversationReplyProtocol.decode("""{"reply":"Czy coś jeszcze?","tool":"none"}""").tool)
    }

    @Test fun invalidOrUnimplementedRequestsCannotBecomeSpeechOrAnEndAction() {
        for (raw in listOf(
            "Do widzenia", """{"reply":"Do widzenia"}""",
            """{"reply":"","tool":"end_call"}""",
            """{"reply":"Do widzenia","tool":"dial"}""",
            """{"reply":"Do widzenia","tool":"end_call","number":"synthetic"}""",
            """{"reply":true,"tool":"end_call"}""",
        )) {
            assertTrue(runCatching { ConversationReplyProtocol.decode(raw) }.isFailure)
        }
    }

    @Test fun textOfFarewellDoesNotTriggerAToolWithoutTypedProposal() {
        val reply = ConversationReplyProtocol.decode("""{"reply":"Czy słowa do widzenia są częścią wiadomości?","tool":"none"}""")
        assertNull(reply.tool)
    }

    @Test fun cancellationDropsLateToolAndSpeechAndNextTurnDoesNotReuseIt() {
        val model = FakeModel()
        val backend = ConversationReplyBackend(model) { _, _, _ -> }
        val spoken = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val listener = object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { spoken += text }
            override fun onError(reason: String) { errors += reason }
        }
        backend.generate("synthetic", listener)
        val old = checkNotNull(model.listener)
        backend.cancel()
        old.onComplete("""{"reply":"Do widzenia","tool":"end_call"}""")
        assertTrue(spoken.isEmpty()); assertNull(backend.takeTool())
        backend.generate("synthetic correction", listener)
        model.listener!!.onComplete("""{"reply":"Uwzględniłam poprawkę.","tool":"none"}""")
        assertEquals(listOf("Uwzględniłam poprawkę."), spoken)
        assertTrue(errors.isEmpty()); assertNull(backend.takeTool())
    }

    @Test fun closureProposalIsConsumedOnceAndMalformedReplyReleasesNothing() {
        val model = FakeModel(); val backend = ConversationReplyBackend(model) { _, _, _ -> }
        var speech = 0; var errors = 0
        val listener = object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { speech++ }
            override fun onError(reason: String) { errors++ }
        }
        backend.generate("synthetic closing", listener)
        model.listener!!.onComplete("""{"reply":"Do widzenia.","tool":"end_call"}""")
        assertEquals(ConversationToolId.END_CALL, backend.takeTool()); assertNull(backend.takeTool())
        backend.generate("synthetic next", listener)
        model.listener!!.onComplete("not structured")
        assertEquals(1, speech); assertEquals(1, errors); assertNull(backend.takeTool())
    }

    @Test fun incomingInstructionsHaveOneVoiceAndExplicitOutputContract() {
        val prompt = ConversationInstructionBuilder.build(ConversationPreparation.incoming(), "Przyjmij wiadomość", ConversationResponseFormat.TOOL_REPLY)
        assertTrue(prompt.contains("asystentką"))
        assertTrue(prompt.contains("Powitanie zostało już"))
        assertFalse(prompt.contains("Pan/Pani"))
        assertTrue(prompt.contains("Nie witaj się ponownie"))
        assertTrue(prompt.contains("żeńskiej formie"))
        assertTrue(prompt.contains("end_call"))
        assertFalse(prompt.contains("Bez JSON"))
        val raw = ConversationInstructionBuilder.build(ConversationPreparation.outgoing(), "Cel", ConversationResponseFormat.SPOKEN_TEXT)
        assertTrue(raw.contains("Bez JSON"))
    }

    private class FakeModel : TextCallAgentBackend {
        var listener: TextCallAgentBackend.Listener? = null
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) { this.listener = listener }
        override fun cancel() = Unit
    }
}
