package pl.michalmatu.aicallbridge.textagent

import android.content.Context

/** Android composition for the reviewed network transport into the existing app-owned installer. */
internal class AndroidGemma4ModelDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val installer = Gemma4ModelInstaller(
        directory = checkNotNull(Gemma4LiteRtTextBackendFactory.modelFile(appContext).parentFile),
    )
    private val delegate = Gemma4ModelDownloader(installer = installer)

    fun download(
        onProgress: (Gemma4ModelDownloadProgress) -> Unit = {},
    ): Gemma4ModelInstallResult = delegate.download(onProgress)

    fun cancel(): Boolean = delegate.cancel()
}
