# Project Roadmap

This roadmap is evidence-driven. A capability advances only when its required host/device gate is actually proven.

## Core product goal

Use one stock Samsung phone to complete user-authorized tasks over an ordinary cellular call:

```text
user task + explicit authority
  -> resolve counterparty
  -> cellular call
  -> cellular downlink -> Realtime AI
  -> Realtime AI -> guarded cellular uplink
  -> structured outcome
  -> optional later integrations such as calendar
```

The user must always be able to TAKE OVER immediately. Failure must fall back toward a normal human call rather than leave AI injection active.

## Evidence levels

- `HYPOTHESIS` — plausible, not demonstrated;
- `HOST_GREEN` — deterministic host/unit/build gates pass;
- `PROVEN_S22` — physically reproduced on the target S22+;
- `FAILED_S22` — physically/reproducibly failed on the S22+;
- `PRODUCT_READY` — proven, stable, fail-safe and acceptable for normal use.

## Frozen references

```text
Phase 2B local media
milestone/phase2b-proven-s22-20260916
c10f8dde29f245f8f98fb008a3572c21fe73fe35

Phase 2C Shizuku parity
milestone/phase2c-shizuku-live-proven-20260916
9c136fc05c5b33f383d72b0b7080ad5b9a754bb4

Phase 2D fail-safe/endurance
milestone/phase2d-failsafe-proven-s22-20260918
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Do not move or rewrite these milestone branches during normal development.

## Phase 0 — repository/test discipline

Status: `DONE`

Modular Android project, host regression discipline, explicit privilege boundaries, durable device evidence, and Local Agent workflow are established.

## Phase 1 — cellular media capability

Status: `DONE / PROVEN_S22`

Proven production directions:

```text
RX: VOICE_DOWNLINK -> privileged helper -> PFD -> normal app
TX: normal app -> PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Generic media / voice-communication TX paths are not the production path on this S22.

## Phase 2 — stable local bridge

Status: `DONE / PROVEN_S22 / FROZEN`

Milestone D physical proof covers:

- app death cleanup;
- helper/UserService death cleanup;
- RX/TX endpoint-loss whole-generation cleanup;
- 20/20 start/abort cycles;
- natural cellular call end after `CallModeWatchdog`;
- 600 seconds total live bidirectional media as 10 x 60 seconds;
- a separate 50.1-second external resource trend with stable FDs/threads and roughly 1 MiB RSS growth per process.

Do not claim the 50.1-second resource run covers all 600 seconds.

Detailed evidence: `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Phase 3 — Telephone Agent / OpenAI Realtime

Status: `IN PROGRESS — SUBSTANTIAL HOST INTEGRATION COMPLETE, REAL OPENAI S22 SESSION NOT YET PROVEN`

Current detailed ledger: `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`.

Behavior baseline at the current handoff:

```text
94594aa8f6e321395d5648dea4dffb243db911fd
feat: require function response identity
```

### 3A. Production app-side media ownership

Status: `HOST_GREEN + OFF-CALL S22 SMOKE GREEN`

Delivered:

- `CallMediaSessionCoordinator`;
- production `ShizukuCallMediaSessionBackend`;
- generation ownership and structured failure state;
- dedicated non-main-thread control path;
- local TAKE OVER independent of remote/network cleanup;
- production off-call smoke retaining frozen Shizuku behavior.

### 3B. Realtime transport and PCM path

Status: `HOST_GREEN`

Delivered:

- short-lived credential types/provider boundary;
- hardened OpenAI WebSocket handshake;
- OkHttp connector (pinned to a compileSdk-36-compatible version);
- 16 kHz telephony <-> 24 kHz Realtime PCM adaptation;
- bounded audio workers and queues;
- local barge-in cancellation;
- generation-safe transport/session cleanup;
- typed Realtime events and function calling.

### 3C. Task/workflow/policy model

Status: `HOST_GREEN`

Delivered:

- hard constraints separate from soft preferences;
- immutable `authorizedFacts`;
- deterministic `CallConfirmationPolicy` outside the model;
- `NEEDS_USER_DECISION`;
- structured outcomes;
- privacy-safe debug rendering.

Counterparty speech cannot widen authority.

### 3D. Commitment gate

Status: `HOST_GREEN`

Delivered:

- strict `evaluate_proposal` tool;
- exact one-shot commitment permit;
- `commit_proposal` consumes that permit once;
- response-scoped forcing of `commit_proposal` after approval;
- `NoTools` follow-up after successful commitment;
- permit invalidation on new proposal/start/TAKE OVER/close/stale generation.

This is the application-side authority gate; prompt wording alone is never treated as authorization.

### 3E. Credential broker / real network smoke plumbing

Status: `HOST_GREEN + FAIL-CLOSED S22 DRY-RUN GREEN / REAL OPENAI NETWORK SMOKE PENDING`

Delivered:

- host credential broker with `OPENAI_API_KEY` only in host environment;
- separate client bearer;
- Android backend credential request/provider factories;
- one-shot app-private smoke config;
- protected ADB-only off-call Realtime probe;
- secure host runner staging secrets over stdin rather than argv/Intent.

The physical no-config dry-run kept `CALL_STATE=0 -> 0`, left no helper alive, and did not dial.

Remaining external prerequisite: an actual host `OPENAI_API_KEY` plus authenticated HTTPS access/tunnel to the loopback broker. Never place the long-lived key in APK/phone.

### 3F. Speech-integrity defense in depth

Status: `MECHANICS HOST_GREEN / PRODUCTION POLICY WIRING RED`

Implemented mechanics:

- typed output identity;
- final audio transcript lifecycle;
- `response.done` terminal status;
- bounded whole-response PCM buffer before telephony TX;
- no release until audio done + final transcript done + successful response done;
- cancelled/failed/incomplete response discard;
- unknown response status fail-closed.

The response-lifecycle pump gate was fully GREEN at behavior commit `7d7bd65738568ee5a29ff6d2674b157584264e54`.

Current unfinished host work:

1. implement and production-wire `CallRealtimeAgentOutputApprovalPolicy` using the same `CallCommitmentGate` as proposal/commit handlers;
2. keep ordinary speech RELEASE limited to safe `ACTIVE_NEGOTIATION` state; DROP during pending commitment authority, `NEEDS_USER_DECISION`, and other unsafe states;
3. update the stale legacy function-call transport test to include the newly-required GA `response_id` and rerun the full suite.

At behavior HEAD `94594aa`, the focused response-id protocol test passes but the full realtime-client suite has one stale-fixture failure. Do not call this HEAD fully GREEN until that is closed.

### 3G. First real Realtime physical validation

Status: `PENDING`

Ordered gate:

1. real OpenAI **off-call** S22 network/session smoke — no cellular dial;
2. first real cellular Realtime call to a controlled/non-committing target;
3. verify actual audio quality, latency, barge-in, TAKE OVER, cleanup, and GA event ordering/identity;
4. only then permit the first real clinic-registration attempt.

## Phase 4 — product UX

Status: `LATER`

Needed after core Phase 3 physical validation:

- call task/session screen;
- AI state and diagnostics;
- prominent `Take over now`;
- user-decision surface for `NEEDS_USER_DECISION`;
- disclosure/transcript policy as product requirements settle;
- structured outcome view.

Do not replace the default dialer without a concrete requirement.

## Phase 5 — robustness/product matrix

Status: `LATER`

Validate real Realtime sessions under:

- longer calls;
- screen off / app background;
- network loss/recovery;
- route changes and Bluetooth;
- Wi-Fi Calling;
- incoming/outgoing call variants;
- hold/resume;
- repeated sessions without reboot;
- Realtime/provider/backend failures.

Exit condition: every tested failure has a defined fail-safe behavior and no condition leaves AI injection stuck active.

## Current decision

Do not jump directly from host tests to an autonomous clinic booking.

Close the two current host REDs, get a completely GREEN host baseline, prove real OpenAI connectivity off-call, then prove one non-committing cellular Realtime conversation. Only after those gates should a real appointment booking be attempted.

Authoritative continuation: `docs/HANDOFF_NEXT_CHAT.md` and `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`.
