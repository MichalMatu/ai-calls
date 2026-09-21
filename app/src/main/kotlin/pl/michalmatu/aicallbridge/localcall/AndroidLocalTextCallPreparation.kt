package pl.michalmatu.aicallbridge.localcall

import android.content.Context
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.localspeech.OnDeviceSpeechInput
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.textagent.EdgeGalleryTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.LocalPhoneLlmBackendFactory
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Proves that the selected on-device STT and a non-network TTS voice are usable before dialing. */
internal class AndroidLocalTextCallSpeechPreflight(
    context: Context,
    private val languageTag: String = "pl-PL",
) : LocalTextCallSpeechPreflight {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var generation = 0L
    private var input: OnDeviceSpeechInput? = null
    private var output: LocalTtsSpeechOutput? = null

    override fun prepare(listener: LocalTextCallSpeechPreflight.Listener) {
        cancel()
        val requestGeneration = synchronized(lock) {
            generation += 1
            generation
        }
        val speechInput = OnDeviceSpeechInput(appContext, languageTag)
        synchronized(lock) {
            if (generation != requestGeneration) {
                speechInput.close()
                return
            }
            input = speechInput
        }
        speechInput.start(object : OnDeviceSpeechInput.Listener {
            override fun onReady() {
                if (!isCurrent(requestGeneration)) return
                try { speechInput.cancel() } catch (_: Throwable) {}
                synchronized(lock) {
                    if (generation == requestGeneration && input === speechInput) input = null
                }
                beginTtsPreflight(requestGeneration, listener)
            }

            override fun onFinalTranscript(text: String) {
                finishError(requestGeneration, listener, "stt_unexpected_transcript")
            }

            override fun onError(reason: String) {
                finishError(requestGeneration, listener, "stt_${sanitize(reason)}")
            }
        })
    }

    override fun cancel() {
        val inputToClose: OnDeviceSpeechInput?
        val outputToClose: LocalTtsSpeechOutput?
        synchronized(lock) {
            generation += 1
            inputToClose = input
            outputToClose = output
            input = null
            output = null
        }
        try { inputToClose?.close() } catch (_: Throwable) {}
        try { outputToClose?.close() } catch (_: Throwable) {}
    }

    override fun close() = cancel()

    private fun beginTtsPreflight(
        requestGeneration: Long,
        listener: LocalTextCallSpeechPreflight.Listener,
    ) {
        if (!isCurrent(requestGeneration)) return
        val speechOutput = LocalTtsSpeechOutput(appContext, languageTag)
        synchronized(lock) {
            if (generation != requestGeneration) {
                speechOutput.close()
                return
            }
            output = speechOutput
        }
        speechOutput.synthesize(TTS_PREFLIGHT_TEXT, object : LocalTtsSpeechOutput.Listener {
            override fun onPcm16Mono16k(pcm: ByteArray) {
                if (!isCurrent(requestGeneration)) return
                if (pcm.isEmpty()) {
                    finishError(requestGeneration, listener, "tts_empty_pcm")
                    return
                }
                finishReady(requestGeneration, listener)
            }

            override fun onError(reason: String) {
                finishError(requestGeneration, listener, "tts_${sanitize(reason)}")
            }
        })
    }

    private fun finishReady(
        requestGeneration: Long,
        listener: LocalTextCallSpeechPreflight.Listener,
    ) {
        if (!claim(requestGeneration)) return
        cleanupCurrent()
        listener.onReady()
    }

    private fun finishError(
        requestGeneration: Long,
        listener: LocalTextCallSpeechPreflight.Listener,
        reason: String,
    ) {
        if (!claim(requestGeneration)) return
        cleanupCurrent()
        listener.onError(reason)
    }

    private fun claim(requestGeneration: Long): Boolean = synchronized(lock) {
        if (generation != requestGeneration) return@synchronized false
        generation += 1
        true
    }

    private fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        generation == requestGeneration
    }

    private fun cleanupCurrent() {
        val inputToClose: OnDeviceSpeechInput?
        val outputToClose: LocalTtsSpeechOutput?
        synchronized(lock) {
            inputToClose = input
            outputToClose = output
            input = null
            output = null
        }
        try { inputToClose?.close() } catch (_: Throwable) {}
        try { outputToClose?.close() } catch (_: Throwable) {}
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').take(160).ifBlank { "unknown" }

    private companion object {
        const val TTS_PREFLIGHT_TEXT = "Gotowe."
    }
}

internal object AndroidLocalTextCallBackendFactory {
    fun create(context: Context, provider: TextLlmProvider): TextCallAgentBackend = when (provider) {
        TextLlmProvider.LOCAL_PHONE_LLM -> LocalPhoneLlmBackendFactory.create(context.applicationContext)
        TextLlmProvider.EDGE_GALLERY -> EdgeGalleryTextBackendFactory.create()
        TextLlmProvider.LOCAL_MAC_LLM,
        TextLlmProvider.OPENAI_TEXT,
        -> throw IllegalArgumentException("text_provider_not_product_ready_${provider.name.lowercase()}")
    }
}

internal object AndroidTextCallReadiness {
    fun create(
        context: Context,
        workflow: CallWorkflow,
        targetAuthorization: DialTargetAuthorization,
        provider: TextLlmProvider,
        callPlan: CallPlan? = null,
        phraseMatrix: PhraseMatrix? = null,
    ): LocalTextCallReadinessCoordinator = LocalTextCallReadinessCoordinator(
        workflow = workflow,
        targetAuthorization = targetAuthorization,
        speechPreflight = AndroidLocalTextCallSpeechPreflight(context.applicationContext),
        backendFactory = {
            AndroidLocalTextCallBackendFactory.create(context.applicationContext, provider)
        },
        callPlan = callPlan,
        phraseMatrix = phraseMatrix,
    )
}

internal object LocalPhoneTextCallReadiness {
    fun create(
        context: Context,
        workflow: CallWorkflow,
        targetAuthorization: DialTargetAuthorization,
        callPlan: CallPlan? = null,
        phraseMatrix: PhraseMatrix? = null,
    ): LocalTextCallReadinessCoordinator = AndroidTextCallReadiness.create(
        context = context,
        workflow = workflow,
        targetAuthorization = targetAuthorization,
        provider = TextLlmProvider.LOCAL_PHONE_LLM,
        callPlan = callPlan,
        phraseMatrix = phraseMatrix,
    )
}
