package pl.michalmatu.aicallbridge.audioengines

/**
 * Converts raw generation-setting editor fields without Android UI dependencies.
 * Blank context/output keep the existing automatic budget semantics.
 */
internal object DialogueGenerationInputParser {
    fun parse(
        context: String,
        output: String,
        temperature: String,
        topK: String,
        topP: String,
    ): DialogueGenerationSettings? {
        val contextText = context.trim()
        val outputText = output.trim()
        val next = DialogueGenerationSettings(
            contextTokens = contextText.toIntOrNull(),
            outputTokens = outputText.toIntOrNull(),
            temperature = temperature.replace(',', '.').toDoubleOrNull() ?: Double.NaN,
            topK = topK.toIntOrNull() ?: 0,
            topP = topP.replace(',', '.').toDoubleOrNull() ?: Double.NaN,
        )
        return next.takeIf { it.valid() &&
            (it.contextTokens != null || contextText.isEmpty()) &&
            (it.outputTokens != null || outputText.isEmpty()) }
    }
}
