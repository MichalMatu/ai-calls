package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversation.ConversationReplyBackend
import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.*

class SharedGemmaAudioContractTest {
    private val raw = """{"transcript":"Proszę zakończyć rozmowę.","reply":"Do widzenia.","tool":"end_call"}"""

    @Test fun textAndAudioShareOneRuntimeAndCancellationRejectsLateCompletion() {
        var loads = 0
        val pending = mutableListOf<TextCallAgentBackend.Listener>()
        val runtime = object : Gemma4Runtime {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) { pending.add(listener) }
            override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) { pending.add(listener) }
            override fun cancel() = Unit
            override fun close() = Unit
        }
        val backend = Gemma4LiteRtTextBackend(Gemma4RuntimeFactory { _, _ -> loads++; runtime }, "synthetic")
        val heard = mutableListOf<String>()
        val listener = object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { heard.add(text) }
            override fun onError(reason: String) { fail(reason) }
        }
        backend.generate("synthetic text", listener)
        pending[0].onComplete("text result")
        backend.generateAudio(byteArrayOf(1, 0), listener)
        backend.cancel()
        pending[1].onComplete("stale audio")
        backend.generateAudio(byteArrayOf(2, 0), listener)
        pending[2].onComplete("audio result")
        assertEquals(1, loads)
        assertEquals(listOf("text result", "audio result"), heard)
        backend.close()
    }

    @Test fun audioTranscriptAndToolAreSeparatedFromApprovedSpeech() {
        val model = PendingAudio()
        val reply = ConversationReplyBackend(model) { _, _, _ -> }
        val outputs = mutableListOf<String>()
        val heard = mutableListOf<String>()
        val controller = TextCallTurnController(reply, TextOutputApprovalPolicy {
            assertEquals(ConversationToolId.END_CALL, reply.takeTool())
            TextOutputDecision.RELEASE
        })
        controller.submitUserAudio(byteArrayOf(1, 0), listener(outputs, heard))
        model.pending!!.onComplete(raw)
        assertEquals(listOf("Proszę zakończyć rozmowę."), heard)
        assertEquals(listOf("Do widzenia."), outputs)
        assertNull(reply.takeTool())
    }

    @Test fun cancellationDuringTranscriptDeliveryPreventsSpeechAndToolProposal() {
        val model = PendingAudio()
        val reply = ConversationReplyBackend(model) { _, _, _ -> }
        val outputs = mutableListOf<String>()
        val controller = TextCallTurnController(reply, TextOutputApprovalPolicy { fail("stale approval"); TextOutputDecision.RELEASE })
        controller.submitUserAudio(byteArrayOf(1, 0), object : TextCallTurnController.Listener {
            override fun onUserTranscript(text: String) = controller.cancel()
            override fun onApprovedResponse(text: String) { outputs.add(text) }
            override fun onDroppedResponse() = Unit
            override fun onError(reason: String) { fail(reason) }
        })
        model.pending!!.onComplete(raw)
        assertTrue(outputs.isEmpty())
        assertNull(reply.takeTool())
    }

    @Test fun droppedAudioResponseCannotReleaseItsToolOrBecomeSpeech() {
        val model = PendingAudio()
        val reply = ConversationReplyBackend(model) { _, _, _ -> }
        val outputs = mutableListOf<String>()
        val controller = TextCallTurnController(reply, TextOutputApprovalPolicy { TextOutputDecision.DROP })
        controller.submitUserAudio(byteArrayOf(1, 0), listener(outputs, mutableListOf()))
        model.pending!!.onComplete(raw)
        assertEquals(listOf("dropped"), outputs)
        assertNull(reply.takeTool())
    }

    @Test fun malformedAudioCannotBeMistakenForACompleteReply() {
        for (candidate in listOf(
            """{"reply":"Do widzenia","tool":"end_call"}""",
            """{"transcript":"","reply":"Do widzenia","tool":"end_call"}""",
            """{"transcript":true,"reply":"Do widzenia","tool":"end_call"}""",
            """{"transcript":"synthetic","reply":"Do widzenia","tool":"dial"}""",
            """{"transcript":"synthetic","reply":"Do widzenia","tool":"none","number":"synthetic"}""")) {
            assertTrue(runCatching { ConversationReplyProtocol.decodeAudio(candidate) }.isFailure)
        }
        assertTrue(runCatching { ConversationReplyProtocol.decode(raw) }.isFailure)
    }

    @Test fun finalAudioReplyCannotReplaceAnAlreadyReportedTranscript() {
        val model = PendingAudio()
        val reply = ConversationReplyBackend(model) { _, _, _ -> }
        val errors = mutableListOf<String>()
        reply.generateAudio(byteArrayOf(1, 0), object : TextCallAgentBackend.Listener {
            override fun onUserTranscript(text: String) = Unit
            override fun onComplete(text: String) = fail("mismatched transcript reached approval")
            override fun onError(reason: String) { errors.add(reason) }
        })
        model.pending!!.onUserTranscript("Inna wypowiedź.")
        model.pending!!.onComplete(raw)
        assertEquals(listOf("conversation_reply_invalid"), errors)
        assertNull(reply.takeTool())
    }

    @Test fun pcmInputPublishesExactlyOneAudioPayloadAndNoTranscript() {
        val input = GemmaAudioDirectInputProvider()
        val audio = mutableListOf<ByteArray>()
        val listener = object : SpeechInputProvider.Listener {
            override fun onReady() = Unit
            override fun onFinalTranscript(text: String) { fail("ASR must not allocate a second model") }
            override fun onFinalAudio(pcm16: ByteArray) { audio.add(pcm16) }
            override fun onError(reason: String) { fail(reason) }
        }
        input.start(listener)
        assertTrue(input.writePcm(byteArrayOf(1, 0, 2, 0)))
        input.finishInput(); input.finishInput()
        assertEquals(1, audio.size)
        assertArrayEquals(byteArrayOf(1, 0, 2, 0), audio.single())
        input.start(listener); input.writePcm(byteArrayOf(3, 0)); input.cancel(); input.finishInput()
        assertEquals(1, audio.size)
    }

    @Test fun boundedPcmRejectsOverflowWithoutLosingEarlierAudio() {
        val input = GemmaAudioDirectInputProvider()
        val heard = mutableListOf<ByteArray>()
        val listener = object : SpeechInputProvider.Listener {
            override fun onReady() = Unit
            override fun onFinalTranscript(text: String) = fail("unexpected transcript")
            override fun onFinalAudio(pcm16: ByteArray) { heard.add(pcm16) }
            override fun onError(reason: String) = fail(reason)
        }
        input.start(listener)
        val limit = GemmaAudioPcm16.MAX_PCM_BYTES
        val allowed = ByteArray(limit) { (it and 255).toByte() }
        assertTrue(input.writePcm(allowed, 0, limit - 2))
        assertFalse(input.writePcm(allowed, 0, 4))
        assertTrue(input.writePcm(allowed, limit - 2, 2))
        assertFalse(input.writePcm(allowed, 0, 2))
        input.finishInput()
        assertEquals(1, heard.size)
        assertArrayEquals(allowed, heard.single())
        assertFalse(input.writePcm(byteArrayOf(0, 0)))
        input.close()
    }

    @Test fun cancelAndRestartNeverPublishPreviousSamples() {
        val input = GemmaAudioDirectInputProvider()
        val heard = mutableListOf<ByteArray>()
        val errors = mutableListOf<String>()
        val listener = object : SpeechInputProvider.Listener {
            override fun onReady() = Unit
            override fun onFinalTranscript(text: String) = fail("unexpected transcript")
            override fun onFinalAudio(pcm16: ByteArray) { heard.add(pcm16) }
            override fun onError(reason: String) { errors.add(reason) }
        }
        input.start(listener)
        input.writePcm(ByteArray(128) { 99 })
        input.cancel()
        input.finishInput()
        assertTrue(heard.isEmpty())
        input.start(listener)
        assertTrue(input.writePcm(byteArrayOf(1, 0, 2, 0)))
        input.finishInput()
        assertArrayEquals(byteArrayOf(1, 0, 2, 0), heard.single())
        input.start(listener)
        input.writePcm(byteArrayOf(5))
        input.finishInput()
        assertEquals(listOf("invalid_pcm16"), errors)
        input.close()
    }

    @Test fun cancelErasesBackingPcmInsteadOfOnlyClearingLength() {
        val input = GemmaAudioDirectInputProvider()
        val heard = mutableListOf<ByteArray>()
        val listener = object : SpeechInputProvider.Listener {
            override fun onReady() = Unit
            override fun onFinalTranscript(text: String) = fail("unexpected transcript")
            override fun onFinalAudio(pcm16: ByteArray) { heard.add(pcm16) }
            override fun onError(reason: String) = fail(reason)
        }
        input.start(listener)
        assertTrue(input.writePcm(ByteArray(256) { 0x55.toByte() }))
        val field = input.javaClass.getDeclaredField("pcm").apply { isAccessible = true }
        val beforeCancel = field.get(input) as ByteArray
        assertTrue(beforeCancel.take(256).any { it != 0.toByte() })
        input.cancel()
        assertTrue("Cancelled turn must not publish audio", heard.isEmpty())
        assertTrue("PCM backing must be zeroed", beforeCancel.all { it == 0.toByte() })
        input.start(listener)
        val data = ByteArray(40_000) { 0x55.toByte() }
        input.writePcm(data)
        val grown = field.get(input) as ByteArray
        assertTrue("growth retains no earlier PCM in old buffer",
            beforeCancel.all { it == 0.toByte() })
        input.finishInput()
        assertEquals(1, heard.size)
        assertArrayEquals(data, heard.single())
        assertTrue("finished PCM backing must be zeroed", grown.all { it == 0.toByte() })
        input.close()
        assertEquals(0, (field.get(input) as ByteArray).size)
    }

    private class PendingAudio : TextCallAgentBackend {
        var pending: TextCallAgentBackend.Listener? = null
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) { fail("unexpected text inference") }
        override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) { pending = listener }
        override fun cancel() = Unit
    }

    private fun listener(outputs: MutableList<String>, heard: MutableList<String>) = object : TextCallTurnController.Listener {
        override fun onUserTranscript(text: String) { heard.add(text) }
        override fun onApprovedResponse(text: String) { outputs.add(text) }
        override fun onDroppedResponse() { outputs.add("dropped") }
        override fun onError(reason: String) { fail(reason) }
    }
}
