package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class IncomingCallerFarewellPolicyTest {
    @Test fun shortCallerGoodbyeIsAnUnambiguousEnd() {
        val goodbyes = listOf(
            "Do widzenia", "DO WIDZENIA!", "Dziękuję, do widzenia.",
            "To już wszystko, dziękuję, do widzenia.",
            "Do usłyszenia", "Żegnam.", "Goodbye!", "Thank you, goodbye."
        )
        goodbyes.forEach { assertTrue(it, IncomingCallerFarewellPolicy.isUnambiguousGoodbye(it)) }
    }

    @Test fun quotesQuestionsExtraRequestsAndAmbiguousThanksNeverAutoEnd() {
        val notGoodbyes = listOf(
            "Dziękuję", "Dziękuję za informację",
            "Czy mogę powiedzieć do widzenia?",
            "Powiedział do widzenia, ale mam poprawkę",
            "W notatce zapisz słowa do widzenia",
            "Nie rozłączaj, do widzenia to cytat",
            "Do widzenia, jeszcze jedna sprawa",
            "\"do widzenia\"", "Goodbye? I have another question",
            "Proszę się nie rozłączać", "Widziałem napis do widzenia"
        )
        notGoodbyes.forEach { assertFalse(it,
            IncomingCallerFarewellPolicy.isUnambiguousGoodbye(it)) }
    }

    @Test fun explicitCallerGoodbyeReplacesModelQuestionOrNoteOnlyForIncoming() {
        val proposed = ConversationReply("Czy mogę jeszcze pomóc?", null)
        val forced = IncomingCallerFarewellPolicy.normalizeProposal("Do widzenia.",
            proposed, "pl-PL")
        assertEquals("Dziękuję za rozmowę. Do widzenia.", forced.text)
        assertEquals(ConversationToolId.END_CALL, forced.tool)
        assertNull(forced.note)
        assertSame(proposed, IncomingCallerFarewellPolicy.normalizeProposal(
            "W notatce dopisz do widzenia", proposed, "pl-PL"))
        assertEquals("Thank you for calling. Goodbye.",
            IncomingCallerFarewellPolicy.normalizeProposal(
                "Goodbye.", proposed, "en-US").text)
        val unsavedDetail = ConversationReply("Zapiszę informację.",
            ConversationToolId.SAVE_NOTE, ConversationNoteProposal(ConversationNoteKind.NOTE, "Nowa data"))
        assertSame(unsavedDetail, IncomingCallerFarewellPolicy.normalizeProposal(
            "Mam jeszcze nową datę, do widzenia", unsavedDetail, "pl-PL"))
    }

    @Test fun backendEmitsFinalSpeechAndProposalButDoesNotRunAnyCallAction() {
        val model = FakeModel()
        val backend = ConversationReplyBackend(model,
            normalizeReply = { caller, reply ->
                IncomingCallerFarewellPolicy.normalizeProposal(caller, reply, "pl-PL")
            }
        ) { _, _, _ -> }
        val spoken = mutableListOf<String>()
        backend.generate("Dziękuję, do widzenia.", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { spoken += text }
            override fun onError(reason: String) { fail(reason) }
        })
        model.listener!!.onComplete(
            """{"reply":"Czy to już wszystko?","tool":"none","note":"","kind":"NOTE"}""")
        assertEquals(listOf("Dziękuję za rozmowę. Do widzenia."), spoken)
        assertEquals(ConversationToolId.END_CALL, backend.takeTool())
        assertNull(backend.takeTool()) // existing playback boundary still owns execution
        backend.close()
    }

    @Test fun replyWithNewBusinessKeepsItsToolAndVoice() {
        val model = FakeModel()
        val backend = ConversationReplyBackend(model,
            normalizeReply = { caller, reply ->
                IncomingCallerFarewellPolicy.normalizeProposal(caller, reply, "pl-PL")
            }
        ) { _, _, _ -> }
        val spoken = mutableListOf<String>()
        backend.generate("Mam zmianę terminu, do widzenia", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { spoken += text }
            override fun onError(reason: String) { fail(reason) }
        })
        model.listener!!.onComplete(
            """{"reply":"Zanotuję korektę.","tool":"save_note","note":"Zmiana terminu","kind":"NOTE"}""")
        assertEquals(listOf("Zanotuję korektę."), spoken)
        assertEquals(ConversationToolId.SAVE_NOTE, backend.takeTool())
        backend.close()
    }

    private class FakeModel : TextCallAgentBackend {
        var listener: TextCallAgentBackend.Listener? = null
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            this.listener = listener
        }
        override fun cancel() = Unit
    }
}
