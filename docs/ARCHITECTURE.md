# Architecture

## Goal

AI Calls is a bounded autonomous phone-task engine for ordinary cellular calls. Carrier settings, appointments, reservations, service requests and read-only calls must share one task/dialogue/authority model rather than separate bots.

`docs/AUTONOMOUS_OPERATION_MODE.md` defines the normative physical-operation mode.

## Frozen media boundary

`CallMediaSessionCoordinator` and the Samsung/Shizuku implementation under `privileged-helper/` are `PROVEN_S22 / FROZEN`.

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before changing this layer.

## Runtime

```text
accepted authorization context
 -> exact target + constraints + authorized facts
 -> readiness + IDLE
 -> dial
 -> telephony RX
 -> STT
 -> PhraseMatrix / deterministic task state
 -> Gemma 4 bounded dialogue skill when useful
 -> live supervisor fallback when unresolved
 -> application output approval
 -> TTS/TX
 -> typed external-effect authority if external state must change
 -> factual external-success evidence
 -> independent external-state verification when available
 -> workflow completion
 -> cleanup
```

## Authorization context

The runtime requires an accepted application-owned authorization context for exact target/task/effect scope.

The target product supports a durable, scoped, revocable campaign grant so repeated retries inside an unchanged scope do not require redundant product prompts. The grant must bind target(s), task/effect set, account/SIM scope when relevant, revocation state, optional retry/expiry bounds and disclosure scope.

Chat prose, docs, ServicePacks, model output and connected hardware are not authority stores. A material scope widening remains fail-closed. External platform/tool controls are not bypassed by the application.

## Authority owners

- authorization context / durable campaign grant — exact user-authorized execution scope;
- `CallTask` — authorized goal, hard constraints, preferences and facts;
- `CallResolvedTarget` — exact target binding;
- `CallExternalEffectValidator` — deterministic effect/task/target binding;
- `CallCommitmentGate` — the single one-shot commitment store for typed external effects;
- `CallExternalEffectCompletionTracker` — post-consumption exact success-evidence completion;
- `CallWorkflow` / TaskGraph owner — workflow state and terminal completion;
- `FactDisclosurePolicy` — personal-data disclosure;
- application output approval — final text release before TTS/TX.

TaskGraph definitions, PhraseMatrix, CallPlan, Gemma, supervisor/ChatRelay, ServicePacks, parsers, matchers and diagnostics are proposal/evidence layers, not independent authority owners.

## Generic external effects

The generic commitment migration is implemented.

```text
accepted authorization context + CallTask + CallResolvedTarget + constraints + authorized facts
 -> typed CallExternalEffect candidate
 -> deterministic validation
 -> user-decision policy if needed
 -> one-shot CallCommitmentGate permit bound to the exact effect
 -> reviewed execution/speech
 -> exact permit-consumption evidence
 -> separate external-success evidence
 -> independent state verification when practical
 -> factual effect completion
 -> workflow completion
```

Keep these distinct:

```text
task authorization
 != candidate validation
 != permit issuance
 != permit consumption
 != external success
 != workflow completion
```

Current typed examples include `CallExternalEffect.BookAppointment` and `CallExternalEffect.SetService` (`CLIR=true` / `CLIR=false`). The existing appointment flow uses the same commitment store through compatibility adapters. Do not create task-specific gates such as `ClirCommitmentGate`.

## Dialogue ownership

```text
finalized STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma dialogue skill
 -> live supervisor fallback on unresolved/low-confidence cases
 -> application validation/output approval
 -> TTS/TX
```

Models may classify, reason conversationally and propose bounded data. They cannot widen target/task/effect scope, disclose unapproved facts, create a commitment permit, declare external success or complete a workflow.

A live supervisor fallback may continue the same authorized call when script/Gemma cannot progress. Recurrent fallback cases should later move into deterministic script/PhraseMatrix or bounded Gemma skills.

## Read-only calls

Read-only information gathering does not need commitment authority because it changes no external state. It still requires an accepted dial authorization context, exact target binding, readiness, output approval and bounded cleanup.

## ServicePack role

ServicePacks may contain terminology, known IVR nodes/edges, reviewed actions, parsers and evidence knowledge. They are not dialing or commitment authority.

## Identity

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval when policy requires it
 -> resolve plaintext late
```

Plaintext identity must not live in ServicePacks, TaskGraph definitions, normal evidence/logs or model context by default.

## Current acceptance boundary

The generic authority path and full no-call product chain are already host/S22-no-call proven. The active physical gate is `docs/G5_CLIR_ROUTE_DISCOVERY.md`, now operating as a multi-turn CLIR physical-acceptance loop rather than the historical single-turn discovery probe.

Current independent network-state evidence says caller ID is still not restricted, so CLIR enable is not yet accepted.

`HOST_GREEN` is not `PROVEN_S22`. Route discovery, permit consumption, dialogue wording and call termination are not external-success evidence.
