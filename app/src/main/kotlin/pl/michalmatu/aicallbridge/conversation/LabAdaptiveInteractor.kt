package pl.michalmatu.aicallbridge.conversation

/** Explicit, single-use branches: caller input depends on the actual previous model reply. */
internal data class LabAdaptiveDecision(
    val ruleNumber: Int,
    val condition: String,
    val utterance: String,
)

internal class LabAdaptiveInteractor(private val test: LabCaseRevision) {
    private data class Rule(val number: Int, val condition: String, val utterance: String)
    private val rules = test.rules.lines().mapIndexedNotNull { index, source ->
        val line = source.trim()
        if (line.isBlank()) return@mapIndexedNotNull null
        val parts = line.split("=>", limit = 2)
        require(parts.size == 2 && parts.all { it.trim().isNotBlank() }) {
            "lab_adaptive_rule_invalid_line_" + (index + 1)
        }
        val condition = parts[0].trim().lowercase(java.util.Locale.ROOT)
        require(condition in setOf("question", "end", "otherwise", "refusal") ||
            (condition.startsWith("contains:") && condition.substringAfter(':').isNotBlank()) ||
            (condition.startsWith("missing:") && condition.substringAfter(':').isNotBlank())) {
            "lab_adaptive_condition_unknown_line_" + (index + 1)
        }
        Rule(index + 1, condition, parts[1].trim())
    }
    private val consumed = mutableSetOf<Int>()
    fun firstMessage(): String = test.opening

    /** A rule is used once. No default repeated scripts or self-perpetuating loops. */
    fun next(reply: String, proposedEnd: Boolean): LabAdaptiveDecision? {
        val normalized = reply.lowercase(java.util.Locale.ROOT)
        val rule = rules.firstOrNull { it.number !in consumed && when {
            it.condition == "end" -> proposedEnd
            it.condition == "question" -> '?' in reply
            it.condition == "refusal" ->
                listOf("nie mogę", "nie moge", "cannot", "can't").any(normalized::contains)
            it.condition.startsWith("contains:") ->
                normalized.contains(it.condition.substringAfter(':'))
            it.condition.startsWith("missing:") ->
                !normalized.contains(it.condition.substringAfter(':'))
            else -> it.condition == "otherwise"
        } } ?: return null
        consumed.add(rule.number)
        return LabAdaptiveDecision(rule.number, rule.condition, rule.utterance)
    }
}
