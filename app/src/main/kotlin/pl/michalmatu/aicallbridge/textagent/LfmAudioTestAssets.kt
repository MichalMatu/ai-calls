package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Fixed, pinned bundle; selecting any unknown .gguf never authorizes execution. */
internal object LfmAudioTestAssets {
    internal data class Part(val name: String, val bytes: Long, val sha256: String)
    val parts = listOf(
        Part("LFM2.5-Audio-1.5B-Q4_0.gguf", 695750880L, "3583bee853be20331ca342b0593fefd8acc43fb61a41ec6f1a1dc7465823e0d8"),
        Part("mmproj-LFM2.5-Audio-1.5B-Q4_0.gguf", 219511136L, "6b483682c263b100f8cc8022d61507e446b1d320b9febc328e7960f72d03f7ea"),
        Part("vocoder-LFM2.5-Audio-1.5B-Q4_0.gguf", 108986560L, "423cfcb054f41b69a5706226c243abc96d2531c3aff1121f7a2ed17149b79c95"),
        Part("tokenizer-LFM2.5-Audio-1.5B-Q4_0.gguf", 50546112L, "01ec6afe4578bb1e02a4d43d87e7e5827d6b3d94d2d36912ee931b9c3050f1c1"),
    )
    const val ACTIVE_KEY = "lfm_bundle_directory"
    const val MODE_KEY = "audio_lab_model"
    private const val PREFS = "lfm_audio_lab_v1"
    private val fileName = Regex("lfm-[0-9a-f-]{36}")

    fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun root(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "lfm-audio-lab")
    fun selected(context: Context): File? {
        val id = preferences(context).getString(ACTIVE_KEY, null) ?: return null
        if (!fileName.matches(id)) return null
        return File(root(context), id).takeIf(::isReady)
    }
    fun isReady(directory: File) = directory.isDirectory &&
        parts.all { p -> File(directory, p.name).let { it.isFile && it.length() == p.bytes } }
    fun runnerFile(context: Context) =
        File(context.applicationInfo.nativeLibraryDir, "libaicalls_lfm_cli.so")
    fun runnerReady(context: Context) = runnerFile(context).canExecute()

    /** SAF tree import: one guided pick, a single verified copy, never auto-select Calls. */
    fun importTree(context: Context, tree: Uri, progress: (Int) -> Unit = {}): File {
        val resolver = context.contentResolver
        val id = DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        val documents = HashMap<String, Pair<Uri, Long>>()
        resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                if (parts.any { it.name == name }) {
                    require(!documents.containsKey(name)) { "duplicate_model_file" }
                    documents[name] = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to
                        (if (c.isNull(2)) -1L else c.getLong(2))
                }
            }
        } ?: error("folder_unavailable")
        require(documents.keys == parts.map { it.name }.toSet()) { "four_gguf_files_required" }
        val base = root(context)
        require(base.isDirectory || base.mkdirs()) { "model_storage_unavailable" }
        require(base.usableSpace > parts.sumOf { it.bytes } + 250_000_000L) {
            "not_enough_storage_for_private_import"
        }
        val next = File(base, "lfm-" + UUID.randomUUID())
        require(next.mkdir()) { "staging_unavailable" }
        try {
            parts.forEachIndexed { i, part ->
                val (uri, metadataSize) = checkNotNull(documents[part.name])
                require(metadataSize == -1L || metadataSize == part.bytes) { "incorrect_model_file_size" }
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                val destination = File(next, part.name)
                resolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().buffered(1024 * 1024).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val length = input.read(buffer)
                            if (length < 0) break
                            if (length == 0) continue
                            written += length
                            require(written <= part.bytes) { "model_file_too_large" }
                            digest.update(buffer, 0, length)
                            output.write(buffer, 0, length)
                        }
                    }
                } ?: error("cannot_open_model_file")
                require(written == part.bytes &&
                    digest.digest().joinToString("") { "%02x".format(it) } == part.sha256) {
                    "model_file_hash_mismatch"
                }
                progress((i + 1) * 100 / parts.size)
            }
            require(isReady(next))
            val prefs = preferences(context)
            val old = prefs.getString(ACTIVE_KEY, null)
            check(prefs.edit().putString(ACTIVE_KEY, next.name).commit()) {
                "model_selection_save_failed"
            }
            if (old != null && fileName.matches(old) && old != next.name) {
                File(base, old).deleteRecursively()
            }
            return next
        } finally {
            if (preferences(context).getString(ACTIVE_KEY, null) != next.name)
                next.deleteRecursively()
        }
    }
}
