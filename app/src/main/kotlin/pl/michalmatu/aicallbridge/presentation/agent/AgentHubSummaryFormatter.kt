package pl.michalmatu.aicallbridge.presentation.agent

import pl.michalmatu.aicallbridge.domain.models.AgentHubOverview

/** Semantic localization keys. Android string resources are selected only in the view adapter. */
internal enum class AgentSummaryTextKey {
    LOAD_ERROR, TONE_NATURAL, TONE_FORMAL, TONE_LEGACY,
    SKILLS_SUMMARY, CUSTOM_ON, CUSTOM_OFF, SCENARIO_COUNT,
    PROFILE_COUNT, VAULT_EMPTY, VAULT_COUNT, TOOLS_COUNT,
}

/** Formats already loaded state without reaching into Android resources or services. */
internal class AgentHubSummaryFormatter(private val string: (AgentSummaryTextKey, Array<out Any>) -> String) {
    private fun text(id: AgentSummaryTextKey, vararg args: Any): String = string(id, args)

    fun render(snapshot: AgentHubOverview): Map<String, String> {
        val error = text(AgentSummaryTextKey.LOAD_ERROR)
        val skillSummary = snapshot.skills.getOrNull()?.let { value ->
            val tone = text(when (value.toneVersion) {
                1 -> AgentSummaryTextKey.TONE_NATURAL
                2 -> AgentSummaryTextKey.TONE_FORMAL
                else -> AgentSummaryTextKey.TONE_LEGACY
            })
            text(AgentSummaryTextKey.SKILLS_SUMMARY, tone, text(
                if (value.customInstructions) AgentSummaryTextKey.CUSTOM_ON
                else AgentSummaryTextKey.CUSTOM_OFF))
        } ?: error
        return mapOf(
            "skills" to skillSummary,
            "scenarios" to (snapshot.scenarios.getOrNull()?.let {
                text(AgentSummaryTextKey.SCENARIO_COUNT, it.first, it.second)
            } ?: error),
            "profiles" to (snapshot.profiles.getOrNull()?.let {
                text(AgentSummaryTextKey.PROFILE_COUNT, it.first, it.second)
            } ?: error),
            "vault" to (snapshot.vault.getOrNull()?.let {
                if (it == 0) text(AgentSummaryTextKey.VAULT_EMPTY)
                else text(AgentSummaryTextKey.VAULT_COUNT, it)
            } ?: error),
            "tools" to (snapshot.tools.getOrNull()?.let {
                text(AgentSummaryTextKey.TOOLS_COUNT, it)
            } ?: error),
        )
    }
}
