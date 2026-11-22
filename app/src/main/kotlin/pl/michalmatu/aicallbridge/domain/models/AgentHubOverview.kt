package pl.michalmatu.aicallbridge.domain.models

/** Data required to render the read-only Agent hub; every source fails independently. */
internal data class AgentHubSkillOverview(val toneVersion: Int, val customInstructions: Boolean)

internal data class AgentHubOverview(
    val skills: Result<AgentHubSkillOverview>,
    val scenarios: Result<Pair<Int, Int>>,
    val profiles: Result<Pair<Int, Int>>,
    val vault: Result<Int>,
    val tools: Result<Int>,
)
