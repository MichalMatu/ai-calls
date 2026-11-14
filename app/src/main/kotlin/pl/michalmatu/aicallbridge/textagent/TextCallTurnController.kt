package pl.michalmatu.aicallbridge.textagent

import pl.michalmatu.aicallbridge.localspeech.LocalSpeechGenerationGate

/**
 * One conservative text turn: FINAL ASR text -> COMPLETE text candidate -> app approval.
 *
 * Generative turns obtain the candidate from [TextCallAgentBackend]. Deterministic product-owned
 * callers may submit an already-complete candidate directly, but both paths share the same
 * application-owned approval and generation invalidation. Partial output is intentionally not part
 * of this contract.
 */
internal class TextCallTurnController(
    private val backend: TextCallAgentBackend,
    private val approvalPolicy: TextOutputApprovalPolicy,
) : AutoCloseable {
    interface Listener {
        fun onUserTranscript(text: String) = Unit
        fun onApprovedResponse(text: String)
        fun onDroppedResponse()
        fun onError(reason: String)
    }

    private val gate = LocalSpeechGenerationGate()

    fun submitUserText(userText: String, listener: Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        submit(listener) { backend.generate(userText, it) }
    }

    fun submitUserAudio(pcm16: ByteArray, listener: Listener) {
        require(pcm16.isNotEmpty() && pcm16.size % 2 == 0) { "invalid_pcm16" }
        submit(listener) { backend.generateAudio(pcm16, it) }
    }

    private fun submit(listener: Listener, generate: (TextCallAgentBackend.Listener) -> Unit) {
        backend.cancel()
        val generation = gate.begin()
        try {
            generate(object : TextCallAgentBackend.Listener {
                override fun onUserTranscript(text: String) {
                    if (gate.isCurrent(generation)) listener.onUserTranscript(text)
                }
                override fun onComplete(text: String) {
                    if (!gate.isCurrent(generation)) return
                    if (text.isBlank()) {
                        finishError(generation, listener, "backend_empty_response")
                        return
                    }
                    evaluateCandidate(generation, text, listener)
                }

                override fun onError(reason: String) {
                    finishError(generation, listener, "backend_${sanitize(reason)}")
                }
            })
        } catch (error: Throwable) {
            finishError(generation, listener, "backend_start_${error.javaClass.simpleName}")
        }
    }

    /**
     * Sends one already-complete deterministic candidate through the same application-owned output
     * approval without invoking backend generation. Starting it invalidates any older backend turn.
     */
    fun submitCandidateText(text: String, listener: Listener) {
        require(text.isNotBlank()) { "candidate_text_must_not_be_blank" }
        backend.cancel()
        val generation = gate.begin()
        evaluateCandidate(generation, text, listener)
    }

    fun cancel() {
        gate.invalidate()
        backend.cancel()
    }

    override fun close() {
        cancel()
        try { backend.close() } catch (_: Throwable) {}
    }

    private fun evaluateCandidate(generation: Long, text: String, listener: Listener) {
        if (!gate.isCurrent(generation)) return
        val decision = approvalPolicy.evaluate(text)
        // Approval may call STOP or overlap a timeout. Re-check atomically
        // before publishing ANY output, including a dropped/error callback.
        if (!finishGeneration(generation)) return
        when (decision) {
            TextOutputDecision.RELEASE -> listener.onApprovedResponse(text)
            TextOutputDecision.DROP -> listener.onDroppedResponse()
        }
    }

    private fun finishError(generation: Long, listener: Listener, reason: String) {
        if (finishGeneration(generation)) listener.onError(reason)
    }

    private fun finishGeneration(generation: Long): Boolean {
        if (!gate.invalidateIfCurrent(generation)) return false
        backend.cancel()
        return true
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').take(160)
}
