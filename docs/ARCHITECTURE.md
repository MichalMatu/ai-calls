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
