# Architecture

## Goal

Bridge an ordinary cellular call on the Samsung S22+ to a bounded autonomous task engine that can complete many kinds of phone tasks without turning each service into a separate bot.

The same architecture should support carrier settings, clinic bookings, reservations, service requests and read-only information calls.

## Stable frozen media boundary

`CallMediaSessionCoordinator` and the Samsung implementation under `privileged-helper/` remain `PROVEN_S22 / FROZEN`.

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching this layer.

## Core task authority

Current stable owners:

- `CallTask` — user-authorized task, hard constraints, preferences and authorized facts;
- `CallResolvedTarget` — exact target;
- `CallWorkflow` — proposal/user-decision state and terminal outcome;
- `CallConfirmationPolicy` — deterministic decision policy;
- `CallCommitmentGate` — one-shot commitment permit;
- `FactDisclosurePolicy` — personal-data disclosure;
- application-owned output approval — final speech release.

TaskGraph, PhraseMatrix, CallPlan, Gemma, supervisor/ChatRelay, ServicePacks, parsers and storage are bounded data/proposal layers. They do not replace authority owners.

## Generic phone-task model

The intended product flow is:

```text
CallTask
 + exact target
 + constraints/preferences
 + authorized facts
 -> dialogue
 -> typed candidate external effect
 -> deterministic application validation
 -> user-decision policy when needed
 -> one-shot commitment permit bound to exact effect
 -> reviewed execution/speech
 -> exact consumption evidence
 -> external success evidence
 -> factual workflow completion
```

A typed external effect is the next architectural abstraction. `ExternalEffectCandidate` is a descriptive name until the preimplementation audit chooses the narrowest concrete API.

Examples:

```text
SET_SERVICE(CLIR=true)
BOOK_APPOINTMENT(time=..., price=..., provider=...)
CANCEL_RESERVATION(reference=...)
READ_ONLY_QUERY(topic=...)
```

Read-only effects require no commitment permit because they do not change external state.

Detailed design: `docs/GENERIC_PHONE_TASK_AUTHORITY.md`.

## Existing appointment Gate D

The existing `BOOK_APPOINTMENT` Gate D path is `DONE / HOST_GREEN / PROVEN_S22 / MERGED` and remains the compatibility baseline.

Its proven invariant stays:

```text
permit issued
 != permit consumed
 != business success confirmed
```

The current implementation binds `CallCommitmentGate` to appointment-shaped `CallProposal`. The next refactor must generalize that commitment subject **without weakening or rewriting the proven appointment semantics**.

Do not create a parallel CLIR authority system.

## User authorization and confirmation

A fresh user task grants only the scope it explicitly contains.

If the user's current instruction already exactly authorizes a concrete effect, the product should not invent a redundant second confirmation merely because execution happens later in the call.

If dialogue negotiates materially new terms not already authorized, application-owned policy decides whether explicit user confirmation is required.

Gemma and supervisor never decide whether they have authority to commit.

## Dialogue resilience

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM: deterministic task path
 -> unresolved/ambiguous/cold: Gemma 4 bounded dialogue-skill classifier
 -> app-owned reviewed response
 -> error / low confidence / TAKE_OVER: supervisor/ChatRelay fallback
 -> application output approval
 -> TTS/TX
```

Gemma may emit bounded dialogue decisions such as skill/confidence/reason. Supervisor fallback may reason about the conversation, but its output remains candidate text/data until application validation/output approval.

## Gemma runtime

Current local provider:

```text
provider=LOCAL_GEMMA_4
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
runtime=LiteRT-LM
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Direct inference, SAF import, verified atomic activation, semantic readiness and the full reviewed network acquisition are `HOST_GREEN / PROVEN_S22`.

Model lifecycle is not the current product gate. Do not reopen it without a new concrete requirement/root cause.

## Identity and disclosure

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

Plaintext identity does not belong in TaskGraph definitions, ServicePacks, ordinary logs/evidence or model context by default.

## ServicePack role

A ServicePack may provide terminology, IVR hints, typed parsers/effect adapters and success-evidence knowledge.

It must not become:

- dialing authority;
- disclosure authority;
- commitment authority;
- a brittle exact-phrase script that replaces the dialogue engine.

Orange exact phrase mappings remain acceptance fixtures only.

## Full target runtime

The intended integrated runtime is:

```text
fresh authorized task
 -> product readiness
 -> dial exact target
 -> telephony RX
 -> STT
 -> deterministic task state / PhraseMatrix
 -> Gemma when useful
 -> supervisor fallback when unresolved
 -> application-owned output approval
 -> TTS/TX
 -> generic external-effect authority when state change is requested
 -> success evidence
 -> factual completion
 -> hangup/cleanup
```

## Hard authority invariant

Neither model, supervisor, TaskGraph, ServicePack, matcher, parser, storage, synthetic ingress nor diagnostic tooling may independently:

- dial or widen a target;
- widen task/effect scope;
- disclose plaintext identity;
- release speech/TTS;
- create commitment authority;
- infer external success;
- complete the workflow.

## Verification boundary

`HOST_GREEN` is not `PROVEN_S22`.

The next code scope is the generic commitment/effect abstraction and its compatibility with existing `BOOK_APPOINTMENT`. It should be proven synthetically/no-call before a new live acceptance call.

Every new real call requires fresh authorization for the exact target/number and task in the current chat.
