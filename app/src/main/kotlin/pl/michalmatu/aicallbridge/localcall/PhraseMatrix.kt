package pl.michalmatu.aicallbridge.localcall

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

enum class PhraseMatcherKind {
    EXACT,
    ALIAS,
    FUZZY,
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
    fuzzyPhrases: Set<String> = emptySet(),
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
    val fuzzyPhrases: Set<String> = copyNonBlank(fuzzyPhrases, "fuzzy_phrases")
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
 * Exact phrases and explicit aliases always win. Optional fuzzy phrases are opt-in per rule and
 * accept at most one character insertion, deletion, or substitution while preserving token count.
 * A fuzzy tie between different classifications fails closed. Previous-turn context is explicit
 * input only; PhraseMatrix stores no dialogue state. Unknown input returns null.
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

    private data class FuzzyCandidate(
        val normalized: String,
        val match: PhraseMatch,
        val previousRuleIds: Set<String>,
    )

    private val declaredRules = rules.toList()
    private val index: Map<String, IndexedMatches> = buildIndex(declaredRules)
    private val fuzzyCandidates: List<FuzzyCandidate> = buildFuzzyCandidates(declaredRules)

    fun match(transcript: String, previousRuleId: String? = null): PhraseMatch? {
        val normalized = normalize(transcript)
        if (normalized.isEmpty()) return null

        val previous = previousRuleId?.trim()?.also {
            require(it.isNotEmpty()) { "previous_rule_id_must_not_be_blank" }
        }

        val indexedMatches = index[normalized]
        if (indexedMatches != null) {
            return previous?.let(indexedMatches.byPreviousRuleId::get) ?: indexedMatches.generic
        }

        return fuzzyMatch(normalized, previous)
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

    private fun buildFuzzyCandidates(rules: List<PhraseMatrixRule>): List<FuzzyCandidate> {
        val result = mutableListOf<FuzzyCandidate>()
        val genericSources = mutableSetOf<String>()
        val contextualSources = mutableSetOf<Pair<String, String>>()

        for (rule in rules) {
            for (source in rule.fuzzyPhrases) {
                val normalized = normalize(source)
                require(normalized.length >= MIN_FUZZY_SOURCE_LENGTH) { "fuzzy_phrase_too_short" }

                if (rule.previousRuleIds.isEmpty()) {
                    require(genericSources.add(normalized)) { "normalized_fuzzy_phrase_collision" }
                } else {
                    for (previousRuleId in rule.previousRuleIds) {
                        require(contextualSources.add(normalized to previousRuleId)) {
                            "normalized_fuzzy_phrase_previous_rule_collision"
                        }
                    }
                }

                result += FuzzyCandidate(
                    normalized = normalized,
                    match = PhraseMatch(
                        ruleId = rule.ruleId,
                        confidence = FUZZY_CONFIDENCE,
                        matcherKind = PhraseMatcherKind.FUZZY,
                        variantClass = rule.variantClass,
                    ),
                    previousRuleIds = rule.previousRuleIds,
                )
            }
        }
        return result.toList()
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

    private fun fuzzyMatch(normalized: String, previousRuleId: String?): PhraseMatch? {
        val inputTokenCount = tokenCount(normalized)

        if (previousRuleId != null) {
            val contextual = bestFuzzyMatch(
                normalized = normalized,
                tokenCount = inputTokenCount,
                candidates = fuzzyCandidates.filter { previousRuleId in it.previousRuleIds },
            )
            if (contextual != null) return contextual
        }

        return bestFuzzyMatch(
            normalized = normalized,
            tokenCount = inputTokenCount,
            candidates = fuzzyCandidates.filter { it.previousRuleIds.isEmpty() },
        )
    }

    private fun bestFuzzyMatch(
        normalized: String,
        tokenCount: Int,
        candidates: List<FuzzyCandidate>,
    ): PhraseMatch? {
        var bestDistance = Int.MAX_VALUE
        val bestMatches = linkedSetOf<PhraseMatch>()

        for (candidate in candidates) {
            if (tokenCount(candidate.normalized) != tokenCount) continue
            val distance = editDistanceAtMostOne(normalized, candidate.normalized) ?: continue

            when {
                distance < bestDistance -> {
                    bestDistance = distance
                    bestMatches.clear()
                    bestMatches += candidate.match
                }
                distance == bestDistance -> bestMatches += candidate.match
            }
        }

        return bestMatches.singleOrNull()
    }

    private companion object {
        const val MIN_FUZZY_SOURCE_LENGTH = 6
        const val FUZZY_CONFIDENCE = 0.9

        fun tokenCount(value: String): Int = 1 + value.count { it == ' ' }

        fun editDistanceAtMostOne(left: String, right: String): Int? {
            if (left == right) return 0
            if (abs(left.length - right.length) > 1) return null

            if (left.length == right.length) {
                var mismatches = 0
                for (index in left.indices) {
                    if (left[index] != right[index] && ++mismatches > 1) return null
                }
                return 1
            }

            val shorter = if (left.length < right.length) left else right
            val longer = if (left.length < right.length) right else left
            var shortIndex = 0
            var longIndex = 0
            var skipped = false

            while (shortIndex < shorter.length && longIndex < longer.length) {
                if (shorter[shortIndex] == longer[longIndex]) {
                    shortIndex += 1
                    longIndex += 1
                } else {
                    if (skipped) return null
                    skipped = true
                    longIndex += 1
                }
            }
            return 1
        }

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
