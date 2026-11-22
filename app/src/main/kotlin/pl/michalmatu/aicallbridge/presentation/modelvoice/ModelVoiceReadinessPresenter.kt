package pl.michalmatu.aicallbridge.presentation.modelvoice

import pl.michalmatu.aicallbridge.audioengines.ModelVerificationStatus
import pl.michalmatu.aicallbridge.audioengines.ModelVerificationUi

/** Pure screen state. Android remains responsible for verification and TTS side effects. */
internal object ModelVoiceReadinessPresenter {
    data class Input(
        val modelInstalled: Boolean,
        val downloading: Boolean,
        val checking: Boolean,
        val attemptMatches: Boolean,
        val hasAttemptResult: Boolean,
        val verified: Boolean,
        val requiresTextCheck: Boolean,
        val audioMode: Boolean,
        val reasonCode: String?,
        val transferBusy: Boolean,
        val importedModel: Boolean,
        val voicesReady: Boolean,
    )

    enum class ModelLine {
        DOWNLOAD_PROGRESS, CHECKING_AUDIO, CHECKING_MODEL, ATTEMPT_RESULT,
        VERIFIED_TEXT, VERIFIED_AUDIO, REQUIRED_TEXT, REQUIRED_AUDIO, DOWNLOADED_UNVERIFIED,
        MISSING, READINESS_REASON,
    }
    enum class PrimaryAction { CANCEL, MANAGE_FILES, DOWNLOAD }
    enum class RetryAction { CHECKING, LOADING_VOICES, RETRY_AUDIO, CHECK_AUDIO, RETRY, CHECK_COMPATIBILITY }
    data class State(
        val modelLine: ModelLine,
        val voiceProblem: Boolean,
        val inputProblem: Boolean,
        val primaryVisible: Boolean,
        val primaryAction: PrimaryAction,
        val primaryEnabled: Boolean,
        val retryVisible: Boolean,
        val retryAction: RetryAction,
        val retryEnabled: Boolean,
    )

    fun present(input: Input): State {
        val voiceProblem = input.reasonCode in setOf(
            "tts_offline_voice_probe_required", "polish_offline_voice_missing", "selected_voice_missing")
        val inputProblem = input.reasonCode?.startsWith("parakeet_") == true ||
            input.reasonCode == "android_offline_stt_unavailable"
        val normalLine = when {
            input.downloading -> ModelLine.DOWNLOAD_PROGRESS
            input.checking && input.attemptMatches ->
                if (input.audioMode) ModelLine.CHECKING_AUDIO else ModelLine.CHECKING_MODEL
            input.attemptMatches && input.hasAttemptResult && input.modelInstalled -> ModelLine.ATTEMPT_RESULT
            input.modelInstalled -> when (ModelVerificationUi.status(
                input.verified, input.requiresTextCheck, input.audioMode)) {
                ModelVerificationStatus.VERIFIED_TEXT -> ModelLine.VERIFIED_TEXT
                ModelVerificationStatus.VERIFIED_AUDIO -> ModelLine.VERIFIED_AUDIO
                ModelVerificationStatus.REQUIRED_TEXT -> ModelLine.REQUIRED_TEXT
                ModelVerificationStatus.REQUIRED_AUDIO -> ModelLine.REQUIRED_AUDIO
                ModelVerificationStatus.DOWNLOADED_UNVERIFIED -> ModelLine.DOWNLOADED_UNVERIFIED
            }
            else -> ModelLine.MISSING
        }
        val showReason = input.reasonCode != null && !input.verified && !input.hasAttemptResult &&
            !voiceProblem && !inputProblem && !input.checking && !input.downloading && input.modelInstalled
        val retryAction = when {
            input.checking -> RetryAction.CHECKING
            input.audioMode && !input.voicesReady -> RetryAction.LOADING_VOICES
            input.audioMode && (input.verified || input.attemptMatches) -> RetryAction.RETRY_AUDIO
            input.audioMode -> RetryAction.CHECK_AUDIO
            input.verified || input.attemptMatches -> RetryAction.RETRY
            else -> RetryAction.CHECK_COMPATIBILITY
        }
        return State(
            modelLine = if (showReason) ModelLine.READINESS_REASON else normalLine,
            voiceProblem = voiceProblem,
            inputProblem = inputProblem,
            primaryVisible = input.downloading || !input.modelInstalled,
            primaryAction = when {
                input.downloading -> PrimaryAction.CANCEL
                input.importedModel -> PrimaryAction.MANAGE_FILES
                else -> PrimaryAction.DOWNLOAD
            },
            primaryEnabled = !input.transferBusy || input.downloading,
            retryVisible = ModelVerificationUi.checkVisible(input.modelInstalled, input.downloading),
            retryAction = retryAction,
            retryEnabled = !input.checking && (!input.audioMode || input.voicesReady),
        )
    }
}
