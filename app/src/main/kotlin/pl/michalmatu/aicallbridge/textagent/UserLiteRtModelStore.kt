package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Models explicitly imported by the owner. Never confuses a file name with model identity. */
internal data class UserLiteRtModel(
    val id: String,
    val title: String,
    val sha256: String,
    val bytes: Long,
) {
    val filename: String get() = "$id.litertlm"
}

internal sealed interface UserModelImportResult {
    data class Installed(val model: UserLiteRtModel) : UserModelImportResult
    data class Failure(val reason: String) : UserModelImportResult
}

/**
 * An app-owned LiteRT-LM model library.
 *
 * Import streams into a unique staging file, computes SHA-256 and atomically publishes the bytes
 * before committing the manifest. Only .litertlm inputs are accepted. No model is activated by
 * import; compatibility with the native engine is not implied by a valid file extension.
 */
internal class UserLiteRtModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("user_litert_models_v1", Context.MODE_PRIVATE)
    private val directory: File get() =
        appContext.getExternalFilesDir("models") ?: File(appContext.filesDir, "models")

    fun models(): List<UserLiteRtModel> = synchronized(lock) { readManifest() ?: emptyList() }
    fun find(id: String?): UserLiteRtModel? = if (id == null || !ID.matches(id)) null
        else models().firstOrNull { it.id == id }

    fun file(model: UserLiteRtModel): File {
        require(ID.matches(model.id) && model.id == "user-${model.sha256}")
        return File(directory, model.filename)
    }

    fun isInstalled(model: UserLiteRtModel): Boolean {
        val candidate = file(model)
        return candidate.isFile && candidate.canRead() && candidate.length() == model.bytes
    }

    fun import(
        displayName: String,
        input: InputStream,
        cancelled: () -> Boolean = { false },
        onProgress: (Long) -> Unit = {},
    ): UserModelImportResult {
        if (!displayName.lowercase(java.util.Locale.ROOT).endsWith(".litertlm"))
            return UserModelImportResult.Failure("unsupported_format_litertlm_required")
        val title = displayName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}]"), "").trim().take(96)
        if (title.isBlank()) return UserModelImportResult.Failure("invalid_model_name")
        synchronized(lock) {
            if (readManifest() == null) return UserModelImportResult.Failure("model_registry_corrupt")
        }
        if (!directory.isDirectory && !directory.mkdirs())
            return UserModelImportResult.Failure("model_directory_unavailable")
        val staged = File.createTempFile(".user-", ".importing", directory)
        var written = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            FileOutputStream(staged).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    if (cancelled() || Thread.currentThread().isInterrupted)
                        throw InterruptedIOException("model_import_cancelled")
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    written += n
                    onProgress(written)
                }
                output.flush()
                output.fd.sync()
            }
            if (written == 0L) return UserModelImportResult.Failure("model_empty")
            if (cancelled() || Thread.currentThread().isInterrupted)
                return UserModelImportResult.Failure("model_import_cancelled")
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val model = UserLiteRtModel("user-$sha", title, sha, written)
            val destination = file(model)
            // Never trust equal-length old bytes: atomically replace them with freshly hashed input.
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            synchronized(lock) {
                if (cancelled() || Thread.currentThread().isInterrupted)
                    return UserModelImportResult.Failure("model_import_cancelled")
                val existing = readManifest() ?: return UserModelImportResult.Failure("model_registry_corrupt")
                val updated = existing.filterNot { it.id == model.id } + model
                if (!writeManifest(updated)) return UserModelImportResult.Failure("model_registry_save_failed")
            }
            return UserModelImportResult.Installed(model)
        } catch (error: Exception) {
            return UserModelImportResult.Failure("model_import_${error.javaClass.simpleName}")
        } finally {
            staged.delete()
        }
    }

    /** Caller must verify IDLE; never delete the selected or base model. */
    fun delete(id: String, selectedId: String?): Boolean = synchronized(lock) {
        if (id == selectedId) return@synchronized false
        val previous = readManifest() ?: return@synchronized false
        val model = previous.firstOrNull { it.id == id } ?: return@synchronized false
        val target = file(model)
        if (target.exists() && !target.delete()) return@synchronized false
        writeManifest(previous.filterNot { it.id == id })
    }

    private fun readManifest(): List<UserLiteRtModel>? = try {
        val array = JSONArray(prefs.getString("models", "[]"))
        (0 until array.length()).map { i ->
            val value = array.getJSONObject(i)
            UserLiteRtModel(value.getString("id"), value.getString("title"),
                value.getString("sha256"), value.getLong("bytes")).also {
                require(ID.matches(it.id) && it.id == "user-${it.sha256}" && it.bytes > 0)
            }
        }
    } catch (_: Exception) { null }

    private fun writeManifest(models: List<UserLiteRtModel>): Boolean {
        val array = JSONArray()
        models.forEach { model ->
            array.put(JSONObject().put("id", model.id).put("title", model.title)
                .put("sha256", model.sha256).put("bytes", model.bytes))
        }
        return prefs.edit().putString("models", array.toString()).commit()
    }

    private companion object {
        val lock = Any()
        val ID = Regex("user-[0-9a-f]{64}")
    }
}
