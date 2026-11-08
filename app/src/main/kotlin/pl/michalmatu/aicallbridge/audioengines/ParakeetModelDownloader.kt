package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import android.os.StatFs
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

internal sealed interface ParakeetModelDownloadResult {
    data object Success : ParakeetModelDownloadResult
    data class Failure(val reason: String) : ParakeetModelDownloadResult
}

/**
 * Opt-in 615-MB model download. Only HTTPS from one pinned HF revision, size and SHA-256
 * may be installed. Data is staged in app-owned model directory, never the APK or GitHub.
 * A cancelled/interrupted candidate is discarded. It never starts model inference or a call.
 */
internal class ParakeetModelDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var activeCall: Call? = null
    private var downloading = false
    private var cancelled = false
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(80, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun cancel() {
        val call = synchronized(lock) { cancelled = true; activeCall }
        call?.cancel()
    }

    fun download(onProgress: (Long, Long) -> Unit = { _, _ -> }): ParakeetModelDownloadResult {
        synchronized(lock) {
            if (downloading) return ParakeetModelDownloadResult.Failure("already_downloading")
            if (cancelled) return ParakeetModelDownloadResult.Failure("cancelled")
            downloading = true
        }
        var staging: File? = null
        try {
            val destination = ParakeetModelFile.resolve(appContext)
            if (ParakeetModelFile.validPinnedModel(destination)) return ParakeetModelDownloadResult.Success
            val directory = checkNotNull(destination.parentFile)
            if (!directory.exists() && !directory.mkdirs()) {
                return ParakeetModelDownloadResult.Failure("model_directory_unavailable")
            }
            if (StatFs(directory.absolutePath).availableBytes <
                ParakeetModelFile.MIN_SIZE_BYTES + 160_000_000L
            ) return ParakeetModelDownloadResult.Failure("insufficient_private_storage")

            val req = Request.Builder()
                .url(ParakeetModelFile.DOWNLOAD_URL)
                .header("User-Agent", "ai-calls/0.2")
                .get()
                .build()
            val call = client.newCall(req)
            synchronized(lock) {
                if (cancelled) return ParakeetModelDownloadResult.Failure("cancelled")
                activeCall = call
            }
            call.execute().use { response ->
                val url = response.request.url
                if (url.scheme != "https" || !trustedHfHost(url.host)) {
                    return ParakeetModelDownloadResult.Failure("untrusted_redirect")
                }
                if (response.code != 200) return ParakeetModelDownloadResult.Failure("http_${response.code}")
                val body = response.body
                val size = body.contentLength()
                if (size >= 0 && size != ParakeetModelFile.MIN_SIZE_BYTES) {
                    return ParakeetModelDownloadResult.Failure("content_length_mismatch")
                }
                val hash = MessageDigest.getInstance("SHA-256")
                val candidate = File.createTempFile("parakeet-", ".partial", directory)
                staging = candidate
                var count = 0L
                body.byteStream().use { stream ->
                    candidate.outputStream().buffered().use { output ->
                        val chunk = ByteArray(256 * 1024)
                        while (true) {
                            if (synchronized(lock) { cancelled }) {
                                return ParakeetModelDownloadResult.Failure("cancelled")
                            }
                            val n = stream.read(chunk)
                            if (n < 0) break
                            count += n
                            if (count > ParakeetModelFile.MIN_SIZE_BYTES) {
                                return ParakeetModelDownloadResult.Failure("download_oversized")
                            }
                            hash.update(chunk, 0, n)
                            output.write(chunk, 0, n)
                            if (count % (4L * 1024 * 1024) < n) {
                                onProgress(count, ParakeetModelFile.MIN_SIZE_BYTES)
                            }
                        }
                    }
                }
                if (count != ParakeetModelFile.MIN_SIZE_BYTES) {
                    return ParakeetModelDownloadResult.Failure("download_incomplete")
                }
                val sha = hash.digest().joinToString("") { "%02x".format(it) }
                if (sha != ParakeetModelFile.EXPECTED_SHA256) {
                    return ParakeetModelDownloadResult.Failure("sha256_mismatch")
                }
                if (synchronized(lock) { cancelled }) return ParakeetModelDownloadResult.Failure("cancelled")
                if (!candidate.renameTo(destination)) {
                    return ParakeetModelDownloadResult.Failure("atomic_model_install_failed")
                }
                staging = null
                onProgress(count, ParakeetModelFile.MIN_SIZE_BYTES)
                return ParakeetModelDownloadResult.Success
            }
        } catch (error: Throwable) {
            return ParakeetModelDownloadResult.Failure(
                "download_" + error.javaClass.simpleName.ifEmpty { "failed" },
            )
        } finally {
            staging?.delete()
            synchronized(lock) {
                activeCall = null
                downloading = false
                cancelled = false
            }
        }
    }

    internal companion object {
        fun trustedHfHost(host: String): Boolean =
            host == "huggingface.co" || host.endsWith(".huggingface.co") ||
                host.endsWith(".hf.co")
    }
}
