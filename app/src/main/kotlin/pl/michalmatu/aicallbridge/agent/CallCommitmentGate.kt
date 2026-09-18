package pl.michalmatu.aicallbridge.agent

import java.security.SecureRandom
import java.util.Base64

/** Opaque one-shot authorization proving that app-owned policy approved one concrete proposal. */
class CallCommitmentAuthorization(
    val value: String,
) {
    init {
        require(TOKEN_PATTERN.matches(value)) {
            "commitment authorization must be a URL-safe token of 1..256 characters"
        }
    }

    override fun toString(): String = "CallCommitmentAuthorization(value=REDACTED)"

    private companion object {
        val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{1,256}")
    }
}

/**
 * App-owned one-shot gate for external commitments.
 *
 * The language model never creates authority. Policy/user approval calls [authorize], which binds
 * an opaque permit to the exact concrete proposal. Only the current permit can be consumed and it
 * disappears atomically after one successful consume. Issuing a newer permit revokes the older one.
 */
class CallCommitmentGate(
    private val tokenFactory: () -> String = ::newSecureToken,
) {
    private val lock = Any()
    private var current: Entry? = null

    fun authorize(proposal: CallProposal): CallCommitmentAuthorization {
        val authorization = CallCommitmentAuthorization(tokenFactory())
        synchronized(lock) {
            current = Entry(authorization.value, proposal)
        }
        return authorization
    }

    fun consume(token: String): Result<CallProposal> {
        if (!TOKEN_PATTERN.matches(token)) {
            return Result.failure(IllegalArgumentException("invalid commitment authorization token"))
        }
        synchronized(lock) {
            val entry = current
                ?: return Result.failure(IllegalStateException("no commitment authorization is active"))
            if (entry.token != token) {
                return Result.failure(IllegalStateException("commitment authorization does not match"))
            }
            current = null
            return Result.success(entry.proposal)
        }
    }

    fun hasAuthorization(): Boolean = synchronized(lock) { current != null }

    fun clear() {
        synchronized(lock) {
            current = null
        }
    }

    override fun toString(): String =
        "CallCommitmentGate(active=${hasAuthorization()}, authorization=REDACTED)"

    private data class Entry(
        val token: String,
        val proposal: CallProposal,
    )

    private companion object {
        val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{1,256}")
        val SECURE_RANDOM = SecureRandom()

        fun newSecureToken(): String {
            val bytes = ByteArray(24)
            SECURE_RANDOM.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
