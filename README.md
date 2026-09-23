# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ (`SM-S906B`, Android 16 / API 36 / One UI 8) to a bounded autonomous task engine without external audio hardware.

## Current status

Gate D (`BOOK_APPOINTMENT`) is **DONE / HOST_GREEN / PROVEN_S22** for the reviewed no-call product boundary.

```text
implementation             DONE
canonical host gate         GREEN
Android instrumentation     PROVEN_S22
final owner boundaries      PROVEN_S22
live acceptance call        SEPARATE AUTHORIZATION GATE
```

Latest physical evidence:

```text
chatgpt-gated-s22-final-owner-proofs-v049-20260923
DEFERRED_COMPLETION_BINDING_S22_PROVEN=true
BOOK_APPOINTMENT_COMPLETION_S22_PROVEN=true
FINAL_GATE_D_S22_OWNER_PROOFS_GREEN=true

chatgpt-gated-s22-commitment-regression-v050-20260923
BOOK_APPOINTMENT_COMMITMENT_REGRESSION_S22_GREEN=true
```

Those tests ran physically on the target S22 and made no cellular call.

## Gate D product boundary

The reviewed owner chain is:

```text
finalized STT text OR explicit synthetic finalized text
 -> PhraseMatrix / CallPlan deterministic routing
 -> optional bounded shadow candidate
 -> current-state / generation / slot-authorization re-check
 -> TaskGraphApplyBridge
 -> proposal through existing CallWorkflow policy owner
 -> explicit app-owned user CONFIRM / REJECT
 -> exact proposal-bound one-shot CallCommitmentGate permit
 -> exact permit consumption evidence
 -> structured COMPLETE stays deferred data
 -> exact SUCCESS outcome validation
 -> staged COMMIT_SUCCEEDED graph transition
 -> existing CallWorkflow.complete(outcome) owner
 -> commit staged TaskGraph COMPLETE snapshot only after workflow success
```

Hard invariants:

- `permit issued != permit consumed != business success confirmed`;
- generic deterministic/shadow `commit-complete` candidates cannot own factual completion;
- default/public `CallPlan` completion behavior is unchanged; deferral requires explicit reviewed product binding;
- public `LocalTextCallSession.create(...)` does not automatically activate Gate D product execution;
- no generic effect/completion executor exists;
- model/parser/shadow/storage/reducer/synthetic input do not own dialing, target widening, plaintext disclosure, speech/TTS release, user confirmation, commitment or factual completion;
- accepted graph effects remain inert data until an existing application owner consumes them.

The implementation also includes `CustomTaskGraphCore`, deterministic replay/evidence, `TaskGraphApplyBridge`, reusable `AppointmentInterpreter`, categorical `DialogueFit` + hysteresis, bounded shadow/supervisor validation, `AuthorizedFactSnapshot` / `FactDisclosurePolicy`, host `PersistentIdentityVault`, and Android Keystore-backed IdentityVault storage.

## Verification checkpoint

Final no-phone code checkpoint:

```text
cefe6492c7e714a8124e08cb1f42a68554955832
```

No-phone documentation checkpoint before device proof:

```text
39b2749f85b51a9cb533631326ce1fec4439ca10
```

Canonical evidence includes:

- `BOOK_APPOINTMENT_COMPLETION_RED=true`;
- `BOOK_APPOINTMENT_COMPLETION_GREEN=true`;
- `BOOK_APPOINTMENT_COMPLETION_CANONICAL_GREEN=true`;
- `ANDROID_COMPLETION_CONTRACT_PACKAGED=true`;
- `FINAL_GATE_D_NO_PHONE_CANONICAL_GREEN=true`;
- Android CI #546 and #547 success.

## Physical proof status

Physically `PROVEN_S22` without making a cellular call:

- Android IdentityVault;
- synthetic reviewed Gate D product ingress;
- BOOK_APPOINTMENT proposal -> confirmation -> explicit user confirmation -> one-shot permit issuance;
- reviewed deferred-completion binding;
- factual BOOK_APPOINTMENT completion owner;
- commitment regression after the final owner proof.

## Next gate

PR #5 can be merged after the fresh CI/mergeability re-check for this proof documentation update. After merge, delete `gate-d-taskgraph-core` and continue from `main`.

A live acceptance call is intentionally separate. It requires fresh explicit authorization for the concrete target and task in the current chat/session; a connected phone, successful tests, documentation or prior calls do not grant that authority.

## Frozen foundation

Cellular RX/TX, `CallMediaSessionCoordinator`, Samsung privileged-helper media path, local speech foundation and previously proven media behavior remain frozen. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching them.

Identity plaintext remains late-bound through:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> current task / target / state / generation
 -> optional user approval
 -> resolve plaintext only after ALLOW
```

## Sources of truth

- `docs/ROADMAP.md` — authoritative execution order and acceptance gates;
- `docs/ARCHITECTURE.md` — component and authority ownership;
- `docs/SECURITY_PRIVACY.md` — privacy and live-call rules;
- `docs/HANDOFF_NEXT_CHAT.md` — exact current checkpoint;
- `docs/NEXT_CHAT_PROMPT.md` — ready-to-paste continuation prompt;
- `docs/HANDOFF_PROTOCOL.md` — close-out/transfer rules;
- `docs/GATE_D_TASKGRAPH_V1_AUDIT_2026-09-22.md` — Gate D audit/decision record.

Canonical local gate:

```bash
bash scripts/verify_host.sh
```
