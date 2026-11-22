package pl.michalmatu.aicallbridge.presentation.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.domain.models.AgentHubOverview
import pl.michalmatu.aicallbridge.domain.models.AgentHubSkillOverview

class AgentHubSummaryFormatterTest {
    private val formatter = AgentHubSummaryFormatter { id, args -> "${id}[${args.joinToString()}]" }

    @Test fun failedSourcesKeepIndependentErrorFallbacks() {
        val failedInt: Result<Int> = Result.failure(IllegalStateException("read failure"))
        val failedPair: Result<Pair<Int, Int>> = Result.failure(IllegalStateException("read failure"))
        val result = formatter.render(AgentHubOverview(
            Result.failure(IllegalStateException("read failure")),
            failedPair, failedPair, failedInt, failedInt))
        assertEquals(setOf("skills", "scenarios", "profiles", "vault", "tools"), result.keys)
        assertEquals(setOf("${R.string.ux07_agent_load_error}[]"), result.values.toSet())
    }

    @Test fun countsAndEmptyVaultPreserveExistingResources() {
        val result = formatter.render(AgentHubOverview(
            Result.success(AgentHubSkillOverview(2, true)),
            Result.success(3 to 2), Result.success(1 to 0),
            Result.success(0), Result.success(5)))
        val tone = "${R.string.skills_tone_formal}[]"
        val custom = "${R.string.skills_hub_custom_on}[]"
        assertEquals("${R.string.skills_hub_summary}[$tone, $custom]", result["skills"])
        assertEquals("${R.string.ux07_agent_scenario_count}[3, 2]", result["scenarios"])
        assertEquals("${R.string.ux07_agent_profile_count}[1, 0]", result["profiles"])
        assertEquals("${R.string.ux09_agent_vault_empty}[]", result["vault"])
        assertEquals("${R.string.ux07_agent_tools_count}[5]", result["tools"])
    }

    @Test fun legacyToneAndNonzeroVaultRemainDistinct() {
        val result = formatter.render(AgentHubOverview(
            Result.success(AgentHubSkillOverview(17, false)),
            Result.success(0 to 0), Result.success(0 to 0),
            Result.success(4), Result.success(0)))
        val tone = "${R.string.skills_hub_legacy_style}[]"
        val custom = "${R.string.skills_hub_custom_off}[]"
        assertEquals("${R.string.skills_hub_summary}[$tone, $custom]", result["skills"])
        assertEquals("${R.string.ux07_agent_vault_count}[4]", result["vault"])
    }
}
