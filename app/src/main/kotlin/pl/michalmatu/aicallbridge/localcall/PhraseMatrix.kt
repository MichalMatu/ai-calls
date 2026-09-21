package pl.michalmatu.aicallbridge.localcall

import java.text.Normalizer
import java.util.Locale

enum class PhraseMatcherKind {
    EXACT,
    ALIAS,
}

data class PhraseMatch(
    val ruleId: String,
    val confidence: Double,
    val matcherKind: PhraseMatcherKind,
    val variantClass: String? = null,
)

class PhraseMatrixRule(
    ruleId: String,
    phrases: Set<String>,
    aliases: Set<String> = emptySet(),
    variantClass: String? = null,
) {
    val ruleId: String = ruleId.trim().also {
        require(it.isNotEmpty()) { "rule_id_must_not_be_blank" }
    }
    val phrases: Set<String> = copyNonBlank(phrases, "phrases").also {
        require(it.isNotEmpty()) { "phrases_must_not_be_empty" }
    }
    val aliases: Set<String> = copyNonBlank(aliases, "aliases")
    val variantClass: String? = variantClass?.trim()?.also {
        require(it.isNotEmpty()) { "variant_class_must_not_be_blank" }
    }

    private companion object {
        fun copyNonBlank(values: Set<String>, name: String): Set<String> =
            values.mapTo(linkedSetOf()) { value ->
                value.trim().also {
                    require(it.isNotEmpty()) { "${name}_must_not_contain_blank" }
                }
            }.toSet()
    }
}

/**
 * Small deterministic classification-only phrase matcher.
 *
 * It returns only a predeclared rule id plus matcher diagnostics. It does not resolve CallPlan
 * authority, mutate workflow state, generate speech, or invoke a model. Unknown input returns null.
 */
class PhraseMatrix(rules: List<PhraseMatrixRule>) {
    private val index: Map<String, PhraseMatch> = buildIndex(rules.toList())

    fun match(transcript: String): PhraseMatch? {
        val normalized = normalize(transcript)
        if (normalized.isEmpty()) return null
        return index[normalized]
    }

    private fun buildIndex(rules: List<PhraseMatrixRule>): Map<String, PhraseMatch> {
        val result = linkedMapOf<String, PhraseMatch>()
        for (rule in rules) {
            rule.phrases.forEach { phrase ->
                insert(result, phrase, rule, PhraseMatcherKind.EXACT)
            }
            rule.aliases.forEach { alias ->
                insert(result, alias, rule, PhraseMatcherKind.ALIAS)
            }
        }
        return result.toMap()
    }

    private fun insert(
        result: MutableMap<String, PhraseMatch>,
        source: String,
        rule: PhraseMatrixRule,
        matcherKind: PhraseMatcherKind,
    ) {
        val normalized = normalize(source)
        require(normalized.isNotEmpty()) { "phrase_normalizes_to_blank" }
        require(!result.containsKey(normalized)) { "normalized_phrase_collision" }
        result[normalized] = PhraseMatch(
            ruleId = rule.ruleId,
            confidence = 1.0,
            matcherKind = matcherKind,
            variantClass = rule.variantClass,
        )
    }

    private companion object {
        fun normalize(value: String): String {
            val source = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            val normalized = StringBuilder(source.length)
            source.codePoints().forEach { codePoint ->
                if (Character.isLetterOrDigit(codePoint)) {
                    normalized.appendCodePoint(codePoint)
                } else {
                    normalized.append(' ')
                }
            }
            return normalized.toString().trim().replace(Regex("\\s+"), " ")
        }
    }
}
