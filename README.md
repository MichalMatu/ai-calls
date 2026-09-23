# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on a stock Samsung Galaxy S22+ (`SM-S906B`, Android 16 / API 36 / One UI 8) to a bounded autonomous task engine without external audio hardware.

## Current status

Gate D (`BOOK_APPOINTMENT`) is **complete for all work that can be proven without a phone**.

```text
implementation             HOST_COMPLETE
canonical host gate         GREEN
Android instrumentation APK GREEN / PACKAGED
latest new device boundary  PENDING_PHYSICAL
live acceptance call        NOT AUTHORIZED / NOT RUN
```

The remaining device gap is not a known product failure: Local Agent task `chatgpt-gated-s22-deferred-completion-proof-v039-20260923` stopped before Gradle because ADB could not find `RFCT70L7E8J`, and `chatgpt-gated-adb-inventory-v040-20260923` confirmed an empty device list.

## Gate D product boundary

The reviewed product path now covers the whole bounded owner chain:

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

Important invariants:

- `permit issued != permit consumed != business success confirmed`;
- a generic deterministic/shadow `commit-complete` candidate cannot own factual completion;
- default/public `CallPlan` completion behavior is unchanged; deferral requires explicit reviewed product binding;
- the public Android `LocalTextCallSession.create(...)` path does not automatically bind Gate D product execution;
- no generic effect executor was introduced;
- model/parser/shadow/storage/reducer/synthetic input do not own dialing, target widening, plaintext disclosure, speech/TTS release, user confirmation, commitment or factual completion;
- accepted graph effects remain inert data until an existing application owner consumes them.

The implementation also includes the application-owned `CustomTaskGraphCore`, replay/evidence, `TaskGraphApplyBridge`, reusable `AppointmentInterpreter`, categorical `DialogueFit` + hysteresis, bounded shadow/supervisor validation, `AuthorizedFactSnapshot` / `FactDisclosurePolicy`, host `PersistentIdentityVault`, and Android Keystore-backed IdentityVault storage.

## Verification checkpoint

Final no-phone code checkpoint before this documentation close-out:

```text
cefe6492c7e714a8124e08cb1f42a68554955832
```

Evidence:

- `chatgpt-gated-book-appointment-completion-red-v041-20260923` — expected RED on missing completion-owner APIs;
- `chatgpt-gated-book-appointment-completion-green-v042-20260923` — targeted GREEN;
- `chatgpt-gated-book-appointment-completion-canonical-v043-20260923` — canonical GREEN for factual completion owner;
- `chatgpt-gated-android-completion-contract-build-v044-20260923` — Android completion contract compiled/packaged;
- `chatgpt-gated-final-canonical-v045-20260923` — `FINAL_GATE_D_NO_PHONE_CANONICAL_GREEN=true`.

Ready Android instrumentation contracts include:

- `AndroidGateDDeferredCompletionBindingContractTest`;
- `AndroidGateDBookAppointmentCompletionContractTest`;
- existing `AndroidGateDBookAppointmentCommitmentContractTest` regression.

## Physical proof status

Already physically proven on the target S22 without a cellular call:

- Android IdentityVault — `PROVEN_S22`;
- synthetic reviewed Gate D product ingress — `PROVEN_S22`;
- `BOOK_APPOINTMENT` proposal -> confirmation -> explicit user confirmation -> one-shot permit issuance — `PROVEN_S22 (no-call)`.

The newer deferred-completion and factual-completion owner boundaries are **not** labeled `PROVEN_S22` until their instrumentation tests actually run on the phone.

## Next exact gate

When the S22 is available again:

1. run the focused no-call Android deferred-completion and full BOOK_APPOINTMENT completion contracts;
2. if both pass, update evidence to `PROVEN_S22`;
3. re-check PR #5, merge `gate-d-taskgraph-core` to `main` if clean, then delete the work branch;
4. only after that consider a bounded live acceptance call.

A real call always requires fresh explicit authorization for the concrete target and task in the current chat/session. Documentation, old Local Agent results, a connected phone, or prior calls never carry that authorization forward.

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

It compiles/packages instrumentation tests but does not substitute for physical S22 execution.
