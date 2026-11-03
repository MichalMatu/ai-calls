package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSpeechRecoverableFailureTest {
    @Test
    fun noMatchSpeechTimeoutAndEmptyFinalTranscriptAreNonterminalForActiveOwnedCall() {
        assertTrue(LocalSpeechRecoverableFailure.isTransientNoTranscript("stt_recognition_error_7"))
        assertTrue(LocalSpeechRecoverableFailure.isTransientNoTranscript("stt_recognition_error_6"))
        assertTrue(LocalSpeechRecoverableFailure.isTransientNoTranscript("stt_empty_transcript"))
    }

    @Test
    fun permissionsPipeModelEngineAndUnknownErrorsStillRequireDiagnostics() {
        listOf(
            "stt_record_audio_permission_missing",
            "stt_on_device_recognizer_unavailable",
            "stt_recognition_error_1",
            "stt_recognition_error_2",
            "stt_recognition_error_5",
            "stt_recognition_error_8",
            "stt_recognition_error_9",
            "stt_language_model_not_installed",
            "stt_stt_pcm_write_failed",
            "tts_network_error",
            "stt_empty_transcript_corrupt",
            "stt_empty_transcript_model_unavailable",
            "owned_call_ended",
        ).forEach { reason ->
            assertFalse(reason, LocalSpeechRecoverableFailure.isTransientNoTranscript(reason))
        }
    }
}
