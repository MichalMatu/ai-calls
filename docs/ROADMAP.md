# Roadmap

## Status vocabulary

- `DONE` — implementation complete for the stated scope.
- `HOST_GREEN` — targeted/canonical host and CI evidence is green.
- `PROVEN_S22` — physically executed successfully on the target phone.
- `PENDING_PHYSICAL` — code/test artifact is ready, but the required device execution has not happened.
- `FROZEN` — do not modify without a separate root-cause scope.

## Current gate

Gate D `BOOK_APPOINTMENT` is **HOST_COMPLETE / PENDING_PHYSICAL**.

All implementation and verification work that does not require the phone is finished. The final code checkpoint before documentation close-out is:

```text
cefe6492c7e714a8124e08cb1f42a68554955832
```

The work branch remains `gate-d-taskgraph-core`, PR #5 remains draft until the newest Android owner boundaries receive a physical no-call proof.

## Frozen foundation

`DONE / PROVEN_S22 / FROZEN` unless a new root cause requires reopening:

- Samsung cellular RX/TX path and `CallMediaSessionCoordinator`;
- privileged-helper media boundary;
- local Polish STT/TTS foundation;
- deterministic `CallPlan + PhraseMatrix` fast path and previously proven media cleanup behavior.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Gate D completed scope

The implementation now contains:

1. **Task engine** — application-owned `CustomTaskGraphCore`, typed states/events/transitions, immutable snapshots/context, bounded recovery, effects-as-data, deterministic replay and sequence evaluation.
2. **Application apply boundary** — `TaskGraphApplyBridge` re-checking graph version, current state, generation, transition/event mapping, provenance, slot scope, dynamic authorization and schema before reducer entry.
3. **Dialogue interpretation** — reusable `AppointmentInterpreter`, PhraseMatrix/CallPlan deterministic routing, categorical `DialogueFit` with hysteresis, bounded shadow observations and fail-closed `SupervisorProposalValidator`.
4. **Identity/privacy** — typed `IdentityFieldId`, per-task `AuthorizedFactSnapshot`, application-owned `FactDisclosurePolicy`, host persistent vault and production Android Keystore/AtomicFile vault adapters.
5. **Reviewed product session** — deterministic-first Gate D product binding, optional bounded shadow, shared STT/synthetic finalized-text ingress, stale/cancel/close containment, no public automatic activation.
6. **BOOK_APPOINTMENT owner chain** — proposal owner reuse, policy-neutral graph proposal, explicit app-owned user CONFIRM/REJECT, exact approving-workflow re-check, one-shot proposal-bound commitment permit, token-scoped revocation, redacted exact permit-consumption evidence, explicit deferred completion mode, and factual completion owner requiring matching `SUCCESS` evidence before graph/workflow completion.

## Final commitment/completion invariant

These are separate facts:

```text
permit issued
 != permit consumed
 != business success confirmed
```

The reviewed product path enforces the separation:

- permit consumption records exact redacted proposal-bound evidence only;
- consumption does not run `COMMIT_SUCCEEDED` and does not call `CallWorkflow.complete(...)`;
- `CallPlanAction.COMPLETE` is deferred only for explicit reviewed product wiring; public/default behavior is unchanged;
- a generic deterministic/shadow `commit-complete` candidate is blocked from factual completion ownership;
- `completeBookAppointment(outcome)` requires graph `COMMITMENT`, exact consumed proposal, active exact workflow, `CallOutcomeStatus.SUCCESS`, matching scheduled time and graph slot, and matching known price/provider/location fields;
- the `commit-complete` graph transition is staged first;
- the existing `CallWorkflow.complete(outcome)` owner runs next;
- only after workflow success is the staged TaskGraph `COMPLETE` snapshot committed.

No generic effect/completion executor exists.

## Verification evidence

Recent final slice:

```text
9d18ab2940d126a30c7f1b84d69d1a1db00969d9
RED completion owner scaffold
chatgpt-gated-book-appointment-completion-red-v041-20260923
BOOK_APPOINTMENT_COMPLETION_RED=true

b49305e89c4fa5cb3b5a3dad60df8981e1ae01d5
completion owner production boundary
chatgpt-gated-book-appointment-completion-green-v042-20260923
BOOK_APPOINTMENT_COMPLETION_GREEN=true
chatgpt-gated-book-appointment-completion-canonical-v043-20260923
BOOK_APPOINTMENT_COMPLETION_CANONICAL_GREEN=true

cefe6492c7e714a8124e08cb1f42a68554955832
Android full completion owner contract
chatgpt-gated-android-completion-contract-build-v044-20260923
ANDROID_COMPLETION_CONTRACT_PACKAGED=true
chatgpt-gated-final-canonical-v045-20260923
FINAL_GATE_D_NO_PHONE_CANONICAL_GREEN=true
```

The final canonical gate runs `scripts/verify_host.sh` and packages the Android instrumentation APK with a clean worktree.

Earlier important checkpoints remain in Git history and `.agent/results`; they do not need to be duplicated here.

## Physical proof status

`PROVEN_S22` on `SM-S906B`, Android 16, no cellular call:

- Android IdentityVault — `chatgpt-gated-s22-identity-vault-proof-v004-20260923`, `IDENTITYVAULT_S22_PROVEN=true`;
- synthetic reviewed Gate D product ingress — `chatgpt-gated-s22-synthetic-gated-product-proof-v005-20260923`, `SYNTHETIC_GATE_D_S22_PROVEN=true`;
- BOOK_APPOINTMENT owner chain through unconsumed permit issuance — `chatgpt-gated-s22-book-appointment-commitment-proof-v029-20260923`, `BOOK_APPOINTMENT_COMMITMENT_S22_PROVEN=true`.

`PENDING_PHYSICAL`:

- `AndroidGateDDeferredCompletionBindingContractTest`;
- `AndroidGateDBookAppointmentCompletionContractTest`.

The attempted deferred-completion proof `chatgpt-gated-s22-deferred-completion-proof-v039-20260923` stopped before Gradle because `RFCT70L7E8J` was absent. `chatgpt-gated-adb-inventory-v040-20260923` confirmed an empty ADB device list. This is an environment blocker, not evidence of a product failure.

## Next execution order

Do not start another host feature slice before resolving the physical gate.

1. Fetch fresh repo/PR state and use the fresh Local Chat Bridge binding.
2. Confirm the S22 is visible in ADB.
3. Run the focused no-call instrumentation contracts:
   - `AndroidGateDDeferredCompletionBindingContractTest`;
   - `AndroidGateDBookAppointmentCompletionContractTest`;
   - optionally re-run `AndroidGateDBookAppointmentCommitmentContractTest` as regression evidence.
4. If terminal device evidence is green, update status to `PROVEN_S22`.
5. Re-run/re-check canonical CI if the proof causes any repository change.
6. Merge PR #5 to `main` only when the physical proof is green and the PR is still clean/mergeable; then delete `gate-d-taskgraph-core`. Keep `agent-control` only for bridge evidence/workflow as designed.
7. After merge, a live acceptance call may be planned only under a new explicit user authorization for a concrete target/task.

## Deferred work

- Late plaintext disclosure is already protected by IdentityVault + `AuthorizedFactSnapshot` + `FactDisclosurePolicy`; wire a specific disclosure action only when a concrete acceptance flow requires that field. Do not preload plaintext into model/supervisor context.
- Orange ServicePack remains checkpointed future work, not the active roadmap.
- Reopening media, a generic executor, or another TaskGraph framework is out of scope without new evidence.

## Live-call stop line

No repository document, prior chat, previous target, connected phone, Local Agent result or ServicePack grants permission to dial. Every live call requires fresh explicit authorization for the exact target and task in the current session.
