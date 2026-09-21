# Roadmap

This is the authoritative execution plan. Detailed experiment history belongs in Git history and `.agent/results`.

Evidence levels:

- `HOST_GREEN` — deterministic host tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- `PRODUCT_READY` — proven, fail-safe and acceptable for normal use.

## Foundation

### Cellular media

Status: `DONE / PROVEN_S22 / FROZEN`

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Frozen checkpoint: `59b0505537a53306acdab6a2a66ca6eed2b3f1c0`.

Do not redesign this path during model/planning/dialogue work. See `docs/PHASE2D_FREEZE_2026-09-18.md`.

### Local speech/text boundary

Status: `DONE / PROVEN_S22`

Established owners:

- `LocalSpeechTextPipeline` — STT/TTS lifecycle + outer generation cancellation;
- `TextCallTurnController` — complete text generation/candidate approval + controller generation invalidation;
- `TextOutputApprovalPolicy` — application-owned release decision.

### Endpointing

Status: `SIGNALS PROVEN_S22 / PRODUCT STATE MACHINE OPEN`

Real Orange evidence proved fixed capture durations and short trailing-silence thresholds are not correct IVR semantics. Resumed speech must invalidate an end candidate; a later stable end plus bounded hangover closes a turn; long watchdog is safety-only.

No new physical call is authorized by default.

## Completed gates

### Gate A — product readiness / prepared local text call

Status: `DONE / HOST_GREEN / PROVEN_S22` (off-call readiness)

`LocalTextCallReadinessCoordinator` validates task/target/speech/backend readiness and returns one-shot `PreparedLocalTextCall`. `LocalTextCallSession` consumes it without owning frozen telephony media.

### Gate B — general-purpose phone-local model sweep

Status: `DONE / PROVEN_S22 / FROZEN`

Decision: the tested general-purpose llama.cpp models on the current S22 are not the product direction. Preserve runtime/benchmark infrastructure only.

### Interactive ChatGPT relay

Status: `DONE / PROVEN_S22 / DEVELOPER-ONLY`

Benchmark infrastructure only; not a production/background backend.

### Edge Gallery + Agent Skills feasibility

Status: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`

Useful feasibility was proven, but the bounded live checkpoint exposed unacceptable process robustness/memory/first-decision behavior. Do not continue live Edge probe work by default.

## Active gate — Gate C / deterministic fast path

Status: `ACTIVE / CALLPLAN FINAL-STT WIRING HOST_GREEN / PHRASE MATRIX BASELINE NEXT`

Goal: common bounded turns execute deterministically while all authority remains application-owned. Matchers and language helpers may only propose bounded matches to already-authorized product data.

### Authority ownership

- `CallTask` — immutable task, constraints, preferences, authorized facts;
- `CallResolvedTarget` — concrete target, no allowlist expansion;
- `CallWorkflow` — progress, proposal/user-decision state, terminal outcome;
- `CallConfirmationPolicy` — deterministic typed-proposal evaluation;
- `CallCommitmentGate` — one exact one-shot commitment permit;
- application-owned output approval — final release before TTS/TX.

`CallPlan`, phrase matchers, models, helpers and Agent Skills do not replace these owners.

### CallPlan policy + final-STT product wiring — `HOST_GREEN`

| Slice | Status |
| --- | --- |
| deterministic CallPlan policy core | GREEN |
| `CallPlanTurnCoordinator` | GREEN |
| prepared-call CallPlan binding | GREEN |
| deterministic candidate text through existing approval | GREEN |
| session-owned consecutive-unknown state | GREEN |
| neutral `TextCallFinalTurnDispatcher` (`Generate` / `Candidate` / `Consumed`) | GREEN |
| stale backend callback invalidation on `Consumed` | GREEN |
| `CallPlanFinalTurnRouteMapper` | GREEN |
| optional final-STT route selector in `LocalSpeechTextPipeline` | GREEN |
| plan-bound session structured-result delivery | GREEN |
| no-plan/default `Generate` compatibility | GREEN |
| full canonical host gate | GREEN |

Latest evidence:

```text
.agent/results/chatgpt-gate-c-final-turn-mapper-green-v49-20260921.json
.agent/results/chatgpt-gate-c-final-stt-selector-green-v51-20260921.json
```

Final STT can now be routed before backend generation without introducing a second controller, approval path or workflow owner.

### Current slice — Phrase / Intent Matrix baseline

Status: `NEXT / HOST TDD`

First build the tiny native CallBridge baseline described in `docs/PHRASE_MATRIX_ENGINE_RESEARCH.md`:

```text
final STT
 -> normalize
 -> exact phrase / alias / bounded deterministic pattern match
 -> existing ruleId + confidence + matcher diagnostics
 -> validate against bound CallPlan
 -> existing CallPlan / workflow / output approval
```

Initial requirements:

1. classification-only output; no arbitrary response text;
2. deterministic same-input replay;
3. fail closed on unknowns, cross-intent collisions and negation ambiguity;
4. Polish UTF-8 plus missing-diacritic/ASR-like variants in the test corpus;
5. explicit previous-turn/stage constraints only where needed, with no authority ownership;
6. no third-party dependency in the baseline implementation;
7. after baseline GREEN, compare RiveScript Java against it on build compatibility, Polish behavior, latency, APK/RAM/startup cost and matcher quality;
8. study ChatScript for pattern/topic/rejoinder ideas before any native embedding decision;
9. use KStateMachine only if non-authority dialogue-stage complexity later warrants it;
10. matrix output and any future LLM supervisor remain behind existing CallPlan/workflow/output approval.

Useful metrics: matrix hit rate, false-positive rate, ambiguous/no-match rate, p50/p95 matching latency, LLM invocation rate, takeover rate, incremental APK/RAM/startup cost and deterministic replay.

Gate C exit: bounded product-session turns use deterministic plan data without general-purpose reasoning, common safe turns have a measured deterministic fast path, missing facts are never invented, structured actions never silently become speech/model fallback, and no matcher/model/helper grants itself authority.

## Later gates

### Gate D — bounded multi-turn real tasks

Status: `AFTER C`

Prove repeated turns, IVR recovery, interruption/cancellation, unknown-intent escalation, structured completion/outcome and explicit user-decision surfaces. Start with non-committing tasks.

### Gate E — local audio-model experiments

Status: `LATER`

Only after the text/product baseline is strong, evaluate mobile-feasible audio understanding / speech-to-speech candidates behind the same authority and frozen media boundaries.

## Preserved but deferred providers

- `LOCAL_PHONE_LLM` — experimental infrastructure;
- `EDGE_GALLERY` — frozen experiment;
- `LOCAL_MAC_LLM` — retained option;
- `OPENAI_TEXT` — preserved/deferred;
- `OPENAI_REALTIME_AUDIO` — preserved/frozen;
- `LOCAL_REALTIME_AUDIO` — future.

Do not put standard OpenAI API credentials on Android.

## Completion discipline

Every product behavior slice:

1. starts from fresh `main` + fresh daemon/binding;
2. uses TDD where deterministic behavior is testable;
3. runs targeted regressions;
4. runs `bash scripts/verify_host.sh`;
5. runs only the physical gate required by changed OEM/hardware behavior;
6. updates existing authoritative docs instead of adding status files;
7. leaves `main` clean and deletes temporary work branches.
