package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPlanRule
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueHypothesis
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObserver
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyCandidate
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyPolicy
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyRejectReason
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyResult
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyRule
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplySlotRule
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplySlotSchema
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphCandidateProvenance
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEffect
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEffectId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEventId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

class LocalTextCallSessionGateDProductIntegrationTest {
    @Test
    fun `deterministic apply runs first and shadow sees the resulting current snapshot`() {
        val fixture = fixture()
        val pipeline = FakePipeline()
        val worker = QueuedShadowExecutor()
        val order = mutableListOf<String>()
        val applyResults = mutableListOf<TaskGraphApplyResult>()
        val diagnostics = mutableListOf<GateDShadowTurnDiagnostics>()
        val workflowBefore = fixture.workflow.snapshot()
        val binding = LocalTextCallGateDProductBinding(
            deterministicInterpreter = GateDDeterministicCandidateInterpreter { turn ->
                order += "deterministic:${turn.finalizedTranscript}"
                if (turn.finalizedTranscript == "deterministic") {
                    TaskGraphApplyCandidate.deterministic(
                        generation = turn.snapshot.generation,
                        transitionId = DETERMINISTIC_TRANSITION,
                    )
                } else {
                    null
                }
            },
            applyPolicy = fixture.applyPolicy,
            authorizedSlotIdsProvider = GateDAuthorizedSlotIdsProvider { emptySet() },
            applyResultListener = GateDTaskGraphApplyResultListener { applyResults += it },
            shadowObserver = ShadowDialogueObserver { observation ->
                order += "shadow:${observation.finalizedTranscript}"
                assertEquals(MIDDLE, observation.state)
                assertEquals(1L, observation.generation)
                ShadowDialogueHypothesis(
                    generation = observation.generation,
                    suggestedTransition = SHADOW_TRANSITION,
                    slotCandidates = emptyMap(),
                    confidence = 0.95,
                )
            },
            shadowExecutor = worker,
            shadowDiagnosticsListener = GateDShadowDiagnosticsListener { diagnostics += it },
        )
        val session = fixture.session(pipeline, binding)

        session.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        val selector = checkNotNull(pipeline.selector)

        assertEquals(
            TextCallFinalTurnRoute.Candidate("Mam termin."),
            selector.select("deterministic"),
        )
        assertTrue(worker.isEmpty())
        val first = applyResults.single() as TaskGraphApplyResult.Accepted
        assertEquals(MIDDLE, first.snapshot.state)
        assertEquals(listOf(TaskGraphEffect(DETERMINISTIC_EFFECT)), first.effects)

        assertEquals(
            TextCallFinalTurnRoute.Candidate("Mam termin."),
            selector.select("shadow"),
        )
        assertEquals(1, applyResults.size)
        assertFalse(worker.isEmpty())
        worker.runAll()

        val second = applyResults[1] as TaskGraphApplyResult.Accepted
        assertEquals(END, second.snapshot.state)
        assertEquals(listOf(TaskGraphEffect(SHADOW_EFFECT)), second.effects)
        assertEquals(
            listOf("deterministic:deterministic", "deterministic:shadow", "shadow:shadow"),
            order,
        )
        assertEquals(1, diagnostics.size)
        assertEquals(workflowBefore, fixture.workflow.snapshot())
        session.close()
    }

    @Test
    fun `deterministic rejection fails closed instead of falling through to shadow`() {
        val fixture = fixture()
        val pipeline = FakePipeline()
        val worker = QueuedShadowExecutor()
        val applyResults = mutableListOf<TaskGraphApplyResult>()
        var shadowCalls = 0
        val binding = LocalTextCallGateDProductBinding(
            deterministicInterpreter = GateDDeterministicCandidateInterpreter { turn ->
                TaskGraphApplyCandidate.deterministic(
                    generation = turn.snapshot.generation + 1L,
                    transitionId = DETERMINISTIC_TRANSITION,
                )
            },
            applyPolicy = fixture.applyPolicy,
            authorizedSlotIdsProvider = GateDAuthorizedSlotIdsProvider { emptySet() },
            applyResultListener = GateDTaskGraphApplyResultListener { applyResults += it },
            shadowObserver = ShadowDialogueObserver { observation ->
                shadowCalls += 1
                ShadowDialogueHypothesis(
                    generation = observation.generation,
                    suggestedTransition = DETERMINISTIC_TRANSITION,
                    slotCandidates = emptyMap(),
                    confidence = 0.99,
                )
            },
            shadowExecutor = worker,
        )
        val session = fixture.session(pipeline, binding)

        session.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        checkNotNull(pipeline.selector).select("deterministic")
        worker.runAll()

        val rejected = applyResults.single() as TaskGraphApplyResult.Rejected
        assertEquals(TaskGraphApplyRejectReason.STALE_GENERATION, rejected.reason)
        assertEquals(START, rejected.snapshot.state)
        assertEquals(0, shadowCalls)
        session.close()
    }

    @Test
    fun `shadow validation cannot bypass application slot authorization recheck`() {
        val fixture = fixture(withShadowSlot = true)
        val pipeline = FakePipeline()
        val worker = QueuedShadowExecutor()
        val applyResults = mutableListOf<TaskGraphApplyResult>()
        val diagnostics = mutableListOf<GateDShadowTurnDiagnostics>()
        var authorizationChecks = 0
        val binding = LocalTextCallGateDProductBinding(
            deterministicInterpreter = GateDDeterministicCandidateInterpreter { null },
            applyPolicy = fixture.applyPolicy,
            authorizedSlotIdsProvider = GateDAuthorizedSlotIdsProvider {
                authorizationChecks += 1
                if (authorizationChecks == 1) setOf(SLOT) else emptySet()
            },
            applyResultListener = GateDTaskGraphApplyResultListener { applyResults += it },
            shadowObserver = ShadowDialogueObserver { observation ->
                ShadowDialogueHypothesis(
                    generation = observation.generation,
                    suggestedTransition = SHADOW_SLOT_TRANSITION,
                    slotCandidates = mapOf(SLOT to TaskGraphSlotValue.Text("candidate")),
                    confidence = 0.95,
                )
            },
            shadowExecutor = worker,
            shadowDiagnosticsListener = GateDShadowDiagnosticsListener { diagnostics += it },
        )
        val session = fixture.session(pipeline, binding)

        session.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        checkNotNull(pipeline.selector).select("shadow")
        worker.runAll()

        assertEquals(2, authorizationChecks)
        val rejected = applyResults.single() as TaskGraphApplyResult.Rejected
        assertEquals(TaskGraphApplyRejectReason.SLOT_NOT_AUTHORIZED, rejected.reason)
        assertEquals(START, rejected.snapshot.state)
        assertEquals(GateDShadowValidationStatus.ACCEPTED, diagnostics.single().validationStatus)
        session.close()
    }

    @Test
    fun `cancel invalidates queued product shadow before observer or apply`() {
        val fixture = fixture()
        val pipeline = FakePipeline()
        val worker = QueuedShadowExecutor()
        val applyResults = mutableListOf<TaskGraphApplyResult>()
        var shadowCalls = 0
        val binding = LocalTextCallGateDProductBinding(
            deterministicInterpreter = GateDDeterministicCandidateInterpreter { null },
            applyPolicy = fixture.applyPolicy,
            authorizedSlotIdsProvider = GateDAuthorizedSlotIdsProvider { emptySet() },
            applyResultListener = GateDTaskGraphApplyResultListener { applyResults += it },
            shadowObserver = ShadowDialogueObserver { observation ->
                shadowCalls += 1
                ShadowDialogueHypothesis(
                    generation = observation.generation,
                    suggestedTransition = DETERMINISTIC_TRANSITION,
                    slotCandidates = emptyMap(),
                    confidence = 0.99,
                )
            },
            shadowExecutor = worker,
        )
        val session = fixture.session(pipeline, binding)

        session.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        checkNotNull(pipeline.selector).select("shadow")
        session.cancel()
        worker.runAll()

        assertEquals(0, shadowCalls)
        assertTrue(applyResults.isEmpty())
        assertTrue(pipeline.cancelled)
        session.close()
    }

    private fun fixture(withShadowSlot: Boolean = false): Fixture {
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
        val plan = CallPlan(
            task,
            target,
            listOf(CallPlanRule("reply", setOf("nieużywany"), "reply_text")),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val phraseMatrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    "reply",
                    setOf("deterministic", "shadow"),
                ),
            ),
        )
        val transitions = buildList {
            add(
                TaskGraphTransition(
                    id = DETERMINISTIC_TRANSITION,
                    from = START,
                    event = DETERMINISTIC_EVENT,
                    to = MIDDLE,
                    effects = listOf(TaskGraphEffect(DETERMINISTIC_EFFECT)),
                ),
            )
            add(
                TaskGraphTransition(
                    id = SHADOW_TRANSITION,
                    from = MIDDLE,
                    event = SHADOW_EVENT,
                    to = END,
                    effects = listOf(TaskGraphEffect(SHADOW_EFFECT)),
                ),
            )
            if (withShadowSlot) {
                add(
                    TaskGraphTransition(
                        id = SHADOW_SLOT_TRANSITION,
                        from = START,
                        event = SHADOW_SLOT_EVENT,
                        to = END,
                        contextReducer = { context, event ->
                            context.with(SLOT, requireNotNull(event.candidates[SLOT]))
                        },
                    ),
                )
            }
        }
        val graph = TaskGraphDefinition(
            version = 1,
            initialState = START,
            states = listOf(
                TaskGraphStateDefinition(START),
                TaskGraphStateDefinition(MIDDLE),
                TaskGraphStateDefinition(END),
            ),
            transitions = transitions,
            maxRecoveryCount = 2,
        )
        val rules = buildList {
            add(
                TaskGraphApplyRule(
                    transitionId = DETERMINISTIC_TRANSITION,
                    eventId = DETERMINISTIC_EVENT,
                    allowedProvenance = setOf(TaskGraphCandidateProvenance.DETERMINISTIC),
                ),
            )
            add(
                TaskGraphApplyRule(
                    transitionId = SHADOW_TRANSITION,
                    eventId = SHADOW_EVENT,
                    allowedProvenance = setOf(TaskGraphCandidateProvenance.SUPERVISOR),
                ),
            )
            if (withShadowSlot) {
                add(
                    TaskGraphApplyRule(
                        transitionId = SHADOW_SLOT_TRANSITION,
                        eventId = SHADOW_SLOT_EVENT,
                        allowedProvenance = setOf(TaskGraphCandidateProvenance.SUPERVISOR),
                        slotRules = mapOf(
                            SLOT to TaskGraphApplySlotRule(
                                required = true,
                                schema = TaskGraphApplySlotSchema.Text(),
                            ),
                        ),
                    ),
                )
            }
        }
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 0L,
            availableFields = emptySet(),
            authorizedFields = emptySet(),
        )
        return Fixture(
            workflow = workflow,
            plan = plan,
            phraseMatrix = phraseMatrix,
            graph = graph,
            facts = facts,
            applyPolicy = TaskGraphApplyPolicy(rules),
        )
    }

    private data class Fixture(
        val workflow: CallWorkflow,
        val plan: CallPlan,
        val phraseMatrix: PhraseMatrix,
        val graph: TaskGraphDefinition,
        val facts: AuthorizedFactSnapshot,
        val applyPolicy: TaskGraphApplyPolicy,
    ) {
        fun session(
            pipeline: FakePipeline,
            binding: LocalTextCallGateDProductBinding,
        ): LocalTextCallSession = LocalTextCallSession(
            prepared = PreparedLocalTextCall(
                workflow = workflow,
                backend = FakeBackend(),
                callPlan = plan,
                phraseMatrix = phraseMatrix,
                taskGraph = graph,
                authorizedFacts = facts,
            ),
            pipelineFactory = LocalTextCallSession.PipelineFactory { pipeline },
            gateDProductBinding = binding,
        )
    }

    private class QueuedShadowExecutor : GateDShadowExecutor {
        private val queued = ArrayDeque<() -> Unit>()
        private var closed = false

        override fun execute(block: () -> Unit) {
            check(!closed)
            queued.addLast(block)
        }

        override fun close() {
            closed = true
        }

        fun isEmpty(): Boolean = queued.isEmpty()

        fun runAll() {
            while (queued.isNotEmpty()) {
                queued.removeFirst().invoke()
            }
        }
    }

    private class FakeBackend : TextCallAgentBackend {
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class FakePipeline : LocalTextCallSession.Pipeline {
        var selector: LocalSpeechFinalTurnRouteSelector? = null
        var cancelled = false

        override fun start(listener: LocalSpeechTextPipeline.Listener) = Unit

        override fun start(
            listener: LocalSpeechTextPipeline.Listener,
            finalTurnRouteSelector: LocalSpeechFinalTurnRouteSelector,
        ) {
            selector = finalTurnRouteSelector
        }

        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int) = true
        override fun finishInput() = Unit

        override fun cancel() {
            cancelled = true
        }

        override fun close() = Unit
    }

    private class RecordingSpeechListener : LocalSpeechTextPipeline.Listener {
        override fun onSpeechInputReady() = Unit
        override fun onUserTranscript(text: String) = Unit
        override fun onApprovedText(text: String) = Unit
        override fun onOutputPcm16Mono16k(pcm: ByteArray) = Unit
        override fun onDroppedText() = Unit
        override fun onError(reason: String) = error(reason)
    }

    private companion object {
        val START = TaskGraphStateId("START")
        val MIDDLE = TaskGraphStateId("MIDDLE")
        val END = TaskGraphStateId("END")
        val DETERMINISTIC_TRANSITION = TaskGraphTransitionId("deterministic-transition")
        val SHADOW_TRANSITION = TaskGraphTransitionId("shadow-transition")
        val SHADOW_SLOT_TRANSITION = TaskGraphTransitionId("shadow-slot-transition")
        val DETERMINISTIC_EVENT = TaskGraphEventId("DETERMINISTIC_EVENT")
        val SHADOW_EVENT = TaskGraphEventId("SHADOW_EVENT")
        val SHADOW_SLOT_EVENT = TaskGraphEventId("SHADOW_SLOT_EVENT")
        val DETERMINISTIC_EFFECT = TaskGraphEffectId("DETERMINISTIC_EFFECT")
        val SHADOW_EFFECT = TaskGraphEffectId("SHADOW_EFFECT")
        val SLOT = TaskGraphSlotId("REQUESTED_DAY")
    }
}
