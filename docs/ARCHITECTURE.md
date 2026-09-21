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

Stereo duplication exists only at the Samsung TX boundary.

Before changing this layer, read `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Authority boundary

The existing domain model remains authoritative:

- `CallTask` / `CallConstraints` / `CallPreferences` / `authorizedFacts`;
- `CallResolvedTarget`;
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- application-owned output approval.

`CallPlan` references those owners; it is not a parallel authority store.

`CallPlanEngine` is deterministic policy logic. It can produce only typed `SAY`, `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, or `TAKE_OVER` decisions. It does not dial, mutate workflow state, authorize commitments or touch media/TTS/TX.

`CallPlanTurnCoordinator` is the narrow product owner that applies typed proposal/completion decisions through the existing `CallWorkflow` methods and rejects task/target/state mismatch fail-closed.

## Prepared product session

Pre-dial ownership:

```text
CallWorkflow + explicit target authorization + optional CallPlan
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

`PreparedLocalTextCall` is a one-shot handoff. `LocalTextCallSession` owns the prepared backend/session dialogue state, including the consecutive-unknown CallPlan counter. It still does not own frozen telephony media.

## Speech and final-text boundary

Current Android speech pipeline:

```text
PCM input
 -> OnDeviceSpeechInput
 -> final transcript
 -> TextCallTurnController.submitUserText(...)
 -> backend complete text
 -> TextOutputApprovalPolicy
 -> LocalTtsSpeechOutput
 -> PCM output
```

`LocalSpeechTextPipeline` owns speech lifecycle and its outer generation gate. `TextCallTurnController` owns complete-text generation/candidate approval and controller-level generation invalidation.

Do not duplicate either owner in the session.

### Neutral final-turn dispatcher — host-green checkpoint

`TextCallFinalTurnDispatcher` is the neutral seam prepared for final-STT integration. It knows nothing about CallPlan or Android speech.

```text
TextCallFinalTurnRoute.Generate
  -> TextCallTurnController.submitUserText(finalTranscript)

TextCallFinalTurnRoute.Candidate(text)
  -> TextCallTurnController.submitCandidateText(exact text)

TextCallFinalTurnRoute.Consumed
  -> TextCallTurnController.cancel()
  -> no text generation
```

`Consumed` exists specifically to invalidate stale backend/controller callbacks after a structured product decision.

This dispatcher is `HOST_GREEN` but is **not yet wired into `LocalSpeechTextPipeline.onFinalTranscript`**.

## CallPlan product routing checkpoint

Host-green components now separate decision ownership from text release:

```text
final transcript
 -> CallPlanTurnCoordinator
 -> typed CallPlanTurnResult
 -> product routing
```

Existing `CallPlanTextOutputRouter` / `CallPlanProductTurnRouter` prove that only `SAY` may enter exact candidate approval; `ASK_REPEAT`, `PROPOSAL`, `COMPLETE` and `TAKE_OVER` remain structured and do not invent speech.

The next integration should map those structured results into the neutral final-turn route:

```text
SAY -> Candidate(exact text)
ASK_REPEAT / PROPOSAL / COMPLETE / TAKE_OVER -> Consumed + structured callback
```

Only after that host mapper is green should `LocalSpeechTextPipeline` receive an optional route selector. The default/no-plan path must remain `Generate`.

## Phrase / Intent Matrix fast path + LLM supervisor

A high-value follow-on architecture is a **local deterministic phrase/intent matrix in front of general-purpose LLM reasoning**. The purpose is not only lower latency: common conversational turns should be resolved immediately while a bounded LLM supervisor has time to warm up, accumulate context, and enter only when a turn is ambiguous or materially important.

Target composition:

```text
final STT
   |
   v
normalize / classify locally
   |
   v
Phrase / Intent Matrix
   |------------------------------|
   | high-confidence known turn   | unknown / ambiguous / important turn
   v                              v
exact CallPlan rule /         bounded local LLM supervisor
approved response variant       -> suggest existing intent/ruleId only
   |                              |
   +--------------+---------------+
                  v
         CallPlan / workflow policy
                  v
       application-owned approval
                  v
                 TTS
```

Suggested layers:

1. **Exact normalized matrix** — fastest path for greetings, acknowledgements, repeat requests and other common phrases.
2. **Deterministic fuzzy matcher** — bounded matching for harmless wording variants without invoking an LLM.
3. **LLM supervisor** — receives bounded conversation context and may propose an existing intent/rule/ruleId; it does not create facts, targets, actions, commitments or authority.

Typical matrix entries may include intents such as `GREETING`, `ACK`, `CONFIRM`, `REJECT`, `ASK_REPEAT`, `WAIT`, `ASK_NAME`, `ASK_PURPOSE`, and other task-specific CallPlan rules. Sensitive or committing actions remain outside generic phrase matching and continue through the existing typed policy/workflow gates.

### Deterministic response variation

Natural variation should not require free-form generation. Each safe intent may carry a small finite allowlist of reviewed response variants, for example:

```text
GREETING:
  - "Dzień dobry."
  - "Dzień dobry, słucham."
```

A `temperature`-like product setting may control the size of the eligible variant set, but selection should remain deterministic/testable, for example from a stable seed such as `callId + turnIndex + intent`. The matrix must never generate arbitrary new text merely to sound less repetitive.

### LLM gets time without becoming the turn owner

The matrix fast path intentionally gives the LLM "breathing room": while trivial turns are answered locally in milliseconds, the supervisor can maintain or refresh a bounded semantic view of the conversation and be ready for later key moments. This is useful only if the authority boundary remains strict:

- matrix output is still subject to CallPlan/workflow/output approval;
- LLM output is quarantined until validated against current final transcript and current plan state;
- an LLM suggestion cannot retroactively replace an already-approved deterministic turn;
- resumed/changed speech, cancellation, workflow change or newer context invalidates stale supervisor work;
- the LLM should be invoked selectively rather than on every turn.

The desired steady state is therefore **deterministic first, LLM on demand**, not "LLM writes every response". This architecture should reduce perceived latency, model invocation rate, RAM/thermal pressure and hallucination surface while reserving model capacity for genuinely contextual turns.

Useful future metrics include matrix hit rate, deterministic/fuzzy false-match rate, LLM invocation rate, p50/p95 response latency, takeover rate, and the fraction of important turns that required supervisor help.

## Provider boundary

Text providers for local STT/TTS remain selectable infrastructure:

- `LOCAL_PHONE_LLM` — preserved experiment;
- `EDGE_GALLERY` — frozen experimental provider;
- `LOCAL_MAC_LLM` — retained option;
- `OPENAI_TEXT` — preserved/deferred.

`OPENAI_REALTIME_AUDIO` is preserved/frozen and `LOCAL_REALTIME_AUDIO` is future work.

Provider selection never changes task, target, confirmation, commitment, output approval or TAKE OVER authority.

## Endpointing

Real Orange IVR evidence proved that short fixed trailing silence and a single recognizer end event are not valid universal turn boundaries. The intended endpoint state is:

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

`HOST_GREEN` never implies `PROVEN_S22`. Host-only CallPlan/product routing changes require physical S22 evidence only when a later slice actually changes or exercises Android/OEM behavior.
