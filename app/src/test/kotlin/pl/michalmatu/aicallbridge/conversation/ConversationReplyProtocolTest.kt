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

    @Test fun endCallReplyTruncatesAnyQuestionAfterGoodbye() {
        val examples = listOf(
            "Do widzenia. Dziękuję za wiadomość. Czy coś jeszcze?" to "Do widzenia.",
            "Dziękuję za wiadomość. Do widzenia. Czy mogę pomóc?" to "Dziękuję za wiadomość. Do widzenia.",
            "Dziękuję, do widzenia, czy są pytania?" to "Dziękuję, do widzenia.",
            "Czy jeszcze coś? Do widzenia." to "Dziękuję za rozmowę. Do widzenia.",
            "Czy mogę jeszcze pomóc?" to "Dziękuję za rozmowę. Do widzenia.",
            "Do widzenia!" to "Do widzenia!",
        )
        for ((given, expected) in examples) {
            val raw = com.google.gson.JsonObject().apply {
                addProperty("reply", given)
                addProperty("tool", "end_call")
                addProperty("note", "")
                addProperty("kind", "NOTE")
            }.toString()
            val decoded = ConversationReplyProtocol.decode(raw)
            assertEquals(expected, decoded.text)
            assertEquals(ConversationToolId.END_CALL, decoded.tool)
        }
    }

    @Test fun quotedGoodbyeWithoutEndCallRemainsUntouched() {
        val given = "W wiadomości są słowa do widzenia. Czy mam coś jeszcze zapisać?"
        for (tool in listOf("none", "save_note")) {
            val raw = com.google.gson.JsonObject().apply {
                addProperty("reply", given)
                addProperty("tool", tool)
                addProperty("note", if (tool == "save_note") "Przykład" else "")
                addProperty("kind", "NOTE")
            }.toString()
            assertEquals(given, ConversationReplyProtocol.decode(raw).text)
        }
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

    @Test fun semanticClosureInstructionPreservesNaturalFinalityAndNewBusiness() {
        val prompt = ConversationInstructionBuilder.build(
            ConversationPreparation.incoming(), "Przyjmij wiadomość", ConversationResponseFormat.TOOL_REPLY)
        assertTrue(prompt.contains("Oceń ostatnią wypowiedź w kontekście całej rozmowy"))
        assertTrue(prompt.contains("Zwróć pożegnanie i tool=end_call w tej samej odpowiedzi"))
        assertTrue(prompt.contains("Nie zamieniaj wyraźnego zakończenia w kolejne pytanie"))
        assertTrue(prompt.contains("kolejną sprawę"))
        assertTrue(prompt.contains("cytat lub poprawkę"))
        val plain = ConversationInstructionBuilder.build(
            ConversationPreparation.incoming(), "Przyjmij wiadomość", ConversationResponseFormat.SPOKEN_TEXT)
        assertFalse(plain.contains("Zwróć pożegnanie i tool=end_call"))
    }

    @Test fun notebookToolJsonSchemaIsWellFormedAndPayloadValidated() {
        val json = com.google.gson.JsonParser.parseString(
            ConversationReplyProtocol.schema(setOf(ConversationToolId.END_CALL,
                ConversationToolId.SAVE_NOTE))).asJsonObject
        val names = json.getAsJsonObject("properties")
            .getAsJsonObject("tool").getAsJsonArray("enum").map { it.asString }
        assertTrue(names.containsAll(listOf("none", "end_call", "save_note")))
        assertEquals(setOf("reply", "tool", "note", "kind"),
            json.getAsJsonArray("required").map { it.asString }.toSet())
        for (tool in listOf("none", "end_call")) {
            val reply = ConversationReplyProtocol.decode(
                """{"reply":"Rozumiem.","tool":"$tool","note":"","kind":"NOTE"}""")
            assertNull(reply.note)
        }
        val payload = ConversationReplyProtocol.decode(
            """{"reply":"Zanotuję.","tool":"save_note","note":"Termin do sprawdzenia","kind":"CALENDAR_PROPOSAL"}""")
        assertEquals(ConversationToolId.SAVE_NOTE, payload.tool)
        assertEquals(pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind.CALENDAR_PROPOSAL,
            payload.note?.kind)
        assertEquals("Termin do sprawdzenia", payload.note?.content)
        assertFalse(payload.toString().contains("Termin do sprawdzenia"))
        for (raw in listOf(
            """{"reply":"Zapisuję","tool":"save_note"}""",
            """{"reply":"Zapisuję","tool":"save_note","note":""}""",
            """{"reply":"Zapisuję","tool":"save_note","note":"ok","kind":"SEND_EMAIL"}""",
            """{"reply":"Do widzenia","tool":"end_call","note":"Nie wolno wykonać"}""",
            """{"reply":"Do widzenia","tool":"save_note","note":"ok","number":"+48123123"}"""
        )) assertTrue(runCatching { ConversationReplyProtocol.decode(raw) }.isFailure)
    }

    private class FakeModel : TextCallAgentBackend {
        var listener: TextCallAgentBackend.Listener? = null
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) { this.listener = listener }
        override fun cancel() = Unit
    }
}
