# Handoff — Milestone D frozen / Telephone Agent v1 in progress

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Local Agent binding:

```text
agent_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
repository_id: android-ai-call-bridge
control_branch: agent-control
```

## Start rule

Milestone D is closed. Do not rerun Phase 2 physical gates or expand the diagnostic harness unless a concrete regression requires it.

Read `AGENTS.md`, this handoff, `docs/PHASE2D_FREEZE_2026-09-18.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`, and `docs/superpowers/plans/2026-09-18-telephone-agent-v1.md`.

## Frozen earlier checkpoints

```text
Phase 2B:
milestone/phase2b-proven-s22-20260916
c10f8dde29f245f8f98fb008a3572c21fe73fe35

Phase 2C:
milestone/phase2c-shizuku-live-proven-20260916
9c136fc05c5b33f383d72b0b7080ad5b9a754bb4

Phase 2D:
milestone/phase2d-failsafe-proven-s22-20260918
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

## Milestone D final evidence

Physical GREEN on the target S22+: normal app death during active media, helper/UserService death, transferred RX and TX PFD close, 20/20 start/abort cycles, natural call end after the `CallModeWatchdog` fix, and 600 seconds total live bidirectional media as 10 x 60 seconds.

Resource trend was closed with one additional short live run, not another ten calls:
- 24 external samples / 50.1 seconds, call active throughout;
- app: RSS 123128 -> 124196 KiB, FD 41 -> 41, threads 27 -> 25;
- helper: RSS 152932 -> 153948 KiB, FD 41 -> 41, threads 19 -> 19;
- stable app/helper PIDs;
- cleanup left call idle, helper absent and Bluetooth restored.

Final freeze regression:
- 48/48 Python tests PASS;
- full Gradle unit tests + `:app:assembleDebug` GREEN;
- security-shape GREEN;
- `git diff --check` GREEN;
- clean product tree.

Do not claim the 50.1-second resource trend is 600 seconds of resource telemetry. The 600-second proof is media-plane endurance.

## Preserve these invariants

Preserve direct-shell RX ordering, Shizuku attribution ordering, RX system attribution, TX `com.android.shell` attribution, CALL_ASSISTANT / TELEPHONY_TX, internal mono PCM16LE, stereo duplication only at the Samsung TX boundary, PFD AutoClose ownership, one shared RX+TX fail-safe generation, endpoint loss -> whole-generation cleanup, no per-frame Binder, local TAKE OVER, helper heartbeat, and `CallModeWatchdog` unless concrete regression evidence requires change.

## Telephone Agent v1 progress

Do not make `MainActivity` or diagnostic probes the production lifecycle owner.

Completed on the product branch after the Milestone D freeze:
- production `CallMediaSessionCoordinator` with `IDLE / BINDING / PREPARING / ACTIVE / STOPPING / FAILED`, generation ownership, fail-closed cleanup, heartbeat/PFD lifetime and TAKE OVER independent of blocking Binder work;
- production `ShizukuCallMediaSessionBackend` with dedicated control executor, direct Binder death handling, bounded bind timeout and non-blocking cleanup;
- target-device production off-call smoke GREEN on the S22+: `BINDING -> PREPARING -> STOPPING -> FAILED -> IDLE`, expected because no cellular call was active; call state stayed idle, Bluetooth stayed enabled, Shizuku survived and `:call_media` cleaned up;
- immutable Telephone Agent task/workflow/policy model with hard constraints, preferences, authorized facts, structured outcomes and `NEEDS_USER_DECISION`;
- GA Realtime WebSocket transport boundary with short-lived typed credentials, 24 kHz PCM adaptation, bounded audio queues, barge-in cancellation and whole-generation cleanup;
- typed Realtime function tools and function-call parsing/output;
- generation-bound one-shot function responders so stale tool calls cannot write into a newer session;
- deterministic `evaluate_proposal` bridge: strict JSON -> `CallProposal` -> `CallConfirmationPolicy`; proposals outside authority hold the Realtime function call open until the user approves/rejects that exact proposal;
- task-derived Realtime instructions that explicitly separate hard authority, soft preferences, authorized facts and untrusted counterparty speech;
- `CallRealtimeAgentSessionSpec`, which binds the request, task-derived instructions, `evaluate_proposal` tool and matching handler so production wiring cannot omit one accidentally.

Latest session-spec host gate at commit `170ec73feca831eae2bf322ea719b1641121287f` was GREEN: focused tests, full `realtime-client` + `app` unit tests, `:app:assembleDebug`, 48 Python tests, key-pattern scan, `git diff --check`, clean tree.

The later documentation commits do not change runtime behavior.

## Current OpenAI Realtime direction verified 2026-09-18

Use the GA API, not the retired beta shape:
- developer backend mints a short-lived client secret using `POST /v1/realtime/client_secrets`;
- GA response exposes top-level `value` and `expires_at`;
- a standard WebSocket client may connect to `wss://api.openai.com/v1/realtime?model=...` using the short-lived `ek_...` credential in the WebSocket subprotocol;
- WebRTC remains the preferred browser/mobile transport in OpenAI guidance, but this project intentionally keeps the lower-level WebSocket path while it owns explicit PCM bridging and measures it on the S22+;
- no long-lived OpenAI API key may ever be stored in the APK.

The existing WebSocket serializer already uses the GA session/event shapes relevant to this implementation (`session.type`, `audio.input/output`, `response.output_audio.delta`, typed function-call output).

## Next gates

### 1. Credential issuer contract

Do not add an unauthenticated ad-hoc token endpoint merely to make the demo run.

Define the developer-backend contract first:
- authenticate the app/user before issuing a credential;
- keep the standard OpenAI API key server-side only;
- create a GA Realtime client secret with a bounded TTL;
- associate an `OpenAI-Safety-Identifier` when a stable privacy-preserving user identifier is available;
- return only the minimum client response required by Android, at least `value` + `expires_at`;
- never log the secret value.

Then add the Android `RealtimeCredentialProvider` implementation and tests for HTTPS, malformed/non-2xx responses, expiry and secret redaction.

### 2. Hard commitment enforcement

`evaluate_proposal` is deterministic once invoked, but `tool_choice=auto` plus model instructions is not itself a proof that the model can never verbally imply acceptance without calling the tool.

Before any real autonomous booking/purchase/commitment is allowed, add a separate hard gate so an external commitment cannot be completed merely because the model says yes. Keep the app-owned policy authoritative; counterparty speech must never widen task authority.

### 3. Production session runtime

After the credential boundary is fixed, compose `CallRealtimeAgentSessionSpec` with `CallRealtimeSessionOrchestrator` in one production runtime owner. The same owner must expose local TAKE OVER and pending user-decision resolution without making `MainActivity` the lifecycle owner.

### 4. First Realtime physical validation

Start with a network/session smoke that does not make a cellular call. Only after credential/session behavior is proven should the Realtime engine be attached to a real cellular call. Do not rerun the frozen Milestone D matrix unless a concrete regression appears.

Safe test number `510100100` remains authorized only if a future physical regression genuinely requires it. Keep the phone silent, Bluetooth off during the call, mute before dial and after ACTIVE, speakerphone off, and restore Bluetooth afterward.
