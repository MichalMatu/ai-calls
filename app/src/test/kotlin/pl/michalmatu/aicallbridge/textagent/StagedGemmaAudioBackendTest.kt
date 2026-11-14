package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol

class StagedGemmaAudioBackendTest {
    @Test fun oneModelTranscribesThenRepliesWithValidatedTranscript() {
        val model = FakeModel()
        val backend = StagedGemmaAudioBackend(model)
        val reply = Result()
        backend.prewarm()
        backend.generateAudio(byteArrayOf(1, 0, 2, 0), reply)
        assertEquals(1, model.prewarms)
        assertEquals(1, model.audioRequests)
        assertEquals(listOf("Proszę powtórzyć informację."), model.textRequests)
        val decoded = ConversationReplyProtocol.decodeAudio(requireNotNull(reply.text))
        assertEquals("Proszę powtórzyć informację.", decoded.first)
        assertEquals("Oczywiście.", decoded.second.text)
        assertNull(reply.error)
        backend.close()
        assertEquals(1, model.closes)
    }

    @Test fun invalidTranscriptionNeverStartsTextStage() {
        val model = FakeModel(transcription = """{"transcript":"","tool":"end_call"}""")
        val reply = Result()
        StagedGemmaAudioBackend(model).generateAudio(byteArrayOf(0, 0), reply)
        assertEquals("audio_transcript_invalid", reply.error)
        assertTrue(model.textRequests.isEmpty())
    }

    @Test fun staleAudioCompletionCannotGenerateTextAfterCancel() {
        val model = FakeModel()
        model.deferAudio = true
        val backend = StagedGemmaAudioBackend(model)
        val reply = Result()
        backend.generateAudio(byteArrayOf(1, 0), reply)
        backend.cancel()
        model.completeDeferred()
        assertTrue(model.textRequests.isEmpty())
        assertNull(reply.text)
        assertNull(reply.error)
    }

    @Test fun invalidTranscriptReachesRecoverableErrorWithoutToolOrSpeech() {
        val model = FakeModel(transcription = """{"transcript":""}""")
        val reply = pl.michalmatu.aicallbridge.conversation.ConversationReplyBackend(
            StagedGemmaAudioBackend(model),
        ) { _, _, _ -> }
        val controller = TextCallTurnController(reply, TextOutputApprovalPolicy {
            fail("Invalid ASR must not reach output approval")
            TextOutputDecision.DROP
        })
        val errors = mutableListOf<String>()
        controller.submitUserAudio(byteArrayOf(0, 0), object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) = fail("No speech after failed ASR")
            override fun onDroppedResponse() = fail("No output candidate after failed ASR")
            override fun onError(reason: String) { errors.add(reason) }
        })
        assertEquals(listOf("backend_audio_transcript_invalid"), errors)
        assertTrue(pl.michalmatu.aicallbridge.conversation.ConversationReplyRecovery
            .canClarify(errors.single()))
        assertNull(reply.takeTool())
        assertTrue(model.textRequests.isEmpty())
        controller.close()
    }

    @Test fun invalidTextReplyDoesNotReachCaller() {
        val model = FakeModel(textResponse = """{"reply":"Private text"}""")
        val reply = Result()
        StagedGemmaAudioBackend(model).generateAudio(byteArrayOf(0, 0), reply)
        assertEquals("conversation_reply_invalid", reply.error)
        assertNull(reply.text)
    }

    private class Result : TextCallAgentBackend.Listener {
        var text: String? = null
        var error: String? = null
        override fun onComplete(text: String) { this.text = text }
        override fun onError(reason: String) { error = reason }
    }

    private class FakeModel(
        private val transcription: String = """{"transcript":"Proszę powtórzyć informację."}""",
        private val textResponse: String =
            """{"reply":"Oczywiście.","tool":"none","note":"","kind":"NOTE"}""",
    ) : TextCallAgentBackend {
        var prewarms = 0
        var closes = 0
        var audioRequests = 0
        val textRequests = mutableListOf<String>()
        var deferAudio = false
        private var deferred: TextCallAgentBackend.Listener? = null
        override fun prewarm() { prewarms++ }
        override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) {
            audioRequests++
            if (deferAudio) deferred = listener else listener.onComplete(transcription)
        }
        fun completeDeferred() { deferred?.onComplete(transcription) }
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            textRequests.add(userText)
            listener.onComplete(textResponse)
        }
        override fun cancel() = Unit
        override fun close() { closes++ }
    }
}
