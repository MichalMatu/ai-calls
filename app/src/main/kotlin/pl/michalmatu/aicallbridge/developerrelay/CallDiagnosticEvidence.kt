package pl.michalmatu.aicallbridge.developerrelay

/**
 * Session-local telemetry ledger. Capture/recognition/TTS and the UI can report
 * from different threads; a full snapshot must never iterate a concurrently
 * modified MutableList. This class has no authority to control the call.
 */
internal class CallDiagnosticEvidence(initial: List<String>) {
    private val lines = initial.toMutableList()

    @Synchronized
    operator fun plusAssign(line: String) {
        // Keep receipt output bounded; no transcript/model argument belongs here.
        if (lines.size < 1_024) lines.add(line.take(256))
    }

    @Synchronized
    fun render(): String = lines.joinToString(separator = "\n", postfix = "\n")

    companion object {
        /** Preserve technical identifiers, never free-form error/model prose. */
        private val SAFE_CODE = Regex("[A-Za-z][A-Za-z0-9_]{0,79}")

        fun safeCode(value: String): String =
            value.takeIf { SAFE_CODE.matches(it) } ?: "redacted"
    }
}
