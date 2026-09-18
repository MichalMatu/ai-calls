package pl.michalmatu.aicallbridge.realtime

import java.io.IOException
import java.lang.reflect.Proxy
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpRealtimeSocketConnectorTest {
    @Test
    fun buildsOnlyHandshakeMechanicsAndForwardsTextLifecycle() {
        val factory = RecordingWebSocketFactory()
        val connector = OkHttpRealtimeSocketConnector(factory)
        val listener = RecordingRealtimeListener()
        val handshake = RealtimeWebSocketHandshake(
            "wss://api.openai.com/v1/realtime?model=gpt-realtime",
            listOf("realtime", "openai-insecure-api-key.eph_123"),
        )

        connector.connect(handshake, listener)

        val request = requireNotNull(factory.request)
        assertEquals(
            "wss://api.openai.com/v1/realtime?model=gpt-realtime",
            request.url.toString(),
        )
        assertEquals(
            "realtime, openai-insecure-api-key.eph_123",
            request.header("Sec-WebSocket-Protocol"),
        )
        assertNull(request.header("Authorization"))
        assertFalse(request.url.toString().contains("eph_123"))

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols")
            .build()
        factory.listener!!.onOpen(factory.socket, response)

        val wrapped = requireNotNull(listener.openedSocket)
        assertTrue(wrapped.send("outbound"))
        assertEquals(listOf("outbound"), factory.sentText)

        factory.listener!!.onMessage(factory.socket, "inbound")
        assertEquals(listOf("inbound"), listener.texts)

        factory.listener!!.onClosed(factory.socket, 1000, "done")
        assertEquals(1000, listener.closedCode)
        assertEquals("done", listener.closedReason)

        val failure = IOException("network down")
        factory.listener!!.onFailure(factory.socket, failure, null)
        assertSame(failure, listener.failure)
    }

    @Test
    fun rejectsUnexpectedBinaryFramesAndClosesTheUnderlyingSocket() {
        val factory = RecordingWebSocketFactory()
        val connector = OkHttpRealtimeSocketConnector(factory)
        val listener = RecordingRealtimeListener()
        val handshake = RealtimeWebSocketHandshake(
            "wss://api.openai.com/v1/realtime?model=gpt-realtime",
            listOf("realtime", "openai-insecure-api-key.eph_123"),
        )
        connector.connect(handshake, listener)

        factory.listener!!.onMessage(factory.socket, ByteString.of(1, 2, 3))

        assertNotNull(listener.failure)
        assertTrue(listener.failure is IllegalStateException)
        assertTrue(listener.failure!!.message!!.contains("binary", ignoreCase = true))
        assertEquals(1003, factory.closedCode)
    }

    private class RecordingRealtimeListener : RealtimeSocketConnector.Listener {
        var openedSocket: RealtimeSocket? = null
        val texts = mutableListOf<String>()
        var failure: Throwable? = null
        var closedCode: Int? = null
        var closedReason: String? = null

        override fun onOpen(socket: RealtimeSocket) {
            openedSocket = socket
        }

        override fun onText(text: String) {
            texts += text
        }

        override fun onFailure(error: Throwable) {
            failure = error
        }

        override fun onClosed(code: Int, reason: String) {
            closedCode = code
            closedReason = reason
        }
    }

    private class RecordingWebSocketFactory : WebSocket.Factory {
        var request: Request? = null
        var listener: WebSocketListener? = null
        val sentText = mutableListOf<String>()
        var closedCode: Int? = null
        var closedReason: String? = null

        val socket: WebSocket = Proxy.newProxyInstance(
            WebSocket::class.java.classLoader,
            arrayOf(WebSocket::class.java),
        ) { _, method, args ->
            when (method.name) {
                "request" -> request ?: Request.Builder().url("wss://example.invalid").build()
                "queueSize" -> 0L
                "send" -> {
                    val value = args!![0]
                    if (value is String) sentText += value
                    true
                }
                "close" -> {
                    closedCode = args!![0] as Int
                    closedReason = args[1] as String?
                    true
                }
                "cancel" -> Unit
                "toString" -> "RecordingWebSocket"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === args!![0]
                else -> error("Unexpected WebSocket method: ${method.name}")
            }
        } as WebSocket

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            this.request = request
            this.listener = listener
            return socket
        }
    }
}
