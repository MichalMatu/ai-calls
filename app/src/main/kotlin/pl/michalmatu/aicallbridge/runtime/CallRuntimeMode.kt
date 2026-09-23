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
    LOCAL_GEMMA_4("Local Gemma 4 E2B (S22)"),
    LOCAL_MAC_LLM("Local LLM server (Mac)"),
    ;

    companion object {
        fun fromStored(value: String?): TextLlmProvider = when (value) {
            "EDGE_GALLERY" -> LOCAL_GEMMA_4
            else -> entries.firstOrNull { it.name == value } ?: OPENAI_TEXT
        }
    }
}

data class CallRuntimeSelection(
    val audioMode: CallAudioMode = CallAudioMode.LOCAL_STT_TTS,
    val textLlmProvider: TextLlmProvider = TextLlmProvider.OPENAI_TEXT,
)
