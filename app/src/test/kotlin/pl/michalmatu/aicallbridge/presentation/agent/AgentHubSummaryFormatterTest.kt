package pl.michalmatu.aicallbridge.presentation.agent

import org.junit.Assert.assertEquals
import org.junit.Test
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
        assertEquals(setOf("${AgentSummaryTextKey.LOAD_ERROR}[]"), result.values.toSet())
    }

    @Test fun countsAndEmptyVaultPreserveExistingResources() {
        val result = formatter.render(AgentHubOverview(
            Result.success(AgentHubSkillOverview(2, true)),
            Result.success(3 to 2), Result.success(1 to 0),
            Result.success(0), Result.success(5)))
        val tone = "${AgentSummaryTextKey.TONE_FORMAL}[]"
        val custom = "${AgentSummaryTextKey.CUSTOM_ON}[]"
        assertEquals("${AgentSummaryTextKey.SKILLS_SUMMARY}[$tone, $custom]", result["skills"])
        assertEquals("${AgentSummaryTextKey.SCENARIO_COUNT}[3, 2]", result["scenarios"])
        assertEquals("${AgentSummaryTextKey.PROFILE_COUNT}[1, 0]", result["profiles"])
        assertEquals("${AgentSummaryTextKey.VAULT_EMPTY}[]", result["vault"])
        assertEquals("${AgentSummaryTextKey.TOOLS_COUNT}[5]", result["tools"])
    }

    @Test fun legacyToneAndNonzeroVaultRemainDistinct() {
        val result = formatter.render(AgentHubOverview(
            Result.success(AgentHubSkillOverview(17, false)),
            Result.success(0 to 0), Result.success(0 to 0),
            Result.success(4), Result.success(0)))
        val tone = "${AgentSummaryTextKey.TONE_LEGACY}[]"
        val custom = "${AgentSummaryTextKey.CUSTOM_OFF}[]"
        assertEquals("${AgentSummaryTextKey.SKILLS_SUMMARY}[$tone, $custom]", result["skills"])
        assertEquals("${AgentSummaryTextKey.VAULT_COUNT}[4]", result["vault"])
    }
}
