package pl.michalmatu.aicallbridge.runtime

enum class CallAudioMode(
    val displayName: String,
    val usesTextLlm: Boolean,
) {
    LOCAL_STT_TTS("Local STT + TTS (S22)", true),
    OPENAI_REALTIME_AUDIO("OpenAI Realtime Audio (frozen)", false),
    LOCAL_REALTIME_AUDIO("Local Realtime Audio (server)", false),
    ;

    companion object {
        fun fromStored(value: String?): CallAudioMode =
            entries.firstOrNull { it.name == value } ?: LOCAL_STT_TTS
    }
}

enum class TextLlmProvider(val displayName: String) {
    OPENAI_TEXT("OpenAI API (text)"),
    LOCAL_PHONE_LLM("Local LLM (S22)"),
    EDGE_GALLERY("Google AI Edge Gallery (Gemma 3n E2B)"),
    LOCAL_MAC_LLM("Local LLM server (Mac)"),
    ;

    companion object {
        fun fromStored(value: String?): TextLlmProvider =
            entries.firstOrNull { it.name == value } ?: OPENAI_TEXT
    }
}

data class CallRuntimeSelection(
    val audioMode: CallAudioMode = CallAudioMode.LOCAL_STT_TTS,
    val textLlmProvider: TextLlmProvider = TextLlmProvider.OPENAI_TEXT,
)
