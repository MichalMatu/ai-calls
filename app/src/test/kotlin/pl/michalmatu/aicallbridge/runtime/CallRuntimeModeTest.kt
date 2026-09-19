package pl.michalmatu.aicallbridge.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRuntimeModeTest {
    @Test
    fun defaultsPreferLocalSpeechWithTextApiBrain() {
        val selection = CallRuntimeSelection()

        assertEquals(CallAudioMode.LOCAL_STT_TTS, selection.audioMode)
        assertEquals(TextLlmProvider.OPENAI_TEXT, selection.textLlmProvider)
        assertTrue(selection.audioMode.usesTextLlm)
    }

    @Test
    fun storedValuesRoundTripAndUnknownValuesFailSafeToDefaults() {
        assertEquals(CallAudioMode.OPENAI_REALTIME_AUDIO, CallAudioMode.fromStored("OPENAI_REALTIME_AUDIO"))
        assertEquals(CallAudioMode.LOCAL_REALTIME_AUDIO, CallAudioMode.fromStored("LOCAL_REALTIME_AUDIO"))
        assertEquals(TextLlmProvider.LOCAL_MAC_LLM, TextLlmProvider.fromStored("LOCAL_MAC_LLM"))
        assertEquals(TextLlmProvider.LOCAL_PHONE_LLM, TextLlmProvider.fromStored("LOCAL_PHONE_LLM"))
        assertEquals(CallAudioMode.LOCAL_STT_TTS, CallAudioMode.fromStored("unknown"))
        assertEquals(TextLlmProvider.OPENAI_TEXT, TextLlmProvider.fromStored(null))
    }

    @Test
    fun realtimeAudioModesDoNotUseTextLlmButKeepIndependentTextPreference() {
        listOf(
            CallAudioMode.OPENAI_REALTIME_AUDIO,
            CallAudioMode.LOCAL_REALTIME_AUDIO,
        ).forEach { audioMode ->
            val selection = CallRuntimeSelection(
                audioMode = audioMode,
                textLlmProvider = TextLlmProvider.LOCAL_MAC_LLM,
            )

            assertFalse(selection.audioMode.usesTextLlm)
            assertEquals(TextLlmProvider.LOCAL_MAC_LLM, selection.textLlmProvider)
        }
    }
}
