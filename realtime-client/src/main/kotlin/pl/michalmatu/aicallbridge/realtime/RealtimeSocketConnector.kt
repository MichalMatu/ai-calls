package pl.michalmatu.aicallbridge.realtime

/** Starts one WebSocket handshake; callbacks may arrive on any thread. */
interface RealtimeSocketConnector {
    fun connect(handshake: RealtimeWebSocketHandshake, listener: Listener)

    interface Listener {
        fun onOpen(socket: RealtimeSocket)
        fun onText(text: String)
        fun onFailure(error: Throwable)
        fun onClosed(code: Int, reason: String)
    }
}
