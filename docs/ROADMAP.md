# Roadmap

## Status vocabulary

- `DONE` — implementation complete for the stated scope.
- `HOST_GREEN` — targeted/canonical host and CI evidence is green.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `FROZEN` — do not modify without a separate root-cause scope.

## Current gate

Gate D `BOOK_APPOINTMENT` is **DONE / HOST_GREEN / PROVEN_S22** for the reviewed no-call product boundary.

Final no-phone implementation checkpoint:

```text
cefe6492c7e714a8124e08cb1f42a68554955832
```

Documentation close-out checkpoint before the physical proof:

```text
39b2749f85b51a9cb533631326ce1fec4439ca10
```

Physical proof on Samsung S22+ (`SM-S906B`, Android 16) completed successfully in:

```text
chatgpt-gated-s22-final-owner-proofs-v049-20260923
DEFERRED_COMPLETION_BINDING_S22_PROVEN=true
BOOK_APPOINTMENT_COMPLETION_S22_PROVEN=true
FINAL_GATE_D_S22_OWNER_PROOFS_GREEN=true

chatgpt-gated-s22-commitment-regression-v050-20260923
BOOK_APPOINTMENT_COMMITMENT_REGRESSION_S22_GREEN=true
```

No cellular call was made by these proofs.

## Frozen foundation

`DONE / PROVEN_S22 / FROZEN` unless a new root cause requires reopening:

- Samsung cellular RX/TX path and `CallMediaSessionCoordinator`;
- privileged-helper media boundary;
- local Polish STT/TTS foundation;
- deterministic `CallPlan + PhraseMatrix` fast path and previously proven media cleanup behavior.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Gate D completed scope

The implementation contains:

1. application-owned `CustomTaskGraphCore` with typed bounded state, effects-as-data and deterministic replay;
2. `TaskGraphApplyBridge` re-checking graph/state/generation/mapping/provenance/slot authorization/schema before reducer entry;
3. reusable `AppointmentInterpreter`, deterministic CallPlan/PhraseMatrix routing, `DialogueFit` hysteresis and fail-closed bounded shadow validation;
4. `AuthorizedFactSnapshot`, application-owned `FactDisclosurePolicy`, host persistent IdentityVault and Android Keystore/AtomicFile vault adapters;
5. reviewed deterministic-first Gate D product binding and shared STT/synthetic finalized-text ingress;
6. BOOK_APPOINTMENT owner chain: proposal owner reuse, explicit app-owned user CONFIRM/REJECT, exact approving-workflow re-check, one-shot proposal-bound commitment permit, token-scoped revocation, exact redacted permit-consumption evidence, explicit deferred completion mode, and factual completion owner requiring matching `SUCCESS` evidence before graph/workflow completion.

## Final commitment/completion invariant

```text
permit issued
 != permit consumed
 != business success confirmed
```

The reviewed product path enforces this separation:

- permit consumption records exact proposal-bound evidence only;
- consumption does not run `COMMIT_SUCCEEDED` and does not call `CallWorkflow.complete(...)`;
- `CallPlanAction.COMPLETE` is deferred only for explicit reviewed product wiring; public/default behavior is unchanged;
- generic deterministic/shadow `commit-complete` candidates are blocked from factual completion ownership;
- `completeBookAppointment(outcome)` requires graph `COMMITMENT`, exact consumed proposal, the exact active workflow, `CallOutcomeStatus.SUCCESS`, matching scheduled time/graph slot and matching known price/provider/location fields;
- the `commit-complete` graph transition is staged first;
- existing `CallWorkflow.complete(outcome)` remains the completion owner;
- only after workflow success is the staged TaskGraph `COMPLETE` snapshot committed.

No generic effect/completion executor exists.

## Verification evidence

Host/canonical evidence:

```text
chatgpt-gated-book-appointment-completion-red-v041-20260923
BOOK_APPOINTMENT_COMPLETION_RED=true

chatgpt-gated-book-appointment-completion-green-v042-20260923
BOOK_APPOINTMENT_COMPLETION_GREEN=true

chatgpt-gated-book-appointment-completion-canonical-v043-20260923
BOOK_APPOINTMENT_COMPLETION_CANONICAL_GREEN=true

chatgpt-gated-android-completion-contract-build-v044-20260923
ANDROID_COMPLETION_CONTRACT_PACKAGED=true

chatgpt-gated-final-canonical-v045-20260923
FINAL_GATE_D_NO_PHONE_CANONICAL_GREEN=true
```

Android CI #546 for the code checkpoint and CI #547 for the no-phone documentation checkpoint completed successfully.

## Physical proof status

`PROVEN_S22` on `SM-S906B`, Android 16, without a cellular call:

- Android IdentityVault;
- synthetic reviewed Gate D product ingress;
- BOOK_APPOINTMENT owner chain through one-shot permit issuance;
- reviewed deferred-completion binding;
- factual BOOK_APPOINTMENT completion owner;
- commitment regression after the final completion-owner proof.

The newest evidence is `chatgpt-gated-s22-final-owner-proofs-v049-20260923` plus `chatgpt-gated-s22-commitment-regression-v050-20260923`.

## Next execution order

1. Re-check PR #5 freshness, CI and mergeability.
2. Mark PR #5 ready and merge `gate-d-taskgraph-core` to `main` if still green.
3. Delete `gate-d-taskgraph-core` after merge; preserve `agent-control` for Local Agent evidence/workflow.
4. Do not start another Gate D host feature slice unless a concrete acceptance-flow failure reveals a root cause.
5. A live acceptance call is a separate gate and requires fresh explicit authorization for one concrete target and task in the current session.

## Deferred work

- Late plaintext disclosure remains protected by IdentityVault + `AuthorizedFactSnapshot` + `FactDisclosurePolicy`; wire only the exact fields needed by a concrete acceptance flow.
- Orange ServicePack remains checkpointed future work, not the active roadmap.
- Reopening media, a generic executor, or another TaskGraph framework is out of scope without new evidence.

## Live-call stop line

A connected phone and successful device proofs do not authorize dialing. Every live call requires fresh explicit authorization for the exact target and task in the current session.
