package pl.michalmatu.aicallbridge.developerrelay

import pl.michalmatu.aicallbridge.conversation.AndroidConversationRuntime

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream

/**
 * Session-local telemetry ledger. Capture/recognition/TTS and the UI can report
 * from different threads; a full snapshot must never iterate a concurrently
 * modified MutableList. This class has no authority to control the call.
 */
internal class CallDiagnosticEvidence(initial: List<String>) {
    private val lines = initial.toMutableList()

    @Synchronized
    operator fun plusAssign(line: String) {
        // Preserve the complete sequence of technical events during long calls.
        // The payload is sanitized metadata, never free-form conversation text.
        lines.add(line.take(256))
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

/** Latest-only, crash-safe on-device evidence. Never exports to network/logcat. */
internal object PrivateDiagnosticReportStore {
    fun write(directory: File, name: String, report: String): Boolean {
        require(name == SupervisedCallReadinessDiagnostics.REPORT_FILE ||
            name == AndroidConversationRuntime.REPORT_FILE) { "invalid_diagnostic_report_name" }
        val file = AtomicFile(File(directory, name))
        var stream: FileOutputStream? = null
        return try {
            val opened = file.startWrite()
            stream = opened
            opened.write(report.toByteArray(Charsets.UTF_8))
            file.finishWrite(opened)
            true
        } catch (_: Throwable) {
            stream?.let { runCatching { file.failWrite(it) } }
            false
        }
    }
}
