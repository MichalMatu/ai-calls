package pl.michalmatu.aicallbridge.presentation.modelvoice

import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry

/** Pure profile selection/persistence state. Android and UI lifecycle stay with the screen. */
internal class ModelVoiceProfileController(
    private val load: () -> AudioEngineProfile?,
    private val save: (AudioEngineProfile) -> Boolean,
    private val incompatibility: (AudioEngineProfile) -> String? = AudioEngineRegistry::incompatibility,
) {
    sealed interface SaveResult {
        data object Unchanged : SaveResult
        data class Rejected(val reason: String) : SaveResult
        data object WriteFailed : SaveResult
        data class Saved(val engineChanged: Boolean) : SaveResult
    }

    data class ExternalChange(val profile: AudioEngineProfile, val engineChanged: Boolean)

    var draft: AudioEngineProfile = load() ?: AudioEngineProfile.STABLE
        private set

    fun select(next: AudioEngineProfile): SaveResult {
        // Keep the original order: a persisted no-op bypasses validation and writing.
        if (next == draft && load() == next) return SaveResult.Unchanged
        incompatibility(next)?.let { return SaveResult.Rejected(it) }
        if (!save(next)) return SaveResult.WriteFailed
        val engineChanged = engineChanged(draft, next)
        draft = next
        return SaveResult.Saved(engineChanged)
    }

    /** Do not mutate state until the screen has cancelled the previous compatibility probe. */
    fun pendingExternalChange(): ExternalChange? {
        val stored = load() ?: return null
        if (stored == draft) return null
        return ExternalChange(stored, engineChanged(draft, stored))
    }

    fun acceptExternalChange(change: ExternalChange) {
        draft = change.profile
    }

    private fun engineChanged(previous: AudioEngineProfile, next: AudioEngineProfile): Boolean =
        previous.effectiveTtsEnginePackage != next.effectiveTtsEnginePackage ||
            previous.languageTag != next.languageTag
}
