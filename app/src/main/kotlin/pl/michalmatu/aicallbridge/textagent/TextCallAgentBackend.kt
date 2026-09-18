package pl.michalmatu.aicallbridge.textagent

/** Complete-text provider boundary shared by OpenAI text and future local Mac LLM backends. */
internal interface TextCallAgentBackend : AutoCloseable {
    interface Listener {
        fun onComplete(text: String)
        fun onError(reason: String)
    }

    fun generate(userText: String, listener: Listener)

    fun cancel()

    override fun close() = cancel()
}
