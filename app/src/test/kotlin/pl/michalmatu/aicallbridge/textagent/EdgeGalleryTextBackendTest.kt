package pl.michalmatu.aicallbridge.textagent

import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EdgeGalleryTextBackendTest {
    @Test
    fun `requires health and exact model before inference`() {
        ScriptedHttpServer(
            listOf(
                Response(200, """{"status":"ok"}"""),
                Response(200, """{"object":"list","data":[{"id":"Gemma-3n-E2B-it","object":"model"}]}"""),
                Response(200, """{"choices":[{"message":{"content":"Dzień dobry."}}]}"""),
            ),
        ).use { server ->
            val backend = EdgeGalleryTextBackend(
                baseUrl = server.baseUrl(),
                expectedModelId = "Gemma-3n-E2B-it",
                bearerToken = "edge-gallery-test-token",
                systemPrompt = "test",
            )

            val result = generate(backend)

            assertEquals("Dzień dobry.", result.text)
            assertNull(result.error)
            val requests = server.awaitRequests(3)
            assertTrue(requests[0].startsWith("GET /health HTTP/1.1"))
            assertTrue(requests[1].startsWith("GET /v1/models HTTP/1.1"))
            assertTrue(requests[2].startsWith("POST /v1/chat/completions HTTP/1.1"))
            requests.forEach { request ->
                assertTrue(request.contains("Authorization: Bearer edge-gallery-test-token", ignoreCase = true))
            }
            assertTrue(requests[2].contains("\"model\":\"Gemma-3n-E2B-it\""))
            backend.close()
        }
    }

    @Test
    fun `missing expected model fails closed before inference`() {
        ScriptedHttpServer(
            listOf(
                Response(200, """{"status":"ok"}"""),
                Response(200, """{"data":[{"id":"Other-model"}]}"""),
            ),
        ).use { server ->
            val backend = EdgeGalleryTextBackend(
                baseUrl = server.baseUrl(),
                expectedModelId = "Gemma-3n-E2B-it",
            )

            val result = generate(backend)

            assertNull(result.text)
            assertEquals("edge_gallery_model_not_available", result.error)
            assertEquals(2, server.awaitRequests(2).size)
            backend.close()
        }
    }

    @Test
    fun `unhealthy response fails before model lookup`() {
        ScriptedHttpServer(
            listOf(Response(200, """{"status":"starting"}""")),
        ).use { server ->
            val backend = EdgeGalleryTextBackend(
                baseUrl = server.baseUrl(),
                expectedModelId = "Gemma-3n-E2B-it",
            )

            val result = generate(backend)

            assertEquals("edge_gallery_health_invalid", result.error)
            assertEquals(1, server.awaitRequests(1).size)
            backend.close()
        }
    }

    @Test
    fun `rejects non loopback Edge Gallery endpoint`() {
        try {
            EdgeGalleryTextBackend(
                baseUrl = "http://192.168.1.50:8080/v1/",
                expectedModelId = "Gemma-3n-E2B-it",
            )
            fail("Edge Gallery provider must remain phone-loopback only")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("loopback", ignoreCase = true))
        }
    }

    private fun generate(backend: TextCallAgentBackend): Result {
        val latch = CountDownLatch(1)
        var generatedText: String? = null
        var error: String? = null
        backend.generate("Cześć", object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                generatedText = text
                latch.countDown()
            }

            override fun onError(reason: String) {
                error = reason
                latch.countDown()
            }
        })
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return Result(generatedText, error)
    }

    private data class Result(val text: String?, val error: String?)
    private data class Response(val statusCode: Int, val body: String)

    private class ScriptedHttpServer(
        private val responses: List<Response>,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, responses.size.coerceAtLeast(1), InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val requestLatch = CountDownLatch(responses.size)
        private val requests = mutableListOf<String>()

        init {
            executor.submit {
                for (response in responses) {
                    socket.accept().use { client ->
                        client.soTimeout = 5_000
                        val input = BufferedInputStream(client.getInputStream())
                        val headerBytes = ArrayList<Byte>()
                        var matched = 0
                        val terminator = byteArrayOf(13, 10, 13, 10)
                        while (matched < terminator.size) {
                            val value = input.read()
                            if (value < 0) break
                            val byte = value.toByte()
                            headerBytes += byte
                            matched = if (byte == terminator[matched]) matched + 1 else if (byte == terminator[0]) 1 else 0
                        }
                        val headers = headerBytes.toByteArray().toString(StandardCharsets.UTF_8)
                        val contentLength = Regex("(?i)Content-Length:\\s*(\\d+)")
                            .find(headers)
                            ?.groupValues
                            ?.get(1)
                            ?.toInt()
                            ?: 0
                        val body = ByteArray(contentLength)
                        var offset = 0
                        while (offset < body.size) {
                            val read = input.read(body, offset, body.size - offset)
                            if (read < 0) break
                            offset += read
                        }
                        synchronized(requests) {
                            requests += headers + body.copyOf(offset).toString(StandardCharsets.UTF_8)
                        }
                        requestLatch.countDown()

                        val payload = response.body.toByteArray(StandardCharsets.UTF_8)
                        val reason = if (response.statusCode == 200) "OK" else "Service Unavailable"
                        val responseHeaders = buildString {
                            append("HTTP/1.1 ${response.statusCode} $reason\r\n")
                            append("Content-Type: application/json\r\n")
                            append("Content-Length: ${payload.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(StandardCharsets.US_ASCII)
                        client.getOutputStream().apply {
                            write(responseHeaders)
                            write(payload)
                            flush()
                        }
                    }
                }
            }
        }

        fun baseUrl(): String = "http://127.0.0.1:${socket.localPort}/v1/"

        fun awaitRequests(expected: Int): List<String> {
            assertTrue(requestLatch.await(5, TimeUnit.SECONDS))
            return synchronized(requests) { requests.toList() }.take(expected)
        }

        override fun close() {
            try { socket.close() } catch (_: Throwable) {}
            executor.shutdownNow()
        }
    }
}
