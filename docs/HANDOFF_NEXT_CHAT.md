# Handoff — Gate D finalized-turn shadow lifecycle complete

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable base branch: `main`

Active work branch: `gate-d-taskgraph-core`

Pull request: `#5` — `Gate D TaskGraph v1 core` (draft)

Code checkpoint before this documentation closeout:

```text
4f7dcdd9051bc2090b5dde9ede3d688d17655f1e
Add session-owned Gate D shadow finalized-turn lifecycle
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

### Finalized-turn shadow lifecycle — completed in this slice

RED contract commit:

```text
1a58e174bafa0d9a03334e8ff14906601539b129
test: define Gate D finalized-turn shadow lifecycle contracts
```

Android CI #470 failed at the host quality gate as expected because the new session shadow seam/types were not implemented yet. Setup/JDK/Gradle/SDK steps passed.

Minimal GREEN code checkpoint:

```text
4f7dcdd9051bc2090b5dde9ede3d688d17655f1e
Add session-owned Gate D shadow finalized-turn lifecycle
```

Implemented without changing providers/media:

- real `LocalTextCallSession` plan selector computes the existing PhraseMatrix/CallPlan result first and returns the same route;
- when an explicit host shadow observer is bound, that finalized turn creates one bounded observation from the bound TaskGraph snapshot/context;
- `AuthorizedFactSnapshot` fact IDs are exposed only when generation/state/sensitivity scope matches; plaintext fact values are never added;
- a session-owned epoch invalidates older queued shadow turns;
- `cancel()` / `close()` invalidate pending work; close also closes the shadow executor;
- observer/validator exceptions cannot alter deterministic routing;
- hypotheses pass through existing `SupervisorProposalValidator`;
- accepted/rejected candidate metadata may feed categorical `DialogueFit` diagnostics only;
- ordinary diagnostics expose typed IDs/status/reasons, not transcript, identity values, slot candidate values or model diagnostic values.

The public Android `LocalTextCallSession.create(...)` path still binds no production shadow observer/provider. This slice is intentionally host-only.

## Verification

Canonical full repo gate for GREEN:

```text
commit: 4f7dcdd9051bc2090b5dde9ede3d688d17655f1e
workflow: Android CI
run id: 35752853447
run number: 471
conclusion: success
```

The canonical workflow runs `bash scripts/verify_host.sh`, including app/module unit tests, lint/build checks, Python tests and repository policy scans. The new focused Gate D lifecycle tests are therefore covered by the full host run.

No physical call or device proof was required or authorized for this host-only slice.

## Hard stop line at this checkpoint

The shadow/session boundary remains non-authoritative. It does **not**:

- call `TaskGraphCore.reduce()`;
- execute graph effects;
- mutate `CallWorkflow`;
- release model speech/TTS;
- dial or widen a target;
- read/disclose plaintext IdentityVault values;
- approve proposals;
- consume commitment authority.

Do not collapse this boundary into the observer in the next slice.

## Exact next slice

Add a separate explicit **application-owned TaskGraph apply bridge**, RED first.

Target:

```text
already validated deterministic/supervisor candidate
 -> re-check current generation/state
 -> application-owned legal transition/event mapping
 -> validate slot type/schema/constraints/provenance/authorization
 -> typed TaskGraph event
 -> CustomTaskGraphCore.reduce()
 -> effects as data
 -> existing workflow / proposal / confirmation / commitment / output owners
```

Required RED contracts should prove:

1. stale generation cannot reduce;
2. an illegal/unmapped transition cannot become an event;
3. invalid, unauthorized or authority-bearing candidate slots cannot enter authoritative TaskGraph context;
4. `extract -> validate -> commit` is preserved;
5. accepted reduction returns effects as data only;
6. reducer output does not directly speak, dial, mutate workflow or consume commitment;
7. existing deterministic PhraseMatrix/CallPlan behavior remains unchanged.

Use the smallest owner/seam that preserves existing authority boundaries. Do not broad-refactor provider/session/media code.

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

- trust only a fresh binding envelope injected into the new chat;
- never copy an old `agent_binding` from documentation/history;
- work only in the exact bound repository;
- use fresh daemon/current-task evidence before queueing local work;
- `.agent/tasks` and `.agent/results` are control/evidence data, not product architecture;
- never restart Local Agent merely to hide an unclear task/root cause.

## Live-call rule

No physical call was performed or authorized in this slice. Every future real call requires fresh user/operator authorization in that session and must follow `docs/ROADMAP.md` + `docs/SECURITY_PRIVACY.md`.
