package pl.michalmatu.aicallbridge.realtime

import okhttp3.OkHttpClient
import okhttp3.WebSocket

/**
 * Creates one fresh stateful Realtime transport per session generation.
 *
 * The underlying OkHttp/WebSocket factory may be shared for connection pooling and thread reuse,
 * but RealtimeWebSocketTransport itself is never reused across generations. This keeps stale socket
 * callbacks and transport state confined to the generation that created them.
 */
class OkHttpRealtimeTransportFactory(
    private val webSocketFactory: WebSocket.Factory = OkHttpClient(),
) {
    fun create(): RealtimeTransport =
        RealtimeWebSocketTransport(
            connector = OkHttpRealtimeSocketConnector(webSocketFactory),
        )
}
