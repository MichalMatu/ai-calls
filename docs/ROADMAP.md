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

Do not redesign this path during model/planning/dialogue/service-pack work. See `docs/PHASE2D_FREEZE_2026-09-18.md`.

### Local speech/text boundary

Status: `DONE / PROVEN_S22`

Established owners:

- `LocalSpeechTextPipeline` — STT/TTS lifecycle + outer generation cancellation;
- `TextCallTurnController` — complete text generation/candidate approval + controller generation invalidation;
- `TextOutputApprovalPolicy` — application-owned release decision.

### Endpointing

Status: `SIGNALS PROVEN_S22 / PRODUCT STATE MACHINE OPEN`

Real Orange evidence proved that fixed capture durations and short trailing-silence thresholds are not complete IVR semantics. Resumed speech must invalidate an end candidate; a later stable end plus bounded hangover closes a turn; long watchdog is safety-only.

## Completed gates / frozen experiments

### Gate A — product readiness / prepared local text call

Status: `DONE / HOST_GREEN / PROVEN_S22` (off-call readiness)

### Gate B — general-purpose phone-local model sweep

Status: `DONE / PROVEN_S22 / FROZEN`

The tested general-purpose llama.cpp models on the current S22 are not the product direction. Preserve runtime/benchmark infrastructure only.

### Interactive ChatGPT relay

Status: `DONE / PROVEN_S22 / DEVELOPER-ONLY`

Benchmark infrastructure only; not a production/background backend.

### Edge Gallery + Agent Skills feasibility

Status: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`

Do not continue live Edge probe work by default.

## Active gate — Gate C / deterministic fast path

Status: `ACTIVE / HOST_GREEN / LIVE DETERMINISTIC PATH PROVEN_S22 / ORANGE SERVICE PACK EXPLORER ACTIVE`

Goal: common bounded turns execute deterministically while all authority remains application-owned. Matchers, language helpers and service-pack discovery tools may only classify/propose already-reviewed product data.

### Authority ownership

- `CallTask` — immutable task, constraints, preferences, authorized facts;
- `CallResolvedTarget` — concrete target, no allowlist expansion;
- `CallWorkflow` — progress, proposal/user-decision state, terminal outcome;
- `CallConfirmationPolicy` — deterministic typed-proposal evaluation;
- `CallCommitmentGate` — one exact one-shot commitment permit;
- application-owned output approval — final release before TTS/TX.

`CallPlan`, PhraseMatrix, models, helpers, service-pack explorers and Agent Skills do not replace these owners.

### Deterministic CallPlan + PhraseMatrix path

Status: `HOST_GREEN`

Completed product wiring includes:

- deterministic CallPlan policy core and coordinator;
- prepared-call CallPlan binding;
- final-STT selector before backend generation;
- neutral `Generate` / `Candidate` / `Consumed` dispatch;
- exact candidate text through existing output approval;
- structured actions remain structured;
- native `PhraseMatrix` exact/alias matching with fail-closed collision handling;
- explicit optional previous-rule context owned by the session only after validated decisions;
- bounded opt-in one-edit fuzzy matching with token-count/length/ambiguity guards;
- Android readiness binding for optional `CallPlan + PhraseMatrix`;
- no-plan/default path remains ordinary `Generate`.

Matcher direction remains native Kotlin `PhraseMatrix`; RiveScript/ChatScript are reference material only.

Do not broaden fuzzy matching to make live Orange prompts pass.

### Controlled Gate C live proof

Status: `DONE / PROVEN_S22`

Exact allowlisted target:

```text
510100100
```

Main physical proof:

```text
.agent/results/chatgpt-orange-explorer-live-v115-20260921.json
```

`v115` proved in one real Orange call:

```text
cellular RX
 -> local STT
 -> PhraseMatrix
 -> existing CallPlan rule validation
 -> application-owned approval
 -> local TTS
 -> cellular TX
 -> OBSERVE_ONLY next turn
 -> cleanup to IDLE
```

Required safety markers were present, including `gate_c_fast_path=true`, `gate_c_call_plan_bound=true`, `backend_generate_calls=0`, exact reviewed approved text and nonzero telephony TX.

Therefore do not repeat work whose sole purpose is proving the first deterministic live RX -> TX path.

## Current slice — Orange Service Pack + intent resolver

Status: `ACTIVE / HOST_GREEN / LIVE DETERMINISTIC PATH PROVEN_S22 / SERVICE ROUTES STILL DISCOVERED`.

Durable graph: `service-packs/orange/service_tree.v1.json`. Verified physical nodes are `orange.root` and `orange.root.reprompt`. Verified observed root edges include `list_capabilities`, `invoice_status`, `invoice_topic`, `internet_problem` and `roaming_info`. These edges verify observed transitions only.

Current service seeds `orange.invoice.status`, `orange.internet.problem` and `orange.roaming.info` remain `DISCOVERED`; their tested utterances returned the root reprompt, so no complete service route is claimed `VERIFIED`.

Latest bounded physical evidence is `chatgpt-orange-roaming-live-v151-20260922`: exact reviewed `Roaming.`, `backend_generate_calls=0`, passive `OBSERVE_ONLY`, known root reprompt and cleanup to `IDLE`.

### Generic service-intent resolver

Status: `HOST_GREEN`.

The new `serviceintent/` layer implements a provider-neutral classifier boundary, bounded `ServiceRegistry`, fail-closed resolver and separate execution validator. It accepts only existing service IDs and keeps `DISCOVERED` routes blocked as `ROUTE_NOT_VERIFIED`.

```text
Mam problem z internetem w Orange
 -> orange.internet.problem
 -> registry validation
 -> ROUTE_NOT_VERIFIED
 -> no call / no speech / no commitment
```

### Near-term priorities

1. Continue evidence-driven low-risk Orange mapping without overwriting old evidence.
2. Preserve strict edge-vs-service-route verification semantics.
3. Keep the current small reviewed action enum until it becomes a real bottleneck.
4. Add classifier provider/registry loading only behind the generic resolver boundary.
5. Keep auth/payment/commitment points as recorded barriers, not discovery shortcuts.
6. Use `docs/ORANGE_OVERNIGHT_MAPPING_PROMPT.md` only in a future explicitly authorized session; it is prepared, not running.

### Orange acceptance target

Orange is not complete until a meaningful catalog of service routes is physically verified with deterministic recovery/barrier behavior and natural user intent resolves only to existing verified service IDs before existing authority validates execution.

## Later gate — bounded multi-turn product tasks

Status: `PARTIALLY EXERCISED BY ORANGE EXPLORER / PRODUCT GENERALIZATION AFTER SERVICE-PACK BASELINE`

The Orange Explorer already physically exercises bounded multi-turn behavior (`SAY` then `OBSERVE_ONLY`), but general product multi-turn tasks still require broader recovery, interruption/cancellation, unknown-intent escalation, structured completion and explicit user-decision surfaces.

Do not redesign this separately while the same requirements can be learned from the Orange service-pack work.

## Later gate — local audio-model experiments

Status: `LATER`

Only after the deterministic text/service-pack baseline is strong, evaluate mobile-feasible audio understanding / speech-to-speech candidates behind the same authority and frozen media boundaries.

## Preserved but deferred providers

- `LOCAL_PHONE_LLM` — experimental infrastructure / Gate C diagnostic transport;
- `EDGE_GALLERY` — frozen experiment;
- `LOCAL_MAC_LLM` — retained option;
- `OPENAI_TEXT` — preserved/deferred;
- `OPENAI_REALTIME_AUDIO` — preserved/frozen;
- `LOCAL_REALTIME_AUDIO` — future.

Do not put standard OpenAI API credentials on Android.

## Completion discipline

Every product/service-pack behavior slice:

1. starts from fresh `main` + fresh daemon/binding;
2. uses TDD where deterministic behavior is testable;
3. runs targeted regressions;
4. runs `bash scripts/verify_host.sh`;
5. runs only the physical gate required by changed OEM/hardware/IVR behavior;
6. persists physical Orange evidence into `service_tree.v1.json`;
7. updates existing authoritative docs instead of adding status files;
8. leaves `main` clean and keeps `.agent` traffic only on `agent-control`.
