package pl.michalmatu.aicallbridge.textagent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicLong

enum class DialogueSkillId {
    ASK_REPEAT,
    ASK_CLARIFY,
    ACKNOWLEDGE_NEUTRAL,
    CONFIRM_EXPECTED_SUBJECT,
    TAKE_OVER,
}

data class DialogueSkillDecision(
    val skillId: DialogueSkillId,
    val confidence: Double,
    val reason: String? = null,
) {
    init {
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "dialogue_skill_confidence_out_of_range"
        }
        require(reason == null || reason.length <= MAX_REASON_CHARS) {
            "dialogue_skill_reason_too_long"
        }
    }

    private companion object {
        const val MAX_REASON_CHARS = 160
    }
}

fun interface DialogueSkillDecisionObserver {
    fun onDecision(decision: DialogueSkillDecision)
}

class DialogueSkillPolicy(
    allowedResponses: Map<DialogueSkillId, String>,
    val minimumConfidence: Double = 0.70,
) {
    val allowedResponses: Map<DialogueSkillId, String> = allowedResponses.mapValues { (_, value) ->
        value.trim().also {
            require(it.isNotEmpty()) { "dialogue_skill_response_must_not_be_blank" }
            require(it.length <= MAX_RESPONSE_CHARS) { "dialogue_skill_response_too_long" }
        }
    }.toMap()

    val allowedSkills: Set<DialogueSkillId> =
        (this.allowedResponses.keys + DialogueSkillId.TAKE_OVER).toSet()

    init {
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0) {
            "dialogue_skill_minimum_confidence_out_of_range"
        }
        require(DialogueSkillId.TAKE_OVER !in this.allowedResponses) {
            "take_over_must_not_have_spoken_response"
        }
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 600
    }
}

object DialogueSkillCatalog {
    private val descriptions = mapOf(
        DialogueSkillId.ASK_REPEAT to
            "Wypowiedź jest urwana, zaszumiona lub za krótka; poproś o powtórzenie.",
        DialogueSkillId.ASK_CLARIFY to
            "Słowa są czytelne, ale znaczenie lub oczekiwanie rozmówcy pozostaje niejednoznaczne.",
        DialogueSkillId.ACKNOWLEDGE_NEUTRAL to
            "Można jedynie neutralnie potwierdzić odbiór informacji, bez przyjmowania faktów lub zobowiązań.",
        DialogueSkillId.CONFIRM_EXPECTED_SUBJECT to
            "Rozmówca pyta, czy rozmowa dotyczy dokładnie wcześniej autoryzowanego przez aplikację obiektu/tematu.",
        DialogueSkillId.TAKE_OVER to
            "Potrzebne są fakty, dane wrażliwe, decyzja użytkownika, zobowiązanie, nietypowe rozumowanie albo brak pewności.",
    )

    fun systemPrompt(allowedSkills: Set<DialogueSkillId>): String {
        require(allowedSkills.isNotEmpty()) { "dialogue_skill_set_must_not_be_empty" }
        val ordered = allowedSkills.sortedBy { it.name }
        val list = ordered.joinToString("\n") { skill ->
            "- ${skill.name}: ${checkNotNull(descriptions[skill])}"
        }
        return """
            Jesteś lokalnym klasyfikatorem dialogu telefonicznego. Wejście to finalny transkrypt drugiej strony i może zawierać błędy STT.
            Wybierz dokładnie jeden dozwolony skill. Nie generuj finalnej wypowiedzi do rozmówcy; tekst odpowiedzi należy wyłącznie do aplikacji.
            Nie wolno Ci wybierać numeru, zmieniać celu rozmowy, ujawniać sekretów, potwierdzać zobowiązań ani kończyć zadania.
            Gdy potrzebne są dane, decyzja użytkownika, zobowiązanie albo nie masz wystarczającej pewności, wybierz TAKE_OVER.

            Dozwolone skills:
            $list

            Zwróć wyłącznie jeden obiekt JSON bez markdownu i bez dodatkowych kluczy:
            {"skill":"SKILL_ID","confidence":0.0,"reason":"krótki_powód"}
            confidence musi być liczbą od 0 do 1. reason jest opcjonalny.
        """.trimIndent()
    }
}

/**
 * Converts a local model into a bounded skill selector.
 *
 * The model never owns spoken text. It may only select one app-allowed skill and confidence. The
 * exact response text is supplied by [DialogueSkillPolicy]. Any extra model field, low-confidence
 * result, unavailable skill, or TAKE_OVER fails closed through [TextCallAgentBackend.Listener].
 */
class DialogueSkillTextBackend(
    private val classifierBackend: TextCallAgentBackend,
    private val policy: DialogueSkillPolicy,
    private val observer: DialogueSkillDecisionObserver? = null,
) : TextCallAgentBackend {
    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        classifierBackend.generate(userText, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                val parsed = parseDecision(text)
                if (parsed is ParseResult.Error) {
                    listener.onError(parsed.reason)
                    return
                }
                val decision = (parsed as ParseResult.Success).decision
                try {
                    observer?.onDecision(decision)
                } catch (_: Throwable) {
                    // Diagnostics cannot change fallback/approval behavior.
                }
                if (decision.skillId !in policy.allowedSkills) {
                    listener.onError("dialogue_skill_not_allowed")
                    return
                }
                if (decision.confidence < policy.minimumConfidence) {
                    listener.onError("dialogue_skill_low_confidence")
                    return
                }
                if (decision.skillId == DialogueSkillId.TAKE_OVER) {
                    listener.onError("dialogue_skill_takeover_required")
                    return
                }
                val response = policy.allowedResponses[decision.skillId]
                if (response == null) {
                    listener.onError("dialogue_skill_response_not_authorized")
                    return
                }
                listener.onComplete(response)
            }

            override fun onError(reason: String) {
                listener.onError("dialogue_skill_classifier_${sanitize(reason)}")
            }
        })
    }

    override fun cancel() = classifierBackend.cancel()

    override fun close() = classifierBackend.close()

    private fun parseDecision(raw: String): ParseResult {
        if (raw.isBlank() || raw.length > MAX_MODEL_OUTPUT_CHARS) {
            return ParseResult.Error("dialogue_skill_invalid_model_output")
        }
        val root = try {
            JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        } ?: return ParseResult.Error("dialogue_skill_invalid_model_output")

        if (root.keySet().any { it !in ALLOWED_OUTPUT_KEYS }) {
            return ParseResult.Error("dialogue_skill_unsafe_model_output")
        }
        val skillText = root.string("skill")
            ?: return ParseResult.Error("dialogue_skill_invalid_model_output")
        val confidence = root.number("confidence")
            ?: return ParseResult.Error("dialogue_skill_invalid_model_output")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            return ParseResult.Error("dialogue_skill_invalid_model_output")
        }
        val skill = try {
            DialogueSkillId.valueOf(skillText)
        } catch (_: IllegalArgumentException) {
            return ParseResult.Error("dialogue_skill_unknown")
        }
        val reason = root.optionalString("reason")?.take(MAX_REASON_CHARS)
        return ParseResult.Success(DialogueSkillDecision(skill, confidence, reason))
    }

    private sealed interface ParseResult {
        data class Success(val decision: DialogueSkillDecision) : ParseResult
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
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(120)

    private companion object {
        val ALLOWED_OUTPUT_KEYS = setOf("skill", "confidence", "reason")
        const val MAX_MODEL_OUTPUT_CHARS = 2_000
        const val MAX_REASON_CHARS = 160
    }
}

/**
 * One bounded backend failover. A primary completion wins. A primary error starts exactly one
 * secondary backend for the same user text. Generation guards prevent stale primary/fallback work
 * from leaking into a newer turn. Both candidates still pass through the caller's normal output
 * approval because this class only implements [TextCallAgentBackend].
 */
class FailoverTextCallAgentBackend(
    private val primary: TextCallAgentBackend,
    private val fallback: TextCallAgentBackend,
) : TextCallAgentBackend {
    private val generation = AtomicLong(0L)

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        val requestGeneration = generation.incrementAndGet()
        primary.cancel()
        fallback.cancel()
        try {
            primary.generate(userText, object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    if (isCurrent(requestGeneration)) listener.onComplete(text)
                }

                override fun onError(reason: String) {
                    if (!isCurrent(requestGeneration)) return
                    startFallback(userText, requestGeneration, listener)
                }
            })
        } catch (_: Throwable) {
            if (isCurrent(requestGeneration)) startFallback(userText, requestGeneration, listener)
        }
    }

    override fun cancel() {
        generation.incrementAndGet()
        primary.cancel()
        fallback.cancel()
    }

    override fun close() {
        generation.incrementAndGet()
        try { primary.close() } finally { fallback.close() }
    }

    private fun startFallback(
        userText: String,
        requestGeneration: Long,
        listener: TextCallAgentBackend.Listener,
    ) {
        try {
            fallback.generate(userText, object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    if (isCurrent(requestGeneration)) listener.onComplete(text)
                }

                override fun onError(reason: String) {
                    if (isCurrent(requestGeneration)) listener.onError(sanitize(reason))
                }
            })
        } catch (error: Throwable) {
            if (isCurrent(requestGeneration)) {
                listener.onError("fallback_start_${error.javaClass.simpleName}")
            }
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean =
        generation.get() == requestGeneration

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(160)
}
