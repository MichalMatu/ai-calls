package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class ModelAudioCompatibilityReplyTest {
    @Test fun compatibilityProbeValidatesSyntheticPcmBeforeNativeCall() {
        assertEquals("polish_offline_voice_invalid_pcm",
            ModelAudioProbePcm.failure(byteArrayOf()))
        assertEquals("polish_offline_voice_invalid_pcm",
            ModelAudioProbePcm.failure(byteArrayOf(1, 2, 3)))
        assertEquals("polish_offline_voice_audio_too_long",
            ModelAudioProbePcm.failure(ByteArray(GemmaAudioPcm16.MAX_PCM_BYTES + 2)))
        assertNull(ModelAudioProbePcm.failure(byteArrayOf(0, 0)))
        assertNull(ModelAudioProbePcm.failure(ByteArray(GemmaAudioPcm16.MAX_PCM_BYTES)))
    }

    @Test fun acceptsValidPolishAudioReply() {
        val raw = """{"transcript":"Dzień dobry, proszę o pomoc.","reply":"W czym mogę pomóc?","tool":"none","note":"","kind":"NOTE"}"""
        assertNull(ModelAudioCompatibilityReply.failure(raw))
    }

    @Test fun distinguishesMissingTranscriptFromRecognitionMismatch() {
        val missing = """{"reply":"Witam","tool":"none","note":"","kind":"NOTE"}"""
        val mismatched = """{"transcript":"Ala ma kota","reply":"Słucham","tool":"none","note":"","kind":"NOTE"}"""
        assertEquals("model_audio_reply_format_failed", ModelAudioCompatibilityReply.failure(missing))
        assertEquals("model_audio_transcript_mismatch", ModelAudioCompatibilityReply.failure(mismatched))
    }

    @Test fun rejectsUnexpectedToolDuringCompatibilityCheck() {
        val raw = """{"transcript":"Proszę o pomoc","reply":"Do widzenia","tool":"end_call","note":"","kind":"NOTE"}"""
        assertEquals("model_audio_tool_unexpected", ModelAudioCompatibilityReply.failure(raw))
    }
}
