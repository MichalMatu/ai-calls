package pl.michalmatu.aicallbridge.localcall

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPlanRule
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyCandidate
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyPolicy
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyResult
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyRule
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphCandidateProvenance
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEffect
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEffectId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEventId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

@RunWith(AndroidJUnit4::class)
class AndroidGateDProductSyntheticInputContractTest {
    @Test
    fun syntheticFinalizedTextExercisesSharedProductPathWithoutSpeechOrMedia() {
        val task = CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            mapOf("reply_text" to "Mam termin."),
        )
        val target = CallResolvedTarget("Clinic A", "+48123456789")
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }
        val workflowBefore = workflow.snapshot()
        val plan = CallPlan(
            task,
            target,
            listOf(CallPlanRule("reply", setOf("unused"), "reply_text")),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val phraseMatrix = PhraseMatrix(
            listOf(PhraseMatrixRule("reply", setOf("synthetic"))),
        )
        val graph = TaskGraphDefinition(
            version = 1,
            initialState = START,
            states = listOf(
                TaskGraphStateDefinition(START),
                TaskGraphStateDefinition(END),
            ),
            transitions = listOf(
                TaskGraphTransition(
                    id = TRANSITION,
                    from = START,
                    event = EVENT,
                    to = END,
                    effects = listOf(TaskGraphEffect(EFFECT)),
                ),
            ),
            maxRecoveryCount = 2,
        )
        val policy = TaskGraphApplyPolicy(
            listOf(
                TaskGraphApplyRule(
                    transitionId = TRANSITION,
                    eventId = EVENT,
                    allowedProvenance = setOf(TaskGraphCandidateProvenance.DETERMINISTIC),
                ),
            ),
        )
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 0L,
            availableFields = emptySet(),
            authorizedFields = emptySet(),
        )
        val backend = RecordingBackend()
        val pipeline = RecordingPipeline()
        val applyResults = mutableListOf<TaskGraphApplyResult>()
        val session = LocalTextCallSession(
            prepared = PreparedLocalTextCall(
                workflow = workflow,
                backend = backend,
                callPlan = plan,
                phraseMatrix = phraseMatrix,
                taskGraph = graph,
                authorizedFacts = facts,
            ),
            pipelineFactory = LocalTextCallSession.PipelineFactory { pipeline },
            gateDProductBinding = LocalTextCallGateDProductBinding(
                deterministicInterpreter = GateDDeterministicCandidateInterpreter { turn ->
                    TaskGraphApplyCandidate.deterministic(
                        generation = turn.snapshot.generation,
                        transitionId = TRANSITION,
                    )
                },
                applyPolicy = policy,
                authorizedSlotIdsProvider = GateDAuthorizedSlotIdsProvider { emptySet() },
                applyResultListener = GateDTaskGraphApplyResultListener { applyResults += it },
            ),
        )

        try {
            val selection = session.injectSyntheticFinalTranscript("synthetic")

            assertEquals(TextCallFinalTurnRoute.Candidate("Mam termin."), selection.route)
            assertNull(selection.structuredResult)
            assertEquals(0, pipeline.startCalls)
            assertEquals(0, pipeline.writeCalls)
            assertEquals(0, backend.generateCalls)
            val accepted = applyResults.single() as TaskGraphApplyResult.Accepted
            assertEquals(END, accepted.snapshot.state)
            assertEquals(1L, accepted.snapshot.generation)
            assertEquals(listOf(TaskGraphEffect(EFFECT)), accepted.effects)
            assertEquals(workflowBefore, workflow.snapshot())
        } finally {
            session.close()
        }
    }

    private class RecordingBackend : TextCallAgentBackend {
        var generateCalls = 0

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
        }

        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class RecordingPipeline : LocalTextCallSession.Pipeline {
        var startCalls = 0
        var writeCalls = 0

        override fun start(listener: LocalSpeechTextPipeline.Listener) {
            startCalls += 1
        }

        override fun start(
            listener: LocalSpeechTextPipeline.Listener,
            finalTurnRouteSelector: LocalSpeechFinalTurnRouteSelector,
        ) {
            startCalls += 1
        }

        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int): Boolean {
            writeCalls += 1
            return true
        }

        override fun finishInput() = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private companion object {
        val START = TaskGraphStateId("START")
        val END = TaskGraphStateId("END")
        val TRANSITION = TaskGraphTransitionId("synthetic-turn")
        val EVENT = TaskGraphEventId("SYNTHETIC_TURN")
        val EFFECT = TaskGraphEffectId("SYNTHETIC_EFFECT")
    }
}
