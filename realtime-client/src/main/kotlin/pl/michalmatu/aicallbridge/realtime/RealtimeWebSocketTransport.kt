package pl.michalmatu.aicallbridge.realtime

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import pl.michalmatu.aicallbridge.audio.PcmFrame

/**
 * Transport-neutral Realtime lifecycle implemented over a text WebSocket seam.
 *
 * The connector owns only socket mechanics. Credential target validation, GA event encoding and
 * stale-callback protection stay here so changing the networking library cannot bypass them.
 */
class RealtimeWebSocketTransport(
    private val connector: RealtimeSocketConnector,
    private val handshakeFactory: RealtimeOpenAiWebSocketHandshakeFactory =
        RealtimeOpenAiWebSocketHandshakeFactory(),
    private val protocol: RealtimeWebSocketProtocol = RealtimeWebSocketProtocol(),
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val monotonicNs: () -> Long = System::nanoTime,
) : RealtimeTransport {
    private enum class State {
        IDLE,
        CONNECTING,
        ACTIVE,
    }

    private val lock = Any()
    private var generation = 0L
    private var state = State.IDLE
    private var activeSocket: RealtimeSocket? = null
    private var pendingConfig: RealtimeSessionConfig? = null
    private var pendingConnect: Continuation<Result<Unit>>? = null

    @Volatile
    private var listener: RealtimeTransport.Listener? = null

    override suspend fun connect(config: RealtimeSessionConfig): Result<Unit> =
        suspendCoroutine { continuation ->
            val expectedGeneration: Long
            synchronized(lock) {
                if (state != State.IDLE) {
                    continuation.resume(
                        Result.failure(IllegalStateException("Realtime transport is already active")),
                    )
                    return@suspendCoroutine
                }
                generation++
                expectedGeneration = generation
                state = State.CONNECTING
                pendingConfig = config
                pendingConnect = continuation
            }

            val handshake = try {
                handshakeFactory.create(config, epochSeconds())
            } catch (error: Throwable) {
                failConnecting(expectedGeneration, error)
                return@suspendCoroutine
            }

            try {
                connector.connect(handshake, SocketListener(expectedGeneration))
            } catch (error: Throwable) {
                failConnecting(expectedGeneration, error)
            }
        }

    override fun sendAudio(frame: PcmFrame): Result<Unit> =
        sendActive { socket -> socket.send(protocol.inputAudioAppend(frame)) }

    override fun cancelResponse(): Result<Unit> =
        sendActive { socket -> socket.send(protocol.responseCancel()) }

    override fun submitFunctionOutput(callId: String, outputJson: String): Result<Unit> =
        submitFunctionOutput(callId, outputJson, RealtimeFunctionFollowup.Auto)

    override fun submitFunctionOutput(
        callId: String,
        outputJson: String,
        followup: RealtimeFunctionFollowup,
    ): Result<Unit> =
        sendActive { socket ->
            socket.send(protocol.functionCallOutput(callId, outputJson)) &&
                socket.send(protocol.responseCreate(followup))
        }

    override fun close() {
        val socket: RealtimeSocket?
        val pending: Continuation<Result<Unit>>?
        synchronized(lock) {
            if (state == State.IDLE) {
                return
            }
            generation++
            socket = activeSocket
            activeSocket = null
            pending = pendingConnect
            pendingConnect = null
            pendingConfig = null
            state = State.IDLE
        }
        socket?.close(NORMAL_CLOSE_CODE, NORMAL_CLOSE_REASON)
        pending?.resume(Result.failure(IllegalStateException("Realtime transport closed during connect")))
    }

    override fun setListener(listener: RealtimeTransport.Listener?) {
        this.listener = listener
    }

    private fun handleOpen(expectedGeneration: Long, socket: RealtimeSocket) {
        val config: RealtimeSessionConfig
        synchronized(lock) {
            if (expectedGeneration != generation || state != State.CONNECTING) {
                socket.close(NORMAL_CLOSE_CODE, STALE_CLOSE_REASON)
                return
            }
            config = pendingConfig
                ?: run {
                    socket.close(NORMAL_CLOSE_CODE, STALE_CLOSE_REASON)
                    return
                }
        }

        val sessionUpdate = try {
            protocol.sessionUpdate(config)
        } catch (error: Throwable) {
            socket.close(NORMAL_CLOSE_CODE, "session update failed")
            failConnecting(expectedGeneration, error)
            return
        }
        if (!socket.send(sessionUpdate)) {
            socket.close(NORMAL_CLOSE_CODE, "session update rejected")
            failConnecting(
                expectedGeneration,
                IllegalStateException("Realtime socket rejected session.update"),
            )
            return
        }

        val continuation: Continuation<Result<Unit>>?
        synchronized(lock) {
            if (expectedGeneration != generation || state != State.CONNECTING) {
                socket.close(NORMAL_CLOSE_CODE, STALE_CLOSE_REASON)
                return
            }
            activeSocket = socket
            state = State.ACTIVE
            pendingConfig = null
            continuation = pendingConnect
            pendingConnect = null
        }
        continuation?.resume(Result.success(Unit))
    }

    private fun failConnecting(expectedGeneration: Long, error: Throwable) {
        val continuation: Continuation<Result<Unit>>?
        synchronized(lock) {
            if (expectedGeneration != generation || state != State.CONNECTING) {
                return
            }
            continuation = pendingConnect
            pendingConnect = null
            pendingConfig = null
            activeSocket = null
            state = State.IDLE
        }
        continuation?.resume(Result.failure(error))
    }

    private fun handleFailure(expectedGeneration: Long, error: Throwable) {
        val continuation: Continuation<Result<Unit>>?
        val notifyListener: Boolean
        synchronized(lock) {
            if (expectedGeneration != generation) {
                return
            }
            when (state) {
                State.CONNECTING -> {
                    continuation = pendingConnect
                    pendingConnect = null
                    pendingConfig = null
                    activeSocket = null
                    state = State.IDLE
                    notifyListener = false
                }
                State.ACTIVE -> {
                    continuation = null
                    activeSocket = null
                    state = State.IDLE
                    notifyListener = true
                }
                State.IDLE -> return
            }
        }
        continuation?.resume(Result.failure(error))
        if (notifyListener) {
            safeNotify { it.onError(error) }
        }
    }

    private fun handleClosed(expectedGeneration: Long) {
        synchronized(lock) {
            if (expectedGeneration != generation) {
                return
            }
            when (state) {
                State.CONNECTING -> {
                    // A close before open is a failed connection even if the connector did not
                    // provide a separate failure callback.
                }
                State.ACTIVE -> {
                    activeSocket = null
                    state = State.IDLE
                    return
                }
                State.IDLE -> return
            }
        }
        failConnecting(
            expectedGeneration,
            IllegalStateException("Realtime socket closed before connection completed"),
        )
    }

    private fun handleText(expectedGeneration: Long, text: String) {
        synchronized(lock) {
            if (expectedGeneration != generation || state != State.ACTIVE) {
                return
            }
        }
        val event = try {
            protocol.parseServerEvent(text, monotonicNs())
        } catch (error: Throwable) {
            safeNotify { it.onError(error) }
            return
        }

        when (event.type()) {
            RealtimeServerEvent.Type.AUDIO_DELTA ->
                event.audioFrame()?.let { frame -> safeNotify { it.onAudio(frame) } }
            RealtimeServerEvent.Type.REMOTE_SPEECH_STARTED ->
                safeNotify { it.onRemoteSpeechStarted() }
            RealtimeServerEvent.Type.REMOTE_SPEECH_STOPPED ->
                safeNotify { it.onRemoteSpeechStopped() }
            RealtimeServerEvent.Type.FUNCTION_CALL ->
                event.functionCall()?.let { call -> safeNotify { it.onFunctionCall(call) } }
            RealtimeServerEvent.Type.ERROR ->
                safeNotify {
                    it.onError(
                        IllegalStateException(event.errorMessage() ?: "Realtime server error"),
                    )
                }
            RealtimeServerEvent.Type.OTHER -> Unit
        }
    }

    private fun sendActive(action: (RealtimeSocket) -> Boolean): Result<Unit> {
        val socket = synchronized(lock) {
            if (state != State.ACTIVE) null else activeSocket
        } ?: return Result.failure(IllegalStateException("Realtime transport is not connected"))

        return try {
            if (action(socket)) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Realtime socket rejected outbound event"))
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun safeNotify(action: (RealtimeTransport.Listener) -> Unit) {
        val current = listener ?: return
        try {
            action(current)
        } catch (_: Throwable) {
            // Observers do not own transport cleanup or safety behavior.
        }
    }

    private inner class SocketListener(
        private val expectedGeneration: Long,
    ) : RealtimeSocketConnector.Listener {
        override fun onOpen(socket: RealtimeSocket) {
            handleOpen(expectedGeneration, socket)
        }

        override fun onText(text: String) {
            handleText(expectedGeneration, text)
        }

        override fun onFailure(error: Throwable) {
            handleFailure(expectedGeneration, error)
        }

        override fun onClosed(code: Int, reason: String) {
            handleClosed(expectedGeneration)
        }
    }

    private companion object {
        const val NORMAL_CLOSE_CODE = 1000
        const val NORMAL_CLOSE_REASON = "client close"
        const val STALE_CLOSE_REASON = "stale realtime generation"
    }
}
