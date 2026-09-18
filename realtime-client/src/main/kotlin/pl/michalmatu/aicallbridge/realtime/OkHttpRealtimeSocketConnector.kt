package pl.michalmatu.aicallbridge.realtime

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** Thin OkHttp adapter for the already-validated Realtime WebSocket handshake. */
class OkHttpRealtimeSocketConnector(
    private val webSocketFactory: WebSocket.Factory = OkHttpClient(),
) : RealtimeSocketConnector {
    override fun connect(
        handshake: RealtimeWebSocketHandshake,
        listener: RealtimeSocketConnector.Listener,
    ) {
        val request = okhttp3.Request.Builder()
            .url(handshake.requestUrl())
            .header(
                SEC_WEBSOCKET_PROTOCOL,
                handshake.protocols().joinToString(", "),
            )
            .build()

        webSocketFactory.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen(OkHttpRealtimeSocket(webSocket))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onText(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    webSocket.close(UNSUPPORTED_DATA_CLOSE_CODE, UNSUPPORTED_BINARY_REASON)
                    listener.onFailure(
                        IllegalStateException("Realtime WebSocket received unsupported binary frame"),
                    )
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed(code, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(t)
                }
            },
        )
    }

    private class OkHttpRealtimeSocket(
        private val webSocket: WebSocket,
    ) : RealtimeSocket {
        override fun send(text: String): Boolean = webSocket.send(text)

        override fun close(code: Int, reason: String): Boolean = webSocket.close(code, reason)
    }

    private companion object {
        const val SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol"
        const val UNSUPPORTED_DATA_CLOSE_CODE = 1003
        const val UNSUPPORTED_BINARY_REASON = "binary frames unsupported"
    }
}
