package pl.michalmatu.aicallbridge.textagent

/** Complete-text provider boundary shared by OpenAI text and future local Mac LLM backends. */
internal interface TextCallAgentBackend : AutoCloseable {
    interface Listener {
        /** Only multimodal responses supply this; it is private conversation data. */
        fun onUserTranscript(text: String) = Unit
        fun onComplete(text: String)
        fun onError(reason: String)
    }

    fun generate(userText: String, listener: Listener)

    fun generateAudio(pcm16: ByteArray, listener: Listener) {
        listener.onError("model_audio_input_unavailable")
    }

    fun cancel()

    override fun close() = cancel()
}
