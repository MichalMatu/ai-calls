package pl.michalmatu.aicallbridge.ui

/** Parses the existing key=value report without changing its disk/ADB format. */
internal object CapabilityReportParser {
    fun read(report: String): Map<String, String> = buildMap {
        report.lineSequence().forEach { line ->
            val divider = line.indexOf('=')
            if (divider > 0) put(line.substring(0, divider), line.substring(divider + 1))
        }
    }

    enum class AudioSourceState { INITIALIZED, UNINITIALIZED, ERROR, UNKNOWN }

    fun audioSource(raw: String?): AudioSourceState = when {
        raw == null -> AudioSourceState.UNKNOWN
        raw.startsWith("result:initialized,") -> AudioSourceState.INITIALIZED
        raw.startsWith("result:uninitialized,") -> AudioSourceState.UNINITIALIZED
        raw.startsWith("error:") -> AudioSourceState.ERROR
        else -> AudioSourceState.UNKNOWN
    }

    data class AudioDevice(val id: String, val type: String, val direction: String, val product: String)

    fun device(raw: String): AudioDevice {
        fun field(name: String) = Regex("(?:^|,)" + name + "=([^,]*)")
            .find(raw)?.groupValues?.get(1).orEmpty()
        return AudioDevice(field("id"), field("type").substringBefore('('), field("dir"),
            raw.substringAfter("product=", "").substringBefore(",rates="))
    }
}
