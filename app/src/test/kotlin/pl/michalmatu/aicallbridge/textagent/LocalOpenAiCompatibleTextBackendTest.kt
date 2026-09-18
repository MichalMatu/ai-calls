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

class LocalOpenAiCompatibleTextBackendTest {
    @Test
    fun `sends one non-streaming chat completion and returns complete text`() {
        OneShotHttpServer(
            statusCode = 200,
            responseBody = """{"choices":[{"message":{"content":"Odpowiedź lokalna."}}]}""",
        ).use { server ->
            val backend = LocalOpenAiCompatibleTextBackend(
                LocalOpenAiTextBackendConfig(
                    baseUrl = server.baseUrl(),
                    model = "fixture-model",
                ),
            )
            val latch = CountDownLatch(1)
            var result: String? = null
            var error: String? = null
            backend.generate("Cześć", object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    result = text
                    latch.countDown()
                }

                override fun onError(reason: String) {
                    error = reason
                    latch.countDown()
                }
            })

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals("Odpowiedź lokalna.", result)
            assertNull(error)
            val request = server.awaitRequest()
            assertTrue(request.startsWith("POST /v1/chat/completions HTTP/1.1"))
            assertTrue(request.contains("\"model\":\"fixture-model\""))
            assertTrue(request.contains("\"stream\":false"))
            assertTrue(request.contains("\"role\":\"user\""))
            assertTrue(request.contains("Cześć"))
            backend.close()
        }
    }

    @Test
    fun `reports http failure without exposing response body`() {
        OneShotHttpServer(statusCode = 503, responseBody = "SECRET BODY").use { server ->
            val backend = LocalOpenAiCompatibleTextBackend(
                LocalOpenAiTextBackendConfig(server.baseUrl(), "fixture-model"),
            )
            val latch = CountDownLatch(1)
            var error: String? = null
            backend.generate("test", object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) = latch.countDown()
                override fun onError(reason: String) {
                    error = reason
                    latch.countDown()
                }
            })
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals("http_503", error)
            assertTrue(error?.contains("SECRET") == false)
            backend.close()
        }
    }

    @Test
    fun `rejects public cleartext endpoint`() {
        try {
            LocalOpenAiTextBackendConfig("http://example.com:11434/v1/", "fixture-model")
            fail("public cleartext endpoint must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("local", ignoreCase = true))
        }
    }

    @Test
    fun `rejects standard OpenAI api key shaped token`() {
        try {
            LocalOpenAiTextBackendConfig(
                "https://localhost:11434/v1/",
                "fixture-model",
                "sk-should-never-live-on-device",
            )
            fail("standard OpenAI key shaped token must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("OpenAI", ignoreCase = true))
        }
    }

    private class OneShotHttpServer(
        private val statusCode: Int,
        private val responseBody: String,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val requestLatch = CountDownLatch(1)
        @Volatile private var requestText: String = ""

        init {
            executor.submit {
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
                    requestText = headers + body.copyOf(offset).toString(StandardCharsets.UTF_8)
                    requestLatch.countDown()

                    val payload = responseBody.toByteArray(StandardCharsets.UTF_8)
                    val reason = if (statusCode == 200) "OK" else "Service Unavailable"
                    val responseHeaders = buildString {
                        append("HTTP/1.1 $statusCode $reason\r\n")
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

        fun baseUrl(): String = "http://127.0.0.1:${socket.localPort}/v1/"

        fun awaitRequest(): String {
            assertTrue(requestLatch.await(5, TimeUnit.SECONDS))
            return requestText
        }

        override fun close() {
            try { socket.close() } catch (_: Throwable) {}
            executor.shutdownNow()
        }
    }
}
