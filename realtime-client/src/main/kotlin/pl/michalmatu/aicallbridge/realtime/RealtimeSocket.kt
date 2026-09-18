package pl.michalmatu.aicallbridge.realtime

/** Minimal text WebSocket surface owned by the Realtime transport. */
interface RealtimeSocket {
    fun send(text: String): Boolean
    fun close(code: Int, reason: String): Boolean
}
