package pl.michalmatu.aicallbridge.session

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionFollowup
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionTool
import pl.michalmatu.aicallbridge.realtime.RealtimeSessionConfig
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

enum class CallRealtimeSessionOrchestratorState {
    IDLE,
    FETCHING_CREDENTIAL,
    CONNECTING_REALTIME,
    STARTING_MEDIA,
    ACTIVE,
    STOPPING,
    TAKEN_OVER,
    FAILED,
}

data class CallRealtimeSessionRequest(
    val sessionEndpoint: String,
    val model: String,
    val instructions: String,
    val sampleRateHz: Int = 16_000,
    val tools: List<RealtimeFunctionTool> = emptyList(),
) {
    init {
        require(sessionEndpoint.isNotBlank()) { "sessionEndpoint must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(instructions.isNotBlank()) { "instructions must not be blank" }
        require(sampleRateHz > 0) { "sampleRateHz must be > 0" }
        require(tools.map { it.name }.distinct().size == tools.size) {
            "Realtime function tool names must be unique"
        }
    }
}

data class CallRealtimeSessionOrchestratorSnapshot(
    val generation: Long,
    val state: CallRealtimeSessionOrchestratorState,
    val mediaGeneration: Long?,
    val failureReason: String?,
)

/**
 * Coordinates one Realtime transport generation with one privileged telephony-media generation.
 *
 * Realtime is connected before privileged media starts, so an unavailable network/model path does
 * not leave RX/TX pipes open without an active consumer. Local TAKE OVER always reaches the media
 * coordinator before any remote Realtime cleanup once privileged media has started.
 */
class CallRealtimeSessionOrchestrator(
    private val coordinator: CallMediaSessionCoordinator,
    private val credentialProvider: RealtimeCredentialProvider,
    private val transportFactory: () -> RealtimeTransport,
    private val bootstrapExecutor: Executor,
    private val listener: (CallRealtimeSessionOrchestratorSnapshot) -> Unit = {},
    private val functionCallHandler: CallRealtimeFunctionCallHandler? = null,
) : AutoCloseable {
    private val lock = Any()

    private var generation = 0L
    private var state = CallRealtimeSessionOrchestratorState.IDLE
    private var mediaGeneration: Long? = null
    private var failureReason: String? = null
    private var transport: RealtimeTransport? = null
    private var mediaSession: CallRealtimeMediaSession? = null
    private var coordinatorSubscription: AutoCloseable? = null
    private val pendingFunctionCalls = mutableSetOf<String>()
    private var closed = false

    fun start(request: CallRealtimeSessionRequest): Long {
        val expectedGeneration: Long
        val fetchingSnapshot: CallRealtimeSessionOrchestratorSnapshot
        synchronized(lock) {
            check(!closed) { "Realtime session orchestrator is closed" }
            check(
                state == CallRealtimeSessionOrchestratorState.IDLE ||
                    state == CallRealtimeSessionOrchestratorState.TAKEN_OVER ||
                    state == CallRealtimeSessionOrchestratorState.FAILED,
            ) { "cannot start Realtime session from $state" }
            check(transport == null && mediaSession == null && coordinatorSubscription == null) {
                "previous Realtime session resources are still attached"
            }

            generation++
            expectedGeneration = generation
            state = CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL
            mediaGeneration = null
            failureReason = null
            pendingFunctionCalls.clear()
            fetchingSnapshot = snapshotLocked()
        }
        publish(fetchingSnapshot)

        val candidateTransport = try {
            transportFactory()
        } catch (error: Throwable) {
            failBeforeMedia(expectedGeneration, null, error)
            return expectedGeneration
        }

        val accepted = synchronized(lock) {
            if (isCurrentLocked(expectedGeneration, CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL)) {
                transport = candidateTransport
                true
            } else {
                false
            }
        }
        if (!accepted) {
            closeTransportBestEffort(candidateTransport)
            return expectedGeneration
        }

        try {
            bootstrapExecutor.execute {
                val bootstrap: suspend () -> Unit = {
                    runBootstrap(expectedGeneration, request, candidateTransport)
                }
                bootstrap.startCoroutine(
                    object : Continuation<Unit> {
                        override val context = EmptyCoroutineContext

                        override fun resumeWith(result: Result<Unit>) {
                            result.exceptionOrNull()?.let { error ->
                                failBootstrap(expectedGeneration, candidateTransport, error)
                            }
                        }
                    },
                )
            }
        } catch (error: Throwable) {
            failBeforeMedia(expectedGeneration, candidateTransport, error)
        }
        return expectedGeneration
    }

    fun snapshot(): CallRealtimeSessionOrchestratorSnapshot =
        synchronized(lock) { snapshotLocked() }

    /**
     * Immediate user takeover. If privileged media exists, local coordinator cleanup happens before
     * Realtime cancel/close. Pending credential/connect completions are invalidated by state checks.
     */
    fun takeOverNow() {
        val stoppingSnapshot: CallRealtimeSessionOrchestratorSnapshot
        val currentSession: CallRealtimeMediaSession?
        val currentTransport: RealtimeTransport?
        val subscription: AutoCloseable?
        val mayHavePrivilegedMedia: Boolean

        synchronized(lock) {
            if (
                state == CallRealtimeSessionOrchestratorState.IDLE ||
                state == CallRealtimeSessionOrchestratorState.TAKEN_OVER ||
                state == CallRealtimeSessionOrchestratorState.FAILED ||
                state == CallRealtimeSessionOrchestratorState.STOPPING
            ) {
                return
            }
            state = CallRealtimeSessionOrchestratorState.STOPPING
            stoppingSnapshot = snapshotLocked()
            currentSession = mediaSession
            currentTransport = transport
            subscription = coordinatorSubscription
            coordinatorSubscription = null
            mayHavePrivilegedMedia = mediaGeneration != null ||
                coordinator.snapshot().state != CallMediaSessionState.IDLE
        }
        publish(stoppingSnapshot)
        closeQuietly(subscription)

        if (currentSession != null) {
            currentSession.takeOverNow()
        } else {
            if (mayHavePrivilegedMedia) {
                coordinator.takeOverNow()
            }
            cleanupRealtimeBestEffort(currentTransport)
        }

        val terminalSnapshot = synchronized(lock) {
            if (state != CallRealtimeSessionOrchestratorState.STOPPING) {
                null
            } else {
                mediaSession = null
                transport = null
                mediaGeneration = null
                pendingFunctionCalls.clear()
                failureReason = null
                state = CallRealtimeSessionOrchestratorState.TAKEN_OVER
                snapshotLocked()
            }
        }
        publish(terminalSnapshot)
    }

    override fun close() {
        val shouldTakeOver = synchronized(lock) {
            if (closed) {
                false
            } else {
                closed = true
                state != CallRealtimeSessionOrchestratorState.IDLE &&
                    state != CallRealtimeSessionOrchestratorState.TAKEN_OVER &&
                    state != CallRealtimeSessionOrchestratorState.FAILED
            }
        }
        if (shouldTakeOver) {
            takeOverNowAfterCloseFlag()
        } else {
            val resources = synchronized(lock) {
                val values = Triple(mediaSession, transport, coordinatorSubscription)
                mediaSession = null
                transport = null
                coordinatorSubscription = null
                pendingFunctionCalls.clear()
                values
            }
            closeQuietly(resources.third)
            resources.first?.takeOverNow()
            if (resources.first == null) cleanupRealtimeBestEffort(resources.second)
        }
    }

    private suspend fun runBootstrap(
        expectedGeneration: Long,
        request: CallRealtimeSessionRequest,
        expectedTransport: RealtimeTransport,
    ) {
        val secret = credentialProvider.fetchClientSecret().getOrElse { throw it }
        if (!transition(
                expectedGeneration,
                CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL,
                CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME,
            )
        ) {
            closeTransportBestEffort(expectedTransport)
            return
        }

        val connectResult = expectedTransport.connect(
            RealtimeSessionConfig(
                sessionEndpoint = request.sessionEndpoint,
                clientSecret = secret,
                model = request.model,
                instructions = request.instructions,
                tools = request.tools,
            ),
        )
        connectResult.getOrElse { throw it }

        if (!transition(
                expectedGeneration,
                CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME,
                CallRealtimeSessionOrchestratorState.STARTING_MEDIA,
            )
        ) {
            closeTransportBestEffort(expectedTransport)
            return
        }

        startPrivilegedMedia(expectedGeneration, request, expectedTransport)
    }

    private fun startPrivilegedMedia(
        expectedGeneration: Long,
        request: CallRealtimeSessionRequest,
        expectedTransport: RealtimeTransport,
    ) {
        val subscription = coordinator.addListener { snapshot ->
            handleCoordinatorSnapshot(expectedGeneration, expectedTransport, snapshot)
        }
        val keepSubscription = synchronized(lock) {
            if (isCurrentLocked(expectedGeneration, CallRealtimeSessionOrchestratorState.STARTING_MEDIA) &&
                transport === expectedTransport
            ) {
                coordinatorSubscription = subscription
                true
            } else {
                false
            }
        }
        if (!keepSubscription) {
            closeQuietly(subscription)
            closeTransportBestEffort(expectedTransport)
            return
        }

        val startedGeneration = try {
            coordinator.start(request.sampleRateHz)
        } catch (error: Throwable) {
            failBootstrap(expectedGeneration, expectedTransport, error)
            return
        }

        synchronized(lock) {
            if (
                expectedGeneration == generation &&
                (state == CallRealtimeSessionOrchestratorState.STARTING_MEDIA ||
                    state == CallRealtimeSessionOrchestratorState.ACTIVE) &&
                transport === expectedTransport
            ) {
                mediaGeneration = startedGeneration
            }
        }
    }

    private fun handleCoordinatorSnapshot(
        expectedGeneration: Long,
        expectedTransport: RealtimeTransport,
        snapshot: CallMediaSessionSnapshot,
    ) {
        when (snapshot.state) {
            CallMediaSessionState.ACTIVE -> attachActiveMedia(
                expectedGeneration,
                expectedTransport,
                snapshot.generation,
            )
            CallMediaSessionState.FAILED -> handleCoordinatorFailure(
                expectedGeneration,
                expectedTransport,
                snapshot,
            )
            else -> Unit
        }
    }

    private fun attachActiveMedia(
        expectedGeneration: Long,
        expectedTransport: RealtimeTransport,
        activeMediaGeneration: Long,
    ) {
        synchronized(lock) {
            if (!isCurrentLocked(expectedGeneration, CallRealtimeSessionOrchestratorState.STARTING_MEDIA) ||
                transport !== expectedTransport
            ) {
                return
            }
            mediaGeneration = activeMediaGeneration
        }

        val candidateSession = try {
            CallRealtimeMediaSession(
                coordinator = coordinator,
                generation = activeMediaGeneration,
                transport = expectedTransport,
                onTerminalState = { terminal ->
                    handleMediaTerminal(expectedGeneration, candidateTransport = expectedTransport, terminal)
                },
                onFunctionCall = { call ->
                    handleFunctionCall(expectedGeneration, expectedTransport, call)
                },
            )
        } catch (error: Throwable) {
            failBootstrap(expectedGeneration, expectedTransport, error)
            return
        }

        val accepted = synchronized(lock) {
            if (isCurrentLocked(expectedGeneration, CallRealtimeSessionOrchestratorState.STARTING_MEDIA) &&
                transport === expectedTransport
            ) {
                mediaSession = candidateSession
                true
            } else {
                false
            }
        }
        if (!accepted) {
            candidateSession.takeOverNow()
            return
        }

        try {
            candidateSession.start()
        } catch (error: Throwable) {
            failBootstrap(expectedGeneration, expectedTransport, error)
            return
        }

        val activeSnapshot = synchronized(lock) {
            if (isCurrentLocked(expectedGeneration, CallRealtimeSessionOrchestratorState.STARTING_MEDIA) &&
                mediaSession === candidateSession
            ) {
                state = CallRealtimeSessionOrchestratorState.ACTIVE
                snapshotLocked()
            } else {
                null
            }
        }
        publish(activeSnapshot)
    }

    private fun handleCoordinatorFailure(
        expectedGeneration: Long,
        expectedTransport: RealtimeTransport,
        snapshot: CallMediaSessionSnapshot,
    ) {
        val error = IllegalStateException(
            snapshot.failureDetail ?: "call media failed: ${snapshot.failure}",
        )
        failAfterMediaCoordinatorCleanup(expectedGeneration, expectedTransport, error)
    }

    private fun handleMediaTerminal(
        expectedGeneration: Long,
        candidateTransport: RealtimeTransport,
        terminal: CallRealtimeMediaSessionSnapshot,
    ) {
        val terminalSnapshot: CallRealtimeSessionOrchestratorSnapshot?
        val subscription: AutoCloseable?
        synchronized(lock) {
            if (expectedGeneration != generation || transport !== candidateTransport) {
                return
            }
            if (
                state != CallRealtimeSessionOrchestratorState.ACTIVE &&
                state != CallRealtimeSessionOrchestratorState.STARTING_MEDIA
            ) {
                return
            }

            subscription = coordinatorSubscription
            coordinatorSubscription = null
            mediaSession = null
            transport = null
            mediaGeneration = null
            pendingFunctionCalls.clear()
            when (terminal.state) {
                CallRealtimeMediaSessionState.FAILED -> {
                    failureReason = terminal.failureReason ?: "Realtime media session failed"
                    state = CallRealtimeSessionOrchestratorState.FAILED
                }
                CallRealtimeMediaSessionState.TAKEN_OVER -> {
                    failureReason = null
                    state = CallRealtimeSessionOrchestratorState.TAKEN_OVER
                }
                else -> return
            }
            terminalSnapshot = snapshotLocked()
        }
        closeQuietly(subscription)
        publish(terminalSnapshot)
    }

    private fun handleFunctionCall(
        expectedGeneration: Long,
        expectedTransport: RealtimeTransport,
        call: RealtimeFunctionCall,
    ) {
        var duplicate = false
        val accepted = synchronized(lock) {
            if (
                expectedGeneration != generation ||
                transport !== expectedTransport ||
                (state != CallRealtimeSessionOrchestratorState.STARTING_MEDIA &&
                    state != CallRealtimeSessionOrchestratorState.ACTIVE)
            ) {
                false
            } else if (!pendingFunctionCalls.add(call.callId)) {
                duplicate = true
                false
            } else {
                true
            }
        }
        if (!accepted) {
            if (duplicate) {
                failWithPossibleMedia(
                    expectedGeneration,
                    expectedTransport,
                    IllegalStateException("duplicate Realtime function call id"),
                )
            }
            return
        }

        val handler = functionCallHandler
        if (handler == null) {
            synchronized(lock) { pendingFunctionCalls.remove(call.callId) }
            failWithPossibleMedia(
                expectedGeneration,
                expectedTransport,
                IllegalStateException("Realtime function call received without an app handler"),
            )
            return
        }

        val responder = GenerationBoundFunctionResponder(
            expectedGeneration,
            expectedTransport,
            call.callId,
        )
        try {
            handler.onFunctionCall(call, responder)
        } catch (error: Throwable) {
            synchronized(lock) { pendingFunctionCalls.remove(call.callId) }
            failWithPossibleMedia(expectedGeneration, expectedTransport, error)
        }
    }

    private inner class GenerationBoundFunctionResponder(
        private val expectedGeneration: Long,
        private val expectedTransport: RealtimeTransport,
        private val callId: String,
    ) : CallRealtimeFunctionResponder {
        private val used = AtomicBoolean(false)

        override fun submit(outputJson: String): Result<Unit> =
            submit(outputJson, RealtimeFunctionFollowup.Auto)

        override fun submit(
            outputJson: String,
            followup: RealtimeFunctionFollowup,
        ): Result<Unit> {
            if (outputJson.isBlank()) {
                return Result.failure(IllegalArgumentException("function output must not be blank"))
            }
            if (!used.compareAndSet(false, true)) {
                return Result.failure(IllegalStateException("Realtime function responder already used"))
            }

            var transportFailure: Throwable? = null
            val result = synchronized(lock) {
                if (
                    expectedGeneration != generation ||
                    transport !== expectedTransport ||
                    (state != CallRealtimeSessionOrchestratorState.STARTING_MEDIA &&
                        state != CallRealtimeSessionOrchestratorState.ACTIVE) ||
                    !pendingFunctionCalls.remove(callId)
                ) {
                    Result.failure(IllegalStateException("Realtime function responder is stale"))
                } else {
                    val submitted = try {
                        expectedTransport.submitFunctionOutput(callId, outputJson, followup)
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    transportFailure = submitted.exceptionOrNull()
                    submitted
                }
            }

            transportFailure?.let { error ->
                failWithPossibleMedia(expectedGeneration, expectedTransport, error)
            }
            return result
        }
    }

    private fun failBootstrap(
        expectedGeneration: Long,
        expectedTransport: RealtimeTransport,
        error: Throwable,
    ) {
        val hasMedia = synchronized(lock) {
            if (expectedGeneration != generation || transport !== expectedTransport) {
                return
            }
            state == CallRealtimeSessionOrchestratorState.STARTING_MEDIA ||
                state == CallRealtimeSessionOrchestratorState.ACTIVE ||
                mediaGeneration != null
        }
        if (hasMedia) {
            failWithPossibleMedia(expectedGeneration, expectedTransport, error)
        } else {
            failBeforeMedia(expectedGeneration, expectedTransport, error)
        }
    }

    private fun failBeforeMedia(
        expectedGeneration: Long,
        expectedTransport: RealtimeTransport?,
        error: Throwable,
    ) {
        val subscription: AutoCloseable?
        val failedSnapshot = synchronized(lock) {
            if (expectedGeneration != generation || isTerminalLocked()) {
                return
            }
            if (expectedTransport != null && transport !== expectedTransport) {
                return
            }
            subscription = coordinatorSubscription
            coordinatorSubscription = null
            mediaSession = null
            transport = null
            mediaGeneration = null
            pendingFunctionCalls.clear()
            failureReason = describe(error)
            state = CallRealtimeSessionOrchestratorState.FAILED
            snapshotLocked()
        }
        closeQuietly(subscription)
        closeTransportBestEffort(expectedTransport)
        publish(failedSnapshot)
    }

    private fun failWithPossibleMedia(
        expectedGeneration: Long,
        expectedTransport: RealtimeTransport,
        error: Throwable,
    ) {
        val currentSession: CallRealtimeMediaSession?
        val subscription: AutoCloseable?
        val failedSnapshot = synchronized(lock) {
            if (expectedGeneration != generation || isTerminalLocked() || transport !== expectedTransport) {
                return
            }
            state = CallRealtimeSessionOrchestratorState.STOPPING
            failureReason = describe(error)
            currentSession = mediaSession
            subscription = coordinatorSubscription
            coordinatorSubscription = null
            snapshotLocked()
        }
        publish(failedSnapshot)
        closeQuietly(subscription)

        if (currentSession != null) {
            currentSession.takeOverNow()
        } else {
            coordinator.takeOverNow()
            cleanupRealtimeBestEffort(expectedTransport)
        }

        val terminal = synchronized(lock) {
            if (expectedGeneration != generation || state != CallRealtimeSessionOrchestratorState.STOPPING) {
                null
            } else {
                mediaSession = null
                transport = null
                mediaGeneration = null
                pendingFunctionCalls.clear()
                state = CallRealtimeSessionOrchestratorState.FAILED
                snapshotLocked()
            }
        }
        publish(terminal)
    }

    private fun failAfterMediaCoordinatorCleanup(
        expectedGeneration: Long,
        expectedTransport: RealtimeTransport,
        error: Throwable,
    ) {
        val currentSession: CallRealtimeMediaSession?
        val subscription: AutoCloseable?
        val failedSnapshot = synchronized(lock) {
            if (expectedGeneration != generation || isTerminalLocked() || transport !== expectedTransport) {
                return
            }
            currentSession = mediaSession
            subscription = coordinatorSubscription
            coordinatorSubscription = null
            mediaSession = null
            transport = null
            mediaGeneration = null
            pendingFunctionCalls.clear()
            failureReason = describe(error)
            state = CallRealtimeSessionOrchestratorState.FAILED
            snapshotLocked()
        }
        closeQuietly(subscription)
        if (currentSession != null) {
            currentSession.takeOverNow()
        } else {
            coordinator.takeOverNow()
            cleanupRealtimeBestEffort(expectedTransport)
        }
        publish(failedSnapshot)
    }

    private fun transition(
        expectedGeneration: Long,
        expectedState: CallRealtimeSessionOrchestratorState,
        nextState: CallRealtimeSessionOrchestratorState,
    ): Boolean {
        val nextSnapshot = synchronized(lock) {
            if (!isCurrentLocked(expectedGeneration, expectedState)) {
                return false
            }
            state = nextState
            snapshotLocked()
        }
        publish(nextSnapshot)
        return true
    }

    private fun takeOverNowAfterCloseFlag() {
        val stoppingSnapshot: CallRealtimeSessionOrchestratorSnapshot
        val currentSession: CallRealtimeMediaSession?
        val currentTransport: RealtimeTransport?
        val subscription: AutoCloseable?
        val mayHavePrivilegedMedia: Boolean
        synchronized(lock) {
            if (
                state == CallRealtimeSessionOrchestratorState.IDLE ||
                state == CallRealtimeSessionOrchestratorState.TAKEN_OVER ||
                state == CallRealtimeSessionOrchestratorState.FAILED ||
                state == CallRealtimeSessionOrchestratorState.STOPPING
            ) {
                return
            }
            state = CallRealtimeSessionOrchestratorState.STOPPING
            stoppingSnapshot = snapshotLocked()
            currentSession = mediaSession
            currentTransport = transport
            subscription = coordinatorSubscription
            coordinatorSubscription = null
            mayHavePrivilegedMedia = mediaGeneration != null ||
                coordinator.snapshot().state != CallMediaSessionState.IDLE
        }
        publish(stoppingSnapshot)
        closeQuietly(subscription)
        if (currentSession != null) {
            currentSession.takeOverNow()
        } else {
            if (mayHavePrivilegedMedia) coordinator.takeOverNow()
            cleanupRealtimeBestEffort(currentTransport)
        }
        val terminal = synchronized(lock) {
            mediaSession = null
            transport = null
            mediaGeneration = null
            pendingFunctionCalls.clear()
            failureReason = null
            state = CallRealtimeSessionOrchestratorState.TAKEN_OVER
            snapshotLocked()
        }
        publish(terminal)
    }

    private fun isCurrentLocked(
        expectedGeneration: Long,
        expectedState: CallRealtimeSessionOrchestratorState,
    ): Boolean = generation == expectedGeneration && state == expectedState

    private fun isTerminalLocked(): Boolean =
        state == CallRealtimeSessionOrchestratorState.IDLE ||
            state == CallRealtimeSessionOrchestratorState.TAKEN_OVER ||
            state == CallRealtimeSessionOrchestratorState.FAILED ||
            state == CallRealtimeSessionOrchestratorState.STOPPING

    private fun snapshotLocked(): CallRealtimeSessionOrchestratorSnapshot =
        CallRealtimeSessionOrchestratorSnapshot(
            generation = generation,
            state = state,
            mediaGeneration = mediaGeneration,
            failureReason = failureReason,
        )

    private fun publish(snapshot: CallRealtimeSessionOrchestratorSnapshot?) {
        if (snapshot == null) return
        try {
            listener(snapshot)
        } catch (_: Throwable) {
            // Observers do not own telephony or Realtime safety cleanup.
        }
    }

    private fun cleanupRealtimeBestEffort(value: RealtimeTransport?) {
        if (value == null) return
        try {
            value.cancelResponse()
        } catch (_: Throwable) {
        }
        closeTransportBestEffort(value)
    }

    private fun closeTransportBestEffort(value: RealtimeTransport?) {
        if (value == null) return
        try {
            value.close()
        } catch (_: Throwable) {
        }
    }

    private fun closeQuietly(value: AutoCloseable?) {
        if (value == null) return
        try {
            value.close()
        } catch (_: Throwable) {
        }
    }

    private fun describe(error: Throwable): String {
        val type = error.javaClass.simpleName
        val message = error.message?.replace('\n', ' ')?.replace('\r', ' ')
        return if (message.isNullOrBlank()) type else "$type:$message"
    }
}
