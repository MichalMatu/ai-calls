# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a bounded autonomous task engine while keeping ownership explicit across cellular media, speech conversion, deterministic dialogue routing, task/service knowledge, task/workflow authority, optional bounded supervisor observation/proposals, identity disclosure authority, takeover and fail-safe cleanup.

Counterparty speech, model output, ServicePack data, parsers and diagnostic tools never widen authority by themselves.

## Frozen media boundary

`CallMediaSessionCoordinator` and the Samsung implementation under `privileged-helper/` remain `DONE / PROVEN_S22 / FROZEN`.

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching this layer. Gate D work must not redesign media merely to simplify task-engine integration.

## Authority owners

The following remain authoritative:

- `CallTask`, including constraints/preferences and task scope;
- `CallResolvedTarget`;
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- application-owned output approval.

TaskGraph, PhraseMatrix, parsers, ServicePacks, shadow observers, LLMs and Skills may classify or propose into those owners but do not replace them.

## Product knowledge/data layers

### TaskGraph

TaskGraph owns bounded conversational micro-state: typed state/event/transition IDs, legal state-compatible transitions, pure guards, validated non-secret dialogue slots, bounded recovery, orchestration state, terminal state, effects as returned data and versioned replay evidence.

TaskGraph does **not** own target authorization, telephony execution, arbitrary speech approval, identity plaintext, disclosure permission, user-confirmation authority or commitment permits.

### ServicePack

ServicePack describes a specific service/counterparty environment: known prompts, nodes/edges, reviewed actions, barriers, evidence and future freshness metadata. Orange remains the first persistent evidence-backed ServicePack. A ServicePack does not authorize a task or commitment.

### IdentityVault and authorized facts

Keep three layers distinct:

```text
IdentityVault
  durable encrypted values

AuthorizedFactSnapshot
  typed field IDs available/authorized for this task

DialogueState / TaskGraph context
  transient validated non-secret facts learned in this call
```

A value existing in the vault is not permission to disclose it. `FactDisclosurePolicy` is application-owned. Plaintext identity values should be resolved as late as practical and stay outside supervisor context by default.

## TaskGraph engine decision

The production Gate D core is the minimal application-owned custom reducer: `CustomTaskGraphCore`.

KStateMachine was evaluated as a spike candidate and is not carried as a production runtime/dependency. Do not reopen this decision without new concrete capability evidence.

## Current Gate D host components

### `TaskGraphCore.kt`

Typed IDs/state kinds, immutable snapshots/context, pure guarded transitions, stale generation/state/version checks, bounded recovery, effects-as-data and deterministic versioned replay.

`TaskGraphContext` now has only an internal read-only map snapshot used to form the bounded shadow observation from already-authoritative context. That accessor does not validate or commit new values.

### `BookAppointmentSimulator.kt`

Host-only deterministic product simulator composing TaskGraph with existing `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and `FactDisclosurePolicy`. It is an evidence/simulation harness, not a telephony orchestrator.

### `FactDisclosurePolicy.kt`

Defines typed identity-field/sensitivity/per-task snapshot boundaries. It owns disclosure decisions, not encrypted value storage.

### `DialogueFit.kt`

Defines categorical fit signals/results plus bounded shadow observation/hypothesis types. The first policy is intentionally categorical; numeric tuning/hysteresis must come from simulator/eval evidence.

### `SupervisorProposalValidator.kt`

Fail-closed boundary from quarantined hypothesis to candidate data. It checks generation, transition scope, allowed non-secret slot IDs, authority-bearing slot names and confidence. An accepted `ValidatedSupervisorCandidate` is still not executable.

## Prepared product session and Gate D binding

Current composition:

```text
CallWorkflow + target authorization
 + optional CallPlan
 + optional PhraseMatrix
 + optional TaskGraphDefinition
 + optional AuthorizedFactSnapshot
        |
        v
AndroidTextCallReadiness / LocalPhoneTextCallReadiness
        |
        v
LocalTextCallReadinessCoordinator
        |
        v
PreparedLocalTextCall
        |
        v
LocalTextCallSession
        |
        +-> existing deterministic CallPlan/PhraseMatrix routing
        +-> optional LocalTextCallGateDRuntime
        +-> optional host-only LocalTextCallGateDShadowLifecycle
```

`PreparedLocalTextCall` remains a one-shot ownership handoff. `LocalTextCallSession` is the Gate D product owner because it already owns finalized-turn deterministic dialogue context.

The public Android `LocalTextCallSession.create(...)` path currently does **not** bind a production shadow observer/provider. The completed activation seam is explicit/internal and host-tested so provider architecture was not broadened during this slice.

## Read-only Gate D runtime boundary

`LocalTextCallGateDRuntime` may:

- bind one `CallTask`, `TaskGraphDefinition` and optional `AuthorizedFactSnapshot` to the session;
- create `ShadowDialogueObservation` from an authoritative snapshot/context and one finalized transcript;
- expose only currently legal transition IDs;
- expose only authorized/available fact field IDs allowed in the current state, with high-sensitivity filtering;
- fail closed to no available fact IDs when the `AuthorizedFactSnapshot` generation does not match the TaskGraph snapshot generation;
- revalidate `ShadowDialogueHypothesis` through `SupervisorProposalValidator`.

It has no reducer, effect executor, workflow mutation, speech/TTS, target/dial, commitment or plaintext IdentityVault API.

## Finalized-turn shadow lifecycle

For an explicitly host-bound observer, the real selector now follows:

```text
STT final transcript
 -> existing PhraseMatrix / CallPlan deterministic interpretation
 -> deterministic route/result is fixed
 -> existing structured-result listener
 -> create one bounded Gate D observation from session snapshot/context
 -> quarantined observer work
 -> SupervisorProposalValidator
 -> categorical DialogueFit + redacted candidate diagnostics
 -> no authoritative mutation
```

`LocalTextCallGateDShadowLifecycle` owns a monotonically increasing session epoch. A newer finalized turn invalidates older queued shadow work. `cancel()` invalidates pending work; `close()` invalidates it and closes the shadow executor. Epoch checks occur before observer execution, after observer output, after proposal validation and before diagnostics publication.

Observer/validator exceptions are contained inside the shadow path. `LocalTextCallSession` also protects the deterministic selector with a fail-open-for-shadow/fail-closed-for-authority boundary: shadow activation failure cannot change the already selected PhraseMatrix/CallPlan route.

Ordinary `GateDShadowTurnDiagnostics` contains typed IDs, generations, validation status/reject reason and `DialogueFitResult`; it does not carry transcript text, task/fact plaintext, slot candidate values or model diagnostic values.

The lifecycle passes hypothesis output through the existing `SupervisorProposalValidator`. Accepted output remains candidate data only. Current first integration deliberately does not invent semantic deterministic-vs-shadow scoring; `DialogueFit` remains categorical diagnostic evidence.

## Hard authority invariant after shadow activation

The completed lifecycle still does **not**:

- call `TaskGraphCore.reduce()`;
- create/apply a TaskGraph event automatically;
- execute graph effects;
- mutate `CallWorkflow`;
- release model speech/TTS;
- dial or widen a target;
- disclose plaintext facts;
- approve a proposal;
- consume commitment authority.

This is the stop line of the current checkpoint.

## Next integration boundary — application-owned TaskGraph apply bridge

The next slice must be separate from the observer and begin with RED contracts.

Target:

```text
already validated candidate
 -> application-owned freshness + legal transition/event mapping check
 -> validate slot type/schema/constraints/provenance/authorization
 -> typed TaskGraph event
 -> CustomTaskGraphCore.reduce()
 -> effects as data
 -> existing workflow/proposal/confirmation/commitment/output owners
```

The bridge must re-check current generation/state at apply time. A `ValidatedSupervisorCandidate` is insufficient by itself: transition-to-event mapping and candidate validity remain application-owned. No effect is executable simply because the reducer returned it.

## Slot/fact extraction invariant

```text
extract candidate
 -> validate type/state/constraints/provenance/authorization
 -> commit to authoritative state only after validation
```

Parser/NLU/LLM confidence never makes a value authoritative on its own.

## TAKE OVER and failure invariant

Failure moves toward deterministic fallback, local recovery, takeover or safe stop. Local cancellation must not wait on model/network acknowledgement before stopping AI output/media generation and invalidating pending inference generations.

## Evidence rule

- `HOST_GREEN` is host evidence only;
- `PROVEN_S22` requires physical reproduction on the target phone;
- documentation or a host test never upgrades a hardware claim;
- host-only Gate D contracts do not justify a live call;
- live-call authorization is session-scoped and must be freshly granted.
