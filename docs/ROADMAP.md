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

Status: `ACTIVE / HOST_GREEN / FIRST CONTROLLED S22 PHYSICAL GATE NEXT`

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

### Native PhraseMatrix + bounded matcher — `HOST_GREEN`

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
| opt-in one-edit fuzzy rule with token-count guard | GREEN |
| exact/alias priority over fuzzy | GREEN |
| short fuzzy sources rejected | GREEN |
| fuzzy ties fail closed | GREEN |
| context-specific fuzzy ambiguity cannot fall through to generic fuzzy | GREEN |
| canonical host gate after matcher extension | GREEN |

Key evidence:

```text
.agent/results/chatgpt-gate-c-phrase-matrix-baseline-green-v55-20260921.json
.agent/results/chatgpt-gate-c-phrase-router-green-v59-20260921.json
.agent/results/chatgpt-phrase-matrix-previous-context-green-v64-20260921.json
.agent/results/chatgpt-session-phrase-context-green-v68-20260921.json
.agent/results/chatgpt-readiness-phrase-matrix-green-v70-20260921.json
.agent/results/chatgpt-android-readiness-binding-green-v72-20260921.json
.agent/results/chatgpt-phrase-matrix-fuzzy-green-v75-20260921.json
.agent/results/chatgpt-phrase-matrix-fuzzy-context-green-v77-20260921.json
```

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

### Controlled Gate C live diagnostic — `HOST_GREEN / PHYSICAL PROOF NEXT`

The first physical Gate C proof is intentionally tiny and non-committing.

Bounded scenario:

- exact operator-defined Orange allowlist target: `510100100`;
- one reviewed Orange greeting rule;
- only authorized spoken candidate: `Dzień dobry.`;
- no proposal rules and no completion rules;
- unknown/changed input fails closed to takeover;
- no model fallback.

Live wiring now uses:

```text
cellular RX
 -> local STT
 -> LocalTextCallSession
 -> PhraseMatrix
 -> CallPlan rule validation
 -> existing application-owned output approval
 -> local TTS
 -> cellular TX
```

Safety/evidence properties:

- `LocalPhoneLlmLiveCallProbeRequest` accepts Gate C only for `LOCAL_PHONE_LLM` diagnostics and exact target `510100100`;
- `DiagnosticProbeActivity` receives explicit `gate_c_fast_path` and `live_call_target` extras;
- `GateCFastPathSentinelBackend` forbids `generate()` as a successful path;
- the physical report must show `backend_generate_calls=0`;
- Python runner validates the Gate C report before declaring success;
- legacy provider mode remains available without Gate C binding.

Key evidence:

```text
.agent/results/chatgpt-gate-c-live-safety-green-v82-20260921.json
.agent/results/chatgpt-gate-c-live-fastpath-green-v84-20260921.json
.agent/results/chatgpt-gate-c-live-report-green-v86-20260921.json
.agent/results/chatgpt-gate-c-host-final-v96-20260921.json
```

`v96` passed targeted Gate C live/session/authority regressions, 6 Python runner tests, the full canonical `bash scripts/verify_host.sh`, and `:app:assembleDebug`. The debug APK exists and is ready for S22 installation.

### Current slice — first physical Gate C proof

Status: `NEXT / REQUIRES S22 CONNECTED`

No additional host implementation is required before this physical checkpoint.

Required pre-dial sequence:

1. exact direct USB S22 serial `RFCT70L7E8J` is present;
2. model is `SM-S906B` and API level is 36;
3. cellular call state is `IDLE`;
4. install fresh `app-debug.apk` built from fresh `main`;
5. prove Shizuku diagnostic path healthy;
6. confirm generated Gate C probe intent contains `gate_c_fast_path=true` and `live_call_target=510100100` and contains no `tel:` / dial action;
7. only after explicit current-session operator authorization, execute one bounded call to the exact allowlisted target;
8. require final report markers:
   - `gate_c_fast_path=true`;
   - `gate_c_call_plan_bound=true`;
   - nonblank final STT;
   - `approved_text=Dzień dobry.`;
   - `backend_generate_calls=0`;
   - nonzero TTS and telephony TX PCM;
   - bounded trailing-silence endpointing;
9. hang up and restore phone state in `finally`.

`.agent/results/chatgpt-gate-c-s22-predial-v93-20260921.json` is not valid physical evidence: the S22 was absent, and its command did not fail-fast, allowing later shell commands to mask the failed device assertion. Any successor physical task must use `set -euo pipefail` or equivalent first-failure preservation.

After this physical proof, continue with expanded Polish ASR-like corpus metrics and only then define the bounded LLM supervisor contract. The supervisor may suggest only an existing ruleId, cannot release speech or mutate authority itself, and stale speculative work must be invalidated by newer transcript/resumed speech/cancel/workflow state.

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
