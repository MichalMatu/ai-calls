package pl.michalmatu.aicallbridge.realtime

/**
 * Short-lived client credential minted by the developer-controlled backend.
 *
 * This is deliberately not a data class: generated toString/copy helpers must never expose the
 * secret value in logs. Long-lived OpenAI API keys do not belong in this client-side model.
 */
class RealtimeClientSecret(
    val value: String,
    val expiresAtEpochSeconds: Long,
) {
    init {
        require(value.isNotBlank()) { "value must not be blank" }
        require(expiresAtEpochSeconds > 0L) { "expiresAtEpochSeconds must be > 0" }
    }

    fun isExpired(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= expiresAtEpochSeconds

    override fun toString(): String =
        "RealtimeClientSecret(value=REDACTED, expiresAtEpochSeconds=$expiresAtEpochSeconds)"
}
