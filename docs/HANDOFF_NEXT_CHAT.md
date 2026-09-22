# Handoff — Gate D host foundation complete; Android IdentityVault next

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable base branch: `main`

Active work branch: `gate-d-taskgraph-core`

Pull request: `#5` — `Gate D TaskGraph v1 core` (draft)

Latest host-green checkpoint at handoff:

```text
27457e3e103b89dac9f7e86a1b427f297128b7dc
Add encrypted IdentityVault persistence core
Android CI #488: success
```

This handoff is a state snapshot. It is **not** live-call authorization and contains no reusable Local Chat Bridge binding.

Use fresh repository state in the next chat. Do not trust an embedded SHA as current until it is re-fetched. `docs/ROADMAP.md` is the authoritative execution order; `docs/ARCHITECTURE.md` owns component boundaries; `docs/SECURITY_PRIVACY.md` owns privacy/live-call rules.

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

The work is no longer in architecture-selection mode. The remaining Gate D work is primarily safe product integration, Android IdentityVault persistence and physical proof on the existing S22 path.

## Physically proven vs host-only

### Already physically proven on Samsung S22+

The pre-existing cellular/media and deterministic local speech/text path remains:

```text
DONE / PROVEN_S22 / FROZEN
```

Do not casually modify `privileged-helper/`, Samsung media behavior or `CallMediaSessionCoordinator`.

During this chat the target phone was also re-identified read-only through Local Agent/ADB as Samsung `SM-S906B`, serial `RFCT70L7E8J`, Android 16 / SDK 36. That proves current ADB reachability only; it does **not** upgrade any new Gate D behavior to `PROVEN_S22` and does not authorize a call.

### Host-green Gate D foundation

The active branch now includes:

- application-owned `CustomTaskGraphCore` with typed state/event/transition/slot/effect IDs, pure guards, stale/version/state rejection, bounded recovery, effects-as-data and deterministic replay/evidence;
- closed v1 engine decision: no KStateMachine production runtime/dependency;
- typed `IdentityFieldId`, `AuthorizedFactSnapshot` and `FactDisclosurePolicy` contracts;
- deterministic `BOOK_APPOINTMENT` TaskGraph/simulator using existing workflow/confirmation/commitment owners;
- reusable `AppointmentInterpreter` for explicit-anchored relative dates/weekdays, times/ranges, concrete offer candidates, accept/reject/alternative semantics and identity-field request IDs;
- preserved `extract -> validate -> commit` semantics;
- categorical explainable `DialogueFit`;
- bounded `ShadowDialogueObservation` / `ShadowDialogueHypothesis`;
- fail-closed `SupervisorProposalValidator`;
- session-owned finalized-turn shadow lifecycle with generation/epoch invalidation and redacted diagnostics;
- application-owned `TaskGraphApplyBridge` that rechecks mapping, state, generation, provenance, slot scope, authorization and schema before `CustomTaskGraphCore.reduce()`;
- categorical `DialogueFitHysteresis` where deterioration is immediate and recovery requires consecutive deterministic evidence;
- sequence-level Gate D evaluation corpus covering recovery exhaustion, ambiguity/recovery, unacceptable/alternate offers, user rejection, unauthorized/high-sensitivity disclosure, cancel/takeover, stale supervisor results and clean recovery;
- host persistence core `PersistentIdentityVault` with typed secret wrapper, versioned encrypted envelope/payload, AEAD port, associated data, defensive copies, fail-closed decode/decrypt behavior and explicit `DEVICE_BOUND_NO_BACKUP` policy.

## Important recent checkpoints

```text
c2bd394df32f389aa6e2cb218df44a3b8b66de1d
Add categorical DialogueFit hysteresis
```

```text
a86034c77df23cd9375cad20a04e7609aef13956
Add Gate D sequence evaluation corpus
```

```text
502f13a1c9e5b8b0f7aae2383629e451a8d8821c
Persistent IdentityVault RED contract
```

```text
27457e3e103b89dac9f7e86a1b427f297128b7dc
Add encrypted IdentityVault persistence core
Android CI #488: success
```

Earlier appointment/shadow/apply RED->GREEN evidence remains valid in Git history and prior documented checkpoints; do not repeat those slices.

## Current authority stop line

The public Android session path still does **not** automatically:

- bind a production shadow provider;
- apply a shadow hypothesis;
- call `TaskGraphApplyBridge` from `LocalTextCallSession`;
- execute graph effects;
- mutate `CallWorkflow` from reducer output;
- release model speech/TTS;
- dial or widen a target;
- resolve/disclose plaintext IdentityVault values;
- approve a proposal;
- consume commitment authority.

This is intentional. Shadow/model/parser output remains candidate-only. `TaskGraphApplyBridge` may invoke the reducer only after application-owned validation, and reducer effects remain data until existing owners are deliberately wired.

## Exact next implementation order

### 1. Android IdentityVault production adapter — next slice

Do this before any real call that needs personal data.

Start with a narrow seam audit of the new host ports in `PersistentIdentityVault.kt` and the requirements in `docs/SECURITY_PRIVACY.md`. Then use RED -> minimal GREEN.

Implement Android-owned adapters for:

- app-private ciphertext storage with atomic write semantics;
- a non-exportable Android Keystore key;
- authenticated encryption, expected to be AES/GCM unless a concrete platform constraint proves otherwise;
- stable algorithm identity + associated-data use compatible with the host core;
- key creation/reuse and fail-closed behavior when ciphertext/key/version data is invalid;
- explicit device-bound/no-backup semantics;
- no plaintext secrets in logs, diagnostics, Git, Local Agent task JSON or supervisor context.

Do **not** use deprecated `EncryptedSharedPreferences` / `MasterKey` for new persistence.

Keep disclosure policy separate from encrypted storage. A value existing in the vault still does not authorize disclosure.

### 2. Product shadow/apply integration

After the Android IdentityVault persistence boundary is host/Android-green, intentionally wire the reviewed product seam:

```text
finalized turn
 -> deterministic interpretation first
 -> optional bounded shadow proposal
 -> SupervisorProposalValidator
 -> application-owned current-state/apply policy
 -> TaskGraphApplyBridge
 -> effects as data
 -> existing workflow / proposal / confirmation / commitment / output owners
```

Do not introduce a generic effect executor that bypasses existing application owners.

### 3. Integration verification and device proof

Run targeted tests + `bash scripts/verify_host.sh` / canonical Android CI for every deterministic slice.

Use Local Agent only for developer-machine/Android/ADB evidence. Physical tests should prove only the boundary that changed. Android IdentityVault/device behavior may be tested on the connected S22 without making a cellular call.

Host evidence remains `HOST_GREEN`; device evidence must be explicitly reproduced before using `PROVEN_S22`.

### 4. Real-world BOOK_APPOINTMENT gate

Only after the required identity handling and product integration are proven.

A future physical call requires fresh explicit authorization in that chat/session. Never infer authorization from this handoff, an old allowlist, previous `.agent/results` or the fact that the phone is connected.

For test-only ordinary reception/business validation, disclose AI/test purpose at the start, obtain consent and do not create a real booking. Genuine booking requires fresh user authorization plus normal fact-disclosure, proposal, confirmation and one-shot commitment gates.

## Known non-product issue

`CallRealtimeMediaSessionTest.pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose` previously exposed a test synchronization race: `transport.close()` may precede the final `STOPPING -> FAILED` publication. The test was corrected to wait on the existing terminal-state callback. No production media/helper code was changed. If this test flakes again, investigate test ordering/synchronization before touching frozen media.

## Frozen / deferred boundaries

Do not casually touch:

- `privileged-helper/`;
- frozen Samsung cellular/media path;
- physically proven `CallMediaSessionCoordinator` behavior;
- general-purpose phone-local llama.cpp product direction;
- Edge Gallery/Gemma experiment path;
- diagnostic runners/probes as product orchestrators;
- broad Orange mapping unless a concrete product need resumes it.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Local Agent / Local Chat Bridge

If Local Chat Bridge is used in the new window:

- trust only the fresh binding envelope injected into that chat;
- never copy an old `agent_binding` from this file, chat history, Git history or an old task;
- work only in the exact bound repository;
- inspect fresh daemon/current-task evidence before local work;
- use direct GitHub changes for bounded reviewable source/docs diffs when CI evidence is sufficient;
- use Local Agent for local Gradle/Android tooling, ADB/device state and physical evidence;
- queue/ACK is not success — inspect exact terminal result;
- never launch local Codex from a Local Agent task.

No current binding is persisted here intentionally.

## New-chat bootstrap

Use:

```text
docs/NEXT_CHAT_PROMPT.md
```

That prompt is intentionally short and points back to these authoritative sources instead of duplicating hidden chat history.
