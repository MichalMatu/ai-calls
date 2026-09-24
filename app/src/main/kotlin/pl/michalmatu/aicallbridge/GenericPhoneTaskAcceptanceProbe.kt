package pl.michalmatu.aicallbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import pl.michalmatu.aicallbridge.agent.CallCommitmentConsumptionEvidence
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallExternalEffect
import pl.michalmatu.aicallbridge.agent.CallExternalEffectCompletionTracker
import pl.michalmatu.aicallbridge.agent.CallExternalEffectSuccessEvidence
import pl.michalmatu.aicallbridge.agent.CallExternalEffectValidation
import pl.michalmatu.aicallbridge.agent.CallExternalEffectValidator
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanFallback
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState
import pl.michalmatu.aicallbridge.localcall.AndroidTextCallReadiness
import pl.michalmatu.aicallbridge.localcall.DialTargetAuthorization
import pl.michalmatu.aicallbridge.localcall.LocalTextCallHybridSessionFactory
import pl.michalmatu.aicallbridge.localcall.LocalTextCallReadinessCoordinator
import pl.michalmatu.aicallbridge.localcall.LocalTextCallSession
import pl.michalmatu.aicallbridge.localcall.PreparedLocalTextCall
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.DialogueSkillBackendFactory
import pl.michalmatu.aicallbridge.textagent.DialogueSkillPolicy
import pl.michalmatu.aicallbridge.textagent.FailoverTextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Full G4 acceptance runner with no cellular call and no real external side effect.
 *
 * The probe intentionally reuses production owners instead of creating a parallel authority path:
 * synthetic speech PCM -> on-device STT -> deterministic CallPlan TAKE_OVER -> Gemma 4 bounded
 * classifier -> deterministic supervisor fallback -> application output approval -> on-device TTS ->
 * synthetic TX sink -> typed SET_SERVICE(CLIR=true) validation -> one-shot permit consumption ->
 * exact synthetic external-success evidence -> factual completion.
 */
internal object GenericPhoneTaskAcceptanceProbe {
    private const val REPORT_FILE = "generic-phone-acceptance-report.txt"
    private const val TIMEOUT_MS = 240_000L
    private const val SYNTHETIC_DIAL_ADDRESS = "000"
    private const val SOURCE_TEXT =
        "Proszę pomóc z ustawieniem prezentacji numeru w usłudze telefonicznej."
    private const val SUPERVISOR_RESPONSE =
        "Rozumiem. Sprawdzam dokładnie autoryzowaną zmianę usługi."

    fun run(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        val lines = mutableListOf(
            "probe=generic_phone_task_acceptance",
            "call_required=false",
            "external_effect_real_execution=false",
            "text_llm_provider=${TextLlmProvider.LOCAL_GEMMA_4.name}",
            "approval_policy=application_owned",
            "synthetic_tx=true",
            "timestamp_utc=${Instant.now()}",
            "target_pcm=mono,pcm16,${LocalSpeechFormat.SAMPLE_RATE_HZ}",
        )
        val finished = AtomicBoolean(false)
        val classifierCalls = AtomicInteger(0)
        val supervisorCalls = AtomicInteger(0)
        val handler = Handler(Looper.getMainLooper())
        val sourceTts = LocalTtsSpeechOutput(appContext)
        val task = CallTask(
            "Synthetic Orange service target",
            "SET_SERVICE",
            "CLIR",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            mapOf(CallExternalEffectValidator.SERVICE_ENABLED_FACT to "true"),
        )
        val target = CallResolvedTarget("Synthetic Orange target", SYNTHETIC_DIAL_ADDRESS)
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        workflow.resolveTarget(target)
        val callPlan = CallPlan(
            task,
            target,
            emptyList(),
            CallPlanFallback.TAKE_OVER,
        )
        val commitmentGate = CallCommitmentGate { "g4-synthetic-clir-permit" }
        val approval = CallTextAgentOutputApprovalPolicy(workflow, commitmentGate)
        val dialoguePolicy = DialogueSkillPolicy(
            allowedResponses = emptyMap(),
            minimumConfidence = 0.0,
        )
        var prepared: PreparedLocalTextCall? = null
        var session: LocalTextCallSession? = null
        var warmupClassifierCalls = 0
        var warmupSupervisorCalls = 0

        val readiness = AndroidTextCallReadiness.create(
            context = appContext,
            workflow = workflow,
            targetAuthorization = DialTargetAuthorization {
                it.dialAddress() == SYNTHETIC_DIAL_ADDRESS
            },
            provider = TextLlmProvider.LOCAL_GEMMA_4,
            callPlan = callPlan,
            backendFactoryOverride = {
                val primary = CountingBackend(
                    delegate = DialogueSkillBackendFactory.create(
                        context = appContext,
                        provider = TextLlmProvider.LOCAL_GEMMA_4,
                        policy = dialoguePolicy,
                    ),
                    calls = classifierCalls,
                )
                val supervisor = SyntheticSupervisorBackend(supervisorCalls)
                FailoverTextCallAgentBackend(primary, supervisor)
            },
        )

        fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            try { sourceTts.close() } catch (_: Throwable) {}
            try { session?.close() } catch (_: Throwable) {}
            try { prepared?.close() } catch (_: Throwable) {}
            try { readiness.close() } catch (_: Throwable) {}
            lines += "gemma_classifier_calls=${classifierCalls.get()}"
            lines += "supervisor_fallback_calls=${supervisorCalls.get()}"
            lines += "generic_phone_task_acceptance_success=$success"
            if (reason != null) lines += "failure_reason=${sanitize(reason)}"
            lines += "probe_complete=true"
            val report = lines.joinToString("\n") + "\n"
            try { File(appContext.filesDir, REPORT_FILE).writeText(report) } catch (_: Throwable) {}
            callback(report)
        }

        fun completeSyntheticEffect() {
            val candidate = CallExternalEffect.SetService(
                target = target,
                service = CallService.CLIR,
                enabled = true,
            )
            val validation = CallExternalEffectValidator.validateSetService(task, target, candidate)
            if (validation !is CallExternalEffectValidation.Accepted) {
                finish(false, "effect_validation_rejected")
                return
            }
            lines += "effect_validation_accepted=true"

            val authorization = try {
                commitmentGate.authorize(validation.effect)
            } catch (error: Throwable) {
                finish(false, "effect_authorize_${error.javaClass.simpleName}")
                return
            }
            lines += "effect_permit_issued=true"

            val consumed = commitmentGate.consumeEffect(authorization.value)
            if (consumed.isFailure) {
                finish(false, "effect_consume_failed")
                return
            }
            val consumedEffect = consumed.getOrThrow()
            if (consumedEffect != candidate || commitmentGate.hasAuthorization()) {
                finish(false, "effect_consumption_mismatch")
                return
            }
            lines += "effect_permit_consumed=true"

            val consumptionEvidence = CallCommitmentConsumptionEvidence(consumedEffect)
            val completion = CallExternalEffectCompletionTracker.fromConsumption(consumptionEvidence)
            val completed = completion.complete(CallExternalEffectSuccessEvidence(candidate))
            if (completed.isFailure || completed.getOrThrow() != candidate || !completion.isCompleted()) {
                finish(false, "effect_completion_failed")
                return
            }
            lines += "external_success_evidence_exact=true"
            lines += "effect_factual_completion=true"

            try {
                workflow.complete(
                    CallOutcome(
                        CallOutcomeStatus.SUCCESS,
                        "Synthetic CLIR effect completed",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                )
            } catch (error: Throwable) {
                finish(false, "workflow_complete_${error.javaClass.simpleName}")
                return
            }
            if (workflow.snapshot().state() != CallWorkflowState.COMPLETED) {
                finish(false, "workflow_completion_state_mismatch")
                return
            }
            lines += "workflow_completion=true"
            finish(true)
        }

        fun runPreparedPipeline(ready: PreparedLocalTextCall) {
            if (finished.get()) {
                ready.close()
                return
            }
            prepared = ready
            warmupClassifierCalls = classifierCalls.get()
            warmupSupervisorCalls = supervisorCalls.get()
            lines += "readiness_state=READY_TO_DIAL"
            lines += "ready_to_dial=true"
            lines += "gemma_warmup_classifier_calls=$warmupClassifierCalls"
            lines += "gemma_warmup_supervisor_calls=$warmupSupervisorCalls"

            try {
                workflow.markDialing()
                workflow.markCallActive()
            } catch (error: Throwable) {
                finish(false, "workflow_activate_${error.javaClass.simpleName}")
                return
            }

            val activeSession = try {
                LocalTextCallHybridSessionFactory.create(
                    context = appContext,
                    prepared = ready,
                    approvalPolicy = approval,
                )
            } catch (error: Throwable) {
                finish(false, "session_create_${error.javaClass.simpleName}")
                return
            }
            session = activeSession

            sourceTts.synthesize(SOURCE_TEXT, object : LocalTtsSpeechOutput.Listener {
                override fun onPcm16Mono16k(pcm: ByteArray) {
                    if (pcm.isEmpty()) {
                        finish(false, "source_tts_empty_pcm")
                        return
                    }
                    lines += "source_tts_pcm_bytes=${pcm.size}"
                    activeSession.startWithPlanRouting(
                        object : LocalSpeechTextPipeline.Listener {
                            override fun onSpeechInputReady() {
                                lines += "stt_ready=true"
                                Thread({
                                    try {
                                        val silence = ByteArray(LocalSpeechFormat.bytesForDurationMs(500))
                                        val chunk = LocalSpeechFormat.bytesForDurationMs(20)
                                        fun writePaced(data: ByteArray): Boolean {
                                            var offset = 0
                                            while (offset < data.size && !finished.get()) {
                                                val length = minOf(chunk, data.size - offset)
                                                if (!activeSession.writeInputPcm(data, offset, length)) {
                                                    return false
                                                }
                                                offset += length
                                                Thread.sleep(20L)
                                            }
                                            return true
                                        }
                                        if (!writePaced(silence) || !writePaced(pcm) || !writePaced(silence)) {
                                            appContext.mainExecutor.execute {
                                                finish(false, "pcm_write_failed")
                                            }
                                            return@Thread
                                        }
                                        activeSession.finishInput()
                                        lines += "stt_pcm_eof_sent=true"
                                    } catch (error: Throwable) {
                                        appContext.mainExecutor.execute {
                                            finish(false, "stream_${error.javaClass.simpleName}")
                                        }
                                    }
                                }, "GenericPhoneTaskAcceptanceInput").start()
                            }

                            override fun onUserTranscript(text: String) {
                                lines += "stt_text=${sanitize(text)}"
                                lines += "stt_transcript_nonblank=${text.isNotBlank()}"
                            }

                            override fun onApprovedText(text: String) {
                                lines += "approved_text=${sanitize(text)}"
                                lines += "approved_text_matches_supervisor=${text == SUPERVISOR_RESPONSE}"
                            }

                            override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                                lines += "output_tts_pcm_bytes=${pcm.size}"
                                if (pcm.isEmpty()) {
                                    finish(false, "output_tts_empty_pcm")
                                    return
                                }
                                val turnClassifierCalls = classifierCalls.get() - warmupClassifierCalls
                                val turnSupervisorCalls = supervisorCalls.get() - warmupSupervisorCalls
                                lines += "turn_gemma_classifier_calls=$turnClassifierCalls"
                                lines += "turn_supervisor_fallback_calls=$turnSupervisorCalls"
                                if (turnClassifierCalls < 1 || turnSupervisorCalls != 1) {
                                    finish(false, "dialogue_fallback_not_proven")
                                    return
                                }
                                lines += "synthetic_tx_pcm_bytes=${pcm.size}"
                                lines += "synthetic_tx_complete=true"
                                completeSyntheticEffect()
                            }

                            override fun onDroppedText() = finish(false, "output_dropped")

                            override fun onError(reason: String) = finish(false, reason)
                        },
                        LocalTextCallSession.StructuredPlanTurnListener { action, ruleId ->
                            lines += "call_plan_action=${action.name.lowercase()}"
                            ruleId?.let { lines += "call_plan_rule_id=${sanitize(it)}" }
                            if (action != CallPlanAction.TAKE_OVER) {
                                finish(false, "unexpected_call_plan_action_${action.name.lowercase()}")
                            }
                        },
                    )
                }

                override fun onError(reason: String) = finish(false, "source_tts_$reason")
            })
        }

        handler.postDelayed({ finish(false, "probe_timeout") }, TIMEOUT_MS)
        readiness.prepare(object : LocalTextCallReadinessCoordinator.Listener {
            override fun onReady(prepared: PreparedLocalTextCall) {
                appContext.mainExecutor.execute { runPreparedPipeline(prepared) }
            }

            override fun onFailure(reason: String) {
                appContext.mainExecutor.execute {
                    lines += "ready_to_dial=false"
                    finish(false, "readiness_$reason")
                }
            }
        })
    }

    private class CountingBackend(
        private val delegate: TextCallAgentBackend,
        private val calls: AtomicInteger,
    ) : TextCallAgentBackend {
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            calls.incrementAndGet()
            delegate.generate(userText, listener)
        }

        override fun cancel() = delegate.cancel()
        override fun close() = delegate.close()
    }

    private class SyntheticSupervisorBackend(
        private val calls: AtomicInteger,
    ) : TextCallAgentBackend {
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            calls.incrementAndGet()
            listener.onComplete(SUPERVISOR_RESPONSE)
        }

        override fun cancel() = Unit
        override fun close() = Unit
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(240)
}
