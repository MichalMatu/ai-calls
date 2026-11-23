package pl.michalmatu.aicallbridge.presentation.scenarios

import pl.michalmatu.aicallbridge.conversationcore.ScenarioDefinition
import pl.michalmatu.aicallbridge.conversationcore.ScenarioDraft

/**
 * Validate the outgoing information-only scenario handoff without Android,
 * Telecom, permissions or persistence. It never grants permission to dial.
 */
internal object ScenarioCallPreparationPolicy {
    sealed interface Result {
        data class Missing(val fieldIds: List<String>) : Result
        data object Invalid : Result
        data class Ready(val title: String, val goal: String, val brief: String) : Result
    }

    fun prepare(
        definition: ScenarioDefinition,
        draft: ScenarioDraft,
        language: String,
        title: String,
        goal: String,
    ): Result {
        val missing = draft.missingRequiredFields(definition)
        if (missing.isNotEmpty()) return Result.Missing(missing)
        val trimmedTitle = title.trim()
        val trimmedGoal = goal.trim()
        val fields = definition.fields.mapNotNull { field ->
            draft.values[field.id]?.trim()?.takeIf(String::isNotBlank)
                ?.let { field.title(language) + ": " + it }
        }
        val brief = (listOf(trimmedGoal) + fields).joinToString("\n")
        if (trimmedTitle.length !in 2..140 || trimmedGoal.length !in 5..300 ||
            brief.length !in 5..3500) return Result.Invalid
        return Result.Ready(trimmedTitle, trimmedGoal, brief)
    }
}
