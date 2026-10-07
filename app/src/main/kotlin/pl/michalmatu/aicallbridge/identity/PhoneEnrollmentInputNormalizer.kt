package pl.michalmatu.aicallbridge.identity

internal object PhoneEnrollmentInputNormalizer {
    fun normalize(raw: CharSequence): String? {
        if (raw.isEmpty()) return null
        if (raw.any { it !in '0'..'9' && it !in ALLOWED_FORMATTING }) return null

        val digits = buildString(raw.length) {
            raw.forEach { char ->
                if (char in '0'..'9') append(char)
            }
        }
        return digits.takeIf { it.length in MIN_DIGITS..MAX_DIGITS }
    }

    private const val ALLOWED_FORMATTING = " +-()"
    private const val MIN_DIGITS = 7
    private const val MAX_DIGITS = 15
}
