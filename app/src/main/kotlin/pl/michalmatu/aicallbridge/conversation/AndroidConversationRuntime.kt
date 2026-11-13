package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.conversationcore.IncomingIdleTimeout
import pl.michalmatu.aicallbridge.conversationcore.ConversationSessionRegistry
import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason
import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination
import pl.michalmatu.aicallbridge.tools.ConversationToolUi


import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.developerrelay.RemoteBargeInMonitor
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
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
import pl.michalmatu.aicallbridge.session.CallMediaEndpointLease
import pl.michalmatu.aicallbridge.session.CallMediaSessionRuntime
import pl.michalmatu.aicallbridge.session.CallMediaSessionSnapshot
import pl.michalmatu.aicallbridge.session.CallMediaSessionState
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

/**
 * Developer-only bounded multi-turn proof:
 * frozen telephony RX -> local STT -> interactive chat relay -> approval -> local TTS -> frozen TX.
 *
 * It intentionally reuses production speech/backend/approval boundaries and owns no model runtime.
 */
import pl.michalmatu.aicallbridge.developerrelay.*


internal object AndroidConversationRuntime {
    /** Stops *only* the application audio/media session, never the cellular call. */
    internal fun interface SessionControl {
        fun stopAudio(reason: ConversationStopReason)
    }

    private val sessions = ConversationSessionRegistry<Run>()

    const val REPORT_FILE = "chat-relay-live-call-report.txt"
    /** Campaign dialogue ends on factual completion, remote disconnect or STOP, not turn #10. */
    const val TASK_SCOPED_TURNS = Int.MAX_VALUE
    private const val RESPONSE_TIMEOUT_MS = 90_000L
    private const val NO_SPEECH_RETRY_DELAY_MS = 750L
    fun run(
        context: Context,
        sessionId: String,
        maxTurns: Int,
        scenario: ConversationScenario,
        liveAuthorization: () -> Boolean = { true },
        callStillActive: () -> Boolean = { true },
        evidenceRecorder: CallSessionEvidenceRecorder? = null,
        runConfiguration: ConversationRunConfiguration,
        callback: (ConversationRunResult) -> Unit,
    ): SessionControl? {
        ChatRelayEnvelope(sessionId, 1, "probe").validate()
        require(AudioEngineRegistry.incompatibility(runConfiguration.audioProfile) == null) {
            "audio_engine_profile_unavailable"
        }
        require(maxTurns > 0) { "invalid_relay_max_turns" }
        val appContext = context.applicationContext
        scenario.readinessFailure()?.let {
            evidenceRecorder?.finish(it)
            callback(immediateReport(it))
            return null
        }
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null || audioManager.mode != AudioManager.MODE_IN_CALL) {
            evidenceRecorder?.finish("cellular_call_not_active")
            callback(immediateReport("cellular_call_not_active"))
            return null
        }
        val session = sessions.tryCreate { Run(
            appContext,
            sessionId,
            maxTurns,
            scenario,
            callback,
            liveAuthorization,
            callStillActive,
            evidenceRecorder ?: CallSessionEvidenceRecorder.create(appContext, recordAudio = false),
            runConfiguration,
        ) } ?: run {
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
        private val scenario: ConversationScenario,
        private val callback: (ConversationRunResult) -> Unit,
        private val liveAuthorization: () -> Boolean,
        private val callStillActive: () -> Boolean,
        private val evidenceRecorder: CallSessionEvidenceRecorder?,
        private val runConfiguration: ConversationRunConfiguration,
    ) : SessionControl {
        override fun stopAudio(reason: ConversationStopReason) {
            // Revoke TX synchronously, even if the main-thread cleanup is delayed.
            outputEpoch.incrementAndGet()
            stopRequested.compareAndSet(null, reason)
            handler.post { finish(ConversationTermination.Stopped(checkNotNull(stopRequested.get()))) }
        }

        private val handler = Handler(Looper.getMainLooper())
        private val mediaTurnStarted = AtomicBoolean(false)
        private val quickAckPcm = AtomicReference<ByteArray?>(null)
        private val lastQuickAckTurn = AtomicInteger(0)
        private val finalAudioReadyTurn = AtomicInteger(0)
        private val quickAckSynthesizer = LocalTtsSpeechOutput(
            context, runConfiguration.audioProfile.languageTag, runConfiguration.audioProfile.voiceId,
            enginePackage = runConfiguration.audioProfile.effectiveTtsEnginePackage,
        )
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ChatRelayLiveCall").apply { isDaemon = true }
        }
        private val readOnlySupervised = scenario.mode == ConversationRuntimeMode.SUPERVISED_ATTACH
        private val supervisorRelayEnabled = scenario.mode == ConversationRuntimeMode.DEVELOPER_RELAY
        private val activeMediaGeneration = AtomicLong(-1L)
        /** Fences queued and in-flight PCM across local turns and STOP on the same Call. */
        private val outputEpoch = AtomicLong(0L)
        /** A no-speech retry creates a new RX attempt without incrementing the turn. */
        private val rxCaptureAttemptSequence = AtomicLong(0L)
        private val lines = CallDiagnosticEvidence(listOf(
            "probe=chat_relay_live_call",
            "supervised_read_only=$readOnlySupervised",
            "dialogue_mode=${if (runConfiguration is ConversationRunConfiguration.ModelDirect) "gemma_direct" else "script_gemma"}",
            "conversation_scenario=${scenario.id}",
            "conversation_direction=${scenario.direction.name}",
            "developer_relay=$supervisorRelayEnabled",
            "hybrid_dialogue=${if (supervisorRelayEnabled) "gemma_then_chatgpt" else "gemma_local_only"}",
            "call_required=true",
            "session_id=$sessionId",
            "max_turns=$maxTurns",
            "approval_policy=application_owned",
            "endpointing=trailing_silence",
            "timestamp_utc=${Instant.now()}",
            "private_audio_recording=${evidenceRecorder?.recordsAudio == true}",
            "private_transcript_capture=${if (evidenceRecorder != null) "enabled" else "unavailable"}",
        ))
        private val idlePolicy = scenario.idlePolicy
        private val idleTimeout = idlePolicy?.let { IncomingIdleTimeout(it.silenceMs, it.graceMs) }
        private var idlePromptTurn = -1
        private val finished = AtomicBoolean(false)
        private val stopRequested = AtomicReference<ConversationStopReason?>(null)
        private var currentTurn = 0
        private val dialogue = scenario.open(ConversationRuntimeHooks(
            authorized = ::authorizedNow,
            mediaGeneration = { activeMediaGeneration.get() },
            turn = { currentTurn },
            emit = { lines += it },
            decisionSink = ConversationDecisionSink { turn, action, response, elapsed, error ->
                evidenceRecorder?.recordDialogueDecision(turn, action, response, elapsed, error)
            },
            sessionId = sessionId,
            toolEventSink = { event ->
                evidenceRecorder?.recordToolEvent(event)
                ConversationChatFeed.tool(liveChatToken, event)
                ConversationToolUi.record(event)
                lines += "turn_${event.turn}_tool_${event.tool.wireName}_${event.stage.name.lowercase()}=${event.result?.name ?: "pending"}"
            },
        ))
        private val pipeline = LocalSpeechTextPipeline(
            context,
            dialogue.backend,
            dialogue.approvalPolicy,
            languageTag = runConfiguration.audioProfile.languageTag,
            voiceId = runConfiguration.audioProfile.voiceId,
            input = when (runConfiguration.audioProfile.input) {
                SpeechInputId.ANDROID_STT -> AndroidSttInputProvider(context, runConfiguration.audioProfile.languageTag)
                SpeechInputId.PARAKEET_PL -> ParakeetSpeechInputProvider(context)
                SpeechInputId.GEMMA_AUDIO_DIRECT -> GemmaAudioDirectInputProvider()
            },
            output = when (runConfiguration.audioProfile.output) {
                SpeechOutputId.ANDROID_TTS_PL -> AndroidTtsOutputProvider(
                    context, runConfiguration.audioProfile.languageTag, runConfiguration.audioProfile.voiceId,
                    runConfiguration.audioProfile.effectiveTtsEnginePackage,
                )
                SpeechOutputId.PIPER_PL -> PiperAndroidTtsOutputProvider(
                    context, runConfiguration.audioProfile.languageTag, runConfiguration.audioProfile.voiceId,
                )
                SpeechOutputId.KOKORO, SpeechOutputId.POCKET_TTS ->
                    error("unavailable_polish_output_engine")
            },
        )

        private var mediaRuntime: CallMediaSessionRuntime? = null
        private var downlinkPump: ChatRelayDownlinkPump? = null
        private val bargeMonitor = AtomicReference<RemoteBargeInMonitor?>(null)
        private var turnsCompleted = 0
        private var turnStartedAtMs = 0L
        private var estimatedSpeechEndElapsedMs: Long? = null
        private class ActiveRxCapture(
            val turn: Int,
            initial: PcmEndOfUtteranceDetector.Snapshot,
        ) {
            val latest = AtomicReference(initial)
            val complete = AtomicBoolean(false)
            val voiceNoticed = AtomicBoolean(false)
            /** Bytes passed into STT, including a turn interrupted by operator STOP. */
            val partialBytes = AtomicLong(0L)
        }
        private val activeRxCapture = AtomicReference<ActiveRxCapture?>(null)
        private var liveChatToken = 0L
        private var totalRxBytes = 0L
        private var totalTxBytes = 0L
        private var silenceWindows = 0L

        private val timeout = Runnable { fail("probe_timeout") }
        private val authorityWatchdog = object : Runnable {
            override fun run() {
                if (finished.get()) return
                if (!authorizedNow()) finish(authorityTermination())
                else handler.postDelayed(this, 500L)
            }
        }

        private fun authorizedNow(): Boolean =
            stopRequested.get() == null && runCatching { liveAuthorization() }.getOrDefault(false)

        private fun authorityTermination(): ConversationTermination = ConversationTermination.Stopped(
            stopRequested.get() ?: if (!runCatching { callStillActive() }.getOrDefault(false))
                ConversationStopReason.CALL_ENDED else ConversationStopReason.POLICY_REVOKED,
        )

        fun start() {
            if (!authorizedNow()) {
                finish(authorityTermination())
                return
            }
            // Only an already authorized, active owner-controlled session
            // publishes private live text to the local UI memory.
            liveChatToken = ConversationChatFeed.begin()
            evidenceRecorder?.recordConversation(scenario.direction, scenario.preparationId, scenario.responseKind)
            try { dialogue.start() } catch (failure: Exception) {
                fail("scenario_start_${failure.javaClass.simpleName}")
                return
            }
            handler.postDelayed(authorityWatchdog, 500L)

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
                fail("media_launch_${error.javaClass.simpleName}")
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
                    fail("media_${snapshot.failure.name.lowercase()}")
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
                fail("endpoint_${error.javaClass.simpleName}")
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
                fail("downlink_pump_${error.javaClass.simpleName}")
                return
            }
            val opening = dialogue.openingText
            if (opening == null) beginNextTurn(generation, lease)
            else {
                currentTurn = 0
                outputEpoch.incrementAndGet()
                turnStartedAtMs = SystemClock.elapsedRealtime()
                pipeline.speakCandidate(opening, turnListener(generation, lease))
            }
        }

        private fun beginNextTurn(generation: Long, lease: CallMediaEndpointLease) {
            if (finished.get()) return
            if (activeMediaGeneration.get() != generation) {
                fail("stale_media_generation")
                return
            }
            if (!authorizedNow()) { finish(authorityTermination()); return }
            if (maxTurns != TASK_SCOPED_TURNS && turnsCompleted >= maxTurns) {
                finish(ConversationTermination.Completed)
                return
            }
            outputEpoch.incrementAndGet()
            currentTurn = turnsCompleted + 1
            idlePromptTurn = -1
            silenceWindows = 0L
            dialogue.beginTurn()
            turnStartedAtMs = SystemClock.elapsedRealtime()
            estimatedSpeechEndElapsedMs = null
            lines += "turn_${currentTurn}_started=true"
            ConversationChatFeed.stage(liveChatToken, "Tura " + currentTurn + " • przygotowanie STT")
            pipeline.start(turnListener(generation, lease))
        }

        private fun turnListener(generation: Long, lease: CallMediaEndpointLease) =
            object : LocalSpeechTextPipeline.Listener {
                private val ownerTurn = currentTurn
                private val ownerEpoch = outputEpoch.get()
                private val owner = ConversationTurnOwner(ownerTurn, ownerEpoch, generation)
                private val transcriptAccepted = AtomicBoolean(false)
                private fun stale() = !owner.isCurrent(
                    currentTurn, outputEpoch.get(), activeMediaGeneration.get(), finished.get(),
                )

                override fun onSpeechInputReady() {
                    if (stale()) return
                    lines += "turn_${currentTurn}_stt_ready=true"
                    lines += "turn_${currentTurn}_stt_ready_elapsed_ms=${elapsedTurnMs()}"
                    ConversationChatFeed.stage(liveChatToken, "Tura " + currentTurn + " • słucham rozmówcy…")
                    beginCaptureInputTurn(currentTurn, generation, lease)
                }

                override fun onUserTranscript(text: String) {
                    if (stale()) return
                    if (!authorizedNow()) { finish(authorityTermination()); return }
                    // A real transcript supersedes any queued STT no-match retry.
                    transcriptAccepted.set(true)
                    evidenceRecorder?.recordTranscript(currentTurn, fromAgent = false, text = text, responseKind = ConversationChatFeed.ResponseKind.UNSPECIFIED)
                    ConversationChatFeed.heard(liveChatToken, currentTurn, text)
                    ConversationChatFeed.stage(liveChatToken, "Tura " + currentTurn + " • model analizuje wypowiedź…")
                    lines += "turn_${currentTurn}_stt_chars=${text.length}"
                    lines += "turn_${currentTurn}_stt_elapsed_ms=${elapsedTurnMs()}"
                    dialogue.onTranscript(currentTurn, text)
                    // Scenario outcome evaluation stays on the same main-thread boundary.
                    handler.post {
                        if (stale()) return@post
                        if (!authorizedNow()) { finish(authorityTermination()); return@post }
                        dialogue.takeOutcome(ConversationOutcomeBoundary.TRANSCRIPT)?.let(::applyOutcome)
                    }
                }

                override fun onApprovedText(text: String) {
                    if (stale()) return
                    if (!authorizedNow()) { finish(authorityTermination()); return }
                    evidenceRecorder?.recordTranscript(currentTurn, fromAgent = true, text = text, responseKind = responseKind())
                    ConversationChatFeed.replied(
                        liveChatToken, currentTurn, text,
                        kind = responseKind(),
                    )
                    ConversationChatFeed.stage(liveChatToken, "Tura " + currentTurn + " • synteza TTS…")
                    lines += "turn_${currentTurn}_approved_chars=${text.length}"
                    lines += "turn_${currentTurn}_approved_elapsed_ms=${elapsedTurnMs()}"
                    dialogue.recordTurnDiagnostics(currentTurn)
                }

                override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                    if (stale()) return
                    if (!authorizedNow()) { finish(authorityTermination()); return }
                    val outputTurn = currentTurn
                    finalAudioReadyTurn.set(outputTurn)
                    lines += "turn_${outputTurn}_output_pcm_ready_elapsed_ms=${elapsedTurnMs()}"
                    ConversationChatFeed.stage(liveChatToken, "Tura " + outputTurn + " • wysyłanie głosu…")
                    if (!readOnlySupervised) armBargeMonitor(outputTurn, generation, lease, ownerEpoch)
                    executor.execute { writeOutputTurn(lease, pcm, outputTurn, generation, ownerEpoch) }
                }

                override fun onDroppedText() {
                    if (stale()) return
                    dialogue.recordTurnDiagnostics(currentTurn)
                    fail("output_dropped")
                }

                override fun onError(reason: String) {
                    if (stale()) return
                    dialogue.recordTurnDiagnostics(currentTurn)
                    if (LocalSpeechRecoverableFailure.isTransientNoTranscript(reason)) {
                        if (transcriptAccepted.get()) return
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
                            if (stale() || transcriptAccepted.get() ||
                                finalAudioReadyTurn.get() == retryTurn) return@postDelayed
                            if (!authorizedNow()) {
                                finish(authorityTermination())
                            } else if (activeMediaGeneration.get() != generation) {
                                fail("stale_media_generation")
                            } else {
                                beginNextTurn(generation, lease)
                            }
                        }, NO_SPEECH_RETRY_DELAY_MS)
                    } else {
                        fail(reason)
                    }
                }
            }

        private fun responseKind() = if (currentTurn == idlePromptTurn ||
            (currentTurn == 0 && dialogue.openingText != null))
            ConversationChatFeed.ResponseKind.SCRIPT_ASSISTED else scenario.responseKind

        private fun beginCaptureInputTurn(
            turn: Int,
            generation: Long,
            lease: CallMediaEndpointLease,
        ) {
            val pump = downlinkPump ?: run {
                fail("turn_${turn}_downlink_pump_unavailable")
                return
            }
            // The barge monitor owns RX until STT announces readiness. Preserve its
            // bounded in-memory speech onset instead of discarding the first syllables.
            val previousMonitor = bargeMonitor.getAndSet(null)
            val preRoll = previousMonitor?.freezeAndDrain() ?: ByteArray(0)
            if (previousMonitor != null) pump.abandonCapture(previousMonitor)
            // A completed bounded segment goes to STT without disconnecting Telecom.
            val rxWindowMs = scenario.maxRxCaptureMs
            require(rxWindowMs in 8_000..60_000) { "invalid_rx_capture_window" }
            lines += "turn_" + turn + "_rx_max_capture_ms=" + rxWindowMs
            val detector = PcmEndOfUtteranceDetector(
                trailingSilenceMs = 1_500,
                maxCaptureMs = rxWindowMs,
            )
            val captureStartedElapsedMs = (elapsedTurnMs() -
                ChatRelayTurnPacing.pcmDurationMs(preRoll.size)).coerceAtLeast(0L)
            val evidenceRx = evidenceRecorder?.openRxTurn(turn)
            var total = 0
            var endpoint = detector.acceptPcm16(ByteArray(0))
            val capture = ActiveRxCapture(turn, endpoint)
            val captureOwner = ConversationRxCaptureOwner(
                ConversationTurnOwner(turn, outputEpoch.get(), generation),
                rxCaptureAttemptSequence.incrementAndGet(),
            )
            fun currentCapture(): Boolean = captureOwner.isCurrent(
                currentTurn, outputEpoch.get(), activeMediaGeneration.get(),
                finished.get(), rxCaptureAttemptSequence.get(),
            )
            activeRxCapture.set(capture)
            try {
                if (preRoll.isNotEmpty()) {
                    if (!pipeline.writeInputPcm(preRoll)) throw IllegalStateException("stt_barge_preroll_write_failed")
                    evidenceRx?.append(preRoll, preRoll.size)
                    total += preRoll.size
                    capture.partialBytes.addAndGet(preRoll.size.toLong())
                    endpoint = detector.acceptPcm16(preRoll)
                    capture.latest.set(endpoint)
                    if (endpoint.estimatedSpeechEndMs != null && capture.voiceNoticed.compareAndSet(false, true))
                        idleTimeout?.heardVoice()
                }
                pump.beginCapture(object : ChatRelayDownlinkPump.Capture {
                    override fun onFrame(bytes: ByteArray, length: Int): Boolean {
                        if (!currentCapture() ||
                            activeRxCapture.get() !== capture || capture.complete.get()) return false
                        if (!pipeline.writeInputPcm(bytes, 0, length)) {
                            throw IllegalStateException("stt_pcm_write_failed")
                        }
                        evidenceRx?.append(bytes, length)
                        total += length
                        capture.partialBytes.addAndGet(length.toLong())
                        endpoint = detector.acceptPcm16(bytes, 0, length)
                        capture.latest.set(endpoint)
                        // First detected voice (one non-silent 20 ms frame) cancels the idle limit.
                        // Do not wait for an entire recognized transcript to preserve the call.
                        if (endpoint.estimatedSpeechEndMs != null && capture.voiceNoticed.compareAndSet(false, true)) {
                            idleTimeout?.heardVoice()
                        }
                        return !endpoint.shouldStop && !finished.get()
                    }

                    override fun onAbandoned() {
                        if (!capture.complete.compareAndSet(false, true)) return
                        if (!activeRxCapture.compareAndSet(capture, null)) return
                        // STT no-match while downlink capture is still running.
                        // Abandon just that capture; the Call's RX pump stays alive.
                        evidenceRx?.finish()
                        totalRxBytes += total.toLong()
                    }

                    override fun onFinished() {
                        if (!capture.complete.compareAndSet(false, true)) return
                        if (!activeRxCapture.compareAndSet(capture, null)) return
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
                        if (!currentCapture()) return
                        if (!endpoint.speechDetected) {
                            // Silence or waiting in the operator's IVR is not task failure.
                            // Keep listening while the exact call and grant remain active.
                            silenceWindows = (silenceWindows + 1L).coerceAtMost(Long.MAX_VALUE)
                            lines += "turn_${turn}_no_speech_window=$silenceWindows"
                            handler.postDelayed({
                                if (!currentCapture()) return@postDelayed
                                if (!authorizedNow()) finish(authorityTermination())
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
                        // The source can fail before the Call termination callback.
                        // Claim RX_INTERRUPTED before dropping the active capture:
                        // finish() must not lose this partial spoken turn.
                        val cause = ConversationRxInterruptionGate.sourceFailureCode(
                            stopRequested.get(),
                            runCatching { callStillActive() }.getOrDefault(false),
                        )
                        if (!recordInterruptedCapture(capture, cause)) return
                        if (!activeRxCapture.compareAndSet(capture, null)) return
                        evidenceRx?.finish()
                        totalRxBytes += total.toLong()
                        if (finished.get()) return
                        context.mainExecutor.execute {
                            if (currentCapture()) {
                                fail("turn_${turn}_rx_${sanitize(reason)}")
                            }
                        }
                    }
                })
                val guard = idleTimeout
                val policy = idlePolicy
                if (guard != null && policy != null) {
                    val remaining = guard.remainingMs(SystemClock.elapsedRealtime())
                    handler.postDelayed({
                        if (finished.get() || currentTurn != turn || activeMediaGeneration.get() != generation ||
                            activeRxCapture.get() !== capture || capture.complete.get()) return@postDelayed
                        if (capture.latest.get().estimatedSpeechEndMs != null) {
                            guard.heardVoice()
                            return@postDelayed
                        }
                        if (!authorizedNow()) {
                            finish(authorityTermination())
                            return@postDelayed
                        }
                        val due = guard.expire(SystemClock.elapsedRealtime()) ?: return@postDelayed
                        if (!pump.abandonActiveCapture()) return@postDelayed
                        idlePromptTurn = currentTurn
                        when (due) {
                            IncomingIdleTimeout.Action.WARN -> {
                                lines += "incoming_idle_warning_triggered=true"
                                pipeline.speakCandidate(policy.warningText, turnListener(generation, lease))
                            }
                            IncomingIdleTimeout.Action.CLOSE -> {
                                lines += "incoming_idle_closure_triggered=true"
                                if (!dialogue.armIdleClosure()) {
                                    fail("incoming_idle_closure_not_authorized")
                                    return@postDelayed
                                }
                                pipeline.speakCandidate(policy.farewellText, turnListener(generation, lease))
                            }
                        }
                    }, remaining)
                }
            } catch (error: Throwable) {
                capture.complete.set(true)
                activeRxCapture.compareAndSet(capture, null)
                evidenceRx?.finish()
                fail("turn_${turn}_rx_${error.javaClass.simpleName}")
            }
        }

        /** Call remains owned and active: only the old local output turn is revoked. */
        private fun armBargeMonitor(
            turn: Int,
            generation: Long,
            lease: CallMediaEndpointLease,
            turnEpoch: Long,
        ) {
            val pump = downlinkPump ?: return
            val monitor = RemoteBargeInMonitor(
                onVoice = {
                    if (outputEpoch.compareAndSet(turnEpoch, turnEpoch + 1L)) {
                        handler.post {
                            if (finished.get() || !authorizedNow() ||
                                !runCatching { callStillActive() }.getOrDefault(false) ||
                                currentTurn != turn || activeMediaGeneration.get() != generation ||
                                outputEpoch.get() != turnEpoch + 1L) return@post
                            // Revoke old model/TTS callback generation; do not disconnect Call.
                            pipeline.cancel()
                            turnsCompleted = maxOf(turnsCompleted, turn)
                            lines += "local_rx_during_tx_detected=true"
                            beginNextTurn(generation, lease)
                        }
                    }
                },
                onUnavailable = {
                    handler.post {
                        if (finished.get() || currentTurn != turn ||
                            activeMediaGeneration.get() != generation ||
                            outputEpoch.get() != turnEpoch || bargeMonitor.get() == null) return@post
                        // A dead RX source cannot support another listening turn.
                        // A remote hangup remains CALL_ENDED, never a fabricated TX success.
                        fail("tx_downlink_unavailable")
                    }
                },
            )
            if (!bargeMonitor.compareAndSet(null, monitor)) return
            try { pump.beginCapture(monitor) } catch (_: IllegalStateException) {
                bargeMonitor.compareAndSet(monitor, null)
                if (pump.isSourceTerminated()) fail("tx_downlink_unavailable")
                else lines += "barge_monitor_unavailable=true"
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
                val turnEpoch = outputEpoch.get()
                if (!QuickAcknowledgementPolicy.shouldPlay(
                        authorized = !finished.get() && authorizedNow() &&
                            runCatching { callStillActive() }.getOrDefault(false) &&
                            currentTurn == turn && activeMediaGeneration.get() == generation &&
                            dialogue.approvalPolicy.evaluate(QuickAcknowledgementPolicy.TEXT) ==
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
                    executor.execute { writeQuickAcknowledgement(lease, ready, turn, generation, turnEpoch) }
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
            turnEpoch: Long,
        ) {
            if (finished.get() || !authorizedNow() || outputEpoch.get() != turnEpoch ||
                !runCatching { callStillActive() }.getOrDefault(false) ||
                currentTurn != turn || activeMediaGeneration.get() != generation ||
                finalAudioReadyTurn.get() == turn ||
                dialogue.approvalPolicy.evaluate(QuickAcknowledgementPolicy.TEXT) !=
                    TextOutputDecision.RELEASE
            ) return
            try {
                val atMs = elapsedTurnMs()
                val evidenceAtMs = evidenceRecorder?.elapsed() ?: 0L
                val txWriteStarted = SystemClock.elapsedRealtime()
                val attempt = AuthorizedPcmUplinkWriter.writeOutcome(
                    uplink = lease.uplink(),
                    pcm = pcm,
                    isAuthorized = {
                        !finished.get() && authorizedNow() &&
                            runCatching { callStillActive() }.getOrDefault(false) &&
                            currentTurn == turn && activeMediaGeneration.get() == generation &&
                            outputEpoch.get() == turnEpoch
                    },
                )
                val txWriteWallMs =
                    (SystemClock.elapsedRealtime() - txWriteStarted).coerceAtLeast(0L)
                val sent = attempt.acceptedBytes
                evidenceRecorder?.recordAcknowledgementAudio(
                    turn, pcm, sent, evidenceAtMs, writeCompleted = attempt.complete,
                    writeStatus = attempt.status,
                )
                totalTxBytes += sent.toLong()
                lines += "turn_${turn}_quick_ack_tx_bytes=$sent"
                lines += "turn_${turn}_quick_ack_tx_elapsed_ms=$atMs"
                lines += "turn_${turn}_quick_ack_write_wall_ms=$txWriteWallMs"
                estimatedSpeechEndElapsedMs?.let { speechEndMs ->
                    lines += "turn_${turn}_eos_to_quick_ack_tx_ms=${(atMs - speechEndMs).coerceAtLeast(0L)}"
                }
                if (!attempt.complete || sent != pcm.size) return
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
            turnEpoch: Long,
        ) {
            try {
                if (finished.get() || !authorizedNow() || currentTurn != turn ||
                    outputEpoch.get() != turnEpoch) return
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
                val attempt = AuthorizedPcmUplinkWriter.writeOutcome(
                    uplink = lease.uplink(),
                    pcm = pcm,
                    isAuthorized = {
                        !finished.get() && authorizedNow() &&
                            runCatching { callStillActive() }.getOrDefault(false) &&
                            activeMediaGeneration.get() == generation &&
                            currentTurn == turn && outputEpoch.get() == turnEpoch
                    },
                )
                val bytesWritten = attempt.acceptedBytes
                evidenceRecorder?.recordTransmittedAudio(turn, pcm, bytesWritten, txEvidenceAtMs)
                evidenceRecorder?.recordFullTxAttempt(
                    turn, bytesWritten, pcm.size, writeCompleted = attempt.complete,
                    writeStatus = attempt.status,
                )
                totalTxBytes += bytesWritten.toLong()
                ConversationChatFeed.tx(liveChatToken, turn, attempt.complete && bytesWritten == pcm.size)
                if (!attempt.complete || bytesWritten != pcm.size ||
                    finished.get() || !authorizedNow() ||
                    activeMediaGeneration.get() != generation || currentTurn != turn ||
                    outputEpoch.get() != turnEpoch
                ) {
                    // A revoked old turn is not an error in its successor.
                    if (finished.get() || currentTurn != turn || outputEpoch.get() != turnEpoch) return
                    context.mainExecutor.execute {
                        if (!finished.get() && currentTurn == turn && outputEpoch.get() == turnEpoch) {
                            fail(if (attempt.status == PcmUplinkWriteStatus.IO_UNCERTAIN)
                                "tx_write_uncertain" else "tx_authority_revoked")
                        }
                    }
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
                ConversationChatFeed.stage(liveChatToken, "Tura " + turn + " • odpowiedź wysłana do TX")
                lines += "turn_${turn}_tx_pcm_bytes=${pcm.size}"
                handler.postDelayed(
                    { completeTurnAfterPlayback(turn, generation, lease, turnEpoch) },
                    postWriteHoldMs,
                )
            } catch (error: Throwable) {
                context.mainExecutor.execute {
                    if (!finished.get() && turn == currentTurn && outputEpoch.get() == turnEpoch)
                        fail("turn_${turn}_tx_${error.javaClass.simpleName}")
                }
            }
        }

        private fun completeTurnAfterPlayback(
            turn: Int,
            generation: Long,
            lease: CallMediaEndpointLease,
            turnEpoch: Long,
        ) {
            if (finished.get() || turn != currentTurn || outputEpoch.get() != turnEpoch) return
            if (activeMediaGeneration.get() != generation) { fail("stale_media_generation"); return }
            bargeMonitor.getAndSet(null)?.let { downlinkPump?.abandonCapture(it) }
            turnsCompleted = turn
            lines += "turn_${turn}_complete_elapsed_ms=${elapsedTurnMs()}"
            if (!authorizedNow()) { finish(authorityTermination()); return }
            dialogue.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE)?.let { applyOutcome(it); return }
            if (maxTurns != TASK_SCOPED_TURNS && turnsCompleted >= maxTurns) {
                applyOutcome(dialogue.turnBudgetOutcome())
            } else {
                beginNextTurn(generation, lease)
            }
        }

        private fun applyOutcome(outcome: ConversationOutcome) = when (outcome) {
            ConversationOutcome.Completed -> finish(ConversationTermination.Completed)
            is ConversationOutcome.Failed -> fail(outcome.code)
        }

        private fun elapsedTurnMs(): Long =
            (SystemClock.elapsedRealtime() - turnStartedAtMs).coerceAtLeast(0L)

        private var interruptedRxBytes = 0L
        private fun recordInterruptedCapture(active: ActiveRxCapture, cause: String): Boolean =
            ConversationRxInterruptionGate.claimAndRecord(active.complete) {
                val last = active.latest.get()
                interruptedRxBytes += active.partialBytes.get()
                lines += "turn_${active.turn}_rx_interrupted_pcm_bytes=${active.partialBytes.get()}"
                val trailing = last.estimatedSpeechEndMs?.let { speechEnd ->
                    (last.capturedMs - speechEnd).coerceAtLeast(0L)
                }
                lines += "turn_${active.turn}_rx_interrupted=true"
                lines += "turn_${active.turn}_rx_interrupted_capture_ms=${last.capturedMs}"
                lines += "turn_${active.turn}_rx_interrupted_trailing_silence_ms=${trailing ?: -1L}"
                evidenceRecorder?.recordInterruptedRx(
                    active.turn, last.capturedMs, last.speechDetected, trailing, cause,
                )
            }

        private fun fail(code: String) = finish(if (stopRequested.get() != null || !runCatching { callStillActive() }.getOrDefault(false)) authorityTermination() else ConversationTermination.Failed(code))

        private fun finish(termination: ConversationTermination) {
            if (!finished.compareAndSet(false, true)) return
            outputEpoch.incrementAndGet()
            bargeMonitor.getAndSet(null)?.let { downlinkPump?.abandonCapture(it) }
            val result = stopRequested.get()?.let { ConversationTermination.Stopped(it) } ?: termination
            val reason = result.code
            activeMediaGeneration.set(-1L)
            handler.removeCallbacks(timeout)
            handler.removeCallbacks(authorityWatchdog)
            // F-047: the owned Call can end before the 1500 ms VAD trailing silence.
            // Preserve only safe metadata; NEVER attempt Gemma/TTS/TX after call teardown.
            activeRxCapture.getAndSet(null)?.let { active ->
                recordInterruptedCapture(active, result.rxInterruptionCode())
            }
            try { pipeline.close() } catch (_: Throwable) {}
            try { quickAckSynthesizer.close() } catch (_: Throwable) {}
            quickAckPcm.set(null)
            try { dialogue.close() } catch (_: Throwable) {}
            downlinkPump?.let { pump ->
                lines += "downlink_discarded_pcm_bytes=${pump.discardedBytes()}"
                try { pump.close() } catch (_: Throwable) {}
            }
            downlinkPump = null
            try { mediaRuntime?.coordinator()?.takeOverNow() } catch (_: Throwable) {}
            try { mediaRuntime?.close() } catch (_: Throwable) {}
            mediaRuntime = null
            executor.shutdownNow()
            dialogue.finalDiagnostics().forEach { lines += it }
            lines += "turns_completed=$turnsCompleted"
            lines += "telephony_rx_pcm_bytes=$totalRxBytes"
            lines += "telephony_rx_partial_pcm_bytes=$interruptedRxBytes"
            lines += "telephony_tx_pcm_bytes=$totalTxBytes"
            lines += "chat_relay_live_call_success=${result == ConversationTermination.Completed}"
            lines += "conversation_end_reason=${sanitize(reason)}"
            if (result is ConversationTermination.Failed) lines += "failure_reason=${sanitize(reason)}"
            lines += "probe_complete=true"
            val report = lines.render()
            evidenceRecorder?.recordDiagnosticReport(report)
            if (!dialogue.awaitToolCompletion) evidenceRecorder?.finish(reason)
            ConversationChatFeed.finish(liveChatToken, reason)
            sessions.release(this)
            PrivateDiagnosticReportStore.write(context.filesDir, REPORT_FILE, report)
            context.mainExecutor.execute { callback(ConversationRunResult(result, report, dialogue.awaitToolCompletion, liveChatToken)) }
        }
    }

    private fun immediateReport(reason: String): ConversationRunResult = ConversationRunResult(ConversationTermination.Failed(reason),
        "probe=chat_relay_live_call\n" +
            "chat_relay_live_call_success=false\n" +
            "failure_reason=$reason\n" +
            "probe_complete=true\n", false, 0L)

    private fun sanitize(value: String): String =
        CallDiagnosticEvidence.safeCode(value)
}
