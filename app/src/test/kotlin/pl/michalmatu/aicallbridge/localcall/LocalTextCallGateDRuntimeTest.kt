package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueHypothesis
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalRejectReason
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalValidation
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.identity.IdentitySensitivity
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEventId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateKind
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class LocalTextCallGateDRuntimeTest {
    @Test
    fun `session builds bounded shadow observation from bound gate d context`() {
        val fixture = fixture()
        val session = fixture.session()
        val slot = TaskGraphSlotId("requested_day")
        val snapshot = fixture.graph.initialSnapshot()

        val observation = session.createGateDShadowObservation(
            snapshot = snapshot,
            finalizedTranscript = "Czy macie termin w środę?",
            validatedNonSecretSlots = mapOf(slot to TaskGraphSlotValue.Text("środa")),
        )

        assertEquals(snapshot.generation, observation.generation)
        assertEquals(snapshot.state, observation.state)
        assertEquals(setOf(TaskGraphTransitionId("offer-slot")), observation.allowedTransitions)
        assertEquals(TaskGraphSlotValue.Text("środa"), observation.validatedSlots[slot])
        assertEquals(setOf(IdentityFieldId.FIRST_NAME), observation.availableFacts)
        assertEquals("book consultation", observation.taskGoal)
        assertEquals("Czy macie termin w środę?", observation.finalizedTranscript)
        assertFalse(observation.toString().contains("Czy macie termin"))
        assertFalse(observation.toString().contains("Jan"))
        session.close()
    }

    @Test
    fun `session validates shadow proposal as candidate without reducing task graph`() {
        val fixture = fixture()
        val session = fixture.session()
        val slot = TaskGraphSlotId("requested_day")
        val snapshot = fixture.graph.initialSnapshot()
        val observation = session.createGateDShadowObservation(
            snapshot = snapshot,
            finalizedTranscript = "Środa pasuje",
            validatedNonSecretSlots = mapOf(slot to TaskGraphSlotValue.Text("środa")),
        )
        val hypothesis = ShadowDialogueHypothesis(
            generation = snapshot.generation,
            suggestedTransition = TaskGraphTransitionId("offer-slot"),
            slotCandidates = mapOf(slot to TaskGraphSlotValue.Text("środa")),
            confidence = 0.95,
        )

        val result = session.validateGateDShadowProposal(
            observation = observation,
            hypothesis = hypothesis,
            allowedNonSecretSlots = setOf(slot),
        )

        val accepted = result as SupervisorProposalValidation.Accepted
        assertEquals(TaskGraphTransitionId("offer-slot"), accepted.candidate.transitionId)
        assertEquals(snapshot.generation, accepted.candidate.generation)
        assertEquals(snapshot, fixture.graph.initialSnapshot())
        session.close()
    }

    @Test
    fun `session fails closed for stale or authority bearing shadow proposal`() {
        val fixture = fixture()
        val session = fixture.session()
        val snapshot = fixture.graph.initialSnapshot()
        val observation = session.createGateDShadowObservation(
            snapshot = snapshot,
            finalizedTranscript = "Tak",
            validatedNonSecretSlots = emptyMap(),
        )

        val stale = session.validateGateDShadowProposal(
            observation = observation,
            hypothesis = ShadowDialogueHypothesis(
                generation = snapshot.generation + 1,
                suggestedTransition = TaskGraphTransitionId("offer-slot"),
                slotCandidates = emptyMap(),
                confidence = 0.95,
            ),
            allowedNonSecretSlots = emptySet(),
        ) as SupervisorProposalValidation.Rejected
        assertEquals(SupervisorProposalRejectReason.STALE_GENERATION, stale.reason)

        val authoritySlot = TaskGraphSlotId("identity_value")
        val authority = session.validateGateDShadowProposal(
            observation = observation,
            hypothesis = ShadowDialogueHypothesis(
                generation = snapshot.generation,
                suggestedTransition = TaskGraphTransitionId("offer-slot"),
                slotCandidates = mapOf(authoritySlot to TaskGraphSlotValue.Text("secret")),
                confidence = 0.95,
            ),
            allowedNonSecretSlots = setOf(authoritySlot),
        ) as SupervisorProposalValidation.Rejected
        assertEquals(SupervisorProposalRejectReason.AUTHORITY_BEARING_SLOT, authority.reason)
        session.close()
    }

    @Test
    fun `observation exposes only currently scoped authorized fact ids`() {
        val fixture = fixture(includeSensitiveFacts = true)
        val session = fixture.session()
        val start = fixture.graph.initialSnapshot()
        val startObservation = session.createGateDShadowObservation(
            snapshot = start,
            finalizedTranscript = "Dzień dobry",
            validatedNonSecretSlots = emptyMap(),
        )

        assertTrue(IdentityFieldId.FIRST_NAME in startObservation.availableFacts)
        assertFalse(IdentityFieldId.EMAIL in startObservation.availableFacts)
        assertFalse(IdentityFieldId.PESEL in startObservation.availableFacts)
        assertTrue(IdentityFieldId.PESEL.sensitivity == IdentitySensitivity.HIGH)
        session.close()
    }

    @Test
    fun `session without task graph refuses gate d observation`() {
        val task = task()
        val target = target()
        val workflow = readyWorkflow(task, target)
        val session = LocalTextCallSession(
            prepared = PreparedLocalTextCall(workflow, FakeBackend()),
            pipelineFactory = LocalTextCallSession.PipelineFactory { FakePipeline() },
        )

        assertThrows(IllegalStateException::class.java) {
            session.createGateDShadowObservation(
                snapshot = fixture().graph.initialSnapshot(),
                finalizedTranscript = "test",
                validatedNonSecretSlots = emptyMap(),
            )
        }
        session.close()
    }

    private fun fixture(includeSensitiveFacts: Boolean = false): Fixture {
        val task = task()
        val target = target()
        val workflow = readyWorkflow(task, target)
        val start = TaskGraphStateId("start")
        val offered = TaskGraphStateId("offered")
        val graph = TaskGraphDefinition(
            version = 1,
            initialState = start,
            states = listOf(
                TaskGraphStateDefinition(start),
                TaskGraphStateDefinition(offered, TaskGraphStateKind.COMPLETED),
            ),
            transitions = listOf(
                TaskGraphTransition(
                    id = TaskGraphTransitionId("offer-slot"),
                    from = start,
                    event = TaskGraphEventId("slot-found"),
                    to = offered,
                ),
            ),
            maxRecoveryCount = 2,
        )
        val available = if (includeSensitiveFacts) {
            setOf(IdentityFieldId.FIRST_NAME, IdentityFieldId.EMAIL, IdentityFieldId.PESEL)
        } else {
            setOf(IdentityFieldId.FIRST_NAME)
        }
        val authorized = if (includeSensitiveFacts) available else setOf(IdentityFieldId.FIRST_NAME)
        val scopes = buildMap {
            put(IdentityFieldId.FIRST_NAME, setOf(start))
            if (includeSensitiveFacts) {
                put(IdentityFieldId.EMAIL, setOf(offered))
                put(IdentityFieldId.PESEL, setOf(start))
            }
        }
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 0,
            availableFields = available,
            authorizedFields = authorized,
            highSensitivityApprovedFields = emptySet(),
            allowedDisclosureStates = scopes,
        )
        return Fixture(workflow, graph, facts)
    }

    private fun task() = CallTask(
        "Clinic A",
        "book",
        "consultation",
        CallConstraints.unconstrained(),
        CallPreferences.none(),
        mapOf("first_name" to "Jan"),
    )

    private fun target() = CallResolvedTarget("Clinic A", "+48123456789")

    private fun readyWorkflow(task: CallTask, target: CallResolvedTarget): CallWorkflow =
        CallWorkflow(task, CallConfirmationPolicy()) { }.apply { resolveTarget(target) }

    private data class Fixture(
        val workflow: CallWorkflow,
        val graph: TaskGraphDefinition,
        val facts: AuthorizedFactSnapshot,
    ) {
        fun session(): LocalTextCallSession = LocalTextCallSession(
            prepared = PreparedLocalTextCall(
                workflow = workflow,
                backend = FakeBackend(),
                taskGraph = graph,
                authorizedFacts = facts,
            ),
            pipelineFactory = LocalTextCallSession.PipelineFactory { FakePipeline() },
        )
    }

    private class FakeBackend : TextCallAgentBackend {
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class FakePipeline : LocalTextCallSession.Pipeline {
        override fun start(listener: LocalSpeechTextPipeline.Listener) = Unit
        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int) = true
        override fun finishInput() = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }
}
