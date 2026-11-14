package pl.michalmatu.aicallbridge.conversation

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallTurnController
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

class ConversationReplyRecoveryTest {
    private val valid = """{"reply":"Uwzględnię poprawkę.","tool":"none"}"""

    @Test fun truncatedJsonIsRegeneratedBeforeItCanBecomeSpeechOrATool() {
        val utterance = "synthetic correction"
        val inputs = mutableListOf<String>()
        var repairs = 0
        val failures = mutableListOf<ConversationReplyFailure>()
        val raw = sendWithReplyRecovery(
            isCurrent = { true },
            send = {
                inputs += utterance
                if (repairs == 0) """{"reply":"Do widzenia.","tool":"end_call""" else valid
            },
            validate = { ConversationReplyProtocol.validateGenerated(it, false) },
            recreateForRepair = { repairs++ }, rejected = failures::add,
        )
        assertEquals(listOf(utterance, utterance), inputs)
        assertEquals(1, repairs)
        assertEquals(listOf(ConversationReplyFailure.JSON), failures)
        assertNull(ConversationReplyProtocol.decode(raw).tool)
        assertEquals("Uwzględnię poprawkę.", ConversationReplyProtocol.decode(raw).text)
    }

    @Test fun failedRepairStopsInferenceAndRequestsClarificationWithoutReleasingATool() {
        var sends = 0
        var repairs = 0
        val model = object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                try {
                    val raw = sendWithReplyRecovery(
                        { true }, { sends++; """{"reply":"synthetic","tool":"dial"}""" },
                        { ConversationReplyProtocol.validateGenerated(it, false) }, { repairs++ },
                    )
                    listener.onComplete(raw)
                } catch (_: InvalidConversationReply) {
                    listener.onError("gemma4_runtime_conversation_reply_invalid")
                }
            }
            override fun cancel() = Unit
        }
        val backend = ConversationReplyBackend(model) { _, _, _ -> }
        val errors = mutableListOf<String>()
        val controller = TextCallTurnController(backend, TextOutputApprovalPolicy {
            fail("invalid proposal reached approval"); TextOutputDecision.DROP
        })
        controller.submitUserText("synthetic", object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) = fail("invalid speech released")
            override fun onDroppedResponse() = fail("invalid proposal reached approval")
            override fun onError(reason: String) { errors += reason }
        })
        assertEquals(2, sends)
        assertEquals(1, repairs)
        assertEquals(listOf("backend_conversation_reply_invalid"), errors)
        assertTrue(ConversationReplyRecovery.canClarify(errors.single()))
        assertNull(backend.takeTool())
    }

    @Test fun stopWhileRejectingPreventsRecreation() {
        var current = true
        var repairs = 0
        try {
            sendWithReplyRecovery(
                { current }, { "synthetic invalid" },
                { ConversationReplyProtocol.validateGenerated(it, false) },
                { repairs++ }, { current = false },
            )
            fail("STOP must cancel")
        } catch (_: CancellationException) { }
        assertEquals(0, repairs)
    }

    @Test fun stopDuringRecreationPreventsASecondSend() {
        var current = true
        var sends = 0
        try {
            sendWithReplyRecovery(
                { current }, { sends++; "synthetic invalid" },
                { ConversationReplyProtocol.validateGenerated(it, false) }, { current = false },
            )
            fail("STOP must cancel")
        } catch (_: CancellationException) { }
        assertEquals(1, sends)
    }

    @Test fun stopDuringSendRejectsEvenAnOtherwiseValidReply() {
        var current = true
        var validated = false
        try {
            sendWithReplyRecovery(
                { current }, { current = false; valid }, { validated = true },
                { fail("no repair after STOP") },
            )
            fail("STOP must cancel")
        } catch (_: CancellationException) { }
        assertFalse(validated)
    }

    @Test fun stopDuringValidationCannotReleaseTheValidatedReply() {
        var current = true
        try {
            sendWithReplyRecovery(
                { current }, { valid },
                { ConversationReplyProtocol.validateGenerated(it, false); current = false },
                { fail("no repair after STOP") },
            )
            fail("STOP must cancel")
        } catch (_: CancellationException) { }
    }

    @Test fun invalidNativeAudioTranscriptRequestsClarificationButNotDisconnect() {
        assertTrue(ConversationReplyRecovery.canClarify("backend_audio_transcript_invalid"))
        assertTrue(ConversationReplyRecovery.canClarify("backend_conversation_reply_invalid"))
        for (other in listOf("backend_model_audio_input_unavailable",
            "backend_gemma4_runtime_engine_unavailable", "backend_stt_audio_model_unavailable")) {
            assertFalse(ConversationReplyRecovery.canClarify(other))
        }
    }

    @Test fun diagnosticsContainOnlyFixedCategoriesAndUnrelatedFailuresAreNotRetried() {
        for ((raw, expected) in listOf(
            "PRIVATE_FIXTURE invalid" to ConversationReplyFailure.JSON,
            "[]" to ConversationReplyFailure.OBJECT,
            """{"reply":"PRIVATE_FIXTURE","tool":"none","extra":"PRIVATE_FIXTURE"}""" to ConversationReplyFailure.FIELDS,
            """{"reply":false,"tool":"none"}""" to ConversationReplyFailure.SPEECH,
            """{"reply":"PRIVATE_FIXTURE","tool":"unsupported"}""" to ConversationReplyFailure.TOOL,
            """{"reply":"PRIVATE_FIXTURE","tool":"save_note"}""" to ConversationReplyFailure.NOTE,
        )) {
            try {
                ConversationReplyProtocol.validateGenerated(raw, false)
                fail("invalid response accepted")
            } catch (error: InvalidConversationReply) {
                assertEquals(expected, error.failure)
                assertFalse(error.toString().contains("PRIVATE_FIXTURE"))
            }
        }
        val failure = IllegalStateException("synthetic GPU failure")
        try {
            sendWithReplyRecovery<String>({ true }, { throw failure }, {}, { fail("unrelated retry") })
            fail("native error must propagate")
        } catch (error: IllegalStateException) { assertSame(failure, error) }
        for (reason in listOf("backend_start_error", "operator_stopped", "backend_model_unavailable",
            "backend_conversation_reply_invalid_extra")) assertFalse(ConversationReplyRecovery.canClarify(reason))
    }

    @Test fun incomingInstructionsCarryTheActualApplicationOpeningAsConversationState() {
        val prompt = ConversationInstructionBuilder.build(ConversationPreparation.incoming(),
            "Przyjmij wiadomość", ConversationResponseFormat.TOOL_REPLY)
        assertTrue(prompt.contains(ConversationInstructionBuilder.INCOMING_OPENING))
        assertTrue(prompt.contains("Rozmówca teraz na to odpowiada"))
    }

    @Test fun unfinishedEditorialTemplateIsRepairedBeforeSpeechAndHistory() {
        var attempts = 0
        val raw = sendWithReplyRecovery(
            { true }, {
                attempts++
                if (attempts == 1)
                    """{"reply":"Godzina to [tutaj wstaw aktualną godzinę].","tool":"none","note":"","kind":"NOTE"}"""
                else """{"reply":"Po poprawce proponowana godzina to piętnasta.","tool":"none","note":"","kind":"NOTE"}"""
            },
            { ConversationReplyProtocol.validateGenerated(it, false) }, {},
        )
        assertEquals(2, attempts)
        assertTrue(ConversationReplyProtocol.decode(raw).text.contains("piętnasta"))
        assertEquals("Tytuł wiadomości to [pilne].", ConversationReplyProtocol.decode(
            """{"reply":"Tytuł wiadomości to [pilne].","tool":"none"}""").text)
    }
}
