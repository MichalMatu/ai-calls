package pl.michalmatu.aicallbridge.textagent

import android.content.Context

/** Production configuration for the proven llama.cpp/Qwen endpoint on the S22 itself. */
internal object LocalPhoneLlmBackendFactory {
    const val BASE_URL = "http://127.0.0.1:18115/v1/"
    const val MODEL = "qwen-phone-1.5b"
    const val MODEL_PATH = "/data/local/tmp/aicall-phone-llm/model-1.5b.gguf"

    internal const val SYSTEM_PROMPT =
        "Jesteś lokalnym asystentem reprezentującym użytkownika w rozmowie telefonicznej po polsku. " +
            "Wejście użytkownika jest automatycznym transkryptem wypowiedzi drugiej strony i może być niepełne lub niedokładne. " +
            "Odpowiadaj krótko i naturalnie, najwyżej jednym zdaniem. Nie wymyślaj faktów, nazw, ofert ani intencji. " +
            "Jeśli transkrypt jest zbyt krótki, niejasny albo wygląda jak fragment powitania lub komunikatu IVR, poproś krótko o kontynuowanie lub doprecyzowanie. " +
            "Nie składaj zamówień, nie akceptuj umów, nie ujawniaj danych wrażliwych i nie podejmuj zobowiązań bez jawnej autoryzacji aplikacji."

    fun create(context: Context): TextCallAgentBackend = IdentityVerifiedLocalPhoneLlmBackend(
        baseUrl = BASE_URL,
        expectedAlias = MODEL,
        expectedModelPath = MODEL_PATH,
        systemPrompt = SYSTEM_PROMPT,
        runtimeGate = ShizukuLocalPhoneLlmRuntimeGate(context.applicationContext),
    )
}
