# Phrase / Intent Matrix — engine research and integration plan

Date: 2026-09-21

Status: `PLANNED / RESEARCH NOTE`

This document preserves the concrete follow-on research for the local Phrase / Intent Matrix idea. It is not permission to add another dialogue authority layer. The current `CallTask` / `CallWorkflow` / `CallPlan` / output-approval boundaries remain authoritative.

## Why this matters

The target architecture is **deterministic fast path first, LLM supervisor on demand**:

```text
final STT
 -> normalization
 -> exact / lightweight phrase-intent matcher
 -> high-confidence existing ruleId
 -> CallPlan / workflow / approval
 -> TTS

                 +-> bounded LLM supervisor in parallel/background
                     observes context / warms / classifies
                     may suggest only an existing ruleId
```

Typical turns such as greetings, acknowledgements, repeat/wait requests and simple task-known questions should complete in lookup/matcher time. This gives the local LLM breathing room to accumulate context and enter only when the turn is ambiguous or materially important.

Naturalness should come from a small reviewed response-variant bank. A temperature-like product setting may control how wide the eligible variant set is, but selection should remain deterministic/replayable, for example from a stable seed derived from call id + turn index + intent/rule id.

## Non-negotiable integration boundary

Any imported script/matcher technology is **classification/matching infrastructure only**.

It must not:

- create or widen dialing authority;
- invent facts, targets, prices, proposals, outcomes or commitments;
- bypass `CallConfirmationPolicy`, `CallWorkflow`, `CallCommitmentGate` or output approval;
- call Android/OS/network code from a conversation script;
- perform DTMF, authentication, payment, purchase, activation or contract/tariff changes;
- emit arbitrary speech directly to TTS/TX;
- create a second conversation authority/state store parallel to `CallWorkflow`.

Preferred output contract:

```text
PhraseMatch(
    existingRuleId,
    confidence,
    matcherKind,
    optionalVariantClass,
)
```

The product layer then validates the `ruleId` against the bound `CallPlan`. Only an already-authorized/predeclared rule can become a `CallPlanDecision`.

## Repository candidate A — RiveScript Java

Repository: `aichaos/rivescript-java`

First-pass revision inspected: `04abeca7fffeaf0783e6aa9c2fe84c34829e5a67` (2019-08-06).

### What is attractive

- Native Java library rather than a standalone server.
- Simple trigger -> reply scripting model.
- Topics, conditions, user/session variables and `%Previous`-style previous-turn matching are already implemented.
- Supports custom Java subroutines/object handlers, although CallBridge should **not** expose those to ordinary conversation scripts.
- UTF-8 mode exists, which is essential for Polish.
- Published as `com.rivescript:rivescript-core` and licensed MIT.
- Core was compiled for Java 7, so bytecode/language requirements are conservative enough to make Android feasibility plausible.

Relevant upstream files to inspect in a dedicated spike:

```text
README.md
LICENSE
build.gradle
rivescript-core/src/main/java/com/rivescript/RiveScript.java
rivescript-core/src/main/java/com/rivescript/Config.java
rivescript-core/src/main/java/com/rivescript/parser/Parser.java
rivescript-core/src/main/java/com/rivescript/session/...
rivescript-core/src/integration-test/resources/testsuite.rive
```

### Risks / caveats

- Upstream Java repository appears inactive since 2019; do not assume modern Android/Gradle compatibility from the README alone.
- The old build uses deprecated Gradle/JCenter-era configuration.
- `rivescript-core` has at least an SLF4J API dependency in the upstream build; dependency and APK-size impact must be measured.
- UTF-8 was described upstream as experimental. Polish diacritics, punctuation stripping and ASR-normalized text need explicit tests.
- RiveScript normally produces reply text. In CallBridge, raw RiveScript reply text must **not** become authority or direct TTS output.
- Object handlers/macros are too powerful for the intended safety boundary and should be disabled/not registered unless a future narrowly-scoped audited adapter requires one.

### Preferred CallBridge adaptation

Do not start by allowing `.rive` scripts to freely generate sentences. Use RiveScript, if selected, primarily as a matcher/context engine.

Example concept:

```text
+ dzień dobry
- rule:GREETING

+ proszę powtórzyć
- rule:ASK_REPEAT

+ tak
% czy termin jutro pasuje
- rule:CONFIRM_TIME
```

The adapter parses only the restricted result form (`rule:<existingRuleId>`), rejects everything else fail-closed, and passes the id to the existing deterministic CallPlan validator.

Reviewed speech variants remain CallBridge-owned data, not arbitrary script output.

### Required RiveScript spike before adoption

1. Add `rivescript-core` only in an isolated host/Android build experiment; do not wire it into live calls.
2. Verify current AGP/Gradle/R8 compatibility and min/target API behavior.
3. Measure incremental APK size, cold initialization time and steady RAM on S22.
4. Verify loading scripts from packaged Android assets or a safe app-private representation; if the upstream API requires filesystem paths, decide whether copying assets to app-private storage is acceptable or whether a small custom loader is cleaner.
5. Run Polish UTF-8 tests with `ą ć ę ł ń ó ś ź ż`, punctuation and casing.
6. Test ASR-like noise/variants: missing punctuation, repeated words, short fillers and spacing.
7. Verify topic / `%Previous` behavior for bounded conversational context.
8. Verify deterministic behavior: same state + same transcript -> same rule id.
9. Verify no hidden random reply selection is enabled for authority-bearing routing.
10. Verify cancellation/thread-safety assumptions if matching can happen while the LLM supervisor is running.
11. Disable/not register object handlers and arbitrary Java callbacks.
12. Build a restricted adapter that returns only an existing rule id and matcher diagnostics.
13. Compare latency/accuracy against a tiny native CallBridge matcher on the same Polish test corpus.

Decision criterion: use RiveScript only if it materially reduces our parser/matcher work without importing stale build risk or a broader scripting authority surface.

## Repository candidate B — ChatScript

Repository: `ChatScript/ChatScript`

First-pass revision inspected: `9f5eec4736ba22bd992a6498c1e0052e2a795125` (`CS 14.1`, 2024-05-06).

### What is attractive

ChatScript is a mature rule/dialog engine with mechanisms very relevant to our problem:

- powerful semantic/pattern matching;
- topics and pending-topic management;
- rejoinders / follow-ups tied to prior conversation output;
- wildcard captures and concepts/ontologies;
- persistent interaction state;
- extensive debugging/testing tools;
- UTF-8 support;
- upstream explicitly lists Android among supported OS targets.

The most valuable design material for CallBridge is likely:

```text
README.md
WIKI/ChatScript-Basic-User-Manual.md
WIKI/ChatScript-Advanced-Pattern-Manual.md
WIKI/ChatScript-Advanced-Topic-Manual.md
SRC/patternSystem.cpp
SRC/topicSystem.cpp
SRC/common.h
```

In particular, study how ChatScript models:

- pattern specificity and ordering;
- negative terms / exclusions;
- wildcard capture;
- concepts/synonym classes;
- rejoinders after a previous response;
- topic activation/priority;
- deterministic testing/debug traces.

These ideas may be more valuable than embedding the whole engine.

### Integration concerns

- This is a large C/C++ codebase with dictionaries, tooling and server-oriented infrastructure; the repository itself is very large compared with what CallBridge needs.
- Upstream claims Android support and contains Android-specific preprocessor paths, but the first-pass search did not expose a modern first-class Android Gradle/JNI integration that we should blindly drop into the app. Treat Android embedding as an NDK/native integration question until proven otherwise.
- Full ChatScript would add a large amount of capability that CallBridge explicitly does not want scripts to own (I/O, general scripting, broader state, potentially network/system integrations).
- Native/JNI ownership, startup, data files, ABI packaging and crash isolation would add complexity next to an already safety-sensitive telephony application.

### License caution

Do **not** vendor the entire ChatScript tree or dictionaries merely because many source headers contain permissive MIT-style permission text.

The repository has component/data-specific licensing details. For example, dictionary license notes mention separate commercial licensing for some language POS-tagger use. Before copying any source/data, perform a path-by-path license audit of the exact files we would take.

Safer initial use: study algorithms/concepts and reimplement the small ideas we actually need unless a later audit proves a clean minimal embeddable subset.

### Required ChatScript research spike

1. Identify the smallest actual Android build target and required native/data files.
2. Determine whether there is a supported JNI API or whether a custom JNI bridge would be required.
3. Measure minimal native binary + required dictionary/data footprint for **Polish use without unnecessary English NLP features**.
4. Determine which pattern features are language-neutral and usable without licensed/large linguistic datasets.
5. Audit licenses for every proposed source/data path.
6. Extract a feature comparison against the custom matcher:
   - exact tokens/phrases;
   - synonym/concept sets;
   - optional tokens;
   - negation;
   - bounded wildcards/captures;
   - previous-turn/rejoinder matching;
   - topic/stage priority.
7. Prototype only if the minimal footprint and integration are clearly better than a small Kotlin implementation.

Expected role today: **reference implementation / source of mature dialogue ideas**, not the default dependency choice.

## Repository candidate C — KStateMachine

Repository: `KStateMachine/kstatemachine`

First-pass revision inspected: `d3d94c3e7e30de2abaa0c056a0d9c1ebea125362` (active on 2026-09-21).

License: Boost Software License.

### What it is good for

- Modern Kotlin Multiplatform library with Android support.
- Core has zero mandatory dependencies beyond Kotlin stdlib.
- Typed events, guards, nested states, parallel regions, history/pseudo states and persistence helpers.
- Actively maintained.

### What it is **not**

It is not a phrase matcher and does not replace the PhraseMatrix.

It becomes interesting only if conversational stage tracking becomes sufficiently complex that our own small product state becomes error-prone.

### Integration rule

Do not let KStateMachine become a second authority machine next to `CallWorkflow`.

Possible future use is limited to non-authority dialogue/navigation state, for example:

```text
INTRO -> PURPOSE -> INFORMATION -> WRAP_UP
```

while `CallWorkflow` still owns proposals, user-decision state, commitments and terminal outcome.

Before adoption, prove that the added state abstraction reduces complexity rather than duplicating the existing workflow.

## Recommended order

Do not interrupt the current Gate C final-STT integration to add any of these engines.

After clean final-STT / CallPlan selector wiring is `HOST_GREEN`:

1. Build a tiny native CallBridge `PhraseMatrix` reference implementation first so we have a baseline.
2. Run a bounded **RiveScript Java vs native PhraseMatrix** spike on the same Polish corpus.
3. Study ChatScript pattern/topic/rejoinder behavior and selectively port only high-value ideas.
4. Consider KStateMachine only if multi-stage dialogue state becomes complex enough to justify it.
5. Choose based on measured data, not feature count.

## Baseline native PhraseMatrix to compare against

Keep the baseline deliberately small:

```text
normalize(text)
 -> exact phrase map
 -> token/phrase aliases
 -> small deterministic pattern rules
 -> optional bounded fuzzy score
 -> existing ruleId + confidence
```

Suggested rule data:

```text
ruleId
stage/topic constraints
positive phrases/tokens
negative phrases/tokens
optional previous-rule constraint
confidence threshold
reviewed response variant class
```

No arbitrary executable script code is required for the baseline.

## Polish evaluation corpus

The spike should contain at least these groups, with real ASR-like variants:

- greetings: `dzień dobry`, `witam`, `halo`, combinations;
- acknowledgements: `dobrze`, `okej`, `rozumiem`, `mhm`;
- confirmation/rejection: `tak`, `zgadza się`, `nie`, `nie zgadzam się`;
- repeat/wait: `proszę powtórzyć`, `jeszcze raz`, `chwileczkę`, `proszę poczekać`;
- identity/purpose questions backed by authorized facts;
- negation collisions (`nie, dzień dobry...`, `nie zgadzam się` vs `zgadzam się`);
- multiple intents in one utterance;
- inflection and common word-order changes;
- missing diacritics (`dzien dobry`, `prosze`);
- common STT substitutions / repeated fragments;
- previous-turn context where `tak` only makes sense after a concrete question;
- unknown/ambiguous utterances that must not falsely match.

## Metrics / decision table

Record for each candidate:

| Metric | Why |
| --- | --- |
| exact/high-confidence hit rate | how much traffic avoids LLM |
| false-positive rate | safety-critical matcher quality |
| ambiguous/no-match rate | expected supervisor load |
| p50/p95 matching latency | fast-path benefit |
| cold init latency | call readiness impact |
| incremental APK/native/data size | deployment cost |
| steady + peak RAM | S22 pressure |
| Polish/UTF-8 behavior | language viability |
| deterministic replay | testability |
| previous-turn/context support | conversation usefulness |
| cancellation/thread behavior | safe parallel LLM supervision |
| license/integration burden | product maintainability |

## LLM supervisor relationship

The LLM is not the fallback author of arbitrary speech by default. It should act as a bounded semantic supervisor:

```text
transcript + bounded recent context + current stage + available rule ids
 -> suggest existing ruleId + confidence
 -> deterministic validator
 -> CallPlan / workflow
```

The fast matrix may answer trivial turns while the supervisor processes bounded context in the background. Shadow/speculative results remain quarantined. A newer transcript, resumed speech, cancellation, workflow state change or already-released deterministic response invalidates stale supervisor output.

This gives the LLM time to understand the conversation without forcing it to win the latency race on every turn.

## Exit criteria for this research item

The engine-selection spike is complete only when we have:

1. a native PhraseMatrix baseline;
2. a measured RiveScript Java experiment or a documented build incompatibility;
3. a documented ChatScript minimal-integration/license conclusion;
4. a decision whether KStateMachine is needed at all;
5. one recommended implementation path with measured S22/host costs;
6. explicit mapping into existing `CallPlan` rule ids and authority tests;
7. no new direct script -> TTS/TX or script -> commitment path.

Until then, do not vendor large third-party dialogue engines into production code.