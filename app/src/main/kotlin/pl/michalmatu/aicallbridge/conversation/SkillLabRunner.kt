package pl.michalmatu.aicallbridge.conversation

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** A model session supplies a fresh backend for each independent corpus story. */
internal fun interface SkillLabModelSession {
    fun createStoryBackend(): TextCallAgentBackend
}

internal fun interface SkillLabModelSessionFactory {
    fun open(presetId: String): SkillLabModelSession
}

internal enum class SkillLabPhase { READY, STARTING, WAITING, TURN_COMPLETED, COMPLETED, STOPPED, FAILED }

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

/** Immutable snapshots, never Android Views or a mutable worker-owned result list. */
internal data class SkillLabUiState(
    val phase: SkillLabPhase = SkillLabPhase.READY,
    val totalTurns: Int = 2,
    val completed: List<SkillLabCompletedTurn> = emptyList(),
    val pending: SkillLabPendingTurn? = null,
    val metrics: SkillLabMetrics? = null,
    val error: String? = null,
    val canStart: Boolean = true,
)

/**
 * Sole owner of synthetic/off-call Skill Lab worker, model and terminal callbacks.
 * END_CALL is decoded and scored ONLY; no actual tools or Telecom authority exist here.
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

    private class Job {
        val stopped = AtomicBoolean(false)
        @Volatile var backend: TextCallAgentBackend? = null
        lateinit var thread: Thread
    }

    private val mutex = Any()
    private var generation = 0L
    private var revision = 0L
    private var current: Job? = null
    private var closed = false

    @Volatile var state = SkillLabUiState()
        private set

    fun start(presetId: String, quick: Boolean): Boolean {
        val job = Job()
        val total = if (quick) 2 else SkillLabCorpus.stories.sumOf { it.turns.size }
        val snapshot: SkillLabUiState
        val token: Long
        val version: Long
        synchronized(mutex) {
            if (closed || current != null) return false
            generation++
            current = job
            job.thread = Thread({ execute(job, presetId, quick) }, "aicalls-skills-lab")
            snapshot = SkillLabUiState(phase = SkillLabPhase.STARTING,
                totalTurns = total, canStart = false)
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
                metrics = null, error = null, canStart = false)
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
            snapshot = transition(state).copy(canStart = false)
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

    private fun execute(job: Job, presetId: String, quick: Boolean) {
        val completed = mutableListOf<SkillLabCompletedTurn>()
        try {
            val session = sessions.open(presetId)
            val stories = if (quick) SkillLabCorpus.stories.take(1) else SkillLabCorpus.stories
            for ((storyIndex, story) in stories.withIndex()) {
                if (!active(job)) break
                val backend = session.createStoryBackend()
                val accepted = synchronized(mutex) {
                    if (!closed && current === job && !job.stopped.get()) {
                        job.backend = backend
                        true
                    } else false
                }
                try {
                    if (!accepted) break
                    val turns = if (quick) story.turns.take(2) else story.turns
                    for ((index, turn) in turns.withIndex()) {
                        if (!active(job)) break
                        val pending = SkillLabPendingTurn(
                            storyIndex, story.title, index + 1, turn.utterance)
                        change(job) { it.copy(phase = SkillLabPhase.WAITING, pending = pending) }
                        if (!active(job)) break
                        val started = System.nanoTime()
                        val raw = awaitResponse(job, backend, turn.utterance)
                        if (!active(job)) break
                        val reply = ConversationReplyProtocol.decode(raw)
                        val observation = SkillLabObservation(
                            story.title, index + 1, turn.shouldEnd,
                            reply.tool == ConversationToolId.END_CALL, reply.text,
                            (System.nanoTime() - started) / 1_000_000L, turn.expectQuestion)
                        completed.add(SkillLabCompletedTurn(pending, observation))
                        val immutable = Collections.unmodifiableList(ArrayList(completed))
                        change(job) { it.copy(phase = SkillLabPhase.TURN_COMPLETED,
                            completed = immutable, pending = null) }
                    }
                } finally {
                    synchronized(mutex) {
                        if (job.backend === backend) job.backend = null
                    }
                    backend.close()
                }
            }
            if (active(job)) {
                val immutable = Collections.unmodifiableList(ArrayList(completed))
                change(job) {
                    it.copy(phase = SkillLabPhase.COMPLETED, completed = immutable,
                        pending = null, metrics = SkillLabScoring.score(completed.map { item ->
                            item.observation
                        }))
                }
            }
        } catch (error: Exception) {
            if (active(job)) change(job) {
                it.copy(phase = SkillLabPhase.FAILED, pending = null,
                    error = error.message ?: error.javaClass.simpleName)
            }
        } finally {
            finish(job)
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
            snapshot = state.copy(canStart = true, pending = null)
            state = snapshot
            token = generation
            version = ++revision
        }
        publish(token, version, snapshot)
    }

    override fun close() {
        stop()
        synchronized(mutex) {
            closed = true
            generation++
            revision++
        }
    }
}
