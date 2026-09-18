# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on one stock Samsung phone to an OpenAI Realtime voice agent without external audio hardware.

Target device: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## Status

- Cellular RX/TX and fail-safe media lifecycle: `DONE / PROVEN_S22`.
- Telephone Agent policy, Realtime transport, PCM bridge, commitment gate, speech gate, redacted diagnostics and credential boundary: `HOST_GREEN`.
- Genuine OpenAI session on the S22: **not run yet**.
- Real cellular Realtime audio: **not proven yet**.

The next gate needs the host-side API/broker prerequisites; nothing else should be bypassed to reach it.

## Architecture

```text
CallTask + explicit authority
  -> CallWorkflow / deterministic policy
  -> Telephone Agent session
  -> Realtime session orchestrator
       |-> OpenAI Realtime transport
       `-> CallMediaSessionCoordinator
            `-> Shizuku privileged helper
                 |-> VOICE_DOWNLINK RX
                 `-> CALL_ASSISTANT / TELEPHONY_TX
```

Modules:

- `app/` — workflow, policy, session orchestration and diagnostic entrypoints;
- `audio-bridge/` — small device-independent PCM contracts/models;
- `privileged-helper/` — Samsung telephony audio primitives and fail-safe lifecycle;
- `realtime-client/` — short-lived credential boundary and Realtime protocol/transport;
- `scripts/` — host/device validation and credential broker tooling.

Continuous PCM crosses the privilege boundary through transferred PFDs, never per-frame Binder calls.

## Frozen Samsung invariants

The physically proven media path must not be casually redesigned:

- RX uses `VOICE_DOWNLINK` with the proven construction/attribution ordering;
- TX uses `com.android.shell` attribution and `USAGE_CALL_ASSISTANT` / TELEPHONY_TX;
- internal media is mono PCM16LE;
- stereo duplication exists only at the Samsung TX boundary;
- endpoint loss aborts the whole generation;
- PFD ownership is close-safe;
- TAKE OVER is local and immediate;
- heartbeat and `CallModeWatchdog` remain active safeguards.

See `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Telephone Agent safety

Authority belongs to deterministic application code, not model or counterparty text.

`evaluate_proposal` parses one strict proposal and evaluates it against user constraints. An allowed proposal receives a one-shot opaque commitment permit. `commit_proposal` consumes exactly that permit. Speech is buffered before cellular TX and released only when the application-owned output policy allows it.

The strict proposal decoder is separated from workflow/commitment mutation in `CallRealtimeProposalParser`; malformed model JSON cannot mutate authority state while parsing.

## Credentials

A standard OpenAI API key must never enter the APK, Android Intent, app-private smoke config, ADB arguments or phone.

Developer flow:

```text
host OPENAI_API_KEY
  -> loopback credential broker
  -> protected HTTPS path
  -> short-lived client secret
  -> Android Realtime WebSocket
```

The genuine off-call smoke additionally requires a separate broker bearer and protected HTTPS URL.

## Quality gate

Run the same gate locally and in GitHub Actions:

```bash
bash scripts/verify_host.sh
```

It runs all module unit tests, Android lint for every module, debug APK build, Python tests, secret-pattern checks, live-runner telephony-control checks and `git diff --check`.

Hardware claims still require physical device evidence; host tests never upgrade a capability to `PROVEN_S22`.

## Branch policy

Durable product development happens on `main`. `agent-control` is reserved for Local Agent task/result traffic and must never be merged into product history. Do not accumulate milestone/work branches for routine single-developer work; Git history and evidence documents preserve checkpoints.

## Current next gate

Before any live Realtime cellular call, run the genuine OpenAI **off-call** S22 smoke. Required host environment:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

Expected successful safety path:

```text
FETCHING_CREDENTIAL -> CONNECTING_REALTIME -> STARTING_MEDIA -> FAILED
```

with reason `realtime_connected_off_call_media_rejected`. Reaching `ACTIVE` while off-call is a safety failure.

After that passes, perform one controlled non-committing cellular Realtime call, then only later a real task such as clinic registration.

See `docs/HANDOFF_NEXT_CHAT.md` and `docs/PHASE3_REALTIME_STATUS_2026-09-18.md` for the exact continuation state.
