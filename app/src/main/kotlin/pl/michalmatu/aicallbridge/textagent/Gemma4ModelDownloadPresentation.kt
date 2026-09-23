package pl.michalmatu.aicallbridge.textagent

import java.util.Locale

internal class Gemma4ModelDownloadPresentation(
    private val source: Gemma4ModelAcquisitionSource = Gemma4ModelAcquisitionCatalog.GEMMA_4_E2B_IT,
) {
    val startButtonLabel: String = "Download reviewed Gemma 4 model (~${downloadGigabytes()} GB)"
    val confirmButtonLabel: String = "Download ${downloadGigabytes()} GB"
    val cancelButtonLabel: String = "Cancel"

    val sourceSummary: String = buildString {
        append("Reviewed source: ").append(source.repositoryId).append('\n')
        append("License: ").append(displayLicense()).append('\n')
        append("Expected download: ").append(downloadGigabytes()).append(" GB")
    }

    val confirmationMessage: String = buildString {
        append("Source: ").append(source.repositoryId).append('\n')
        append("License: ").append(displayLicense()).append('\n')
        append("Pinned revision: ").append(source.revision).append('\n')
        source.expectedBytes?.let { expected ->
            append("Download: ").append(downloadGigabytes()).append(" GB (")
                .append(String.format(Locale.US, "%,d", expected)).append(" bytes)\n")
        }
        append("The app verifies the full pinned SHA-256 before replacing the active model.\n")
        append("Retry starts from byte 0. Wi-Fi is recommended.")
    }

    fun progressText(progress: Gemma4ModelDownloadProgress): String {
        val total = progress.expectedBytes
        if (total == null || total <= 0L) {
            return "Downloading Gemma 4: ${megabytes(progress.bytesRead)} MB"
        }
        val percent = ((progress.bytesRead * 100L) / total).coerceIn(0L, 100L)
        return "Downloading Gemma 4: $percent% (${megabytes(progress.bytesRead)} MB / ${megabytes(total)} MB)"
    }

    fun progressPercent(progress: Gemma4ModelDownloadProgress): Int {
        val total = progress.expectedBytes ?: return 0
        if (total <= 0L) return 0
        return ((progress.bytesRead * 100L) / total).coerceIn(0L, 100L).toInt()
    }

    private fun downloadGigabytes(): String {
        val bytes = source.expectedBytes ?: return "unknown"
        return String.format(Locale.US, "%.2f", bytes / 1_000_000_000.0)
    }

    private fun megabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f", bytes / 1_000_000.0)

    private fun displayLicense(): String = when (source.declaredLicense.lowercase(Locale.US)) {
        "apache-2.0" -> "Apache-2.0"
        else -> source.declaredLicense
    }
}
