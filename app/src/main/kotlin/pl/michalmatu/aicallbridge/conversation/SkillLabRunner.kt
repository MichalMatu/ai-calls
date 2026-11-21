package pl.michalmatu.aicallbridge.conversation

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Model configuration is prepared before allocating a backend for one dialogue. */
internal fun interface SkillLabModelSession {
    fun createBackend(): TextCallAgentBackend
}

internal fun interface SkillLabModelSessionFactory {
    fun open(presetId: String): SkillLabModelSession
}

internal enum class SkillLabPhase { READY, STARTING, INPUT_READY, WAITING, COMPLETED, STOPPED, FAILED }

internal data class SkillLabPendingTurn(
    val storyIndex: Int,
    val storyTitle: String,
    val turnIndex: Int,
    val utterance: String,
)

internal data class SkillLabCompletedTurn(
    val pending: SkillLabPendingTurn,
    val observation: SkillLabObservation,
)

internal data class LabAdaptiveTrace(val turn: Int, val ruleNumber: Int, val condition: String, val utterance: String)

/** Snapshot of a single synthetic dialogue. */
internal data class SkillLabUiState(
    val phase: SkillLabPhase = SkillLabPhase.READY,
    val totalTurns: Int = 0,
    val completed: List<SkillLabCompletedTurn> = emptyList(),
    val pending: SkillLabPendingTurn? = null,
    val error: String? = null,
    val manualStartAllowed: Boolean = true,
    val adaptive: Boolean = false,
    val adaptiveTrace: List<LabAdaptiveTrace> = emptyList(),
)

/**
 * Sole owner of synthetic/off-call Skill Lab worker, model and terminal callbacks.
 * Records END_CALL proposals for review. The runner has no tool executor or Telecom authority.
 *
 * Generation + revision fencing drops old or overtaken UI deliveries even when
 * STOP, timeout or close races with a synchronous/delayed backend callback.
 */
internal class SkillLabRunner(
    private val sessions: SkillLabModelSessionFactory,
    private val dispatchToUi: (() -> Unit) -> Unit,
    private val onState: (SkillLabUiState) -> Unit,
    private val timeoutMillis: Long = TimeUnit.SECONDS.toMillis(120),
) : AutoCloseable {
    init { require(timeoutMillis > 0) }

    private class Job(val interactor: LabAdaptiveInteractor?) {
        val inputs = LinkedBlockingQueue<String>()
        val stopped = AtomicBoolean(false)
        var backend: TextCallAgentBackend? = null
        lateinit var thread: Thread
    }

    private val mutex = Any()
    private var generation = 0L
    private var revision = 0L
    private var current: Job? = null
    private var closed = false

    @Volatile var state = SkillLabUiState()
        private set

    fun startManual(presetId: String): Boolean = startDialogue(presetId, null)

    fun startAdaptive(presetId: String, test: LabCaseRevision): Boolean =
        startDialogue(presetId, LabAdaptiveInteractor(test.validated()))

    private fun startDialogue(presetId: String, interactor: LabAdaptiveInteractor?): Boolean {
        val job = Job(interactor)
        val snapshot: SkillLabUiState
        val token: Long
        val version: Long
        synchronized(mutex) {
            if (closed || current != null) return false
            generation++
            current = job
            job.thread = Thread({ execute(job, presetId) }, "aicalls-lab-dialogue")
            snapshot = SkillLabUiState(phase = SkillLabPhase.STARTING,
                manualStartAllowed = false, adaptive = interactor != null)
            state = snapshot
            token = generation
            version = ++revision
        }
        publish(token, version, snapshot)
        try {
            job.thread.start()
        } catch (error: RuntimeException) {
            change(job) { it.copy(phase = SkillLabPhase.FAILED,
                error = error.javaClass.simpleName, pending = null) }
            finish(job)
        }
        return true
    }

    /** Nonblocking submit; refuse overlapping generations and blank messages. */
    fun submitManual(text: String): Boolean {
        val job = synchronized(mutex) { current } ?: return false
        if (job.interactor != null) return false
        return submit(job, text)
    }

    private fun submit(job: Job, text: String): Boolean {
        if (text.isBlank()) return false
        val snapshot: SkillLabUiState
        val token: Long
        val version: Long
        synchronized(mutex) {
            if (closed || current !== job || job.stopped.get() ||
                state.phase != SkillLabPhase.INPUT_READY) return false
            val pending = SkillLabPendingTurn(0, "Dialog", state.completed.size + 1, text.trim())
            snapshot = state.copy(phase = SkillLabPhase.WAITING, pending = pending,
                totalTurns = pending.turnIndex)
            state = snapshot
            token = generation
            version = ++revision
            job.inputs.offer(text.trim())
        }
        publish(token, version, snapshot)
        return true
    }

    private fun execute(job: Job, presetId: String) {
        val completed = mutableListOf<SkillLabCompletedTurn>()
        try {
            val interactor = job.interactor
            val session = sessions.open(presetId)
            if (!active(job)) return
            val backend = session.createBackend()
            val accepted = synchronized(mutex) {
                if (!closed && current === job && !job.stopped.get()) {
                    job.backend = backend
                    true
                } else false
            }
            try {
                if (!accepted) return
                change(job) { it.copy(phase = SkillLabPhase.INPUT_READY) }
                if (interactor != null) submit(job, interactor.firstMessage())
                while (active(job)) {
                    val utterance = try { job.inputs.take() }
                        catch (_: InterruptedException) { break }
                    if (!active(job)) break
                    val pending = synchronized(mutex) { state.pending } ?: continue
                    val started = System.nanoTime()
                    val raw = awaitResponse(job, backend, utterance)
                    if (!active(job)) break
                    val reply = ConversationReplyProtocol.decode(raw)
                    val toolReview = LabToolProposalPolicy.validate(
                        reply, setOf(ConversationToolId.END_CALL))
                    val observation = SkillLabObservation("Dialog", pending.turnIndex,
                        actualEnd = toolReview.acceptedProposal == ConversationToolId.END_CALL,
                        reply = reply.text,
                        elapsedMs = (System.nanoTime() - started) / 1_000_000L)
                    completed.add(SkillLabCompletedTurn(pending, observation))
                    val immutable = Collections.unmodifiableList(ArrayList(completed))
                    val decision = interactor?.next(reply.text, observation.actualEnd)
                    val trace = decision?.let { LabAdaptiveTrace(pending.turnIndex,
                        it.ruleNumber, it.condition, it.utterance) }
                    val finished = interactor != null && decision == null
                    change(job) { prior ->
                        prior.copy(phase = if (finished) SkillLabPhase.WAITING
                            else SkillLabPhase.INPUT_READY,
                            completed = immutable, pending = null,
                            totalTurns = immutable.size,
                            adaptiveTrace = if (trace == null) prior.adaptiveTrace else
                                prior.adaptiveTrace + trace)
                    }
                    if (finished) break
                    if (decision != null && active(job)) submit(job, decision.utterance)
                }
            } finally {
                synchronized(mutex) { if (job.backend === backend) job.backend = null }
                backend.close()
            }
            if (interactor != null) change(job) { it.copy(phase = SkillLabPhase.COMPLETED) }
        } catch (error: Exception) {
            if (active(job)) change(job) {
                it.copy(phase = SkillLabPhase.FAILED, pending = null,
                    error = error.message ?: error.javaClass.simpleName)
            }
        } finally {
            finish(job)
        }
    }

    /** STOP takes effect synchronously; a new model cannot start until old cleanup finishes. */
    fun stop(): Boolean {
        val backend: TextCallAgentBackend?
        val thread: Thread
        val snapshot: SkillLabUiState
        val token: Long
        val version: Long
        synchronized(mutex) {
            val job = current ?: return false
            if (job.stopped.get() || state.phase == SkillLabPhase.COMPLETED ||
                state.phase == SkillLabPhase.FAILED) return false
            job.stopped.set(true)
            generation++  // Invalidate already queued progress and terminal UI deliveries.
            backend = job.backend
            thread = job.thread
            snapshot = state.copy(phase = SkillLabPhase.STOPPED, pending = null,
                error = null)
            state = snapshot
            token = generation
            version = ++revision
        }
        publish(token, version, snapshot)
        thread.interrupt()
        // Model cancellation is best effort; the worker is the sole backend closer.
        runCatching { backend?.cancel() }
        return true
    }

    private fun active(job: Job): Boolean =
        synchronized(mutex) { !closed && current === job && !job.stopped.get() }

    private fun change(job: Job, transition: (SkillLabUiState) -> SkillLabUiState) {
        val snapshot: SkillLabUiState
        val token: Long
        val version: Long
        synchronized(mutex) {
            if (closed || current !== job || job.stopped.get()) return
            snapshot = transition(state)
            state = snapshot
            token = generation
            version = ++revision
        }
        publish(token, version, snapshot)
    }

    private fun publish(token: Long, version: Long, snapshot: SkillLabUiState) {
        dispatchToUi {
            val shouldDeliver = synchronized(mutex) {
                !closed && generation == token && revision == version
            }
            if (shouldDeliver) onState(snapshot)
        }
    }

    private fun awaitResponse(job: Job, backend: TextCallAgentBackend, utterance: String): String {
        val latch = CountDownLatch(1)
        val settled = AtomicBoolean(false)
        val value = AtomicReference<String?>(null)
        val failure = AtomicReference<String?>(null)
        try {
            backend.generate(utterance, object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    if (active(job) && settled.compareAndSet(false, true)) {
                        value.set(text)
                        latch.countDown()
                    }
                }
                override fun onError(reason: String) {
                    if (active(job) && settled.compareAndSet(false, true)) {
                        failure.set(reason)
                        latch.countDown()
                    }
                }
            })
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                runCatching { backend.cancel() }
                throw IllegalStateException("model_response_timeout")
            }
        } finally {
            // Disallow late, duplicated and out-of-order callbacks after each turn.
            settled.set(true)
        }
        failure.get()?.let { throw IllegalStateException("model_error_" + it) }
        return checkNotNull(value.get()) { "model_response_missing" }
    }

    private fun finish(job: Job) {
        val snapshot: SkillLabUiState
        val token: Long
        val version: Long
        synchronized(mutex) {
            if (current !== job) return
            job.backend = null
            current = null
            snapshot = state.copy(pending = null, manualStartAllowed = true)
            state = snapshot
            token = generation
            version = ++revision
        }
        publish(token, version, snapshot)
    }

    override fun close() {
        synchronized(mutex) {
            closed = true
            generation++
            revision++
        }
        stop()
    }
}
