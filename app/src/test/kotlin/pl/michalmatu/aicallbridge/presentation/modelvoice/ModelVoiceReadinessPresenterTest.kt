package pl.michalmatu.aicallbridge.presentation.modelvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelVoiceReadinessPresenterTest {
    private fun state(
        installed: Boolean = true, downloading: Boolean = false,
        checking: Boolean = false, attemptMatches: Boolean = false, result: Boolean = false,
        verified: Boolean = false, textRequired: Boolean = false, audio: Boolean = false,
        reason: String? = null, transferBusy: Boolean = false, imported: Boolean = false,
        voicesReady: Boolean = true,
    ) = ModelVoiceReadinessPresenter.present(ModelVoiceReadinessPresenter.Input(
        installed, downloading, checking, attemptMatches, result, verified,
        textRequired, audio, reason, transferBusy, imported, voicesReady))

    @Test fun downloadingBeatsCheckAndDisablesRetry() {
        val ui = state(downloading = true, checking = true, attemptMatches = true,
            result = true, transferBusy = true)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.DOWNLOAD_PROGRESS, ui.modelLine)
        assertEquals(ModelVoiceReadinessPresenter.PrimaryAction.CANCEL, ui.primaryAction)
        assertTrue(ui.primaryVisible)
        assertTrue(ui.primaryEnabled)
        assertFalse(ui.retryVisible)
    }

    @Test fun checkProgressDependsOnMatchingAttemptAndAudio() {
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.CHECKING_AUDIO,
            state(checking = true, attemptMatches = true, audio = true).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.CHECKING_MODEL,
            state(checking = true, attemptMatches = true).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.DOWNLOADED_UNVERIFIED,
            state(checking = true).modelLine)
    }

    @Test fun explicitResultHasPriorityOverVerifiedStatus() {
        val ui = state(attemptMatches = true, result = true, verified = true, audio = true)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.ATTEMPT_RESULT, ui.modelLine)
        assertEquals(ModelVoiceReadinessPresenter.RetryAction.RETRY_AUDIO, ui.retryAction)
    }

    @Test fun qualificationStatesDoNotBecomeFalsePositivePasses() {
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.VERIFIED_AUDIO,
            state(verified = true, audio = true).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.VERIFIED_TEXT,
            state(verified = true).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.REQUIRED_AUDIO, state(audio = true).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.REQUIRED_TEXT,
            state(textRequired = true).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.DOWNLOADED_UNVERIFIED, state().modelLine)
    }

    @Test fun neutralReasonOnlyOverridesIdleUnverifiedInstalledState() {
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.READINESS_REASON,
            state(reason = "model_native_check_failed").modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.DOWNLOADED_UNVERIFIED,
            state(reason = "model_native_check_failed", checking = true).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.VERIFIED_TEXT,
            state(reason = "model_native_check_failed", verified = true).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.MISSING,
            state(reason = "model_native_check_failed", installed = false).modelLine)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.ATTEMPT_RESULT,
            state(reason = "model_native_check_failed", attemptMatches = true, result = true).modelLine)
    }

    @Test fun inputAndVoiceErrorsStayInTheirOwnRows() {
        val voice = state(reason = "polish_offline_voice_missing")
        assertTrue(voice.voiceProblem)
        assertFalse(voice.inputProblem)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.DOWNLOADED_UNVERIFIED, voice.modelLine)
        val input = state(reason = "parakeet_model_missing_or_incomplete")
        assertTrue(input.inputProblem)
        assertFalse(input.voiceProblem)
        assertTrue(state(reason = "android_offline_stt_unavailable").inputProblem)
        assertTrue(state(reason = "selected_voice_missing").voiceProblem)
        assertTrue(state(reason = "tts_offline_voice_probe_required").voiceProblem)
    }

    @Test fun missingImportedModelShowsManageFilesAndDisablesBusyAction() {
        val ui = state(installed = false, imported = true, transferBusy = true)
        assertEquals(ModelVoiceReadinessPresenter.ModelLine.MISSING, ui.modelLine)
        assertEquals(ModelVoiceReadinessPresenter.PrimaryAction.MANAGE_FILES, ui.primaryAction)
        assertTrue(ui.primaryVisible)
        assertFalse(ui.primaryEnabled)
        assertFalse(ui.retryVisible)
    }

    @Test fun retryStatesRespectBusyCheckAndOfflineVoices() {
        val waiting = state(audio = true, voicesReady = false)
        assertEquals(ModelVoiceReadinessPresenter.RetryAction.LOADING_VOICES, waiting.retryAction)
        assertFalse(waiting.retryEnabled)
        assertEquals(ModelVoiceReadinessPresenter.RetryAction.CHECKING,
            state(checking = true, audio = true, voicesReady = false).retryAction)
        assertEquals(ModelVoiceReadinessPresenter.RetryAction.CHECK_AUDIO, state(audio = true).retryAction)
        assertEquals(ModelVoiceReadinessPresenter.RetryAction.RETRY_AUDIO,
            state(audio = true, verified = true).retryAction)
        assertEquals(ModelVoiceReadinessPresenter.RetryAction.CHECK_COMPATIBILITY, state().retryAction)
        assertEquals(ModelVoiceReadinessPresenter.RetryAction.RETRY,
            state(attemptMatches = true).retryAction)
    }

    @Test fun defaultActionAndRetryVisibilityMatchExistingView() {
        assertEquals(ModelVoiceReadinessPresenter.PrimaryAction.DOWNLOAD,
            state(installed = false).primaryAction)
        assertFalse(state(transferBusy = true).primaryEnabled)
        assertFalse(state().primaryVisible)
        assertTrue(state().retryVisible)
        assertTrue(state().retryEnabled)
    }
}
