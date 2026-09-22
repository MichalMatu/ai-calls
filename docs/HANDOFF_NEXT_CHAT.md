# Handoff — Gate D Android vault + reviewed product integration host-green

Date: 2026-09-22

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

The project is no longer in TaskGraph architecture-selection mode. Current work is safe application integration and physical proof of already-defined boundaries.

## Current code checkpoints

### Android IdentityVault production adapter

```text
3b79d42012d80c7cc6956bac77f590bfae20dc72
Fix Android IdentityVault test method names
Android CI #494: success
```

The preceding RED proved the instrumentation contract failed only because the Android adapter classes did not yet exist. Production implementation now provides:

- `AndroidIdentityVault.create(...)`;
- `AndroidIdentityVaultBlobStorage` under `Context.noBackupFilesDir` using `AtomicFile` replacement;
- `AndroidKeystoreIdentityVaultAead` using Android Keystore AES-256/GCM/NoPadding;
- non-exportable SecretKey expectation;
- create-on-first-encrypt and reuse behavior;
- decrypt-only-existing-key behavior so missing/invalid keys fail closed instead of silently replacing ciphertext;
- stable algorithm ID + AAD use;
- ordinary diagnostics without plaintext secret values.

Canonical CI compiles/packages `AndroidIdentityVaultContractTest`, but the instrumentation tests have **not** yet been executed on the physical S22 in this checkpoint. Therefore this boundary is `HOST_GREEN`, not `PROVEN_S22`.

### Reviewed Gate D product shadow/apply integration

RED checkpoint:

```text
86dcbb78d1f22efdc9d3d5b443524a32cb810778
Add RED Gate D product integration contracts
```

The RED gate failed exactly on missing product-binding seams after production compilation reached the new test contract.

GREEN implementation:

```text
fcd16bbffb40d1262b45d61dd22e53f7835162fb
Add reviewed Gate D product integration seam
```

A Kotlin constructor-delegation compile issue was then fixed without behavior change:

```text
a355f604484a78d7a99d7594455f3344ca081e04
Fix Gate D shadow constructor delegation
Android CI #497: success
```

The final canonical gate passed host unit tests, lint, app build, Android test APK build, Python tests and repository scans.

The new internal composition includes:

- `GateDFinalizedTurn` with redacted ordinary diagnostics;
- candidate-only `GateDDeterministicCandidateInterpreter`;
- application-owned `GateDAuthorizedSlotIdsProvider`;
- inert `GateDTaskGraphApplyResultListener`;
- explicit `LocalTextCallGateDProductBinding`;
- `LocalTextCallGateDProductIntegration` owning only the current TaskGraph snapshot/apply sequence;
- dynamic current-snapshot support in `LocalTextCallGateDShadowLifecycle` while preserving the old fixed-snapshot host path;
- explicit internal `LocalTextCallSession` constructor for reviewed product binding.

Contracts prove:

1. deterministic candidate apply happens before optional shadow;
2. shadow observes the updated current snapshot/generation;
3. deterministic rejection fails closed and does not fall through to shadow;
4. shadow candidates pass `SupervisorProposalValidator` and still face a fresh application-owned slot-authorization check at apply time;
5. accepted effects are observed only as `TaskGraphApplyResult` data;
6. `CallWorkflow` is not mutated by this integration seam;
7. cancel invalidates queued product shadow before observer/apply.

## Current authority stop line

The public Android `LocalTextCallSession.create(...)` path still does **not** automatically bind:

- a production shadow provider;
- `LocalTextCallGateDProductBinding`;
- automatic shadow apply;
- an effect executor.

The reviewed internal product path follows:

```text
finalized turn
 -> deterministic interpretation first
 -> optional bounded shadow proposal
 -> SupervisorProposalValidator
 -> application-owned current-state / slot-authorization re-check
 -> TaskGraphApplyBridge
 -> effects as inert data
 -> existing workflow / proposal / confirmation / commitment / output owners
```

Shadow/model/parser/storage/reducer still have no authority to:

- dial or widen a target;
- resolve/disclose plaintext facts;
- release speech/TTS;
- approve a proposal;
- approve user confirmation;
- consume commitment authority;
- claim completion authority.

Do not add a generic effect executor.

## Exact next implementation order

### 1. S22 Android IdentityVault proof — no cellular call

Requires a fresh Local Chat Bridge / Local Agent binding in the new chat.

Execute the instrumentation contract on the target S22 and capture terminal evidence for:

- no-backup app-private ciphertext file;
- atomic overwrite behavior;
- Android Keystore AES key creation and reuse;
- non-exportability;
- AES/GCM/AAD behavior;
- corruption / unsupported record / missing-or-invalid key fail-closed cases;
- no plaintext secret in ordinary diagnostics/durable bytes.

Do not label this `PROVEN_S22` before physical execution.

### 2. Android/S22 product-binding integration proof — still no cellular call

Exercise reviewed session wiring on device/Android boundaries and prove:

- deterministic-first order;
- current snapshot generation progression;
- optional shadow only when deterministic interpreter returns no candidate;
- stale/cancel behavior;
- apply-time authorization re-check;
- public Android create path remains non-automatic.

### 3. Bounded `BOOK_APPOINTMENT` owner wiring

Only after the device proof above, map the minimum required TaskGraph effects to existing application owners. Preserve proposal -> user confirmation -> one-shot commitment and late `FactDisclosurePolicy`-approved plaintext resolution.

Wire one reviewed effect/owner boundary at a time; do not create a generic executor.

### 4. Real-world gate

Only after simulation/host/device integration is strong.

Every live call requires fresh explicit target/task authorization in that session. The fact that the Samsung is connected is not authorization. No authorization from this handoff, an old chat, an old allowlist or `.agent/results` carries forward.

For test-only ordinary reception/business validation, disclose AI/test purpose at the start, obtain consent and create no real booking. Genuine booking requires fresh user authorization plus the normal fact-disclosure, proposal, confirmation and one-shot commitment gates.

## Frozen boundaries

Do not casually touch:

- `privileged-helper/`;
- frozen Samsung cellular/media path;
- physically proven `CallMediaSessionCoordinator` behavior.

If `CallRealtimeMediaSessionTest.pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose` returns, inspect test ordering/synchronization/test pollution first. The known prior root cause was a test milestone race and the correction was test-only.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Local Agent / Local Chat Bridge

If Local Chat Bridge is used in the next chat:

- trust only the fresh binding envelope injected into that chat;
- never copy an old `agent_binding` from this file, history, Git or old tasks;
- inspect fresh daemon/current-task evidence before local work;
- work only in the exact bound repository;
- use GitHub for bounded reviewable source/docs/data diffs;
- use Local Agent for local Gradle/Android tooling, ADB/Keystore/device evidence;
- queue/ACK is not success — require the terminal task result;
- never launch local Codex from a Local Agent task.

No current binding is persisted here intentionally.

## New-chat bootstrap

Use `docs/NEXT_CHAT_PROMPT.md`.
