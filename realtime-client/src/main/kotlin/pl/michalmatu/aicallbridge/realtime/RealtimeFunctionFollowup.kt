package pl.michalmatu.aicallbridge.realtime

/**
 * Response-scoped tool-choice policy for the follow-up response after one function result.
 *
 * Session-wide tool selection remains independent. This type is intentionally narrow so app-side
 * policy can force the next response through one exact function without changing the whole session.
 */
sealed interface RealtimeFunctionFollowup {
    data object Auto : RealtimeFunctionFollowup

    data object NoTools : RealtimeFunctionFollowup

    data class ForceFunction(val name: String) : RealtimeFunctionFollowup {
        init {
            require(FUNCTION_NAME.matches(name)) {
                "forced Realtime function name must contain only letters, digits, underscore, or hyphen"
            }
        }
    }

    private companion object {
        val FUNCTION_NAME = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
