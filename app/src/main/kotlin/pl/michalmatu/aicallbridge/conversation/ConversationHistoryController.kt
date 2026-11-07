package pl.michalmatu.aicallbridge.conversation

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Immutable data for the history screen. No Android View, MediaPlayer, or Call authority. */
internal data class HistoryListRow(
    val session: RecordedConversation,
    val canDelete: Boolean,
)

internal sealed interface HistoryDeleteResult {
    data class Single(val deleted: Boolean) : HistoryDeleteResult
    data class Bulk(val deleted: Int, val skipped: Int, val failed: Int) : HistoryDeleteResult
}

internal sealed interface HistoryScreenState {
    object Loading : HistoryScreenState
    data class ListReady(val items: List<HistoryListRow>) : HistoryScreenState {
        val deletableCount: Int get() = items.count { it.canDelete }
    }
    data class DetailReady(
        val session: RecordedConversation,
        val canDelete: Boolean,
    ) : HistoryScreenState
    data class Deleted(val result: HistoryDeleteResult) : HistoryScreenState
    data class Failed(val reason: String) : HistoryScreenState
}

/**
 * One serialized IO owner over the existing private archive. Only UI-confirmed
 * deletion methods mutate it; all paths and COMPLETE/END checks remain in the
 * existing RecordedConversationHistory implementation.
 *
 * Every navigation invalidates older deliveries, including already queued UI
 * callbacks. Destroy suppresses pending callbacks and unstarted IO.
 */
internal class ConversationHistoryController(
    private val archive: RecordedConversationHistory,
    private val worker: Executor = Executors.newSingleThreadExecutor(),
    private val dispatchToUi: (() -> Unit) -> Unit,
) {
    private val closed = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private val deleting = AtomicBoolean(false)

    val isDeletionRunning: Boolean get() = deleting.get()

    fun showList(onState: (HistoryScreenState) -> Unit) {
        val token = generation.incrementAndGet()
        deliver(token, onState, HistoryScreenState.Loading)
        runRead(token, onState) {
            val rows = archive.list().map { session ->
                HistoryListRow(session, session.complete && archive.canDelete(session.id))
            }
            HistoryScreenState.ListReady(rows)
        }
    }

    fun showSession(id: String, onState: (HistoryScreenState) -> Unit) {
        val token = generation.incrementAndGet()
        deliver(token, onState, HistoryScreenState.Loading)
        runRead(token, onState) {
            val session = archive.load(id)
            HistoryScreenState.DetailReady(
                session, session.complete && archive.canDelete(id),
            )
        }
    }

    /** Only call from the positive button of the existing confirmation dialog. */
    fun deleteCompletedConfirmed(id: String, onState: (HistoryScreenState) -> Unit): Boolean =
        runDeletion(onState) {
            HistoryScreenState.Deleted(HistoryDeleteResult.Single(archive.deleteCompleted(id)))
        }

    /** Only call from the positive button of the existing bulk confirmation dialog. */
    fun deleteAllCompletedConfirmed(onState: (HistoryScreenState) -> Unit): Boolean =
        runDeletion(onState) {
            val outcome = archive.deleteAllCompleted()
            HistoryScreenState.Deleted(HistoryDeleteResult.Bulk(
                outcome.deleted, outcome.skipped, outcome.failed,
            ))
        }

    private fun runRead(
        token: Long,
        onState: (HistoryScreenState) -> Unit,
        load: () -> HistoryScreenState,
    ) {
        if (closed.get()) return
        worker.execute {
            if (!closed.get()) {
                val outcome = runCatching(load).getOrElse {
                    HistoryScreenState.Failed(it.javaClass.simpleName)
                }
                deliver(token, onState, outcome)
            }
        }
    }

    private fun runDeletion(
        onState: (HistoryScreenState) -> Unit,
        operation: () -> HistoryScreenState,
    ): Boolean {
        if (closed.get() || !deleting.compareAndSet(false, true)) return false
        val token = generation.get()
        return try {
            worker.execute {
                if (!closed.get()) {
                    val outcome = runCatching(operation).getOrElse {
                        HistoryScreenState.Failed(it.javaClass.simpleName)
                    }
                    deleting.set(false)
                    deliver(token, onState, outcome)
                } else {
                    deleting.set(false)
                }
            }
            true
        } catch (_: RuntimeException) {
            deleting.set(false)
            false
        }
    }

    private fun deliver(
        token: Long,
        onState: (HistoryScreenState) -> Unit,
        value: HistoryScreenState,
    ) {
        dispatchToUi {
            if (!closed.get() && generation.get() == token) onState(value)
        }
    }

    fun close() {
        closed.set(true)
        generation.incrementAndGet()
        if (worker is ExecutorService) worker.shutdown()
    }
}
