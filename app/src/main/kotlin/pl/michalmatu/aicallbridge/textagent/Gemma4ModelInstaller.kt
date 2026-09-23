package pl.michalmatu.aicallbridge.textagent

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal data class Gemma4ModelSpec(
    val modelId: String,
    val fileName: String,
    val sha256: String,
    val expectedBytes: Long? = null,
) {
    init {
        require(modelId.isNotBlank()) { "model_id_must_not_be_blank" }
        require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName) {
            "model_file_name_invalid"
        }
        require(SHA256_REGEX.matches(sha256)) { "model_sha256_invalid" }
        require(expectedBytes == null || expectedBytes > 0L) { "model_expected_bytes_invalid" }
    }

    private companion object {
        val SHA256_REGEX = Regex("[0-9a-f]{64}")
    }
}

internal object Gemma4ModelCatalog {
    val GEMMA_4_E2B_IT = Gemma4ModelSpec(
        modelId = "Gemma 4 E2B IT",
        fileName = "gemma-4-E2B-it.litertlm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        expectedBytes = 2_588_147_712L,
    )
}

internal enum class Gemma4ModelReadinessState {
    MISSING,
    INVALID,
    READY,
}

internal data class Gemma4ModelReadiness(
    val state: Gemma4ModelReadinessState,
    val file: File,
    val reason: String? = null,
)

/**
 * Cheap ordinary readiness check for the app-owned active model.
 *
 * Full content integrity remains owned by [Gemma4ModelInstaller] at import time. Ordinary call
 * preparation intentionally does not re-hash the multi-gigabyte model on every session; it checks
 * the reviewed destination plus pinned catalog size so missing/truncated/replaced files fail before
 * LiteRT initialization. A future stronger revalidation policy can remain a separate explicit gate.
 */
internal class Gemma4ModelReadinessProbe(
    private val directory: File,
    private val spec: Gemma4ModelSpec = Gemma4ModelCatalog.GEMMA_4_E2B_IT,
) {
    fun check(): Gemma4ModelReadiness {
        val active = File(directory, spec.fileName)
        if (!active.exists()) {
            return Gemma4ModelReadiness(
                state = Gemma4ModelReadinessState.MISSING,
                file = active,
                reason = "model_missing",
            )
        }
        if (!active.isFile || !active.canRead()) {
            return Gemma4ModelReadiness(
                state = Gemma4ModelReadinessState.INVALID,
                file = active,
                reason = "model_unreadable",
            )
        }
        val bytes = active.length()
        if (bytes <= 0L) {
            return Gemma4ModelReadiness(
                state = Gemma4ModelReadinessState.INVALID,
                file = active,
                reason = "model_empty",
            )
        }
        val expectedBytes = spec.expectedBytes
        if (expectedBytes != null && bytes != expectedBytes) {
            return Gemma4ModelReadiness(
                state = Gemma4ModelReadinessState.INVALID,
                file = active,
                reason = "model_size_mismatch",
            )
        }
        return Gemma4ModelReadiness(
            state = Gemma4ModelReadinessState.READY,
            file = active,
        )
    }
}

internal sealed interface Gemma4ModelInstallResult {
    data class Success(
        val modelId: String,
        val file: File,
        val bytesWritten: Long,
        val sha256: String,
    ) : Gemma4ModelInstallResult

    data class Failure(val reason: String) : Gemma4ModelInstallResult
}

/**
 * Installs one reviewed Gemma model into an app-owned directory.
 *
 * Incoming bytes are written to a sibling staging file, hashed while streaming and activated only
 * when the digest matches the pinned model identity. The final replacement requests an atomic move
 * on the same filesystem; an unsupported/failed atomic move fails closed and preserves the current
 * active model. No source application or transport is trusted by this class.
 */
internal class Gemma4ModelInstaller(
    private val directory: File,
    private val spec: Gemma4ModelSpec = Gemma4ModelCatalog.GEMMA_4_E2B_IT,
    private val atomicMove: (File, File) -> Unit = { source, destination ->
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    },
) {
    fun activeFile(): File = File(directory, spec.fileName)

    fun install(input: InputStream): Gemma4ModelInstallResult {
        if (!ensureDirectory()) {
            return Gemma4ModelInstallResult.Failure("model_directory_unavailable")
        }

        val staged = File(directory, ".${spec.fileName}.importing")
        if (staged.exists() && !staged.delete()) {
            return Gemma4ModelInstallResult.Failure("model_staging_cleanup_failed")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var bytesWritten = 0L
        try {
            FileOutputStream(staged).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedIOException("model_import_interrupted")
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytesWritten += read
                }
                output.flush()
                output.fd.sync()
            }
        } catch (error: Throwable) {
            staged.delete()
            return Gemma4ModelInstallResult.Failure(
                "model_import_failed_${error.javaClass.simpleName.ifBlank { "Throwable" }}",
            )
        }

        if (bytesWritten <= 0L) {
            staged.delete()
            return Gemma4ModelInstallResult.Failure("model_empty")
        }

        val expectedBytes = spec.expectedBytes
        if (expectedBytes != null && bytesWritten != expectedBytes) {
            staged.delete()
            return Gemma4ModelInstallResult.Failure("model_size_mismatch")
        }

        val actualSha256 = digest.digest().toHex()
        if (actualSha256 != spec.sha256) {
            staged.delete()
            return Gemma4ModelInstallResult.Failure("model_sha256_mismatch")
        }

        val active = activeFile()
        try {
            atomicMove(staged, active)
        } catch (error: Throwable) {
            staged.delete()
            return Gemma4ModelInstallResult.Failure(
                "model_activation_failed_${error.javaClass.simpleName.ifBlank { "Throwable" }}",
            )
        }

        return Gemma4ModelInstallResult.Success(
            modelId = spec.modelId,
            file = active,
            bytesWritten = bytesWritten,
            sha256 = actualSha256,
        )
    }

    private fun ensureDirectory(): Boolean = when {
        directory.isDirectory -> true
        directory.exists() -> false
        else -> directory.mkdirs() && directory.isDirectory
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val COPY_BUFFER_BYTES = 1024 * 1024
    }
}
