# Roadmap

## Status vocabulary

- `DONE` — implementation complete for the stated scope.
- `HOST_GREEN` — targeted/canonical host and CI evidence is green.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `FROZEN` — do not modify without a separate root-cause scope.

## Current gate

Gate D `BOOK_APPOINTMENT` is **DONE / HOST_GREEN / PROVEN_S22 / MERGED**.

PR #5 was squash-merged to `main` as:

```text
46bcfc9e13bed747e13429c50f54c7b4d3e47f69
```

The feature branch was deleted after merge. Durable remote branches are `main` and `agent-control`.

## Frozen foundation

`DONE / PROVEN_S22 / FROZEN` unless a new root cause requires reopening:

- Samsung cellular RX/TX path and `CallMediaSessionCoordinator`;
- privileged-helper media boundary;
- local Polish STT/TTS foundation;
- deterministic `CallPlan + PhraseMatrix` fast path and previously proven media cleanup behavior.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Gate D completed scope

1. application-owned `CustomTaskGraphCore` with typed bounded state, effects-as-data and deterministic replay;
2. `TaskGraphApplyBridge` re-checking graph/state/generation/mapping/provenance/slot authorization/schema before reducer entry;
3. reusable `AppointmentInterpreter`, deterministic CallPlan/PhraseMatrix routing, `DialogueFit` hysteresis and fail-closed bounded shadow validation;
4. `AuthorizedFactSnapshot`, application-owned `FactDisclosurePolicy`, host persistent IdentityVault and Android Keystore/AtomicFile vault adapters;
5. reviewed deterministic-first Gate D product binding and shared STT/synthetic finalized-text ingress;
6. BOOK_APPOINTMENT owner chain: proposal owner reuse, explicit app-owned user CONFIRM/REJECT, exact approving-workflow re-check, one-shot proposal-bound commitment permit, token-scoped revocation, exact redacted permit-consumption evidence, explicit deferred completion mode, and factual completion owner requiring matching `SUCCESS` evidence before graph/workflow completion.

## Final invariant

```text
permit issued
 != permit consumed
 != business success confirmed
```

Consumption alone never completes the task. `CallWorkflow.complete(outcome)` remains the completion owner, and TaskGraph `COMPLETE` is committed only after exact success evidence and workflow success. Generic deterministic/shadow `commit-complete` candidates cannot own factual completion.

## Evidence

Host/canonical:

```text
BOOK_APPOINTMENT_COMPLETION_RED=true
BOOK_APPOINTMENT_COMPLETION_GREEN=true
BOOK_APPOINTMENT_COMPLETION_CANONICAL_GREEN=true
ANDROID_COMPLETION_CONTRACT_PACKAGED=true
FINAL_GATE_D_NO_PHONE_CANONICAL_GREEN=true
```

Android CI #546, #547 and #552 completed successfully.

Physical S22 no-call proof:

```text
chatgpt-gated-s22-final-owner-proofs-v049-20260923
DEFERRED_COMPLETION_BINDING_S22_PROVEN=true
BOOK_APPOINTMENT_COMPLETION_S22_PROVEN=true
FINAL_GATE_D_S22_OWNER_PROOFS_GREEN=true

chatgpt-gated-s22-commitment-regression-v050-20260923
BOOK_APPOINTMENT_COMMITMENT_REGRESSION_S22_GREEN=true
```

Earlier S22 proofs for IdentityVault, synthetic product ingress and permit issuance remain valid.

## Next execution order

There is no pending Gate D implementation slice.

1. Continue development from `main` only.
2. Do not reopen Gate D internals unless a concrete acceptance-flow failure exposes a root cause.
3. If the next step is a live acceptance call, require fresh explicit authorization for one concrete target/number and one concrete task in the current session before dialing.
4. For test-only public business/reception calls, disclose the AI/test purpose at the start and obtain consent; no consent means stop without creating a commitment.
5. For genuine user-authorized tasks, use only authorized facts and the existing proposal/user-confirmation/commitment/completion owners.

## Deferred work

- Late plaintext disclosure remains protected by IdentityVault + `AuthorizedFactSnapshot` + `FactDisclosurePolicy`; wire only exact fields needed by a concrete acceptance flow.
- Orange ServicePack remains checkpointed future work, not the active roadmap.
- Reopening media, a generic executor, or another TaskGraph framework is out of scope without new evidence.

## Live-call stop line

A connected phone and successful physical proofs do not authorize dialing. Every live call requires fresh explicit authorization for the exact target and task in the current session.
