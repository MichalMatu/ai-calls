package pl.michalmatu.aicallbridge.campaign

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Local export of a completed, app-private call recording via Android's user-selected
 * document destination. No ADB, cloud upload, diagnostic server or broad storage permission.
 */
internal object CampaignEvidenceArchive {
    private val audioFile = Regex("turn-[0-9]+-(rx|tx)\\.wav")

    fun completedSessions(root: File): List<File> =
        root.listFiles().orEmpty()
            .filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) &&
                File(it, "COMPLETE").isFile }
            .sortedByDescending { it.name }

    fun writeCompletedSession(session: File, destination: OutputStream): Int {
        require(session.isDirectory && !Files.isSymbolicLink(session.toPath()) &&
            File(session, "COMPLETE").isFile) { "evidence_session_not_complete" }
        val entries = session.listFiles().orEmpty()
            .filter { file ->
                file.isFile && !Files.isSymbolicLink(file.toPath()) &&
                    (file.name == "COMPLETE" || file.name == "timeline.jsonl" ||
                        audioFile.matches(file.name))
            }
            .sortedBy { it.name }
        require(entries.any { it.name == "timeline.jsonl" }) { "evidence_timeline_missing" }
        ZipOutputStream(destination).use { zip ->
            for (file in entries) {
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return entries.size
    }
}
