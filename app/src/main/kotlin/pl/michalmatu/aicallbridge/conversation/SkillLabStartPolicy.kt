package pl.michalmatu.aicallbridge.conversation

/**
 * Pure Lab run validation and routing selection. No Activity, persistence,
 * native model or external call effects belong in this decision.
 */
internal object SkillLabStartPolicy {
    sealed interface Decision {
        data object CaseRequired : Decision
        data object InvalidBudget : Decision
        data class Accepted(
            val contextTokenBudget: Int?,
            val skillLoadingMode: SkillLoadingMode,
        ) : Decision
    }

    fun decide(
        adaptive: Boolean,
        hasCase: Boolean,
        contextBudgetText: String,
        contextualSkills: Boolean,
    ): Decision {
        if (adaptive && !hasCase) return Decision.CaseRequired
        val value = contextBudgetText.trim()
        val budget = value.toIntOrNull()
        if (value.isNotEmpty() && (budget == null || budget <= 0)) return Decision.InvalidBudget
        return Decision.Accepted(
            contextTokenBudget = budget,
            skillLoadingMode = if (contextualSkills) SkillLoadingMode.CONTEXTUAL
                else SkillLoadingMode.ALL_ACTIVE,
        )
    }
}
