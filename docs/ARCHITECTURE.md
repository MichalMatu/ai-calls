# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to selectable AI engines while keeping these ownership boundaries separate:

1. cellular media;
2. speech conversion;
3. text generation / deterministic text routing;
4. task/dialogue authority;
5. user takeover and fail-safe cleanup.

Failure must move toward a normal human call. Counterparty speech, model text and helper/tool output never widen authority.

## Frozen media boundary

`CallMediaSessionCoordinator` owns one privileged RX+TX generation, bind/prepare/start ordering, heartbeat, endpoint leases, helper failure observation and whole-generation teardown.

Continuous PCM crosses the privilege boundary through transferred PFDs. Binder/AIDL is control only.

Internal format:

```text
signed PCM16LE
mono
16 kHz
```

Samsung implementation stays in `privileged-helper/`:

```text
RX: VOICE_DOWNLINK -> SamsungVoiceDownlinkCapture -> PFD
TX: PFD -> SamsungUplinkPipeSession -> SamsungCallAssistantTrack
    -> USAGE_CALL_ASSISTANT / TELEPHONY_TX
```

Stereo duplication exists only at the Samsung TX boundary. Before changing this layer, read `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Authority boundary

The existing domain model remains authoritative:

- `CallTask` / `CallConstraints` / `CallPreferences` / `authorizedFacts`;
- `CallResolvedTarget`;
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- application-owned output approval.

`CallPlan` references those owners; it is not a parallel authority store. `CallPlanEngine` can produce only typed `SAY`, `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, or `TAKE_OVER` decisions. `CallPlanTurnCoordinator` is the narrow product owner that applies typed proposal/completion decisions through the existing workflow and rejects task/target/state mismatch fail-closed.

## Prepared product session

Pre-dial ownership:

```text
CallWorkflow + explicit target authorization
 + optional CallPlan
 + optional PhraseMatrix (requires CallPlan)
        |
        v
LocalTextCallReadinessCoordinator
        |
        +-> STT/TTS preflight
        +-> selected backend readiness / exact identity / warm-up
        +-> task/target/plan consistency
        |
        v
PreparedLocalTextCall
        |
        v
LocalTextCallSession
```

`PreparedLocalTextCall` is a one-shot handoff. PhraseMatrix cannot be carried without a bound CallPlan. The Android readiness factories accept the same optional `CallPlan + PhraseMatrix` pair and pass it into the existing readiness owner.

`LocalTextCallSession` owns prepared dialogue state, including:

- the consecutive-unknown CallPlan counter;
- `previousValidatedRuleId` for bounded previous-turn matching.

The previous-rule value is sourced only from a successful validated `CallPlanDecision.ruleId()`. A raw matcher hit, unknown rule or fallback cannot become later-turn context. The session still does not own frozen telephony media.

## Speech and final-text boundary

Current Android speech pipeline:

```text
PCM input
 -> OnDeviceSpeechInput
 -> final transcript
 -> product-owned final-turn selector
      no plan: Generate
      plan + matrix hit: validate existing ruleId through CallPlan
      plan SAY: Candidate(exact text)
      plan structured action: Consumed
 -> TextCallFinalTurnDispatcher
 -> one TextCallTurnController
 -> TextOutputApprovalPolicy for released text
 -> LocalTtsSpeechOutput
 -> PCM output
```

`LocalSpeechTextPipeline` owns speech lifecycle and its outer generation gate. `TextCallTurnController` owns complete-text generation/candidate approval and controller-level generation invalidation. `LocalTextCallSession` owns plan/matrix selection and bounded dialogue context. CallPlan/workflow policy does not move into `localspeech`.

`TextCallFinalTurnDispatcher` remains neutral:

```text
Generate -> TextCallTurnController.submitUserText(finalTranscript)
Candidate(text) -> TextCallTurnController.submitCandidateText(exact text)
Consumed -> TextCallTurnController.cancel(); no text generation
```

`CallPlanFinalTurnRouteMapper` maps `SAY` to an exact candidate and maps `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, `TAKE_OVER` to `Consumed` while preserving the exact structured result.

This path is host-green and preserves exactly one controller, approval path and generation/cancellation lifecycle.

## Native PhraseMatrix fast path

The selected production matcher is the small native Kotlin `PhraseMatrix`.

Current host-green contract:

```text
final transcript
 -> NFKC/lowercase/punctuation-space normalization
 -> exact phrase / explicit alias lookup
 -> optional explicit previousRuleId constraint
 -> PhraseMatch(ruleId, confidence, matcherKind, variantClass?)
 -> PhraseMatrixProductTurnRouter
 -> CallPlanTurnCoordinator.handleSuggestedRuleId(...)
 -> existing workflow/output path
```

Properties:

- classification only, no arbitrary response text;
- deterministic replay;
- normalized collisions rejected fail-closed;
- unknown input returns no match;
- previous-turn matching receives context explicitly and owns no state;
- all rule ids are revalidated against the bound CallPlan before authority/workflow/output effects;
- rejected matcher ids do not poison later-turn context.

The next layer may add bounded deterministic fuzzy/pattern matching, but only with explicit false-positive/negation guards and the same classification-only contract.

## Matcher engine decision

Host spikes selected the native matcher over importing a dialogue engine:

- native PhraseMatrix: ~11.85 ms init, ~0.815 us average match;
- RiveScript Java: ~46.56 ms init/sort, ~88.85 us average reply, +134,132 B debug APK and `slf4j-api`;
- RiveScript proved UTF-8/previous-turn feasibility but can emit arbitrary reply text, so it remains reference-only;
- ChatScript remains design reference for pattern/topic/rejoinder ideas because full C++/JNI/data integration is too broad for the current need;
- KStateMachine is deferred unless non-authority stage tracking becomes complex enough to justify it.

These are host measurements, not S22 performance claims.

## Service intent resolver boundary

Status: `HOST_GREEN`. Generic code lives in `serviceintent/` and is intentionally independent of Orange, telephony and speech output.

```text
natural user text + bounded catalog
 -> UserIntentClassificationModel
 -> existing service_id or null + confidence
 -> ModelBackedUserIntentResolver fail-closed checks
 -> ServiceRegistry revalidation
 -> ServiceIntentExecutionValidator
 -> existing authority owners
```

Unknown IDs, cross-pack IDs, low confidence, stale generations and authority-bearing metadata such as speech/action/target are rejected. A `DISCOVERED` service may classify but yields `ROUTE_NOT_VERIFIED`; only a `VERIFIED` route may become eligible, and existing application authority must separately allow execution. The resolver never dials, speaks or mutates workflow.

Provider implementations can later be local, OpenAI, Gemma or deterministic without changing service-pack or validator semantics.

## Bounded rule supervisor — deferred

A later supervisor may suggest an existing CallPlan rule id plus confidence from bounded context. It remains classification-only and must pass deterministic CallPlan/workflow/output-approval authority.

## Provider boundary

Text providers remain selectable infrastructure:

- `LOCAL_PHONE_LLM` — preserved experiment;
- `EDGE_GALLERY` — frozen experimental provider;
- `LOCAL_MAC_LLM` — retained option;
- `OPENAI_TEXT` — preserved/deferred.

`OPENAI_REALTIME_AUDIO` is preserved/frozen and `LOCAL_REALTIME_AUDIO` is future work. Provider selection never changes task, target, confirmation, commitment, output approval or TAKE OVER authority.

## Endpointing

Real Orange IVR evidence proved that short fixed trailing silence and a single recognizer end event are not valid universal turn boundaries:

```text
speech/begin -> cancel pending END
recognizer END -> candidate end
resumed speech/partial growth -> cancel candidate
stable later END + bounded hangover -> final turn
long watchdog -> safety only
```

Partial/speculative inference may prepare work but never creates authority or early TX. Changed/resumed speech invalidates stale work.

## Diagnostics separation

Diagnostic probes are evidence drivers only. Do not grow `DiagnosticProbeActivity`, `MainActivity`, `LocalPhoneLlmLiveCallProbe`, Edge harnesses or the ChatGPT relay into product session orchestrators.

Keep the frozen media coordinator cohesive. Do not refactor physically proven Samsung media merely for line count or stylistic cleanup.

## TAKE OVER invariant

Every engine preserves local-first cancellation:

```text
stop accepting/releasing AI output
 -> abort local telephony media generation
 -> stop speech/audio workers
 -> invalidate active text/model generation
 -> best-effort cancel remote/local inference
```

The first steps never wait for model/network acknowledgement.

## Evidence rule

`HOST_GREEN` never implies `PROVEN_S22`. Host-only matcher/product-routing changes require physical S22 evidence only when a later slice actually changes or exercises Android/OEM behavior.
