# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ (`SM-S906B`, Android 16 / API 36 / One UI 8) to a bounded autonomous task engine without external audio hardware.

## Current status

Gate D (`BOOK_APPOINTMENT`) is **DONE / HOST_GREEN / PROVEN_S22 / MERGED**.

PR #5 `Gate D TaskGraph v1 core` was squash-merged to `main` as:

```text
46bcfc9e13bed747e13429c50f54c7b4d3e47f69
```

The temporary `gate-d-taskgraph-core` branch was deleted after merge. The intended durable remote branches are now `main` and `agent-control`.

## Product boundary

The reviewed BOOK_APPOINTMENT owner chain is:

```text
finalized STT text OR explicit synthetic finalized text
 -> PhraseMatrix / CallPlan deterministic routing
 -> optional bounded shadow candidate
 -> current-state / generation / slot-authorization re-check
 -> TaskGraphApplyBridge
 -> existing CallWorkflow proposal policy owner
 -> explicit app-owned user CONFIRM / REJECT
 -> exact proposal-bound one-shot CallCommitmentGate permit
 -> exact permit-consumption evidence
 -> structured COMPLETE remains deferred data
 -> exact SUCCESS outcome validation
 -> staged COMMIT_SUCCEEDED graph transition
 -> existing CallWorkflow.complete(outcome) owner
 -> commit staged TaskGraph COMPLETE only after workflow success
```

Hard invariants:

- `permit issued != permit consumed != business success confirmed`;
- generic deterministic/shadow `commit-complete` candidates cannot own factual completion;
- default/public CallPlan completion behavior is unchanged; deferral requires explicit reviewed product binding;
- public `LocalTextCallSession.create(...)` does not automatically activate Gate D product execution;
- no generic effect/completion executor exists;
- model/parser/shadow/storage/reducer/synthetic input do not own dialing, target widening, plaintext disclosure, speech/TTS release, user confirmation, commitment or factual completion.

## Verification

Final no-phone implementation evidence includes:

- `BOOK_APPOINTMENT_COMPLETION_RED=true`;
- `BOOK_APPOINTMENT_COMPLETION_GREEN=true`;
- `BOOK_APPOINTMENT_COMPLETION_CANONICAL_GREEN=true`;
- `ANDROID_COMPLETION_CONTRACT_PACKAGED=true`;
- `FINAL_GATE_D_NO_PHONE_CANONICAL_GREEN=true`;
- Android CI #546, #547 and proof-doc CI #552 success.

Physical Samsung S22+ proof without a cellular call:

```text
chatgpt-gated-s22-final-owner-proofs-v049-20260923
DEFERRED_COMPLETION_BINDING_S22_PROVEN=true
BOOK_APPOINTMENT_COMPLETION_S22_PROVEN=true
FINAL_GATE_D_S22_OWNER_PROOFS_GREEN=true

chatgpt-gated-s22-commitment-regression-v050-20260923
BOOK_APPOINTMENT_COMMITMENT_REGRESSION_S22_GREEN=true
```

Earlier physical proofs remain valid for Android IdentityVault, synthetic reviewed Gate D product ingress and BOOK_APPOINTMENT permit issuance.

## Next product gate

There is no unfinished Gate D implementation slice.

A live acceptance call is a separate gate. Before dialing, the current session must contain fresh explicit authorization for **one concrete target/number and one concrete task**. A connected phone, successful device proofs, documentation or previous calls never grant that authority.

For test-only public business/reception calls, disclose the AI/test purpose at the start and ask consent; if consent is declined, stop without creating a real commitment. For genuine user-authorized tasks, stay within `CallTask`, `FactDisclosurePolicy`, user-confirmation, commitment and completion owners.

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
- `docs/HANDOFF_PROTOCOL.md` — close-out/transfer rules.

Canonical local gate:

```bash
bash scripts/verify_host.sh
```
