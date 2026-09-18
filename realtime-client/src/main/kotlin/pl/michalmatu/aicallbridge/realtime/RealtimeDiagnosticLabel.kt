package pl.michalmatu.aicallbridge.realtime

/** Bounded ASCII diagnostic labels safe for one-line logs/traces. */
internal object RealtimeDiagnosticLabel {
    const val REDACTED = "REDACTED"
    private const val MAX_CHARS = 64

    fun sanitize(value: String?): String? {
        if (value == null) return null
        if (value.length !in 1..MAX_CHARS) return REDACTED
        if (!value.all(::isSafeChar)) return REDACTED
        return value
    }

    private fun isSafeChar(char: Char): Boolean =
        char in 'a'..'z' ||
            char in 'A'..'Z' ||
            char in '0'..'9' ||
            char == '_' ||
            char == '-' ||
            char == '.' ||
            char == ':' ||
            char == '$'
}
