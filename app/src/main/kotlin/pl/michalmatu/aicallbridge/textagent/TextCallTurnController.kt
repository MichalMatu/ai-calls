package pl.michalmatu.aicallbridge.textagent

import pl.michalmatu.aicallbridge.localspeech.LocalSpeechGenerationGate

/**
 * One conservative text turn: FINAL ASR text -> COMPLETE backend text -> app approval.
 * Partial backend output is intentionally not part of this contract.
 */
internal class TextCallTurnController(
    private val backend: TextCallAgentBackend,
    private val approvalPolicy: TextOutputApprovalPolicy,
) : AutoCloseable {
    interface Listener {
        fun onApprovedResponse(text: String)
        fun onDroppedResponse()
        fun onError(reason: String)
    }

    private val gate = LocalSpeechGenerationGate()

    fun submitUserText(userText: String, listener: Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        backend.cancel()
        val generation = gate.begin()
        try {
            backend.generate(userText, object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    if (!gate.isCurrent(generation)) return
                    if (text.isBlank()) {
                        finishError(generation, listener, "backend_empty_response")
                        return
                    }
                    when (approvalPolicy.evaluate(text)) {
                        TextOutputDecision.RELEASE -> {
                            finishGeneration(generation)
                            listener.onApprovedResponse(text)
                        }
                        TextOutputDecision.DROP -> {
                            finishGeneration(generation)
                            listener.onDroppedResponse()
                        }
                    }
                }

                override fun onError(reason: String) {
                    finishError(generation, listener, "backend_${sanitize(reason)}")
                }
            })
        } catch (error: Throwable) {
            finishError(generation, listener, "backend_start_${error.javaClass.simpleName}")
        }
    }

    fun cancel() {
        gate.invalidate()
        backend.cancel()
    }

    override fun close() {
        cancel()
        try { backend.close() } catch (_: Throwable) {}
    }

    private fun finishError(generation: Long, listener: Listener, reason: String) {
        if (!gate.isCurrent(generation)) return
        finishGeneration(generation)
        listener.onError(reason)
    }

    private fun finishGeneration(generation: Long) {
        if (!gate.isCurrent(generation)) return
        gate.invalidate()
        backend.cancel()
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').take(160)
}
