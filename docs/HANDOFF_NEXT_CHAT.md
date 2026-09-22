# Handoff — Gate D synthetic no-call ingress ready for S22 proof

Date: 2026-09-23

Repository: `MichalMatu/android-ai-call-bridge`

Durable base branch: `main`

Active work branch: `gate-d-taskgraph-core`

Pull request: `#5` — `Gate D TaskGraph v1 core` (draft)

This handoff is a state snapshot. It is **not** live-call authorization and contains no reusable Local Chat Bridge binding. Always fetch fresh branch/PR state before work; documentation commits may make the live HEAD newer than the code checkpoints below.

## Read first

1. `AGENTS.md`
2. `README.md`
3. `docs/HANDOFF_NEXT_CHAT.md`
4. `docs/ROADMAP.md`
5. `docs/ARCHITECTURE.md`
6. `docs/GATE_D_TASKGRAPH_V1_AUDIT_2026-09-22.md`
7. `docs/SECURITY_PRIVACY.md`
8. `docs/HANDOFF_PROTOCOL.md`
9. `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media

Orange remains checkpointed and is not the active target.

## Current product goal

Active milestone remains:

```text
Gate D — hybrid multi-turn Task Engine
```

Primary acceptance task remains:

```text
BOOK_APPOINTMENT
```

The architecture-selection/audit phase is complete. Current work is physical proof of already-defined Android boundaries, followed by bounded application-owner wiring.

## Current verified checkpoints

### Android IdentityVault adapter

```text
3b79d42012d80c7cc6956bac77f590bfae20dc72
Android CI #494: success
```

Production adapter provides app-private `noBackupFilesDir + AtomicFile` ciphertext storage and Android Keystore AES-256/GCM with non-exportable key expectations, key create/reuse, stable algorithm identity/AAD and fail-closed missing/invalid-key behavior.

Its instrumentation contract is compiled/packaged by canonical CI but has not yet been physically executed on the S22. Status remains `HOST_GREEN`, not `PROVEN_S22`.

### Reviewed Gate D product integration

```text
a355f604484a78d7a99d7594455f3344ca081e04
Android CI #497: success
```

The reviewed internal binding preserves:

```text
finalized turn
 -> deterministic interpretation first
 -> optional bounded shadow proposal
 -> SupervisorProposalValidator
 -> application-owned current-state / slot-authorization re-check
 -> TaskGraphApplyBridge
 -> effects as inert data
 -> existing application owners only
```

Deterministic rejection fails closed and cannot fall through to shadow. Cancel invalidates queued product shadow. The public Android `LocalTextCallSession.create(...)` path still does not auto-bind the product integration or an effect executor.

### Synthetic finalized-text ingress

RED contract:

```text
e10f5d3de036f8d76b2276cd1b61fc5ae45c7c1b
Android CI #499: expected failure at Host quality gate
```

GREEN implementation:

```text
79e6dabd62cb325b41bc37615252165fe563c3e4
Android CI #500: success
```

Android instrumentation contract:

```text
313c6bb43251bc2cdd06e5e783a22154ac378f49
Android CI #505: success
```

`LocalTextCallSession.injectSyntheticFinalTranscript(...)` is now an explicit internal test/diagnostic ingress for text already considered final. It converges with normal STT output at the same shared finalized-turn processing path:

```text
STT-finalized text --------+
                           +-> shared finalized-turn ingress
synthetic finalized text --+     -> PhraseMatrix / CallPlan
                                  -> deterministic Gate D interpretation
                                  -> optional bounded shadow
                                  -> current-state / authorization re-check
                                  -> TaskGraphApplyBridge
                                  -> effects as data
```

Synthetic input bypasses speech-pipeline start, PCM input, STT, backend generation and TTS/media output. It does not create a second state machine or new authority boundary. Any structured CallPlan/workflow behavior remains owned by exactly the same existing owners/policies as on the STT-finalized path.

The host deterministic SAY contract proves Gate D apply and route selection while speech/media/backend paths stay untouched; its fixture also verifies no `CallWorkflow` mutation for that SAY case. The Android contract is compiled/packaged and ready for physical no-call execution on S22, but has not yet run there.

## Current authority stop line

Neither the synthetic ingress, shadow, model, parser, storage nor reducer may independently:

- dial or widen a target;
- resolve/disclose plaintext identity facts;
- release speech/TTS;
- approve a proposal;
- approve user confirmation;
- consume commitment authority;
- claim completion authority.

No generic effect executor exists or should be introduced.

Plaintext IdentityVault values remain late-bound through:

```text
AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> current task / target / state / generation
 -> optional user approval
```

## Exact next order — all still before any live call

### 1. S22 Android IdentityVault proof

Requires a **fresh** Local Chat Bridge / Local Agent binding for this chat/repository. No cellular call is required.

Physically execute the existing instrumentation contract on the target S22 and capture terminal evidence for:

- app-private no-backup ciphertext storage;
- atomic replacement;
- Android Keystore AES key creation/reuse;
- non-exportability;
- AES/GCM + AAD/algorithm identity;
- corruption / unsupported record / missing-or-invalid key fail-closed cases;
- no plaintext secret in ordinary diagnostics/durable bytes.

### 2. S22 reviewed product-binding proof with synthetic text

Still **no cellular call**.

Execute `AndroidGateDProductSyntheticInputContractTest` and targeted session regressions on the physical S22. Verify deterministic-first behavior, current generation progression, optional shadow, stale/cancel handling, apply-time authorization re-check and non-automatic public Android wiring.

Use the synthetic finalized-text ingress to exercise post-STT product flow without touching the frozen media path when audio itself is not under test.

### 3. Bounded `BOOK_APPOINTMENT` owner wiring

Only after both physical device proofs pass, map the minimum required TaskGraph effects to existing application owners, one reviewed boundary at a time. Preserve proposal -> user confirmation -> one-shot commitment and late disclosure through `FactDisclosurePolicy`.

Do not create a generic executor and do not transfer authority into model/parser/shadow/reducer/storage/synthetic input.

### 4. Canonical regressions + docs

After owner wiring, run targeted regressions and the canonical host/Android verification, then refresh this handoff/roadmap with the actual evidence.

### 5. STOP before live call

After all no-call work is green, stop before dialing. Every live call requires fresh explicit authorization for the concrete target and task in the current chat/session. A connected phone, prior call, old allowlist, old `.agent/results`, this handoff or an old chat is not authorization.

No cellular call was attempted or performed in the work captured by this handoff.

## Why work stops here in this chat

No fresh Local Chat Bridge / Local Agent binding is available in the current chat/tooling, so physical S22 instrumentation cannot be truthfully claimed here. The new Android contracts are prepared and CI-green, but they remain `HOST_GREEN` until executed on the device.

Because the authoritative roadmap requires those device proofs before bounded `BOOK_APPOINTMENT` owner wiring, that wiring was intentionally not started rather than bypassing the gate.

## Frozen boundaries

No changes were made to:

- `privileged-helper/`;
- frozen Samsung cellular/media path;
- physically proven `CallMediaSessionCoordinator` behavior.

If `CallRealtimeMediaSessionTest.pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose` returns, inspect test ordering/synchronization/test pollution first. The known prior root cause was a test milestone race and the correction was test-only.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Local Agent / Local Chat Bridge

For the next session:

- trust only the fresh binding envelope injected into that chat;
- never copy an old `agent_binding` from this file, history, Git or old tasks;
- inspect fresh daemon/current-task evidence before local work;
- work only in the exact bound repository;
- use GitHub for bounded reviewable source/docs/data diffs;
- use Local Agent for local Gradle/Android tooling, ADB/Keystore/device evidence;
- queue/ACK is not success — require the terminal task result;
- never launch local Codex from a Local Agent task.

No binding is persisted here intentionally.

## New-chat bootstrap

Use `docs/NEXT_CHAT_PROMPT.md`.
