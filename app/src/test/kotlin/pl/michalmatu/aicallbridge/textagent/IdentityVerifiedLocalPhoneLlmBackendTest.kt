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
import org.junit.Test

class IdentityVerifiedLocalPhoneLlmBackendTest {
    @Test
    fun `verifies exact props before sending inference`() {
        ScriptedHttpServer(
            listOf(
                Response(200, """{"model_alias":"qwen-phone-1.5b","model_path":"/data/local/tmp/aicall-phone-llm/model-1.5b.gguf","chat_template":"fixture"}"""),
                Response(200, """{"choices":[{"message":{"content":"Dzień dobry."}}]}"""),
            ),
        ).use { server ->
            val backend = IdentityVerifiedLocalPhoneLlmBackend(
                baseUrl = server.baseUrl(),
                expectedAlias = "qwen-phone-1.5b",
                expectedModelPath = "/data/local/tmp/aicall-phone-llm/model-1.5b.gguf",
                systemPrompt = "test",
            )
            val result = generate(backend)

            assertEquals("Dzień dobry.", result.text)
            assertNull(result.error)
            val requests = server.awaitRequests(2)
            assertTrue(requests[0].startsWith("GET /props HTTP/1.1"))
            assertTrue(requests[1].startsWith("POST /v1/chat/completions HTTP/1.1"))
            backend.close()
        }
    }

    @Test
    fun `stale alias fails closed without sending inference`() {
        ScriptedHttpServer(
            listOf(
                Response(200, """{"model_alias":"qwen-phone-0.5b","model_path":"/data/local/tmp/aicall-phone-llm/model.gguf","chat_template":"fixture"}"""),
            ),
        ).use { server ->
            val backend = IdentityVerifiedLocalPhoneLlmBackend(
                baseUrl = server.baseUrl(),
                expectedAlias = "qwen-phone-1.5b",
                expectedModelPath = "/data/local/tmp/aicall-phone-llm/model-1.5b.gguf",
                systemPrompt = "test",
            )
            val result = generate(backend)

            assertNull(result.text)
            assertEquals("server_identity_mismatch", result.error)
            val requests = server.awaitRequests(1)
            assertTrue(requests.single().startsWith("GET /props HTTP/1.1"))
            assertEquals(1, server.requestCount())
            backend.close()
        }
    }

    @Test
    fun `wrong model path fails closed even when alias matches`() {
        ScriptedHttpServer(
            listOf(
                Response(200, """{"model_alias":"qwen-phone-1.5b","model_path":"/data/local/tmp/aicall-phone-llm/model.gguf","chat_template":"fixture"}"""),
            ),
        ).use { server ->
            val backend = IdentityVerifiedLocalPhoneLlmBackend(
                baseUrl = server.baseUrl(),
                expectedAlias = "qwen-phone-1.5b",
                expectedModelPath = "/data/local/tmp/aicall-phone-llm/model-1.5b.gguf",
                systemPrompt = "test",
            )
            val result = generate(backend)

            assertEquals("server_identity_mismatch", result.error)
            assertEquals(1, server.requestCount())
            backend.close()
        }
    }

    private fun generate(backend: TextCallAgentBackend): Result {
        val latch = CountDownLatch(1)
        var text: String? = null
        var error: String? = null
        backend.generate("Cześć", object : TextCallAgentBackend.Listener {
            override fun onComplete(value: String) {
                text = value
                latch.countDown()
            }

            override fun onError(reason: String) {
                error = reason
                latch.countDown()
            }
        })
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return Result(text, error)
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

        fun requestCount(): Int = synchronized(requests) { requests.size }

        override fun close() {
            try { socket.close() } catch (_: Throwable) {}
            executor.shutdownNow()
        }
    }
}
