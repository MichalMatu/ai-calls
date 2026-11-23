package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import pl.michalmatu.aicallbridge.session.GsmShellMediaProtocol

/** One local model adapter. No external model runtime or privilege manager. */
internal object LocalPhoneLlmBackendFactory {
    internal const val SYSTEM_PROMPT =
        "Jesteś lokalnym asystentem reprezentującym użytkownika w rozmowie telefonicznej po polsku. " +
            "Wejście użytkownika jest automatycznym transkryptem wypowiedzi drugiej strony i może być niepełne lub niedokładne. " +
            "Odpowiadaj krótko i naturalnie, najwyżej jednym zdaniem. Nie wymyślaj faktów, nazw, ofert ani intencji. " +
            "Jeśli transkrypt jest zbyt krótki, niejasny albo wygląda jak fragment powitania lub komunikatu IVR, poproś krótko o kontynuowanie lub doprecyzowanie. " +
            "Nie składaj zamówień, nie akceptuj umów, nie ujawniaj danych wrażliwych i nie podejmuj zobowiązań bez jawnej autoryzacji aplikacji."

    internal fun supportsPackage(packageName: String): Boolean =
        GsmShellMediaProtocol.supportedPackage(packageName)

    fun create(
        context: Context,
        systemPrompt: String = SYSTEM_PROMPT,
    ): TextCallAgentBackend {
        require(supportsPackage(context.packageName)) { "gsm_model_unapproved_package" }
        return Gemma4LiteRtTextBackendFactory.create(
            context = context.applicationContext,
            systemInstruction = systemPrompt,
        )
    }
}
