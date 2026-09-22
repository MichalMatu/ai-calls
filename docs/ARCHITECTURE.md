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
- application-owned output approval;
- application-owned `FactDisclosurePolicy` for personal-data disclosure.

TaskGraph, PhraseMatrix, parsers, ServicePacks, shadow observers, LLMs, encrypted storage and Skills may classify, persist or propose into those owners but do not replace them.

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

`TaskGraphContext` has only an internal read-only map snapshot used to form the bounded shadow observation from already-authoritative context. That accessor does not validate or commit new values.

### `TaskGraphApplyBridge.kt`

Explicit application-owned boundary from already validated candidate data to `TaskGraphCore.reduce()`.

Before constructing a typed event it re-checks:

- graph version and current state;
- candidate generation;
- application-owned transition-to-event mapping;
- transition legality from the current state;
- deterministic/supervisor provenance policy;
- allowed slot IDs;
- dynamic slot authorization;
- required slot presence;
- slot type/schema/constraint predicates;
- authority-bearing slot IDs as a defense-in-depth fail-closed guard.

Rejected candidates do not call the reducer. Accepted reductions expose only the new immutable snapshot, event record and effects as data. The bridge imports no workflow, output, telephony, IdentityVault or commitment owner and performs none of those side effects.

`TaskGraphApplyCandidate.fromSupervisor(...)` is only an adapter from an already `SupervisorProposalValidator`-accepted candidate into the application apply boundary. It does not upgrade candidate data into authority.

### `BookAppointmentSimulator.kt`

Host-only deterministic product simulator composing TaskGraph with existing `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and `FactDisclosurePolicy`. It is an evidence/simulation harness, not a telephony orchestrator.

### `AppointmentInterpreter.kt`

Reusable typed appointment interpretation for absolute dates, explicitly anchored relative dates/weekdays, concrete times/time ranges, concrete offer candidates, deterministic accept/reject/request-alternative dialogue acts and identity-field request IDs.

It has no hidden clock. Relative/weekday interpretation requires a caller-supplied reference date and fails closed without it. Parser/matcher output remains candidate data only and must still pass state/type/constraint/provenance/authorization validation before authoritative commit.

### `FactDisclosurePolicy.kt`

Defines typed identity-field/sensitivity/per-task snapshot boundaries. It owns disclosure decisions, not encrypted value storage.

### `PersistentIdentityVault.kt`

Host persistence core for durable identity values. It owns a versioned encrypted envelope/payload format around explicit ports:

```text
IdentityVaultBlobStorage
IdentityVaultAead
```

The host core provides:

- typed/redacted `IdentitySecretValue`;
- AEAD associated-data binding;
- versioned envelope/payload parsing;
- bounds checks and defensive copies;
- fail-closed decode/decrypt behavior;
- best-effort zeroing of transient plaintext byte arrays;
- explicit `DEVICE_BOUND_NO_BACKUP` policy.

This is not yet the Android production storage/key implementation. The next persistence slice must provide app-private atomic ciphertext storage plus a non-exportable Android Keystore key and authenticated encryption. Encrypted storage does not gain disclosure authority merely by holding a value.

### `DialogueFit.kt`

Defines explainable categorical fit signals/results plus bounded shadow observation/hypothesis types.

### `DialogueFitHysteresis.kt`

Caller-owned categorical sequence policy. Safety deterioration is immediate. Recovery to a better category requires configured consecutive evidence at the same target level; same/worse evidence or target changes reset pending recovery.

Hysteresis is non-authoritative. It cannot approve TaskGraph transitions, workflow changes, speech, dialing, disclosure or commitments. Callers must reset it at session/generation boundaries.

### `SupervisorProposalValidator.kt`

Fail-closed boundary from quarantined hypothesis to candidate data. It checks generation, transition scope, allowed non-secret slot IDs, authority-bearing slot names and confidence. An accepted `ValidatedSupervisorCandidate` is still not executable.

### Sequence-level evaluation corpus

`GateDSequenceEvaluationCorpusTest` composes existing owners without creating a product orchestrator. It covers repeated unknowns/recovery exhaustion, ambiguity recovery, hard-rejected and alternate offers, user rejection, unauthorized/high-sensitivity disclosure, cancel/takeover replay, stale supervisor rejection and clean hysteresis recovery.

The corpus is acceptance/evidence infrastructure only. It does not add runtime authority.

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

The public Android `LocalTextCallSession.create(...)` path currently does **not** bind a production shadow observer/provider. The activation seam is explicit/internal and host-tested so provider architecture was not broadened prematurely.

`TaskGraphApplyBridge` is also not automatically invoked by this session path. Keeping it separate prevents shadow observation from silently acquiring execution authority.

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

For an explicitly host-bound observer, the real selector follows:

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

The lifecycle passes hypothesis output through the existing `SupervisorProposalValidator`. Accepted output remains candidate data only.

## Explicit apply boundary

The application may separately compose:

```text
already validated deterministic/supervisor candidate
 -> TaskGraphApplyPolicy
 -> re-check current generation/state/mapping
 -> validate slot scope/schema/constraints/provenance/authorization
 -> typed TaskGraphEvent
 -> CustomTaskGraphCore.reduce()
 -> snapshot + event record + effects as data
 -> existing application owners, if and only if explicitly wired
```

No effect is executable merely because the reducer returned it. Product integration must delegate proposal, confirmation, commitment, target and output behavior to their existing owners rather than creating a generic effect executor.

## Hard authority invariant

The shadow/session lifecycle still does **not** automatically:

- call the apply bridge or `TaskGraphCore.reduce()`;
- create/apply a TaskGraph event from observer output;
- execute graph effects;
- mutate `CallWorkflow`;
- release model speech/TTS;
- dial or widen a target;
- resolve/disclose plaintext facts;
- approve a proposal;
- consume commitment authority.

The apply bridge itself may call the reducer only after its explicit validation boundary passes, and then only returns data. It owns none of the side effects above.

## Next implementation focus — Android IdentityVault persistence

The host vault contract/core is stable enough to move to the Android production adapter before a real call needs identity values.

Required boundary:

```text
PersistentIdentityVault
 -> app-private atomic ciphertext storage
 -> Android Keystore non-exportable key
 -> authenticated encryption / AAD
```

The Android adapter must preserve `DEVICE_BOUND_NO_BACKUP`, fail closed on corruption/key/version mismatch and avoid deprecated `EncryptedSharedPreferences` / `MasterKey` as the new persistence foundation.

After that boundary is green, move to reviewed product shadow/apply integration. Keep deterministic interpretation first, supervisor proposals bounded, `TaskGraphApplyBridge` explicit, graph effects as data and all real side effects owned by the existing workflow/output/disclosure/commitment authorities.

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
- current ADB reachability alone is not product proof;
- host-only Gate D contracts do not justify a live call;
- live-call authorization is session-scoped and must be freshly granted.
