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
  -> optional later integrations
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

Modular Android project, host regression discipline, explicit privilege boundaries, durable device evidence and Local Agent workflow are established.

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

Milestone D physical proof covers app/helper death cleanup, RX/TX endpoint loss, 20/20 start/abort cycles, natural call end, 600 seconds total live bidirectional media and a separate 50.1-second resource trend.

Detailed evidence: `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Phase 3 — Telephone Agent / OpenAI Realtime

Status: `IN PROGRESS — HOST STACK GREEN / GENUINE OPENAI S22 SESSION BLOCKED ON EXTERNAL PREREQUISITES`

Current behavior baseline:

```text
c939a9bbcc4603d846ab8e3cd17d2b8dd19d17e5
fix: sanitize realtime diagnostic labels
```

Detailed ledger: `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`.

### 3A. Production app-side media ownership

Status: `HOST_GREEN + OFF-CALL S22 SMOKE GREEN`

Delivered production coordinator/backend ownership, generation checks, local TAKE OVER and fail-safe cleanup without changing the frozen Samsung media contract.

### 3B. Realtime transport and PCM path

Status: `HOST_GREEN`

Delivered short-lived credential boundary, hardened OpenAI WebSocket handshake, OkHttp connector, 16 kHz <-> 24 kHz mono PCM conversion, bounded audio workers, local barge-in, generation-safe cleanup and typed output/function lifecycle.

### 3C. Task/workflow/policy model

Status: `HOST_GREEN`

Delivered hard constraints, soft preferences, immutable `authorizedFacts`, deterministic `CallConfirmationPolicy`, `NEEDS_USER_DECISION`, structured outcomes and privacy-safe rendering. Counterparty speech cannot widen authority.

### 3D. Commitment gate

Status: `HOST_GREEN`

Delivered strict `evaluate_proposal`, exact one-shot commitment permit, `commit_proposal`, forced commit follow-up, `NoTools` after commitment and invalidation on new proposal/start/TAKE OVER/close/stale generation.

### 3E. Credential broker / real network smoke plumbing

Status: `HOST_GREEN + FAIL-CLOSED S22 DRY-RUN GREEN / REAL OPENAI NETWORK SMOKE BLOCKED`

Delivered loopback host broker, separate Android bearer, credential provider factories, one-shot private smoke config, protected ADB-only off-call probe and host runner staging broker URL/token through stdin.

The physical no-config dry-run kept `CALL_STATE=0 -> 0`, left no helper alive and did not dial.

External prerequisites still missing from Local Agent environment:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

Never move the long-lived key to APK/phone to bypass this gate.

### 3F. Speech-integrity defense in depth

Status: `HOST_GREEN`

Delivered typed output identity, final transcript/response lifecycle, bounded full-response PCM buffering, production application output approval and required function `response_id`.

Ordinary speech is RELEASED only in safe `ACTIVE_NEGOTIATION` with no pending commitment permit and DROPPED during pending permit, `NEEDS_USER_DECISION` or other unsafe states.

### 3G. Privacy-safe Realtime evidence trace

Status: `HOST_GREEN`

Delivered:

- bounded `RealtimeEventTrace`;
- `TracingRealtimeTransport` decorator;
- relative event timing/lifecycle ordering;
- local aliases for response/item/call correlation;
- per-trace salted SHA-256 internal identity keys;
- no PCM, transcript text, function arguments/output, credentials, raw error messages or raw provider IDs retained;
- optional runtime instrumentation;
- off-call smoke `trace=...` evidence without changing PASS criteria;
- shared bounded diagnostic-label sanitizer preventing newline/control/oversized function/error labels from entering trace or `RealtimeFunctionCall.toString()`.

Latest full evidence task:

```text
realtime-diagnostic-labels-host-green-20260918-2770
exit_code: 0
```

### 3H. First real Realtime physical validation

Status: `PENDING / BLOCKED ON 3E EXTERNAL PREREQUISITES`

Ordered gate:

1. genuine OpenAI **off-call** S22 network/session smoke — no cellular dial;
2. require `FETCHING_CREDENTIAL -> CONNECTING_REALTIME -> STARTING_MEDIA -> FAILED` with expected off-call media rejection and redacted trace evidence;
3. first real cellular Realtime call to a controlled/non-committing target;
4. verify actual audio quality, latency, barge-in, TAKE OVER, cleanup and GA event ordering/identity;
5. only then permit the first real clinic-registration attempt.

## Phase 4 — product UX

Status: `LATER`

After core Phase 3 physical validation:

- call task/session screen;
- AI state and diagnostics;
- prominent `Take over now`;
- user-decision surface for `NEEDS_USER_DECISION`;
- disclosure/transcript policy;
- structured outcome view.

Do not replace the default dialer without a concrete requirement.

## Phase 5 — robustness/product matrix

Status: `LATER`

Validate real Realtime sessions under longer calls, screen-off/background operation, network failure/recovery, route changes/Bluetooth, Wi-Fi Calling, incoming/outgoing variants, hold/resume, repeated sessions and backend/provider failures.

Exit condition: every tested failure has defined fail-safe behavior and no condition leaves AI injection active.

## Current decision

Do not jump from host tests directly to an autonomous clinic booking.

The host stack including speech authorization, function identity, redacted Realtime evidence tracing and diagnostic log-injection hardening is GREEN. The next authoritative gate is the genuine OpenAI off-call S22 smoke, currently blocked only by external credential/tunnel prerequisites. After that passes, prove one controlled non-committing cellular Realtime conversation before any real appointment booking.

Authoritative continuation: `docs/HANDOFF_NEXT_CHAT.md` and `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`.
