package pl.michalmatu.aicallbridge.developerrelay

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class ChatRelayBackendTest {
    @Test
    fun envelopeCodec_roundTripsUnicodeAndNewlines() {
        val original = ChatRelayEnvelope(
            sessionId = "orange-demo_1",
            turnId = 2,
            text = "Dzień dobry.\nW czym mogę pomóc?",
        )

        val encoded = ChatRelayEnvelopeCodec.encode("request", original)
        val decoded = ChatRelayEnvelopeCodec.decode("request", encoded)

        assertEquals(original, decoded)
        assertTrue(encoded.contains("AICALL_CHAT_RELAY_V1"))
        assertTrue(encoded.contains("Dzień dobry."))
    }

    @Test
    fun envelopeCodec_rejectsWrongKindAndUnsafeSessionId() {
        val valid = ChatRelayEnvelope("session-1", 1, "tekst")
        val encoded = ChatRelayEnvelopeCodec.encode("request", valid)

        assertThrows(IllegalArgumentException::class.java) {
            ChatRelayEnvelopeCodec.decode("response", encoded)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatRelayEnvelope("../escape", 1, "tekst").validate()
        }
    }

    @Test
    fun mailbox_ignoresStaleResponseAndConsumesMatchingResponse() {
        withMailbox { mailbox ->
            mailbox.publishRequest(ChatRelayEnvelope("session-1", 3, "pytanie"))
            assertEquals("pytanie", mailbox.readRequest()!!.text)

            mailbox.publishResponse(ChatRelayEnvelope("other-session", 3, "stare"))
            assertNull(mailbox.takeMatchingResponse("session-1", 3))

            mailbox.publishResponse(ChatRelayEnvelope("session-1", 3, "odpowiedź"))
            assertEquals(
                "odpowiedź",
                mailbox.takeMatchingResponse("session-1", 3)!!.text,
            )
            assertNull(mailbox.takeMatchingResponse("session-1", 3))
        }
    }

    @Test
    fun backend_publishesRequestAndCompletesOnlyMatchingTurn() {
        withMailbox { mailbox ->
            val complete = AtomicReference<String?>(null)
            val error = AtomicReference<String?>(null)
            val latch = CountDownLatch(1)
            val backend = InteractiveChatRelayBackend(
                mailbox = mailbox,
                sessionId = "session-1",
                responseTimeoutMs = 2_000,
                pollIntervalMs = 10,
            )
            try {
                backend.generate("Co słychać?", object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        complete.set(text)
                        latch.countDown()
                    }

                    override fun onError(reason: String) {
                        error.set(reason)
                        latch.countDown()
                    }
                })

                val request = waitForRequest(mailbox)
                assertEquals(1L, request.turnId)
                assertEquals("Co słychać?", request.text)

                mailbox.publishResponse(ChatRelayEnvelope("session-1", 99, "zły turn"))
                Thread.sleep(40)
                assertNull(complete.get())

                mailbox.publishResponse(ChatRelayEnvelope("session-1", 1, "W porządku."))
                assertTrue(latch.await(2, TimeUnit.SECONDS))
                assertEquals("W porządku.", complete.get())
                assertNull(error.get())
            } finally {
                backend.close()
            }
        }
    }

    @Test
    fun backend_cancelInvalidatesLateResponse() {
        withMailbox { mailbox ->
            val callbackSeen = CountDownLatch(1)
            val backend = InteractiveChatRelayBackend(
                mailbox = mailbox,
                sessionId = "session-1",
                responseTimeoutMs = 2_000,
                pollIntervalMs = 10,
            )
            try {
                backend.generate("test", object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) = callbackSeen.countDown()
                    override fun onError(reason: String) = callbackSeen.countDown()
                })
                val request = waitForRequest(mailbox)
                backend.cancel()
                mailbox.publishResponse(ChatRelayEnvelope("session-1", request.turnId, "za późno"))

                assertFalse(callbackSeen.await(150, TimeUnit.MILLISECONDS))
            } finally {
                backend.close()
            }
        }
    }

    @Test
    fun backend_timesOutFailClosed() {
        withMailbox { mailbox ->
            val error = AtomicReference<String?>(null)
            val latch = CountDownLatch(1)
            val backend = InteractiveChatRelayBackend(
                mailbox = mailbox,
                sessionId = "session-1",
                responseTimeoutMs = 80,
                pollIntervalMs = 10,
            )
            try {
                backend.generate("test", object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) = latch.countDown()
                    override fun onError(reason: String) {
                        error.set(reason)
                        latch.countDown()
                    }
                })
                assertTrue(latch.await(1, TimeUnit.SECONDS))
                assertEquals("relay_timeout", error.get())
            } finally {
                backend.close()
            }
        }
    }

    private fun waitForRequest(mailbox: ChatRelayMailbox): ChatRelayEnvelope {
        repeat(100) {
            mailbox.readRequest()?.let { return it }
            Thread.sleep(10)
        }
        error("request was not published")
    }

    private fun withMailbox(block: (ChatRelayMailbox) -> Unit) {
        val dir = Files.createTempDirectory("chat-relay-test").toFile()
        try {
            block(ChatRelayMailbox(File(dir, "mailbox")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
