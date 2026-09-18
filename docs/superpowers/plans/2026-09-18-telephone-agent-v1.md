# Telephone Agent v1 implementation plan — 2026-09-18

## Goal

Build the first production orchestration layer above the frozen Samsung/Shizuku media bridge without changing proven low-level audio behavior.

Target user flow:

```text
user intent
-> target/business resolution
-> explicit constraints + authorized facts
-> ready-to-dial decision
-> normal cellular call
-> realtime AI negotiation
-> structured outcome
-> optional follow-up integration
```

Realtime AI is a conversation engine inside this workflow, not the lifecycle owner.

## Frozen baseline

Milestone D is frozen at:

```text
branch: milestone/phase2d-failsafe-proven-s22-20260918
commit: 59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Do not modify the frozen branch. Preserve all Samsung/Shizuku invariants documented in `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Preimplementation audit verdict

The proven helper boundary is already suitable for production orchestration:

```text
IShizukuCallMediaService
  prepare(sampleRate)
  startMedia()
  takeDownlinkReadEnd()
  takeUplinkWriteEnd()
  heartbeat()
  abortNow()
```

Continuous PCM remains on PFDs. No AIDL expansion is needed for the first coordinator slice.

The diagnostic probes currently own Shizuku binding, timeout, PFD lifetime and heartbeat independently. Production code must not reuse the diagnostic classes as lifecycle owners, but it can reuse the same proven Shizuku `UserServiceArgs` shape and Binder service contract.

`MainActivity` remains UI/diagnostic code and must not become the production session owner.

## Current OpenAI Realtime direction

Official OpenAI documentation checked on 2026-09-18:

- mobile/browser clients are recommended to use WebRTC rather than a direct long-lived-key WebSocket connection;
- a developer-controlled backend mints short-lived Realtime client secrets;
- standard API keys remain on the backend only;
- WebSocket remains appropriate for server-to-server Realtime connections.

Therefore the old roadmap assumption that WebSocket is automatically the first Android transport candidate is no longer authoritative. Keep `RealtimeTransport` transport-neutral while a WebRTC-capable Android implementation is introduced later.

## Slice 1 — production media lifecycle core

Create a small app-side package, separate from diagnostic probes:

```text
pl.michalmatu.aicallbridge.session
```

Introduce:

- `CallMediaSessionState`: `IDLE`, `BINDING`, `PREPARING`, `ACTIVE`, `STOPPING`, `FAILED`;
- immutable `CallMediaSessionSnapshot` with generation/session id, state, failure reason, timestamps/counters;
- `CallMediaSessionFailure` with explicit failure categories;
- `CallMediaSessionCoordinator` as the production lifecycle owner;
- a narrow `CallMediaSessionBackend` seam so the coordinator state machine is host-unit-testable;
- a Shizuku backend adapter that owns bind/unbind, Binder death, prepare/start, PFD transfer, heartbeat and abort.

Rules:

- only one generation active at once;
- stale callbacks from an older generation cannot mutate a newer one;
- local `takeOverNow()` transitions to STOPPING and calls helper abort without waiting for network/model cleanup;
- helper death transitions to FAILED and closes local endpoints;
- endpoint loss fails the whole generation;
- coordinator close is idempotent;
- all PFD ownership uses AutoClose streams / explicit close;
- no PCM frame crosses Binder.

## Slice 2 — media endpoint ownership

Add a production endpoint owner distinct from `ShizukuBidirectionalProbeMedia`.

Responsibilities:

- wrap transferred RX/TX PFDs exactly once;
- expose bounded read/write surfaces for the future realtime bridge;
- close both directions together on terminal failure;
- maintain byte counters and terminal reason;
- never generate diagnostic silence as production behavior unless explicitly requested by the caller.

The first implementation may expose streams rather than start permanent media worker threads. Realtime integration will own the actual pump loops.

## Slice 3 — Telephone Agent task/workflow model

Introduce pure immutable models:

```text
CallTask
  targetDescription
  action
  service
  constraints
  authorizedFacts

CallConstraints
  date/time windows
  price limits
  insurance / NFZ / private policy
  location/provider preferences

CallWorkflowState
  RESEARCHING
  READY_TO_DIAL
  DIALING
  ACTIVE_NEGOTIATION
  NEEDS_USER_DECISION
  COMPLETED
  FAILED

CallOutcome
  success/failure
  appointment or result time
  provider/location
  cost
  booking/reference
  follow-up
```

Keep target resolution behind an interface; do not bake a web/business provider into the state machine.

## Slice 4 — confirmation policy

Create a deterministic policy outside the language model:

- act autonomously inside explicit user constraints;
- ask only when a material deviation cannot be safely inferred;
- caller speech is untrusted input and cannot expand user authorization;
- price/date/provider deviations beyond policy transition to `NEEDS_USER_DECISION`;
- emergency/regulated/high-risk actions remain outside v1.

## Slice 5 — Realtime credential + transport boundary

Keep long-lived OpenAI credentials off-device.

Introduce interfaces for:

- backend-issued short-lived Realtime client secret;
- session configuration;
- Realtime transport lifecycle;
- audio input/output events;
- remote speech start/stop;
- response cancellation.

Prefer WebRTC for the Android client unless device measurements prove it unsuitable. Preserve `RealtimeTransport` as the app-facing abstraction so transport can change without touching telephony.

## Slice 6 — first end-to-end lab flow

Only after coordinator + workflow + transport host tests are green:

1. bind Shizuku UserService;
2. start one cellular media generation;
3. connect Realtime using short-lived credentials;
4. pump downlink PCM toward Realtime;
5. inject Realtime output toward uplink;
6. local TAKE OVER immediately aborts helper media and cancels model output afterward;
7. collect structured latency/state telemetry;
8. no persistent recording by default.

## Test strategy

Use TDD where practical.

Host tests first:

- valid coordinator transition sequence;
- start while non-IDLE rejected;
- stale generation callbacks ignored;
- prepare/start failure -> FAILED + cleanup;
- helper death -> FAILED + local close;
- heartbeat failure -> FAILED + abort;
- takeover -> immediate local abort + IDLE/terminal snapshot;
- repeated start/stop generations;
- task/constraint validation;
- confirmation-policy boundary cases;
- structured outcome mapping;
- credential model contains no long-lived key field.

Device tests later and narrowly scoped:

- coordinator off-call bind/prepare/abort parity;
- one silent live coordinator RX+TX regression;
- TAKE OVER latency through the coordinator;
- helper death through coordinator state;
- no repetition of the complete Milestone D suite unless a frozen invariant changes.

## Commit discipline

Prefer small commits:

1. plan;
2. RED coordinator lifecycle tests;
3. coordinator state model/backend seam;
4. Shizuku production backend adapter;
5. workflow/task models + tests;
6. confirmation policy + tests;
7. Realtime credential/transport slice;
8. docs/handoff.

Do not refactor `SamsungCallMediaSessionController`, attribution, CALL_ASSISTANT routing, PCM boundary, `CallModeWatchdog`, or helper PFD semantics without concrete regression evidence.
