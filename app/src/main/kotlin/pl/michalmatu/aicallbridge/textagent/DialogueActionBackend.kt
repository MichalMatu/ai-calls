package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicLong
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

/**
 * Structured actions selected by the local model. The model interprets dialogue only; it never
 * owns facts, external-effect authority, or final side effects.
 */
internal enum class DialogueActionId {
    ASK_REPEAT,
    ASK_CLARIFY,
    ACKNOWLEDGE_NEUTRAL,
    STATE_TASK_SUBJECT,
    DISCLOSE_AUTHORIZED_FACT,
    CONFIRM_AUTHORIZED_EFFECT,
    TAKE_OVER,
}

internal data class DialogueActionDecision(
    val actionId: DialogueActionId,
    val confidence: Double,
    val argument: String? = null,
    val reason: String? = null,
) {
    init {
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "dialogue_action_confidence_out_of_range"
        }
        require(argument == null || argument.length <= MAX_ARGUMENT_CHARS) {
            "dialogue_action_argument_too_long"
        }
        require(reason == null || reason.length <= MAX_REASON_CHARS) {
            "dialogue_action_reason_too_long"
        }
    }

    private companion object {
        const val MAX_ARGUMENT_CHARS = 80
        const val MAX_REASON_CHARS = 160
    }
}

internal class DialogueTaskContext(
    val subject: String,
    val authorizedEffect: String? = null,
    argumentHints: Map<String, String> = emptyMap(),
) {
    val argumentHints: Map<String, String> = argumentHints
        .mapKeys { (key, _) -> key.trim().uppercase() }
        .mapValues { (_, value) -> value.trim() }
        .filterKeys { it.isNotEmpty() }
        .filterValues { it.isNotEmpty() }
        .toMap()

    init {
        require(subject.isNotBlank() && subject.length <= 320) { "dialogue_task_subject_invalid" }
        require(authorizedEffect == null || (authorizedEffect.isNotBlank() && authorizedEffect.length <= 320)) {
            "dialogue_task_effect_invalid"
        }
        require(this.argumentHints.keys.all { it.length <= 80 }) { "dialogue_task_argument_hint_key_too_long" }
        require(this.argumentHints.values.all { it.length <= 320 }) { "dialogue_task_argument_hint_too_long" }
    }
}

internal fun interface DialogueActionDecisionObserver {
    fun onDecision(decision: DialogueActionDecision)
}

internal fun interface DialogueActionExecutor {
    fun execute(
        userText: String,
        decision: DialogueActionDecision,
        listener: TextCallAgentBackend.Listener,
    )
}

internal class DialogueActionPolicy(
    allowedActions: Set<DialogueActionId>,
    allowedArguments: Map<DialogueActionId, Set<String>> = emptyMap(),
    val minimumConfidence: Double = 0.72,
) {
    val allowedActions: Set<DialogueActionId> =
        (allowedActions + DialogueActionId.TAKE_OVER).toSet()

    val allowedArguments: Map<DialogueActionId, Set<String>> =
        allowedArguments.mapValues { (_, values) ->
            values.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        }.toMap()

    init {
        require(this.allowedActions.isNotEmpty()) { "dialogue_action_set_must_not_be_empty" }
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0) {
            "dialogue_action_minimum_confidence_out_of_range"
        }
        require(this.allowedArguments.keys.all { it in this.allowedActions }) {
            "dialogue_action_arguments_require_allowed_action"
        }
        require(DialogueActionId.TAKE_OVER !in this.allowedArguments) {
            "take_over_must_not_have_arguments"
        }
    }

    fun normalize(decision: DialogueActionDecision): DialogueActionDecision {
        if (decision.actionId !in allowedActions) return decision
        if (decision.actionId == DialogueActionId.TAKE_OVER) {
            return if (decision.argument == null) decision else decision.copy(argument = null)
        }
        val permitted = allowedArguments[decision.actionId].orEmpty()
        if (permitted.isEmpty()) {
            return if (decision.argument == null) decision else decision.copy(argument = null)
        }
        val normalizedArgument = decision.argument?.uppercase()
        return if (normalizedArgument in permitted) {
            if (normalizedArgument == decision.argument) decision else decision.copy(argument = normalizedArgument)
        } else {
            DialogueActionDecision(
                actionId = DialogueActionId.TAKE_OVER,
                confidence = decision.confidence,
                reason = decision.reason ?: "unsupported_action_argument",
            )
        }
    }

    fun allows(decision: DialogueActionDecision): Boolean {
        if (decision.actionId !in allowedActions) return false
        if (decision.actionId == DialogueActionId.TAKE_OVER) return decision.argument == null
        val permitted = allowedArguments[decision.actionId].orEmpty()
        return if (permitted.isEmpty()) {
            decision.argument == null
        } else {
            decision.argument?.uppercase() in permitted
        }
    }
}

internal object DialogueActionCatalog {
    private val descriptions = mapOf(
        DialogueActionId.ASK_REPEAT to
            "Wypowiedź jest urwana, zaszumiona lub nieczytelna; poproś rozmówcę o powtórzenie.",
        DialogueActionId.ASK_CLARIFY to
            "Wypowiedź jest czytelna, ale nie wiadomo czego rozmówca oczekuje; poproś o doprecyzowanie.",
        DialogueActionId.ACKNOWLEDGE_NEUTRAL to
            "Wystarczy neutralnie potwierdzić odbiór informacji; ta akcja nie odpowiada na pytania i nie podaje danych.",
        DialogueActionId.STATE_TASK_SUBJECT to
            "Rozmówca lub IVR pyta, w jakiej sprawie dzwonimy, czego dotyczy rozmowa albo jak może pomóc.",
        DialogueActionId.DISCLOSE_AUTHORIZED_FACT to
            "Rozmówca prosi o konkretny fakt/daną potrzebną do bieżącego zadania. W argument podaj wyłącznie identyfikator faktu, nigdy jego wartość.",
        DialogueActionId.CONFIRM_AUTHORIZED_EFFECT to
            "Rozmówca pyta o zgodę/potwierdzenie wykonania dokładnie tego efektu, który jest już celem bieżącego zadania.",
        DialogueActionId.TAKE_OVER to
            "Potrzebna jest nieobsługiwana akcja, nieautoryzowany fakt, nietypowe rozumowanie albo brak wystarczającej pewności.",
    )

    fun systemPrompt(
        policy: DialogueActionPolicy,
        taskContext: DialogueTaskContext? = null,
    ): String {
        val list = policy.allowedActions.sortedBy { it.name }.joinToString("\n") { action ->
            val arguments = policy.allowedArguments[action].orEmpty()
            val suffix = if (arguments.isEmpty()) {
                ""
            } else {
                " Dozwolone argumenty: ${arguments.sorted().joinToString(", ")}."
            }
            "- ${action.name}: ${checkNotNull(descriptions[action])}$suffix"
        }
        val contextBlock = if (taskContext == null) {
            "Kontekst bieżącego zadania nie został podany. Nie zakładaj celu ani efektu; w razie potrzeby wybierz TAKE_OVER."
        } else {
            buildString {
                append("Kontekst bieżącego zadania:\n")
                append("- temat/cel: ${taskContext.subject}\n")
                taskContext.authorizedEffect?.let { append("- autoryzowany efekt: $it\n") }
                if (taskContext.argumentHints.isNotEmpty()) {
                    append("- znaczenie identyfikatorów faktów:\n")
                    taskContext.argumentHints.toSortedMap().forEach { (id, hint) ->
                        append("  - $id: $hint\n")
                    }
                }
            }.trimEnd()
        }
        return """
            Jesteś lokalnym routerem akcji dla rozmowy telefonicznej. Wejście to finalny transkrypt drugiej strony i może zawierać błędy STT.
            Masz WYŁĄCZNIE zinterpretować intencję rozmówcy i wybrać jedną dozwoloną akcję.
            Nie generuj odpowiedzi dla rozmówcy. Nie wymyślaj faktów. Nie podawaj numerów, danych osobowych ani wartości sekretów.
            Nie wykonuj efektów zewnętrznych. Nie zmieniaj celu rozmowy.

            $contextBlock

            Jeżeli rozmówca prosi o konkretny fakt, wybierz DISCLOSE_AUTHORIZED_FACT WYŁĄCZNIE gdy jego identyfikator jest dozwolonym argumentem tej akcji. W przeciwnym razie wybierz TAKE_OVER.
            Pytanie o numer usługi, numer abonenta albo numer telefonu dotyczący bieżącej sprawy jest prośbą o fakt, a nie pytaniem o temat zadania.
            Jeżeli rozmówca pyta czy wykonać, włączyć, aktywować lub potwierdzić dokładnie autoryzowany efekt z kontekstu, wybierz CONFIRM_AUTHORIZED_EFFECT.
            STATE_TASK_SUBJECT wybieraj tylko gdy rozmówca pyta w jakiej sprawie dzwonimy, czego dotyczy rozmowa albo jak może pomóc.
            Jeżeli prosi o coś poza katalogiem lub nie jesteś pewien, wybierz TAKE_OVER.

            Dozwolone akcje:
            $list

            Zwróć wyłącznie jeden obiekt JSON bez markdownu i dodatkowych kluczy:
            {"action":"ACTION_ID","confidence":0.0,"argument":"OPTIONAL_ARGUMENT","reason":"krótki_powód"}
            Klucz argument wolno zwrócić WYŁĄCZNIE dla akcji, która ma jawnie wymienione dozwolone argumenty. Dla pozostałych akcji pomiń go całkowicie.
            confidence musi być liczbą 0..1.
        """.trimIndent()
    }
}

/**
 * Gemma-first action router. Classification and execution are deliberately separate boundaries.
 */
internal class DialogueActionBackend(
    private val classifierBackend: TextCallAgentBackend,
    private val policy: DialogueActionPolicy,
    private val executor: DialogueActionExecutor,
    private val observer: DialogueActionDecisionObserver? = null,
) : TextCallAgentBackend {
    private val generation = AtomicLong(0L)

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        val requestGeneration = generation.incrementAndGet()
        classifierBackend.cancel()
        classifierBackend.generate(userText, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                if (!isCurrent(requestGeneration)) return
                val parsed = parseDecision(text)
                if (parsed is ParseResult.Error) {
                    listener.onError(parsed.reason)
                    return
                }
                val rawDecision = (parsed as ParseResult.Success).decision
                val decision = policy.normalize(rawDecision)
                try {
                    observer?.onDecision(decision)
                } catch (_: Throwable) {
                    // Diagnostics never alter routing.
                }
                if (decision.actionId !in policy.allowedActions) {
                    listener.onError("dialogue_action_not_allowed")
                    return
                }
                if (decision.confidence < policy.minimumConfidence) {
                    listener.onError("dialogue_action_low_confidence")
                    return
                }
                if (decision.actionId == DialogueActionId.TAKE_OVER) {
                    listener.onError("dialogue_action_takeover_required")
                    return
                }
                if (!policy.allows(decision)) {
                    listener.onError("dialogue_action_argument_not_allowed")
                    return
                }
                try {
                    executor.execute(
                        userText,
                        decision,
                        guardedListener(requestGeneration, listener),
                    )
                } catch (error: Throwable) {
                    if (isCurrent(requestGeneration)) {
                        listener.onError("dialogue_action_executor_${sanitize(error.javaClass.simpleName)}")
                    }
                }
            }

            override fun onError(reason: String) {
                if (isCurrent(requestGeneration)) {
                    listener.onError("dialogue_action_classifier_${sanitize(reason)}")
                }
            }
        })
    }

    override fun cancel() {
        generation.incrementAndGet()
        classifierBackend.cancel()
    }

    override fun close() {
        generation.incrementAndGet()
        classifierBackend.close()
    }

    private fun guardedListener(
        requestGeneration: Long,
        listener: TextCallAgentBackend.Listener,
    ) = object : TextCallAgentBackend.Listener {
        override fun onComplete(text: String) {
            if (isCurrent(requestGeneration)) listener.onComplete(text)
        }

        override fun onError(reason: String) {
            if (isCurrent(requestGeneration)) listener.onError(sanitize(reason))
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean =
        generation.get() == requestGeneration

    private fun parseDecision(raw: String): ParseResult {
        if (raw.isBlank() || raw.length > MAX_MODEL_OUTPUT_CHARS) {
            return ParseResult.Error("dialogue_action_invalid_model_output")
        }
        val root = try {
            JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        } ?: return ParseResult.Error("dialogue_action_invalid_model_output")

        if (root.keySet().any { it !in ALLOWED_OUTPUT_KEYS }) {
            return ParseResult.Error("dialogue_action_unsafe_model_output")
        }
        val actionText = root.string("action")
            ?: return ParseResult.Error("dialogue_action_invalid_model_output")
        val confidence = root.number("confidence")
            ?: return ParseResult.Error("dialogue_action_invalid_model_output")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            return ParseResult.Error("dialogue_action_invalid_model_output")
        }
        val action = try {
            DialogueActionId.valueOf(actionText)
        } catch (_: IllegalArgumentException) {
            return ParseResult.Error("dialogue_action_unknown")
        }
        val rawArgument = root.optionalString("argument")
            ?.trim()
            ?.uppercase()
            ?.take(MAX_ARGUMENT_CHARS)
            ?.ifBlank { null }
        val argument = if (policy.allowedArguments[action].orEmpty().isEmpty()) null else rawArgument
        val reason = root.optionalString("reason")?.take(MAX_REASON_CHARS)
        return ParseResult.Success(
            DialogueActionDecision(
                actionId = action,
                confidence = confidence,
                argument = argument,
                reason = reason,
            ),
        )
    }

    private sealed interface ParseResult {
        data class Success(val decision: DialogueActionDecision) : ParseResult
        data class Error(val reason: String) : ParseResult
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.optionalString(name: String): String? {
        val value = get(name) ?: return null
        return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }

    private fun JsonObject.number(name: String): Double? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        return try { value.asDouble } catch (_: RuntimeException) { null }
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(160)

    private companion object {
        val ALLOWED_OUTPUT_KEYS = setOf("action", "confidence", "argument", "reason")
        const val MAX_MODEL_OUTPUT_CHARS = 2_000
        const val MAX_ARGUMENT_CHARS = 80
        const val MAX_REASON_CHARS = 160
    }
}

internal object DialogueActionBackendFactory {
    fun create(
        context: Context,
        provider: TextLlmProvider,
        policy: DialogueActionPolicy,
        executor: DialogueActionExecutor,
        observer: DialogueActionDecisionObserver? = null,
        taskContext: DialogueTaskContext? = null,
    ): TextCallAgentBackend {
        val prompt = DialogueActionCatalog.systemPrompt(policy, taskContext)
        val classifier = when (provider) {
            TextLlmProvider.LOCAL_PHONE_LLM ->
                LocalPhoneLlmBackendFactory.create(context.applicationContext, prompt)

            TextLlmProvider.LOCAL_GEMMA_4 ->
                Gemma4LiteRtTextBackendFactory.createActionClassifier(
                    context = context.applicationContext,
                    systemInstruction = prompt,
                    allowedActions = policy.allowedActions,
                )

            else -> throw IllegalArgumentException(
                "provider_not_enabled_for_dialogue_actions_${provider.name.lowercase()}",
            )
        }
        return DialogueActionBackend(classifier, policy, executor, observer)
    }
}
