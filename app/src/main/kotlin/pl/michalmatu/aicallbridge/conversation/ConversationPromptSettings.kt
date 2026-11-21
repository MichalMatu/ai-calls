package pl.michalmatu.aicallbridge.conversation

/** Operator-authored model guidance. Tool grants and response validation are owned elsewhere. */
internal data class ConversationPromptSettings(
    val incomingRole: String? = null,
    val outgoingRole: String? = null,
    val additionalPermissionRules: String? = null,
    val spokenReplyStyle: String? = null,
) {
    fun role(direction: ConversationDirection): String? = when (direction) {
        ConversationDirection.INCOMING -> incomingRole
        ConversationDirection.OUTGOING -> outgoingRole
        ConversationDirection.UNSPECIFIED -> null
    }

    fun validated(): ConversationPromptSettings {
        require(listOf(incomingRole, outgoingRole, additionalPermissionRules, spokenReplyStyle)
            .all { it == null || it.isNotBlank() && it.length <= MAX_TEXT_LENGTH }) {
            "conversation_prompt_settings_invalid"
        }
        return this
    }

    override fun toString() = "ConversationPromptSettings(REDACTED)"

    companion object {
        const val MAX_TEXT_LENGTH = 2_000
        val DEFAULTS = ConversationPromptSettings()
    }
}
