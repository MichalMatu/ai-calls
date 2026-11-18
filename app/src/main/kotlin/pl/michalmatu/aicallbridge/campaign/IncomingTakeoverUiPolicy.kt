package pl.michalmatu.aicallbridge.campaign

/**
 * An outgoing or unmanaged Telecom call must not display AI takeover controls.
 * Only an incoming call already owned by the assistant can be taken over.
 */
internal object IncomingTakeoverUiPolicy {
    private val aiOwnedStates = setOf("ANSWERING", "STARTING", "AI", "ENDING")

    fun canTakeOver(incomingAssistantStatus: String?): Boolean =
        incomingAssistantStatus?.substringBefore(':') in aiOwnedStates
}
