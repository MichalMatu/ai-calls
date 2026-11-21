package pl.michalmatu.aicallbridge.audioengines

/** Native dialogue tunables. Null budgets use the same defaults as prior app versions. */
internal data class DialogueGenerationSettings(
    val contextTokens: Int? = null,
    val outputTokens: Int? = null,
    val temperature: Double = 0.0,
    val topK: Int = 1,
    val topP: Double = 1.0,
) {
    fun valid(): Boolean = (contextTokens == null || contextTokens in CONTEXT_RANGE) &&
        (outputTokens == null || outputTokens in OUTPUT_RANGE) &&
        temperature in TEMPERATURE_RANGE && topK in TOP_K_RANGE && topP in TOP_P_RANGE

    fun contextBudget(model: DialogueModelId, structured: Boolean): Int = contextTokens
        ?: if (structured && model in DialogueModelId.builtInChoices) 4_096 else 2_048

    fun outputBudget(structured: Boolean): Int = outputTokens ?: if (structured) 512 else 128

    companion object {
        val DEFAULTS = DialogueGenerationSettings()
        val CONTEXT_RANGE = 2_048..8_192
        val OUTPUT_RANGE = 256..1_024
        val TEMPERATURE_RANGE = 0.0..1.5
        val TOP_K_RANGE = 1..64
        val TOP_P_RANGE = 0.05..1.0
    }
}
