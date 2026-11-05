package pl.michalmatu.aicallbridge.textagent

import android.content.Context

/** Android composition for the reviewed network transport into the existing app-owned installer. */
internal class AndroidGemma4ModelDownloader(context: Context, model: LanguageModelEntry = LanguageModelCatalog[pl.michalmatu.aicallbridge.audioengines.DialogueModelId.GEMMA_4_E2B]) {
    private val appContext = context.applicationContext
    private val installer = Gemma4ModelInstaller(
        spec = model.spec,
        directory = checkNotNull(Gemma4LiteRtTextBackendFactory.modelFile(appContext, model.id).parentFile),
    )
    private val delegate = Gemma4ModelDownloader(source = model.source, installer = installer)

    fun download(
        onProgress: (Gemma4ModelDownloadProgress) -> Unit = {},
    ): Gemma4ModelInstallResult = delegate.download(onProgress)

    fun cancel(): Boolean = delegate.cancel()
}
