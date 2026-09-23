package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class LocalTextCallGateDBindingTest {
    private val identityState = TaskGraphStateId("IDENTITY_REQUEST")

    @Test
    fun `authorized facts require an explicit TaskGraph binding`() {
        val task = task()
        val target = target()
        val facts = facts(task, target, identityState)

        assertThrows(IllegalArgumentException::class.java) {
            coordinator(
                workflow = readyWorkflow(task, target),
                authorizedFacts = facts,
                taskGraph = null,
            )
        }
    }

    @Test
    fun `readiness rejects authorized facts bound to another task before preflight`() {
        val workflowTask = task()
        val otherTask = task()
        val target = target()
        val speech = FakeSpeechPreflight()
        val backend = FakeBackend()
        val listener = RecordingListener()

        coordinator(
            workflow = readyWorkflow(workflowTask, target),
            taskGraph = graph(identityState),
            authorizedFacts = facts(otherTask, target, identityState),
            speech = speech,
            backend = backend,
        ).prepare(listener)

        assertEquals("authorized_facts_task_mismatch", listener.failureReason)
        assertEquals(0, speech.prepareCalls)
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `readiness rejects authorized facts bound to another target before preflight`() {
        val task = task()
        val workflowTarget = target()
        val otherTarget = CallResolvedTarget("Other clinic", "+48987654321")
        val speech = FakeSpeechPreflight()
        val backend = FakeBackend()
        val listener = RecordingListener()

        coordinator(
            workflow = readyWorkflow(task, workflowTarget),
            taskGraph = graph(identityState),
            authorizedFacts = facts(task, otherTarget, identityState),
            speech = speech,
            backend = backend,
        ).prepare(listener)

        assertEquals("authorized_facts_target_mismatch", listener.failureReason)
        assertEquals(0, speech.prepareCalls)
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `readiness rejects disclosure state missing from bound TaskGraph before preflight`() {
        val task = task()
        val target = target()
        val missingState = TaskGraphStateId("NOT_IN_GRAPH")
        val speech = FakeSpeechPreflight()
        val backend = FakeBackend()
        val listener = RecordingListener()

        coordinator(
            workflow = readyWorkflow(task, target),
            taskGraph = graph(identityState),
            authorizedFacts = facts(task, target, missingState),
            speech = speech,
            backend = backend,
        ).prepare(listener)

        assertEquals("authorized_facts_state_not_in_task_graph", listener.failureReason)
        assertEquals(0, speech.prepareCalls)
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `successful readiness carries exact TaskGraph and fact snapshot into one shot prepared handoff`() {
        val task = task()
        val target = target()
        val graph = graph(identityState)
        val facts = facts(task, target, identityState)
        val listener = RecordingListener()
        val backend = FakeBackend(autoResponse = "gotowe")

        coordinator(
            workflow = readyWorkflow(task, target),
            taskGraph = graph,
            authorizedFacts = facts,
            speech = FakeSpeechPreflight(autoReady = true),
            backend = backend,
        ).prepare(listener)

        val prepared = requireNotNull(listener.prepared)
        assertSame(graph, prepared.taskGraph)
        assertSame(facts, prepared.authorizedFacts)
        assertEquals(1, backend.generateCalls)
        prepared.close()
    }

    private fun coordinator(
        workflow: CallWorkflow,
        taskGraph: TaskGraphDefinition?,
        authorizedFacts: AuthorizedFactSnapshot?,
        speech: FakeSpeechPreflight = FakeSpeechPreflight(),
        backend: FakeBackend = FakeBackend(),
    ) = LocalTextCallReadinessCoordinator(
        workflow = workflow,
        targetAuthorization = DialTargetAuthorization { true },
        speechPreflight = speech,
        backendFactory = { backend },
        timeoutScheduler = FakeTimeoutScheduler(),
        taskGraph = taskGraph,
        authorizedFacts = authorizedFacts,
    )

    private fun graph(identityState: TaskGraphStateId) = TaskGraphDefinition(
        version = 1,
        initialState = TaskGraphStateId("START"),
        states = listOf(
            TaskGraphStateDefinition(TaskGraphStateId("START")),
            TaskGraphStateDefinition(identityState),
        ),
        transitions = emptyList(),
        maxRecoveryCount = 2,
    )

    private fun facts(
        task: CallTask,
        target: CallResolvedTarget,
        allowedState: TaskGraphStateId,
    ) = AuthorizedFactSnapshot(
        task = task,
        target = target,
        generation = 4L,
        availableFields = setOf(IdentityFieldId.PHONE),
        authorizedFields = setOf(IdentityFieldId.PHONE),
        allowedDisclosureStates = mapOf(IdentityFieldId.PHONE to setOf(allowedState)),
    )

    private fun task() = CallTask(
        "Clinic A",
        "book appointment",
        "consultation",
        CallConstraints.unconstrained(),
        CallPreferences.none(),
        emptyMap(),
    )

    private fun target() = CallResolvedTarget("Clinic A", "+48123456789")

    private fun readyWorkflow(task: CallTask, target: CallResolvedTarget) =
        CallWorkflow(task, CallConfirmationPolicy()) { }.apply { resolveTarget(target) }

    private class RecordingListener : LocalTextCallReadinessCoordinator.Listener {
        var prepared: PreparedLocalTextCall? = null
        var failureReason: String? = null
        override fun onReady(prepared: PreparedLocalTextCall) { this.prepared = prepared }
        override fun onFailure(reason: String) { failureReason = reason }
    }

    private class FakeSpeechPreflight(
        private val autoReady: Boolean = false,
    ) : LocalTextCallSpeechPreflight {
        var prepareCalls = 0
        override fun prepare(listener: LocalTextCallSpeechPreflight.Listener) {
            prepareCalls += 1
            if (autoReady) listener.onReady()
        }
        override fun cancel() = Unit
    }

    private class FakeBackend(
        private val autoResponse: String? = null,
    ) : TextCallAgentBackend {
        var generateCalls = 0
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            autoResponse?.let(listener::onComplete)
        }
        override fun cancel() = Unit
    }

    private class FakeTimeoutScheduler : LocalTextCallTimeoutScheduler {
        override fun schedule(delayMs: Long, action: () -> Unit): AutoCloseable = AutoCloseable { }
    }
}
