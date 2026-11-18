package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import pl.michalmatu.aicallbridge.session.CallMediaBackendMode

/** Production configuration for the proven llama.cpp/Qwen endpoint on the S22 itself. */
internal enum class LocalPhoneModelExecution {
    SHIZUKU_QWEN,
    APP_PRIVATE_LITERT,
}

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

    /**
     * Stable production Qwen stays unchanged. The isolated S8 GSM lab must
     * use its existing app-private Gemma LiteRT backend, not start Shizuku
     * through a nested model path. No silent Qwen/Gemma model substitution:
     * the selected backend's own readiness checks still apply.
     */
    internal fun defaultExecution(packageName: String): LocalPhoneModelExecution =
        if (CallMediaBackendMode.forPackage(packageName) == CallMediaBackendMode.GSM_SHELL_ADB)
            LocalPhoneModelExecution.APP_PRIVATE_LITERT
        else LocalPhoneModelExecution.SHIZUKU_QWEN

    fun create(
        context: Context,
        systemPrompt: String = SYSTEM_PROMPT,
        execution: LocalPhoneModelExecution = defaultExecution(context.packageName),
    ): TextCallAgentBackend {
        require(!(CallMediaBackendMode.forPackage(context.packageName) ==
            CallMediaBackendMode.GSM_SHELL_ADB &&
            execution == LocalPhoneModelExecution.SHIZUKU_QWEN)) {
            "s8_gsm_model_shizuku_forbidden"
        }
        return when (execution) {
        LocalPhoneModelExecution.SHIZUKU_QWEN ->
            IdentityVerifiedLocalPhoneLlmBackend(
                baseUrl = BASE_URL,
                expectedAlias = MODEL,
                expectedModelPath = MODEL_PATH,
                systemPrompt = systemPrompt,
                runtimeGate = ShizukuLocalPhoneLlmRuntimeGate(context.applicationContext),
            )
        LocalPhoneModelExecution.APP_PRIVATE_LITERT ->
            Gemma4LiteRtTextBackendFactory.create(
                context = context.applicationContext,
                systemInstruction = systemPrompt,
            )
        }
    }
}
