# Phrase / Intent Matrix — engine research and integration plan

Date: 2026-09-21

Status: `DECISION RECORDED / NATIVE PHRASE MATRIX SELECTED`

This document records the matcher-engine research for the local Phrase / Intent Matrix. It is not permission to add another dialogue authority layer. `CallTask` / `CallWorkflow` / `CallPlan` / output approval remain authoritative.

## Product goal

The target architecture is **deterministic fast path first, LLM supervisor on demand**:

```text
final STT
 -> normalize
 -> native PhraseMatrix
 -> existing ruleId + confidence/diagnostics
 -> CallPlan / workflow / approval
 -> TTS

unknown / ambiguous / important turn
 -> bounded LLM supervisor
 -> suggest existing ruleId only
 -> same CallPlan / workflow / approval
```

Naturalness may later come from small reviewed response-variant banks. Free-form matcher/script output is not an authority path.

## Non-negotiable integration boundary

Matcher/script/model technology must not:

- create or widen dialing authority;
- invent facts, targets, prices, proposals, outcomes or commitments;
- bypass `CallConfirmationPolicy`, `CallWorkflow`, `CallCommitmentGate` or output approval;
- perform DTMF, authentication, payment, purchase, activation or tariff/contract changes;
- emit arbitrary speech directly to TTS/TX;
- create a second conversation authority/state store parallel to `CallWorkflow`.

The selected contract remains:

```text
PhraseMatch(
    existingRuleId,
    confidence,
    matcherKind,
    optionalVariantClass,
)
```

The product layer validates the `ruleId` against the bound `CallPlan`. Only the validated `CallPlanDecision.ruleId()` may become session previous-turn context.

## Native CallBridge PhraseMatrix — selected

The host-green Kotlin implementation currently provides:

- NFKC normalization, lowercasing and deterministic punctuation/space normalization;
- exact phrase matching;
- explicit aliases for reviewed ASR/missing-diacritic variants;
- deterministic collision rejection and unknown fail-closed behavior;
- `PhraseMatch(ruleId, confidence, matcherKind, variantClass?)` only;
- optional explicit previous-rule constraints;
- no matcher-owned session state;
- validation through `CallPlanTurnCoordinator` before workflow/output effects;
- session-owned `previousValidatedRuleId`, populated only from validated CallPlan decisions;
- readiness / prepared-call / Android factory binding for optional `CallPlan + PhraseMatrix`.

Key evidence:

```text
.agent/results/chatgpt-gate-c-phrase-matrix-baseline-green-v55-20260921.json
.agent/results/chatgpt-gate-c-phrase-router-green-v59-20260921.json
.agent/results/chatgpt-phrase-matrix-previous-context-green-v64-20260921.json
.agent/results/chatgpt-session-phrase-context-green-v68-20260921.json
.agent/results/chatgpt-readiness-phrase-matrix-green-v70-20260921.json
.agent/results/chatgpt-android-readiness-binding-green-v72-20260921.json
```

### Host benchmark

Evidence: `.agent/results/chatgpt-native-phrase-matrix-bench-v62b-20260921.json`.

Small Polish corpus used 8 rules / 20 reviewed variants and false-positive guards.

```text
init:              11.845833 ms
average match:      0.815414585 us
Polish corpus:      GREEN
false-positive guard: GREEN
```

This is a host microbenchmark, not an S22 performance claim.

## RiveScript Java — spike complete, not selected

Repository: `aichaos/rivescript-java`.

Revision inspected: `04abeca7fffeaf0783e6aa9c2fe84c34829e5a67`.

Evidence:

```text
.agent/results/chatgpt-rivescript-java-spike-v60b-20260921.json
.agent/results/chatgpt-rivescript-apk-size-v61-20260921.json
```

The isolated spike proved:

- Polish UTF-8 matching works for the tested examples;
- `%Previous`-style context works;
- current project toolchain can assemble with `rivescript-core:0.11.0`;
- the library can emit arbitrary free reply text, which is broader than the desired classification-only contract.

Measured host/build results:

```text
init + sort:         46.557333 ms
average reply:       88.8522959 us
rivescript jar:      79,391 B
slf4j-api jar:       41,472 B
clean baseline APK:  5,383,492 B
RiveScript APK:      5,517,624 B
APK delta:           +134,132 B
```

Dependency path:

```text
com.rivescript:rivescript-core:0.11.0
 -> org.slf4j:slf4j-api:1.7.26
```

### Decision

Do **not** add RiveScript to production now.

It provides useful proof that previous-turn scripting is feasible, but the native implementation is substantially smaller in capability surface, roughly two orders of magnitude faster in this host microbenchmark, has no extra matcher dependency/APK cost, and naturally enforces the classification-only contract.

Re-evaluate only if the native matcher later accumulates enough parser complexity that measured maintenance cost clearly outweighs the dependency/scripting surface.

## ChatScript — design reference only

Repository: `ChatScript/ChatScript`.

Revision inspected: `9f5eec4736ba22bd992a6498c1e0052e2a795125` (`CS 14.1`, 2024-05-06).

Useful concepts to borrow selectively:

- pattern specificity and ordering;
- negative terms / exclusions;
- bounded wildcard capture;
- concepts/synonym classes;
- rejoinders / previous-response context;
- topic/stage priority;
- deterministic debug traces.

The audit found Android-specific preprocessor paths, but the engine remains a broad C++ system with native/JNI, data-file and dictionary integration concerns. Its embedding documentation describes compiling the C++ engine and using APIs such as `InitSystem` / `PerformChat`; a large configuration is documented around 15–18 MB memory, with miniaturized dictionaries as a special concern.

For the current CallBridge need this is disproportionate. Do not vendor ChatScript or its dictionaries. Treat it as mature design/reference material; any later native embedding would first require a minimal Android/JNI/data/license/footprint audit.

## KStateMachine — deferred

Repository: `KStateMachine/kstatemachine`.

It is a state-machine library, not a phrase matcher. Current session state (`consecutiveUnknownCount`, `previousValidatedRuleId`) does not justify introducing another state abstraction.

Reconsider only if non-authority navigation/stage state becomes materially complex. `CallWorkflow` must remain the authority owner regardless.

## Decision table

| Candidate | Matching/context result | Host cost | Product-surface cost | Decision |
| --- | --- | --- | --- | --- |
| native Kotlin PhraseMatrix | exact/alias + explicit previous rule GREEN | ~11.85 ms init, ~0.815 us/match | no new dependency | **SELECTED** |
| RiveScript Java | Polish UTF-8 + previous context GREEN | ~46.56 ms init, ~88.85 us/reply | +134,132 B debug APK, SLF4J, arbitrary reply scripting | reference only |
| ChatScript | mature patterns/topics/rejoinders | not embedded | C++/JNI/data/license/memory complexity | design reference |
| KStateMachine | stage/state only | not measured | unnecessary second abstraction today | deferred |

## Next matcher work

The next product/research slice stays native and bounded:

1. expand the Polish ASR-like corpus;
2. add a deterministic fuzzy/pattern rule only for a concrete exact/alias miss;
3. every new positive rule must include neighboring negative/negation/multi-intent guards;
4. preserve deterministic replay and classification-only output;
5. measure hit rate, no-match rate, false-positive rate and p50/p95 latency;
6. keep sensitive/committing actions out of generic fuzzy shortcuts.

A possible rule model may eventually include reviewed positive/negative tokens, optional tokens, bounded edit/token distance and explicit previous/stage constraints. Do not implement all of these merely for feature parity with third-party engines.

## LLM supervisor relationship

The future LLM layer is a bounded semantic supervisor, not the default reply author:

```text
transcript + bounded recent context + current stage + available rule ids
 -> suggest existing ruleId + confidence
 -> deterministic validator
 -> CallPlan / workflow
```

Its output remains quarantined until validated. A newer transcript, resumed speech, cancellation, workflow state change or already-released deterministic response invalidates stale supervisor output.

## Research item conclusion

The original engine-selection exit criteria are satisfied at host level:

1. native PhraseMatrix baseline exists and is integrated;
2. RiveScript was measured rather than assumed;
3. ChatScript minimal-integration concerns were documented;
4. KStateMachine is not currently needed;
5. native PhraseMatrix is the recommended implementation path;
6. matcher rule ids flow through existing CallPlan authority tests;
7. there is no script/matcher -> direct TTS/TX or commitment path.

Further work belongs to matcher-quality and supervisor slices, not another engine-selection round unless new evidence changes the tradeoff.
