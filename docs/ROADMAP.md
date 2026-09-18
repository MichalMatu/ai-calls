# Roadmap

Capabilities advance only when their required evidence gate is actually proven.

Evidence levels:

- `HOST_GREEN` — deterministic tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- `PRODUCT_READY` — proven, fail-safe and acceptable for normal use.

## Phase 0 — repository and verification discipline

Status: `DONE`

The project is modularized into app, audio contracts, privileged helper and Realtime client. `scripts/verify_host.sh` is the canonical local/CI quality gate. Durable work is main-first; Local Agent metadata remains isolated on `agent-control`.

## Phase 1 — cellular media capability

Status: `DONE / PROVEN_S22`

Production directions:

```text
RX: VOICE_DOWNLINK -> privileged helper -> PFD -> app
TX: app -> PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Generic media/voice-communication TX experiments are not the production route on this S22.

## Phase 2 — stable local bridge

Status: `DONE / PROVEN_S22 / FROZEN`

Physical evidence covers simultaneous bidirectional media, endpoint loss, app/helper death cleanup, repeated start/abort, natural call end, 600 seconds total live bidirectional media and separate resource telemetry.

Frozen checkpoint commit:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Do not repeat the full physical matrix without concrete regression evidence. Details: `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Phase 3 — Telephone Agent / OpenAI Realtime

Status: `HOST_GREEN / WAITING FOR EXTERNAL CREDENTIAL PREREQUISITES`

Implemented and host-verified:

- production app-side media coordinator/backend/runtime;
- Realtime WebSocket transport and generation safety;
- 16 kHz telephony <-> 24 kHz Realtime PCM path;
- bounded audio pump and barge-in;
- task/constraints/preferences/authorized-facts workflow model;
- deterministic `CallConfirmationPolicy` and `NEEDS_USER_DECISION`;
- typed Realtime function calling;
- strict side-effect-free proposal parser;
- one-shot commitment authorization and forced `commit_proposal`;
- output speech buffering/approval before cellular TX;
- host credential broker and short-lived Android credential provider;
- bounded privacy-safe event trace;
- controlled live-call probe with fail-closed S22 preflight;
- unified host/CI quality gate.

### Gate 3A — genuine OpenAI off-call S22 smoke

Status: `BLOCKED ONLY BY HOST ENVIRONMENT`

Required:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

No cellular call is made. Expected successful safety path:

```text
FETCHING_CREDENTIAL -> CONNECTING_REALTIME -> STARTING_MEDIA -> FAILED
```

Reason: `realtime_connected_off_call_media_rejected`. `ACTIVE` off-call is failure.

### Gate 3B — first controlled cellular Realtime call

Status: `PENDING 3A`

Use direct USB, Bluetooth OFF, active `MODE_IN_CALL`, earpiece and muted voice-call stream before broker config is staged. Validate RX/TX intelligibility, latency, barge-in, TAKE OVER, cleanup and actual Realtime event ordering. The smoke runner does not dial/hang up.

### Gate 3C — first real task

Status: `PENDING 3B`

Only after one controlled non-committing call passes, attempt a real user-authorized task such as clinic registration. Anything outside explicit authority goes to `NEEDS_USER_DECISION`.

## Phase 4 — product UX

Status: `LATER`

Only after Phase 3 physical proof: task/session UX, prominent TAKE OVER, user-decision surface, concise diagnostics and structured outcomes. Do not replace the default dialer without a concrete requirement.

## Phase 5 — robustness matrix

Status: `LATER`

Validate longer real Realtime calls, screen/background behavior, network failure/recovery, route/Bluetooth changes, Wi-Fi Calling, incoming/outgoing variants and repeated sessions. Exit condition: failures have defined fail-safe behavior and never leave AI injection active.

## Current decision

Do not expand features before Gate 3A. The codebase is intentionally being kept small and auditable at the credential boundary. Current evidence and exact continuation: `docs/PHASE3_REALTIME_STATUS_2026-09-18.md` and `docs/HANDOFF_NEXT_CHAT.md`.
