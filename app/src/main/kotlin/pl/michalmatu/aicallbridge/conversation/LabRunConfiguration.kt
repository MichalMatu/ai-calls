package pl.michalmatu.aicallbridge.conversation

/** Snapshot of effective inference settings, excluding the prompt and dialogue text. */
internal data class LabRunConfiguration(
    val modelId: String,
    val contextTokens: Int,
    val outputTokens: Int,
    val temperature: Double,
    val topK: Int,
    val topP: Double,
    val language: String,
    val skillMode: SkillLoadingMode,
    val initialPromptSha256: String,
) {
    fun evidenceText(): String =
        "Context: $contextTokens · Output: $outputTokens · Temperature: $temperature · " +
            "Top K: $topK · Top P: $topP · Language: $language · Skills: $skillMode\n" +
            "Initial prompt SHA-256: $initialPromptSha256"
}
