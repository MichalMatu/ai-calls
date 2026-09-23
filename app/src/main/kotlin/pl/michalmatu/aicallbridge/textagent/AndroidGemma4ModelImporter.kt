package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import android.net.Uri
import java.io.File

internal object AndroidGemma4ModelReadiness {
    fun check(context: Context): Gemma4ModelReadiness {
        val active = Gemma4LiteRtTextBackendFactory.modelFile(context.applicationContext)
        val directory = checkNotNull(active.parentFile)
        return Gemma4ModelReadinessProbe(directory).check()
    }
}

/** Android SAF adapter for the app-owned Gemma installer. */
internal class AndroidGemma4ModelImporter(context: Context) {
    private val appContext = context.applicationContext
    private val installer = Gemma4ModelInstaller(
        directory = checkNotNull(Gemma4LiteRtTextBackendFactory.modelFile(appContext).parentFile),
    )

    fun activeModelFile(): File = installer.activeFile()

    fun readiness(): Gemma4ModelReadiness = AndroidGemma4ModelReadiness.check(appContext)

    fun import(uri: Uri): Gemma4ModelInstallResult {
        return try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: return Gemma4ModelInstallResult.Failure("model_source_unreadable")
            input.use(installer::install)
        } catch (error: Throwable) {
            Gemma4ModelInstallResult.Failure(
                "model_source_failed_${error.javaClass.simpleName.ifBlank { "Throwable" }}",
            )
        }
    }
}
