# Roadmap

This is the authoritative execution plan. Detailed experiment history belongs in Git history and `.agent/results`. Operational Orange procedure lives in `docs/ORANGE_MAPPING_RUNBOOK.md`.

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

Status: `ACTIVE / HOST_GREEN / LIVE DETERMINISTIC PATH PROVEN_S22 / ORANGE CHECKPOINTED`

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

Exact allowlisted target used for explicit physical proof:

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

Status: `CHECKPOINTED / LIVE EVIDENCE PROVEN_S22 / SERVICE ROUTES STILL DISCOVERED`.

Durable graph: `service-packs/orange/service_tree.v1.json`.

Checkpoint state after the 2026-09-22 mapping session:

- verified physical nodes: `orange.root`, `orange.root.reprompt`, `orange.activation.clarification_barrier`;
- 19 verified observed root edges;
- 16 service seeds, all `DISCOVERED`;
- no complete service route has `service_route_verified=true`;
- manual network selection reached the activation clarification barrier and is closed;
- all other currently closed reviewed diagnostic/informational wordings ended in a known root reprompt;
- latest physical evidence: `chatgpt-orange-caller-id-restriction-info-live-v264-20260922`;
- latest exact reviewed speech: `Jak działa zastrzeganie numeru?`;
- latest physical call used `backend_generate_calls=0`, then `OBSERVE_ONLY`, then cleanup to `IDLE`.

The last code/data persistence slice adds `orange.root.caller_id_restriction_info -> orange.root.reprompt` as a physically `VERIFIED` observed edge while `orange.caller_id.restriction_info` remains `DISCOVERED`.

### Generic service-intent resolver

Status: `HOST_GREEN`.

The `serviceintent/` layer implements a provider-neutral classifier boundary, bounded `ServiceRegistry`, fail-closed resolver and separate execution validator. It accepts only existing service IDs and keeps `DISCOVERED` routes blocked as `ROUTE_NOT_VERIFIED`.

```text
Mam problem z internetem w Orange
 -> orange.internet.problem
 -> registry validation
 -> ROUTE_NOT_VERIFIED
 -> no call / no speech / no commitment
```

### Next execution order

The next work session should follow this order without a separate paste-ready prompt:

1. Read `docs/HANDOFF_NEXT_CHAT.md` and `docs/ORANGE_MAPPING_RUNBOOK.md`, then fetch fresh `main` and fresh Local Agent daemon/binding state.
2. Confirm the stabilized checkpoint with the canonical host gate if it has not already been proven for the exact fresh HEAD.
3. Do not repeat any closed reviewed utterance from the current service tree.
4. Select one new low-risk informational/diagnostic seed only after reviewing the existing tree and official operator backlog.
5. Add it with RED -> minimal GREEN -> targeted tests -> full host gate.
6. Only when the current operator session explicitly authorizes the exact physical target, install the exact GREEN SHA and perform one bounded reviewed turn followed by `OBSERVE_ONLY`.
7. Persist physical evidence conservatively; a reprompt verifies only the observed edge, not the complete service route.
8. Stop and record barriers for auth, credentials, payment, purchase, activation/deactivation, tariff/contract/package changes, ticket creation or any other commitment.
9. Repeat one seed at a time until enough genuinely verified service routes exist for a natural-intent -> `service_id` -> deterministic route acceptance test.

Do not start the next session by refactoring frozen media or broadening the matcher. The repeated Orange action wiring may be made data-driven only if it becomes a demonstrated maintenance bottleneck and the same typed IDs, reviewed speech, CallPlan validation, output approval, exact target allowlist and zero-backend-generation invariants remain intact.

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
5. runs only the physical gate required by changed OEM/hardware/IVR behavior and only with current-session authorization;
6. persists physical Orange evidence into `service_tree.v1.json`;
7. updates existing authoritative docs instead of adding status files;
8. follows `docs/ORANGE_MAPPING_RUNBOOK.md` for Orange work;
9. leaves `main` clean and keeps `.agent` traffic only on `agent-control`.
