# Handoff — Gate D appointment interpretation complete

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable base branch: `main`

Active work branch: `gate-d-taskgraph-core`

Pull request: `#5` — `Gate D TaskGraph v1 core` (draft)

Latest integrated host-green code checkpoint before this documentation closeout:

```text
cd91a2dbaafdf570c9e3e67b132c02cb88c0cf96
Stabilize media terminal-state assertion
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

Orange remains checkpointed and is not the active target.

## Current product goal

Active milestone remains:

```text
Gate D — hybrid multi-turn Task Engine
```

First acceptance task remains `BOOK_APPOINTMENT`.

## Completed boundaries on this branch

### TaskGraph / identity / simulator foundation

- application-owned `CustomTaskGraphCore` with typed state/event/transition/slot/effect IDs, pure guards, stale/version/state rejection, bounded recovery, effects as data and deterministic replay/evidence;
- v1 engine decision closed: no KStateMachine production runtime/dependency;
- `AuthorizedFactSnapshot` / `FactDisclosurePolicy` host contracts;
- deterministic `BOOK_APPOINTMENT` receptionist simulator using existing workflow/confirmation/commitment owners;
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

For an explicit host observer, the real finalized-turn selector preserves the existing PhraseMatrix/CallPlan route, creates one bounded observation, owns stale/cancel/close-safe session epochs, revalidates hypotheses through `SupervisorProposalValidator` and exposes redacted `DialogueFit`/candidate diagnostics only. Public Android session creation still binds no production shadow provider.

### Application-owned TaskGraph apply bridge

RED:

```text
8041ffa57181a6a5bf75581134b85d6d10347758
test: define Gate D TaskGraph apply bridge contracts
Android CI #473: Host quality gate failure as intended
```

GREEN:

```text
052f20ea11d20b97ade324ee734a1cff1c43bec3
Add application-owned TaskGraph apply bridge
Android CI #474: success
```

The bridge re-checks graph version/state/generation, exact application mapping, provenance, slot scope, authorization and schema before constructing a typed event/reducing. Rejections never call the reducer. Accepted effects remain inert data. The bridge has no workflow/speech/dial/plaintext identity/proposal approval/commitment API.

### Generic appointment interpretation — completed

RED contract checkpoint:

```text
9a56ddf2bdc58b27b2ca52fae982637930a9b250
test: define generic appointment interpretation contracts
Android CI #476: Host quality gate failure as intended
```

Implementation progression:

```text
e611c2b50f7480ffd6d2fbeefc3234b9fa2f340b  add pure AppointmentInterpreter
6e12ac60aca5ed1b6a5ea9525d6dc835f446f06c  delegate simulator offer parsing to shared interpreter
d78805c4b0ffcf3e46b56ebcf864636b5d53f75c  Kotlin callable-reference compile fix
61e5fc31e733f31d1d9eb535d083ed0e3baad4e5  simulator integration contracts
603c8418d0f974fa7c6bfdd3ade452fa06457b62  birth-date request morphology fix
cd91a2dbaafdf570c9e3e67b132c02cb88c0cf96  test-only media terminal synchronization fix
```

Current interpreter behavior:

- absolute dates (`YYYY-MM-DD`, Polish numeric forms);
- caller-anchored relative dates (`dzisiaj`, `jutro`, `pojutrze`);
- caller-anchored Polish weekdays;
- concrete times and bounded `od ... do ...` time ranges;
- concrete appointment offer candidate only when date + time are both resolved;
- a time range never silently collapses into one concrete offer;
- deterministic appointment dialogue acts for accept/reject/request-alternative via appointment-only PhraseMatrix rule IDs;
- identity request interpretation yields `IdentityFieldId` only, never a vault/plaintext value;
- relative/weekday parsing has deliberately no hidden `now()`; without explicit `referenceDate` it fails closed;
- `BookAppointmentSimulator` now uses this same interpreter.

Parser/matcher output remains candidate data. It does not mutate TaskGraph, workflow, confirmation, commitment, speech/output, dialing/target or identity disclosure state.

## Verification

Appointment RED evidence:

```text
commit: 9a56ddf2bdc58b27b2ca52fae982637930a9b250
workflow: Android CI
run number: 476
conclusion: failure
failed step: Host quality gate
```

Integrated GREEN evidence:

```text
commit: cd91a2dbaafdf570c9e3e67b132c02cb88c0cf96
workflow: Android CI
run id: 35761516859
run number: 482
conclusion: success
Host quality gate: success
```

The canonical workflow runs `bash scripts/verify_host.sh`, so GREEN covers app/module unit tests plus the repository's normal host checks.

No phone/device proof was required for this host-only slice.

## Media test root-cause note

While verifying appointment interpretation, `CallRealtimeMediaSessionTest.pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose` reappeared.

Audit result:

- each test owns a fresh fixture; no static/shared coordinator, endpoint or transport state was found;
- production `CallRealtimeMediaSession` first performs coordinator/pump/transport cleanup, then changes `STOPPING -> FAILED`, then calls the existing `onTerminalState` observer;
- the flaky test waited for `transport.close()` and immediately asserted `FAILED`, creating an asynchronous milestone race;
- test-only correction: wait for existing `onTerminalState` before asserting terminal session state;
- Android CI #482 is green with that synchronization;
- **no production media code, `CallMediaSessionCoordinator`, or `privileged-helper/` code changed**.

Keep the Samsung media path frozen.

## Hard stop line

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

The apply bridge may call `TaskGraphCore.reduce()` only after application-owned validation. Returned effects remain inert data until an existing owner is intentionally wired.

## Exact next host-only slice

Expand **deterministic replay/eval coverage**, RED first.

Create sequence-level scripted evaluation for:

1. ambiguity/parser incompleteness;
2. contradiction;
3. repeated deterministic unknowns and recovery exhaustion;
4. unavailable slots and alternate offers;
5. unauthorized/high-sensitivity fact requests;
6. user rejection;
7. takeover and cancellation;
8. stale supervisor results;
9. clean recovery toward deterministic handling.

Required evidence per scenario should keep business outcome, TaskGraph replay/evidence and `DialogueFit` reasons explicit.

Then decide from that corpus whether a small categorical hysteresis boundary is warranted. Do not add numeric model scoring. Any hysteresis remains non-authoritative routing/diagnostic policy: degradation must be immediate, and it cannot approve a transition, proposal, speech, dial, disclosure or commitment.

After the eval/hysteresis slice, the roadmap moves to Android IdentityVault persistence and then reviewed product shadow/apply integration.

## Frozen boundaries

Do not casually touch:

- `privileged-helper/`;
- frozen Samsung media path;
- physically proven `CallMediaSessionCoordinator` behavior;
- general-purpose phone-local llama.cpp product direction;
- Edge Gallery/Gemma experiment path;
- diagnostic runners/probes as product orchestrators.

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before any media change.

## Local Agent / Local Chat Bridge

- trust only a fresh binding envelope injected into the current/new chat;
- never copy an old `agent_binding` from documentation/history;
- one bridge conversation stays hard-bound to exactly one repository identity;
- use direct GitHub changes when exact diff + CI are sufficient;
- use Local Agent only when local Mac commands, Gradle/Android tools, ADB/device access or other machine-local evidence are actually needed;
- a queued task or ACK is not proof of success; inspect the terminal result;
- `.agent/tasks` and `.agent/results` are control/evidence data, not product architecture.

No fresh Local Chat Bridge binding envelope was used for these host-only commits. The user disconnected the phone after requesting continued host-only work, so no device/ADB/live-call task should be attempted until a future fresh authorization/binding exists.

## Live-call rule

No physical call was performed or authorized in this slice. Every future real call requires fresh user/operator authorization in that session and must follow `docs/ROADMAP.md` + `docs/SECURITY_PRIVACY.md`.
