package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun blankFinalResultsUseRecoverableNoMatchRatherThanDispatcherFailure() {
        listOf("", " ", "\\t", "\\n").forEach { transcript ->
            val error = LocalSpeechRecoverableFailure.finalTranscriptError(transcript)
            assertEquals("stt_empty_transcript", error)
            assertTrue(LocalSpeechRecoverableFailure.isTransientNoTranscript(checkNotNull(error)))
        }
        assertNull(LocalSpeechRecoverableFailure.finalTranscriptError("Terminy od 10 do 12"))
        assertNull(LocalSpeechRecoverableFailure.finalTranscriptError("  Tak, pasuje  "))
        assertFalse(LocalSpeechRecoverableFailure.isTransientNoTranscript(
            "final_turn_route_IllegalArgumentException",
        ))
    }

    @Test
    fun finalSegmentsSurviveRecognizerNoMatchAndSpeechTimeout() {
        val segments = SpeechRecognitionSegments()
        segments.add("chciałbym umówić wizytę")
        val text = segments.resolve("")
        assertEquals(
            text,
            LocalSpeechRecoverableFailure.completedSegmentsAfterRecognizerError(
                "stt_recognition_error_7", text,
            ),
        )
        assertEquals(
            text,
            LocalSpeechRecoverableFailure.completedSegmentsAfterRecognizerError(
                "stt_recognition_error_6", text,
            ),
        )
    }

    @Test
    fun absenceOfFinalSegmentsAndHardErrorsNeverBecomeSyntheticTranscript() {
        listOf("stt_recognition_error_7", "stt_recognition_error_6",
            "stt_recognition_error_5", "stt_recognition_error_9", "stt_empty_transcript",
            "stt_record_audio_permission_missing", "owned_call_ended").forEach { reason ->
            assertNull(
                reason,
                LocalSpeechRecoverableFailure.completedSegmentsAfterRecognizerError(reason, ""),
            )
        }
        assertNull(LocalSpeechRecoverableFailure.completedSegmentsAfterRecognizerError(
            "stt_recognition_error_5", "segment previously finalized",
        ))
        assertNull(LocalSpeechRecoverableFailure.completedSegmentsAfterRecognizerError(
            "stt_empty_transcript", "segment previously finalized",
        ))
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
