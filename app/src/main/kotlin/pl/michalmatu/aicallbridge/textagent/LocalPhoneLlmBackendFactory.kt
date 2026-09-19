package pl.michalmatu.aicallbridge.textagent

/** Production configuration for the proven llama.cpp/Qwen endpoint on the S22 itself. */
internal object LocalPhoneLlmBackendFactory {
    const val BASE_URL = "http://127.0.0.1:18115/v1/"
    const val MODEL = "qwen-phone-0.5b"

    private const val SYSTEM_PROMPT =
        "Jesteś lokalnym asystentem prowadzącym rozmowę telefoniczną po polsku. " +
            "Odpowiadaj krótko i naturalnie, najwyżej dwoma zdaniami. " +
            "Nie składaj zamówień, nie akceptuj umów, nie ujawniaj danych wrażliwych i nie podejmuj zobowiązań bez jawnej autoryzacji aplikacji."

    fun create(): TextCallAgentBackend = LocalOpenAiCompatibleTextBackend(
        LocalOpenAiTextBackendConfig(
            baseUrl = BASE_URL,
            model = MODEL,
            systemPrompt = SYSTEM_PROMPT,
        ),
    )
}
