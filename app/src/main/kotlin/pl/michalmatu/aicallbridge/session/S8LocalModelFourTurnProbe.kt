package pl.michalmatu.aicallbridge.session

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Four bounded, synthetic text turns against the *actual* app-owned local
 * LiteRT backend. Never creates a Call, SIP account, microphone stream or
 * network transport. Only counts accepted nonempty completions; synthetic
 * prompts and model responses are not stored or returned to the observer.
 *
 * Explicit STOP wins over all late model callbacks. The caller owns one
 * dedicated model executor and an independent timeout.
 */
internal class S8LocalModelFourTurnProbe(
    private val factory: () -> TextCallAgentBackend,
    private val worker: Executor,
    private val onDone: (Result) -> Unit,
) : AutoCloseable {
    internal enum class Status { PASS, FAILED, STOPPED }

    internal data class Result(val status: Status, val completedTurns: Int)

    private val started = AtomicBoolean()
    private val terminal = AtomicBoolean()
    private val turns = AtomicInteger()
    @Volatile private var model: TextCallAgentBackend? = null

    fun start() {
        check(started.compareAndSet(false, true)) { "s8_litert_probe_single_use" }
        try {
            worker.execute {
                if (terminal.get()) return@execute
                try {
                    val created = factory()
                    model = created
                    if (terminal.get()) {
                        try { created.close() } catch (_: Throwable) {}
                        return@execute
                    }
                    generate(1)
                } catch (_: Throwable) {
                    finish(Status.FAILED)
                }
            }
        } catch (_: RuntimeException) {
            finish(Status.FAILED)
        }
    }

    private fun generate(turn: Int) {
        if (terminal.get() || turns.get() != turn - 1) return
        val current = model ?: run { finish(Status.FAILED); return }
        try {
            current.generate(PROMPTS[turn - 1], object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    if (terminal.get()) return
                    if (text.isBlank() || text.length > MAX_RESPONSE_CHARS) {
                        finish(Status.FAILED)
                        return
                    }
                    if (!turns.compareAndSet(turn - 1, turn)) return
                    if (turn == PROMPTS.size) {
                        finish(Status.PASS)
                    } else {
                        try {
                            worker.execute { generate(turn + 1) }
                        } catch (_: RuntimeException) {
                            finish(Status.FAILED)
                        }
                    }
                }

                override fun onError(reason: String) {
                    if (!terminal.get() && turns.get() == turn - 1) {
                        finish(Status.FAILED)
                    }
                }
            })
        } catch (_: Throwable) {
            finish(Status.FAILED)
        }
    }

    private fun finish(status: Status) {
        if (!terminal.compareAndSet(false, true)) return
        // Native cancel can be slow. Never run it on the Android main thread
        // or on a model output callback.
        val old = model
        if (old != null) {
            try {
                worker.execute { try { old.close() } catch (_: Throwable) {} }
            } catch (_: RuntimeException) { /* Result already terminal. */ }
        }
        try { onDone(Result(status, turns.get())) } catch (_: Throwable) {}
    }

    override fun close() { finish(Status.STOPPED) }

    private companion object {
        const val MAX_RESPONSE_CHARS = 8_192
        val PROMPTS = arrayOf(
            "Odpowiedz krótko po polsku: czy jesteś gotowy do testu?",
            "Potwierdź jednym zdaniem, że ten test nie wykonuje połączenia telefonicznego.",
            "Podaj krótką polską poradę dotyczącą punktualności.",
            "Zakończ krótkim polskim pożegnaniem.",
        )
    }
}
