package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import java.io.File

internal object AndroidGemma4ModelReadiness {
    fun check(context: Context, modelId: pl.michalmatu.aicallbridge.audioengines.DialogueModelId = checkNotNull(pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore(context).load()) { "invalid_audio_engine_profile" }.dialogue): Gemma4ModelReadiness {
        if (modelId == DialogueModelId.USER_LITERT) {
            val profile = AudioEngineProfileStore(context).load()
            val library = UserLiteRtModelStore(context)
            val model = library.find(profile?.customModelId)
                ?: return Gemma4ModelReadiness(Gemma4ModelReadinessState.MISSING,
                    File(context.filesDir, "models/user-model-missing"), "user_model_missing")
            return Gemma4ModelReadinessProbe(checkNotNull(library.file(model).parentFile),
                Gemma4ModelSpec(model.title, model.filename, model.sha256, model.bytes)).check()
        }
        val active = Gemma4LiteRtTextBackendFactory.modelFile(context.applicationContext, modelId)
        val directory = checkNotNull(active.parentFile)
        return Gemma4ModelReadinessProbe(directory, LanguageModelCatalog[modelId].spec).check()
    }
}
