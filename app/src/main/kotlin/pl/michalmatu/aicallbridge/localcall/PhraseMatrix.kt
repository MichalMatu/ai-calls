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
    previousRuleIds: Set<String> = emptySet(),
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
    val previousRuleIds: Set<String> = copyNonBlank(previousRuleIds, "previous_rule_ids")

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
 * authority, mutate workflow state, generate speech, or invoke a model. Previous-turn context is
 * explicit input only; PhraseMatrix stores no dialogue state. Unknown input returns null.
 */
class PhraseMatrix(rules: List<PhraseMatrixRule>) {
    private data class IndexedMatches(
        val generic: PhraseMatch?,
        val byPreviousRuleId: Map<String, PhraseMatch>,
    )

    private class MutableIndexedMatches {
        var generic: PhraseMatch? = null
        val byPreviousRuleId: MutableMap<String, PhraseMatch> = linkedMapOf()
    }

    private val index: Map<String, IndexedMatches> = buildIndex(rules.toList())

    fun match(transcript: String, previousRuleId: String? = null): PhraseMatch? {
        val normalized = normalize(transcript)
        if (normalized.isEmpty()) return null

        val previous = previousRuleId?.trim()?.also {
            require(it.isNotEmpty()) { "previous_rule_id_must_not_be_blank" }
        }
        val matches = index[normalized] ?: return null
        return previous?.let(matches.byPreviousRuleId::get) ?: matches.generic
    }

    private fun buildIndex(rules: List<PhraseMatrixRule>): Map<String, IndexedMatches> {
        val mutable = linkedMapOf<String, MutableIndexedMatches>()
        for (rule in rules) {
            rule.phrases.forEach { phrase ->
                insert(mutable, phrase, rule, PhraseMatcherKind.EXACT)
            }
            rule.aliases.forEach { alias ->
                insert(mutable, alias, rule, PhraseMatcherKind.ALIAS)
            }
        }
        return mutable.mapValues { (_, matches) ->
            IndexedMatches(
                generic = matches.generic,
                byPreviousRuleId = matches.byPreviousRuleId.toMap(),
            )
        }.toMap()
    }

    private fun insert(
        result: MutableMap<String, MutableIndexedMatches>,
        source: String,
        rule: PhraseMatrixRule,
        matcherKind: PhraseMatcherKind,
    ) {
        val normalized = normalize(source)
        require(normalized.isNotEmpty()) { "phrase_normalizes_to_blank" }
        val matches = result.getOrPut(normalized) { MutableIndexedMatches() }
        val match = PhraseMatch(
            ruleId = rule.ruleId,
            confidence = 1.0,
            matcherKind = matcherKind,
            variantClass = rule.variantClass,
        )

        if (rule.previousRuleIds.isEmpty()) {
            require(matches.generic == null) { "normalized_phrase_collision" }
            matches.generic = match
            return
        }

        for (previousRuleId in rule.previousRuleIds) {
            require(!matches.byPreviousRuleId.containsKey(previousRuleId)) {
                "normalized_phrase_previous_rule_collision"
            }
            matches.byPreviousRuleId[previousRuleId] = match
        }
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
