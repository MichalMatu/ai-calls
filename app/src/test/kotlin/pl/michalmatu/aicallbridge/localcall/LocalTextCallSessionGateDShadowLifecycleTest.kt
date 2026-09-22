package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
import pl.michalmatu.aicallbridge.dialogue.DialogueFitLevel
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueHypothesis
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObservation
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObserver
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalRejectReason
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
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
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

class LocalTextCallSessionGateDShadowLifecycleTest {
    @Test
    fun `gate d finalized turn creates one bounded observation without changing phrase matrix route`() {
        val fixture = fixture()
        val pipeline = FakePipeline()
        val worker = QueuedShadowExecutor()
        val observations = mutableListOf<ShadowDialogueObservation>()
        val diagnostics = mutableListOf<GateDShadowTurnDiagnostics>()
        val before = fixture.workflow.snapshot()
        val session = fixture.session(
            pipeline = pipeline,
            observer = ShadowDialogueObserver { observation ->
                observations += observation
                ShadowDialogueHypothesis(
                    generation = observation.generation,
                    suggestedTransition = TaskGraphTransitionId("offer-slot"),
                    slotCandidates = emptyMap(),
                    confidence = 0.95,
                    diagnostics = mapOf("note" to "Jan"),
                )
            },
            worker = worker,
            diagnostics = diagnostics,
        )

        session.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        val route = checkNotNull(pipeline.selector).select("termin")

        assertEquals(TextCallFinalTurnRoute.Candidate("Mam środę."), route)
        assertTrue(observations.isEmpty())
        assertEquals(before, fixture.workflow.snapshot())

        worker.runAll()

        val observation = observations.single()
        assertEquals(0L, observation.generation)
        assertEquals(TaskGraphStateId("start"), observation.state)
        assertEquals(setOf(TaskGraphTransitionId("offer-slot")), observation.allowedTransitions)
        assertTrue(observation.validatedSlots.isEmpty())
        assertEquals(setOf(IdentityFieldId.FIRST_NAME), observation.availableFacts)
        assertEquals("termin", observation.finalizedTranscript)
        assertFalse(observation.toString().contains("Jan"))

        val diagnostic = diagnostics.single()
        assertEquals(GateDShadowValidationStatus.ACCEPTED, diagnostic.validationStatus)
        assertEquals(DialogueFitLevel.HIGH, diagnostic.dialogueFit.level)
        assertFalse(diagnostic.toString().contains("Jan"))
        assertEquals(before, fixture.workflow.snapshot())
        session.close()
    }

    @Test
    fun `stale hypothesis is rejected by supervisor validator and remains diagnostics only`() {
        val fixture = fixture()
        val pipeline = FakePipeline()
        val worker = QueuedShadowExecutor()
        val diagnostics = mutableListOf<GateDShadowTurnDiagnostics>()
        val before = fixture.workflow.snapshot()
        val session = fixture.session(
            pipeline = pipeline,
            observer = ShadowDialogueObserver { observation ->
                ShadowDialogueHypothesis(
                    generation = observation.generation + 1L,
                    suggestedTransition = TaskGraphTransitionId("offer-slot"),
                    slotCandidates = emptyMap(),
                    confidence = 0.95,
                )
            },
            worker = worker,
            diagnostics = diagnostics,
        )

        session.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        assertEquals(
            TextCallFinalTurnRoute.Candidate("Mam środę."),
            checkNotNull(pipeline.selector).select("termin"),
        )
        worker.runAll()

        val diagnostic = diagnostics.single()
        assertEquals(GateDShadowValidationStatus.REJECTED, diagnostic.validationStatus)
        assertEquals(SupervisorProposalRejectReason.STALE_GENERATION, diagnostic.rejectReason)
        assertEquals(before, fixture.workflow.snapshot())
        session.close()
    }

    @Test
    fun `new finalized turn invalidates queued shadow work from the older session generation`() {
        val fixture = fixture()
        val pipeline = FakePipeline()
        val worker = QueuedShadowExecutor()
        val observedTranscripts = mutableListOf<String>()
        val diagnostics = mutableListOf<GateDShadowTurnDiagnostics>()
        val session = fixture.session(
            pipeline = pipeline,
            observer = ShadowDialogueObserver { observation ->
                observedTranscripts += observation.finalizedTranscript
                ShadowDialogueHypothesis(
                    generation = observation.generation,
                    suggestedTransition = TaskGraphTransitionId("offer-slot"),
                    slotCandidates = emptyMap(),
                    confidence = 0.95,
                )
            },
            worker = worker,
            diagnostics = diagnostics,
        )

        session.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        val selector = checkNotNull(pipeline.selector)
        assertEquals(TextCallFinalTurnRoute.Candidate("Mam środę."), selector.select("termin"))
        assertEquals(TextCallFinalTurnRoute.Candidate("Mam środę."), selector.select("termin"))

        worker.runAll()

        assertEquals(listOf("termin"), observedTranscripts)
        assertEquals(1, diagnostics.size)
        session.close()
    }

    @Test
    fun `cancel and close invalidate queued observer work`() {
        val cancelledFixture = fixture()
        val cancelledPipeline = FakePipeline()
        val cancelledWorker = QueuedShadowExecutor()
        var cancelledObserverCalls = 0
        val cancelledDiagnostics = mutableListOf<GateDShadowTurnDiagnostics>()
        val cancelledSession = cancelledFixture.session(
            pipeline = cancelledPipeline,
            observer = ShadowDialogueObserver { observation ->
                cancelledObserverCalls += 1
                acceptedHypothesis(observation)
            },
            worker = cancelledWorker,
            diagnostics = cancelledDiagnostics,
        )
        cancelledSession.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        checkNotNull(cancelledPipeline.selector).select("termin")
        cancelledSession.cancel()
        cancelledWorker.runAll()

        assertEquals(0, cancelledObserverCalls)
        assertTrue(cancelledDiagnostics.isEmpty())
        assertTrue(cancelledPipeline.cancelled)
        cancelledSession.close()

        val closedFixture = fixture()
        val closedPipeline = FakePipeline()
        val closedWorker = QueuedShadowExecutor()
        var closedObserverCalls = 0
        val closedDiagnostics = mutableListOf<GateDShadowTurnDiagnostics>()
        val closedSession = closedFixture.session(
            pipeline = closedPipeline,
            observer = ShadowDialogueObserver { observation ->
                closedObserverCalls += 1
                acceptedHypothesis(observation)
            },
            worker = closedWorker,
            diagnostics = closedDiagnostics,
        )
        closedSession.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        checkNotNull(closedPipeline.selector).select("termin")
        closedSession.close()
        closedWorker.runAll()

        assertEquals(0, closedObserverCalls)
        assertTrue(closedDiagnostics.isEmpty())
        assertTrue(closedWorker.closed)
        assertTrue(closedPipeline.closed)
    }

    @Test
    fun `candidate values and identity facts never enter ordinary shadow diagnostics`() {
        val fixture = fixture()
        val pipeline = FakePipeline()
        val worker = QueuedShadowExecutor()
        val diagnostics = mutableListOf<GateDShadowTurnDiagnostics>()
        val session = fixture.session(
            pipeline = pipeline,
            observer = ShadowDialogueObserver { observation ->
                ShadowDialogueHypothesis(
                    generation = observation.generation,
                    suggestedTransition = TaskGraphTransitionId("offer-slot"),
                    slotCandidates = mapOf(
                        TaskGraphSlotId("requested_day") to TaskGraphSlotValue.Text("Jan"),
                    ),
                    confidence = 0.95,
                    diagnostics = mapOf("model_note" to "Jan"),
                )
            },
            worker = worker,
            diagnostics = diagnostics,
        )

        session.start(RecordingSpeechListener(), LocalTextCallSession.PlanTurnListener { })
        checkNotNull(pipeline.selector).select("termin")
        worker.runAll()

        val diagnostic = diagnostics.single()
        assertEquals(GateDShadowValidationStatus.REJECTED, diagnostic.validationStatus)
        assertEquals(SupervisorProposalRejectReason.SLOT_NOT_ALLOWED, diagnostic.rejectReason)
        assertEquals(setOf(TaskGraphSlotId("requested_day")), diagnostic.candidateSlotIds)
        assertFalse(diagnostic.toString().contains("Jan"))
        session.close()
    }

    private fun acceptedHypothesis(observation: ShadowDialogueObservation) = ShadowDialogueHypothesis(
        generation = observation.generation,
        suggestedTransition = TaskGraphTransitionId("offer-slot"),
        slotCandidates = emptyMap(),
        confidence = 0.95,
    )

    private fun fixture(): Fixture {
        val task = CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            mapOf(
                "reply_text" to "Mam środę.",
                "first_name" to "Jan",
            ),
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
            listOf(PhraseMatrixRule("reply", setOf("termin"))),
        )
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
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 0,
            availableFields = setOf(IdentityFieldId.FIRST_NAME),
            authorizedFields = setOf(IdentityFieldId.FIRST_NAME),
            highSensitivityApprovedFields = emptySet(),
            allowedDisclosureStates = mapOf(IdentityFieldId.FIRST_NAME to setOf(start)),
        )
        return Fixture(workflow, plan, phraseMatrix, graph, facts)
    }

    private data class Fixture(
        val workflow: CallWorkflow,
        val plan: CallPlan,
        val phraseMatrix: PhraseMatrix,
        val graph: TaskGraphDefinition,
        val facts: AuthorizedFactSnapshot,
    ) {
        fun session(
            pipeline: FakePipeline,
            observer: ShadowDialogueObserver,
            worker: QueuedShadowExecutor,
            diagnostics: MutableList<GateDShadowTurnDiagnostics>,
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
            gateDShadowObserver = observer,
            gateDShadowExecutor = worker,
            gateDShadowDiagnosticsListener = GateDShadowDiagnosticsListener { diagnostics += it },
        )
    }

    private class QueuedShadowExecutor : GateDShadowExecutor {
        private val queued = ArrayDeque<() -> Unit>()
        var closed = false
            private set

        override fun execute(block: () -> Unit) {
            queued.addLast(block)
        }

        override fun close() {
            closed = true
        }

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
        var closed = false

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

        override fun close() {
            closed = true
        }
    }

    private class RecordingSpeechListener : LocalSpeechTextPipeline.Listener {
        override fun onSpeechInputReady() = Unit
        override fun onUserTranscript(text: String) = Unit
        override fun onApprovedText(text: String) = Unit
        override fun onOutputPcm16Mono16k(pcm: ByteArray) = Unit
        override fun onDroppedText() = Unit
        override fun onError(reason: String) = error(reason)
    }
}
