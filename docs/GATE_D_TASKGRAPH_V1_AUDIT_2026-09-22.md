# Gate D TaskGraph v1 audit + implementation decision record — 2026-09-22

Status: **preimplementation audit complete; first Gate D host/product-binding foundation implemented and verified**.

This document records the ownership analysis, engine decision and the implementation checkpoint reached before handoff. It is not live-call authorization.

## Repository checkpoint

Repository: `MichalMatu/ai-calls`

Work branch: `gate-d-taskgraph-core`

PR: `#5` — `Gate D TaskGraph v1 core` (draft)

Code checkpoint before the docs-only closeout commit:

```text
a8e6130c7dd85dd011ed420ddd7be1294b2322a2
Bind Gate D context through Android readiness
```

That code checkpoint is 27 commits ahead of `main` base `7b6519238808599a5084f3f1c72d103ea43abd9b` and changes only the Gate D/domain/local-session surface plus this audit document; the frozen media implementation is not part of the Gate D diff.

Verified GitHub Actions evidence for the code checkpoint:

```text
Android CI
run id: 35748536552
run number: 468
conclusion: success
```

## Audit conclusion

The existing product already owns target authorization, coarse call workflow, proposal confirmation, one-shot commitment authority, output approval, media and readiness.

Gate D therefore belongs as a bounded conversational micro-state layer under the real product session owner. It must compose existing owners rather than introduce a second workflow/authority stack.

`LocalTextCallSession` is the correct product owner for the first integration because it already owns finalized-turn dialogue context on the deterministic local text-call path.

## Existing owners preserved

### `CallTask`

Owns bounded user goal, hard constraints, preferences and task scope. Gate D must not duplicate a plaintext fact-authority map inside TaskGraph.

### `CallResolvedTarget`

Owns the concrete authorized target. TaskGraph/supervisor cannot choose or widen a dial address.

### `CallWorkflow`

Owns coarse task/call progress and proposal/completion flow. TaskGraph owns finer conversational state only.

### `CallConfirmationPolicy`

Evaluates concrete proposals and decides whether user confirmation is required. TaskGraph proposal/confirmation states do not replace this authority.

### `CallCommitmentGate`

Owns one-shot commitment permits for exact proposals. A `COMMITMENT` TaskGraph state is orchestration state, not a permit.

### `CallPlan` / `CallPlanTurnCoordinator`

Remain the proven deterministic dialogue decision path and provide the design precedent for revalidating bounded suggested IDs before action.

### `LocalTextCallSession`

Owns the prepared deterministic dialogue session, finalized-turn handling context, prior validated rule context and bounded unknown/recovery state. Gate D is bound here rather than in diagnostic runners or media code.

## Engine spike decision

Decision: **keep the minimal custom application-owned reducer**.

Production implementation:

```text
CustomTaskGraphCore
```

KStateMachine was considered as a candidate but is not carried as a runtime/dependency in the work branch.

Reasons:

- the required v1 semantics fit a small deterministic reducer;
- explicit application-owned event/evidence records remain the source for replay;
- effects remain returned data rather than hidden runtime actions;
- no framework needs to own persistence, side effects or authority;
- no second state-machine abstraction must be maintained.

Revisit only if a later concrete requirement such as genuinely necessary hierarchical/composed state behavior cannot remain simple without a framework.

## Implemented TaskGraph v1 contract

`TaskGraphCore.kt` now provides:

- typed state/event/transition/slot/effect IDs;
- declared state kinds including proposal, confirmation, commitment and terminal kinds;
- immutable snapshots/context;
- state-compatible transitions;
- pure guards;
- stale generation/version/state rejection;
- ambiguous/no-compatible transition rejection;
- bounded recovery counters;
- context reduction;
- effects as data;
- versioned immutable event records;
- deterministic replay;
- fail-closed replay on schema/version/sequence/evidence mismatch.

It does not execute speech, media, workflow or commitments.

## Identity/disclosure seam implemented

Host contracts separate:

```text
IdentityVault
  future persistent encrypted values

AuthorizedFactSnapshot
  typed field IDs available/authorized for one task

DialogueState / TaskGraph context
  transient validated non-secret values
```

`FactDisclosurePolicy` is application-owned and produces typed `ALLOW / ASK_USER / DENY` decisions. High-sensitivity fields require additional explicit task approval before they may appear as available field IDs to the shadow runtime.

No Android encrypted value store is implemented yet; that remains a later slice after host semantics stabilize.

## BOOK_APPOINTMENT host simulator implemented

`BookAppointmentTaskGraph` + `BookAppointmentSimulator` provide a deterministic host harness that composes the new graph with the existing:

- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- `FactDisclosurePolicy`.

The simulator covers the first useful product semantics: offered appointment parsing, policy rejection, proposal creation, required confirmation, user rejection/confirmation, one-shot commitment, no availability, harmless-question/clarification recovery, identity-field disclosure decisions, cancel/takeover and deterministic replay evidence.

This simulator is not a live-call orchestrator.

## DialogueFit and bounded supervisor contracts implemented

`DialogueFit.kt` defines:

- deterministic signal types;
- categorical `HIGH / UNCERTAIN / LOW / BROKEN` result;
- explainable reasons;
- bounded `ShadowDialogueObservation`;
- quarantined `ShadowDialogueHypothesis`.

`SupervisorProposalValidator` rejects:

- stale generation;
- missing/unknown-for-observation transition;
- low confidence;
- slots outside the allowed non-secret slot set;
- authority-bearing slot IDs.

Accepted output is `ValidatedSupervisorCandidate`, still candidate data only.

## Real product binding implemented

The branch now carries optional `TaskGraphDefinition + AuthorizedFactSnapshot` through:

```text
AndroidTextCallReadiness / LocalPhoneTextCallReadiness
 -> LocalTextCallReadinessCoordinator
 -> PreparedLocalTextCall
 -> LocalTextCallSession
 -> LocalTextCallGateDRuntime
```

`LocalTextCallGateDRuntime` can:

- create a bounded observation from a real session-bound task/graph/fact scope;
- expose only currently legal transitions;
- expose only authorized/available field IDs valid in the current graph state;
- filter high-sensitivity fields unless explicitly approved;
- revalidate a shadow hypothesis.

The runtime deliberately has no reducer/workflow/speech/dial/commitment/plaintext-vault API.

## Current architectural stop line

This checkpoint intentionally stops before automatic Gate D execution.

The following are **not** implemented yet:

- automatic invocation of the shadow observer on every finalized product turn;
- observer lifecycle/cancellation/provider integration;
- production mapping from existing deterministic turn evidence into `DialogueFitSignals`;
- conversion of an accepted supervisor candidate into a TaskGraph event;
- automatic `TaskGraphCore.reduce()` from the session path;
- graph effect execution;
- Android encrypted IdentityVault persistence;
- a Gate D live appointment call.

That stop line is deliberate and should remain visible in the next slice.

## Exact next slice

Start with a RED contract for the real `LocalTextCallSession` finalized-turn path:

1. when Gate D is bound, a finalized turn can create one bounded shadow observation from the current authoritative graph snapshot/context;
2. deterministic PhraseMatrix/CallPlan behavior remains identical;
3. the observer is quarantined and session-owned;
4. stale/failed/cancelled observer work fails closed and cannot affect speech/workflow;
5. hypothesis validation and `DialogueFit` produce diagnostics/candidate data only;
6. **no `reduce()` in this slice**.

After that is host-green, a separate later RED/GREEN slice may introduce an explicit application-owned candidate -> typed TaskGraph event -> reducer bridge.

## Known media-test history

During the Gate D branch work, repeated full host runs around `59b5f75` hit an existing/frozen test failure in:

```text
CallRealtimeMediaSessionTest.pumpFailure...
```

The Gate D slice tests were green and the frozen media implementation was not modified. The failure repeated enough times that it should not simply be labelled random without a focused order/pollution audit.

However, the latest full Android CI for code checkpoint `a8e6130` passed completely. Therefore this is a **known follow-up signal, not a current Gate D blocker**.

Do not patch frozen media as part of Gate D unless a separate audit proves a real media defect and the user explicitly accepts that scope.

## Safety and handoff rules

- no physical call is authorized by this document;
- fresh live-call authorization is required in every new session;
- never copy an old Local Chat Bridge `agent_binding` into a new session;
- use the fresh binding envelope provided by the new bridge session;
- do not promote diagnostic runners into the product orchestrator;
- do not touch `privileged-helper/` or frozen Samsung media for stylistic cleanup;
- keep `extract -> validate -> commit` semantics for all dialogue-derived data;
- update `docs/HANDOFF_NEXT_CHAT.md` and `docs/NEXT_CHAT_PROMPT.md` when the next major slice closes.