package pl.michalmatu.aicallbridge.conversation

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class SkillLabRunnerTest {
    private class Backend(private val immediate: Boolean = false) : TextCallAgentBackend {
        @Volatile var listener: TextCallAgentBackend.Listener? = null
        val generated = CountDownLatch(1)
        val cancelled = AtomicInteger()
        val closed = AtomicInteger()
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            this.listener = listener
            generated.countDown()
            if (immediate) {
                listener.onComplete("Przyjęto wiadomość.")
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

    @Test fun quickRunPreservesBothTurnsAndDistinguishesEndDecisionsFromQuality() {
        val models = Collections.synchronizedList(mutableListOf<Backend>())
        val states = Collections.synchronizedList(mutableListOf<SkillLabUiState>())
        val runner = SkillLabRunner(factory { Backend(immediate = true).also(models::add) },
            { it() }, states::add)
        assertTrue(runner.start("synthetic", quick = true))
        eventually { runner.state.phase == SkillLabPhase.COMPLETED && runner.state.canStart }
        val result = runner.state
        assertEquals(2, result.totalTurns)
        assertEquals(2, result.completed.size)
        assertEquals(2, result.metrics?.turns)
        assertEquals(1, models.size)
        assertEquals(1, models.single().closed.get())
        assertTrue(states.any { it.phase == SkillLabPhase.WAITING })
        assertEquals(2, states.last().completed.size)
        assertNull(result.error)
        runner.close()
    }

    @Test fun fullCorpusOpensOneBackendPerStoryAndCompletesAllTurns() {
        val models = Collections.synchronizedList(mutableListOf<Backend>())
        val runner = SkillLabRunner(factory { Backend(immediate = true).also(models::add) },
            { it() }, {})
        assertTrue(runner.start("synthetic", quick = false))
        eventually { runner.state.phase == SkillLabPhase.COMPLETED && runner.state.canStart }
        assertEquals(SkillLabCorpus.stories.sumOf { it.turns.size }, runner.state.completed.size)
        assertEquals(SkillLabCorpus.stories.size, models.size)
        assertTrue(models.all { it.closed.get() == 1 })
        runner.close()
    }

    @Test fun stopImmediatelyWinsOverPendingAndLateModelCallback() {
        val model = Backend()
        val states = Collections.synchronizedList(mutableListOf<SkillLabUiState>())
        val runner = SkillLabRunner(factory { model }, { it() }, states::add)
        assertTrue(runner.start("synthetic", true))
        assertTrue(model.generated.await(2, TimeUnit.SECONDS))
        assertTrue(runner.stop())
        assertFalse(runner.stop())
        model.listener!!.onComplete("late result that must not appear")
        eventually { runner.state.phase == SkillLabPhase.STOPPED && runner.state.canStart }
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
            while (release.count > 0) {
                try { release.await(10, TimeUnit.MILLISECONDS) }
                catch (_: InterruptedException) { /* Simulate non-interruptible model init. */ }
            }
            SkillLabModelSession { model }
        }
        val runner = SkillLabRunner(sessions, { it() }, {})
        assertTrue(runner.start("synthetic", true))
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        assertTrue(runner.stop())
        release.countDown()
        eventually { runner.state.phase == SkillLabPhase.STOPPED && runner.state.canStart }
        assertEquals(1L, model.generated.count)
        assertEquals(0, model.closed.get()) // No model was created after stop.
        runner.close()
    }

    @Test fun timeoutCancelsBackendAndLateResponseCannotChangeFailure() {
        val model = Backend()
        val runner = SkillLabRunner(factory { model }, { it() }, {}, timeoutMillis = 45)
        assertTrue(runner.start("synthetic", true))
        assertTrue(model.generated.await(2, TimeUnit.SECONDS))
        eventually { runner.state.phase == SkillLabPhase.FAILED && runner.state.canStart }
        assertEquals("model_response_timeout", runner.state.error)
        assertTrue(model.cancelled.get() >= 1)
        assertEquals(1, model.closed.get())
        model.listener!!.onComplete("too late")
        assertEquals(SkillLabPhase.FAILED, runner.state.phase)
        assertTrue(runner.state.completed.isEmpty())
        runner.close()
    }

    @Test fun firstCallbackWinsAndDuplicatedErrorNeverReplacesSuccess() {
        val runner = SkillLabRunner(factory { Backend(immediate = true) }, { it() }, {})
        assertTrue(runner.start("synthetic", true))
        eventually { runner.state.phase == SkillLabPhase.COMPLETED && runner.state.canStart }
        assertEquals(2, runner.state.completed.size)
        assertTrue(runner.state.completed.all { it.observation.reply == "Przyjęto wiadomość." })
        runner.close()
    }

    @Test fun oldStopAndQueuedUiCallbacksCannotOverwriteNextRun() {
        val ui = Collections.synchronizedList(mutableListOf<() -> Unit>())
        val delivered = Collections.synchronizedList(mutableListOf<SkillLabUiState>())
        val first = Backend()
        val next = Backend(immediate = true)
        val models = AtomicInteger()
        val runner = SkillLabRunner(factory {
            if (models.incrementAndGet() == 1) first else next
        }, { ui.add(it) }, delivered::add)
        assertTrue(runner.start("synthetic", true))
        assertTrue(first.generated.await(2, TimeUnit.SECONDS))
        assertTrue(runner.stop())
        eventually { runner.state.canStart }
        assertTrue(runner.start("synthetic", true))
        eventually { runner.state.phase == SkillLabPhase.COMPLETED && runner.state.canStart }
        first.listener!!.onError("stale_error")
        ui.toList().forEach { it() }
        assertTrue(delivered.isNotEmpty())
        assertTrue(delivered.none { it.phase == SkillLabPhase.STOPPED })
        assertEquals(SkillLabPhase.COMPLETED, delivered.last().phase)
        assertEquals(2, delivered.last().completed.size)
        runner.close()
    }

    @Test fun closeSuppressesAllQueuedUiCallbacksAndFutureStarts() {
        val queued = mutableListOf<() -> Unit>()
        val delivered = mutableListOf<SkillLabUiState>()
        val runner = SkillLabRunner(factory { Backend(immediate = true) },
            { queued.add(it) }, delivered::add)
        assertTrue(runner.start("synthetic", true))
        eventually { runner.state.canStart }
        runner.close()
        queued.forEach { it() }
        assertTrue(delivered.isEmpty())
        assertFalse(runner.start("synthetic", true))
        assertFalse(runner.stop())
    }

    @Test fun simultaneousStartIsRejectedWhileExistingModelIsPending() {
        val model = Backend()
        val runner = SkillLabRunner(factory { model }, { it() }, {})
        assertTrue(runner.start("one", true))
        assertTrue(model.generated.await(2, TimeUnit.SECONDS))
        assertFalse(runner.start("two", true))
        assertTrue(runner.stop())
        eventually { runner.state.phase == SkillLabPhase.STOPPED && runner.state.canStart }
        runner.close()
    }
}
