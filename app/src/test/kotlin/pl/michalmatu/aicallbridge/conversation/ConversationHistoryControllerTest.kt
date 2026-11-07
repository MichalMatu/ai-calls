package pl.michalmatu.aicallbridge.conversation

import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class ConversationHistoryControllerTest {
    private class QueuedExecutor : Executor {
        val jobs = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { jobs.addLast(command) }
        fun drain() { while (jobs.isNotEmpty()) jobs.removeFirst().run() }
    }

    private fun archive(root: File, id: String, complete: Boolean = true): File {
        val dir = File(root, id).apply { assertTrue(mkdirs()) }
        File(dir, "timeline.jsonl").writeText(
            """{"type":"CONVERSATION","direction":"INCOMING","started_at_ms":1000}""" + "\n" +
            """{"type":"COUNTERPART_STT","turn":1,"text":"synthetic","elapsed_ms":1}""" + "\n" +
            if (complete) """{"type":"END","elapsed_ms":2}""" + "\n" else ""
        )
        if (complete) File(dir, "COMPLETE").writeText("complete")
        return dir
    }

    private fun controller(
        root: File, worker: QueuedExecutor, ui: QueuedExecutor,
    ) = ConversationHistoryController(
        RecordedConversationHistory(root), worker,
        { action -> ui.execute(Runnable(action)) },
    )

    @Test fun listAndDetailCarryTypedDeletionEligibility() {
        val root = Files.createTempDirectory("ux02-list").toFile()
        try {
            archive(root, "1000-done")
            archive(root, "2000-live", complete = false)
            val worker = QueuedExecutor(); val ui = QueuedExecutor()
            val c = controller(root, worker, ui)
            val states = mutableListOf<HistoryScreenState>()
            c.showList { states.add(it) }
            worker.drain(); ui.drain()
            val ready = states.filterIsInstance<HistoryScreenState.ListReady>().single()
            assertEquals(2, ready.items.size)
            assertEquals(1, ready.deletableCount)
            c.showSession("1000-done") { states.add(it) }
            worker.drain(); ui.drain()
            assertTrue(states.last() is HistoryScreenState.DetailReady)
            assertTrue((states.last() as HistoryScreenState.DetailReady).canDelete)
            c.close()
        } finally { root.deleteRecursively() }
    }

    @Test fun latestNavigationWinsAndStaleListNeverRendersAfterDetails() {
        val root = Files.createTempDirectory("ux02-navigation").toFile()
        try {
            archive(root, "1000-done")
            val worker = QueuedExecutor(); val ui = QueuedExecutor()
            val c = controller(root, worker, ui)
            val delivered = mutableListOf<HistoryScreenState>()
            c.showList { delivered.add(it) }
            c.showSession("1000-done") { delivered.add(it) }
            worker.drain(); ui.drain()
            assertEquals(1, delivered.filterIsInstance<HistoryScreenState.DetailReady>().size)
            assertTrue(delivered.none { it is HistoryScreenState.ListReady })
            c.showSession("1000-done") { delivered.add(it) }
            c.showList { delivered.add(it) }
            worker.drain(); ui.drain()
            assertTrue(delivered.last() is HistoryScreenState.ListReady)
            c.close()
        } finally { root.deleteRecursively() }
    }

    @Test fun closingDiscardsQueuedCallbacksWithoutReadingPrivateFiles() {
        val root = Files.createTempDirectory("ux02-close").toFile()
        try {
            archive(root, "1000-done")
            val worker = QueuedExecutor(); val ui = QueuedExecutor()
            val c = controller(root, worker, ui)
            val states = mutableListOf<HistoryScreenState>()
            c.showList { states.add(it) }
            c.close()
            worker.drain(); ui.drain()
            assertTrue(states.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun duplicateBulkDeleteIsRejectedAndIncompleteAndUnknownDataRemain() {
        val root = Files.createTempDirectory("ux02-bulk").toFile()
        try {
            val completed = archive(root, "1000-done")
            val active = archive(root, "2000-active", complete = false)
            val unrelated = File(root, "other-private-data").apply { assertTrue(mkdir()) }
            File(unrelated, "keep.txt").writeText("still here")
            val worker = QueuedExecutor(); val ui = QueuedExecutor()
            val c = controller(root, worker, ui)
            val states = mutableListOf<HistoryScreenState>()
            assertTrue(c.deleteAllCompletedConfirmed { states.add(it) })
            assertTrue(c.isDeletionRunning)
            assertFalse(c.deleteAllCompletedConfirmed { states.add(it) })
            worker.drain(); ui.drain()
            val outcome = (states.single() as HistoryScreenState.Deleted).result
                as HistoryDeleteResult.Bulk
            assertEquals(1, outcome.deleted)
            assertEquals(1, outcome.skipped)
            assertEquals(0, outcome.failed)
            assertFalse(completed.exists())
            assertTrue(active.exists())
            assertEquals("still here", File(unrelated, "keep.txt").readText())
            assertFalse(c.isDeletionRunning)
            c.close()
        } finally { root.deleteRecursively() }
    }

    @Test fun confirmedSingleDeleteRefusesTraversalAndAnActiveArchive() {
        val root = Files.createTempDirectory("ux02-single").toFile()
        try {
            val active = archive(root, "1000-live", complete = false)
            val worker = QueuedExecutor(); val ui = QueuedExecutor()
            val c = controller(root, worker, ui)
            val results = mutableListOf<HistoryScreenState>()
            assertTrue(c.deleteCompletedConfirmed("../escaped") { results.add(it) })
            worker.drain(); ui.drain()
            assertFalse((results.last() as HistoryScreenState.Deleted)
                .result.let { it as HistoryDeleteResult.Single }.deleted)
            assertTrue(c.deleteCompletedConfirmed("1000-live") { results.add(it) })
            worker.drain(); ui.drain()
            assertFalse(((results.last() as HistoryScreenState.Deleted)
                .result as HistoryDeleteResult.Single).deleted)
            assertTrue(active.exists())
            c.close()
        } finally { root.deleteRecursively() }
    }

    @Test fun symlinkCannotEscapeTheArchiveEvenDuringBulkDeletion() {
        val root = Files.createTempDirectory("ux02-symlink").toFile()
        val outside = Files.createTempDirectory("ux02-outside").toFile()
        try {
            archive(root, "1000-done")
            val target = File(outside, "timeline.jsonl").apply { writeText("external") }
            File(outside, "COMPLETE").writeText("complete")
            Files.createSymbolicLink(File(root, "9000-link").toPath(), outside.toPath())
            val worker = QueuedExecutor(); val ui = QueuedExecutor()
            val c = controller(root, worker, ui)
            assertTrue(c.deleteAllCompletedConfirmed { })
            worker.drain(); ui.drain()
            assertEquals("external", target.readText())
            c.close()
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }

    @Test fun destroyBeforeQueuedConfirmedDeletionLeavesSyntheticArchiveIntact() {
        val root = Files.createTempDirectory("ux02-destroy-delete").toFile()
        try {
            val completed = archive(root, "1000-done")
            val worker = QueuedExecutor(); val ui = QueuedExecutor()
            val c = controller(root, worker, ui)
            assertTrue(c.deleteCompletedConfirmed("1000-done") { })
            c.close()
            worker.drain(); ui.drain()
            assertTrue(completed.exists())
        } finally { root.deleteRecursively() }
    }
}
