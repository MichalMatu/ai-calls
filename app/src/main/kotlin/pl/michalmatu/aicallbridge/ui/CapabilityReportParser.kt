package pl.michalmatu.aicallbridge.ui

/** Parses the existing key=value report without changing its disk/ADB format. */
internal object CapabilityReportParser {
    fun read(report: String): Map<String, String> = buildMap {
        report.lineSequence().forEach { line ->
            val divider = line.indexOf('=')
            if (divider > 0) put(line.substring(0, divider), line.substring(divider + 1))
        }
    }

    fun audioSource(raw: String?): String = when {
        raw == null -> "unknown"
        raw.startsWith("result:initialized,") -> "initialized"
        raw.startsWith("result:uninitialized,") -> "uninitialized"
        raw.startsWith("error:") -> "error: " + raw.removePrefix("error:").substringBefore(':')
        else -> "unknown"
    }

    fun deviceType(raw: String): String =
        raw.substringAfter("type=", "UNKNOWN").substringBefore('(').replace('_', ' ')

    fun deviceProduct(raw: String): String =
        raw.substringAfter("product=", "unknown").substringBefore(",rates=")
}
