package pl.michalmatu.aicallbridge.developerrelay

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Developer-only TextCallAgentBackend that waits for a human/ChatGPT response delivered by ADB.
 *
 * It owns no networking and no telephony behavior. The Android side only publishes a request into
 * an app-private mailbox and waits for an exact session/turn response. Timeout/cancel fail closed.
 */
internal class InteractiveChatRelayBackend(
    private val mailbox: ChatRelayMailbox,
    private val sessionId: String,
    private val responseTimeoutMs: Long = DEFAULT_RESPONSE_TIMEOUT_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : TextCallAgentBackend {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "InteractiveChatRelayBackend").apply { isDaemon = true }
    }
    private val generation = AtomicLong(0L)
    private val turn = AtomicLong(0L)
    private val lock = Any()
    private var activeFuture: Future<*>? = null
    private var closed = false

    init {
        ChatRelayEnvelope(sessionId, 1, "probe").validate()
        require(responseTimeoutMs in 1..MAX_RESPONSE_TIMEOUT_MS) { "invalid_relay_timeout" }
        require(pollIntervalMs in 1..responseTimeoutMs) { "invalid_relay_poll_interval" }
    }

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        require(userText.length <= ChatRelayMailbox.MAX_REQUEST_CHARS) { "relay_request_too_long" }

        val generationId: Long
        val turnId: Long
        synchronized(lock) {
            check(!closed) { "relay_backend_closed" }
            cancelLocked()
            generationId = generation.incrementAndGet()
            turnId = turn.incrementAndGet()
            mailbox.publishRequest(ChatRelayEnvelope(sessionId, turnId, userText))
            activeFuture = executor.submit {
                waitForResponse(generationId, turnId, listener)
            }
        }
    }

    override fun cancel() {
        synchronized(lock) {
            generation.incrementAndGet()
            cancelLocked()
            mailbox.clear()
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation.incrementAndGet()
            cancelLocked()
            mailbox.clear()
        }
        executor.shutdownNow()
        try { executor.awaitTermination(250, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun waitForResponse(
        generationId: Long,
        turnId: Long,
        listener: TextCallAgentBackend.Listener,
    ) {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(responseTimeoutMs)
        while (isCurrent(generationId) && System.nanoTime() < deadlineNanos) {
            val response = mailbox.takeMatchingResponse(sessionId, turnId)
            if (response != null) {
                if (!isCurrent(generationId)) return
                if (response.text.length > ChatRelayMailbox.MAX_RESPONSE_CHARS) {
                    finishWithError(generationId, listener, "relay_response_too_long")
                    return
                }
                finishGeneration(generationId)
                listener.onComplete(response.text)
                return
            }
            try {
                Thread.sleep(pollIntervalMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
        if (isCurrent(generationId)) {
            finishWithError(generationId, listener, "relay_timeout")
        }
    }

    private fun isCurrent(generationId: Long): Boolean =
        !closed && generation.get() == generationId

    private fun finishWithError(
        generationId: Long,
        listener: TextCallAgentBackend.Listener,
        reason: String,
    ) {
        if (!finishGeneration(generationId)) return
        listener.onError(reason)
    }

    private fun finishGeneration(generationId: Long): Boolean {
        synchronized(lock) {
            if (!isCurrent(generationId)) return false
            generation.incrementAndGet()
            activeFuture = null
            mailbox.clear()
            return true
        }
    }

    private fun cancelLocked() {
        activeFuture?.cancel(true)
        activeFuture = null
    }

    private companion object {
        const val DEFAULT_RESPONSE_TIMEOUT_MS = 90_000L
        const val DEFAULT_POLL_INTERVAL_MS = 100L
        const val MAX_RESPONSE_TIMEOUT_MS = 180_000L
    }
}
