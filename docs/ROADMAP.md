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

## Current slice — Orange Service Pack Explorer

Status: `ACTIVE / FIRST NODES AND EDGES PHYSICALLY VERIFIED`

Goal: make Orange the first fully decoded deterministic service pack.

Durable graph:

```text
service-packs/orange/service_tree.v1.json
```

### Current verified nodes

```text
orange.root
orange.root.reprompt
```

Max is a voice-first intent router. The verified root asks the caller to state what the call concerns.

### Current verified observed edges

`v115`:

```text
orange.root.list_capabilities
orange.root -> orange.root.reprompt
speech: Jakie sprawy możesz załatwić?
outcome: REPROMPT
```

`v123`:

```text
orange.root.invoice_status
orange.root -> orange.root.reprompt
speech: Chcę sprawdzić fakturę.
outcome: REPROMPT
```

The invoice edge verifies what actually happened, not that the invoice service was reached.

### Current service seed state

```text
orange.invoice.status
status: DISCOVERED
service route: NOT VERIFIED
last outcome: REPROMPT
```

Service-state progression:

```text
SEED -> DISCOVERED -> VERIFIED
```

Only physical Orange evidence promotes a route to `VERIFIED`.

### Root-acquisition policy

Physically observed exact ignorable branding/pre-roll fragments:

```text
orange
jakości orange
5g jakości orange
```

They are ignored only in bounded root acquisition with the tested Gate C safety conditions. `OBSERVE_ONLY` never consumes them as retryable pre-roll.

A strict bounded retry for Android `SpeechRecognizer ERROR_NO_MATCH(7)` also exists only for root acquisition after real detected speech + trailing-silence endpointing + bound Gate C + zero backend generation.

Do not replace these exact evidence-backed rules with generic semantic guessing.

### Current host checkpoint

Product/data checkpoint:

```text
5b0f599b98aefcef2279490cb14824f70e63ea17
```

Evidence:

```text
.agent/results/chatgpt-orange-service-tree-final-green-v125-20260921.json
```

Result:

```text
12 Orange tree/runner tests OK
bash scripts/verify_host.sh -> host_quality_gate_green=true
ORANGE_SERVICE_TREE_HANDOFF_HOST_GREEN=true
```

Documentation commits after that checkpoint do not change runtime behavior.

### Discovery execution loop

For each unverified Orange capability:

1. select one service seed;
2. define one exact reviewed non-committing utterance;
3. RED test the action/route contract;
4. minimal GREEN through the existing authority path;
5. targeted regressions + `bash scripts/verify_host.sh`;
6. only with explicit current-session authorization, execute one bounded call to exact target `510100100` with `OBSERVE_ONLY` follow-up;
7. persist the observed node/edge/outcome into the service tree;
8. if the branch reaches authentication, customer-data entry, payment, purchase, activation, tariff/contract change or other commitment barrier, record the barrier and continue another branch instead of finalizing it;
9. repeat.

The overall explorer should continue across branches rather than stop permanently at the first barrier. This does not authorize bypassing barriers or inventing credentials.

Public Orange capability information is a seed backlog only. Candidate low-risk domains include invoice/payment information, outages/technical support, PUK, Neostrada/Wi-Fi, roaming information, voicemail, SIM/eSIM, data usage, prepaid/top-up information, forwarding, My Orange and human handoff. None is route-verified until reproduced physically.

### Near-term engineering priorities

1. Preserve the `v123` invoice reprompt edge; do not overwrite history.
2. Try a separately reviewed simpler/operator-native invoice utterance based on concrete vocabulary evidence.
3. Add at least one new low-risk capability seed so exploration does not stall on invoice wording.
4. After several intents prove the pattern, consider a data-driven reviewed explorer action catalog instead of an ever-growing enum — only if it keeps exact reviewed speech, CallPlan validation, output approval and zero model generation.
5. Map repeat/back/recovery behavior when concrete nodes expose those options.
6. Build the eventual service resolver only after the verified catalog is meaningful.

### Gate C / Orange acceptance target

Orange service-pack acceptance requires:

- meaningful catalog of verified service routes;
- deterministic recovery/repeat/back/barrier behavior where available;
- exact target allowlist retained;
- no arbitrary model speech during route execution;
- natural-language user intent resolves only to an existing verified `service_id`;
- validated service route reaches the expected terminal node or explicit barrier;
- call always cleans up to `IDLE`.

Final intended flow:

```text
natural user request
 -> bounded resolver proposes existing service_id
 -> service_id validated against verified service pack
 -> deterministic Orange route
 -> expected service terminal/barrier
 -> explicit auth/confirmation only if required
```

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
