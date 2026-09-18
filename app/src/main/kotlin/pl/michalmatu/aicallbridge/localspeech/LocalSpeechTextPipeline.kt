package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallTurnController
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy

/**
 * One conservative LOCAL_STT_TTS turn. It never emits PCM until the complete backend response has
 * passed the application-owned text approval policy.
 */
internal class LocalSpeechTextPipeline(
    context: Context,
    backend: TextCallAgentBackend,
    approvalPolicy: TextOutputApprovalPolicy,
    languageTag: String = "pl-PL",
) : AutoCloseable {
    interface Listener {
        fun onSpeechInputReady()
        fun onUserTranscript(text: String)
        fun onApprovedText(text: String)
        fun onOutputPcm16Mono16k(pcm: ByteArray)
        fun onDroppedText()
        fun onError(reason: String)
    }

    private val input = OnDeviceSpeechInput(context, languageTag)
    private val output = LocalTtsSpeechOutput(context, languageTag)
    private val controller = TextCallTurnController(backend, approvalPolicy)
    private val gate = LocalSpeechGenerationGate()

    fun start(listener: Listener) {
        cancel()
        val generation = gate.begin()
        input.start(object : OnDeviceSpeechInput.Listener {
            override fun onReady() {
                if (gate.isCurrent(generation)) listener.onSpeechInputReady()
            }

            override fun onFinalTranscript(text: String) {
                if (!gate.isCurrent(generation)) return
                listener.onUserTranscript(text)
                controller.submitUserText(text, object : TextCallTurnController.Listener {
                    override fun onApprovedResponse(text: String) {
                        if (!gate.isCurrent(generation)) return
                        listener.onApprovedText(text)
                        output.synthesize(text, object : LocalTtsSpeechOutput.Listener {
                            override fun onPcm16Mono16k(pcm: ByteArray) {
                                if (!gate.isCurrent(generation)) return
                                finishGeneration(generation)
                                listener.onOutputPcm16Mono16k(pcm)
                            }

                            override fun onError(reason: String) {
                                finishError(generation, listener, "tts_$reason")
                            }
                        })
                    }

                    override fun onDroppedResponse() {
                        if (!gate.isCurrent(generation)) return
                        finishGeneration(generation)
                        listener.onDroppedText()
                    }

                    override fun onError(reason: String) {
                        finishError(generation, listener, reason)
                    }
                })
            }

            override fun onError(reason: String) {
                finishError(generation, listener, "stt_$reason")
            }
        })
    }

    fun writeInputPcm(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Boolean =
        input.writePcm(bytes, offset, length)

    fun finishInput() = input.finishInput()

    fun cancel() {
        gate.invalidate()
        input.cancel()
        controller.cancel()
        output.cancel()
    }

    override fun close() {
        cancel()
        controller.close()
        input.close()
        output.close()
    }

    private fun finishError(generation: Long, listener: Listener, reason: String) {
        if (!gate.isCurrent(generation)) return
        finishGeneration(generation)
        listener.onError(reason)
    }

    private fun finishGeneration(generation: Long) {
        if (!gate.isCurrent(generation)) return
        gate.invalidate()
        input.cancel()
        controller.cancel()
        output.cancel()
    }
}
