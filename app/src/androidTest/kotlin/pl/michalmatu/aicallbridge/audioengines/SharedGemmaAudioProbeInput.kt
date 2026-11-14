package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.NativeConversationContextBackend
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Native QA adapter for the same model engine and audio reply protocol used by real dialogue. */
internal class SharedGemmaAudioProbeInput(context: Context) : SpeechInputProvider {
    private val input = GemmaAudioDirectInputProvider()
    private val model = Gemma4LiteRtTextBackendFactory.createCompatibilityProbe(
        Gemma4LiteRtTextBackendFactory.modelFile(context, DialogueModelId.GEMMA_4_E2B), true)
    @Volatile private var syntheticPreflightDone = false

    override fun start(listener: SpeechInputProvider.Listener) {
        if (syntheticPreflightDone) {
            startCapture(listener)
            return
        }
        model.generate("Lokalna próba, odpowiedz krótko w wymaganym formacie, tool=none.",
            object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    (model as NativeConversationContextBackend).resetConversationContext()
                    syntheticPreflightDone = true
                    startCapture(listener)
                }
                override fun onError(reason: String) = listener.onError(reason)
            })
    }

    private fun startCapture(listener: SpeechInputProvider.Listener) {
                    input.start(object : SpeechInputProvider.Listener {
                        override fun onReady() = listener.onReady()
                        override fun onFinalTranscript(text: String) = error("unexpected_second_ASR")
                        override fun onError(reason: String) = listener.onError(reason)
                        override fun onFinalAudio(pcm16: ByteArray) {
                            model.generateAudio(pcm16, object : TextCallAgentBackend.Listener {
                                override fun onComplete(text: String) {
                                    val decoded = runCatching { ConversationReplyProtocol.decodeAudio(text) }
                                    decoded.fold({ listener.onFinalTranscript(it.first) },
                                        { error ->
                                            val empty = error.message == "audio_transcript_empty" && runCatching {
                                                val root = com.google.gson.JsonParser.parseString(text).asJsonObject
                                                root.remove("transcript")
                                                ConversationReplyProtocol.decode(root.toString())
                                            }.isSuccess
                                            listener.onError(if (empty) "gemma_audio_empty_transcript" else "gemma_audio_invalid_reply")
                                        })
                                }
                                override fun onError(reason: String) = listener.onError(reason)
                            })
                        }
                    })
    }
    override fun writePcm(bytes: ByteArray, offset: Int, length: Int) = input.writePcm(bytes, offset, length)
    override fun finishInput() = input.finishInput()
    override fun cancel() { input.cancel(); model.cancel() }
    override fun close() { input.close(); model.close() }
}
