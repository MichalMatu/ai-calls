package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversation.ConversationInstructionBuilder
import pl.michalmatu.aicallbridge.conversation.ConversationPreparation
import pl.michalmatu.aicallbridge.conversation.ConversationReplyRecovery
import pl.michalmatu.aicallbridge.conversation.ConversationResponseFormat

class EnglishSpeechProfileContractTest {
    @Test fun englishIsExplicitAndRequiresOnDeviceAndroidSpeechAndTts() {
        val english = AudioEngineProfile(languageTag = "en-US")
        assertNull(AudioEngineRegistry.incompatibility(english))
        assertEquals("unsupported_language", AudioEngineRegistry.incompatibility(english.copy(languageTag = "de-DE")))
        assertEquals("english_android_stt_required", AudioEngineRegistry.incompatibility(
            english.copy(input = SpeechInputId.PARAKEET_PL)))
        assertEquals("english_android_stt_required", AudioEngineRegistry.incompatibility(
            english.copy(input = SpeechInputId.GEMMA_AUDIO_DIRECT)))
        assertEquals("english_android_tts_required", AudioEngineRegistry.incompatibility(
            english.copy(ttsEnginePackage = PiperServiceContract.PACKAGE)))
        assertEquals("english_android_tts_required", AudioEngineRegistry.incompatibility(
            english.copy(output = SpeechOutputId.PIPER_PL)))
        assertNull(AudioEngineRegistry.incompatibility(AudioEngineProfile.STABLE))
    }

    @Test fun englishIncomingPromptOpeningAndRecoveryHaveMatchingLanguage() {
        val english = ConversationInstructionBuilder.build(ConversationPreparation.incoming(),
            "Take a message", ConversationResponseFormat.TOOL_REPLY, "en-US")
        assertTrue(english.contains("Speak entirely in natural English"))
        assertTrue(english.contains(ConversationInstructionBuilder.INCOMING_OPENING_EN))
        assertFalse(english.contains("Język: polski"))
        assertEquals(ConversationInstructionBuilder.INCOMING_OPENING_EN,
            ConversationInstructionBuilder.incomingOpening("en-US"))
        assertTrue(ConversationReplyRecovery.clarificationFor("en-US").startsWith("I could"))
        val polish = ConversationInstructionBuilder.build(ConversationPreparation.incoming(),
            "Przyjmij wiadomość", ConversationResponseFormat.TOOL_REPLY)
        assertTrue(polish.contains("Język: polski"))
        assertTrue(polish.contains(ConversationInstructionBuilder.INCOMING_OPENING))
        assertEquals(ConversationReplyRecovery.CLARIFICATION,
            ConversationReplyRecovery.clarificationFor("pl-PL"))
    }
}
