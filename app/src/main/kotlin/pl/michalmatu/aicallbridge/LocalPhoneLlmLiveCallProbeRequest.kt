package pl.michalmatu.aicallbridge

import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

internal class LocalPhoneLlmLiveCallProbeRequest private constructor(
    val provider: TextLlmProvider,
    val gateCFastPath: Boolean,
    val liveCallTarget: String?,
) {
    companion object {
        fun create(
            provider: TextLlmProvider,
            gateCFastPath: Boolean,
            liveCallTarget: String?,
        ): LocalPhoneLlmLiveCallProbeRequest {
            require(
                provider == TextLlmProvider.LOCAL_PHONE_LLM ||
                    provider == TextLlmProvider.EDGE_GALLERY,
            ) {
                "provider_not_enabled_for_local_live_call_${provider.name.lowercase()}"
            }

            if (!gateCFastPath) {
                return LocalPhoneLlmLiveCallProbeRequest(
                    provider = provider,
                    gateCFastPath = false,
                    liveCallTarget = null,
                )
            }

            require(provider == TextLlmProvider.LOCAL_PHONE_LLM) {
                "gate_c_live_probe_requires_local_phone_llm_provider"
            }
            val target = liveCallTarget?.trim().orEmpty()
            require(target == GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER) {
                "target_not_allowlisted_for_gate_c_live_probe"
            }
            return LocalPhoneLlmLiveCallProbeRequest(
                provider = provider,
                gateCFastPath = true,
                liveCallTarget = target,
            )
        }
    }
}
