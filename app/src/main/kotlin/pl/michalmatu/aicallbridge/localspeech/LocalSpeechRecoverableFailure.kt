package pl.michalmatu.aicallbridge.localspeech

/**
 * Android SpeechRecognizer can report no recognized words or a speech-timeout for
 * one PCM window even when an owned cellular call remains connected. Android can also
 * deliver an empty final transcript through onResults/onEndOfSegmentedSession. These outcomes
 * should lead to listening again, not abandonment of an authorized campaign.
 *
 * Other failures (permission, language model, engine/pipe, service) are not
 * silently retried; the app still surfaces them as genuine diagnostics.
 */
internal object LocalSpeechRecoverableFailure {
    fun isTransientNoTranscript(reason: String): Boolean =
        reason == "stt_recognition_error_7" || // SpeechRecognizer.ERROR_NO_MATCH
            reason == "stt_recognition_error_6" || // SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            reason == "stt_empty_transcript"      // Final recognition result contained no words

    /**
     * Only onSegmentResults delivers *final* earlier segments. If Android reports a
     * later no-match/timeout, retain those confirmed words rather than discard the turn.
     * Never recover partial hypotheses or non-transient recognition failures.
     */
    fun completedSegmentsAfterRecognizerError(reason: String, completedText: String): String? =
        completedText.trim().takeIf {
            it.isNotEmpty() &&
                (reason == "stt_recognition_error_7" || reason == "stt_recognition_error_6")
        }
}
