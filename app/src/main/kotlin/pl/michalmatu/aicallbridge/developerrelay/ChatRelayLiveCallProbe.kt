package pl.michalmatu.aicallbridge.developerrelay

import pl.michalmatu.aicallbridge.campaign.ClirCarrierEvidence
import pl.michalmatu.aicallbridge.campaign.ClirDeterministicDialogueBackend
import pl.michalmatu.aicallbridge.campaign.CampaignGemmaDirectPreflight
import pl.michalmatu.aicallbridge.campaign.CampaignGemmaPreflight
import pl.michalmatu.aicallbridge.campaign.ClirRouteEvidence

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import pl.michalmatu.aicallbridge.GateCHybridDiagnostics
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState
import pl.michalmatu.aicallbridge.GateCHybridDialogueBackendFactory
import pl.michalmatu.aicallbridge.agent.CallCommitmentConsumptionEvidence
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallExternalEffect
import pl.michalmatu.aicallbridge.agent.CallExternalEffectCompletionTracker
import pl.michalmatu.aicallbridge.agent.CallExternalEffectSuccessEvidence
import pl.michalmatu.aicallbridge.agent.CallExternalEffectValidation
import pl.michalmatu.aicallbridge.agent.CallExternalEffectValidator
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.identity.AndroidIdentityVault
import pl.michalmatu.aicallbridge.identity.CallTaskMode
import pl.michalmatu.aicallbridge.identity.DefaultFactDisclosurePolicy
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import pl.michalmatu.aicallbridge.audioengines.SpeechInputId
import pl.michalmatu.aicallbridge.audioengines.SpeechOutputId
import pl.michalmatu.aicallbridge.audioengines.PiperServiceContract
import pl.michalmatu.aicallbridge.audioengines.PiperAndroidTtsOutputProvider
import pl.michalmatu.aicallbridge.audioengines.AndroidTtsOutputProvider
import pl.michalmatu.aicallbridge.audioengines.ParakeetSpeechInputProvider
import pl.michalmatu.aicallbridge.audioengines.GemmaAudioDirectInputProvider
import pl.michalmatu.aicallbridge.audioengines.AndroidSttInputProvider
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechRecoverableFailure
import pl.michalmatu.aicallbridge.localspeech.PcmEndOfUtteranceDetector
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.session.CallMediaEndpointLease
import pl.michalmatu.aicallbridge.session.CallMediaSessionRuntime
import pl.michalmatu.aicallbridge.session.CallMediaSessionSnapshot
import pl.michalmatu.aicallbridge.session.CallMediaSessionState
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.textagent.AuthorizedPhoneFactBackend
import pl.michalmatu.aicallbridge.textagent.AuthorizedPhoneFactScope
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Developer-only bounded multi-turn proof:
 * frozen telephony RX -> local STT -> interactive chat relay -> approval -> local TTS -> frozen TX.
 *
 * It intentionally reuses production speech/backend/approval boundaries and owns no model runtime.
 */
internal object ChatRelayLiveCallProbe {
    /** Stops *only* the application audio/media session, never the cellular call. */
    internal fun interface SessionControl {
        fun stopAudio()
    }

    private val activeSession = AtomicReference<Run?>(null)

    const val REPORT_FILE = "chat-relay-live-call-report.txt"
    /** Campaign dialogue ends on factual completion, remote disconnect or STOP, not turn #10. */
    const val TASK_SCOPED_TURNS = Int.MAX_VALUE
    private const val RESPONSE_TIMEOUT_MS = 90_000L
    private const val NO_SPEECH_RETRY_DELAY_MS = 750L
    private const val ORANGE_SUPPORT_NUMBER = "*100"
    private const val COMMIT_CLIR_ENABLE = "[[COMMIT_CLIR_ENABLE]]"
    private const val REVIEWED_CLIR_COMMIT_SPEECH =
        "Potwierdzam. Proszę włączyć blokadę prezentacji numeru, usługę CLIR."

    fun run(
        context: Context,
        sessionId: String,
        maxTurns: Int,
        phoneDisclosureAuthorized: Boolean,
        supervisorRelayEnabled: Boolean = true,
        liveAuthorization: () -> Boolean = { true },
        readOnlySupervised: Boolean = false,
        supervisedGemmaEnabled: Boolean = false,
        callStillActive: () -> Boolean = { true },
        evidenceRecorder: CallSessionEvidenceRecorder? = null,
        runConfiguration: ConversationRunConfiguration,
        callback: (String) -> Unit,
    ): SessionControl? {
        ChatRelayEnvelope(sessionId, 1, "probe").validate()
        require(AudioEngineRegistry.incompatibility(runConfiguration.audioProfile) == null) {
            "audio_engine_profile_unavailable"
        }
        require(maxTurns > 0) { "invalid_relay_max_turns" }
        require(!readOnlySupervised || (!phoneDisclosureAuthorized && !supervisorRelayEnabled)) {
            "supervised_probe_must_be_read_only_local"
        }
        val appContext = context.applicationContext
        if (supervisedGemmaEnabled &&
            AndroidGemma4ModelReadiness.check(appContext).state != Gemma4ModelReadinessState.READY
        ) {
            callback(immediateReport("gemma_model_not_ready"))
            return null
        }
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null || audioManager.mode != AudioManager.MODE_IN_CALL) {
            callback(immediateReport("cellular_call_not_active"))
            return null
        }
        require(!supervisedGemmaEnabled || readOnlySupervised) {
            "gemma_supervised_requires_read_only_session"
        }
        val session = Run(
            appContext,
            sessionId,
            maxTurns,
            phoneDisclosureAuthorized,
            callback,
            supervisorRelayEnabled,
            liveAuthorization,
            readOnlySupervised,
            supervisedGemmaEnabled,
            callStillActive,
            evidenceRecorder,
            runConfiguration,
        )
        if (!activeSession.compareAndSet(null, session)) {
            evidenceRecorder?.finish("audio_session_already_active")
            callback(immediateReport("audio_session_already_active"))
            return null
        }
        session.start()
        return session
    }

    private class Run(
        private val context: Context,
        private val sessionId: String,
        private val maxTurns: Int,
        private val phoneDisclosureAuthorized: Boolean,
        private val callback: (String) -> Unit,
        private val supervisorRelayEnabled: Boolean,
        private val liveAuthorization: () -> Boolean,
        private val readOnlySupervised: Boolean,
        private val supervisedGemmaEnabled: Boolean,
        private val callStillActive: () -> Boolean,
        private val evidenceRecorder: CallSessionEvidenceRecorder?,
        private val runConfiguration: ConversationRunConfiguration,
    ) : SessionControl {
        override fun stopAudio() {
            // Revoke TX synchronously, even if the main-thread cleanup is delayed.
            stopRequested.set(true)
            handler.post { finish(false, "operator_stopped") }
        }

        private val handler = Handler(Looper.getMainLooper())
        private val finished = AtomicBoolean(false)
        private val stopRequested = AtomicBoolean(false)
        private val mediaTurnStarted = AtomicBoolean(false)
        private val supervisedGemmaReplies = AtomicInteger(0)
        private val supervisedFallbackReplies = AtomicInteger(0)
        private val quickAckPcm = AtomicReference<ByteArray?>(null)
        private val lastQuickAckTurn = AtomicInteger(0)
        private val lastPrivateDecisionTraceTurn = AtomicInteger(0)
        private val finalAudioReadyTurn = AtomicInteger(0)
        private val quickAckSynthesizer = LocalTtsSpeechOutput(
            context, runConfiguration.audioProfile.languageTag, runConfiguration.audioProfile.voiceId,
            enginePackage = if (runConfiguration.audioProfile.output == SpeechOutputId.PIPER_PL)
                PiperServiceContract.PACKAGE else null,
        )
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ChatRelayLiveCall").apply { isDaemon = true }
        }
        private val mailbox = ChatRelayMailbox(File(context.filesDir, ChatRelayMailbox.DIRECTORY_NAME))
        private val target = if (readOnlySupervised) {
            CallResolvedTarget("User-controlled existing call", "manual-call")
        } else {
            CallResolvedTarget("Orange support", ORANGE_SUPPORT_NUMBER)
        }
        private val task = if (readOnlySupervised) {
            CallTask(
                "Supervised sound test", "READ_ONLY_AUDIO_TEST", "audio",
                CallConstraints.unconstrained(), CallPreferences.none(), emptyMap(),
            )
        } else {
            CallTask(
                "Orange CLIR enable", "SET_SERVICE", "CLIR",
                CallConstraints.unconstrained(), CallPreferences.none(),
                mapOf(CallExternalEffectValidator.SERVICE_ENABLED_FACT to "true"),
            )
        }
        private val effect by lazy {
            CallExternalEffect.SetService(target, CallService.CLIR, true)
        }
        private val workflow = activeWorkflow(task, target)
        private val commitmentGate = CallCommitmentGate { "clir-live-${UUID.randomUUID()}" }
        private val hybridDiagnostics = GateCHybridDiagnostics()
        private val routeVerified = AtomicBoolean(false)
        private val commitmentConsumed = AtomicBoolean(false)
        private val activeMediaGeneration = AtomicLong(-1L)
        private val identityVault by lazy { AndroidIdentityVault.create(context) }
        private val factDisclosurePolicy = DefaultFactDisclosurePolicy()
        private val phoneDisclosureState = TaskGraphStateId("clir-service-number-request")
        private val localPhoneFactBackend by lazy { AuthorizedPhoneFactBackend(
            vault = identityVault,
            policy = factDisclosurePolicy,
            scopeProvider = {
                AuthorizedPhoneFactScope(
                    task = task,
                    target = target,
                    state = phoneDisclosureState,
                    generation = activeMediaGeneration.get(),
                    mode = CallTaskMode.GENUINE,
                    authorized = phoneDisclosureAuthorized && routeVerified.get() &&
                        activeMediaGeneration.get() > 0L && authorizedNow(),
                )
            },
            onDisclosure = { lines += "identity_phone_disclosure_local=true" },
        ) }
        @Volatile private var completionTracker: CallExternalEffectCompletionTracker? = null
        private val pendingExternalSuccessEvidence = AtomicBoolean(false)
        private val hybridBackend by lazy { GateCHybridDialogueBackendFactory.create(
            context = context,
            provider = TextLlmProvider.LOCAL_GEMMA_4,
            relaySessionId = sessionId,
            diagnostics = hybridDiagnostics,
            authorizedFactBackend = localPhoneFactBackend,
            effectCommitControl = COMMIT_CLIR_ENABLE,
            canConfirmEffect = {
                !readOnlySupervised && activeMediaGeneration.get() > 0L && authorizedNow() &&
                    routeVerified.get() && !commitmentConsumed.get()
            },
            supervisorRelayEnabled = supervisorRelayEnabled,
            // Only the app-owned campaign may consume its already initialized engine.
            // Supervised and developer probes keep independent model ownership.
            preparedClassifier = (if (!readOnlySupervised && !supervisorRelayEnabled) {
                CampaignGemmaPreflight.takePrepared(runConfiguration.audioProfile)
            } else null) ?: CampaignGemmaPreflight.createClassifier(context, runConfiguration.audioProfile),
        ) }
        private val directBackend by lazy {
            val direct = runConfiguration as ConversationRunConfiguration.ModelDirect
            GemmaDirectDialogueBackendFactory.create(
                model = direct.acquireModel(
                    takePrepared = { profile, instruction ->
                        CampaignGemmaDirectPreflight.takePrepared(instruction, profile)
                    },
                    create = { profile, instruction ->
                        DialogueEngineProviders.resolve(profile).createDirect(context, instruction)
                    },
                ),
                authorizedPhoneFact = localPhoneFactBackend,
                canConfirmEffect = {
                    !readOnlySupervised && activeMediaGeneration.get() > 0L &&
                        authorizedNow() && routeVerified.get() && !commitmentConsumed.get()
                },
                observer = GemmaDirectObserver { action, source, elapsedMs ->
                    lines += "turn_${currentTurn}_direct_action=$action"
                    lines += "turn_${currentTurn}_direct_response_source=$source"
                    lines += "turn_${currentTurn}_direct_inference_ms=$elapsedMs"
                    evidenceRecorder?.recordDialogueDecision(
                        turn = currentTurn,
                        actionId = action,
                        responseSource = source,
                        classifierWallMs = elapsedMs,
                        localErrorPresent = source == "DIRECT_REJECTED" ||
                            source == "DIRECT_MODEL_ERROR",
                    )
                },
            )
        }
        private val backend: TextCallAgentBackend = if (!readOnlySupervised &&
            runConfiguration is ConversationRunConfiguration.ModelDirect
        ) {
            // F-046 raw speech-only experiment. No PhraseMatrix/JSON parser,
            // no model-callable fact/disclosure/effect tools, and no commitment
            // control-token interpreter. Existing live grant, owned-call,
            // speech-approval, STOP and TX gates still run in the pipeline.
            directBackend
        } else if (readOnlySupervised) {
            if (supervisedGemmaEnabled) {
                SupervisedGemmaDialogueBackend(
                    context = context,
                    onModelReply = { supervisedGemmaReplies.incrementAndGet() },
                    onFallbackReply = { supervisedFallbackReplies.incrementAndGet() },
                )
            } else {
                SupervisedReadOnlyDialogueBackend()
            }
        } else {
            controlAwareBackend(
                ClirDeterministicDialogueBackend(hybridBackend) { ruleId ->
                    lines += "clir_phrase_matrix_rule=$ruleId"
                },
            )
        }
        private val outputApprovalPolicy = CallTextAgentOutputApprovalPolicy(workflow, commitmentGate)
        private val pipeline = LocalSpeechTextPipeline(
            context,
            backend,
            outputApprovalPolicy,
            languageTag = runConfiguration.audioProfile.languageTag,
            voiceId = runConfiguration.audioProfile.voiceId,
            input = when (runConfiguration.audioProfile.input) {
                SpeechInputId.ANDROID_STT -> AndroidSttInputProvider(context, runConfiguration.audioProfile.languageTag)
                SpeechInputId.PARAKEET_PL -> ParakeetSpeechInputProvider(context)
                SpeechInputId.GEMMA_AUDIO_DIRECT -> GemmaAudioDirectInputProvider(context)
            },
            output = when (runConfiguration.audioProfile.output) {
                SpeechOutputId.ANDROID_TTS_PL -> AndroidTtsOutputProvider(
                    context, runConfiguration.audioProfile.languageTag, runConfiguration.audioProfile.voiceId,
                )
                SpeechOutputId.PIPER_PL -> PiperAndroidTtsOutputProvider(
                    context, runConfiguration.audioProfile.languageTag, runConfiguration.audioProfile.voiceId,
                )
                SpeechOutputId.KOKORO, SpeechOutputId.POCKET_TTS ->
                    error("unavailable_polish_output_engine")
            },
        )
        private val lines = CallDiagnosticEvidence(listOf(
            "probe=chat_relay_live_call",
            "supervised_read_only=$readOnlySupervised",
            "dialogue_mode=${if (runConfiguration is ConversationRunConfiguration.ModelDirect) "gemma_direct" else "script_gemma"}",
            "supervised_dialogue_mode=${if (supervisedGemmaEnabled) "gemma4" else "script"}",
            "developer_relay=$supervisorRelayEnabled",
            "hybrid_dialogue=${if (supervisorRelayEnabled) "gemma_then_chatgpt" else "gemma_local_only"}",
            "clir_commit_control=$COMMIT_CLIR_ENABLE",
            "call_required=true",
            "session_id=$sessionId",
            "max_turns=$maxTurns",
            "approval_policy=application_owned",
            "phone_disclosure_authorized=$phoneDisclosureAuthorized",
            "endpointing=trailing_silence",
            "timestamp_utc=${Instant.now()}",
            "private_audio_recording=${evidenceRecorder != null}",
        ))
        private var mediaRuntime: CallMediaSessionRuntime? = null
        private var downlinkPump: ChatRelayDownlinkPump? = null
        private var turnsCompleted = 0
        private var currentTurn = 0
        private var turnStartedAtMs = 0L
        private var estimatedSpeechEndElapsedMs: Long? = null
        private class ActiveRxCapture(
            val turn: Int,
            initial: PcmEndOfUtteranceDetector.Snapshot,
        ) {
            val latest = AtomicReference(initial)
            val complete = AtomicBoolean(false)
        }
        private val activeRxCapture = AtomicReference<ActiveRxCapture?>(null)
        private var liveChatToken = 0L
        private var totalRxBytes = 0L
        private var totalTxBytes = 0L
        private var silenceWindows = 0L
        private var turnDecisionBaseline = 0
        private var turnResponseSourceBaseline = 0
        private var turnLocalSkillErrorBaseline = 0
        private var turnClassifierTimingBaseline = 0

        private val timeout = Runnable { finish(false, "probe_timeout") }
        private val authorityWatchdog = object : Runnable {
            override fun run() {
                if (finished.get()) return
                if (!authorizedNow()) finish(false, authorityFailureReason())
                else handler.postDelayed(this, 500L)
            }
        }

        private fun authorizedNow(): Boolean =
            !stopRequested.get() && runCatching { liveAuthorization() }.getOrDefault(false)

        private fun authorityFailureReason(): String =
            if (!runCatching { callStillActive() }.getOrDefault(false)) "owned_call_ended"
            else "campaign_authority_revoked_or_expired"

        fun start() {
            if (!authorizedNow()) {
                finish(false, authorityFailureReason())
                return
            }
            // Only an already authorized, active owner-controlled session
            // publishes private live text to the local UI memory.
            liveChatToken = LiveCallChatFeed.begin()
            handler.postDelayed(authorityWatchdog, 500L)
            mailbox.clear()
            if (!readOnlySupervised && !supervisorRelayEnabled) {
                // Preparation is only for the authorized app-owned campaign, not any dev relay.
                // Preparation is optional: a TTS constructor/service failure cannot abort the call.
                runCatching { quickAckSynthesizer.synthesize(
                    QuickAcknowledgementPolicy.TEXT,
                    object : LocalTtsSpeechOutput.Listener {
                        override fun onPcm16Mono16k(pcm: ByteArray) {
                            if (!finished.get() && pcm.isNotEmpty()) {
                                quickAckPcm.set(pcm)
                                lines += "quick_ack_local_pcm_ready=true"
                            }
                        }

                        override fun onError(reason: String) {
                            lines += "quick_ack_prewarm_error=${sanitize(reason)}"
                        }
                    },
                ) }.onFailure { error ->
                    lines += "quick_ack_prewarm_error=${error.javaClass.simpleName}"
                }
            }
            try {
                mediaRuntime = CallMediaSessionRuntime(
                    context,
                    Consumer { snapshot -> onMediaSnapshot(snapshot) },
                )
                // Supervised bounded probes keep their explicit test timeout.
                // Authorized campaign calls terminate by actual call state/STOP instead.
                if (maxTurns != TASK_SCOPED_TURNS) {
                    handler.postDelayed(timeout, maxTurns.toLong() * 105_000L + 10_000L)
                }
                val generation = mediaRuntime!!.coordinator().start(LocalSpeechFormat.SAMPLE_RATE_HZ)
                lines += "media_generation=$generation"
            } catch (error: Throwable) {
                finish(false, "media_launch_${error.javaClass.simpleName}")
            }
        }

        private fun onMediaSnapshot(snapshot: CallMediaSessionSnapshot) {
            if (finished.get()) return
            when (snapshot.state) {
                CallMediaSessionState.ACTIVE -> {
                    activeMediaGeneration.set(snapshot.generation)
                    if (mediaTurnStarted.compareAndSet(false, true)) {
                        context.mainExecutor.execute { startTurns(snapshot.generation) }
                    }
                }
                CallMediaSessionState.FAILED -> context.mainExecutor.execute {
                    activeMediaGeneration.set(-1L)
                    finish(false, "media_${snapshot.failure.name.lowercase()}")
                }
                else -> Unit
            }
        }

        private fun startTurns(generation: Long) {
            if (finished.get()) return
            val lease = try {
                mediaRuntime?.coordinator()?.activeEndpointLease(generation)
                    ?: throw IllegalStateException("media_runtime_unavailable")
            } catch (error: Throwable) {
                finish(false, "endpoint_${error.javaClass.simpleName}")
                return
            }
            lines += "endpoint_lease_acquired=true"
            try {
                downlinkPump = ChatRelayDownlinkPump(
                    input = lease.downlink(),
                    frameBytes = LocalSpeechFormat.bytesForDurationMs(20),
                ).also { it.start() }
                lines += "downlink_pump_started=true"
            } catch (error: Throwable) {
                finish(false, "downlink_pump_${error.javaClass.simpleName}")
                return
            }
            beginNextTurn(generation, lease)
        }

        private fun beginNextTurn(generation: Long, lease: CallMediaEndpointLease) {
            if (finished.get()) return
            if (activeMediaGeneration.get() != generation) {
                finish(false, "stale_media_generation")
                return
            }
            if (!authorizedNow()) { finish(false, authorityFailureReason()); return }
            if (maxTurns != TASK_SCOPED_TURNS && turnsCompleted >= maxTurns) {
                finish(true)
                return
            }
            currentTurn = turnsCompleted + 1
            silenceWindows = 0L
            val diagnosticsBeforeTurn = hybridDiagnostics.snapshot()
            turnDecisionBaseline = diagnosticsBeforeTurn.decisions.size
            turnResponseSourceBaseline = diagnosticsBeforeTurn.responseSources.size
            turnLocalSkillErrorBaseline = diagnosticsBeforeTurn.localSkillErrors.size
            turnClassifierTimingBaseline = diagnosticsBeforeTurn.classifierLatenciesMs.size
            turnStartedAtMs = SystemClock.elapsedRealtime()
            estimatedSpeechEndElapsedMs = null
            lines += "turn_${currentTurn}_started=true"
            LiveCallChatFeed.stage(liveChatToken, "Tura " + currentTurn + " • przygotowanie STT")
            pipeline.start(object : LocalSpeechTextPipeline.Listener {
                override fun onSpeechInputReady() {
                    lines += "turn_${currentTurn}_stt_ready=true"
                    lines += "turn_${currentTurn}_stt_ready_elapsed_ms=${elapsedTurnMs()}"
                    LiveCallChatFeed.stage(liveChatToken, "Tura " + currentTurn + " • słucham rozmówcy…")
                    beginCaptureInputTurn(currentTurn, generation, lease)
                }

                override fun onUserTranscript(text: String) {
                    if (finished.get()) return
                    if (!authorizedNow()) { finish(false, authorityFailureReason()); return }
                    evidenceRecorder?.recordTranscript(currentTurn, fromAgent = false, text = text)
                    LiveCallChatFeed.heard(liveChatToken, currentTurn, text)
                    LiveCallChatFeed.stage(liveChatToken, "Tura " + currentTurn + " • Gemma analizuje wypowiedź…")
                    lines += "turn_${currentTurn}_stt_chars=${text.length}"
                    lines += "turn_${currentTurn}_stt_elapsed_ms=${elapsedTurnMs()}"
                    val routeEvidence = ClirRouteEvidence.isExplicitServiceContext(text)
                    if (routeEvidence) {
                        lines += "turn_${currentTurn}_clir_route_evidence=true"
                        if (routeVerified.compareAndSet(false, true)) {
                            lines += "clir_route_verified=true"
                        }
                    }
                    if (commitmentConsumed.get() && ClirCarrierEvidence.isConfirmedCarrierStatement(text)) {
                        pendingExternalSuccessEvidence.set(true)
                        lines += "clir_success_evidence_detected=true"
                        // Carrier evidence does not depend on completing another local TTS turn.
                        handler.post {
                            if (!finished.get() && pendingExternalSuccessEvidence.compareAndSet(true, false)) {
                                if (finalizeExternalSuccess()) finish(true)
                            }
                        }
                    }
                }

                override fun onApprovedText(text: String) {
                    if (finished.get()) return
                    if (!authorizedNow()) { finish(false, authorityFailureReason()); return }
                    evidenceRecorder?.recordTranscript(currentTurn, fromAgent = true, text = text)
                    LiveCallChatFeed.replied(
                        liveChatToken, currentTurn, text,
                        direct = !readOnlySupervised && runConfiguration is ConversationRunConfiguration.ModelDirect,
                    )
                    LiveCallChatFeed.stage(liveChatToken, "Tura " + currentTurn + " • synteza TTS…")
                    lines += "turn_${currentTurn}_approved_chars=${text.length}"
                    lines += "turn_${currentTurn}_approved_elapsed_ms=${elapsedTurnMs()}"
                    recordTurnDialogueDiagnostics(currentTurn)
                }

                override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                    if (finished.get() || !authorizedNow()) { finish(false, authorityFailureReason()); return }
                    if (activeMediaGeneration.get() != generation) { finish(false, "stale_media_generation"); return }
                    val outputTurn = currentTurn
                    finalAudioReadyTurn.set(outputTurn)
                    lines += "turn_${outputTurn}_output_pcm_ready_elapsed_ms=${elapsedTurnMs()}"
                    LiveCallChatFeed.stage(liveChatToken, "Tura " + outputTurn + " • wysyłanie głosu…")
                    executor.execute { writeOutputTurn(lease, pcm, outputTurn, generation) }
                }

                override fun onDroppedText() {
                    recordTurnDialogueDiagnostics(currentTurn)
                    finish(false, "output_dropped")
                }

                override fun onError(reason: String) {
                    recordTurnDialogueDiagnostics(currentTurn)
                    if (LocalSpeechRecoverableFailure.isTransientNoTranscript(reason)) {
                        val retryTurn = currentTurn
                        lines += "turn_${retryTurn}_stt_transient_no_transcript=true"
                        // The SpeechRecognizer has already closed its pipe. Detach
                        // its RX capture before the next frame tries to write
                        // into that closed pipe and incorrectly ends the call.
                        val rxAbandoned = downlinkPump?.abandonActiveCapture() == true
                        lines += "turn_${retryTurn}_stt_rx_abandoned=$rxAbandoned"
                        // A no-match is not a failed task or reason to redial.
                        // Re-open the same listening turn if exact Call/grant still
                        // owns the media session. No arbitrary retry ceiling.
                        handler.postDelayed({
                            if (finished.get() || currentTurn != retryTurn) return@postDelayed
                            if (!authorizedNow()) {
                                finish(false, authorityFailureReason())
                            } else if (activeMediaGeneration.get() != generation) {
                                finish(false, "stale_media_generation")
                            } else {
                                beginNextTurn(generation, lease)
                            }
                        }, NO_SPEECH_RETRY_DELAY_MS)
                    } else {
                        finish(false, reason)
                    }
                }
            })
        }

        private fun beginCaptureInputTurn(
            turn: Int,
            generation: Long,
            lease: CallMediaEndpointLease,
        ) {
            val pump = downlinkPump ?: run {
                finish(false, "turn_${turn}_downlink_pump_unavailable")
                return
            }
            val detector = PcmEndOfUtteranceDetector(
                trailingSilenceMs = 1_500,
                maxCaptureMs = 60_000,
            )
            val captureStartedElapsedMs = elapsedTurnMs()
            val evidenceRx = evidenceRecorder?.openRxTurn(turn)
            var total = 0
            var endpoint = detector.acceptPcm16(ByteArray(0))
            val capture = ActiveRxCapture(turn, endpoint)
            activeRxCapture.set(capture)
            try {
                pump.beginCapture(object : ChatRelayDownlinkPump.Capture {
                    override fun onFrame(bytes: ByteArray, length: Int): Boolean {
                        if (finished.get()) return false
                        if (!pipeline.writeInputPcm(bytes, 0, length)) {
                            throw IllegalStateException("stt_pcm_write_failed")
                        }
                        evidenceRx?.append(bytes, length)
                        total += length
                        endpoint = detector.acceptPcm16(bytes, 0, length)
                        capture.latest.set(endpoint)
                        return !endpoint.shouldStop && !finished.get()
                    }

                    override fun onAbandoned() {
                        capture.complete.set(true)
                        activeRxCapture.compareAndSet(capture, null)
                        // STT no-match while downlink capture is still running.
                        // Abandon just that capture; the Call's RX pump stays alive.
                        evidenceRx?.finish()
                        totalRxBytes += total.toLong()
                    }

                    override fun onFinished() {
                        capture.complete.set(true)
                        activeRxCapture.compareAndSet(capture, null)
                        evidenceRx?.finish()
                        totalRxBytes += total.toLong()
                        lines += "turn_${turn}_rx_pcm_bytes=$total"
                        lines += "turn_${turn}_endpoint_reason=${endpoint.reason?.name?.lowercase() ?: "capture_finished"}"
                        lines += "turn_${turn}_endpoint_capture_ms=${endpoint.capturedMs}"
                        lines += "turn_${turn}_speech_detected=${endpoint.speechDetected}"
                        endpoint.estimatedSpeechEndMs?.let { speechEndMs ->
                            val estimated = captureStartedElapsedMs + speechEndMs
                            estimatedSpeechEndElapsedMs = estimated
                            lines += "turn_${turn}_estimated_eos_elapsed_ms=$estimated"
                        }
                        if (!endpoint.speechDetected) {
                            // Silence or waiting in the operator's IVR is not task failure.
                            // Keep listening while the exact call and grant remain active.
                            silenceWindows = (silenceWindows + 1L).coerceAtMost(Long.MAX_VALUE)
                            lines += "turn_${turn}_no_speech_window=$silenceWindows"
                            handler.postDelayed({
                                if (finished.get() || turn != currentTurn) return@postDelayed
                                if (!authorizedNow()) finish(false, authorityFailureReason())
                                else beginCaptureInputTurn(turn, generation, lease)
                            }, NO_SPEECH_RETRY_DELAY_MS)
                            return
                        }
                        silenceWindows = 0L
                        // The listener still generates its full answer on the existing
                        // independent Gemma/STT path. No waiting for this optional filler.
                        pipeline.finishInput()
                        scheduleQuickAcknowledgement(turn, generation, lease)
                    }

                    override fun onError(reason: String) {
                        capture.complete.set(true)
                        activeRxCapture.compareAndSet(capture, null)
                        evidenceRx?.finish()
                        if (finished.get()) return
                        context.mainExecutor.execute {
                            finish(false, "turn_${turn}_rx_${sanitize(reason)}")
                        }
                    }
                })
            } catch (error: Throwable) {
                capture.complete.set(true)
                activeRxCapture.compareAndSet(capture, null)
                evidenceRx?.finish()
                finish(false, "turn_${turn}_rx_${error.javaClass.simpleName}")
            }
        }

        private fun scheduleQuickAcknowledgement(
            turn: Int,
            generation: Long,
            lease: CallMediaEndpointLease,
        ) {
            if (readOnlySupervised || supervisorRelayEnabled) return
            handler.postDelayed({
                val pcm = quickAckPcm.get()
                if (!QuickAcknowledgementPolicy.shouldPlay(
                        authorized = !finished.get() && authorizedNow() &&
                            runCatching { callStillActive() }.getOrDefault(false) &&
                            currentTurn == turn && activeMediaGeneration.get() == generation &&
                            outputApprovalPolicy.evaluate(QuickAcknowledgementPolicy.TEXT) ==
                                TextOutputDecision.RELEASE,
                        readOnlySupervised = readOnlySupervised,
                        speechDetected = true,
                        clipReady = pcm != null,
                        turn = turn,
                        lastAcknowledgedTurn = lastQuickAckTurn.get(),
                        finalAudioReadyForTurn = finalAudioReadyTurn.get(),
                    )
                ) return@postDelayed
                if (lastQuickAckTurn.getAndSet(turn) >= turn) return@postDelayed
                val ready = pcm ?: return@postDelayed
                runCatching {
                    executor.execute { writeQuickAcknowledgement(lease, ready, turn, generation) }
                }.onFailure { error ->
                    lines += "turn_${turn}_quick_ack_queue_error=${error.javaClass.simpleName}"
                }
            }, QuickAcknowledgementPolicy.AFTER_SPEECH_END_DELAY_MS)
        }

        private fun writeQuickAcknowledgement(
            lease: CallMediaEndpointLease,
            pcm: ByteArray,
            turn: Int,
            generation: Long,
        ) {
            if (finished.get() || !authorizedNow() ||
                !runCatching { callStillActive() }.getOrDefault(false) ||
                currentTurn != turn || activeMediaGeneration.get() != generation ||
                finalAudioReadyTurn.get() == turn ||
                outputApprovalPolicy.evaluate(QuickAcknowledgementPolicy.TEXT) !=
                    TextOutputDecision.RELEASE
            ) return
            try {
                val atMs = elapsedTurnMs()
                val evidenceAtMs = evidenceRecorder?.elapsed() ?: 0L
                val txWriteStarted = SystemClock.elapsedRealtime()
                val sent = AuthorizedPcmUplinkWriter.write(
                    uplink = lease.uplink(),
                    pcm = pcm,
                    isAuthorized = {
                        !finished.get() && authorizedNow() &&
                            runCatching { callStillActive() }.getOrDefault(false) &&
                            currentTurn == turn && activeMediaGeneration.get() == generation
                    },
                )
                val txWriteWallMs =
                    (SystemClock.elapsedRealtime() - txWriteStarted).coerceAtLeast(0L)
                evidenceRecorder?.recordAcknowledgementAudio(turn, pcm, sent, evidenceAtMs)
                totalTxBytes += sent.toLong()
                lines += "turn_${turn}_quick_ack_tx_bytes=$sent"
                lines += "turn_${turn}_quick_ack_tx_elapsed_ms=$atMs"
                lines += "turn_${turn}_quick_ack_write_wall_ms=$txWriteWallMs"
                estimatedSpeechEndElapsedMs?.let { speechEndMs ->
                    lines += "turn_${turn}_eos_to_quick_ack_tx_ms=${(atMs - speechEndMs).coerceAtLeast(0L)}"
                }
                if (sent != pcm.size) return
                // Writes may already have blocked while telephony consumed frames.
                // Keep the serial TX fence only for remaining playback + guard,
                // not a second full audio duration. Gemma inference is unaffected.
                val postWriteHoldMs = ChatRelayTurnPacing.acknowledgementPostWriteHoldMs(
                    pcmBytes = pcm.size,
                    txWriteWallMs = txWriteWallMs,
                )
                lines += "turn_${turn}_quick_ack_post_write_hold_ms=$postWriteHoldMs"
                Thread.sleep(postWriteHoldMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Throwable) {
                // Filler is best-effort; the already-running real answer must not fail.
                lines += "turn_${turn}_quick_ack_error=${error.javaClass.simpleName}"
            }
        }

        private fun writeOutputTurn(
            lease: CallMediaEndpointLease,
            pcm: ByteArray,
            turn: Int,
            generation: Long,
        ) {
            try {
                if (finished.get() || !authorizedNow()) return
                if (activeMediaGeneration.get() != generation) return
                if (pcm.isEmpty()) throw IllegalStateException("tts_pcm_empty")
                val firstTxElapsed = elapsedTurnMs()
                val ttsPcmDurationMs = ChatRelayTurnPacing.pcmDurationMs(pcm.size)
                val postTxHoldMs = ChatRelayTurnPacing.postTxHoldMs(pcm.size)
                lines += "turn_${turn}_tts_pcm_duration_ms=$ttsPcmDurationMs"
                lines += "turn_${turn}_post_tx_hold_ms=$postTxHoldMs"
                lines += "turn_${turn}_first_tx_elapsed_ms=$firstTxElapsed"
                estimatedSpeechEndElapsedMs?.let { speechEndMs ->
                    val eosToFullTxMs = (firstTxElapsed - speechEndMs).coerceAtLeast(0L)
                    lines += "turn_${turn}_eos_to_first_tx_ms=$eosToFullTxMs"
                    lines += "turn_${turn}_eos_to_full_tx_ms=$eosToFullTxMs"
                }
                val txWriteStarted = SystemClock.elapsedRealtime()
                val txEvidenceAtMs = evidenceRecorder?.elapsed() ?: 0L
                val bytesWritten = AuthorizedPcmUplinkWriter.write(
                    uplink = lease.uplink(),
                    pcm = pcm,
                    isAuthorized = {
                        !finished.get() && authorizedNow() &&
                            activeMediaGeneration.get() == generation
                    },
                )
                evidenceRecorder?.recordTransmittedAudio(turn, pcm, bytesWritten, txEvidenceAtMs)
                evidenceRecorder?.recordFullTxAttempt(turn, bytesWritten, pcm.size)
                LiveCallChatFeed.tx(liveChatToken, turn, bytesWritten == pcm.size)
                if (bytesWritten != pcm.size || finished.get() || !authorizedNow() ||
                    activeMediaGeneration.get() != generation
                ) {
                    context.mainExecutor.execute { finish(false, "tx_authority_revoked") }
                    return
                }
                val txWriteWallMs =
                    (SystemClock.elapsedRealtime() - txWriteStarted).coerceAtLeast(0L)
                val postWriteHoldMs = ChatRelayTurnPacing.postWriteHoldMs(
                    pcmBytes = pcm.size,
                    txWriteWallMs = txWriteWallMs,
                )
                lines += "turn_${turn}_tx_write_wall_ms=$txWriteWallMs"
                lines += "turn_${turn}_post_write_hold_ms=$postWriteHoldMs"
                totalTxBytes += pcm.size.toLong()
                LiveCallChatFeed.stage(liveChatToken, "Tura " + turn + " • odpowiedź wysłana do TX")
                lines += "turn_${turn}_tx_pcm_bytes=${pcm.size}"
                handler.postDelayed(
                    { completeTurnAfterPlayback(turn, generation, lease) },
                    postWriteHoldMs,
                )
            } catch (error: Throwable) {
                context.mainExecutor.execute {
                    finish(false, "turn_${turn}_tx_${error.javaClass.simpleName}")
                }
            }
        }

        private fun completeTurnAfterPlayback(
            turn: Int,
            generation: Long,
            lease: CallMediaEndpointLease,
        ) {
            if (finished.get() || turn != currentTurn) return
            turnsCompleted = turn
            lines += "turn_${turn}_complete_elapsed_ms=${elapsedTurnMs()}"
            if (!authorizedNow()) { finish(false, authorityFailureReason()); return }
            if (pendingExternalSuccessEvidence.compareAndSet(true, false)) {
                if (!finalizeExternalSuccess()) return
                finish(true)
                return
            }
            if (maxTurns != TASK_SCOPED_TURNS && turnsCompleted >= maxTurns) {
                if (readOnlySupervised) finish(true)
                else finish(false, "clir_external_success_not_observed")
            } else {
                beginNextTurn(generation, lease)
            }
        }

        private fun controlAwareBackend(delegate: TextCallAgentBackend): TextCallAgentBackend =
            object : TextCallAgentBackend {
                override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                    delegate.generate(userText, object : TextCallAgentBackend.Listener {
                        override fun onComplete(text: String) {
                            try {
                                when (text.trim()) {
                                    COMMIT_CLIR_ENABLE -> listener.onComplete(commitClirEnable())
                                    else -> listener.onComplete(text)
                                }
                            } catch (error: Throwable) {
                                listener.onError("clir_control_${error.javaClass.simpleName}")
                            }
                        }

                        override fun onError(reason: String) = listener.onError(reason)
                    })
                }

                override fun cancel() = delegate.cancel()
                override fun close() = delegate.close()
            }

        private fun commitClirEnable(): String {
            require(!readOnlySupervised) { "supervised_external_effect_blocked" }
            require(authorizedNow()) { "campaign_authority_revoked_or_expired" }
            require(activeMediaGeneration.get() > 0L) { "media_generation_unbound" }
            require(routeVerified.get()) { "clir_route_not_verified" }
            require(!commitmentConsumed.get()) { "clir_commitment_already_consumed" }
            val validation = CallExternalEffectValidator.validateSetService(task, target, effect)
            require(validation is CallExternalEffectValidation.Accepted) { "clir_effect_validation_rejected" }
            val authorization = commitmentGate.authorize(validation.effect)
            lines += "clir_effect_permit_issued=true"
            val consumed = commitmentGate.consumeEffect(authorization.value).getOrThrow()
            require(consumed == effect && !commitmentGate.hasAuthorization()) {
                "clir_effect_consumption_mismatch"
            }
            completionTracker = CallExternalEffectCompletionTracker.fromConsumption(
                CallCommitmentConsumptionEvidence(consumed),
            )
            commitmentConsumed.set(true)
            lines += "clir_effect_permit_consumed=true"
            return REVIEWED_CLIR_COMMIT_SPEECH
        }

        /** Carrier claim is provisional; network status verification is a separate acceptance gate. */
        private fun finalizeExternalSuccess(): Boolean {
            if (!authorizedNow()) {
                finish(false, authorityFailureReason())
                return false
            }
            if (completionTracker == null) {
                finish(false, "clir_completion_tracker_missing")
                return false
            }
            lines += "clir_carrier_reported_success=true"
            lines += "clir_network_verification=pending"
            return true
        }

        private fun recordTurnDialogueDiagnostics(turn: Int) {
            val snapshot = hybridDiagnostics.snapshot()
            val decision = snapshot.decisions.drop(turnDecisionBaseline).lastOrNull()
            val source = snapshot.responseSources.drop(turnResponseSourceBaseline).lastOrNull()
            val classifierMs = snapshot.classifierLatenciesMs.drop(turnClassifierTimingBaseline).lastOrNull()
            val localErrorPresent = snapshot.localSkillErrors.drop(turnLocalSkillErrorBaseline).isNotEmpty()
            decision?.let {
                lines += "turn_${turn}_dialogue_action=${it.actionId.name}"
                lines += "turn_${turn}_dialogue_confidence=${it.confidence}"
                if (it.argument != null) {
                    lines += "turn_${turn}_dialogue_argument_present=true"
                }
            }
            source?.let { lines += "turn_${turn}_response_source=${it.name}" }
            classifierMs?.let { lines += "turn_${turn}_gemma_classifier_wall_ms=$it" }
            if (localErrorPresent) lines += "turn_${turn}_local_error_present=true"
            if ((decision != null || source != null || classifierMs != null || localErrorPresent) &&
                lastPrivateDecisionTraceTurn.getAndSet(turn) != turn
            ) {
                evidenceRecorder?.recordDialogueDecision(
                    turn = turn,
                    actionId = decision?.actionId?.name,
                    responseSource = source?.name,
                    classifierWallMs = classifierMs,
                    localErrorPresent = localErrorPresent,
                )
            }
        }

        private fun elapsedTurnMs(): Long =
            (SystemClock.elapsedRealtime() - turnStartedAtMs).coerceAtLeast(0L)

        private fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            activeSession.compareAndSet(this, null)
            activeMediaGeneration.set(-1L)
            handler.removeCallbacks(timeout)
            handler.removeCallbacks(authorityWatchdog)
            // F-047: the owned Call can end before the 1500 ms VAD trailing silence.
            // Preserve only safe metadata; NEVER attempt Gemma/TTS/TX after call teardown.
            activeRxCapture.getAndSet(null)?.let { active ->
                if (!active.complete.get()) {
                    val last = active.latest.get()
                    val trailing = last.estimatedSpeechEndMs?.let { speechEnd ->
                        (last.capturedMs - speechEnd).coerceAtLeast(0L)
                    }
                    val cause = when (reason) {
                        "owned_call_ended" -> "OWNED_CALL_ENDED"
                        "operator_stopped" -> "OPERATOR_STOP"
                        else -> "SESSION_END"
                    }
                    lines += "turn_${active.turn}_rx_interrupted=true"
                    lines += "turn_${active.turn}_rx_interrupted_capture_ms=${last.capturedMs}"
                    lines += "turn_${active.turn}_rx_interrupted_trailing_silence_ms=${trailing ?: -1L}"
                    evidenceRecorder?.recordInterruptedRx(
                        active.turn, last.capturedMs, last.speechDetected, trailing, cause,
                    )
                }
            }
            try { pipeline.close() } catch (_: Throwable) {}
            try { quickAckSynthesizer.close() } catch (_: Throwable) {}
            quickAckPcm.set(null)
            try { mailbox.clear() } catch (_: Throwable) {}
            downlinkPump?.let { pump ->
                lines += "downlink_discarded_pcm_bytes=${pump.discardedBytes()}"
                try { pump.close() } catch (_: Throwable) {}
            }
            downlinkPump = null
            try { mediaRuntime?.coordinator()?.takeOverNow() } catch (_: Throwable) {}
            try { mediaRuntime?.close() } catch (_: Throwable) {}
            mediaRuntime = null
            executor.shutdownNow()
            val hybridSnapshot = hybridDiagnostics.snapshot()
            lines += "gemma_action_decision_count=${hybridSnapshot.decisions.size}"
            lines += "gemma_skill_decision_count=${hybridSnapshot.decisions.size}"
            lines += "host_action_count=${hybridSnapshot.responseSources.count { it.name == "HOST_ACTION" }}"
            lines += "chatgpt_fallback_count=${hybridSnapshot.responseSources.count { it.name == "CHAT_RELAY" }}"
            lines += "clir_route_verified=${routeVerified.get()}"
            lines += "clir_commitment_consumed=${commitmentConsumed.get()}"
            lines += "clir_external_success=false" // never independently verified here
            lines += "supervised_gemma_replies=${supervisedGemmaReplies.get()}"
            lines += "supervised_script_fallback_replies=${supervisedFallbackReplies.get()}"
            lines += "turns_completed=$turnsCompleted"
            lines += "telephony_rx_pcm_bytes=$totalRxBytes"
            lines += "telephony_tx_pcm_bytes=$totalTxBytes"
            lines += "chat_relay_live_call_success=$success"
            if (reason != null) lines += "failure_reason=${sanitize(reason)}"
            lines += "probe_complete=true"
            evidenceRecorder?.finish(reason)
            LiveCallChatFeed.finish(liveChatToken, reason)
            val report = lines.render()
            PrivateDiagnosticReportStore.write(context.filesDir, REPORT_FILE, report)
            context.mainExecutor.execute { callback(report) }
        }
    }

    private fun activeWorkflow(task: CallTask, target: CallResolvedTarget): CallWorkflow {
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        workflow.resolveTarget(target)
        workflow.markDialing()
        workflow.markCallActive()
        return workflow
    }

    private fun immediateReport(reason: String): String =
        "probe=chat_relay_live_call\n" +
            "chat_relay_live_call_success=false\n" +
            "failure_reason=$reason\n" +
            "probe_complete=true\n"

    private fun sanitize(value: String): String =
        CallDiagnosticEvidence.safeCode(value)
}
