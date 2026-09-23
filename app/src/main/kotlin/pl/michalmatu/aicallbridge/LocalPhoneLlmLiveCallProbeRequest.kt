package pl.michalmatu.aicallbridge

import pl.michalmatu.aicallbridge.developerrelay.ChatRelayEnvelope
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

internal class LocalPhoneLlmLiveCallProbeRequest private constructor(
    val provider: TextLlmProvider,
    val gateCFastPath: Boolean,
    val liveCallTarget: String?,
    val orangeLiveAction: OrangeLiveAction?,
    val gateCRelaySessionId: String?,
) {
    val gateCHybridDialogue: Boolean
        get() = gateCFastPath && gateCRelaySessionId != null

    companion object {
        fun create(
            provider: TextLlmProvider,
            gateCFastPath: Boolean,
            liveCallTarget: String?,
            orangeLiveActionId: String? = null,
            gateCRelaySessionId: String? = null,
        ): LocalPhoneLlmLiveCallProbeRequest {
            require(
                provider == TextLlmProvider.LOCAL_PHONE_LLM ||
                    provider == TextLlmProvider.EDGE_GALLERY,
            ) {
                "provider_not_enabled_for_local_live_call_${provider.name.lowercase()}"
            }

            if (!gateCFastPath) {
                require(orangeLiveActionId.isNullOrBlank()) {
                    "orange_live_action_requires_gate_c"
                }
                require(gateCRelaySessionId.isNullOrBlank()) {
                    "gate_c_relay_session_requires_gate_c"
                }
                return LocalPhoneLlmLiveCallProbeRequest(
                    provider = provider,
                    gateCFastPath = false,
                    liveCallTarget = null,
                    orangeLiveAction = null,
                    gateCRelaySessionId = null,
                )
            }

            val target = liveCallTarget?.trim().orEmpty()
            require(target == GateCLiveCallScenarioFactory.ORANGE_SUPPORT_NUMBER) {
                "target_not_allowlisted_for_gate_c_live_probe"
            }
            val relaySession = gateCRelaySessionId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.also { ChatRelayEnvelope(it, 1, "probe").validate() }
            return LocalPhoneLlmLiveCallProbeRequest(
                provider = provider,
                gateCFastPath = true,
                liveCallTarget = target,
                orangeLiveAction = OrangeLiveAction.fromWireId(orangeLiveActionId),
                gateCRelaySessionId = relaySession,
            )
        }
    }
}
