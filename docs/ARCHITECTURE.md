# Architecture

## Goal

AI Calls is a bounded autonomous phone-task engine for ordinary cellular calls. Carrier settings, appointments, reservations, service requests and read-only calls must share one task/dialogue/authority model rather than separate bots.

## Frozen media boundary

`CallMediaSessionCoordinator` and the Samsung/Shizuku implementation under `privileged-helper/` are `PROVEN_S22 / FROZEN`.

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before changing this layer.

## Runtime

```text
fresh authorized task
 -> exact target + constraints + authorized facts
 -> readiness
 -> dial
 -> telephony RX
 -> STT
 -> PhraseMatrix / deterministic task state
 -> Gemma 4 bounded dialogue skill when useful
 -> supervisor fallback when unresolved
 -> application output approval
 -> TTS/TX
 -> typed external-effect authority if external state must change
 -> factual external-success evidence
 -> workflow completion
 -> cleanup
```

## Authority owners

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
CallTask + CallResolvedTarget + constraints + authorized facts
 -> typed CallExternalEffect candidate
 -> deterministic validation
 -> user-decision policy if needed
 -> one-shot CallCommitmentGate permit bound to the exact effect
 -> reviewed execution/speech
 -> exact permit-consumption evidence
 -> separate external-success evidence
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

Current typed examples include `CallExternalEffect.BookAppointment` and `CallExternalEffect.SetService` (`CLIR=true`). The existing appointment flow uses the same commitment store through compatibility adapters. Do not create task-specific gates such as `ClirCommitmentGate`.

## Dialogue ownership

```text
finalized STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma dialogue skill
 -> app-owned response when available
 -> supervisor fallback on unresolved/low-confidence cases
 -> application validation/output approval
 -> TTS/TX
```

Models may classify, reason conversationally and propose bounded data. They cannot widen target/task/effect scope, disclose unapproved facts, create a commitment permit, declare external success or complete a workflow.

## Read-only calls

Read-only information gathering does not need commitment authority because it changes no external state. It still requires fresh dial authorization, exact target binding, readiness, output approval and bounded cleanup.

G5 CLIR route discovery is intentionally read-only and separate from later `SET_SERVICE(CLIR=true)` execution.

## ServicePack role

ServicePacks may contain terminology, known IVR nodes/edges, reviewed actions, parsers and evidence knowledge. They are not dialing or commitment authority. `service_route_verified=false` must remain fail-closed for execution claims.

## Identity

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> resolve plaintext late
```

Plaintext identity must not live in ServicePacks, TaskGraph definitions, normal evidence/logs or model context by default.

## Current acceptance boundary

The generic authority path and full no-call product chain are already host/S22-no-call proven. The next physical gate is `docs/G5_CLIR_ROUTE_DISCOVERY.md`: one fresh-authorized, read-only Orange route-discovery call. Only after that evidence is understood may a separately authorized G5c use the generic effect lifecycle to attempt CLIR activation.

`HOST_GREEN` is not `PROVEN_S22`, and route discovery is not external-success evidence.