package pl.michalmatu.aicallbridge.presentation.agent

import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.domain.models.AgentHubOverview

/** Formats already loaded state without reaching into Android persistence or services. */
internal class AgentHubSummaryFormatter(private val string: (Int, Array<out Any>) -> String) {
    private fun text(id: Int, vararg args: Any): String = string(id, args)

    fun render(snapshot: AgentHubOverview): Map<String, String> {
        val error = text(R.string.ux07_agent_load_error)
        val skillSummary = snapshot.skills.getOrNull()?.let { value ->
            val tone = text(when (value.toneVersion) {
                1 -> R.string.skills_tone_natural
                2 -> R.string.skills_tone_formal
                else -> R.string.skills_hub_legacy_style
            })
            text(R.string.skills_hub_summary, tone, text(
                if (value.customInstructions) R.string.skills_hub_custom_on
                else R.string.skills_hub_custom_off))
        } ?: error
        return mapOf(
            "skills" to skillSummary,
            "scenarios" to (snapshot.scenarios.getOrNull()?.let {
                text(R.string.ux07_agent_scenario_count, it.first, it.second)
            } ?: error),
            "profiles" to (snapshot.profiles.getOrNull()?.let {
                text(R.string.ux07_agent_profile_count, it.first, it.second)
            } ?: error),
            "vault" to (snapshot.vault.getOrNull()?.let {
                if (it == 0) text(R.string.ux09_agent_vault_empty)
                else text(R.string.ux07_agent_vault_count, it)
            } ?: error),
            "tools" to (snapshot.tools.getOrNull()?.let {
                text(R.string.ux07_agent_tools_count, it)
            } ?: error),
        )
    }
}
