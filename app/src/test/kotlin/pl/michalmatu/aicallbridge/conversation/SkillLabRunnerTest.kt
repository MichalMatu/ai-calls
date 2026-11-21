package pl.michalmatu.aicallbridge.conversation

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class SkillLabRunnerTest {
    private val runners = mutableListOf<SkillLabRunner>()

    @org.junit.After fun closeRunners() = runners.forEach(SkillLabRunner::close)

    private fun testRunner(
        sessions: SkillLabModelSessionFactory,
        dispatchToUi: (() -> Unit) -> Unit,
        onState: (SkillLabUiState) -> Unit,
        timeoutMillis: Long = TimeUnit.SECONDS.toMillis(120),
    ): SkillLabRunner = SkillLabRunner(
        sessions, dispatchToUi, onState, timeoutMillis = timeoutMillis,
    ).also(runners::add)

    private class Backend(private val immediate: Boolean = false) : TextCallAgentBackend {
        @Volatile var listener: TextCallAgentBackend.Listener? = null
        val generated = CountDownLatch(1)
        val cancelled = AtomicInteger()
        val closed = AtomicInteger()
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            this.listener = listener
            generated.countDown()
            if (immediate) {
                listener.onComplete("""{"reply":"Przyjęto wiadomość.","tool":"none"}""")
                listener.onError("duplicate_error_must_be_ignored")
            }
        }
        override fun cancel() { cancelled.incrementAndGet() }
        override fun close() { closed.incrementAndGet() }
    }

    private fun factory(create: () -> Backend): SkillLabModelSessionFactory =
        SkillLabModelSessionFactory { SkillLabModelSession { create() } }

    private fun eventually(timeoutMs: Long = 3000, check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!check() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("condition_not_reached", check())
    }

    private fun submit(runner: SkillLabRunner, text: String = "synthetic input") {
        eventually { runner.state.phase == SkillLabPhase.INPUT_READY }
        assertTrue(runner.submitManual(text))
    }

    @Test fun stopImmediatelyWinsOverPendingAndLateModelCallback() {
        val model = Backend()
        val states = Collections.synchronizedList(mutableListOf<SkillLabUiState>())
        val runner = testRunner(factory { model }, { it() }, states::add)
        assertTrue(runner.startManual("synthetic"))
        submit(runner)
        assertTrue(model.generated.await(2, TimeUnit.SECONDS))
        assertTrue(runner.stop())
        assertFalse(runner.stop())
        model.listener!!.onComplete("late result that must not appear")
        eventually { runner.state.phase == SkillLabPhase.STOPPED && runner.state.manualStartAllowed }
        assertTrue(runner.state.completed.isEmpty())
        assertTrue(model.cancelled.get() >= 1)
        assertEquals(1, model.closed.get())
        assertTrue(states.none { it.phase == SkillLabPhase.COMPLETED })
        runner.close()
    }

    @Test fun stopDuringModelPreparationFencesNewBackendBeforeGenerate() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val model = Backend()
        val sessions = SkillLabModelSessionFactory {
            entered.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            while (release.count > 0 && System.nanoTime() < deadline) {
                try { release.await(10, TimeUnit.MILLISECONDS) }
                catch (_: InterruptedException) { /* Simulate non-interruptible model init. */ }
            }
            SkillLabModelSession { model }
        }
        val runner = testRunner(sessions, { it() }, {})
        assertTrue(runner.startManual("synthetic"))
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        assertTrue(runner.stop())
        release.countDown()
        eventually { runner.state.phase == SkillLabPhase.STOPPED && runner.state.manualStartAllowed }
        assertEquals(1L, model.generated.count)
        assertEquals(0, model.closed.get()) // No model was created after stop.
        runner.close()
    }

    @Test fun timeoutCancelsBackendAndLateResponseCannotChangeFailure() {
        val model = Backend()
        val runner = testRunner(factory { model }, { it() }, {}, timeoutMillis = 45)
        assertTrue(runner.startManual("synthetic"))
        submit(runner)
        assertTrue(model.generated.await(2, TimeUnit.SECONDS))
        eventually { runner.state.phase == SkillLabPhase.FAILED && runner.state.manualStartAllowed }
        assertEquals("model_response_timeout", runner.state.error)
        assertTrue(model.cancelled.get() >= 1)
        assertEquals(1, model.closed.get())
        model.listener!!.onComplete("too late")
        assertEquals(SkillLabPhase.FAILED, runner.state.phase)
        assertTrue(runner.state.completed.isEmpty())
        runner.close()
    }

    @Test fun firstCallbackWinsAndDuplicatedErrorNeverReplacesSuccess() {
        val runner = testRunner(factory { Backend(immediate = true) }, { it() }, {})
        assertTrue(runner.startManual("synthetic"))
        submit(runner)
        eventually { runner.state.completed.size == 1 }
        submit(runner, "second input")
        eventually { runner.state.completed.size == 2 }
        assertTrue(runner.state.completed.all { it.observation.reply == "Przyjęto wiadomość." })
        runner.close()
    }

    @Test fun oldStopAndQueuedUiCallbacksCannotOverwriteNextRun() {
        val ui = Collections.synchronizedList(mutableListOf<() -> Unit>())
        val delivered = Collections.synchronizedList(mutableListOf<SkillLabUiState>())
        val first = Backend()
        val next = Backend(immediate = true)
        val models = AtomicInteger()
        val runner = testRunner(factory {
            if (models.incrementAndGet() == 1) first else next
        }, { ui.add(it) }, delivered::add)
        assertTrue(runner.startManual("synthetic"))
        submit(runner)
        assertTrue(first.generated.await(2, TimeUnit.SECONDS))
        assertTrue(runner.stop())
        eventually { runner.state.manualStartAllowed }
        assertTrue(runner.startManual("synthetic"))
        submit(runner, "new input")
        eventually { runner.state.completed.size == 1 }
        first.listener!!.onError("stale_error")
        eventually {
            synchronized(ui) { ui.toList() }.forEach { it() }
            delivered.lastOrNull()?.completed?.size == 1
        }
        assertTrue(delivered.isNotEmpty())
        assertTrue(delivered.none { it.phase == SkillLabPhase.STOPPED })
        assertEquals(SkillLabPhase.INPUT_READY, delivered.last().phase)
        assertEquals("new input", delivered.last().completed.single().pending.utterance)
        runner.close()
    }

    @Test fun closeSuppressesAllQueuedUiCallbacksAndFutureStarts() {
        val queued = Collections.synchronizedList(mutableListOf<() -> Unit>())
        val delivered = mutableListOf<SkillLabUiState>()
        val runner = testRunner(factory { Backend(immediate = true) },
            { queued.add(it) }, delivered::add)
        assertTrue(runner.startManual("synthetic"))
        submit(runner)
        eventually { runner.state.completed.size == 1 }
        runner.close()
        synchronized(queued) { queued.toList() }.forEach { it() }
        assertTrue(delivered.isEmpty())
        assertFalse(runner.startManual("synthetic"))
        assertFalse(runner.stop())
    }

    @Test fun simultaneousStartIsRejectedWhileExistingModelIsPending() {
        val model = Backend()
        val runner = testRunner(factory { model }, { it() }, {})
        assertTrue(runner.startManual("one"))
        submit(runner)
        assertTrue(model.generated.await(2, TimeUnit.SECONDS))
        assertFalse(runner.startManual("two"))
        assertTrue(runner.stop())
        eventually { runner.state.phase == SkillLabPhase.STOPPED && runner.state.manualStartAllowed }
        runner.close()
    }
}
