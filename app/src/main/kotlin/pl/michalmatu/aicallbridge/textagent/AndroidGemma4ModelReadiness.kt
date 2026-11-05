package pl.michalmatu.aicallbridge.textagent

import android.content.Context

internal object AndroidGemma4ModelReadiness {
    fun check(context: Context, modelId: pl.michalmatu.aicallbridge.audioengines.DialogueModelId = checkNotNull(pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore(context).load()) { "invalid_audio_engine_profile" }.dialogue): Gemma4ModelReadiness {
        val active = Gemma4LiteRtTextBackendFactory.modelFile(context.applicationContext, modelId)
        val directory = checkNotNull(active.parentFile)
        return Gemma4ModelReadinessProbe(directory, LanguageModelCatalog[modelId].spec).check()
    }
}
