# Handoff — Gate D TaskGraph apply bridge complete

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable base branch: `main`

Active work branch: `gate-d-taskgraph-core`

Pull request: `#5` — `Gate D TaskGraph v1 core` (draft)

Code checkpoint before this documentation closeout:

```text
052f20ea11d20b97ade324ee734a1cff1c43bec3
Add application-owned TaskGraph apply bridge
```

This handoff is a state snapshot. It is **not** live-call authorization and contains no reusable Local Chat Bridge binding.

Use fresh repository state in the next chat. `docs/ROADMAP.md` is the authoritative execution order; `docs/ARCHITECTURE.md` owns component boundaries; `docs/SECURITY_PRIVACY.md` owns privacy/live-call rules.

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

Orange remains relevant only when intentionally resumed; it is not the active target.

## Current product goal

Active milestone remains:

```text
Gate D — hybrid multi-turn Task Engine
```

First acceptance task remains `BOOK_APPOINTMENT`.

## What is complete on the work branch

### TaskGraph / identity / simulator foundation

- application-owned `CustomTaskGraphCore` with typed state/event/transition/slot/effect IDs, pure guards, stale/version/state rejection, bounded recovery, effects as data and deterministic replay/evidence;
- engine decision closed for v1: no KStateMachine production runtime/dependency;
- `AuthorizedFactSnapshot` / `FactDisclosurePolicy` host contracts;
- deterministic `BOOK_APPOINTMENT` receptionist simulator composing existing workflow/confirmation/commitment owners;
- categorical `DialogueFit`;
- bounded `ShadowDialogueObservation` / `ShadowDialogueHypothesis`;
- fail-closed `SupervisorProposalValidator`;
- `TaskGraphDefinition + AuthorizedFactSnapshot` binding through Android/LocalPhone readiness -> coordinator -> prepared call -> session;
- read-only `LocalTextCallGateDRuntime`.

### Finalized-turn shadow lifecycle

Code checkpoint:

```text
4f7dcdd9051bc2090b5dde9ede3d688d17655f1e
Add session-owned Gate D shadow finalized-turn lifecycle
```

For an explicit host observer, the real finalized-turn selector preserves the existing PhraseMatrix/CallPlan route, creates one bounded observation, owns stale/cancel/close-safe session epochs, revalidates hypotheses through `SupervisorProposalValidator` and exposes redacted `DialogueFit`/candidate diagnostics only. The public Android session path still binds no production shadow provider.

### Application-owned TaskGraph apply bridge — completed in this slice

RED contract commit:

```text
8041ffa57181a6a5bf75581134b85d6d10347758
test: define Gate D TaskGraph apply bridge contracts
```

Android CI #473 failed at the Host quality gate as expected because the apply bridge API/types were not implemented. Setup/JDK/Gradle/SDK steps passed.

Minimal GREEN code checkpoint:

```text
052f20ea11d20b97ade324ee734a1cff1c43bec3
Add application-owned TaskGraph apply bridge
```

Implemented as a standalone `taskgraph` boundary without changing session/providers/media/workflow:

- `TaskGraphApplyCandidate` carries typed generation, transition ID, slot candidates and provenance only;
- `TaskGraphApplyPolicy` owns allowed transition-to-event mappings, provenance and slot rules;
- the bridge re-checks graph version, known/current state and candidate generation at apply time;
- the mapped transition must exist, be legal from the current state and map to the exact application-owned event ID;
- provenance must be explicitly allowed;
- candidate slots must be mapped, dynamically authorized and schema/constraint-valid;
- required slots must be present;
- authority-bearing slot IDs such as speech/dial/target/commitment/identity-value metadata are rejected again as defense in depth;
- all of those checks occur before event construction/reduction;
- rejected candidates never call `TaskGraphCore.reduce()`;
- accepted candidates create one typed `TaskGraphEvent`, call the reducer once and return immutable snapshot/event record/effects as data;
- no workflow, speech/TTS, dialing/target, plaintext IdentityVault, proposal approval or commitment API exists in the bridge.

The bridge is **not** automatically wired to `LocalTextCallSession` or the host shadow lifecycle. Shadow output remains non-authoritative and the public Android path still has no production observer/provider.

## Verification

RED evidence:

```text
commit: 8041ffa57181a6a5bf75581134b85d6d10347758
workflow: Android CI
run id: 35757487319
run number: 473
conclusion: failure
failed step: Host quality gate
```

GREEN evidence:

```text
commit: 052f20ea11d20b97ade324ee734a1cff1c43bec3
workflow: Android CI
run id: 35757837255
run number: 474
conclusion: success
```

The canonical workflow runs `bash scripts/verify_host.sh`, so the GREEN result covers the new focused apply contracts plus the existing app/module unit tests, lint/build checks, Python tests and repository policy scans.

The slice diff from the previous documentation checkpoint `01bb372d8355aab5cda52ee558bb0dda94302382` to the GREEN code checkpoint contains exactly two files: `TaskGraphApplyBridge.kt` and `TaskGraphApplyBridgeTest.kt`.

No physical call or device proof was required or authorized for this host-only slice.

## Hard stop line at this checkpoint

Do not confuse an explicit reducer boundary with side-effect authority.

Current product/session code still does **not** automatically:

- apply a shadow hypothesis;
- call `TaskGraphApplyBridge` from `LocalTextCallSession`;
- execute graph effects;
- mutate `CallWorkflow` from reducer output;
- release model speech/TTS;
- dial or widen a target;
- read/disclose plaintext IdentityVault values;
- approve proposals;
- consume commitment authority.

The apply bridge itself may call `TaskGraphCore.reduce()` only after its application-owned validation checks pass. Returned effects remain inert data until an existing owner is explicitly wired to consume them.

## Exact next slice

Finish **generic appointment interpretation**, RED first.

Extract simulator-local interpretation into reusable typed parsers/normalizers for:

1. dates, relative dates and weekdays;
2. times and time ranges;
3. offered appointment candidates;
4. accept/reject/alternative semantics;
5. common identity-field requests.

Add PhraseMatrix dialogue-act coverage where deterministic phrases are appropriate.

Required boundaries:

- parser/matcher output is candidate data only;
- do not directly mutate TaskGraph context from a parser;
- preserve `extract -> validate -> commit`;
- final authoritative apply still goes through application validation + `TaskGraphApplyBridge`;
- keep `CallWorkflow`, confirmation, commitment, speech/output approval and disclosure policy as existing owners;
- do not broad-refactor session/provider/media while extracting interpretation.

After that, expand deterministic replay/eval scenarios and calibrate `DialogueFit`/hysteresis from evidence before product shadow/apply wiring.

## Frozen boundaries

Do not casually touch:

- `privileged-helper/`;
- frozen Samsung media path;
- physically proven `CallMediaSessionCoordinator` behavior;
- general-purpose phone-local llama.cpp product direction;
- Edge Gallery/Gemma experiment path;
- diagnostic runners/probes as product orchestrators.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

If `CallRealtimeMediaSessionTest.pumpFailure...` reappears, first audit test order/pollution by running the media test separately and alongside Gate D tests. Do **not** patch frozen media as part of Gate D without a separate root-cause result and explicit scope decision.

## Local Agent / Local Chat Bridge

- trust only a fresh binding envelope injected into the current/new chat;
- never copy an old `agent_binding` from documentation/history;
- one bridge conversation stays hard-bound to exactly one repository identity;
- use direct GitHub changes when exact diff + CI are sufficient;
- use Local Agent when local Mac commands, Gradle/Android tools, ADB/device access or other machine-local evidence are needed;
- before a local task, read fresh bound-repository daemon/current-task evidence;
- a queued task or ACK is not proof of success; inspect the exact terminal result;
- `.agent/tasks` and `.agent/results` are control/evidence data, not product architecture;
- never restart Local Agent merely to hide an unclear task/root cause.

No fresh Local Chat Bridge binding envelope was used for this completed code slice; GitHub + canonical Android CI supplied the required source/execution evidence.

## Live-call rule

No physical call was performed or authorized in this slice. Every future real call requires fresh user/operator authorization in that session and must follow `docs/ROADMAP.md` + `docs/SECURITY_PRIVACY.md`.
