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

Status: `ACTIVE / CALLPLAN + NATIVE PHRASE MATRIX HOST_GREEN / BOUNDED MATCHER EXTENSION NEXT`

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

### Native PhraseMatrix baseline + product binding — `HOST_GREEN`

| Slice | Status |
| --- | --- |
| normalized exact phrase + explicit alias matcher | GREEN |
| collision/unknown deterministic fail-closed behavior | GREEN |
| matcher `ruleId` -> existing CallPlan coordinator | GREEN |
| explicit previous-rule constraints | GREEN |
| previous-rule context through product router | GREEN |
| session-owned `previousValidatedRuleId` | GREEN |
| PhraseMatrix through readiness/prepared call | GREEN |
| Android readiness factory `CallPlan + PhraseMatrix` binding | GREEN |
| canonical host gate after product binding | GREEN |

Key evidence:

```text
.agent/results/chatgpt-gate-c-phrase-matrix-baseline-green-v55-20260921.json
.agent/results/chatgpt-gate-c-phrase-router-green-v59-20260921.json
.agent/results/chatgpt-phrase-matrix-previous-context-green-v64-20260921.json
.agent/results/chatgpt-session-phrase-context-green-v68-20260921.json
.agent/results/chatgpt-readiness-phrase-matrix-green-v70-20260921.json
.agent/results/chatgpt-android-readiness-binding-green-v72-20260921.json
```

The product can now bind a native PhraseMatrix with the same CallPlan during Android readiness, carry it through `PreparedLocalTextCall`, and intercept final STT without introducing another controller, workflow owner or approval path.

### Matcher engine decision

Status: `DONE / NATIVE SELECTED`

Host comparison on the same small Polish corpus:

| Candidate | Init | Average match/reply | Incremental production cost | Decision |
| --- | ---: | ---: | --- | --- |
| native `PhraseMatrix` | ~11.85 ms | ~0.815 us | no third-party matcher dependency | SELECTED |
| RiveScript Java | ~46.56 ms | ~88.85 us | +134,132 B debug APK, `slf4j-api` | REFERENCE ONLY |
| ChatScript | not embedded | not benchmarked | large C++/JNI/data integration surface | DESIGN REFERENCE |
| KStateMachine | not needed | n/a | state abstraction only, not matcher | DEFERRED |

RiveScript did prove Polish UTF-8 and previous-turn support, but it can emit arbitrary reply text and its broader scripting surface is unnecessary for the current product boundary. These numbers are host-spike measurements, not S22 performance claims.

### Current slice — bounded native matcher extension

Status: `NEXT / HOST TDD`

Start from concrete corpus failures, not feature count:

```text
final STT
 -> normalize
 -> exact/alias PhraseMatrix
 -> optional bounded deterministic fuzzy/pattern rule
 -> existing ruleId + confidence + matcher diagnostics
 -> CallPlan/workflow/output approval
```

Requirements:

1. classification-only output; no arbitrary response text;
2. fail closed on ambiguous, negated and multi-intent inputs;
3. deterministic same-input/state replay;
4. every new positive test must have neighboring false-positive guards;
5. previous-turn context remains explicit and session-owned only after CallPlan validation;
6. no third-party matcher dependency unless later measurements show a clear need;
7. collect hit/no-match/false-positive and p50/p95 matcher metrics on an expanded Polish ASR-like corpus.

After the deterministic matcher is strong enough, define the bounded LLM supervisor contract: it may suggest only an existing ruleId, cannot release speech or mutate authority itself, and stale speculative work is invalidated by newer transcript/resumed speech/cancel/workflow state.

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
