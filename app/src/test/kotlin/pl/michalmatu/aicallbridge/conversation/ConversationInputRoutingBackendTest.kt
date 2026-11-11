package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.*

class ConversationInputRoutingBackendTest {
    private val raw = """{"transcript":"Proponuję termin.","reply":"Potwierdzam.","tool":"end_call"}"""

    @Test fun audioTranscriptUpdatesHostStateBeforeRoutingAndCannotReleaseTheReplacedTool() {
        val model = Pending()
        var reviewPending = false
        val routed = ConversationInputRoutingBackend(model, { true }, "revoked") { _, host ->
            assertTrue(reviewPending)
            host.onComplete("Termin do weryfikacji. Bez potwierdzenia.")
            true
        }
        val reply = ConversationReplyBackend(routed) { _, _, _ -> }
        val heard = mutableListOf<String>()
        val speech = mutableListOf<String>()
        val controller = TextCallTurnController(reply, TextOutputApprovalPolicy {
            assertNull(reply.takeTool())
            TextOutputDecision.RELEASE
        })
        controller.submitUserAudio(byteArrayOf(1, 0), object : TextCallTurnController.Listener {
            override fun onUserTranscript(text: String) { heard.add(text); reviewPending = true }
            override fun onApprovedResponse(text: String) { speech.add(text) }
            override fun onDroppedResponse() = fail("unexpected drop")
            override fun onError(reason: String) = fail(reason)
        })
        model.pending!!.onComplete(raw)
        assertEquals(1, model.audioRequests)
        assertEquals(listOf("Proponuję termin."), heard)
        assertEquals(listOf("Termin do weryfikacji. Bez potwierdzenia."), speech)
        assertNull(reply.takeTool())
    }

    @Test fun textUsesTheSameHostRoutingWithoutInvokingTheModel() {
        val model = Pending()
        val routed = ConversationInputRoutingBackend(model, { true }, "revoked") { input, host ->
            assertEquals("synthetic field request", input)
            host.onComplete("Brak zgody. \"Dane\" pozostają prywatne.")
            true
        }
        val results = mutableListOf<String>()
        routed.generate("synthetic field request", listener(results))
        assertEquals("Brak zgody. \"Dane\" pozostają prywatne.", ConversationReplyProtocol.decode(results.single()).text)
        assertNull(ConversationReplyProtocol.decode(results.single()).tool)
        assertNull(model.pending)
    }

    @Test fun cancellationInTranscriptCallbackPreventsHostRoutingSpeechAndTools() {
        val model = Pending()
        val routed = ConversationInputRoutingBackend(model, { true }, "revoked") { _, _ ->
            fail("cancelled host routing"); false
        }
        val reply = ConversationReplyBackend(routed) { _, _, _ -> }
        reply.generateAudio(byteArrayOf(1, 0), object : TextCallAgentBackend.Listener {
            override fun onUserTranscript(text: String) = reply.cancel()
            override fun onComplete(text: String) = fail("cancelled speech")
            override fun onError(reason: String) = fail(reason)
        })
        model.pending!!.onComplete(raw)
        assertNull(reply.takeTool())
    }

    @Test fun revokedOrStaleAudioCannotReachTranscriptOrHostRouting() {
        for (cancel in listOf(false, true)) {
            val model = Pending()
            var authorized = true
            val routed = ConversationInputRoutingBackend(model, { authorized }, "revoked") { _, _ ->
                fail("revoked host routing"); false
            }
            val errors = mutableListOf<String>()
            routed.generateAudio(byteArrayOf(1, 0), object : TextCallAgentBackend.Listener {
                override fun onUserTranscript(text: String) = fail("revoked transcript")
                override fun onComplete(text: String) = fail("revoked speech")
                override fun onError(reason: String) { errors.add(reason) }
            })
            if (cancel) routed.cancel() else authorized = false
            model.pending!!.onComplete(raw)
            assertEquals(if (cancel) emptyList<String>() else listOf("revoked"), errors)
        }
    }

    @Test fun ordinaryOutgoingAudioPassesTheExactReplyAndEmitsItsTranscriptOnce() {
        val model = Pending()
        val routed = ConversationInputRoutingBackend(model, { true }, "revoked")
        val results = mutableListOf<String>()
        val heard = mutableListOf<String>()
        val reply = ConversationReplyBackend(routed) { _, _, _ -> }
        reply.generateAudio(byteArrayOf(1, 0), listener(results, heard))
        model.pending!!.onComplete(raw)
        assertEquals(listOf("Proponuję termin."), heard)
        assertEquals(listOf("Potwierdzam."), results)
        assertEquals(pl.michalmatu.aicallbridge.conversationtools.ConversationToolId.END_CALL, reply.takeTool())
    }

    @Test fun malformedAudioCannotBeUsedForHostRouting() {
        val model = Pending()
        val routed = ConversationInputRoutingBackend(model, { true }, "revoked") { _, _ ->
            fail("malformed transcript"); false
        }
        val results = mutableListOf<String>()
        routed.generateAudio(byteArrayOf(1, 0), object : TextCallAgentBackend.Listener {
            override fun onUserTranscript(text: String) = fail("malformed transcript")
            override fun onComplete(text: String) = fail("malformed reply")
            override fun onError(reason: String) { results.add(reason) }
        })
        model.pending!!.onComplete("""{"transcript":"synthetic","reply":"text","tool":"unknown"}""")
        assertEquals(listOf("conversation_reply_invalid"), results)
    }

    private class Pending : TextCallAgentBackend {
        var pending: TextCallAgentBackend.Listener? = null
        var audioRequests = 0
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) { pending = listener }
        override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) {
            audioRequests++; pending = listener
        }
        override fun cancel() = Unit
    }
    private fun listener(results: MutableList<String>, heard: MutableList<String> = mutableListOf()) =
        object : TextCallAgentBackend.Listener {
            override fun onUserTranscript(text: String) { heard.add(text) }
            override fun onComplete(text: String) { results.add(text) }
            override fun onError(reason: String) = fail(reason)
        }
}
