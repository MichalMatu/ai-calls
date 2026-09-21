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

## Active gate — Gate C / CallPlan v1

Status: `ACTIVE / HOST POLICY + HOST PRODUCT-WIRING CHECKPOINT GREEN / FINAL-STT INTEGRATION NEXT`

Goal: common bounded turns execute deterministically while all authority remains application-owned. A language helper may only propose a bounded match to already-authorized plan data.

### Authority ownership

- `CallTask` — immutable task, constraints, preferences, authorized facts;
- `CallResolvedTarget` — concrete target, no allowlist expansion;
- `CallWorkflow` — progress, proposal/user-decision state, terminal outcome;
- `CallConfirmationPolicy` — deterministic typed-proposal evaluation;
- `CallCommitmentGate` — one exact one-shot commitment permit;
- application-owned output approval — final release before TTS/TX.

`CallPlan`, models, helpers and Agent Skills do not replace these owners.

### Completed deterministic policy core — `HOST_GREEN`

- known authorized facts;
- missing-fact fail closed;
- bounded `ASK_REPEAT` / `TAKE_OVER`;
- typed predeclared `CallProposal`;
- typed predeclared `CallOutcome`;
- cross-kind collisions fail closed;
- helper suggestion restricted to one existing `ruleId`;
- immutable defensive copies / redacted ordinary rendering;
- authority regression coverage for workflow, policy, commitment and output approval.

### Completed product-wiring checkpoint — `HOST_GREEN`

| Slice | Status |
| --- | --- |
| `CallPlanTurnCoordinator` | GREEN |
| prepared-call CallPlan binding | GREEN |
| deterministic candidate text through existing approval | GREEN |
| `CallPlanTextOutputRouter` (`SAY` only) | GREEN |
| `CallPlanProductTurnRouter` | GREEN |
| session-owned consecutive-unknown state | GREEN |
| neutral `TextCallFinalTurnDispatcher` (`Generate` / `Candidate` / `Consumed`) | GREEN |
| stale backend callback invalidation on `Consumed` | GREEN |
| full canonical host gate | GREEN |

Latest checkpoint evidence:

```text
.agent/results/chatgpt-gate-c-session-fallback-state-green-v45b-20260921.json
.agent/results/chatgpt-gate-c-final-text-dispatcher-green-v47-20260921.json
```

### Current open gap

Current Android path still does:

```text
final STT
 -> TextCallTurnController.submitUserText(...)
 -> backend.generate(...)
 -> approval
 -> TTS
```

CallPlan is not yet automatically selected at this final-STT boundary.

### Next slice — exact start point

Host-only TDD first: map one structured `CallPlanTurnResult` to one `TextCallFinalTurnRoute`.

Required mapping:

```text
SAY -> Candidate(exact plan text)
ASK_REPEAT -> Consumed + structured result
PROPOSAL -> Consumed + structured result
COMPLETE -> Consumed + structured result
TAKE_OVER -> Consumed + structured result
```

Rules:

- no invented text;
- no plan-bound fallthrough to `Generate`;
- session remains owner of consecutive-unknown state;
- workflow mutations remain in coordinator/workflow;
- no Android speech/media change in this mapper slice.

After mapper GREEN:

1. add one optional final-turn route selector at the `LocalSpeechTextPipeline` final-transcript seam;
2. default/no-plan path remains behavior-compatible `Generate`;
3. plan-bound path selects `Candidate`/`Consumed` before backend generation;
4. keep one `TextCallTurnController`, one application approval path and one cancellation lifecycle;
5. keep CallPlan/workflow decisions outside `localspeech`;
6. prove stale callbacks cannot escape after consumed/cancelled turns;
7. targeted tests + `bash scripts/verify_host.sh`.

Physical S22 validation follows only after host integration is complete and only when explicitly authorized.

Gate C exit: bounded product-session turns use deterministic plan data without general-purpose reasoning, missing facts are never invented, structured actions never silently become speech/model fallback, and no model/helper grants itself authority.

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
