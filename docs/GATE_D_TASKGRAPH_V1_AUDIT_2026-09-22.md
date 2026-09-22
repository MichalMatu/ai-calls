# Gate D TaskGraph v1 preimplementation audit — 2026-09-22

Status: preimplementation audit complete. This document records ownership boundaries before product integration.

## Scope and conclusion

Gate D needs a bounded, replayable conversational TaskGraph. The existing product already owns target authorization, call workflow, proposal confirmation, commitment authority, output approval, media and readiness. TaskGraph must compose those boundaries, not replace them.

No blocker was found for a host-only TaskGraph v1. The safest insertion is a pure application-owned reducer with immutable context and explicit event records. Product integration should happen under `LocalTextCallSession`, which is already the bounded live-session owner for finalized turns and recovery context.

## Existing ownership map

### `CallTask`

Owns the bounded user goal plus `CallConstraints`, `CallPreferences` and the current legacy `authorizedFacts: Map<String, String>`.

Gate D consequence: do not copy this map into TaskGraph context. Identity values need a typed identity/disclosure boundary. TaskGraph may hold validated non-secret dialogue slots and typed field IDs, never a second plaintext authority store.

### `CallConstraints` / `CallPreferences`

Already distinguish hard constraints from soft preferences. Appointment candidate validation should reuse/compose these contracts rather than invent parallel constraint semantics in the graph.

### `CallResolvedTarget`

Already represents the concrete authorized target. TaskGraph must not choose arbitrary dial addresses or widen target authorization.

### `CallWorkflow`

Already owns coarse product progress and structured proposal/completion flow: ready-to-dial, dialing, active negotiation, pending user decision and completion/failure.

TaskGraph should own the finer conversational micro-state inside an active task. It must not become a second product workflow or mark the call task complete on its own.

### `CallConfirmationPolicy`

Already evaluates a concrete proposal against hard constraints/preferences and decides whether user confirmation is required. TaskGraph may create a typed proposal candidate but must delegate confirmation authority here.

### `CallCommitmentGate`

Already owns one-shot commitment authority tied to an exact concrete proposal. TaskGraph states such as `COMMITMENT` are descriptive orchestration states only; they do not grant a commitment permit.

### `CallPlan` / `CallPlanEngine`

Already provide a deterministic bounded dialogue plan and final-transcript rule evaluation. The current fact path reads the legacy `authorizedFacts` map, so Gate D identity work should replace that boundary deliberately rather than copy it.

TaskGraph should reuse the concept of existing legal transition/rule IDs. It should not generate arbitrary executable actions or arbitrary speech.

### `CallPlanTurnCoordinator`

Already bridges finalized turns / bounded rule suggestions into `CallPlan` and delegates typed proposals/completion to `CallWorkflow`. Its bounded suggested-rule validation is a useful precedent for future supervisor suggestions.

Do not turn diagnostic runners/probes into the new product orchestrator.

### `PhraseMatrix`

Classification only. It returns deterministic match diagnostics and remains outside authority. A PhraseMatrix match is an extraction/interpretation signal, not slot commitment.

### `LocalTextCallSession`

This is the existing product session owner for the local deterministic text-call path. It already owns finalized-turn handling, previous validated rule context and bounded unknown/recovery count while explicitly not owning commitment or telephony output authority.

Recommended future Gate D seam:

```text
final transcript
  -> PhraseMatrix / typed parser candidates
  -> shadow supervisor observation
  -> DialogueFit
  -> validated TaskGraph event/candidate
  -> pure TaskGraph reduction
  -> approved effect/proposal handoff to existing owners
```

The physically proven media path does not need redesign for TaskGraph v1.

### preparation/readiness

`AndroidLocalTextCallPreparation`, `LocalTextCallReadinessCoordinator` and `PreparedLocalTextCall` already enforce pre-dial readiness and one-shot ownership transfer. TaskGraph integration should carry only immutable/binding data through readiness if needed later; it should not move dial/media authority into the graph.

### `serviceintent/`

`ServiceIntentExecutionValidator` already demonstrates the required fail-closed pattern: registry/generation/service-pack/route/authority validation before execution. Supervisor proposals should follow the same pattern: known transition ID, current generation, compatible state, valid typed candidates, then application-owned validation.

## TaskGraph v1 ownership

TaskGraph should own only:

- typed state, event, transition and non-secret slot IDs;
- legal state-compatible transitions;
- pure guards;
- immutable validated dialogue context;
- bounded recovery counters;
- explicit proposal/confirmation/commitment orchestration states;
- explicit completion/failure/takeover terminal states;
- effects as returned data, never executed inside the reducer;
- versioned replayable event evidence;
- stale/invalid event rejection and deterministic replay.

TaskGraph must not own:

- target resolution or dial allowlists;
- telephony/media/TTS execution;
- arbitrary output approval;
- identity plaintext storage;
- identity disclosure permission;
- user confirmation authority;
- commitment permits;
- final `CallWorkflow` completion authority.

## Identity / disclosure seam

Use three distinct layers:

```text
IdentityVault
  persistent encrypted values

AuthorizedFactSnapshot
  typed field IDs authorized/available for this task

DialogueState / TaskGraph context
  transient validated non-secret facts from this call
```

`FactDisclosurePolicy` should receive the task, exact target, current TaskGraph state, typed `IdentityFieldId`, sensitivity and per-task authorization. It returns `ALLOW`, `ASK_USER` or `DENY`. Availability in the vault alone is never permission to disclose.

The Android encrypted persistence layer should be implemented only after this host contract is stable.

## DialogueFit / supervisor seam

Do not start the LLM only after deterministic failure. When enabled, the shadow observer may see bounded non-secret finalized-turn context from the start, but its output remains a hypothesis.

`DialogueFit` should be application-owned and combine available deterministic signals such as STT quality, PhraseMatrix/parser result, expected transition compatibility, negation/contradiction, missing slots, recovery count and deterministic-vs-shadow disagreement.

Initial categories remain `HIGH`, `UNCERTAIN`, `LOW`, `BROKEN`. No arbitrary numeric thresholds are selected in this slice.

Supervisor activation comes only after deterministic TaskGraph + simulator evidence. Supervisor output must be revalidated against existing transition IDs, state, generation, typed slots, constraints and disclosure policy.

## Event log / replay recommendation

Each accepted reduction should emit a versioned immutable record containing at least:

- graph/schema version;
- sequence and generation before/after;
- transition ID and typed event;
- state before/after;
- resulting bounded context/recovery count;
- returned effects as data.

Replay starts from a known initial snapshot and re-runs the reducer. Any unsupported schema, wrong graph version, stale generation, sequence discontinuity or mismatch between recorded and recomputed evidence fails closed.

## Core spike

Shared RED contract tests were added before implementation. They cover the required TaskGraph v1 semantics and intentionally failed because no TaskGraph implementation existed.

The production choice between a minimal custom reducer and KStateMachine is evaluated against those same semantics. The final choice and rationale will be appended here and copied into the session handoff; only one engine will remain production code.
