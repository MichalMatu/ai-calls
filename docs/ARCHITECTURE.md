# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a bounded autonomous task engine while keeping ownership explicit across:

1. cellular media;
2. speech conversion;
3. deterministic dialogue routing;
4. task/service knowledge;
5. task/workflow authority;
6. optional bounded supervisor observation/proposals;
7. identity disclosure authority;
8. takeover and fail-safe cleanup.

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

TaskGraph owns bounded conversational micro-state:

- typed state/event/transition IDs;
- legal state-compatible transitions;
- pure guards;
- validated non-secret dialogue slots;
- bounded recovery;
- proposal/confirmation/commitment orchestration state;
- completion/failure/takeover terminal state;
- effects as returned data;
- versioned replay evidence.

TaskGraph does **not** own target authorization, telephony execution, arbitrary speech approval, identity plaintext, disclosure permission, user-confirmation authority or commitment permits.

### ServicePack

ServicePack describes a specific service/counterparty environment: known prompts, nodes/edges, reviewed actions, barriers, evidence and future freshness metadata.

Orange remains the first persistent evidence-backed ServicePack. A ServicePack does not authorize a task or commitment.

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

A value existing in the vault is not permission to disclose it.

`FactDisclosurePolicy` is application-owned and returns a typed `ALLOW / ASK_USER / DENY` decision from task/target/state/field/sensitivity/per-task authorization. Plaintext identity values should be resolved as late as practical and stay outside supervisor context by default.

Android persistence, when implemented, must use app-private ciphertext plus Android Keystore-protected non-exportable key material and authenticated encryption. Do not build a new vault on deprecated `EncryptedSharedPreferences` / `MasterKey` APIs.

## TaskGraph engine decision

The production Gate D core is the minimal application-owned custom reducer.

Current implementation: `CustomTaskGraphCore`.

Reasons:

- required semantics fit a small deterministic reducer;
- effects remain data rather than hidden runtime side effects;
- replay/evidence remain explicit application-owned records;
- no additional state-machine runtime/dependency is needed;
- existing authority owners remain outside the graph runtime.

KStateMachine was considered as a spike candidate but is not carried as the production runtime/dependency. Do not add a second parallel state-machine abstraction unless new evidence demonstrates a concrete missing capability that outweighs the extra runtime/maintenance surface.

## Current Gate D host components

### `TaskGraphCore.kt`

Provides:

- typed IDs and state kinds;
- immutable snapshots/context;
- pure guarded transitions;
- stale-generation/state/version checks;
- bounded recovery;
- effects-as-data;
- versioned event records;
- deterministic replay with mismatch rejection.

### `BookAppointmentSimulator.kt`

Host-only deterministic product simulator. It composes TaskGraph with existing `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and `FactDisclosurePolicy`.

It is an evidence/simulation harness, not a telephony orchestrator.

### `FactDisclosurePolicy.kt`

Defines typed identity-field/sensitivity/per-task snapshot boundaries. It owns the disclosure decision, not the actual encrypted value store.

### `DialogueFit.kt`

Defines explainable categorical fit signals/results plus bounded shadow observation/hypothesis types.

The first policy is intentionally categorical; numeric tuning/hysteresis must be based on simulator/eval evidence.

### `SupervisorProposalValidator.kt`

Fail-closed boundary from quarantined hypothesis to candidate data. It checks generation, transition scope, allowed non-secret slot IDs, authority-bearing slot names and confidence.

An accepted `ValidatedSupervisorCandidate` is still not an executable transition.

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
```

`PreparedLocalTextCall` remains a one-shot ownership handoff.

`LocalTextCallSession` is the correct Gate D product owner because it already owns finalized-turn dialogue context for the local deterministic path. Do not promote `DiagnosticProbeActivity`, Orange runners or other diagnostic harnesses into product orchestration.

## `LocalTextCallGateDRuntime` boundary

This runtime is deliberately **read-only**.

It may:

- bind one `CallTask`, `TaskGraphDefinition` and optional `AuthorizedFactSnapshot` to the session;
- create `ShadowDialogueObservation` from an already-authoritative snapshot + validated non-secret slots + one finalized transcript;
- expose only currently legal transition IDs;
- expose only authorized/available fact field IDs allowed in the current state, with high-sensitivity filtering;
- revalidate `ShadowDialogueHypothesis` through `SupervisorProposalValidator`.

It deliberately has no:

- `TaskGraphCore.reduce()` call;
- graph effect executor;
- workflow mutation API;
- speech/TTS release API;
- dial/target API;
- commitment API;
- plaintext IdentityVault API.

This separation is a hard invariant for the current checkpoint.

## Current finalized-turn path

Existing deterministic behavior remains:

```text
PCM input
 -> STT final transcript
 -> PhraseMatrix / CallPlan deterministic interpretation
 -> structured plan decision
 -> output approval / workflow handling
 -> TTS / telephony where allowed
```

Gate D context is now bindable to the same session, but the shadow observer is not yet automatically invoked by each finalized product turn.

## Next integration boundary

The next slice should activate observation, not authority.

Target:

```text
finalized transcript
 -> existing deterministic routing
 -> create bounded Gate D shadow observation
 -> quarantined observer
 -> validate hypothesis
 -> compare with deterministic evidence
 -> DialogueFit
 -> candidate/diagnostic result only
```

Required invariants:

- deterministic routing remains unchanged when Gate D is absent or observer fails;
- only finalized turns are observed;
- stale/generation mismatch fails closed;
- cancellation/session close invalidates pending observer work;
- observations contain field IDs/availability, not plaintext identity values;
- normal diagnostics avoid transcript/identity leakage;
- no automatic graph reduction in this slice;
- no model-generated speech/action/target/commitment authority.

Only after this observation/lifecycle boundary is host-green should a later explicit application-owned bridge map an already-validated candidate into a typed TaskGraph event and call the reducer.

## TaskGraph apply bridge — future boundary

When introduced, the bridge must be separate from the observer and must re-check:

- session/generation/state freshness;
- existing legal transition/event mapping;
- slot schema/types;
- user constraints/preferences;
- provenance/authorization;
- disclosure policy where identity is involved.

Then:

```text
validated candidate
 -> typed TaskGraph event
 -> CustomTaskGraphCore.reduce()
 -> effects as data
 -> existing workflow/proposal/confirmation/commitment/output owners
```

The reducer does not speak or commit by itself.

## Slot/fact extraction invariant

```text
extract candidate
 -> validate type/state/constraints/provenance/authorization
 -> commit to authoritative state only after validation
```

Parser/NLU/LLM confidence never makes a value authoritative on its own.

## TAKE OVER and failure invariant

Failure moves toward deterministic fallback, local recovery, takeover or safe stop.

Local cancellation must not wait on model/network acknowledgement before stopping AI output/media generation and invalidating pending inference generations.

## Evidence rule

- `HOST_GREEN` is host evidence only;
- `PROVEN_S22` requires physical reproduction on the target phone;
- documentation or a host test never upgrades a hardware claim;
- host-only Gate D contracts do not justify a live call;
- live-call authorization is session-scoped and must be freshly granted.